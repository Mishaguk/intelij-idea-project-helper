package com.github.mishaguk.projecttrailer.toolWindow

import com.github.mishaguk.projecttrailer.ProjectTrailerBundle
import com.github.mishaguk.projecttrailer.ai.ChatService
import com.github.mishaguk.projecttrailer.ai.OpenAiClient
import com.github.mishaguk.projecttrailer.ai.ProjectStructureScanner
import com.github.mishaguk.projecttrailer.ai.TourService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.SwingConstants
import javax.swing.border.CompoundBorder
import javax.swing.border.MatteBorder

class ProjectTrailerToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val toolWindowContent = ProjectTrailerToolWindow(toolWindow)
        val content = ContentFactory.getInstance().createContent(toolWindowContent.getContent(), null, false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true

    class ProjectTrailerToolWindow(toolWindow: ToolWindow) {
        private val project = toolWindow.project
        private val log = thisLogger()

        fun getContent(): JComponent {
            val tabbedPane = JBTabbedPane()
            tabbedPane.addTab("Project Tour", createTourPanel())
            tabbedPane.addTab("AI Chat & Debug", createChatAndDebugPanel())
            return tabbedPane
        }

        private fun createTourPanel(): JPanel {
            val mainPanel = JBPanel<JBPanel<*>>(BorderLayout())

            val topPanel = JPanel(FlowLayout(FlowLayout.LEFT))
            val btnStartTour = JButton(ProjectTrailerBundle.message("tour.start"))
            topPanel.add(btnStartTour)

            val centerPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
                border = JBUI.Borders.empty(10)
            }
            val titleLabel = JBLabel(ProjectTrailerBundle.message("tour.welcome"))
            val explanationArea = JTextArea().apply {
                isEditable = false
                lineWrap = true
                wrapStyleWord = true
                isOpaque = false
                border = JBUI.Borders.emptyTop(10)
            }
            centerPanel.add(titleLabel, BorderLayout.NORTH)
            centerPanel.add(explanationArea, BorderLayout.CENTER)

            val bottomPanel = JPanel(BorderLayout())
            val navButtonsPanel = JPanel(FlowLayout(FlowLayout.CENTER))
            val btnPrev = JButton(ProjectTrailerBundle.message("tour.prev")).apply { isEnabled = false }
            val btnNext = JButton(ProjectTrailerBundle.message("tour.next")).apply { isEnabled = false }
            val btnClose = JButton(ProjectTrailerBundle.message("tour.close")).apply { isEnabled = false }

            val counterLabel = JBLabel("", SwingConstants.CENTER)

            navButtonsPanel.add(btnPrev)
            navButtonsPanel.add(btnNext)
            navButtonsPanel.add(btnClose)

            bottomPanel.add(counterLabel, BorderLayout.NORTH)
            bottomPanel.add(navButtonsPanel, BorderLayout.CENTER)

            mainPanel.add(topPanel, BorderLayout.NORTH)
            mainPanel.add(centerPanel, BorderLayout.CENTER)
            mainPanel.add(bottomPanel, BorderLayout.SOUTH)

            val controller = TourController(
                project = project,
                onStepChanged = { step, currentIndex, totalSteps ->
                    titleLabel.text = "${currentIndex + 1}. ${step.path}"
                    explanationArea.text = step.explanation
                    counterLabel.text = ProjectTrailerBundle.message("tour.counter", currentIndex + 1, totalSteps)

                    btnPrev.isEnabled = currentIndex > 0
                    btnNext.isEnabled = currentIndex < totalSteps - 1
                    btnClose.isEnabled = true
                },
                onTourClosed = {
                    titleLabel.text = ProjectTrailerBundle.message("tour.welcome")
                    explanationArea.text = ""
                    counterLabel.text = ""

                    btnPrev.isEnabled = false
                    btnNext.isEnabled = false
                    btnClose.isEnabled = false
                    btnStartTour.isEnabled = true
                }
            )

            btnPrev.addActionListener { controller.prev() }
            btnNext.addActionListener { controller.next() }
            btnClose.addActionListener { controller.close() }

            btnStartTour.addActionListener {
                titleLabel.text = ProjectTrailerBundle.message("tour.generating")
                explanationArea.text = ""
                btnStartTour.isEnabled = false

                ApplicationManager.getApplication().executeOnPooledThread {
                    val result = TourService.getInstance(project).generate()

                    ApplicationManager.getApplication().invokeLater {
                        result.onSuccess { steps ->
                            if (steps.isNotEmpty()) {
                                controller.start(steps)
                            } else {
                                titleLabel.text = "Tour is empty."
                                btnStartTour.isEnabled = true
                            }
                        }.onFailure { e ->
                            titleLabel.text = "Failed to generate tour."
                            Messages.showErrorDialog(project, e.message ?: "Error", "Tour Error")
                            btnStartTour.isEnabled = true
                        }
                    }
                }
            }

            return mainPanel
        }

        private fun createChatAndDebugPanel(): JPanel {
            return JPanel(BorderLayout()).apply {
                add(debugBar(), BorderLayout.NORTH)
                add(ChatPanel().component, BorderLayout.CENTER)
            }
        }

        private fun debugBar(): JPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            add(scanButton())
            add(testConnectionButton())
            add(generateTourButton())
        }

        private fun scanButton() = JButton(ProjectTrailerBundle.message("debug.scan.button")).apply {
            addActionListener {
                val button = this
                button.isEnabled = false
                ApplicationManager.getApplication().executeOnPooledThread {
                    val output = ProjectStructureScanner.scan(project)
                    log.info("ProjectStructureScanner output:\n$output")
                    ApplicationManager.getApplication().invokeLater {
                        button.isEnabled = true
                        Messages.showInfoMessage(
                            project,
                            ProjectTrailerBundle.message("debug.scan.done", output.length),
                            ProjectTrailerBundle.message("debug.scan.title"),
                        )
                    }
                }
            }
        }

        private fun testConnectionButton() = JButton(ProjectTrailerBundle.message("ai.test.button")).apply {
            addActionListener {
                val button = this
                button.isEnabled = false
                ApplicationManager.getApplication().executeOnPooledThread {
                    val result = OpenAiClient.getInstance().testConnection()
                    ApplicationManager.getApplication().invokeLater {
                        button.isEnabled = true
                        val title = ProjectTrailerBundle.message("ai.test.title")
                        result.onSuccess {
                            Messages.showInfoMessage(project, ProjectTrailerBundle.message("ai.test.ok"), title)
                        }.onFailure { e ->
                            Messages.showErrorDialog(project, ProjectTrailerBundle.message("ai.test.failed", e.message ?: ""), title)
                        }
                    }
                }
            }
        }

        private fun generateTourButton() = JButton(ProjectTrailerBundle.message("debug.tour.button")).apply {
            addActionListener {
                val button = this
                button.isEnabled = false
                ApplicationManager.getApplication().executeOnPooledThread {
                    val result = TourService.getInstance(project).generate()
                    ApplicationManager.getApplication().invokeLater {
                        button.isEnabled = true
                        val title = ProjectTrailerBundle.message("debug.tour.title")
                        result.onSuccess { steps ->
                            Messages.showInfoMessage(project, ProjectTrailerBundle.message("debug.tour.done", steps.size), title)
                        }.onFailure { e ->
                            Messages.showErrorDialog(project, ProjectTrailerBundle.message("ai.test.failed", e.message ?: ""), title)
                        }
                    }
                }
            }
        }

        private inner class ChatPanel {
            private val messages = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                border = JBUI.Borders.empty(8)
                background = JBColor.background()
            }
            private val scroll = JBScrollPane(wrap(messages)).apply {
                verticalScrollBar.unitIncrement = 16
                border = JBUI.Borders.empty()
            }
            private val input = JBTextField().apply { emptyText.text = ProjectTrailerBundle.message("chat.placeholder") }
            private val sendBtn = JButton(ProjectTrailerBundle.message("chat.send"))
            private val resetBtn = JButton(ProjectTrailerBundle.message("chat.reset"))

            val component: JComponent

            init {
                sendBtn.addActionListener { send() }
                input.addActionListener { send() }
                resetBtn.addActionListener { reset() }

                val south = JPanel(BorderLayout(6, 0)).apply {
                    border = JBUI.Borders.empty(6, 8)
                    add(resetBtn, BorderLayout.WEST)
                    add(input, BorderLayout.CENTER)
                    add(sendBtn, BorderLayout.EAST)
                }
                component = JPanel(BorderLayout()).apply {
                    add(scroll, BorderLayout.CENTER)
                    add(south, BorderLayout.SOUTH)
                }
            }

            private fun wrap(content: JComponent): JComponent = JPanel(BorderLayout()).apply {
                add(content, BorderLayout.NORTH)
                background = JBColor.background()
            }

            private fun send() {
                val text = input.text?.trim().orEmpty()
                if (text.isEmpty()) return
                input.text = ""
                setInputEnabled(false)

                addBubble(Role.USER, text)
                val thinkingBubble = addBubble(Role.ASSISTANT, ProjectTrailerBundle.message("chat.thinking"))

                ApplicationManager.getApplication().executeOnPooledThread {
                    val result = ChatService.getInstance(project).ask(text)
                    ApplicationManager.getApplication().invokeLater {
                        result.onSuccess { reply -> thinkingBubble.setText(reply) }
                            .onFailure { e -> thinkingBubble.setError(ProjectTrailerBundle.message("chat.error", e.message ?: "")) }
                        setInputEnabled(true)
                        input.requestFocusInWindow()
                    }
                }
            }

            private fun reset() {
                ChatService.getInstance(project).reset()
                messages.removeAll()
                messages.revalidate()
                messages.repaint()
                addSystemLine(ProjectTrailerBundle.message("chat.resetDone"))
            }

            private fun setInputEnabled(enabled: Boolean) {
                input.isEnabled = enabled
                sendBtn.isEnabled = enabled
            }

            private fun addBubble(role: Role, text: String): MessageBubble {
                val bubble = MessageBubble(role, text)
                messages.add(bubble)
                messages.add(Box.createVerticalStrut(6))
                messages.revalidate()
                scrollToBottom()
                return bubble
            }

            private fun addSystemLine(text: String) {
                val label = JLabel(text, SwingConstants.CENTER).apply {
                    alignmentX = Component.CENTER_ALIGNMENT
                    foreground = JBColor.GRAY
                    font = JBFont.small()
                    border = JBUI.Borders.empty(4)
                }
                messages.add(label)
                messages.revalidate()
                scrollToBottom()
            }

            private fun scrollToBottom() {
                ApplicationManager.getApplication().invokeLater {
                    val bar = scroll.verticalScrollBar
                    bar.value = bar.maximum
                }
            }
        }

        private enum class Role(val label: String, val accent: JBColor, val background: JBColor) {
            USER("You", JBColor(Color(0x34, 0x78, 0xF6), Color(0x58, 0xA6, 0xFF)), JBColor(Color(0xEE, 0xF4, 0xFF), Color(0x2C, 0x33, 0x42))),
            ASSISTANT("AI", JBColor(Color(0x28, 0xA7, 0x45), Color(0x3F, 0xB9, 0x50)), JBColor(Color(0xF5, 0xF7, 0xF5), Color(0x2A, 0x2E, 0x2A)));
        }

        private class MessageBubble(role: Role, text: String) : JPanel(BorderLayout()) {
            private val body: JTextArea
            init {
                alignmentX = Component.LEFT_ALIGNMENT
                background = role.background
                border = CompoundBorder(MatteBorder(0, 3, 0, 0, role.accent), JBUI.Borders.empty(8, 10, 8, 10))

                val header = JLabel(role.label).apply {
                    font = JBFont.label().asBold()
                    foreground = role.accent
                    border = JBUI.Borders.emptyBottom(4)
                }

                body = JTextArea(text).apply {
                    isEditable = false
                    lineWrap = true
                    wrapStyleWord = true
                    background = role.background
                    border = JBUI.Borders.empty()
                    font = JBFont.label()
                }

                val column = JPanel().apply {
                    layout = BoxLayout(this, BoxLayout.Y_AXIS)
                    isOpaque = false
                    add(header)
                    add(body)
                }
                add(column, BorderLayout.CENTER)
                maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE / 2)
            }

            fun setText(text: String) {
                body.text = text
                revalidate()
                repaint()
            }

            fun setError(text: String) {
                body.text = text
                body.foreground = JBColor(Color(0xB4, 0x21, 0x2A), Color(0xF8, 0x51, 0x49))
                revalidate()
                repaint()
            }
        }
    }
}