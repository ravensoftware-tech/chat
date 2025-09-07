package im.conversations.android.xmpp.model.ping;

import tech.ravensoftware.chat.xml.Namespace;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement(namespace = Namespace.PING)
public class Ping extends Extension {

    public Ping() {
        super(Ping.class);
    }
}
