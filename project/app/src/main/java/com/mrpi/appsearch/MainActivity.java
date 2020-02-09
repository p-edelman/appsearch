package com.mrpi.appsearch;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

import android.app.Activity;
import android.app.FragmentManager;
import android.app.ProgressDialog;
import android.app.SearchManager;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

/** The main Activity for the app.
 *  <p>
 *  To make the app speedy and snappy, parallelization is employed in two ways.
 *  The first is for indexing the installed apps. This is delegated to a
 *  {@link AppIndexService} running in the background. The trade-off is
 *  accuracy: while the background service is running (which may take up to tens
 *  of seconds), the search is performed on a cached index.
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

  /** The maximum number of most used apps to show when opening the activity. */
  private static final int MAX_TOP_APPS = 4;

  // Class variables
  private EditText       m_input_box;       // The GUI EditText where the user types the query
  private ListView       m_results_view;    // The GUI ListView to present the results of the search
  private SearchThread   m_search_thread;   // The background thread to perform the search. It is
                                            // needed to keep this instance so it can be
                                            // cancelled when a new query arrives.
  private ProgressDialog m_launch_progress; // When the user launches an app, a waiting spinner is
                                            // presented to let her know something is happening.
  private AboutDialog    m_about_dialog;    // The "about" dialog.

  private CountAndDecay  m_count_decay = null;

  /** Command that can be typed into the search box for additional functionality */
  private final static String COMMAND_SEND_DATA        = "send database";
  private final static String COMMAND_COLLECT_RAW      = "collect raw data";
  private final static String COMMAND_DONT_COLLECT_RAW = "don't collect raw data";

  /** Keys for individual preferences */
  private final static String PREFS_COLLECT_RAW = "collect_raw_data";

  @Override
  protected void onCreate(Bundle saved_instance) {
    // We actually never want to restore state, we should always come up as
    // clean as possible, so we pass in null here.
    super.onCreate(null);

    setContentView(R.layout.activity_main);

    // The two main GUI elements: the text box and the result list
    m_input_box     = (EditText)findViewById(R.id.appSearchView);
    m_results_view  = (ListView)findViewById(R.id.resultsListView);

    // Attach a listener for when the user starts typing.
    m_input_box.addTextChangedListener(new TextWatcher() {
      @Override
      public void onTextChanged(CharSequence char_sequence, int start, int before, int count) {
        doSearch(char_sequence.toString());
      }

      @Override
      public void beforeTextChanged(CharSequence char_sequence, int start, int count, int after) {}

      @Override
      public void afterTextChanged(Editable editable) {}
    });

    // Add listener for the action button
    m_input_box.setOnEditorActionListener(new TextView.OnEditorActionListener() {
      @Override
      public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
        String query = textView.getText().toString();
        if (query.equals(COMMAND_SEND_DATA)) {
          sendUsageData();
        } else if (query.equals(COMMAND_COLLECT_RAW) || query.equals(COMMAND_DONT_COLLECT_RAW)) {
          boolean collect_raw = query.equals(COMMAND_COLLECT_RAW);
          SharedPreferences.Editor editor = getPreferences(Context.MODE_PRIVATE).edit();
          editor.putBoolean(PREFS_COLLECT_RAW, collect_raw);
          if (m_count_decay != null) {
            m_count_decay.setRawDataCollection(collect_raw);
          }
          editor.apply();

          String msg;
          if (collect_raw) {
            msg = "All app openings will be saved from now on";
          } else {
            msg = "App openings won't be saved anymore";
          }
          Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
        } else {
          // If there are results, launch the top one
          if (m_results_view.getCount() > 0) {
            launchApp((AppData)m_results_view.getAdapter().getItem(0));
          }
        }
        return true;
      }
    });

    // Attach a listener for when the user clicks on a search result to launch
    // the app.
    final AdapterView.OnItemClickListener click_listener = new AdapterView.OnItemClickListener() {
      @Override
      public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        launchApp((AppData)parent.getItemAtPosition(position));
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

    // Every time onResume is called, the apps are indexed again.
    Intent app_index_intent = new Intent(this, AppIndexService.class);
    startService(app_index_intent);

    // If we were not called from the widget, populate with the top apps for the
    // moment.
    String starting_action = getIntent().getAction();
    if (starting_action != null &&
            (starting_action.equals(Intent.ACTION_MAIN) ||
             starting_action.equals(Intent.ACTION_ASSIST))) {
      m_search_thread = new SearchMostUsedThread(this, this);
      m_search_thread.execute(MAX_TOP_APPS);
    }

    // Make sure the user can start typing right away
    m_input_box.requestFocus();

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
    AppArrayAdapter adapter = ((AppArrayAdapter)m_results_view.getAdapter());
    if (adapter != null) {
      adapter.clear();
      adapter.notifyDataSetChanged();
    }
    m_input_box.setText("");
    
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
  
  /** The entry point to launch an app. This takes care of all the housekeeping and makes sure this
   *  app is properly closed before launching the other app.
   */
  private void launchApp(AppData app_data) {
    // Get the app name from the GUI list
    final String name         = app_data.name;
    final String package_name = app_data.package_name;
    Log.d("AppSearch", "Launching app " + name);
  
    // Show a waiting dialog
    m_launch_progress.setTitle(String.format(getString(R.string.launching_app), name));
    m_launch_progress.setMessage(getString(R.string.please_wait));
    m_launch_progress.show();

    // Save the launch time slot to the database
    if (m_count_decay == null) {
      m_count_decay = new CountAndDecay(DBHelper.getInstance(this));
      m_count_decay.setRawDataCollection(getPreferences(Context.MODE_PRIVATE).getBoolean(PREFS_COLLECT_RAW, false));
    }
    m_count_decay.countAppLaunch(package_name);

    // Now, launch the app.
    Intent launch_intent = getPackageManager().getLaunchIntentForPackage(package_name);
    launch_intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    startActivity(launch_intent);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.activity_main, menu);
    return true;
  }

  public boolean onPrepareOptionsMenu (Menu menu) {
    // Disable enable smart icons entry based on whether there are smart
    // icons.
    ComponentName component  = new ComponentName(this, SmartIcon.class);
    AppWidgetManager manager = AppWidgetManager.getInstance(this);
    int num_smart_icons = manager.getAppWidgetIds(component).length;
    if (num_smart_icons == 0) {
      menu.findItem(R.id.menu_smart_icon_settings).setVisible(false);
    } else {
      menu.findItem(R.id.menu_smart_icon_settings).setVisible(true);
    }
    
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
    } else if (item.getItemId() == R.id.menu_smart_icon_settings) {
      Intent settings_intent = new Intent(this, SmartIconConfig.class);
      startActivity(settings_intent);
      finish(); // We need to hide the main activity to show the configuration
                // dialog on the home screen, so the user can actually see what
                // happens to the icons.
    }
    return super.onOptionsItemSelected(item);
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

  private void sendUsageData() {
    boolean file_copied = false;
    File external_file = new File(getExternalCacheDir().toString(), "AppSearch.sqlite");
    if (external_file.exists()) {
      external_file.delete();
    }

    // Copy the db to a readable location
    InputStream  is = null;
    OutputStream os = null;
    try {
      external_file.createNewFile();
      SQLiteDatabase db = DBHelper.getInstance(getApplicationContext()).getReadableDatabase();
      is = new FileInputStream(db.getPath());
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
}
