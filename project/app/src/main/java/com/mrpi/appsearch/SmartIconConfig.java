package com.mrpi.appsearch;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.maps.MapView;

/** Activity to configure a smart icon.
 *  The activity is a transparent dialog, so the icon (widget) can be seen
 *  while the settings are changed. */
public class SmartIconConfig extends Activity {

  /** The SharedPreferences object for all the smart icon preferences. */
  SharedPreferences m_preferences;

  /** The parts that make up the smart icon. */
  LinearLayout m_box;
  ImageView    m_icon;
  TextView     m_text;

  /** Needed for feedback to the user. */
  Toast        m_toast;
  Float        m_density = null;

  /** These parameters define the layout. */
  private float   m_icon_size_f; // As a float for calculations, but it gets converted to int to actually use it
  private float   m_text_size;
  private boolean m_is_bold;
  private boolean m_is_italic;
  private int     m_icon_padding;
  private int     m_text_padding;
  private boolean m_has_background;

  /** We need to keep track of which element we're working on when zooming and
   *  dragging. */
  private enum Element {ICON, TEXT}
  private      Element m_element;

  /** We also need to keep track of which gesture is being performed. */
  private enum Motion {NONE, DRAG, ZOOM}

  /** We need to know the offset of the icon within the widget. */
  private int x_offset;
  private int y_offset;

  @Override
  protected void onCreate(Bundle saved_state) {
    super.onCreate(saved_state);

    // Make sure the window is placed at the upper side of the screen, so that
    // the user has a larger chance of having a smart icon in view.
    WindowManager.LayoutParams params = this.getWindow().getAttributes();
    params.gravity = Gravity.TOP;
    params.y       = 10;
    LayoutInflater inflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    View view = inflater.inflate(R.layout.activity_smart_icon_config, null, false);
    setContentView(view, params);

    // Initialize the UI components with their parameters
    m_box  = (LinearLayout)findViewById(R.id.icon_box);
    m_icon = (ImageView)findViewById(R.id.config_icon);
    m_text = (TextView)findViewById(R.id.config_text);

    // Create the toast for displaying scaling and dragging feedback
    m_toast = Toast.makeText(this, "", Toast.LENGTH_SHORT);
    m_toast.setGravity(Gravity.TOP, 0, 0);

    // Load all the preferences
    m_preferences = getSharedPreferences(SmartIcon.SMART_ICON_PREFERENCES,
                                         Context.MODE_MULTI_PROCESS);
    if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
      m_icon_size_f = (float) m_preferences.getInt(SmartIcon.ICON_SIZE_P,
              getResources().getDimensionPixelSize(android.R.dimen.app_icon_size));
      m_text_size = m_preferences.getFloat(SmartIcon.TEXT_SIZE_P,
              getResources().getDimension(R.dimen.smart_icon_text_size_default));
      m_icon_padding = m_preferences.getInt(SmartIcon.ICON_PADDING_P, 0);
      m_text_padding = m_preferences.getInt(SmartIcon.TEXT_PADDING_P, 0);
    } else {
      m_icon_size_f = (float) m_preferences.getInt(SmartIcon.ICON_SIZE_L,
              getResources().getDimensionPixelSize(android.R.dimen.app_icon_size));
      m_text_size = m_preferences.getFloat(SmartIcon.TEXT_SIZE_L,
              getResources().getDimension(R.dimen.smart_icon_text_size_default));
      m_icon_padding = m_preferences.getInt(SmartIcon.ICON_PADDING_L, 0);
      m_text_padding = m_preferences.getInt(SmartIcon.TEXT_PADDING_L, 0);
    }
    m_is_bold        = m_preferences.getBoolean(SmartIcon.TEXT_BOLD, false);
    m_is_italic      = m_preferences.getBoolean(SmartIcon.TEXT_ITALIC, false);
    m_has_background = m_preferences.getBoolean(SmartIcon.HAS_BACKGROUND, true);
    renderBox();
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

