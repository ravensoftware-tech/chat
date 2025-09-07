package im.conversations.android.xmpp.processor;

import tech.ravensoftware.chat.entities.Conversation;
import tech.ravensoftware.chat.entities.Message;
import tech.ravensoftware.chat.services.XmppConnectionService;
import tech.ravensoftware.chat.xmpp.Jid;
import tech.ravensoftware.chat.xmpp.XmppConnection;
import tech.ravensoftware.chat.xmpp.jingle.JingleConnectionManager;
import tech.ravensoftware.chat.xmpp.jingle.JingleRtpConnection;
import java.util.function.BiFunction;

public class MessageAcknowledgedProcessor extends XmppConnection.Delegate
        implements BiFunction<Jid, String, Boolean> {

    private final XmppConnectionService service;

    public MessageAcknowledgedProcessor(
            final XmppConnectionService service, final XmppConnection connection) {
        super(service.getApplicationContext(), connection);
        this.service = service;
    }

    @Override
    public Boolean apply(final Jid to, final String id) {
        if (id.startsWith(JingleRtpConnection.JINGLE_MESSAGE_PROPOSE_ID_PREFIX)) {
            final String sessionId =
                    id.substring(JingleRtpConnection.JINGLE_MESSAGE_PROPOSE_ID_PREFIX.length());
            this.service
                    .getJingleConnectionManager()
                    .updateProposedSessionDiscovered(
                            getAccount(),
                            to,
                            sessionId,
                            JingleConnectionManager.DeviceDiscoveryState.SEARCHING_ACKNOWLEDGED);
        }

        final Jid bare = to.asBareJid();

        for (final Conversation conversation : service.getChat()) {
            if (conversation.getAccount() == getAccount()
                    && conversation.getAddress().asBareJid().equals(bare)) {
                final Message message = conversation.findUnsentMessageWithUuid(id);
                if (message != null) {
                    message.setStatus(Message.STATUS_SEND);
                    message.setErrorMessage(null);
                    getDatabase().updateMessage(message, false);
                    return true;
                }
            }
        }
        return false;
    }
}
