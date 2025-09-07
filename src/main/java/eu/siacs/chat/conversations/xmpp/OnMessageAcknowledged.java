package tech.ravensoftware.chat.xmpp;

import tech.ravensoftware.chat.entities.Account;

public interface OnMessageAcknowledged {
    boolean onMessageAcknowledged(Account account, Jid to, String id);
}
