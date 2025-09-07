package tech.ravensoftware.chat.ui.fragment.settings;

import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.common.base.Strings;
import tech.ravensoftware.chat.AppSettings;
import tech.ravensoftware.chat.Config;
import tech.ravensoftware.chat.R;
import tech.ravensoftware.chat.entities.Account;
import tech.ravensoftware.chat.services.QuickChatService;
import tech.ravensoftware.chat.utils.Resolver;
import java.util.Arrays;

public class ConnectionSettingsFragment extends XmppPreferenceFragment {

    private static final String GROUPS_AND_CONFERENCES = "groups_and_conferences";

    public static boolean hideChannelDiscovery() {
        return QuickChatService.isQuicksy()
                || QuickChatService.isPlayStoreFlavor()
                || Strings.isNullOrEmpty(Config.CHANNEL_DISCOVERY);
    }

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences_connection, rootKey);
        final var connectionOptions = findPreference(AppSettings.SHOW_CONNECTION_OPTIONS);
        final var channelDiscovery = findPreference(AppSettings.CHANNEL_DISCOVERY_METHOD);
        final var groupsAndConferences = findPreference(GROUPS_AND_CONFERENCES);
        if (connectionOptions == null || channelDiscovery == null || groupsAndConferences == null) {
            throw new IllegalStateException();
        }
        if (QuickChatService.isQuicksy()) {
            connectionOptions.setVisible(false);
        }
        if (hideChannelDiscovery()) {
            groupsAndConferences.setVisible(false);
            channelDiscovery.setVisible(false);
        }
    }

    @Override
    protected void onSharedPreferenceChanged(@NonNull String key) {
        super.onSharedPreferenceChanged(key);
        switch (key) {
            case AppSettings.USE_TOR -> {
                final var appSettings = new AppSettings(requireContext());
                if (appSettings.isUseTor()) {
                    runOnUiThread(
                            () ->
                                    Toast.makeText(
                                                    requireActivity(),
                                                    R.string.audio_video_disabled_tor,
                                                    Toast.LENGTH_LONG)
                                            .show());
                }
                reconnectAccounts();
                requireService().reinitializeMuclumbusService();
            }
            case AppSettings.SHOW_CONNECTION_OPTIONS -> reconnectAccounts();
        }
        if (Arrays.asList(AppSettings.USE_TOR, AppSettings.SHOW_CONNECTION_OPTIONS).contains(key)) {
            final var appSettings = new AppSettings(requireContext());
            if (appSettings.isUseTor() || appSettings.isExtendedConnectionOptions()) {
                return;
            }
            resetUserDefinedHostname();
        }
    }

    private void resetUserDefinedHostname() {
        final var service = requireService();
        for (final Account account : service.getAccounts()) {
            Log.d(
                    Config.LOGTAG,
                    account.getJid().asBareJid() + ": resetting hostname and port to defaults");
            account.setHostname(null);
            account.setPort(Resolver.XMPP_PORT_STARTTLS);
            service.databaseBackend.updateAccount(account);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        requireActivity().setTitle(R.string.pref_connection_options);
    }
}
