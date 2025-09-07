package tech.ravensoftware.chat.ui;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import tech.ravensoftware.chat.utils.SignupUtils;

public class ConversationActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startActivity(SignupUtils.getRedirectionIntent(this));
        finish();
    }
}
