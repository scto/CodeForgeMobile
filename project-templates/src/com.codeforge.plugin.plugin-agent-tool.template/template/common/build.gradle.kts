// Packt das Plugin aus plugin/ als installierbares ZIP: ./gradlew packagePlugin → build/plugin/<id>-<version>.zip
import groovy.json.JsonSlurper

val pluginDir = layout.projectDirectory.dir("plugin")
val manifest = JsonSlurper().parse(pluginDir.file("plugin.json").asFile) as Map<*, *>
val pluginId = manifest["id"] as String
val pluginVersion = manifest["version"] as String

val validatePlugin by tasks.registering {
    group = "codeforge plugin"
    description = "Prüft JSON-Dateien und alle in plugin.json genannten Dateien."
    inputs.dir(pluginDir)
    doLast {
        val root = pluginDir.asFile
        root.walkTopDown().filter { it.isFile && it.extension == "json" }.forEach { f ->
            runCatching { JsonSlurper().parse(f) }
                .onFailure { throw GradleException("Ungültiges JSON: ${f.relativeTo(root)}: ${it.message}") }
        }
        val contributes = manifest["contributes"] as? Map<*, *> ?: emptyMap<Any, Any>()
        fun items(key: String) = (contributes[key] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()
        val files = listOf("colorThemes", "iconThemes", "languagePacks", "grammars", "snippets")
            .flatMap { k -> items(k).mapNotNull { it["path"] as? String } } +
            items("projectTemplates").mapNotNull { it["icon"] as? String } +
            listOf("tools", "agentTools", "formatters").flatMap { k -> items(k) }
                .flatMap { (it["command"] as? List<*>).orEmpty().filterIsInstance<String>() }
                .filter { it.startsWith("\${pluginDir}/") }.map { it.removePrefix("\${pluginDir}/") }
        val dirs = items("projectTemplates").mapNotNull { it["path"] as? String }
        (files.map { it to false } + dirs.map { it to true }).forEach { (rel, dir) ->
            val f = File(root, rel).canonicalFile
            val ok = f.path.startsWith(root.canonicalPath + File.separator) && (if (dir) f.isDirectory else f.isFile)
            if (!ok) throw GradleException("plugin.json verweist auf fehlende Datei: $rel")
        }
        logger.lifecycle("Plugin $pluginId $pluginVersion: in Ordnung")
    }
}

val packagePlugin by tasks.registering(Zip::class) {
    group = "codeforge plugin"
    description = "Baut das installierbare Plugin-ZIP."
    dependsOn(validatePlugin)
    from(pluginDir)
    archiveFileName.set("$pluginId-$pluginVersion.zip")
    destinationDirectory.set(layout.buildDirectory.dir("plugin"))
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
