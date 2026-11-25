package dev.bluehouse.enablevolte

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

enum class ApplyState {
    APPLYING,
    APPLIED,
    FAILED,
}

enum class ImsCheckState {
    CHECKING,
    REGISTERED,
    NOT_REGISTERED,
    UNKNOWN,
}

/** Outcome of the latest carrier config change of one subscription, as shown in the UI. */
data class ApplyStatus(
    val applyState: ApplyState,
    val error: String? = null,
    val imsState: ImsCheckState? = null,
)

/**
 * Tracks, per subscription, whether the last carrier config change was applied and whether IMS
 * registered afterwards.
 *
 * All mutable state is confined to the main thread. The IMS check runs on a background thread
 * and is bounded by [IMS_POLL_TIMEOUT_MS].
 */
object ApplyResultTracker {
    private const val TAG = "ApplyResultTracker"
    const val IMS_POLL_TIMEOUT_MS = 30_000L
    private const val IMS_POLL_INTERVAL_MS = 1_000L

    // Give the modem time to drop the old registration after a reset, so the first poll does not
    // report the registration that is about to go away.
    private const val IMS_RESET_SETTLE_MS = 3_000L

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Observed by Compose; written on the main thread only. */
    val statuses = mutableStateMapOf<Int, ApplyStatus>()

    private val pendingApplies = HashMap<Int, Int>()
    private val batchErrors = HashMap<Int, String>()
    private val deferredResets = HashSet<Int>()
    private val imsCheckGenerations = ConcurrentHashMap<Int, AtomicInteger>()

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    fun isApplying(subId: Int): Boolean = (pendingApplies[subId] ?: 0) > 0

    /** A change for [subId] was handed to the broker and its outcome is not known yet. */
    fun onApplyStarted(subId: Int) =
        runOnMain {
            val pending = pendingApplies[subId] ?: 0
            if (pending == 0) {
                batchErrors.remove(subId)
            }
            pendingApplies[subId] = pending + 1
            statuses[subId] = ApplyStatus(ApplyState.APPLYING)
        }

    /**
     * A change for [subId] finished; [error] is null on success. Once every queued change of the
     * subscription has finished, runs a deferred IMS reset (if one was requested meanwhile) and
     * checks the IMS registration.
     */
    fun onApplyFinished(
        context: Context,
        subId: Int,
        error: String?,
    ) = runOnMain {
        if (error != null) {
            batchErrors[subId] = error
        }
        val pending = ((pendingApplies[subId] ?: 1) - 1).coerceAtLeast(0)
        if (pending > 0) {
            pendingApplies[subId] = pending
            return@runOnMain
        }
        pendingApplies.remove(subId)
        val batchError = batchErrors.remove(subId)
        statuses[subId] =
            if (batchError == null) {
                ApplyStatus(ApplyState.APPLIED)
            } else {
                ApplyStatus(ApplyState.FAILED, batchError)
            }
        val resetIms = deferredResets.remove(subId)
        startImsCheck(context.applicationContext, subId, resetIms)
    }

    /**
     * Requests an IMS reset for [subId]. Returns true when it was deferred because a change is
     * still being applied; the reset then runs right after that change finishes.
     */
    fun deferImsResetIfApplying(subId: Int): Boolean {
        if (!isApplying(subId)) return false
        deferredResets.add(subId)
        return true
    }

    /**
     * Polls the IMS registration of [subId] on a background thread for up to
     * [IMS_POLL_TIMEOUT_MS], optionally resetting IMS first ([resetIms]) and letting a reset
     * settle before the first poll ([waitForReset]). A newer check of the same subscription
     * supersedes a running one. Only runs as the follow-up of a change tracked here.
     */
    fun startImsCheck(
        context: Context,
        subId: Int,
        resetIms: Boolean,
        waitForReset: Boolean = resetIms,
    ) {
        if (statuses[subId] == null) return
        val generation = imsCheckGenerations.getOrPut(subId) { AtomicInteger() }.incrementAndGet()
        updateImsState(subId, generation, ImsCheckState.CHECKING)
        val appContext = context.applicationContext
        thread(name = "ImsCheck-$subId", isDaemon = true) {
            val moder = SubscriptionModer(appContext, subId)
            if (resetIms) {
                try {
                    moder.resetIms()
                } catch (e: Exception) {
                    Log.w(TAG, "Deferred IMS reset failed for subId $subId", e)
                } catch (e: LinkageError) {
                    Log.w(TAG, "Deferred IMS reset is unavailable for subId $subId", e)
                }
            }
            if (waitForReset) {
                Thread.sleep(IMS_RESET_SETTLE_MS)
            }
            val deadline = System.currentTimeMillis() + IMS_POLL_TIMEOUT_MS
            var lastState = ImsCheckState.UNKNOWN
            while (isCurrent(subId, generation)) {
                lastState =
                    try {
                        if (moder.isIMSRegistered) ImsCheckState.REGISTERED else ImsCheckState.NOT_REGISTERED
                    } catch (e: Exception) {
                        Log.w(TAG, "IMS registration check failed for subId $subId", e)
                        ImsCheckState.UNKNOWN
                    } catch (e: LinkageError) {
                        Log.w(TAG, "IMS registration check is unavailable for subId $subId", e)
                        ImsCheckState.UNKNOWN
                    }
                if (lastState == ImsCheckState.REGISTERED || System.currentTimeMillis() >= deadline) {
                    break
                }
                Thread.sleep(IMS_POLL_INTERVAL_MS)
            }
            updateImsState(subId, generation, lastState)
        }
    }

    private fun isCurrent(
        subId: Int,
        generation: Int,
    ): Boolean = imsCheckGenerations[subId]?.get() == generation

    private fun updateImsState(
        subId: Int,
        generation: Int,
        state: ImsCheckState,
    ) = runOnMain {
        if (!isCurrent(subId, generation)) return@runOnMain
        // Only report IMS state as the follow-up of a config change made in this process.
        val current = statuses[subId] ?: return@runOnMain
        statuses[subId] = current.copy(imsState = state)
    }
}
