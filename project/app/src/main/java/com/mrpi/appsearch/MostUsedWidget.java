package com.mrpi.appsearch;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Log;
import android.widget.RemoteViews;

public class MostUsedWidget extends AppWidgetProvider {

  @Override
  public void onUpdate(Context          context,
                       AppWidgetManager widget_manager,
                       int[]            widget_ids) {
    // There may be multiple widgets active, so update all of them
    for (int i = 0; i < widget_ids.length; i++) {
      updateAppWidget(context, widget_manager, widget_ids[i]);
    }
    super.onUpdate(context, widget_manager, widget_ids);
  }

  @Override
  public void onEnabled(Context context) {
    // Enter relevant functionality for when the first widget is created
    super.onEnabled(context);
  }

  @Override
  public void onDisabled(Context context) {
    // Enter relevant functionality for when the last widget is disabled
    super.onDisabled(context);
  }

  static void updateAppWidget(Context          context,
                              AppWidgetManager widget_manager,
                              int              widget_id) {

    Log.d("AppSearch", "Updating widget " + widget_id);
    // Set up the intent that starts the StackViewService, which will
    // provide the views for this collection.
    Intent intent = new Intent(context, MostUsedWidgetService.class);

    // Add the app widget ID to the intent extras.
    intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widget_id);
    intent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));

    // Instantiate the RemoteViews object for the app widget layout.
    RemoteViews remote_views = new RemoteViews(context.getPackageName(),
                                               R.layout.most_used_widget);

    // Set up the RemoteViews object to use a RemoteViews adapter.
    // This adapter connects
    // to a RemoteViewsService  through the specified intent.
    // This is how you populate the data.
    remote_views.setRemoteAdapter(R.id.most_used_widget_list, intent);

    // The empty view is displayed when the collection has no items.
    // It should be in the same layout used to instantiate the RemoteViews
    // object above.
    //rv.setEmptyView(R.id.stack_view, R.id.empty_view);

    // Instruct the widget manager to update the widget
    widget_manager.updateAppWidget(widget_id, remote_views);
  }
}


