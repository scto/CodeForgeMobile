plugins {
    id("java")
    application
}

application {
    mainClass.set("${packageName}.Main")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
