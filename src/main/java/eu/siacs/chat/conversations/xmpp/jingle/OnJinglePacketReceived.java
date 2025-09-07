package tech.ravensoftware.chat.xmpp.jingle;

import tech.ravensoftware.chat.entities.Account;
import im.conversations.android.xmpp.model.stanza.Iq;

public interface OnJinglePacketReceived {
	void onJinglePacketReceived(Account account, Iq packet);
}
