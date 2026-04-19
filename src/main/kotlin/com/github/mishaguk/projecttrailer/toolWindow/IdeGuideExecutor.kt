package com.github.mishaguk.projecttrailer.toolWindow

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project

internal object IdeGuideExecutor {

    @Suppress("DEPRECATION")
    fun execute(project: Project, actionId: String): Boolean {
        val action = ActionManager.getInstance().getAction(actionId) ?: return false
        val dataContext = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)
            .build()
        val event = AnActionEvent.createFromDataContext(ActionPlaces.UNKNOWN, null, dataContext)
        ApplicationManager.getApplication().invokeLater {
            action.actionPerformed(event)
        }
        return true
    }
}
