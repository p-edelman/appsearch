package com.mrpi.appsearch;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;

/**
 * Class to detect device rotation changes and notify the SmartIcon's of this.
 */
public class SmartIconRotationService extends Service {
    @Override
    public void onCreate() {
        super.onCreate();

        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Intent update_intent = new Intent(context, SmartIcon.class);
                update_intent.setAction("ACTION_WIDGET_UPDATE");
                context.sendBroadcast(update_intent);
            }
        };

        IntentFilter filter = new IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED);
        registerReceiver(receiver, filter);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
