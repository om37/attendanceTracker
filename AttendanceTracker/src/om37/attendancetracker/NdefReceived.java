package om37.attendancetracker;

import globals.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.opengl.Visibility;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentFilter.MalformedMimeTypeException;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.text.format.Time;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

public class NdefReceived extends Activity {

	PendingIntent pending;
	String[][] techListArray;
	IntentFilter[] intentFiltersArray;

	NfcAdapter mAdapter;

	String currentTime;
	String unixTimeString;
	String tagText;
	String username;

	String responseText;

	SharedPreferences settings;

	Intent startedMe;
	TextView statusTv;
	TextView responseTv;

	/**
	 * Called when app is started.
	 * Assign intent to global variable, initialise state, prepare for foreground dispatch
	 * 
	 * Determine if Intent contains NDEF/NFC data
	 * If so, proceed with analysing it
	 * Else, do nothing
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_ndef_received);

		startedMe = getIntent();
		setupPendingActivity();

		initialise();

		String displayText = username.equals(Globals.STUDENT_ID_DEF) ? 
				"Set your username in settings!" : 
					"Welcome, " + username + ".\n\rScan a tag to sign the register."; 

		statusTv.setText(displayText);

		if(getIntent().getAction().equals(NfcAdapter.ACTION_NDEF_DISCOVERED))
		{
			statusTv.setText("Reading tag...");
			tagText = getTextFromTag(startedMe);
			//Toast.makeText(getApplicationContext(), tagText, Toast.LENGTH_LONG).show();
			displayText = tagText + "\n\r";
			displayResult();
		}
	}

	/**
	 * Displays system state using view boxes on layout
	 * Analyses contents of tagText to decide whether or
	 * not to sendPostToScript
	 *  
	 */
	public void displayResult()
	{
		responseTv.setVisibility(View.INVISIBLE);
		if(!tagText.equals(Globals.ERR_NO_RAW_MESSAGE) && !tagText.equals(Globals.ERR_NO_NDEF_MESSAGE) && !tagText.equals(Globals.ERR_NO_ROOM_NUMBER))
		{
			if(!username.equals(Globals.STUDENT_ID_DEF))
				sendPostToScript();
			else			
			{
				statusTv.setText("Error signing in");
				responseTv.setText(Globals.ERR_NO_USERNAME);
				responseTv.setBackgroundResource(R.drawable.rounded_corer_red);
			}
		}
		else
		{
			statusTv.setText("Error signing in");
			responseTv.setText(tagText);
			responseTv.setBackgroundResource(R.drawable.rounded_corer_red);
		}
		responseTv.setVisibility(View.VISIBLE);
	}

	/**
	 * Supply the xml menu to display
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.ndef_received, menu);
		return true;
	}

	/**
	 * Start preferences activity if user indicated Settings
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch(item.getItemId()) {
		case R.id.action_settings:
			startActivity(new Intent(this, Preferences.class));
			return true;
		}
		return false;
	}

	/**
	 * Override onResume when using foreground dipatch.
	 * Enable it here to ensure: 1) It is called when the app is in focus
	 * 							 2) It is called from the main thread
	 * 							 3) It is called when focus returns to app
	 */
	@Override
	public void onResume()
	{
		super.onResume();
		mAdapter.enableForegroundDispatch(this, pending, intentFiltersArray, techListArray);
		username = Preferences.getName(getApplicationContext());
	}

	/**
	 * Override onPause when using foreground dipatch.
	 * Disable it here to ensure it's disabled when app loses focus.
	 */
	@Override
	public void onPause()
	{
		super.onPause();
		mAdapter.disableForegroundDispatch(this); //enableForegroundDispatch(this, pending, intentFiltersArray, techListArray);
	}

	/**
	 * Overridden lifecycle method. An Intent ends up here when an activity enables
	 * foreground dispatch giving a PendingIntent with matching filters as the second parameter.
	 * 
	 * Similar process to onCreate()
	 */
	@Override	
	protected void onNewIntent(Intent intent)
	{
		startedMe = intent;
		initialise();
		tagText = getTextFromTag(startedMe);
		statusTv.setText("Reading tag...");
		displayResult();
	};

	/**
	 * A method used to show the preferences screen if username not said.
	 * I used this with a while loop to "force" users to set the username,
	 * but this irritated even me to high-hell so left it unimplemented for
	 * now.
	 */
	void checkUserName()
	{	
		if(username.equals(Globals.STUDENT_ID_DEF))
		{
			startActivityForResult(new Intent(this, Preferences.class), Globals.SET_USER_REQ);
		}
	}
	
	/**
	 * This initialise most of the global variables.
	 * It uses findViewById to instantiate the response and status text views
	 * getSharedPreferences() returns the preferences file
	 * username is instantiated from the settings page once getSharedPreferences() has been called
	 * get mAdapter by calling to NfcAdapter.getDefaultAdapter
	 * 
	 * This is called in onCreate() and onNewIntent() ensure the activity state is
	 * setup correctly.
	 */
	void initialise()
	{
		responseTv = (TextView)findViewById(R.id.phpResponse);
		responseTv.setText("");
		responseTv.setVisibility(View.INVISIBLE);

		statusTv = (TextView)findViewById(R.id.txtDislay);
		statusTv.setText("");

		settings = getSharedPreferences(Globals.PREFERENCES_FILE, MODE_PRIVATE);	
		username = settings.getString(Globals.STUDENT_ID, Globals.STUDENT_ID_DEF);

		mAdapter = NfcAdapter.getDefaultAdapter(getApplicationContext());		
	}


