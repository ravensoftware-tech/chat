package tech.ravensoftware.chat.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;
import com.google.android.material.textfield.TextInputLayout;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import tech.ravensoftware.chat.Config;
import tech.ravensoftware.chat.R;
import tech.ravensoftware.chat.databinding.ActivityChangePasswordBinding;
import tech.ravensoftware.chat.entities.Account;
import tech.ravensoftware.chat.ui.widget.DisabledActionModeCallback;

public class ChangePasswordActivity extends XmppActivity {

    private ActivityChangePasswordBinding binding;

    private final FutureCallback<? super Void> passwordChangedCallback =
            new FutureCallback<>() {
                @Override
                public void onSuccess(Void result) {
                    Toast.makeText(
                                    ChangePasswordActivity.this,
                                    R.string.password_changed,
                                    Toast.LENGTH_LONG)
                            .show();
                    finish();
                }

                @Override
                public void onFailure(@NonNull Throwable t) {
                    Log.d(Config.LOGTAG, "could not change password", t);
                    binding.newPasswordLayout.setError(
                            getString(R.string.could_not_change_password));
                    binding.changePasswordButton.setEnabled(true);
                    binding.changePasswordButton.setText(R.string.change_password);
                }
            };
    private final View.OnClickListener mOnChangePasswordButtonClicked =
            new View.OnClickListener() {
                @Override
                public void onClick(final View view) {
                    final var account = mAccount;
                    if (account == null) {
                        return;
                    }
                    final String currentPassword = binding.currentPassword.getText().toString();
                    final String newPassword = binding.newPassword.getText().toString();
                    if (!account.isOptionSet(Account.OPTION_MAGIC_CREATE)
                            && !currentPassword.equals(account.getPassword())) {
                        binding.currentPassword.requestFocus();
                        binding.currentPasswordLayout.setError(
                                getString(R.string.account_status_unauthorized));
                        removeErrorsOnAllBut(binding.currentPasswordLayout);
                    } else if (newPassword.trim().isEmpty()) {
                        binding.newPassword.requestFocus();
                        binding.newPasswordLayout.setError(
                                getString(R.string.password_should_not_be_empty));
                        removeErrorsOnAllBut(binding.newPasswordLayout);
                    } else {
                        binding.currentPasswordLayout.setError(null);
                        binding.newPasswordLayout.setError(null);
                        final var future =
                                xmppConnectionService.updateAccountPasswordOnServer(
                                        account, newPassword);
                        Futures.addCallback(
                                future,
                                ChangePasswordActivity.this.passwordChangedCallback,
                                ContextCompat.getMainExecutor(getApplication()));
                        binding.changePasswordButton.setEnabled(false);
                        binding.changePasswordButton.setText(R.string.updating);
                    }
                }
            };

    private Account mAccount;

    @Override
    protected void onBackendConnected() {
        this.mAccount = extractAccount(getIntent());
        if (this.mAccount != null && this.mAccount.isOptionSet(Account.OPTION_MAGIC_CREATE)) {
            this.binding.currentPasswordLayout.setVisibility(View.GONE);
        } else {
            this.binding.currentPasswordLayout.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.binding = DataBindingUtil.setContentView(this, R.layout.activity_change_password);
        Activities.setStatusAndNavigationBarColors(this, binding.getRoot());
        setSupportActionBar(binding.toolbar);
        configureActionBar(getSupportActionBar());
        binding.cancelButton.setOnClickListener(view -> finish());
        binding.changePasswordButton.setOnClickListener(this.mOnChangePasswordButtonClicked);
        binding.currentPassword.setCustomSelectionActionModeCallback(
                new DisabledActionModeCallback());
        binding.newPassword.setCustomSelectionActionModeCallback(new DisabledActionModeCallback());
    }

    @Override
    public void onStart() {
        super.onStart();
        Intent intent = getIntent();
        String password = intent != null ? intent.getStringExtra("password") : null;
        if (password != null) {
            binding.newPassword.getEditableText().clear();
            binding.newPassword.getEditableText().append(password);
        }
    }

    private void removeErrorsOnAllBut(TextInputLayout exception) {
        if (this.binding.currentPasswordLayout != exception) {
            this.binding.currentPasswordLayout.setErrorEnabled(false);
            this.binding.currentPasswordLayout.setError(null);
        }
        if (this.binding.newPasswordLayout != exception) {
            this.binding.newPasswordLayout.setErrorEnabled(false);
            this.binding.newPasswordLayout.setError(null);
        }
    }

    public void refreshUiReal() {}
}
