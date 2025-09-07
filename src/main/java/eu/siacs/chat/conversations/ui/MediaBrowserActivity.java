package tech.ravensoftware.chat.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.databinding.DataBindingUtil;
import tech.ravensoftware.chat.R;
import tech.ravensoftware.chat.databinding.ActivityMediaBrowserBinding;
import tech.ravensoftware.chat.entities.Account;
import tech.ravensoftware.chat.entities.Contact;
import tech.ravensoftware.chat.entities.Conversation;
import tech.ravensoftware.chat.ui.adapter.MediaAdapter;
import tech.ravensoftware.chat.ui.interfaces.OnMediaLoaded;
import tech.ravensoftware.chat.ui.util.Attachment;
import tech.ravensoftware.chat.ui.util.GridManager;
import tech.ravensoftware.chat.xmpp.Jid;
import java.util.List;

public class MediaBrowserActivity extends XmppActivity implements OnMediaLoaded {

    private ActivityMediaBrowserBinding binding;

    private MediaAdapter mMediaAdapter;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.binding = DataBindingUtil.setContentView(this, R.layout.activity_media_browser);
        Activities.setStatusAndNavigationBarColors(this, binding.getRoot());
        setSupportActionBar(binding.toolbar);
        configureActionBar(getSupportActionBar());
        mMediaAdapter = new MediaAdapter(this, R.dimen.media_size);
        this.binding.media.setAdapter(mMediaAdapter);
        GridManager.setupLayoutManager(this, this.binding.media, R.dimen.browser_media_size);
    }

    @Override
    protected void refreshUiReal() {}

    @Override
    protected void onBackendConnected() {
        Intent intent = getIntent();
        String account = intent == null ? null : intent.getStringExtra("account");
        String jid = intent == null ? null : intent.getStringExtra("jid");
        if (account != null && jid != null) {
            xmppConnectionService.getAttachments(account, Jid.of(jid), 0, this);
        }
    }

    public static void launch(Context context, Contact contact) {
        launch(context, contact.getAccount(), contact.getAddress().asBareJid().toString());
    }

    public static void launch(Context context, Conversation conversation) {
        launch(
                context,
                conversation.getAccount(),
                conversation.getAddress().asBareJid().toString());
    }

    private static void launch(Context context, Account account, String jid) {
        final Intent intent = new Intent(context, MediaBrowserActivity.class);
        intent.putExtra("account", account.getUuid());
        intent.putExtra("jid", jid);
        context.startActivity(intent);
    }

    @Override
    public void onMediaLoaded(List<Attachment> attachments) {
        runOnUiThread(
                () -> {
                    mMediaAdapter.setAttachments(attachments);
                });
    }
}
