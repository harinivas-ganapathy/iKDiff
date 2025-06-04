package com.github.harinivasganapathy.idiff

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
//import com.intellij.diff.impl.DiffWindow
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.ide.DataManager
import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import java.awt.BorderLayout
import java.awt.KeyboardFocusManager
import java.awt.event.ActionListener
import java.io.File
import java.io.FileInputStream
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import com.intellij.icons.AllIcons
import javax.swing.Icon
import com.intellij.openapi.util.IconLoader

object PluginIcons {
    @JvmField
    val PluginIcon = IconLoader.getIcon("/icons/pluginIcon.svg", PluginIcons::class.java)
}



class FileComparatorPanel1(private val project: Project) : JBPanel<FileComparatorPanel1>(BorderLayout()) {
    private val baselineDropdown = ComboBox<String>()
    private val regressionDropdown = ComboBox<String>()
    private val baselineButton = JButton("Baseline Button")
    private val regressionButton = JButton("Regression Button")
    private val previousButton = JButton("Previous")
    private val nextButton = JButton("Next")
    private val diffPanel = JPanel(BorderLayout()) // We will try to embed the diff here
    private val dropFileButton = JButton("Drop File")

    private var baselineFiles: List<VirtualFile> = emptyList()
    private var regressionFiles: List<VirtualFile> = emptyList()
    private var controlsPanel = JPanel()

    init {
        controlsPanel.add(JLabel("Baseline:"))
        controlsPanel.add(baselineDropdown)
        controlsPanel.add(baselineButton)
        controlsPanel.add(JLabel("Regression:"))
        controlsPanel.add(regressionDropdown)
        controlsPanel.add(regressionButton)
        controlsPanel.add(previousButton)
        controlsPanel.add(nextButton)
        controlsPanel.add(dropFileButton)

        add(controlsPanel, BorderLayout.NORTH)
        add(JBScrollPane(diffPanel), BorderLayout.CENTER)

        baselineButton.addActionListener { loadFiles("baseline", baselineDropdown) }
        regressionButton.addActionListener { loadFiles("regression", regressionDropdown) }

        baselineDropdown.addActionListener { updateDiff() }
        regressionDropdown.addActionListener { updateDiff() }

        previousButton.addActionListener { navigateFiles(-1) }
        nextButton.addActionListener { navigateFiles(1) }

        previousButton.isEnabled = false
        nextButton.isEnabled = false

//        dropFileButton.addActionListener { performSftpTransfer() }
    }

    private fun loadFiles(directoryName: String, dropdown: ComboBox<String>) {
        val descriptor = FileChooserDescriptor(false, true, false, false, false, false)
        val virtualFile = FileChooser.chooseFile(descriptor, project, null)
        virtualFile?.let {
            val directory = File(it.path)
            if (directory.exists() && directory.isDirectory) {
                val files = directory.listFiles()
                if (!files.isNullOrEmpty()) {
                    dropdown.removeAllItems()
                    val virtualFiles =
                        files.filter { it.isFile }.mapNotNull { LocalFileSystem.getInstance().findFileByIoFile(it) }
                    if (directoryName == "baseline") {
                        baselineFiles = virtualFiles
                    } else if (directoryName == "regression") {
                        regressionFiles = virtualFiles
                    }
                    dropdown.model = DefaultComboBoxModel(virtualFiles.map { it.name }.toTypedArray())
                    if (virtualFiles.isNotEmpty()) {
                        dropdown.selectedIndex = 0
                    }
                    updateNavigationButtonStates()
                    updateDiff()
                } else {
                    Messages.showInfoMessage("No files found in the '$directoryName' directory.", "Info")
                    if (directoryName == "baseline") baselineFiles = emptyList() else regressionFiles = emptyList()
                    dropdown.removeAllItems()
                    updateDiff()
                    updateNavigationButtonStates()
                }
            } else {
                Messages.showErrorDialog("Directory '$directoryName' not found in the selected location.", "Error")
                if (directoryName == "baseline") baselineFiles = emptyList() else regressionFiles = emptyList()
                dropdown.removeAllItems()
                updateDiff()
                updateNavigationButtonStates()
            }
        }
    }

