package org.sidoine.smsimporters60;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.sidoine.smsimporters60.R;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends ActionBarActivity {
	public final static String TAG = "org.sidoine.smsimporters60";
	public final static String EXTRA_MESSAGE = "org.sidoine.smsimporters60.MESSAGE";
	public final static int FILE_SELECT_CODE = 145;
	private String path=  Environment.getExternalStorageDirectory().getPath() + "/sms-messages-2013-06-05.utf8.txt";


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
	}


	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	/** Called when the user clicks the Send button */
	public void sendMessage(View view) {
		Intent intent = new Intent(this, DisplayMessageActivity.class);
		EditText editText = (EditText) findViewById(R.id.edit_message);
		String message = editText.getText().toString();
		intent.putExtra(EXTRA_MESSAGE, message);
		startActivity(intent);
	}

	/** Called when the user clicks the ChooseFile button */
	public void chooseFile(View view) {
		Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
		intent.setType("*/*");
		intent.addCategory(Intent.CATEGORY_OPENABLE);

		try {
			startActivityForResult(
					Intent.createChooser(intent, "Select the file to import"),FILE_SELECT_CODE);
		} catch (android.content.ActivityNotFoundException ex) {
			// Potentially direct the user to the Market with a Dialog
			Toast.makeText(this, "Please install a File Manager, dude.", Toast.LENGTH_SHORT).show();
		}
	}

	/** Called when the user clicks the importFile button 
	 * @throws IOException */
	public void importFile(View view) {
		try {
			//FileInputStream in = openFileInput(path);
			File file = new File(path);
			InputStreamReader inputStreamReader = new InputStreamReader(new FileInputStream(file));
			BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
			String line;
			Pattern p = Pattern.compile(".*<(.*)>");
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss.", Locale.ENGLISH);
			int messageid, smsCount=0;
			Date date=null;
			String from = null, to = null, sep="", sms="";
			boolean firstMessage=true;
			
			while ((line = bufferedReader.readLine()) != null) {
				if(smsCount>1000) {
					bufferedReader.close();
					getContentResolver().delete(Uri.parse("content://sms/conversations/-1"), null, null);
					Log.d(TAG, smsCount + " SMS have been added !");
					return;
				}
				if(line.startsWith("Message-ID: ")) {
					if(!firstMessage) {
						// if not the first message, then save the previous one
						saveSms(date, from, to, sms);
						smsCount++;
						
						// reset values for next message
						sms = "";
						sep = "";
					}
					messageid = Integer.parseInt(line.replaceAll("Message-ID: ", ""));
					firstMessage = false;
				}
				else if(line.startsWith("Date: ")) {
					date = sdf.parse(line.replaceAll("Date: ", ""));
				}
				else if(line.startsWith("From: ")) {
					to = null;
					from = line.replaceAll("From: ", "");
					Matcher m = p.matcher(from);
					while(m.find()) {
						from = m.group(1);
					}
					bufferedReader.readLine();
				}
				else if(line.startsWith("To: ")) {
					from = null;
					to = line.replaceAll("To: ", "");
					Matcher m = p.matcher(to);
					while(m.find()) {
						to = m.group(1);
					}
					bufferedReader.readLine();
				}
				else{
					// message content, add it to sms string
					sms += sep + line;
					sep = "\n";
				}
			}
			
			//send tha last message !
			saveSms(date, from, to, sms);
			smsCount++;
			
			bufferedReader.close();
			getContentResolver().delete(Uri.parse("content://sms/conversations/-1"), null, null);
			Log.d(TAG, smsCount + " SMS have been added !");
		}
		catch(Exception e) {
			Log.d(TAG, "Error: " + e.toString());
		}
	}


	private void saveSms(Date date, String from, String to, String sms) {
		//remove last char of the sms (always a new line)
		sms = sms.substring(0, sms.length()-1);
		// end of the message, just put message to false and save the sms
		ContentValues values = new ContentValues();
		values.put("body", sms);
		values.put("date", date.getTime());
		values.put("read", 1);
		values.put("seen", 1);
		if(from == null) {
			values.put("type", 2);
			values.put("address", to);
			getContentResolver().insert(Uri.parse("content://sms"), values);
			Log.d(TAG,"send msg: " + to + ", date: " + date);
		}
		else {
			values.put("type", 1);
			values.put("address", from);
			getContentResolver().insert(Uri.parse("content://sms"), values);
			Log.d(TAG,"inbox msg: " + from + ", date: " + date);
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		try {
			switch (requestCode) {
			case FILE_SELECT_CODE:
				if (resultCode == RESULT_OK) {
					// Get the Uri of the selected file 
					Uri uri = data.getData();
					Log.d(TAG, "File Uri: " + uri.toString());
					// Get the path
					path = getPath(this, uri);
					Log.d(TAG, "File Path: " + path);
					TextView textView =  (TextView)findViewById(R.id.filetoimport);
					textView.setText(path);
				}
				break;
			}
		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		super.onActivityResult(requestCode, resultCode, data);

	}

	public static String getPath(Context context, Uri uri) throws URISyntaxException {
		if ("content".equalsIgnoreCase(uri.getScheme())) {
			String[] projection = { "_data" };
			Cursor cursor = null;

			try {
				cursor = context.getContentResolver().query(uri, projection, null, null, null);
				int column_index = cursor.getColumnIndexOrThrow("_data");
				if (cursor.moveToFirst()) {
					return cursor.getString(column_index);
				}
			} catch (Exception e) {
				// Eat it
			}
		}
		else if ("file".equalsIgnoreCase(uri.getScheme())) {
			return uri.getPath();
		}

		return null;
	} 

}
