plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.protobuf)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.codeforge.core.datastore"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    sourceSets {
        getByName("debug") {
            java.srcDirs(layout.buildDirectory.dir("generated/source/proto/debug/java"))
        }
        getByName("main") {
            java.srcDirs(layout.buildDirectory.dir("generated/source/proto/main/java"))
        }
    }
}

afterEvaluate {
    tasks.findByName("kspDebugKotlin")?.dependsOn("generateDebugProto")
    tasks.findByName("kspReleaseKotlin")?.dependsOn("generateReleaseProto")
}

dependencies {
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.datastore.proto)
    api("com.google.protobuf:protobuf-javalite:4.30.0")
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(project(":core:common"))
}

protobuf {
    protoc {
        val localProtoc = file("/data/data/com.termux/files/usr/bin/protoc")
        if (localProtoc.exists()) {
            path = localProtoc.absolutePath
        } else {
            artifact = libs.protobuf.protoc.get().toString()
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("java") { 
                    option("lite") 
                }
            }
        }
    }
}