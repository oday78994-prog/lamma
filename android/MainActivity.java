package com.lammatullab.app;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Button;

public final class MainActivity extends Activity {
    private static final String HOST = "oday78994-prog.github.io";
    private static final String HOME = "https://" + HOST + "/lamma/";
    private static final int PICK_ATTACHMENT = 10;
    private static final int REQUEST_MIC = 11;
    private WebView web;
    private Button retry;
    private ValueCallback<Uri[]> attachment;
    private PermissionRequest microphone;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(17, 26, 28));
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets safe = insets.getInsets(WindowInsets.Type.systemBars()
                    | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
                return WindowInsets.CONSUMED;
            });
        }
        web = new WebView(this);
        web.setBackgroundColor(Color.rgb(17, 26, 28));
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(false);
        web.getSettings().setAllowContentAccess(false);
        web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                Uri uri = request.getUrl();
                if (isAppPage(uri)) return false;
                if ("https".equals(uri.getScheme())) openBrowser(uri);
                return true;
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) retry.setVisibility(View.VISIBLE);
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (attachment != null) attachment.onReceiveValue(null);
                attachment = callback;
                Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                picker.addCategory(Intent.CATEGORY_OPENABLE);
                picker.setType("*/*");
                String[] requested = params.getAcceptTypes();
                String[] media = {"image/*", "audio/*", "video/*"};
                if (requested.length == 1 && ("image/*".equals(requested[0])
                    || "audio/*".equals(requested[0]) || "video/*".equals(requested[0]))) {
                    picker.setType(requested[0]);
                } else {
                    picker.putExtra(Intent.EXTRA_MIME_TYPES, media);
                }
                try { startActivityForResult(picker, PICK_ATTACHMENT); }
                catch (ActivityNotFoundException error) { finishAttachment(null); }
                return true;
            }
            @Override public void onPermissionRequest(PermissionRequest request) {
                Uri origin = request.getOrigin();
                String resource = PermissionRequest.RESOURCE_AUDIO_CAPTURE;
                if (!isAppOrigin(origin) || request.getResources().length != 1
                    || !resource.equals(request.getResources()[0]) || microphone != null) {
                    request.deny();
                } else if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    request.grant(new String[] {resource});
                } else {
                    microphone = request;
                    requestPermissions(new String[] {Manifest.permission.RECORD_AUDIO}, REQUEST_MIC);
                }
            }
            @Override public void onPermissionRequestCanceled(PermissionRequest request) {
                if (microphone == request) microphone = null;
            }
        });
        web.setDownloadListener((url, agent, disposition, mime, size) -> {
            Uri uri = Uri.parse(url);
            if ("https".equals(uri.getScheme())) openBrowser(uri);
        });
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        retry = new Button(this);
        retry.setText("تعذر الاتصال · إعادة المحاولة");
        retry.setVisibility(View.GONE);
        retry.setOnClickListener(view -> {
            retry.setVisibility(View.GONE);
            web.loadUrl(HOME);
        });
        root.addView(retry, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        setContentView(root);
        web.loadUrl(HOME);
    }

    private static boolean isAppOrigin(Uri uri) {
        return "https".equals(uri.getScheme()) && HOST.equals(uri.getHost())
            && (uri.getPort() == -1 || uri.getPort() == 443) && uri.getUserInfo() == null;
    }

    private static boolean isAppPage(Uri uri) {
        String path = uri.getPath();
        return isAppOrigin(uri) && ("/lamma/".equals(path) || "/lamma/index.html".equals(path));
    }

    private void openBrowser(Uri uri) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
        catch (ActivityNotFoundException ignored) { }
    }

    private void finishAttachment(Uri[] files) {
        ValueCallback<Uri[]> callback = attachment;
        attachment = null;
        if (callback != null) callback.onReceiveValue(files);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_ATTACHMENT) return;
        Uri file = result == RESULT_OK && data != null ? data.getData() : null;
        finishAttachment(file != null && "content".equals(file.getScheme()) ? new Uri[] {file} : null);
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request != REQUEST_MIC || microphone == null) return;
        PermissionRequest pending = microphone;
        microphone = null;
        if (results.length == 1 && results[0] == PackageManager.PERMISSION_GRANTED) {
            pending.grant(new String[] {PermissionRequest.RESOURCE_AUDIO_CAPTURE});
        } else {
            pending.deny();
        }
    }

    @Override public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        finishAttachment(null);
        if (microphone != null) { microphone.deny(); microphone = null; }
        ((FrameLayout) web.getParent()).removeView(web);
        web.destroy();
        super.onDestroy();
    }
}
