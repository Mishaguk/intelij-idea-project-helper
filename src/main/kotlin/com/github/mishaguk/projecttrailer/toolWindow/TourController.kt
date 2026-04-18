package com.github.mishaguk.projecttrailer.toolWindow

import com.github.mishaguk.projecttrailer.ai.TourStep
import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager
import java.nio.file.Path

class TourController(
    private val project: Project,
    private val onStepChanged: (step: TourStep, currentIndex: Int, totalSteps: Int) -> Unit,
    private val onTourClosed: () -> Unit
) {
    private var steps: List<TourStep> = emptyList()
    private var currentIndex: Int = -1

    fun start(newSteps: List<TourStep>) {
        if ( newSteps.isEmpty() ) {
            return
        }
        steps = newSteps
        currentIndex = 0
        renderCurrentStep()
    }

    fun next() {
        if ( hasNext() ) {
            currentIndex++
            renderCurrentStep()
        }
    }

    fun prev() {
        if ( hasPrev() ) {
            currentIndex--
            renderCurrentStep()
        }
    }

    fun close() {
        steps = emptyList()
        currentIndex = -1
        onTourClosed()
    }

    fun hasNext(): Boolean = currentIndex < steps.size - 1
    fun hasPrev(): Boolean = currentIndex > 0

    private fun renderCurrentStep() {
        if ( currentIndex !in steps.indices ) {
            return
        }
        val step = steps[currentIndex]

        onStepChanged(step, currentIndex, steps.size)
        selectInProjectView(step.path)
    }

    private fun selectInProjectView(path: String) {
        ApplicationManager.getApplication().invokeLater {
            val basePath = project.basePath ?: return@invokeLater

            val absolutePath = Path.of(basePath, path.trimStart('/', '\\')).toString().replace('\\', '/')

            val virtualFile = LocalFileSystem.getInstance().findFileByPath(absolutePath) ?: return@invokeLater

            val psiManager = PsiManager.getInstance(project)
            val psiElement = if (virtualFile.isDirectory) {
                psiManager.findDirectory(virtualFile)
            } else {
                psiManager.findFile(virtualFile)
            } ?: return@invokeLater

            ProjectView.getInstance(project).selectPsiElement(psiElement, true)
        }
    }
}