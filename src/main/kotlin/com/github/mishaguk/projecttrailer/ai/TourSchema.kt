package com.github.mishaguk.projecttrailer.ai

internal object TourSchema {

    const val SYSTEM_PROMPT: String =
        "You are a code guide for newcomers to a project. " +
            "Given a listing of the project's folders and top-level files, " +
            "produce an ordered tour of 3 to 10 steps that introduces the newcomer " +
            "to the most important parts of the codebase. " +
            "For each step: 'path' must be an exact path from the listing (project-relative, no leading slash); " +
            "'title' is a short human label; 'explanation' is at most two sentences describing what lives there and why it matters. " +
            "Order steps so earlier ones give context for later ones. Do not invent paths."

    fun userPrompt(structure: String): String =
        "Project structure (indented listing, directories end with '/'):\n\n$structure"

    // OpenAI Structured Outputs JSON schema for the /chat/completions response_format.
    const val JSON_SCHEMA: String = """{
        "type":"object",
        "additionalProperties":false,
        "required":["steps"],
        "properties":{
          "steps":{
            "type":"array",
            "minItems":3,
            "maxItems":10,
            "items":{
              "type":"object",
              "additionalProperties":false,
              "required":["path","title","explanation"],
              "properties":{
                "path":{"type":"string"},
                "title":{"type":"string"},
                "explanation":{"type":"string"}
              }
            }
          }
        }
    }"""
}
