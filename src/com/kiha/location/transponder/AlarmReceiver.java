package com.kiha.location.transponder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;

public class AlarmReceiver extends BroadcastReceiver
{
	private LocationListener _locationListener;
	private LocationController _lc;
	private Context _context;

	@Override
	public void onReceive (Context context, Intent intent)
	{
		_context = context;
		_lc = new LocationController(context);

		// Define a listener that responds to location updates
		_locationListener = new LocationListener() {
			public void onLocationChanged (Location location) {
				// Called when a new location is found by the network location provider.
				sendLocation(location);
		    }
			
		    public void onStatusChanged(String provider, int status, Bundle extras) {}
		    public void onProviderEnabled(String provider) {}
		    public void onProviderDisabled(String provider) {}
		};
		
		registerListeners();
	}

	protected void sendLocation (Location location)
	{
		Log.d(this.getClass().getCanonicalName(), "*** sendLocation() called");
		_lc.sendLocation(location);
		unregisterListeners();
	}

	protected void registerListeners ()
	{
		LocationManager locationManager = (LocationManager) _context.getSystemService(Context.LOCATION_SERVICE);
		locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, _locationListener);		
		locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, _locationListener);		
	}
	
	protected void unregisterListeners ()
	{
		LocationManager locationManager = (LocationManager) _context.getSystemService(Context.LOCATION_SERVICE);
		locationManager.removeUpdates(_locationListener);
	}
}
