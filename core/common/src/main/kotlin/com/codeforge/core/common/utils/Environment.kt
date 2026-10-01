package com.codeforge.core.common.utils

import android.annotation.SuppressLint
import android.content.Context
import com.codeforge.core.common.logging.AppLogger
import java.io.File
import java.util.UUID

@SuppressLint("SdCardPath")
object Environment {

    const val PROJECTS_FOLDER = "CodeforgeProjects"
    private const val TAG = "Environment"
    
    lateinit var ROOT: File
    lateinit var PREFIX: File
    lateinit var HOME: File
    lateinit var CODEFORGE_HOME: File
    lateinit var CODEFORGE_UI: File
    lateinit var JAVA_HOME: File
    lateinit var ANDROID_HOME: File
    lateinit var TMP_DIR: File
    lateinit var BIN_DIR: File
    lateinit var LIB_DIR: File
    lateinit var PROJECTS_DIR: File
    lateinit var REALM_DB_DIR: File

    /**
     * Used by Java LSP until the project is initialized.
     */
    lateinit var ANDROID_JAR: File
    lateinit var TOOLING_API_JAR: File
    lateinit var INIT_SCRIPT: File
    lateinit var GRADLE_USER_HOME: File
    lateinit var AAPT2: File
    lateinit var JAVA: File
    lateinit var BASH_SHELL: File
    lateinit var LOGIN_SHELL: File

    fun init(context: Context) {
        ROOT = context.filesDir
        PREFIX = mkdirIfNotExists(File(ROOT, "usr"))
        HOME = mkdirIfNotExists(File(ROOT, "home"))
        CODEFORGE_HOME = mkdirIfNotExists(File(HOME, ".codeforge"))
        TMP_DIR = mkdirIfNotExists(File(PREFIX, "tmp"))
        BIN_DIR = mkdirIfNotExists(File(PREFIX, "bin"))
        LIB_DIR = mkdirIfNotExists(File(PREFIX, "lib"))
        
        val externalDir = android.os.Environment.getExternalStorageDirectory()
        PROJECTS_DIR = mkdirIfNotExists(File(externalDir, PROJECTS_FOLDER))
        
        ANDROID_JAR = mkdirIfNotExists(File(CODEFORGE_HOME, "android.jar"))
        TOOLING_API_JAR = File(
            mkdirIfNotExists(File(CODEFORGE_HOME, "tooling-api")),
            "tooling-api-all.jar"
        )
        AAPT2 = File(CODEFORGE_HOME, "aapt2")
        CODEFORGE_UI = mkdirIfNotExists(File(CODEFORGE_HOME, "ui"))
        REALM_DB_DIR = mkdirIfNotExists(File(ROOT, "realm-dbs"))

        INIT_SCRIPT = File(mkdirIfNotExists(File(CODEFORGE_HOME, "init")), "init.gradle")
        GRADLE_USER_HOME = File(HOME, ".gradle")

        ANDROID_HOME = File(HOME, "android-sdk")
        JAVA_HOME = File(PREFIX, "opt/openjdk")

        JAVA = File(JAVA_HOME, "bin/java")
        BASH_SHELL = File(BIN_DIR, "bash")
        LOGIN_SHELL = File(BIN_DIR, "login")

        setExecutable(JAVA)
        setExecutable(BASH_SHELL)

        System.setProperty("user.home", HOME.absolutePath)
    }

    fun mkdirIfNotExists(inDir: File): File {
        if (!inDir.exists()) {
            inDir.mkdirs()
        }
        return inDir
    }

    fun setExecutable(file: File) {
        if (file.exists() && !file.setExecutable(true)) {
            AppLogger.w(TAG, "Unable to set executable permissions to file: ${file.absolutePath}")
        }
    }

    fun setProjectDir(file: File) {
        PROJECTS_DIR = File(file.absolutePath)
    }

    fun putEnvironment(env: MutableMap<String, String>, forFailsafe: Boolean) {
        env["HOME"] = HOME.absolutePath
        env["ANDROID_HOME"] = ANDROID_HOME.absolutePath
        env["ANDROID_SDK_ROOT"] = ANDROID_HOME.absolutePath
        env["ANDROID_USER_HOME"] = "${HOME.absolutePath}/.android"
        env["JAVA_HOME"] = JAVA_HOME.absolutePath
        env["GRADLE_USER_HOME"] = GRADLE_USER_HOME.absolutePath
        env["SYSROOT"] = PREFIX.absolutePath
        env["PROJECTS"] = PROJECTS_DIR.absolutePath
    }

    fun getProjectCacheDir(projectDir: File): File {
        return File(projectDir, ".codeforge")
    }

    fun createTempFile(): File {
        var file = newTempFile()
        while (file.exists()) {
            file = newTempFile()
        }
        return file
    }

    private fun newTempFile(): File {
        return File(TMP_DIR, "temp_" + UUID.randomUUID().toString().replace('-', 'X'))
    }
}