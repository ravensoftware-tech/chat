package tech.ravensoftware.chat.xmpp.jingle;

import java.util.Map;

import tech.ravensoftware.chat.xmpp.jingle.stanzas.Group;
import tech.ravensoftware.chat.xmpp.jingle.stanzas.IceUdpTransportInfo;
import tech.ravensoftware.chat.xmpp.jingle.stanzas.OmemoVerifiedIceUdpTransportInfo;
import tech.ravensoftware.chat.xmpp.jingle.stanzas.RtpDescription;

public class OmemoVerifiedRtpContentMap extends RtpContentMap {
    public OmemoVerifiedRtpContentMap(Group group, Map<String, DescriptionTransport<RtpDescription, IceUdpTransportInfo>> contents) {
        super(group, contents);
        for(final DescriptionTransport<RtpDescription,IceUdpTransportInfo> descriptionTransport : contents.values()) {
            if (descriptionTransport.transport instanceof OmemoVerifiedIceUdpTransportInfo) {
                ((OmemoVerifiedIceUdpTransportInfo) descriptionTransport.transport).ensureNoPlaintextFingerprint();
                continue;
            }
            throw new IllegalStateException("OmemoVerifiedRtpContentMap contains non-verified transport info");
        }
    }
}
