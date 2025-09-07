package tech.ravensoftware.chat.utils;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.SpannableStringBuilder;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.util.Pair;
import android.widget.TextView;
import androidx.annotation.ColorInt;
import androidx.annotation.ColorRes;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import com.google.android.material.color.MaterialColors;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import tech.ravensoftware.chat.Config;
import tech.ravensoftware.chat.R;
import tech.ravensoftware.chat.crypto.axolotl.AxolotlService;
import tech.ravensoftware.chat.entities.Account;
import tech.ravensoftware.chat.entities.Conversation;
import tech.ravensoftware.chat.entities.Conversational;
import tech.ravensoftware.chat.entities.Message;
import tech.ravensoftware.chat.entities.MucOptions;
import tech.ravensoftware.chat.entities.RtpSessionStatus;
import tech.ravensoftware.chat.entities.Transferable;
import tech.ravensoftware.chat.ui.util.QuoteHelper;
import tech.ravensoftware.chat.worker.ExportBackupWorker;
import tech.ravensoftware.chat.xmpp.Jid;
import im.conversations.android.xmpp.model.idle.LastUserInteraction;
import im.conversations.android.xmpp.model.stanza.Presence;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

public class UIHelper {

    private static final List<String> LOCATION_QUESTIONS =
            Arrays.asList(
                    "where are you", // en
                    "where are you now", // en
                    "where are you right now", // en
                    "whats your 20", // en
                    "what is your 20", // en
                    "what's your 20", // en
                    "whats your twenty", // en
                    "what is your twenty", // en
                    "what's your twenty", // en
                    "wo bist du", // de
                    "wo bist du jetzt", // de
                    "wo bist du gerade", // de
                    "wo seid ihr", // de
                    "wo seid ihr jetzt", // de
                    "wo seid ihr gerade", // de
                    "dónde estás", // es
                    "donde estas" // es
                    );

    private static final List<Character> PUNCTIONATION =
            Arrays.asList('.', ',', '?', '!', ';', ':');

    private static final int SHORT_DATE_FLAGS =
            DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_NO_YEAR | DateUtils.FORMAT_ABBREV_ALL;
    private static final int FULL_DATE_FLAGS =
            DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_ABBREV_ALL | DateUtils.FORMAT_SHOW_DATE;

    public static String readableTimeDifference(Context context, long time) {
        return readableTimeDifference(context, time, false);
    }

    public static String readableTimeDifferenceFull(Context context, long time) {
        return readableTimeDifference(context, time, true);
    }

    private static String readableTimeDifference(
            final Context context, final long time, final boolean fullDate) {
        if (time == 0) {
            return context.getString(R.string.just_now);
        }
        Date date = new Date(time);
        long difference = (System.currentTimeMillis() - time) / 1000;
        if (difference < 60) {
            return context.getString(R.string.just_now);
        } else if (difference < 60 * 2) {
            return context.getString(R.string.minute_ago);
        } else if (difference < 60 * 15) {
            return context.getString(R.string.minutes_ago, Math.round(difference / 60.0));
        } else if (today(date)) {
            java.text.DateFormat df = DateFormat.getTimeFormat(context);
            return df.format(date);
        } else {
            if (fullDate) {
                return DateUtils.formatDateTime(context, date.getTime(), FULL_DATE_FLAGS);
            } else {
                return DateUtils.formatDateTime(context, date.getTime(), SHORT_DATE_FLAGS);
            }
        }
    }

    private static boolean today(Date date) {
        return sameDay(date, new Date(System.currentTimeMillis()));
    }

    public static boolean today(long date) {
        return sameDay(date, System.currentTimeMillis());
    }

