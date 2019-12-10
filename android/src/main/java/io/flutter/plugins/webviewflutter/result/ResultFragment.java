package io.flutter.plugins.webviewflutter.result;

import android.app.Fragment;
import android.content.Intent;

public class ResultFragment extends Fragment {

  ActivityResultCall call;


  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    if (requestCode == ActivityResult.REQUEST_CODE && call != null) {
      call.onResult(data,resultCode);
    }

  }

  public void setResultCall(ActivityResultCall call) {
    this.call = call;
  }
}
