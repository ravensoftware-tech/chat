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

package tech.ravensoftware.chat.ui;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.StringRes;
import androidx.databinding.DataBindingUtil;
import com.canhub.cropper.CropImageContract;
import com.canhub.cropper.CropImageContractOptions;
import tech.ravensoftware.chat.Config;
import tech.ravensoftware.chat.R;
import tech.ravensoftware.chat.databinding.ActivityPublishProfilePictureBinding;
import tech.ravensoftware.chat.entities.Conversation;
import tech.ravensoftware.chat.ui.interfaces.OnAvatarPublication;
import tech.ravensoftware.chat.ui.util.PendingItem;

public class PublishGroupChatProfilePictureActivity extends XmppActivity
        implements OnAvatarPublication {
    private final PendingItem<String> pendingConversationUuid = new PendingItem<>();
    private ActivityPublishProfilePictureBinding binding;
    private Conversation conversation;
    private Uri uri;

    final ActivityResultLauncher<CropImageContractOptions> cropImage =
            registerForActivityResult(
                    new CropImageContract(),
                    cropResult -> {
                        if (cropResult.isSuccessful()) {
                            onAvatarPicked(cropResult.getUriContent());
                        }
                    });

    @Override
    protected void refreshUiReal() {}

    @Override
    protected void onBackendConnected() {
        String uuid = pendingConversationUuid.pop();
        if (uuid != null) {
            this.conversation = xmppConnectionService.findConversationByUuid(uuid);
        }
        if (this.conversation == null) {
            return;
        }
        reloadAvatar();
    }

    private void reloadAvatar() {
        final int size = (int) getResources().getDimension(R.dimen.publish_avatar_size);
        final Bitmap bitmap;
        if (uri == null) {
            bitmap = xmppConnectionService.getAvatarService().get(conversation, size);
        } else {
            Log.d(Config.LOGTAG, "loading " + uri + " into preview");
            bitmap = xmppConnectionService.getFileBackend().cropCenterSquare(uri, size);
        }
        this.binding.accountImage.setImageBitmap(bitmap);
        this.binding.publishButton.setEnabled(uri != null);
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.binding =
                DataBindingUtil.setContentView(this, R.layout.activity_publish_profile_picture);
        this.binding.contactOnly.setVisibility(View.GONE);
        Activities.setStatusAndNavigationBarColors(this, binding.getRoot());
        setSupportActionBar(this.binding.toolbar);
        configureActionBar(getSupportActionBar());
        this.binding.cancelButton.setOnClickListener((v) -> this.finish());
        this.binding.secondaryHint.setVisibility(View.GONE);
        this.binding.accountImage.setOnClickListener((v) -> pickAvatar());

        final var intent = getIntent();
        final var uuid = intent == null ? null : intent.getStringExtra("uuid");
        if (uuid != null) {
            pendingConversationUuid.push(uuid);
        }
        this.binding.publishButton.setEnabled(uri != null);
        this.binding.publishButton.setOnClickListener(this::publish);
    }

    private void publish(final View view) {
        binding.publishButton.setText(R.string.publishing);
        binding.publishButton.setEnabled(false);
        xmppConnectionService.publishMucAvatar(conversation, uri, this);
    }

    public void pickAvatar() {
        this.cropImage.launch(
                new CropImageContractOptions(
                        null, PublishProfilePictureActivity.getCropImageOptions()));
    }

    private void onAvatarPicked(final Uri uri) {
        this.uri = uri;
        if (xmppConnectionServiceBound) {
            reloadAvatar();
        }
    }

    @Override
    public void onAvatarPublicationSucceeded() {
        runOnUiThread(
                () -> {
                    Toast.makeText(this, R.string.avatar_has_been_published, Toast.LENGTH_SHORT)
                            .show();
                    finish();
                });
    }

    @Override
    public void onAvatarPublicationFailed(@StringRes int res) {
        runOnUiThread(
                () -> {
                    Toast.makeText(this, res, Toast.LENGTH_SHORT).show();
                    this.binding.publishButton.setText(R.string.publish);
                    this.binding.publishButton.setEnabled(true);
                });
    }
}
