package com.mrpi.appsearch;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.maps.MapView;

/** Activity to configure a smart icon.
 *  The activity is a transparent dialog, so the icon (widget) can be seen
 *  while the settings are changed. */
public class SmartIconConfig extends Activity {

  /** The SharedPreferences object for all the smart icon preferences. */
  SharedPreferences m_preferences;

  /** The icon and text parts of the smart icon. */
  ImageView m_icon;
  TextView  m_text;

  /** These parameters define the layout. */
  private float   m_icon_size_f; // As a float for calculations, but it gets converted to int to actually use it
  private float   m_text_size;
  private boolean m_is_bold;
  private boolean m_is_italic;
  private int     m_icon_padding;
  private int     m_text_padding;

  /** We need to keep track of which element we're working on when zooming and
   *  dragging. */
  private enum Element {ICON, TEXT}
  private      Element m_element;

  /** We also need to keep track of which gesture is being performed. */
  private enum Motion {NONE, DRAG, ZOOM}

  @Override
  protected void onCreate(Bundle saved_state) {
    super.onCreate(saved_state);
    setContentView(R.layout.activity_smart_icon_config);

    // Initialize the UI components with their parameters
    m_icon = (ImageView)findViewById(R.id.config_icon);
    m_text = (TextView)findViewById(R.id.config_text);

    m_preferences = getSharedPreferences(SmartIcon.SMART_ICON_PREFERENCES,
                                         Context.MODE_MULTI_PROCESS);
    m_icon_size_f = (float)m_preferences.getInt(SmartIcon.ICON_SIZE,
            getResources().getDimensionPixelSize(android.R.dimen.app_icon_size));
    m_text_size = m_preferences.getFloat(SmartIcon.TEXT_SIZE,
            getResources().getDimensionPixelSize(R.dimen.smart_icon_text_size_default));
    m_icon_padding = m_preferences.getInt(SmartIcon.ICON_PADDING, 0);
    m_text_padding = m_preferences.getInt(SmartIcon.TEXT_PADDING, 0);
    m_is_bold      = m_preferences.getBoolean(SmartIcon.TEXT_BOLD, false);
    m_is_italic    = m_preferences.getBoolean(SmartIcon.TEXT_ITALIC, false);
    renderIcon();
    renderText();

    // Initialize the checkboxes
    final CheckBox bold_check = (CheckBox)findViewById(R.id.bold_checkbox);
    bold_check.setChecked(m_is_bold);
    bold_check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
      @Override
      public void onCheckedChanged(CompoundButton button_view, boolean is_checked) {
        m_is_bold = is_checked;
        renderText();
        updateWidgets();
      }
    });

    final CheckBox italic_check = (CheckBox)findViewById(R.id.italic_checkbox);
    italic_check.setChecked(m_is_italic);
    italic_check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
      @Override
      public void onCheckedChanged(CompoundButton button_view, boolean is_checked) {
        m_is_italic = is_checked;
        renderText();
        updateWidgets();
      }
    });

    // Attach movement listeners to the general, outer box (so we can detect
    // pinch gestures all over the window instead of only the elements
    // themselves).
    final LinearLayout box = (LinearLayout) findViewById(R.id.config_icon_box);
    ScaleGestureDetector scale_detector = new ScaleGestureDetector(this, new ScaleListener());
    box.setOnTouchListener(new TouchListener(scale_detector));

    // The close button dismisses the "dialog".
    Button dismiss_button = (Button) findViewById(R.id.dismiss_button);
    dismiss_button.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        finish();
      }
    });
  }

  /** After updating the icon parameters, render the icon with these new
   *  settings. */
  private void renderIcon() {
    m_icon.setPadding(0, m_icon_padding, 0, 0);
    int icon_size_i = (int) m_icon_size_f;
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(icon_size_i, icon_size_i + m_icon_padding);
    params.gravity = MapView.LayoutParams.CENTER_HORIZONTAL;
    m_icon.setLayoutParams(params);
  }

  /** After updating the text parameters, render the text with these new
   *  settings. */
  private void renderText() {
    if (m_is_bold) {
      if (m_is_italic) {
        m_text.setTypeface(null, Typeface.BOLD_ITALIC);
      } else {
        m_text.setTypeface(null, Typeface.BOLD);
      }
    } else if (m_is_italic) {
      m_text.setTypeface(null, Typeface.ITALIC);
    } else {
      m_text.setTypeface(null, Typeface.NORMAL);
    }
    m_text.setPadding(0, m_text_padding, 0, 0);
    m_text.setTextSize(m_text_size);
  }

  /** After adjusting same parameters, save them to the settings and signal
   *  the active widgets to update themselves with these new settings. */
  private void updateWidgets() {
    // Save parameters
    SharedPreferences.Editor edit = m_preferences.edit();
    edit.putInt(SmartIcon.ICON_SIZE, (int)m_icon_size_f);
    edit.putInt(SmartIcon.ICON_PADDING, m_icon_padding);
    edit.putFloat(SmartIcon.TEXT_SIZE, m_text_size);
    edit.putInt(SmartIcon.TEXT_PADDING, m_text_padding);
    edit.putBoolean(SmartIcon.TEXT_BOLD, m_is_bold);
    edit.putBoolean(SmartIcon.TEXT_ITALIC, m_is_italic);
    edit.commit();
    edit.commit();

    // Send the widget update intent
    Intent update_intent = new Intent(SmartIconConfig.this, SmartIcon.class);
    update_intent.setAction(SmartIcon.ACTION_WIDGET_UPDATE);
    sendBroadcast(update_intent);
  }

  /** A custom listener for handling pinch scaling. */
  private class ScaleListener
          implements ScaleGestureDetector.OnScaleGestureListener {

    final float max_icon_size_f = (float)getResources().getDimensionPixelSize(R.dimen.widget_cell_size);
    final float max_text_size   = getResources().getDimensionPixelSize(R.dimen.smart_icon_text_size_max);

    @Override
    public boolean onScale(ScaleGestureDetector detector) {
      // Halve the scale factor to provide better control
      double scale_factor = ((detector.getScaleFactor() - 1.0) / 2.0) + 1.0;

      // Handle the scaling for either the icon or the text
      if (m_element == Element.ICON) {
        m_icon_size_f *= scale_factor;
        if (m_icon_size_f > max_icon_size_f)
          m_icon_size_f = max_icon_size_f;
        renderIcon();
        return true;
      } else if (m_element == Element.TEXT) {
        m_text_size *= scale_factor;
        if (m_text_size > max_text_size) m_text_size = max_text_size;
        renderText();
        return true;
      }
      return false;
    }

    /** We only respond to a scale gesture if it was done over the icon or the
     *  text. The active element is set to which one it is.
     *  @return true if scaling started on an active element, false otherwise.
     */
    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
      int focus_x = (int)detector.getFocusX();
      int focus_y = (int)detector.getFocusY();

      Rect icon_rect = new Rect(m_icon.getLeft(), m_icon.getTop(), m_icon.getRight(), m_icon.getBottom());
      if (icon_rect.contains(focus_x, focus_y)) {
        // Pinch center is on the icon, scale the icon
        m_element = Element.ICON;
        return true;
      } else if (focus_x > m_text.getLeft() &&
                 focus_x < m_text.getRight() &&
                 focus_y > m_icon.getBottom()) {
        // Pinch center is below the icon, scale the text
        m_element = Element.TEXT;
        return true;
      }
      return false;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {
      // It would be too costly to keep updating the widgets all the time, so
      // we only do this if scaling has ended.
      updateWidgets();
    }
  }

  /** A custom touch listener to respond to drag events. */
  private class TouchListener implements View.OnTouchListener {
    /** Android motion handling is kind of rough around the edges. We need to
     *  manually keep track of what's going on to make sense of it. */
    private       Motion m_motion;
    private float m_drag_start;
    private int   m_drag_padding;

    ScaleGestureDetector m_scale_detector;

    /** Initialize the class
     *  This class handles drag events, but if no dragging is done, a separate
     *  ScaleGestureDetector is needed to find scaling events.
     *  @param scale_detector a ScaleGestureDetector to detect scaling events.
     */
    public TouchListener(ScaleGestureDetector scale_detector) {
      m_scale_detector = scale_detector;
    }

    @Override
    public boolean onTouch(View view, MotionEvent motion_event) {
      // Android fires the ACTION_MOVE action also when we're scaling, so we
      // need to keep track of what's happening to filter out only the genuine
      // dragging actions.
      int action = motion_event.getActionMasked();
      switch (action) {
        case MotionEvent.ACTION_DOWN:
          // We're starting a drag event. Detect what we're dragging and save
          // it's starting position and padding.
          m_motion = Motion.DRAG;
          m_drag_start = motion_event.getY();
          if (m_drag_start < m_icon.getBottom()) {
            m_element      = Element.ICON;
            m_drag_padding = m_icon_padding;
          } else {
            m_element      = Element.TEXT;
            m_drag_padding = m_text_padding;
          }
          break;
        case MotionEvent.ACTION_POINTER_DOWN:
          // If we're using a second finger, we're not dragging, we're zooming.
          m_motion = Motion.ZOOM;
          break;
        case MotionEvent.ACTION_UP:
          m_motion = Motion.NONE;
          updateWidgets();
          break;
        case MotionEvent.ACTION_POINTER_UP:
          // If we release our second finger, the gesture is considered done.
          m_motion = Motion.NONE;
          break;
        case MotionEvent.ACTION_MOVE:
          if (m_motion == Motion.DRAG) {
            // We halve the movements to get better control
            int delta = (int)((motion_event.getY() - m_drag_start) / 2.0);

            // If we're actually dragging, calculate the displacement and update
            // the UI with the new value.
            if (m_element == Element.ICON) {
              m_icon_padding = m_drag_padding + delta;
              if (m_icon_padding < 0) m_icon_padding = 0;
              renderIcon();
            } else if (m_element == Element.TEXT) {
              m_text_padding = m_drag_padding + delta;
              if (m_text_padding < 0) m_text_padding = 0;
              renderText();
            }
          }
          break;
      }

      // Also send the motion event for inspection to the scale gesture detector
      m_scale_detector.onTouchEvent(motion_event);
      return true;
    }
  }
}