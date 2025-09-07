package tech.ravensoftware.chat.xmpp;

import tech.ravensoftware.chat.entities.Account;

public interface OnAdvancedStreamFeaturesLoaded {
	void onAdvancedStreamFeaturesAvailable(final Account account);
}
