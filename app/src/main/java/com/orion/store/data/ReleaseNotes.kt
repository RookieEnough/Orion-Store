package com.orion.store.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.concurrent.ConcurrentHashMap

/** Outcome of one "What's new" lookup. [Loading] is UI state and is never cached. */
sealed class ReleaseNotesResult {
    data object Loading : ReleaseNotesResult()
    data class Loaded(val text: String) : ReleaseNotesResult()
    data object Empty : ReleaseNotesResult()
    data object NotFound : ReleaseNotesResult()
    data object Unsupported : ReleaseNotesResult()
    data class Error(val message: String) : ReleaseNotesResult()
}

/** Reads GitHub's release JSON and returns the owner-written `body`, trimmed, or null when absent/blank. Throws on malformed JSON. */
fun releaseNotesBody(raw: String): String? {
    val root = json.parseToJsonElement(raw)
    val o = root as? JsonObject ?: throw IllegalArgumentException("Release response is not a JSON object.")
    return (o["body"] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
}

/** Maps one HTTP response to a result. 404 is a missing release; any other non-2xx is an error. */
fun releaseNotesResultFor(statusCode: Int, raw: String): ReleaseNotesResult = when {
    statusCode == 404 -> ReleaseNotesResult.NotFound
    statusCode !in 200..299 -> ReleaseNotesResult.Error("GitHub returned HTTP $statusCode.")
    else -> runCatching { releaseNotesBody(raw) }.fold(
        onSuccess = { if (it == null) ReleaseNotesResult.Empty else ReleaseNotesResult.Loaded(it) },
        onFailure = { ReleaseNotesResult.Error("GitHub returned an unreadable response.") },
    )
}

/** Cache key: repository and exact tag, lowercased for the repository only. */
fun releaseNotesCacheKey(githubRepo: String?, tag: String?): String? {
    val repo = githubRepo?.trim()?.lowercase().orEmpty()
    val ref = tag?.trim().orEmpty()
    return if (repo.isEmpty() || ref.isEmpty()) null else "$repo#$ref"
}

/** Default fetcher. Kept injectable so unit tests never touch the network. */
val githubReleaseNotesFetch: suspend (String) -> String = { url ->
    Net.text(url, headers = mapOf("Accept" to "application/vnd.github+json"))
}

/** Fetches the owner-written notes for the exact stored tag; null/blank repository or tag is [ReleaseNotesResult.Unsupported]. */
suspend fun fetchReleaseNotes(
    githubRepo: String?,
    tag: String?,
    fetch: suspend (String) -> String = githubReleaseNotesFetch,
): ReleaseNotesResult {
    val url = releaseNotesApiUrl(githubRepo, tag) ?: return ReleaseNotesResult.Unsupported
    return try {
        releaseNotesResultFor(200, fetch(url))
    } catch (e: HttpError) {
        if (e.code == 404) ReleaseNotesResult.NotFound else ReleaseNotesResult.Error("GitHub returned HTTP ${e.code}.")
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        ReleaseNotesResult.Error("Could not reach GitHub. Check your connection and try again.")
    }
}

/**
 * Session-scoped cache and request coalescer. Loaded, empty, not-found and unsupported results are stable
 * and cached for the session. Errors are kept in memory too, so reopening the sheet does not silently retry;
 * the Retry button calls [load] with `force = true` to bypass the cache. A per-key lock makes concurrent
 * requests for the same repository and tag share the first result. Nothing is written to disk.
 */
class ReleaseNotesLoader(
    private val fetch: suspend (String) -> String = githubReleaseNotesFetch,
) {
    private val entries = ConcurrentHashMap<String, ReleaseNotesResult>()
    private val locks = ConcurrentHashMap<String, Mutex>()

    private fun lockFor(key: String): Mutex = locks.computeIfAbsent(key) { Mutex() }

    fun cached(githubRepo: String?, tag: String?): ReleaseNotesResult? =
        releaseNotesCacheKey(githubRepo, tag)?.let(entries::get)

    fun size() = entries.size

    suspend fun load(githubRepo: String?, tag: String?, force: Boolean = false): ReleaseNotesResult {
        val key = releaseNotesCacheKey(githubRepo, tag) ?: return ReleaseNotesResult.Unsupported
        return lockFor(key).withLock {
            if (!force) entries[key]?.let { return@withLock it }
            val result = fetchReleaseNotes(githubRepo, tag, fetch)
            entries[key] = result
            result
        }
    }
}
