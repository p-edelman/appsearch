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

/** Adapter to provide the data of the search results and present it in the
 *  proper way.
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
    AppData app_data = m_app_data.get(position);
    
    // Find the app_result XML resource and extract the views for icon and text
    LayoutInflater inflater = (LayoutInflater)getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    View row_view = inflater.inflate(R.layout.app_result, parent, false);
    ImageView image_view = (ImageView) row_view.findViewById(R.id.AppIcon);
    TextView text_view   = (TextView)row_view.findViewById(R.id.AppName);
   
    // Set icon
    Drawable icon;
    try {
      icon = m_package_manager.getApplicationIcon(app_data.package_name);
      image_view.setImageDrawable(icon);
    } catch (NameNotFoundException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

    // Set text; make the matching letters underlined and bold
    text_view.setText(app_data.name, TextView.BufferType.SPANNABLE);
    if (!m_render_clear) {
      Spannable spannable = (Spannable)text_view.getText();
      for (Integer index : app_data.char_matches) {
        spannable.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), index, index + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannable.setSpan(new UnderlineSpan(), index, index + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
      }
    }
    
    return row_view;
  }

  /** Special method to re-render the results list but without the highlighting
   *  of the matched characters. 
   */
  public void renderClear() {
    m_render_clear = true;
    notifyDataSetChanged();
  }
}
