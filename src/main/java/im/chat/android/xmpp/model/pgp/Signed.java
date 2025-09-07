package im.conversations.android.xmpp.model.pgp;

import tech.ravensoftware.chat.xml.Namespace;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement(name = "x", namespace = Namespace.PGP_SIGNED)
public class Signed extends Extension {

    public Signed() {
        super(Signed.class);
    }

    public Signed(final String signature) {
        this();
        this.setContent(signature);
    }
}