    private fun updateDiff() {
        val baselineSelectedIndex = baselineDropdown.selectedIndex
        val regressionSelectedIndex = regressionDropdown.selectedIndex

        if (baselineSelectedIndex != -1 && regressionSelectedIndex != -1 &&
            baselineSelectedIndex < baselineFiles.size && regressionSelectedIndex < regressionFiles.size
        ) {
            val selectedBaselineFile = baselineFiles[baselineSelectedIndex]
            val selectedRegressionFile = regressionFiles[regressionSelectedIndex]
            val baselineFileName = selectedBaselineFile.name
            val regressionFileName = selectedRegressionFile.name

            val request = SimpleDiffRequest(
                "$baselineFileName (Baseline)",
                DiffContentFactory.getInstance().create(project, selectedBaselineFile),
                DiffContentFactory.getInstance().create(project, selectedRegressionFile),
                "$regressionFileName (Regression)",
                "Comparison"
            )
            // Attempt to show the diff, reusing the window if possible
            DiffManager.getInstance().showDiff(project, request)
        } else {
            // If no files are selected, clear the diff panel (though DiffManager might handle this)
            diffPanel.removeAll()
            diffPanel.repaint()
            diffPanel.revalidate()
        }
        updateNavigationButtonStates()
    }

    private fun navigateFiles(direction: Int) {
        val newBaselineIndex = baselineDropdown.selectedIndex + direction
        val newRegressionIndex = regressionDropdown.selectedIndex + direction

        if (newBaselineIndex in 0 until baselineFiles.size && newRegressionIndex in 0 until regressionFiles.size) {
            baselineDropdown.selectedIndex = newBaselineIndex
            regressionDropdown.selectedIndex = newRegressionIndex
            updateDiff()
        }
    }

    private fun updateNavigationButtonStates() {
        previousButton.isEnabled = baselineFiles.isNotEmpty() && baselineDropdown.selectedIndex > 0 &&
                regressionFiles.isNotEmpty() && regressionDropdown.selectedIndex > 0
        nextButton.isEnabled = baselineFiles.isNotEmpty() && baselineDropdown.selectedIndex < baselineFiles.size - 1 &&
                regressionFiles.isNotEmpty() && regressionDropdown.selectedIndex < regressionFiles.size - 1
    }

    private fun performSftpTransfer() {
        val projectView = ProjectView.getInstance(project)

        val selectedElement = projectView.currentProjectViewPane?.selectedPath?.lastPathComponent
        println("selected element ${selectedElement.toString()}")
        if (selectedElement != null && selectedElement is com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode) {
            println("Hello")
        }
        val tree = projectView.currentProjectViewPane?.tree
        val selectionPaths = tree?.selectionPaths
        val x = selectionPaths?.last()?.path
        println("x: $x")
        println("Selected list ${selectionPaths?.last()}")
        if (selectionPaths != null && selectionPaths.isNotEmpty()) {
            // Get the last selected path (assuming single selection or the last in multi-selection)
            val lastSelectionPath = selectionPaths.last()
            val userObject = lastSelectionPath.lastPathComponent
            println("user object ${userObject.toString()}")
            var selectedVirtualFile: VirtualFile? = null
            when (userObject) {
                is com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode -> {
                    selectedVirtualFile = userObject.virtualFile
                    println("1")
                }

                is com.intellij.ide.projectView.impl.nodes.PsiFileNode -> {
                    selectedVirtualFile = userObject.virtualFile?.parent // Get the parent directory
                    println("2")
                }

                is com.intellij.ide.projectView.impl.nodes.NamedLibraryElementNode -> {
                    // Handle library node selection if needed
                    println("3")
                }

                is com.intellij.ide.projectView.impl.nodes.ModuleGroupNode -> {
                    // Handle module node selection if needed
                    println("4")
                }
                is com.intellij.ide.util.treeView.AbstractTreeNode<*> -> {
                    println("5")
                    val dataContext = DataManager.getInstance().getDataContext(tree)
                    val selectedFiles = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext)
                    if (selectedFiles != null && selectedFiles.isNotEmpty()) {
                        selectedVirtualFile = selectedFiles.first()
                    }
                }
            }

             if   (selectedVirtualFile != null && selectedVirtualFile.isDirectory) {
                    val selectedDirectoryPath = selectedVirtualFile.path
                    val selectedDirectoryName = selectedVirtualFile.name

                    val remoteBaseDirectory = "/path/to/remote/base"
                    val fullRemoteDirectory = "$remoteBaseDirectory/$selectedDirectoryName"

                    JOptionPane.showMessageDialog(
                        controlsPanel,
                        "Transferring files from local: '$selectedDirectoryPath' to remote: '$fullRemoteDirectory'",
                        "SFTP Info",
                        JOptionPane.INFORMATION_MESSAGE
                    )

                    transferFilesViaSftp(selectedDirectoryPath, fullRemoteDirectory)
                }
                else {
                    JOptionPane.showMessageDialog(
                        controlsPanel,
                        "Please select a directory in the Project Explorer to transfer.",
                        "SFTP Info",
                        JOptionPane.INFORMATION_MESSAGE
                    )
                }
            }
        }



