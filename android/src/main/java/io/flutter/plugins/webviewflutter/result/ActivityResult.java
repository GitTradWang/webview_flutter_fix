package io.flutter.plugins.webviewflutter.result;

import android.app.Activity;
import android.app.FragmentManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import androidx.annotation.NonNull;


public class ActivityResult {

  private static final String TAG = ActivityResult.class.getSimpleName();

  static final int REQUEST_CODE = 1000;

  ResultFragment resultFragment;

  public ActivityResult(Context context) {
    Activity activity = getActivity(context);

    if (activity != null) {
      resultFragment = getSingleton(activity.getFragmentManager());
    }
  }


  public ActivityResult startActivityForResult(Intent intent) {
    resultFragment.startActivityForResult(intent, REQUEST_CODE);
    return this;
  }

  public void setOnActivityResult(ActivityResultCall call) {
    resultFragment.setResultCall(call);
  }

  @NonNull
  private ResultFragment getSingleton(@NonNull final FragmentManager fragmentManager) {

    if (resultFragment == null) {
      resultFragment = getRxPermissionsFragment(fragmentManager);
    }
    return resultFragment;
  }

  private ResultFragment getRxPermissionsFragment(@NonNull final FragmentManager fragmentManager) {
    ResultFragment resultFragment = findRxPermissionsFragment(fragmentManager);
    boolean isNewInstance = resultFragment == null;
    if (isNewInstance) {
      resultFragment = new ResultFragment();

      fragmentManager.executePendingTransactions();
      if (VERSION.SDK_INT >= VERSION_CODES.N) {
        fragmentManager
            .beginTransaction()
            .add(resultFragment, TAG)
            .commit();
        fragmentManager.executePendingTransactions();
      }
    }
    return resultFragment;
  }

  private ResultFragment findRxPermissionsFragment(@NonNull final FragmentManager fragmentManager) {
    return (ResultFragment) fragmentManager.findFragmentByTag(TAG);
  }


  private static Activity getActivity(Context context) {

    if (context instanceof Activity) {
      return (Activity) context;
    }
    if (context instanceof ContextWrapper) {
      ContextWrapper wrapper = (ContextWrapper) context;
      return getActivity(wrapper.getBaseContext());
    } else {
      return null;
    }
  }
}
