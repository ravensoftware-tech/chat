package tech.ravensoftware.chat.xmpp.jingle;

public interface OnTransportConnected {
	void failed();

	void established();
}