    final CheckBox background_check = (CheckBox)findViewById(R.id.background_checkbox);
    background_check.setChecked(m_has_background);
    background_check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
      @Override
      public void onCheckedChanged(CompoundButton button_view, boolean is_checked) {
        m_has_background = is_checked;
        renderBox();
        updateWidgets();
      }
    });

    // Attach movement listeners to the general, outer box (so we can detect
    // pinch gestures all over the window instead of only the elements
    // themselves).
    RelativeLayout widget = (RelativeLayout)findViewById(R.id.widget);
    ScaleGestureDetector scale_detector = new ScaleGestureDetector(this, new ScaleListener());
    widget.setOnTouchListener(new TouchListener(scale_detector));

    // The close button dismisses the "dialog".
    Button dismiss_button = (Button) findViewById(R.id.dismiss_button);
    dismiss_button.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        finish();
      }
    });
  }

  @Override
  public void onWindowFocusChanged(boolean has_focus) {
    // Calculate the offset of the preview smart icon
    LinearLayout container = (LinearLayout)findViewById(R.id.icon_container);
    x_offset = m_box.getLeft() + container.getLeft();
    y_offset = m_box.getTop()  + container.getTop();
  }

  /** After updating the box parameters, render the box with these new
   *  settings. */
  private void renderBox() {
    if (m_has_background) {
      m_box.setBackgroundResource(R.drawable.smart_icon_background);
    } else {
      // Draw a simple white box to show the box area
      m_box.setBackgroundResource(R.drawable.thin_line_box);
    }
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

  /** Set the toast text to the provided number, converting from pixels
   *  to dp.
   *  @param str a string to start the message with, the dp value will be
   *             concatenated to it
   *  @param pixels pixel value to display as dp */
  private void setFeedbackTextToDP(String str, int pixels) {
    if (m_density == null) {
      DisplayMetrics metrics = new DisplayMetrics();
      getWindowManager().getDefaultDisplay().getMetrics(metrics);
      m_density = metrics.density;
    }
    pixels = (int)(pixels / m_density);
    m_toast.setText(str + Integer.toString(pixels));
    m_toast.show();
  }

  /** After adjusting same parameters, save them to the settings and signal
   *  the active widgets to update themselves with these new settings. */
  private void updateWidgets() {
    // Save parameters
    SharedPreferences.Editor edit = m_preferences.edit();
    if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
      Log.d("Config", "Writing portrait parameters");
      edit.putInt(SmartIcon.ICON_SIZE_P, (int) m_icon_size_f);
      edit.putInt(SmartIcon.ICON_PADDING_P, m_icon_padding);
      edit.putFloat(SmartIcon.TEXT_SIZE_P, m_text_size);
      edit.putInt(SmartIcon.TEXT_PADDING_P, m_text_padding);
    } else {
      edit.putInt(SmartIcon.ICON_SIZE_L, (int) m_icon_size_f);
      edit.putInt(SmartIcon.ICON_PADDING_L, m_icon_padding);
      edit.putFloat(SmartIcon.TEXT_SIZE_L, m_text_size);
      edit.putInt(SmartIcon.TEXT_PADDING_L, m_text_padding);
    }
    edit.putBoolean(SmartIcon.TEXT_BOLD, m_is_bold);
    edit.putBoolean(SmartIcon.TEXT_ITALIC, m_is_italic);
    edit.putBoolean(SmartIcon.HAS_BACKGROUND, m_has_background);
    edit.commit();

    // Send the widget update intent
    Intent update_intent = new Intent(SmartIconConfig.this, SmartIcon.class);
    update_intent.setAction(SmartIcon.ACTION_WIDGET_UPDATE);
    sendBroadcast(update_intent);
  }

  /** A custom listener for handling pinch scaling. */
  private class ScaleListener
          implements ScaleGestureDetector.OnScaleGestureListener {

    final float max_icon_size_f = (float)getResources().getDimensionPixelSize(R.dimen.approx_widget_cell_width);
    final float max_text_size   = getResources().getDimension(R.dimen.smart_icon_text_size_max);

    @Override
    public boolean onScale(ScaleGestureDetector detector) {
      // Scale down the scale factor to provide better control
      double scale_factor = ((detector.getScaleFactor() - 1.0) / 3.0) + 1.0;

      // Handle the scaling for either the icon or the text
      if (m_element == Element.ICON) {
        m_icon_size_f *= scale_factor;
        if (m_icon_size_f > max_icon_size_f)
          m_icon_size_f = max_icon_size_f;
        String str = getResources().getString(R.string.feedback_icon_size);
        setFeedbackTextToDP(str, (int) m_icon_size_f);
        renderIcon();
        return true;
      } else if (m_element == Element.TEXT) {
        m_text_size *= scale_factor;
        if (m_text_size > max_text_size) m_text_size = max_text_size;
        String str = getResources().getString(R.string.feedback_text_size);
        m_toast.setText(str + String.format("%.1f", m_text_size));
        m_toast.show();
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
      // Find focus position within the icon box
      int focus_x = (int)detector.getFocusX() - x_offset;
      int focus_y = (int)detector.getFocusY() - y_offset;

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
          m_drag_start = motion_event.getY() - y_offset;
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
            // We scale down the movements to get better control
            int delta = (int)((motion_event.getY() - y_offset - m_drag_start) / 3.0);

            // If we're actually dragging, calculate the displacement and update
            // the UI with the new value.
            if (m_element == Element.ICON) {
              m_icon_padding = m_drag_padding + delta;
              if (m_icon_padding < 0) m_icon_padding = 0;
              String str = getResources().getString(R.string.feedback_icon_distance);
              setFeedbackTextToDP(str, m_icon_padding);
              renderIcon();
            } else if (m_element == Element.TEXT) {
              m_text_padding = m_drag_padding + delta;
              if (m_text_padding < 0) m_text_padding = 0;
              String str = getResources().getString(R.string.feedback_text_distance);
              setFeedbackTextToDP(str, m_text_padding);
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