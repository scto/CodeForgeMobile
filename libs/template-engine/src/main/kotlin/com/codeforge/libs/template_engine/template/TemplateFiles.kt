package com.codeforge.libs.template_engine.template

import com.codeforge.libs.template_engine.ProjectTemplate

fun buildStringsXml(name: String): String = """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">$name</string>
</resources>
"""

fun BaseLayoutContentMain(): String = """<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
"""

fun SimpleMaterial3Theme(themeName: String = "AppTheme", isDark: Boolean = false): String = """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.App" parent="Theme.Material3.DayNight.NoActionBar" />
</resources>
"""

fun BasicActivityJava(appId: String): String = """package $appId;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }
}
"""

fun BasicActivityKt(appId: String): String = """package $appId

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }
}
"""

fun BasicActivityLayout(): String = """<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical" />
"""

fun BotNavActivityKt(appId: String): String = """package $appId

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }
}
"""

fun BotNavActivityJava(appId: String): String = """package $appId;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }
}
"""

fun HomeViewModelKt(appId: String): String = """package $appId.ui.home

import androidx.lifecycle.ViewModel

class HomeViewModel : ViewModel()
"""

fun HomeFragmentKt(appId: String): String = """package $appId.ui.home

import androidx.fragment.app.Fragment

class HomeFragment : Fragment()
"""

fun HomeFragmentJava(appId: String): String = """package $appId.ui.home;

import androidx.fragment.app.Fragment;

public class HomeFragment extends Fragment {}
"""

fun DashboardFragmentKt(appId: String): String = """package $appId.ui.dashboard

import androidx.fragment.app.Fragment

class DashboardFragment : Fragment()
"""

fun DashboardFragmentJava(appId: String): String = """package $appId.ui.dashboard;

import androidx.fragment.app.Fragment;

public class DashboardFragment extends Fragment {}
"""

fun NotifFragmentKt(appId: String): String = """package $appId.ui.notifications

import androidx.fragment.app.Fragment

class NotifFragment : Fragment()
"""

fun NotifFragmentJava(appId: String): String = """package $appId.ui.notifications;

import androidx.fragment.app.Fragment;

public class NotifFragment extends Fragment {}
"""

fun TabActivityKt(appId: String): String = """package $appId

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }
}
"""

fun TabActivityJava(appId: String): String = """package $appId;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }
}
"""

fun EmptyActivityKt(appId: String): String = """package $appId

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }
}
"""

fun PlaceholderFragmentKt(appId: String): String = """package $appId.ui.main

import androidx.fragment.app.Fragment

class PlaceholderFragment : Fragment()
"""

fun PlaceholderFragmentJava(appId: String): String = """package $appId.ui.main;

import androidx.fragment.app.Fragment;

public class PlaceholderFragment extends Fragment {}
"""

fun ComposeActivityKt(appId: String): String = """package $appId

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Text("Hello Compose!")
        }
    }
}
"""

fun ComposeColorKt(appId: String): String = """package $appId.ui.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
"""

fun ComposeThemeKt(appId: String): String = """package $appId.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
"""

fun CppActivityKt(appId: String): String = """package $appId

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }
}
"""

fun CppActivityJava(appId: String): String = """package $appId;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }
}
"""

fun NavDrawerActivityKt(appId: String): String = """package $appId

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }
}
"""

fun NavDrawerActivityJava(appId: String): String = """package $appId;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }
}
"""

fun AppBuildGradle(appId: String = "", minSdk: Int = 24, useKotlin: Boolean = true, useKts: Boolean = false, templateKind: Any? = null): String {
    val isCompose = templateKind?.toString()?.contains("COMPOSE", ignoreCase = true) == true
    val isCpp = templateKind?.toString()?.contains("CPP", ignoreCase = true) == true
    val isNoAndroidX = templateKind?.toString()?.contains("NO_ANDROIDX", ignoreCase = true) == true

    return if (useKts) {
        """plugins {
    id("com.android.application")
    ${if (useKotlin) "id(\"org.jetbrains.kotlin.android\")" else ""}
}

android {
    namespace = "$appId"
    compileSdk = 34

    defaultConfig {
        applicationId = "$appId"
        minSdk = $minSdk
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    ${if (useKotlin) """kotlinOptions {
        jvmTarget = "17"
    }""" else ""}
    ${if (isCompose) """buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }""" else ""}
}

dependencies {
    ${if (isNoAndroidX) """implementation("com.android.support:appcompat-v7:28.0.0")"""
    else if (isCompose) """implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")"""
    else """implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")"""}
}
"""
    } else {
        """plugins {
    id 'com.android.application'
    ${if (useKotlin) "id 'org.jetbrains.kotlin.android'" else ""}
}

android {
    namespace '$appId'
    compileSdk 34

    defaultConfig {
        applicationId "$appId"
        minSdk $minSdk
        targetSdk 34
        versionCode 1
        versionName "1.0"
        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
    ${if (useKotlin) """kotlinOptions {
        jvmTarget = '17'
    }""" else ""}
    ${if (isCompose) """buildFeatures {
        compose true
    }
    composeOptions {
        kotlinCompilerExtensionVersion '1.5.8'
    }""" else ""}
}

dependencies {
    ${if (isNoAndroidX) """implementation 'com.android.support:appcompat-v7:28.0.0'"""
    else if (isCompose) """implementation 'androidx.core:core-ktx:1.12.0'
    implementation 'androidx.lifecycle:lifecycle-runtime-ktx:2.7.0'
    implementation 'androidx.activity:activity-compose:1.8.2'
    implementation platform('androidx.compose:compose-bom:2024.02.00')
    implementation 'androidx.compose.ui:ui'
    implementation 'androidx.compose.ui:ui-graphics'
    implementation 'androidx.compose.ui:ui-tooling-preview'
    implementation 'androidx.compose.material3:material3'"""
    else """implementation 'androidx.core:core-ktx:1.12.0'
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.11.0'
    implementation 'androidx.constraintlayout:constraintlayout:2.1.4'"""}
}
"""
    }
}

fun GradleProperties(addAndroidX: Boolean = true): String = """
android.useAndroidX=$addAndroidX
android.enableJetifier=$addAndroidX
"""

fun TabStringsXml(): String = """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">My Application</string>
</resources>
"""

fun BotNavStringsXml(): String = """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">My Application</string>
</resources>
"""

fun NavigationXml(appId: String): String = """<?xml version="1.0" encoding="utf-8"?>
<navigation xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/mobile_navigation">
</navigation>
"""

fun NavDrawerNavigationXml(appId: String): String = """<?xml version="1.0" encoding="utf-8"?>
<navigation xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/mobile_navigation">
</navigation>
"""

fun AndroidMkFile(): String = """LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE    := native-lib
LOCAL_SRC_FILES := native-lib.cpp
include $(BUILD_SHARED_LIBRARY)
"""

fun ApplicationMkFile(): String = """APP_ABI := all
"""

fun BasicJniCppSource(appId: String = ""): String = """#include <jni.h>

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_app_MainActivity_stringFromJNI(JNIEnv* env, jobject) {
    return env->NewStringUTF("Hello from C++");
}
"""
