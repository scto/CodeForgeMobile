package com.codeforge.libs.template_engine.templates.bottomnav

fun BotNavNotifViewModelJava(pkg: String) = """
    package $pkg.ui.notifications;

    import androidx.lifecycle.LiveData;
    import androidx.lifecycle.MutableLiveData;
    import androidx.lifecycle.ViewModel;

    public class NotificationsViewModel extends ViewModel {

        private final MutableLiveData<String> mText;

        public NotificationsViewModel() {
            mText = new MutableLiveData<>();
            mText.setValue("This is notifications fragment");
        }

        public LiveData<String> getText() {
            return mText;
        }
    }
""".trimIndent()
