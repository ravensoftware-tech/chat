package im.conversations.android.xmpp.model.moderation;

import tech.ravensoftware.chat.xmpp.Jid;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;
import im.conversations.android.xmpp.model.occupant.OccupantId;

@XmlElement
public class Moderated extends Extension {

    public Moderated() {
        super(Moderated.class);
    }

    public Jid getBy() {
        return this.getAttributeAsJid("by");
    }

    public OccupantId getOccupantId() {
        return this.getExtension(OccupantId.class);
    }
}
