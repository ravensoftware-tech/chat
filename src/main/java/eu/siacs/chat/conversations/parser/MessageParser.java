package tech.ravensoftware.chat.parser;

import android.util.Log;
import android.util.Pair;
import com.google.common.base.Strings;
import tech.ravensoftware.chat.AppSettings;
import tech.ravensoftware.chat.Config;
import tech.ravensoftware.chat.R;
import tech.ravensoftware.chat.crypto.axolotl.AxolotlService;
import tech.ravensoftware.chat.crypto.axolotl.BrokenSessionException;
import tech.ravensoftware.chat.crypto.axolotl.NotEncryptedForThisDeviceException;
import tech.ravensoftware.chat.crypto.axolotl.OutdatedSenderException;
import tech.ravensoftware.chat.crypto.axolotl.XmppAxolotlMessage;
import tech.ravensoftware.chat.entities.Account;
import tech.ravensoftware.chat.entities.Contact;
import tech.ravensoftware.chat.entities.Conversation;
import tech.ravensoftware.chat.entities.Conversational;
import tech.ravensoftware.chat.entities.Message;
import tech.ravensoftware.chat.entities.MucOptions;
import tech.ravensoftware.chat.entities.Reaction;
import tech.ravensoftware.chat.entities.ReadByMarker;
import tech.ravensoftware.chat.entities.ReceiptRequest;
import tech.ravensoftware.chat.entities.RtpSessionStatus;
import tech.ravensoftware.chat.http.HttpConnectionManager;
import tech.ravensoftware.chat.services.XmppConnectionService;
import tech.ravensoftware.chat.utils.CryptoHelper;
import tech.ravensoftware.chat.xml.Element;
import tech.ravensoftware.chat.xml.LocalizedContent;
import tech.ravensoftware.chat.xml.Namespace;
import tech.ravensoftware.chat.xmpp.Jid;
import tech.ravensoftware.chat.xmpp.XmppConnection;
import tech.ravensoftware.chat.xmpp.chatstate.ChatState;
import tech.ravensoftware.chat.xmpp.jingle.JingleConnectionManager;
import tech.ravensoftware.chat.xmpp.jingle.JingleRtpConnection;
import tech.ravensoftware.chat.xmpp.manager.MessageArchiveManager;
import tech.ravensoftware.chat.xmpp.manager.ModerationManager;
import tech.ravensoftware.chat.xmpp.manager.MultiUserChatManager;
import tech.ravensoftware.chat.xmpp.manager.PubSubManager;
import tech.ravensoftware.chat.xmpp.manager.RosterManager;
import im.conversations.android.xmpp.model.Extension;
import im.conversations.android.xmpp.model.axolotl.Encrypted;
import im.conversations.android.xmpp.model.carbons.Received;
import im.conversations.android.xmpp.model.carbons.Sent;
import im.conversations.android.xmpp.model.conference.DirectInvite;
import im.conversations.android.xmpp.model.correction.Replace;
import im.conversations.android.xmpp.model.forward.Forwarded;
import im.conversations.android.xmpp.model.mam.Result;
import im.conversations.android.xmpp.model.markers.Displayed;
import im.conversations.android.xmpp.model.markers.Markable;
import im.conversations.android.xmpp.model.muc.user.MucUser;
import im.conversations.android.xmpp.model.nick.Nick;
import im.conversations.android.xmpp.model.occupant.OccupantId;
import im.conversations.android.xmpp.model.oob.OutOfBandData;
import im.conversations.android.xmpp.model.pubsub.event.Event;
import im.conversations.android.xmpp.model.reactions.Reactions;
import im.conversations.android.xmpp.model.receipts.Request;
import im.conversations.android.xmpp.model.retraction.Retract;
import im.conversations.android.xmpp.model.unique.StanzaId;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

