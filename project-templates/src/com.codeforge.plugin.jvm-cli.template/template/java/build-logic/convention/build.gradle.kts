plugins {
    `kotlin-dsl`
}

group = "{{packageName}}.buildlogic"

gradlePlugin {
    plugins {
        register("javaApplication") {
            id = "app.java.application"
            implementationClass = "JavaApplicationConventionPlugin"
        }
    }
}
