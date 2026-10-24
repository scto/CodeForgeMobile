package com.codeforge.feature.layoutdesigner.model

import java.io.File

data class CustomViewInfo(
    val qualifiedName: String,
    val simpleName: String,
    val isViewGroup: Boolean,
    val sourceFile: File,
)

class CustomViewScanner {

    private val viewBaseTypes = setOf(
        "View", "ViewGroup", "TextView", "Button", "EditText", "ImageView",
        "ImageButton", "CheckBox", "RadioButton", "Switch", "ProgressBar",
        "LinearLayout", "FrameLayout", "RelativeLayout", "ConstraintLayout",
        "CoordinatorLayout", "RecyclerView", "ScrollView", "NestedScrollView",
        "CardView", "MaterialCardView", "MaterialButton", "TabLayout",
        "AppBarLayout", "Toolbar", "MaterialToolbar"
    )

    /**
     * Durchsucht das angegebene Quellverzeichnis (z. B. src/) nach benutzerdefinierten View-Klassen.
     */
    fun scanDirectory(srcDir: File): List<CustomViewInfo> {
        if (!srcDir.exists() || !srcDir.isDirectory) return emptyList()

        val results = mutableListOf<CustomViewInfo>()
        srcDir.walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
            .forEach { file ->
                scanFile(file, results)
            }
        return results.distinctBy { it.qualifiedName }
    }

    private fun scanFile(file: File, results: MutableList<CustomViewInfo>) {
        val lines = try {
            file.readLines()
        } catch (_: Exception) {
            return
        }

        var packageName = ""
        val classRegexKt = Regex("""class\s+([A-Za-z0-9_]+)\s*(?:<[^>]+>)?\s*(?:\([^)]*\))?\s*:\s*([A-Za-z0-9_.,\s<>]+)""")
        val classRegexJava = Regex("""class\s+([A-Za-z0-9_]+)\s+(?:extends|implements)\s+([A-Za-z0-9_.,\s<>]+)""")

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("package ")) {
                packageName = trimmed.removePrefix("package ").removeSuffix(";").trim()
                continue
            }

            if (!trimmed.contains("class ")) continue

            val match = if (file.extension == "kt") classRegexKt.find(trimmed) else classRegexJava.find(trimmed)
            if (match != null) {
                val className = match.groupValues[1]
                val superTypesRaw = match.groupValues[2]
                val superTypes = superTypesRaw.split(',', '&').map { it.trim().substringBefore('<').substringBefore('(') }

                val isView = superTypes.any { st ->
                    st in viewBaseTypes || st.endsWith("View") || st.endsWith("Layout") || st.endsWith("ViewGroup")
                }

                if (isView) {
                    val isViewGroup = superTypes.any { st ->
                        st == "ViewGroup" || st.endsWith("ViewGroup") || st.endsWith("Layout") ||
                            st == "RecyclerView" || st == "ScrollView" || st == "CardView"
                    }
                    val qualified = if (packageName.isNotEmpty()) "$packageName.$className" else className
                    results.add(
                        CustomViewInfo(
                            qualifiedName = qualified,
                            simpleName = className,
                            isViewGroup = isViewGroup,
                            sourceFile = file,
                        )
                    )
                }
            }
        }
    }
}
