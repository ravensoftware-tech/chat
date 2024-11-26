package eu.siacs.conversations.ui.fragment.settings;

import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.Nullable;
import androidx.preference.Preference;

import com.google.common.base.Optional;

import eu.siacs.conversations.AppSettings;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.services.NotificationService;
import eu.siacs.conversations.ui.activity.result.PickRingtone;

public class NotificationsSettingsFragment extends XmppPreferenceFragment {

    private final ActivityResultLauncher<Uri> pickRingtoneLauncher =
            registerForActivityResult(
                    new PickRingtone(RingtoneManager.TYPE_RINGTONE),
                    result -> {
                        if (result == null) {
                            // do nothing. user aborted
                            return;
                        }
                        final Uri uri = PickRingtone.noneToNull(result);
                        appSettings().setRingtone(uri);
                        Log.i(Config.LOGTAG, "User set ringtone to " + uri);
                        NotificationService.recreateIncomingCallChannel(requireContext(), uri);
                    });

    @Override
    public void onCreatePreferences(
            @Nullable final Bundle savedInstanceState, final @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences_notifications, rootKey);
        final var messageNotificationSettings = findPreference("message_notification_settings");
        final var fullscreenNotification = findPreference("fullscreen_notification");
        if (messageNotificationSettings == null
                || fullscreenNotification == null) {
            throw new IllegalStateException("The preference resource file is missing preferences");
        }
        fullscreenNotification.setOnPreferenceClickListener(this::manageAppUseFullScreen);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                || requireContext()
                        .getSystemService(NotificationManager.class)
                        .canUseFullScreenIntent()) {
            fullscreenNotification.setVisible(false);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        final var fullscreenNotification = findPreference("fullscreen_notification");
        if (fullscreenNotification == null) {
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                || requireContext()
                        .getSystemService(NotificationManager.class)
                        .canUseFullScreenIntent()) {
            fullscreenNotification.setVisible(false);
        }
    }

    private boolean manageAppUseFullScreen(final Preference preference) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return false;
        }
        final var intent = new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT);
        intent.setData(Uri.parse(String.format("package:%s", requireContext().getPackageName())));
        try {
            startActivity(intent);
        } catch (final ActivityNotFoundException e) {
            Toast.makeText(requireContext(), R.string.unsupported_operation, Toast.LENGTH_SHORT)
                    .show();
            return false;
        }
        return true;
    }

    @Override
    public void onStart() {
        super.onStart();
        requireActivity().setTitle(R.string.notifications);
    }

    @Override
    public boolean onPreferenceTreeClick(final Preference preference) {
        final var key = preference.getKey();
        if (AppSettings.RINGTONE.equals(key)) {
            pickRingtone();
            return true;
        }
        return super.onPreferenceTreeClick(preference);
    }

    private void pickRingtone() {
        final Optional<Uri> channelRingtone;
        channelRingtone =
                NotificationService.getCurrentIncomingCallChannel(requireContext())
                        .transform(channel -> PickRingtone.nullToNone(channel.getSound()));
        final Uri uri;
        if (channelRingtone.isPresent()) {
            uri = channelRingtone.get();
            Log.d(Config.LOGTAG, "ringtone came from channel");
        } else {
            uri = appSettings().getRingtone();
        }
        Log.i(Config.LOGTAG, "current ringtone: " + uri);
        try {
            this.pickRingtoneLauncher.launch(uri);
        } catch (final ActivityNotFoundException e) {
            Toast.makeText(requireActivity(), R.string.no_application_found, Toast.LENGTH_LONG)
                    .show();
        }
    }

    private AppSettings appSettings() {
        return new AppSettings(requireContext());
    }
}
