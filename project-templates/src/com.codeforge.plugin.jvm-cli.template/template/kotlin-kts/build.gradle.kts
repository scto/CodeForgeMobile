plugins {
    // Kotlin/JVM, application, JUnit, detekt und ktlint – siehe build-logic
    id("app.kotlin.application")
}

application { mainClass.set("{{packageName}}.MainKt") }
