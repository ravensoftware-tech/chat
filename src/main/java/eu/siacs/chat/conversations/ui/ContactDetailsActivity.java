package tech.ravensoftware.chat.ui;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Intents;
import android.provider.Settings;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.databinding.DataBindingUtil;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import tech.ravensoftware.chat.AppSettings;
import tech.ravensoftware.chat.Config;
import tech.ravensoftware.chat.R;
import tech.ravensoftware.chat.crypto.axolotl.AxolotlService;
import tech.ravensoftware.chat.crypto.axolotl.FingerprintStatus;
import tech.ravensoftware.chat.crypto.axolotl.XmppAxolotlSession;
import tech.ravensoftware.chat.databinding.ActivityContactDetailsBinding;
import tech.ravensoftware.chat.entities.Account;
import tech.ravensoftware.chat.entities.Contact;
import tech.ravensoftware.chat.entities.ListItem;
import tech.ravensoftware.chat.services.AbstractQuickChatService;
import tech.ravensoftware.chat.services.QuickChatService;
import tech.ravensoftware.chat.services.XmppConnectionService.OnAccountUpdate;
import tech.ravensoftware.chat.services.XmppConnectionService.OnRosterUpdate;
import tech.ravensoftware.chat.ui.adapter.MediaAdapter;
import tech.ravensoftware.chat.ui.interfaces.OnMediaLoaded;
import tech.ravensoftware.chat.ui.util.Attachment;
import tech.ravensoftware.chat.ui.util.AvatarWorkerTask;
import tech.ravensoftware.chat.ui.util.GridManager;
import tech.ravensoftware.chat.ui.util.JidDialog;
import tech.ravensoftware.chat.ui.util.MenuDoubleTabUtil;
import tech.ravensoftware.chat.utils.AccountUtils;
import tech.ravensoftware.chat.utils.Compatibility;
import tech.ravensoftware.chat.utils.Emoticons;
import tech.ravensoftware.chat.utils.IrregularUnicodeDetector;
import tech.ravensoftware.chat.utils.PhoneNumberUtilWrapper;
import tech.ravensoftware.chat.utils.UIHelper;
import tech.ravensoftware.chat.utils.XEP0392Helper;
import tech.ravensoftware.chat.utils.XmppUri;
import tech.ravensoftware.chat.xmpp.Jid;
import tech.ravensoftware.chat.xmpp.OnKeyStatusUpdated;
import tech.ravensoftware.chat.xmpp.OnUpdateBlocklist;
import tech.ravensoftware.chat.xmpp.XmppConnection;
import tech.ravensoftware.chat.xmpp.manager.BlockingManager;
import tech.ravensoftware.chat.xmpp.manager.PresenceManager;
import tech.ravensoftware.chat.xmpp.manager.RosterManager;
import im.conversations.android.xmpp.model.stanza.Presence;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.openintents.openpgp.util.OpenPgpUtils;

