package tech.ravensoftware.chat.xmpp.manager;

import android.util.Log;
import androidx.annotation.NonNull;
import com.google.common.base.Strings;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import tech.ravensoftware.chat.Config;
import tech.ravensoftware.chat.entities.Conversation;
import tech.ravensoftware.chat.entities.Message;
import tech.ravensoftware.chat.services.XmppConnectionService;
import tech.ravensoftware.chat.xml.Namespace;
import tech.ravensoftware.chat.xmpp.Jid;
import tech.ravensoftware.chat.xmpp.XmppConnection;
import im.conversations.android.xmpp.NodeConfiguration;
import im.conversations.android.xmpp.model.mds.Displayed;
import im.conversations.android.xmpp.model.pubsub.Items;
import im.conversations.android.xmpp.model.unique.StanzaId;
import java.util.Map;

public class MessageDisplayedSynchronizationManager extends AbstractManager {

    private final XmppConnectionService service;

    public MessageDisplayedSynchronizationManager(
            final XmppConnectionService service, XmppConnection connection) {
        super(service.getApplicationContext(), connection);
        this.service = service;
    }

    public void handleItems(final Items items) {
        for (final var item : items.getItemMap(Displayed.class).entrySet()) {
            this.processMdsItem(item);
        }
    }

    public void processMdsItem(final Map.Entry<String, Displayed> item) {
        final var account = getAccount();
        final Jid jid = Jid.Invalid.getNullForInvalid(Jid.ofOrInvalid(item.getKey()));
        if (jid == null) {
            return;
        }
        final var displayed = item.getValue();
        final var stanzaId = displayed.getStanzaId();
        final String id = stanzaId == null ? null : stanzaId.getId();
        final Conversation conversation = this.service.find(account, jid);
        if (id != null && conversation != null) {
            conversation.setDisplayState(id);
            this.service.markReadUpToStanzaId(conversation, id);
        }
    }

    public void fetch() {
        final var future = getManager(PepManager.class).fetchItems(Displayed.class);
        Futures.addCallback(
                future,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(Map<String, Displayed> result) {
                        for (final var entry : result.entrySet()) {
                            processMdsItem(entry);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Throwable t) {
                        Log.d(
                                Config.LOGTAG,
                                getAccount().getJid().asBareJid()
                                        + ": could not retrieve MDS items",
                                t);
                    }
                },
                MoreExecutors.directExecutor());
    }

    public boolean hasFeature() {
        final var pepManager = getManager(PepManager.class);
        return pepManager.hasPublishOptions()
                && pepManager.hasConfigNodeMax()
                && Config.MESSAGE_DISPLAYED_SYNCHRONIZATION;
    }

    public boolean hasServerAssist() {
        return getManager(DiscoManager.class).hasAccountFeature(Namespace.MDS_DISPLAYED);
    }

    public static Displayed displayed(final String id, final Conversation conversation) {
        final Jid by;
        if (conversation.getMode() == Conversation.MODE_MULTI) {
            by = conversation.getAddress().asBareJid();
        } else {
            by = conversation.getAccount().getJid().asBareJid();
        }
        final var displayed = new Displayed();
        final var stanzaId = displayed.addExtension(new StanzaId(id));
        stanzaId.setBy(by);
        return displayed;
    }

    public void displayed(final Message message) {
        final String stanzaId = message.getServerMsgId();
        if (Strings.isNullOrEmpty(stanzaId)) {
            return;
        }
        final Conversation conversation;
        final var conversational = message.getConversation();
        if (conversational instanceof Conversation c) {
            conversation = c;
        } else {
            return;
        }
        final var account = conversation.getAccount();
        final var connection = account.getXmppConnection();
        if (!connection.getManager(MessageDisplayedSynchronizationManager.class).hasFeature()) {
            return;
        }
        final Jid itemId;
        if (message.isPrivateMessage()) {
            itemId = message.getCounterpart();
        } else {
            itemId = conversation.getAddress().asBareJid();
        }
        final var future = this.publish(itemId, displayed(stanzaId, conversation));
        Futures.addCallback(
                future,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(Void result) {
                        Log.d(Config.LOGTAG, "published mds for " + itemId + "#" + stanzaId);
                    }

                    @Override
                    public void onFailure(@NonNull Throwable t) {
                        Log.d(Config.LOGTAG, "failed to publish MDS", t);
                    }
                },
                MoreExecutors.directExecutor());
    }

    private ListenableFuture<Void> publish(final Jid itemId, final Displayed displayed) {
        return getManager(PepManager.class)
                .publish(displayed, itemId.toString(), NodeConfiguration.WHITELIST_MAX_ITEMS);
    }
}
