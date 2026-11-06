package com.orion.store.platform

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileInputStream
import com.rosan.dhizuku.api.Dhizuku
import com.rosan.dhizuku.api.DhizukuRequestPermissionListener
import com.rosan.dhizuku.api.DhizukuUserServiceArgs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/** Silent installs and uninstalls through Dhizuku, which shares a device owner with other apps. [DhizukuService] runs inside
 *  Dhizuku's process, so its PackageInstaller sessions are the device owner's and need no confirmation. */
object DhizukuBridge {
    const val URL = "https://github.com/iamr0s/Dhizuku/releases"
    @Volatile private var service: IBinder? = null

    /** Dhizuku is installed and holds a device or profile owner. */
    fun available(ctx: Context) = Build.VERSION.SDK_INT >= 26 && runCatching { Dhizuku.init(ctx.applicationContext) }.getOrDefault(false)
    fun granted(ctx: Context) = available(ctx) && runCatching { Dhizuku.isPermissionGranted() }.getOrDefault(false)

    suspend fun request(ctx: Context): Boolean {
        if (!available(ctx)) throw NativeError("Dhizuku is not installed or not activated as device owner.")
        if (Dhizuku.isPermissionGranted()) return true
        return suspendCancellableCoroutine { c ->
            Dhizuku.requestPermission(object : DhizukuRequestPermissionListener() {
                override fun onRequestPermission(grantResult: Int) { if (c.isActive) c.resume(grantResult == PackageManager.PERMISSION_GRANTED) }
            })
        }
    }

    private suspend fun binder(ctx: Context): IBinder {
        service?.takeIf { it.pingBinder() }?.let { return it }
        if (!granted(ctx)) throw NativeError("Dhizuku permission missing. Turn Dhizuku on again in Settings.")
        return withTimeoutOrNull(10_000) {
            suspendCancellableCoroutine { c ->
                val conn = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, b: IBinder?) { service = b; if (b != null && c.isActive) c.resume(b) }
                    override fun onServiceDisconnected(name: ComponentName?) { service = null }
                }
                Dhizuku.bindUserService(DhizukuUserServiceArgs(ComponentName(ctx, DhizukuService::class.java)), conn)
            }
        } ?: throw NativeError("Dhizuku did not answer. Open Dhizuku once, then try again.")
    }

    private suspend fun call(ctx: Context, code: Int, write: (Parcel) -> Unit) = withContext(Dispatchers.IO) {
        val b = binder(ctx); val data = Parcel.obtain(); val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(DhizukuService.DESCRIPTOR); write(data)
            b.transact(code, data, reply, 0)
            val status = reply.readInt(); val msg = reply.readString()
            if (status != PackageInstaller.STATUS_SUCCESS) throw NativeError(msg?.ifBlank { null } ?: "INSTALL_FAILED")
        } finally { data.recycle(); reply.recycle() }
    }

    suspend fun install(ctx: Context, f: File) {
        val parts = Bundles.parts(ctx, f).ifEmpty { listOf(f) }
        if (parts.size == 1) ParcelFileDescriptor.open(parts[0], ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            call(ctx, DhizukuService.INSTALL) { it.writeLong(parts[0].length()); fd.writeToParcel(it, 0) }
        } else {
            val fds = parts.map { ParcelFileDescriptor.open(it, ParcelFileDescriptor.MODE_READ_ONLY) }
            try {
                call(ctx, DhizukuService.INSTALL_MANY) { p ->
                    p.writeInt(parts.size)
                    parts.zip(fds).forEach { (file, fd) -> p.writeLong(file.length()); fd.writeToParcel(p, 0) }
                }
            } finally { fds.forEach { runCatching { it.close() } } }
        }
    }

    suspend fun uninstall(ctx: Context, pkg: String) = call(ctx, DhizukuService.UNINSTALL) { it.writeString(pkg) }
}

/** Runs in Dhizuku's process with its device-owner context (Dhizuku builds it from this class name, see proguard-rules). Codes 1 and 2 are
 *  Dhizuku's own create and destroy calls; replies are a PackageInstaller status and its message. */
class DhizukuService(private val ctx: Context) : Binder() {
    companion object {
        const val DESCRIPTOR = "com.orion.store.DhizukuService"
        const val INSTALL = FIRST_CALL_TRANSACTION + 10
        const val INSTALL_MANY = FIRST_CALL_TRANSACTION + 12
        const val UNINSTALL = FIRST_CALL_TRANSACTION + 11
        private const val ACTION = "com.orion.store.DHIZUKU_RESULT"
    }

    init { attachInterface(null, DESCRIPTOR) }
    override fun getInterfaceDescriptor() = DESCRIPTOR

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (code != INSTALL && code != INSTALL_MANY && code != UNINSTALL) return code in FIRST_CALL_TRANSACTION + 1..FIRST_CALL_TRANSACTION + 2 || super.onTransact(code, data, reply, flags)
        data.enforceInterface(DESCRIPTOR)
        val (status, msg) = runCatching {
            val pi = ctx.packageManager.packageInstaller
            if (code == UNINSTALL) {
                val pkg = data.readString().orEmpty(); result { pi.uninstall(pkg, it) }
            } else {
                val files = if (code == INSTALL) listOf(data.readLong() to ParcelFileDescriptor.CREATOR.createFromParcel(data))
                else List(data.readInt()) { data.readLong() to ParcelFileDescriptor.CREATOR.createFromParcel(data) }
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setSize(files.sumOf { it.first }); if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
                val id = pi.createSession(params)
                result { sender -> pi.openSession(id).use { s ->
                    files.forEachIndexed { i, (size, fd) ->
                        s.openWrite(if (i == 0) "base.apk" else "split$i.apk", 0, size).use { out -> FileInputStream(fd.fileDescriptor).use { it.copyTo(out) }; s.fsync(out) }
                        fd.close()
                    }
                    s.commit(sender)
                } }
            }
        }.getOrElse { PackageInstaller.STATUS_FAILURE to (it.message ?: it.toString()) }
        reply?.writeInt(status); reply?.writeString(msg)
        return true
    }

    /** Hands [start] a result IntentSender and waits for PackageInstaller's answer on this binder thread. */
    private fun result(start: (android.content.IntentSender) -> Unit): Pair<Int, String?> {
        val done = CountDownLatch(1); var out: Pair<Int, String?> = PackageInstaller.STATUS_FAILURE to "No answer from Android."
        val action = "$ACTION.${System.nanoTime()}"
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                out = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE) to i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE); done.countDown()
            }
        }
        if (Build.VERSION.SDK_INT >= 33) ctx.registerReceiver(r, IntentFilter(action), Context.RECEIVER_NOT_EXPORTED) else ctx.registerReceiver(r, IntentFilter(action))
        try {
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            start(PendingIntent.getBroadcast(ctx, action.hashCode(), Intent(action).setPackage(ctx.packageName), flags).intentSender)
            done.await(3, TimeUnit.MINUTES)
        } finally { runCatching { ctx.unregisterReceiver(r) } }
        return out
    }
}
