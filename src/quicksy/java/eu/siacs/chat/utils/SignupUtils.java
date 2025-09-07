package tech.ravensoftware.chat.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.preference.PreferenceManager;
import android.util.Log;
import com.google.common.collect.Iterables;
import tech.ravensoftware.chat.Config;
import tech.ravensoftware.chat.Chat;
import tech.ravensoftware.chat.entities.Account;
import tech.ravensoftware.chat.ui.ChatActivity;
import tech.ravensoftware.chat.ui.EnterPhoneNumberActivity;
import tech.ravensoftware.chat.ui.TosActivity;
import tech.ravensoftware.chat.ui.VerifyActivity;
import tech.ravensoftware.chat.xmpp.Jid;

public class SignupUtils {

    public static Intent getSignUpIntent(Activity activity, boolean ignored) {
        return getSignUpIntent(activity);
    }

    public static Intent getSignUpIntent(final Context context) {
        final var preferences = PreferenceManager.getDefaultSharedPreferences(context);
        if (preferences.getBoolean("tos", false)) {
            return new Intent(context, EnterPhoneNumberActivity.class);
        } else {
            return new Intent(context, TosActivity.class);
        }
    }

    public static Intent getRedirectionIntent(final Context context) {
        final var accounts = Chat.getInstance(context).getAccounts();
        Log.d(Config.LOGTAG, "getRedirection intent " + accounts);
        final Intent intent;
        final var account = Iterables.getFirst(accounts, null);
        if (account != null) {
            if (account.isOptionSet(Account.OPTION_UNVERIFIED)) {
                intent = new Intent(context, VerifyActivity.class);
            } else {
                intent = new Intent(context, ChatActivity.class);
            }
        } else {
            intent = getSignUpIntent(context);
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        return intent;
    }

    public static boolean isSupportTokenRegistry() {
        return false;
    }

    public static Intent getTokenRegistrationIntent(Activity activity, Jid preset, String key) {
        return null;
    }
}
