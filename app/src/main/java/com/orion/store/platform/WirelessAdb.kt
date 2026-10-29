package com.orion.store.platform

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.edit
import com.orion.store.R
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import io.github.muntashirakon.adb.android.AdbMdns
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.util.concurrent.TimeUnit

/** Built-in wireless ADB client talking to this device's own adbd. Installs run as the shell user, the same route as
 *  `adb install`, which Android exempts from the unverified-developer 24-hour wait. Pair once; afterwards Orion
 *  reconnects by itself and, holding WRITE_SECURE_SETTINGS (self-granted over ADB), turns Wireless debugging back on after reboots. */
object WirelessAdb {
    enum class Phase { IDLE, WAITING, PAIRING, READY, FAILED }
    data class Status(val phase: Phase = Phase.IDLE, val msg: String = "")

    private val _status = MutableStateFlow(Status())
    val status = _status.asStateFlow()
    val supported get() = Build.VERSION.SDK_INT >= 30
    val connected get() = m?.isConnected == true

    /** No Wi-Fi, so wireless debugging cannot work. Callers ask the user whether to use the regular installer. */
    class NoWifiError : Exception("Connect to Wi-Fi to install without the wait.")

    const val KEY_CODE = "code"
    private const val CHANNEL = "orion_wireless_adb"
    private const val NOTIF = 7310
    private const val ADB_WIFI = "adb_wifi_enabled"
    // adbd listens on every interface, so localhost works without depending on the Wi-Fi address
    private const val HOST = "127.0.0.1"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    /** The attempt in progress, shared by every caller: a download's warm-up and the install itself must not each start their own,
     *  because every attempt can make Android ask to allow wireless debugging on the network again. */
    private var inFlight: Deferred<Unit>? = null
    @Volatile private var failedAt = 0L

