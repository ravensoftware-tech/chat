package tech.ravensoftware.chat.xmpp.jingle;

import tech.ravensoftware.chat.entities.Account;
import tech.ravensoftware.chat.services.CallIntegration;
import tech.ravensoftware.chat.xmpp.Jid;

import java.util.Set;

public interface OngoingRtpSession {
    Account getAccount();

    Jid getWith();

    String getSessionId();

    CallIntegration getCallIntegration();

    Set<Media> getMedia();
}
