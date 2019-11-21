// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.webviewflutter;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.text.TextUtils;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.ValueCallback;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import java.util.ArrayList;
import java.util.Map;
import java.util.Map.Entry;

class FlutterCookieManager implements MethodCallHandler {

  private FlutterCookieManager() {
    // Do not instantiate.
    // This class should only be used in context of a BinaryMessenger.
    // Use FlutterCookieManager#registerWith instead.
  }

  static void registerWith(BinaryMessenger messenger) {
    MethodChannel methodChannel = new MethodChannel(messenger, "plugins.flutter.io/cookie_manager");
    FlutterCookieManager cookieManager = new FlutterCookieManager();
    methodChannel.setMethodCallHandler(cookieManager);
  }

  @Override
  public void onMethodCall(MethodCall methodCall, Result result) {
    switch (methodCall.method) {
      case "clearCookies":
        clearCookies(result);
        break;
      default:
        result.notImplemented();
    }
  }

  private static void clearCookies(final Result result) {
    CookieManager cookieManager = CookieManager.getInstance();
    final boolean hasCookies = cookieManager.hasCookies();
    if (Build.VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
      cookieManager.removeAllCookies(
          new ValueCallback<Boolean>() {
            @Override
            public void onReceiveValue(Boolean value) {
              result.success(hasCookies);
            }
          });
    } else {
      cookieManager.removeAllCookie();
      result.success(hasCookies);
    }
  }

  public static void setCookie(Context context, String url, Map<String, String> cookies) {

    if (context == null || url == null || "about:blank".equals(url)) {
      return;
    }

    if (cookies == null || cookies.size() == 0) {
      return;
    }

    ArrayList<String> cookieList = new ArrayList<>();

    String domain = getDomain(url);
    String baseUrl = getBaseUrl(url);

    for (Entry<String, String> entry : cookies.entrySet()) {
      cookieList.add(entry.getKey() + "=" + entry.getValue());
    }

    CookieSyncManager.createInstance(context);
    CookieManager cookieManager = CookieManager.getInstance();
    cookieManager.setAcceptCookie(true);


    System.out.println("setCookie  url:  "+url);
    System.out.println("setCookie  cookies:  "+cookies);
    System.out.println("setCookie  domain:  "+domain);
    System.out.println("setCookie  baseUrl:  "+baseUrl);
    System.out.println("setCookie  cookieList:  "+cookieList);

    if (cookieList.size() > 0) {
      for (String cookie : cookieList) {
        cookieManager.setCookie(baseUrl, cookie);
      }
    }
    cookieManager.setCookie(baseUrl, "Domain=" + domain);
    cookieManager.setCookie(baseUrl, "Path=/");

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      cookieManager.flush();
    } else {
      CookieSyncManager.getInstance().sync();
    }
  }

  /**
   * 根据传入的URL获取一级域名
   */
  public static String getDomain(String url) {
    String domain = "";
    if (url != null && url.startsWith("http")) {
      try {
        String host = Uri.parse(url).getHost();
        if (host != null && host.contains(".")) {
          domain = host.substring(host.indexOf("."));
        }
      } catch (Exception ignored) {
      }
    }
    return domain;
  }

  /**
   * 根据传入的URL获取一级域名
   */
  public static String getBaseUrl(String url) {
    String baseUrl = "";
    if (url != null && url.startsWith("http")) {
      try {
        Uri uri = Uri.parse(url);
        String host = uri.getHost();
        String scheme = uri.getScheme();

        baseUrl = scheme + "://" + host;

      } catch (Exception ignored) {
      }
    }
    return baseUrl;
  }
}
