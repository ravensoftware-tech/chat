package tech.ravensoftware.chat.ui.interfaces;

import java.util.List;

import tech.ravensoftware.chat.ui.util.Attachment;

public interface OnMediaLoaded {

    void onMediaLoaded(List<Attachment> attachments);
}
