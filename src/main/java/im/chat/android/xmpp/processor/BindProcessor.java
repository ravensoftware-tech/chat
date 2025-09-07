package im.conversations.android.xmpp.processor;

import android.util.Log;
import androidx.annotation.NonNull;
import com.google.common.base.Strings;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import tech.ravensoftware.chat.Config;
import tech.ravensoftware.chat.entities.Account;
import tech.ravensoftware.chat.services.PushManagementService;
import tech.ravensoftware.chat.services.XmppConnectionService;
import tech.ravensoftware.chat.xmpp.XmppConnection;
import tech.ravensoftware.chat.xmpp.manager.BookmarkManager;
import tech.ravensoftware.chat.xmpp.manager.HttpUploadManager;
import tech.ravensoftware.chat.xmpp.manager.MessageArchiveManager;
import tech.ravensoftware.chat.xmpp.manager.MessageDisplayedSynchronizationManager;
import tech.ravensoftware.chat.xmpp.manager.MultiUserChatManager;
import tech.ravensoftware.chat.xmpp.manager.NickManager;
import tech.ravensoftware.chat.xmpp.manager.OfflineMessagesManager;
import tech.ravensoftware.chat.xmpp.manager.PresenceManager;
import tech.ravensoftware.chat.xmpp.manager.RosterManager;

public class BindProcessor extends XmppConnection.Delegate implements Runnable {

    private final XmppConnectionService service;

    public BindProcessor(final XmppConnectionService service, final XmppConnection connection) {
        super(service.getApplicationContext(), connection);
        this.service = service;
    }

    @Override
    public void run() {
        final var account = this.getAccount();
        final var features = connection.getFeatures();
        final boolean loggedInSuccessfully =
                account.setOption(Account.OPTION_LOGGED_IN_SUCCESSFULLY, true);
        final boolean sosModified;
        final var sos = features.getServiceOutageStatus();
        if (sos != null) {
            Log.d(Config.LOGTAG, account.getJid().asBareJid() + " server has SOS on " + sos);
            sosModified = account.setKey(Account.KEY_SOS_URL, sos.toString());
        } else {
            sosModified = false;
        }
        final boolean gainedFeature =
                account.setOption(
                        Account.OPTION_HTTP_UPLOAD_AVAILABLE,
                        getManager(HttpUploadManager.class).isAvailableForSize(0));
        if (loggedInSuccessfully || gainedFeature || sosModified) {
            service.databaseBackend.updateAccount(account);
        }

        if (loggedInSuccessfully) {
            final String displayName = account.getDisplayName();
            if (!Strings.isNullOrEmpty(displayName)) {
                Log.d(
                        Config.LOGTAG,
                        account.getJid().asBareJid()
                                + ": display name wasn't empty on first log in. publishing");
                getManager(NickManager.class).publish(displayName);
            }
        }

        getManager(PresenceManager.class).clear();
        getManager(MultiUserChatManager.class).clearInProgress();
        service.getJingleConnectionManager().notifyRebound(account);
        service.getQuickChatService().considerSyncBackground(false);

        getManager(RosterManager.class).request();
        getManager(BookmarkManager.class).request();

        final var mdsManager = getManager(MessageDisplayedSynchronizationManager.class);
        if (mdsManager.hasFeature()) {
            mdsManager.fetch();
        } else {
            Log.d(Config.LOGTAG, account.getJid() + ": server has no support for mds");
        }
        final var archiveManager = getManager(MessageArchiveManager.class);
        final var offlineManager = getManager(OfflineMessagesManager.class);
        final boolean bind2 = features.bind2();
        final boolean flexible = offlineManager.hasFeature();
        final boolean catchup = archiveManager.inCatchup();
        final boolean trackOfflineMessageRetrieval;
        if (!bind2 && flexible && catchup && archiveManager.isMamPreferenceAlways()) {
            trackOfflineMessageRetrieval = false;
            Futures.addCallback(
                    offlineManager.purge(),
                    new FutureCallback<>() {
                        @Override
                        public void onSuccess(Void result) {
                            Log.d(
                                    Config.LOGTAG,
                                    account.getJid().asBareJid()
                                            + ": successfully purged offline messages");
                        }

                        @Override
                        public void onFailure(@NonNull Throwable t) {
                            Log.d(Config.LOGTAG, "could not purge offline messages", t);
                        }
                    },
                    MoreExecutors.directExecutor());
        } else {
            trackOfflineMessageRetrieval = true;
        }
        getManager(PresenceManager.class).available();
        connection.trackOfflineMessageRetrieval(trackOfflineMessageRetrieval);

        final var pushManagementService =
                new PushManagementService(context.getApplicationContext());

        if (pushManagementService.available(account)) {
            pushManagementService.registerPushTokenOnServer(account);
        }
        service.connectMultiModeChat(account);
        getManager(RosterManager.class).syncDirtyContacts();

        service.getUnifiedPushBroker().renewUnifiedPushEndpointsOnBind(account);
    }
}