public class MessageParser extends AbstractParser
        implements Consumer<im.conversations.android.xmpp.model.stanza.Message> {

    private static final SimpleDateFormat TIME_FORMAT =
            new SimpleDateFormat("HH:mm:ss", Locale.ENGLISH);

    private static final List<String> JINGLE_MESSAGE_ELEMENT_NAMES =
            Arrays.asList("accept", "propose", "proceed", "reject", "retract", "ringing", "finish");

    public MessageParser(final XmppConnectionService service, final XmppConnection connection) {
        super(service, connection);
    }

    private String extractStanzaId(
            final im.conversations.android.xmpp.model.stanza.Message packet,
            final boolean isTypeGroupChat,
            final Conversation conversation) {
        final Jid by;
        final boolean safeToExtract;
        if (isTypeGroupChat) {
            by = conversation.getAddress().asBareJid();
            safeToExtract =
                    getManager(MultiUserChatManager.class)
                            .getOrCreateState(conversation)
                            .hasFeature(Namespace.STANZA_IDS);
        } else {
            Account account = conversation.getAccount();
            by = account.getJid().asBareJid();
            safeToExtract = account.getXmppConnection().getFeatures().stanzaIds();
        }
        return safeToExtract ? StanzaId.get(packet, by) : null;
    }

    private static String extractStanzaId(
            final Account account,
            final im.conversations.android.xmpp.model.stanza.Message packet) {
        final boolean safeToExtract = account.getXmppConnection().getFeatures().stanzaIds();
        return safeToExtract ? StanzaId.get(packet, account.getJid().asBareJid()) : null;
    }

    private boolean extractChatState(
            Conversation c,
            final boolean isTypeGroupChat,
            final im.conversations.android.xmpp.model.stanza.Message packet) {
        ChatState state = ChatState.parse(packet);
        if (state != null && c != null) {
            final Account account = c.getAccount();
            final Jid from = packet.getFrom();
            if (from.asBareJid().equals(account.getJid().asBareJid())) {
                c.setOutgoingChatState(state);
                if (state == ChatState.ACTIVE || state == ChatState.COMPOSING) {
                    if (c.getContact().isSelf()) {
                        return false;
                    }
                    mXmppConnectionService.markRead(c);
                    activateGracePeriod(account);
                }
                return false;
            } else {
                if (isTypeGroupChat) {
                    // TODO we can use Manager.getUser; we don’t even need the conversation
                    MucOptions.User user =
                            getManager(MultiUserChatManager.class)
                                    .getOrCreateState(c)
                                    .getUser(from);
                    if (user != null) {
                        return user.setChatState(state);
                    } else {
                        return false;
                    }
                } else {
                    return c.setIncomingChatState(state);
                }
            }
        }
        return false;
    }

    private Message parseAxolotlChat(
            final Encrypted axolotlMessage,
            final Jid from,
            final Conversation conversation,
            final int status,
            final boolean checkedForDuplicates,
            final boolean postpone) {
        final AxolotlService service = conversation.getAccount().getAxolotlService();
        final XmppAxolotlMessage xmppAxolotlMessage;
        try {
            xmppAxolotlMessage = XmppAxolotlMessage.fromElement(axolotlMessage, from.asBareJid());
        } catch (final Exception e) {
            Log.d(
                    Config.LOGTAG,
                    conversation.getAccount().getJid().asBareJid()
                            + ": invalid omemo message received "
                            + e.getMessage());
            return null;
        }
        if (xmppAxolotlMessage.hasPayload()) {
            final XmppAxolotlMessage.XmppAxolotlPlaintextMessage plaintextMessage;
            try {
                plaintextMessage =
                        service.processReceivingPayloadMessage(xmppAxolotlMessage, postpone);
            } catch (BrokenSessionException e) {
                if (checkedForDuplicates) {
                    if (service.trustedOrPreviouslyResponded(from.asBareJid())) {
                        service.reportBrokenSessionException(e, postpone);
                        return new Message(
                                conversation, "", Message.ENCRYPTION_AXOLOTL_FAILED, status);
                    } else {
                        Log.d(
                                Config.LOGTAG,
                                "ignoring broken session exception because contact was not"
                                        + " trusted");
                        return new Message(
                                conversation, "", Message.ENCRYPTION_AXOLOTL_FAILED, status);
                    }
                } else {
                    Log.d(
                            Config.LOGTAG,
                            "ignoring broken session exception because checkForDuplicates failed");
                    return null;
                }
            } catch (NotEncryptedForThisDeviceException e) {
                return new Message(
                        conversation, "", Message.ENCRYPTION_AXOLOTL_NOT_FOR_THIS_DEVICE, status);
            } catch (OutdatedSenderException e) {
                return new Message(conversation, "", Message.ENCRYPTION_AXOLOTL_FAILED, status);
            }
            if (plaintextMessage != null) {
                Message finishedMessage =
                        new Message(
                                conversation,
                                plaintextMessage.getPlaintext(),
                                Message.ENCRYPTION_AXOLOTL,
                                status);
                finishedMessage.setFingerprint(plaintextMessage.getFingerprint());
                Log.d(
                        Config.LOGTAG,
                        AxolotlService.getLogprefix(finishedMessage.getConversation().getAccount())
                                + " Received Message with session fingerprint: "
                                + plaintextMessage.getFingerprint());
                return finishedMessage;
            }
        } else {
            Log.d(
                    Config.LOGTAG,
                    conversation.getAccount().getJid().asBareJid()
                            + ": received OMEMO key transport message");
            service.processReceivingKeyTransportMessage(xmppAxolotlMessage, postpone);
        }
        return null;
    }

    private Invite extractInvite(final im.conversations.android.xmpp.model.stanza.Message message) {
        final Element mucUser = message.findChild("x", Namespace.MUC_USER);
        if (mucUser != null) {
            final Element invite = mucUser.findChild("invite");
            if (invite != null) {
                final String password = mucUser.findChildContent("password");
                final Jid from = Jid.Invalid.getNullForInvalid(invite.getAttributeAsJid("from"));
                final Jid to = Jid.Invalid.getNullForInvalid(invite.getAttributeAsJid("to"));
                if (to != null && from == null) {
                    Log.d(Config.LOGTAG, "do not parse outgoing mediated invite " + message);
                    return null;
                }
                final Jid room = Jid.Invalid.getNullForInvalid(message.getAttributeAsJid("from"));
                if (room == null) {
                    return null;
                }
                return new Invite(room, password, false, from);
            }
        }
        final var conference = message.getExtension(DirectInvite.class);
        if (conference != null) {
            Jid from = Jid.Invalid.getNullForInvalid(message.getAttributeAsJid("from"));
            Jid room = Jid.Invalid.getNullForInvalid(conference.getAttributeAsJid("jid"));
            if (room == null) {
                return null;
            }
            return new Invite(room, conference.getAttribute("password"), true, from);
        }
        return null;
    }

    private boolean handleErrorMessage(
            final Account account,
            final im.conversations.android.xmpp.model.stanza.Message packet) {
        if (packet.getType() == im.conversations.android.xmpp.model.stanza.Message.Type.ERROR) {
            if (packet.fromServer(account)) {
                final var forwarded =
                        getForwardedMessagePacket(packet, "received", Namespace.CARBONS);
                if (forwarded != null) {
                    return handleErrorMessage(account, forwarded.first);
                }
            }
            final Jid from = packet.getFrom();
            final String id = packet.getId();
            if (from != null && id != null) {
                if (id.startsWith(JingleRtpConnection.JINGLE_MESSAGE_PROPOSE_ID_PREFIX)) {
                    final String sessionId =
                            id.substring(
                                    JingleRtpConnection.JINGLE_MESSAGE_PROPOSE_ID_PREFIX.length());
                    mXmppConnectionService
                            .getJingleConnectionManager()
                            .updateProposedSessionDiscovered(
                                    account,
                                    from,
                                    sessionId,
                                    JingleConnectionManager.DeviceDiscoveryState.FAILED);
                    return true;
                }
                if (id.startsWith(JingleRtpConnection.JINGLE_MESSAGE_PROCEED_ID_PREFIX)) {
                    final String sessionId =
                            id.substring(
                                    JingleRtpConnection.JINGLE_MESSAGE_PROCEED_ID_PREFIX.length());
                    final String message = extractErrorMessage(packet);
                    mXmppConnectionService
                            .getJingleConnectionManager()
                            .failProceed(account, from, sessionId, message);
                    return true;
                }
                mXmppConnectionService.markMessage(
                        account,
                        from.asBareJid(),
                        id,
                        Message.STATUS_SEND_FAILED,
                        extractErrorMessage(packet));
                final Element error = packet.findChild("error");
                final boolean pingWorthyError =
                        error != null
                                && (error.hasChild("not-acceptable")
                                        || error.hasChild("remote-server-timeout")
                                        || error.hasChild("remote-server-not-found"));
                if (pingWorthyError) {
                    Conversation conversation = mXmppConnectionService.find(account, from);
                    if (conversation != null
                            && conversation.getMode() == Conversational.MODE_MULTI) {
                        if (getManager(MultiUserChatManager.class)
                                .getOrCreateState(conversation)
                                .online()) {
                            Log.d(
                                    Config.LOGTAG,
                                    account.getJid().asBareJid()
                                            + ": received ping worthy error for seemingly online"
                                            + " muc at "
                                            + from);
                            getManager(MultiUserChatManager.class).pingAndRejoin(conversation);
                        }
                    }
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public void accept(final im.conversations.android.xmpp.model.stanza.Message original) {
        final var originalFrom = original.getFrom();
        final var account = this.getAccount();
        if (handleErrorMessage(account, original)) {
            return;
        }
        final im.conversations.android.xmpp.model.stanza.Message packet;
        Long timestamp = null;
        boolean isCarbon = false;
        String serverMsgId = null;
        final var result = original.getExtension(Result.class);
        final String queryId = result == null ? null : result.getQueryId();
        final MessageArchiveManager.Query query =
                queryId == null ? null : getManager(MessageArchiveManager.class).findQuery(queryId);
        final boolean offlineMessagesRetrieved = connection.isOfflineMessagesRetrieved();
        if (query != null
                && getManager(MessageArchiveManager.class).validFrom(query, original.getFrom())) {
            final var f = result.getForwarded();
            final var stamp = f == null ? null : f.getStamp();
            final var m = f == null ? null : f.getMessage();
            if (stamp == null || m == null) {
                return;
            }

            timestamp = stamp.toEpochMilli();
            packet = m;
            serverMsgId = result.getId();
            query.incrementMessageCount();

            if (query.isImplausibleFrom(packet.getFrom())) {
                Log.d(Config.LOGTAG, "found implausible from in MUC MAM archive");
                return;
            }

            if (handleErrorMessage(account, packet)) {
                return;
            }
        } else if (query != null) {
            Log.d(
                    Config.LOGTAG,
                    account.getJid().asBareJid()
                            + ": received mam result with invalid from ("
                            + original.getFrom()
                            + ") or queryId ("
                            + queryId
                            + ")");
            return;
        } else if (original.fromServer(account)
                && original.getType()
                        != im.conversations.android.xmpp.model.stanza.Message.Type.GROUPCHAT) {
            Pair<im.conversations.android.xmpp.model.stanza.Message, Long> f;
            f = getForwardedMessagePacket(original, Received.class);
            f = f == null ? getForwardedMessagePacket(original, Sent.class) : f;
            packet = f != null ? f.first : original;
            if (handleErrorMessage(account, packet)) {
                return;
            }
            timestamp = f != null ? f.second : null;
            isCarbon = f != null;
        } else {
            packet = original;
        }

        if (timestamp == null) {
            timestamp =
                    AbstractParser.parseTimestamp(original, AbstractParser.parseTimestamp(packet));
        }
        final LocalizedContent body = packet.getBody();
        final Element mucUserElement = packet.findChild("x", Namespace.MUC_USER);
        final boolean isTypeGroupChat =
                packet.getType()
                        == im.conversations.android.xmpp.model.stanza.Message.Type.GROUPCHAT;
        final var encrypted =
                packet.getOnlyExtension(im.conversations.android.xmpp.model.pgp.Encrypted.class);
        final String pgpEncrypted = encrypted == null ? null : encrypted.getContent();

        final var oob = packet.getExtension(OutOfBandData.class);
        final String oobUrl = oob != null ? oob.getURL() : null;
        final var replace = packet.getExtension(Replace.class);
        final var replacementId = replace == null ? null : replace.getId();
        final var axolotlEncrypted = packet.getOnlyExtension(Encrypted.class);
        // TODO this can probably be refactored to be final
        int status;
        final Jid counterpart;
        final Jid to = packet.getTo();
        final Jid from = packet.getFrom();
        final Element originId = packet.findChild("origin-id", Namespace.STANZA_IDS);
        final String remoteMsgId;
        if (originId != null && originId.getAttribute("id") != null) {
            remoteMsgId = originId.getAttribute("id");
        } else {
            remoteMsgId = packet.getId();
        }
        boolean notify = false;

        if (from == null || !Jid.Invalid.isValid(from) || !Jid.Invalid.isValid(to)) {
            Log.e(Config.LOGTAG, "encountered invalid message from='" + from + "' to='" + to + "'");
            return;
        }
        if (query != null && !query.muc() && isTypeGroupChat) {
            Log.e(
                    Config.LOGTAG,
                    account.getJid().asBareJid()
                            + ": received groupchat ("
                            + from
                            + ") message on regular MAM request. skipping");
            return;
        }
        boolean isMucStatusMessage =
                Jid.Invalid.hasValidFrom(packet)
                        && from.isBareJid()
                        && mucUserElement != null
                        && mucUserElement.hasChild("status");
        boolean selfAddressed;
        if (packet.fromAccount(account)) {
            status = Message.STATUS_SEND;
            selfAddressed = to == null || account.getJid().asBareJid().equals(to.asBareJid());
            if (selfAddressed) {
                counterpart = from;
            } else {
                counterpart = to;
            }
        } else {
            status = Message.STATUS_RECEIVED;
            counterpart = from;
            selfAddressed = false;
        }

        final Invite invite = extractInvite(packet);
        if (invite != null) {
            if (invite.jid.asBareJid().equals(account.getJid().asBareJid())) {
                Log.d(
                        Config.LOGTAG,
                        account.getJid().asBareJid()
                                + ": ignore invite to "
                                + invite.jid
                                + " because it matches account");
            } else if (isTypeGroupChat) {
                Log.d(
                        Config.LOGTAG,
                        account.getJid().asBareJid()
                                + ": ignoring invite to "
                                + invite.jid
                                + " because it was received as group chat");
            } else if (invite.direct
                    && (mucUserElement != null
                            || invite.inviter == null
                            || getManager(MultiUserChatManager.class).isMuc(invite.inviter))) {
                Log.d(
                        Config.LOGTAG,
                        account.getJid().asBareJid()
                                + ": ignoring direct invite to "
                                + invite.jid
                                + " because it was received in MUC");
            } else {
                invite.execute(account);
                return;
            }
        }

        if ((body != null
                        || pgpEncrypted != null
                        || (axolotlEncrypted != null && axolotlEncrypted.hasChild("payload"))
                        || oobUrl != null)
                && !isMucStatusMessage) {
            final boolean conversationIsProbablyMuc =
                    isTypeGroupChat
                            || mucUserElement != null
                            || connection
                                    .getMucServersWithholdAccount()
                                    .contains(counterpart.getDomain());
            final Conversation conversation =
                    mXmppConnectionService.findOrCreateConversation(
                            account,
                            counterpart.asBareJid(),
                            conversationIsProbablyMuc,
                            false,
                            query,
                            false);
            final boolean conversationMultiMode = conversation.getMode() == Conversation.MODE_MULTI;

            if (serverMsgId == null) {
                serverMsgId = extractStanzaId(packet, isTypeGroupChat, conversation);
            }

            if (selfAddressed) {
                // don’t store serverMsgId on reflections for edits
                final var reflectedServerMsgId =
                        Strings.isNullOrEmpty(replacementId) ? serverMsgId : null;
                if (mXmppConnectionService.markMessage(
                        conversation,
                        remoteMsgId,
                        Message.STATUS_SEND_RECEIVED,
                        reflectedServerMsgId)) {
                    return;
                }
                status = Message.STATUS_RECEIVED;
                if (remoteMsgId != null
                        && conversation.findMessageWithRemoteId(remoteMsgId, counterpart) != null) {
                    return;
                }
            }

            if (isTypeGroupChat) {
                // this should probably remain a counterpart check
                if (getManager(MultiUserChatManager.class)
                        .getOrCreateState(conversation)
                        .isSelf(counterpart)) {
                    status = Message.STATUS_SEND_RECEIVED;
                    isCarbon = true; // not really carbon but received from another resource
                    // don’t store serverMsgId on reflections for edits
                    final var reflectedServerMsgId =
                            Strings.isNullOrEmpty(replacementId) ? serverMsgId : null;
                    if (mXmppConnectionService.markMessage(
                            conversation, remoteMsgId, status, reflectedServerMsgId, body)) {
                        return;
                    } else if (remoteMsgId == null || Config.IGNORE_ID_REWRITE_IN_MUC) {
                        if (body != null) {
                            Message message = conversation.findSentMessageWithBody(body.content);
                            if (message != null) {
                                mXmppConnectionService.markMessage(message, status);
                                return;
                            }
                        }
                    }
                } else {
                    final var user =
                            getManager(MultiUserChatManager.class).getMucUser(packet, query);
                    if (user != null) {
                        final var mucOptions =
                                getManager(MultiUserChatManager.class).getState(from.asBareJid());
                        if (mucOptions != null && mucOptions.isOurAccount(user)) {
                            status = Message.STATUS_SEND_RECEIVED;
                            isCarbon = true;
                        } else {
                            status = Message.STATUS_RECEIVED;
                        }
                    } else {
                        status = Message.STATUS_RECEIVED;
                    }
                }
            }
            final Message message;
            if (pgpEncrypted != null && Config.supportOpenPgp()) {
                message = new Message(conversation, pgpEncrypted, Message.ENCRYPTION_PGP, status);
            } else if (axolotlEncrypted != null && Config.supportOmemo()) {
                final Jid origin;
                if (conversationMultiMode) {
                    final var user =
                            getManager(MultiUserChatManager.class).getMucUser(packet, query);
                    origin = user == null ? null : user.getRealJid();
                    if (origin == null) {
                        Log.d(Config.LOGTAG, "received omemo message in anonymous conference");
                        return;
                    }

                } else {
                    origin = from;
                }

                final boolean liveMessage =
                        query == null && !isTypeGroupChat && mucUserElement == null;
                final boolean checkedForDuplicates =
                        liveMessage
                                || (serverMsgId != null
                                        && remoteMsgId != null
                                        && !conversation.possibleDuplicate(
                                                serverMsgId, remoteMsgId));

                message =
                        parseAxolotlChat(
                                axolotlEncrypted,
                                origin,
                                conversation,
                                status,
                                checkedForDuplicates,
                                query != null);
                if (message == null) {
                    if (query == null
                            && extractChatState(
                                    mXmppConnectionService.find(account, counterpart.asBareJid()),
                                    isTypeGroupChat,
                                    packet)) {
                        mXmppConnectionService.updateConversationUi();
                    }
                    if (query != null && status == Message.STATUS_SEND && remoteMsgId != null) {
                        Message previouslySent = conversation.findSentMessageWithUuid(remoteMsgId);
                        if (previouslySent != null
                                && previouslySent.getServerMsgId() == null
                                && serverMsgId != null) {
                            previouslySent.setServerMsgId(serverMsgId);
                            mXmppConnectionService.databaseBackend.updateMessage(
                                    previouslySent, false);
                            Log.d(
                                    Config.LOGTAG,
                                    account.getJid().asBareJid()
                                            + ": encountered previously sent OMEMO message without"
                                            + " serverId. updating...");
                        }
                    }
                    return;
                }
                if (conversationMultiMode) {
                    message.setTrueCounterpart(origin);
                }
            } else if (body == null && oobUrl != null) {
                message = new Message(conversation, oobUrl, Message.ENCRYPTION_NONE, status);
                message.setOob(true);
                if (CryptoHelper.isPgpEncryptedUrl(oobUrl)) {
                    message.setEncryption(Message.ENCRYPTION_DECRYPTED);
                }
            } else {
                message = new Message(conversation, body.content, Message.ENCRYPTION_NONE, status);
                if (body.count > 1) {
                    message.setBodyLanguage(body.language);
                }
            }

            message.setCounterpart(counterpart);
            message.setRemoteMsgId(remoteMsgId);
            message.setServerMsgId(serverMsgId);
            message.setCarbon(isCarbon);
            message.setTime(timestamp);
            if (body != null && body.content != null && body.content.equals(oobUrl)) {
                message.setOob(true);
                if (CryptoHelper.isPgpEncryptedUrl(oobUrl)) {
                    message.setEncryption(Message.ENCRYPTION_DECRYPTED);
                }
            }
            message.markable = packet.hasExtension(Markable.class);
            if (conversationMultiMode) {
                final var mucOptions =
                        getManager(MultiUserChatManager.class).getOrCreateState(conversation);
                final var occupantId =
                        mucOptions.occupantId() ? packet.getOnlyExtension(OccupantId.class) : null;
                if (occupantId != null) {
                    message.setOccupantId(occupantId.getId());
                }
                final var user = getManager(MultiUserChatManager.class).getMucUser(packet, query);
                final var trueCounterpart = user == null ? null : user.getRealJid();
                message.setTrueCounterpart(trueCounterpart);
                if (!isTypeGroupChat) {
                    message.setType(Message.TYPE_PRIVATE);
                }
            } else {
                updateLastseen(account, from);
            }

            if (replacementId != null && mXmppConnectionService.allowMessageCorrection()) {
                final Message replacedMessage =
                        conversation.findMessageWithRemoteIdAndCounterpart(
                                replacementId,
                                counterpart,
                                message.getStatus() == Message.STATUS_RECEIVED,
                                message.isCarbon());
                if (replacedMessage != null) {
                    final boolean fingerprintsMatch =
                            replacedMessage.getFingerprint() == null
                                    || replacedMessage
                                            .getFingerprint()
                                            .equals(message.getFingerprint());
                    final boolean trueCountersMatch =
                            replacedMessage.getTrueCounterpart() != null
                                    && message.getTrueCounterpart() != null
                                    && replacedMessage
                                            .getTrueCounterpart()
                                            .asBareJid()
                                            .equals(message.getTrueCounterpart().asBareJid());
                    final boolean occupantIdMatch =
                            replacedMessage.getOccupantId() != null
                                    && replacedMessage
                                            .getOccupantId()
                                            .equals(message.getOccupantId());
                    final boolean duplicate = conversation.hasDuplicateMessage(message);
                    if (fingerprintsMatch
                            && (trueCountersMatch || occupantIdMatch || !conversationMultiMode)
                            && !duplicate) {
                        synchronized (replacedMessage) {
                            final String uuid = replacedMessage.getUuid();
                            replacedMessage.setUuid(UUID.randomUUID().toString());
                            replacedMessage.setBody(message.getBody());
                            // we store the IDs of the replacing message. This is essentially unused
                            // today (only the fact that there are _some_ edits causes the edit icon
                            // to appear)
                            replacedMessage.putEdited(
                                    message.getRemoteMsgId(), message.getServerMsgId());

                            // we used to call
                            // `replacedMessage.setServerMsgId(message.getServerMsgId());` so during
                            // catchup we could start from the edit; not the original message
                            // however this caused problems for things like reactions that refer to
                            // the serverMsgId

                            replacedMessage.setEncryption(message.getEncryption());
                            if (replacedMessage.getStatus() == Message.STATUS_RECEIVED) {
                                replacedMessage.markUnread();
                            }
                            extractChatState(
                                    mXmppConnectionService.find(account, counterpart.asBareJid()),
                                    isTypeGroupChat,
                                    packet);
                            mXmppConnectionService.updateMessage(replacedMessage, uuid);
                            if (mXmppConnectionService.confirmMessages()
                                    && replacedMessage.getStatus() == Message.STATUS_RECEIVED
                                    && (replacedMessage.trusted()
                                            || replacedMessage
                                                    .isPrivateMessage()) // TODO do we really want
                                    // to send receipts for all
                                    // PMs?
                                    && remoteMsgId != null
                                    && !selfAddressed
                                    && !isTypeGroupChat) {
                                processMessageReceipts(account, packet, remoteMsgId, query);
                            }
                            if (replacedMessage.getEncryption() == Message.ENCRYPTION_PGP) {
                                conversation
                                        .getAccount()
                                        .getPgpDecryptionService()
                                        .discard(replacedMessage);
                                conversation
                                        .getAccount()
                                        .getPgpDecryptionService()
                                        .decrypt(replacedMessage, false);
                            }
                        }
                        mXmppConnectionService.getNotificationService().updateNotification();
                        return;
                    } else {
                        Log.d(
                                Config.LOGTAG,
                                account.getJid().asBareJid()
                                        + ": received message correction but verification didn't"
                                        + " check out");
                    }
                }
            }

            long deletionDate = mXmppConnectionService.getAutomaticMessageDeletionDate();
            if (deletionDate != 0 && message.getTimeSent() < deletionDate) {
                Log.d(
                        Config.LOGTAG,
                        account.getJid().asBareJid()
                                + ": skipping message from "
                                + message.getCounterpart().toString()
                                + " because it was sent prior to our deletion date");
                return;
            }

            boolean checkForDuplicates =
                    (isTypeGroupChat && packet.hasChild("delay", "urn:xmpp:delay"))
                            || message.isPrivateMessage()
                            || message.getServerMsgId() != null
                            || (query == null
                                    && getManager(MessageArchiveManager.class)
                                            .isCatchupInProgress(conversation));
            if (checkForDuplicates) {
                final Message duplicate = conversation.findDuplicateMessage(message);
                if (duplicate != null) {
                    final boolean serverMsgIdUpdated;
                    if (duplicate.getStatus() != Message.STATUS_RECEIVED
                            && duplicate.getUuid().equals(message.getRemoteMsgId())
                            && duplicate.getServerMsgId() == null
                            && message.getServerMsgId() != null) {
                        duplicate.setServerMsgId(message.getServerMsgId());
                        if (mXmppConnectionService.databaseBackend.updateMessage(
                                duplicate, false)) {
                            serverMsgIdUpdated = true;
                        } else {
                            serverMsgIdUpdated = false;
                            Log.e(Config.LOGTAG, "failed to update message");
                        }
                    } else {
                        serverMsgIdUpdated = false;
                    }
                    Log.d(
                            Config.LOGTAG,
                            "skipping duplicate message with "
                                    + message.getCounterpart()
                                    + ". serverMsgIdUpdated="
                                    + serverMsgIdUpdated);
                    return;
                }
            }

            if (query != null
                    && query.getPagingOrder() == MessageArchiveManager.PagingOrder.REVERSE) {
                conversation.prepend(query.getActualInThisQuery(), message);
            } else {
                conversation.add(message);
            }
            if (query != null) {
                query.incrementActualMessageCount();
            }

            if (query == null || query.isCatchup()) { // either no mam or catchup
                if (status == Message.STATUS_SEND || status == Message.STATUS_SEND_RECEIVED) {
                    mXmppConnectionService.markRead(conversation);
                    if (query == null) {
                        activateGracePeriod(account);
                    }
                } else {
                    message.markUnread();
                    notify = true;
                }
            }

            if (message.getEncryption() == Message.ENCRYPTION_PGP) {
                notify =
                        conversation
                                .getAccount()
                                .getPgpDecryptionService()
                                .decrypt(message, notify);
            } else if (message.getEncryption() == Message.ENCRYPTION_AXOLOTL_NOT_FOR_THIS_DEVICE
                    || message.getEncryption() == Message.ENCRYPTION_AXOLOTL_FAILED) {
                notify = false;
            }

            if (query == null) {
                extractChatState(
                        mXmppConnectionService.find(account, counterpart.asBareJid()),
                        isTypeGroupChat,
                        packet);
                mXmppConnectionService.updateConversationUi();
            }

            if (mXmppConnectionService.confirmMessages()
                    && message.getStatus() == Message.STATUS_RECEIVED
                    && (message.trusted() || message.isPrivateMessage())
                    && remoteMsgId != null
                    && !selfAddressed
                    && !isTypeGroupChat) {
                processMessageReceipts(account, packet, remoteMsgId, query);
            }

            mXmppConnectionService.databaseBackend.createMessage(message);
            final HttpConnectionManager manager =
                    this.mXmppConnectionService.getHttpConnectionManager();
            if (message.trusted()
                    && message.treatAsDownloadable()
                    && manager.getAutoAcceptFileSize() > 0) {
                manager.createNewDownloadConnection(message);
            } else if (notify) {
                if (query != null && query.isCatchup()) {
                    mXmppConnectionService.getNotificationService().pushFromBacklog(message);
                } else {
                    mXmppConnectionService.getNotificationService().push(message);
                }
            }
        } else if (!packet.hasChild("body")) { // no body

            final var conversation = mXmppConnectionService.find(account, counterpart.asBareJid());
            if (axolotlEncrypted != null) {
                final Jid origin;
                if (conversation != null && conversation.getMode() == Conversation.MODE_MULTI) {
                    final var user =
                            getManager(MultiUserChatManager.class).getMucUser(packet, query);
                    origin = user == null ? null : user.getRealJid();
                    if (origin == null) {
                        Log.d(
                                Config.LOGTAG,
                                "omemo key transport message in anonymous conference received");
                        return;
                    }
                } else if (isTypeGroupChat) {
                    return;
                } else {
                    origin = from;
                }
                try {
                    final XmppAxolotlMessage xmppAxolotlMessage =
                            XmppAxolotlMessage.fromElement(axolotlEncrypted, origin.asBareJid());
                    account.getAxolotlService()
                            .processReceivingKeyTransportMessage(xmppAxolotlMessage, query != null);
                    Log.d(
                            Config.LOGTAG,
                            account.getJid().asBareJid()
                                    + ": omemo key transport message received from "
                                    + origin);
                } catch (Exception e) {
                    Log.d(
                            Config.LOGTAG,
                            account.getJid().asBareJid()
                                    + ": invalid omemo key transport message received "
                                    + e.getMessage());
                    return;
                }
            }

            if (query == null && extractChatState(conversation, isTypeGroupChat, packet)) {
                mXmppConnectionService.updateConversationUi();
            }

            if (isTypeGroupChat) {
                if (packet.hasChild("subject")
                        && !packet.hasChild("thread")) { // We already know it has no body per above
                    if (conversation != null && conversation.getMode() == Conversation.MODE_MULTI) {
                        conversation.setHasMessagesLeftOnServer(conversation.countMessages() > 0);
                        final LocalizedContent subject = packet.getSubject();
                        if (subject != null
                                && getManager(MultiUserChatManager.class)
                                        .getOrCreateState(conversation)
                                        .setSubject(subject.content)) {
                            mXmppConnectionService.updateConversation(conversation);
                        }
                        mXmppConnectionService.updateConversationUi();
                        return;
                    }
                }
            }

            if (original.hasExtension(MucUser.class)) {
                getManager(MultiUserChatManager.class).handleStatusMessage(original);
            }

            if (!isTypeGroupChat) {
                for (Element child : packet.getChildren()) {
                    if (Namespace.JINGLE_MESSAGE.equals(child.getNamespace())
                            && JINGLE_MESSAGE_ELEMENT_NAMES.contains(child.getName())) {
                        final String action = child.getName();
                        final String sessionId = child.getAttribute("id");
                        if (sessionId == null) {
                            break;
                        }
                        if (query == null && offlineMessagesRetrieved) {
                            if (serverMsgId == null) {
                                serverMsgId = extractStanzaId(account, packet);
                            }
                            mXmppConnectionService
                                    .getJingleConnectionManager()
                                    .deliverMessage(
                                            account,
                                            packet.getTo(),
                                            packet.getFrom(),
                                            child,
                                            remoteMsgId,
                                            serverMsgId,
                                            timestamp);
                            final Contact contact = account.getRoster().getContact(from);
                            // this is the same condition that is found in JingleRtpConnection for
                            // the 'ringing' response. Responding with delivery receipts predates
                            // the 'ringing' spec'd
                            final boolean sendReceipts =
                                    contact.showInContactList()
                                            || Config.JINGLE_MESSAGE_INIT_STRICT_OFFLINE_CHECK;
                            if (remoteMsgId != null && !contact.isSelf() && sendReceipts) {
                                processMessageReceipts(account, packet, remoteMsgId, null);
                            }
                        } else if ((query != null && query.isCatchup())
                                || !offlineMessagesRetrieved) {
                            if ("propose".equals(action)) {
                                final Element description = child.findChild("description");
                                final String namespace =
                                        description == null ? null : description.getNamespace();
                                if (Namespace.JINGLE_APPS_RTP.equals(namespace)) {
                                    final Conversation c =
                                            mXmppConnectionService.findOrCreateConversation(
                                                    account, counterpart.asBareJid(), false, false);
                                    final Message preExistingMessage =
                                            c.findRtpSession(sessionId, status);
                                    if (preExistingMessage != null) {
                                        preExistingMessage.setServerMsgId(serverMsgId);
                                        mXmppConnectionService.updateMessage(preExistingMessage);
                                        break;
                                    }
                                    final Message message =
                                            new Message(
                                                    c, status, Message.TYPE_RTP_SESSION, sessionId);
                                    message.setServerMsgId(serverMsgId);
                                    message.setTime(timestamp);
                                    message.setBody(new RtpSessionStatus(false, 0).toString());
                                    c.add(message);
                                    mXmppConnectionService.databaseBackend.createMessage(message);
                                }
                            } else if ("proceed".equals(action)) {
                                // status needs to be flipped to find the original propose
                                final Conversation c =
                                        mXmppConnectionService.findOrCreateConversation(
                                                account, counterpart.asBareJid(), false, false);
                                final int s =
                                        packet.fromAccount(account)
                                                ? Message.STATUS_RECEIVED
                                                : Message.STATUS_SEND;
                                final Message message = c.findRtpSession(sessionId, s);
                                if (message != null) {
                                    message.setBody(new RtpSessionStatus(true, 0).toString());
                                    if (serverMsgId != null) {
                                        message.setServerMsgId(serverMsgId);
                                    }
                                    message.setTime(timestamp);
                                    mXmppConnectionService.updateMessage(message, true);
                                } else {
                                    Log.d(
                                            Config.LOGTAG,
                                            "unable to find original rtp session message for"
                                                    + " received propose");
                                }

                            } else if ("finish".equals(action)) {
                                Log.d(
                                        Config.LOGTAG,
                                        "received JMI 'finish' during MAM catch-up. Can be used to"
                                                + " update success/failure and duration");
                            }
                        } else {
                            // MAM reloads (non catchups
                            if ("propose".equals(action)) {
                                final Element description = child.findChild("description");
                                final String namespace =
                                        description == null ? null : description.getNamespace();
                                if (Namespace.JINGLE_APPS_RTP.equals(namespace)) {
                                    final Conversation c =
                                            mXmppConnectionService.findOrCreateConversation(
                                                    account, counterpart.asBareJid(), false, false);
                                    final Message preExistingMessage =
                                            c.findRtpSession(sessionId, status);
                                    if (preExistingMessage != null) {
                                        preExistingMessage.setServerMsgId(serverMsgId);
                                        mXmppConnectionService.updateMessage(preExistingMessage);
                                        break;
                                    }
                                    final Message message =
                                            new Message(
                                                    c, status, Message.TYPE_RTP_SESSION, sessionId);
                                    message.setServerMsgId(serverMsgId);
                                    message.setTime(timestamp);
                                    message.setBody(new RtpSessionStatus(true, 0).toString());
                                    if (query.getPagingOrder()
                                            == MessageArchiveManager.PagingOrder.REVERSE) {
                                        c.prepend(query.getActualInThisQuery(), message);
                                    } else {
                                        c.add(message);
                                    }
                                    query.incrementActualMessageCount();
                                    mXmppConnectionService.databaseBackend.createMessage(message);
                                }
                            }
                        }
                        break;
                    }
                }
            }

            final var received =
                    packet.getExtension(
                            im.conversations.android.xmpp.model.receipts.Received.class);
            if (received != null) {
                processReceived(received, packet, query, from);
            }
            final var displayed = packet.getExtension(Displayed.class);
            if (displayed != null) {
                processDisplayed(
                        displayed,
                        packet,
                        selfAddressed,
                        counterpart,
                        query,
                        isTypeGroupChat,
                        conversation,
                        from);
            }
            final Reactions reactions = packet.getExtension(Reactions.class);
            if (reactions != null) {
                final var user = getManager(MultiUserChatManager.class).getMucUser(packet, query);
                processReactions(
                        reactions, conversation, isTypeGroupChat, counterpart, user, packet);
            }

            if (original.hasExtension(Retract.class)
                    && originalFrom != null
                    && originalFrom.isBareJid()) {
                getManager(ModerationManager.class).handleRetraction(original);
            }

            // end no body
        }

        if (original.hasExtension(Event.class)) {
            getManager(PubSubManager.class).handleEvent(original);
        }

        final var nick = packet.getExtension(Nick.class);
        if (nick != null && Jid.Invalid.isValid(from)) {
            if (getManager(MultiUserChatManager.class).isMuc(from)) {
                return;
            }
            final Contact contact = account.getRoster().getContact(from);
            if (contact.setPresenceName(nick.getContent())) {
                connection.getManager(RosterManager.class).writeToDatabaseAsync();
                mXmppConnectionService.getAvatarService().clear(contact);
            }
        }
    }

    private void processReceived(
            final im.conversations.android.xmpp.model.receipts.Received received,
            final im.conversations.android.xmpp.model.stanza.Message packet,
            final MessageArchiveManager.Query query,
            final Jid from) {
        final var account = this.getAccount();
        final var id = received.getId();
        if (packet.fromAccount(account)) {
            if (query != null && id != null && packet.getTo() != null) {
                query.removePendingReceiptRequest(new ReceiptRequest(packet.getTo(), id));
            }
        } else if (id != null) {
            if (id.startsWith(JingleRtpConnection.JINGLE_MESSAGE_PROPOSE_ID_PREFIX)) {
                final String sessionId =
                        id.substring(JingleRtpConnection.JINGLE_MESSAGE_PROPOSE_ID_PREFIX.length());
                mXmppConnectionService
                        .getJingleConnectionManager()
                        .updateProposedSessionDiscovered(
                                account,
                                from,
                                sessionId,
                                JingleConnectionManager.DeviceDiscoveryState.DISCOVERED);
            } else {
                mXmppConnectionService.markMessage(
                        account, from.asBareJid(), id, Message.STATUS_SEND_RECEIVED);
            }
        }
    }

    private void processDisplayed(
            final Displayed displayed,
            final im.conversations.android.xmpp.model.stanza.Message packet,
            final boolean selfAddressed,
            final Jid counterpart,
            final MessageArchiveManager.Query query,
            final boolean isTypeGroupChat,
            final Conversation conversation,
            final Jid from) {
        final var account = getAccount();
        final var id = displayed.getId();
        // TODO we don’t even use 'sender' any more. Remove this!
        final Jid sender = Jid.Invalid.getNullForInvalid(displayed.getAttributeAsJid("sender"));
        if (packet.fromAccount(account) && !selfAddressed) {
            final Conversation c = mXmppConnectionService.find(account, counterpart.asBareJid());
            final Message message =
                    (c == null || id == null) ? null : c.findReceivedWithRemoteId(id);
            if (message != null && (query == null || query.isCatchup())) {
                mXmppConnectionService.markReadUpTo(c, message);
            }
            if (query == null) {
                activateGracePeriod(account);
            }
        } else if (isTypeGroupChat) {
            final Message message;
            if (conversation != null && id != null) {
                if (sender != null) {
                    message = conversation.findMessageWithRemoteId(id, sender);
                } else {
                    message = conversation.findMessageWithServerMsgId(id);
                }
            } else {
                message = null;
            }
            if (message != null) {
                final var user = getManager(MultiUserChatManager.class).getMucUser(packet, query);
                if (user != null && user.getMucOptions().isOurAccount(user)) {
                    if (!message.isRead()
                            && (query == null || query.isCatchup())) { // checking if message is
                        // unread fixes race conditions
                        // with reflections
                        mXmppConnectionService.markReadUpTo(conversation, message);
                    }
                } else if (!counterpart.isBareJid() && user != null && user.getRealJid() != null) {
                    final ReadByMarker readByMarker = ReadByMarker.from(user);
                    if (message.addReadByMarker(readByMarker)) {
                        final var mucOptions =
                                getManager(MultiUserChatManager.class)
                                        .getOrCreateState(conversation);
                        final var everyone = mucOptions.getMembers();
                        final var readyBy = message.getReadyByTrue();
                        final var mStatus = message.getStatus();
                        if (mucOptions.isPrivateAndNonAnonymous()
                                && (mStatus == Message.STATUS_SEND_RECEIVED
                                        || mStatus == Message.STATUS_SEND)
                                && readyBy.containsAll(everyone)) {
                            message.setStatus(Message.STATUS_SEND_DISPLAYED);
                        }
                        mXmppConnectionService.updateMessage(message, false);
                    }
                }
            }
        } else {
            final Message displayedMessage =
                    mXmppConnectionService.markMessage(
                            account, from.asBareJid(), id, Message.STATUS_SEND_DISPLAYED);
            Message message = displayedMessage == null ? null : displayedMessage.prev();
            while (message != null
                    && message.getStatus() == Message.STATUS_SEND_RECEIVED
                    && message.getTimeSent() < displayedMessage.getTimeSent()) {
                mXmppConnectionService.markMessage(message, Message.STATUS_SEND_DISPLAYED);
                message = message.prev();
            }
            if (displayedMessage != null && selfAddressed) {
                dismissNotification(account, counterpart, query, id);
            }
        }
    }

    private void processReactions(
            final Reactions reactions,
            final Conversation conversation,
            final boolean isTypeGroupChat,
            final Jid counterpart,
            final MucOptions.User user,
            final im.conversations.android.xmpp.model.stanza.Message packet) {
        final var account = getAccount();
        final String reactingTo = reactions.getId();
        if (conversation == null || reactingTo == null) {
            return;
        }
        if (isTypeGroupChat && conversation.getMode() == Conversational.MODE_MULTI) {
            final var mucOptions =
                    getManager(MultiUserChatManager.class).getOrCreateState(conversation);
            final var occupant =
                    mucOptions.occupantId() ? packet.getOnlyExtension(OccupantId.class) : null;
            final var occupantId = occupant == null ? null : occupant.getId();
            if (occupantId != null) {
                final boolean isReceived = user == null || !mucOptions.isOurAccount(user);
                final Message message;
                final var inMemoryMessage = conversation.findMessageWithServerMsgId(reactingTo);
                if (inMemoryMessage != null) {
                    message = inMemoryMessage;
                } else {
                    message =
                            mXmppConnectionService.databaseBackend.getMessageWithServerMsgId(
                                    conversation, reactingTo);
                }
                if (message != null) {
                    final var combinedReactions =
                            Reaction.withOccupantId(
                                    message.getReactions(),
                                    reactions.getReactions(),
                                    isReceived,
                                    counterpart,
                                    user == null ? null : user.getRealJid(),
                                    occupantId);
                    message.setReactions(combinedReactions);
                    mXmppConnectionService.updateMessage(message, false);
                } else {
                    Log.d(Config.LOGTAG, "message with id " + reactingTo + " not found");
                }
            } else {
                Log.d(Config.LOGTAG, "received reaction in channel w/o occupant ids. ignoring");
            }
        } else {
            final Message message;
            final var inMemoryMessage = conversation.findMessageWithUuidOrRemoteId(reactingTo);
            if (inMemoryMessage != null) {
                message = inMemoryMessage;
            } else {
                message =
                        mXmppConnectionService.databaseBackend.getMessageWithUuidOrRemoteId(
                                conversation, reactingTo);
            }
            if (message == null) {
                Log.d(Config.LOGTAG, "message with id " + reactingTo + " not found");
                return;
            }
            final boolean isReceived;
            final Jid reactionFrom;
            if (conversation.getMode() == Conversational.MODE_MULTI) {
                Log.d(Config.LOGTAG, "received reaction as MUC PM. triggering validation");
                final var mucOptions =
                        getManager(MultiUserChatManager.class).getOrCreateState(conversation);
                final var occupant =
                        mucOptions.occupantId() ? packet.getOnlyExtension(OccupantId.class) : null;
                final var occupantId = occupant == null ? null : occupant.getId();
                if (occupantId == null) {
                    Log.d(
                            Config.LOGTAG,
                            "received reaction via PM channel w/o occupant ids. ignoring");
                    return;
                }
                isReceived = user == null || !mucOptions.isOurAccount(user);
                if (isReceived) {
                    reactionFrom = counterpart;
                } else {
                    if (!occupantId.equals(message.getOccupantId())) {
                        Log.d(
                                Config.LOGTAG,
                                "reaction received via MUC PM did not pass validation");
                        return;
                    }
                    reactionFrom = account.getJid().asBareJid();
                }
            } else {
                if (packet.fromAccount(account)) {
                    isReceived = false;
                    reactionFrom = account.getJid().asBareJid();
                } else {
                    isReceived = true;
                    reactionFrom = counterpart;
                }
            }
            final var combinedReactions =
                    Reaction.withFrom(
                            message.getReactions(),
                            reactions.getReactions(),
                            isReceived,
                            reactionFrom);
            message.setReactions(combinedReactions);
            mXmppConnectionService.updateMessage(message, false);
        }
    }

    private static Pair<im.conversations.android.xmpp.model.stanza.Message, Long>
            getForwardedMessagePacket(
                    final im.conversations.android.xmpp.model.stanza.Message original,
                    Class<? extends Extension> clazz) {
        final var extension = original.getExtension(clazz);
        final var forwarded = extension == null ? null : extension.getExtension(Forwarded.class);
        if (forwarded == null) {
            return null;
        }
        final Long timestamp = AbstractParser.parseTimestamp(forwarded, null);
        final var forwardedMessage = forwarded.getMessage();
        if (forwardedMessage == null) {
            return null;
        }
        return new Pair<>(forwardedMessage, timestamp);
    }

    private static Pair<im.conversations.android.xmpp.model.stanza.Message, Long>
            getForwardedMessagePacket(
                    final im.conversations.android.xmpp.model.stanza.Message original,
                    final String name,
                    final String namespace) {
        final Element wrapper = original.findChild(name, namespace);
        final var forwardedElement =
                wrapper == null ? null : wrapper.findChild("forwarded", Namespace.FORWARD);
        if (forwardedElement instanceof Forwarded forwarded) {
            final Long timestamp = AbstractParser.parseTimestamp(forwarded, null);
            final var forwardedMessage = forwarded.getMessage();
            if (forwardedMessage == null) {
                return null;
            }
            return new Pair<>(forwardedMessage, timestamp);
        }
        return null;
    }

    private void dismissNotification(
            Account account, Jid counterpart, MessageArchiveManager.Query query, final String id) {
        final Conversation conversation =
                mXmppConnectionService.find(account, counterpart.asBareJid());
        if (conversation != null && (query == null || query.isCatchup())) {
            final String displayableId = conversation.findMostRecentRemoteDisplayableId();
            if (displayableId != null && displayableId.equals(id)) {
                mXmppConnectionService.markRead(conversation);
            } else {
                Log.w(
                        Config.LOGTAG,
                        account.getJid().asBareJid()
                                + ": received dismissing display marker that did not match our last"
                                + " id in that conversation");
            }
        }
    }

    private void processMessageReceipts(
            final Account account,
            final im.conversations.android.xmpp.model.stanza.Message packet,
            final String remoteMsgId,
            final MessageArchiveManager.Query query) {
        final var request = packet.hasExtension(Request.class);
        if (query == null) {
            if (request) {
                final var receipt =
                        mXmppConnectionService
                                .getMessageGenerator()
                                .received(packet.getFrom(), remoteMsgId, packet.getType());
                mXmppConnectionService.sendMessagePacket(account, receipt);
            }
        } else if (query.isCatchup()) {
            if (request) {
                query.addPendingReceiptRequest(new ReceiptRequest(packet.getFrom(), remoteMsgId));
            }
        }
    }

    private void activateGracePeriod(Account account) {
        long duration =
                mXmppConnectionService.getLongPreference(
                                "grace_period_length", R.integer.grace_period)
                        * 1000;
        Log.d(
                Config.LOGTAG,
                account.getJid().asBareJid()
                        + ": activating grace period till "
                        + TIME_FORMAT.format(new Date(System.currentTimeMillis() + duration)));
        account.activateGracePeriod(duration);
    }

    private class Invite {
        final Jid jid;
        final String password;
        final boolean direct;
        final Jid inviter;

        Invite(Jid jid, String password, boolean direct, Jid inviter) {
            this.jid = jid;
            this.password = password;
            this.direct = direct;
            this.inviter = inviter;
        }

        public boolean execute(final Account account) {
            if (this.jid == null) {
                return false;
            }
            final Contact contact =
                    this.inviter != null ? account.getRoster().getContact(this.inviter) : null;
            if (contact != null && contact.isBlocked()) {
                Log.d(
                        Config.LOGTAG,
                        account.getJid().asBareJid()
                                + ": ignore invite from "
                                + contact.getAddress()
                                + " because contact is blocked");
                return false;
            }
            final AppSettings appSettings = new AppSettings(mXmppConnectionService);
            if ((contact != null && contact.showInContactList())
                    || appSettings.isAcceptInvitesFromStrangers()) {
                final Conversation conversation =
                        mXmppConnectionService.findOrCreateConversation(account, jid, true, false);
                if (conversation.getMucOptions().online()) {
                    Log.d(
                            Config.LOGTAG,
                            account.getJid().asBareJid()
                                    + ": received invite to "
                                    + jid
                                    + " but muc is considered to be online");
                    getManager(MultiUserChatManager.class).pingAndRejoin(conversation);
                } else {
                    conversation.getMucOptions().setPassword(password);
                    mXmppConnectionService.databaseBackend.updateConversation(conversation);
                    if (contact != null && contact.showInContactList()) {
                        getManager(MultiUserChatManager.class).joinFollowingInvite(conversation);
                    } else {
                        getManager(MultiUserChatManager.class).join(conversation);
                    }
                    mXmppConnectionService.updateConversationUi();
                }
                return true;
            } else {
                Log.d(
                        Config.LOGTAG,
                        account.getJid().asBareJid()
                                + ": ignoring invite from "
                                + this.inviter
                                + " because we are not accepting invites from strangers. direct="
                                + direct);
                return false;
            }
        }
    }
}
