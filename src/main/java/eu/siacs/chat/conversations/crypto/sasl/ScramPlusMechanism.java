package tech.ravensoftware.chat.crypto.sasl;

import javax.net.ssl.SSLSocket;

import tech.ravensoftware.chat.entities.Account;

public abstract class ScramPlusMechanism extends ScramMechanism implements ChannelBindingMechanism {

    ScramPlusMechanism(Account account, ChannelBinding channelBinding) {
        super(account, channelBinding);
    }

    @Override
    protected byte[] getChannelBindingData(final SSLSocket sslSocket)
            throws AuthenticationException {
        return ChannelBindingMechanism.getChannelBindingData(sslSocket, this.channelBinding);
    }

    @Override
    public ChannelBinding getChannelBinding() {
        return this.channelBinding;
    }
}
