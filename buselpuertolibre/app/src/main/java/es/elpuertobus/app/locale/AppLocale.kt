package es.elpuertobus.app.locale

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

object AppLocale {
    private const val PREFS = "app_prefs"
    private const val KEY = "locale_tag"

    fun storedTag(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY, null)
        return LocaleDefaults.effectiveTag(stored, Locale.getDefault().language)
    }

    fun currentTag(context: Context): String = storedTag(context)

    fun applyStored(context: Context) {
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(storedTag(context)),
        )
    }

    fun save(context: Context, tag: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, tag)
            .apply()
    }

    fun wrap(context: Context, tag: String): Context {
        val locale = Locale.forLanguageTag(tag)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }
}
