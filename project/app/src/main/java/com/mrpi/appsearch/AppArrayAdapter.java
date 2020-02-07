package com.mrpi.appsearch;

import java.util.List;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.drawable.Drawable;
import android.text.Spannable;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

/** Adapter to provide the data of the search results and render it for the results list. This
 *  adapter will revert the order of the items to show the results list as a bottom-to-top list.
 */
public class AppArrayAdapter extends ArrayAdapter<AppData> {

  // The list of application data we need to format.
  private List<AppData> m_app_data;
  
  // The central package manager
  private PackageManager m_package_manager;

  // Flag to indicate whether the matched characters should be highlighted.
  private boolean m_render_clear;
  
  public AppArrayAdapter(Context context, int textview_resource_id, List<AppData> app_data) {
    super(context, textview_resource_id, app_data);
    m_app_data        = app_data;
    m_package_manager = context.getPackageManager();
    m_render_clear    = false;
  }

  /** Format a single app from the list and return it as a {@link View} that can
   *  be attached to a GUI.
   *  @param position in the app list.
   *  @param convert_view a view to reuse. This parameter is not used.
   *  @param parent a parent to attach the returned view to.
   *  @return a {@link View} that can be attached to a GUI.
   */
  @Override
  public View getView(int position, View convert_view, ViewGroup parent) {
    while (position < m_app_data.size()) {
      AppData app_data = getItem(position);
      View row_view = renderRow(getRevertedPosition(position) + 1, app_data, parent);
      if (row_view != null) {
        return row_view;
      } else {
        // App is not installed anymore
        DBHelper db_helper = DBHelper.getInstance(getContext());
        db_helper.removePackage(app_data.package_name);
        m_app_data.remove(getRevertedPosition(position));
        notifyDataSetChanged();
      }
    }

    // Return empty row when one app was removed from the list
    LayoutInflater inflater = (LayoutInflater)getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    return inflater.inflate(R.layout.app_result, parent, false);
  }

  /** Convert the position in the adapter to a "reverted" position in the AppData list, which is
   *  needed to create a bottom-to-top list.
   * @param position the position in the adapter
   * @return the corresponding position in the AppData list. Position 0 will be converted to
   *         the last position in the list, position 1 to the second-to-last position, etc. */
  private int getRevertedPosition(int position) {
    return m_app_data.size() - position - 1;
  }

  /** Overridden method to map a position in the adapter to a bottom-to-top item in the AppData
   *  list. This is needed to create the bottom-to-top list view. */
  @Override
  public AppData getItem(int position) {
    if (position < m_app_data.size()) {
      return m_app_data.get(getRevertedPosition(position));
    }

    return null;
  }

  /** Render a single row.
   *  @param rank The human readable rank of the app in the results list
   *  @param app_data The AppData object describing the app
   *  @param parent The parent view to attach the view to
   *  @return the rendered view
   */
  private View renderRow(int rank, AppData app_data, ViewGroup parent) {
    // Find the app_result XML resource and extract the views for icon and text
    LayoutInflater inflater = (LayoutInflater)getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    View row_view = inflater.inflate(R.layout.app_result, parent, false);
    TextView rank_view   = (TextView)row_view.findViewById(R.id.AppRank);
    ImageView image_view = (ImageView) row_view.findViewById(R.id.AppIcon);
    TextView text_view   = (TextView)row_view.findViewById(R.id.AppName);

    // Render rank
    rank_view.setText(rank + ".");

    Drawable icon;
    try {
      // Set icon
      icon = m_package_manager.getApplicationIcon(app_data.package_name);
      image_view.setImageDrawable(icon);
    } catch (NameNotFoundException e) {
      return null;
    }

    // Set text; make the matching letters underlined and bold
    text_view.setText(app_data.name, TextView.BufferType.SPANNABLE);
    if (!m_render_clear && app_data.char_matches != null) {
      Spannable spannable = (Spannable)text_view.getText();
      for (Integer index : app_data.char_matches) {
        spannable.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), index, index + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannable.setSpan(new UnderlineSpan(), index, index + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
      }
    }

    return row_view;
  }

  /** Special method to re-render the results list but without the highlighting
    *  of the matched characters. */
  public void renderClear() {
    m_render_clear = true;
    notifyDataSetChanged();
  }
}
