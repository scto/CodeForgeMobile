package com.codeforge.libs.template_engine.templates.compose

fun ComposeActivityKt(pkg: String) = """
    package $pkg

    import android.os.Bundle
    import androidx.activity.ComponentActivity
    import androidx.activity.compose.setContent
    import androidx.compose.foundation.layout.Arrangement
    import androidx.compose.foundation.layout.Column
    import androidx.compose.foundation.layout.fillMaxSize
    import androidx.compose.material3.MaterialTheme
    import androidx.compose.material3.Surface
    import androidx.compose.material3.Text
    import androidx.compose.runtime.Composable
    import androidx.compose.ui.Alignment
    import androidx.compose.ui.Modifier
    import androidx.compose.ui.tooling.preview.Preview
    import $pkg.ui.theme.MyComposeApplicationTheme

    class MainActivity : ComponentActivity() {

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setContent {
                MyComposeApplicationTheme {
                    Surface(modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background) {
                        Greeting("Android")
                    }
                }
            }
        }
    }

    @Composable
    fun Greeting(name: String, modifier: Modifier = Modifier) {
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Hello ${'$'}name!")
        }
    }

    @Preview(showBackground = true)
    @Composable
    fun GreetingPreview() {
        MyComposeApplicationTheme {
            Greeting("Android")
        }
    }
""".trimIndent()
