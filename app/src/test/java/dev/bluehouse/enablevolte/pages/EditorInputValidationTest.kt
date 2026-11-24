package dev.bluehouse.enablevolte.pages

import dev.bluehouse.enablevolte.components.ValueType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@Suppress("unused")
object EditorTestKeys {
    @JvmField
    val KEY_SAMPLE_INT = "sample_int"

    @JvmField
    val KEY_SAMPLE_INT_ARRAY = "sample_int_array"
}

class EditorInputValidationTest {
    private val intField = EditorTestKeys::class.java.getDeclaredField("KEY_SAMPLE_INT")
    private val intArrayField = EditorTestKeys::class.java.getDeclaredField("KEY_SAMPLE_INT_ARRAY")

    @Test
    fun `valid input accepts parseable numbers`() {
        assertTrue(isValidTypedInput(ValueType.Int, "42"))
        assertTrue(isValidTypedInput(ValueType.Long, "99999999999"))
    }

    @Test
    fun `valid input rejects partial or out of range numbers`() {
        assertFalse(isValidTypedInput(ValueType.Int, ""))
        assertFalse(isValidTypedInput(ValueType.Int, "-"))
        assertFalse(isValidTypedInput(ValueType.Int, "99999999999"))
        assertFalse(isValidTypedInput(ValueType.Long, "1.5"))
    }

    @Test
    fun `valid input accepts empty string but not null for strings`() {
        assertTrue(isValidTypedInput(ValueType.String, ""))
        assertFalse(isValidTypedInput(ValueType.String, null))
    }

    @Test
    fun `blank array item covers null empty and whitespace`() {
        assertTrue(isBlankArrayItem(null))
        assertTrue(isBlankArrayItem(""))
        assertTrue(isBlankArrayItem("  "))
        assertFalse(isBlankArrayItem("0"))
    }

    @Test
    fun `array save drops blank items`() {
        assertEquals(listOf(1, 3), parseArrayItemsForSave(ValueType.Int, listOf("1", null, "", "3")))
        assertEquals(listOf(5L), parseArrayItemsForSave(ValueType.Long, listOf(null, "5")))
    }

    @Test
    fun `array save of only blank items yields empty list`() {
        assertEquals(emptyList<Any>(), parseArrayItemsForSave(ValueType.Int, listOf(null, " ")))
    }

    @Test
    fun `array save rejects any non blank invalid item`() {
        assertNull(parseArrayItemsForSave(ValueType.Int, listOf("1", "-", "3")))
        assertNull(parseArrayItemsForSave(ValueType.Int, listOf("1", "99999999999")))
    }

    @Test
    fun `single row is savable only when its value parses`() {
        assertTrue(DataRow(intField, "7").isSavable())
        assertFalse(DataRow(intField, "").isSavable())
        assertFalse(DataRow(intField, "-").isSavable())
    }

    @Test
    fun `list row is savable with blank items but not with invalid ones`() {
        assertTrue(ListDataRow(intArrayField, listOf("1", null, "")).isSavable())
        assertFalse(ListDataRow(intArrayField, listOf("1", "x")).isSavable())
    }
}
