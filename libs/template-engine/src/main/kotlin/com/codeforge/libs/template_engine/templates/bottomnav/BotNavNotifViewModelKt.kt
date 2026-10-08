package com.codeforge.libs.template_engine.templates.bottomnav

fun BotNavNotifViewModelKt(pkg: String) = """
    package $pkg.ui.notifications

    import androidx.lifecycle.LiveData
    import androidx.lifecycle.MutableLiveData
    import androidx.lifecycle.ViewModel

    class NotificationsViewModel : ViewModel() {

        private val _text = MutableLiveData<String>().apply {
            value = "This is notifications Fragment"
        }
        val text: LiveData<String> = _text
    }
""".trimIndent()
