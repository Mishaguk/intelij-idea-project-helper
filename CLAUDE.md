# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

**ProjectTrailer** — IntelliJ Platform plugin by team **Who_knows**, scaffolded from the [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template). Kotlin/JVM, built with Gradle (Kotlin DSL) and the `org.jetbrains.intellij.platform` Gradle plugin. Targets IntelliJ IDEA `2025.2.6.1` (see `build.gradle.kts`).

Note on directory name: the on-disk folder is still `intelij-idea-project-helper` (legacy from the original clone). The Gradle project name, plugin id, and source package are all `ProjectTrailer` / `com.github.mishaguk.projecttrailer`. Renaming the working directory is optional and only affects local IDE bookmarks.

The classes under `src/main/kotlin/com/github/mishaguk/projecttrailer/{services,startup,toolWindow}` (`ProjectTrailerProjectService`, `ProjectTrailerStartupActivity`, `ProjectTrailerToolWindowFactory`) are intentionally kept as a working scaffold. Replace their bodies with real functionality rather than adding parallel sample classes.

## Common commands

Use the Gradle wrapper (`./gradlew` from bash, `gradlew.bat` from cmd/PowerShell):

- Build plugin distribution: `./gradlew build` (output: `build/distributions/*.zip`)
- Run a sandbox IDE with the plugin loaded: `./gradlew runIde`
- Run unit tests: `./gradlew test`
- Run a single test: `./gradlew test --tests "com.github.mishaguk.projecttrailer.ProjectTrailerPluginTest.testXMLFile"`
- Verify plugin against target IDE versions: `./gradlew verifyPlugin` (matches the `Run Verifications` IDE config)
- Patch CHANGELOG from `[Unreleased]`: `./gradlew patchChangelog` (auto-runs before `publishPlugin`)
- Publish to JetBrains Marketplace: `./gradlew publishPlugin` (requires deployment token / signing secrets)

Pre-made IntelliJ run configs live in `.run/` (`Run Plugin`, `Run Tests`, `Run Verifications`).

## Architecture notes

- **Plugin manifest** (`src/main/resources/META-INF/plugin.xml`) is the single source of truth for what the plugin contributes to the IDE — extensions, services, tool windows, startup activities, actions. New features must be registered here; class-level annotations alone are not enough for most extension points. The tool-window id is `ProjectTrailer`.
- **Plugin description and change notes** are not authored in `plugin.xml`. `build.gradle.kts` injects them at build time:
  - Description is extracted from `README.md` between the `<!-- Plugin description -->` / `<!-- Plugin description end -->` markers — the build **fails** if these markers are missing or mismatched.
  - `changeNotes` is rendered from `CHANGELOG.md` for the current `version` via the `org.jetbrains.changelog` plugin. Add user-facing changes under `## [Unreleased]` in `CHANGELOG.md`; `patchChangelog` rolls them into the released version on publish.
- **i18n / strings**: user-facing strings live in `src/main/resources/messages/ProjectTrailerBundle.properties` and are looked up via `ProjectTrailerBundle.message("key", args...)`. The bundle base name in `plugin.xml` (`<resource-bundle>`) and in `ProjectTrailerBundle.kt` (`BUNDLE` constant) must stay in sync.
- **Versioning / coordinates** live in `gradle.properties` (`group=com.github.mishaguk.projecttrailer`, `version`, `pluginRepositoryUrl`). The plugin `id` in `plugin.xml` and the Kotlin source package must stay aligned with `group`.
- **Plugin / settings versions** are pinned in `settings.gradle.kts` via `pluginManagement` and the settings-level `org.jetbrains.intellij.platform.settings` plugin (which also configures `defaultRepositories()` for Maven Central + the IntelliJ repos). `build.gradle.kts` references plugins by id without versions because of this.
- **Gradle config + build cache are enabled** (`org.gradle.configuration-cache=true`, `org.gradle.caching=true`). When touching `build.gradle.kts`, prefer `providers`/`layout` APIs and avoid capturing `Project`/`Task` references in lambdas — the existing `pluginConfiguration` block is structured this way deliberately for configuration-cache compatibility.
- **Kotlin stdlib bundling is opted out** (`kotlin.stdlib.default.dependency=false`) — the platform provides it. Don't re-add a stdlib dependency.
- **Tests** use the IntelliJ Platform `TestFrameworkType.Platform` (JUnit 4, `BasePlatformTestCase`). Test data fixtures live under `src/test/testData/` (e.g. `rename/foo.xml`, `foo_after.xml` for rename/refactor tests).

## CI

`.github/workflows/` contains `build.yml`, `release.yml`, `run-ui-tests.yml` (template defaults). Dependabot is configured via `.github/dependabot.yml`. Repository URL: `https://github.com/Mishaguk/Project-Trailer` (matches `pluginRepositoryUrl` in `gradle.properties`; note the hyphen).

## AI integration (`ai/` package)

The `com.github.mishaguk.projecttrailer.ai` package holds the OpenAI-backed features (chat + project tour generation + folder-structure scanning). Key points:

- **API key loading**: `AiKeyProvider` (APP-level service) reads the key from a classpath resource named by `AiConfig.ENV_RESOURCE` (`env.local`), parsing `OPENAI_API_KEY=...` (supports `#` comments and quoted values). The file is expected on the plugin classpath — typically `src/main/resources/env.local`, which must **not** be committed. No key → services degrade gracefully (see call sites).
- **Central config**: `AiConfig` pins `BASE_URL`, `MODEL` (`gpt-4o-mini`), the env resource name, and the key name. Change model/base URL here rather than inlining.
- **HTTP layer**: `OpenAiClient` is the single transport; `ChatService` and `TourService` are higher-level features that should go through it. `ProjectStructureScanner` produces the project-tree context; `TourSchema`/`TourStep` define the structured-output format.
- When adding AI features, wire them through `AiKeyProvider` + `OpenAiClient` rather than re-reading env files or building new HTTP clients.