//    private fun performSftpTransfer() {
//        val currentProject = this.project // Access the project instance
//
//        if (currentProject == null || currentProject.isDisposed) {
//            JOptionPane.showMessageDialog(
//                controlsPanel,
//                "No project is currently open.",
//                "SFTP Info",
//                JOptionPane.INFORMATION_MESSAGE
//            )
//            return
//        }
//
//        // Ensure the project view is focused
//        val projectView = ProjectView.getInstance(currentProject)
//        val tree = projectView.currentProjectViewPane?.tree
//        val selectionPaths = tree?.selectionPaths
//
//        if (selectionPaths != null && selectionPaths.isNotEmpty()) {
//            // Get the last selected path (assuming single selection or the last in multi-selection)
//            val lastSelectionPath = selectionPaths.last()
//            val userObject = lastSelectionPath.lastPathComponent
//
//            var selectedVirtualFile: VirtualFile? = null
//            if (userObject is com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode) {
//                selectedVirtualFile = userObject.virtualFile
//            } else if (userObject is com.intellij.ide.projectView.impl.nodes.PsiFileNode) {
//                selectedVirtualFile = userObject.virtualFile?.parent // Get the parent directory
//            } else if (userObject is com.intellij.ide.projectView.impl.nodes.NamedLibraryElementNode) {
//                // Handle library node selection if needed
//            } else
////                if (userObject is com.intellij.ide.projectView.impl.nodes.ModuleNode) {
////                // Handle module node selection if needed
////            }
//
//            if (selectedVirtualFile != null && selectedVirtualFile.isDirectory) {
//                val selectedDirectoryPath = selectedVirtualFile.path
//                val selectedDirectoryName = selectedVirtualFile.name
//
//                val remoteBaseDirectory = "/path/to/remote/base"
//                val fullRemoteDirectory = "$remoteBaseDirectory/$selectedDirectoryName"
//
//                JOptionPane.showMessageDialog(
//                    controlsPanel,
//                    "Transferring files from local: '$selectedDirectoryPath' to remote: '$fullRemoteDirectory'",
//                    "SFTP Info",
//                    JOptionPane.INFORMATION_MESSAGE
//                )
//
//                transferFilesViaSftp(selectedDirectoryPath, fullRemoteDirectory)
//            } else {
//                JOptionPane.showMessageDialog(
//                    controlsPanel,
//                    "Please select a directory in the Project Explorer to transfer.",
//                    "SFTP Info",
//                    JOptionPane.INFORMATION_MESSAGE
//                )
//            }
//        } else {
//            JOptionPane.showMessageDialog(
//                controlsPanel,
//                "Please select a directory in the Project Explorer.",
//                "SFTP Info",
//                JOptionPane.INFORMATION_MESSAGE
//            )
//        }
//    }

    private fun transferFilesViaSftp(localDirectoryPath: String, remoteDir: String = "") {
        // SFTP connection details (replace with your actual details or a user input mechanism)
        val sftpHost = "your_sftp_host"
        val sftpPort = 22 // Default SFTP port
        val sftpUser = "your_username"
        val sftpPassword = "your_password"
        val remoteDirectory = "/path/to/remote/directory"

        val jsch = JSch()
        var session: com.jcraft.jsch.Session? = null
        var sftpChannel: ChannelSftp? = null

        try {
            session = jsch.getSession(sftpUser, sftpHost, sftpPort)
            session.setPassword(sftpPassword)

            // Warning: Disabling host key checking is insecure for production environments.
            // You should implement proper host key verification.
            val config = java.util.Properties()
            config["StrictHostKeyChecking"] = "no"
            session.setConfig(config)

            session.connect()

            sftpChannel = session.openChannel("sftp") as ChannelSftp
            sftpChannel.connect()

            val localDir = File(localDirectoryPath)
            localDir.listFiles()?.forEach { localFile ->
                if (localFile.isFile) {
                    val remoteFilePath = "$remoteDirectory/${localFile.name}"
                    sftpChannel.put(localFile.absolutePath, remoteFilePath)
                    println("Uploaded: ${localFile.name} to $remoteFilePath")
                }
            }

            JOptionPane.showMessageDialog(
                controlsPanel,
                "Files from '$localDirectoryPath' uploaded successfully via SFTP!",
                "SFTP Success",
                JOptionPane.INFORMATION_MESSAGE
            )

        } catch (e: Exception) {
            e.printStackTrace()
            JOptionPane.showMessageDialog(
                controlsPanel,
                "Error during SFTP transfer: ${e.message}",
                "SFTP Error",
                JOptionPane.ERROR_MESSAGE
            )
        } finally {
            sftpChannel?.disconnect()
            session?.disconnect()
        }
    }
}

