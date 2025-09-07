package tech.ravensoftware.chat.xmpp;

import tech.ravensoftware.chat.entities.Contact;

public interface OnContactStatusChanged {
	void onContactStatusChanged(final Contact contact, final boolean online);
}
