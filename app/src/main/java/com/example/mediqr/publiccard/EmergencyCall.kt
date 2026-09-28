package com.example.mediqr.publiccard

/**
 * Phone-number rules for the emergency Call action (Phase 6).
 *
 * Pure functions (no Android imports) so the behavior is unit-testable. A
 * number is callable when it consists of digits with an optional leading `+`
 * and common formatting separators, holding 7-15 digits (the E.164 range).
 * Anything else - letters, words, extra `+` signs, too short or too long - is
 * rejected BEFORE any phone intent is created, so the card never offers a
 * Call button that could not dial.
 */

/** Minimum meaningful subscriber number length. */
private const val MIN_DIGITS = 7

/** E.164 maximum number of digits. */
private const val MAX_DIGITS = 15

/** Digits with one optional leading `+` and space/paren/dash/dot separators. */
private val PHONE_FORMAT_REGEX = Regex("""^\+?[0-9 ().\-]+$""")

/**
 * Returns a dial-ready representation of [raw] (digits plus a leading `+`
 * when the original had one), or null when the number is missing or invalid.
 */
internal fun normalizePhoneNumber(raw: String?): String? {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    if (!PHONE_FORMAT_REGEX.matches(trimmed)) return null
    val digitCount = trimmed.count { it.isDigit() }
    if (digitCount < MIN_DIGITS || digitCount > MAX_DIGITS) return null
    return buildString {
        if (trimmed.startsWith('+')) append('+')
        for (ch in trimmed) if (ch.isDigit()) append(ch)
    }
}

/** True when [raw] can be handed to the dialer (see [normalizePhoneNumber]). */
internal fun isValidPhoneNumber(raw: String?): Boolean = normalizePhoneNumber(raw) != null
