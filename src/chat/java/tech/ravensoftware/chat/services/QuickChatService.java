package tech.ravensoftware.chat.services;

import android.content.Intent;
import android.util.Log;

import tech.ravensoftware.chat.Config;
import tech.ravensoftware.chat.services.AbstractQuickChatService;
import tech.ravensoftware.chat.services.XmppConnectionService;

public class QuickChatService extends AbstractQuickChatService {

    QuickChatService(XmppConnectionService xmppConnectionService) {
        super(xmppConnectionService);
    }

    @Override
    public void considerSync() {

    }

    @Override
    public void signalAccountStateChange() {

    }

    @Override
    public boolean isSynchronizing() {
        return false;
    }

    @Override
    public void considerSyncBackground(boolean force) {

    }

    @Override
    public void handleSmsReceived(Intent intent) {
        Log.d(Config.LOGTAG,"ignoring received SMS");
    }
}