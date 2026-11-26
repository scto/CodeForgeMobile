"""Tests für agp9_migrate.py: python3 -m unittest tool-plugins/scripts/test_agp9_migrate.py"""
import io
import os
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import agp9_migrate  # noqa: E402

CATALOG = """[versions]
agp = "8.7.3"
kotlin = "2.0.21"
ksp = "2.0.21-1.0.28"

[libraries]
android-gradlePlugin = { module = "com.android.tools.build:gradle", version.ref = "agp" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-kapt = { id = "org.jetbrains.kotlin.kapt", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
"""

ROOT_KTS = """plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
"""

APP_KTS = """plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.kapt)
}

android {
    namespace = "com.example.app"
    compileSdk = 36
    buildToolsVersion = "34.0.0"

    defaultConfig {
        minSdk = 26
        resValue("string", "flavor", "demo")
    }
    dexOptions {
        javaMaxHeapSize = "4g"
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-Xcontext-receivers", "-opt-in=kotlin.RequiresOptIn")
        languageVersion = "2.0"
        useK2 = true
    }
    applicationVariants.all { }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
}
"""

APP_GROOVY = """buildscript {
    dependencies {
        classpath 'com.android.tools.build:gradle:8.5.0'
        classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.24"
    }
}
apply plugin: 'com.android.application'
apply plugin: 'kotlin-android'
apply plugin: 'kotlin-kapt'

android {
    compileSdk 34
    kotlinOptions {
        jvmTarget = '1.8'
    }
}
"""

WRAPPER = """distributionBase=GRADLE_USER_HOME
distributionUrl=https\\://services.gradle.org/distributions/gradle-8.11.1-bin.zip
distributionSha256Sum=abc
"""

PROPS = """org.gradle.jvmargs=-Xmx2g
android.useAndroidX=true
android.defaults.buildfeatures.aidl=false
android.newDsl=false
"""


def project(files):
    root = tempfile.mkdtemp()
    for name, text in files.items():
        p = Path(root) / name
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(text, encoding="utf-8")
    return root


def run(root, mode):
    mig = agp9_migrate.Migration(root, mode == "apply", agp9_migrate.DEFAULT_AGP)
    with redirect_stdout(io.StringIO()):
        mig.run()
    return mig


class KtsCatalogTest(unittest.TestCase):
    def setUp(self):
        self.root = project({
            "gradle/libs.versions.toml": CATALOG,
            "build.gradle.kts": ROOT_KTS,
            "app/build.gradle.kts": APP_KTS,
            "gradle/wrapper/gradle-wrapper.properties": WRAPPER,
            "gradle.properties": PROPS,
            "settings.gradle.kts": 'include(":app")\n',
        })

    def read(self, name):
        return (Path(self.root) / name).read_text(encoding="utf-8")

    def test_check_changes_nothing(self):
        mig = run(self.root, "check")
        self.assertTrue(mig.changes)
        self.assertEqual(self.read("app/build.gradle.kts"), APP_KTS)
        self.assertEqual(self.read("gradle/libs.versions.toml"), CATALOG)

    def test_apply(self):
        mig = run(self.root, "apply")
        toml = self.read("gradle/libs.versions.toml")
        self.assertIn('agp = "9.0.1"', toml)
        self.assertIn('kotlin = "2.2.10"', toml)
        self.assertNotIn("org.jetbrains.kotlin.android", toml)
        self.assertIn('kotlin-kapt = { id = "com.android.legacy-kapt", version.ref = "agp" }', toml)
        self.assertIn("kotlin-compose", toml)

        root_build = self.read("build.gradle.kts")
        self.assertNotIn("kotlin.android", root_build)
        self.assertIn("kotlin.compose", root_build)

        app = self.read("app/build.gradle.kts")
        self.assertNotIn("kotlin.android", app)
        self.assertNotIn("kotlinOptions", app)
        self.assertNotIn("dexOptions", app)
        self.assertIn('buildToolsVersion = "36.0.0"', app)
        self.assertIn("kotlin {\n    compilerOptions {", app)
        self.assertIn('jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget("17"))', app)
        self.assertIn('freeCompilerArgs.addAll("-Xcontext-receivers", "-opt-in=kotlin.RequiresOptIn")', app)
        self.assertIn("languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0)", app)
        self.assertIn("// TODO AGP 9: useK2 = true", app)
        self.assertEqual(app.count("{"), app.count("}"))

        wrapper = self.read("gradle/wrapper/gradle-wrapper.properties")
        self.assertIn("gradle-9.1.0-bin.zip", wrapper)
        self.assertNotIn("distributionSha256Sum", wrapper)

        props = self.read("gradle.properties")
        self.assertNotIn("buildfeatures.aidl", props)
        self.assertIn("android.defaults.buildfeatures.resvalues=true", props)

        notes = "\n".join(mig.notes)
        self.assertIn("Variant-API", notes)
        self.assertIn("android.newDsl=false", notes)
        self.assertIn('ksp = "2.3.6"', toml)
        self.assertIn("android.newDsl=false", props)  # alte Variant-API vorhanden -> Schalter bleibt

    def test_second_run_is_noop(self):
        run(self.root, "apply")
        again = run(self.root, "apply")
        self.assertEqual([], again.changes)


