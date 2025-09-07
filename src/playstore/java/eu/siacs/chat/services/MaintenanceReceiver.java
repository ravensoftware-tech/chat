package tech.ravensoftware.chat.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.firebase.installations.FirebaseInstallations;

import tech.ravensoftware.chat.Config;
import tech.ravensoftware.chat.utils.Compatibility;

public class MaintenanceReceiver extends BroadcastReceiver {
	@Override
	public void onReceive(Context context, Intent intent) {
		Log.d(Config.LOGTAG, "received intent in maintenance receiver");
		if ("tech.ravensoftware.chat.RENEW_INSTANCE_ID".equals(intent.getAction())) {
			renewInstanceToken(context);

		}
	}

	private void renewInstanceToken(final Context context) {
		FirebaseInstallations.getInstance().delete().addOnSuccessListener(unused -> {
			final Intent intent = new Intent(context, XmppConnectionService.class);
				intent.setAction(XmppConnectionService.ACTION_FCM_TOKEN_REFRESH);
				Compatibility.startService(context, intent);
		});
	}
}
