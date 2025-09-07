package tech.ravensoftware.chat.xmpp.manager;

import android.content.Context;
import tech.ravensoftware.chat.xmpp.XmppConnection;

public abstract class AbstractManager extends XmppConnection.Delegate {

    protected AbstractManager(final Context context, final XmppConnection connection) {
        super(context.getApplicationContext(), connection);
    }
}
