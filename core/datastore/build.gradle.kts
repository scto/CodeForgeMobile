import java.io.File

plugins {
    alias(libs.plugins.codeforge.android.library)
    alias(libs.plugins.codeforge.android.hilt)
    alias(libs.plugins.protobuf)
    alias(libs.plugins.codeforge.quality)
}

android {
    namespace = "com.codeforge.core.datastore"

    sourceSets {
        getByName("main") {
            // src/main/proto ist bereits das Standardverzeichnis, das das
            // Protobuf-Gradle-Plugin automatisch erkennt — die explizite srcDir-Angabe
            // hier führte zu einem Konflikt mit der automatischen Task-Generierung.
            //proto {
            //    srcDir("src/main/proto")
            //}
        }
    }
}

dependencies {
    implementation(project(":core:resources"))
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.datastore.proto)
    implementation(libs.protobuf.javalite)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(project(":core:common"))
}

// Hilfsfunktion zur dynamischen Suche nach dem lokalen protoc-Compiler
fun findDynamicProtoc(): String? {
    // 1. Suche in Termux-basierten Umgebungen (Termux, AndroidIDE, Code Studio)
    val prefix = System.getenv("PREFIX")
    if (prefix != null) {
        val termuxProtoc = File("$prefix/bin/protoc")
        if (termuxProtoc.exists() && termuxProtoc.canExecute()) {
            return termuxProtoc.absolutePath
        }
    }

    // 2. Suche in Standard-Linux-Umgebungen über die systemweite $PATH-Variable
    val systemPath = System.getenv("PATH")
    if (systemPath != null) {
        for (dir in systemPath.split(File.pathSeparator)) {
            val linuxProtoc = File(dir, "protoc")
            if (linuxProtoc.exists() && linuxProtoc.canExecute()) {
                return linuxProtoc.absolutePath
            }
        }
    }

    // Nichts gefunden
    return null
}

protobuf {
    protoc {
        val localProtocPath = findDynamicProtoc()
        
        if (localProtocPath != null) {
            // Nutze das im System installierte Binary (löst Kompatibilitätsprobleme unter Android/Linux)
            path = localProtocPath
        } else {
            // Fallback: Lade das vorkompilierte Artefakt aus den Dependencies herunter
            artifact = libs.protobuf.protoc.get().toString()
        }
    }
    
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("java") { option("lite") }
            }
        }
    }
}
