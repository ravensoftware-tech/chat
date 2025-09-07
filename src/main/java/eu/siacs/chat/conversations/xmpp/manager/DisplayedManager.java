package tech.ravensoftware.chat.xmpp.manager;

import android.content.Context;
import android.util.Log;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import tech.ravensoftware.chat.AppSettings;
import tech.ravensoftware.chat.Config;
import tech.ravensoftware.chat.entities.Conversation;
import tech.ravensoftware.chat.entities.Conversational;
import tech.ravensoftware.chat.entities.Message;
import tech.ravensoftware.chat.xmpp.Jid;
import tech.ravensoftware.chat.xmpp.XmppConnection;
import im.conversations.android.xmpp.model.hints.Store;
import im.conversations.android.xmpp.model.markers.Displayed;
import java.util.List;

public class DisplayedManager extends AbstractManager {

    private final AppSettings appSettings;

    public DisplayedManager(final Context context, final XmppConnection connection) {
        super(context, connection);
        this.appSettings = new AppSettings(context);
    }

    public void displayed(final List<Message> readMessages) {
        final var last =
                Iterables.getLast(
                        Collections2.filter(
                                readMessages,
                                m ->
                                        !m.isPrivateMessage()
                                                && m.getStatus() == Message.STATUS_RECEIVED),
                        null);
        if (last == null) {
            return;
        }

        final Conversation conversation;
        if (last.getConversation() instanceof Conversation c) {
            conversation = c;
        } else {
            return;
        }

        final boolean isPrivateAndNonAnonymousMuc =
                conversation.getMode() == Conversation.MODE_MULTI
                        && conversation.isPrivateAndNonAnonymous();

        final boolean sendDisplayedMarker =
                appSettings.isConfirmMessages()
                        && (last.trusted() || isPrivateAndNonAnonymousMuc)
                        && ((last.getConversation().getMode() == Conversation.MODE_SINGLE
                                        && last.getRemoteMsgId() != null)
                                || (last.getConversation().getMode() == Conversational.MODE_MULTI
                                        && last.getServerMsgId() != null))
                        && (last.markable || isPrivateAndNonAnonymousMuc);

        final String stanzaId = last.getServerMsgId();

        final boolean serverAssist =
                stanzaId != null
                        && connection
                                .getManager(MessageDisplayedSynchronizationManager.class)
                                .hasServerAssist();

        if (sendDisplayedMarker && serverAssist) {
            final var displayedMessage = displayedMessage(last);
            displayedMessage.addExtension(
                    MessageDisplayedSynchronizationManager.displayed(stanzaId, conversation));
            displayedMessage.setTo(displayedMessage.getTo().asBareJid());
            Log.d(
                    Config.LOGTAG,
                    getAccount().getJid().asBareJid() + ": server assisted " + displayedMessage);
            this.connection.sendMessagePacket(displayedMessage);
        } else {
            getManager(MessageDisplayedSynchronizationManager.class).displayed(last);
            // read markers will be sent after MDS to flush the CSI stanza queue
            if (sendDisplayedMarker) {
                final var displayedMessage = displayedMessage(last);
                Log.d(
                        Config.LOGTAG,
                        getAccount().getJid().asBareJid()
                                + ": sending displayed marker to "
                                + displayedMessage.getTo());
                this.connection.sendMessagePacket(displayedMessage);
            }
        }
    }

    private static im.conversations.android.xmpp.model.stanza.Message displayedMessage(
            final Message message) {
        final boolean groupChat = message.getConversation().getMode() == Conversational.MODE_MULTI;
        final Jid to = message.getCounterpart();
        final var packet = new im.conversations.android.xmpp.model.stanza.Message();
        packet.setType(
                groupChat
                        ? im.conversations.android.xmpp.model.stanza.Message.Type.GROUPCHAT
                        : im.conversations.android.xmpp.model.stanza.Message.Type.CHAT);
        packet.setTo(groupChat ? to.asBareJid() : to);
        final var displayed = packet.addExtension(new Displayed());
        if (groupChat) {
            displayed.setId(message.getServerMsgId());
        } else {
            displayed.setId(message.getRemoteMsgId());
        }
        packet.addExtension(new Store());
        return packet;
    }
}