    public static boolean yesterday(long date) {
        Calendar cal1 = Calendar.getInstance();
        Calendar cal2 = Calendar.getInstance();
        cal1.add(Calendar.DAY_OF_YEAR, -1);
        cal2.setTime(new Date(date));
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR)
                && cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }

    public static boolean sameDay(long a, long b) {
        return sameDay(new Date(a), new Date(b));
    }

    private static boolean sameDay(Date a, Date b) {
        Calendar cal1 = Calendar.getInstance();
        Calendar cal2 = Calendar.getInstance();
        cal1.setTime(a);
        cal2.setTime(b);
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR)
                && cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }

    public static String lastUserInteraction(
            final Context context, final LastUserInteraction interaction) {
        if (interaction instanceof LastUserInteraction.Online) {
            return context.getString(R.string.presence_online);
        } else if (interaction instanceof LastUserInteraction.None) {
            return null; // just hide the subtitle
        } else if (interaction instanceof LastUserInteraction.Idle idle) {
            if (context.getResources().getBoolean(R.bool.last_seen_full_text)) {
                return context.getString(
                        R.string.last_seen,
                        UIHelper.readableTimeDifferenceFull(
                                context, idle.getSince().toEpochMilli()));
            } else {
                return UIHelper.readableTimeDifference(context, idle.getSince().toEpochMilli());
            }
        } else {
            return null;
        }
    }

    @ColorInt
    public static int getColorForName(final String name) {
        return XEP0392Helper.rgbFromNick(name);
    }

    public static Pair<CharSequence, Boolean> getMessagePreview(
            final Context context, final Message message) {
        return getMessagePreview(context, message, null, ' ');
    }

    public static Pair<CharSequence, Boolean> getMessagePreview(
            final Context context, final Message message, final char separator) {
        return getMessagePreview(context, message, null, separator);
    }

    public static Pair<CharSequence, Boolean> getMessagePreview(
            final Context context, final Message message, final @ColorInt int textColor) {
        return getMessagePreview(context, message, textColor, null);
    }

    private static Pair<CharSequence, Boolean> getMessagePreview(
            final Context context,
            final Message message,
            final @Nullable @ColorInt Integer textColor,
            @Nullable final Character separator) {
        final Transferable d = message.getTransferable();
        if (d != null) {
            switch (d.getStatus()) {
                case Transferable.STATUS_CHECKING:
                    return new Pair<>(
                            context.getString(
                                    R.string.checking_x,
                                    getFileDescriptionString(context, message)),
                            true);
                case Transferable.STATUS_DOWNLOADING:
                    return new Pair<>(
                            context.getString(
                                    R.string.receiving_x_file,
                                    getFileDescriptionString(context, message),
                                    d.getProgress()),
                            true);
                case Transferable.STATUS_OFFER:
                case Transferable.STATUS_OFFER_CHECK_FILESIZE:
                    return new Pair<>(
                            context.getString(
                                    R.string.x_file_offered_for_download,
                                    getFileDescriptionString(context, message)),
                            true);
                case Transferable.STATUS_FAILED:
                    return new Pair<>(context.getString(R.string.file_transmission_failed), true);
                case Transferable.STATUS_CANCELLED:
                    return new Pair<>(
                            context.getString(R.string.file_transmission_cancelled), true);
                case Transferable.STATUS_UPLOADING:
                    if (message.getStatus() == Message.STATUS_OFFERED) {
                        return new Pair<>(
                                context.getString(
                                        R.string.offering_x_file,
                                        getFileDescriptionString(context, message)),
                                true);
                    } else {
                        return new Pair<>(
                                context.getString(
                                        R.string.sending_x_file,
                                        getFileDescriptionString(context, message)),
                                true);
                    }
                default:
                    return new Pair<>("", false);
            }
        } else if (message.isFileOrImage() && message.isDeleted()) {
            return new Pair<>(context.getString(R.string.file_deleted), true);
        } else if (message.getEncryption() == Message.ENCRYPTION_PGP) {
            return new Pair<>(context.getString(R.string.pgp_message), true);
        } else if (message.getEncryption() == Message.ENCRYPTION_DECRYPTION_FAILED) {
            return new Pair<>(context.getString(R.string.decryption_failed), true);
        } else if (message.getEncryption() == Message.ENCRYPTION_AXOLOTL_NOT_FOR_THIS_DEVICE) {
            return new Pair<>(context.getString(R.string.not_encrypted_for_this_device), true);
        } else if (message.getEncryption() == Message.ENCRYPTION_AXOLOTL_FAILED) {
            return new Pair<>(context.getString(R.string.omemo_decryption_failed), true);
        } else if (message.isFileOrImage()) {
            return new Pair<>(getFileDescriptionString(context, message), true);
        } else if (message.getType() == Message.TYPE_RTP_SESSION) {
            RtpSessionStatus rtpSessionStatus = RtpSessionStatus.of(message.getBody());
            final boolean received = message.getStatus() == Message.STATUS_RECEIVED;
            if (!rtpSessionStatus.successful && received) {
                return new Pair<>(context.getString(R.string.missed_call), true);
            } else {
                return new Pair<>(
                        context.getString(
                                received ? R.string.incoming_call : R.string.outgoing_call),
                        true);
            }
        } else {
            final String body = MessageUtils.filterLtrRtl(message.getBody());
            if (body.startsWith(Message.ME_COMMAND)) {
                return new Pair<>(
                        body.replaceAll(
                                "^" + Message.ME_COMMAND,
                                UIHelper.getMessageDisplayName(message) + " "),
                        false);
            } else if (message.isGeoUri()) {
                return new Pair<>(context.getString(R.string.location), true);
            } else if (message.treatAsDownloadable()
                    || MessageUtils.unInitiatedButKnownSize(message)) {
                return new Pair<>(
                        context.getString(
                                R.string.x_file_offered_for_download,
                                getFileDescriptionString(context, message)),
                        true);
            } else {
                if (textColor != null) {
                    return new Pair<>(getStyledBodyOneLine(body, textColor), false);
                } else if (separator != null) {
                    return new Pair<>(getBodyOmitQuotesAndBlocks(body, separator), false);
                } else {
                    throw new AssertionError("specify either separator or textColor");
                }
            }
        }
    }

    private static CharSequence getBodyOmitQuotesAndBlocks(
            final String body, final char separator) {
        final var parts = Splitter.on('\n').trimResults().omitEmptyStrings().splitToList(body);
        final var filtered =
                Collections2.filter(
                        parts,
                        line ->
                                !QuoteHelper.isPositionQuoteCharacter(line, 0)
                                        && !line.equals("```"));
        if (filtered.isEmpty()) {
            return body;
        }
        return Joiner.on(separator).join(filtered);
    }

    private static CharSequence getStyledBodyOneLine(final String body, final int textColor) {
        final var styledBody = new SpannableStringBuilder(body);
        StylingHelper.format(styledBody, 0, styledBody.length() - 1, textColor);
        final var builder = new SpannableStringBuilder();
        for (final var l : CharSequenceUtils.split(styledBody, '\n')) {
            if (l.length() == 0) {
                continue;
            }
            if (l.toString().equals("```")) {
                continue;
            }
            if (QuoteHelper.isPositionQuoteCharacter(l, 0)) {
                continue;
            }
            final var trimmed = CharSequenceUtils.trim(l);
            if (trimmed.length() == 0) {
                continue;
            }
            char last = trimmed.charAt(trimmed.length() - 1);
            if (builder.length() != 0) {
                builder.append(' ');
            }
            builder.append(trimmed);
            if (!PUNCTIONATION.contains(last)) {
                break;
            }
        }
        if (builder.length() == 0) {
            return body.trim();
        } else {
            return builder;
        }
    }

    public static boolean isLastLineQuote(String body) {
        if (body.endsWith("\n")) {
            return false;
        }
        String[] lines = body.split("\n");
        if (lines.length == 0) {
            return false;
        }
        String line = lines[lines.length - 1];
        if (line.isEmpty()) {
            return false;
        }
        char first = line.charAt(0);
        return first == '>' && isPositionFollowedByQuoteableCharacter(line, 0) || first == '\u00bb';
    }

    public static CharSequence shorten(CharSequence input) {
        return input.length() > 256 ? StylingHelper.subSequence(input, 0, 256) : input;
    }

    public static boolean isPositionPrecededByBodyStart(CharSequence body, int pos) {
        // true if not a single linebreak before current position
        for (int i = pos - 1; i >= 0; i--) {
            if (body.charAt(i) != ' ') {
                return false;
            }
        }
        return true;
    }

    public static boolean isPositionPrecededByLineStart(CharSequence body, int pos) {
        if (isPositionPrecededByBodyStart(body, pos)) {
            return true;
        }
        return body.charAt(pos - 1) == '\n';
    }

    public static boolean isPositionFollowedByQuoteableCharacter(CharSequence body, int pos) {
        return !isPositionFollowedByNumber(body, pos)
                && !isPositionFollowedByEmoticon(body, pos)
                && !isPositionFollowedByEquals(body, pos);
    }

    private static boolean isPositionFollowedByNumber(CharSequence body, int pos) {
        boolean previousWasNumber = false;
        for (int i = pos + 1; i < body.length(); i++) {
            char c = body.charAt(i);
            if (Character.isDigit(body.charAt(i))) {
                previousWasNumber = true;
            } else if (previousWasNumber && (c == '.' || c == ',')) {
                previousWasNumber = false;
            } else {
                return (Character.isWhitespace(c) || c == '%' || c == '+') && previousWasNumber;
            }
        }
        return previousWasNumber;
    }

    private static boolean isPositionFollowedByEquals(CharSequence body, int pos) {
        return body.length() > pos + 1 && body.charAt(pos + 1) == '=';
    }

    private static boolean isPositionFollowedByEmoticon(CharSequence body, int pos) {
        if (body.length() <= pos + 1) {
            return false;
        } else {
            final char first = body.charAt(pos + 1);
            return first == ';'
                    || first == ':'
                    || first == '.' // do not quote >.< (but >>.<)
                    || closingBeforeWhitespace(body, pos + 1);
        }
    }

    private static boolean closingBeforeWhitespace(CharSequence body, int pos) {
        for (int i = pos; i < body.length(); ++i) {
            final char c = body.charAt(i);
            if (Character.isWhitespace(c)) {
                return false;
            } else if (QuoteHelper.isPositionQuoteCharacter(body, pos)
                    || QuoteHelper.isPositionQuoteEndCharacter(body, pos)) {
                return body.length() == i + 1 || Character.isWhitespace(body.charAt(i + 1));
            }
        }
        return false;
    }

    public static String concatNames(final Collection<MucOptions.User> users) {
        return concatNames(users, users.size() >= 3);
    }

    public static String concatNames(
            final Collection<MucOptions.User> users, final boolean shortNames) {

        return Joiner.on(", ")
                .join(
                        Collections2.transform(
                                users,
                                u -> {
                                    final var name = u.getDisplayName();
                                    return shortNames ? name.split("\\s+")[0] : name;
                                }));
    }

    public static String concatNames(final Collection<MucOptions.User> users, final int max) {
        return concatNames(ImmutableList.copyOf(Iterables.limit(users, max)));
    }

    public static String getFileDescriptionString(final Context context, final Message message) {
        final String mime = message.getMimeType();
        if (Strings.isNullOrEmpty(mime)) {
            return context.getString(R.string.file);
        } else if (MimeUtils.AMBIGUOUS_CONTAINER_FORMATS.contains(mime)) {
            return context.getString(R.string.multimedia_file);
        } else if (mime.equals("audio/x-m4b")) {
            return context.getString(R.string.audiobook);
        } else if (mime.startsWith("audio/")) {
            return context.getString(R.string.audio);
        } else if (mime.startsWith("video/")) {
            return context.getString(R.string.video);
        } else if (mime.equals("image/gif")) {
            return context.getString(R.string.gif);
        } else if (mime.equals("image/svg+xml")) {
            return context.getString(R.string.vector_graphic);
        } else if (mime.startsWith("image/") || message.getType() == Message.TYPE_IMAGE) {
            return context.getString(R.string.image);
        } else if (mime.contains("pdf")) {
            return context.getString(R.string.pdf_document);
        } else if (MimeUtils.WORD_DOCUMENT_MIMES.contains(mime)) {
            return context.getString(R.string.word_document);
        } else if (mime.equals("application/vnd.android.package-archive")) {
            return context.getString(R.string.apk);
        } else if (mime.equals(ExportBackupWorker.MIME_TYPE)) {
            return context.getString(R.string.chat_backup);
        } else if (mime.contains("vcard")) {
            return context.getString(R.string.vcard);
        } else if (mime.equals("text/x-vcalendar") || mime.equals("text/calendar")) {
            return context.getString(R.string.event);
        } else if (mime.equals("application/epub+zip")
                || mime.equals("application/vnd.amazon.mobi8-ebook")) {
            return context.getString(R.string.ebook);
        } else if (mime.equals("application/gpx+xml")) {
            return context.getString(R.string.gpx_track);
        } else if (mime.equals("text/plain")) {
            return context.getString(R.string.plain_text_document);
        } else {
            return mime;
        }
    }

    public static String getMessageDisplayName(final Message message) {
        final Conversational conversation = message.getConversation();
        if (message.getStatus() == Message.STATUS_RECEIVED) {
            if (conversation.getMode() == Conversation.MODE_MULTI) {
                if (conversation instanceof Conversation c) {
                    return c.getMucOptions().getUserOrStub(message).getDisplayName();
                } else {
                    final var counterpart = message.getCounterpart();
                    return counterpart.isBareJid()
                            ? counterpart.toString()
                            : counterpart.getResource();
                }
            } else {
                return conversation.getContact().getDisplayName();
            }
        } else {
            if (conversation instanceof Conversation c
                    && conversation.getMode() == Conversation.MODE_MULTI) {
                return c.getMucOptions().getSelf().getDisplayName();
            } else {
                final Account account = conversation.getAccount();
                final Jid jid = account.getJid();
                final String displayName = account.getDisplayName();
                if (Strings.isNullOrEmpty(displayName)) {
                    return jid.isDomainJid() ? jid.getDomain().toString() : jid.getLocal();
                } else {
                    return displayName;
                }
            }
        }
    }

    public static String getMessageHint(final Context context, final Conversation conversation) {
        return switch (conversation.getNextEncryption()) {
            case Message.ENCRYPTION_NONE -> {
                if (Config.multipleEncryptionChoices()) {
                    yield context.getString(R.string.send_unencrypted_message);
                } else {
                    yield context.getString(R.string.send_message_to_x, conversation.getName());
                }
            }
            case Message.ENCRYPTION_AXOLOTL -> {
                final AxolotlService axolotlService = conversation.getAccount().getAxolotlService();
                if (axolotlService != null && axolotlService.trustedSessionVerified(conversation)) {
                    yield context.getString(R.string.send_omemo_x509_message);
                } else {
                    yield context.getString(R.string.send_encrypted_message);
                }
            }
            default -> context.getString(R.string.send_encrypted_message);
        };
    }

    public static boolean receivedLocationQuestion(final Message message) {
        if (message == null
                || message.getStatus() != Message.STATUS_RECEIVED
                || message.getType() != Message.TYPE_TEXT) {
            return false;
        }
        final String body =
                Strings.nullToEmpty(message.getBody())
                        .trim()
                        .toLowerCase(Locale.getDefault())
                        .replace("?", "")
                        .replace("¿", "");
        return LOCATION_QUESTIONS.contains(body);
    }

    public static void setStatus(final TextView textView, Presence.Availability status) {
        final @StringRes int text;
        final @ColorRes int color =
                switch (status) {
                    case CHAT -> {
                        text = R.string.presence_chat;
                        yield R.color.green_800;
                    }
                    case ONLINE -> {
                        text = R.string.presence_online;
                        yield R.color.green_800;
                    }
                    case AWAY -> {
                        text = R.string.presence_away;
                        yield R.color.amber_800;
                    }
                    case XA -> {
                        text = R.string.presence_xa;
                        yield R.color.orange_800;
                    }
                    case DND -> {
                        text = R.string.presence_dnd;
                        yield R.color.red_800;
                    }
                    default -> throw new IllegalStateException();
                };
        textView.setText(text);
        textView.setBackgroundTintList(
                ColorStateList.valueOf(
                        MaterialColors.harmonizeWithPrimary(
                                textView.getContext(),
                                ContextCompat.getColor(textView.getContext(), color))));
    }

    public static String filesizeToString(long size) {
        if (size > (1.5 * 1024 * 1024)) {
            return Math.round(size * 1f / (1024 * 1024)) + " MiB";
        } else if (size >= 1024) {
            return Math.round(size * 1f / 1024) + " KiB";
        } else {
            return size + " B";
        }
    }
}
