package tech.ravensoftware.chat.entities;

import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.text.TextUtils;
import androidx.annotation.NonNull;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import tech.ravensoftware.chat.Config;
import tech.ravensoftware.chat.android.AbstractPhoneContact;
import tech.ravensoftware.chat.android.JabberIdContact;
import tech.ravensoftware.chat.services.QuickChatService;
import tech.ravensoftware.chat.utils.JidHelper;
import tech.ravensoftware.chat.utils.UIHelper;
import tech.ravensoftware.chat.xml.Element;
import tech.ravensoftware.chat.xmpp.Jid;
import tech.ravensoftware.chat.xmpp.jingle.RtpCapability;
import tech.ravensoftware.chat.xmpp.manager.DiscoManager;
import tech.ravensoftware.chat.xmpp.manager.PresenceManager;
import im.conversations.android.xmpp.model.disco.info.InfoQuery;
import im.conversations.android.xmpp.model.idle.LastUserInteraction;
import im.conversations.android.xmpp.model.stanza.Presence;
import im.conversations.android.xmpp.model.stanza.Stanza;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class Contact implements ListItem, Blockable, MucOptions.IdentifiableUser {
    public static final String TABLENAME = "contacts";

    public static final String SYSTEMNAME = "systemname";
    public static final String SERVERNAME = "servername";
    public static final String PRESENCE_NAME = "presence_name";
    public static final String JID = "jid";
    public static final String OPTIONS = "options";
    public static final String SYSTEMACCOUNT = "systemaccount";
    public static final String PHOTOURI = "photouri";
    public static final String KEYS = "pgpkey";
    public static final String ACCOUNT = "accountUuid";
    public static final String AVATAR = "avatar";
    public static final String LAST_PRESENCE = "last_presence";
    public static final String LAST_TIME = "last_time";
    public static final String GROUPS = "groups";
    public static final String RTP_CAPABILITY = "rtpCapability";
    private String accountUuid;
    private String systemName;
    private String serverName;
    private String presenceName;
    private String commonName;
    protected Jid jid;
    private int subscription = 0;
    private Uri systemAccount;
    private String photoUri;
    private final JSONObject keys;
    private JSONArray groups = new JSONArray();
    protected Account account;
    protected String avatar;

    private String mLastPresence = null;
    private RtpCapability.Capability rtpCapability;

    public Contact(
            final String account,
            final String systemName,
            final String serverName,
            final String presenceName,
            final Jid jid,
            final int subscription,
            final String photoUri,
            final Uri systemAccount,
            final String keys,
            final String avatar,
            final String presence,
            final String groups,
            final RtpCapability.Capability rtpCapability) {
        this.accountUuid = account;
        this.systemName = systemName;
        this.serverName = serverName;
        this.presenceName = presenceName;
        this.jid = jid;
        this.subscription = subscription;
        this.photoUri = photoUri;
        this.systemAccount = systemAccount;
        JSONObject tmpJsonObject;
        try {
            tmpJsonObject = (keys == null ? new JSONObject("") : new JSONObject(keys));
        } catch (JSONException e) {
            tmpJsonObject = new JSONObject();
        }
        this.keys = tmpJsonObject;
        this.avatar = avatar;
        try {
            this.groups = (groups == null ? new JSONArray() : new JSONArray(groups));
        } catch (JSONException e) {
            this.groups = new JSONArray();
        }
        this.mLastPresence = presence;
        this.rtpCapability = rtpCapability;
    }

    public Contact(final Jid jid) {
        this.jid = jid;
        this.keys = new JSONObject();
    }

    public static Contact fromCursor(final Cursor cursor) {
        final Jid jid;
        try {
            jid = Jid.of(cursor.getString(cursor.getColumnIndexOrThrow(JID)));
        } catch (final IllegalArgumentException e) {
            // TODO: Borked DB... handle this somehow?
            return null;
        }
        Uri systemAccount;
        try {
            systemAccount =
                    Uri.parse(cursor.getString(cursor.getColumnIndexOrThrow(SYSTEMACCOUNT)));
        } catch (Exception e) {
            systemAccount = null;
        }
        return new Contact(
                cursor.getString(cursor.getColumnIndexOrThrow(ACCOUNT)),
                cursor.getString(cursor.getColumnIndexOrThrow(SYSTEMNAME)),
                cursor.getString(cursor.getColumnIndexOrThrow(SERVERNAME)),
                cursor.getString(cursor.getColumnIndexOrThrow(PRESENCE_NAME)),
                jid,
                cursor.getInt(cursor.getColumnIndexOrThrow(OPTIONS)),
                cursor.getString(cursor.getColumnIndexOrThrow(PHOTOURI)),
                systemAccount,
                cursor.getString(cursor.getColumnIndexOrThrow(KEYS)),
                cursor.getString(cursor.getColumnIndexOrThrow(AVATAR)),
                cursor.getString(cursor.getColumnIndexOrThrow(LAST_PRESENCE)),
                cursor.getString(cursor.getColumnIndexOrThrow(GROUPS)),
                RtpCapability.Capability.of(
                        cursor.getString(cursor.getColumnIndexOrThrow(RTP_CAPABILITY))));
    }

    @Override
    public String getDisplayName() {
        if (isSelf()) {
            final String displayName = account.getDisplayName();
            if (!Strings.isNullOrEmpty(displayName)) {
                return displayName;
            }
        }
        if (Config.X509_VERIFICATION && !TextUtils.isEmpty(this.commonName)) {
            return this.commonName;
        } else if (!TextUtils.isEmpty(this.systemName)) {
            return this.systemName;
        } else if (!TextUtils.isEmpty(this.serverName)) {
            return this.serverName;
        } else if (!TextUtils.isEmpty(this.presenceName)
                && ((QuickChatService.isQuicksy()
                                && JidHelper.isQuicksyDomain(jid.getDomain()))
                        || mutualPresenceSubscription())) {
            return this.presenceName;
        } else if (jid.getLocal() != null) {
            return JidHelper.localPartOrFallback(jid);
        } else {
            return jid.getDomain().toString();
        }
    }

    public String getPublicDisplayName() {
        if (!TextUtils.isEmpty(this.presenceName)) {
            return this.presenceName;
        } else if (jid.getLocal() != null) {
            return JidHelper.localPartOrFallback(jid);
        } else {
            return jid.getDomain().toString();
        }
    }

    public String getProfilePhoto() {
        return this.photoUri;
    }

    public Jid getAddress() {
        return jid;
    }

    @Override
    public Collection<Tag> getTags() {
        return Tag.of(this.getGroups());
    }

    public ContentValues getContentValues() {
        synchronized (this.keys) {
            final ContentValues values = new ContentValues();
            values.put(ACCOUNT, accountUuid);
            values.put(SYSTEMNAME, systemName);
            values.put(SERVERNAME, serverName);
            values.put(PRESENCE_NAME, presenceName);
            values.put(JID, jid.toString());
            values.put(OPTIONS, subscription);
            values.put(SYSTEMACCOUNT, systemAccount != null ? systemAccount.toString() : null);
            values.put(PHOTOURI, photoUri);
            values.put(KEYS, keys.toString());
            values.put(AVATAR, avatar);
            values.put(LAST_PRESENCE, mLastPresence);
            values.put(GROUPS, groups.toString());
            values.put(RTP_CAPABILITY, rtpCapability == null ? null : rtpCapability.toString());
            return values;
        }
    }

    public Account getAccount() {
        return this.account;
    }

    public void setAccount(Account account) {
        this.account = account;
        this.accountUuid = account.getUuid();
    }

    public List<Presence> getPresences() {
        return this.account
                .getXmppConnection()
                .getManager(PresenceManager.class)
                .getPresences(getAddress());
    }

    public Presence.Availability getShownStatus() {
        final var availabilities = Lists.transform(getPresences(), Presence::getAvailability);
        if (availabilities.isEmpty()) {
            return Presence.Availability.OFFLINE;
        } else if (availabilities.contains(Presence.Availability.DND)) {
            return Presence.Availability.DND;
        } else {
            return Ordering.natural().min(availabilities);
        }
    }

    public LastUserInteraction getLastUserInteraction() {
        final Collection<LastUserInteraction> interactions;
        final var presences = getPresences();

        // getShowStatus shows DND if any presence is DND
        // if we were to look at all presence for last interaction title might show 'online' while
        // send button is red
        // only look at DND presences if there are any for user interaction fixes this
        final var dnd =
                Collections2.filter(
                        presences, p -> p.getAvailability() == Presence.Availability.DND);
        if (dnd.isEmpty()) {
            interactions = Collections2.transform(presences, Presence::getLastUserInteraction);
        } else {
            interactions = Collections2.transform(dnd, Presence::getLastUserInteraction);
        }
        return LastUserInteraction.max(interactions);
    }

    public List<Optional<InfoQuery>> getCapabilities() {
        return this.account.getXmppConnection().getManager(DiscoManager.class).get(getPresences());
    }

    public List<Jid> getFullAddresses() {
        return Lists.transform(getPresences(), Stanza::getFrom);
    }

    public boolean setPhotoUri(String uri) {
        if (uri != null && !uri.equals(this.photoUri)) {
            this.photoUri = uri;
            return true;
        } else if (this.photoUri != null && uri == null) {
            this.photoUri = null;
            return true;
        } else {
            return false;
        }
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public boolean setSystemName(String systemName) {
        final String old = getDisplayName();
        this.systemName = systemName;
        return !old.equals(getDisplayName());
    }

    public boolean setPresenceName(String presenceName) {
        final String old = getDisplayName();
        this.presenceName = presenceName;
        return !old.equals(getDisplayName());
    }

    public Uri getSystemAccount() {
        return systemAccount;
    }

    public void setSystemAccount(Uri lookupUri) {
        this.systemAccount = lookupUri;
    }

    public Collection<String> getGroups() {
        final Collection<String> groups = new HashSet<>();
        for (int i = 0; i < this.groups.length(); ++i) {
            try {
                groups.add(this.groups.getString(i));
            } catch (final JSONException ignored) {
            }
        }
        return groups;
    }

    public long getPgpKeyId() {
        synchronized (this.keys) {
            if (this.keys.has("pgp_keyid")) {
                try {
                    return this.keys.getLong("pgp_keyid");
                } catch (JSONException e) {
                    return 0;
                }
            } else {
                return 0;
            }
        }
    }

    public boolean setPgpKeyId(long keyId) {
        final long previousKeyId = getPgpKeyId();
        synchronized (this.keys) {
            try {
                this.keys.put("pgp_keyid", keyId);
                return previousKeyId != keyId;
            } catch (final JSONException ignored) {
            }
        }
        return false;
    }

    public void setOption(int option) {
        this.subscription |= 1 << option;
    }

    public void resetOption(int option) {
        this.subscription &= ~(1 << option);
    }

    public boolean getOption(int option) {
        return ((this.subscription & (1 << option)) != 0);
    }

    public boolean showInRoster() {
        return (this.getOption(Contact.Options.IN_ROSTER)
                        && (!this.getOption(Contact.Options.DIRTY_DELETE)))
                || (this.getOption(Contact.Options.DIRTY_PUSH));
    }

    public boolean showInContactList() {
        return showInRoster()
                || getOption(Options.SYNCED_VIA_OTHER)
                || (QuickChatService.isQuicksy() && systemAccount != null);
    }

    public void parseSubscriptionFromElement(Element item) {
        String ask = item.getAttribute("ask");
        String subscription = item.getAttribute("subscription");

        if (subscription == null) {
            this.resetOption(Options.FROM);
            this.resetOption(Options.TO);
        } else {
            switch (subscription) {
                case "to":
                    this.resetOption(Options.FROM);
                    this.setOption(Options.TO);
                    break;
                case "from":
                    this.resetOption(Options.TO);
                    this.setOption(Options.FROM);
                    this.resetOption(Options.PREEMPTIVE_GRANT);
                    this.resetOption(Options.PENDING_SUBSCRIPTION_REQUEST);
                    break;
                case "both":
                    this.setOption(Options.TO);
                    this.setOption(Options.FROM);
                    this.resetOption(Options.PREEMPTIVE_GRANT);
                    this.resetOption(Options.PENDING_SUBSCRIPTION_REQUEST);
                    break;
                case "none":
                    this.resetOption(Options.FROM);
                    this.resetOption(Options.TO);
                    break;
            }
        }

        // do NOT override asking if pending push request
        if (!this.getOption(Contact.Options.DIRTY_PUSH)) {
            if ((ask != null) && (ask.equals("subscribe"))) {
                this.setOption(Contact.Options.ASKING);
            } else {
                this.resetOption(Contact.Options.ASKING);
            }
        }
    }

    public void parseGroupsFromElement(Element item) {
        this.groups = new JSONArray();
        for (Element element : item.getChildren()) {
            if (element.getName().equals("group") && element.getContent() != null) {
                this.groups.put(element.getContent());
            }
        }
    }

    @Override
    public int compareTo(@NonNull final ListItem another) {
        return this.getDisplayName().compareToIgnoreCase(another.getDisplayName());
    }

    public String getServer() {
        return getAddress().getDomain().toString();
    }

    public boolean setAvatar(final String avatar) {
        if (this.avatar != null && this.avatar.equals(avatar)) {
            return false;
        }
        this.avatar = avatar;
        return true;
    }

    public String getAvatar() {
        return this.avatar;
    }

    public boolean mutualPresenceSubscription() {
        return getOption(Options.FROM) && getOption(Options.TO);
    }

    @Override
    public boolean isBlocked() {
        return getAccount().isBlocked(this);
    }

    @Override
    public boolean isDomainBlocked() {
        return getAccount().isBlocked(this.getAddress().getDomain());
    }

    @Override
    @NonNull
    public Jid getBlockedAddress() {
        if (isDomainBlocked()) {
            return getAddress().getDomain();
        } else {
            return getAddress();
        }
    }

    public boolean isSelf() {
        return account.getJid().asBareJid().equals(jid.asBareJid());
    }

    boolean isOwnServer() {
        return account.getJid().getDomain().equals(jid.asBareJid());
    }

    public void setCommonName(String cn) {
        this.commonName = cn;
    }

    public void setLastResource(String resource) {
        this.mLastPresence = resource;
    }

    public String getLastResource() {
        return this.mLastPresence;
    }

    public String getServerName() {
        return serverName;
    }

    public synchronized boolean setPhoneContact(AbstractPhoneContact phoneContact) {
        setOption(getOption(phoneContact.getClass()));
        setSystemAccount(phoneContact.getLookupUri());
        boolean changed = setSystemName(phoneContact.getDisplayName());
        changed |= setPhotoUri(phoneContact.getPhotoUri());
        return changed;
    }

    public synchronized boolean unsetPhoneContact(Class<? extends AbstractPhoneContact> clazz) {
        resetOption(getOption(clazz));
        boolean changed = false;
        if (!getOption(Options.SYNCED_VIA_ADDRESS_BOOK) && !getOption(Options.SYNCED_VIA_OTHER)) {
            setSystemAccount(null);
            changed |= setPhotoUri(null);
            changed |= setSystemName(null);
        }
        return changed;
    }

    public static int getOption(Class<? extends AbstractPhoneContact> clazz) {
        if (clazz == JabberIdContact.class) {
            return Options.SYNCED_VIA_ADDRESS_BOOK;
        } else {
            return Options.SYNCED_VIA_OTHER;
        }
    }

    @Override
    public int getAvatarBackgroundColor() {
        return UIHelper.getColorForName(
                jid != null ? jid.asBareJid().toString() : getDisplayName());
    }

    public boolean hasAvatarOrPresenceName() {
        return avatar != null || presenceName != null;
    }

    public boolean refreshRtpCapability() {
        final RtpCapability.Capability previous = this.rtpCapability;
        this.rtpCapability = RtpCapability.check(this, getPresences());
        return !Objects.equals(previous, this.rtpCapability);
    }

    public RtpCapability.Capability getRtpCapability() {
        return this.rtpCapability == null ? RtpCapability.Capability.NONE : this.rtpCapability;
    }

    @Override
    public Jid mucUserAddress() {
        return null;
    }

    @Override
    public Jid mucUserRealAddress() {
        return getAddress();
    }

    @Override
    public String mucUserOccupantId() {
        return null;
    }

    public static final class Options {
        public static final int TO = 0;
        public static final int FROM = 1;
        public static final int ASKING = 2;
        public static final int PREEMPTIVE_GRANT = 3;
        public static final int IN_ROSTER = 4;
        public static final int PENDING_SUBSCRIPTION_REQUEST = 5;
        public static final int DIRTY_PUSH = 6;
        public static final int DIRTY_DELETE = 7;
        private static final int SYNCED_VIA_ADDRESS_BOOK = 8;
        public static final int SYNCED_VIA_OTHER = 9;
    }
}
