package tech.ravensoftware.chat.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import com.google.android.material.color.MaterialColors;
import tech.ravensoftware.chat.R;
import tech.ravensoftware.chat.databinding.ItemAccountBinding;
import tech.ravensoftware.chat.entities.Account;
import tech.ravensoftware.chat.ui.XmppActivity;
import tech.ravensoftware.chat.ui.util.AvatarWorkerTask;
import java.util.List;

public class AccountAdapter extends ArrayAdapter<Account> {

    private final XmppActivity activity;
    private final boolean showStateButton;

    public AccountAdapter(XmppActivity activity, List<Account> objects, boolean showStateButton) {
        super(activity, 0, objects);
        this.activity = activity;
        this.showStateButton = showStateButton;
    }

    public AccountAdapter(XmppActivity activity, List<Account> objects) {
        super(activity, 0, objects);
        this.activity = activity;
        this.showStateButton = true;
    }

    @NonNull
    @Override
    public View getView(int position, View view, @NonNull ViewGroup parent) {
        final Account account = getItem(position);
        final ViewHolder viewHolder;
        if (view == null) {
            ItemAccountBinding binding =
                    DataBindingUtil.inflate(
                            LayoutInflater.from(parent.getContext()),
                            R.layout.item_account,
                            parent,
                            false);
            view = binding.getRoot();
            viewHolder = new ViewHolder(binding);
            view.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) view.getTag();
        }
        if (account == null) {
            return view;
        }
        viewHolder.binding.accountJid.setText(account.getJid().asBareJid().toString());
        AvatarWorkerTask.loadAvatar(account, viewHolder.binding.accountImage, R.dimen.avatar);
        final var status = account.getStatus();
        if (account.isServiceOutage()) {
            final var sos = account.getServiceOutageStatus();
            if (sos != null && sos.isPlanned()) {
                viewHolder.binding.accountStatus.setText(
                        R.string.account_status_service_outage_scheduled);
            } else {
                viewHolder.binding.accountStatus.setText(
                        R.string.account_status_service_outage_known);
            }
        } else {
            viewHolder.binding.accountStatus.setText(status.getReadableId());
        }
        switch (status) {
            case ONLINE:
                viewHolder.binding.accountStatus.setTextColor(
                        MaterialColors.getColor(
                                viewHolder.binding.accountStatus,
                                androidx.appcompat.R.attr.colorPrimary));
                break;
            case DISABLED:
            case LOGGED_OUT:
            case CONNECTING:
                viewHolder.binding.accountStatus.setTextColor(
                        MaterialColors.getColor(
                                viewHolder.binding.accountStatus,
                                com.google.android.material.R.attr.colorOnSurfaceVariant));
                break;
            default:
                viewHolder.binding.accountStatus.setTextColor(
                        MaterialColors.getColor(
                                viewHolder.binding.accountStatus,
                                androidx.appcompat.R.attr.colorError));
                break;
        }
        final boolean isDisabled = (account.getStatus() == Account.State.DISABLED);
        viewHolder.binding.tglAccountStatus.setOnCheckedChangeListener(null);
        viewHolder.binding.tglAccountStatus.setChecked(!isDisabled);
        if (this.showStateButton) {
            viewHolder.binding.tglAccountStatus.setVisibility(View.VISIBLE);
        } else {
            viewHolder.binding.tglAccountStatus.setVisibility(View.GONE);
        }
        viewHolder.binding.tglAccountStatus.setOnCheckedChangeListener(
                (compoundButton, b) -> {
                    if (b == isDisabled && activity instanceof OnTglAccountState tglAccountState) {
                        tglAccountState.onClickTglAccountState(account, b);
                    }
                });
        return view;
    }

    private static class ViewHolder {
        private final ItemAccountBinding binding;

        private ViewHolder(ItemAccountBinding binding) {
            this.binding = binding;
        }
    }

    public interface OnTglAccountState {
        void onClickTglAccountState(Account account, boolean state);
    }
}
