package com.example.mediqr.publiccard

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Public card lookup rules: what counts as a card id, and the guarantee that a
 * server response carrying extra/private fields can't put them on the screen.
 */
class PublicCardDataTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `a canonical card id is accepted`() {
        assertTrue(isValidCardId("d867e871-73b7-4658-b5d9-011d462bc8cf"))
    }

    @Test
    fun `card id check tolerates surrounding whitespace`() {
        assertTrue(isValidCardId("  d867e871-73b7-4658-b5d9-011d462bc8cf\n"))
    }

    @Test
    fun `uppercase card ids are accepted`() {
        assertTrue(isValidCardId("D867E871-73B7-4658-B5D9-011D462BC8CF"))
    }

    @Test
    fun `malformed ids are rejected before any network call`() {
        val invalid = listOf(
            "",
            "not-a-uuid",
            "12345",
            "d867e871-73b7-4658-b5d9",              // truncated
            "d867e871-73b7-4658-b5d9-011d462bc8cf0", // wrong tail
            "../../etc/passid",                      // path-ish junk
            "d867e87173b74658b5d9011d462bc8cf",      // missing hyphens
        )
        invalid.forEach { assertFalse("expected reject: $it", isValidCardId(it)) }
    }

    @Test
    fun `private account fields in a server response are dropped on decode`() {
        val payload = """
            {
              "full_name": "Sam Scanner",
              "blood_group": "O+",
              "allergies": "Penicillin",
              "id": "auth-uid-must-not-surface",
              "email": "sam@example.com",
              "created_at": "2026-09-28T00:00:00Z",
              "card_id": "also-whitelisted-away-here"
            }
        """.trimIndent()

        val card = json.decodeFromString<PublicMedicalCard>(payload)

        assertEquals("Sam Scanner", card.fullName)
        assertEquals("O+", card.bloodGroup)
        assertEquals("Penicillin", card.allergies)

        // What the screen renders is what the model re-serializes: no email,
        // no ids, no timestamps can survive the round trip.
        val rendered = json.encodeToString(PublicMedicalCard.serializer(), card)
        assertFalse(rendered.contains("email"))
        assertFalse(rendered.contains("auth-uid"))
        assertFalse(rendered.contains("created_at"))
        assertFalse(rendered.contains("card_id"))
    }
}
