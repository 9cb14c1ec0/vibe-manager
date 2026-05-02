package com.oss.vibemanager.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.m3.Markdown
import io.github.composefluent.FluentTheme
import io.github.composefluent.background.Layer
import io.github.composefluent.component.Text

@Composable
fun PlanCard(
    input: String,
    modifier: Modifier = Modifier,
) {
    val planText = remember(input) { extractPlan(input) }

    Layer(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Plan",
                fontSize = 14.sp,
                color = FluentTheme.colors.text.accent.primary,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            if (planText != null) {
                Markdown(content = planText)
            } else {
                Text(
                    text = input,
                    fontSize = 12.sp,
                    color = FluentTheme.colors.text.text.secondary,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}
