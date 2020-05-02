package com.mrpi.appsearch;

/**
 * Container for holding the data of an app.
 */
public class FuzzyAppSearchResult extends FuzzySearchResult {
    public String package_name;

    /**
     * Initialize the app info container.
     *
     * @param name         the public display name of the app
     * @param package_name the full package name of the app
     */
    public FuzzyAppSearchResult(String name, String package_name) {
        super(name);
        this.package_name = package_name;
    }

    @Override
    public boolean equals(Object other) {
        if (other != null) {
            if (getClass() == other.getClass()) {
                if (package_name.equals(((FuzzyAppSearchResult) other).package_name)) {
                    return true;
                }
            }
        }
        return false;
    }
}
