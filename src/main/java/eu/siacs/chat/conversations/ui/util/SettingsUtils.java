package tech.ravensoftware.chat.ui.util;

import android.app.Activity;
import android.view.Window;
import android.view.WindowManager;

import tech.ravensoftware.chat.AppSettings;

public class SettingsUtils {
    public static void applyScreenshotSetting(final Activity activity) {
        final var appSettings = new AppSettings(activity);
        final Window activityWindow = activity.getWindow();
        if (appSettings.isAllowScreenshots()) {
            activityWindow.clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        } else {
            activityWindow.addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
    }
}
