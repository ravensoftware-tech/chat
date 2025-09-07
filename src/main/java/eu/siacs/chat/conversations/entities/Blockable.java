package tech.ravensoftware.chat.entities;

import androidx.annotation.NonNull;
import tech.ravensoftware.chat.xmpp.Jid;

public interface Blockable {
    boolean isBlocked();

    boolean isDomainBlocked();

    @NonNull
    Jid getBlockedAddress();

    Jid getAddress();

    Account getAccount();
}
