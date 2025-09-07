/*
 * Copyright (c) 2018, Daniel Gultsch All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation and/or
 * other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package tech.ravensoftware.chat.ui.util;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Build;
import android.widget.Toast;
import androidx.annotation.StringRes;
import com.google.common.collect.Iterables;
import de.gultsch.common.Linkify;
import tech.ravensoftware.chat.R;
import tech.ravensoftware.chat.entities.DownloadableFile;
import tech.ravensoftware.chat.entities.Message;
import tech.ravensoftware.chat.persistance.FileBackend;
import tech.ravensoftware.chat.ui.ChatActivity;
import tech.ravensoftware.chat.ui.XmppActivity;
import java.util.Arrays;
import java.util.Collection;

public class ShareUtil {

    private static final Collection<String> SCHEMES_COPY_PATH_ONLY =
            Arrays.asList("xmpp", "mailto", "tel");

    public static void share(XmppActivity activity, Message message) {
        Intent shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND);
        if (message.isGeoUri()) {
            shareIntent.putExtra(Intent.EXTRA_TEXT, message.getBody());
            shareIntent.setType("text/plain");
        } else if (!message.isFileOrImage()) {
            shareIntent.putExtra(Intent.EXTRA_TEXT, message.getBody());
            shareIntent.setType("text/plain");
            shareIntent.putExtra(
                    ChatActivity.EXTRA_AS_QUOTE,
                    message.getStatus() == Message.STATUS_RECEIVED);
        } else {
            final DownloadableFile file =
                    activity.xmppConnectionService.getFileBackend().getFile(message);
            try {
                shareIntent.putExtra(
                        Intent.EXTRA_STREAM, FileBackend.getUriForFile(activity, file));
            } catch (SecurityException e) {
                Toast.makeText(
                                activity,
                                activity.getString(
                                        R.string.no_permission_to_access_x, file.getAbsolutePath()),
                                Toast.LENGTH_SHORT)
                        .show();
                return;
            }
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            String mime = message.getMimeType();
            if (mime == null) {
                mime = "*/*";
            }
            shareIntent.setType(mime);
        }
        try {
            activity.startActivity(
                    Intent.createChooser(shareIntent, activity.getText(R.string.share_with)));
        } catch (ActivityNotFoundException e) {
            // This should happen only on faulty androids because normally chooser is always
            // available
            Toast.makeText(activity, R.string.no_application_found_to_open_file, Toast.LENGTH_SHORT)
                    .show();
        }
    }

    public static void copyToClipboard(final XmppActivity activity, final Message message) {
        if (activity.copyTextToClipboard(message.getBody(), R.string.message)
                && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(activity, R.string.message_copied_to_clipboard, Toast.LENGTH_SHORT)
                    .show();
        }
    }

    public static void copyUrlToClipboard(final XmppActivity activity, final Message message) {
        final String url;
        final int resId;
        if (message.isGeoUri()) {
            resId = R.string.location;
            url = message.getBody();
        } else if (message.hasFileOnRemoteHost()) {
            resId = R.string.file_url;
            url = message.getFileParams().url;
        } else {
            final Message.FileParams fileParams = message.getFileParams();
            url =
                    (fileParams != null && fileParams.url != null)
                            ? fileParams.url
                            : message.getBody().trim();
            resId = R.string.file_url;
        }
        if (activity.copyTextToClipboard(url, resId)
                && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(activity, R.string.url_copied_to_clipboard, Toast.LENGTH_SHORT).show();
        }
    }

    public static void copyLinkToClipboard(final XmppActivity activity, final Message message) {
        final var firstUri = Iterables.getFirst(Linkify.getLinks(message.getBody()), null);
        if (firstUri == null) {
            return;
        }
        final String clip;
        if (SCHEMES_COPY_PATH_ONLY.contains(firstUri.getScheme())) {
            clip = firstUri.getPath();
        } else {
            clip = firstUri.getRaw();
        }
        final @StringRes int label =
                switch (firstUri.getScheme()) {
                    case "http", "https", "gemini" -> R.string.web_address;
                    case "xmpp" -> R.string.account_settings_jabber_id;
                    default -> R.string.uri;
                };
        final @StringRes int toast =
                switch (firstUri.getScheme()) {
                    case "http", "https", "gemini", "web+ap" -> R.string.url_copied_to_clipboard;
                    case "xmpp" -> R.string.jabber_id_copied_to_clipboard;
                    case "tel" -> R.string.copied_phone_number;
                    case "mailto" -> R.string.copied_email_address;
                    default -> R.string.uri_copied_to_clipboard;
                };
        if (activity.copyTextToClipboard(clip, label)
                && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(activity, toast, Toast.LENGTH_SHORT).show();
        }
    }
}
