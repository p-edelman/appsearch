package com.mrpi.appsearch;

import java.util.ArrayList;

/** Container for holding the data of an app. Nothing fancy here.
 *  Of all the data, only package_name needs to be set. Anything using this class should expect that
 *  the rest may not be set. */
public class AppData {
  public String             name = null;
  public String             package_name;
  public int                match_rating = 0;
  public ArrayList<Integer> char_matches = null;

  public AppData(String package_name) {
    this.package_name = package_name;
  }
}
