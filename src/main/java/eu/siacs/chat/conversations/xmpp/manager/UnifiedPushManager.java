package tech.ravensoftware.chat.xmpp.manager;

import tech.ravensoftware.chat.services.XmppConnectionService;
import tech.ravensoftware.chat.xmpp.Jid;
import tech.ravensoftware.chat.xmpp.XmppConnection;
import im.conversations.android.xmpp.model.error.Condition;
import im.conversations.android.xmpp.model.error.Error;
import im.conversations.android.xmpp.model.stanza.Iq;
import im.conversations.android.xmpp.model.up.Push;

public class UnifiedPushManager extends AbstractManager {

    private final XmppConnectionService service;

    public UnifiedPushManager(
            final XmppConnectionService service, final XmppConnection connection) {
        super(service, connection);
        this.service = service;
    }

    public void push(final Iq packet) {
        final Jid transport = packet.getFrom();
        final var push = packet.getOnlyExtension(Push.class);
        if (push == null || transport == null) {
            connection.sendErrorFor(packet, Error.Type.MODIFY, new Condition.BadRequest());
            return;
        }
        if (service.processUnifiedPushMessage(getAccount(), transport, push)) {
            connection.sendResultFor(packet);
        } else {
            connection.sendErrorFor(packet, Error.Type.CANCEL, new Condition.ItemNotFound());
        }
    }
}
