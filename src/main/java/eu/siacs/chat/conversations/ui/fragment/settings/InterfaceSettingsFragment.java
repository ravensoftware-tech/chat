package tech.ravensoftware.chat.ui.fragment.settings;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;


import tech.ravensoftware.chat.AppSettings;
import tech.ravensoftware.chat.R;
import tech.ravensoftware.chat.ui.activity.SettingsActivity;
import tech.ravensoftware.chat.ui.util.SettingsUtils;

public class InterfaceSettingsFragment extends XmppPreferenceFragment {

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences_interface, rootKey);
    }

    @Override
    protected void onSharedPreferenceChanged(@NonNull String key) {
        super.onSharedPreferenceChanged(key);
        if (key.equals(AppSettings.ALLOW_SCREENSHOTS)) {
            SettingsUtils.applyScreenshotSetting(requireActivity());
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        requireActivity().setTitle(R.string.pref_title_interface);
    }

    public SettingsActivity requireSettingsActivity() {
        final var activity = requireActivity();
        if (activity instanceof SettingsActivity settingsActivity) {
            return settingsActivity;
        }
        throw new IllegalStateException(
                String.format(
                        "%s is not %s",
                        activity.getClass().getName(), SettingsActivity.class.getName()));
    }
}
