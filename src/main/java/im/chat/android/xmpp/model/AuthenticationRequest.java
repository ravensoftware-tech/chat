package im.conversations.android.xmpp.model;

import tech.ravensoftware.chat.crypto.sasl.SaslMechanism;

public abstract class AuthenticationRequest extends StreamElement{


    protected AuthenticationRequest(Class<? extends AuthenticationRequest> clazz) {
        super(clazz);
    }

    public abstract void setMechanism(final SaslMechanism mechanism);
}
