package dev.bluehouse.enablevolte

import dev.bluehouse.enablevolte.ImsRegistrationCheck.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImsRegistrationCheckTest {
    @Suppress("unused")
    class FakeTelephony {
        val calls = mutableListOf<String>()

        fun isImsRegistered(subId: Int): Boolean {
            calls += "isImsRegistered($subId)"
            return subId == 1
        }

        fun isImsRegisteredForSubscriber(subId: Long): Boolean = true

        fun failing(subId: Int): Boolean = throw SecurityException("denied")
    }

    @Test
    fun `first available step wins and later steps are not called`() {
        val called = mutableListOf<String>()
        val result =
            ImsRegistrationCheck.resolve(
                listOf(
                    Step("a") {
                        called += "a"
                        false
                    },
                    Step("b") {
                        called += "b"
                        true
                    },
                ),
            )
        assertEquals(false, result)
        assertEquals(listOf("a"), called)
    }

    @Test
    fun `inapplicable step falls through to the next one`() {
        val result =
            ImsRegistrationCheck.resolve(
                listOf(
                    Step("missing") { null },
                    Step("works") { true },
                ),
            )
        assertEquals(true, result)
    }

    @Test
    fun `hidden api drift errors fall through and are reported`() {
        val failed = mutableListOf<String>()
        val result =
            ImsRegistrationCheck.resolve(
                listOf(
                    Step("nsme") { throw NoSuchMethodError("isImsRegisteredForSubscriber") },
                    Step("ame") { throw AbstractMethodError() },
                    Step("security") { throw SecurityException("denied") },
                    Step("reflect") { throw NoSuchMethodException() },
                    Step("works") { false },
                ),
            ) { name, _ -> failed += name }
        assertEquals(false, result)
        assertEquals(listOf("nsme", "ame", "security", "reflect"), failed)
    }

    @Test
    fun `returns unknown when every step fails`() {
        val result =
            ImsRegistrationCheck.resolve(
                listOf(
                    Step("nsme") { throw NoSuchMethodError() },
                    Step("missing") { null },
                ),
            )
        assertNull(result)
    }

    @Test
    fun `binder failures fall through`() {
        // IllegalArgumentException stands in for RemoteException, whose android.jar stub cannot load on the JVM
        val result =
            ImsRegistrationCheck.resolve(
                listOf(
                    Step("bad-arg") { throw IllegalArgumentException("invalid subId") },
                    Step("works") { true },
                ),
            )
        assertEquals(true, result)
    }

    @Test
    fun `returns unknown for an empty chain`() {
        assertNull(ImsRegistrationCheck.resolve(emptyList()))
    }

    @Test
    fun `unrelated exceptions are not swallowed`() {
        try {
            ImsRegistrationCheck.resolve(listOf(Step("boom") { throw IllegalStateException("shizuku dead") }))
            throw AssertionError("expected IllegalStateException")
        } catch (_: IllegalStateException) {
        }
    }

    @Test
    fun `invokeIntToBoolean calls a matching int method`() {
        val telephony = FakeTelephony()
        assertEquals(true, ImsRegistrationCheck.invokeIntToBoolean(telephony, "isImsRegistered", 1))
        assertEquals(false, ImsRegistrationCheck.invokeIntToBoolean(telephony, "isImsRegistered", 2))
        assertEquals(listOf("isImsRegistered(1)", "isImsRegistered(2)"), telephony.calls)
    }

    @Test
    fun `invokeIntToBoolean returns null when the method is missing or has another signature`() {
        val telephony = FakeTelephony()
        assertNull(ImsRegistrationCheck.invokeIntToBoolean(telephony, "doesNotExist", 1))
        // isImsRegisteredForSubscriber(long) does not match the (int) signature
        assertNull(ImsRegistrationCheck.invokeIntToBoolean(telephony, "isImsRegisteredForSubscriber", 1))
    }

    @Test
    fun `invocation errors from reflection fall through the chain`() {
        val telephony = FakeTelephony()
        var failure: Throwable? = null
        val result =
            ImsRegistrationCheck.resolve(
                listOf(
                    Step("failing") { ImsRegistrationCheck.invokeIntToBoolean(telephony, "failing", 1) },
                    Step("subId") { ImsRegistrationCheck.invokeIntToBoolean(telephony, "isImsRegistered", 1) },
                ),
            ) { _, e -> failure = e }
        assertTrue(result!!)
        assertTrue(failure is java.lang.reflect.InvocationTargetException)
        assertFalse(telephony.calls.isEmpty())
    }
}
