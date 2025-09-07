package tech.ravensoftware.chat.ui.util;

import android.content.Context;
import androidx.annotation.StringRes;
import com.google.common.collect.ImmutableMap;
import tech.ravensoftware.chat.R;
import tech.ravensoftware.chat.entities.MucOptions;
import java.util.Map;

public class MucConfiguration {

    public final @StringRes int title;
    public final String[] names;
    public final boolean[] values;
    public final Option[] options;

    private MucConfiguration(
            @StringRes int title, String[] names, boolean[] values, Option[] options) {
        this.title = title;
        this.names = names;
        this.values = values;
        this.options = options;
    }

    public static MucConfiguration get(Context context, boolean advanced, MucOptions mucOptions) {
        if (mucOptions.isPrivateAndNonAnonymous()) {
            String[] names =
                    new String[] {
                        context.getString(R.string.allow_participants_to_edit_subject),
                        context.getString(R.string.allow_participants_to_invite_others)
                    };
            boolean[] values =
                    new boolean[] {
                        mucOptions.participantsCanChangeSubject(), mucOptions.allowInvites()
                    };
            final Option[] options =
                    new Option[] {
                        new Option("muc#roomconfig_changesubject"),
                        new Option("muc#roomconfig_allowinvites")
                    };
            return new MucConfiguration(R.string.conference_options, names, values, options);
        } else {
            final String[] names;
            final boolean[] values;
            final Option[] options;
            if (advanced) {
                names =
                        new String[] {
                            context.getString(R.string.non_anonymous),
                            context.getString(R.string.allow_participants_to_edit_subject),
                            context.getString(R.string.moderated),
                            context.getString(R.string.allow_private_messages)
                        };
                values =
                        new boolean[] {
                            mucOptions.nonanonymous(),
                            mucOptions.participantsCanChangeSubject(),
                            mucOptions.moderated(),
                            mucOptions.allowPmRaw()
                        };
                options =
                        new Option[] {
                            new Option("muc#roomconfig_whois", "anyone", "moderators"),
                            new Option("muc#roomconfig_changesubject"),
                            new Option("muc#roomconfig_moderatedroom"),
                            new Option("muc#roomconfig_allowpm", "anyone", "moderators"),
                        };
            } else {
                names =
                        new String[] {
                            context.getString(R.string.non_anonymous),
                            context.getString(R.string.allow_participants_to_edit_subject),
                        };
                values =
                        new boolean[] {
                            mucOptions.nonanonymous(), mucOptions.participantsCanChangeSubject()
                        };
                options =
                        new Option[] {
                            new Option("muc#roomconfig_whois", "anyone", "moderators"),
                            new Option("muc#roomconfig_changesubject")
                        };
            }
            return new MucConfiguration(R.string.channel_options, names, values, options);
        }
    }

    public static String describe(final Context context, final MucOptions mucOptions) {
        final StringBuilder builder = new StringBuilder();
        if (mucOptions.isPrivateAndNonAnonymous()) {
            if (mucOptions.participantsCanChangeSubject()) {
                builder.append(context.getString(R.string.anyone_can_edit_subject));
            } else {
                builder.append(context.getString(R.string.owners_can_edit_subject));
            }
            builder.append(' ');
            if (mucOptions.allowInvites()) {
                builder.append(context.getString(R.string.anyone_can_invite_others));
            } else {
                builder.append(context.getString(R.string.owners_can_invite_others));
            }
        } else {
            if (mucOptions.nonanonymous()) {
                builder.append(context.getString(R.string.jabber_ids_are_visible_to_anyone));
            } else {
                builder.append(context.getString(R.string.jabber_ids_are_visible_to_admins));
            }
            builder.append(' ');
            if (mucOptions.participantsCanChangeSubject()) {
                builder.append(context.getString(R.string.anyone_can_edit_subject));
            } else {
                builder.append(context.getString(R.string.admins_can_edit_subject));
            }
        }
        return builder.toString();
    }

    public Map<String, Object> toBundle(boolean[] values) {
        final var builder = new ImmutableMap.Builder<String, Object>();
        for (int i = 0; i < values.length; ++i) {
            final Option option = options[i];
            builder.put(option.name, option.values[values[i] ? 0 : 1]);
        }
        builder.put("muc#roomconfig_persistentroom", true);
        return builder.buildOrThrow();
    }

    private static class Option {
        public final String name;
        public final Object[] values;

        private Option(String name) {
            this.name = name;
            this.values = new Boolean[] {true, false};
        }

        private Option(String name, String on, String off) {
            this.name = name;
            this.values = new String[] {on, off};
        }
    }
}
