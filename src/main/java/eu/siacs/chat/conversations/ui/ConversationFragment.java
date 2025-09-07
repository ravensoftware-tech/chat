package tech.ravensoftware.chat.ui;

import static tech.ravensoftware.chat.ui.XmppActivity.EXTRA_ACCOUNT;
import static tech.ravensoftware.chat.ui.XmppActivity.REQUEST_INVITE_TO_CONVERSATION;
import static tech.ravensoftware.chat.ui.util.SoftKeyboardUtils.hideSoftKeyboard;
import static tech.ravensoftware.chat.utils.PermissionUtils.allGranted;
import static tech.ravensoftware.chat.utils.PermissionUtils.audioGranted;
import static tech.ravensoftware.chat.utils.PermissionUtils.cameraGranted;
import static tech.ravensoftware.chat.utils.PermissionUtils.getFirstDenied;
import static tech.ravensoftware.chat.utils.PermissionUtils.writeGranted;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.MenuProvider;
import androidx.core.view.inputmethod.InputConnectionCompat;
import androidx.core.view.inputmethod.InputContentInfoCompat;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import de.gultsch.common.Linkify;
import de.gultsch.common.Patterns;
import tech.ravensoftware.chat.Config;
import tech.ravensoftware.chat.R;
import tech.ravensoftware.chat.crypto.PgpEngine;
import tech.ravensoftware.chat.crypto.axolotl.AxolotlService;
import tech.ravensoftware.chat.crypto.axolotl.FingerprintStatus;
import tech.ravensoftware.chat.databinding.DialogModerationBinding;
import tech.ravensoftware.chat.databinding.FragmentConversationBinding;
import tech.ravensoftware.chat.entities.Account;
import tech.ravensoftware.chat.entities.Blockable;
import tech.ravensoftware.chat.entities.Contact;
import tech.ravensoftware.chat.entities.Conversation;
import tech.ravensoftware.chat.entities.Conversational;
import tech.ravensoftware.chat.entities.DownloadableFile;
import tech.ravensoftware.chat.entities.Message;
import tech.ravensoftware.chat.entities.MucOptions;
import tech.ravensoftware.chat.entities.ReadByMarker;
import tech.ravensoftware.chat.entities.Transferable;
import tech.ravensoftware.chat.entities.TransferablePlaceholder;
import tech.ravensoftware.chat.http.HttpDownloadConnection;
import tech.ravensoftware.chat.persistance.FileBackend;
import tech.ravensoftware.chat.services.CallIntegrationConnectionService;
import tech.ravensoftware.chat.services.QuickChatService;
import tech.ravensoftware.chat.services.XmppConnectionService;
import tech.ravensoftware.chat.ui.adapter.MediaPreviewAdapter;
import tech.ravensoftware.chat.ui.adapter.MessageAdapter;
import tech.ravensoftware.chat.ui.util.ActivityResult;
import tech.ravensoftware.chat.ui.util.Attachment;
import tech.ravensoftware.chat.ui.util.ConversationMenuConfigurator;
import tech.ravensoftware.chat.ui.util.DateSeparator;
import tech.ravensoftware.chat.ui.util.EditMessageActionModeCallback;
import tech.ravensoftware.chat.ui.util.ListViewUtils;
import tech.ravensoftware.chat.ui.util.MenuDoubleTabUtil;
import tech.ravensoftware.chat.ui.util.MucDetailsContextMenuHelper;
import tech.ravensoftware.chat.ui.util.PendingItem;
import tech.ravensoftware.chat.ui.util.PresenceSelector;
import tech.ravensoftware.chat.ui.util.ScrollState;
import tech.ravensoftware.chat.ui.util.SendButtonAction;
import tech.ravensoftware.chat.ui.util.SendButtonTool;
import tech.ravensoftware.chat.ui.util.ShareUtil;
import tech.ravensoftware.chat.ui.util.ViewUtil;
import tech.ravensoftware.chat.ui.widget.EditMessage;
import tech.ravensoftware.chat.utils.AccountUtils;
import tech.ravensoftware.chat.utils.CharSequenceUtils;
import tech.ravensoftware.chat.utils.Compatibility;
import tech.ravensoftware.chat.utils.GeoHelper;
import tech.ravensoftware.chat.utils.MessageUtils;
import tech.ravensoftware.chat.utils.NickValidityChecker;
import tech.ravensoftware.chat.utils.PermissionUtils;
import tech.ravensoftware.chat.utils.QuickLoader;
import tech.ravensoftware.chat.utils.StylingHelper;
import tech.ravensoftware.chat.utils.TimeFrameUtils;
import tech.ravensoftware.chat.utils.UIHelper;
import tech.ravensoftware.chat.xmpp.Jid;
import tech.ravensoftware.chat.xmpp.XmppConnection;
import tech.ravensoftware.chat.xmpp.chatstate.ChatState;
import tech.ravensoftware.chat.xmpp.jingle.AbstractJingleConnection;
import tech.ravensoftware.chat.xmpp.jingle.JingleConnectionManager;
import tech.ravensoftware.chat.xmpp.jingle.JingleFileTransferConnection;
import tech.ravensoftware.chat.xmpp.jingle.Media;
import tech.ravensoftware.chat.xmpp.jingle.OngoingRtpSession;
import tech.ravensoftware.chat.xmpp.jingle.RtpCapability;
import tech.ravensoftware.chat.xmpp.manager.BlockingManager;
import tech.ravensoftware.chat.xmpp.manager.HttpUploadManager;
import tech.ravensoftware.chat.xmpp.manager.MessageArchiveManager;
import tech.ravensoftware.chat.xmpp.manager.ModerationManager;
import tech.ravensoftware.chat.xmpp.manager.MultiUserChatManager;
import tech.ravensoftware.chat.xmpp.manager.PresenceManager;
import im.conversations.android.xmpp.model.muc.Role;
import im.conversations.android.xmpp.model.stanza.Presence;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class ConversationFragment extends XmppFragment
        implements EditMessage.KeyboardListener,
                MessageAdapter.OnContactPictureLongClicked,
                MessageAdapter.OnContactPictureClicked {

    private static Instant ackModeration = Instant.MIN;

    public static final int REQUEST_SEND_MESSAGE = 0x0201;
    public static final int REQUEST_DECRYPT_PGP = 0x0202;
    public static final int REQUEST_ENCRYPT_MESSAGE = 0x0207;
    public static final int REQUEST_TRUST_KEYS_TEXT = 0x0208;
    public static final int REQUEST_TRUST_KEYS_ATTACHMENTS = 0x0209;
    public static final int REQUEST_START_DOWNLOAD = 0x0210;
    public static final int REQUEST_ADD_EDITOR_CONTENT = 0x0211;
    public static final int REQUEST_COMMIT_ATTACHMENTS = 0x0212;
    public static final int REQUEST_START_AUDIO_CALL = 0x213;
    public static final int REQUEST_START_VIDEO_CALL = 0x214;
    public static final int ATTACHMENT_CHOICE_CHOOSE_IMAGE = 0x0301;
    public static final int ATTACHMENT_CHOICE_TAKE_PHOTO = 0x0302;
    public static final int ATTACHMENT_CHOICE_CHOOSE_FILE = 0x0303;
    public static final int ATTACHMENT_CHOICE_RECORD_VOICE = 0x0304;
    public static final int ATTACHMENT_CHOICE_LOCATION = 0x0305;
    public static final int ATTACHMENT_CHOICE_INVALID = 0x0306;
    public static final int ATTACHMENT_CHOICE_RECORD_VIDEO = 0x0307;

    public static final String RECENTLY_USED_QUICK_ACTION = "recently_used_quick_action";
    public static final String STATE_CONVERSATION_UUID =
            ConversationFragment.class.getName() + ".uuid";
    public static final String STATE_SCROLL_POSITION =
            ConversationFragment.class.getName() + ".scroll_position";
    public static final String STATE_PHOTO_URI =
            ConversationFragment.class.getName() + ".media_previews";
    public static final String STATE_MEDIA_PREVIEWS =
            ConversationFragment.class.getName() + ".take_photo_uri";
    private static final String STATE_LAST_MESSAGE_UUID = "state_last_message_uuid";

    private final List<Message> messageList = new ArrayList<>();
    private final PendingItem<ActivityResult> postponedActivityResult = new PendingItem<>();
    private final PendingItem<String> pendingChatUuid = new PendingItem<>();
    private final PendingItem<ArrayList<Attachment>> pendingMediaPreviews = new PendingItem<>();
    private final PendingItem<Bundle> pendingExtras = new PendingItem<>();
    private final PendingItem<Uri> pendingTakePhotoUri = new PendingItem<>();
    private final PendingItem<ScrollState> pendingScrollState = new PendingItem<>();
    private final PendingItem<String> pendingLastMessageUuid = new PendingItem<>();
    private final PendingItem<Message> pendingMessage = new PendingItem<>();
    private final MediaPreviewAdapter mediaPreviewAdapter = new MediaPreviewAdapter(this);
    public Uri mPendingEditorContent = null;
    protected MessageAdapter messageListAdapter;
    private String lastMessageUuid = null;
    private Conversation conversation;
    private FragmentConversationBinding binding;
    private Toast messageLoaderToast;
    private boolean reInitRequiredOnStart = true;
    private final OnClickListener clickToMuc =
            new OnClickListener() {

                @Override
                public void onClick(View v) {
                    ConferenceDetailsActivity.open(getActivity(), conversation);
                }
            };
    private final OnClickListener leaveMuc =
            new OnClickListener() {

                @Override
                public void onClick(View v) {
                    requireXmppActivity().xmppConnectionService.archiveConversation(conversation);
                }
            };
    private final OnClickListener joinMuc =
            new OnClickListener() {

                @Override
                public void onClick(View v) {
                    requireXmppActivity().xmppConnectionService.joinMuc(conversation);
                }
            };

    private final OnClickListener acceptJoin =
            new OnClickListener() {
                @Override
                public void onClick(View v) {
                    conversation.setAttribute("accept_non_anonymous", true);
                    requireXmppActivity().xmppConnectionService.updateConversation(conversation);
                    requireXmppActivity().xmppConnectionService.joinMuc(conversation);
                }
            };

    private final OnClickListener enterPassword =
            new OnClickListener() {

                @Override
                public void onClick(View v) {
                    MucOptions muc = conversation.getMucOptions();
                    String password = muc.getPassword();
                    if (password == null) {
                        password = "";
                    }
                    requireXmppActivity()
                            .quickPasswordEdit(
                                    password,
                                    value -> {
                                        requireXmppActivity()
                                                .xmppConnectionService
                                                .providePasswordForMuc(conversation, value);
                                        return null;
                                    });
                }
            };
    private final OnScrollListener mOnScrollListener =
            new OnScrollListener() {

                @Override
                public void onScrollStateChanged(AbsListView view, int scrollState) {
                    if (AbsListView.OnScrollListener.SCROLL_STATE_IDLE == scrollState) {
                        fireReadEvent();
                    }
                }

                @Override
                public void onScroll(
                        final AbsListView view,
                        int firstVisibleItem,
                        int visibleItemCount,
                        int totalItemCount) {
                    toggleScrollDownButton(view);
                    synchronized (ConversationFragment.this.messageList) {
                        if (firstVisibleItem < 5
                                && conversation != null
                                && conversation.messagesLoaded.compareAndSet(true, false)
                                && messageList.size() > 0) {
                            long timestamp;
                            if (messageList.get(0).getType() == Message.TYPE_STATUS
                                    && messageList.size() >= 2) {
                                timestamp = messageList.get(1).getTimeSent();
                            } else {
                                timestamp = messageList.get(0).getTimeSent();
                            }
                            requireXmppActivity()
                                    .xmppConnectionService
                                    .loadMoreMessages(
                                            conversation,
                                            timestamp,
                                            new XmppConnectionService.OnMoreMessagesLoaded() {
                                                @Override
                                                public void onMoreMessagesLoaded(
                                                        final int c,
                                                        final Conversation conversation) {
                                                    if (ConversationFragment.this.conversation
                                                            != conversation) {
                                                        conversation.messagesLoaded.set(true);
                                                        return;
                                                    }
                                                    runOnUiThread(
                                                            () -> {
                                                                synchronized (messageList) {
                                                                    final int oldPosition =
                                                                            binding.messagesView
                                                                                    .getFirstVisiblePosition();
                                                                    Message message = null;
                                                                    int childPos;
                                                                    for (childPos = 0;
                                                                            childPos + oldPosition
                                                                                    < messageList
                                                                                            .size();
                                                                            ++childPos) {
                                                                        message =
                                                                                messageList.get(
                                                                                        oldPosition
                                                                                                + childPos);
                                                                        if (message.getType()
                                                                                != Message
                                                                                        .TYPE_STATUS) {
                                                                            break;
                                                                        }
                                                                    }
                                                                    final String uuid =
                                                                            message != null
                                                                                    ? message
                                                                                            .getUuid()
                                                                                    : null;
                                                                    View v =
                                                                            binding.messagesView
                                                                                    .getChildAt(
                                                                                            childPos);
                                                                    final int pxOffset =
                                                                            (v == null)
                                                                                    ? 0
                                                                                    : v.getTop();
                                                                    ConversationFragment.this
                                                                            .conversation
                                                                            .populateWithMessages(
                                                                                    ConversationFragment
                                                                                            .this
                                                                                            .messageList);
                                                                    try {
                                                                        updateStatusMessages();
                                                                    } catch (
                                                                            IllegalStateException
                                                                                    e) {
                                                                        Log.d(
                                                                                Config.LOGTAG,
                                                                                "caught illegal"
                                                                                    + " state"
                                                                                    + " exception"
                                                                                    + " while"
                                                                                    + " updating"
                                                                                    + " status"
                                                                                    + " messages");
                                                                    }
                                                                    messageListAdapter
                                                                            .notifyDataSetChanged();
                                                                    int pos =
                                                                            Math.max(
                                                                                    getIndexOf(
                                                                                            uuid,
                                                                                            messageList),
                                                                                    0);
                                                                    binding.messagesView
                                                                            .setSelectionFromTop(
                                                                                    pos, pxOffset);
                                                                    if (messageLoaderToast
                                                                            != null) {
                                                                        messageLoaderToast.cancel();
                                                                    }
                                                                    conversation.messagesLoaded.set(
                                                                            true);
                                                                }
                                                            });
                                                }

                                                @Override
                                                public void informUser(final int resId) {

                                                    runOnUiThread(
                                                            () -> {
                                                                if (messageLoaderToast != null) {
                                                                    messageLoaderToast.cancel();
                                                                }
                                                                if (ConversationFragment.this
                                                                                .conversation
                                                                        != conversation) {
                                                                    return;
                                                                }
                                                                messageLoaderToast =
                                                                        Toast.makeText(
                                                                                view.getContext(),
                                                                                resId,
                                                                                Toast.LENGTH_LONG);
                                                                messageLoaderToast.show();
                                                            });
                                                }
                                            });
                        }
                    }
                }
            };
    private final EditMessage.OnCommitContentListener mEditorContentListener =
            new EditMessage.OnCommitContentListener() {
                @Override
                public boolean onCommitContent(
                        InputContentInfoCompat inputContentInfo,
                        int flags,
                        Bundle opts,
                        String[] contentMimeTypes) {
                    // try to get permission to read the image, if applicable
                    if ((flags & InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION)
                            != 0) {
                        try {
                            inputContentInfo.requestPermission();
                        } catch (Exception e) {
                            Log.e(
                                    Config.LOGTAG,
                                    "InputContentInfoCompat#requestPermission() failed.",
                                    e);
                            Toast.makeText(
                                            requireContext(),
                                            requireContext()
                                                    .getString(
                                                            R.string.no_permission_to_access_x,
                                                            inputContentInfo.getDescription()),
                                            Toast.LENGTH_LONG)
                                    .show();
                            return false;
                        }
                    }
                    if (hasPermissions(
                            REQUEST_ADD_EDITOR_CONTENT,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                        attachEditorContentToConversation(inputContentInfo.getContentUri());
                    } else {
                        mPendingEditorContent = inputContentInfo.getContentUri();
                    }
                    return true;
                }
            };
    private Message selectedMessage;
    private final OnClickListener mEnableAccountListener =
            new OnClickListener() {
                @Override
                public void onClick(View v) {
                    final Account account = conversation == null ? null : conversation.getAccount();
                    if (account != null) {
                        account.setOption(Account.OPTION_SOFT_DISABLED, false);
                        account.setOption(Account.OPTION_DISABLED, false);
                        requireXmppActivity().xmppConnectionService.updateAccount(account);
                    }
                }
            };
    private final OnClickListener mUnblockClickListener =
            new OnClickListener() {
                @Override
                public void onClick(final View v) {
                    v.post(() -> v.setVisibility(View.INVISIBLE));
                    if (conversation.isDomainBlocked()) {
                        BlockContactDialog.show(requireXmppActivity(), conversation);
                    } else {
                        unblockConversation(conversation);
                    }
                }
            };
    private final OnClickListener mBlockClickListener = this::showBlockSubmenu;
    private final OnClickListener mAddBackClickListener =
            new OnClickListener() {

                @Override
                public void onClick(View v) {
                    final Contact contact = conversation == null ? null : conversation.getContact();
                    if (contact != null) {
                        requireXmppActivity().xmppConnectionService.createContact(contact);
                        requireXmppActivity().switchToContactDetails(contact);
                    }
                }
            };
    private final View.OnLongClickListener mLongPressBlockListener = this::showBlockSubmenu;
    private final OnClickListener mAllowPresenceSubscription =
            new OnClickListener() {
                @Override
                public void onClick(View v) {
                    final Contact contact = conversation == null ? null : conversation.getContact();
                    if (contact != null) {
                        final var connection = contact.getAccount().getXmppConnection();
                        connection
                                .getManager(PresenceManager.class)
                                .subscribed(contact.getAddress().asBareJid());
                        hideSnackbar();
                    }
                }
            };
    protected OnClickListener clickToDecryptListener =
            new OnClickListener() {

                @Override
                public void onClick(View v) {
                    PendingIntent pendingIntent =
                            conversation.getAccount().getPgpDecryptionService().getPendingIntent();
                    if (pendingIntent != null) {
                        try {
                            getActivity()
                                    .startIntentSenderForResult(
                                            pendingIntent.getIntentSender(),
                                            REQUEST_DECRYPT_PGP,
                                            null,
                                            0,
                                            0,
                                            0,
                                            Compatibility.pgpStartIntentSenderOptions());
                        } catch (SendIntentException e) {
                            Toast.makeText(
                                            getActivity(),
                                            R.string.unable_to_connect_to_keychain,
                                            Toast.LENGTH_SHORT)
                                    .show();
                            conversation
                                    .getAccount()
                                    .getPgpDecryptionService()
                                    .continueDecryption(true);
                        }
                    }
                    updateSnackBar(conversation);
                }
            };
    private final AtomicBoolean mSendingPgpMessage = new AtomicBoolean(false);
    private final OnEditorActionListener mEditorActionListener =
            (v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    InputMethodManager imm =
                            (InputMethodManager)
                                    requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null && imm.isFullscreenMode()) {
                        imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                    }
                    sendMessage();
                    return true;
                } else {
                    return false;
                }
            };
    private final OnClickListener mScrollButtonListener =
            new OnClickListener() {

                @Override
                public void onClick(View v) {
                    stopScrolling();
                    setSelection(binding.messagesView.getCount() - 1, true);
                }
            };
    private final OnClickListener mSendButtonListener =
            new OnClickListener() {

                @Override
                public void onClick(View v) {
                    Object tag = v.getTag();
                    if (tag instanceof SendButtonAction action) {
                        switch (action) {
                            case TAKE_PHOTO:
                            case RECORD_VIDEO:
                            case SEND_LOCATION:
                            case RECORD_VOICE:
                            case CHOOSE_PICTURE:
                                attachFile(action.toChoice());
                                break;
                            case CANCEL:
                                if (conversation != null) {
                                    if (conversation.setCorrectingMessage(null)) {
                                        binding.textinput.setText("");
                                        binding.textinput.append(conversation.getDraftMessage());
                                        conversation.setDraftMessage(null);
                                    } else if (conversation.getMode() == Conversation.MODE_MULTI) {
                                        conversation.setNextCounterpart(null);
                                        binding.textinput.setText("");
                                    } else {
                                        binding.textinput.setText("");
                                    }
                                    updateChatMsgHint();
                                    updateSendButton();
                                    updateEditablity();
                                }
                                break;
                            default:
                                sendMessage();
                        }
                    } else {
                        sendMessage();
                    }
                }
            };
    private final MenuProvider menuProvider =
            new MenuProvider() {
                @Override
                public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                    menuInflater.inflate(R.menu.fragment_conversation, menu);
                    final MenuItem menuMucDetails = menu.findItem(R.id.action_muc_details);
                    final MenuItem menuContactDetails = menu.findItem(R.id.action_contact_details);
                    final MenuItem menuInviteContact = menu.findItem(R.id.action_invite);
                    final MenuItem menuMute = menu.findItem(R.id.action_mute);
                    final MenuItem menuUnmute = menu.findItem(R.id.action_unmute);
                    final MenuItem menuCall = menu.findItem(R.id.action_call);
                    final MenuItem menuOngoingCall = menu.findItem(R.id.action_ongoing_call);
                    final MenuItem menuVideoCall = menu.findItem(R.id.action_video_call);
                    final MenuItem menuTogglePinned = menu.findItem(R.id.action_toggle_pinned);
                    final var c = ConversationFragment.this.conversation;
                    if (c == null) {
                        return;
                    }
                    if (c.getMode() == Conversation.MODE_MULTI) {
                        menuContactDetails.setVisible(false);
                        menuInviteContact.setVisible(c.getMucOptions().canInvite());
                        menuMucDetails.setTitle(
                                c.getMucOptions().isPrivateAndNonAnonymous()
                                        ? R.string.action_muc_details
                                        : R.string.channel_details);
                        menuCall.setVisible(false);
                        menuOngoingCall.setVisible(false);
                    } else {
                        final XmppConnectionService service = getXmppConnectionService();
                        final Optional<OngoingRtpSession> ongoingRtpSession =
                                service == null
                                        ? Optional.absent()
                                        : service.getJingleConnectionManager()
                                                .getOngoingRtpConnection(c.getContact());
                        if (ongoingRtpSession.isPresent()) {
                            menuOngoingCall.setVisible(true);
                            menuCall.setVisible(false);
                        } else {
                            menuOngoingCall.setVisible(false);
                            // use RtpCapability.check(conversation.getContact()); to check if
                            // contact
                            // actually has support
                            final boolean cameraAvailable =
                                    requireXmppActivity().isCameraFeatureAvailable();
                            menuCall.setVisible(true);
                            menuVideoCall.setVisible(cameraAvailable);
                        }
                        menuContactDetails.setVisible(!c.withSelf());
                        menuMucDetails.setVisible(false);
                        final var connection = c.getAccount().getXmppConnection();
                        menuInviteContact.setVisible(
                                !connection
                                        .getManager(MultiUserChatManager.class)
                                        .getServices()
                                        .isEmpty());
                    }
                    if (c.isMuted()) {
                        menuMute.setVisible(false);
                    } else {
                        menuUnmute.setVisible(false);
                    }
                    ConversationMenuConfigurator.configureAttachmentMenu(c, menu);
                    ConversationMenuConfigurator.configureEncryptionMenu(c, menu);
                    if (c.getBooleanAttribute(Conversation.ATTRIBUTE_PINNED_ON_TOP, false)) {
                        menuTogglePinned.setTitle(R.string.remove_from_favorites);
                    } else {
                        menuTogglePinned.setTitle(R.string.add_to_favorites);
                    }
                }

                @Override
                public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                    if (MenuDoubleTabUtil.shouldIgnoreTap()) {
                        return false;
                    } else if (conversation == null) {
                        return false;
                    }
                    switch (menuItem.getItemId()) {
                        case R.id.encryption_choice_axolotl:
                        case R.id.encryption_choice_pgp:
                        case R.id.encryption_choice_none:
                            handleEncryptionSelection(menuItem);
                            break;
                        case R.id.attach_choose_picture:
                        case R.id.attach_take_picture:
                        case R.id.attach_record_video:
                        case R.id.attach_choose_file:
                        case R.id.attach_record_voice:
                        case R.id.attach_location:
                            handleAttachmentSelection(menuItem);
                            break;
                        case R.id.action_search:
                            startSearch();
                            break;
                        case R.id.action_archive:
                            requireXmppActivity()
                                    .xmppConnectionService
                                    .archiveConversation(conversation);
                            break;
                        case R.id.action_contact_details:
                            requireXmppActivity().switchToContactDetails(conversation.getContact());
                            break;
                        case R.id.action_muc_details:
                            ConferenceDetailsActivity.open(getActivity(), conversation);
                            break;
                        case R.id.action_invite:
                            startActivityForResult(
                                    ChooseContactActivity.create(requireActivity(), conversation),
                                    REQUEST_INVITE_TO_CONVERSATION);
                            break;
                        case R.id.action_clear_history:
                            clearHistoryDialog(conversation);
                            break;
                        case R.id.action_mute:
                            muteConversationDialog(conversation);
                            break;
                        case R.id.action_unmute:
                            unMuteConversation(conversation);
                            break;
                        case R.id.action_block:
                        case R.id.action_unblock:
                            final Activity activity = getActivity();
                            if (activity instanceof XmppActivity) {
                                BlockContactDialog.show((XmppActivity) activity, conversation);
                            }
                            break;
                        case R.id.action_audio_call:
                            checkPermissionAndTriggerAudioCall();
                            break;
                        case R.id.action_video_call:
                            checkPermissionAndTriggerVideoCall();
                            break;
                        case R.id.action_ongoing_call:
                            returnToOngoingCall();
                            break;
                        case R.id.action_toggle_pinned:
                            togglePinned();
                            break;
                        default:
                            break;
                    }
                    return false;
                }
            };
    private int completionIndex = 0;
    private int lastCompletionLength = 0;
    private String incomplete;
    private int lastCompletionCursor;
    private boolean firstWord = false;
    private Message mPendingDownloadableMessage;

    private static ConversationFragment findConversationFragment(AppCompatActivity activity) {
        final var main = activity.getSupportFragmentManager().findFragmentById(R.id.main_fragment);
        if (main instanceof ConversationFragment conversationFragment) {
            return conversationFragment;
        }
        final var secondary =
                activity.getSupportFragmentManager().findFragmentById(R.id.secondary_fragment);
        if (secondary instanceof ConversationFragment conversationFragment) {
            return conversationFragment;
        }
        return null;
    }

    public static void startStopPending(AppCompatActivity activity) {
        ConversationFragment fragment = findConversationFragment(activity);
        if (fragment != null) {
            fragment.messageListAdapter.startStopPending();
        }
    }

    public static void downloadFile(AppCompatActivity activity, Message message) {
        ConversationFragment fragment = findConversationFragment(activity);
        if (fragment != null) {
            fragment.startDownloadable(message);
        }
    }

    public static void registerPendingMessage(AppCompatActivity activity, Message message) {
        ConversationFragment fragment = findConversationFragment(activity);
        if (fragment != null) {
            fragment.pendingMessage.push(message);
        }
    }

    public static void openPendingMessage(AppCompatActivity activity) {
        ConversationFragment fragment = findConversationFragment(activity);
        if (fragment != null) {
            Message message = fragment.pendingMessage.pop();
            if (message != null) {
                fragment.messageListAdapter.openDownloadable(message);
            }
        }
    }

    public static Conversation getConversation(FragmentActivity activity) {
        return getConversation(activity, R.id.secondary_fragment);
    }

    private static Conversation getConversation(FragmentActivity activity, @IdRes int res) {
        final Fragment fragment = activity.getSupportFragmentManager().findFragmentById(res);
        if (fragment instanceof ConversationFragment conversationFragment) {
            return conversationFragment.getConversation();
        } else {
            return null;
        }
    }

    public static ConversationFragment get(AppCompatActivity activity) {
        final var fragmentManager = activity.getSupportFragmentManager();
        final var main = fragmentManager.findFragmentById(R.id.main_fragment);
        if (main instanceof ConversationFragment conversationFragment) {
            return conversationFragment;
        }
        final var secondary = fragmentManager.findFragmentById(R.id.secondary_fragment);
        if (secondary instanceof ConversationFragment conversationFragment) {
            return conversationFragment;
        }
        return null;
    }

    public static Conversation getConversationReliable(AppCompatActivity activity) {
        final Conversation conversation = getConversation(activity, R.id.secondary_fragment);
        if (conversation != null) {
            return conversation;
        }
        return getConversation(activity, R.id.main_fragment);
    }

    private static boolean scrolledToBottom(AbsListView listView) {
        final int count = listView.getCount();
        if (count == 0) {
            return true;
        } else if (listView.getLastVisiblePosition() == count - 1) {
            final View lastChild = listView.getChildAt(listView.getChildCount() - 1);
            return lastChild != null && lastChild.getBottom() <= listView.getHeight();
        } else {
            return false;
        }
    }

    private void toggleScrollDownButton() {
        toggleScrollDownButton(binding.messagesView);
    }

    private void toggleScrollDownButton(AbsListView listView) {
        if (conversation == null) {
            return;
        }
        if (scrolledToBottom(listView)) {
            lastMessageUuid = null;
            hideUnreadMessagesCount();
        } else {
            binding.scrollToBottomButton.setEnabled(true);
            binding.scrollToBottomButton.show();
            if (lastMessageUuid == null) {
                lastMessageUuid = conversation.getLatestMessage().getUuid();
            }
            if (conversation.getReceivedMessagesCountSinceUuid(lastMessageUuid) > 0) {
                binding.unreadCountCustomView.setVisibility(View.VISIBLE);
            }
        }
    }

    private int getIndexOf(String uuid, List<Message> messages) {
        if (uuid == null) {
            return messages.size() - 1;
        }
        for (int i = 0; i < messages.size(); ++i) {
            if (uuid.equals(messages.get(i).getUuid())) {
                return i;
            }
        }
        return -1;
    }

    private ScrollState getScrollPosition() {
        final ListView listView = this.binding == null ? null : this.binding.messagesView;
        if (listView == null
                || listView.getCount() == 0
                || listView.getLastVisiblePosition() == listView.getCount() - 1) {
            return null;
        } else {
            final int pos = listView.getFirstVisiblePosition();
            final View view = listView.getChildAt(0);
            if (view == null) {
                return null;
            } else {
                return new ScrollState(pos, view.getTop());
            }
        }
    }

    private void setScrollPosition(ScrollState scrollPosition, String lastMessageUuid) {
        if (scrollPosition != null) {

            this.lastMessageUuid = lastMessageUuid;
            if (lastMessageUuid != null) {
                binding.unreadCountCustomView.setUnreadCount(
                        conversation.getReceivedMessagesCountSinceUuid(lastMessageUuid));
            }
            // TODO maybe this needs a 'post'
            this.binding.messagesView.setSelectionFromTop(
                    scrollPosition.position, scrollPosition.offset);
            toggleScrollDownButton();
        }
    }

    private void attachLocationToConversation(final Conversation conversation, final Uri uri) {
        if (conversation == null) {
            return;
        }
        final var future =
                requireXmppActivity()
                        .xmppConnectionService
                        .attachLocationToConversation(conversation, uri);
        // TODO add callback to potentially show PGP errors
    }

    private void attachFileToConversation(
            final Conversation conversation, final Uri uri, final String type) {
        if (conversation == null) {
            return;
        }
        final Toast prepareFileToast =
                Toast.makeText(getActivity(), getText(R.string.preparing_file), Toast.LENGTH_LONG);
        prepareFileToast.show();
        requireXmppActivity().delegateUriPermissionsToService(uri);
        final var future =
                requireXmppActivity()
                        .xmppConnectionService
                        .attachFileToConversation(conversation, uri, type);
        Futures.addCallback(
                future,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(final Void result) {
                        prepareFileToast.cancel();
                    }

                    @Override
                    public void onFailure(@NonNull final Throwable t) {
                        Log.d(Config.LOGTAG, "could not attach file", t);
                        prepareFileToast.cancel();
                        displayToastForException(t);
                    }
                },
                ContextCompat.getMainExecutor(requireContext()));
    }

    public void attachEditorContentToConversation(Uri uri) {
        mediaPreviewAdapter.addMediaPreviews(
                Attachment.of(getActivity(), uri, Attachment.Type.FILE));
        toggleInputMethod();
    }

    private void attachImageToConversation(
            final Conversation conversation, final Uri uri, final String type) {
        if (conversation == null) {
            return;
        }
        final Toast prepareFileToast =
                Toast.makeText(getActivity(), getText(R.string.preparing_image), Toast.LENGTH_LONG);
        prepareFileToast.show();
        requireXmppActivity().delegateUriPermissionsToService(uri);
        final var future =
                requireXmppActivity()
                        .xmppConnectionService
                        .attachImageToConversation(conversation, uri, type);
        Futures.addCallback(
                future,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(final Void result) {
                        Log.d(Config.LOGTAG, "attachImageToConversation.onSuccess");
                        prepareFileToast.cancel();
                    }

                    @Override
                    public void onFailure(final @NonNull Throwable t) {
                        Log.d(Config.LOGTAG, "could not attach image", t);
                        prepareFileToast.cancel();
                        displayToastForException(t);
                    }
                },
                ContextCompat.getMainExecutor(requireContext()));
    }

    private void displayToastForException(final Throwable t) {
        if (t instanceof FileBackend.FileCopyException e) {
            Toast.makeText(requireContext(), e.getResId(), Toast.LENGTH_LONG).show();
        } else {
            final String message = t.getMessage();
            if (Strings.isNullOrEmpty(message)) {
                return;
            }
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
        }
    }

    private void sendMessage() {
        if (mediaPreviewAdapter.hasAttachments()) {
            commitAttachments();
            return;
        }
        final Editable text = this.binding.textinput.getText();
        final String body = text == null ? "" : text.toString();
        final Conversation conversation = this.conversation;
        if (body.isEmpty() || conversation == null) {
            return;
        }
        if (trustKeysIfNeeded(conversation, REQUEST_TRUST_KEYS_TEXT)) {
            return;
        }
        final Message message;
        if (conversation.getCorrectingMessage() == null) {
            message = new Message(conversation, body, conversation.getNextEncryption());
            Message.configurePrivateMessage(message);
        } else {
            message = conversation.getCorrectingMessage();
            message.setBody(body);
            message.putEdited(message.getUuid(), message.getServerMsgId());
            message.setUuid(UUID.randomUUID().toString());
        }
        if (conversation.getNextEncryption() == Message.ENCRYPTION_PGP) {
            sendPgpMessage(message);
        } else {
            sendMessage(message);
        }
    }

    private boolean trustKeysIfNeeded(final Conversation conversation, final int requestCode) {
        return conversation.getNextEncryption() == Message.ENCRYPTION_AXOLOTL
                && trustKeysIfNeeded(requestCode);
    }

    protected boolean trustKeysIfNeeded(final int requestCode) {
        final var axolotlService = conversation.getAccount().getAxolotlService();
        final var targets = axolotlService.getCryptoTargets(conversation);
        boolean hasUnaccepted = !conversation.getAcceptedCryptoTargets().containsAll(targets);
        // TODO basically all of those are hitting the database. This should be async
        boolean hasUndecidedOwn =
                !axolotlService
                        .getKeysWithTrust(FingerprintStatus.createActiveUndecided())
                        .isEmpty();
        boolean hasUndecidedContacts =
                !axolotlService
                        .getKeysWithTrust(FingerprintStatus.createActiveUndecided(), targets)
                        .isEmpty();
        boolean hasPendingKeys = !axolotlService.findDevicesWithoutSession(conversation).isEmpty();
        boolean hasNoTrustedKeys = axolotlService.anyTargetHasNoTrustedKeys(targets);
        boolean downloadInProgress = axolotlService.hasPendingKeyFetches(targets);
        if (hasUndecidedOwn
                || hasUndecidedContacts
                || hasPendingKeys
                || hasNoTrustedKeys
                || hasUnaccepted
                || downloadInProgress) {
            axolotlService.createSessionsIfNeeded(conversation);
            final Intent intent = new Intent(requireActivity(), TrustKeysActivity.class);
            intent.putExtra(
                    "contacts",
                    Collections2.transform(targets, Jid::toString).toArray(new String[0]));
            intent.putExtra(
                    EXTRA_ACCOUNT, conversation.getAccount().getJid().asBareJid().toString());
            intent.putExtra("conversation", conversation.getUuid());
            startActivityForResult(intent, requestCode);
            return true;
        } else {
            return false;
        }
    }

    public void updateChatMsgHint() {
        final boolean multi = conversation.getMode() == Conversation.MODE_MULTI;
        if (conversation.getCorrectingMessage() != null) {
            this.binding.textInputHint.setVisibility(View.GONE);
            this.binding.textinput.setHint(R.string.send_corrected_message);
        } else if (multi && conversation.getNextCounterpart() != null) {
            this.binding.textinput.setHint(R.string.send_unencrypted_message);
            this.binding.textInputHint.setVisibility(View.VISIBLE);
            this.binding.textInputHint.setText(
                    getString(
                            R.string.send_private_message_to,
                            conversation.getNextCounterpart().getResource()));
        } else if (multi && !conversation.getMucOptions().participating()) {
            this.binding.textInputHint.setVisibility(View.GONE);
            this.binding.textinput.setHint(R.string.you_are_not_participating);
        } else {
            this.binding.textInputHint.setVisibility(View.GONE);
            this.binding.textinput.setHint(UIHelper.getMessageHint(requireContext(), conversation));
            requireActivity().invalidateOptionsMenu();
        }
    }

    public void setupIme() {
        this.binding.textinput.refreshIme();
    }

    private void handleActivityResult(ActivityResult activityResult) {
        if (activityResult.resultCode == AppCompatActivity.RESULT_OK) {
            handlePositiveActivityResult(activityResult.requestCode, activityResult.data);
        } else {
            handleNegativeActivityResult(activityResult.requestCode);
        }
    }

    private void handlePositiveActivityResult(final int requestCode, final Intent data) {
        switch (requestCode) {
            case REQUEST_TRUST_KEYS_TEXT:
                sendMessage();
                break;
            case REQUEST_TRUST_KEYS_ATTACHMENTS:
                commitAttachments();
                break;
            case REQUEST_START_AUDIO_CALL:
                triggerRtpSession(RtpSessionActivity.ACTION_MAKE_VOICE_CALL);
                break;
            case REQUEST_START_VIDEO_CALL:
                triggerRtpSession(RtpSessionActivity.ACTION_MAKE_VIDEO_CALL);
                break;
            case ATTACHMENT_CHOICE_CHOOSE_IMAGE:
                final List<Attachment> imageUris =
                        Attachment.extractAttachments(
                                requireContext(), data, Attachment.Type.IMAGE);
                mediaPreviewAdapter.addMediaPreviews(imageUris);
                toggleInputMethod();
                break;
            case ATTACHMENT_CHOICE_TAKE_PHOTO:
                final Uri takePhotoUri = pendingTakePhotoUri.pop();
                if (takePhotoUri != null) {
                    mediaPreviewAdapter.addMediaPreviews(
                            Attachment.of(requireContext(), takePhotoUri, Attachment.Type.IMAGE));
                    toggleInputMethod();
                } else {
                    Log.d(Config.LOGTAG, "lost take photo uri. unable to to attach");
                }
                break;
            case ATTACHMENT_CHOICE_CHOOSE_FILE:
            case ATTACHMENT_CHOICE_RECORD_VIDEO:
            case ATTACHMENT_CHOICE_RECORD_VOICE:
                final Attachment.Type type =
                        requestCode == ATTACHMENT_CHOICE_RECORD_VOICE
                                ? Attachment.Type.RECORDING
                                : Attachment.Type.FILE;
                final List<Attachment> fileUris =
                        Attachment.extractAttachments(requireContext(), data, type);
                mediaPreviewAdapter.addMediaPreviews(fileUris);
                toggleInputMethod();
                break;
            case ATTACHMENT_CHOICE_LOCATION:
                final double latitude = data.getDoubleExtra("latitude", 0);
                final double longitude = data.getDoubleExtra("longitude", 0);
                final int accuracy = data.getIntExtra("accuracy", 0);
                final Uri geo;
                if (accuracy > 0) {
                    geo = Uri.parse(String.format("geo:%s,%s;u=%s", latitude, longitude, accuracy));
                } else {
                    geo = Uri.parse(String.format("geo:%s,%s", latitude, longitude));
                }
                mediaPreviewAdapter.addMediaPreviews(
                        Attachment.of(getActivity(), geo, Attachment.Type.LOCATION));
                toggleInputMethod();
                break;
            case REQUEST_INVITE_TO_CONVERSATION:
                XmppActivity.ConferenceInvite invite = XmppActivity.ConferenceInvite.parse(data);
                if (invite != null) {
                    if (invite.execute(requireXmppActivity())) {
                        requireXmppActivity().mToast =
                                Toast.makeText(
                                        requireContext(),
                                        R.string.creating_conference,
                                        Toast.LENGTH_LONG);
                        requireXmppActivity().mToast.show();
                    }
                }
                break;
        }
    }

    private void commitAttachments() {
        final List<Attachment> attachments = mediaPreviewAdapter.getAttachments();
        if (anyNeedsExternalStoragePermission(attachments)
                && !hasPermissions(
                        REQUEST_COMMIT_ATTACHMENTS, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            return;
        }
        if (trustKeysIfNeeded(conversation, REQUEST_TRUST_KEYS_ATTACHMENTS)) {
            return;
        }
        final PresenceSelector.OnPresenceSelected callback =
                () -> {
                    for (Iterator<Attachment> i = attachments.iterator(); i.hasNext(); i.remove()) {
                        final Attachment attachment = i.next();
                        if (attachment.getType() == Attachment.Type.LOCATION) {
                            attachLocationToConversation(conversation, attachment.getUri());
                        } else if (attachment.getType() == Attachment.Type.IMAGE) {
                            Log.d(
                                    Config.LOGTAG,
                                    "ChatActivity.commitAttachments() - attaching image to"
                                            + " chat. CHOOSE_IMAGE");
                            attachImageToConversation(
                                    conversation, attachment.getUri(), attachment.getMime());
                        } else {
                            Log.d(
                                    Config.LOGTAG,
                                    "ChatActivity.commitAttachments() - attaching file to"
                                        + " chat. CHOOSE_FILE/RECORD_VOICE/RECORD_VIDEO");
                            attachFileToConversation(
                                    conversation, attachment.getUri(), attachment.getMime());
                        }
                    }
                    mediaPreviewAdapter.notifyDataSetChanged();
                    toggleInputMethod();
                };
        if (conversation == null
                || conversation.getMode() == Conversation.MODE_MULTI
                || Attachment.canBeSendInBand(attachments)
                || (conversation.getAccount().httpUploadAvailable()
                        && FileBackend.allFilesUnderSize(
                                getActivity(), attachments, getMaxHttpUploadSize(conversation)))) {
            callback.onPresenceSelected();
        } else {
            requireXmppActivity().selectPresence(conversation, callback);
        }
    }

    private static boolean anyNeedsExternalStoragePermission(
            final Collection<Attachment> attachments) {
        for (final Attachment attachment : attachments) {
            if (attachment.getType() != Attachment.Type.LOCATION) {
                return true;
            }
        }
        return false;
    }

    public void toggleInputMethod() {
        boolean hasAttachments = mediaPreviewAdapter.hasAttachments();
        binding.textinput.setVisibility(hasAttachments ? View.GONE : View.VISIBLE);
        binding.mediaPreview.setVisibility(hasAttachments ? View.VISIBLE : View.GONE);
        updateSendButton();
    }

    private void handleNegativeActivityResult(int requestCode) {
        switch (requestCode) {
            case ATTACHMENT_CHOICE_TAKE_PHOTO:
                if (pendingTakePhotoUri.clear()) {
                    Log.d(
                            Config.LOGTAG,
                            "cleared pending photo uri after negative activity result");
                }
                break;
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        ActivityResult activityResult = ActivityResult.of(requestCode, resultCode, data);
        if (requireXmppActivity().xmppConnectionService != null) {
            handleActivityResult(activityResult);
        } else {
            this.postponedActivityResult.push(activityResult);
        }
    }

    public void unblockConversation(final Blockable conversation) {
        requireXmppActivity().xmppConnectionService.sendUnblockRequest(conversation);
    }

    @Override
    public void onViewCreated(@NonNull final View view, final Bundle savedInstanceState) {
        requireActivity()
                .addMenuProvider(menuProvider, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
        if (savedInstanceState == null) {
            return;
        }
        this.processSavedInstanceState(savedInstanceState);
    }

    @Override
    public View onCreateView(
            @NonNull final LayoutInflater inflater,
            final ViewGroup container,
            final Bundle savedInstanceState) {
        this.binding =
                DataBindingUtil.inflate(inflater, R.layout.fragment_conversation, container, false);
        binding.getRoot().setOnClickListener(null); // TODO why the fuck did we do this?

        binding.textinput.addTextChangedListener(
                new StylingHelper.MessageEditorStyler(binding.textinput));

        binding.textinput.setOnEditorActionListener(mEditorActionListener);
        binding.textinput.setRichContentListener(new String[] {"image/*"}, mEditorContentListener);

        binding.textSendButton.setOnClickListener(this.mSendButtonListener);

        binding.scrollToBottomButton.setOnClickListener(this.mScrollButtonListener);
        binding.messagesView.setOnScrollListener(mOnScrollListener);
        binding.messagesView.setTranscriptMode(ListView.TRANSCRIPT_MODE_NORMAL);
        binding.mediaPreview.setAdapter(mediaPreviewAdapter);
        messageListAdapter = new MessageAdapter((XmppActivity) getActivity(), this.messageList);
        messageListAdapter.setOnContactPictureClicked(this);
        messageListAdapter.setOnContactPictureLongClicked(this);
        binding.messagesView.setAdapter(messageListAdapter);

        registerForContextMenu(binding.messagesView);

        this.binding.textinput.setCustomInsertionActionModeCallback(
                new EditMessageActionModeCallback(this.binding.textinput));

        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Log.d(Config.LOGTAG, "ConversationFragment.onDestroyView()");
        messageListAdapter.setOnContactPictureClicked(null);
        messageListAdapter.setOnContactPictureLongClicked(null);
    }

    private void quoteText(String text) {
        if (binding.textinput.isEnabled()) {
            binding.textinput.insertAsQuote(text);
            binding.textinput.requestFocus();
            InputMethodManager inputMethodManager =
                    (InputMethodManager)
                            requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (inputMethodManager != null) {
                inputMethodManager.showSoftInput(
                        binding.textinput, InputMethodManager.SHOW_IMPLICIT);
            }
        }
    }

    private void quoteMessage(Message message) {
        quoteText(MessageUtils.prepareQuote(message));
    }

    @Override
    public void onCreateContextMenu(@NonNull ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        // This should cancel any remaining click events that would otherwise trigger links
        v.dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0f, 0f, 0));
        synchronized (this.messageList) {
            super.onCreateContextMenu(menu, v, menuInfo);
            AdapterView.AdapterContextMenuInfo acmi = (AdapterContextMenuInfo) menuInfo;
            this.selectedMessage = this.messageList.get(acmi.position);
            populateContextMenu(menu);
        }
    }

    private static boolean isAckedModerationDisclaimer() {
        return ackModeration.isAfter(Instant.now());
    }

    private void populateContextMenu(final ContextMenu menu) {
        final Message m = this.selectedMessage;
        final Transferable t = m.getTransferable();
        if (m.getType() != Message.TYPE_STATUS && m.getType() != Message.TYPE_RTP_SESSION) {

            if (m.getEncryption() == Message.ENCRYPTION_AXOLOTL_NOT_FOR_THIS_DEVICE
                    || m.getEncryption() == Message.ENCRYPTION_AXOLOTL_FAILED) {
                return;
            }

            if (m.getStatus() == Message.STATUS_RECEIVED
                    && t != null
                    && (t.getStatus() == Transferable.STATUS_CANCELLED
                            || t.getStatus() == Transferable.STATUS_FAILED)) {
                return;
            }

            final boolean deleted = m.isDeleted();
            final boolean encrypted =
                    m.getEncryption() == Message.ENCRYPTION_DECRYPTION_FAILED
                            || m.getEncryption() == Message.ENCRYPTION_PGP;
            final boolean receiving =
                    m.getStatus() == Message.STATUS_RECEIVED
                            && (t instanceof JingleFileTransferConnection
                                    || t instanceof HttpDownloadConnection);
            requireActivity().getMenuInflater().inflate(R.menu.message_context, menu);
            menu.setHeaderTitle(R.string.message_options);
            final MenuItem addReaction = menu.findItem(R.id.action_add_reaction);
            final MenuItem reportAndBlock = menu.findItem(R.id.action_report_and_block);
            final MenuItem openWith = menu.findItem(R.id.open_with);
            final MenuItem copyMessage = menu.findItem(R.id.copy_message);
            final MenuItem copyLink = menu.findItem(R.id.copy_link);
            final MenuItem quoteMessage = menu.findItem(R.id.quote_message);
            final MenuItem retryDecryption = menu.findItem(R.id.retry_decryption);
            final MenuItem correctMessage = menu.findItem(R.id.correct_message);
            final MenuItem shareWith = menu.findItem(R.id.share_with);
            final MenuItem sendAgain = menu.findItem(R.id.send_again);
            final MenuItem retryAsP2P = menu.findItem(R.id.send_again_as_p2p);
            final MenuItem copyUrl = menu.findItem(R.id.copy_url);
            final MenuItem downloadFile = menu.findItem(R.id.download_file);
            final MenuItem cancelTransmission = menu.findItem(R.id.cancel_transmission);
            final MenuItem deleteFile = menu.findItem(R.id.delete_file);
            final MenuItem moderateMessage = menu.findItem(R.id.moderation);
            final MenuItem showErrorMessage = menu.findItem(R.id.show_error_message);
            final boolean unInitiatedButKnownSize = MessageUtils.unInitiatedButKnownSize(m);
            final boolean showError =
                    m.getStatus() == Message.STATUS_SEND_FAILED
                            && m.getErrorMessage() != null
                            && !Message.ERROR_MESSAGE_CANCELLED.equals(m.getErrorMessage());
            final Conversational conversational = m.getConversation();
            final var connection = conversational.getAccount().getXmppConnection();
            if (m.getStatus() == Message.STATUS_RECEIVED
                    && conversational instanceof Conversation c) {
                if (c.isWithStranger()
                        && m.getServerMsgId() != null
                        && !c.isBlocked()
                        && connection != null
                        && connection.getManager(BlockingManager.class).hasSpamReporting()) {
                    reportAndBlock.setVisible(true);
                }
            }
            if (conversational instanceof Conversation c) {
                final var singleOrOccupantId =
                        c.getMode() == Conversational.MODE_SINGLE
                                || (c.getMucOptions().occupantId()
                                        && c.getMucOptions().participating());
                addReaction.setVisible(
                        m.getStatus() != Message.STATUS_SEND_FAILED
                                && !m.isDeleted()
                                && singleOrOccupantId);
                if (m.getStatus() != Message.STATUS_SEND_FAILED
                        && c.getMode() == Conversational.MODE_MULTI) {
                    final var mucOptions = c.getMucOptions();
                    moderateMessage.setVisible(
                            !mucOptions.isPrivateAndNonAnonymous()
                                    && mucOptions.moderation()
                                    && mucOptions.getSelf().ranks(Role.MODERATOR)
                                    && m.getServerMsgId() != null);
                } else {
                    moderateMessage.setVisible(false);
                }
                moderateMessage.setTitle(
                        isAckedModerationDisclaimer()
                                ? R.string.moderate_delete
                                : R.string.moderate_delete_dot_dot_dot);
                correctMessage.setVisible(
                        !showError
                                && m.getType() == Message.TYPE_TEXT
                                && !m.isGeoUri()
                                && m.isLastCorrectableMessage()
                                && singleOrOccupantId);
            } else {
                moderateMessage.setVisible(false);
                addReaction.setVisible(false);
                correctMessage.setVisible(false);
            }
            if (!m.isFileOrImage()
                    && !encrypted
                    && !m.isGeoUri()
                    && !m.treatAsDownloadable()
                    && !unInitiatedButKnownSize
                    && t == null) {
                copyMessage.setVisible(true);
                quoteMessage.setVisible(!showError && !MessageUtils.prepareQuote(m).isEmpty());
                final var firstUri = Iterables.getFirst(Linkify.getLinks(m.getBody()), null);
                if (firstUri != null) {
                    final var scheme = firstUri.getScheme();
                    final @StringRes int resForScheme =
                            switch (scheme) {
                                case "xmpp" -> R.string.copy_jabber_id;
                                case "http", "https", "gemini" -> R.string.copy_link;
                                case "geo" -> R.string.copy_geo_uri;
                                case "tel" -> R.string.copy_telephone_number;
                                case "mailto" -> R.string.copy_email_address;
                                default -> R.string.copy_URI;
                            };
                    copyLink.setTitle(resForScheme);
                    copyLink.setVisible(true);
                } else {
                    copyLink.setVisible(false);
                }
            }
            if (m.getEncryption() == Message.ENCRYPTION_DECRYPTION_FAILED && !deleted) {
                retryDecryption.setVisible(true);
            }
            if ((m.isFileOrImage() && !deleted && !receiving)
                    || (m.getType() == Message.TYPE_TEXT && !m.treatAsDownloadable())
                            && !unInitiatedButKnownSize
                            && t == null) {
                shareWith.setVisible(true);
            }
            if (m.getStatus() == Message.STATUS_SEND_FAILED) {
                sendAgain.setVisible(true);
                final var httpUploadAvailable =
                        connection != null
                                && Objects.nonNull(
                                        connection
                                                .getManager(HttpUploadManager.class)
                                                .getService());
                final var fileNotUploaded = m.isFileOrImage() && !m.hasFileOnRemoteHost();
                final var isPeerOnline =
                        conversational.getMode() == Conversation.MODE_SINGLE
                                && (conversational instanceof Conversation c)
                                && !c.getContact().getPresences().isEmpty();
                retryAsP2P.setVisible(fileNotUploaded && isPeerOnline && httpUploadAvailable);
            }
            if (m.getEncryption() == Message.ENCRYPTION_NONE
                    && (m.hasFileOnRemoteHost()
                            || m.treatAsDownloadable()
                            || unInitiatedButKnownSize
                            || t instanceof HttpDownloadConnection)) {
                copyUrl.setVisible(true);
            }
            if (m.isFileOrImage() && deleted && m.hasFileOnRemoteHost()) {
                downloadFile.setVisible(true);
                downloadFile.setTitle(
                        requireContext()
                                .getString(
                                        R.string.download_x_file,
                                        UIHelper.getFileDescriptionString(requireContext(), m)));
            }
            final boolean waitingOfferedSending =
                    m.getStatus() == Message.STATUS_WAITING
                            || m.getStatus() == Message.STATUS_UNSEND
                            || m.getStatus() == Message.STATUS_OFFERED;
            final boolean cancelable =
                    (t != null && !deleted) || waitingOfferedSending && m.needsUploading();
            if (cancelable) {
                cancelTransmission.setVisible(true);
            }
            if (m.isFileOrImage() && !deleted && !cancelable) {
                final String path = m.getRelativeFilePath();
                if (path == null
                        || !path.startsWith("/")
                        || FileBackend.inChatDirectory(requireActivity(), path)) {
                    deleteFile.setVisible(true);
                    deleteFile.setTitle(
                            requireContext()
                                    .getString(
                                            R.string.delete_x_file,
                                            UIHelper.getFileDescriptionString(
                                                    requireContext(), m)));
                }
            }
            if (showError) {
                showErrorMessage.setVisible(true);
            }
            final String mime = m.isFileOrImage() ? m.getMimeType() : null;
            if ((m.isGeoUri() && GeoHelper.openInOsmAnd(getActivity(), m))
                    || (mime != null && mime.startsWith("audio/"))) {
                openWith.setVisible(true);
            }
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        return switch (item.getItemId()) {
            case R.id.share_with -> {
                ShareUtil.share(requireXmppActivity(), selectedMessage);
                yield true;
            }
            case R.id.correct_message -> {
                correctMessage(selectedMessage);
                yield true;
            }
            case R.id.copy_message -> {
                ShareUtil.copyToClipboard(requireXmppActivity(), selectedMessage);
                yield true;
            }
            case R.id.copy_link -> {
                ShareUtil.copyLinkToClipboard(requireXmppActivity(), selectedMessage);
                yield true;
            }
            case R.id.quote_message -> {
                quoteMessage(selectedMessage);
                yield true;
            }
            case R.id.send_again -> {
                resendMessage(selectedMessage, false);
                yield true;
            }
            case R.id.send_again_as_p2p -> {
                resendMessage(selectedMessage, true);
                yield true;
            }
            case R.id.copy_url -> {
                ShareUtil.copyUrlToClipboard(requireXmppActivity(), selectedMessage);
                yield true;
            }
            case R.id.download_file -> {
                startDownloadable(selectedMessage);
                yield true;
            }
            case R.id.cancel_transmission -> {
                cancelTransmission(selectedMessage);
                yield true;
            }
            case R.id.retry_decryption -> {
                retryDecryption(selectedMessage);
                yield true;
            }
            case R.id.delete_file -> {
                deleteFile(selectedMessage);
                yield true;
            }
            case R.id.moderation -> {
                moderate(selectedMessage);
                yield true;
            }
            case R.id.show_error_message -> {
                showErrorMessage(selectedMessage);
                yield true;
            }
            case R.id.open_with -> {
                openWith(selectedMessage);
                yield true;
            }
            case R.id.action_report_and_block -> {
                reportMessage(selectedMessage);
                yield true;
            }
            case R.id.action_add_reaction -> {
                addReaction(selectedMessage);
                yield true;
            }
            default -> super.onContextItemSelected(item);
        };
    }

    private void startSearch() {
        final Intent intent = new Intent(getActivity(), SearchActivity.class);
        intent.putExtra(SearchActivity.EXTRA_CONVERSATION_UUID, conversation.getUuid());
        startActivity(intent);
    }

    private void returnToOngoingCall() {
        final Optional<OngoingRtpSession> ongoingRtpSession =
                requireXmppActivity()
                        .xmppConnectionService
                        .getJingleConnectionManager()
                        .getOngoingRtpConnection(conversation.getContact());
        if (ongoingRtpSession.isPresent()) {
            final OngoingRtpSession id = ongoingRtpSession.get();
            final Intent intent = new Intent(getActivity(), RtpSessionActivity.class);
            intent.setAction(Intent.ACTION_VIEW);
            intent.putExtra(
                    RtpSessionActivity.EXTRA_ACCOUNT,
                    id.getAccount().getJid().asBareJid().toString());
            intent.putExtra(RtpSessionActivity.EXTRA_WITH, id.getWith().toString());
            if (id instanceof AbstractJingleConnection) {
                intent.putExtra(RtpSessionActivity.EXTRA_SESSION_ID, id.getSessionId());
                startActivity(intent);
            } else if (id instanceof JingleConnectionManager.RtpSessionProposal proposal) {
                if (Media.audioOnly(proposal.media)) {
                    intent.putExtra(
                            RtpSessionActivity.EXTRA_LAST_ACTION,
                            RtpSessionActivity.ACTION_MAKE_VOICE_CALL);
                } else {
                    intent.putExtra(
                            RtpSessionActivity.EXTRA_LAST_ACTION,
                            RtpSessionActivity.ACTION_MAKE_VIDEO_CALL);
                }
                intent.putExtra(RtpSessionActivity.EXTRA_PROPOSED_SESSION_ID, proposal.sessionId);
                startActivity(intent);
            }
        }
    }

    private void togglePinned() {
        final boolean pinned =
                conversation.getBooleanAttribute(Conversation.ATTRIBUTE_PINNED_ON_TOP, false);
        conversation.setAttribute(Conversation.ATTRIBUTE_PINNED_ON_TOP, !pinned);
        requireXmppActivity().xmppConnectionService.updateConversation(conversation);
        requireActivity().invalidateOptionsMenu();
    }

    private void checkPermissionAndTriggerAudioCall() {
        if (requireXmppActivity().mUseTor || conversation.getAccount().isOnion()) {
            Toast.makeText(requireContext(), R.string.disable_tor_to_make_call, Toast.LENGTH_SHORT)
                    .show();
            return;
        }
        final List<String> permissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions =
                    Arrays.asList(
                            Manifest.permission.RECORD_AUDIO,
                            Manifest.permission.BLUETOOTH_CONNECT);
        } else {
            permissions = Collections.singletonList(Manifest.permission.RECORD_AUDIO);
        }
        if (hasPermissions(REQUEST_START_AUDIO_CALL, permissions)) {
            triggerRtpSession(RtpSessionActivity.ACTION_MAKE_VOICE_CALL);
        }
    }

    private void checkPermissionAndTriggerVideoCall() {
        if (requireXmppActivity().mUseTor || conversation.getAccount().isOnion()) {
            Toast.makeText(requireContext(), R.string.disable_tor_to_make_call, Toast.LENGTH_SHORT)
                    .show();
            return;
        }
        final List<String> permissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions =
                    Arrays.asList(
                            Manifest.permission.RECORD_AUDIO,
                            Manifest.permission.CAMERA,
                            Manifest.permission.BLUETOOTH_CONNECT);
        } else {
            permissions =
                    Arrays.asList(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA);
        }
        if (hasPermissions(REQUEST_START_VIDEO_CALL, permissions)) {
            triggerRtpSession(RtpSessionActivity.ACTION_MAKE_VIDEO_CALL);
        }
    }

    private void triggerRtpSession(final String action) {
        if (requireXmppActivity().xmppConnectionService.getJingleConnectionManager().isBusy()) {
            Toast.makeText(getActivity(), R.string.only_one_call_at_a_time, Toast.LENGTH_LONG)
                    .show();
            return;
        }
        final Account account = conversation.getAccount();
        if (account.setOption(Account.OPTION_SOFT_DISABLED, false)) {
            requireXmppActivity().xmppConnectionService.updateAccount(account);
        }
        final Contact contact = conversation.getContact();
        if (Config.USE_JINGLE_MESSAGE_INIT && RtpCapability.jmiSupport(contact)) {
            triggerRtpSession(contact.getAccount(), contact.getAddress().asBareJid(), action);
        } else {
            final RtpCapability.Capability capability;
            if (action.equals(RtpSessionActivity.ACTION_MAKE_VIDEO_CALL)) {
                capability = RtpCapability.Capability.VIDEO;
            } else {
                capability = RtpCapability.Capability.AUDIO;
            }
            PresenceSelector.selectFullJidForDirectRtpConnection(
                    requireActivity(),
                    contact,
                    capability,
                    fullJid -> {
                        triggerRtpSession(contact.getAccount(), fullJid, action);
                    });
        }
    }

    private void triggerRtpSession(final Account account, final Jid with, final String action) {
        CallIntegrationConnectionService.placeCall(
                requireXmppActivity().xmppConnectionService,
                account,
                with,
                RtpSessionActivity.actionToMedia(action));
    }

    private void handleAttachmentSelection(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.attach_choose_picture:
                attachFile(ATTACHMENT_CHOICE_CHOOSE_IMAGE);
                break;
            case R.id.attach_take_picture:
                attachFile(ATTACHMENT_CHOICE_TAKE_PHOTO);
                break;
            case R.id.attach_record_video:
                attachFile(ATTACHMENT_CHOICE_RECORD_VIDEO);
                break;
            case R.id.attach_choose_file:
                attachFile(ATTACHMENT_CHOICE_CHOOSE_FILE);
                break;
            case R.id.attach_record_voice:
                attachFile(ATTACHMENT_CHOICE_RECORD_VOICE);
                break;
            case R.id.attach_location:
                attachFile(ATTACHMENT_CHOICE_LOCATION);
                break;
        }
    }

    private void handleEncryptionSelection(MenuItem item) {
        if (conversation == null) {
            return;
        }
        final boolean updated;
        switch (item.getItemId()) {
            case R.id.encryption_choice_none:
                updated = conversation.setNextEncryption(Message.ENCRYPTION_NONE);
                item.setChecked(true);
                break;
            case R.id.encryption_choice_pgp:
                if (requireXmppActivity().hasPgp()) {
                    if (conversation.getAccount().getPgpSignature() != null) {
                        updated = conversation.setNextEncryption(Message.ENCRYPTION_PGP);
                        item.setChecked(true);
                    } else {
                        updated = false;
                        requireXmppActivity()
                                .announcePgp(
                                        conversation.getAccount(),
                                        conversation,
                                        null,
                                        requireXmppActivity().onOpenPGPKeyPublished);
                    }
                } else {
                    requireXmppActivity().showInstallPgpDialog();
                    updated = false;
                }
                break;
            case R.id.encryption_choice_axolotl:
                Log.d(
                        Config.LOGTAG,
                        AxolotlService.getLogprefix(conversation.getAccount())
                                + "Enabled axolotl for Contact "
                                + conversation.getContact().getAddress());
                updated = conversation.setNextEncryption(Message.ENCRYPTION_AXOLOTL);
                item.setChecked(true);
                break;
            default:
                updated = conversation.setNextEncryption(Message.ENCRYPTION_NONE);
                break;
        }
        if (updated) {
            requireXmppActivity().xmppConnectionService.updateConversation(conversation);
        }
        updateChatMsgHint();
        requireXmppActivity().invalidateOptionsMenu();
        requireXmppActivity().refreshUi();
    }

    public void attachFile(final int attachmentChoice) {
        attachFile(attachmentChoice, true);
    }

    public void attachFile(final int attachmentChoice, final boolean updateRecentlyUsed) {
        if (attachmentChoice == ATTACHMENT_CHOICE_RECORD_VOICE) {
            if (!hasPermissions(
                    attachmentChoice,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.RECORD_AUDIO)) {
                return;
            }
        } else if (attachmentChoice == ATTACHMENT_CHOICE_TAKE_PHOTO
                || attachmentChoice == ATTACHMENT_CHOICE_RECORD_VIDEO) {
            if (!hasPermissions(
                    attachmentChoice,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.CAMERA)) {
                return;
            }
        } else if (attachmentChoice != ATTACHMENT_CHOICE_LOCATION) {
            if (!hasPermissions(attachmentChoice, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                return;
            }
        }
        if (updateRecentlyUsed) {
            storeRecentlyUsedQuickAction(attachmentChoice);
        }
        final int encryption = conversation.getNextEncryption();
        final int mode = conversation.getMode();
        if (encryption == Message.ENCRYPTION_PGP) {
            if (requireXmppActivity().hasPgp()) {
                if (mode == Conversation.MODE_SINGLE
                        && conversation.getContact().getPgpKeyId() != 0) {
                    final var future =
                            requireXmppActivity()
                                    .xmppConnectionService
                                    .getPgpEngine()
                                    .hasKey(conversation.getContact());
                    Futures.addCallback(
                            future,
                            new FutureCallback<>() {
                                @Override
                                public void onSuccess(Boolean result) {
                                    invokeAttachFileIntent(attachmentChoice);
                                }

                                @Override
                                public void onFailure(@NonNull Throwable t) {
                                    if (t instanceof PgpEngine.UserInputRequiredException e) {
                                        startPendingIntent(e.getPendingIntent(), attachmentChoice);
                                    } else {
                                        final String msg = t.getMessage();
                                        if (Strings.isNullOrEmpty(msg)) {
                                            requireXmppActivity().replaceToast(msg);
                                        }
                                    }
                                }
                            },
                            MoreExecutors.directExecutor());
                } else if (mode == Conversation.MODE_MULTI
                        && conversation.getMucOptions().pgpKeysInUse()) {
                    if (conversation.getMucOptions().missingPgpKeys()) {
                        Toast warning =
                                Toast.makeText(
                                        getActivity(),
                                        R.string.missing_public_keys,
                                        Toast.LENGTH_LONG);
                        warning.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
                        warning.show();
                    }
                    invokeAttachFileIntent(attachmentChoice);
                } else {
                    showNoPGPKeyDialog(
                            false,
                            (dialog, which) -> {
                                conversation.setNextEncryption(Message.ENCRYPTION_NONE);
                                requireXmppActivity()
                                        .xmppConnectionService
                                        .updateConversation(conversation);
                                invokeAttachFileIntent(attachmentChoice);
                            });
                }
            } else {
                requireXmppActivity().showInstallPgpDialog();
            }
        } else {
            invokeAttachFileIntent(attachmentChoice);
        }
    }

    private void storeRecentlyUsedQuickAction(final int attachmentChoice) {
        try {
            requireXmppActivity()
                    .getPreferences()
                    .edit()
                    .putString(
                            RECENTLY_USED_QUICK_ACTION,
                            SendButtonAction.of(attachmentChoice).toString())
                    .apply();
        } catch (IllegalArgumentException e) {
            // just do not save
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        final PermissionUtils.PermissionResult permissionResult =
                PermissionUtils.removeBluetoothConnect(permissions, grantResults);
        if (grantResults.length > 0) {
            if (allGranted(permissionResult.grantResults)) {
                switch (requestCode) {
                    case REQUEST_START_DOWNLOAD:
                        if (this.mPendingDownloadableMessage != null) {
                            startDownloadable(this.mPendingDownloadableMessage);
                        }
                        break;
                    case REQUEST_ADD_EDITOR_CONTENT:
                        if (this.mPendingEditorContent != null) {
                            attachEditorContentToConversation(this.mPendingEditorContent);
                        }
                        break;
                    case REQUEST_COMMIT_ATTACHMENTS:
                        commitAttachments();
                        break;
                    case REQUEST_START_AUDIO_CALL:
                        triggerRtpSession(RtpSessionActivity.ACTION_MAKE_VOICE_CALL);
                        break;
                    case REQUEST_START_VIDEO_CALL:
                        triggerRtpSession(RtpSessionActivity.ACTION_MAKE_VIDEO_CALL);
                        break;
                    default:
                        attachFile(requestCode);
                        break;
                }
            } else {
                @StringRes int res;
                String firstDenied =
                        getFirstDenied(permissionResult.grantResults, permissionResult.permissions);
                if (Manifest.permission.RECORD_AUDIO.equals(firstDenied)) {
                    res = R.string.no_microphone_permission;
                } else if (Manifest.permission.CAMERA.equals(firstDenied)) {
                    res = R.string.no_camera_permission;
                } else {
                    res = R.string.no_storage_permission;
                }
                Toast.makeText(
                                getActivity(),
                                getString(res, getString(R.string.app_name)),
                                Toast.LENGTH_SHORT)
                        .show();
            }
        }
        if (writeGranted(grantResults, permissions)) {
            final var service = getXmppConnectionService();
            if (service != null) {
                service.getBitmapCache().evictAll();
                service.restartFileObserver();
            }
            refresh();
        }
        if (cameraGranted(grantResults, permissions) || audioGranted(grantResults, permissions)) {
            XmppConnectionService.toggleForegroundService(requireXmppActivity());
        }
    }

    public void startDownloadable(final Message message) {
        if (!hasPermissions(REQUEST_START_DOWNLOAD, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            this.mPendingDownloadableMessage = message;
            return;
        }
        Transferable transferable = message.getTransferable();
        if (transferable != null) {
            if (transferable instanceof TransferablePlaceholder && message.hasFileOnRemoteHost()) {
                createNewConnection(message);
                return;
            }
            if (!transferable.start()) {
                Log.d(Config.LOGTAG, "type: " + transferable.getClass().getName());
                Toast.makeText(getActivity(), R.string.not_connected_try_again, Toast.LENGTH_SHORT)
                        .show();
            }
        } else if (message.treatAsDownloadable()
                || message.hasFileOnRemoteHost()
                || MessageUtils.unInitiatedButKnownSize(message)) {
            createNewConnection(message);
        } else {
            Log.d(
                    Config.LOGTAG,
                    message.getConversation().getAccount() + ": unable to start downloadable");
        }
    }

    private void createNewConnection(final Message message) {
        if (!requireXmppActivity().xmppConnectionService.hasInternetConnection()) {
            Toast.makeText(getActivity(), R.string.not_connected_try_again, Toast.LENGTH_SHORT)
                    .show();
            return;
        }
        requireXmppActivity()
                .xmppConnectionService
                .getHttpConnectionManager()
                .createNewDownloadConnection(message, true);
    }

    @SuppressLint("InflateParams")
    protected void clearHistoryDialog(final Conversation conversation) {
        final MaterialAlertDialogBuilder builder =
                new MaterialAlertDialogBuilder(requireActivity());
        builder.setTitle(R.string.clear_conversation_history);
        final View dialogView =
                requireActivity().getLayoutInflater().inflate(R.layout.dialog_clear_history, null);
        final CheckBox endConversationCheckBox =
                dialogView.findViewById(R.id.end_conversation_checkbox);
        builder.setView(dialogView);
        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.setPositiveButton(
                getString(R.string.confirm),
                (dialog, which) -> {
                    requireXmppActivity()
                            .xmppConnectionService
                            .clearConversationHistory(conversation);
                    if (endConversationCheckBox.isChecked()) {
                        requireXmppActivity()
                                .xmppConnectionService
                                .archiveConversation(conversation);
                        requireChatActivity().onConversationArchived(conversation);
                    } else {
                        requireChatActivity().onChatListItemUpdated();
                        refresh();
                    }
                });
        builder.create().show();
    }

    protected void muteConversationDialog(final Conversation conversation) {
        final MaterialAlertDialogBuilder builder =
                new MaterialAlertDialogBuilder(requireActivity());
        builder.setTitle(R.string.disable_notifications);
        final int[] durations = getResources().getIntArray(R.array.mute_options_durations);
        final CharSequence[] labels = new CharSequence[durations.length];
        for (int i = 0; i < durations.length; ++i) {
            if (durations[i] == -1) {
                labels[i] = getString(R.string.until_further_notice);
            } else {
                labels[i] = TimeFrameUtils.resolve(requireContext(), 1000L * durations[i]);
            }
        }
        builder.setItems(
                labels,
                (dialog, which) -> {
                    final long till;
                    if (durations[which] == -1) {
                        till = Long.MAX_VALUE;
                    } else {
                        till = System.currentTimeMillis() + (durations[which] * 1000L);
                    }
                    conversation.setMutedTill(till);
                    requireXmppActivity().xmppConnectionService.updateConversation(conversation);
                    requireChatActivity().onChatListItemUpdated();
                    refresh();
                    requireActivity().invalidateOptionsMenu();
                });
        builder.create().show();
    }

    private boolean hasPermissions(int requestCode, List<String> permissions) {
        final List<String> missingPermissions = new ArrayList<>();
        for (String permission : permissions) {
            if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                            || Config.ONLY_INTERNAL_STORAGE)
                    && permission.equals(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                continue;
            }
            if (requireActivity().checkSelfPermission(permission)
                    != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission);
            }
        }
        if (missingPermissions.isEmpty()) {
            return true;
        } else {
            requestPermissions(missingPermissions.toArray(new String[0]), requestCode);
            return false;
        }
    }

    private boolean hasPermissions(int requestCode, String... permissions) {
        return hasPermissions(requestCode, ImmutableList.copyOf(permissions));
    }

    public void unMuteConversation(final Conversation conversation) {
        conversation.setMutedTill(0);
        requireXmppActivity().xmppConnectionService.updateConversation(conversation);
        requireChatActivity().onChatListItemUpdated();
        refresh();
        requireActivity().invalidateOptionsMenu();
    }

    protected void invokeAttachFileIntent(final int attachmentChoice) {
        Intent intent = new Intent();
        boolean chooser = false;
        switch (attachmentChoice) {
            case ATTACHMENT_CHOICE_CHOOSE_IMAGE:
                intent.setAction(Intent.ACTION_GET_CONTENT);
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                intent.setType("image/*");
                chooser = true;
                break;
            case ATTACHMENT_CHOICE_RECORD_VIDEO:
                intent.setAction(MediaStore.ACTION_VIDEO_CAPTURE);
                break;
            case ATTACHMENT_CHOICE_TAKE_PHOTO:
                final Uri uri =
                        requireXmppActivity()
                                .xmppConnectionService
                                .getFileBackend()
                                .getTakePhotoUri();
                pendingTakePhotoUri.push(uri);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, uri);
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.setAction(MediaStore.ACTION_IMAGE_CAPTURE);
                break;
            case ATTACHMENT_CHOICE_CHOOSE_FILE:
                chooser = true;
                intent.setType("*/*");
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setAction(Intent.ACTION_GET_CONTENT);
                break;
            case ATTACHMENT_CHOICE_RECORD_VOICE:
                intent = new Intent(getActivity(), RecordingActivity.class);
                break;
            case ATTACHMENT_CHOICE_LOCATION:
                intent = GeoHelper.getFetchIntent(requireContext());
                break;
        }
        final Context context = getActivity();
        if (context == null) {
            return;
        }
        try {
            if (chooser) {
                startActivityForResult(
                        Intent.createChooser(intent, getString(R.string.perform_action_with)),
                        attachmentChoice);
            } else {
                startActivityForResult(intent, attachmentChoice);
            }
        } catch (final ActivityNotFoundException e) {
            Toast.makeText(context, R.string.no_application_found, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        binding.messagesView.post(this::fireReadEvent);
    }

    private void fireReadEvent() {
        final var c = this.conversation;
        if (c == null) {
            return;
        }
        final String uuid = getLastVisibleMessageUuid();
        if (uuid != null && getActivity() instanceof ChatActivity ca) {
            ca.onConversationRead(c, uuid);
        }
    }

    private String getLastVisibleMessageUuid() {
        if (binding == null) {
            return null;
        }
        synchronized (this.messageList) {
            int pos = binding.messagesView.getLastVisiblePosition();
            if (pos >= 0) {
                Message message = null;
                for (int i = pos; i >= 0; --i) {
                    try {
                        message = (Message) binding.messagesView.getItemAtPosition(i);
                    } catch (IndexOutOfBoundsException e) {
                        // should not happen if we synchronize properly. however if that fails we
                        // just gonna try item -1
                        continue;
                    }
                    if (message.getType() != Message.TYPE_STATUS) {
                        break;
                    }
                }
                if (message != null) {
                    return message.getUuid();
                }
            }
        }
        return null;
    }

    private void openWith(final Message message) {
        if (message.isGeoUri()) {
            GeoHelper.view(getActivity(), message);
        } else {
            final DownloadableFile file =
                    requireXmppActivity().xmppConnectionService.getFileBackend().getFile(message);
            ViewUtil.view(requireContext(), file);
        }
    }

    private void addReaction(final Message message) {
        requireXmppActivity()
                .addReaction(
                        message,
                        reactions -> {
                            if (requireXmppActivity()
                                    .xmppConnectionService
                                    .sendReactions(message, reactions)) {
                                return;
                            }
                            Toast.makeText(
                                            requireContext(),
                                            R.string.could_not_add_reaction,
                                            Toast.LENGTH_LONG)
                                    .show();
                        });
    }

    private void reportMessage(final Message message) {
        BlockContactDialog.show(
                requireXmppActivity(), conversation.getContact(), message.getServerMsgId());
    }

    private void showErrorMessage(final Message message) {
        final MaterialAlertDialogBuilder builder =
                new MaterialAlertDialogBuilder(requireActivity());
        builder.setTitle(R.string.error_message);
        final String errorMessage = message.getErrorMessage();
        final String[] errorMessageParts =
                errorMessage == null ? new String[0] : errorMessage.split("\\u001f");
        final String displayError;
        if (errorMessageParts.length == 2) {
            displayError = errorMessageParts[1];
        } else {
            displayError = errorMessage;
        }
        builder.setMessage(displayError);
        builder.setNegativeButton(
                R.string.copy_to_clipboard,
                (dialog, which) -> {
                    if (requireXmppActivity()
                                    .copyTextToClipboard(displayError, R.string.error_message)
                            && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        Toast.makeText(
                                        requireContext(),
                                        R.string.error_message_copied_to_clipboard,
                                        Toast.LENGTH_SHORT)
                                .show();
                    }
                });
        builder.setPositiveButton(R.string.confirm, null);
        builder.create().show();
    }

    private void deleteFile(final Message message) {
        final MaterialAlertDialogBuilder builder =
                new MaterialAlertDialogBuilder(requireActivity());
        builder.setNegativeButton(R.string.cancel, null);
        builder.setTitle(R.string.delete_file_dialog);
        builder.setMessage(R.string.delete_file_dialog_msg);
        builder.setPositiveButton(
                R.string.confirm,
                (dialog, which) -> {
                    if (requireXmppActivity()
                            .xmppConnectionService
                            .getFileBackend()
                            .deleteFile(message)) {
                        message.setDeleted(true);
                        requireXmppActivity().xmppConnectionService.evictPreview(message.getUuid());
                        requireXmppActivity().xmppConnectionService.updateMessage(message, false);
                        requireChatActivity().onChatListItemUpdated();
                        refresh();
                    }
                });
        builder.create().show();
    }

    private void moderate(final Message message) {
        final var manager =
                message.getConversation()
                        .getAccount()
                        .getXmppConnection()
                        .getManager(ModerationManager.class);
        final FutureCallback<Void> callback =
                new FutureCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {}

                    @Override
                    public void onFailure(@NonNull Throwable t) {
                        Log.d(Config.LOGTAG, "could not moderate message", t);
                        Toast.makeText(
                                        requireActivity(),
                                        R.string.could_not_moderate_message,
                                        Toast.LENGTH_LONG)
                                .show();
                    }
                };
        if (isAckedModerationDisclaimer()) {
            final var future = manager.moderate(message);
            Futures.addCallback(future, callback, ContextCompat.getMainExecutor(requireActivity()));
        } else {
            final DialogModerationBinding viewBinding =
                    DataBindingUtil.inflate(
                            requireActivity().getLayoutInflater(),
                            R.layout.dialog_moderation,
                            null,
                            false);
            final MaterialAlertDialogBuilder builder =
                    new MaterialAlertDialogBuilder(requireActivity());
            builder.setNegativeButton(R.string.cancel, null);
            builder.setView(viewBinding.getRoot());
            builder.setTitle(R.string.delete_message);
            builder.setPositiveButton(
                    R.string.confirm,
                    (dialog, which) -> {
                        if (viewBinding.doNotShowAgain.isChecked()) {
                            ackModeration = Instant.now().plus(Duration.ofMinutes(5));
                        }
                        final var future = manager.moderate(message);
                        Futures.addCallback(
                                future, callback, ContextCompat.getMainExecutor(requireActivity()));
                    });
            builder.create().show();
        }
    }

    private void resendMessage(final Message message, final boolean forceP2P) {
        if (message.isFileOrImage()) {
            if (!(message.getConversation() instanceof Conversation conversation)) {
                return;
            }
            final DownloadableFile file =
                    requireXmppActivity().xmppConnectionService.getFileBackend().getFile(message);
            if ((file.exists() && file.canRead()) || message.hasFileOnRemoteHost()) {
                final XmppConnection xmppConnection = conversation.getAccount().getXmppConnection();
                if (!message.hasFileOnRemoteHost()
                        && xmppConnection != null
                        && conversation.getMode() == Conversational.MODE_SINGLE
                        && (!xmppConnection
                                        .getManager(HttpUploadManager.class)
                                        .isAvailableForSize(message.getFileParams().getSize())
                                || forceP2P)) {
                    requireXmppActivity()
                            .selectPresence(
                                    conversation,
                                    () -> {
                                        message.setCounterpart(conversation.getNextCounterpart());
                                        requireXmppActivity()
                                                .xmppConnectionService
                                                .resendFailedMessages(message, forceP2P);
                                        new Handler()
                                                .post(
                                                        () -> {
                                                            int size = messageList.size();
                                                            this.binding.messagesView.setSelection(
                                                                    size - 1);
                                                        });
                                    });
                    return;
                }
            } else if (!Compatibility.hasStoragePermission(getActivity())) {
                Toast.makeText(requireContext(), R.string.no_storage_permission, Toast.LENGTH_SHORT)
                        .show();
                return;
            } else {
                Toast.makeText(requireContext(), R.string.file_deleted, Toast.LENGTH_SHORT).show();
                message.setDeleted(true);
                requireXmppActivity().xmppConnectionService.updateMessage(message, false);
                requireChatActivity().onChatListItemUpdated();
                refresh();
                return;
            }
        }
        requireXmppActivity().xmppConnectionService.resendFailedMessages(message, false);
        new Handler()
                .post(
                        () -> {
                            int size = messageList.size();
                            this.binding.messagesView.setSelection(size - 1);
                        });
    }

    private void cancelTransmission(Message message) {
        Transferable transferable = message.getTransferable();
        if (transferable != null) {
            transferable.cancel();
        } else if (message.getStatus() != Message.STATUS_RECEIVED) {
            requireXmppActivity()
                    .xmppConnectionService
                    .markMessage(
                            message, Message.STATUS_SEND_FAILED, Message.ERROR_MESSAGE_CANCELLED);
        }
    }

    private void retryDecryption(Message message) {
        message.setEncryption(Message.ENCRYPTION_PGP);
        requireChatActivity().onChatListItemUpdated();
        refresh();
        conversation.getAccount().getPgpDecryptionService().decrypt(message, false);
    }

    public void privateMessageWith(final Jid counterpart) {
        if (conversation.setOutgoingChatState(Config.DEFAULT_CHAT_STATE)) {
            requireXmppActivity().xmppConnectionService.sendChatState(conversation);
        }
        this.binding.textinput.setText("");
        this.conversation.setNextCounterpart(counterpart);
        updateChatMsgHint();
        updateSendButton();
        updateEditablity();
    }

    private void correctMessage(final Message message) {
        this.conversation.setCorrectingMessage(message);
        final Editable editable = binding.textinput.getText();
        this.conversation.setDraftMessage(editable.toString());
        this.binding.textinput.setText("");
        this.binding.textinput.append(message.getBody());
    }

    private void highlightInConference(String nick) {
        final Editable editable = this.binding.textinput.getText();
        String oldString = editable.toString().trim();
        final int pos = this.binding.textinput.getSelectionStart();
        if (oldString.isEmpty() || pos == 0) {
            editable.insert(0, nick + ": ");
        } else {
            final char before = editable.charAt(pos - 1);
            final char after = editable.length() > pos ? editable.charAt(pos) : '\0';
            if (before == '\n') {
                editable.insert(pos, nick + ": ");
            } else {
                if (pos > 2 && editable.subSequence(pos - 2, pos).toString().equals(": ")) {
                    if (NickValidityChecker.check(
                            conversation,
                            Arrays.asList(
                                    editable.subSequence(0, pos - 2).toString().split(", ")))) {
                        editable.insert(pos - 2, ", " + nick);
                        return;
                    }
                }
                editable.insert(
                        pos,
                        (Character.isWhitespace(before) ? "" : " ")
                                + nick
                                + (Character.isWhitespace(after) ? "" : " "));
                if (Character.isWhitespace(after)) {
                    this.binding.textinput.setSelection(
                            this.binding.textinput.getSelectionStart() + 1);
                }
            }
        }
    }

    @Override
    public void startActivityForResult(Intent intent, int requestCode) {
        requireChatActivity().clearPendingViewIntent();
        super.startActivityForResult(intent, requestCode);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (conversation != null) {
            outState.putString(STATE_CONVERSATION_UUID, conversation.getUuid());
            outState.putString(STATE_LAST_MESSAGE_UUID, lastMessageUuid);
            final Uri uri = pendingTakePhotoUri.peek();
            if (uri != null) {
                outState.putString(STATE_PHOTO_URI, uri.toString());
            }
            final ScrollState scrollState = getScrollPosition();
            if (scrollState != null) {
                outState.putParcelable(STATE_SCROLL_POSITION, scrollState);
            }
            final ArrayList<Attachment> attachments = mediaPreviewAdapter.getAttachments();
            if (!attachments.isEmpty()) {
                outState.putParcelableArrayList(STATE_MEDIA_PREVIEWS, attachments);
            }
        }
    }

    private void processSavedInstanceState(@NonNull final Bundle savedInstanceState) {
        final var uuid = savedInstanceState.getString(STATE_CONVERSATION_UUID);
        final ArrayList<Attachment> attachments =
                savedInstanceState.getParcelableArrayList(STATE_MEDIA_PREVIEWS);
        pendingLastMessageUuid.push(savedInstanceState.getString(STATE_LAST_MESSAGE_UUID, null));
        if (uuid == null) {
            return;
        }
        QuickLoader.set(uuid);
        this.pendingChatUuid.push(uuid);
        if (attachments != null && !attachments.isEmpty()) {
            this.pendingMediaPreviews.push(attachments);
        }
        String takePhotoUri = savedInstanceState.getString(STATE_PHOTO_URI);
        if (takePhotoUri != null) {
            pendingTakePhotoUri.push(Uri.parse(takePhotoUri));
        }
        pendingScrollState.push(savedInstanceState.getParcelable(STATE_SCROLL_POSITION));
    }

    @Override
    public void onStart() {
        super.onStart();
        if (this.reInitRequiredOnStart && this.conversation != null) {
            final Bundle extras = pendingExtras.pop();
            reInit(this.conversation, extras != null);
            if (extras != null) {
                processExtras(extras);
            }
        } else if (conversation == null && requireXmppActivity().xmppConnectionService != null) {
            final String uuid = pendingChatUuid.pop();
            Log.d(
                    Config.LOGTAG,
                    "ConversationFragment.onStart() - activity was bound but no conversation"
                            + " loaded. uuid="
                            + uuid);
            if (uuid != null) {
                findAndReInitByUuidOrArchive(uuid);
            }
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        final Activity activity = getActivity();
        messageListAdapter.unregisterListenerInAudioPlayer();
        if (activity == null || !activity.isChangingConfigurations()) {
            hideSoftKeyboard(activity);
            messageListAdapter.stopAudioPlayer();
        }
        if (this.conversation != null) {
            final String msg = this.binding.textinput.getText().toString();
            storeNextMessage(msg);
            updateChatState(this.conversation, msg);
            requireXmppActivity()
                    .xmppConnectionService
                    .getNotificationService()
                    .setOpenConversation(null);
        }
        this.reInitRequiredOnStart = true;
    }

    private void updateChatState(final Conversation conversation, final String msg) {
        ChatState state = msg.length() == 0 ? Config.DEFAULT_CHAT_STATE : ChatState.PAUSED;
        Account.State status = conversation.getAccount().getStatus();
        if (status == Account.State.ONLINE && conversation.setOutgoingChatState(state)) {
            requireXmppActivity().xmppConnectionService.sendChatState(conversation);
        }
    }

    private void saveMessageDraftStopAudioPlayer() {
        final Conversation previousConversation = this.conversation;
        if (this.binding == null || previousConversation == null) {
            return;
        }
        Log.d(Config.LOGTAG, "ConversationFragment.saveMessageDraftStopAudioPlayer()");
        final String msg = this.binding.textinput.getText().toString();
        storeNextMessage(msg);
        updateChatState(this.conversation, msg);
        messageListAdapter.stopAudioPlayer();
        mediaPreviewAdapter.clearPreviews();
        toggleInputMethod();
    }

    public void reInit(final Conversation conversation, final Bundle extras) {
        QuickLoader.set(conversation.getUuid());
        final boolean changedConversation = this.conversation != conversation;
        if (changedConversation) {
            this.saveMessageDraftStopAudioPlayer();
        }
        this.clearPending();
        if (this.reInit(conversation, extras != null)) {
            if (extras != null) {
                processExtras(extras);
            }
            this.reInitRequiredOnStart = false;
        } else {
            this.reInitRequiredOnStart = true;
            pendingExtras.push(extras);
        }
        resetUnreadMessagesCount();
    }

    private void reInit(Conversation conversation) {
        reInit(conversation, false);
    }

    private boolean reInit(final Conversation conversation, final boolean hasExtras) {
        if (conversation == null) {
            return false;
        }
        this.conversation = conversation;
        // once we set the conversation all is good and it will automatically do the right thing in
        // onStart()
        if (this.binding == null) {
            return false;
        }

        if (!requireXmppActivity()
                .xmppConnectionService
                .isConversationStillOpen(this.conversation)) {
            requireChatActivity().onConversationArchived(this.conversation);
            return false;
        }

        stopScrolling();
        Log.d(Config.LOGTAG, "reInit(hasExtras=" + hasExtras + ")");

        if (this.conversation.isRead() && hasExtras) {
            Log.d(Config.LOGTAG, "trimming conversation");
            this.conversation.trim();
        }

        setupIme();

        final boolean scrolledToBottomAndNoPending =
                this.scrolledToBottom() && pendingScrollState.peek() == null;

        this.binding.textSendButton.setContentDescription(
                requireContext().getString(R.string.send_message_to_x, conversation.getName()));
        this.binding.textinput.setKeyboardListener(null);
        final boolean participating =
                conversation.getMode() == Conversational.MODE_SINGLE
                        || conversation.getMucOptions().participating();
        if (participating) {
            this.binding.textinput.setText(this.conversation.getNextMessage());
            this.binding.textinput.setSelection(this.binding.textinput.length());
        } else {
            this.binding.textinput.setText(MessageUtils.EMPTY_STRING);
        }
        this.binding.textinput.setKeyboardListener(this);
        messageListAdapter.updatePreferences();
        refresh(false);
        requireXmppActivity().invalidateOptionsMenu();
        this.conversation.messagesLoaded.set(true);
        Log.d(Config.LOGTAG, "scrolledToBottomAndNoPending=" + scrolledToBottomAndNoPending);

        if (hasExtras || scrolledToBottomAndNoPending) {
            resetUnreadMessagesCount();
            synchronized (this.messageList) {
                Log.d(Config.LOGTAG, "jump to first unread message");
                final Message first = conversation.getFirstUnreadMessage();
                final int bottom = Math.max(0, this.messageList.size() - 1);
                final int pos;
                final boolean jumpToBottom;
                if (first == null) {
                    pos = bottom;
                    jumpToBottom = true;
                } else {
                    int i = getIndexOf(first.getUuid(), this.messageList);
                    pos = i < 0 ? bottom : i;
                    jumpToBottom = false;
                }
                setSelection(pos, jumpToBottom);
            }
        }

        this.binding.messagesView.post(this::fireReadEvent);
        // TODO if we only do this when this fragment is running on main it won't *bing* in tablet
        // layout which might be unnecessary since we can *see* it
        requireXmppActivity()
                .xmppConnectionService
                .getNotificationService()
                .setOpenConversation(this.conversation);
        return true;
    }

    private void resetUnreadMessagesCount() {
        lastMessageUuid = null;
        hideUnreadMessagesCount();
    }

    private void hideUnreadMessagesCount() {
        if (this.binding == null) {
            return;
        }
        this.binding.scrollToBottomButton.setEnabled(false);
        this.binding.scrollToBottomButton.hide();
        this.binding.unreadCountCustomView.setVisibility(View.GONE);
    }

    private void setSelection(int pos, boolean jumpToBottom) {
        ListViewUtils.setSelection(this.binding.messagesView, pos, jumpToBottom);
        this.binding.messagesView.post(
                () -> ListViewUtils.setSelection(this.binding.messagesView, pos, jumpToBottom));
        this.binding.messagesView.post(this::fireReadEvent);
    }

    private boolean scrolledToBottom() {
        return this.binding != null && scrolledToBottom(this.binding.messagesView);
    }

    private void processExtras(final Bundle extras) {
        final String downloadUuid = extras.getString(ChatActivity.EXTRA_DOWNLOAD_UUID);
        final String text = extras.getString(Intent.EXTRA_TEXT);
        final String nick = extras.getString(ChatActivity.EXTRA_NICK);
        final String postInitAction =
                extras.getString(ChatActivity.EXTRA_POST_INIT_ACTION);
        final boolean asQuote = extras.getBoolean(ChatActivity.EXTRA_AS_QUOTE);
        final boolean pm = extras.getBoolean(ChatActivity.EXTRA_IS_PRIVATE_MESSAGE, false);
        final boolean doNotAppend =
                extras.getBoolean(ChatActivity.EXTRA_DO_NOT_APPEND, false);
        final String type = extras.getString(ChatActivity.EXTRA_TYPE);
        final List<Uri> uris = extractUris(extras);
        if (uris != null && !uris.isEmpty()) {
            if (uris.size() == 1 && "geo".equals(uris.get(0).getScheme())) {
                mediaPreviewAdapter.addMediaPreviews(
                        Attachment.of(getActivity(), uris.get(0), Attachment.Type.LOCATION));
            } else {
                final List<Uri> cleanedUris = cleanUris(new ArrayList<>(uris));
                mediaPreviewAdapter.addMediaPreviews(
                        Attachment.of(getActivity(), cleanedUris, type));
            }
            toggleInputMethod();
            return;
        }
        if (nick != null) {
            if (pm) {
                Jid jid = conversation.getAddress();
                try {
                    Jid next = Jid.of(jid.getLocal(), jid.getDomain(), nick);
                    privateMessageWith(next);
                } catch (final IllegalArgumentException ignored) {
                    // do nothing
                }
            } else {
                final MucOptions mucOptions = conversation.getMucOptions();
                if (mucOptions.participating() || conversation.getNextCounterpart() != null) {
                    highlightInConference(nick);
                }
            }
        } else {
            if (text != null && Patterns.URI_GEO.matcher(text).matches()) {
                mediaPreviewAdapter.addMediaPreviews(
                        Attachment.of(getActivity(), Uri.parse(text), Attachment.Type.LOCATION));
                toggleInputMethod();
                return;
            } else if (text != null && asQuote) {
                quoteText(text);
            } else {
                appendText(text, doNotAppend);
            }
        }
        if (ChatActivity.POST_ACTION_RECORD_VOICE.equals(postInitAction)) {
            attachFile(ATTACHMENT_CHOICE_RECORD_VOICE, false);
            return;
        }
        final Message message =
                downloadUuid == null ? null : conversation.findMessageWithFileAndUuid(downloadUuid);
        if (message != null) {
            startDownloadable(message);
        }
    }

    private List<Uri> extractUris(final Bundle extras) {
        final List<Uri> uris = extras.getParcelableArrayList(Intent.EXTRA_STREAM);
        if (uris != null) {
            return uris;
        }
        final Uri uri = extras.getParcelable(Intent.EXTRA_STREAM);
        if (uri != null) {
            return Collections.singletonList(uri);
        } else {
            return null;
        }
    }

    private List<Uri> cleanUris(final List<Uri> uris) {
        final Iterator<Uri> iterator = uris.iterator();
        while (iterator.hasNext()) {
            final Uri uri = iterator.next();
            if (FileBackend.dangerousFile(uri)) {
                iterator.remove();
                Toast.makeText(
                                requireActivity(),
                                R.string.security_violation_not_attaching_file,
                                Toast.LENGTH_SHORT)
                        .show();
            }
        }
        return uris;
    }

    private boolean showBlockSubmenu(View view) {
        final Jid jid = conversation.getAddress();
        final boolean showReject =
                !conversation.isWithStranger()
                        && conversation
                                .getContact()
                                .getOption(Contact.Options.PENDING_SUBSCRIPTION_REQUEST);
        PopupMenu popupMenu = new PopupMenu(getActivity(), view);
        popupMenu.inflate(R.menu.block);
        popupMenu.getMenu().findItem(R.id.block_contact).setVisible(jid.getLocal() != null);
        popupMenu.getMenu().findItem(R.id.reject).setVisible(showReject);
        popupMenu.setOnMenuItemClickListener(
                menuItem -> {
                    Blockable blockable;
                    switch (menuItem.getItemId()) {
                        case R.id.reject:
                            requireXmppActivity()
                                    .xmppConnectionService
                                    .stopPresenceUpdatesTo(conversation.getContact());
                            updateSnackBar(conversation);
                            return true;
                        case R.id.block_domain:
                            blockable =
                                    conversation
                                            .getAccount()
                                            .getRoster()
                                            .getContact(jid.getDomain());
                            break;
                        default:
                            blockable = conversation;
                    }
                    BlockContactDialog.show(requireXmppActivity(), blockable);
                    return true;
                });
        popupMenu.show();
        return true;
    }

    private void updateSnackBar(final Conversation conversation) {
        final Account account = conversation.getAccount();
        final XmppConnection connection = account.getXmppConnection();
        final int mode = conversation.getMode();
        final Contact contact = mode == Conversation.MODE_SINGLE ? conversation.getContact() : null;
        if (conversation.getStatus() == Conversation.STATUS_ARCHIVED) {
            return;
        }
        if (account.getStatus() == Account.State.DISABLED) {
            showSnackbar(
                    R.string.this_account_is_disabled,
                    R.string.enable,
                    this.mEnableAccountListener);
        } else if (account.getStatus() == Account.State.LOGGED_OUT) {
            showSnackbar(
                    R.string.this_account_is_logged_out,
                    R.string.log_in,
                    this.mEnableAccountListener);
        } else if (conversation.isBlocked()) {
            showSnackbar(R.string.contact_blocked, R.string.unblock, this.mUnblockClickListener);
        } else if (contact != null
                && !contact.showInRoster()
                && contact.getOption(Contact.Options.PENDING_SUBSCRIPTION_REQUEST)) {
            showSnackbar(
                    R.string.contact_added_you,
                    R.string.add_back,
                    this.mAddBackClickListener,
                    this.mLongPressBlockListener);
        } else if (contact != null
                && contact.getOption(Contact.Options.PENDING_SUBSCRIPTION_REQUEST)) {
            showSnackbar(
                    R.string.contact_asks_for_presence_subscription,
                    R.string.allow,
                    this.mAllowPresenceSubscription,
                    this.mLongPressBlockListener);
        } else if (mode == Conversation.MODE_MULTI
                && !conversation.getMucOptions().online()
                && account.getStatus() == Account.State.ONLINE) {
            switch (conversation.getMucOptions().getError()) {
                case NICK_IN_USE:
                    showSnackbar(R.string.nick_in_use, R.string.edit, clickToMuc);
                    break;
                case NO_RESPONSE:
                    showSnackbar(R.string.joining_conference, 0, null);
                    break;
                case SERVER_NOT_FOUND:
                    if (conversation.receivedMessagesCount() > 0) {
                        showSnackbar(R.string.remote_server_not_found, R.string.try_again, joinMuc);
                    } else {
                        showSnackbar(R.string.remote_server_not_found, R.string.leave, leaveMuc);
                    }
                    break;
                case REMOTE_SERVER_TIMEOUT:
                    if (conversation.receivedMessagesCount() > 0) {
                        showSnackbar(R.string.remote_server_timeout, R.string.try_again, joinMuc);
                    } else {
                        showSnackbar(R.string.remote_server_timeout, R.string.leave, leaveMuc);
                    }
                    break;
                case PASSWORD_REQUIRED:
                    showSnackbar(
                            R.string.conference_requires_password,
                            R.string.enter_password,
                            enterPassword);
                    break;
                case BANNED:
                    showSnackbar(R.string.conference_banned, R.string.leave, leaveMuc);
                    break;
                case MEMBERS_ONLY:
                    showSnackbar(R.string.conference_members_only, R.string.leave, leaveMuc);
                    break;
                case RESOURCE_CONSTRAINT:
                    showSnackbar(
                            R.string.conference_resource_constraint, R.string.try_again, joinMuc);
                    break;
                case KICKED:
                    showSnackbar(R.string.conference_kicked, R.string.join, joinMuc);
                    break;
                case TECHNICAL_PROBLEMS:
                    showSnackbar(
                            R.string.conference_technical_problems, R.string.try_again, joinMuc);
                    break;
                case UNKNOWN:
                    showSnackbar(R.string.conference_unknown_error, R.string.try_again, joinMuc);
                    break;
                case INVALID_NICK:
                    showSnackbar(R.string.invalid_muc_nick, R.string.edit, clickToMuc);
                case SHUTDOWN:
                    showSnackbar(R.string.conference_shutdown, R.string.try_again, joinMuc);
                    break;
                case DESTROYED:
                    showSnackbar(R.string.conference_destroyed, R.string.leave, leaveMuc);
                    break;
                case NON_ANONYMOUS:
                    showSnackbar(
                            R.string.group_chat_will_make_your_jabber_id_public,
                            R.string.join,
                            acceptJoin);
                    break;
                default:
                    hideSnackbar();
                    break;
            }
        } else if (account.hasPendingPgpIntent(conversation)) {
            showSnackbar(R.string.openpgp_messages_found, R.string.decrypt, clickToDecryptListener);
        } else if (connection != null
                && connection.getManager(BlockingManager.class).hasFeature()
                && conversation.countMessages() != 0
                && !conversation.isBlocked()
                && conversation.isWithStranger()) {
            showSnackbar(
                    R.string.received_message_from_stranger, R.string.block, mBlockClickListener);
        } else {
            hideSnackbar();
        }
    }

    @Override
    public void refresh() {
        if (this.binding == null) {
            Log.d(
                    Config.LOGTAG,
                    "ConversationFragment.refresh() skipped updated because view binding was null");
            return;
        }
        if (this.conversation != null && requireXmppActivity().xmppConnectionService != null) {
            if (!requireXmppActivity()
                    .xmppConnectionService
                    .isConversationStillOpen(this.conversation)) {
                requireChatActivity().onConversationArchived(this.conversation);
                return;
            }
        }
        this.refresh(true);
    }

    private void refresh(boolean notifyConversationRead) {
        synchronized (this.messageList) {
            if (this.conversation != null) {
                conversation.populateWithMessages(this.messageList);
                updateSnackBar(conversation);
                updateStatusMessages();
                if (conversation.getReceivedMessagesCountSinceUuid(lastMessageUuid) != 0) {
                    binding.unreadCountCustomView.setVisibility(View.VISIBLE);
                    binding.unreadCountCustomView.setUnreadCount(
                            conversation.getReceivedMessagesCountSinceUuid(lastMessageUuid));
                }
                this.messageListAdapter.notifyDataSetChanged();
                updateChatMsgHint();
                if (notifyConversationRead) {
                    binding.messagesView.post(this::fireReadEvent);
                }
                updateSendButton();
                updateEditablity();
            }
        }
    }

    protected void messageSent() {
        mSendingPgpMessage.set(false);
        this.binding.textinput.setText("");
        if (conversation.setCorrectingMessage(null)) {
            this.binding.textinput.append(conversation.getDraftMessage());
            conversation.setDraftMessage(null);
        }
        storeNextMessage();
        updateChatMsgHint();
        SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(requireContext());
        final boolean prefScrollToBottom =
                p.getBoolean(
                        "scroll_to_bottom",
                        requireContext().getResources().getBoolean(R.bool.scroll_to_bottom));
        if (prefScrollToBottom || scrolledToBottom()) {
            new Handler()
                    .post(
                            () -> {
                                int size = messageList.size();
                                this.binding.messagesView.setSelection(size - 1);
                            });
        }
    }

    private boolean storeNextMessage() {
        return storeNextMessage(this.binding.textinput.getText().toString());
    }

    private boolean storeNextMessage(String msg) {
        final boolean participating =
                conversation.getMode() == Conversational.MODE_SINGLE
                        || conversation.getMucOptions().participating();
        if (this.conversation.getStatus() != Conversation.STATUS_ARCHIVED
                && participating
                && this.conversation.setNextMessage(msg)) {
            requireXmppActivity().xmppConnectionService.updateConversation(this.conversation);
            return true;
        }
        return false;
    }

    public void doneSendingPgpMessage() {
        mSendingPgpMessage.set(false);
    }

    public Long getMaxHttpUploadSize(final Conversation conversation) {

        final var connection = conversation.getAccount().getXmppConnection();
        final var httpUploadService = connection.getManager(HttpUploadManager.class).getService();
        if (httpUploadService == null) {
            return -1L;
        }
        return httpUploadService.getMaxFileSize();
    }

    private void updateEditablity() {
        boolean canWrite =
                this.conversation.getMode() == Conversation.MODE_SINGLE
                        || this.conversation.getMucOptions().participating()
                        || this.conversation.getNextCounterpart() != null;
        this.binding.textinput.setFocusable(canWrite);
        this.binding.textinput.setFocusableInTouchMode(canWrite);
        this.binding.textSendButton.setEnabled(canWrite);
        this.binding.textinput.setCursorVisible(canWrite);
        this.binding.textinput.setEnabled(canWrite);
    }

    public void updateSendButton() {
        boolean hasAttachments =
                mediaPreviewAdapter != null && mediaPreviewAdapter.hasAttachments();
        final Conversation c = this.conversation;
        final var connection = c.getAccount().getXmppConnection();
        final Presence.Availability status;
        final String text = CharSequenceUtils.nullToEmpty(this.binding.textinput.getText());
        final SendButtonAction action;
        if (hasAttachments) {
            action = SendButtonAction.TEXT;
        } else {
            action = SendButtonTool.getAction(getActivity(), c, text);
        }
        if (c.getAccount().getStatus() == Account.State.ONLINE) {
            if (connection.getManager(MessageArchiveManager.class).isCatchingUp(c)) {
                status = Presence.Availability.OFFLINE;
            } else if (c.getMode() == Conversation.MODE_SINGLE) {
                status = c.getContact().getShownStatus();
            } else {
                status =
                        c.getMucOptions().online()
                                ? Presence.Availability.ONLINE
                                : Presence.Availability.OFFLINE;
            }
        } else {
            status = Presence.Availability.OFFLINE;
        }
        this.binding.textSendButton.setTag(action);
        this.binding.textSendButton.setIconResource(
                SendButtonTool.getSendButtonImageResource(action));
        this.binding.textSendButton.setIconTint(
                ColorStateList.valueOf(
                        SendButtonTool.getSendButtonColor(this.binding.textSendButton, status)));
        // TODO send button color
        final Activity activity = getActivity();
        if (activity != null) {}
    }

    protected void updateStatusMessages() {
        DateSeparator.addAll(this.messageList);
        if (showLoadMoreMessages(conversation)) {
            this.messageList.add(0, Message.createLoadMoreMessage(conversation));
        }
        if (conversation.getMode() == Conversation.MODE_SINGLE) {
            ChatState state = conversation.getIncomingChatState();
            if (state == ChatState.COMPOSING) {
                this.messageList.add(
                        Message.createStatusMessage(
                                conversation,
                                getString(R.string.contact_is_typing, conversation.getName())));
            } else if (state == ChatState.PAUSED) {
                this.messageList.add(
                        Message.createStatusMessage(
                                conversation,
                                getString(
                                        R.string.contact_has_stopped_typing,
                                        conversation.getName())));
            } else {
                for (int i = this.messageList.size() - 1; i >= 0; --i) {
                    final Message message = this.messageList.get(i);
                    if (message.getType() != Message.TYPE_STATUS) {
                        if (message.getStatus() == Message.STATUS_RECEIVED) {
                            return;
                        } else {
                            if (message.getStatus() == Message.STATUS_SEND_DISPLAYED) {
                                this.messageList.add(
                                        i + 1,
                                        Message.createStatusMessage(
                                                conversation,
                                                getString(
                                                        R.string.contact_has_read_up_to_this_point,
                                                        conversation.getName())));
                                return;
                            }
                        }
                    }
                }
            }
        } else {
            final MucOptions mucOptions = conversation.getMucOptions();
            final List<MucOptions.User> allUsers = mucOptions.getUsers();
            final Set<ReadByMarker> addedMarkers = new HashSet<>();
            ChatState state = ChatState.COMPOSING;
            List<MucOptions.User> users =
                    conversation.getMucOptions().getUsersWithChatState(state, 5);
            if (users.isEmpty()) {
                state = ChatState.PAUSED;
                users = conversation.getMucOptions().getUsersWithChatState(state, 5);
            }
            if (mucOptions.isPrivateAndNonAnonymous()) {
                for (int i = this.messageList.size() - 1; i >= 0; --i) {
                    final Set<ReadByMarker> markersForMessage =
                            messageList.get(i).getReadByMarkers();
                    final List<MucOptions.User> shownMarkers = new ArrayList<>();
                    for (ReadByMarker marker : markersForMessage) {
                        if (!ReadByMarker.contains(marker, addedMarkers)) {
                            addedMarkers.add(
                                    marker); // may be put outside this condition. set should do
                            // dedup anyway
                            MucOptions.User user = mucOptions.getUser(marker);
                            if (user != null && !users.contains(user)) {
                                shownMarkers.add(user);
                            }
                        }
                    }
                    final ReadByMarker markerForSender = ReadByMarker.from(messageList.get(i));
                    final Message statusMessage;
                    final int size = shownMarkers.size();
                    if (size > 1) {
                        final String body;
                        if (size <= 4) {
                            body =
                                    getString(
                                            R.string.contacts_have_read_up_to_this_point,
                                            UIHelper.concatNames(shownMarkers));
                        } else if (ReadByMarker.allUsersRepresented(
                                allUsers, markersForMessage, markerForSender)) {
                            body = getString(R.string.everyone_has_read_up_to_this_point);
                        } else {
                            body =
                                    getString(
                                            R.string.contacts_and_n_more_have_read_up_to_this_point,
                                            UIHelper.concatNames(shownMarkers, 3),
                                            size - 3);
                        }
                        statusMessage = Message.createStatusMessage(conversation, body);
                        statusMessage.setCounterparts(shownMarkers);
                    } else if (size == 1) {
                        statusMessage =
                                Message.createStatusMessage(
                                        conversation,
                                        getString(
                                                R.string.contact_has_read_up_to_this_point,
                                                shownMarkers.get(0).getDisplayName()));
                        statusMessage.setCounterpart(shownMarkers.get(0).getFullJid());
                        statusMessage.setTrueCounterpart(shownMarkers.get(0).getRealJid());
                    } else {
                        statusMessage = null;
                    }
                    if (statusMessage != null) {
                        this.messageList.add(i + 1, statusMessage);
                    }
                    addedMarkers.add(markerForSender);
                    if (ReadByMarker.allUsersRepresented(allUsers, addedMarkers)) {
                        break;
                    }
                }
            }
            if (users.isEmpty()) {
                return;
            }
            Message statusMessage;
            if (users.size() == 1) {
                MucOptions.User user = users.get(0);
                int id =
                        state == ChatState.COMPOSING
                                ? R.string.contact_is_typing
                                : R.string.contact_has_stopped_typing;
                statusMessage =
                        Message.createStatusMessage(
                                conversation, getString(id, user.getDisplayName()));
                statusMessage.setTrueCounterpart(user.getRealJid());
                statusMessage.setCounterpart(user.getFullJid());
            } else {
                int id =
                        state == ChatState.COMPOSING
                                ? R.string.contacts_are_typing
                                : R.string.contacts_have_stopped_typing;
                statusMessage =
                        Message.createStatusMessage(
                                conversation, getString(id, UIHelper.concatNames(users)));
                statusMessage.setCounterparts(users);
            }
            this.messageList.add(statusMessage);
        }
    }

    private void stopScrolling() {
        long now = SystemClock.uptimeMillis();
        MotionEvent cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0, 0, 0);
        binding.messagesView.dispatchTouchEvent(cancel);
    }

    private boolean showLoadMoreMessages(final Conversation c) {
        if (requireXmppActivity().xmppConnectionService == null) {
            return false;
        }
        final var connection = c.getAccount().getXmppConnection();
        final boolean mam = hasMamSupport(c) && !c.getContact().isBlocked();
        final MessageArchiveManager service = connection.getManager(MessageArchiveManager.class);
        return mam
                && (c.getLastClearHistory().getTimestamp() != 0
                        || (c.countMessages() == 0
                                && c.messagesLoaded.get()
                                && c.hasMessagesLeftOnServer()
                                && !service.queryInProgress(c)));
    }

    private boolean hasMamSupport(final Conversation c) {
        if (c.getMode() == Conversation.MODE_SINGLE) {
            final var connection = c.getAccount().getXmppConnection();
            return connection.getManager(MessageArchiveManager.class).hasFeature();
        } else {
            return c.getMucOptions().mamSupport();
        }
    }

    protected void showSnackbar(
            final int message, final int action, final OnClickListener clickListener) {
        showSnackbar(message, action, clickListener, null);
    }

    protected void showSnackbar(
            final int message,
            final int action,
            final OnClickListener clickListener,
            final View.OnLongClickListener longClickListener) {
        this.binding.snackbar.setVisibility(View.VISIBLE);
        this.binding.snackbar.setOnClickListener(null);
        this.binding.snackbarMessage.setText(message);
        this.binding.snackbarMessage.setOnClickListener(null);
        this.binding.snackbarAction.setVisibility(clickListener == null ? View.GONE : View.VISIBLE);
        if (action != 0) {
            this.binding.snackbarAction.setText(action);
        }
        this.binding.snackbarAction.setOnClickListener(clickListener);
        this.binding.snackbarAction.setOnLongClickListener(longClickListener);
    }

    protected void hideSnackbar() {
        this.binding.snackbar.setVisibility(View.GONE);
    }

    protected void sendMessage(final Message message) {
        requireXmppActivity().xmppConnectionService.sendMessage(message);
        messageSent();
    }

    protected void sendPgpMessage(final Message message) {
        final XmppConnectionService xmppService = requireXmppActivity().xmppConnectionService;
        final Contact contact = message.getConversation().getContact();
        if (!requireXmppActivity().hasPgp()) {
            requireXmppActivity().showInstallPgpDialog();
            return;
        }
        if (conversation.getAccount().getPgpSignature() == null) {
            requireXmppActivity()
                    .announcePgp(
                            conversation.getAccount(),
                            conversation,
                            null,
                            requireXmppActivity().onOpenPGPKeyPublished);
            return;
        }
        if (!mSendingPgpMessage.compareAndSet(false, true)) {
            Log.d(Config.LOGTAG, "sending pgp message already in progress");
        }
        if (conversation.getMode() == Conversation.MODE_SINGLE) {
            if (contact.getPgpKeyId() != 0) {
                final var future = xmppService.getPgpEngine().hasKey(contact);
                Futures.addCallback(
                        future,
                        new FutureCallback<>() {
                            @Override
                            public void onSuccess(final Boolean result) {
                                encryptTextMessage(message);
                            }

                            @Override
                            public void onFailure(@NonNull Throwable t) {
                                if (t instanceof PgpEngine.UserInputRequiredException e) {
                                    startPendingIntent(
                                            e.getPendingIntent(), REQUEST_ENCRYPT_MESSAGE);
                                } else {
                                    final var message = t.getMessage();
                                    if (Strings.isNullOrEmpty(message)) {
                                        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG)
                                                .show();
                                    }
                                    mSendingPgpMessage.set(false);
                                }
                            }
                        },
                        ContextCompat.getMainExecutor(requireContext()));
            } else {
                showNoPGPKeyDialog(
                        false,
                        (dialog, which) -> {
                            conversation.setNextEncryption(Message.ENCRYPTION_NONE);
                            xmppService.updateConversation(conversation);
                            message.setEncryption(Message.ENCRYPTION_NONE);
                            xmppService.sendMessage(message);
                            messageSent();
                        });
            }
        } else {
            if (conversation.getMucOptions().pgpKeysInUse()) {
                if (conversation.getMucOptions().missingPgpKeys()) {
                    Toast warning =
                            Toast.makeText(
                                    getActivity(), R.string.missing_public_keys, Toast.LENGTH_LONG);
                    warning.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
                    warning.show();
                }
                encryptTextMessage(message);
            } else {
                showNoPGPKeyDialog(
                        true,
                        (dialog, which) -> {
                            conversation.setNextEncryption(Message.ENCRYPTION_NONE);
                            message.setEncryption(Message.ENCRYPTION_NONE);
                            xmppService.updateConversation(conversation);
                            xmppService.sendMessage(message);
                            messageSent();
                        });
            }
        }
    }

    public void encryptTextMessage(final Message message) {
        final var future =
                requireXmppActivity().xmppConnectionService.encryptIfNeededAndSend(message);
        Futures.addCallback(
                future,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(Void result) {
                        messageSent();
                    }

                    @Override
                    public void onFailure(@NonNull Throwable t) {
                        if (t instanceof PgpEngine.UserInputRequiredException e) {
                            startPendingIntent(e.getPendingIntent(), REQUEST_SEND_MESSAGE);
                        } else {
                            final String errorMessage = t.getMessage();
                            if (Strings.isNullOrEmpty(errorMessage)) {
                                Toast.makeText(
                                                requireContext(),
                                                R.string.unable_to_connect_to_keychain,
                                                Toast.LENGTH_LONG)
                                        .show();
                            } else {
                                Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG)
                                        .show();
                            }
                            doneSendingPgpMessage();
                        }
                    }
                },
                ContextCompat.getMainExecutor(requireContext()));
    }

    public void showNoPGPKeyDialog(
            final boolean plural, final DialogInterface.OnClickListener listener) {
        final MaterialAlertDialogBuilder builder =
                new MaterialAlertDialogBuilder(requireActivity());
        if (plural) {
            builder.setTitle(getString(R.string.no_pgp_keys));
            builder.setMessage(getText(R.string.contacts_have_no_pgp_keys));
        } else {
            builder.setTitle(getString(R.string.no_pgp_key));
            builder.setMessage(getText(R.string.contact_has_no_pgp_key));
        }
        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.setPositiveButton(getString(R.string.send_unencrypted), listener);
        builder.create().show();
    }

    public void appendText(String text, final boolean doNotAppend) {
        if (text == null) {
            return;
        }
        final Editable editable = this.binding.textinput.getText();
        String previous = editable == null ? "" : editable.toString();
        if (doNotAppend && !TextUtils.isEmpty(previous)) {
            Toast.makeText(getActivity(), R.string.already_drafting_message, Toast.LENGTH_LONG)
                    .show();
            return;
        }
        if (UIHelper.isLastLineQuote(previous)) {
            text = '\n' + text;
        } else if (previous.length() != 0
                && !Character.isWhitespace(previous.charAt(previous.length() - 1))) {
            text = " " + text;
        }
        this.binding.textinput.append(text);
    }

    @Override
    public boolean onEnterPressed(final boolean isCtrlPressed) {
        if (isCtrlPressed || enterIsSend()) {
            sendMessage();
            return true;
        }
        return false;
    }

    private boolean enterIsSend() {
        final SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(getActivity());
        return p.getBoolean("enter_is_send", getResources().getBoolean(R.bool.enter_is_send));
    }

    public boolean onArrowUpCtrlPressed() {
        final Message lastEditableMessage =
                conversation == null ? null : conversation.getLastEditableMessage();
        if (lastEditableMessage != null) {
            correctMessage(lastEditableMessage);
            return true;
        } else {
            Toast.makeText(getActivity(), R.string.could_not_correct_message, Toast.LENGTH_LONG)
                    .show();
            return false;
        }
    }

    @Override
    public void onTypingStarted() {
        final var service = getXmppConnectionService();
        if (service == null) {
            return;
        }
        final Account.State status = conversation.getAccount().getStatus();
        if (status == Account.State.ONLINE
                && conversation.setOutgoingChatState(ChatState.COMPOSING)) {
            service.sendChatState(conversation);
        }
        runOnUiThread(this::updateSendButton);
    }

    @Override
    public void onTypingStopped() {
        final var service = getXmppConnectionService();
        if (service == null) {
            return;
        }
        final Account.State status = conversation.getAccount().getStatus();
        if (status == Account.State.ONLINE && conversation.setOutgoingChatState(ChatState.PAUSED)) {
            service.sendChatState(conversation);
        }
    }

    @Override
    public void onTextDeleted() {
        final var service = getXmppConnectionService();
        if (service == null) {
            return;
        }
        final Account.State status = conversation.getAccount().getStatus();
        if (status == Account.State.ONLINE
                && conversation.setOutgoingChatState(Config.DEFAULT_CHAT_STATE)) {
            service.sendChatState(conversation);
        }
        if (storeNextMessage()) {
            runOnUiThread(() -> requireChatActivity().onChatListItemUpdated());
        }
        runOnUiThread(this::updateSendButton);
    }

    @Override
    public void onTextChanged() {
        if (conversation != null && conversation.getCorrectingMessage() != null) {
            runOnUiThread(this::updateSendButton);
        }
    }

    @Override
    public boolean onTabPressed(boolean repeated) {
        if (conversation == null || conversation.getMode() == Conversation.MODE_SINGLE) {
            return false;
        }
        if (repeated) {
            completionIndex++;
        } else {
            lastCompletionLength = 0;
            completionIndex = 0;
            final String content = this.binding.textinput.getText().toString();
            lastCompletionCursor = this.binding.textinput.getSelectionEnd();
            int start =
                    lastCompletionCursor > 0
                            ? content.lastIndexOf(" ", lastCompletionCursor - 1) + 1
                            : 0;
            firstWord = start == 0;
            incomplete = content.substring(start, lastCompletionCursor);
        }
        List<String> completions = new ArrayList<>();
        for (MucOptions.User user : conversation.getMucOptions().getUsers()) {
            String name = user.resource();
            if (name != null && name.startsWith(incomplete)) {
                completions.add(name + (firstWord ? ": " : " "));
            }
        }
        Collections.sort(completions);
        if (completions.size() > completionIndex) {
            String completion = completions.get(completionIndex).substring(incomplete.length());
            this.binding
                    .textinput
                    .getEditableText()
                    .delete(lastCompletionCursor, lastCompletionCursor + lastCompletionLength);
            this.binding.textinput.getEditableText().insert(lastCompletionCursor, completion);
            lastCompletionLength = completion.length();
        } else {
            completionIndex = -1;
            this.binding
                    .textinput
                    .getEditableText()
                    .delete(lastCompletionCursor, lastCompletionCursor + lastCompletionLength);
            lastCompletionLength = 0;
        }
        return true;
    }

    private void startPendingIntent(PendingIntent pendingIntent, int requestCode) {
        try {
            getActivity()
                    .startIntentSenderForResult(
                            pendingIntent.getIntentSender(),
                            requestCode,
                            null,
                            0,
                            0,
                            0,
                            Compatibility.pgpStartIntentSenderOptions());
        } catch (final SendIntentException ignored) {
        }
    }

    @Override
    public void onBackendConnected() {
        Log.d(Config.LOGTAG, "ConversationFragment.onBackendConnected()");
        String uuid = pendingChatUuid.pop();
        if (uuid != null) {
            if (!findAndReInitByUuidOrArchive(uuid)) {
                return;
            }
        } else {
            if (!requireXmppActivity()
                    .xmppConnectionService
                    .isConversationStillOpen(conversation)) {
                clearPending();
                requireChatActivity().onConversationArchived(conversation);
                return;
            }
        }
        ActivityResult activityResult = postponedActivityResult.pop();
        if (activityResult != null) {
            handleActivityResult(activityResult);
        }
        clearPending();
    }

    private boolean findAndReInitByUuidOrArchive(@NonNull final String uuid) {
        Conversation conversation =
                requireXmppActivity().xmppConnectionService.findConversationByUuid(uuid);
        if (conversation == null) {
            clearPending();
            requireChatActivity().onConversationArchived(null);
            return false;
        }
        reInit(conversation);
        ScrollState scrollState = pendingScrollState.pop();
        String lastMessageUuid = pendingLastMessageUuid.pop();
        List<Attachment> attachments = pendingMediaPreviews.pop();
        if (scrollState != null) {
            setScrollPosition(scrollState, lastMessageUuid);
        }
        if (attachments != null && attachments.size() > 0) {
            Log.d(Config.LOGTAG, "had attachments on restore");
            mediaPreviewAdapter.addMediaPreviews(attachments);
            toggleInputMethod();
        }
        return true;
    }

    private void clearPending() {
        if (postponedActivityResult.clear()) {
            Log.e(Config.LOGTAG, "cleared pending intent with unhandled result left");
            if (pendingTakePhotoUri.clear()) {
                Log.e(Config.LOGTAG, "cleared pending photo uri");
            }
        }
        if (pendingScrollState.clear()) {
            Log.e(Config.LOGTAG, "cleared scroll state");
        }
        if (pendingChatUuid.clear()) {
            Log.e(Config.LOGTAG, "cleared pending chat uuid");
        }
        if (pendingMediaPreviews.clear()) {
            Log.e(Config.LOGTAG, "cleared pending media previews");
        }
    }

    public Conversation getConversation() {
        return conversation;
    }

    @Override
    public void onContactPictureLongClicked(View v, final Message message) {
        final String fingerprint;
        if (message.getEncryption() == Message.ENCRYPTION_PGP
                || message.getEncryption() == Message.ENCRYPTION_DECRYPTED) {
            fingerprint = "pgp";
        } else {
            fingerprint = message.getFingerprint();
        }
        final PopupMenu popupMenu = new PopupMenu(getActivity(), v);
        final Contact contact = message.getContact();
        if (message.getStatus() <= Message.STATUS_RECEIVED
                && (contact == null || !contact.isSelf())) {
            if (message.getConversation().getMode() == Conversation.MODE_MULTI) {
                final var user = conversation.getMucOptions().getUserOrStub(message);
                popupMenu.inflate(R.menu.muc_details_context);
                final Menu menu = popupMenu.getMenu();
                MucDetailsContextMenuHelper.configureMucDetailsContextMenu(
                        requireActivity(), menu, conversation, user);
                popupMenu.setOnMenuItemClickListener(
                        menuItem ->
                                MucDetailsContextMenuHelper.onContextItemSelected(
                                        menuItem, user, requireXmppActivity(), fingerprint));
            } else {
                popupMenu.inflate(R.menu.one_on_one_context);
                popupMenu.setOnMenuItemClickListener(
                        item -> {
                            switch (item.getItemId()) {
                                case R.id.action_contact_details:
                                    requireXmppActivity()
                                            .switchToContactDetails(
                                                    message.getContact(), fingerprint);
                                    break;
                                case R.id.action_show_qr_code:
                                    requireXmppActivity()
                                            .showQrCode(
                                                    "xmpp:"
                                                            + message.getContact()
                                                                    .getAddress()
                                                                    .asBareJid()
                                                                    .toString());
                                    break;
                            }
                            return true;
                        });
            }
        } else {
            popupMenu.inflate(R.menu.account_context);
            final Menu menu = popupMenu.getMenu();
            menu.findItem(R.id.action_manage_accounts)
                    .setVisible(QuickChatService.isChat());
            popupMenu.setOnMenuItemClickListener(
                    item -> {
                        switch (item.getItemId()) {
                            case R.id.action_show_qr_code:
                                requireXmppActivity()
                                        .showQrCode(conversation.getAccount().getShareableUri());
                                break;
                            case R.id.action_account_details:
                                requireXmppActivity()
                                        .switchToAccount(
                                                message.getConversation().getAccount(),
                                                fingerprint);
                                break;
                            case R.id.action_manage_accounts:
                                AccountUtils.launchManageAccounts(requireActivity());
                                break;
                        }
                        return true;
                    });
        }
        popupMenu.show();
    }

    @Override
    public void onContactPictureClicked(Message message) {
        String fingerprint;
        if (message.getEncryption() == Message.ENCRYPTION_PGP
                || message.getEncryption() == Message.ENCRYPTION_DECRYPTED) {
            fingerprint = "pgp";
        } else {
            fingerprint = message.getFingerprint();
        }
        final boolean received = message.getStatus() <= Message.STATUS_RECEIVED;
        if (received) {
            if (message.getConversation() instanceof Conversation c
                    && message.getConversation().getMode() == Conversation.MODE_MULTI) {
                final var mucOptions = c.getMucOptions();
                if (mucOptions.participating()) {
                    final var user = mucOptions.getUserOrStub(message);
                    if (user.resource() != null) {
                        if (user instanceof MucOptions.Stub) {
                            Toast.makeText(
                                            requireActivity(),
                                            requireActivity()
                                                    .getString(
                                                            R.string.user_has_left_conference,
                                                            user.getDisplayName()),
                                            Toast.LENGTH_SHORT)
                                    .show();
                        }
                        highlightInConference(user.resource());
                    } else {
                        final var counterpart = message.getCounterpart();
                        if (counterpart != null && counterpart.isFullJid()) {
                            highlightInConference(counterpart.getResource());
                        }
                    }
                } else {
                    Toast.makeText(
                                    requireActivity(),
                                    R.string.you_are_not_participating,
                                    Toast.LENGTH_SHORT)
                            .show();
                }
                return;
            } else {
                if (!message.getContact().isSelf()) {
                    requireXmppActivity().switchToContactDetails(message.getContact(), fingerprint);
                    return;
                }
            }
        }
        requireXmppActivity().switchToAccount(message.getConversation().getAccount(), fingerprint);
    }

    private ChatActivity requireChatActivity() {
        if (getActivity() instanceof ChatActivity ca) {
            return ca;
        }
        throw new IllegalStateException("Fragment not attached to ChatActivity");
    }
}
