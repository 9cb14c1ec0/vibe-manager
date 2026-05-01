package com.oss.vibemanager.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.Button
import io.github.composefluent.component.Text
import com.oss.vibemanager.model.PendingPermission

@Composable
fun PermissionBanner(
    permission: PendingPermission,
    onRespond: (optionId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 2.dp,
                color = Color(0xFFFF8C00), // orange for attention
                shape = RoundedCornerShape(8.dp),
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Permission Required",
            fontSize = 14.sp,
            color = Color(0xFFFF8C00),
        )

        Text(
            permission.toolName,
            fontSize = 16.sp,
            color = FluentTheme.colors.text.text.primary,
        )

        if (permission.inputSummary.isNotBlank()) {
            Text(
                permission.inputSummary,
                fontSize = 12.sp,
                color = FluentTheme.colors.text.text.secondary,
                maxLines = 6,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Show allow options first, then reject
            val allowOptions = permission.options.filter { it.kind.startsWith("allow") }
            val rejectOptions = permission.options.filter { it.kind.startsWith("reject") }

            for (option in allowOptions) {
                Button(onClick = { onRespond(option.id) }) {
                    Text(option.name, fontSize = 12.sp)
                }
            }
            for (option in rejectOptions) {
                Button(onClick = { onRespond(option.id) }) {
                    Text(option.name, fontSize = 12.sp)
                }
            }
        }
    }
}
