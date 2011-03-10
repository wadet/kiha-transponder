package com.kiha.location.transponder;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.telephony.TelephonyManager;
import android.util.Log;

public class LocationController
{
	private Context _context;
	private AppConfiguration _appConfig;


	public LocationController (Context context)
	{
		_context = context;
		_appConfig = new AppConfiguration(context);
	}
	
	public HttpResponse sendLocation (Location curLocation)
	{
		Log.d(this.getClass().getCanonicalName(), "*** sendLocation() called");

		LocationManager locationManager = (LocationManager) _context.getSystemService(Context.LOCATION_SERVICE);
		Location lastKnownNetworkLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
		Location lastKnownGPSLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
		Location bestLocation;
		
		if (isBetterLocation(curLocation, lastKnownGPSLocation) || isBetterLocation(curLocation, lastKnownNetworkLocation)) {
			bestLocation = curLocation;
			Log.d(this.getClass().getCanonicalName(), "*** Using cur location");
		} else {
			bestLocation = isBetterLocation(lastKnownGPSLocation, lastKnownNetworkLocation) ? lastKnownGPSLocation : lastKnownNetworkLocation;
			Log.d(this.getClass().getCanonicalName(), "*** Using prev location");
		}

		String url = "";
		HttpResponse result;
		try {
			url = buildUrl(bestLocation);
			Log.d(this.getClass().getCanonicalName(), "*** trying url=" + url);
			HttpClient client = getHttpClient();
			HttpGet request = new HttpGet(url);
			Log.d(this.getClass().getCanonicalName(), "*** before execute()");
	        result = client.execute(request);
			Log.d(this.getClass().getCanonicalName(), "*** after execute()");
	    } catch (Exception e) {
	    	// FIXME
	    	Log.d(this.getClass().getCanonicalName(), "OOPS", e);
			Log.d(this.getClass().getCanonicalName(), "*** " + e.getMessage());
			result = new BasicHttpResponse(HttpVersion.HTTP_1_1, 503, "Fake response");
		}
	
	    return result;
	}

	// -----------------------------------------------------------------------

	protected String buildUrl (Location location)
		throws UnsupportedEncodingException
	{
		TelephonyManager tMgr = (TelephonyManager) _context.getSystemService(Context.TELEPHONY_SERVICE);
		
		StringBuilder sb = new StringBuilder();
		sb.append(_appConfig.getLocationServiceUrl());
		
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
	    sb.append("&client-id=");
	    sb.append(URLEncoder.encode(tMgr.getDeviceId(), "UTF-8"));
	    sb.append("&timestamp=");
	    sb.append(URLEncoder.encode(Long.toString(System.currentTimeMillis()), "UTF-8"));
	    return sb.toString();
	}

	protected HttpClient getHttpClient ()
	{
		HttpParams params = new BasicHttpParams();
		HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
		HttpProtocolParams.setContentCharset(params, "utf-8");
		HttpProtocolParams.setUserAgent(params, "Kiha GPS App");
		HttpConnectionParams.setSoTimeout(params, _appConfig.getSocketTimeout());
		HttpConnectionParams.setConnectionTimeout(params, _appConfig.getConnectionTimeout());
		params.setBooleanParameter("http.protocol.expect-continue", false);
		
		SchemeRegistry registry = new SchemeRegistry();
		registry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
		final SSLSocketFactory sslSocketFactory = SSLSocketFactory.getSocketFactory();
		sslSocketFactory.setHostnameVerifier(SSLSocketFactory.BROWSER_COMPATIBLE_HOSTNAME_VERIFIER);
		registry.register(new Scheme("https", sslSocketFactory, 443));
		ThreadSafeClientConnManager manager = new ThreadSafeClientConnManager(params, registry);
		
		return new DefaultHttpClient(manager, params);
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
	    boolean isSignificantlyNewer = timeDelta > _appConfig.getSampleInterval();
	    boolean isSignificantlyOlder = timeDelta < -_appConfig.getSampleInterval();
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
