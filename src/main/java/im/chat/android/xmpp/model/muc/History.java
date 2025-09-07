package im.conversations.android.xmpp.model.muc;

import tech.ravensoftware.chat.generator.AbstractGenerator;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class History extends Extension {

    public History() {
        super(History.class);
    }

    public void setMaxChars(final int maxChars) {
        this.setAttribute("maxchars", maxChars);
    }

    public void setMaxStanzas(final int maxStanzas) {
        this.setAttribute("maxstanzas", maxStanzas);
    }

    public void setSince(long timestamp) {
        this.setAttribute("since", AbstractGenerator.getTimestamp(timestamp));
    }
}
