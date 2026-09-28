package com.example.mediqr.publiccard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 6 - emergency contact Call action.
 *
 * These tests pin down the phone-number rules that gate the Call button and
 * the ACTION_DIAL intent: valid numbers normalize to a dial-ready form,
 * anything unusable (words, letters, wrong length, misplaced '+') is rejected
 * so the UI never offers a call that could not be placed.
 */
class EmergencyCallTest {

    // --- Valid numbers -> dial-ready form ---------------------------------

    @Test
    fun `normalizes an international number with separators`() {
        assertEquals("+15551234567", normalizePhoneNumber("+1 555-123-4567"))
    }

    @Test
    fun `normalizes a national number with parentheses`() {
        assertEquals("5551234567", normalizePhoneNumber("(555) 123-4567"))
    }

    @Test
    fun `keeps plain digits untouched`() {
        assertEquals("02079460958", normalizePhoneNumber("020 7946 0958"))
    }

    @Test
    fun `accepts exactly 7 digits (minimum) and 15 digits (maximum)`() {
        assertEquals("1234567", normalizePhoneNumber("1234567"))
        assertEquals("123456789012345", normalizePhoneNumber("123456789012345"))
        assertTrue(isValidPhoneNumber("5551234"))
        assertTrue(isValidPhoneNumber("+555123456789012"))
    }

    @Test
    fun `trims surrounding whitespace before validating`() {
        assertEquals("+15551234567", normalizePhoneNumber("  +1 555 123 4567  "))
    }

    // --- Invalid numbers -> rejected --------------------------------------

    @Test
    fun `rejects missing or blank input`() {
        assertNull(normalizePhoneNumber(null))
        assertNull(normalizePhoneNumber(""))
        assertNull(normalizePhoneNumber("   "))
        assertFalse(isValidPhoneNumber(null))
        assertFalse(isValidPhoneNumber(" "))
    }

    @Test
    fun `rejects words and letters`() {
        assertNull(normalizePhoneNumber("call me maybe"))
        assertNull(normalizePhoneNumber("555-CALL-NOW"))
        assertNull(normalizePhoneNumber("+1-555-INFO"))
        assertFalse(isValidPhoneNumber("not a phone"))
    }

    @Test
    fun `rejects numbers that are too short or too long`() {
        assertNull(normalizePhoneNumber("123456"))           // 6 digits
        assertNull(normalizePhoneNumber("1234567890123456")) // 16 digits
        assertFalse(isValidPhoneNumber("9111"))
    }

    @Test
    fun `rejects misplaced or repeated plus signs`() {
        assertNull(normalizePhoneNumber("555+1234567")) // '+' not leading
        assertNull(normalizePhoneNumber("+1+555123456")) // second '+'
        assertFalse(isValidPhoneNumber("++15551234567"))
    }

    @Test
    fun `rejects unexpected punctuation`() {
        assertNull(normalizePhoneNumber("555;123;4567"))
        assertNull(normalizePhoneNumber("tel:5551234567"))
        assertNull(normalizePhoneNumber("555.123.4567 ext 9"))
    }
}
