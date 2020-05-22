package com.mrpi.appsearch;

import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import android.app.Activity;
import android.app.FragmentManager;
import android.app.ProgressDialog;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * The main Activity for the app.
 *
 * To make the app speedy and snappy, parallelization is employed in two ways.
 * The first is for indexing the installed apps. This is delegated to a {@link AppIndexService}
 * running in the background. The trade-off is accuracy: while the background service is running
 * (which may take up to tens of seconds), the search is performed on a cached index.
 *
 * Also searching and subsequently formatting the results is done in a background threads. This
 * prevents the UI from blocking when a search is performed. Although searching is quite fast in
 * general, it can sometimes hang when starting up. When more characters are typed, the running
 * search is aborted, further speeding up the search.
 */
public class MainActivity
        extends Activity {

    /** The maximum number of most used apps to show when opening the activity. */
    private static final int MAX_TOP_APPS = 4;

    // The GUI EditText where the user types the query
    private InputBox m_input_box;

    // The GUI ListView to present the results of the search
    private ListView m_results_view;

    // Thread pool for our asynchronous search operations
    private ExecutorService m_executor_service = Executors.newCachedThreadPool();

    // The background thread to perform the search. It is needed to keep this instance so it can be
    // cancelled when a new query arrives.
    private Future<?> m_search_future;

    // The list of matched apps or commands
    private ArrayList<? extends SearchResult> m_search_results;

    // When the user launches an app, a waiting spinner is presented to let her know something is
    // happening.
    private ProgressDialog m_launch_progress;

    // The "about" dialog.
    private AboutDialog m_about_dialog;

    private CountAndDecay m_count_decay = null;

    /**
     * Keys for individual preferences
     */
    private final static String PREFS_COLLECT_RAW_CLICKS = "collect_raw_clicks";

    @Override
    protected void onCreate(Bundle saved_instance) {
        // First, make sure we can catch all unhandled Exception
        Thread.setDefaultUncaughtExceptionHandler(new ExceptionLogger(this));

        // We actually never want to restore state, we should always come up as clean as possible,
        // so we pass in null here.
        super.onCreate(null);

        setContentView(R.layout.activity_main);

        // The two main GUI elements: the text box and the result list
        m_input_box = (InputBox) findViewById(R.id.appSearchView);
        m_results_view = (ListView) findViewById(R.id.resultsListView);

        // Attach a listener for when the user starts typing.
        m_input_box.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence char_sequence, int start, int before, int count) {
                doFuzzySearch(char_sequence.toString());
            }

            @Override
            public void beforeTextChanged(CharSequence char_sequence, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });

        // Add listener for the action keyboard button
        m_input_box.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                if (m_search_results.size() > 0) {
                    launch(m_search_results.get(0));
                }

                return true;
            }
        });

        // Add a similar listener for the "go" button
        Button launch_button = (Button) findViewById(R.id.launchButton);
        launch_button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (m_search_results.size() > 0) {
                    launch(m_search_results.get(0));
                }
            }
        });

        // Attach a listener for when the user clicks on a search result to launch
        // the app.
        final AdapterView.OnItemClickListener click_listener = new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                try {
                    launch((SearchResult) parent.getItemAtPosition(position));
                } catch (ClassCastException e) {}
            }
        };
        m_results_view.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                try {
                    launch((SearchResult) parent.getItemAtPosition(position));
                } catch (ClassCastException e) {
                    // Silently ignore this, since results are guaranteed to be derived from
                    // SearchResult.
                }
            }
        });

        // Instantiate the waiting dialog
        m_launch_progress = new ProgressDialog(this);
        m_launch_progress.setIndeterminate(true);
        m_launch_progress.setCancelable(false);
    }

    /**
     * Perform the action with the result that the user selected from the GUI. This is normally
     * an app that should be opened (captured using AppSearchResult), but may be a command
     * as well (a CommandSearchResult), in which case the proper action is dispatched.
     *
     * @param search_result the SearchResult that the user selected.
     */
    private void launch(SearchResult search_result) {
        if (search_result instanceof AppSearchResult) {
            launchApp((AppSearchResult)search_result);
        } else if (search_result instanceof CommandSearchResult) {
            CommandSearchResult.CommandCode code = ((CommandSearchResult) search_result).command;
            switch (code) {
                case EXPORT_DB:
                case EXPORT_STACKTRACES:
                    // Create an intent for sharing the db, attach a content:// uri with for the
                    // DebugContentProvider, and wrap the whole thing in a chooser so the user can
                    // select how to share the database.
                    Intent intent = new Intent(android.content.Intent.ACTION_SEND);
                    intent.setType("*/*");
                    if (code == CommandSearchResult.CommandCode.EXPORT_DB) {
                        intent.putExtra(android.content.Intent.EXTRA_SUBJECT, "AppSearch database");
                        intent.putExtra(android.content.Intent.EXTRA_STREAM, DebugContentProvider.getUriForAction(DebugContentProvider.Action.EXPORT_DB));
                    } else {
                        intent.putExtra(android.content.Intent.EXTRA_SUBJECT, "AppSearch stacktraces");
                        intent.putExtra(android.content.Intent.EXTRA_STREAM, DebugContentProvider.getUriForAction(DebugContentProvider.Action.EXPORT_STACKTRACES));
                    }
                    startActivity(Intent.createChooser(intent, "Share via"));
                    break;
                case COLLECT_RAW_CLICKS:
                case DONT_COLLECT_RAW_CLICKS:
                    boolean collect_raw = (code == CommandSearchResult.CommandCode.COLLECT_RAW_CLICKS);
                    SharedPreferences.Editor prefs_editor = getPreferences(Context.MODE_PRIVATE).edit();
                    prefs_editor.putBoolean(PREFS_COLLECT_RAW_CLICKS, collect_raw);
                    if (m_count_decay != null) {
                        m_count_decay.setRawDataCollection(collect_raw);
                    }

                    String toast;
                    if (collect_raw) {
                        toast = "All app openings will be saved to the database";
                    } else {
                        toast = "App openings won't be saved and are cleared from the database";
                        SQLiteDatabase db = DBHelper.getInstance(this).getReadableDatabase();
                        db.delete(DBHelper.TBL_RAW_DATA, null, null);
                    }
                    prefs_editor.apply();
                    Toast.makeText(getApplicationContext(), toast, Toast.LENGTH_LONG).show();
                    break;
            }
        }
    }

    @Override
    protected void onResume() {
        // App should have been reset on exit, but occasionally the app is not stopped, so no reset
        // was fired. To make sure, we reset it once again.
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
            doBackgroundSearch(() -> {
                MostUsedAppsSearcher searcher = new MostUsedAppsSearcher(this, MAX_TOP_APPS);
                return searcher.search();
            });
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

    /**
     * Reset the app for a fresh search: clear results list, search box and
     * progress spinner.
     */
    private void reset() {
        Log.d("Reset", "Resetting");
        // Clear results
        SearchResultArrayAdapter adapter = ((SearchResultArrayAdapter) m_results_view.getAdapter());
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

    /**
     * Perform a fuzzy match on the given query asynchronously.
     * If a search was still running, it is cancelled first.
     *
     * @param query the list of characters to search for in an app name.
     */
    private void doFuzzySearch(final String query) {
        if (query.length() > 0) {
            m_input_box.renderClear(false);
            doBackgroundSearch(() -> {
                FuzzyAppsSearcher app_searcher = new FuzzyAppsSearcher(this);
                ArrayList results = app_searcher.search(query);
                if (query.startsWith("/")) { // Magic character to _also_ search for commands
                    FuzzyCommandSearcher command_searcher = new FuzzyCommandSearcher(this);
                    results.addAll(command_searcher.search(query));
                }
                return results;
            });
        } else {
            // If the user clears the view, we don't clean up the list of results but we remove the
            // highlighting of the matched letters.
            SearchResultArrayAdapter adapter = ((SearchResultArrayAdapter) m_results_view.getAdapter());
            if (adapter != null) {
                adapter.renderClear();
            }

            m_input_box.renderClear(true);
        }
    }

    /**
     * The entry point to launch an app. This takes care of all the housekeeping and makes sure this
     * app is properly closed before launching the other app.
     */
    private void launchApp(AppSearchResult app_data) {
        // Get the app name from the GUI list
        final String name = app_data.name;
        final String package_name = app_data.package_name;
        Log.d("AppSearch", "Launching app " + name);

        // Show a waiting dialog
        m_launch_progress.setTitle(String.format(getString(R.string.launching_app), name));
        m_launch_progress.setMessage(getString(R.string.please_wait));
        m_launch_progress.show();

        // Save the launch time slot to the database
        if (m_count_decay == null) {
            m_count_decay = new CountAndDecay(DBHelper.getInstance(this));
            m_count_decay.setRawDataCollection(getPreferences(Context.MODE_PRIVATE).getBoolean(PREFS_COLLECT_RAW_CLICKS, false));
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

    public boolean onPrepareOptionsMenu(Menu menu) {
        // Disable enable smart icons entry based on whether there are smart
        // icons.
        ComponentName component = new ComponentName(this, SmartIcon.class);
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
    public boolean onOptionsItemSelected(MenuItem item) {
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

    /**
     * We need to find out how the activity was brought to the foreground; by
     * the widget or by a launcher/search button. So we need to set the starting
     * intent to the intent that brought it to the foreground.
     */
    @Override
    protected void onNewIntent(Intent new_intent) {
        setIntent(new_intent);
    }

    /** Perform a search for AppData in a background thread (using m_search_future). When finished,
     *  call onBackgroundSearchFinished on the UI thread to process the results. If a search is
     *  currently running, it will be cancelled first.
     *
     * @param callable a Callable that should return an ArrayList of AppData objects.
     */
    private <T extends SearchResult> void doBackgroundSearch(Callable<ArrayList<T>> callable) {
        if (m_search_future != null) m_search_future.cancel(true);
        m_search_future = m_executor_service.submit(() -> {
            try {
                ArrayList<T> results = callable.call();
                Handler main = new Handler(Looper.getMainLooper());
                main.post(() -> onBackgroundSearchFinished(results));
            } catch (Exception e) {
                Log.d("AppSearch", "Exception occurred", e);
                // TODO: report exception
            }
        });
    }

    /**
     * Callback for doBackgroundSearch to handle the results of an AppData search on the UI thread.
     * It will set the search box to the first result and the listview to the remainder of the
     * results.
     *
     * @param apps the result from the Callable being passed to doBackgroundSearch()
     */
    public <T extends SearchResult> void onBackgroundSearchFinished(ArrayList<T> apps) {
        m_search_results = apps;

        // Use the first result as the "selected" app
        Drawable icon = null;
        if (apps.size() > 0) {
            m_input_box.setMatchingSearchResult(apps.get(0));
            icon = apps.get(0).resolveIcon(this); // might be null
        } else {
            m_input_box.setMatchingSearchResult(null);
        }
        if (icon == null) {
            // Show an "error" icon if no actual icon could be found.
            icon = getResources().getDrawable(android.R.drawable.ic_delete);
        }
        ((ImageView) findViewById(R.id.selectedAppIcon)).setImageDrawable(icon);

        // Fill the list view with the rest of the results
        SearchResultArrayAdapter adapter;
        if (apps.size() > 0) {
            adapter = new SearchResultArrayAdapter(this, R.id.resultsListView, apps.subList(1, apps.size()));
        } else {
            adapter = new SearchResultArrayAdapter(this, R.id.resultsListView, new ArrayList<SearchResult>());
        }
        ListView results_list_view = (ListView) findViewById(R.id.resultsListView);
        results_list_view.setAdapter(adapter);
    }
}
