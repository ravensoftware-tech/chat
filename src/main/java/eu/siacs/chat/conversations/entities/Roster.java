package tech.ravensoftware.chat.entities;

import androidx.annotation.NonNull;
import tech.ravensoftware.chat.android.AbstractPhoneContact;
import tech.ravensoftware.chat.xmpp.Jid;
import java.util.List;

public interface Roster {

    List<Contact> getContacts();

    List<Contact> getWithSystemAccounts(Class<? extends AbstractPhoneContact> clazz);

    Contact getContact(@NonNull final Jid jid);

    Contact getContactFromContactList(@NonNull final Jid jid);
}
