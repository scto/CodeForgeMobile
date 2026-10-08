package com.codeforge.libs.template_engine.templates.bottomnav

fun BotNavHomeViewModelKt(pkg: String) = """
    package $pkg.ui.home

    import androidx.lifecycle.LiveData
    import androidx.lifecycle.MutableLiveData
    import androidx.lifecycle.ViewModel

    class HomeViewModel : ViewModel() {

        private val _text = MutableLiveData<String>().apply {
            value = "This is home Fragment"
        }
        val text: LiveData<String> = _text
    }
""".trimIndent()
