package com.codeforge.libs.template_engine.templates.tabactivity

fun TabActivityJava(pkg: String) = """
    package $pkg;

    import android.os.Bundle;

    import com.google.android.material.snackbar.Snackbar;

    import androidx.appcompat.app.AppCompatActivity;

    import android.view.View;

    import $pkg.ui.main.SectionsPagerAdapter;
    import $pkg.databinding.ActivityMainBinding;

    public class MainActivity extends AppCompatActivity {

        private ActivityMainBinding binding;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            binding = ActivityMainBinding.inflate(getLayoutInflater());
            setContentView(binding.getRoot());

            SectionsPagerAdapter sectionsPagerAdapter = new SectionsPagerAdapter(this, getSupportFragmentManager());
            binding.viewPager.setAdapter(sectionsPagerAdapter);

            binding.tabs.setupWithViewPager(binding.viewPager);

            binding.fab.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                            .setAction("Action", null)
                            .setAnchorView(R.id.fab).show();
                }
            });
        }
    }
""".trimIndent()
