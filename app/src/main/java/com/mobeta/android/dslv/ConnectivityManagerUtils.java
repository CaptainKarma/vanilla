package com.mobeta.android.dslv;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;

public class ConnectivityManagerUtils {

	public static boolean isInternetAvailable(Context context) {
		boolean result = false;
		ConnectivityManager connectivityManager = (ConnectivityManager) context.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
		if (connectivityManager != null) {
			NetworkCapabilities networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.getActiveNetwork());
			if (networkCapabilities != null) {
				if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
					result = true;
				} else if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
					result = true;
				} else {
					result = false;
				}
			}
		}
		return result;
	}
}