class FileComparatorPanel(private val project: Project) : JBPanel<FileComparatorPanel>(BorderLayout()) {
    private val baselineDropdown = ComboBox<String>()
    private val regressionDropdown = ComboBox<String>()
    private val baselineButton = JButton("Baseline Button")
    private val regressionButton = JButton("Regression Button")
    private val previousButton = JButton("Previous", AllIcons.Actions.Back)
    private val nextButton = JButton("Next", AllIcons.Actions.Forward)
    private val diffPanel = JPanel(BorderLayout()) // We will try to embed the diff here
    private val dropFileButton = JButton("Drop File")

    private var baselineFiles: List<VirtualFile> = emptyList()
    private var regressionFiles: List<VirtualFile> = emptyList()
    private var controlsPanel = JPanel()

    init {
        controlsPanel.add(JLabel("Baseline:"))
        controlsPanel.add(baselineDropdown)
        controlsPanel.add(baselineButton)
        controlsPanel.add(JLabel("Regression:"))
        controlsPanel.add(regressionDropdown)
        controlsPanel.add(regressionButton)
        controlsPanel.add(previousButton)
        controlsPanel.add(nextButton)
        controlsPanel.add(dropFileButton)

        add(controlsPanel, BorderLayout.NORTH)
        add(JBScrollPane(diffPanel), BorderLayout.CENTER)

        baselineButton.addActionListener { loadFiles("baseline", baselineDropdown) }
        regressionButton.addActionListener { loadFiles("regression", regressionDropdown) }

        baselineDropdown.addActionListener { updateDiff() }
        regressionDropdown.addActionListener { updateDiff() }

        previousButton.addActionListener { navigateFiles(-1) }
        nextButton.addActionListener { navigateFiles(1) }

        previousButton.isEnabled = false
        nextButton.isEnabled = false

//        dropFileButton.addActionListener { performSftpTransfer() }
    }

