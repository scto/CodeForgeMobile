// Modul: :core:domain
package com.codeforge.core.domain.model

/** Validierung der Wizard-Eingaben (reine Logik, ohne Android). */
object ProjectInputValidator {

    private val PROJECT_NAME = Regex("^[A-Za-z][A-Za-z0-9_\\-+]{1,50}$")
    private val PACKAGE_NAME = Regex("^[a-zA-Z]+(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")

    private val JAVA_KEYWORDS = setOf(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
        "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
        "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
        "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp",
        "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void",
        "volatile", "while", "true", "false", "null"
    )

    enum class Error { INVALID_NAME, INVALID_PACKAGE, RESERVED_PACKAGE_SEGMENT, DIRECTORY_EXISTS, TARGET_BLANK, COMPOSE_REQUIRES_KOTLIN }

    fun isValidProjectName(name: String): Boolean = PROJECT_NAME.matches(name)

    fun isValidPackageName(pkg: String): Boolean = PACKAGE_NAME.matches(pkg)

    fun hasReservedSegment(pkg: String): Boolean = pkg.split('.').any { it in JAVA_KEYWORDS }

    /** Schlägt aus Vorlagenname und Projektname einen Package-Namen vor (`com.<vorlage>.<projekt>`). */
    fun suggestPackageName(templateLabel: String, projectName: String): String {
        val template = templateLabel.lowercase().replace("project", "").replace("activity", "")
            .trim().replace(Regex("[^a-z0-9]"), "")
        val project = projectName.lowercase().replace(Regex("[^a-z0-9]"), "")
        val prefix = if (template.isNotEmpty() && template[0].isLetter()) "com.$template" else "com.example"
        return if (project.isNotEmpty() && project[0].isLetter()) "$prefix.$project" else prefix
    }

    /**
     * Prüft [request]; [directoryExists] entkoppelt vom Dateisystem (Aufrufer liefert die Antwort
     * für `<targetDir>/<projectName>`).
     */
    fun validate(request: ProjectRequest, directoryExists: (String) -> Boolean): Map<Field, Error> {
        val errors = LinkedHashMap<Field, Error>()
        if (!isValidProjectName(request.projectName)) errors[Field.PROJECT_NAME] = Error.INVALID_NAME
        if (!isValidPackageName(request.packageName)) errors[Field.PACKAGE_NAME] = Error.INVALID_PACKAGE
        else if (hasReservedSegment(request.packageName)) errors[Field.PACKAGE_NAME] = Error.RESERVED_PACKAGE_SEGMENT
        if (request.targetDir.isBlank()) errors[Field.TARGET_DIR] = Error.TARGET_BLANK
        else if (Field.PROJECT_NAME !in errors && directoryExists(request.projectName)) errors[Field.TARGET_DIR] = Error.DIRECTORY_EXISTS
        if (!request.template.supportsJava && request.language == ProjectLanguage.JAVA) errors[Field.LANGUAGE] = Error.COMPOSE_REQUIRES_KOTLIN
        return errors
    }

    enum class Field { PROJECT_NAME, PACKAGE_NAME, TARGET_DIR, LANGUAGE }
}
