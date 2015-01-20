package com.mrpi.appsearch;

import android.content.Intent;
import android.widget.RemoteViewsService;

public class MostUsedWidgetService extends RemoteViewsService {

  @Override
  public RemoteViewsFactory onGetViewFactory (Intent intent) {
    return new MostUsedRemoteViewsFactory(this.getApplicationContext(), intent);
  }
}
