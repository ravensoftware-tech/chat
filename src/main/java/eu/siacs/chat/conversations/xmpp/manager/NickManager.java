package tech.ravensoftware.chat.xmpp.manager;

import com.google.common.base.Strings;
import com.google.common.util.concurrent.ListenableFuture;
import tech.ravensoftware.chat.entities.Contact;
import tech.ravensoftware.chat.services.QuickChatService;
import tech.ravensoftware.chat.services.XmppConnectionService;
import tech.ravensoftware.chat.xml.Namespace;
import tech.ravensoftware.chat.xmpp.Jid;
import tech.ravensoftware.chat.xmpp.XmppConnection;
import im.conversations.android.xmpp.NodeConfiguration;
import im.conversations.android.xmpp.model.nick.Nick;
import im.conversations.android.xmpp.model.pubsub.Items;

public class NickManager extends AbstractManager {

    private final XmppConnectionService service;

    public NickManager(final XmppConnectionService service, final XmppConnection connection) {
        super(service.getApplicationContext(), connection);
        this.service = service;
    }

    public void handleItems(final Jid from, final Items items) {
        final var item = items.getFirstItem(Nick.class);
        final var nick = item == null ? null : item.getContent();
        if (from == null || Strings.isNullOrEmpty(nick)) {
            return;
        }
        setNick(from, nick);
    }

    private void setNick(final Jid user, final String nick) {
        final var account = getAccount();
        if (user.asBareJid().equals(account.getJid().asBareJid())) {
            account.setDisplayName(nick);
            if (QuickChatService.isQuicksy()) {
                service.getAvatarService().clear(account);
            }
            service.checkMucRequiresRename();
        } else {
            final Contact contact = account.getRoster().getContact(user);
            if (contact.setPresenceName(nick)) {
                connection.getManager(RosterManager.class).writeToDatabaseAsync();
                service.getAvatarService().clear(contact);
            }
        }
        service.updateConversationUi();
        service.updateAccountUi();
    }

    public ListenableFuture<Void> publish(final String name) {
        if (Strings.isNullOrEmpty(name)) {
            return getManager(PepManager.class).delete(Namespace.NICK);
        } else {
            return getManager(PepManager.class)
                    .publishSingleton(new Nick(name), NodeConfiguration.PRESENCE);
        }
    }

    public void handleDelete(final Jid from) {
        this.setNick(from, null);
    }
}
