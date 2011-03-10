package com.kiha.location.transponder;

import java.io.IOException;
import java.util.Calendar;
import java.util.Date;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.impl.client.BasicResponseHandler;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.Toast;


public class LocationActivity extends Activity
{
	private AppConfiguration _appConfig;
	private LocationListener _locationListener;
	private LocationController _lc;
	private WebView _webview;
	private Button _stopButton;
	private PendingIntent _pi;
	
	
	@Override
	public void onCreate (Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		Log.d(this.getClass().getCanonicalName(), "*** onCreate() called");

		// ----------------------------------------------------
		// Setup the listeners and setup our background updater
		// ----------------------------------------------------
		_appConfig = new AppConfiguration(this);
		_lc = new LocationController(this);

		// Define a listener that responds to location updates
		_locationListener = new LocationListener() {
			public void onLocationChanged(Location location) {
				// Called when a new location is found by the network location provider.
				displayActions(location);
		    }
			
		    public void onStatusChanged(String provider, int status, Bundle extras) {}
		    public void onProviderEnabled(String provider) {}
		    public void onProviderDisabled(String provider) {}
		};

		registerReceiver();

		// --------------------------
		// Now setup our context view
		// --------------------------
		setContentView(R.layout.main);
		_webview = (WebView) findViewById(R.id.webview);
		_stopButton = (Button) findViewById(R.id.stop_button);

		OnClickListener stopButtonListener = new OnClickListener() {
		    public void onClick (View v)
		    {
		    	unregisterReceiver();
		    }
		};

		_stopButton.setOnClickListener(stopButtonListener);
	}
	
	@Override
	public void onResume ()
	{
		super.onResume();
		registerListeners();
		Log.d(this.getClass().getCanonicalName(), "*** onResume() called");

	}
	
	@Override
	public void onPause ()
	{
		super.onPause();
		unregisterListeners();
		Log.d(this.getClass().getCanonicalName(), "*** onPause() called");
	}

	@Override
	public void onStop ()
	{
		super.onStop();
		unregisterListeners();
		Log.d(this.getClass().getCanonicalName(), "*** onStop() called");
	}
	
	// -----------------------------------------------------------------------
	
	protected void registerReceiver ()
	{
		Toast.makeText(this, "Registering background updater", Toast.LENGTH_SHORT).show();

		Intent intent = new Intent(this, AlarmReceiver.class);
		_pi = PendingIntent.getBroadcast(this, 192837, intent, PendingIntent.FLAG_UPDATE_CURRENT);
		
		AlarmManager am = (AlarmManager) this.getSystemService(ALARM_SERVICE);
		am.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), _appConfig.getBackgroundUpdateInterval(), _pi);
		Log.d(this.getClass().getCanonicalName(), "Registered background location updater.  Update interval is " + _appConfig.getBackgroundUpdateInterval() + " ms");
	}

	protected void unregisterReceiver ()
	{
		Toast.makeText(this, "Unregistered background updater", Toast.LENGTH_SHORT).show();
		
		AlarmManager am = (AlarmManager) this.getSystemService(ALARM_SERVICE);
		am.cancel(_pi);
	}
	
	protected void registerListeners ()
	{
		LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

		locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, _appConfig.getSampleInterval(), _appConfig.getMinDistance(), _locationListener);		
		locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, _appConfig.getSampleInterval(), _appConfig.getMinDistance(), _locationListener);		
	}
	
	protected void unregisterListeners ()
	{
		LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
		locationManager.removeUpdates(_locationListener);
	}
	
	public void displayActions (Location curLocation)
	{
		Log.d(this.getClass().getCanonicalName(), "*** displayActions() called");

		HttpResponse response = _lc.sendLocation(curLocation);
		String url = _appConfig.getLocationServiceUrl();
		int responseCode = response.getStatusLine().getStatusCode();
		JSONArray actions;
		try
		{
			actions = parseResponse(response);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			Log.d(this.getClass().getCanonicalName(), "Failed to parse returned JSON", e);
			actions = new JSONArray();
			Toast.makeText(this, "Failed to parse returned JSON (see logcat)", Toast.LENGTH_SHORT);
		}
	    
	    displayActions(url, responseCode, actions);
	}
	
	protected JSONArray parseResponse (HttpResponse response)
		throws ClientProtocolException, IOException, JSONException
	{
		ResponseHandler<String> responseHandler = new BasicResponseHandler();
        String body = responseHandler.handleResponse(response);
        Log.d(this.getClass().getCanonicalName(), body);

        return new JSONArray(body);
	}
	
	protected void displayActions (String url, int responseCode, JSONArray actions)
	{
	    Calendar cal = Calendar.getInstance();
	    Date date = cal.getTime();
	    StringBuilder html = new StringBuilder();
	    StringBuilder temp = new StringBuilder();
	    html.append("<html><body bgcolor=\"lime\">");
	    temp.append(date.toLocaleString()).append(":  ").append(" url=").append(url).append(" -> ").append(responseCode);
	    html.append(TextUtils.htmlEncode(temp.toString()));
	    html.append("<br/><br/>");
	    html.append("<b>Relevant Actions</b><br/>");
	    for (int i=0; i < actions.length(); ++i) {
	    	try {
		    	JSONObject action = actions.getJSONObject(i);
		    	JSONObject actionAttribs = action.getJSONObject("attributes");
		    	String actionUrl = actionAttribs.getString("action-url");
		    	html.append("<a href=\"").append(actionUrl).append("\">").append(actions.getJSONObject(i).getString("label")).append("</a><br/>");
	    	} catch (JSONException e) {
	    		html.append("Failed to parse JSON object ").append(i).append(e);
	    	}
	    }
	    
	    html.append("</body></html>");
	    String output = html.toString();
	    Log.d(this.getClass().getCanonicalName(), "*** displayActions() status=" + output);
		_webview.loadData(output, "text/html", "UTF-8");
	}
}
