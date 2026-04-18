package com.github.mishaguk.projecttrailer.ai

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile

internal object ProjectStructureScanner {

    private val DENY_LIST = setOf(
        ".git", ".idea", ".gradle", ".kotlin", ".intellijPlatform",
        "build", "out", "node_modules", "dist", "target",
    )

    private const val MAX_CHARS = 8_000
    private const val FILE_DEPTH_LIMIT = 2

    fun scan(project: Project, maxDepth: Int = 3): String = ReadAction.compute<String, RuntimeException> {
        val fileIndex = ProjectFileIndex.getInstance(project)
        val basePath = project.basePath
        val sb = StringBuilder()

        for (root in ProjectRootManager.getInstance(project).contentRoots) {
            walk(root, 0, maxDepth, fileIndex, basePath, sb)
            if (sb.length >= MAX_CHARS) break
        }

        if (sb.length >= MAX_CHARS) {
            sb.setLength(MAX_CHARS)
            sb.append("\n… (truncated)")
        }
        sb.toString()
    }

    private fun walk(
        file: VirtualFile,
        depth: Int,
        maxDepth: Int,
        fileIndex: ProjectFileIndex,
        basePath: String?,
        sb: StringBuilder,
    ) {
        if (sb.length >= MAX_CHARS) return
        if (file.name in DENY_LIST) return
        if (fileIndex.isExcluded(file)) return

        val isDir = file.isDirectory
        if (!isDir && depth > FILE_DEPTH_LIMIT) return

        val indent = "  ".repeat(depth.coerceAtLeast(0))
        val label = if (isDir) "${file.name}/" else file.name
        sb.append(indent).append(label).append('\n')

        if (isDir && depth < maxDepth) {
            val children = file.children ?: return
            for (child in children) {
                walk(child, depth + 1, maxDepth, fileIndex, basePath, sb)
                if (sb.length >= MAX_CHARS) return
            }
        }
    }
}
