package com.github.mishaguk.projecttrailer.toolWindow

import com.github.mishaguk.projecttrailer.ProjectTrailerBundle
import com.github.mishaguk.projecttrailer.ai.ChatService
import com.github.mishaguk.projecttrailer.ai.IdeGuideService
import com.github.mishaguk.projecttrailer.ai.IdeGuideStep
import com.github.mishaguk.projecttrailer.ai.TourService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
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

        fun getContent(): JComponent {
            val topPanel = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                add(createTourBar())
                add(createIdeGuideBar())
            }
            val root = JPanel(BorderLayout())
            root.add(topPanel, BorderLayout.NORTH)
            root.add(ChatPanel().component, BorderLayout.CENTER)
            return root
        }

        private fun createTourBar(): JPanel {
            val accentColor = JBColor(Color(0x28, 0xA7, 0x45), Color(0x3F, 0xB9, 0x50))
            val cardBg = JBColor(Color(0xF7, 0xFB, 0xF7), Color(0x2A, 0x2E, 0x2A))

            val btnStartTour = JButton(ProjectTrailerBundle.message("tour.start")).apply {
                font = JBFont.label().asBold()
                putClientProperty("JButton.buttonType", "roundRect")
            }
            val focusField = JBTextField().apply {
                emptyText.text = ProjectTrailerBundle.message("tour.focus.placeholder")
                font = JBFont.label()
            }
            val btnFocusTour = JButton(ProjectTrailerBundle.message("tour.focus.start")).apply {
                putClientProperty("JButton.buttonType", "roundRect")
            }

            var activeBalloonRef: java.util.concurrent.atomic.AtomicReference<Balloon?>? = null
            lateinit var controller: TourController
            controller = TourController(
                project = project,
                onStepChanged = { _, _, _ ->
                    activeBalloonRef?.get()?.hide()
                    activeBalloonRef = null
                },
                onTourClosed = {
                    activeBalloonRef?.get()?.hide()
                    activeBalloonRef = null
                    btnStartTour.isEnabled = true
                    btnFocusTour.isEnabled = true
                },
                onAfterSelect = { step, currentIndex, totalSteps, file ->
                    activeBalloonRef?.get()?.hide()
                    activeBalloonRef = TourBalloonPresenter.show(
                        project = project,
                        step = step,
                        index = currentIndex,
                        total = totalSteps,
                        targetFile = file,
                        onPrev = { controller.prev() },
                        onNext = { controller.next() },
                        onClose = { controller.close() },
                    )
                },
            )

            fun launchTour(focusQuery: String?) {
                btnStartTour.isEnabled = false
                btnFocusTour.isEnabled = false
                ApplicationManager.getApplication().executeOnPooledThread {
                    val result = TourService.getInstance(project).generate(focusQuery)
                    ApplicationManager.getApplication().invokeLater {
                        result.onSuccess { steps ->
                            if (steps.isNotEmpty()) {
                                controller.start(steps)
                            } else {
                                btnStartTour.isEnabled = true
                                btnFocusTour.isEnabled = true
                                Messages.showInfoMessage(project, "Tour is empty.", "Project Tour")
                            }
                        }.onFailure { e ->
                            btnStartTour.isEnabled = true
                            btnFocusTour.isEnabled = true
                            Messages.showErrorDialog(project, e.message ?: "Error", "Tour Error")
                        }
                    }
                }
            }

            btnStartTour.addActionListener { launchTour(null) }

            btnFocusTour.addActionListener {
                val query = focusField.text?.trim().orEmpty()
                if (query.isEmpty()) {
                    Messages.showWarningDialog(project, ProjectTrailerBundle.message("tour.focus.empty"), "Tour")
                    return@addActionListener
                }
                launchTour(query)
            }
            focusField.addActionListener { btnFocusTour.doClick() }

            val title = JLabel(ProjectTrailerBundle.message("tour.header.title")).apply {
                font = JBFont.label().biggerOn(3f).asBold()
                foreground = accentColor
                border = JBUI.Borders.empty(0, 0, 2, 0)
            }

            val subtitle = JLabel(ProjectTrailerBundle.message("tour.header.subtitle")).apply {
                font = JBFont.small()
                foreground = JBColor.GRAY
            }

            val headerRow = JPanel(BorderLayout()).apply {
                isOpaque = false
                add(JPanel().apply {
                    isOpaque = false
                    layout = BoxLayout(this, BoxLayout.Y_AXIS)
                    add(title)
                    add(subtitle)
                }, BorderLayout.CENTER)
                add(btnStartTour, BorderLayout.EAST)
            }

            val divider = object : JPanel() {
                override fun getPreferredSize() = Dimension(super.getPreferredSize().width, 1)
                override fun getMaximumSize() = Dimension(Int.MAX_VALUE, 1)
            }.apply {
                background = JBColor.border()
            }

            val focusLabel = JLabel(ProjectTrailerBundle.message("tour.focus.label")).apply {
                font = JBFont.small().asBold()
                foreground = JBColor.GRAY
                border = JBUI.Borders.empty(0, 0, 4, 0)
            }

            val focusRow = JPanel(BorderLayout(6, 0)).apply {
                isOpaque = false
                add(focusField, BorderLayout.CENTER)
                add(btnFocusTour, BorderLayout.EAST)
            }

            val card = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                background = cardBg
                border = CompoundBorder(
                    JBUI.Borders.empty(8, 10, 6, 10),
                    CompoundBorder(
                        MatteBorder(0, 3, 0, 0, accentColor),
                        JBUI.Borders.empty(10, 12, 10, 12)
                    )
                )
                add(headerRow)
                add(Box.createVerticalStrut(8))
                add(divider)
                add(Box.createVerticalStrut(8))
                add(focusLabel)
                add(focusRow)
            }

            return JPanel(BorderLayout()).apply {
                border = JBUI.Borders.empty(6, 6, 2, 6)
                add(card, BorderLayout.NORTH)
            }
        }

        private fun createIdeGuideBar(): JPanel {
            val accentColor = JBColor(Color(0x34, 0x78, 0xF6), Color(0x58, 0xA6, 0xFF))
            val cardBg = JBColor(Color(0xEE, 0xF4, 0xFF), Color(0x2C, 0x33, 0x42))

            val guideInput = JBTextField().apply {
                emptyText.text = ProjectTrailerBundle.message("guide.placeholder")
                font = JBFont.label()
            }
            val btnAsk = JButton(ProjectTrailerBundle.message("guide.ask")).apply {
                putClientProperty("JButton.buttonType", "roundRect")
            }

            val stepsPanel = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                isOpaque = false
                isVisible = false
            }

            fun showSteps(steps: List<IdeGuideStep>) {
                stepsPanel.removeAll()
                steps.forEachIndexed { index, step ->
                    stepsPanel.add(createStepCard(index + 1, step, accentColor))
                    stepsPanel.add(Box.createVerticalStrut(6))
                }
                stepsPanel.isVisible = true
                stepsPanel.revalidate()
                stepsPanel.repaint()
            }

            fun askGuide() {
                val question = guideInput.text?.trim().orEmpty()
                if (question.isEmpty()) return
                guideInput.isEnabled = false
                btnAsk.isEnabled = false
                stepsPanel.removeAll()
                stepsPanel.isVisible = true
                val loadingLabel = JLabel(ProjectTrailerBundle.message("guide.loading")).apply {
                    foreground = JBColor.GRAY
                    font = JBFont.small()
                    border = JBUI.Borders.empty(4)
                }
                stepsPanel.add(loadingLabel)
                stepsPanel.revalidate()

                ApplicationManager.getApplication().executeOnPooledThread {
                    val result = IdeGuideService.getInstance(project).generate(question)
                    ApplicationManager.getApplication().invokeLater {
                        result.onSuccess { steps -> showSteps(steps) }
                            .onFailure { e ->
                                stepsPanel.removeAll()
                                stepsPanel.add(JLabel(ProjectTrailerBundle.message("guide.error", e.message ?: "")).apply {
                                    foreground = JBColor(Color(0xB4, 0x21, 0x2A), Color(0xF8, 0x51, 0x49))
                                    font = JBFont.small()
                                    border = JBUI.Borders.empty(4)
                                })
                                stepsPanel.revalidate()
                            }
                        guideInput.isEnabled = true
                        btnAsk.isEnabled = true
                    }
                }
            }

            btnAsk.addActionListener { askGuide() }
            guideInput.addActionListener { askGuide() }

            val title = JLabel(ProjectTrailerBundle.message("guide.header.title")).apply {
                font = JBFont.label().biggerOn(3f).asBold()
                foreground = accentColor
                border = JBUI.Borders.empty(0, 0, 2, 0)
                alignmentX = Component.LEFT_ALIGNMENT
            }

            val subtitle = JLabel(ProjectTrailerBundle.message("guide.header.subtitle")).apply {
                font = JBFont.small()
                foreground = JBColor.GRAY
                alignmentX = Component.LEFT_ALIGNMENT
            }

            val headerPanel = JPanel().apply {
                isOpaque = false
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                add(title)
                add(subtitle)
            }

            val inputRow = JPanel(BorderLayout(6, 0)).apply {
                isOpaque = false
                add(guideInput, BorderLayout.CENTER)
                add(btnAsk, BorderLayout.EAST)
            }

            val card = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                background = cardBg
                border = CompoundBorder(
                    JBUI.Borders.empty(4, 10, 6, 10),
                    CompoundBorder(
                        MatteBorder(0, 3, 0, 0, accentColor),
                        JBUI.Borders.empty(10, 12, 10, 12)
                    )
                )
                add(headerPanel)
                add(Box.createVerticalStrut(8))
                add(inputRow)
                add(Box.createVerticalStrut(6))
                add(stepsPanel)
            }

            return JPanel(BorderLayout()).apply {
                border = JBUI.Borders.empty(2, 6, 6, 6)
                add(card, BorderLayout.NORTH)
            }
        }

        private fun createStepCard(number: Int, step: IdeGuideStep, accentColor: JBColor): JPanel {
            val stepBg = JBColor(Color(0xF8, 0xFA, 0xFF), Color(0x2E, 0x32, 0x3A))

            val badge = JLabel("  $number  ").apply {
                isOpaque = true
                background = accentColor
                foreground = JBColor.WHITE
                font = JBFont.small().asBold()
                horizontalAlignment = SwingConstants.CENTER
            }

            val titleLabel = JLabel(step.title).apply {
                font = JBFont.label().asBold()
                border = JBUI.Borders.emptyLeft(8)
            }

            val topRow = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                isOpaque = false
                add(badge)
                add(titleLabel)
            }

            val instructionArea = JTextArea(step.instruction).apply {
                isEditable = false
                lineWrap = true
                wrapStyleWord = true
                background = stepBg
                border = JBUI.Borders.empty(4, 0, 0, 0)
                font = JBFont.label()
            }

            val card = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                background = stepBg
                border = JBUI.Borders.empty(8, 10, 8, 10)
                add(topRow)
                add(instructionArea)
            }

            if (step.actionId != null) {
                val actionBtn = JButton(step.actionLabel ?: ProjectTrailerBundle.message("guide.doIt")).apply {
                    putClientProperty("JButton.buttonType", "roundRect")
                    font = JBFont.small().asBold()
                    cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                    addActionListener {
                        val success = IdeGuideExecutor.execute(project, step.actionId)
                        if (!success) {
                            Messages.showErrorDialog(project, ProjectTrailerBundle.message("guide.actionNotFound", step.actionId), "IDE Guide")
                        }
                    }
                }
                val btnRow = JPanel(FlowLayout(FlowLayout.LEFT, 0, 4)).apply {
                    isOpaque = false
                    add(actionBtn)
                }
                card.add(btnRow)
            }

            card.maximumSize = Dimension(Int.MAX_VALUE, card.preferredSize.height + 200)
            return card
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

                ChatPanelBridge.getInstance(project).onExplainRequest = { userLabel, question ->
                    submitQuestion(userLabel, question)
                }
            }

            fun submitQuestion(userLabel: String, question: String) {
                addBubble(Role.USER, userLabel)
                val thinkingBubble = addBubble(Role.ASSISTANT, ProjectTrailerBundle.message("chat.thinking"))
                setInputEnabled(false)

                ApplicationManager.getApplication().executeOnPooledThread {
                    val result = ChatService.getInstance(project).ask(question)
                    ApplicationManager.getApplication().invokeLater {
                        result.onSuccess { reply -> thinkingBubble.setText(reply) }
                            .onFailure { e -> thinkingBubble.setError(ProjectTrailerBundle.message("chat.error", e.message ?: "")) }
                        setInputEnabled(true)
                    }
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
