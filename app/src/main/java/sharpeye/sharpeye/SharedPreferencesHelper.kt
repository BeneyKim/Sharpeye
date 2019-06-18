package sharpeye.sharpeye

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager


object SharedPreferenceHelper {
    private val PREF_FILE = "PREF"

    /**
     * Set a string shared preference
     * @param key - Key to set shared preference
     * @param value - Value for the key
     */
    internal fun setSharedPreferenceString(context: Context, key: String, value: String) {
        //val settings = context.getSharedPreferences(PREF_FILE, 0)
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = settings.edit()
        editor.putString(key, value)
        editor.apply()
    }

    /**
     * Set a integer shared preference
     * @param key - Key to set shared preference
     * @param value - Value for the key
     */
    internal fun setSharedPreferenceInt(context: Context, key: String, value: Int) {
        //val settings = context.getSharedPreferences(PREF_FILE, 0)
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = settings.edit()
        editor.putInt(key, value)
        editor.apply()
    }

    /**
     * Set a Boolean shared preference
     * @param key - Key to set shared preference
     * @param value - Value for the key
     */
    internal fun setSharedPreferenceBoolean(context: Context, key: String, value: Boolean) {
        //val settings = context.getSharedPreferences(PREF_FILE, 0)
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = settings.edit()
        editor.putBoolean(key, value)
        editor.apply()
    }

    /**
     * Get a string shared preference
     * @param key - Key to look up in shared preferences.
     * @param defValue - Default value to be returned if shared preference isn't found.
     * @return value - String containing value of the shared preference if found.
     */
    internal fun getSharedPreferenceString(context: Context, key: String, defValue: String): String? {
        //val settings = context.getSharedPreferences(PREF_FILE, 0)
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        return settings.getString(key, defValue)
    }

    /**
     * Get a integer shared preference
     * @param key - Key to look up in shared preferences.
     * @param defValue - Default value to be returned if shared preference isn't found.
     * @return value - String containing value of the shared preference if found.
     */
    internal fun getSharedPreferenceInt(context: Context, key: String, defValue: Int): Int {
        //val settings = context.getSharedPreferences(PREF_FILE, 0)
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        return settings.getInt(key, defValue)
    }

    /**
     * Get a boolean shared preference
     * @param key - Key to look up in shared preferences.
     * @param defValue - Default value to be returned if shared preference isn't found.
     * @return value - String containing value of the shared preference if found.
     */
    internal fun getSharedPreferenceBoolean(context: Context, key: String, defValue: Boolean): Boolean {
        //val settings = context.getSharedPreferences(PREF_FILE, 0)
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        return settings.getBoolean(key, defValue)
    }
}