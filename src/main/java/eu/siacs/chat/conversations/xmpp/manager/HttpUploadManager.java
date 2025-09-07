package tech.ravensoftware.chat.xmpp.manager;

import android.util.Base64;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Longs;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import tech.ravensoftware.chat.Config;
import tech.ravensoftware.chat.entities.DownloadableFile;
import tech.ravensoftware.chat.services.XmppConnectionService;
import tech.ravensoftware.chat.xml.Namespace;
import tech.ravensoftware.chat.xmpp.Jid;
import tech.ravensoftware.chat.xmpp.XmppConnection;
import im.conversations.android.xmpp.ExtensionFactory;
import im.conversations.android.xmpp.model.disco.info.InfoQuery;
import im.conversations.android.xmpp.model.stanza.Iq;
import im.conversations.android.xmpp.model.upload.Request;
import im.conversations.android.xmpp.model.upload.purpose.Purpose;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.UUID;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.Response;

public class HttpUploadManager extends AbstractManager {

    private final XmppConnectionService service;

    public HttpUploadManager(final XmppConnectionService service, final XmppConnection connection) {
        super(service.getApplicationContext(), connection);
        this.service = service;
    }

    public ListenableFuture<Slot> request(final DownloadableFile file, final String mime) {
        return request(file.getName(), mime, file.getExpectedSize(), null);
    }

    public ListenableFuture<Slot> request(
            final String filename,
            final String mime,
            final long size,
            @Nullable final Purpose purpose) {
        final var result =
                getManager(DiscoManager.class).findDiscoItemByFeature(Namespace.HTTP_UPLOAD);
        if (result == null) {
            return Futures.immediateFailedFuture(
                    new IllegalStateException("No HTTP upload host found"));
        }
        return requestHttpUpload(result.getKey(), filename, mime, size, purpose);
    }

    public ListenableFuture<HttpUrl> upload(
            final File file, final String mime, final Purpose purpose) {
        final var filename = file.getName();
        final var size = file.length();
        final var slotFuture = request(filename, mime, size, purpose);
        return Futures.transformAsync(
                slotFuture, slot -> upload(file, mime, slot), MoreExecutors.directExecutor());
    }

    private ListenableFuture<HttpUrl> upload(final File file, final String mime, final Slot slot) {
        final SettableFuture<HttpUrl> future = SettableFuture.create();
        final OkHttpClient client =
                service.getHttpConnectionManager()
                        .buildHttpClient(slot.put, getAccount(), 0, false);
        final var body = RequestBody.create(MediaType.parse(mime), file);
        final okhttp3.Request request =
                new okhttp3.Request.Builder().url(slot.put).put(body).headers(slot.headers).build();
        client.newCall(request)
                .enqueue(
                        new Callback() {
                            @Override
                            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                                future.setException(e);
                            }

                            @Override
                            public void onResponse(@NonNull Call call, @NonNull Response response) {
                                if (response.isSuccessful()) {
                                    future.set(slot.get);
                                } else {
                                    future.setException(
                                            new IllegalStateException(
                                                    String.format(
                                                            "Response code was %s",
                                                            response.code())));
                                }
                            }
                        });
        return future;
    }

    private ListenableFuture<Slot> requestHttpUpload(
            final Jid host,
            final String filename,
            final String mime,
            final long size,
            @Nullable final Purpose purpose) {
        final Iq iq = new Iq(Iq.Type.GET);
        iq.setTo(host);
        final var request = iq.addExtension(new Request());
        request.setFilename(convertFilename(filename));
        request.setSize(size);
        request.setContentType(mime);
        if (purpose != null) {
            request.addExtension(purpose);
        }
        Log.d(Config.LOGTAG, "-->" + iq);
        final var iqFuture = this.connection.sendIqPacket(iq);
        return Futures.transform(
                iqFuture,
                response -> {
                    final var slot =
                            response.getExtension(
                                    im.conversations.android.xmpp.model.upload.Slot.class);
                    if (slot == null) {
                        throw new IllegalStateException("Slot not found in IQ response");
                    }
                    final var getUrl = slot.getGetUrl();
                    final var put = slot.getPut();
                    if (getUrl == null || put == null) {
                        throw new IllegalStateException("Missing get or put in slot response");
                    }
                    final var putUrl = put.getUrl();
                    if (putUrl == null) {
                        throw new IllegalStateException("Missing put url");
                    }
                    final var contentType = mime == null ? "application/octet-stream" : mime;
                    final var headers =
                            new ImmutableMap.Builder<String, String>()
                                    .putAll(put.getHeadersAllowList())
                                    .put("Content-Type", contentType)
                                    .buildKeepingLast();
                    return new Slot(putUrl, getUrl, headers);
                },
                MoreExecutors.directExecutor());
    }

    public Service getService() {
        if (Config.ENABLE_HTTP_UPLOAD) {
            final var entry =
                    getManager(DiscoManager.class).findDiscoItemByFeature(Namespace.HTTP_UPLOAD);
            return entry == null ? null : new Service(entry);
        }
        return null;
    }

    private static String convertFilename(final String name) {
        int pos = name.indexOf('.');
        if (pos < 0) {
            return name;
        }
        try {
            UUID uuid = UUID.fromString(name.substring(0, pos));
            ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
            bb.putLong(uuid.getMostSignificantBits());
            bb.putLong(uuid.getLeastSignificantBits());
            return Base64.encodeToString(
                            bb.array(), Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP)
                    + name.substring(pos);
        } catch (final Exception e) {
            return name;
        }
    }

    public boolean isAvailableForSize(final long size) {
        final var result = getManager(HttpUploadManager.class).getService();
        if (result == null) {
            return false;
        }
        final Long maxSize = result.getMaxFileSize();
        if (maxSize == null) {
            return true;
        }
        if (size <= maxSize) {
            return true;
        } else {
            Log.d(
                    Config.LOGTAG,
                    getAccount().getJid().asBareJid()
                            + ": http upload is not available for files with"
                            + " size "
                            + size
                            + " (max is "
                            + maxSize
                            + ")");
            return false;
        }
    }

    public static final class Service {
        private final Map.Entry<Jid, InfoQuery> addressInfoQuery;

        public Service(final Map.Entry<Jid, InfoQuery> addressInfoQuery) {
            this.addressInfoQuery = addressInfoQuery;
        }

        public Jid getAddress() {
            return this.addressInfoQuery.getKey();
        }

        public InfoQuery getInfoQuery() {
            return this.addressInfoQuery.getValue();
        }

        public boolean supportsPurpose(final Class<? extends Purpose> purpose) {
            final var id = ExtensionFactory.id(purpose);
            if (id == null) {
                throw new IllegalStateException("Purpose has not been annotated as @XmlElement");
            }
            final var feature = String.format("%s#%s", id.namespace, id.name);
            return getInfoQuery().hasFeature(feature);
        }

        public Long getMaxFileSize() {
            final var value =
                    getInfoQuery()
                            .getServiceDiscoveryExtension(Namespace.HTTP_UPLOAD, "max-file-size");
            return value == null ? null : Longs.tryParse(value);
        }
    }

    public static class Slot {
        public final HttpUrl put;
        public final HttpUrl get;
        public final Headers headers;

        private Slot(final HttpUrl put, final HttpUrl get, final Headers headers) {
            this.put = put;
            this.get = get;
            this.headers = headers;
        }

        private Slot(final HttpUrl put, final HttpUrl get, final Map<String, String> headers) {
            this(put, get, Headers.of(headers));
        }

        @Override
        @NonNull
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("put", put)
                    .add("get", get)
                    .add("headers", headers)
                    .toString();
        }
    }
}
