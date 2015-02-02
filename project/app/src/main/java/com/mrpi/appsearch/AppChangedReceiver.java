package com.mrpi.appsearch;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

public class AppChangedReceiver extends BroadcastReceiver {

  @Override
  public void onReceive(Context context, Intent received_intent) {
    Log.d("AppSearch", "Something has changed in the apps");

    String action = received_intent.getAction();
    if (action == Intent.ACTION_UNINSTALL_PACKAGE ||
        action == Intent.ACTION_PACKAGE_FULLY_REMOVED) {
      if (!(received_intent.getBooleanExtra(Intent.EXTRA_REPLACING, false))) { // It's no upgrade
        // Remove package from launch cache
        Uri data = received_intent.getData();
        String pkg_name = data.getEncodedSchemeSpecificPart(); // URL has the form of: "package:package_url"
        Log.d("AppSearch", "Package '" + pkg_name + "' has been uninstalled, removing it from the database");
        DBHelper db_helper = DBHelper.getInstance(context);
        db_helper.removePackage(pkg_name);
      }
    }
    Intent app_index_intent = new Intent(context, AppIndexService.class);
    context.startService(app_index_intent);
  }
}
