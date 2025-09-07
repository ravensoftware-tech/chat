package tech.ravensoftware.chat.ui.fragment.settings;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceFragmentCompat;

import tech.ravensoftware.chat.R;
import tech.ravensoftware.chat.utils.UIHelper;

public class AttachmentsSettingsFragment extends XmppPreferenceFragment {

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences_attachments, rootKey);
        final ListPreference autoAcceptFileSize = findPreference("auto_accept_file_size");
        if (autoAcceptFileSize == null) {
            throw new IllegalStateException("The preference resource file is missing preferences");
        }
        setValues(
                autoAcceptFileSize,
                R.array.file_size_values,
                value -> {
                    if (value <= 0) {
                        return getString(R.string.never);
                    } else {
                        return UIHelper.filesizeToString(value);
                    }
                });
    }

    @Override
    public void onStart() {
        super.onStart();
        requireActivity().setTitle(R.string.pref_attachments);
    }
}
