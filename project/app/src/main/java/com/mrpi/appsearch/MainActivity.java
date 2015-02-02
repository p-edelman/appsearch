package com.mrpi.appsearch;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.app.FragmentManager;
import android.app.ProgressDialog;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SearchView;

/** The main Activity for the app.
 *  <p>
 *  To make the app speedy and snappy, parallelization is employed in two ways.
 *  The first is for indexing the installed apps. This is delegated to a
 *  {@link AppIndexService} running in the background. The tradeoff is accuracy:
 *  while the background service is running (which may take up to tens of
 *  seconds), the search is performed on a cached index.
 *  <p>
 *  Also searching and subsequently formatting the results is done in a parallel
 *  process through {@link SearchFuzzyTextThread}. This prevents the keyboard from
 *  blocking when a search is performed. Although this is quite fast in general,
 *  it can sometimes hang when starting up. When more characters are typed while
 *  the current search isn't finished, it is aborted, further speeding up the
 *  search.
 */
public class MainActivity
        extends Activity
        implements SearchThreadListener {

  // Class variables
  private SearchView     m_search_view;     // The GUI SearchView element
  private ListView       m_results_view;    // The GUI ListView to present the
                                            // results of the search.
  private SearchThread   m_search_thread;   // The background thread to perform
                                            // the search. It is needed to keep
                                            // this instance so it can be
                                            // cancelled when a new query
                                            // arrives.
  private ProgressDialog m_launch_progress; // When the user clicks on a result,
                                            // a waiting spinner is presented
                                            // to let her know something is
                                            // happening.
  private AboutDialog    m_about_dialog;    // The "about" dialog.

  private CountAndDecay m_count_decay = null;

  private final static String SECRET_SEND_DATA = "send database";

  @Override
  protected void onCreate(Bundle saved_instance) {
    super.onCreate(saved_instance);
    setContentView(R.layout.activity_main);

    // Attach the search system to the SearchView in the layout
    SearchManager search_manager = (SearchManager)getSystemService(Context.SEARCH_SERVICE);
    m_search_view = (SearchView)findViewById(R.id.appSearchView);
    m_search_view.setSearchableInfo(search_manager.getSearchableInfo(getComponentName()));
    m_results_view = (ListView)findViewById(R.id.resultsListView);
   
    // Attach a listener for when the user starts typing or presses the search
    // button.
    final SearchView.OnQueryTextListener queryTextListener = new SearchView.OnQueryTextListener() {
      @Override
      public boolean onQueryTextChange(String query) {
        doSearch(query);
        return true;
      }

      @Override
      public boolean onQueryTextSubmit(String query) {
        if (query.equals(SECRET_SEND_DATA)) {
          sendUsageData();
        } else {
          doSearch(query);
        }
        return true;
      }
    };
    m_search_view.setOnQueryTextListener(queryTextListener);

    // Attach a listener for when the user clicks on a search result to launch
    // the app.
    final AdapterView.OnItemClickListener click_listener = new AdapterView.OnItemClickListener() {
      @Override
      public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        launchApp(parent, position);
      }
    };
    m_results_view.setOnItemClickListener(click_listener);  

    // Instantiate the waiting dialog
    m_launch_progress = new ProgressDialog(this);
    m_launch_progress.setIndeterminate(true);
    m_launch_progress.setCancelable(false);
  }
  
  @Override
  protected void onResume() {
    // App should have been reset on exit, but occasionally the app is not
    // stopped, so no reset was fired. To make sure, we reset it once again.
    reset();
    Log.d("Status", "App restarted");

    // Clear the results list
    AppArrayAdapter adapter = (AppArrayAdapter)m_results_view.getAdapter();
    if (adapter != null) {
      adapter.clear();
    }

    String starting_action = getIntent().getAction();
    if (starting_action != null &&
            (starting_action.equals(Intent.ACTION_MAIN) ||
                    starting_action.equals(Intent.ACTION_ASSIST))) {
      m_search_thread = new SearchMostUsedThread(this, this);
      m_search_thread.execute();
    }

    // Every time onResume is called, the apps are indexed again.
    Intent app_index_intent = new Intent(this, AppIndexService.class);
    startService(app_index_intent);

    super.onResume();
  }

  @Override
  protected void onStop() {
    // Clear everything for fresh search when we start up again.
    //reset();
    Log.d("Status", "App stopped");
    super.onStop();
  }

  /** Reset the app for a fresh search: clear results list, search box and
   *  progress spinner.
   */
  private void reset() {
    Log.d("Reset", "Resetting");
    // Clear results
    AppArrayAdapter adapter = (AppArrayAdapter)m_results_view.getAdapter(); 
    if (adapter != null) {
      adapter.clear();
    }
    m_search_view.setQuery("", false);
    
    if (m_about_dialog != null) {
      m_about_dialog.dismiss();
    }

    // Remove progress dialog
    m_launch_progress.dismiss();
    Log.d("Reset", "Everything clean");
  }
  
  /** Start a {link SearchFuzzyTextThread} to perform a fuzzy match on the given query.
   *  If a search was still running, it is cancelled.
   *  @param query the list of characters to search for in an app name.
   */
  private void doSearch(final String query) {  
    if (query.length() > 0) {
      if (m_search_thread != null) {
        m_search_thread.cancel(true);
      }
      m_search_thread = new SearchFuzzyTextThread(this, this);
      m_search_thread.execute(query);
    } else {
      // If the user clears the view, we don't clean up the list of results but
      // we remove the highlighting of the matched letters.
      AppArrayAdapter adapter = ((AppArrayAdapter)m_results_view.getAdapter());
      if (adapter != null) {
        adapter.renderClear();
      }
    }
  }
  
  /** Launch the app that the user clicks on in the result list.
   * @param parent the AdapterView that's the parent of the ListView
   * @param position the position of the item that's been clicked
   */
  private void launchApp(AdapterView<?> parent, int position) {
    // Get the app name from the GUI list
    final String name         = ((AppData)parent.getItemAtPosition(position)).name;
    final String package_name = ((AppData)parent.getItemAtPosition(position)).package_name;
    Log.d("AppSearch", "Launching app " + name);
  
    // Show a waiting dialog
    m_launch_progress.setTitle(String.format(getString(R.string.launching_app), name));
    m_launch_progress.setMessage(getString(R.string.please_wait));
    m_launch_progress.show();

    // Save the launch time slot to the database
    if (m_count_decay == null) {
      m_count_decay = new CountAndDecay(DBHelper.getInstance(this));
    }
    m_count_decay.countAppLaunch(name, package_name);
      
    // Now, launch the app.
    Intent launch_intent = getPackageManager().getLaunchIntentForPackage(package_name);
    startActivity(launch_intent);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.activity_main, menu);
    return true;
  }
  
  @Override
  public boolean onOptionsItemSelected (MenuItem item) {
    if (item.getItemId() == R.id.menu_about) {
      FragmentManager fm = getFragmentManager();
      if (m_about_dialog == null) {
        m_about_dialog = new AboutDialog();
      }
      m_about_dialog.show(fm, "dialog_about");

      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  private void sendUsageData() {
    boolean file_copied = false;
    File external_file = new File(getExternalCacheDir().toString(), "apps.sqlite");
    if (external_file.exists()) {
      external_file.delete();
    }

    // Copy the db to a readable location
    InputStream  is = null;
    OutputStream os = null;
    try {
      external_file.createNewFile();
      DBHelper db_helper = DBHelper.getInstance(this);
      is = new FileInputStream(db_helper.getReadableDatabase().getPath());
      os = new FileOutputStream(external_file);
      // Transfer bytes from in to out
      byte[] buf = new byte[1024];
      int len;
      while ((len = is.read(buf)) > 0) {
        os.write(buf, 0, len);
      }
      is.close();
      os.close();
      file_copied = true;
    } catch (IOException e) {
    }

    if (file_copied) {
      // Create the intent
      Intent intent = new Intent(android.content.Intent.ACTION_SEND);
      intent.setType("*/*");
      intent.putExtra(android.content.Intent.EXTRA_SUBJECT, "AppSearch database");

      // Attach the file to the intent
      Uri db_uri = Uri.fromFile(external_file);
      intent.putExtra(android.content.Intent.EXTRA_STREAM, db_uri);

      // Run the intent
      startActivity(Intent.createChooser(intent, "Share via"));
    } else {
      Log.e("AppSearch", "Couldn't copy the database file");
    }
  }

  /** We need to find out how the activity was brought to the foreground; by
   *  the widget or by a launcher/search button. So we need to set the starting
   *  intent to the intent that brought it to the foreground. */
  @Override
  protected void onNewIntent(Intent new_intent) {
    setIntent(new_intent);
  }

  public void onSearchThreadFinished(ArrayList<AppData> apps, Context context) {
    AppArrayAdapter adapter = new AppArrayAdapter(this, R.id.resultsListView, apps);
    ListView results_list_view = (ListView)findViewById(R.id.resultsListView);
    results_list_view.setAdapter(adapter);
  }
}
