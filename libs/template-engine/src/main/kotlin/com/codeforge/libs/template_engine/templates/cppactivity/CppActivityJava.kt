package com.codeforge.libs.template_engine.templates.cppactivity

fun CppActivityJava(pkg: String) = """
    package $pkg;

    import androidx.appcompat.app.AppCompatActivity;
    import android.os.Bundle;
    import android.widget.Toast;
    import $pkg.databinding.ActivityMainBinding;

    public class MainActivity extends AppCompatActivity {
        static {
            System.loadLibrary("tomaslib");
        }

        private ActivityMainBinding binding;

        public native String sayHello();

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            binding = ActivityMainBinding.inflate(getLayoutInflater());
            setContentView(binding.getRoot());
            String message = sayHello();
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        }

        @Override
        protected void onDestroy() {
            super.onDestroy();
            this.binding = null;
        }
    }
""".trimIndent()
