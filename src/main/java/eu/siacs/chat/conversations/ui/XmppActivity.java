package tech.ravensoftware.chat.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.text.Html;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.databinding.DataBindingUtil;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import tech.ravensoftware.chat.AppSettings;
import tech.ravensoftware.chat.BuildConfig;
import tech.ravensoftware.chat.Config;
import tech.ravensoftware.chat.R;
import tech.ravensoftware.chat.crypto.PgpEngine;
import tech.ravensoftware.chat.databinding.DialogAddReactionBinding;
import tech.ravensoftware.chat.databinding.DialogQuickeditBinding;
import tech.ravensoftware.chat.entities.Account;
import tech.ravensoftware.chat.entities.Contact;
import tech.ravensoftware.chat.entities.Conversation;
import tech.ravensoftware.chat.entities.Message;
import tech.ravensoftware.chat.entities.Reaction;
import tech.ravensoftware.chat.services.AvatarService;
import tech.ravensoftware.chat.services.BarcodeProvider;
import tech.ravensoftware.chat.services.NotificationService;
import tech.ravensoftware.chat.services.XmppConnectionService;
import tech.ravensoftware.chat.services.XmppConnectionService.XmppConnectionBinder;
import tech.ravensoftware.chat.ui.util.MenuDoubleTabUtil;
import tech.ravensoftware.chat.ui.util.PresenceSelector;
import tech.ravensoftware.chat.ui.util.SettingsUtils;
import tech.ravensoftware.chat.ui.util.SoftKeyboardUtils;
import tech.ravensoftware.chat.utils.AccountUtils;
import tech.ravensoftware.chat.utils.Compatibility;
import tech.ravensoftware.chat.utils.SignupUtils;
import tech.ravensoftware.chat.xmpp.Jid;
import tech.ravensoftware.chat.xmpp.OnKeyStatusUpdated;
import tech.ravensoftware.chat.xmpp.OnUpdateBlocklist;
import tech.ravensoftware.chat.xmpp.manager.PresenceManager;
import tech.ravensoftware.chat.xmpp.manager.RegistrationManager;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

public abstract class XmppActivity extends ActionBarActivity {

    public static final String EXTRA_ACCOUNT = "account";
    protected static final int REQUEST_ANNOUNCE_PGP = 0x0101;
    protected static final int REQUEST_INVITE_TO_CONVERSATION = 0x0102;
    protected static final int REQUEST_CHOOSE_PGP_ID = 0x0103;
    protected static final int REQUEST_BATTERY_OP = 0x49ff;
    protected static final int REQUEST_POST_NOTIFICATION = 0x50ff;
    public XmppConnectionService xmppConnectionService;
    public boolean xmppConnectionServiceBound = false;

    protected static final String FRAGMENT_TAG_DIALOG = "dialog";

    private boolean isCameraFeatureAvailable = false;

    protected boolean mUsingEnterKey = false;
    protected boolean mUseTor = false;
    protected boolean mShowLastUserInteraction = false;
    protected Toast mToast;
    public Runnable onOpenPGPKeyPublished =
            () ->
                    Toast.makeText(
                                    XmppActivity.this,
                                    R.string.openpgp_has_been_published,
                                    Toast.LENGTH_SHORT)
                            .show();
    protected ConferenceInvite mPendingConferenceInvite = null;
    protected ServiceConnection mConnection =
            new ServiceConnection() {

                @Override
                public void onServiceConnected(ComponentName className, IBinder service) {
                    XmppConnectionBinder binder = (XmppConnectionBinder) service;
                    xmppConnectionService = binder.getService();
                    xmppConnectionServiceBound = true;
                    registerListeners();
                    onBackendConnected();
                }

                @Override
                public void onServiceDisconnected(ComponentName arg0) {
                    xmppConnectionServiceBound = false;
                }
            };
    private DisplayMetrics metrics;
    private long mLastUiRefresh = 0;
    private final Handler mRefreshUiHandler = new Handler();
    private final Runnable mRefreshUiRunnable =
            () -> {
                mLastUiRefresh = SystemClock.elapsedRealtime();
                refreshUiReal();
            };
    protected final FutureCallback<Conversation> adhocCallback =
            new FutureCallback<Conversation>() {
                @Override
                public void onSuccess(final Conversation conversation) {
                    switchToConversation(conversation);
                    hideToast();
                }

                @Override
                public void onFailure(@NonNull Throwable throwable) {
                    Log.d(Config.LOGTAG, "could not create adhoc conference", throwable);
                    hideToast();
                    mToast =
                            Toast.makeText(
                                    XmppActivity.this,
                                    R.string.conference_creation_failed,
                                    Toast.LENGTH_LONG);
                    mToast.show();
                }
            };

    public static boolean cancelPotentialWork(Message message, ImageView imageView) {
        final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);

