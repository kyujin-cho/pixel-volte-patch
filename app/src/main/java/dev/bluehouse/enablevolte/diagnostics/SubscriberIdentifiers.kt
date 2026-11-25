package dev.bluehouse.enablevolte.diagnostics

import android.content.Context
import android.util.Log
import dev.bluehouse.enablevolte.CarrierModer
import dev.bluehouse.enablevolte.Moder

/**
 * Best-effort lookup of this device's own subscriber and device identifiers (IMSI, ICCID, IMEI,
 * MEID, phone numbers) through Shizuku, so [IdentifierMasker] can mask them as literals.
 *
 * Every lookup goes through reflection and swallows failures: the hidden telephony signatures
 * drift between releases, and a missing identifier only means the generic patterns have to catch
 * it. Values are never logged. Call off the main thread.
 */
internal class SubscriberIdentifiers(
    private val context: Context,
) : Moder() {
    private companion object {
        const val TAG = "SubscriberIdentifiers"

        // Calls through ShizukuBinderWrapper carry the shell uid, so the package must be shell's.
        const val SHELL_PACKAGE = "com.android.shell"
        const val MAX_SLOTS = 2

        val PER_SUBSCRIPTION_GETTERS =
            listOf(
                "getSubscriberIdForSubscriber",
                "getIccSerialForSubscriber",
                "getLine1NumberForSubscriber",
                "getMsisdnForSubscriber",
                "getImeiForSubscriber",
            )
        val PER_SLOT_GETTERS = listOf("getImeiForSlot", "getMeidForSlot")
        val SUB_GETTERS = listOf("getPhoneNumberFromFirstAvailableSource")
    }

    fun collect(): Set<String> {
        val identifiers = mutableSetOf<String>()
        val subscriptions =
            try {
                CarrierModer(context).subscriptions
            } catch (e: Throwable) {
                Log.w(TAG, "Could not list subscriptions: ${e.javaClass.simpleName}")
                emptyList()
            }
        for (info in subscriptions) {
            identifiers.addIfPresent { info.iccId }
            @Suppress("DEPRECATION")
            identifiers.addIfPresent { info.number }
        }

        val phoneSubInfo = safely { phoneSubInfo }
        val sub = safely { sub }
        for (subId in subscriptions.map { it.subscriptionId }) {
            phoneSubInfo?.let { target ->
                PER_SUBSCRIPTION_GETTERS.forEach { identifiers.addIfPresent { invokeGetter(target, it, subId) } }
            }
            sub?.let { target -> SUB_GETTERS.forEach { identifiers.addIfPresent { invokeGetter(target, it, subId) } } }
        }

        val telephony = safely { telephony }
        if (telephony != null) {
            for (slot in 0 until MAX_SLOTS) {
                PER_SLOT_GETTERS.forEach { identifiers.addIfPresent { invokeGetter(telephony, it, slot) } }
            }
        }
        return identifiers
    }

    private inline fun <T> safely(block: () -> T): T? =
        try {
            block()
        } catch (_: Throwable) {
            null
        }

    private inline fun MutableSet<String>.addIfPresent(block: () -> String?) {
        safely(block)?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it) }
    }

    /**
     * Calls the first public method named [name] returning String whose parameters are an int
     * (filled with [id]) and Strings (calling package, then nulls for feature ids). Returns null
     * when no variant exists or every variant fails.
     */
    private fun invokeGetter(
        target: Any,
        name: String,
        id: Int,
    ): String? {
        for (method in target.javaClass.methods) {
            if (method.name != name || method.returnType != String::class.java) continue
            var packageUsed = false
            val args =
                method.parameterTypes.map { type ->
                    when (type) {
                        Int::class.javaPrimitiveType -> id
                        String::class.java ->
                            if (packageUsed) {
                                null
                            } else {
                                packageUsed = true
                                SHELL_PACKAGE
                            }
                        else -> return@map Unsupported
                    }
                }
            if (args.any { it === Unsupported }) continue
            val value = safely { method.invoke(target, *args.toTypedArray()) as? String }
            if (!value.isNullOrBlank()) return value
        }
        return null
    }

    private object Unsupported
}
