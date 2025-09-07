package tech.ravensoftware.chat.xmpp;

import com.google.common.collect.ClassToInstanceMap;
import com.google.common.collect.ImmutableClassToInstanceMap;
import tech.ravensoftware.chat.services.XmppConnectionService;
import tech.ravensoftware.chat.xmpp.manager.AbstractManager;
import tech.ravensoftware.chat.xmpp.manager.AvatarManager;
import tech.ravensoftware.chat.xmpp.manager.AxolotlManager;
import tech.ravensoftware.chat.xmpp.manager.BlockingManager;
import tech.ravensoftware.chat.xmpp.manager.BookmarkManager;
import tech.ravensoftware.chat.xmpp.manager.CarbonsManager;
import tech.ravensoftware.chat.xmpp.manager.DiscoManager;
import tech.ravensoftware.chat.xmpp.manager.DisplayedManager;
import tech.ravensoftware.chat.xmpp.manager.EasyOnboardingManager;
import tech.ravensoftware.chat.xmpp.manager.EntityTimeManager;
import tech.ravensoftware.chat.xmpp.manager.ExternalServiceDiscoveryManager;
import tech.ravensoftware.chat.xmpp.manager.HttpUploadManager;
import tech.ravensoftware.chat.xmpp.manager.LegacyBookmarkManager;
import tech.ravensoftware.chat.xmpp.manager.MessageArchiveManager;
import tech.ravensoftware.chat.xmpp.manager.MessageDisplayedSynchronizationManager;
import tech.ravensoftware.chat.xmpp.manager.ModerationManager;
import tech.ravensoftware.chat.xmpp.manager.MultiUserChatManager;
import tech.ravensoftware.chat.xmpp.manager.NativeBookmarkManager;
import tech.ravensoftware.chat.xmpp.manager.NickManager;
import tech.ravensoftware.chat.xmpp.manager.OfflineMessagesManager;
import tech.ravensoftware.chat.xmpp.manager.PepManager;
import tech.ravensoftware.chat.xmpp.manager.PingManager;
import tech.ravensoftware.chat.xmpp.manager.PresenceManager;
import tech.ravensoftware.chat.xmpp.manager.PrivateStorageManager;
import tech.ravensoftware.chat.xmpp.manager.PubSubManager;
import tech.ravensoftware.chat.xmpp.manager.PushNotificationManager;
import tech.ravensoftware.chat.xmpp.manager.RegistrationManager;
import tech.ravensoftware.chat.xmpp.manager.RosterManager;
import tech.ravensoftware.chat.xmpp.manager.StreamHostManager;
import tech.ravensoftware.chat.xmpp.manager.UnifiedPushManager;
import tech.ravensoftware.chat.xmpp.manager.VCardManager;

public class Managers {

    private Managers() {
        throw new AssertionError("Do not instantiate me");
    }

    public static ClassToInstanceMap<AbstractManager> get(
            final XmppConnectionService context, final XmppConnection connection) {
        return new ImmutableClassToInstanceMap.Builder<AbstractManager>()
                .put(AvatarManager.class, new AvatarManager(context, connection))
                .put(AxolotlManager.class, new AxolotlManager(context, connection))
                .put(BlockingManager.class, new BlockingManager(context, connection))
                .put(BookmarkManager.class, new BookmarkManager(context, connection))
                .put(CarbonsManager.class, new CarbonsManager(context, connection))
                .put(DiscoManager.class, new DiscoManager(context, connection))
                .put(DisplayedManager.class, new DisplayedManager(context, connection))
                .put(EasyOnboardingManager.class, new EasyOnboardingManager(context, connection))
                .put(EntityTimeManager.class, new EntityTimeManager(context, connection))
                .put(
                        ExternalServiceDiscoveryManager.class,
                        new ExternalServiceDiscoveryManager(context, connection))
                .put(HttpUploadManager.class, new HttpUploadManager(context, connection))
                .put(LegacyBookmarkManager.class, new LegacyBookmarkManager(context, connection))
                .put(MessageArchiveManager.class, new MessageArchiveManager(context, connection))
                .put(
                        MessageDisplayedSynchronizationManager.class,
                        new MessageDisplayedSynchronizationManager(context, connection))
                .put(ModerationManager.class, new ModerationManager(context, connection))
                .put(MultiUserChatManager.class, new MultiUserChatManager(context, connection))
                .put(NativeBookmarkManager.class, new NativeBookmarkManager(context, connection))
                .put(NickManager.class, new NickManager(context, connection))
                .put(OfflineMessagesManager.class, new OfflineMessagesManager(context, connection))
                .put(PepManager.class, new PepManager(context, connection))
                .put(PingManager.class, new PingManager(context, connection))
                .put(PresenceManager.class, new PresenceManager(context, connection))
                .put(PrivateStorageManager.class, new PrivateStorageManager(context, connection))
                .put(PubSubManager.class, new PubSubManager(context, connection))
                .put(
                        PushNotificationManager.class,
                        new PushNotificationManager(context, connection))
                .put(RegistrationManager.class, new RegistrationManager(context, connection))
                .put(RosterManager.class, new RosterManager(context, connection))
                .put(StreamHostManager.class, new StreamHostManager(context, connection))
                .put(UnifiedPushManager.class, new UnifiedPushManager(context, connection))
                .put(VCardManager.class, new VCardManager(context, connection))
                .build();
    }
}
