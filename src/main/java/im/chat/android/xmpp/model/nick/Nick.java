package im.conversations.android.xmpp.model.nick;

import tech.ravensoftware.chat.xml.Namespace;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement(namespace = Namespace.NICK)
public class Nick extends Extension {

    public Nick() {
        super(Nick.class);
    }

    public Nick(final String nick) {
        this();
        this.setContent(nick);
    }
}
