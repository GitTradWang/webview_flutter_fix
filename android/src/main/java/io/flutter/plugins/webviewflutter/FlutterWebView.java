// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.webviewflutter;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Handler;
import android.view.View;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient.FileChooserParams;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;

import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.platform.PlatformView;
import io.flutter.plugins.webviewflutter.FlutterWebChromeClient.OpenFileChooserCallBack;
import io.flutter.plugins.webviewflutter.result.ActivityResult;
import io.flutter.plugins.webviewflutter.result.ActivityResultCall;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class FlutterWebView implements PlatformView, MethodCallHandler, OpenFileChooserCallBack {

    private static final String JS_CHANNEL_NAMES_FIELD = "javascriptChannelNames";
    private final InputAwareWebView webView;
    private final MethodChannel methodChannel;
    private final FlutterWebViewClient flutterWebViewClient;
    private final Handler platformThreadHandler;

    private Map<String, String> cookies;

    private Context context;

    @TargetApi(VERSION_CODES.JELLY_BEAN_MR1)
    @SuppressWarnings("unchecked")
    FlutterWebView(
            final Context context,
            BinaryMessenger messenger,
            int id,
            Map<String, Object> params,
            final View containerView) {

        if (containerView != null) {
            this.context = containerView.getContext();
        }

        cookies = (Map<String, String>) params.get("cookies");

        DisplayListenerProxy displayListenerProxy = new DisplayListenerProxy();
        DisplayManager displayManager =
                (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        displayListenerProxy.onPreWebViewInitialization(displayManager);
        webView = new InputAwareWebView(context, containerView);
        displayListenerProxy.onPostWebViewInitialization(displayManager);

        platformThreadHandler = new Handler(context.getMainLooper());
        // Allow local storage.
        webView.getSettings().setDomStorageEnabled(true);

        webView.setWebChromeClient(new FlutterWebChromeClient(this));

        settings(webView);

        //启用混合模式
        if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
            webView.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        //禁止缩放
        webView.getSettings().setTextZoom(100);
        methodChannel = new MethodChannel(messenger, "plugins.flutter.io/webview_" + id);
        methodChannel.setMethodCallHandler(this);

        flutterWebViewClient = new FlutterWebViewClient(methodChannel);
        applySettings((Map<String, Object>) params.get("settings"));

        if (params.containsKey(JS_CHANNEL_NAMES_FIELD)) {
            registerJavaScriptChannelNames((List<String>) params.get(JS_CHANNEL_NAMES_FIELD));
        }

        updateAutoMediaPlaybackPolicy((Integer) params.get("autoMediaPlaybackPolicy"));
        if (params.containsKey("userAgent")) {
            String userAgent = (String) params.get("userAgent");
            updateUserAgent(userAgent);
        }
        if (params.containsKey("initialUrl")) {
            String url = (String) params.get("initialUrl");
            FlutterCookieManager.setCookie(context, url, cookies);
            webView.loadUrl(url);
        }
    }

    @Override
    public View getView() {
        return webView;
    }

    // @Override
    // This is overriding a method that hasn't rolled into stable Flutter yet. Including the
    // annotation would cause compile time failures in versions of Flutter too old to include the new
    // method. However leaving it raw like this means that the method will be ignored in old versions
    // of Flutter but used as an override anyway wherever it's actually defined.
    public void onInputConnectionUnlocked() {
        webView.unlockInputConnection();
    }

    // @Override
    // This is overriding a method that hasn't rolled into stable Flutter yet. Including the
    // annotation would cause compile time failures in versions of Flutter too old to include the new
    // method. However leaving it raw like this means that the method will be ignored in old versions
    // of Flutter but used as an override anyway wherever it's actually defined.
    public void onInputConnectionLocked() {
        webView.lockInputConnection();
    }

    // @Override
    // This is overriding a method that hasn't rolled into stable Flutter yet. Including the
    // annotation would cause compile time failures in versions of Flutter too old to include the new
    // method. However leaving it raw like this means that the method will be ignored in old versions
    // of Flutter but used as an override anyway wherever it's actually defined.
    public void onFlutterViewAttached(@NonNull View flutterView) {
        webView.setContainerView(flutterView);
        this.context = flutterView.getContext();
    }

    // @Override
    // This is overriding a method that hasn't rolled into stable Flutter yet. Including the
    // annotation would cause compile time failures in versions of Flutter too old to include the new
    // method. However leaving it raw like this means that the method will be ignored in old versions
    // of Flutter but used as an override anyway wherever it's actually defined.
    public void onFlutterViewDetached() {
        webView.setContainerView(null);
    }

    @Override
    public void onMethodCall(MethodCall methodCall, Result result) {
        switch (methodCall.method) {
            case "loadUrl":
                loadUrl(methodCall, result);
                break;
            case "updateSettings":
                updateSettings(methodCall, result);
                break;
            case "canGoBack":
                canGoBack(result);
                break;
            case "canGoForward":
                canGoForward(result);
                break;
            case "goBack":
                goBack(result);
                break;
            case "goForward":
                goForward(result);
                break;
            case "reload":
                reload(result);
                break;
            case "currentUrl":
                currentUrl(result);
                break;
            case "evaluateJavascript":
                evaluateJavaScript(methodCall, result);
                break;
            case "addJavascriptChannels":
                addJavaScriptChannels(methodCall, result);
                break;
            case "removeJavascriptChannels":
                removeJavaScriptChannels(methodCall, result);
                break;
            case "clearCache":
                clearCache(result);
                break;
            case "getTitle":
                getTitle(result);
                break;
            default:
                result.notImplemented();
        }
    }

    @SuppressWarnings("unchecked")
    private void loadUrl(MethodCall methodCall, Result result) {
        Map<String, Object> request = (Map<String, Object>) methodCall.arguments;
        String url = (String) request.get("url");
        Map<String, String> headers = (Map<String, String>) request.get("headers");
        if (headers == null) {
            headers = Collections.emptyMap();
        }
        FlutterCookieManager.setCookie(getView().getContext(), url, cookies);
        webView.loadUrl(url, headers);
        result.success(null);
    }

    private void canGoBack(Result result) {
        result.success(webView.canGoBack());
    }

    private void canGoForward(Result result) {
        result.success(webView.canGoForward());
    }

    private void goBack(Result result) {
        if (webView.canGoBack()) {
            webView.goBack();
        }
        result.success(null);
    }

    private void goForward(Result result) {
        if (webView.canGoForward()) {
            webView.goForward();
        }
        result.success(null);
    }

    private void reload(Result result) {
        webView.reload();
        result.success(null);
    }

    private void currentUrl(Result result) {
        result.success(webView.getUrl());
    }

    @SuppressWarnings("unchecked")
    private void updateSettings(MethodCall methodCall, Result result) {
        applySettings((Map<String, Object>) methodCall.arguments);
        result.success(null);
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private void evaluateJavaScript(MethodCall methodCall, final Result result) {
        String jsString = (String) methodCall.arguments;
        if (jsString == null) {
            throw new UnsupportedOperationException("JavaScript string cannot be null");
        }
        webView.evaluateJavascript(
                jsString,
                new android.webkit.ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String value) {
                        result.success(value);
                    }
                });
    }

    @SuppressWarnings("unchecked")
    private void addJavaScriptChannels(MethodCall methodCall, Result result) {
        List<String> channelNames = (List<String>) methodCall.arguments;
        registerJavaScriptChannelNames(channelNames);
        result.success(null);
    }

    @SuppressWarnings("unchecked")
    private void removeJavaScriptChannels(MethodCall methodCall, Result result) {
        List<String> channelNames = (List<String>) methodCall.arguments;
        for (String channelName : channelNames) {
            webView.removeJavascriptInterface(channelName);
        }
        result.success(null);
    }

    private void clearCache(Result result) {
        webView.clearCache(true);
        WebStorage.getInstance().deleteAllData();
        result.success(null);
    }

    private void getTitle(Result result) {
        result.success(webView.getTitle());
    }

    private void applySettings(Map<String, Object> settings) {
        for (String key : settings.keySet()) {
            switch (key) {
                case "jsMode":
                    updateJsMode((Integer) settings.get(key));
                    break;
                case "hasNavigationDelegate":
                    final boolean hasNavigationDelegate = (boolean) settings.get(key);

                    final WebViewClient webViewClient =
                            flutterWebViewClient.createWebViewClient(hasNavigationDelegate);

                    webView.setWebViewClient(webViewClient);
                    break;
                case "debuggingEnabled":
                    final boolean debuggingEnabled = (boolean) settings.get(key);

                    webView.setWebContentsDebuggingEnabled(debuggingEnabled);
                    break;
                case "userAgent":
                    updateUserAgent((String) settings.get(key));
                    break;
                default:
                    throw new IllegalArgumentException("Unknown WebView setting: " + key);
            }
        }
    }

    private void updateJsMode(int mode) {
        switch (mode) {
            case 0: // disabled
                webView.getSettings().setJavaScriptEnabled(false);
                break;
            case 1: // unrestricted
                webView.getSettings().setJavaScriptEnabled(true);
                break;
            default:
                throw new IllegalArgumentException("Trying to set unknown JavaScript mode: " + mode);
        }
    }

    private void updateAutoMediaPlaybackPolicy(int mode) {
        // This is the index of the AutoMediaPlaybackPolicy enum, index 1 is always_allow, for all
        // other values we require a user gesture.
        boolean requireUserGesture = mode != 1;
        webView.getSettings().setMediaPlaybackRequiresUserGesture(requireUserGesture);
    }

    private void registerJavaScriptChannelNames(List<String> channelNames) {
        for (String channelName : channelNames) {
            webView.addJavascriptInterface(
                    new JavaScriptChannel(methodChannel, channelName, platformThreadHandler), channelName);
        }
    }

    private void updateUserAgent(String userAgent) {
        webView.getSettings().setUserAgentString(userAgent);
    }

    private void settings(WebView webView) {
        WebSettings viewSettings = webView.getSettings();
        viewSettings.setJavaScriptEnabled(true);
        viewSettings.setSupportZoom(true);
        viewSettings.setBuiltInZoomControls(false);
        viewSettings.setSavePassword(false);
        if (checkNetwork(webView.getContext())) {
            //根据cache-control获取数据。
            viewSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        } else {
            //没网，则从本地获取，即离线加载
            viewSettings.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            //适配5.0不允许http和https混合使用情况
            viewSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }
        viewSettings.setTextZoom(100);
        viewSettings.setDatabaseEnabled(true);
        viewSettings.setAppCacheEnabled(true);
        viewSettings.setLoadsImagesAutomatically(true);
        viewSettings.setSupportMultipleWindows(false);
        // 是否阻塞加载网络图片  协议http or https
        viewSettings.setBlockNetworkImage(false);
        // 允许加载本地文件html  file协议
        viewSettings.setAllowFileAccess(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            // 通过 file url 加载的 Javascript 读取其他的本地文件 .建议关闭
            viewSettings.setAllowFileAccessFromFileURLs(false);
            // 允许通过 file url 加载的 Javascript 可以访问其他的源，包括其他的文件和 http，https 等其他的源
            viewSettings.setAllowUniversalAccessFromFileURLs(false);
        }
        viewSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {

            viewSettings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.SINGLE_COLUMN);
        } else {
            viewSettings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NORMAL);
        }
        viewSettings.setLoadWithOverviewMode(false);
        viewSettings.setUseWideViewPort(false);
        viewSettings.setDomStorageEnabled(true);
        viewSettings.setNeedInitialFocus(true);
        viewSettings.setDefaultTextEncodingName("utf-8");//设置编码格式
        viewSettings.setDefaultFontSize(16);
        viewSettings.setMinimumFontSize(12);//设置 WebView 支持的最小字体大小，默认为 8
        viewSettings.setGeolocationEnabled(true);
        String dir = webView.getContext().getCacheDir().getAbsolutePath() + "web_cache";
        //设置数据库路径  api19 已经废弃,这里只针对 webkit 起作用
        viewSettings.setGeolocationDatabasePath(dir);
        viewSettings.setDatabasePath(dir);
        viewSettings.setAppCachePath(dir);
        //缓存文件最大值
        viewSettings.setAppCacheMaxSize(Long.MAX_VALUE);
    }

    public static boolean checkNetwork(Context context) {

        try {
            ConnectivityManager connectivity = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivity == null) {
                return false;
            }
            @SuppressLint("MissingPermission") NetworkInfo info = connectivity.getActiveNetworkInfo();
            return info != null && info.isConnected();
        } catch (Exception e) {

        }
        return true;
    }

    @Override
    public void dispose() {
        methodChannel.setMethodCallHandler(null);
        webView.dispose();
        webView.destroy();
    }

    @Override
    public void openFileChooserCallBack(final ValueCallback<Uri> uploadMsg, String acceptType) {
        new ActivityResult(context).startActivityForResult(getImageChooserIntent()).setOnActivityResult(new ActivityResultCall() {
            @Override
            public void onResult(Intent intent, int resultCode) {

                if (uploadMsg == null) {
                    return;
                }

                if (resultCode == Activity.RESULT_OK && intent != null) {
                    uploadMsg.onReceiveValue(intent.getData());
                }
            }
        });
    }

    @Override
    public void showFileChooserCallBack(final ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {

        new ActivityResult(context).startActivityForResult(getImageChooserIntent()).setOnActivityResult(new ActivityResultCall() {
            @Override
            public void onResult(Intent intent, int resultCode) {

                if (filePathCallback == null) {
                    return;
                }

                if (resultCode == Activity.RESULT_OK && intent != null) {
                    Uri[] results = null;
                    String dataString = intent.getDataString();
                    ClipData clipData = intent.getClipData();

                    if (clipData != null) {
                        results = new Uri[clipData.getItemCount()];
                        for (int i = 0; i < clipData.getItemCount(); i++) {
                            ClipData.Item item = clipData.getItemAt(i);
                            results[i] = item.getUri();
                        }
                    }
                    if (dataString != null) {
                        results = new Uri[]{Uri.parse(dataString)};
                    }
                    filePathCallback.onReceiveValue(results);
                }
            }
        });
    }

    private Intent getImageChooserIntent() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        return Intent.createChooser(i, "Image Chooser");
    }
}
