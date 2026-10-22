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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

    const val KEY_CODE = "code"
    private const val CHANNEL = "orion_wireless_adb"
    private const val NOTIF = 7310
    private const val ADB_WIFI = "adb_wifi_enabled"
    // adbd listens on every interface, so localhost works without depending on the Wi-Fi address
    private const val HOST = "127.0.0.1"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

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

    /** Connects to adbd: the last known port first (instant), then mDNS, switching Wireless debugging on when Orion is allowed to. */
    suspend fun connect(ctx: Context): Unit = lock.withLock {
        withContext(Dispatchers.IO) {
            val mg = mgr(ctx)
            if (mg.isConnected) return@withContext
            val last = prefs(ctx).getInt("port", 0)
            if (last <= 0 || !tryConnect(mg, last)) {
                if (!onWifi(ctx)) throw NativeError("连接 Wi-Fi 即可免等待安装。")
                if (canToggle(ctx) && !wirelessOn(ctx)) runCatching { Settings.Global.putInt(ctx.contentResolver, ADB_WIFI, 1) }
                val port = discover(ctx, AdbMdns.SERVICE_TYPE_TLS_CONNECT, 15_000)
                    ?: throw NativeError("无线调试未开启。请打开后重试，或重新运行设置向导。")
                if (!tryConnect(mg, port)) throw NativeError("无线调试拒绝了 Orion 的连接，请重新运行设置向导配对。")
                prefs(ctx).edit { putInt("port", port) }
            }
            if (!canToggle(ctx)) runCatching { exec(mg, "pm grant ${ctx.packageName} ${Manifest.permission.WRITE_SECURE_SETTINGS}") }
        }
    }

    /** Fire-and-forget connect, started when a download begins so the install itself does not wait on discovery. */
    fun warm(ctx: Context) { if (!connected) scope.launch { runCatching { connect(ctx.applicationContext) } } }

    private fun exec(mg: Manager, cmd: String, input: File? = null): String = mg.openStream("exec:$cmd").use { s ->
        // AdbOutputStream.close only flushes, so the stream stays open for the reply
        input?.let { f -> s.openOutputStream().use { o -> f.inputStream().use { it.copyTo(o) } } }
        s.openInputStream().bufferedReader().readText()
    }

    suspend fun install(ctx: Context, f: File) = withContext(Dispatchers.IO) {
        val cmd = "pm install -r -S ${f.length()}"
        connect(ctx)
        val out = try { exec(mgr(ctx), cmd, f) } catch (_: Exception) {
            // Stale socket after a Wi-Fi drop or adbd restart: reconnect once
            runCatching { mgr(ctx).disconnect() }; connect(ctx); exec(mgr(ctx), cmd, f)
        }
        if ("Success" !in out) throw NativeError(out.trim().ifEmpty { "INSTALL_FAILED" })
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
            !devOptions(ctx) -> Step(aboutPhoneIntent(), "第 1 步（共 3 步）· 解锁开发者选项",
                "连点“版本号”7 次（部分手机位于“软件信息”内）。Orion 会自动继续下一步。", false)
            !wirelessOn(ctx) -> Step(wirelessDebuggingIntent(), "第 2 步（共 3 步）· 打开无线调试",
                "点按这里，打开“无线调试”开关，并在弹窗中允许。", false)
            else -> Step(wirelessDebuggingIntent(), "第 3 步（共 3 步）· 配对 Orion",
                "在“无线调试”中点按“使用配对码配对设备”，然后在这里输入 6 位配对码。", true)
        }
        _status.value = Status(Phase.WAITING, step.title)
        notify(ctx, step.title, step.text, code = step.code, open = step.open)
        if (open) runCatching { ctx.startActivity(step.open) }
    }

    suspend fun pair(ctx: Context, code: String) = withContext(Dispatchers.IO) {
        _status.value = Status(Phase.PAIRING, "正在配对…")
        notify(ctx, "正在配对…", "请保持配对码弹窗打开。", quiet = true)
        try {
            val port = discover(ctx, AdbMdns.SERVICE_TYPE_TLS_PAIRING, 10_000)
                ?: throw NativeError("未找到配对弹窗。请重新打开并输入新的配对码。")
            try { mgr(ctx).pair(HOST, port, code) } catch (_: Exception) { throw NativeError("配对码错误或已过期，请重新获取后重试。") }
            connect(ctx)
            stopSetup(ctx)
            _status.value = Status(Phase.READY, "配对成功，安装从此免等待。")
            notify(ctx, "Orion 已配对", "应用现在可即时安装，无需 24 小时等待。", done = true)
        } catch (e: Exception) {
            val msg = (e as? NativeError)?.message ?: "配对失败，请重试。"
            _status.value = Status(Phase.FAILED, msg)
            notify(ctx, "配对失败", msg, code = true, open = wirelessDebuggingIntent())
        }
    }

    fun pairAsync(ctx: Context, code: String) = scope.launch { pair(ctx.applicationContext, code) }

    @SuppressLint("MissingPermission")
    private fun notify(ctx: Context, title: String, text: String, code: Boolean = false, done: Boolean = false, open: Intent? = null, quiet: Boolean = false) {
        val nm = NotificationManagerCompat.from(ctx)
        nm.createNotificationChannel(NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_HIGH).setName("无线安装设置").build())
        if (!nm.areNotificationsEnabled()) return
        val n = NotificationCompat.Builder(ctx, CHANNEL).setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title).setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH).setOnlyAlertOnce(quiet).setAutoCancel(done).apply { if (done) setTimeoutAfter(10_000) }
        open?.let { n.setContentIntent(PendingIntent.getActivity(ctx, 1, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)) }
        if (code) {
            val pi = PendingIntent.getBroadcast(ctx, 0, Intent(ctx, AdbPairReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            n.addAction(NotificationCompat.Action.Builder(0, "输入配对码", pi)
                .addRemoteInput(RemoteInput.Builder(KEY_CODE).setLabel("6 位配对码").build()).build())
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
