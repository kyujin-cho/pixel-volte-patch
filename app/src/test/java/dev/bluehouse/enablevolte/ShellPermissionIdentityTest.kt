package dev.bluehouse.enablevolte

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellPermissionIdentityTest {
    @Test
    fun legacyInterfaceCleansUpAndPreservesTheOperationResult() {
        val manager = LegacyManager()
        val expected = Any()

        val result =
            ShellPermissionIdentity(manager, LegacyInterface::class.java, UID).use {
                assertEquals(0, manager.stopCalls)
                expected
            }

        assertSame(expected, result)
        assertEquals(1, manager.stopCalls)
    }

    @Test
    fun uidInterfaceReceivesTheDelegatedAppUid() {
        val manager = UidManager()

        ShellPermissionIdentity(manager, UidInterface::class.java, UID).use { }

        assertEquals(listOf(UID), manager.stoppedUids)
    }

    @Test
    fun uidSignatureIsPreferredWhenBothAreAvailable() {
        val manager = DualManager()

        ShellPermissionIdentity(manager, DualInterface::class.java, UID).use { }

        assertEquals(listOf(UID), manager.stoppedUids)
        assertEquals(0, manager.legacyStopCalls)
    }

    @Test
    fun unsupportedInterfaceFailsBeforePermissionsCanBeGranted() {
        var permissionsGranted = false
        var operationRan = false

        assertThrows(NoSuchMethodException::class.java) {
            val identity = ShellPermissionIdentity(Any(), Any::class.java, UID)
            permissionsGranted = true
            identity.use { operationRan = true }
        }

        assertFalse(permissionsGranted)
        assertFalse(operationRan)
    }

    @Test
    fun serviceFailureIsUnwrappedAndDoesNotFallBackToTheLegacySignature() {
        val failure = SecurityException("Delegation belongs to a different UID")
        val manager = DualManager(failure)

        val thrown =
            assertThrows(SecurityException::class.java) {
                ShellPermissionIdentity(manager, DualInterface::class.java, UID).use { }
            }

        assertSame(failure, thrown)
        assertEquals(listOf(UID), manager.stoppedUids)
        assertEquals(0, manager.legacyStopCalls)
    }

    @Test
    fun failedOperationStillReleasesItsPermissions() {
        val manager = UidManager()
        val failure = IllegalStateException("Carrier configuration failed")

        val thrown =
            assertThrows(IllegalStateException::class.java) {
                ShellPermissionIdentity(manager, UidInterface::class.java, UID).use {
                    throw failure
                }
            }

        assertSame(failure, thrown)
        assertEquals(listOf(UID), manager.stoppedUids)
        assertTrue(thrown.suppressed.isEmpty())
    }

    @Test
    fun cleanupFailureDoesNotReplaceTheOriginalOperationFailure() {
        val operationFailure = IllegalStateException("Carrier configuration failed")
        val cleanupFailure = SecurityException("Permission cleanup failed")
        val manager = DualManager(cleanupFailure)

        val thrown =
            assertThrows(IllegalStateException::class.java) {
                ShellPermissionIdentity(manager, DualInterface::class.java, UID).use {
                    throw operationFailure
                }
            }

        assertSame(operationFailure, thrown)
        assertEquals(1, thrown.suppressed.size)
        assertSame(cleanupFailure, thrown.suppressed.single())
        assertEquals(listOf(UID), manager.stoppedUids)
        assertEquals(0, manager.legacyStopCalls)
    }

    @Test
    fun cleanupFailureRemainsVisibleAfterASuccessfulOperation() {
        val failure = IllegalStateException("Permission service unavailable")
        val manager = LegacyManager(failure)
        var operationCompleted = false

        val thrown =
            assertThrows(IllegalStateException::class.java) {
                ShellPermissionIdentity(manager, LegacyInterface::class.java, UID).use {
                    operationCompleted = true
                }
            }

        assertTrue(operationCompleted)
        assertSame(failure, thrown)
        assertEquals(1, manager.stopCalls)
    }

    interface LegacyInterface {
        fun stopDelegateShellPermissionIdentity()
    }

    interface UidInterface {
        fun stopDelegateShellPermissionIdentity(uid: Int)
    }

    interface DualInterface :
        LegacyInterface,
        UidInterface

    class LegacyManager(
        private val failure: RuntimeException? = null,
    ) : LegacyInterface {
        var stopCalls = 0

        override fun stopDelegateShellPermissionIdentity() {
            stopCalls++
            failure?.let { throw it }
        }
    }

    class UidManager : UidInterface {
        val stoppedUids = mutableListOf<Int>()

        override fun stopDelegateShellPermissionIdentity(uid: Int) {
            stoppedUids.add(uid)
        }
    }

    class DualManager(
        private val failure: RuntimeException? = null,
    ) : DualInterface {
        val stoppedUids = mutableListOf<Int>()
        var legacyStopCalls = 0

        override fun stopDelegateShellPermissionIdentity(uid: Int) {
            stoppedUids.add(uid)
            failure?.let { throw it }
        }

        override fun stopDelegateShellPermissionIdentity() {
            legacyStopCalls++
        }
    }

    companion object {
        private const val UID = 10137
    }
}
