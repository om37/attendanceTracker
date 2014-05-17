package om37.attendancetracker;

import globals.Globals;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;


public class Preferences extends PreferenceActivity {
	
	SharedPreferences settings;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.settings);
		
		settings = getSharedPreferences(Globals.PREFERENCES_FILE, MODE_PRIVATE);
	}
	
	public static String getName(Context con)
	{
		return PreferenceManager.getDefaultSharedPreferences(con)
				.getString(Globals.STUDENT_ID, Globals.STUDENT_ID_DEF);
	}
	
	@Override
	protected void onPause() 
	{
		super.onPause();
		SharedPreferences.Editor editor = settings.edit();		
		editor.putString(Globals.STUDENT_ID, getName(getApplicationContext()));
		editor.apply();
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		SharedPreferences.Editor editor = settings.edit();		
		editor.putString(Globals.STUDENT_ID, getName(getApplicationContext()));
		editor.apply();
	}

}
