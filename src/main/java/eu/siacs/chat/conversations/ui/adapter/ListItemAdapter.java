package tech.ravensoftware.chat.ui.adapter;

import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.constraintlayout.helper.widget.Flow;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.databinding.DataBindingUtil;
import com.google.android.material.color.MaterialColors;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Ints;
import tech.ravensoftware.chat.AppSettings;
import tech.ravensoftware.chat.Config;
import tech.ravensoftware.chat.R;
import tech.ravensoftware.chat.databinding.ItemContactBinding;
import tech.ravensoftware.chat.entities.Contact;
import tech.ravensoftware.chat.entities.ListItem;
import tech.ravensoftware.chat.ui.XmppActivity;
import tech.ravensoftware.chat.ui.util.AvatarWorkerTask;
import tech.ravensoftware.chat.utils.IrregularUnicodeDetector;
import tech.ravensoftware.chat.utils.UIHelper;
import tech.ravensoftware.chat.utils.XEP0392Helper;
import tech.ravensoftware.chat.xmpp.Jid;
import im.conversations.android.xmpp.model.stanza.Presence;
import java.util.List;

public class ListItemAdapter extends ArrayAdapter<ListItem> {

    protected XmppActivity activity;
    private boolean showDynamicTags = false;
    private OnTagClickedListener mOnTagClickedListener = null;
    private final View.OnClickListener onTagTvClick =
            view -> {
                if (view instanceof TextView tv && mOnTagClickedListener != null) {
                    final String tag = tv.getText().toString();
                    mOnTagClickedListener.onTagClicked(tag);
                }
            };

    public ListItemAdapter(XmppActivity activity, List<ListItem> objects) {
        super(activity, 0, objects);
        this.activity = activity;
    }

    public void refreshSettings() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
        this.showDynamicTags = preferences.getBoolean(AppSettings.SHOW_DYNAMIC_TAGS, false);
    }

    @NonNull
    @Override
    public View getView(int position, View view, @NonNull ViewGroup parent) {
        LayoutInflater inflater = activity.getLayoutInflater();
        final ListItem item = getItem(position);
        ViewHolder viewHolder;
        if (view == null) {
            final ItemContactBinding binding =
                    DataBindingUtil.inflate(inflater, R.layout.item_contact, parent, false);
            viewHolder = ViewHolder.get(binding);
            view = binding.getRoot();
        } else {
            viewHolder = (ViewHolder) view.getTag();
        }
        if (view.isActivated()) {
            Log.d(Config.LOGTAG, "item " + item.getDisplayName() + " is activated");
        }
        // view.setBackground(StyledAttributes.getDrawable(view.getContext(),R.attr.list_item_background));
        final var tags = item.getTags();
        final boolean hasMetaTags;
        if (item instanceof Contact contact) {
            hasMetaTags =
                    contact.isBlocked()
                            || contact.getShownStatus() != Presence.Availability.OFFLINE;
        } else {
            hasMetaTags = false;
        }
        if ((tags.isEmpty() && !hasMetaTags) || !this.showDynamicTags) {
            viewHolder.tags.setVisibility(View.GONE);
        } else {
            viewHolder.tags.setVisibility(View.VISIBLE);
            viewHolder.tags.removeViews(1, viewHolder.tags.getChildCount() - 1);
            final ImmutableList.Builder<Integer> viewIdBuilder = new ImmutableList.Builder<>();
            for (final ListItem.Tag tag : tags) {
                final String name = tag.getName();
                final TextView tv =
                        (TextView) inflater.inflate(R.layout.item_tag, viewHolder.tags, false);
                tv.setText(name);
                tv.setBackgroundTintList(
                        ColorStateList.valueOf(
                                MaterialColors.harmonizeWithPrimary(
                                        getContext(), XEP0392Helper.rgbFromNick(name))));
                tv.setOnClickListener(this.onTagTvClick);
                final int id = ViewCompat.generateViewId();
                tv.setId(id);
                viewIdBuilder.add(id);
                viewHolder.tags.addView(tv);
            }
            if (item instanceof Contact contact) {
                if (contact.isBlocked()) {
                    final TextView tv =
                            (TextView) inflater.inflate(R.layout.item_tag, viewHolder.tags, false);
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
                    viewHolder.tags.addView(tv);
                } else {
                    final Presence.Availability status = contact.getShownStatus();
                    if (status != Presence.Availability.OFFLINE) {
                        final TextView tv =
                                (TextView)
                                        inflater.inflate(R.layout.item_tag, viewHolder.tags, false);
                        UIHelper.setStatus(tv, status);
                        final int id = ViewCompat.generateViewId();
                        tv.setId(id);
                        viewIdBuilder.add(id);
                        viewHolder.tags.addView(tv);
                    }
                }
            }
            viewHolder.flowWidget.setReferencedIds(Ints.toArray(viewIdBuilder.build()));
        }
        final Jid jid = item.getAddress();
        if (jid != null) {
            viewHolder.jid.setVisibility(View.VISIBLE);
            viewHolder.jid.setText(IrregularUnicodeDetector.style(activity, jid));
        } else {
            viewHolder.jid.setVisibility(View.GONE);
        }
        viewHolder.name.setText(item.getDisplayName());
        AvatarWorkerTask.loadAvatar(item, viewHolder.avatar, R.dimen.avatar);
        return view;
    }

    public void setOnTagClickedListener(OnTagClickedListener listener) {
        this.mOnTagClickedListener = listener;
    }

    public interface OnTagClickedListener {
        void onTagClicked(String tag);
    }

    private static class ViewHolder {
        private TextView name;
        private TextView jid;
        private ImageView avatar;
        private ConstraintLayout tags;
        private Flow flowWidget;

        private ViewHolder() {}

        public static ViewHolder get(final ItemContactBinding binding) {
            ViewHolder viewHolder = new ViewHolder();
            viewHolder.name = binding.contactDisplayName;
            viewHolder.jid = binding.contactJid;
            viewHolder.avatar = binding.contactPhoto;
            viewHolder.tags = binding.tags;
            viewHolder.flowWidget = binding.flowWidget;
            binding.getRoot().setTag(viewHolder);
            return viewHolder;
        }
    }
}
