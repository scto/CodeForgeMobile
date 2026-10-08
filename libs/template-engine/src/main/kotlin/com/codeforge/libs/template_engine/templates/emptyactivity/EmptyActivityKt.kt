package com.codeforge.libs.template_engine.templates.emptyactivity

fun EmptyActivityKt(pkg: String) = """
    package $pkg

    import androidx.appcompat.app.AppCompatActivity
    import android.os.Bundle
    import $pkg.databinding.ActivityMainBinding

    public class MainActivity : AppCompatActivity() {

        private var _binding: ActivityMainBinding? = null

        private val binding: ActivityMainBinding
          get() = checkNotNull(_binding) { "Activity has been destroyed" }

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)

            _binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)
        }

        override fun onDestroy() {
            super.onDestroy()
            _binding = null
        }
    }
""".trimIndent()
