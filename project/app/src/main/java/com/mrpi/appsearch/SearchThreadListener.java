package com.mrpi.appsearch;

import android.content.Context;

import java.util.ArrayList;

/**
 * Interface to implement a listener for AsyncTasks that provide a list of
 * AppData.
 */
public interface SearchThreadListener {
    public void onSearchThreadFinished(ArrayList<AppData> apps,
                                       Context context);
}
