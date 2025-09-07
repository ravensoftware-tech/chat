package tech.ravensoftware.chat.utils;

import static tech.ravensoftware.chat.receiver.SystemEventReceiver.EXTRA_NEEDS_FOREGROUND_SERVICE;

import android.annotation.SuppressLint;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import tech.ravensoftware.chat.Config;

public class Compatibility {

    public static boolean hasStoragePermission(final Context context) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(
                                context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean s() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
    }

    public static boolean twentySix() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
    }

    public static boolean twentyEight() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P;
    }

    public static boolean thirtyFour() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE;
    }

    public static void startService(final Context context, final Intent intent) {
        try {
            if (Compatibility.twentySix()) {
                intent.putExtra(EXTRA_NEEDS_FOREGROUND_SERVICE, true);
                ContextCompat.startForegroundService(context, intent);
            } else {
                context.startService(intent);
            }
        } catch (final RuntimeException e) {
            Log.d(
                    Config.LOGTAG,
                    context.getClass().getSimpleName() + " was unable to start service");
        }
    }

    @SuppressLint("UnsupportedChromeOsCameraSystemFeature")
    public static boolean hasFeatureCamera(final Context context) {
        final PackageManager packageManager = context.getPackageManager();
        return packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public static int getRestrictBackgroundStatus(
            @NonNull final ConnectivityManager connectivityManager) {
        try {
            return connectivityManager.getRestrictBackgroundStatus();
        } catch (final Exception e) {
            Log.d(
                    Config.LOGTAG,
                    "platform bug detected. Unable to get restrict background status",
                    e);
            return ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public static boolean isActiveNetworkMetered(
            @NonNull final ConnectivityManager connectivityManager) {
        try {
            return connectivityManager.isActiveNetworkMetered();
        } catch (final RuntimeException e) {
            // when in doubt better assume it's metered
            return true;
        }
    }

    public static Bundle pgpStartIntentSenderOptions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(
                            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                    .toBundle();
        } else {
            return null;
        }
    }
}
