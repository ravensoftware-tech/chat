package im.conversations.android.xmpp.model.avatar;

import tech.ravensoftware.chat.xml.Namespace;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;
import java.util.Collection;

@XmlElement(namespace = Namespace.AVATAR_METADATA)
public class Metadata extends Extension {

    public Metadata() {
        super(Metadata.class);
    }

    public Collection<Info> getInfos() {
        return this.getExtensions(Info.class);
    }
}
