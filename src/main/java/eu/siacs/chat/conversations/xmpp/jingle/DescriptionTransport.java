package tech.ravensoftware.chat.xmpp.jingle;

import tech.ravensoftware.chat.xmpp.jingle.stanzas.Content;
import tech.ravensoftware.chat.xmpp.jingle.stanzas.GenericDescription;
import tech.ravensoftware.chat.xmpp.jingle.stanzas.GenericTransportInfo;

public class DescriptionTransport<D extends GenericDescription, T extends GenericTransportInfo> {

    public final Content.Senders senders;
    public final D description;
    public final T transport;

    public DescriptionTransport(
            final Content.Senders senders, final D description, final T transport) {
        this.senders = senders;
        this.description = description;
        this.transport = transport;
    }
}
