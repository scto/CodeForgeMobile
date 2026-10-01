package com.codeforge.libs.template_engine.template.noandroidx

fun NoAndroidXActivityKt(pkg: String) = """
    package $pkg

    import android.app.Activity
    import android.os.Bundle
    import android.widget.TextView

    public class MainActivity : Activity() {

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setContentView(TextView(this).apply {
                text = "Hello World!"
            })
        }
    }
""".trimIndent()
