plugins {
    kotlin("jvm") version "2.0.20"
    application
}

application {
    mainClass.set("${packageName}.MainKt")
}
