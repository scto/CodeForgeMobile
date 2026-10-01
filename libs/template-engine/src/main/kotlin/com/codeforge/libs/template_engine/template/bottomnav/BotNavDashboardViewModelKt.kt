package com.codeforge.libs.template_engine.template.bottomnav

fun BotNavDashboardViewModelKt(pkg: String) = """
    package $pkg.ui.dashboard

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
