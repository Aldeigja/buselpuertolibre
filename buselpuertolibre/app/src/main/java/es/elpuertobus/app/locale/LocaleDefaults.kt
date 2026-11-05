package es.elpuertobus.app.locale

object LocaleDefaults {
    fun effectiveTag(storedTag: String?, systemLanguage: String): String {
        storedTag?.let { return it }
        return if (systemLanguage.equals("es", ignoreCase = true)) "es" else "en"
    }
}
