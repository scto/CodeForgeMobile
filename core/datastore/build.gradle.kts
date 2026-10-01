plugins {
    id("codeforge.android.library")
    id("codeforge.android.hilt")
    alias(libs.plugins.protobuf)
}

android {
    namespace = "com.codeforge.core.datastore"
}

dependencies {
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.datastore.proto)
    api(libs.protobuf.javalite)
    implementation(project(":core:common"))
}

protobuf {
    protoc { path = "/data/data/com.termux/files/usr/bin/protoc" }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("java") { option("lite") }
            }
        }
    }
}

afterEvaluate {
    tasks.findByName("kspDebugKotlin")?.dependsOn("generateDebugProto")
    tasks.findByName("kspReleaseKotlin")?.dependsOn("generateReleaseProto")
}
