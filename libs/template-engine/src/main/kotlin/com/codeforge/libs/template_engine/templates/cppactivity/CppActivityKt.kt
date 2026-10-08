package com.codeforge.libs.template_engine.templates.cppactivity

fun CppActivityKt(pkg: String) = """
    package $pkg

    import androidx.appcompat.app.AppCompatActivity
    import android.os.Bundle
    import android.widget.Toast
    import $pkg.databinding.ActivityMainBinding

    public class MainActivity : AppCompatActivity() {

        companion object {
            init {
                System.loadLibrary("tomaslib")
            }
        }

        private var _binding: ActivityMainBinding? = null

        private val binding: ActivityMainBinding
          get() = checkNotNull(_binding) { "Activity has been destroyed" }

        external fun sayHello(): String

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)

            _binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)

            val message = sayHello()
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }

        override fun onDestroy() {
            super.onDestroy()
            _binding = null
        }
    }
""".trimIndent()
