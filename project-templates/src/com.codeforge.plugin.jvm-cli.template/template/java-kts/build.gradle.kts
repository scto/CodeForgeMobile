plugins {
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

application { mainClass.set("{{packageName}}.Main") }

dependencies { testImplementation(libs.junit) }
