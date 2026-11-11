package com.orion.store.platform

import android.content.Context
import android.os.Build
import java.io.File
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/** XAPK, APKM and APKS are zip files of split APKs. Android installs the ones that match this phone, base first. */
object Bundles {
    private val DPI = listOf("ldpi" to 120, "mdpi" to 160, "tvdpi" to 213, "hdpi" to 240, "xhdpi" to 320, "xxhdpi" to 480, "xxxhdpi" to 640)
    private val DPI_NAME = listOf("xxxhdpi", "xxhdpi", "xhdpi", "tvdpi", "nodpi", "anydpi", "hdpi", "mdpi", "ldpi")
    private val ABI = listOf("arm64-v8a", "armeabi-v7a", "armeabi", "x86-64", "x86")

    fun abiOf(name: String): String? {
        val n = name.lowercase().replace('_', '-')
        return ABI.firstOrNull { Regex("(^|[^a-z0-9])${Regex.escape(it)}([^a-z0-9]|$)").containsMatchIn(n) }
    }

    fun dpiOf(name: String): String? {
        val n = name.lowercase()
        return DPI_NAME.firstOrNull { Regex("(^|[^a-z])$it([^a-z]|$)").containsMatchIn(n) }
    }

    /** Which entries of a bundle this device should install. */
    fun choose(names: List<String>, abis: List<String>, densityDpi: Int): List<String> {
        val want = abis.map { it.lowercase().replace('_', '-') }
        val abiOk = names.filter { n -> abiOf(n).let { it == null || it in want } }
        val densities = abiOk.mapNotNull { dpiOf(it) }.filter { it != "nodpi" && it != "anydpi" }.toSet()
        val closest = densities.minByOrNull { d -> kotlin.math.abs((DPI.firstOrNull { it.first == d }?.second ?: densityDpi) - densityDpi) }
        val kept = if (closest == null) abiOk else abiOk.filter { n -> dpiOf(n).let { it == null || it == "nodpi" || it == "anydpi" || it == closest } }
        return kept.sortedBy { n -> val l = n.lowercase(); when { l == "base.apk" -> 0; "split" in l || "config." in l || "config_" in l -> 2; else -> 1 } }
    }

    /** The APKs inside [f] that this phone can install, or [f] itself when it is already one APK. Empty when it is neither.
     *  Split files are written next to the bundle. The package manager reads that folder, and it cannot read the app's private cache. */
    fun parts(ctx: Context, f: File): List<File> {
        if (readableApk(ctx, f)) return listOf(f)
        if (!isZip(f)) return emptyList()
        val names = zipNames(f)
        val packed = names.any { entry -> apkName(entry) != null }
        if (!packed && names.any { it.substringAfterLast('/').substringAfterLast('\\').equals("AndroidManifest.xml", true) }) {
            val copy = File(f.parentFile, "${f.nameWithoutExtension}.apk")
            if (copy.absolutePath != f.absolutePath && (!copy.exists() || copy.length() != f.length())) f.copyTo(copy, overwrite = true)
            copy.setReadable(true, false)
            return if (readableApk(ctx, copy)) listOf(copy) else emptyList()
        }
        val parent = f.parentFile ?: return emptyList()
        val dir = File(parent, "splits-${f.nameWithoutExtension}-${f.length()}").apply { mkdirs() }
        parent.listFiles { d -> d.isDirectory && d.name.startsWith("splits-${f.nameWithoutExtension}-") && d.name != dir.name }?.forEach { it.deleteRecursively() }
        val have = dir.listFiles { x -> x.isFile && x.extension.equals("apk", true) }?.toList().orEmpty()
        val apks = if (have.isNotEmpty()) have else extractApks(f, dir)
        if (apks.isEmpty()) return emptyList()
        val abis = Build.SUPPORTED_ABIS.toList()
        val dpi = ctx.resources.displayMetrics.densityDpi
        val picked = choose(apks.map { it.name }, abis, dpi).toSet()
        return apks.filter { it.name in picked }.ifEmpty { apks }.sortedBy { it.name.lowercase().let { n -> if (n == "base.apk") 0 else 1 } }
    }

    private fun readableApk(ctx: Context, f: File) = runCatching { ctx.packageManager.getPackageArchiveInfo(f.absolutePath, 0)?.packageName }.getOrNull() != null

    private fun apkName(entry: String): String? {
        if (entry.endsWith("/")) return null
        val name = entry.substringAfterLast('/').substringAfterLast('\\')
        return name.takeIf { it.endsWith(".apk", true) && ".." !in it && it.isNotBlank() }
    }

    private fun zipNames(f: File): List<String> = runCatching {
        ZipFile(f).use { zip -> zip.entries().asSequence().map { it.name }.toList() }
    }.getOrElse {
        runCatching {
            ZipInputStream(f.inputStream().buffered()).use { zin ->
                buildList {
                    var e = zin.nextEntry
                    while (e != null) { add(e.name); zin.closeEntry(); e = zin.nextEntry }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun extractApks(f: File, dir: File): List<File> {
        fun write(name: String, copy: (File) -> Unit): File? {
            val leaf = apkName(name) ?: return null
            val out = File(dir, leaf)
            copy(out)
            out.setReadable(true, false)
            return out.takeIf { it.length() > 0 }
        }
        val fromZip = runCatching {
            ZipFile(f).use { zip ->
                zip.entries().asSequence().mapNotNull { e ->
                    if (e.isDirectory) null else write(e.name) { out -> zip.getInputStream(e).use { inp -> out.outputStream().use { inp.copyTo(it) } } }
                }.toList()
            }
        }.getOrNull()
        if (!fromZip.isNullOrEmpty()) return fromZip
        return runCatching {
            ZipInputStream(f.inputStream().buffered()).use { zin ->
                buildList {
                    var e = zin.nextEntry
                    while (e != null) {
                        write(e.name) { out -> out.outputStream().use { zin.copyTo(it) } }?.let { add(it) }
                        zin.closeEntry(); e = zin.nextEntry
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun isZip(f: File) = runCatching {
        val h = ByteArray(4); f.inputStream().use { if (it.read(h) != 4) return false }
        h[0] == 0x50.toByte() && h[1] == 0x4B.toByte()
    }.getOrDefault(false)
}
