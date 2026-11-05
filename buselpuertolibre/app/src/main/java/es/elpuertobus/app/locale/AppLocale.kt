package es.elpuertobus.app.locale

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
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

    fun applyStored() {
        // A framework locale restarts the activity and the map goes black.
        // Drop any locale stored by an older build. Text uses preferences instead.
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
    }

    fun save(context: Context, tag: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, tag)
            .commit()
    }

    fun resourcesFor(context: Context, tag: String): Resources {
        val locale = Locale.forLanguageTag(tag)
        val config = Configuration(context.resources.configuration)
        config.setLocales(LocaleList(locale))
        config.setLayoutDirection(locale)
        val base = context.applicationContext ?: context
        return base.createConfigurationContext(config).resources
    }
}
