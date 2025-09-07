package tech.ravensoftware.chat.xmpp;

import tech.ravensoftware.chat.crypto.axolotl.AxolotlService;

public interface OnKeyStatusUpdated {
	void onKeyStatusUpdated(AxolotlService.FetchStatus report);
}
