package tech.ravensoftware.chat.receiver;

import android.content.Intent;
import android.util.Log;
import androidx.annotation.NonNull;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import tech.ravensoftware.chat.Config;
import tech.ravensoftware.chat.Chat;
import tech.ravensoftware.chat.services.XmppConnectionService;
import tech.ravensoftware.chat.utils.Compatibility;
import java.util.Map;

public class PushMessageReceiver extends FirebaseMessagingService {

    @Override
    public void onMessageReceived(@NonNull final RemoteMessage message) {
        if (!Chat.getInstance(getApplicationContext()).hasEnabledAccount()) {
            Log.d(
                    Config.LOGTAG,
                    "PushMessageReceiver ignored message because no accounts are enabled");
            return;
        }
        final Map<String, String> data = message.getData();
        final Intent intent = new Intent(this, XmppConnectionService.class);
        intent.setAction(XmppConnectionService.ACTION_FCM_MESSAGE_RECEIVED);
        intent.putExtra("account", data.get("account"));
        Compatibility.startService(this, intent);
    }

    @Override
    public void onNewToken(@NonNull final String token) {
        if (!Chat.getInstance(getApplicationContext()).hasEnabledAccount()) {
            Log.d(
                    Config.LOGTAG,
                    "PushMessageReceiver ignored new token because no accounts are enabled");
            return;
        }
        final Intent intent = new Intent(this, XmppConnectionService.class);
        intent.setAction(XmppConnectionService.ACTION_FCM_TOKEN_REFRESH);
        Compatibility.startService(this, intent);
    }
}
