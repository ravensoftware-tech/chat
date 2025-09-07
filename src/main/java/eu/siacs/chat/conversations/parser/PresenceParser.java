package tech.ravensoftware.chat.parser;

import tech.ravensoftware.chat.services.XmppConnectionService;
import tech.ravensoftware.chat.xmpp.XmppConnection;
import tech.ravensoftware.chat.xmpp.manager.MultiUserChatManager;
import tech.ravensoftware.chat.xmpp.manager.PresenceManager;
import im.conversations.android.xmpp.model.muc.MultiUserChat;
import im.conversations.android.xmpp.model.muc.user.MucUser;
import im.conversations.android.xmpp.model.stanza.Presence;
import java.util.function.Consumer;

public class PresenceParser extends AbstractParser
        implements Consumer<im.conversations.android.xmpp.model.stanza.Presence> {

    public PresenceParser(final XmppConnectionService service, final XmppConnection connection) {
        super(service, connection);
    }

    @Override
    public void accept(final Presence presence) {
        final var multiUserChatManager = getManager(MultiUserChatManager.class);
        final var type = presence.getType();
        if ((type == null || type == Presence.Type.UNAVAILABLE)
                && presence.hasExtension(MucUser.class)) {
            multiUserChatManager.handlePresence(presence);
        } else if (type == Presence.Type.ERROR
                && (presence.hasExtension(MultiUserChat.class)
                        || multiUserChatManager.isMuc(presence.getFrom()))) {
            multiUserChatManager.handleErrorPresence(presence);
        } else {
            getManager(PresenceManager.class).handlePresence(presence);
        }
    }
}
