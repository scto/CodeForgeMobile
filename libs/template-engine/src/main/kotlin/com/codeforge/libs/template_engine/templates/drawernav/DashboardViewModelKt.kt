package com.codeforge.libs.template_engine.templates.drawernav

/**
 * Liefert den Text der Vorlagendatei `DashboardViewModelKt`.
 *
 * @param pkg Package-Name des Projekts.
 */
fun DashboardViewModelKt(pkg: String) = """
    package $pkg.ui.dashboard;

    import androidx.lifecycle.LiveData
    import androidx.lifecycle.MutableLiveData
    import androidx.lifecycle.ViewModel

    class DashboardViewModel : ViewModel() {

        private val _text = MutableLiveData<String>().apply {
            value = "This is dashboard Fragment"
        }
        val text: LiveData<String> = _text
    }
""".trimIndent()
