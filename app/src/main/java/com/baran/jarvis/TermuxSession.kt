package com.baran.jarvis

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.app.PendingIntent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Bridges the app with Termux via the RUN_COMMAND intent.
 *
 * Each command gets a unique request id; the result comes back asynchronously
 * through [ResultService] (a PendingIntent target) which calls [deliver].
 */
object TermuxSession {

    private const val TERMUX_PKG = "com.termux"
    private const val RUN_COMMAND_ACTION = "com.termux.RUN_COMMAND"
    private const val RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService"
    private const val EXTRA_COMMAND_STRING = "com.termux.RUN_COMMAND_COMMAND_STRING"
    private const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
    private const val EXTRA_SESSION_ACTION = "com.termux.RUN_COMMAND_SESSION_ACTION"
    private const val EXTRA_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT"
    private const val EXTRA_RESULT_BUNDLE = "com.termux.service.extra.PLUGIN_RESULT_BUNDLE"
    private const val RESULT_STDOUT = "com.termux.service.extra.PLUGIN_RESULT_BUNDLE_STDOUT"
    private const val RESULT_STDERR = "com.termux.service.extra.PLUGIN_RESULT_BUNDLE_STDERR"
    private const val RESULT_EXIT_CODE = "com.termux.service.extra.PLUGIN_RESULT_BUNDLE_EXIT_CODE"
    private const val RESULT_ERR = "com.termux.service.extra.PLUGIN_RESULT_BUNDLE_ERR"
    private const val RESULT_ERRMSG = "com.termux.service.extra.PLUGIN_RESULT_BUNDLE_ERRMSG"
    const val PERMISSION = "com.termux.permission.RUN_COMMAND"

    lateinit var context: Context
        private set

    private val counter = AtomicInteger(1)
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<RunResult>>()

    data class RunResult(
        val stdout: String,
        val stderr: String,
        val err: Int,
        val errmsg: String
    )

    fun init(ctx: Context) {
        context = ctx.applicationContext
    }

    fun isTermuxInstalled(): Boolean = try {
        context.packageManager.getPackageInfo(TERMUX_PKG, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    fun hasRunPermission(): Boolean =
        context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    fun openTermux() {
        context.packageManager.getLaunchIntentForPackage(TERMUX_PKG)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(it)
        }
    }

    suspend fun run(command: String, timeoutMs: Long = 5 * 60_000): RunResult {
        val reqId = counter.getAndIncrement()
        val deferred = CompletableDeferred<RunResult>()
        pending[reqId] = deferred

        val pi = PendingIntent.getService(
            context,
            reqId,
            Intent(context, ResultService::class.java).putExtra("reqId", reqId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val intent = Intent().apply {
            setClassName(TERMUX_PKG, RUN_COMMAND_SERVICE)
            action = RUN_COMMAND_ACTION
            putExtra(EXTRA_COMMAND_STRING, command)
            putExtra(EXTRA_BACKGROUND, true)
            putExtra(EXTRA_SESSION_ACTION, "2")
            putExtra(EXTRA_PENDING_INTENT, pi)
        }
        context.startService(intent)

        return try {
            withTimeout(timeoutMs) { deferred.await() }
        } catch (e: Exception) {
            pending.remove(reqId)
            RunResult("", "", -2, "zaman aşımı veya iptal: ${e.message}")
        }
    }

    fun deliver(reqId: Int, result: RunResult) {
        pending.remove(reqId)?.complete(result)
    }

    fun readBundle(bundle: android.os.Bundle?): RunResult {
        if (bundle == null) return RunResult("", "", -1, "sonuç bundle'ı yok")
        return RunResult(
            stdout = bundle.getString(RESULT_STDOUT) ?: "",
            stderr = bundle.getString(RESULT_STDERR) ?: "",
            err = bundle.getInt(RESULT_ERR, -1),
            errmsg = bundle.getString(RESULT_ERRMSG) ?: ""
        )
    }
}
