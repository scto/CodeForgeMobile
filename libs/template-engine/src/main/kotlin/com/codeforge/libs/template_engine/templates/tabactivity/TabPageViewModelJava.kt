package com.codeforge.libs.template_engine.templates.tabactivity

fun TabPageViewModelJava(pkg: String) = """
    package $pkg.ui.main;

    import androidx.lifecycle.LiveData;
    import androidx.lifecycle.MediatorLiveData;
    import androidx.lifecycle.MutableLiveData;
    import androidx.lifecycle.ViewModel;

    public class PageViewModel extends ViewModel {

        private MutableLiveData<Integer> mIndex = new MutableLiveData<>();
        private MediatorLiveData<String> mText = new MediatorLiveData<>();

        public PageViewModel() {
            mText.addSource(mIndex, index -> {
                if (index != null) {
                    mText.setValue("Hello world from section: " + index);
                }
            });
        }

        public void setIndex(int index) {
            mIndex.setValue(index);
        }

        public LiveData<String> getText() {
            return mText;
        }
    }
""".trimIndent()