    /** RSA identity adbd remembers after pairing. Lives in no-backup storage so a restore onto another phone does not carry it. */
    private class Manager(ctx: Context) : AbsAdbConnectionManager() {
        private val pk: PrivateKey
        private val crt: Certificate
        init {
            setApi(Build.VERSION.SDK_INT)
            setTimeout(10, TimeUnit.SECONDS)
            val dir = File(ctx.noBackupFilesDir, "adb").apply { mkdirs() }
            val kf = File(dir, "key"); val cf = File(dir, "cert")
            if (kf.exists() && cf.exists()) {
                pk = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(kf.readBytes()))
                crt = cf.inputStream().use { CertificateFactory.getInstance("X.509").generateCertificate(it) }
            } else {
                val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
                val name = X500Name("CN=Orion Store"); val now = System.currentTimeMillis()
                crt = JcaX509CertificateConverter().getCertificate(
                    JcaX509v3CertificateBuilder(name, BigInteger.valueOf(now), Date(now), Date(now + 3650L * 86_400_000), name, kp.public)
                        .build(JcaContentSignerBuilder("SHA256withRSA").build(kp.private)))
                pk = kp.private
                kf.writeBytes(pk.encoded); cf.writeBytes(crt.encoded)
            }
        }
        override fun getPrivateKey() = pk
        override fun getCertificate() = crt
        override fun getDeviceName() = "Orion Store"
    }

    @Volatile private var m: Manager? = null
    private fun mgr(ctx: Context) = m ?: synchronized(this) { m ?: Manager(ctx.applicationContext).also { m = it } }
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("wireless_adb", Context.MODE_PRIVATE)

    fun onWifi(ctx: Context): Boolean = ctx.getSystemService(ConnectivityManager::class.java).let {
        it.getNetworkCapabilities(it.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }
    private fun global(ctx: Context, key: String) = runCatching { Settings.Global.getInt(ctx.contentResolver, key, 0) == 1 }.getOrDefault(false)
    fun devOptions(ctx: Context) = global(ctx, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED)
    fun wirelessOn(ctx: Context) = global(ctx, ADB_WIFI)
    /** Orion has reached adbd before (pairing done), so connecting is worth a try. */
    fun paired(ctx: Context) = prefs(ctx).getInt("port", 0) > 0
    private fun canToggle(ctx: Context) = ctx.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    private fun aboutPhoneIntent(): Intent = Intent(Settings.ACTION_DEVICE_INFO_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    /** Developer options, scrolled to the Wireless debugging row where the platform supports the highlight extra. */
    private fun wirelessDebuggingIntent(): Intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        .putExtra(":settings:fragment_args_key", "toggle_adb_wireless").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private suspend fun discover(ctx: Context, type: String, ms: Long): Int? {
        val found = CompletableDeferred<Int>()
        val mdns = AdbMdns(ctx, type) { h, p -> if (h != null && p > 0) found.complete(p) }
        mdns.start()
        return try { withTimeoutOrNull(ms) { found.await() } } finally { mdns.stop() }
    }

    private fun tryConnect(mg: Manager, port: Int) = runCatching { mg.connect(HOST, port) }.getOrDefault(false) || mg.isConnected

    /** Connects to adbd: the last known port first (instant), then mDNS, switching Wireless debugging on when Orion is allowed to.
     *  Concurrent callers join the attempt already running and get its result. */
    suspend fun connect(ctx: Context) {
        val app = ctx.applicationContext
        val job = synchronized(this) {
            inFlight?.takeIf { it.isActive } ?: scope.async {
                try { lock.withLock { doConnect(app) } } catch (e: Exception) { if (e !is NoWifiError) failedAt = System.currentTimeMillis(); throw e }
            }.also { inFlight = it }
        }
        job.await()
    }

    private suspend fun doConnect(ctx: Context) {
        withContext(Dispatchers.IO) {
            val mg = mgr(ctx)
            if (mg.isConnected) return@withContext
            val last = prefs(ctx).getInt("port", 0)
            if (last <= 0 || !tryConnect(mg, last)) {
                if (!onWifi(ctx)) throw NoWifiError()
                val cr = ctx.contentResolver; val toggle = canToggle(ctx); val wasOn = wirelessOn(ctx)
                // Like Shizuku: with WRITE_SECURE_SETTINGS, Orion brings back Developer options and Wireless debugging itself, and turns off
                // the 7-day authorization timeout that would otherwise make Android forget the pairing
                if (toggle) runCatching {
                    if (!devOptions(ctx)) Settings.Global.putInt(cr, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1)
                    Settings.Global.putLong(cr, "adb_allowed_connection_time", 0)
                    if (!wasOn) Settings.Global.putInt(cr, ADB_WIFI, 1)
                }
                // Just switched on: Android may be asking to allow this network, so give that prompt time to be answered
                var port = discover(ctx, AdbMdns.SERVICE_TYPE_TLS_CONNECT, if (wasOn) 15_000 else 30_000)
                // Already on but silent (Wi-Fi changed, adbd stuck): restart Wireless debugging once. Never right after switching it on
                // ourselves, since the restart would raise the network prompt a second time.
                if (port == null && toggle && wasOn) {
                    runCatching { Settings.Global.putInt(cr, ADB_WIFI, 0); delay(1500); Settings.Global.putInt(cr, ADB_WIFI, 1) }
                    port = discover(ctx, AdbMdns.SERVICE_TYPE_TLS_CONNECT, 15_000)
                }
                val found = port ?: throw NativeError(if (toggle) "Wireless debugging didn't start on this Wi-Fi. Open Wireless debugging once and allow this network (tick Always allow)." else "Wireless debugging is off. Turn it on or run the setup again.")
                if (!tryConnect(mg, found)) throw NativeError("Wireless debugging refused Orion. Run the setup again to re-pair.")
                prefs(ctx).edit { putInt("port", found) }
            }
            if (!canToggle(ctx)) runCatching { exec(mg, "pm grant ${ctx.packageName} ${Manifest.permission.WRITE_SECURE_SETTINGS}") }
        }
    }

    /** Fire-and-forget connect, started when a download begins so the install itself does not wait on discovery. */
    fun warm(ctx: Context) {
        // Off Wi-Fi there is nothing to warm, and right after a failure another background attempt would only repeat the network prompt
        if (connected || !onWifi(ctx) || System.currentTimeMillis() - failedAt < 120_000) return
        scope.launch { runCatching { connect(ctx.applicationContext) } }
    }

    private fun exec(mg: Manager, cmd: String, input: File? = null): String = mg.openStream("exec:$cmd").use { s ->
        // AdbOutputStream.close only flushes, so the stream stays open for the reply
        input?.let { f -> s.openOutputStream().use { o -> f.inputStream().use { it.copyTo(o) } } }
        s.openInputStream().bufferedReader().readText()
    }

    /** [opts]: Beyond install picks; the shell user may pass every pm install switch, same as root and Shizuku. */
    suspend fun install(ctx: Context, f: File, opts: Collection<String>? = null) = withContext(Dispatchers.IO) {
        val cmd = "pm install -r${opts?.let { com.orion.store.data.installArgs(it, Build.VERSION.SDK_INT, "") }.orEmpty()} -S ${f.length()}"
        val pi = runCatching { ctx.packageManager.getPackageArchiveInfo(f.absolutePath, 0) }.getOrNull()
        val t0 = System.currentTimeMillis()
        connect(ctx)
        // adbd sometimes leaves the stream open after pm prints Success, so the reply never ends. The package itself says when it landed.
        val reply = CompletableDeferred<Result<String>>()
        scope.launch {
            reply.complete(runCatching {
                try { exec(mgr(ctx), cmd, f) } catch (e: Exception) {
                    if (reply.isCompleted) throw e
                    // Stale socket after a Wi-Fi drop or adbd restart: reconnect once
                    runCatching { mgr(ctx).disconnect() }; connect(ctx); exec(mgr(ctx), cmd, f)
                }
            })
        }
        fun landed() = pi != null && runCatching {
            val p = ctx.packageManager.getPackageInfo(pi.packageName, 0)
            p.lastUpdateTime >= t0 - 2000 && androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(p) == androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(pi)
        }.getOrDefault(false)
        val out = withTimeoutOrNull(5 * 60_000L) {
            while (!reply.isCompleted) {
                delay(700)
                if (landed()) { reply.complete(Result.success("Success")); runCatching { mgr(ctx).disconnect() } } // frees the hung stream; the next call reconnects on the saved port
            }
            reply.await().getOrThrow()
        } ?: if (landed()) "Success" else throw NativeError("Wireless debugging stopped answering. Try again.")
        if ("Success" !in out) throw NativeError(out.trim().ifEmpty { "INSTALL_FAILED" })
    }

    /** Runs one shell command over ADB as the shell user, reconnecting once on a stale socket. A reply adbd never closes is cut off after [ms]. */
    suspend fun shell(ctx: Context, cmd: String, ms: Long = 20_000): String = withContext(Dispatchers.IO) {
        connect(ctx)
        val reply = scope.async {
            runCatching {
                try { exec(mgr(ctx), cmd) } catch (_: Exception) {
                    runCatching { mgr(ctx).disconnect() }; connect(ctx); exec(mgr(ctx), cmd)
                }
            }
        }
        withTimeoutOrNull(ms) { reply.await() }?.getOrThrow()
            ?: run { runCatching { mgr(ctx).disconnect() }; throw NativeError("Wireless debugging stopped answering. Try again.") }
    }

    // ---------------- Guided setup ----------------
    // Runs from the notification shade: Settings drops its pairing dialog once it leaves the screen, and Orion cannot open
    // pages from the background, so each step is a tappable notification that advances by itself as the switches flip.

    private data class Step(val open: Intent, val title: String, val text: String, val code: Boolean)
    private var observer: ContentObserver? = null

    fun startSetup(ctx: Context) {
        val app = ctx.applicationContext
        if (_status.value.phase == Phase.READY || _status.value.phase == Phase.FAILED) _status.value = Status()
        if (observer == null) observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = advance(app, false)
        }.also { o -> listOf(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, ADB_WIFI).forEach { app.contentResolver.registerContentObserver(Settings.Global.getUriFor(it), false, o) } }
        advance(app, true)
    }

    private fun stopSetup(ctx: Context) { observer?.let { ctx.contentResolver.unregisterContentObserver(it) }; observer = null }

    private fun advance(ctx: Context, open: Boolean) {
        if (_status.value.phase == Phase.PAIRING || _status.value.phase == Phase.READY) return
        val step = when {
            !devOptions(ctx) -> Step(aboutPhoneIntent(), "Step 1 of 3 · Unlock Developer options",
                "Tap Build number 7 times (on some phones it sits under Software information). Orion moves on by itself.", false)
            !wirelessOn(ctx) -> Step(wirelessDebuggingIntent(), "Step 2 of 3 · Turn on Wireless debugging",
                "Tap here, open Wireless debugging, switch it on and tap Allow.", false)
            else -> Step(wirelessDebuggingIntent(), "Step 3 of 3 · Pair Orion",
                "In Wireless debugging tap Pair device with pairing code, then type the 6-digit code here.", true)
        }
        _status.value = Status(Phase.WAITING, step.title)
        notify(ctx, step.title, step.text, code = step.code, open = step.open)
        if (open) runCatching { ctx.startActivity(step.open) }
    }

    suspend fun pair(ctx: Context, code: String) = withContext(Dispatchers.IO) {
        _status.value = Status(Phase.PAIRING, "Pairing...")
        notify(ctx, "Pairing...", "Keep the pairing code dialog open.", quiet = true)
        try {
            val port = discover(ctx, AdbMdns.SERVICE_TYPE_TLS_PAIRING, 10_000)
                ?: throw NativeError("Pairing dialog not found. Open it again and use the new code.")
            try { mgr(ctx).pair(HOST, port, code) } catch (_: Exception) { throw NativeError("Wrong or expired code. Try again with a new one.") }
            connect(ctx)
            stopSetup(ctx)
            _status.value = Status(Phase.READY, "Paired. Installs now skip the wait.")
            notify(ctx, "Orion is paired", "Apps now install instantly, no 24-hour wait.", done = true)
        } catch (e: Exception) {
            val msg = (if (e is NativeError || e is NoWifiError) e.message else null) ?: "Pairing failed. Try again."
            _status.value = Status(Phase.FAILED, msg)
            notify(ctx, "Pairing failed", msg, code = true, open = wirelessDebuggingIntent())
        }
    }

    fun pairAsync(ctx: Context, code: String) = scope.launch { pair(ctx.applicationContext, code) }

    @SuppressLint("MissingPermission")
    private fun notify(ctx: Context, title: String, text: String, code: Boolean = false, done: Boolean = false, open: Intent? = null, quiet: Boolean = false) {
        val nm = NotificationManagerCompat.from(ctx)
        nm.createNotificationChannel(NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_HIGH).setName("Wireless install setup").build())
        if (!nm.areNotificationsEnabled()) return
        val n = NotificationCompat.Builder(ctx, CHANNEL).setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title).setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH).setOnlyAlertOnce(quiet).setAutoCancel(done).apply { if (done) setTimeoutAfter(10_000) }
        open?.let { n.setContentIntent(PendingIntent.getActivity(ctx, 1, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)) }
        if (code) {
            val pi = PendingIntent.getBroadcast(ctx, 0, Intent(ctx, AdbPairReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            n.addAction(NotificationCompat.Action.Builder(0, "Enter code", pi)
                .addRemoteInput(RemoteInput.Builder(KEY_CODE).setLabel("6-digit pairing code").build()).build())
        }
        nm.notify(NOTIF, n.build())
    }
}

class AdbPairReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        val code = RemoteInput.getResultsFromIntent(i)?.getCharSequence(WirelessAdb.KEY_CODE)?.filter(Char::isDigit)?.toString() ?: return
        val done = goAsync()
        WirelessAdb.pairAsync(ctx, code).invokeOnCompletion { done.finish() }
    }
}
