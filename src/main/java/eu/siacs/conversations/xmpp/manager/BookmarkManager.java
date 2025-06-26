package eu.siacs.conversations.xmpp.manager;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.NonNull;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Bookmark;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.xmpp.XmppConnection;

public class BookmarkManager extends AbstractManager {

    public BookmarkManager(final Context context, final XmppConnection connection) {
        super(context, connection);
    }

    public void request() {
        if (getManager(NativeBookmarkManager.class).hasFeature()) {
            getManager(NativeBookmarkManager.class).fetch();
        } else if (getManager(LegacyBookmarkManager.class).hasConversion()) {
            final var account = getAccount();
            Log.d(
                    Config.LOGTAG,
                    account.getJid() + ": not fetching bookmarks. waiting for server to push");
        } else {
            getManager(PrivateStorageManager.class).fetchBookmarks();
        }
    }

    public void save(final Conversation conversation, final String name) {
        final Account account = conversation.getAccount();
        final Bookmark bookmark = new Bookmark(account, conversation.getJid().asBareJid());
        final String nick = conversation.getJid().getResource();
        if (nick != null && !nick.isEmpty() && !nick.equals(MucOptions.defaultNick(account))) {
            bookmark.setNick(nick);
        }
        if (!TextUtils.isEmpty(name)) {
            bookmark.setBookmarkName(name);
        }
        bookmark.setAutojoin(true);
        createBookmark(bookmark);
        bookmark.setConversation(conversation);
    }

    public void createBookmark(final Bookmark bookmark) {
        final var account = getAccount();
        account.putBookmark(bookmark);
        final ListenableFuture<Void> future;
        if (getManager(NativeBookmarkManager.class).hasFeature()) {
            future = getManager(NativeBookmarkManager.class).publish(bookmark);
        } else if (getManager(LegacyBookmarkManager.class).hasConversion()) {
            future = getManager(LegacyBookmarkManager.class).publish(account.getBookmarks());
        } else {
            future =
                    getManager(PrivateStorageManager.class)
                            .publishBookmarks(account.getBookmarks());
        }
        Futures.addCallback(
                future,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(Void result) {
                        Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": created bookmark");
                    }

                    @Override
                    public void onFailure(@NonNull Throwable t) {
                        Log.d(
                                Config.LOGTAG,
                                account.getJid().asBareJid() + ": could not create bookmark",
                                t);
                    }
                },
                MoreExecutors.directExecutor());
    }

    public void deleteBookmark(final Bookmark bookmark) {
        final var account = getAccount();
        account.removeBookmark(bookmark);
        final ListenableFuture<Void> future;
        if (getManager(NativeBookmarkManager.class).hasFeature()) {
            future = getManager(NativeBookmarkManager.class).retract(bookmark.getJid().asBareJid());
        } else if (getManager(LegacyBookmarkManager.class).hasConversion()) {
            future = getManager(LegacyBookmarkManager.class).publish(account.getBookmarks());
        } else {
            future =
                    getManager(PrivateStorageManager.class)
                            .publishBookmarks(account.getBookmarks());
        }
        Futures.addCallback(
                future,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(Void result) {
                        Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": deleted bookmark");
                    }

                    @Override
                    public void onFailure(@NonNull Throwable t) {
                        Log.d(
                                Config.LOGTAG,
                                account.getJid().asBareJid() + ": could not delete bookmark",
                                t);
                    }
                },
                MoreExecutors.directExecutor());
    }

    public void ensureBookmarkIsAutoJoin(final Conversation conversation) {
        final var account = getAccount();
        final var existingBookmark = conversation.getBookmark();
        if (existingBookmark == null) {
            final var bookmark = new Bookmark(account, conversation.getJid().asBareJid());
            bookmark.setAutojoin(true);
            createBookmark(bookmark);
        } else {
            if (existingBookmark.autojoin()) {
                return;
            }
            existingBookmark.setAutojoin(true);
            createBookmark(existingBookmark);
        }
    }
}
