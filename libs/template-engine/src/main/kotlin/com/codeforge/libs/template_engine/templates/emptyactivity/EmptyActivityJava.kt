package com.codeforge.libs.template_engine.templates.emptyactivity

fun EmptyActivityJava(pkg: String) = """
    package $pkg;

    import androidx.appcompat.app.AppCompatActivity;
    import android.os.Bundle;
    import $pkg.databinding.ActivityMainBinding;

    public class MainActivity extends AppCompatActivity {
        private ActivityMainBinding binding;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            binding = ActivityMainBinding.inflate(getLayoutInflater());
            setContentView(binding.getRoot());
        }

        @Override
        protected void onDestroy() {
            super.onDestroy();
            this.binding = null;
        }
    }
""".trimIndent()