    private fun loadFiles(directoryName: String, dropdown: ComboBox<String>) {
        val descriptor = FileChooserDescriptor(false, true, false, false, false, false)
        val virtualFile = FileChooser.chooseFile(descriptor, project, null)
        virtualFile?.let {
            val directory = File(it.path)
            if (directory.exists() && directory.isDirectory) {
                val files = directory.listFiles()
                if (!files.isNullOrEmpty()) {
                    dropdown.removeAllItems()
                    val virtualFiles =
                        files.filter { it.isFile }.mapNotNull { LocalFileSystem.getInstance().findFileByIoFile(it) }
                    if (directoryName == "baseline") {
                        baselineFiles = virtualFiles
                    } else if (directoryName == "regression") {
                        regressionFiles = virtualFiles
                    }
                    dropdown.model = DefaultComboBoxModel(virtualFiles.map { it.name }.toTypedArray())
                    if (virtualFiles.isNotEmpty()) {
                        dropdown.selectedIndex = 0
                    }
                    updateNavigationButtonStates()
                    updateDiff()
                } else {
                    Messages.showInfoMessage("No files found in the '$directoryName' directory.", "Info")
                    if (directoryName == "baseline") baselineFiles = emptyList() else regressionFiles = emptyList()
                    dropdown.removeAllItems()
                    updateDiff()
                    updateNavigationButtonStates()
                }
            } else {
                Messages.showErrorDialog("Directory '$directoryName' not found in the selected location.", "Error")
                if (directoryName == "baseline") baselineFiles = emptyList() else regressionFiles = emptyList()
                dropdown.removeAllItems()
                updateDiff()
                updateNavigationButtonStates()
            }
        }
    }

    private fun updateDiff() {
        // First, close all previously opened diff windows
        val fileEditorManager = FileEditorManager.getInstance(project)
        fileEditorManager.openFiles.forEach { fileEditorManager.closeFile(it) }

        val baselineSelectedIndex = baselineDropdown.selectedIndex
        val regressionSelectedIndex = regressionDropdown.selectedIndex

        if (baselineSelectedIndex != -1 && regressionSelectedIndex != -1 &&
            baselineSelectedIndex < baselineFiles.size && regressionSelectedIndex < regressionFiles.size
        ) {
            val selectedBaselineFile = baselineFiles[baselineSelectedIndex]
            val selectedRegressionFile = regressionFiles[regressionSelectedIndex]
            val baselineFileName = selectedBaselineFile.name
            val regressionFileName = selectedRegressionFile.name

            val request = SimpleDiffRequest(
                "$baselineFileName (Baseline)",
                DiffContentFactory.getInstance().create(project, selectedBaselineFile),
                DiffContentFactory.getInstance().create(project, selectedRegressionFile),
                "$regressionFileName (Regression)",
                "Comparison"
            )

            // Show the new diff
            DiffManager.getInstance().showDiff(project, request)
        } else {
            // If no files are selected, clear the diff panel
            diffPanel.removeAll()
            diffPanel.repaint()
            diffPanel.revalidate()
        }
        updateNavigationButtonStates()
    }



    private fun navigateFiles(direction: Int) {
        val newBaselineIndex = baselineDropdown.selectedIndex + direction
        val newRegressionIndex = regressionDropdown.selectedIndex + direction

        if (newBaselineIndex in 0 until baselineFiles.size && newRegressionIndex in 0 until regressionFiles.size) {
            baselineDropdown.selectedIndex = newBaselineIndex
            regressionDropdown.selectedIndex = newRegressionIndex
            updateDiff()
        }
    }

