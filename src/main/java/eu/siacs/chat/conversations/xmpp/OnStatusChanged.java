package tech.ravensoftware.chat.xmpp;

import tech.ravensoftware.chat.entities.Account;

public interface OnStatusChanged {
	void onStatusChanged(Account account);
}
