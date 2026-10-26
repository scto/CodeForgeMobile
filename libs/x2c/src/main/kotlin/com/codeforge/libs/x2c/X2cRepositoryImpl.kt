package com.codeforge.libs.x2c

import com.codeforge.core.domain.model.GeneratorContext
import com.codeforge.core.domain.model.LayoutClassification
import com.codeforge.core.domain.model.LayoutPlanEntry
import com.codeforge.core.domain.model.PlannedChange
import com.codeforge.core.domain.model.ThemeSources
import com.codeforge.core.domain.model.UndoConflictPolicy
import com.codeforge.core.domain.model.UndoResult
import com.codeforge.core.domain.model.WireStatus
import com.codeforge.core.domain.model.WireTarget
import com.codeforge.core.domain.model.X2cFileResult
import com.codeforge.core.domain.model.X2cFileStatus
import com.codeforge.core.domain.model.X2cOptions
import com.codeforge.core.domain.model.X2cPlan
import com.codeforge.core.domain.model.X2cProgress
import com.codeforge.core.domain.model.X2cReport
import com.codeforge.core.domain.repository.ComposeBuildEnabler
import com.codeforge.core.domain.repository.ComposeEntryWirer
import com.codeforge.core.domain.repository.ComposeMigrationScanner
import com.codeforge.core.domain.repository.ComposeThemeGenerator
import com.codeforge.core.domain.repository.GitBranchCommitter
import com.codeforge.core.domain.repository.LayoutToComposeGenerator
import com.codeforge.core.domain.repository.LayoutUsageAnalyzer
import com.codeforge.core.domain.repository.X2cBackupRepository
import com.codeforge.core.domain.repository.X2cChangeApplier
import com.codeforge.core.domain.repository.X2cRepository
import com.codeforge.libs.x2c.analysis.LayoutUsageAnalyzerImpl
import com.codeforge.libs.x2c.apply.ProcessGitBranchCommitter
import com.codeforge.libs.x2c.apply.X2cChangeApplierImpl
import com.codeforge.libs.x2c.apply.XmlRemovalSelector
import com.codeforge.libs.x2c.backup.X2cBackupRepositoryImpl
import com.codeforge.libs.x2c.build.ComposeBuildEnablerImpl
import com.codeforge.libs.x2c.generator.LayoutNames
import com.codeforge.libs.x2c.generator.LayoutToComposeGeneratorImpl
import com.codeforge.libs.x2c.scanner.ComposeMigrationScannerImpl
import com.codeforge.libs.x2c.theme.ComposeThemeGeneratorImpl
import com.codeforge.libs.x2c.wiring.ComposeEntryWirerImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.CancellationException

/**
 * Task 52.9: Ablauf der Migration. Alles Erzeugen geschieht im Speicher (Projekt unverändert); erst danach folgen
 * Backup, dann **eine** Übernahme mit Journal ([X2cChangeApplier]) inklusive Build-Vorbereitung und optionalem Entfernen
 * der XML-Layouts. Fehler oder Abbruch nehmen alles zurück.
 */
