package com.github.harinivasganapathy.idiff.toolWindow

import com.github.harinivasganapathy.idiff.FileComparatorPanel
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.content.ContentFactory
import com.github.harinivasganapathy.idiff.MyBundle
import com.github.harinivasganapathy.idiff.services.MyProjectService
import com.intellij.ui.content.Content
import javax.swing.JButton


class MyToolWindowFactory : ToolWindowFactory {

    init {
        thisLogger().warn("Don't forget to remove all non-needed sample code files with their corresponding registration entries in `plugin.xml`.")
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val fileComparatorPanel = FileComparatorPanel(project)
        val contentFactory = ContentFactory.getInstance()
        val content: Content = contentFactory.createContent(fileComparatorPanel, "", false)
        toolWindow.contentManager.addContent(content)
    }

    override fun isApplicable(project: Project): Boolean {
        return true // Make the tool window available for all projects
    }
}
