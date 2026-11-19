/**
 * Code-Plugins (API 4, nur Sideload). Alle bauen: `./gradlew :code-plugins:packageAll` → code-plugins/out/<modul>/<id>.zip
 * Signieren: CF_PLUGIN_SIGNING_KEY=/pfad/schluessel.pem setzen (siehe docs/dev/PluginSigning.md).
 */
tasks.register("packageAll") {
    group = "codeforge plugin"
    description = "Baut alle Code-Plugins nach code-plugins/out."
    dependsOn(subprojects.map { "${it.path}:packagePlugin" })
}