        if (bitmapWorkerTask != null) {
            final Message oldMessage = bitmapWorkerTask.message;
            if (oldMessage == null || message != oldMessage) {
                bitmapWorkerTask.cancel(true);
            } else {
                return false;
            }
        }
        return true;
    }

    private static BitmapWorkerTask getBitmapWorkerTask(ImageView imageView) {
        if (imageView != null) {
            final Drawable drawable = imageView.getDrawable();
            if (drawable instanceof AsyncDrawable asyncDrawable) {
                return asyncDrawable.getBitmapWorkerTask();
            }
        }
        return null;
    }

    protected void hideToast() {
        final var toast = this.mToast;
        if (toast == null) {
            return;
        }
        toast.cancel();
    }

    protected void replaceToast(String msg) {
        replaceToast(msg, true);
    }

    protected void replaceToast(String msg, boolean showlong) {
        hideToast();
        mToast = Toast.makeText(this, msg, showlong ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT);
        mToast.show();
    }

    protected final void refreshUi() {
        final long diff = SystemClock.elapsedRealtime() - mLastUiRefresh;
        if (diff > Config.REFRESH_UI_INTERVAL) {
            mRefreshUiHandler.removeCallbacks(mRefreshUiRunnable);
            runOnUiThread(mRefreshUiRunnable);
        } else {
            final long next = Config.REFRESH_UI_INTERVAL - diff;
            mRefreshUiHandler.removeCallbacks(mRefreshUiRunnable);
            mRefreshUiHandler.postDelayed(mRefreshUiRunnable, next);
        }
    }

    protected abstract void refreshUiReal();

    @Override
    public void onStart() {
        super.onStart();
        if (!xmppConnectionServiceBound) {
            connectToBackend();
        } else {
            this.registerListeners();
            this.onBackendConnected();
        }
        final var appSettings = new AppSettings(this);
        this.mUsingEnterKey = appSettings.isDisplayEnterKey();
        this.mUseTor = appSettings.isUseTor();
        this.mShowLastUserInteraction = appSettings.isBroadcastLastActivity();
    }

    public void connectToBackend() {
        Intent intent = new Intent(this, XmppConnectionService.class);
        intent.setAction("ui");
        try {
            startService(intent);
        } catch (IllegalStateException e) {
            Log.w(Config.LOGTAG, "unable to start service from " + getClass().getSimpleName());
        }
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (xmppConnectionServiceBound) {
            this.unregisterListeners();
            unbindService(mConnection);
            xmppConnectionServiceBound = false;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    protected void configureCustomNotification(final ShortcutInfoCompat shortcut) {
        final var notificationManager = getSystemService(NotificationManager.class);
        final var channel =
                notificationManager.getNotificationChannel(
                        NotificationService.MESSAGES_NOTIFICATION_CHANNEL, shortcut.getId());
        if (channel != null && channel.getConversationId() != null) {
            ShortcutManagerCompat.pushDynamicShortcut(this, shortcut);
            openNotificationSettings(shortcut);
        } else {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.custom_notifications)
                    .setMessage(R.string.custom_notifications_enable)
                    .setPositiveButton(
                            R.string.continue_btn,
                            (d, w) -> {
                                NotificationService.createConversationChannel(this, shortcut);
                                ShortcutManagerCompat.pushDynamicShortcut(this, shortcut);
                                openNotificationSettings(shortcut);
                            })
                    .setNegativeButton(R.string.cancel, null)
                    .create()
                    .show();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    protected void openNotificationSettings(final ShortcutInfoCompat shortcut) {
        final var intent = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        intent.putExtra(
                Settings.EXTRA_CHANNEL_ID, NotificationService.MESSAGES_NOTIFICATION_CHANNEL);
        intent.putExtra(Settings.EXTRA_CONVERSATION_ID, shortcut.getId());
        startActivity(intent);
    }

    public boolean hasPgp() {
        return xmppConnectionService.getPgpEngine() != null;
    }

    public void showInstallPgpDialog() {
        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle(getString(R.string.openkeychain_required));
        builder.setIconAttribute(android.R.attr.alertDialogIcon);
        builder.setMessage(
                Html.fromHtml(
                        getString(
                                R.string.openkeychain_required_long,
                                getString(R.string.app_name))));
        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.setNeutralButton(
                getString(R.string.restart),
                (dialog, which) -> {
                    if (xmppConnectionServiceBound) {
                        unbindService(mConnection);
                        xmppConnectionServiceBound = false;
                    }
                    stopService(new Intent(XmppActivity.this, XmppConnectionService.class));
                    finish();
                });
        builder.setPositiveButton(
                getString(R.string.install),
                (dialog, which) -> {
                    final Uri uri =
                            Uri.parse("market://details?id=org.sufficientlysecure.keychain");
                    Intent marketIntent = new Intent(Intent.ACTION_VIEW, uri);
                    PackageManager manager = getApplicationContext().getPackageManager();
                    final var infos = manager.queryIntentActivities(marketIntent, 0);
                    if (infos.isEmpty()) {
                        final var website = Uri.parse("http://www.openkeychain.org/");
                        final Intent browserIntent = new Intent(Intent.ACTION_VIEW, website);
                        try {
                            startActivity(browserIntent);
                        } catch (final ActivityNotFoundException e) {
                            Toast.makeText(
                                            this,
                                            R.string.application_found_to_open_website,
                                            Toast.LENGTH_LONG)
                                    .show();
                        }
                    } else {
                        startActivity(marketIntent);
                    }
                    finish();
                });
        builder.create().show();
    }

    public void addReaction(final Message message, Consumer<Collection<String>> callback) {
        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        final var layoutInflater = this.getLayoutInflater();
        final DialogAddReactionBinding viewBinding =
                DataBindingUtil.inflate(layoutInflater, R.layout.dialog_add_reaction, null, false);
        builder.setView(viewBinding.getRoot());
        final var dialog = builder.create();
        for (final String emoji : Reaction.SUGGESTIONS) {
            final Button button =
                    (Button)
                            layoutInflater.inflate(
                                    R.layout.item_emoji_button, viewBinding.emojis, false);
            viewBinding.emojis.addView(button);
            button.setText(emoji);
            button.setOnClickListener(
                    v -> {
                        final var aggregated = message.getAggregatedReactions();
                        if (aggregated.ourReactions.contains(emoji)) {
                            callback.accept(aggregated.ourReactions);
                        } else {
                            final ImmutableSet.Builder<String> reactionBuilder =
                                    new ImmutableSet.Builder<>();
                            reactionBuilder.addAll(aggregated.ourReactions);
                            reactionBuilder.add(emoji);
                            callback.accept(reactionBuilder.build());
                        }
                        dialog.dismiss();
                    });
        }
        viewBinding.more.setOnClickListener(
                v -> {
                    dialog.dismiss();
                    final var intent = new Intent(this, AddReactionActivity.class);
                    intent.putExtra("conversation", message.getConversation().getUuid());
                    intent.putExtra("message", message.getUuid());
                    startActivity(intent);
                });
        dialog.show();
    }

    protected void deleteAccount(final Account account) {
        this.deleteAccount(account, null);
    }

    protected void deleteAccount(final Account account, final Runnable postDelete) {
        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        final View dialogView = getLayoutInflater().inflate(R.layout.dialog_delete_account, null);
        builder.setView(dialogView);
        builder.setTitle(R.string.mgmt_account_delete);
        builder.setPositiveButton(getString(R.string.delete), null);
        builder.setNegativeButton(getString(R.string.cancel), null);
        final AlertDialog dialog = builder.create();
        dialog.setOnShowListener(
                dialogInterface -> onShowDeleteDialog(dialogInterface, account, postDelete));
        dialog.show();
    }

    private void onShowDeleteDialog(
            final DialogInterface dialogInterface,
            final Account account,
            final Runnable postDelete) {
        final AlertDialog alertDialog;
        if (dialogInterface instanceof AlertDialog dialog) {
            alertDialog = dialog;
        } else {
            throw new IllegalStateException("DialogInterface was not of type AlertDialog");
        }
        final var button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        button.setOnClickListener(
                v -> onDeleteDialogButtonClicked(alertDialog, account, postDelete));
    }

    private void onDeleteDialogButtonClicked(
            final AlertDialog dialog, final Account account, final Runnable postDelete) {
        final CheckBox deleteFromServer = dialog.findViewById(R.id.delete_from_server);
        if (deleteFromServer == null) {
            throw new IllegalStateException("AlertDialog did not have button");
        }
        final var button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        final boolean unregister = deleteFromServer.isChecked();
        if (unregister) {
            if (account.isOnlineAndConnected()) {
                final var connection = account.getXmppConnection();
                deleteFromServer.setEnabled(false);
                button.setText(R.string.please_wait);
                button.setEnabled(false);
                final var future = connection.getManager(RegistrationManager.class).unregister();
                Futures.addCallback(
                        future,
                        new FutureCallback<>() {
                            @Override
                            public void onSuccess(Void result) {
                                runOnUiThread(
                                        () -> onAccountDeletedSuccess(account, dialog, postDelete));
                            }

                            @Override
                            public void onFailure(@NonNull Throwable t) {
                                Log.d(Config.LOGTAG, "could not unregister account", t);
                                runOnUiThread(() -> onAccountDeletionFailure(dialog, postDelete));
                            }
                        },
                        MoreExecutors.directExecutor());
            } else {
                Toast.makeText(this, R.string.not_connected_try_again, Toast.LENGTH_LONG).show();
            }
        } else {
            onAccountDeletedSuccess(account, dialog, postDelete);
        }
    }

    private void onAccountDeletedSuccess(
            final Account account, final AlertDialog dialog, final Runnable postDelete) {
        xmppConnectionService.deleteAccount(account);
        dialog.dismiss();
        if (xmppConnectionService.getAccounts().isEmpty() && Config.MAGIC_CREATE_DOMAIN != null) {
            final Intent intent = SignupUtils.getSignUpIntent(this);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        } else if (postDelete != null) {
            postDelete.run();
        }
    }

    private void onAccountDeletionFailure(final AlertDialog dialog, final Runnable postDelete) {
        final CheckBox deleteFromServer = dialog.findViewById(R.id.delete_from_server);
        if (deleteFromServer == null) {
            throw new IllegalStateException("AlertDialog did not have button");
        }
        final var button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        deleteFromServer.setEnabled(true);
        button.setText(R.string.delete);
        button.setEnabled(true);
        Toast.makeText(this, R.string.could_not_delete_account_from_server, Toast.LENGTH_LONG)
                .show();
    }

    protected abstract void onBackendConnected();

    protected void registerListeners() {
        if (this instanceof XmppConnectionService.OnConversationUpdate) {
            this.xmppConnectionService.setOnConversationListChangedListener(
                    (XmppConnectionService.OnConversationUpdate) this);
        }
        if (this instanceof XmppConnectionService.OnAccountUpdate) {
            this.xmppConnectionService.setOnAccountListChangedListener(
                    (XmppConnectionService.OnAccountUpdate) this);
        }
        if (this instanceof XmppConnectionService.OnCaptchaRequested) {
            this.xmppConnectionService.setOnCaptchaRequestedListener(
                    (XmppConnectionService.OnCaptchaRequested) this);
        }
        if (this instanceof XmppConnectionService.OnRosterUpdate) {
            this.xmppConnectionService.setOnRosterUpdateListener(
                    (XmppConnectionService.OnRosterUpdate) this);
        }
        if (this instanceof XmppConnectionService.OnMucRosterUpdate) {
            this.xmppConnectionService.setOnMucRosterUpdateListener(
                    (XmppConnectionService.OnMucRosterUpdate) this);
        }
        if (this instanceof OnUpdateBlocklist) {
            this.xmppConnectionService.setOnUpdateBlocklistListener((OnUpdateBlocklist) this);
        }
        if (this instanceof XmppConnectionService.OnShowErrorToast) {
            this.xmppConnectionService.setOnShowErrorToastListener(
                    (XmppConnectionService.OnShowErrorToast) this);
        }
        if (this instanceof OnKeyStatusUpdated) {
            this.xmppConnectionService.setOnKeyStatusUpdatedListener((OnKeyStatusUpdated) this);
        }
        if (this instanceof XmppConnectionService.OnJingleRtpConnectionUpdate) {
            this.xmppConnectionService.setOnRtpConnectionUpdateListener(
                    (XmppConnectionService.OnJingleRtpConnectionUpdate) this);
        }
    }

    protected void unregisterListeners() {
        if (this instanceof XmppConnectionService.OnConversationUpdate) {
            this.xmppConnectionService.removeOnConversationListChangedListener(
                    (XmppConnectionService.OnConversationUpdate) this);
        }
        if (this instanceof XmppConnectionService.OnAccountUpdate) {
            this.xmppConnectionService.removeOnAccountListChangedListener(
                    (XmppConnectionService.OnAccountUpdate) this);
        }
        if (this instanceof XmppConnectionService.OnCaptchaRequested) {
            this.xmppConnectionService.removeOnCaptchaRequestedListener(
                    (XmppConnectionService.OnCaptchaRequested) this);
        }
        if (this instanceof XmppConnectionService.OnRosterUpdate) {
            this.xmppConnectionService.removeOnRosterUpdateListener(
                    (XmppConnectionService.OnRosterUpdate) this);
        }
        if (this instanceof XmppConnectionService.OnMucRosterUpdate) {
            this.xmppConnectionService.removeOnMucRosterUpdateListener(
                    (XmppConnectionService.OnMucRosterUpdate) this);
        }
        if (this instanceof OnUpdateBlocklist) {
            this.xmppConnectionService.removeOnUpdateBlocklistListener((OnUpdateBlocklist) this);
        }
        if (this instanceof XmppConnectionService.OnShowErrorToast) {
            this.xmppConnectionService.removeOnShowErrorToastListener(
                    (XmppConnectionService.OnShowErrorToast) this);
        }
        if (this instanceof OnKeyStatusUpdated) {
            this.xmppConnectionService.removeOnNewKeysAvailableListener((OnKeyStatusUpdated) this);
        }
        if (this instanceof XmppConnectionService.OnJingleRtpConnectionUpdate) {
            this.xmppConnectionService.removeRtpConnectionUpdateListener(
                    (XmppConnectionService.OnJingleRtpConnectionUpdate) this);
        }
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                startActivity(
                        new Intent(
                                this, tech.ravensoftware.chat.ui.activity.SettingsActivity.class));
                break;
            case R.id.action_privacy_policy:
                openPrivacyPolicy();
                break;
            case R.id.action_accounts:
                AccountUtils.launchManageAccounts(this);
                break;
            case R.id.action_account:
                AccountUtils.launchManageAccount(this);
                break;
            case android.R.id.home:
                finish();
                break;
            case R.id.action_show_qr_code:
                showQrCode();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void openPrivacyPolicy() {
        if (BuildConfig.PRIVACY_POLICY == null) {
            return;
        }
        final var viewPolicyIntent = new Intent(Intent.ACTION_VIEW);
        viewPolicyIntent.setData(Uri.parse(BuildConfig.PRIVACY_POLICY));
        try {
            startActivity(viewPolicyIntent);
        } catch (final ActivityNotFoundException e) {
            Toast.makeText(this, R.string.no_application_found_to_open_link, Toast.LENGTH_SHORT)
                    .show();
        }
    }

    public void selectPresence(
            final Conversation conversation, final PresenceSelector.OnPresenceSelected listener) {
        final Contact contact = conversation.getContact();
        if (contact.showInRoster() || contact.isSelf()) {
            final var presences = contact.getPresences();
            if (presences.isEmpty()) {
                if (contact.isSelf()) {
                    conversation.setNextCounterpart(null);
                    listener.onPresenceSelected();
                } else if (!contact.getOption(Contact.Options.TO)
                        && !contact.getOption(Contact.Options.ASKING)
                        && contact.getAccount().getStatus() == Account.State.ONLINE) {
                    showAskForPresenceDialog(contact);
                } else if (!contact.getOption(Contact.Options.TO)
                        || !contact.getOption(Contact.Options.FROM)) {
                    PresenceSelector.warnMutualPresenceSubscription(this, conversation, listener);
                } else {
                    conversation.setNextCounterpart(null);
                    listener.onPresenceSelected();
                }
            } else if (presences.size() == 1) {
                conversation.setNextCounterpart(Iterables.getFirst(presences, null).getFrom());
                listener.onPresenceSelected();
            } else {
                PresenceSelector.showPresenceSelectionDialog(this, conversation, listener);
            }
        } else {
            showAddToRosterDialog(conversation.getContact());
        }
    }

    @SuppressLint("UnsupportedChromeOsCameraSystemFeature")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        metrics = getResources().getDisplayMetrics();
        this.isCameraFeatureAvailable =
                getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY);
    }

    protected boolean isCameraFeatureAvailable() {
        return this.isCameraFeatureAvailable;
    }

    protected boolean isOptimizingBattery() {
        final PowerManager pm = getSystemService(PowerManager.class);
        return !pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    protected boolean isAffectedByDataSaver() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            final ConnectivityManager cm =
                    (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            return cm != null
                    && cm.isActiveNetworkMetered()
                    && Compatibility.getRestrictBackgroundStatus(cm)
                            == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED;
        } else {
            return false;
        }
    }

    protected SharedPreferences getPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
    }

    public void switchToConversation(Conversation conversation) {
        switchToConversation(conversation, null);
    }

    public void switchToConversationAndQuote(Conversation conversation, String text) {
        switchToConversation(conversation, text, true, null, false, false);
    }

    public void switchToConversation(Conversation conversation, String text) {
        switchToConversation(conversation, text, false, null, false, false);
    }

    public void switchToConversationDoNotAppend(Conversation conversation, String text) {
        switchToConversation(conversation, text, false, null, false, true);
    }

    public void highlightInMuc(Conversation conversation, String nick) {
        switchToConversation(conversation, null, false, nick, false, false);
    }

    public void privateMsgInMuc(Conversation conversation, String nick) {
        switchToConversation(conversation, null, false, nick, true, false);
    }

    private void switchToConversation(
            Conversation conversation,
            String text,
            boolean asQuote,
            String nick,
            boolean pm,
            boolean doNotAppend) {
        Intent intent = new Intent(this, ChatActivity.class);
        intent.setAction(ChatActivity.ACTION_VIEW_CONVERSATION);
        intent.putExtra(ChatActivity.EXTRA_CONVERSATION, conversation.getUuid());
        if (text != null) {
            intent.putExtra(Intent.EXTRA_TEXT, text);
            if (asQuote) {
                intent.putExtra(ChatActivity.EXTRA_AS_QUOTE, true);
            }
        }
        if (nick != null) {
            intent.putExtra(ChatActivity.EXTRA_NICK, nick);
            intent.putExtra(ChatActivity.EXTRA_IS_PRIVATE_MESSAGE, pm);
        }
        if (doNotAppend) {
            intent.putExtra(ChatActivity.EXTRA_DO_NOT_APPEND, true);
        }
        intent.setFlags(intent.getFlags() | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }

    public void switchToContactDetails(Contact contact) {
        switchToContactDetails(contact, null);
    }

    public void switchToContactDetails(Contact contact, String messageFingerprint) {
        Intent intent = new Intent(this, ContactDetailsActivity.class);
        intent.setAction(ContactDetailsActivity.ACTION_VIEW_CONTACT);
        intent.putExtra(EXTRA_ACCOUNT, contact.getAccount().getJid().asBareJid().toString());
        intent.putExtra("contact", contact.getAddress().toString());
        intent.putExtra("fingerprint", messageFingerprint);
        startActivity(intent);
    }

    public void switchToAccount(Account account, String fingerprint) {
        switchToAccount(account, false, fingerprint);
    }

    public void switchToAccount(Account account) {
        switchToAccount(account, false, null);
    }

    public void switchToAccount(Account account, boolean init, String fingerprint) {
        Intent intent = new Intent(this, EditAccountActivity.class);
        intent.putExtra("jid", account.getJid().asBareJid().toString());
        intent.putExtra("init", init);
        if (init) {
            intent.setFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TASK
                            | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        }
        if (fingerprint != null) {
            intent.putExtra("fingerprint", fingerprint);
        }
        startActivity(intent);
        if (init) {
            overridePendingTransition(0, 0);
        }
    }

    protected void delegateUriPermissionsToService(Uri uri) {
        Intent intent = new Intent(this, XmppConnectionService.class);
        intent.setAction(Intent.ACTION_SEND);
        intent.setData(uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startService(intent);
        } catch (Exception e) {
            Log.e(Config.LOGTAG, "unable to delegate uri permission", e);
        }
    }

    protected void inviteToConversation(Conversation conversation) {
        startActivityForResult(
                ChooseContactActivity.create(this, conversation), REQUEST_INVITE_TO_CONVERSATION);
    }

    protected void announcePgp(
            final Account account,
            final Conversation conversation,
            Intent intent,
            final Runnable onSuccess) {
        if (account.getPgpId() == 0) {
            choosePgpSignId(account);
        } else {
            final String status = Strings.nullToEmpty(account.getPresenceStatusMessage());
            final var future =
                    xmppConnectionService.getPgpEngine().generateSignature(intent, account, status);
            Futures.addCallback(
                    future,
                    new FutureCallback<String>() {
                        @Override
                        public void onSuccess(String signature) {
                            account.setPgpSignature(signature);
                            xmppConnectionService.databaseBackend.updateAccount(account);
                            account.getXmppConnection()
                                    .getManager(PresenceManager.class)
                                    .available();
                            if (conversation != null) {
                                conversation.setNextEncryption(Message.ENCRYPTION_PGP);
                                xmppConnectionService.updateConversation(conversation);
                                refreshUi();
                            }
                            if (onSuccess == null) {
                                return;
                            }
                            onSuccess.run();
                        }

                        @Override
                        public void onFailure(@NonNull final Throwable t) {
                            if (t instanceof PgpEngine.UserInputRequiredException e) {
                                try {
                                    startIntentSenderForResult(
                                            e.getPendingIntent().getIntentSender(),
                                            REQUEST_ANNOUNCE_PGP,
                                            null,
                                            0,
                                            0,
                                            0,
                                            Compatibility.pgpStartIntentSenderOptions());
                                } catch (final SendIntentException ignored) {
                                }
                            } else {
                                account.setPgpSignId(0);
                                account.unsetPgpSignature();
                                xmppConnectionService.databaseBackend.updateAccount(account);
                                choosePgpSignId(account);
                            }
                        }
                    },
                    ContextCompat.getMainExecutor(this));
        }
    }

    protected void choosePgpSignId(final Account account) {
        final var future = xmppConnectionService.getPgpEngine().chooseKey(account);
        Futures.addCallback(
                future,
                new FutureCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {}

                    @Override
                    public void onFailure(@NonNull Throwable t) {
                        if (t instanceof PgpEngine.UserInputRequiredException e) {
                            try {
                                startIntentSenderForResult(
                                        e.getPendingIntent().getIntentSender(),
                                        REQUEST_CHOOSE_PGP_ID,
                                        null,
                                        0,
                                        0,
                                        0,
                                        Compatibility.pgpStartIntentSenderOptions());
                            } catch (final SendIntentException ignored) {
                            }
                        }
                    }
                },
                ContextCompat.getMainExecutor(this));
    }

    protected void showAddToRosterDialog(final Contact contact) {
        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle(contact.getAddress().toString());
        builder.setMessage(getString(R.string.not_in_roster));
        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.setPositiveButton(
                getString(R.string.add_contact),
                (dialog, which) -> xmppConnectionService.createContact(contact));
        builder.create().show();
    }

    private void showAskForPresenceDialog(final Contact contact) {
        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle(contact.getAddress().toString());
        builder.setMessage(R.string.request_presence_updates);
        builder.setNegativeButton(R.string.cancel, null);
        builder.setPositiveButton(
                R.string.request_now,
                (dialog, which) -> {
                    final var connection = contact.getAccount().getXmppConnection();
                    connection
                            .getManager(PresenceManager.class)
                            .subscribe(contact.getAddress().asBareJid());
                });
        builder.create().show();
    }

    protected void quickEdit(String previousValue, @StringRes int hint, OnValueEdited callback) {
        quickEdit(previousValue, callback, hint, false, false);
    }

    protected void quickEdit(
            String previousValue,
            @StringRes int hint,
            OnValueEdited callback,
            boolean permitEmpty) {
        quickEdit(previousValue, callback, hint, false, permitEmpty);
    }

    protected void quickPasswordEdit(String previousValue, OnValueEdited callback) {
        quickEdit(previousValue, callback, R.string.password, true, false);
    }

    @SuppressLint("InflateParams")
    private void quickEdit(
            final String previousValue,
            final OnValueEdited callback,
            final @StringRes int hint,
            boolean password,
            boolean permitEmpty) {
        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        final DialogQuickeditBinding binding =
                DataBindingUtil.inflate(
                        getLayoutInflater(), R.layout.dialog_quickedit, null, false);
        if (password) {
            binding.inputEditText.setInputType(
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }
        builder.setPositiveButton(R.string.accept, null);
        if (hint != 0) {
            binding.inputLayout.setHint(getString(hint));
        }
        binding.inputEditText.requestFocus();
        if (previousValue != null) {
            binding.inputEditText.getText().append(previousValue);
        }
        builder.setView(binding.getRoot());
        builder.setNegativeButton(R.string.cancel, null);
        final AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> SoftKeyboardUtils.showKeyboard(binding.inputEditText));
        dialog.show();
        View.OnClickListener clickListener =
                v -> {
                    String value = binding.inputEditText.getText().toString();
                    if (!value.equals(previousValue) && (!value.trim().isEmpty() || permitEmpty)) {
                        String error = callback.onValueEdited(value);
                        if (error != null) {
                            binding.inputLayout.setError(error);
                            return;
                        }
                    }
                    SoftKeyboardUtils.hideSoftKeyboard(binding.inputEditText);
                    dialog.dismiss();
                };
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(clickListener);
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE)
                .setOnClickListener(
                        (v -> {
                            SoftKeyboardUtils.hideSoftKeyboard(binding.inputEditText);
                            dialog.dismiss();
                        }));
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnDismissListener(
                dialog1 -> {
                    SoftKeyboardUtils.hideSoftKeyboard(binding.inputEditText);
                });
    }

    protected boolean hasStoragePermission(int requestCode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                        new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE}, requestCode);
                return false;
            } else {
                return true;
            }
        } else {
            return true;
        }
    }

    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_INVITE_TO_CONVERSATION && resultCode == RESULT_OK) {
            mPendingConferenceInvite = ConferenceInvite.parse(data);
            if (xmppConnectionServiceBound && mPendingConferenceInvite != null) {
                if (mPendingConferenceInvite.execute(this)) {
                    mToast = Toast.makeText(this, R.string.creating_conference, Toast.LENGTH_LONG);
                    mToast.show();
                }
                mPendingConferenceInvite = null;
            }
        }
    }

    public boolean copyTextToClipboard(String text, int labelResId) {
        ClipboardManager mClipBoardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        String label = getResources().getString(labelResId);
        if (mClipBoardManager != null) {
            ClipData mClipData = ClipData.newPlainText(label, text);
            mClipBoardManager.setPrimaryClip(mClipData);
            return true;
        }
        return false;
    }

    protected String getShareableUri() {
        return getShareableUri(false);
    }

    protected String getShareableUri(boolean http) {
        return null;
    }

    protected void shareLink(boolean http) {
        String uri = getShareableUri(http);
        if (uri == null || uri.isEmpty()) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, getShareableUri(http));
        try {
            startActivity(Intent.createChooser(intent, getText(R.string.share_uri_with)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.no_application_to_share_uri, Toast.LENGTH_SHORT).show();
        }
    }

    protected void launchOpenKeyChain(long keyId) {
        PgpEngine pgp = XmppActivity.this.xmppConnectionService.getPgpEngine();
        try {
            startIntentSenderForResult(
                    pgp.getIntentForKey(keyId).getIntentSender(),
                    0,
                    null,
                    0,
                    0,
                    0,
                    Compatibility.pgpStartIntentSenderOptions());
        } catch (final Throwable e) {
            Log.d(Config.LOGTAG, "could not launch OpenKeyChain", e);
            Toast.makeText(XmppActivity.this, R.string.openpgp_error, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        SettingsUtils.applyScreenshotSetting(this);
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public boolean onMenuOpened(int id, Menu menu) {
        if (id == AppCompatDelegate.FEATURE_SUPPORT_ACTION_BAR && menu != null) {
            MenuDoubleTabUtil.recordMenuOpen();
        }
        return super.onMenuOpened(id, menu);
    }

    protected void showQrCode() {
        showQrCode(getShareableUri());
    }

    protected void showQrCode(final String uri) {
        if (uri == null || uri.isEmpty()) {
            return;
        }
        final Point size = new Point();
        getWindowManager().getDefaultDisplay().getSize(size);
        final int width = Math.min(size.x, size.y);
        final int black;
        final int white;
        if (Activities.isNightMode(this)) {
            black =
                    MaterialColors.getColor(
                            this,
                            com.google.android.material.R.attr.colorSurfaceContainerHighest,
                            "No surface color configured");
            white =
                    MaterialColors.getColor(
                            this,
                            com.google.android.material.R.attr.colorSurfaceInverse,
                            "No inverse surface color configured");
        } else {
            black =
                    MaterialColors.getColor(
                            this,
                            com.google.android.material.R.attr.colorSurfaceInverse,
                            "No inverse surface color configured");
            white =
                    MaterialColors.getColor(
                            this,
                            com.google.android.material.R.attr.colorSurfaceContainerHighest,
                            "No surface color configured");
        }
        final var bitmap = BarcodeProvider.create2dBarcodeBitmap(uri, width, black, white);
        final ImageView view = new ImageView(this);
        view.setBackgroundColor(white);
        view.setImageBitmap(bitmap);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setView(view);
        builder.create().show();
    }

    protected Account extractAccount(Intent intent) {
        final String jid = intent != null ? intent.getStringExtra(EXTRA_ACCOUNT) : null;
        try {
            return jid != null ? xmppConnectionService.findAccountByJid(Jid.of(jid)) : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public AvatarService avatarService() {
        return xmppConnectionService.getAvatarService();
    }

    public void loadBitmap(Message message, ImageView imageView) {
        Bitmap bm;
        try {
            bm =
                    xmppConnectionService
                            .getFileBackend()
                            .getThumbnail(message, (int) (metrics.density * 288), true);
        } catch (IOException e) {
            bm = null;
        }
        if (bm != null) {
            cancelPotentialWork(message, imageView);
            imageView.setImageBitmap(bm);
            imageView.setBackgroundColor(0x00000000);
        } else {
            if (cancelPotentialWork(message, imageView)) {
                imageView.setBackgroundColor(0xff333333);
                imageView.setImageDrawable(null);
                final BitmapWorkerTask task = new BitmapWorkerTask(imageView);
                final AsyncDrawable asyncDrawable = new AsyncDrawable(getResources(), null, task);
                imageView.setImageDrawable(asyncDrawable);
                try {
                    task.execute(message);
                } catch (final RejectedExecutionException ignored) {
                    ignored.printStackTrace();
                }
            }
        }
    }

    protected interface OnValueEdited {
        String onValueEdited(String value);
    }

    public static class ConferenceInvite {
        private String uuid;
        private final List<Jid> jids = new ArrayList<>();

        public static ConferenceInvite parse(Intent data) {
            ConferenceInvite invite = new ConferenceInvite();
            invite.uuid = data.getStringExtra(ChooseContactActivity.EXTRA_CONVERSATION);
            if (invite.uuid == null) {
                return null;
            }
            invite.jids.addAll(ChooseContactActivity.extractJabberIds(data));
            return invite;
        }

        public boolean execute(final XmppActivity activity) {
            final XmppConnectionService service = activity.xmppConnectionService;
            final Conversation conversation = service.findConversationByUuid(this.uuid);
            if (conversation == null) {
                return false;
            }
            if (conversation.getMode() == Conversation.MODE_MULTI) {
                for (final Jid jid : jids) {
                    // TODO use direct invites for public conferences
                    service.invite(conversation, jid);
                }
                return false;
            } else {
                final var account = conversation.getAccount();
                jids.add(conversation.getAddress().asBareJid());
                final var future = service.createAdhocConference(account, null, jids);
                Futures.addCallback(
                        future, activity.adhocCallback, ContextCompat.getMainExecutor(activity));
                // when it's an immediate failure do not display 'creating group chat'
                return !future.isDone();
            }
        }
    }

    static class BitmapWorkerTask extends AsyncTask<Message, Void, Bitmap> {
        private final WeakReference<ImageView> imageViewReference;
        private Message message = null;

        private BitmapWorkerTask(ImageView imageView) {
            this.imageViewReference = new WeakReference<>(imageView);
        }

        @Override
        protected Bitmap doInBackground(Message... params) {
            if (isCancelled()) {
                return null;
            }
            message = params[0];
            try {
                final XmppActivity activity = find(imageViewReference);
                if (activity != null && activity.xmppConnectionService != null) {
                    return activity.xmppConnectionService
                            .getFileBackend()
                            .getThumbnail(message, (int) (activity.metrics.density * 288), false);
                } else {
                    return null;
                }
            } catch (IOException e) {
                return null;
            }
        }

        @Override
        protected void onPostExecute(final Bitmap bitmap) {
            if (!isCancelled()) {
                final ImageView imageView = imageViewReference.get();
                if (imageView != null) {
                    imageView.setImageBitmap(bitmap);
                    imageView.setBackgroundColor(bitmap == null ? 0xff333333 : 0x00000000);
                }
            }
        }
    }

    private static class AsyncDrawable extends BitmapDrawable {
        private final WeakReference<BitmapWorkerTask> bitmapWorkerTaskReference;

        private AsyncDrawable(Resources res, Bitmap bitmap, BitmapWorkerTask bitmapWorkerTask) {
            super(res, bitmap);
            bitmapWorkerTaskReference = new WeakReference<>(bitmapWorkerTask);
        }

        private BitmapWorkerTask getBitmapWorkerTask() {
            return bitmapWorkerTaskReference.get();
        }
    }

    public static XmppActivity find(@NonNull WeakReference<ImageView> viewWeakReference) {
        final View view = viewWeakReference.get();
        return view == null ? null : find(view);
    }

    public static XmppActivity find(@NonNull final View view) {
        Context context = view.getContext();
        while (context instanceof ContextWrapper) {
            if (context instanceof XmppActivity) {
                return (XmppActivity) context;
            }
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }
}
