package com.maquis.caisse.core.activation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivationCodeTest {

    @Test
    fun masterKeyMatchesGestionApp() {
        assertEquals("ARIS-2026-NEXA-5363", ActivationService.MASTER_KEY)
    }

    @Test
    fun normalizeIgnoresCaseAndSpaces() {
        fun normalize(code: String) = code.filterNot { it.isWhitespace() }.uppercase()
        assertEquals(
            normalize(ActivationService.MASTER_KEY),
            normalize(" aris-2026-nexa-5363 "),
        )
        assertTrue(normalize("ARIS-2026-NEXA-5363") == normalize(ActivationService.MASTER_KEY))
        assertFalse(normalize("ARIS-1234-NEXA-5363") == normalize(ActivationService.MASTER_KEY))
    }
}