SKILL_CATALOG = """[versions]
agp = "8.9.0"
kotlin = "2.2.10"
hilt = "2.52"
room = "2.6.1"
paparazzi = "1.3.5"

[libraries]
hilt-android = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
hilt-compiler = { module = "com.google.dagger:hilt-compiler", version.ref = "hilt" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }

[plugins]
android-library = { id = "com.android.library", version.ref = "agp" }
kotlin-kapt = { id = "org.jetbrains.kotlin.kapt", version.ref = "kotlin" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
"""

LIB_KTS = """plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.kapt)
}

android {
    namespace = "com.example.data"
    defaultConfig {
        buildConfigField("String", "API", "\\"https://example.com\\"")
    }
}

dependencies {
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    kapt(libs.room.compiler)
}
"""

SKILL_PROPS = """android.builtInKotlin=false
android.uniquePackageNames=false
android.enableAppCompileTimeRClass=false
android.newDsl=false
"""


class SkillStepsTest(unittest.TestCase):
    """Schritte aus Googles Skill agp-9-upgrade."""

    def setUp(self):
        self.root = project({
            "gradle/libs.versions.toml": SKILL_CATALOG,
            "data/build.gradle.kts": LIB_KTS,
            "gradle.properties": SKILL_PROPS,
        })
        self.mig = run(self.root, "apply")

    def read(self, name):
        return (Path(self.root) / name).read_text(encoding="utf-8")

    def test_versions(self):
        toml = self.read("gradle/libs.versions.toml")
        self.assertIn('hilt = "2.59.2"', toml)
        self.assertIn("Paparazzi 1.3.5", "\n".join(self.mig.notes))

    def test_kapt_to_ksp(self):
        lib = self.read("data/build.gradle.kts")
        self.assertIn("ksp(libs.hilt.compiler)", lib)
        self.assertIn("ksp(libs.room.compiler)", lib)
        self.assertNotIn("kapt", lib)
        self.assertIn('id("com.google.devtools.ksp")', lib)

    def test_build_config(self):
        lib = self.read("data/build.gradle.kts")
        self.assertIn("buildFeatures {\n        buildConfig = true\n    }", lib)
        self.assertIn("addCustomBuildConfigFields", "\n".join(self.mig.notes))

    def test_transition_props_removed(self):
        self.assertEqual("", self.read("gradle.properties").strip())

    def test_second_run_is_noop(self):
        self.assertEqual([], run(self.root, "apply").changes)

    def test_remaining_kapt_keeps_legacy_kapt(self):
        root = project({
            "gradle/libs.versions.toml": SKILL_CATALOG,
            "data/build.gradle.kts": LIB_KTS.replace("kapt(libs.room.compiler)", 'kapt("com.example:legacy-processor:1.0")'),
        })
        run(root, "apply")
        lib = (Path(root) / "data/build.gradle.kts").read_text(encoding="utf-8")
        self.assertIn("ksp(libs.hilt.compiler)", lib)
        self.assertIn('kapt("com.example:legacy-processor:1.0")', lib)
        toml = (Path(root) / "gradle/libs.versions.toml").read_text(encoding="utf-8")
        self.assertIn('kotlin-kapt = { id = "com.android.legacy-kapt", version.ref = "agp" }', toml)


class GroovyTest(unittest.TestCase):
    def test_apply(self):
        root = project({
            "build.gradle": APP_GROOVY,
            "gradle/wrapper/gradle-wrapper.properties": WRAPPER,
        })
        run(root, "apply")
        text = (Path(root) / "build.gradle").read_text(encoding="utf-8")
        self.assertIn("com.android.tools.build:gradle:9.0.1", text)
        self.assertIn("kotlin-gradle-plugin:2.2.10", text)
        self.assertNotIn("'kotlin-android'", text)
        self.assertIn("apply plugin: 'com.android.legacy-kapt'", text)
        self.assertIn("jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget('1.8')", text)
        self.assertEqual(text.count("{"), text.count("}"))


class InputTest(unittest.TestCase):
    def test_invalid_version(self):
        root = project({"build.gradle.kts": "plugins { }\n"})
        argv = sys.argv
        sys.argv = ["tool.py", root, "apply 8.13.0"]
        try:
            with redirect_stdout(io.StringIO()) as out:
                code = agp9_migrate.main()
        finally:
            sys.argv = argv
        self.assertEqual(2, code)
        self.assertIn("ungültig", out.getvalue())


if __name__ == "__main__":
    unittest.main()
