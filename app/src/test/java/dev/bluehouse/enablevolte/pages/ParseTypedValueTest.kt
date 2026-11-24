package dev.bluehouse.enablevolte.pages

import dev.bluehouse.enablevolte.components.ValueType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParseTypedValueTest {
    @Test
    fun `int parses a plain number`() {
        assertEquals(2, parseTypedValue(ValueType.Int, "2"))
    }

    @Test
    fun `int parses a negative number`() {
        assertEquals(-1, parseTypedValue(ValueType.Int, "-1"))
    }

    @Test
    fun `int returns null for empty input instead of throwing`() {
        assertNull(parseTypedValue(ValueType.Int, ""))
    }

    @Test
    fun `int returns null for a lone minus sign`() {
        assertNull(parseTypedValue(ValueType.Int, "-"))
    }

    @Test
    fun `int returns null for non numeric input`() {
        assertNull(parseTypedValue(ValueType.Int, "abc"))
    }

    @Test
    fun `int returns null when the value overflows`() {
        assertNull(parseTypedValue(ValueType.Int, "99999999999"))
    }

    @Test
    fun `int array element type parses like int`() {
        assertEquals(6, parseTypedValue(ValueType.IntArray, "6"))
        assertNull(parseTypedValue(ValueType.IntArray, ""))
    }

    @Test
    fun `long parses values beyond int range`() {
        assertEquals(99999999999L, parseTypedValue(ValueType.Long, "99999999999"))
    }

    @Test
    fun `long returns null for empty input instead of throwing`() {
        assertNull(parseTypedValue(ValueType.Long, ""))
    }

    @Test
    fun `long array element type parses like long`() {
        assertEquals(7L, parseTypedValue(ValueType.LongArray, "7"))
        assertNull(parseTypedValue(ValueType.LongArray, "x"))
    }

    @Test
    fun `bool maps true and false and leaves null alone`() {
        assertEquals(true, parseTypedValue(ValueType.Bool, "true"))
        assertEquals(false, parseTypedValue(ValueType.Bool, "false"))
        assertNull(parseTypedValue(ValueType.Bool, null))
    }

    @Test
    fun `string passes through unchanged including empty`() {
        assertEquals("", parseTypedValue(ValueType.String, ""))
        assertEquals("abc", parseTypedValue(ValueType.StringArray, "abc"))
    }

    @Test
    fun `unknown type yields null`() {
        assertNull(parseTypedValue(ValueType.Unknown, "2"))
    }
}
