package tech.ravensoftware.chat.xmpp.manager;

import android.content.Context;
import android.util.Log;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.BaseEncoding;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import tech.ravensoftware.chat.AppSettings;
import tech.ravensoftware.chat.BuildConfig;
import tech.ravensoftware.chat.Config;
import tech.ravensoftware.chat.R;
import tech.ravensoftware.chat.crypto.axolotl.AxolotlService;
import tech.ravensoftware.chat.xml.Namespace;
import tech.ravensoftware.chat.xmpp.Jid;
import tech.ravensoftware.chat.xmpp.XmppConnection;
import im.conversations.android.xmpp.Entity;
import im.conversations.android.xmpp.EntityCapabilities;
import im.conversations.android.xmpp.EntityCapabilities2;
import im.conversations.android.xmpp.ServiceDescription;
import im.conversations.android.xmpp.model.Hash;
import im.conversations.android.xmpp.model.disco.info.InfoQuery;
import im.conversations.android.xmpp.model.disco.items.Item;
import im.conversations.android.xmpp.model.disco.items.ItemsQuery;
import im.conversations.android.xmpp.model.error.Condition;
import im.conversations.android.xmpp.model.error.Error;
import im.conversations.android.xmpp.model.stanza.Iq;
import im.conversations.android.xmpp.model.stanza.Presence;
import im.conversations.android.xmpp.model.version.Version;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class DiscoManager extends AbstractManager {

    public static final String CAPABILITY_NODE = "http://conversations.im";

    private final List<String> STATIC_FEATURES =
            Arrays.asList(
                    Namespace.JINGLE,
                    Namespace.JINGLE_APPS_FILE_TRANSFER,
                    Namespace.JINGLE_TRANSPORTS_S5B,
                    Namespace.JINGLE_TRANSPORTS_IBB,
                    Namespace.JINGLE_ENCRYPTED_TRANSPORT,
                    Namespace.JINGLE_ENCRYPTED_TRANSPORT_OMEMO,
                    Namespace.MUC,
                    Namespace.DIRECT_MUC_INVITATIONS,
                    Namespace.OOB,
                    Namespace.ENTITY_CAPABILITIES,
                    Namespace.ENTITY_CAPABILITIES_2,
                    Namespace.DISCO_INFO,
                    notify(Namespace.AVATAR_METADATA),
                    notify(Namespace.NICK),
                    Namespace.PING,
                    Namespace.VERSION,
                    Namespace.CHAT_STATES,
                    Namespace.REACTIONS);
    private final List<String> MESSAGE_CONFIRMATION_FEATURES =
            Arrays.asList(Namespace.CHAT_MARKERS, Namespace.DELIVERY_RECEIPTS);
    private final List<String> MESSAGE_CORRECTION_FEATURES =
            Collections.singletonList(Namespace.LAST_MESSAGE_CORRECTION);
    private final List<String> PRIVACY_SENSITIVE =
            Collections.singletonList(
                    Namespace.TIME // XEP-0202: Entity Time leaks time zone
                    );
    private final List<String> VOIP_NAMESPACES =
            Arrays.asList(
                    Namespace.JINGLE_TRANSPORT_ICE_UDP,
                    Namespace.JINGLE_FEATURE_AUDIO,
                    Namespace.JINGLE_FEATURE_VIDEO,
                    Namespace.JINGLE_APPS_RTP,
                    Namespace.JINGLE_APPS_DTLS,
                    Namespace.JINGLE_MESSAGE);

    // this is the runtime cache that stores disco information for all entities seen during a
    // session

    // a caps cache will be build in the database

    private final Map<Jid, InfoQuery> entityInformation = new HashMap<>();
    private final Map<Jid, ImmutableSet<Jid>> discoItems = new HashMap<>();
    private final Map<String, Jid> commands = new HashMap<>();

    public DiscoManager(Context context, XmppConnection connection) {
        super(context, connection);
    }

    public static EntityCapabilities.Hash buildHashFromNode(final String node) {
        final var capsPrefix = CAPABILITY_NODE + "#";
        final var caps2Prefix = Namespace.ENTITY_CAPABILITIES_2 + "#";
        if (node.startsWith(capsPrefix)) {
            final String hash = node.substring(capsPrefix.length());
            if (Strings.isNullOrEmpty(hash)) {
                return null;
            }
            if (BaseEncoding.base64().canDecode(hash)) {
                return EntityCapabilities.EntityCapsHash.of(hash);
            }
        } else if (node.startsWith(caps2Prefix)) {
            final String caps = node.substring(caps2Prefix.length());
            if (Strings.isNullOrEmpty(caps)) {
                return null;
            }
            final int separator = caps.lastIndexOf('.');
            if (separator < 0) {
                return null;
            }
            final Hash.Algorithm algorithm = Hash.Algorithm.tryParse(caps.substring(0, separator));
            final String hash = caps.substring(separator + 1);
            if (algorithm == null || Strings.isNullOrEmpty(hash)) {
                return null;
            }
            if (BaseEncoding.base64().canDecode(hash)) {
                return EntityCapabilities2.EntityCaps2Hash.of(algorithm, hash);
            }
        }
        return null;
    }

    public ListenableFuture<Void> infoOrCache(
            final Entity entity,
            final im.conversations.android.xmpp.model.capabilties.EntityCapabilities.NodeHash
                    nodeHash) {
        if (nodeHash == null) {
            return infoOrCache(entity, null, null);
        }
        return infoOrCache(entity, nodeHash.node, nodeHash.hash);
    }

    public ListenableFuture<Void> infoOrCache(
            final Entity entity, final String node, final EntityCapabilities.Hash hash) {
        if (loadFromCache(entity, node, hash)) {
            return Futures.immediateFuture(null);
        }
        return Futures.transform(
                info(entity, node, hash), f -> null, MoreExecutors.directExecutor());
    }

    public boolean loadFromCache(
            final Entity entity, final String node, final EntityCapabilities.Hash hash) {
        final var cached = getDatabase().getInfoQuery(hash);
        if (cached != null && Config.ENABLE_CAPS_CACHE) {
            if (node == null || hash != null) {
                this.put(entity.address, cached);
            }
            return true;
        }
        return false;
    }

    public ListenableFuture<InfoQuery> info(
            @NonNull final Entity entity, @Nullable final String node) {
        return info(entity, node, null);
    }

    public ListenableFuture<InfoQuery> info(
            final Entity entity, @Nullable final String node, final EntityCapabilities.Hash hash) {
        final var requestNode = hash != null ? hash.capabilityNode(node) : node;
        final var iqRequest = new Iq(Iq.Type.GET);
        iqRequest.setTo(entity.address);
        final InfoQuery infoQueryRequest = iqRequest.addExtension(new InfoQuery());
        if (requestNode != null) {
            infoQueryRequest.setNode(requestNode);
        }
        final var future = connection.sendIqPacket(iqRequest);
        return Futures.transform(
                future,
                iqResult -> {
                    final var infoQuery = iqResult.getExtension(InfoQuery.class);
                    if (infoQuery == null) {
                        throw new IllegalStateException("Response did not have query child");
                    }
                    // it's safer to do this check only when we actually requested a node
                    // Prosody <0.11.8 for example puts an empty string in node attribute in the
                    // response
                    if (requestNode != null && !Objects.equals(requestNode, infoQuery.getNode())) {
                        throw new IllegalStateException(
                                "Node in response did not match node in request");
                    }

                    if (node == null
                            || (hash != null
                                    && hash.capabilityNode(node).equals(infoQuery.getNode()))) {
                        // only storing results w/o nodes
                        this.put(entity.address, infoQuery);
                    }

                    final var caps = EntityCapabilities.hash(infoQuery);
                    final var caps2 = EntityCapabilities2.hash(infoQuery);
                    if (hash instanceof EntityCapabilities.EntityCapsHash) {
                        checkMatch(
                                (EntityCapabilities.EntityCapsHash) hash,
                                caps,
                                EntityCapabilities.EntityCapsHash.class);
                    }
                    if (hash instanceof EntityCapabilities2.EntityCaps2Hash) {
                        checkMatch(
                                (EntityCapabilities2.EntityCaps2Hash) hash,
                                caps2,
                                EntityCapabilities2.EntityCaps2Hash.class);
                    }
                    // we want to avoid caching disco info for entities that put variable data (like
                    // number of occupants in a MUC) into it
                    final boolean cache =
                            Objects.nonNull(hash)
                                    || infoQuery.hasFeature(Namespace.ENTITY_CAPABILITIES)
                                    || infoQuery.hasFeature(Namespace.ENTITY_CAPABILITIES_2);

                    if (cache) {
                        getDatabase().insertCapsCache(caps, caps2, infoQuery);
                    }

                    return infoQuery;
                },
                MoreExecutors.directExecutor());
    }

    private <H extends EntityCapabilities.Hash> void checkMatch(
            final H expected, final H was, final Class<H> clazz) {
        if (Arrays.equals(expected.hash, was.hash)) {
            return;
        }
        throw new CapsHashMismatchException(
                String.format(
                        "%s mismatch. Expected %s was %s",
                        clazz.getSimpleName(),
                        BaseEncoding.base64().encode(expected.hash),
                        BaseEncoding.base64().encode(was.hash)));
    }

    public ListenableFuture<Collection<Item>> items(final Entity.DiscoItem entity) {
        return items(entity, null);
    }

    public ListenableFuture<Collection<Item>> items(
            final Entity.DiscoItem entity, @Nullable final String node) {
        final var requestNode = Strings.emptyToNull(node);
        final var iqPacket = new Iq(Iq.Type.GET);
        iqPacket.setTo(entity.address);
        final ItemsQuery itemsQueryRequest = iqPacket.addExtension(new ItemsQuery());
        if (requestNode != null) {
            itemsQueryRequest.setNode(requestNode);
        }
        final var future = connection.sendIqPacket(iqPacket);
        return Futures.transform(
                future,
                iqResult -> {
                    final var itemsQuery = iqResult.getExtension(ItemsQuery.class);
                    if (itemsQuery == null) {
                        throw new IllegalStateException();
                    }
                    if (!Objects.equals(requestNode, itemsQuery.getNode())) {
                        throw new IllegalStateException(
                                "Node in response did not match node in request");
                    }
                    final var items = itemsQuery.getExtensions(Item.class);

                    final var validItems =
                            Collections2.filter(items, i -> Objects.nonNull(i.getJid()));

                    final var itemsAsAddresses =
                            ImmutableSet.copyOf(Collections2.transform(validItems, Item::getJid));
                    if (node == null) {
                        this.discoItems.put(entity.address, itemsAsAddresses);
                    }
                    return validItems;
                },
                MoreExecutors.directExecutor());
    }

    public ListenableFuture<List<InfoQuery>> itemsWithInfo(final Entity.DiscoItem entity) {
        final var itemsFutures = items(entity);
        final var filtered =
                Futures.transform(
                        itemsFutures,
                        items ->
                                Collections2.filter(
                                        items,
                                        i ->
                                                i.getNode() == null
                                                        && !entity.address.equals(i.getJid())),
                        MoreExecutors.directExecutor());
        return Futures.transformAsync(
                filtered,
                items -> {
                    Collection<ListenableFuture<InfoQuery>> infoFutures =
                            Collections2.transform(
                                    items, i -> info(Entity.discoItem(i.getJid()), i.getNode()));
                    return Futures.allAsList(infoFutures);
                },
                MoreExecutors.directExecutor());
    }

    public ListenableFuture<Map<String, Jid>> commands(final Entity.DiscoItem entity) {
        final var itemsFuture = items(entity, Namespace.COMMANDS);
        return Futures.transform(
                itemsFuture,
                items -> {
                    final var builder = new ImmutableMap.Builder<String, Jid>();
                    for (final var item : items) {
                        final var jid = item.getJid();
                        final var node = item.getNode();
                        if (Jid.Invalid.isValid(jid) && node != null) {
                            builder.put(node, jid);
                        }
                    }
                    return builder.buildKeepingLast();
                },
                MoreExecutors.directExecutor());
    }

    ServiceDescription getServiceDescription() {
        final var appSettings = new AppSettings(context);
        final var account = this.getAccount();
        final ImmutableList.Builder<String> features = ImmutableList.builder();
        features.addAll(STATIC_FEATURES);
        if (Config.MESSAGE_DISPLAYED_SYNCHRONIZATION) {
            features.add(notify(Namespace.MDS_DISPLAYED));
        }
        if (appSettings.isConfirmMessages()) {
            features.addAll(MESSAGE_CONFIRMATION_FEATURES);
        }
        if (appSettings.isAllowMessageCorrection()) {
            features.addAll(MESSAGE_CORRECTION_FEATURES);
        }
        if (Config.supportOmemo()) {
            features.add(AxolotlService.PEP_DEVICE_LIST_NOTIFY);
        }
        if (!appSettings.isUseTor() && !account.isOnion()) {
            features.addAll(PRIVACY_SENSITIVE);
            features.addAll(VOIP_NAMESPACES);
            features.add(Namespace.JINGLE_TRANSPORT_WEBRTC_DATA_CHANNEL);
        }
        if (appSettings.isBroadcastLastActivity()) {
            features.add(Namespace.IDLE);
        }
        if (getManager(NativeBookmarkManager.class).hasFeature()) {
            features.add(notify(Namespace.BOOKMARKS2));
        } else {
            features.add(notify(Namespace.BOOKMARKS));
        }
        return new ServiceDescription(
                features.build(),
                new ServiceDescription.Identity(BuildConfig.APP_NAME, "client", getIdentityType()));
    }

    String getIdentityVersion() {
        return BuildConfig.VERSION_NAME;
    }

    String getIdentityType() {
        if ("chromium".equals(android.os.Build.BRAND)) {
            return "pc";
        } else if (context.getResources().getBoolean(R.bool.is_device_table)) {
            return "tablet";
        } else {
            return "phone";
        }
    }

    public void handleVersionRequest(final Iq request) {
        final var version = new Version();
        version.setSoftwareName(context.getString(R.string.app_name));
        version.setVersion(getIdentityVersion());
        if ("chromium".equals(android.os.Build.BRAND)) {
            version.setOs("Chrome OS");
        } else {
            version.setOs("Android");
        }
        Log.d(Config.LOGTAG, "responding to version request from " + request.getFrom());
        connection.sendResultFor(request, version);
    }

    public void handleInfoQuery(final Iq request) {
        final var infoQueryRequest = request.getExtension(InfoQuery.class);
        final var nodeRequest = infoQueryRequest.getNode();
        final ServiceDescription serviceDescription;
        if (Strings.isNullOrEmpty(nodeRequest)) {
            serviceDescription = getServiceDescription();
            Log.d(Config.LOGTAG, "responding to disco request w/o node from " + request.getFrom());
        } else {
            final var hash = buildHashFromNode(nodeRequest);
            final var cachedServiceDescription =
                    hash != null
                            ? getManager(PresenceManager.class).getCachedServiceDescription(hash)
                            : null;
            if (cachedServiceDescription != null) {
                Log.d(
                        Config.LOGTAG,
                        "responding to disco request from "
                                + request.getFrom()
                                + " to node "
                                + nodeRequest
                                + " using hash "
                                + hash.getClass().getName());
                serviceDescription = cachedServiceDescription;
            } else {
                connection.sendErrorFor(request, Error.Type.CANCEL, new Condition.ItemNotFound());
                return;
            }
        }
        final var infoQuery = serviceDescription.asInfoQuery();
        infoQuery.setNode(nodeRequest);
        connection.sendResultFor(request, infoQuery);
    }

    public Map<Jid, InfoQuery> getServerItems() {
        final var builder = new ImmutableMap.Builder<Jid, InfoQuery>();
        final var domain = this.getAccount().getDomain();
        final var domainInfoQuery = get(domain);
        if (domainInfoQuery != null) {
            builder.put(domain, domainInfoQuery);
        }
        final var items = this.discoItems.get(domain);
        if (items == null) {
            return builder.build();
        }
        for (final var item : items) {
            final var infoQuery = get(item);
            if (infoQuery == null) {
                continue;
            }
            builder.put(item, infoQuery);
        }
        return builder.buildKeepingLast();
    }

    private boolean hasServerItem(final Jid address) {
        final var domain = this.getAccount().getDomain();
        final var items = this.discoItems.get(domain);
        return items != null && items.contains(address);
    }

    public boolean hasServerFeature(final String feature) {
        final var infoQuery = this.get(getAccount().getDomain());
        return infoQuery != null && infoQuery.hasFeature(feature);
    }

    public boolean hasAccountFeature(final String feature) {
        final var infoQuery = this.get(getAccount().getJid().asBareJid());
        return infoQuery != null && infoQuery.hasFeature(feature);
    }

    private void put(final Jid address, final InfoQuery infoQuery) {
        synchronized (this.entityInformation) {
            this.entityInformation.put(address, infoQuery);
        }
    }

    // TODO would optional be better instead of returning null?!
    public InfoQuery get(final Jid address) {
        synchronized (this.entityInformation) {
            return this.entityInformation.get(address);
        }
    }

    public List<Optional<InfoQuery>> get(final List<Presence> presences) {
        synchronized (this.entityInformation) {
            return ImmutableList.copyOf(
                    Lists.transform(
                            presences,
                            p -> Optional.fromNullable(this.entityInformation.get(p.getFrom()))));
        }
    }

    public Jid getAddressForCommand(final String node) {
        synchronized (this.commands) {
            return this.commands.get(node);
        }
    }

    public void clear() {
        final var account = getAccount().getJid().asBareJid();
        final var domain = account.getDomain();
        final Set<Jid> items;
        synchronized (this.discoItems) {
            final var discoItems = this.discoItems.get(domain);
            if (discoItems != null) {
                items = ImmutableSet.copyOf(discoItems);
            } else {
                items = Collections.emptySet();
            }
            this.discoItems.clear();
        }
        synchronized (this.entityInformation) {
            this.entityInformation.remove(account);
            this.entityInformation.remove(domain);
            for (final var item : items) {
                this.entityInformation.remove(item);
            }
        }
        synchronized (this.commands) {
            this.commands.clear();
        }
    }

    public void clear(final Jid address) {
        final var account = getAccount().getJid().asBareJid();
        if (hasServerItem(address)
                || account.equals(address)
                || account.getDomain().equals(address)) {
            Log.d(Config.LOGTAG, "do not clear disco#info for " + address);
            return;
        }
        synchronized (this.entityInformation) {
            if (address.isFullJid()) {
                this.entityInformation.remove(address);
            } else {
                final var iterator = this.entityInformation.entrySet().iterator();
                while (iterator.hasNext()) {
                    final var entry = iterator.next();
                    if (entry.getKey().asBareJid().equals(address)) {
                        iterator.remove();
                    }
                }
            }
        }
    }

    public Map<Jid, InfoQuery> findDiscoItemsByFeature(final String feature) {
        return Maps.filterValues(getServerItems(), v -> v.hasFeature(feature));
    }

    public Map.Entry<Jid, InfoQuery> findDiscoItemByFeature(final String feature) {
        final var items = findDiscoItemsByFeature(feature);
        return Iterables.getFirst(items.entrySet(), null);
    }

    public boolean hasServerCommands() {
        return hasServerFeature(Namespace.COMMANDS);
    }

    public void fetchServerCommands() {
        final var future = commands(Entity.discoItem(getAccount().getDomain()));
        Futures.addCallback(
                future,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(Map<String, Jid> result) {
                        synchronized (commands) {
                            commands.clear();
                            commands.putAll(result);
                        }
                    }

                    @Override
                    public void onFailure(@androidx.annotation.NonNull Throwable throwable) {
                        Log.d(
                                Config.LOGTAG,
                                getAccount().getJid().asBareJid() + ": could not fetch commands",
                                throwable);
                    }
                },
                MoreExecutors.directExecutor());
    }

    private static String notify(final String feature) {
        return String.format("%s+notify", feature);
    }

    public static final class CapsHashMismatchException extends IllegalStateException {
        public CapsHashMismatchException(final String message) {
            super(message);
        }
    }
}
