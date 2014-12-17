package com.mrpi.appsearch;

import java.util.ArrayList;

/** Container for holding the data of an app. Nothing fancy here.  
 */
public class AppData {
  public String             name;
  public String             package_name;
  public int                match_rating = 0;
  public ArrayList<Integer> char_matches = null;
}
