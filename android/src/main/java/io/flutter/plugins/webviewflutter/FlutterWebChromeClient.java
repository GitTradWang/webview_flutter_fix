package io.flutter.plugins.webviewflutter;


import android.annotation.TargetApi;
import android.net.Uri;
import android.os.Build;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;

class FlutterWebChromeClient extends WebChromeClient {

  private OpenFileChooserCallBack mOpenFileChooserCallBack;

  FlutterWebChromeClient(OpenFileChooserCallBack callBack){
    this.mOpenFileChooserCallBack=callBack;
  };

  // For Android < 3.0
  public void openFileChooser(ValueCallback<Uri> uploadMsg) {
    openFileChooser(uploadMsg, "");
  }

  //For Android 3.0 - 4.0
  public void openFileChooser(ValueCallback<Uri> uploadMsg, String acceptType) {
    if (mOpenFileChooserCallBack != null) {
      mOpenFileChooserCallBack.openFileChooserCallBack(uploadMsg, acceptType);
    }
  }

  // For Android 4.0 - 5.0
  public void openFileChooser(ValueCallback<Uri> uploadMsg, String acceptType, String capture) {
    openFileChooser(uploadMsg, acceptType);
  }

  // For Android > 5.0
  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
    if (mOpenFileChooserCallBack != null) {
      mOpenFileChooserCallBack.showFileChooserCallBack(filePathCallback, fileChooserParams);
    }
    return true;
  }

  public interface OpenFileChooserCallBack {

    void openFileChooserCallBack(ValueCallback<Uri> uploadMsg, String acceptType);

    void showFileChooserCallBack(ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams);
  }
}