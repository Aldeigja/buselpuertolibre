package es.elpuertobus.app.locale

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

val LocalLanguageTag = compositionLocalOf { "en" }

@Composable
fun appString(@StringRes id: Int, vararg formatArgs: Any): String {
    val tag = LocalLanguageTag.current
    val base = LocalContext.current.applicationContext
    val resources = remember(base, tag) { AppLocale.resourcesFor(base, tag) }
    return text(resources, id, formatArgs)
}

private fun text(resources: Resources, id: Int, formatArgs: Array<out Any>): String =
    if (formatArgs.isEmpty()) {
        resources.getString(id)
    } else {
        resources.getString(id, *formatArgs)
    }
