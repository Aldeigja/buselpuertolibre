package es.elpuertobus.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import es.elpuertobus.app.R
import es.elpuertobus.app.locale.appString
import es.elpuertobus.app.ui.theme.TransitColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutBottomSheet(
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = TransitColors.Slate900,
        dragHandle = null,
    ) {
        AboutSheetBody(Modifier.padding(bottom = 24.dp))
    }
}

@Composable
private fun AboutSheetBody(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val version =
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        } catch (_: Exception) {
            ""
        }
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
    ) {
        Text(
            text = appString(R.string.about_title),
            style = MaterialTheme.typography.titleLarge,
            color = TransitColors.Slate900,
        )
        Spacer(modifier = Modifier.height(12.dp))
        BodyParagraph(R.string.about_intro)
        Spacer(modifier = Modifier.height(16.dp))

        SectionHeader(R.string.about_section_what_you_can_do)
        Bullet(R.string.about_what_line1)
        Bullet(R.string.about_what_line2)
        Bullet(R.string.about_what_line3)
        Bullet(R.string.about_what_line4)
        Bullet(R.string.about_what_line5)
        Spacer(modifier = Modifier.height(16.dp))

        SectionHeader(R.string.about_section_how_to_read)
        Bullet(R.string.about_how_line1)
        Bullet(R.string.about_how_line2)
        Bullet(R.string.about_how_line3)
        Bullet(R.string.about_how_line4)
        Bullet(R.string.about_how_line5)
        Bullet(R.string.about_how_line6)
        Spacer(modifier = Modifier.height(16.dp))

        SectionHeader(R.string.about_section_limits)
        Bullet(R.string.about_limits_line1)
        Bullet(R.string.about_limits_line2)
        Spacer(modifier = Modifier.height(16.dp))

        SectionHeader(R.string.about_section_sources)
        Bullet(R.string.about_sources_line1)
        Bullet(R.string.about_sources_line2)
        Bullet(R.string.about_sources_line3)
        Bullet(R.string.about_sources_line4)
        Spacer(modifier = Modifier.height(16.dp))

        SectionHeader(R.string.about_section_version)
        Text(
            text = appString(R.string.about_version_line, version),
            style = MaterialTheme.typography.bodyMedium,
            color = TransitColors.Slate600,
        )
        Spacer(modifier = Modifier.height(16.dp))

        SectionHeader(R.string.about_section_disclaimer)
        BodyParagraph(R.string.about_disclaimer_para1)
        Spacer(modifier = Modifier.height(8.dp))
        BodyParagraph(R.string.about_disclaimer_para2)
        Spacer(modifier = Modifier.height(8.dp))
        BodyParagraph(R.string.about_disclaimer_para3)
        Spacer(modifier = Modifier.height(8.dp))
        BodyParagraph(R.string.about_disclaimer_para4)
        Spacer(modifier = Modifier.height(16.dp))

        SectionHeader(R.string.about_section_licensing)
        Bullet(R.string.about_license_line1)
        Bullet(R.string.about_license_line2)
        Bullet(R.string.about_license_line3)
        Bullet(R.string.about_license_line4)
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SectionHeader(resId: Int) {
    Text(
        text = appString(resId),
        style = MaterialTheme.typography.titleMedium,
        color = TransitColors.Slate900,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun Bullet(resId: Int) {
    Text(
        text = linkedText("• ${appString(resId)}"),
        style = MaterialTheme.typography.bodyMedium,
        color = TransitColors.Slate600,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun BodyParagraph(resId: Int) {
    Text(
        text = linkedText(appString(resId)),
        style = MaterialTheme.typography.bodyMedium,
        color = TransitColors.Slate600,
    )
}

private val WebAddress = Regex("""https://\S+""")
private val LinkInk = Color(0xFF1E3A8A)

private fun linkedText(source: String) =
    buildAnnotatedString {
        var cursor = 0
        for (match in WebAddress.findAll(source)) {
            append(source.substring(cursor, match.range.first))
            val raw = match.value
            val address = raw.trimEnd('.', ',', ';', ':', ')')
            val trailing = raw.substring(address.length)
            if (address.isNotEmpty()) {
                withLink(
                    LinkAnnotation.Url(
                        url = address,
                        styles =
                            TextLinkStyles(
                                style =
                                    SpanStyle(
                                        color = LinkInk,
                                        textDecoration = TextDecoration.Underline,
                                    ),
                            ),
                    ),
                ) {
                    append(address)
                }
            }
            append(trailing)
            cursor = match.range.last + 1
        }
        if (cursor < source.length) {
            append(source.substring(cursor))
        }
    }
