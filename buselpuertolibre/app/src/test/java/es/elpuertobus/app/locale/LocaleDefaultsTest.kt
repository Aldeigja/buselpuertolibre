package es.elpuertobus.app.locale

import org.junit.Assert.assertEquals
import org.junit.Test

class LocaleDefaultsTest {
    @Test
    fun storedChoiceWins() {
        assertEquals("en", LocaleDefaults.effectiveTag("en", "es"))
    }

    @Test
    fun spanishSystemDefaultsToSpanish() {
        assertEquals("es", LocaleDefaults.effectiveTag(null, "es"))
    }

    @Test
    fun nonSpanishSystemDefaultsToEnglish() {
        assertEquals("en", LocaleDefaults.effectiveTag(null, "en"))
        assertEquals("en", LocaleDefaults.effectiveTag(null, "de"))
    }
}
