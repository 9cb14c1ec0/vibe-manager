package com.oss.vibemanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.SyntaxLanguage
import dev.snipme.highlights.model.SyntaxThemes
import dev.snipme.kodeview.view.CodeTextView
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.Button
import io.github.composefluent.component.Text

private data class FileDiffSection(
    val filename: String,
    val content: String,
)

private fun parseDiff(diffText: String): List<FileDiffSection> {
    val sections = mutableListOf<FileDiffSection>()
    val lines = diffText.lines()
    var currentFile = ""
    val currentContent = StringBuilder()

    for (line in lines) {
        if (line.startsWith("diff --git")) {
            if (currentFile.isNotEmpty() && currentContent.isNotBlank()) {
                sections.add(FileDiffSection(currentFile, currentContent.toString().trimEnd()))
            }
            // Extract filename from "diff --git a/path/to/file b/path/to/file"
            currentFile = line.substringAfterLast(" b/")
            currentContent.clear()
        } else {
            currentContent.appendLine(line)
        }
    }
    if (currentFile.isNotEmpty() && currentContent.isNotBlank()) {
        sections.add(FileDiffSection(currentFile, currentContent.toString().trimEnd()))
    }

    return sections
}

private fun detectLanguage(filename: String): SyntaxLanguage {
    val ext = filename.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "kt", "kts" -> SyntaxLanguage.KOTLIN
        "java" -> SyntaxLanguage.JAVA
        "py" -> SyntaxLanguage.PYTHON
        "js", "jsx" -> SyntaxLanguage.JAVASCRIPT
        "ts", "tsx" -> SyntaxLanguage.TYPESCRIPT
        "rs" -> SyntaxLanguage.RUST
        "go" -> SyntaxLanguage.GO
        "swift" -> SyntaxLanguage.SWIFT
        "c", "h" -> SyntaxLanguage.C
        "cpp", "hpp", "cc", "cxx" -> SyntaxLanguage.CPP
        "cs" -> SyntaxLanguage.CSHARP
        "rb" -> SyntaxLanguage.RUBY
        "php" -> SyntaxLanguage.PHP
        "dart" -> SyntaxLanguage.DART
        "sh", "bash", "zsh" -> SyntaxLanguage.SHELL
        "pl", "pm" -> SyntaxLanguage.PERL
        "coffee" -> SyntaxLanguage.COFFEESCRIPT
        else -> SyntaxLanguage.DEFAULT
    }
}

@Composable
fun DiffPanel(
    diffText: String,
    onClose: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fileSections = remember(diffText) { parseDiff(diffText) }
    val additions = remember(diffText) {
        diffText.lines().count { it.startsWith("+") && !it.startsWith("+++") }
    }
    val deletions = remember(diffText) {
        diffText.lines().count { it.startsWith("-") && !it.startsWith("---") }
    }

    Column(modifier = modifier.clip(RoundedCornerShape(6.dp))) {
        // Header bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(FluentTheme.colors.subtleFill.secondary)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Uncommitted Changes",
                    fontSize = 13.sp,
                    color = FluentTheme.colors.text.text.primary,
                )
                if (additions > 0 || deletions > 0) {
                    Text("+$additions", fontSize = 12.sp, color = Color(0xFF6CCB5F))
                    Text("−$deletions", fontSize = 12.sp, color = Color(0xFFFF6B68))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = onRefresh) { Text("Refresh") }
                Button(onClick = onClose) { Text("Close") }
            }
        }

        // Diff content
        if (fileSections.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .background(Color(0xFF1E1E1E)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No changes found",
                    fontSize = 13.sp,
                    color = Color(0xFF888888),
                )
            }
        } else {
            val verticalScrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .background(Color(0xFF1E1E1E))
                    .verticalScroll(verticalScrollState),
            ) {
                for ((index, section) in fileSections.withIndex()) {
                    DiffFileSectionView(section)
                    if (index < fileSections.lastIndex) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color(0xFF3E3E3E))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiffFileSectionView(section: FileDiffSection) {
    // File header
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF2D2D2D))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            section.filename,
            fontSize = 12.sp,
            color = Color(0xFFCCCCCC),
        )
    }

    // Code content with KodeView syntax highlighting
    val language = remember(section.filename) { detectLanguage(section.filename) }
    val highlights = remember(section.content, language) {
        Highlights.Builder()
            .code(section.content)
            .theme(SyntaxThemes.darcula())
            .language(language)
            .build()
    }

    val horizontalScrollState = rememberScrollState()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(horizontalScrollState)
            .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
    ) {
        CodeTextView(highlights = highlights)
    }
}
