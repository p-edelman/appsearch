package com.mrpi.appsearch;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

public class AppRemovedReceiver extends BroadcastReceiver {

  @Override
  public void onReceive(Context context, Intent received_intent) {
    Log.d("AppSearch", "Got a remove intent");
    if (!received_intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) { // It's a real uninstall, not an upgrade
      // URL has the form of: "package:package_url"
      Uri data = received_intent.getData();
      String pkg_name = data.getEncodedSchemeSpecificPart();
      Log.d("AppSearch", "Package '" + pkg_name + "' has been uninstalled, removing it from the database");
      AppCacheOpenHelper cache = AppCacheOpenHelper.getInstance(context);
      cache.removePackage(pkg_name);
    }
  }
}
