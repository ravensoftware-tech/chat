package im.conversations.android.xmpp.model.register;

import tech.ravensoftware.chat.xml.Namespace;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.StreamFeature;

@XmlElement(name = "register", namespace = Namespace.REGISTER_STREAM_FEATURE)
public class RegisterStreamFeature extends StreamFeature {

    public RegisterStreamFeature() {
        super(RegisterStreamFeature.class);
    }
}
