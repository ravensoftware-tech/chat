package tech.ravensoftware.chat;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import androidx.appcompat.app.AppCompatDelegate;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.DynamicColorsOptions;
import com.google.common.base.Stopwatch;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.Iterables;
import tech.ravensoftware.chat.entities.Account;
import tech.ravensoftware.chat.persistance.DatabaseBackend;
import tech.ravensoftware.chat.services.EmojiInitializationService;
import tech.ravensoftware.chat.utils.ExceptionHelper;
import java.security.Security;
import java.util.Collection;
import org.conscrypt.Conscrypt;

public class Chat extends Application {

    @SuppressLint("StaticFieldLeak")
    private static Context CONTEXT;

    public static Context getContext() {
        return Chat.CONTEXT;
    }

    private final Supplier<Collection<DatabaseBackend.AccountWithOptions>>
            accountWithOptionsSupplier =
                    () -> {
                        final var stopwatch = Stopwatch.createStarted();

                        final var accounts =
                                DatabaseBackend.getInstance(Chat.this)
                                        .getAccountWithOptions();
                        Log.d(
                                Config.LOGTAG,
                                "fetching accounts from database in " + stopwatch.stop());
                        return accounts;
                    };
    private Supplier<Collection<DatabaseBackend.AccountWithOptions>> accountWithOptions =
            Suppliers.memoize(accountWithOptionsSupplier);

    @Override
    public void onCreate() {
        super.onCreate();
        installSecurityProvider();
        CONTEXT = this.getApplicationContext();
        EmojiInitializationService.execute(getApplicationContext());
        ExceptionHelper.init(getApplicationContext());
        applyThemeSettings();
    }

    private static void installSecurityProvider() {
        try {
            Security.insertProviderAt(Conscrypt.newProvider(), 1);
        } catch (final Throwable throwable) {
            Log.e(Config.LOGTAG, "could not install security provider", throwable);
        }
    }

    public static Chat getInstance(final Context context) {
        if (context.getApplicationContext() instanceof Chat c) {
            return c;
        }
        throw new IllegalStateException("Application is not Chat");
    }

    public void applyThemeSettings() {
        final var sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (sharedPreferences == null) {
            return;
        }
        applyThemeSettings(sharedPreferences);
    }

    private void applyThemeSettings(final SharedPreferences sharedPreferences) {
        AppCompatDelegate.setDefaultNightMode(getDesiredNightMode(this, sharedPreferences));
        var dynamicColorsOptions =
                new DynamicColorsOptions.Builder()
                        .setPrecondition((activity, t) -> isDynamicColorsDesired(activity))
                        .build();
        DynamicColors.applyToActivitiesIfAvailable(this, dynamicColorsOptions);
    }

    public static int getDesiredNightMode(final Context context) {
        final var sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        if (sharedPreferences == null) {
            return AppCompatDelegate.getDefaultNightMode();
        }
        return getDesiredNightMode(context, sharedPreferences);
    }

    public static boolean isDynamicColorsDesired(final Context context) {
        final var preferences = PreferenceManager.getDefaultSharedPreferences(context);
        return preferences.getBoolean(AppSettings.DYNAMIC_COLORS, false);
    }

    private static int getDesiredNightMode(
            final Context context, final SharedPreferences sharedPreferences) {
        final String theme =
                sharedPreferences.getString(AppSettings.THEME, context.getString(R.string.theme));
        return getDesiredNightMode(theme);
    }

    public static int getDesiredNightMode(final String theme) {
        if ("automatic".equals(theme)) {
            return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        } else if ("light".equals(theme)) {
            return AppCompatDelegate.MODE_NIGHT_NO;
        } else {
            return AppCompatDelegate.MODE_NIGHT_YES;
        }
    }

    public void resetAccounts() {
        this.accountWithOptions = Suppliers.memoize(accountWithOptionsSupplier);
    }

    public Collection<DatabaseBackend.AccountWithOptions> getAccounts() {
        return this.accountWithOptions.get();
    }

    public boolean hasEnabledAccount() {
        return Iterables.any(
                getAccounts(),
                a ->
                        !a.isOptionSet(Account.OPTION_DISABLED)
                                && !a.isOptionSet(Account.OPTION_SOFT_DISABLED));
    }
}
