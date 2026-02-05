package com.oss.vibemanager.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.composefluent.component.Icon
import io.github.composefluent.component.Text
import io.github.composefluent.icons.Icons
import io.github.composefluent.icons.regular.BranchFork

@Composable
fun GitInfoBar(
    branch: String,
    isClean: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Regular.BranchFork,
            contentDescription = "Branch",
        )
        Text(branch)
        val statusText = if (isClean) "Clean" else "Modified"
        val statusColor = if (isClean) Color(0xFF107C10) else Color(0xFFCA5010)
        Text(statusText, color = statusColor)
    }
}
