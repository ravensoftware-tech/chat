package tech.ravensoftware.chat.ui.adapter;

import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import tech.ravensoftware.chat.R;
import tech.ravensoftware.chat.databinding.ItemUserPreviewBinding;
import tech.ravensoftware.chat.entities.MucOptions;
import tech.ravensoftware.chat.ui.XmppActivity;
import tech.ravensoftware.chat.ui.util.AvatarWorkerTask;
import tech.ravensoftware.chat.ui.util.MucDetailsContextMenuHelper;

public class UserPreviewAdapter extends ListAdapter<MucOptions.User, UserPreviewAdapter.ViewHolder>
        implements View.OnCreateContextMenuListener {

    private MucOptions.User selectedUser = null;

    public UserPreviewAdapter() {
        super(UserAdapter.DIFF);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int position) {
        return new ViewHolder(
                DataBindingUtil.inflate(
                        LayoutInflater.from(viewGroup.getContext()),
                        R.layout.item_user_preview,
                        viewGroup,
                        false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder viewHolder, int position) {
        final MucOptions.User user = getItem(position);
        AvatarWorkerTask.loadAvatar(user, viewHolder.binding.avatar, R.dimen.media_size);
        viewHolder
                .binding
                .getRoot()
                .setOnClickListener(
                        v -> {
                            final XmppActivity activity = XmppActivity.find(v);
                            if (activity == null) {
                                return;
                            }
                            if (user.resource() == null) {
                                Toast.makeText(
                                                activity,
                                                activity.getString(
                                                        R.string.user_has_left_conference,
                                                        user.getDisplayName()),
                                                Toast.LENGTH_SHORT)
                                        .show();
                            }
                            activity.highlightInMuc(user.getConversation(), user.resource());
                        });
        viewHolder.binding.getRoot().setOnCreateContextMenuListener(this);
        viewHolder.binding.getRoot().setTag(user);
        viewHolder
                .binding
                .getRoot()
                .setOnLongClickListener(
                        v -> {
                            selectedUser = user;
                            return false;
                        });
    }

    @Override
    public void onCreateContextMenu(
            ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        MucDetailsContextMenuHelper.onCreateContextMenu(menu, v);
    }

    public MucOptions.User getSelectedUser() {
        return selectedUser;
    }

    public static final class ViewHolder extends RecyclerView.ViewHolder {

        private final ItemUserPreviewBinding binding;

        private ViewHolder(final ItemUserPreviewBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
