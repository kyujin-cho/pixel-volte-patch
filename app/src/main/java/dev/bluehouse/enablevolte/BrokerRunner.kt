package dev.bluehouse.enablevolte

import android.app.Activity
import android.app.IActivityManager
import android.app.IInstrumentationWatcher
import android.app.UiAutomationConnection
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import android.widget.Toast
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.util.concurrent.atomic.AtomicBoolean

// UserHandle.PER_USER_RANGE
private const val PER_USER_RANGE = 100000

/**
 * Runs [BrokerInstrumentation] one at a time and reports each outcome to [ApplyResultTracker].
 *
 * AMS tracks a single active instrumentation per process, so overlapping runs would steal each
 * other's results. Requests are therefore queued, consecutive changes of the same subscription
 * are merged into one run, and the next run starts only after the previous one reported back
 * through its [IInstrumentationWatcher] (or timed out).
 *
 * Calls made off the main thread are posted to it.
 */
object BrokerRunner {
    private const val TAG = "BrokerRunner"

    // Generous upper bound for one run (UiAutomation retries plus the config write).
    private const val RESULT_TIMEOUT_MS = 60_000L

    private val mainHandler = Handler(Looper.getMainLooper())

    private class Request(
        val context: Context,
        val subId: Int,
        val arguments: Bundle,
    ) {
        val isClear: Boolean get() = arguments.getBoolean(BROKER_ARG_CLEAR)
        val persistent: Boolean get() = arguments.getBoolean(BROKER_ARG_PERSISTENT, true)
    }

    private val queue = ArrayDeque<Request>()
    private var running: Run? = null
    private var dispatchScheduled = false

    private class Run(
        val requests: List<Request>,
    ) : IInstrumentationWatcher.Stub() {
        private val completed = AtomicBoolean(false)
        val timeout = Runnable { complete("No result from the broker within ${RESULT_TIMEOUT_MS / 1000} s") }

        override fun instrumentationStatus(
            name: ComponentName?,
            resultCode: Int,
            results: Bundle?,
        ) {
        }

        override fun instrumentationFinished(
            name: ComponentName?,
            resultCode: Int,
            results: Bundle?,
        ) {
            val error =
                if (resultCode == Activity.RESULT_OK) {
                    null
                } else {
                    // AMS fills "shortMsg" when the instrumentation crashed instead of finishing.
                    results?.getString(BROKER_RESULT_ERROR)
                        ?: results?.getString("shortMsg")
                        ?: "Broker finished with result code $resultCode"
                }
            complete(error)
        }

        fun complete(error: String?) {
            if (!completed.compareAndSet(false, true)) return
            mainHandler.post { onRunFinished(this, error) }
        }
    }

    /** Queues a broker run for [subId] with the prepared broker [arguments]. */
    fun enqueue(
        context: Context,
        subId: Int,
        arguments: Bundle,
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            // The queue is confined to the main thread.
            mainHandler.post { enqueue(context, subId, arguments) }
            return
        }
        ApplyResultTracker.onApplyStarted(subId)
        queue.addLast(Request(context.applicationContext, subId, arguments))
        scheduleDispatch()
    }

    private fun scheduleDispatch() {
        if (dispatchScheduled || running != null) return
        dispatchScheduled = true
        // Posted, so that several changes made by one click are queued (and merged) first.
        mainHandler.post {
            dispatchScheduled = false
            dispatchNext()
        }
    }

    private fun canMerge(
        first: Request,
        next: Request?,
    ): Boolean =
        next != null &&
            next.subId == first.subId &&
            !first.isClear &&
            !next.isClear &&
            next.persistent == first.persistent

    private fun dispatchNext() {
        if (running != null) return
        val first = queue.removeFirstOrNull() ?: return
        val requests = mutableListOf(first)
        val arguments = Bundle(first.arguments)
        while (canMerge(first, queue.firstOrNull())) {
            val next = queue.removeFirst()
            arguments.putAll(next.arguments)
            requests.add(next)
        }

        val run = Run(requests)
        running = run
        mainHandler.postDelayed(run.timeout, RESULT_TIMEOUT_MS)
        val error =
            try {
                start(first.context, arguments, run)
            } catch (e: Exception) {
                e.toString()
            } catch (e: LinkageError) {
                e.toString()
            }
        if (error != null) {
            Log.e(TAG, "Failed to start the broker: $error")
            run.complete(error)
        }
    }

    private fun onRunFinished(
        run: Run,
        error: String?,
    ) {
        mainHandler.removeCallbacks(run.timeout)
        if (running === run) {
            running = null
        }
        val first = run.requests.first()
        if (error != null) {
            Log.e(TAG, "Broker run for subId ${first.subId} failed: $error")
            val context = first.context
            Toast.makeText(context, context.getString(R.string.config_apply_failed, error), Toast.LENGTH_LONG).show()
        }
        run.requests.forEach { ApplyResultTracker.onApplyFinished(it.context, it.subId, error) }
        scheduleDispatch()
    }

    /** Starts the instrumentation. Returns null on success, or a description of the failure. */
    private fun start(
        context: Context,
        arguments: Bundle,
        watcher: IInstrumentationWatcher,
    ): String? {
        val component = ComponentName(context, BROKER_INSTRUMENTATION_CLASS)
        val userId = Process.myUid() / PER_USER_RANGE

        // Preferred: start the instrumentation from the shell-uid UserService, so the broker can
        // adopt the shell identity through the public UiAutomation API.
        val service = PrivilegedServiceClient.service
        if (service != null) {
            arguments.putBoolean(BROKER_ARG_UI_AUTOMATION, true)
            val error =
                try {
                    service.startBrokerInstrumentation(component, arguments, userId, watcher.asBinder())
                } catch (e: Exception) {
                    e.toString()
                }
            if (error == null) return null
            Log.w(TAG, "Starting the broker from the UserService failed, falling back: $error")
        } else {
            Log.i(TAG, "UserService is not connected yet, using the legacy broker start")
            PrivilegedServiceClient.ensureBound()
        }

        // Fallback: start it from the app process through Shizuku. The connection is then owned by
        // the app, so the broker has to use the hidden shell permission delegate instead.
        arguments.putBoolean(BROKER_ARG_UI_AUTOMATION, false)
        val am =
            IActivityManager.Stub.asInterface(
                ShizukuBinderWrapper(SystemServiceHelper.getSystemService(Context.ACTIVITY_SERVICE)),
            )
        val started =
            startInstrumentationCompat(am, component, INSTR_FLAG_NO_RESTART, arguments, watcher, UiAutomationConnection(), userId)
        return if (started) null else "startInstrumentation returned false"
    }
}
