package com.oss.vibemanager.ui.components

import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import io.github.composefluent.FluentTheme

@Composable
fun FluentMarkdown(
    content: String,
    modifier: Modifier = Modifier,
) {
    val textColor = FluentTheme.colors.text.text.primary
    val colors = markdownColor(
        text = textColor,
        codeBackground = FluentTheme.colors.background.layer.default,
        inlineCodeBackground = FluentTheme.colors.background.layer.default,
        dividerColor = FluentTheme.colors.stroke.divider.default,
        tableBackground = FluentTheme.colors.background.layer.default,
    )
    CompositionLocalProvider(LocalContentColor provides textColor) {
        Markdown(
            content = content,
            colors = colors,
            modifier = modifier,
        )
    }
}