class X2cRepositoryImpl(
    private val scanner: ComposeMigrationScanner = ComposeMigrationScannerImpl(),
    private val backupRepo: X2cBackupRepository = X2cBackupRepositoryImpl(),
    private val buildEnabler: ComposeBuildEnabler = ComposeBuildEnablerImpl(),
    private val generator: LayoutToComposeGenerator = LayoutToComposeGeneratorImpl(),
    private val wirer: ComposeEntryWirer = ComposeEntryWirerImpl(),
    private val themeGenerator: ComposeThemeGenerator = ComposeThemeGeneratorImpl(),
    private val applier: X2cChangeApplier = X2cChangeApplierImpl(),
    private val removalSelector: XmlRemovalSelector = XmlRemovalSelector(),
    private val git: GitBranchCommitter = ProcessGitBranchCommitter(),
    private val usageAnalyzer: LayoutUsageAnalyzer = LayoutUsageAnalyzerImpl()
) : X2cRepository {

    private val _progress = MutableStateFlow<X2cProgress>(X2cProgress.Idle)
    override val progress: StateFlow<X2cProgress> = _progress.asStateFlow()

    @Volatile
    private var isCancelled = false

    override suspend fun analyzeProject(projectPath: String): X2cPlan {
        _progress.value = X2cProgress.Analyzing("Layouts werden analysiert...")
        val plan = scanner.createPlan(projectPath)
        _progress.value = X2cProgress.Idle
        return plan
    }

    private fun checkCancelled() {
        if (isCancelled) throw CancellationException("Abgebrochen durch Benutzer")
    }

    private fun fail(message: String): Nothing {
        _progress.value = X2cProgress.Failed(message)
        throw IllegalStateException(message)
    }

    private class ModuleInfo(val root: String, val namespace: String, val sourceRoot: String)

    override suspend fun executeMigration(projectPath: String, options: X2cOptions): X2cReport {
        isCancelled = false
        val project = File(projectPath).canonicalFile
        val notes = mutableListOf<String>()

        _progress.value = X2cProgress.Analyzing("Layouts werden analysiert...")
        val plan = scanner.createPlan(projectPath)
        val prereq = buildEnabler.checkPrerequisites(projectPath)
        if (prereq.isFailure) fail(prereq.exceptionOrNull()?.message ?: "Voraussetzungen konnten nicht geprüft werden")
        prereq.getOrThrow().let { if (it.errors.isNotEmpty()) fail(it.errors.joinToString("; ")) }
        if (!options.createBackup) fail("Ohne Backup wird nichts angewendet (Journal und Rückgängig brauchen das Backup-ZIP)")
        checkCancelled()

        // ---- Auswahl
        val results = LinkedHashMap<String, X2cFileResult>() // relativer XML-Pfad -> Ergebnis
        val selected = mutableListOf<LayoutPlanEntry>()
        for (e in plan.layouts) {
            val chosen = options.selectedLayouts.isEmpty() || e.relativePath in options.selectedLayouts || e.layoutName in options.selectedLayouts
            when {
                !chosen -> results[e.relativePath] = X2cFileResult(e.relativePath, null, X2cFileStatus.SKIPPED, notes = listOf("Nicht ausgewählt"))
                e.isVariant -> results[e.relativePath] = X2cFileResult(e.relativePath, null, X2cFileStatus.SKIPPED, notes = listOf("Layout-Variante (${e.variantQualifier}): manuell lösen"))
                e.classification == LayoutClassification.NOT_CONVERTIBLE -> results[e.relativePath] = X2cFileResult(e.relativePath, null, X2cFileStatus.SKIPPED, notes = listOf(e.reason))
                else -> selected += e
            }
        }
        val (composableNames, collisions) = LayoutNames.resolve(selected.map { it.layoutName }.distinct())
        notes += collisions
        val modules = HashMap<String, ModuleInfo>()
        fun module(e: LayoutPlanEntry): ModuleInfo = modules.getOrPut(moduleRoot(e.relativePath)) { moduleInfo(project, moduleRoot(e.relativePath), notes) }

        // ---- Composables erzeugen (im Speicher)
        val changes = LinkedHashMap<String, PlannedChange>()
        val generated = LinkedHashMap<String, Triple<LayoutPlanEntry, String, String>>() // Layoutname -> (Entry, Projektpfad, Paket)
        selected.forEachIndexed { i, e ->
            checkCancelled()
            _progress.value = X2cProgress.GeneratingComposables(i + 1, selected.size, e.layoutName)
            val m = module(e)
            // Zweiter Lauf: bereits erzeugte Composable wiederfinden (Verwender sind inzwischen umgestellt)
            val pkg = existingComposablePackage(project, m, composableNames.getValue(e.layoutName)) ?: composablePackage(project, e, m)
            val xml = runCatching { File(project, e.relativePath).readText() }.getOrElse {
                results[e.relativePath] = X2cFileResult(e.relativePath, null, X2cFileStatus.FAILED, errorMessage = "Layout nicht lesbar: ${it.message}")
                return@forEachIndexed
            }
            val ctx = GeneratorContext(
                rPackage = m.namespace,
                sourcePath = e.relativePath.removePrefix(if (m.root.isEmpty()) "" else m.root + "/"),
                sourceRoot = m.sourceRoot,
                knownLayouts = composableNames
            )
            val r = generator.generateComposable(xml, e.layoutName, pkg, e.touchedViewIds, ctx)
            val file = r.getOrNull()
            if (file == null) {
                results[e.relativePath] = X2cFileResult(e.relativePath, null, X2cFileStatus.FAILED, errorMessage = r.exceptionOrNull()?.message ?: "Unbekannter Fehler")
                return@forEachIndexed
            }
            val target = (if (m.root.isEmpty()) "" else m.root + "/") + file.targetRelativePath
            val exists = File(project, target).exists()
            if (exists && !options.overwriteExisting) {
                results[e.relativePath] = X2cFileResult(e.relativePath, target, X2cFileStatus.SKIPPED, notes = listOf("Composable existiert, übersprungen"))
            } else {
                changes[target] = PlannedChange.Create(target, file.sourceContent, overwrite = exists)
                results[e.relativePath] = X2cFileResult(
                    e.relativePath, target,
                    if (file.todoCount > 0 || file.notes.isNotEmpty()) X2cFileStatus.GENERATED_WITH_NOTES else X2cFileStatus.GENERATED,
                    file.todoCount, file.notes
                )
            }
            generated[e.layoutName] = Triple(e, target, pkg)
        }

        // ---- Theme
        var themeName: String? = null
        var themePackage = ""
        var themeGenerated = false
        var themeTodos = 0
        if (options.generateTheme && generated.isNotEmpty()) {
            checkCancelled()
            _progress.value = X2cProgress.GeneratingTheme("Theme wird erzeugt...")
            val m = module(generated.values.first().first)
            val prefix = if (m.root.isEmpty()) "" else m.root + "/"
            val sources = readThemeSources(File(project, m.root))
            val pkg = m.namespace + ".ui.theme"
            val existingTheme = File(project, m.root).walkTopDown().onEnter { it.name !in ComposeMigrationScannerImpl.EXCLUDED_DIRS }
                .any { it.isFile && it.name == "Theme.kt" && it.parentFile.name == "theme" }
            val existing = if (existingTheme) setOf("${m.sourceRoot}/${pkg.replace('.', '/')}/Theme.kt") else emptySet()
            val t = themeGenerator.generate(sources, pkg, existing, m.sourceRoot).getOrNull()
            if (t == null) notes += "Theme: Erzeugung fehlgeschlagen"
            else if (t.skippedReason != null) notes += "Theme: ${t.skippedReason}"
            else {
                for (f in t.files) changes[prefix + f.targetRelativePath] = PlannedChange.Create(prefix + f.targetRelativePath, f.sourceContent, options.overwriteExisting)
                themeName = t.themeName; themePackage = t.themePackage; themeGenerated = true; themeTodos = t.todoCount
                notes += t.notes.map { "Theme: $it" }
            }
        }

        // ---- Stufe 2
        var wired = 0
        var wireSkipped = 0
        if (options.wireActivities) {
            val candidates = generated.values.filter { it.first.stage2Eligible }
            val current = HashMap<String, String>()
            var idx = 0
            for ((e, _, composablePkg) in candidates) for (u in e.usages.filter { it.isStage2Candidate }) {
                checkCancelled()
                _progress.value = X2cProgress.WiringActivities(++idx, candidates.sumOf { c -> c.first.usages.count { it.isStage2Candidate } }, u.filePath)
                val content = current[u.filePath] ?: runCatching { File(project, u.filePath).readText() }.getOrNull()
                val r = content?.let {
                    wirer.wire(File(u.filePath).name, it, WireTarget(e.layoutName, composableNames.getValue(e.layoutName), composablePkg, themeName, themePackage))
                }
                val res = results.getValue(e.relativePath)
                if (r != null && r.status == WireStatus.WIRED) {
                    current[u.filePath] = r.newContent!!
                    changes[u.filePath] = PlannedChange.Replace(u.filePath, r.newContent!!)
                    wired++
                    results[e.relativePath] = res.copy(notes = res.notes + "Stufe 2: ${u.filePath} umgestellt" + r.reviewHints.map { "Bitte prüfen ${u.filePath}:${it.line}: ${it.message}" })
                } else {
                    wireSkipped++
                    results[e.relativePath] = res.copy(notes = res.notes + "Stufe 2: ${u.filePath} nicht umgestellt (${r?.reason ?: "Datei nicht lesbar"})")
                }
            }
            for (e in plan.layouts.filter { it.stage2Eligible.not() && it.layoutName in generated && it.usages.isNotEmpty() }.distinctBy { it.layoutName }) {
                wireSkipped++
                results[e.relativePath]?.let { results[e.relativePath] = it.copy(notes = it.notes + "Stufe 2: nicht umgestellt (${e.stage2Obstacle ?: "kein einfacher Fall"})") }
            }
        }

        // ---- nichts zu tun: kein Backup, keine Änderung
        val effective = changes.values.toList()
        fun report(backup: String, journal: String, removed: Int, remaining: List<String>) = buildReport(
            projectPath, results, wired, wireSkipped, themeGenerated, themeTodos, removed, backup, journal, remaining, notes
        )
        if (effective.isEmpty()) {
            notes += "Keine Änderungen nötig (alles existiert bereits oder nichts ausgewählt); kein Backup angelegt"
            val r = report("", "", 0, emptyList())
            _progress.value = X2cProgress.Completed(r)
            return r
        }
        checkCancelled()

        // ---- Git-Zweig (nur sauber), Backup, Übernahme
        var branch: String? = null
        if (options.createGitBranch) {
            git.prepare(projectPath).fold(
                onSuccess = { branch = it.branch; notes += "Git-Zweig ${it.branch} angelegt (vorher ${it.previousBranch ?: "?"})" },
                onFailure = { notes += "Git-Zweig nicht angelegt: ${it.message}" }
            )
        }
        _progress.value = X2cProgress.CreatingBackup(projectPath)
        val zip = try { backupRepo.createBackup(projectPath, options.includeGitInBackup) } catch (t: Throwable) { fail("Backup fehlgeschlagen: ${t.message}") }
        checkCancelled()

        val removedPaths = mutableListOf<String>()
        val hasConstraintLayout = selected.any { it.rootTag == "ConstraintLayout" || it.rootTag.endsWith(".ConstraintLayout") }
        _progress.value = X2cProgress.ApplyingChanges("Änderungen werden übernommen...")
        val applied = applier.apply(projectPath, zip, effective, { isCancelled }) { rec ->
            _progress.value = X2cProgress.PreparingBuild("Compose-Unterstützung im Build wird eingerichtet...")
            buildFileCandidates(project).forEach { rec.touch(it) }
            val b = buildEnabler.enableComposeInProject(projectPath, options.useVersionCatalog, options.wireActivities, hasConstraintLayout)
            if (b.isFailure) throw IllegalStateException(b.exceptionOrNull()?.message ?: "Build-Vorbereitung fehlgeschlagen")
            b.getOrThrow().modifiedFiles.forEach { rec.touch(it, alreadyChanged = true) }
            if (options.removeXmlAfterSuccess) {
                val sel = removalSelector.select(projectPath, plan, generated.keys)
                for (p in sel.removablePaths) {
                    rec.touch(p)
                    val f = File(project, p)
                    if (f.isFile) { f.delete(); removedPaths += p }
                }
                sel.kept.forEach { (name, why) -> notes += "XML $name bleibt: noch verwendet in $why" }
            }
        }
        if (applied.isFailure) {
            val msg = applied.exceptionOrNull()?.message ?: "Übernahme fehlgeschlagen"
            if (isCancelled) { _progress.value = X2cProgress.Failed("Abgebrochen: $msg"); throw CancellationException(msg) }
            fail(msg)
        }
        val ap = applied.getOrThrow()
        ap.skippedExisting.forEach { p -> notes += "Existiert, übersprungen: $p" }
        removedPaths.forEach { p -> results[p]?.let { results[p] = it.copy(notes = it.notes + "XML entfernt (Wiederherstellung über Rückgängig)") } }

        if (branch != null) {
            val paths = ap.journal.entries.map { it.relativePath }
            git.commit(projectPath, paths, "x2c: XML-Layouts nach Compose umgewandelt (${generated.size} Layouts)").fold(
                onSuccess = { notes += "Commit $it auf Zweig $branch" },
                onFailure = { notes += "Commit fehlgeschlagen: ${it.message}" }
            )
        }
        val remaining = runCatching { usageAnalyzer.findRemainingViewReferences(projectPath) }.getOrDefault(emptyList())
        val r = report(zip, ap.journalPath, removedPaths.size, remaining)
        _progress.value = X2cProgress.Completed(r)
        return r
    }

    private fun buildReport(
        projectPath: String, results: Map<String, X2cFileResult>, wired: Int, wireSkipped: Int, themeGenerated: Boolean,
        themeTodos: Int, removed: Int, backup: String, journal: String, remaining: List<String>, notes: List<String>
    ): X2cReport {
        val list = results.values.toList()
        return X2cReport(
            projectPath = projectPath,
            timestampEpochMillis = System.currentTimeMillis(),
            generatedCount = list.count { it.status == X2cFileStatus.GENERATED },
            generatedWithNotesCount = list.count { it.status == X2cFileStatus.GENERATED_WITH_NOTES },
            skippedCount = list.count { it.status == X2cFileStatus.SKIPPED },
            failedCount = list.count { it.status == X2cFileStatus.FAILED },
            totalTodos = list.sumOf { it.todoCount } + themeTodos,
            stage2WiredCount = wired,
            stage2SkippedCount = wireSkipped,
            themeGenerated = themeGenerated,
            removedXmlCount = removed,
            backupPath = backup,
            journalPath = journal,
            fileResults = list,
            remainingViewAccesses = remaining,
            markdownSummary = notes.joinToString("\n") { "- $it" }
        )
    }

    // ------------------------------------------------------------------ Projektwissen

    private fun moduleRoot(rel: String): String = if (rel.startsWith("src/")) "" else rel.substringBefore("/src/", "")

    private fun moduleInfo(project: File, root: String, notes: MutableList<String>): ModuleInfo {
        val dir = if (root.isEmpty()) project else File(project, root)
        val gradle = listOf("build.gradle.kts", "build.gradle").map { File(dir, it) }.firstOrNull { it.isFile }?.readText().orEmpty()
        val manifest = File(dir, "src/main/AndroidManifest.xml").takeIf { it.isFile }?.readText().orEmpty()
        val ns = Regex("""namespace\s*=?\s*["']([^"']+)["']""").find(gradle)?.groupValues?.get(1)
            ?: Regex("""\bpackage\s*=\s*"([^"]+)"""").find(manifest)?.groupValues?.get(1)
            ?: Regex("""applicationId\s*=?\s*["']([^"']+)["']""").find(gradle)?.groupValues?.get(1)
            ?: "com.example.app".also { notes += "Modul ${root.ifEmpty { "." }}: Namespace nicht gefunden, $it angenommen" }
        val src = when {
            File(dir, "src/main/kotlin").isDirectory -> "src/main/kotlin"
            File(dir, "src/main/java").isDirectory -> "src/main/java"
            else -> "src/main/kotlin"
        }
        return ModuleInfo(root, ns, src)
    }

    /** Paket der Hauptverwender-Klasse, sonst `<namespace>.ui.compose`. */
    private fun composablePackage(project: File, e: LayoutPlanEntry, m: ModuleInfo): String {
        val user = (e.usages.filter { it.isStage2Candidate } + e.usages).firstOrNull { it.filePath.endsWith(".kt") || it.filePath.endsWith(".java") }
        val pkg = user?.let { u ->
            runCatching { File(project, u.filePath).useLines { l -> l.firstNotNullOfOrNull { Regex("""^\s*package\s+([\w.]+)""").find(it)?.groupValues?.get(1) } } }.getOrNull()
        }
        return pkg ?: (m.namespace + ".ui.compose")
    }

    /** Paket einer schon vorhandenen Datei `<Name>.kt` mit `fun <Name>(` im Modul, sonst null. */
    private fun existingComposablePackage(project: File, m: ModuleInfo, name: String): String? {
        val dir = if (m.root.isEmpty()) File(project, "src") else File(project, m.root + "/src")
        if (!dir.isDirectory) return null
        val f = dir.walkTopDown().onEnter { it.name !in ComposeMigrationScannerImpl.EXCLUDED_DIRS }
            .firstOrNull { it.isFile && it.name == "$name.kt" && Regex("""\bfun\s+$name\s*\(""").containsMatchIn(it.readText()) } ?: return null
        return f.useLines { l -> l.firstNotNullOfOrNull { Regex("""^\s*package\s+([\w.]+)""").find(it)?.groupValues?.get(1) } }
    }

    private fun readThemeSources(module: File): ThemeSources {
        fun read(dirName: String) = File(module, "src/main/res/$dirName").takeIf { it.isDirectory }
            ?.listFiles { f -> f.isFile && f.name.endsWith(".xml") }?.sortedBy { it.name }?.map { it.readText() }.orEmpty()
        return ThemeSources(read("values"), read("values-night"))
    }

    private fun buildFileCandidates(project: File): List<String> {
        val names = setOf("build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts", "libs.versions.toml", "gradle.properties")
        return project.walkTopDown().maxDepth(5).onEnter { it.name !in ComposeMigrationScannerImpl.EXCLUDED_DIRS }
            .filter { it.isFile && it.name in names }.map { it.relativeTo(project).path.replace('\\', '/') }.sorted().toList()
    }

    // ------------------------------------------------------------------ Rückgängig

    override suspend fun undoMigration(projectPath: String, journalPath: String): Boolean {
        _progress.value = X2cProgress.RollingBack("Migration wird zurückgerollt...")
        val r = applier.undo(projectPath, journalPath)
        _progress.value = X2cProgress.Idle
        return r.isSuccess && r.getOrThrow().keptByUser.isEmpty()
    }

    override suspend fun undoMigration(
        projectPath: String,
        journalPath: String,
        onConflict: suspend (String) -> UndoConflictPolicy
    ): Result<UndoResult> {
        _progress.value = X2cProgress.RollingBack("Migration wird zurückgerollt...")
        val r = applier.undo(projectPath, journalPath, onConflict)
        _progress.value = X2cProgress.Idle
        return r
    }

    override fun cancelMigration() {
        isCancelled = true
        _progress.value = X2cProgress.Failed("Abgebrochen durch Benutzer")
    }
}
