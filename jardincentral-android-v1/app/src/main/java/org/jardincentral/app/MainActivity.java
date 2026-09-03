package org.jardincentral.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.PermissionRequest;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;

public class MainActivity extends Activity {
    private static final String HOME = "https://jardincentral.org/";
    private static final int FILE_CHOOSER = 1001;
    private static final int AUDIO_PERMISSION = 1002;

    private WebView webView;
    private ProgressBar progress;
    private LinearLayout errorPanel;
    private TextView errorTitle;
    private TextView errorText;
    private ValueCallback<Uri[]> fileCallback;
    private Uri cameraUri;
    private PermissionRequest pendingPermissionRequest;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(238, 250, 246));
        getWindow().setNavigationBarColor(Color.WHITE);
        buildUi();
        configureWebView();
        if (state != null && webView.restoreState(state) != null) return;
        if (isOnline()) webView.loadUrl(resolveStartUrl(getIntent())); else showError("Sin conexión", "Revisa tu conexión a internet y vuelve a intentarlo.");
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(245, 252, 249));

        webView = new WebView(this);
        root.addView(webView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3), Gravity.TOP);
        root.addView(progress, plp);

        errorPanel = new LinearLayout(this);
        errorPanel.setOrientation(LinearLayout.VERTICAL);
        errorPanel.setGravity(Gravity.CENTER);
        errorPanel.setPadding(dp(28), dp(28), dp(28), dp(28));
        errorPanel.setBackgroundColor(Color.rgb(245, 252, 249));
        errorPanel.setVisibility(View.GONE);

        TextView brand = new TextView(this);
        brand.setText("Jardín Central");
        brand.setTextColor(Color.rgb(7, 94, 84));
        brand.setTextSize(30);
        brand.setGravity(Gravity.CENTER);
        brand.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        errorPanel.addView(brand, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        errorTitle = new TextView(this);
        errorTitle.setTextSize(22);
        errorTitle.setTextColor(Color.rgb(7, 94, 84));
        errorTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = dp(24);
        errorPanel.addView(errorTitle, tlp);

        errorText = new TextView(this);
        errorText.setTextSize(16);
        errorText.setTextColor(Color.rgb(80, 102, 96));
        errorText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.topMargin = dp(12);
        errorPanel.addView(errorText, mlp);

        Button retry = new Button(this);
        retry.setText("Reintentar");
        retry.setOnClickListener(v -> retry());
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = dp(22);
        errorPanel.addView(retry, rlp);

        root.addView(errorPanel, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUserAgentString(s.getUserAgentString() + " JardinCentralAndroid/1.0.0");

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false);

        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (isInternal(u)) return false;
                openExternal(u);
                return true;
            }
            @Override public void onPageFinished(WebView view, String url) {
                hideError();
                CookieManager.getInstance().flush();
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError err) {
                if (req.isForMainFrame()) showError(isOnline() ? "Jardín Central no está disponible" : "Sin conexión", isOnline() ? "El portal no respondió correctamente. Intenta de nuevo en unos momentos." : "Revisa tu conexión a internet y vuelve a intentarlo.");
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int newProgress) {
                progress.setProgress(newProgress);
                progress.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }
            @Override public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = cb;
                launchFileChooser(params);
                return true;
            }
            @Override public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> {
                    boolean wantsAudio = false;
                    for (String r : request.getResources()) if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r)) wantsAudio = true;
                    if (!wantsAudio) { request.deny(); return; }
                    pendingPermissionRequest = request;
                    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) grantAudio();
                    else requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, AUDIO_PERMISSION);
                });
            }
        });

        webView.setDownloadListener((url, userAgent, disposition, mimeType, length) -> download(url, userAgent, disposition, mimeType));
    }

    private void launchFileChooser(WebChromeClient.FileChooserParams params) {
        Intent pick = params.createIntent();
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        Intent camera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        Intent chooser;
        try {
            File dir = new File(getCacheDir(), "camera");
            if (!dir.exists()) dir.mkdirs();
            File f = File.createTempFile("jc_", ".jpg", dir);
            cameraUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
            camera.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
            camera.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            chooser = Intent.createChooser(pick, "Seleccionar archivo");
            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{camera});
        } catch (IOException e) {
            chooser = Intent.createChooser(pick, "Seleccionar archivo");
        }
        try { startActivityForResult(chooser, FILE_CHOOSER); }
        catch (ActivityNotFoundException e) { fileCallback.onReceiveValue(null); fileCallback = null; }
    }

    private void download(String url, String userAgent, String disposition, String mimeType) {
        if (url == null || !(url.startsWith("https://") || url.startsWith("http://"))) return;
        try {
            DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
            r.setMimeType(mimeType);
            r.addRequestHeader("User-Agent", userAgent);
            String cookie = CookieManager.getInstance().getCookie(url);
            if (cookie != null) r.addRequestHeader("Cookie", cookie);
            r.setTitle(URLUtil.guessFileName(url, disposition, mimeType));
            r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, disposition, mimeType));
            ((DownloadManager)getSystemService(DOWNLOAD_SERVICE)).enqueue(r);
            Toast.makeText(this, "Descarga iniciada", Toast.LENGTH_SHORT).show();
        } catch (Exception e) { Toast.makeText(this, "No se pudo iniciar la descarga", Toast.LENGTH_SHORT).show(); }
    }

    private void grantAudio() {
        if (pendingPermissionRequest != null) {
            pendingPermissionRequest.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
            pendingPermissionRequest = null;
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == AUDIO_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) grantAudio();
            else if (pendingPermissionRequest != null) { pendingPermissionRequest.deny(); pendingPermissionRequest = null; }
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != FILE_CHOOSER || fileCallback == null) return;
        Uri[] result = null;
        if (resultCode == RESULT_OK) {
            if (data != null && data.getData() != null) result = new Uri[]{data.getData()};
            else if (cameraUri != null) result = new Uri[]{cameraUri};
        }
        fileCallback.onReceiveValue(result);
        fileCallback = null;
        cameraUri = null;
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }

    private boolean isInternal(Uri u) {
        if (u == null || u.getHost() == null) return false;
        String h = u.getHost().toLowerCase();
        return "jardincentral.org".equals(h) || "www.jardincentral.org".equals(h);
    }

    private void openExternal(Uri u) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, u)); }
        catch (Exception ignored) { Toast.makeText(this, "No hay una aplicación disponible para abrir este enlace", Toast.LENGTH_SHORT).show(); }
    }

    private String resolveStartUrl(Intent intent) {
        Uri d = intent == null ? null : intent.getData();
        return d != null && isInternal(d) ? d.toString() : HOME;
    }

    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null || cm.getActiveNetwork() == null) return false;
        NetworkCapabilities c = cm.getNetworkCapabilities(cm.getActiveNetwork());
        return c != null && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private void retry() {
        if (!isOnline()) { showError("Sin conexión", "Revisa tu conexión a internet y vuelve a intentarlo."); return; }
        hideError();
        webView.reload();
        if (webView.getUrl() == null) webView.loadUrl(HOME);
    }

    private void showError(String title, String message) {
        errorTitle.setText(title);
        errorText.setText(message);
        errorPanel.setVisibility(View.VISIBLE);
    }

    private void hideError() { errorPanel.setVisibility(View.GONE); }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
