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
			tagText = getTextFromTag(startedMe);
			//Toast.makeText(getApplicationContext(), tagText, Toast.LENGTH_LONG).show();
			displayText = tagText + "\n\r";
			displayResult();
		}
	}

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

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.ndef_received, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch(item.getItemId()) {
		case R.id.action_settings:
			startActivity(new Intent(this, Preferences.class));
			return true;
		}
		return false;
	}

	@Override
	public void onResume()
	{
		super.onResume();
		mAdapter.enableForegroundDispatch(this, pending, intentFiltersArray, techListArray);
		username = Preferences.getName(getApplicationContext());
	}

	@Override
	public void onPause()
	{
		super.onPause();
		mAdapter.disableForegroundDispatch(this); //enableForegroundDispatch(this, pending, intentFiltersArray, techListArray);
	}

	@Override	
	protected void onNewIntent(Intent intent)
	{
		startedMe = intent;
		initialise();
		tagText = getTextFromTag(startedMe);
		statusTv.setText("Reading tag...");
		displayResult();
	};

	void checkUserName()
	{	
		if(username.equals(Globals.STUDENT_ID_DEF))
		{
			startActivityForResult(new Intent(this, Preferences.class), Globals.SET_USER_REQ);
		}
	}
	
	void initialise()
	{
		responseTv = (TextView)findViewById(R.id.phpResponse);
		responseTv.setText("");
		responseTv.setVisibility(View.INVISIBLE);
		settings = getSharedPreferences(Globals.PREFERENCES_FILE, MODE_PRIVATE);		
		mAdapter = NfcAdapter.getDefaultAdapter(getApplicationContext());		
		statusTv = (TextView)findViewById(R.id.txtDislay);
		statusTv.setText("");
		username = settings.getString(Globals.STUDENT_ID, Globals.STUDENT_ID_DEF);
	}

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
				String username = Preferences.getName(getApplicationContext());

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

			public void hideResponseView() {
				h.post(new Runnable() {
					
					@Override
					public void run() {
						responseTv.setVisibility(View.INVISIBLE);	
					}
				});
			}

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

	private String getTextFromTag(Intent startedMe) {

		String returnMessage;		
		Parcelable[] rawMsgs = startedMe.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);			
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
