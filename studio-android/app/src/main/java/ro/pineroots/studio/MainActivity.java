package ro.pineroots.studio;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.util.Size;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.webkit.WebViewAssetLoader;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * PineRoots Studio: the Instagram post generator, running locally in a WebView.
 * The page talks to Android through the "PRAndroid" bridge for things a WebView
 * cannot do on its own: picking photos (incl. HEIC), saving to the Gallery and sharing.
 */
public class MainActivity extends Activity {

    private static final String HOST = "appassets.androidplatform.net";
    private static final String START_URL = "https://" + HOST + "/assets/www/index.html";
    private static final int REQ_FILE_CHOOSER = 1;
    private static final int REQ_PICK_IMAGES = 2;
    private static final int MAX_SIDE = 2400;

    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final List<Uri> shareUris = new ArrayList<>();
    private final List<Uri> pendingImports = new ArrayList<>();
    private boolean pageReady = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .setDomain(HOST)
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web = new WebView(this);
        web.setBackgroundColor(0xFFF3F0EA);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setTextZoom(100);
        s.setSupportZoom(false);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if (HOST.equals(u.getHost())) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (ActivityNotFoundException ignored) {
                }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                pageReady = true;
                flushPendingImports();
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("image/*");
                try {
                    startActivityForResult(i, REQ_FILE_CHOOSER);
                } catch (ActivityNotFoundException e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });

        web.addJavascriptInterface(new Bridge(), "PRAndroid");

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
        } else {
            web.loadUrl(START_URL);
        }
        handleShareIntent(getIntent());
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleShareIntent(intent);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        io.shutdown();
        if (web != null) web.destroy();
        super.onDestroy();
    }

    /* ---------- Photos shared into the app from the Gallery ---------- */

    @SuppressWarnings("deprecation")
    private void handleShareIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        List<Uri> uris = new ArrayList<>();
        if (Intent.ACTION_SEND.equals(action)) {
            Uri u = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (u != null) uris.add(u);
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            ArrayList<Uri> list = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (list != null) uris.addAll(list);
        }
        if (uris.isEmpty()) return;
        intent.setAction(Intent.ACTION_MAIN); // don't import twice after a config change
        pendingImports.addAll(uris);
        flushPendingImports();
    }

    private void flushPendingImports() {
        if (!pageReady || pendingImports.isEmpty()) return;
        List<Uri> batch = new ArrayList<>(pendingImports);
        pendingImports.clear();
        importImages(batch);
    }

    /* ---------- Activity results ---------- */

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        List<Uri> uris = new ArrayList<>();
        if (resultCode == RESULT_OK && data != null) {
            ClipData clip = data.getClipData();
            if (clip != null) {
                for (int i = 0; i < clip.getItemCount(); i++) {
                    Uri u = clip.getItemAt(i).getUri();
                    if (u != null) uris.add(u);
                }
            } else if (data.getData() != null) {
                uris.add(data.getData());
            }
        }
        if (requestCode == REQ_FILE_CHOOSER) {
            if (fileCallback != null) {
                fileCallback.onReceiveValue(uris.isEmpty() ? null : uris.toArray(new Uri[0]));
                fileCallback = null;
            }
        } else if (requestCode == REQ_PICK_IMAGES && !uris.isEmpty()) {
            importImages(uris);
        }
    }

    /* ---------- Decoding photos (handles HEIC and EXIF rotation) ---------- */

    private void importImages(final List<Uri> uris) {
        js("window.__prImportStart&&window.__prImportStart(" + uris.size() + ")");
        io.execute(() -> {
            int failed = 0;
            for (Uri u : uris) {
                try {
                    String dataUrl = decodeToJpegDataUrl(u);
                    String name = displayName(u);
                    js("window.__prImport(" + JSONObject.quote(dataUrl) + "," + JSONObject.quote(name) + ")");
                } catch (Throwable t) {
                    failed++;
                }
            }
            js("window.__prImportEnd&&window.__prImportEnd(" + failed + ")");
        });
    }

    private String decodeToJpegDataUrl(Uri uri) throws Exception {
        ImageDecoder.Source src = ImageDecoder.createSource(getContentResolver(), uri);
        Bitmap bmp = ImageDecoder.decodeBitmap(src, (decoder, info, source) -> {
            Size sz = info.getSize();
            int w = sz.getWidth(), h = sz.getHeight();
            float scale = Math.min(1f, (float) MAX_SIDE / Math.max(w, h));
            if (scale < 1f) decoder.setTargetSize(Math.max(1, Math.round(w * scale)), Math.max(1, Math.round(h * scale)));
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
        });
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.JPEG, 92, out);
        bmp.recycle();
        return "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
    }

    private String displayName(Uri uri) {
        String name = "poza";
        try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                String n = c.getString(0);
                if (n != null) name = n;
            }
        } catch (Exception ignored) {
        }
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /* ---------- Saving to the Gallery / Downloads ---------- */

    private Uri insertMedia(byte[] bytes, String name, String mime) throws Exception {
        ContentResolver cr = getContentResolver();
        ContentValues v = new ContentValues();
        v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
        v.put(MediaStore.MediaColumns.IS_PENDING, 1);
        Uri collection;
        if (mime.startsWith("image/")) {
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PineRoots");
            collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        } else {
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/PineRoots");
            collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        }
        Uri item = cr.insert(collection, v);
        if (item == null) throw new IllegalStateException("insert failed");
        try (OutputStream os = cr.openOutputStream(item)) {
            if (os == null) throw new IllegalStateException("no stream");
            os.write(bytes);
        }
        ContentValues done = new ContentValues();
        done.put(MediaStore.MediaColumns.IS_PENDING, 0);
        cr.update(item, done, null, null);
        return item;
    }

    private void copyToClipboard(String text) {
        runOnUiThread(() -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Descriere PineRoots", text));
        });
    }

    private void js(final String script) {
        runOnUiThread(() -> {
            if (web != null) web.evaluateJavascript(script, null);
        });
    }

    /* ---------- The bridge the page calls ---------- */

    private class Bridge {

        @JavascriptInterface
        public void pickImages() {
            runOnUiThread(() -> {
                Intent i;
                if (Build.VERSION.SDK_INT >= 33) {
                    i = new Intent(MediaStore.ACTION_PICK_IMAGES);
                    int max = Math.min(20, MediaStore.getPickImagesMaxLimit());
                    if (max > 1) i.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, max);
                } else {
                    i = new Intent(Intent.ACTION_GET_CONTENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("image/*");
                    i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                }
                try {
                    startActivityForResult(i, REQ_PICK_IMAGES);
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(MainActivity.this, "Nu am găsit o aplicație pentru poze", Toast.LENGTH_LONG).show();
                }
            });
        }

        @JavascriptInterface
        public String saveFile(String base64, String name, String mime) {
            try {
                byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                insertMedia(bytes, name, mime);
                return mime.startsWith("image/")
                        ? "Salvat în Galerie › Pictures/PineRoots"
                        : "Salvat în Descărcări › Download/PineRoots";
            } catch (Exception e) {
                return "Nu am putut salva fișierul: " + e.getMessage();
            }
        }

        @JavascriptInterface
        public void copyText(String text) {
            copyToClipboard(text);
        }

        @JavascriptInterface
        public void shareBegin() {
            synchronized (shareUris) {
                shareUris.clear();
            }
        }

        @JavascriptInterface
        public boolean shareAdd(String base64, String name) {
            try {
                Uri u = insertMedia(Base64.decode(base64, Base64.DEFAULT), name, "image/jpeg");
                synchronized (shareUris) {
                    shareUris.add(u);
                }
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public String shareCommit(String caption, String target) {
            final ArrayList<Uri> uris;
            synchronized (shareUris) {
                uris = new ArrayList<>(shareUris);
            }
            if (uris.isEmpty()) return "Nu am ce distribui";
            if (caption != null && !caption.isEmpty()) copyToClipboard(caption);
            final boolean instagram = "instagram".equals(target);
            runOnUiThread(() -> {
                Intent send;
                if (uris.size() == 1) {
                    send = new Intent(Intent.ACTION_SEND);
                    send.putExtra(Intent.EXTRA_STREAM, uris.get(0));
                    send.setClipData(ClipData.newRawUri("", uris.get(0)));
                } else {
                    send = new Intent(Intent.ACTION_SEND_MULTIPLE);
                    send.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
                    ClipData cd = ClipData.newRawUri("", uris.get(0));
                    for (int i = 1; i < uris.size(); i++) cd.addItem(new ClipData.Item(uris.get(i)));
                    send.setClipData(cd);
                }
                send.setType("image/jpeg");
                if (caption != null) send.putExtra(Intent.EXTRA_TEXT, caption);
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                if (instagram) {
                    Intent ig = new Intent(send).setPackage("com.instagram.android");
                    try {
                        startActivity(ig);
                        return;
                    } catch (ActivityNotFoundException e) {
                        Toast.makeText(MainActivity.this, "Instagram nu e instalat, alege altă aplicație", Toast.LENGTH_LONG).show();
                    }
                }
                startActivity(Intent.createChooser(send, "Distribuie postarea"));
            });
            return "Descrierea e copiată. În Instagram, ține apăsat pe câmpul de text și alege Lipește.";
        }
    }
}
