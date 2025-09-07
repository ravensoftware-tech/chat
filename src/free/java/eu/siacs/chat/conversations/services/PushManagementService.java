package tech.ravensoftware.chat.services;

import android.content.Context;
import tech.ravensoftware.chat.entities.Account;

public class PushManagementService {

    protected final Context context;

    public PushManagementService(final Context context) {
        this.context = context;
    }

    public void registerPushTokenOnServer(Account account) {
        // stub implementation. only affects PlayStore flavor
    }

    public boolean available(Account account) {
        return false;
    }

    public static boolean isStub() {
        return true;
    }
}
