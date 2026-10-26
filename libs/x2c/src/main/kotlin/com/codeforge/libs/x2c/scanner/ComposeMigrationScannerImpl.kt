package com.codeforge.libs.x2c.scanner

import com.codeforge.core.domain.model.ComposeScanResult
import com.codeforge.core.domain.model.LayoutClassification
import com.codeforge.core.domain.model.LayoutPlanEntry
import com.codeforge.core.domain.model.LayoutUsage
import com.codeforge.core.domain.model.X2cPlan
import com.codeforge.core.domain.repository.ComposeMigrationScanner
import com.codeforge.core.domain.repository.LayoutUsageAnalyzer
import com.codeforge.libs.x2c.analysis.LayoutUsageAnalyzerImpl
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.File
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.SAXParserFactory

class ComposeMigrationScannerImpl(
    private val usageAnalyzer: LayoutUsageAnalyzer = LayoutUsageAnalyzerImpl()
) : ComposeMigrationScanner {

    companion object {
        val EXCLUDED_DIRS = setOf(
            "build", ".gradle", "Backup", ".git", ".cxx",
            "node_modules", "out", ".idea", ".agents", ".cfm"
        )

        private val AGP_MARKERS = listOf(
            "com.android.application",
            "com.android.library",
            "com.android.dynamic-feature",
            "com.android.test",
            "apply plugin: 'com.android",
            "apply plugin: \"com.android",
            "apply plugin: 'android'",
            "apply plugin: \"android\""
        )

        private val COMPOSE_MARKERS = listOf(
            "org.jetbrains.kotlin.plugin.compose",
            "androidx.compose",
            "compose-bom",
            "activity-compose"
        )

        private val STANDARD_TAGS = setOf(
            "LinearLayout",
            "FrameLayout",
            "ConstraintLayout",
            "androidx.constraintlayout.widget.ConstraintLayout",
            "ScrollView",
            "androidx.core.widget.NestedScrollView",
            "HorizontalScrollView",
            "TextView",
            "com.google.android.material.textview.MaterialTextView",
            "Button",
            "com.google.android.material.button.MaterialButton",
            "EditText",
            "com.google.android.material.textfield.TextInputLayout",
            "com.google.android.material.textfield.TextInputEditText",
            "ImageView",
            "ImageButton",
            "com.google.android.material.imageview.ShapeableImageView",
            "CheckBox",
            "Switch",
            "androidx.appcompat.widget.SwitchCompat",
            "com.google.android.material.switchmaterial.SwitchMaterial",
            "RadioButton",
            "RadioGroup",
            "ProgressBar",
            "Slider",
            "SeekBar",
            "com.google.android.material.slider.Slider",
            "CardView",
            "androidx.cardview.widget.CardView",
            "com.google.android.material.card.MaterialCardView",
            "View",
            "androidx.recyclerview.widget.RecyclerView"
        )
    }

    override suspend fun scanProject(projectPath: String): ComposeScanResult {
        val root = File(projectPath)
        if (!root.exists() || !root.isDirectory) {
            return ComposeScanResult(
                projectPath = projectPath,
                isAndroidProject = false,
                layoutCount = 0,
                moduleCount = 0,
                layoutFiles = emptyList(),
                hasExistingCompose = false,
                qualifiesForAutoPrompt = false
            )
        }

        var isAndroid = false
        var hasCompose = false
        val layoutFiles = mutableListOf<String>()
        val layoutModules = mutableSetOf<String>()

        root.walkTopDown()
            .onEnter { dir -> !EXCLUDED_DIRS.contains(dir.name) }
            .filter { it.isFile }
            .forEach { file ->
                val name = file.name
                val path = file.invariantSeparatorsPath

                // Build file inspection for AGP & Compose
                if (name == "build.gradle" || name == "build.gradle.kts") {
                    val content = runCatching { file.readText() }.getOrDefault("")
                    if (!isAndroid && AGP_MARKERS.any { content.contains(it) }) {
                        isAndroid = true
                    }
                    if (!hasCompose) {
                        if (COMPOSE_MARKERS.any { content.contains(it) } ||
                            checkComposeBuildFeatures(content)
                        ) {
                            hasCompose = true
                        }
                    }
                }

                // Layout file inspection
                if (name.endsWith(".xml") && isLayoutXmlPath(path)) {
                    val relPath = file.relativeTo(root).invariantSeparatorsPath
                    layoutFiles.add(relPath)
                    val module = extractModuleName(relPath)
                    layoutModules.add(module)
                }
            }

        val qualifies = isAndroid && layoutFiles.isNotEmpty() && !hasCompose

        return ComposeScanResult(
            projectPath = projectPath,
            isAndroidProject = isAndroid,
            layoutCount = layoutFiles.size,
            moduleCount = if (layoutModules.isEmpty()) 0 else layoutModules.size,
            layoutFiles = layoutFiles,
            hasExistingCompose = hasCompose,
            qualifiesForAutoPrompt = qualifies
        )
    }

    override suspend fun createPlan(projectPath: String, preselectedLayout: String?): X2cPlan {
        val root = File(projectPath)
        if (!root.exists() || !root.isDirectory) {
            return X2cPlan(
                projectPath = projectPath,
                totalLayouts = 0,
                automaticCount = 0,
                withNotesCount = 0,
                notConvertibleCount = 0,
                layouts = emptyList(),
                stage2CandidatesCount = 0,
                warnings = listOf("Projektpfad existiert nicht oder ist kein Verzeichnis: $projectPath")
            )
        }

        val scan = scanProject(projectPath)
        val rawEntries = mutableListOf<LayoutPlanEntry>()
        for (relPath in scan.layoutFiles) {
            val file = File(root, relPath)
            if (!file.exists()) continue
            rawEntries.add(analyzeLayoutFile(file, relPath))
        }

        // Task 52.5: Verwendung im Code (ein Durchlauf über das Projekt)
        val idsByLayout = rawEntries.groupBy { it.layoutName }
            .mapValues { (_, list) -> list.flatMap { it.declaredViewIds }.toSet() }
        val usagesByLayout = usageAnalyzer.findAllUsages(projectPath, idsByLayout)
        val layoutEntries = rawEntries.map { enrichWithUsages(it, usagesByLayout[it.layoutName].orEmpty()) }

        var automatic = 0
        var withNotes = 0
        var notConvertible = 0
        var stage2Candidates = 0
        for (entry in layoutEntries) {
            when (entry.classification) {
                LayoutClassification.AUTOMATIC -> automatic++
                LayoutClassification.WITH_NOTES -> withNotes++
                LayoutClassification.NOT_CONVERTIBLE -> notConvertible++
            }
            if (entry.stage2Eligible) stage2Candidates++
        }

        val versionCatalogFile = File(root, "gradle/libs.versions.toml")
        val detectedTheme = detectMaterialTheme(root)

        return X2cPlan(
            projectPath = projectPath,
            totalLayouts = layoutEntries.size,
            automaticCount = automatic,
            withNotesCount = withNotes,
            notConvertibleCount = notConvertible,
            layouts = layoutEntries,
            stage2CandidatesCount = stage2Candidates,
            detectedTheme = detectedTheme,
            hasExistingCompose = scan.hasExistingCompose,
            hasVersionCatalog = versionCatalogFile.exists()
        )
    }

    private fun isLayoutXmlPath(path: String): Boolean {
        // Path contains /src/*/res/layout*/*.xml
        val segments = path.split('/')
        val resIdx = segments.indexOf("res")
        if (resIdx < 0 || resIdx >= segments.size - 2) return false
        val layoutDir = segments[resIdx + 1]
        return layoutDir.startsWith("layout")
    }

    private fun extractModuleName(relPath: String): String {
        val srcIdx = relPath.indexOf("/src/")
        return if (srcIdx > 0) {
            relPath.substring(0, srcIdx)
        } else {
            "app"
        }
    }

    private fun checkComposeBuildFeatures(content: String): Boolean {
        return content.contains("compose = true") ||
            content.contains("compose true") ||
            content.contains("compose.set(true)") ||
            (content.contains("buildFeatures") && content.contains("compose"))
    }

    private fun analyzeLayoutFile(file: File, relPath: String): LayoutPlanEntry {
        val layoutName = file.nameWithoutExtension
        val content = runCatching { file.readText() }.getOrDefault("")
        val parentDirName = file.parentFile?.name ?: "layout"
        val isVariant = parentDirName.startsWith("layout-")
        val variantQualifier = if (isVariant) parentDirName.removePrefix("layout-") else null

        val xmlInfo = parseXmlStructure(content)

        val classification: LayoutClassification
        val reason: String

        when {
            // Data Binding: <layout> Root oder @{...}
            xmlInfo.rootTag == "layout" || content.contains("@{") || content.contains("@={") -> {
                classification = LayoutClassification.NOT_CONVERTIBLE
                reason = "Data-Binding-Layout (<layout> / @{…})"
            }
            // MotionLayout
            xmlInfo.rootTag.contains("MotionLayout") || content.contains("MotionScene") -> {
                classification = LayoutClassification.NOT_CONVERTIBLE
                reason = "MotionLayout erfordert manuelle Compose-Animations-Migration"
            }
            // RemoteViews / Widgets
            content.contains("appwidget-provider") || layoutName.startsWith("widget_") || layoutName.contains("appwidget") -> {
                classification = LayoutClassification.NOT_CONVERTIBLE
                reason = "RemoteViews (Widgets/Benachrichtigungen) unterstützen kein Compose"
            }
            // ViewStub
            xmlInfo.allTags.contains("ViewStub") -> {
                classification = LayoutClassification.WITH_NOTES
                reason = "Enthält ViewStub (wird als AndroidView-Interop generiert)"
            }
            // Custom Views (Tags mit '.')
            xmlInfo.allTags.any { tag -> tag.contains('.') && !STANDARD_TAGS.contains(tag) } -> {
                val customTag = xmlInfo.allTags.first { tag -> tag.contains('.') && !STANDARD_TAGS.contains(tag) }
                classification = LayoutClassification.WITH_NOTES
                reason = "Enthält Custom View ($customTag, AndroidView-Interop)"
            }
            // Includes & Merges
            xmlInfo.allTags.contains("include") || xmlInfo.rootTag == "merge" -> {
                classification = LayoutClassification.WITH_NOTES
                reason = "Enthält <include> oder <merge>"
            }
            // Styles
            content.contains("style=\"@style/") || content.contains("style=\"?") -> {
                classification = LayoutClassification.WITH_NOTES
                reason = "Enthält XML-Styles (als TODO annotiert)"
            }
            // RelativeLayout
            xmlInfo.rootTag == "RelativeLayout" -> {
                classification = LayoutClassification.WITH_NOTES
                reason = "RelativeLayout (als Box/ConstraintLayout-Näherung)"
            }
            // Variante
            isVariant -> {
                classification = LayoutClassification.WITH_NOTES
                reason = "Layout-Variante ($variantQualifier, manuell mit WindowSizeClass lösen)"
            }
            else -> {
                classification = LayoutClassification.AUTOMATIC
                reason = "Automatisch umwandelbar"
            }
        }

        return LayoutPlanEntry(
            relativePath = relPath,
            layoutName = layoutName,
            classification = classification,
            reason = reason,
            rootTag = xmlInfo.rootTag,
            childCount = xmlInfo.directChildCount,
            declaredViewIds = xmlInfo.viewIds,
            isVariant = isVariant,
            variantQualifier = variantQualifier
        )
    }

    /**
     * Ergänzt Verwendungen aus dem Code, passt die Einstufung an (RemoteViews, Data Binding) und
     * bestimmt die Eignung für Stufe 2 (52.7). Der Analyzer ist eine Textheuristik.
     */
    private fun enrichWithUsages(entry: LayoutPlanEntry, usages: List<LayoutUsage>): LayoutPlanEntry {
        val remote = usages.firstOrNull { it.usageType == "RemoteViews" || it.usageType == "appwidget" }
        val dataBinding = usages.firstOrNull { it.usageType == "DataBinding" }
        var classification = entry.classification
        var reason = entry.reason
        when {
            remote != null && classification != LayoutClassification.NOT_CONVERTIBLE -> {
                classification = LayoutClassification.NOT_CONVERTIBLE
                reason = "RemoteViews/App-Widget verwendet dieses Layout (${remote.filePath}:${remote.lineNumber})"
            }
            dataBinding != null && classification != LayoutClassification.NOT_CONVERTIBLE -> {
                classification = LayoutClassification.NOT_CONVERTIBLE
                reason = "Data Binding im Code (${dataBinding.filePath}:${dataBinding.lineNumber})"
            }
        }

        val touched = usages.flatMap { it.referencedViewIds }.distinct()
        val eligible = !entry.isVariant &&
            classification != LayoutClassification.NOT_CONVERTIBLE &&
            usages.any { it.isStage2Candidate }
        val obstacle = when {
            eligible -> null
            entry.isVariant -> "Layout-Variante: manuell lösen"
            classification == LayoutClassification.NOT_CONVERTIBLE -> "Layout nicht umwandelbar"
            usages.isEmpty() -> "Keine Verwendung im Code gefunden"
            else -> usages.flatMap { it.notes }.firstOrNull()
                ?: "Keine Activity/Fragment-Verwendung, die sich einfach umstellen lässt"
        }
        return entry.copy(
            classification = classification,
            reason = reason,
            touchedViewIds = touched,
            usages = usages,
            stage2Eligible = eligible,
            stage2Obstacle = obstacle
        )
    }

    private data class ParsedXmlInfo(
        val rootTag: String = "unknown",
        val directChildCount: Int = 0,
        val allTags: Set<String> = emptySet(),
        val viewIds: List<String> = emptyList()
    )

    private fun parseXmlStructure(content: String): ParsedXmlInfo {
        if (content.isBlank()) return ParsedXmlInfo()

        return try {
            val spf = SAXParserFactory.newInstance()
            spf.isNamespaceAware = true
            // XXE Protection
            spf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            runCatching { spf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { spf.setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { spf.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }

            val parser = spf.newSAXParser()
            var rootTag = ""
            var directChildren = 0
            var depth = 0
            val allTags = mutableSetOf<String>()
            val viewIds = mutableListOf<String>()

            val handler = object : DefaultHandler() {
                override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
                    val tag = (if (localName.isNullOrBlank()) qName else localName) ?: "unknown"
                    if (depth == 0) {
                        rootTag = tag
                    } else if (depth == 1) {
                        directChildren++
                    }
                    allTags.add(tag)
                    depth++

                    attributes?.let { attrs ->
                        for (i in 0 until attrs.length) {
                            val attrName = attrs.getLocalName(i).ifBlank { attrs.getQName(i) }
                            if (attrName == "id" || attrs.getQName(i).endsWith(":id")) {
                                val value = attrs.getValue(i)
                                val cleanId = value.substringAfter("@+id/").substringAfter("@id/")
                                if (cleanId.isNotBlank() && cleanId != value) {
                                    viewIds.add(cleanId)
                                }
                            }
                        }
                    }
                }

                override fun endElement(uri: String?, localName: String?, qName: String?) {
                    depth--
                }
            }

            parser.parse(InputSource(StringReader(content)), handler)
            ParsedXmlInfo(
                rootTag = rootTag,
                directChildCount = directChildren,
                allTags = allTags,
                viewIds = viewIds
            )
        } catch (_: Exception) {
            // Fallback auf heuristisches Tag-Parsing bei XML-Syntaxproblemen
            heuristicXmlParse(content)
        }
    }

    private fun heuristicXmlParse(content: String): ParsedXmlInfo {
        val rootMatch = Regex("<([a-zA-Z0-9_\\.\\-]+)").find(content)
        val rootTag = rootMatch?.groupValues?.get(1) ?: "unknown"
        val allTags = Regex("<([a-zA-Z0-9_\\.\\-]+)").findAll(content)
            .map { it.groupValues[1] }
            .filter { !it.startsWith("?") && !it.startsWith("!") }
            .toSet()

        val viewIds = Regex("android:id=\"@\\+?id/([a-zA-Z0-9_]+)\"").findAll(content)
            .map { it.groupValues[1] }
            .toList()

        return ParsedXmlInfo(
            rootTag = rootTag,
            directChildCount = 0,
            allTags = allTags,
            viewIds = viewIds
        )
    }

    private fun detectMaterialTheme(root: File): String? {
        val valuesDirs = root.walkTopDown()
            .onEnter { !EXCLUDED_DIRS.contains(it.name) }
            .filter { it.isDirectory && it.name.startsWith("values") }

        for (dir in valuesDirs) {
            val themesFile = File(dir, "themes.xml").takeIf { it.exists() }
                ?: File(dir, "styles.xml").takeIf { it.exists() }
                ?: continue

            val text = runCatching { themesFile.readText() }.getOrDefault("")
            if (text.contains("Theme.Material3")) return "Theme.Material3"
            if (text.contains("Theme.MaterialComponents")) return "Theme.MaterialComponents"
            if (text.contains("Theme.AppCompat")) return "Theme.AppCompat"
        }
        return null
    }
}
