package tech.ravensoftware.chat.xmpp.jingle;

import tech.ravensoftware.chat.entities.DownloadableFile;

public interface OnFileTransmissionStatusChanged {
	void onFileTransmitted(DownloadableFile file);

	void onFileTransferAborted();
}
