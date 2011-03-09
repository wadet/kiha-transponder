package com.kiha.location.transponder;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Calendar;
import java.util.Date;

import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.WebView;


public class XmitLocation extends Activity
{
	// FIXME:  Move these into a config file
	private static final int SAMPLE_INTERVAL = 1000 * 15; // Millisecs
	private static String SERVICE_URL = "http://dev.kihatest.com:8080/api/v1/actions?";
	private LocationListener _locationListener;
	private long _updateInterval = SAMPLE_INTERVAL; // Millisecs
	private float _minDistance = 5; // Meters
	private WebView _webview;
	
	@Override
	public void onCreate (Bundle savedInstanceState)
	{
		Log.d(XmitLocation.class.getCanonicalName(), "****** onCreate() called");
		super.onCreate(savedInstanceState);

		// Define a listener that responds to location updates
		_locationListener = new LocationListener() {
			public void onLocationChanged(Location location) {
				// Called when a new location is found by the network location provider.
				getActions(location);
		    }
			
		    public void onStatusChanged(String provider, int status, Bundle extras) {}
		    public void onProviderEnabled(String provider) {}
		    public void onProviderDisabled(String provider) {}
		};

		_webview = new WebView(this);
		setContentView(_webview);
	}
	
	@Override
	public void onResume ()
	{
		super.onResume();
		registerListeners();
		Log.d(XmitLocation.class.getCanonicalName(), "****** onResume() called");

	}
	
	@Override
	public void onPause ()
	{
		super.onPause();
		unregisterListeners();
		Log.d(XmitLocation.class.getCanonicalName(), "****** onPause() called");
	}

	// -----------------------------------------------------------------------
	
