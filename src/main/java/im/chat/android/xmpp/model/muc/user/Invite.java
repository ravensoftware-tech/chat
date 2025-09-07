package im.conversations.android.xmpp.model.muc.user;

import tech.ravensoftware.chat.xmpp.Jid;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Invite extends Extension {

    public Invite() {
        super(Invite.class);
    }

    public void setTo(final Jid to) {
        this.setAttribute("to", to);
    }
}