    private fun updateNavigationButtonStates() {
        previousButton.isEnabled = baselineFiles.isNotEmpty() && baselineDropdown.selectedIndex > 0 &&
                regressionFiles.isNotEmpty() && regressionDropdown.selectedIndex > 0
        nextButton.isEnabled = baselineFiles.isNotEmpty() && baselineDropdown.selectedIndex < baselineFiles.size - 1 &&
                regressionFiles.isNotEmpty() && regressionDropdown.selectedIndex < regressionFiles.size - 1
    }

    private fun performSftpTransfer() {
        val projectView = ProjectView.getInstance(project)

        val selectedElement = projectView.currentProjectViewPane?.selectedPath?.lastPathComponent
        println("selected element ${selectedElement.toString()}")
        if (selectedElement != null && selectedElement is com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode) {
            println("Hello")
        }
        val tree = projectView.currentProjectViewPane?.tree
        val selectionPaths = tree?.selectionPaths
        val x = selectionPaths?.last()?.path
        println("x: $x")
        println("Selected list ${selectionPaths?.last()}")
        if (selectionPaths != null && selectionPaths.isNotEmpty()) {
            // Get the last selected path (assuming single selection or the last in multi-selection)
            val lastSelectionPath = selectionPaths.last()
            val userObject = lastSelectionPath.lastPathComponent
            println("user object ${userObject.toString()}")
            var selectedVirtualFile: VirtualFile? = null
            when (userObject) {
                is com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode -> {
                    selectedVirtualFile = userObject.virtualFile
                    println("1")
                }

                is com.intellij.ide.projectView.impl.nodes.PsiFileNode -> {
                    selectedVirtualFile = userObject.virtualFile?.parent // Get the parent directory
                    println("2")
                }

                is com.intellij.ide.projectView.impl.nodes.NamedLibraryElementNode -> {
                    // Handle library node selection if needed
                    println("3")
                }

                is com.intellij.ide.projectView.impl.nodes.ModuleGroupNode -> {
                    // Handle module node selection if needed
                    println("4")
                }
                is com.intellij.ide.util.treeView.AbstractTreeNode<*> -> {
                    println("5")
                    val dataContext = DataManager.getInstance().getDataContext(tree)
                    val selectedFiles = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext)
                    if (selectedFiles != null && selectedFiles.isNotEmpty()) {
                        selectedVirtualFile = selectedFiles.first()
                    }
                }
            }

            if   (selectedVirtualFile != null && selectedVirtualFile.isDirectory) {
                val selectedDirectoryPath = selectedVirtualFile.path
                val selectedDirectoryName = selectedVirtualFile.name

                val remoteBaseDirectory = "/path/to/remote/base"
                val fullRemoteDirectory = "$remoteBaseDirectory/$selectedDirectoryName"

                JOptionPane.showMessageDialog(
                    controlsPanel,
                    "Transferring files from local: '$selectedDirectoryPath' to remote: '$fullRemoteDirectory'",
                    "SFTP Info",
                    JOptionPane.INFORMATION_MESSAGE
                )

                transferFilesViaSftp(selectedDirectoryPath, fullRemoteDirectory)
            }
            else {
                JOptionPane.showMessageDialog(
                    controlsPanel,
                    "Please select a directory in the Project Explorer to transfer.",
                    "SFTP Info",
                    JOptionPane.INFORMATION_MESSAGE
                )
            }
        }
    }



