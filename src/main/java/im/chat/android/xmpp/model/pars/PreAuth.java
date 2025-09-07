package im.conversations.android.xmpp.model.pars;

import tech.ravensoftware.chat.xml.Namespace;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement(namespace = Namespace.PARS, name = "preauth")
public class PreAuth extends Extension {

    public PreAuth() {
        super(PreAuth.class);
    }

    public void setToken(final String token) {
        this.setAttribute("token", token);
    }
}
