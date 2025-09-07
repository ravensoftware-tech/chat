package tech.ravensoftware.chat.utils;

import android.app.Activity;
import android.content.Intent;
import android.widget.Toast;
import tech.ravensoftware.chat.R;
import tech.ravensoftware.chat.utils.Compatibility;
import tech.ravensoftware.chat.entities.AccountConfiguration;
import tech.ravensoftware.chat.persistance.DatabaseBackend;
import tech.ravensoftware.chat.services.XmppConnectionService;
import tech.ravensoftware.chat.ui.EditAccountActivity;
import tech.ravensoftware.chat.xmpp.Jid;

public class ProvisioningUtils {

    public static void provision(final Activity activity, final String json) {
        final AccountConfiguration accountConfiguration;
        try {
            accountConfiguration = AccountConfiguration.parse(json);
        } catch (final IllegalArgumentException e) {
            Toast.makeText(activity, R.string.improperly_formatted_provisioning, Toast.LENGTH_LONG)
                    .show();
            return;
        }
        final Jid jid = accountConfiguration.getJid();
        final var accounts = DatabaseBackend.getInstance(activity).getAccountAddresses(true);
        if (accounts.contains(jid)) {
            Toast.makeText(activity, R.string.account_already_exists, Toast.LENGTH_LONG).show();
            return;
        }
        final Intent serviceIntent = new Intent(activity, XmppConnectionService.class);
        serviceIntent.setAction(XmppConnectionService.ACTION_PROVISION_ACCOUNT);
        serviceIntent.putExtra("address", jid.asBareJid().toString());
        serviceIntent.putExtra("password", accountConfiguration.password);
        Compatibility.startService(activity, serviceIntent);
        final Intent intent = new Intent(activity, EditAccountActivity.class);
        intent.putExtra("jid", jid.asBareJid().toString());
        intent.putExtra("init", true);
        activity.startActivity(intent);
    }
}
