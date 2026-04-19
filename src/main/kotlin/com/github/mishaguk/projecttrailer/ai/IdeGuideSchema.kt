package com.github.mishaguk.projecttrailer.ai

internal object IdeGuideSchema {

    private val ACTION_CATALOG = """
Available IntelliJ IDEA action IDs you may use:

Settings & Configuration:
  ShowSettings — Open IDE Settings dialog
  ShowProjectStructureSettings — Open Project Structure (SDKs, modules, libraries)

Navigation:
  GotoFile — Go to File by name
  GotoClass — Go to Class by name
  GotoSymbol — Go to Symbol by name
  GotoAction — Find Action by name
  SearchEverywhere — Search Everywhere dialog
  FindInPath — Find in Files
  ReplaceInPath — Replace in Files

Tool Windows:
  ActivateProjectToolWindow — Open Project tool window
  ActivateTerminalToolWindow — Open Terminal
  ActivateStructureToolWindow — Open Structure view
  ActivateProblemsViewToolWindow — Open Problems panel
  ActivateVersionControlToolWindow — Open VCS / Git panel
  ActivateTODOToolWindow — Open TODO panel

Run & Debug:
  Run — Run current configuration
  Debug — Debug current configuration
  RunClass — Run current file/class
  DebugClass — Debug current file/class
  ChooseRunConfiguration — Switch run configuration
  editRunConfigurations — Edit Run/Debug Configurations dialog

Build:
  CompileProject — Compile the project
  RebuildProject — Rebuild entire project

Version Control:
  Vcs.Push — Push commits
  CheckinProject — Commit changes dialog
  Vcs.UpdateProject — Update / Pull from remote

Refactoring:
  RefactoringMenu — Open Refactor menu
  RenameElement — Rename symbol
  ExtractMethod — Extract method from selection
  IntroduceVariable — Introduce variable from expression

Code Quality:
  ReformatCode — Reformat current file
  OptimizeImports — Optimize imports
  Generate — Generate code (constructors, getters, etc.)
  CodeInspection.OnEditor — Run code inspections

Files:
  NewFile — Create new file
  NewClass — Create new class
""".trimIndent()

    const val SYSTEM_PROMPT: String =
        "You are an IntelliJ IDEA assistant that helps users navigate the IDE. " +
            "Given a user question about IDE usage, produce 3 to 8 clear step-by-step instructions. " +
            "Each step has a title (short label) and instruction (1-2 sentences explaining what to do). " +
            "If a step can be automated by invoking a single IDE action, include the actionId and a short actionLabel for the button (e.g. \"Open Settings\"). " +
            "If the step requires manual interaction within a dialog (clicking tabs, typing values), omit actionId — set it to null. " +
            "Only use actionId values from the provided list. Never invent action IDs. " +
            "Order steps logically so the user follows them top-to-bottom. " +
            "Be concise and practical."

    fun systemPromptFull(): String = "$SYSTEM_PROMPT\n\n$ACTION_CATALOG"

    fun userPrompt(question: String): String = "User question: $question"

    // OpenAI structured outputs require all fields in "required"; use ["string","null"] for nullable.
    const val JSON_SCHEMA: String = """{
        "type":"object",
        "additionalProperties":false,
        "required":["steps"],
        "properties":{
          "steps":{
            "type":"array",
            "minItems":3,
            "maxItems":8,
            "items":{
              "type":"object",
              "additionalProperties":false,
              "required":["title","instruction","actionId","actionLabel"],
              "properties":{
                "title":{"type":"string"},
                "instruction":{"type":"string"},
                "actionId":{"type":["string","null"]},
                "actionLabel":{"type":["string","null"]}
              }
            }
          }
        }
    }"""
}