	protected void registerListeners ()
	{
		LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

		locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, _updateInterval, _minDistance, _locationListener);		
		locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, _updateInterval, _minDistance, _locationListener);		
	}
	
	protected void unregisterListeners ()
	{
		LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
		locationManager.removeUpdates(_locationListener);
	}
	
	protected JSONArray getActions (Location curLocation)
	{
		Log.d(XmitLocation.class.getCanonicalName(), "****** sendLocation() called");

		LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
		Location lastKnownNetworkLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
		Location lastKnownGPSLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
		Location bestLocation;
		String whichLocation;
		
		if (isBetterLocation(curLocation, lastKnownGPSLocation) || isBetterLocation(curLocation, lastKnownNetworkLocation)) {
			bestLocation = curLocation;
			whichLocation = "Using cur location";
		} else {
			bestLocation = isBetterLocation(lastKnownGPSLocation, lastKnownNetworkLocation) ? lastKnownGPSLocation : lastKnownNetworkLocation;
			whichLocation = "Using prev location";
		}

		int responseCode = 500;
		String url = "";
		JSONArray result = null;
		
		try {
			url = buildUrl(bestLocation);
			Log.d(XmitLocation.class.getCanonicalName(), "*** sendLocation() trying url=" + url);
			HttpClient client = getHttpClient();
			HttpGet request = new HttpGet(url);
			Log.d(XmitLocation.class.getCanonicalName(), "*** sendLocation() before execute()");
	        HttpResponse response = client.execute(request);
			Log.d(XmitLocation.class.getCanonicalName(), "*** sendLocation() after execute()");
	        responseCode = response.getStatusLine().getStatusCode();
	        result = parseResponse(response);
	        displayResult(whichLocation, url, responseCode, result);
	    } catch (Exception e) {
	    	// FIXME
	    	Log.d(XmitLocation.class.getCanonicalName(), "OOPS", e);
			Log.d(XmitLocation.class.getCanonicalName(), "*** sendLocation() " + e.getMessage());
			result = new JSONArray();
		}
	    
	    return result;
	}

	protected HttpClient getHttpClient ()
	{
		HttpParams params = new BasicHttpParams();
		HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
		HttpProtocolParams.setContentCharset(params, "utf-8");
		HttpProtocolParams.setUserAgent(params, "Kiha GPS App");
		params.setBooleanParameter("http.protocol.expect-continue", false);
		
		SchemeRegistry registry = new SchemeRegistry();
		registry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
		final SSLSocketFactory sslSocketFactory = SSLSocketFactory.getSocketFactory();
		sslSocketFactory.setHostnameVerifier(SSLSocketFactory.BROWSER_COMPATIBLE_HOSTNAME_VERIFIER);
		registry.register(new Scheme("https", sslSocketFactory, 443));
		ThreadSafeClientConnManager manager = new ThreadSafeClientConnManager(params, registry);
		
		return new DefaultHttpClient(manager, params);
	}
	
	protected JSONArray parseResponse (HttpResponse response)
		throws ClientProtocolException, IOException, JSONException
	{
		ResponseHandler<String> responseHandler = new BasicResponseHandler();
        String body = responseHandler.handleResponse(response);
        return new JSONArray(body);
	}
	
	protected void displayResult (String whichLocation, String url, int responseCode, JSONArray result) throws JSONException
	{
	    Calendar cal = Calendar.getInstance();
	    Date date = cal.getTime();
	    StringBuilder html = new StringBuilder();
	    StringBuilder temp = new StringBuilder();
	    html.append("<html><body bgcolor=\"lime\">");
	    temp.append(date.toLocaleString()).append(":  ").append(whichLocation).append(" url=").append(url).append(" -> ").append(responseCode);
	    html.append(TextUtils.htmlEncode(temp.toString()));
	    html.append("<br/><br/>");
	    html.append("<b>Relevant Actions</b><br/>");
	    for (int i=0; i < result.length(); ++i) {
	    	JSONObject action = result.getJSONObject(i);
	    	JSONObject actionAttribs = action.getJSONObject("attributes");
	    	String actionUrl = actionAttribs.getString("action-url");
	    	html.append("<a href=\"").append(actionUrl).append("\">").append(result.getJSONObject(i).getString("label")).append("</a><br/>");
	    }
	    
	    html.append("</body></html>");
	    String output = html.toString();
	    Log.d(XmitLocation.class.getCanonicalName(), "*** sendLocation() status=" + output);
		_webview.loadData(output, "text/html", "UTF-8");
	}
		
	protected String buildUrl (Location location)
		throws UnsupportedEncodingException
	{
		TelephonyManager tMgr = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
		
		StringBuilder sb = new StringBuilder();
		sb.append(SERVICE_URL);
		
        sb.append("venue-latitude=");
        sb.append(URLEncoder.encode(Double.toString(location.getLatitude()), "UTF-8"));
        sb.append("&venue-longitude=");
        sb.append(URLEncoder.encode(Double.toString(location.getLongitude()), "UTF-8"));
        sb.append("&altitude=");
        sb.append(URLEncoder.encode(Double.toString(location.getAltitude()), "UTF-8"));
        sb.append("&speed=");
        sb.append(URLEncoder.encode(Float.toString(location.getSpeed()), "UTF-8"));
        sb.append("&src=");
        sb.append(URLEncoder.encode(location.getProvider(), "UTF-8"));
        sb.append("&deviceId=");
        sb.append(URLEncoder.encode(tMgr.getDeviceId(), "UTF-8"));
        sb.append("&timestamp=");
        sb.append(URLEncoder.encode(Long.toString(System.currentTimeMillis()), "UTF-8"));
        return sb.toString();
	}

	// From the Android sample location app code using a cached last known good location
	protected boolean isBetterLocation (Location location, Location currentBestLocation)
	{
	    if (currentBestLocation == null) {
	        // A new location is always better than no location
	        return true;
	    }

	    // Check whether the new location fix is newer or older
	    long timeDelta = location.getTime() - currentBestLocation.getTime();
	    boolean isSignificantlyNewer = timeDelta > SAMPLE_INTERVAL;
	    boolean isSignificantlyOlder = timeDelta < -SAMPLE_INTERVAL;
	    boolean isNewer = timeDelta > 0;

	    // If it's been more than two minutes since the current location, use the new location
	    // because the user has likely moved
	    if (isSignificantlyNewer) {
	        return true;
	    // If the new location is more than two minutes older, it must be worse
	    } else if (isSignificantlyOlder) {
	        return false;
	    }

	    // Check whether the new location fix is more or less accurate
	    int accuracyDelta = (int) (location.getAccuracy() - currentBestLocation.getAccuracy());
	    boolean isLessAccurate = accuracyDelta > 0;
	    boolean isMoreAccurate = accuracyDelta < 0;
	    boolean isSignificantlyLessAccurate = accuracyDelta > 200;

	    // Check if the old and new location are from the same provider
	    boolean isFromSameProvider = isSameProvider(location.getProvider(),
	            currentBestLocation.getProvider());

	    // Determine location quality using a combination of timeliness and accuracy
	    if (isMoreAccurate) {
	        return true;
	    } else if (isNewer && !isLessAccurate) {
	        return true;
	    } else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
	        return true;
	    }
	    return false;
	}


	private boolean isSameProvider (String provider1, String provider2)
	{
	    if (provider1 == null) {
	      return provider2 == null;
	    }
	    return provider1.equals(provider2);
	}
}
