package com.mrpi.appsearch;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class AppsChangedReceiver extends BroadcastReceiver {

  @Override
  public void onReceive(Context context, Intent received_intent) {
    Log.d("AppSearch", "Something has changed in the apps, re-indexing");
    Intent app_index_intent = new Intent(context, AppIndexService.class);
    context.startService(app_index_intent);
  }

}
