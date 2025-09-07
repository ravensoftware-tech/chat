package tech.ravensoftware.chat.entities;

import androidx.annotation.NonNull;
import tech.ravensoftware.chat.utils.UIHelper;
import tech.ravensoftware.chat.xmpp.Jid;
import java.util.Collections;
import java.util.List;

public class RawBlockable implements ListItem, Blockable {

    private final Account account;
    private final Jid jid;

    public RawBlockable(@NonNull Account account, @NonNull Jid jid) {
        this.account = account;
        this.jid = jid;
    }

    @Override
    public boolean isBlocked() {
        return true;
    }

    @Override
    public boolean isDomainBlocked() {
        throw new AssertionError("not implemented");
    }

    @Override
    @NonNull
    public Jid getBlockedAddress() {
        return this.jid;
    }

    @Override
    public String getDisplayName() {
        if (jid.isFullJid()) {
            return jid.getResource();
        } else {
            return jid.toString();
        }
    }

    @Override
    public Jid getAddress() {
        return this.jid;
    }

    @Override
    public List<Tag> getTags() {
        return Collections.emptyList();
    }

    @Override
    public Account getAccount() {
        return account;
    }

    @Override
    public int getAvatarBackgroundColor() {
        return UIHelper.getColorForName(jid.toString());
    }
}