//    private fun performSftpTransfer() {
//        val currentProject = this.project // Access the project instance
//
//        if (currentProject == null || currentProject.isDisposed) {
//            JOptionPane.showMessageDialog(
//                controlsPanel,
//                "No project is currently open.",
//                "SFTP Info",
//                JOptionPane.INFORMATION_MESSAGE
//            )
//            return
//        }
//
//        // Ensure the project view is focused
//        val projectView = ProjectView.getInstance(currentProject)
//        val tree = projectView.currentProjectViewPane?.tree
//        val selectionPaths = tree?.selectionPaths
//
//        if (selectionPaths != null && selectionPaths.isNotEmpty()) {
//            // Get the last selected path (assuming single selection or the last in multi-selection)
//            val lastSelectionPath = selectionPaths.last()
//            val userObject = lastSelectionPath.lastPathComponent
//
//            var selectedVirtualFile: VirtualFile? = null
//            if (userObject is com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode) {
//                selectedVirtualFile = userObject.virtualFile
//            } else if (userObject is com.intellij.ide.projectView.impl.nodes.PsiFileNode) {
//                selectedVirtualFile = userObject.virtualFile?.parent // Get the parent directory
//            } else if (userObject is com.intellij.ide.projectView.impl.nodes.NamedLibraryElementNode) {
//                // Handle library node selection if needed
//            } else
////                if (userObject is com.intellij.ide.projectView.impl.nodes.ModuleNode) {
////                // Handle module node selection if needed
////            }
//
//            if (selectedVirtualFile != null && selectedVirtualFile.isDirectory) {
//                val selectedDirectoryPath = selectedVirtualFile.path
//                val selectedDirectoryName = selectedVirtualFile.name
//
//                val remoteBaseDirectory = "/path/to/remote/base"
//                val fullRemoteDirectory = "$remoteBaseDirectory/$selectedDirectoryName"
//
//                JOptionPane.showMessageDialog(
//                    controlsPanel,
//                    "Transferring files from local: '$selectedDirectoryPath' to remote: '$fullRemoteDirectory'",
//                    "SFTP Info",
//                    JOptionPane.INFORMATION_MESSAGE
//                )
//
//                transferFilesViaSftp(selectedDirectoryPath, fullRemoteDirectory)
//            } else {
//                JOptionPane.showMessageDialog(
//                    controlsPanel,
//                    "Please select a directory in the Project Explorer to transfer.",
//                    "SFTP Info",
//                    JOptionPane.INFORMATION_MESSAGE
//                )
//            }
//        } else {
//            JOptionPane.showMessageDialog(
//                controlsPanel,
//                "Please select a directory in the Project Explorer.",
//                "SFTP Info",
//                JOptionPane.INFORMATION_MESSAGE
//            )
//        }
//    }

    private fun transferFilesViaSftp(localDirectoryPath: String, remoteDir: String = "") {
        // SFTP connection details (replace with your actual details or a user input mechanism)
        val sftpHost = "your_sftp_host"
        val sftpPort = 22 // Default SFTP port
        val sftpUser = "your_username"
        val sftpPassword = "your_password"
        val remoteDirectory = "/path/to/remote/directory"

        val jsch = JSch()
        var session: com.jcraft.jsch.Session? = null
        var sftpChannel: ChannelSftp? = null

        try {
            session = jsch.getSession(sftpUser, sftpHost, sftpPort)
            session.setPassword(sftpPassword)

            // Warning: Disabling host key checking is insecure for production environments.
            // You should implement proper host key verification.
            val config = java.util.Properties()
            config["StrictHostKeyChecking"] = "no"
            session.setConfig(config)

            session.connect()

            sftpChannel = session.openChannel("sftp") as ChannelSftp
            sftpChannel.connect()

            val localDir = File(localDirectoryPath)
            localDir.listFiles()?.forEach { localFile ->
                if (localFile.isFile) {
                    val remoteFilePath = "$remoteDirectory/${localFile.name}"
                    sftpChannel.put(localFile.absolutePath, remoteFilePath)
                    println("Uploaded: ${localFile.name} to $remoteFilePath")
                }
            }

            JOptionPane.showMessageDialog(
                controlsPanel,
                "Files from '$localDirectoryPath' uploaded successfully via SFTP!",
                "SFTP Success",
                JOptionPane.INFORMATION_MESSAGE
            )

        } catch (e: Exception) {
            e.printStackTrace()
            JOptionPane.showMessageDialog(
                controlsPanel,
                "Error during SFTP transfer: ${e.message}",
                "SFTP Error",
                JOptionPane.ERROR_MESSAGE
            )
        } finally {
            sftpChannel?.disconnect()
            session?.disconnect()
        }
    }
}