package com.codeforge.libs.template_engine.templates.cppactivity.jni

/**
 * Liefert den Text der Vorlagendatei `BasicJniCppSource`.
 *
 * @param pkg Package-Name des Projekts.
 */
fun BasicJniCppSource(pkg: String): String = """
    #include <jni.h>
    #include <string>

    extern "C" JNIEXPORT jstring JNICALL
    Java_${pkg.replace("_", "_1").replace('.', '_')}_MainActivity_sayHello(
    JNIEnv *env, jobject /* this */) {
        std::string hello = "Hello from C++";
        return env->NewStringUTF(hello.c_str());
    }
""".trimIndent()
