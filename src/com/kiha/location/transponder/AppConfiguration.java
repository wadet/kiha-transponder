package com.kiha.location.transponder;

import android.content.SharedPreferences;

public class AppConfiguration
{
	private SharedPreferences _prefs;
	
	private static final int DEFAULT_SAMPLE_INTERVAL = 1000 * 15; // Millisecs
	private static final String DEFAULT_SERVICE_URL = "http://dev.kihatest.com:8080/api/v1/actions?";
	private static final int DEFAULT_SOCKET_TIMEOUT = 5000;
	private static final int DEFAULT_CONNECTION_TIMEOUT = 5000;
	private static final int DEFAULT_MIN_DISTANCE = 5; // Meters
	private static final boolean DEFAULT_CONSERVE_BATTERY = false;
	
	public AppConfiguration (SharedPreferences prefs)
	{
		_prefs = prefs;
	}
	
	public String getLocationServiceUrl ()
	{
		return _prefs.getString("service-url", DEFAULT_SERVICE_URL);
	}
	
	public int getSampleInterval ()
	{
		return _prefs.getInt("sample-interval", DEFAULT_SAMPLE_INTERVAL);
	}
	
	public int getSocketTimeout ()
	{
		return _prefs.getInt("socket-timeout", DEFAULT_SOCKET_TIMEOUT);
	}
	
	public int getConnectionTimeout ()
	{
		return _prefs.getInt("connection-timeout", DEFAULT_CONNECTION_TIMEOUT);
	}
	
	public int getMinDistance ()
	{
		return _prefs.getInt("min-gps-delta-distance", DEFAULT_MIN_DISTANCE);
	}
	
	public boolean getConserveBatteryMode ()
	{
		return _prefs.getBoolean("conserve-battery", DEFAULT_CONSERVE_BATTERY);
	}
}
