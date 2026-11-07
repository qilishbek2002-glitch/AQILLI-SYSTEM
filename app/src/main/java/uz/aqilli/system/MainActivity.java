package uz.aqilli.system;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.OutputStream;
import java.util.ArrayList;

public class MainActivity extends Activity {

    private static final int REQ_FILE = 1001;
    private WebView web;
    private ValueCallback<Uri[]> chooserCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        web.setBackgroundColor(0xFF050907);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);

        web.addJavascriptInterface(new Bridge(), "AndroidBridge");
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (chooserCallback != null) {
                    chooserCallback.onReceiveValue(null);
                }
                chooserCallback = callback;
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                ArrayList<String> types = new ArrayList<>();
                String[] accept = params.getAcceptTypes();
                if (accept != null) {
                    for (String t : accept) {
                        if (t != null && t.contains("/")) {
                            types.add(t);
                        }
                    }
                }
                if (!types.isEmpty()) {
                    intent.putExtra(Intent.EXTRA_MIME_TYPES, types.toArray(new String[0]));
                }
                if (params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) {
                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                }
                try {
                    startActivityForResult(intent, REQ_FILE);
                } catch (Exception e) {
                    chooserCallback = null;
                    callback.onReceiveValue(null);
                    return false;
                }
                return true;
            }
        });

        web.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILE && chooserCallback != null) {
            Uri[] result = null;
            if (resultCode == RESULT_OK && data != null) {
                ArrayList<Uri> list = new ArrayList<>();
                if (data.getClipData() != null) {
                    for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                        Uri u = data.getClipData().getItemAt(i).getUri();
                        if (u != null) list.add(u);
                    }
                }
                if (list.isEmpty() && data.getData() != null) list.add(data.getData());
                if (!list.isEmpty()) result = list.toArray(new Uri[0]);
            }
            if (result == null) result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            android.widget.Toast.makeText(this, "Tanlandi: " + (result == null ? 0 : result.length) + " ta fayl", android.widget.Toast.LENGTH_SHORT).show();
            chooserCallback.onReceiveValue(result);
            chooserCallback = null;
        }
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("(window.__onBack && window.__onBack()) ? '1' : '0'", value -> {
            if (!"\"1\"".equals(value)) {
                finish();
            }
        });
    }

    /** Sahifadagi JavaScript shu orqali videoni telefon galereyasiga (Movies/AQILLI) saqlaydi. */
    private class Bridge {
        private OutputStream out;
        private java.io.File tmp;
        private String name;
        private String mime;

        @JavascriptInterface
        public boolean begin(String fileName, String mimeType) {
            try {
                name = fileName;
                mime = mimeType == null ? "video/mp4" : mimeType;
                tmp = new java.io.File(getCacheDir(), "rec.tmp");
                out = new java.io.FileOutputStream(tmp, false);
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public boolean append(String base64) {
            try {
                out.write(Base64.decode(base64, Base64.DEFAULT));
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        /** Yozib olingan faylni to'g'ri MP4/WebM ga qayta o'rab (davomiyligi va surish bilan) galereyaga saqlaydi. */
        @JavascriptInterface
        public String finish() {
            try {
                out.close();
                boolean webm = mime.contains("webm");
                String outMime = webm ? "video/webm" : "video/mp4";
                String base = name.replaceAll("\\.[A-Za-z0-9]+$", "");
                String outName = base + (webm ? ".webm" : ".mp4");

                ContentValues v = new ContentValues();
                v.put(MediaStore.MediaColumns.DISPLAY_NAME, outName);
                v.put(MediaStore.MediaColumns.MIME_TYPE, outMime);
                v.put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/AQILLI");
                v.put(MediaStore.MediaColumns.IS_PENDING, 1);
                Uri uri = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, v);
                if (uri == null) {
                    return "";
                }

                boolean ok = false;
                try {
                    android.os.ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "rw");
                    try {
                        remux(tmp.getAbsolutePath(), pfd.getFileDescriptor(), webm);
                        ok = true;
                    } finally {
                        pfd.close();
                    }
                } catch (Exception e) {
                    ok = false;
                }
                if (!ok) {
                    // Zaxira: faylni o'zgartirmay nusxalash
                    OutputStream os = getContentResolver().openOutputStream(uri, "wt");
                    java.io.FileInputStream in = new java.io.FileInputStream(tmp);
                    byte[] buf = new byte[1 << 16];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        os.write(buf, 0, n);
                    }
                    in.close();
                    os.close();
                }
                ContentValues done = new ContentValues();
                done.put(MediaStore.MediaColumns.IS_PENDING, 0);
                getContentResolver().update(uri, done, null, null);
                tmp.delete();
                return "Movies/AQILLI";
            } catch (Exception e) {
                return "";
            }
        }

        private void remux(String path, java.io.FileDescriptor fd, boolean webm) throws Exception {
            android.media.MediaExtractor ex = new android.media.MediaExtractor();
            ex.setDataSource(path);
            android.media.MediaMuxer mux = new android.media.MediaMuxer(fd,
                    webm ? android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM
                         : android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int n = ex.getTrackCount();
            int[] map = new int[n];
            for (int i = 0; i < n; i++) {
                map[i] = -1;
                android.media.MediaFormat f = ex.getTrackFormat(i);
                String m = f.getString(android.media.MediaFormat.KEY_MIME);
                if (m != null && (m.startsWith("video/") || m.startsWith("audio/"))) {
                    map[i] = mux.addTrack(f);
                    ex.selectTrack(i);
                }
            }
            mux.start();
            java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocate(2 * 1024 * 1024);
            android.media.MediaCodec.BufferInfo info = new android.media.MediaCodec.BufferInfo();
            long base = -1;
            while (true) {
                buf.clear();
                int size = ex.readSampleData(buf, 0);
                if (size < 0) {
                    break;
                }
                int tr = ex.getSampleTrackIndex();
                long t = ex.getSampleTime();
                if (base < 0) {
                    base = t;
                }
                if (tr >= 0 && map[tr] >= 0) {
                    info.offset = 0;
                    info.size = size;
                    info.presentationTimeUs = Math.max(0, t - base);
                    info.flags = (ex.getSampleFlags() & android.media.MediaExtractor.SAMPLE_FLAG_SYNC) != 0
                            ? android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME : 0;
                    mux.writeSampleData(map[tr], buf, info);
                }
                ex.advance();
            }
            mux.stop();
            mux.release();
            ex.release();
        }

        @JavascriptInterface
        public void keepAwake(final boolean on) {
            runOnUiThread(() -> {
                if (on) {
                    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                } else {
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }
            });
        }
    }
}
