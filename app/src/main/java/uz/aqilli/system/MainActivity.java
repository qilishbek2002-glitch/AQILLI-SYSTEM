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
            Uri[] result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
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
        private Uri uri;

        @JavascriptInterface
        public boolean begin(String name, String mime) {
            try {
                ContentValues v = new ContentValues();
                v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
                v.put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/AQILLI");
                v.put(MediaStore.MediaColumns.IS_PENDING, 1);
                uri = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, v);
                if (uri == null) {
                    return false;
                }
                out = getContentResolver().openOutputStream(uri);
                return out != null;
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

        @JavascriptInterface
        public String finish() {
            try {
                out.close();
                ContentValues v = new ContentValues();
                v.put(MediaStore.MediaColumns.IS_PENDING, 0);
                getContentResolver().update(uri, v, null, null);
                return "Movies/AQILLI";
            } catch (Exception e) {
                return "";
            }
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
