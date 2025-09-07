package tech.ravensoftware.chat.ui;

import android.net.Uri;
import android.os.Bundle;
import androidx.databinding.DataBindingUtil;
import tech.ravensoftware.chat.R;
import tech.ravensoftware.chat.databinding.ActivityViewProfilePictureBinding;
import tech.ravensoftware.chat.persistance.FileBackend;

public class ViewProfilePictureActivity extends ActionBarActivity {

    public static final String EXTRA_DISPLAY_NAME = "tech.ravensoftware.chat.extra.DISPLAY_NAME";

    private ActivityViewProfilePictureBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        this.binding = DataBindingUtil.setContentView(this, R.layout.activity_view_profile_picture);
        Activities.setStatusAndNavigationBarColors(this, binding.getRoot(), false, false);

        setSupportActionBar(binding.toolbar);
        configureActionBar(getSupportActionBar());
    }

    @Override
    public void onStart() {
        super.onStart();
        final var intent = getIntent();
        if (intent == null) {
            return;
        }
        final var uri = intent.getData();
        if (uri == null) {
            return;
        }
        final var avatar = uri.getSchemeSpecificPart();
        if (avatar == null) {
            return;
        }
        final var displayName = intent.getStringExtra(EXTRA_DISPLAY_NAME);
        final var file = FileBackend.getAvatarFile(this, avatar);
        this.binding.imageView.setImageURI(Uri.fromFile(file));
        setTitle(displayName);
    }
}
