package com.codeforge.libs.template_engine.template.tabactivity

fun PageViewModelKt(pkg: String) = """
    package $pkg.ui.main

    import androidx.lifecycle.LiveData
    import androidx.lifecycle.MutableLiveData
    import androidx.lifecycle.map
    import androidx.lifecycle.ViewModel

    class PageViewModel : ViewModel() {

        private val _index = MutableLiveData<Int>()
        val text: LiveData<String> = _index.map {
            "Hello world from section: ${'$'}it"
        }

        fun setIndex(index: Int) {
            _index.value = index
        }
    }
""".trimIndent()
