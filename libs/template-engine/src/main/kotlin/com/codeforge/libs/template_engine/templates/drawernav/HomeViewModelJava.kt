package com.codeforge.libs.template_engine.templates.drawernav

/**
 * Liefert den Text der Vorlagendatei `HomeViewModelJava`.
 *
 * @param pkg Package-Name des Projekts.
 */
fun HomeViewModelJava(pkg: String) = """
    package $pkg.ui.home;

    import androidx.lifecycle.LiveData;
    import androidx.lifecycle.MutableLiveData;
    import androidx.lifecycle.ViewModel;

    public class HomeViewModel extends ViewModel {

        private final MutableLiveData<String> mText;

        public HomeViewModel() {
            mText = new MutableLiveData<>();
            mText.setValue("This is home fragment");
        }

        public LiveData<String> getText() {
            return mText;
        }
    }
""".trimIndent()
