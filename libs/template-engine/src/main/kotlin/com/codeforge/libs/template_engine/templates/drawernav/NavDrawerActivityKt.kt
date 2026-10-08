package com.codeforge.libs.template_engine.templates.drawernav

fun NavDrawerActivityKt(pkg: String) = """
    package $pkg

    import android.os.Bundle
    import android.view.Menu
    import androidx.appcompat.app.AppCompatActivity
    import androidx.navigation.findNavController
    import androidx.navigation.ui.AppBarConfiguration
    import androidx.navigation.ui.navigateUp
    import androidx.navigation.ui.setupActionBarWithNavController
    import androidx.navigation.ui.setupWithNavController
    import com.google.android.material.snackbar.Snackbar
    import $pkg.databinding.ActivityMainBinding

    class MainActivity : AppCompatActivity() {

        private lateinit var appBarConfiguration: AppBarConfiguration
        private lateinit var binding: ActivityMainBinding

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)

            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)

            setSupportActionBar(binding.appBarMain.toolbar)

            binding.appBarMain.fab.setOnClickListener { view ->
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
            }

            val navController = findNavController(R.id.nav_host_fragment_content_main)

            appBarConfiguration = AppBarConfiguration(
                setOf(
                    R.id.nav_home, R.id.nav_gallery, R.id.nav_slideshow
                ), binding.drawerLayout
            )

            setupActionBarWithNavController(navController, appBarConfiguration)
            binding.navView.setupWithNavController(navController)
        }

        override fun onCreateOptionsMenu(menu: Menu): Boolean {
            menuInflater.inflate(R.menu.main, menu)
            return true
        }

        override fun onSupportNavigateUp(): Boolean {
            val navController = findNavController(R.id.nav_host_fragment_content_main)
            return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
        }
    }
""".trimIndent()