	/**
	 * Take current timestamp and prepare Runnable to send POST
	 * request to script
	 * Combine username, timestamp and roomnumber into keyvalue pairs
	 * and add these to post request
	 * Start thread to Send post and await response.
	 * 
	 * Display response or error message
	 */
	private void sendPostToScript()
	{	
		long unixTime = System.currentTimeMillis()/1000l;//Divide by 1000 to get seconds instead of millis
		currentTime = String.valueOf(unixTime);

		Runnable r = new Runnable()
		{	
			HttpClient httpClient = new DefaultHttpClient();//Client to execute the post
			HttpPost post = new HttpPost("http://om37nfcregistration.net46.net/loginWithPOST.php");//The post object/event
			List<NameValuePair> postData = new ArrayList<NameValuePair>();

			Handler h = new Handler();
			String displayText;

			@Override
			public void run() 
			{
				hideResponseView();
				username = Preferences.getName(getApplicationContext()).replaceAll("[^a-zA-Z0-9]+","");
				tagText = tagText.replaceAll("[^a-zA-Z0-9]+","");

				BasicNameValuePair id = new BasicNameValuePair("studentId", username);//studentId		
				BasicNameValuePair rm = new BasicNameValuePair("roomNum", tagText);//roomNum				
				BasicNameValuePair tm = new BasicNameValuePair("dateTime", currentTime);//timeDate

				//Add post vars
				postData.add(id);
				postData.add(rm);
				postData.add(tm);

				try 
				{
					post.setEntity(new UrlEncodedFormEntity(postData));
					HttpResponse response = httpClient.execute(post);
					displayText = "Post executed\r\nResponse:" ;
					responseText = EntityUtils.toString(response.getEntity());
				}				
				catch (ClientProtocolException e)
				{
					displayText += "Protocol Error in httpClient.execute. \r\n" + e.getMessage() + "\n\r";
					e.printStackTrace();
				}
				catch(IOException e)
				{
					displayText += "IOError in httpClient.execute. \r\n" + e.getMessage() + "\n\r";
					e.printStackTrace();
				}

				sendResponse();
			}


			/**
			 * Uses handler to hide response view box
			 */
			public void hideResponseView() {
				h.post(new Runnable() {

					@Override
					public void run() {
						responseTv.setVisibility(View.INVISIBLE);	
					}
				});
			}
			/**
			 * Use handler to set response text box text
			 * Set background based on system state:
			 * 	1) ERROR for red
			 *  2) 1 for green
			 */
			public void sendResponse() {
				h.post(new Runnable(){
					public void run()
					{
						statusTv.setText(displayText);
						if(responseText.contains("Error") || !responseText.startsWith("1"))
							responseTv.setBackgroundResource(R.drawable.rounded_corer_red);
						else
							responseTv.setBackgroundResource(R.drawable.rounded_corer_green);

						responseTv.setText(responseText);
						responseTv.setVisibility(View.VISIBLE);
						//Toast.makeText(getApplicationContext(), displayText, Toast.LENGTH_LONG).show();
					}
				});
			}		
		};

		new Thread(r).start();
	}

	
	/**
	 * Receives an Intent with attached ndef messages
	 * Iterates through messages and determines if any
	 * are related to the system
	 * If so, extract room number from tag with parseMessage and return as string
	 * Else, return an error message
	 * @param intent the intent containing NDEF messages as an extra 
	 * @return the string extracted from the tag or an error message
	 */
	private String getTextFromTag(Intent intent) {

		String returnMessage;		
		Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);			
		NdefMessage[] ndefMsgs;


		if(rawMsgs != null)
		{
			ndefMsgs = new NdefMessage[rawMsgs.length];

			for (int i = 0; i < rawMsgs.length; i++)
				ndefMsgs[i] = (NdefMessage) rawMsgs[i];

			if(ndefMsgs != null && ndefMsgs.length != 0)
				returnMessage=parseMessage(ndefMsgs[0]);
			else
				returnMessage = Globals.ERR_NO_NDEF_MESSAGE; 
		}
		else
			returnMessage = Globals.ERR_NO_RAW_MESSAGE;

		return returnMessage;
	}

	
	/**
	 * Accepts an NDEF messages and assumes it comes from a tag
	 * related with the system. Iterates through records until
	 * a TNF record is found
	 * Extracts payload and constructs a string from the byte[]
	 * Returns the string payload or an error message
	 * @param msg the NDEF message to be processed
	 * @return the String extracted from the NDEF message or an error message
	 */
	private String parseMessage(NdefMessage msg)
	{
		String s;
		NdefRecord roomNumberRecord=null;

		for(NdefRecord r : msg.getRecords())
		{
			if(r.getTnf() == NdefRecord.TNF_MIME_MEDIA)
			{
				roomNumberRecord = r;
				break;
			}		
		}

		if(roomNumberRecord != null)
			s = new String(roomNumberRecord.getPayload());//Gave me a huge headache. Tried to return getPayload.toString();
		else
			s = Globals.ERR_NO_ROOM_NUMBER;

		return s;
	}


	/**
	 * Sets up the pending activity object required to use foreground dispatch
	 * Uses PendingIntent.getActivity() to instantiate the object and then creates
	 * an array of IntentFilters and a techList array.
	 * 
	 * This is called from onCreate();
	 */
	public void setupPendingActivity() {
		pending = PendingIntent.getActivity(
				this, 
				0, 
				new Intent(this,getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
				0
				);		

		IntentFilter ndefPlainTextFilter = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);		

		try
		{
			ndefPlainTextFilter.addDataType("application/om37.ndefwriter");
		}
		catch(MalformedMimeTypeException e)
		{
		}
		intentFiltersArray = new IntentFilter[]{ndefPlainTextFilter,};
		techListArray = new String[][]{new String[]{Ndef.class.getName()}};
	}
}
