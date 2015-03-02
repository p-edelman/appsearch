package com.mrpi.appsearch;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Spinner;

/** Activity to configure a smart icon.
 *  The activity is a transparent dialog, so the icon (widget) can be seen
 *  while the settings are changed. */
public class SmartIconConfig extends Activity {

  @Override
  protected void onCreate(Bundle saved_state) {
    super.onCreate(saved_state);
    setContentView(R.layout.activity_smart_icon_config);

    // Get the preferences
    final SharedPreferences preferences = getSharedPreferences(SmartIcon.SMART_ICON_PREFERENCES,
            Context.MODE_MULTI_PROCESS);

    // Populate the spinner with the list defined in strings.xml
    Spinner options_spinner = (Spinner)findViewById(R.id.layout_spinner);
    ArrayAdapter<CharSequence> options_adapter =
            ArrayAdapter.createFromResource(this, R.array.smart_icon_layouts_public_names,
                                            android.R.layout.simple_spinner_item);
    options_adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    options_spinner.setAdapter(options_adapter);
    int selected_item = preferences.getInt(SmartIcon.SMART_ICON_LAYOUT, 0);
    options_spinner.setSelection(selected_item);
    options_spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> parent_view,
                                 View           view,
                                 int            position,
                                 long           row_id) {

        // Write the selected index to the preferences. It can be matched with
        // the index of the smart_icon_layouts_resource_names string array
        // resource.
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt(SmartIcon.SMART_ICON_LAYOUT, position);
        editor.apply();

        // Update all active widgets with the new layout, so that the user can
        // see the results
        Intent update_intent = new Intent(SmartIconConfig.this, SmartIcon.class);
        update_intent.setAction(SmartIcon.ACTION_WIDGET_UPDATE);
        sendBroadcast(update_intent);
      }

      @Override public void onNothingSelected(AdapterView<?> parent_view) {}
    });

    // Handle the checkbox
    CheckBox dont_repeat_box = (CheckBox)findViewById(R.id.dont_repeat_checkbox);
    dont_repeat_box.setChecked(!preferences.getBoolean(SmartIcon.SMART_ICON_CONFIG_SHOW, true));
    dont_repeat_box.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
      @Override
      public void onCheckedChanged(CompoundButton compound_button,
                                   boolean        checked) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(SmartIcon.SMART_ICON_CONFIG_SHOW,
                          !checked);
        editor.apply();
      }
    });

    // The close button dismisses the "dialog".
    Button dismiss_button = (Button)findViewById(R.id.dismiss_button);
    dismiss_button.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
      finish();
      }
    });
  }
}
