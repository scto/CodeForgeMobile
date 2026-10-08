package com.codeforge.libs.template_engine.templates

fun DefaultGitIgnore(): String = """
    # Gradle files
    .gradle/
    build/
    
    # Local configuration file (sdk path, etc)
    local.properties
    
    # Log/OS Files
    *.log
    
    # Android Studio generated files and folders
    captures/
    .externalNativeBuild/
    .cxx/
    *.aab
    *.apk
    output-metadata.json
    
    # IntelliJ
    *.iml
    .idea/
    misc.xml
    deploymentTargetDropDown.xml
    render.experimental.xml
    
    # Keystore files
    *.jks
    *.keystore
    
    # Google Services (e.g. APIs or Firebase)
    google-services.json
    
    # Android Profiling
    *.hprof
""".trimIndent()
