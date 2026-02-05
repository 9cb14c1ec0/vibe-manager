package com.oss.vibemanager.platform

import javax.swing.JFileChooser

fun chooseDirectory(): String? {
    val chooser = JFileChooser().apply {
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        dialogTitle = "Select Git Repository"
    }
    val result = chooser.showOpenDialog(null)
    return if (result == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile.absolutePath
    } else null
}
