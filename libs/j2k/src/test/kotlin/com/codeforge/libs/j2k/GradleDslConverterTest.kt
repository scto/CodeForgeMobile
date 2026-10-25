package com.codeforge.libs.j2k

import com.codeforge.core.domain.model.J2kFileStatus
import com.codeforge.libs.j2k.converter.GradleDslConverterImpl
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GradleDslConverterTest {

    private val converter = GradleDslConverterImpl()

    @Test
    fun testConvertGroovyToKts() = runBlocking {
        val groovy = """
            apply plugin: 'com.android.application'

            android {
                compileSdkVersion 34
                minSdkVersion 21
                targetSdkVersion 34
                versionCode 1
                versionName "1.0"
                minifyEnabled true
            }

            dependencies {
                implementation 'androidx.core:core-ktx:1.12.0'
                testImplementation 'junit:junit:4.13.2'
                implementation project(':core:model')
            }
        """.trimIndent()

        val res = converter.convertFile(groovy, "build.gradle")
        assertEquals(J2kFileStatus.CONVERTED, res.result.status)
        val kts = res.convertedContent!!
        assertTrue(kts.contains("apply(plugin = \"com.android.application\")"))
        assertTrue(kts.contains("compileSdk = 34"))
        assertTrue(kts.contains("minSdk = 21"))
        assertTrue(kts.contains("isMinifyEnabled = true"))
        assertTrue(kts.contains("implementation(\"androidx.core:core-ktx:1.12.0\")"))
        assertTrue(kts.contains("implementation(project(\":core:model\"))"))
    }
}
