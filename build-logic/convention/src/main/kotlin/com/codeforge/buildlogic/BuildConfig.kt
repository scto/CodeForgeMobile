package com.codeforge.buildlogic

import org.gradle.api.JavaVersion

/**
 * Build configuration for CodeForgeMobile.
 * Adapted from assets/BuildConfig.kt for build-logic convention plugins.
 *
 * @author Thomas Schmid
 */
object BuildConfig {

  /** CodeForgeMobile's main application package name. */
  const val packageName = "com.codeforge"

  /** The compile SDK version. */
  const val compileSdk = 36

  /** The build tools version. */
  const val buildToolsVersion = "37.0.0"

  /** The minimum SDK version. */
  const val minSdk = 26

  /** The target SDK version (28 disables W^X execution restrictions for Termux binaries in data dir). */
  const val targetSdk = 28

  /** The source and target Java compatibility. */
  val javaVersion = JavaVersion.VERSION_17

  /** Target JVM version for Kotlin compilation. */
  const val kotlinJvmTarget = "17"
}