public class ContactDetailsActivity extends OmemoActivity
        implements OnAccountUpdate,
                OnRosterUpdate,
                OnUpdateBlocklist,
                OnKeyStatusUpdated,
                OnMediaLoaded {
    public static final String ACTION_VIEW_CONTACT = "view_contact";
    private final int REQUEST_SYNC_CONTACTS = 0x28cf;
    ActivityContactDetailsBinding binding;
    private MediaAdapter mMediaAdapter;

    private Contact contact;
    private final DialogInterface.OnClickListener removeFromRoster =
            new DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface dialog, int which) {
                    xmppConnectionService.deleteContactOnServer(contact);
                }
            };
    private final OnCheckedChangeListener mOnSendCheckedChange =
            new OnCheckedChangeListener() {

                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (isChecked) {
                        if (contact.getOption(Contact.Options.PENDING_SUBSCRIPTION_REQUEST)) {
                            xmppConnectionService.stopPresenceUpdatesTo(contact);
                        } else {
                            contact.setOption(Contact.Options.PREEMPTIVE_GRANT);
                        }
                    } else {
                        contact.resetOption(Contact.Options.PREEMPTIVE_GRANT);
                        final var connection = contact.getAccount().getXmppConnection();
                        connection
                                .getManager(PresenceManager.class)
                                .unsubscribed(contact.getAddress().asBareJid());
                    }
                }
            };
    private final OnCheckedChangeListener mOnReceiveCheckedChange =
            new OnCheckedChangeListener() {

                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    final var connection = contact.getAccount().getXmppConnection();
                    if (isChecked) {
                        connection
                                .getManager(PresenceManager.class)
                                .subscribe(contact.getAddress().asBareJid());
                    } else {
                        connection
                                .getManager(PresenceManager.class)
                                .unsubscribe(contact.getAddress().asBareJid());
                    }
                }
            };
    private Jid accountJid;
    private Jid contactJid;
    private boolean showDynamicTags = false;
    private boolean showLastSeen = false;
    private boolean showInactiveOmemo = false;
    private String messageFingerprint;

    private void checkContactPermissionAndShowAddDialog() {
        if (hasContactsPermission()) {
            showAddToPhoneBookDialog();
        } else if (QuickChatService.isContactListIntegration(this)) {
            requestPermissions(
                    new String[] {Manifest.permission.READ_CONTACTS}, REQUEST_SYNC_CONTACTS);
        }
    }

    private boolean hasContactsPermission() {
        if (QuickChatService.isContactListIntegration(this)) {
            return checkSelfPermission(Manifest.permission.READ_CONTACTS)
                    == PackageManager.PERMISSION_GRANTED;
        } else {
            return true;
        }
    }

    private void showAddToPhoneBookDialog() {
        final Jid jid = contact.getAddress();
        final boolean quicksyContact =
                AbstractQuickChatService.isQuicksy()
                        && Config.QUICKSY_DOMAIN.equals(jid.getDomain())
                        && jid.getLocal() != null;
        final String value;
        if (quicksyContact) {
            value = PhoneNumberUtilWrapper.toFormattedPhoneNumber(this, jid);
        } else {
            value = jid.toString();
        }
        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle(getString(R.string.save_to_contact));
        builder.setMessage(getString(R.string.add_phone_book_text, value));
        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.setPositiveButton(
                getString(R.string.add),
                (dialog, which) -> {
                    final Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
                    intent.setType(Contacts.CONTENT_ITEM_TYPE);
                    if (quicksyContact) {
                        intent.putExtra(Intents.Insert.PHONE, value);
                    } else {
                        intent.putExtra(Intents.Insert.IM_HANDLE, value);
                        intent.putExtra(
                                Intents.Insert.IM_PROTOCOL, CommonDataKinds.Im.PROTOCOL_JABBER);
                        // TODO for modern use we want PROTOCOL_CUSTOM and an extra field with a
                        // value of 'XMPP'
                        // however we don’t have such a field and thus have to use the legacy
                        // PROTOCOL_JABBER
                    }
                    intent.putExtra("finishActivityOnSaveCompleted", true);
                    try {
                        startActivityForResult(intent, 0);
                    } catch (ActivityNotFoundException e) {
                        Toast.makeText(
                                        ContactDetailsActivity.this,
                                        R.string.no_application_found_to_view_contact,
                                        Toast.LENGTH_SHORT)
                                .show();
                    }
                });
        builder.create().show();
    }

    @Override
    public void onRosterUpdate() {
        refreshUi();
    }

    @Override
    public void onAccountUpdate() {
        refreshUi();
    }

    @Override
    public void OnUpdateBlocklist(final Status status) {
        refreshUi();
    }

    @Override
    protected void refreshUiReal() {
        invalidateOptionsMenu();
        populateView();
    }

    @Override
    protected String getShareableUri(boolean http) {
        if (http) {
            return "https://conversations.im/i/"
                    + XmppUri.lameUrlEncode(contact.getAddress().asBareJid().toString());
        } else {
            return "xmpp:" + contact.getAddress().asBareJid().toString();
        }
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showInactiveOmemo =
                savedInstanceState != null
                        && savedInstanceState.getBoolean("show_inactive_omemo", false);
        if (getIntent().getAction().equals(ACTION_VIEW_CONTACT)) {
            try {
                this.accountJid = Jid.of(getIntent().getExtras().getString(EXTRA_ACCOUNT));
            } catch (final IllegalArgumentException ignored) {
            }
            try {
                this.contactJid = Jid.of(getIntent().getExtras().getString("contact"));
            } catch (final IllegalArgumentException ignored) {
            }
        }
        this.messageFingerprint = getIntent().getStringExtra("fingerprint");
        this.binding = DataBindingUtil.setContentView(this, R.layout.activity_contact_details);
        Activities.setStatusAndNavigationBarColors(this, binding.getRoot());

        setSupportActionBar(binding.toolbar);
        configureActionBar(getSupportActionBar());
        binding.showInactiveDevices.setOnClickListener(
                v -> {
                    showInactiveOmemo = !showInactiveOmemo;
                    populateView();
                });
        binding.addContactButton.setOnClickListener(v -> showAddToRosterDialog(contact));

        mMediaAdapter = new MediaAdapter(this, R.dimen.media_size);
        this.binding.media.setAdapter(mMediaAdapter);
        GridManager.setupLayoutManager(this, this.binding.media, R.dimen.media_size);
    }

    @Override
    public void onSaveInstanceState(final Bundle savedInstanceState) {
        savedInstanceState.putBoolean("show_inactive_omemo", showInactiveOmemo);
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public void onStart() {
        super.onStart();
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        this.showDynamicTags = preferences.getBoolean(AppSettings.SHOW_DYNAMIC_TAGS, false);
        this.showLastSeen = preferences.getBoolean("last_activity", false);
        binding.mediaWrapper.setVisibility(
                Compatibility.hasStoragePermission(this) ? View.VISIBLE : View.GONE);
        mMediaAdapter.setAttachments(Collections.emptyList());
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        // TODO check for Camera / Scan permission
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length == 0) {
            return;
        }
        if (requestCode == REQUEST_SYNC_CONTACTS && xmppConnectionServiceBound) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showAddToPhoneBookDialog();
                xmppConnectionService.loadPhoneContacts();
                xmppConnectionService.startContactObserver();
            } else {
                showRedirectToAppSettings();
            }
        }
    }

    private void showRedirectToAppSettings() {
        final var dialogBuilder = new MaterialAlertDialogBuilder(this);
        dialogBuilder.setTitle(R.string.save_to_contact);
        dialogBuilder.setMessage(
                getString(R.string.no_contacts_permission, getString(R.string.app_name)));
        dialogBuilder.setPositiveButton(
                R.string.continue_btn,
                (d, w) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivity(intent);
                });
        dialogBuilder.setNegativeButton(R.string.cancel, null);
        dialogBuilder.create().show();
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem menuItem) {
        if (MenuDoubleTabUtil.shouldIgnoreTap()) {
            return false;
        }
        switch (menuItem.getItemId()) {
            case android.R.id.home:
                finish();
                break;
            case R.id.action_share_http:
                shareLink(true);
                break;
            case R.id.action_share_uri:
                shareLink(false);
                break;
            case R.id.action_delete_contact:
                final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
                builder.setNegativeButton(getString(R.string.cancel), null);
                builder.setTitle(getString(R.string.action_delete_contact))
                        .setMessage(
                                JidDialog.style(
                                        this,
                                        R.string.remove_contact_text,
                                        contact.getAddress().toString()))
                        .setPositiveButton(getString(R.string.delete), removeFromRoster)
                        .create()
                        .show();
                break;
            case R.id.action_edit_contact:
                final Uri systemAccount = contact.getSystemAccount();
                if (systemAccount == null) {
                    quickEdit(
                            contact.getServerName(),
                            R.string.contact_name,
                            value -> {
                                contact.setServerName(value);
                                final var connection = contact.getAccount().getXmppConnection();
                                connection
                                        .getManager(RosterManager.class)
                                        .addRosterItem(contact, null);
                                populateView();
                                return null;
                            },
                            true);
                } else {
                    Intent intent = new Intent(Intent.ACTION_EDIT);
                    intent.setDataAndType(systemAccount, Contacts.CONTENT_ITEM_TYPE);
                    intent.putExtra("finishActivityOnSaveCompleted", true);
                    try {
                        startActivity(intent);
                    } catch (ActivityNotFoundException e) {
                        Toast.makeText(
                                        ContactDetailsActivity.this,
                                        R.string.no_application_found_to_view_contact,
                                        Toast.LENGTH_SHORT)
                                .show();
                    }
                }
                break;
            case R.id.action_block, R.id.action_unblock:
                BlockContactDialog.show(this, contact);
                break;
            case R.id.action_custom_notifications:
                configureCustomNotifications(contact);
                break;
        }
        return super.onOptionsItemSelected(menuItem);
    }

    private void configureCustomNotifications(final Contact contact) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return;
        }
        final var shortcut = xmppConnectionService.getShortcutService().getShortcutInfo(contact);
        configureCustomNotification(shortcut);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.contact_details, menu);
        AccountUtils.showHideMenuItems(menu);
        final MenuItem block = menu.findItem(R.id.action_block);
        final MenuItem unblock = menu.findItem(R.id.action_unblock);
        final MenuItem edit = menu.findItem(R.id.action_edit_contact);
        final MenuItem delete = menu.findItem(R.id.action_delete_contact);
        final MenuItem customNotifications = menu.findItem(R.id.action_custom_notifications);
        customNotifications.setVisible(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R);
        if (contact == null) {
            return true;
        }
        final XmppConnection connection = contact.getAccount().getXmppConnection();
        if (connection != null && connection.getManager(BlockingManager.class).hasFeature()) {
            if (this.contact.isBlocked()) {
                block.setVisible(false);
            } else {
                unblock.setVisible(false);
            }
        } else {
            unblock.setVisible(false);
            block.setVisible(false);
        }
        if (!contact.showInRoster()) {
            edit.setVisible(false);
            delete.setVisible(false);
        }
        return super.onCreateOptionsMenu(menu);
    }

    private void populateView() {
        if (contact == null) {
            return;
        }
        invalidateOptionsMenu();
        setTitle(contact.getDisplayName());
        if (contact.showInRoster()) {
            binding.detailsSendPresence.setVisibility(View.VISIBLE);
            binding.detailsReceivePresence.setVisibility(View.VISIBLE);
            binding.addContactButton.setVisibility(View.GONE);
            binding.detailsSendPresence.setOnCheckedChangeListener(null);
            binding.detailsReceivePresence.setOnCheckedChangeListener(null);

            Collection<String> statusMessages =
                    ImmutableSet.copyOf(
                            Collections2.filter(
                                    Lists.transform(contact.getPresences(), Presence::getStatus),
                                    s -> !Strings.isNullOrEmpty(s)));
            if (statusMessages.isEmpty()) {
                binding.statusMessage.setVisibility(View.GONE);
            } else if (statusMessages.size() == 1) {
                final String message = Iterables.getOnlyElement(statusMessages);
                binding.statusMessage.setVisibility(View.VISIBLE);
                final Spannable span = new SpannableString(message);
                if (Emoticons.isOnlyEmoji(message)) {
                    span.setSpan(
                            new RelativeSizeSpan(2.0f),
                            0,
                            message.length(),
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                binding.statusMessage.setText(span);
            } else {
                binding.statusMessage.setText(Joiner.on('\n').join(statusMessages));
            }

            if (contact.getOption(Contact.Options.FROM)) {
                binding.detailsSendPresence.setText(R.string.send_presence_updates);
                binding.detailsSendPresence.setChecked(true);
            } else if (contact.getOption(Contact.Options.PENDING_SUBSCRIPTION_REQUEST)) {
                binding.detailsSendPresence.setChecked(false);
                binding.detailsSendPresence.setText(R.string.send_presence_updates);
            } else {
                binding.detailsSendPresence.setText(R.string.preemptively_grant);
                binding.detailsSendPresence.setChecked(
                        contact.getOption(Contact.Options.PREEMPTIVE_GRANT));
            }
            if (contact.getOption(Contact.Options.TO)) {
                binding.detailsReceivePresence.setText(R.string.receive_presence_updates);
                binding.detailsReceivePresence.setChecked(true);
            } else {
                binding.detailsReceivePresence.setText(R.string.ask_for_presence_updates);
                binding.detailsReceivePresence.setChecked(
                        contact.getOption(Contact.Options.ASKING));
            }
            if (contact.getAccount().isOnlineAndConnected()) {
                binding.detailsReceivePresence.setEnabled(true);
                binding.detailsSendPresence.setEnabled(true);
            } else {
                binding.detailsReceivePresence.setEnabled(false);
                binding.detailsSendPresence.setEnabled(false);
            }
            binding.detailsSendPresence.setOnCheckedChangeListener(this.mOnSendCheckedChange);
            binding.detailsReceivePresence.setOnCheckedChangeListener(this.mOnReceiveCheckedChange);
        } else {
            binding.addContactButton.setVisibility(View.VISIBLE);
            binding.detailsSendPresence.setVisibility(View.GONE);
            binding.detailsReceivePresence.setVisibility(View.GONE);
            binding.statusMessage.setVisibility(View.GONE);
        }

        if (contact.isBlocked() && !this.showDynamicTags) {
            binding.detailsLastSeen.setVisibility(View.VISIBLE);
            binding.detailsLastSeen.setText(R.string.contact_blocked);
        } else {
            binding.detailsLastSeen.setVisibility(View.GONE);
        }

        binding.detailsContactXmppAddress.setText(
                IrregularUnicodeDetector.style(this, contact.getAddress()));
        final String account = contact.getAccount().getJid().asBareJid().toString();
        binding.detailsAccount.setOnClickListener(this::onDetailsAccountClicked);
        binding.detailsAccount.setText(getString(R.string.using_account, account));
        AvatarWorkerTask.loadAvatar(contact, binding.detailsAvatar, R.dimen.publish_avatar_size);
        binding.detailsAvatar.setOnClickListener(this::onAvatarClicked);
        if (QuickChatService.isContactListIntegration(this)) {
            if (contact.getSystemAccount() == null) {
                binding.addAddressBook.setText(R.string.save_to_contact);
            } else {
                binding.addAddressBook.setText(R.string.show_in_contacts);
            }
            binding.addAddressBook.setVisibility(View.VISIBLE);
            binding.addAddressBook.setOnClickListener(this::onAddToAddressBookClick);
        } else {
            binding.addAddressBook.setVisibility(View.GONE);
        }

        binding.detailsContactKeys.removeAllViews();
        boolean hasKeys = false;
        final LayoutInflater inflater = getLayoutInflater();
        final AxolotlService axolotlService = contact.getAccount().getAxolotlService();
        if (Config.supportOmemo() && axolotlService != null) {
            final Collection<XmppAxolotlSession> sessions =
                    axolotlService.findSessionsForContact(contact);
            boolean anyActive = false;
            for (XmppAxolotlSession session : sessions) {
                anyActive = session.getTrust().isActive();
                if (anyActive) {
                    break;
                }
            }
            boolean skippedInactive = false;
            boolean showsInactive = false;
            boolean showUnverifiedWarning = false;
            for (final XmppAxolotlSession session : sessions) {
                final FingerprintStatus trust = session.getTrust();
                hasKeys |= !trust.isCompromised();
                if (!trust.isActive() && anyActive) {
                    if (showInactiveOmemo) {
                        showsInactive = true;
                    } else {
                        skippedInactive = true;
                        continue;
                    }
                }
                if (!trust.isCompromised()) {
                    boolean highlight = session.getFingerprint().equals(messageFingerprint);
                    addFingerprintRow(binding.detailsContactKeys, session, highlight);
                }
                if (trust.isUnverified()) {
                    showUnverifiedWarning = true;
                }
            }
            binding.unverifiedWarning.setVisibility(
                    showUnverifiedWarning ? View.VISIBLE : View.GONE);
            if (showsInactive || skippedInactive) {
                binding.showInactiveDevices.setText(
                        showsInactive
                                ? R.string.hide_inactive_devices
                                : R.string.show_inactive_devices);
                binding.showInactiveDevices.setVisibility(View.VISIBLE);
            } else {
                binding.showInactiveDevices.setVisibility(View.GONE);
            }
        } else {
            binding.showInactiveDevices.setVisibility(View.GONE);
        }
        final boolean isCameraFeatureAvailable = isCameraFeatureAvailable();
        binding.scanButton.setVisibility(
                hasKeys && isCameraFeatureAvailable ? View.VISIBLE : View.GONE);
        if (hasKeys) {
            binding.scanButton.setOnClickListener((v) -> ScanActivity.scan(this));
        }
        if (Config.supportOpenPgp() && contact.getPgpKeyId() != 0) {
            hasKeys = true;
            View view =
                    inflater.inflate(
                            R.layout.item_device_fingerprint, binding.detailsContactKeys, false);
            TextView key = view.findViewById(R.id.key);
            TextView keyType = view.findViewById(R.id.key_type);
            keyType.setText(R.string.openpgp_key_id);
            if ("pgp".equals(messageFingerprint)) {
                keyType.setTextColor(
                        MaterialColors.getColor(
                                keyType, com.google.android.material.R.attr.colorPrimaryVariant));
            }
            key.setText(OpenPgpUtils.convertKeyIdToHex(contact.getPgpKeyId()));
            final OnClickListener openKey = v -> launchOpenKeyChain(contact.getPgpKeyId());
            view.setOnClickListener(openKey);
            key.setOnClickListener(openKey);
            keyType.setOnClickListener(openKey);
            binding.detailsContactKeys.addView(view);
        }
        binding.keysWrapper.setVisibility(hasKeys ? View.VISIBLE : View.GONE);

        final var tagList = contact.getTags();
        final boolean hasMetaTags =
                contact.isBlocked() || contact.getShownStatus() != Presence.Availability.OFFLINE;
        if ((tagList.isEmpty() && !hasMetaTags) || !this.showDynamicTags) {
            binding.tags.setVisibility(View.GONE);
        } else {
            binding.tags.setVisibility(View.VISIBLE);
            binding.tags.removeViews(1, binding.tags.getChildCount() - 1);
            final ImmutableList.Builder<Integer> viewIdBuilder = new ImmutableList.Builder<>();
            for (final ListItem.Tag tag : tagList) {
                final String name = tag.getName();
                final TextView tv =
                        (TextView) inflater.inflate(R.layout.item_tag, binding.tags, false);
                tv.setText(name);
                tv.setBackgroundTintList(
                        ColorStateList.valueOf(
                                MaterialColors.harmonizeWithPrimary(
                                        this, XEP0392Helper.rgbFromNick(name))));
                final int id = ViewCompat.generateViewId();
                tv.setId(id);
                viewIdBuilder.add(id);
                binding.tags.addView(tv);
            }
            if (contact.isBlocked()) {
                final TextView tv =
                        (TextView) inflater.inflate(R.layout.item_tag, binding.tags, false);
                tv.setText(R.string.blocked);
                tv.setBackgroundTintList(
                        ColorStateList.valueOf(
                                MaterialColors.harmonizeWithPrimary(
                                        tv.getContext(),
                                        ContextCompat.getColor(
                                                tv.getContext(), R.color.gray_800))));
                final int id = ViewCompat.generateViewId();
                tv.setId(id);
                viewIdBuilder.add(id);
                binding.tags.addView(tv);
            } else {
                final Presence.Availability status = contact.getShownStatus();
                if (status != Presence.Availability.OFFLINE) {
                    final TextView tv =
                            (TextView) inflater.inflate(R.layout.item_tag, binding.tags, false);
                    UIHelper.setStatus(tv, status);
                    final int id = ViewCompat.generateViewId();
                    tv.setId(id);
                    viewIdBuilder.add(id);
                    binding.tags.addView(tv);
                }
            }
            binding.flowWidget.setReferencedIds(Ints.toArray(viewIdBuilder.build()));
        }
    }

    private void onDetailsAccountClicked(final View view) {
        final var contact = this.contact;
        if (contact == null) {
            return;
        }
        switchToAccount(contact.getAccount());
    }

    private void onAvatarClicked(final View view) {
        final var contact = this.contact;
        if (contact == null) {
            return;
        }
        final var avatar = contact.getAvatar();
        if (avatar == null) {
            return;
        }
        final var intent = new Intent(this, ViewProfilePictureActivity.class);
        intent.setData(Uri.fromParts("avatar", avatar, null));
        intent.putExtra(ViewProfilePictureActivity.EXTRA_DISPLAY_NAME, contact.getDisplayName());
        startActivity(intent);
    }

    private void onAddToAddressBookClick(final View view) {
        if (QuickChatService.isContactListIntegration(this)) {
            final Uri systemAccount = contact.getSystemAccount();
            if (systemAccount == null) {
                checkContactPermissionAndShowAddDialog();
            } else {
                final Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(systemAccount);
                try {
                    startActivity(intent);
                } catch (final ActivityNotFoundException e) {
                    Toast.makeText(
                                    this,
                                    R.string.no_application_found_to_view_contact,
                                    Toast.LENGTH_SHORT)
                            .show();
                }
            }
        } else {
            Toast.makeText(
                            this,
                            R.string.contact_list_integration_not_available,
                            Toast.LENGTH_SHORT)
                    .show();
        }
    }

    public void onBackendConnected() {
        if (accountJid != null && contactJid != null) {
            Account account = xmppConnectionService.findAccountByJid(accountJid);
            if (account == null) {
                return;
            }
            this.contact = account.getRoster().getContact(contactJid);
            if (mPendingFingerprintVerificationUri != null) {
                processFingerprintVerification(mPendingFingerprintVerificationUri);
                mPendingFingerprintVerificationUri = null;
            }

            if (Compatibility.hasStoragePermission(this)) {
                final int limit = GridManager.getCurrentColumnCount(this.binding.media);
                xmppConnectionService.getAttachments(
                        account, contact.getAddress().asBareJid(), limit, this);
                this.binding.showMedia.setOnClickListener(
                        (v) -> MediaBrowserActivity.launch(this, contact));
            }
            populateView();
        }
    }

    @Override
    public void onKeyStatusUpdated(AxolotlService.FetchStatus report) {
        refreshUi();
    }

    @Override
    protected void processFingerprintVerification(XmppUri uri) {
        if (contact != null
                && contact.getAddress().asBareJid().equals(uri.getJid())
                && uri.hasFingerprints()) {
            if (xmppConnectionService.verifyFingerprints(contact, uri.getFingerprints())) {
                Toast.makeText(this, R.string.verified_fingerprints, Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, R.string.invalid_barcode, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onMediaLoaded(List<Attachment> attachments) {
        runOnUiThread(
                () -> {
                    int limit = GridManager.getCurrentColumnCount(binding.media);
                    mMediaAdapter.setAttachments(
                            attachments.subList(0, Math.min(limit, attachments.size())));
                    binding.mediaWrapper.setVisibility(
                            attachments.size() > 0 ? View.VISIBLE : View.GONE);
                });
    }
}
