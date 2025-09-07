package tech.ravensoftware.chat.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.google.common.collect.Iterables;
import tech.ravensoftware.chat.Config;
import tech.ravensoftware.chat.Chat;
import tech.ravensoftware.chat.entities.Account;
import tech.ravensoftware.chat.persistance.DatabaseBackend;
import tech.ravensoftware.chat.ui.ChatActivity;
import tech.ravensoftware.chat.ui.EditAccountActivity;
import tech.ravensoftware.chat.ui.MagicCreateActivity;
import tech.ravensoftware.chat.ui.ManageAccountActivity;
import tech.ravensoftware.chat.ui.PickServerActivity;
import tech.ravensoftware.chat.ui.WelcomeActivity;
import tech.ravensoftware.chat.xmpp.Jid;
import java.util.Collection;

public class SignupUtils {

    public static boolean isSupportTokenRegistry() {
        return true;
    }

    public static Intent getTokenRegistrationIntent(
            final Activity activity, Jid jid, String preAuth) {
        final Intent intent = new Intent(activity, MagicCreateActivity.class);
        if (jid.isDomainJid()) {
            intent.putExtra(MagicCreateActivity.EXTRA_DOMAIN, jid.getDomain().toString());
        } else {
            intent.putExtra(MagicCreateActivity.EXTRA_DOMAIN, jid.getDomain().toString());
            intent.putExtra(MagicCreateActivity.EXTRA_USERNAME, jid.getLocal());
        }
        intent.putExtra(MagicCreateActivity.EXTRA_PRE_AUTH, preAuth);
        return intent;
    }

    public static Intent getSignUpIntent(final Activity activity) {
        return getSignUpIntent(activity, false);
    }

    public static Intent getSignUpIntent(final Activity activity, final boolean toServerChooser) {
        final Intent intent;
        if (toServerChooser) {
            intent = new Intent(activity, PickServerActivity.class);
        } else {
            intent = new Intent(activity, WelcomeActivity.class);
        }
        return intent;
    }

    public static Intent getRedirectionIntent(final Context context) {
        final var state = getSetupState(Chat.getInstance(context).getAccounts());
        Log.d(Config.LOGTAG, "setup state: " + state);
        final Intent intent;
        if (state instanceof Done) {
            intent = new Intent(context, ChatActivity.class);
        } else if (state instanceof Pending pending) {
            final var account = pending.account();
            intent = new Intent(context, EditAccountActivity.class);
            intent.putExtra("jid", account.jid().asBareJid().toString());
            if (!account.isOptionSet(Account.OPTION_MAGIC_CREATE)) {
                intent.putExtra(
                        EditAccountActivity.EXTRA_FORCE_REGISTER,
                        account.isOptionSet(Account.OPTION_REGISTER));
            }
        } else if (state instanceof None) {
            if (Config.X509_VERIFICATION) {
                intent = new Intent(context, ManageAccountActivity.class);
            } else if (Config.MAGIC_CREATE_DOMAIN != null) {
                intent = new Intent(context, WelcomeActivity.class);
            } else {
                intent = new Intent(context, EditAccountActivity.class);
            }
        } else {
            throw new AssertionError("Invalid setup state");
        }
        intent.putExtra("init", true);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        return intent;
    }

    private static SetupState getSetupState(
            final Collection<DatabaseBackend.AccountWithOptions> accounts) {
        if (accounts.isEmpty()) {
            return new None();
        }
        final var pending =
                Iterables.all(accounts, a -> !a.isOptionSet(Account.OPTION_LOGGED_IN_SUCCESSFULLY));
        if (pending) {
            return new Pending(Iterables.getFirst(accounts, null));
        }
        return new Done();
    }

    public sealed interface SetupState permits None, Pending, Done {}

    public record None() implements SetupState {}

    public record Pending(DatabaseBackend.AccountWithOptions account) implements SetupState {}

    public record Done() implements SetupState {}
}
