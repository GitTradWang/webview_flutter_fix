package io.flutter.plugins.webviewflutter.result;

import android.content.Intent;

public interface ActivityResultCall {

  void onResult(Intent intent,int resultCode);
}
