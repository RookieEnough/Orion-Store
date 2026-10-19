package com.orion.store

import com.orion.store.data.*
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class ReleaseNotesTest {
    @Test fun parsesOwnerNotesAndPreservesLineBreaks() {
        val r = releaseNotesResultFor(200, """{"tag_name":"v1.2.3","body":"- Fixed crash\n- Added dark mode"}""")
        assertEquals(ReleaseNotesResult.Loaded("- Fixed crash\n- Added dark mode"), r)
    }

    @Test fun missingOrBlankBodyIsEmpty() {
        assertEquals(ReleaseNotesResult.Empty, releaseNotesResultFor(200, """{"tag_name":"v1"}"""))
        assertEquals(ReleaseNotesResult.Empty, releaseNotesResultFor(200, """{"body":""}"""))
        assertEquals(ReleaseNotesResult.Empty, releaseNotesResultFor(200, """{"body":"   \n "}"""))
        assertEquals(ReleaseNotesResult.Empty, releaseNotesResultFor(200, """{"body":null}"""))
    }

    @Test fun malformedJsonIsAnError() {
        assertTrue(releaseNotesResultFor(200, "{not valid") is ReleaseNotesResult.Error)
        assertTrue(releaseNotesResultFor(200, "[1,2]") is ReleaseNotesResult.Error)
    }

    @Test fun mapsHttpStatuses() {
        assertEquals(ReleaseNotesResult.NotFound, releaseNotesResultFor(404, ""))
        assertTrue(releaseNotesResultFor(500, "") is ReleaseNotesResult.Error)
        assertTrue(releaseNotesResultFor(403, "") is ReleaseNotesResult.Error)
    }

    @Test fun fetchMapsHttpAndNetworkFailures() = runBlocking {
        assertEquals(ReleaseNotesResult.NotFound, fetchReleaseNotes("o/r", "v1", fetch = { throw HttpError(404, "") }))
        assertEquals(ReleaseNotesResult.Error("GitHub returned HTTP 500."), fetchReleaseNotes("o/r", "v1", fetch = { throw HttpError(500, "") }))
        assertTrue(fetchReleaseNotes("o/r", "v1", fetch = { throw IOException("offline") }) is ReleaseNotesResult.Error)
        assertEquals(ReleaseNotesResult.Unsupported, fetchReleaseNotes("", "v1", fetch = { error("must not fetch") }))
        assertEquals(ReleaseNotesResult.Unsupported, fetchReleaseNotes("o/r", "", fetch = { error("must not fetch") }))
        assertEquals(ReleaseNotesResult.Unsupported, fetchReleaseNotes("https://github.com/o/r", "v1", fetch = { error("must not fetch") }))
    }

    @Test fun fetchUsesTheExactRepositoryAndTag() = runBlocking {
        var seen = ""
        val r = fetchReleaseNotes("owner/repo", "v1.2/beta #2", fetch = { seen = it; """{"body":"ok"}""" })
        assertEquals(ReleaseNotesResult.Loaded("ok"), r)
        assertEquals("https://api.github.com/repos/owner/repo/releases/tags/v1.2%2Fbeta%20%232", seen)
    }

    @Test fun cacheReusesSuccessfulResult() = runBlocking {
        var calls = 0
        val loader = ReleaseNotesLoader(fetch = { calls++; """{"body":"notes"}""" })
        val first = loader.load("o/r", "v1")
        val second = loader.load("o/r", "v1")
        assertEquals(ReleaseNotesResult.Loaded("notes"), first)
        assertEquals(first, second)
        assertEquals(1, calls)
        assertEquals(1, loader.size())
    }

    @Test fun failureIsCachedUntilExplicitRetry() = runBlocking {
        var calls = 0
        val loader = ReleaseNotesLoader(fetch = {
            calls++
            if (calls == 1) throw IOException("offline")
            """{"body":"notes"}"""
        })
        assertTrue(loader.load("o/r", "v1") is ReleaseNotesResult.Error)
        assertTrue(loader.load("o/r", "v1") is ReleaseNotesResult.Error)
        // Reopening must not retry automatically: the error itself is cached for this session.
        assertEquals(1, calls)
        assertEquals(1, loader.size())
        assertEquals(ReleaseNotesResult.Loaded("notes"), loader.load("o/r", "v1", force = true))
        assertEquals(2, calls)
        // A successful retry replaces the cached error.
        assertEquals(ReleaseNotesResult.Loaded("notes"), loader.load("o/r", "v1"))
        assertEquals(2, calls)
    }

    @Test fun concurrentRequestsForTheSameReleaseShareOneFetch() = runBlocking {
        var calls = 0
        val loader = ReleaseNotesLoader(fetch = { calls++; delay(50); """{"body":"notes"}""" })
        val first = async { loader.load("o/r", "v1") }
        val second = async { loader.load("o/r", "v1") }
        assertEquals(ReleaseNotesResult.Loaded("notes"), first.await())
        assertEquals(ReleaseNotesResult.Loaded("notes"), second.await())
        assertEquals(1, calls)
    }

    @Test fun cacheSeparatesTagsAndCachesStableMisses() = runBlocking {
        var calls = 0
        val loader = ReleaseNotesLoader(fetch = { calls++; """{"body":"${it.substringAfterLast("/")}"}""" })
        assertEquals(ReleaseNotesResult.Loaded("v1"), loader.load("o/r", "v1"))
        assertEquals(ReleaseNotesResult.Loaded("v2"), loader.load("o/r", "v2"))
        assertEquals(2, calls)

        var misses = 0
        val missing = ReleaseNotesLoader(fetch = { misses++; throw HttpError(404, "") })
        assertEquals(ReleaseNotesResult.NotFound, missing.load("o/r", "v1"))
        assertEquals(ReleaseNotesResult.NotFound, missing.load("o/r", "v1"))
        assertEquals(1, misses)
    }

    @Test fun cacheKeyKeepsTheExactTag() {
        assertEquals(releaseNotesCacheKey("Owner/Repo", "v1"), releaseNotesCacheKey("owner/repo", "v1"))
        assertNotEquals(releaseNotesCacheKey("owner/repo", "v1"), releaseNotesCacheKey("owner/repo", "V1"))
        assertNull(releaseNotesCacheKey("", "v1"))
        assertNull(releaseNotesCacheKey("o/r", " "))
    }

    @Test fun sameVersionOnDifferentStreamsKeepsItsOwnTag() {
        val stable = VersionOption(type = "Stable", version = "2.0", tag = "v2.0")
        val beta = VersionOption(type = "Beta", version = "2.0", tag = "v2.0-beta.1")
        val app = AppItem(id = "a", name = "App", githubRepo = "o/r", availableVersions = listOf(beta, stable))

        assertEquals("v2.0-beta.1", preferredVersionOption(app, "Beta")?.tag)
        assertEquals("v2.0", preferredVersionOption(app, "Stable")?.tag)
        assertNotEquals(
            releaseNotesApiUrl(app.githubRepo, preferredVersionOption(app, "Beta")?.tag),
            releaseNotesApiUrl(app.githubRepo, preferredVersionOption(app, "Stable")?.tag),
        )
    }

    @Test fun chosenCandidateCarriesOnlyItsOwnTag() {
        val stable = VersionOption(type = "Stable", version = "2.0", tag = "v2.0")
        val beta = VersionOption(type = "Beta", version = "3.0-beta", tag = "v3.0-beta")
        val app = AppItem(id = "a", name = "App", githubRepo = "o/r", availableVersions = listOf(beta, stable))

        assertEquals("v3.0-beta", preferredVersionOption(app, "Beta")?.tag)
        assertEquals("v2.0", preferredVersionOption(app, "Stable")?.tag)
        assertEquals("v2.0", preferredVersionOption(app, "Nightly")?.tag)
        assertEquals("https://api.github.com/repos/o/r/releases/tags/v3.0-beta", releaseNotesApiUrl(app.githubRepo, preferredVersionOption(app, "Beta")?.tag))
        assertFalse(releaseNotesApiUrl(app.githubRepo, preferredVersionOption(app, "Beta")?.tag).orEmpty().contains("v2.0"))
    }
}
