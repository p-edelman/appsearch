package com.mrpi.appsearch;

import android.content.Context;
import android.os.AsyncTask;

import java.util.ArrayList;

/** Base class for background search threads that look for apps in the database.
 *  Classes take in an application context and a SearchThreadListener; on finishing the search,
 *  the onSearchThreadFinished() method is called with a list of AppData objects and the context. */
public abstract class SearchThread extends AsyncTask<Object, Void, ArrayList<AppData>> {
  protected Context              m_context;
  protected SearchThreadListener m_listener;

  /** Instantiate the class.
   * @param context an application context where this class can run in (and
   *                that provides access to the private database of tha app).
   * @param listener a SearchThreadListener instance whose
   *                 onSearchThreadFinished() method is called upon completion
   *                 with the result of the search as a parameter.
   *                 This parameter may be null, in which case basically nothing
   *                 is done with the result. */
  public SearchThread(Context context,
                      SearchThreadListener listener) {
    m_context  = context;
    m_listener = listener;
  }


  /** When done, communicate the results back to the caller by calling its
   *  onSearchThreadFinished() method. */
  protected void onPostExecute(ArrayList<AppData> apps) {
    if (m_listener != null) {
      m_listener.onSearchThreadFinished(apps, m_context);
    }
  }
}
