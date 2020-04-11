package com.mrpi.appsearch;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.RemoteViews;


/**
 * The search button is a pretty lame widget actually: it launches the main
 * app.
 * So it's in effect not much more than a shortcut icon. However, it is
 * cosmetically somewhat different (you perform an action instead of opening an
 * app). Also, the launch intent action is set to ACTION_WIDGET_SEARCH. The
 * main app can use this information to determine (for example, to determine
 * whether it should be pre-filled with most used apps).
 */
public class SearchButton extends AppWidgetProvider {

    /** Intent actions defined within this class. Because of the widgetarchitecture in Android,
     *  interacting with the widget is done via intents. We capture and handle them directly within
     *  this class. */
    public static String ACTION_WIDGET_SEARCH = "ACTION_WIDGET_SEARCH";

    @Override
    public void onUpdate(Context context,
                         AppWidgetManager manager,
                         int[] widget_ids) {
        Log.d("Widget", "Installing search widget");

        // Instantiate the RemoteViews object for the app widget layout.
        RemoteViews views = new RemoteViews(context.getPackageName(),
                R.layout.search_button);

        // Attach touch listener
        Intent intent = new Intent(context, SearchButton.class);
        intent.setAction(ACTION_WIDGET_SEARCH);
        PendingIntent pending_intent = PendingIntent.getBroadcast(context, -1,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        views.setOnClickPendingIntent(R.id.widget_icon, pending_intent);
        views.setOnClickPendingIntent(R.id.widget_text, pending_intent);

        for (int i = 0; i < widget_ids.length; i++) {
            manager.updateAppWidget(widget_ids[i], views);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(ACTION_WIDGET_SEARCH)) {
            // Open the search app
            Intent launch_intent = new Intent(context, MainActivity.class);
            launch_intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(launch_intent);
        } else {
            super.onReceive(context, intent);
        }
    }
}
