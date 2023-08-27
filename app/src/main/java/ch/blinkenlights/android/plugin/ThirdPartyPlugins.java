package ch.blinkenlights.android.plugin;

import static ch.blinkenlights.android.plugin.ThirdParty.nuberu_url;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import ch.blinkenlights.android.vanilla.FileUtils;
import ch.blinkenlights.android.vanilla.MediaUtils;
import ch.blinkenlights.android.vanilla.PlaybackService;
import ch.blinkenlights.android.vanilla.PrefDefaults;
import ch.blinkenlights.android.vanilla.PrefKeys;
import ch.blinkenlights.android.vanilla.QueryTask;
import ch.blinkenlights.android.vanilla.R;
import ch.blinkenlights.android.vanilla.SharedPrefHelper;
import ch.blinkenlights.android.vanilla.Song;
import ch.blinkenlights.android.vanilla.SongTimeline;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Calendar;
import java.util.Locale;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

public class ThirdPartyPlugins {
	private Context mContext;

	public ThirdPartyPlugins(Context mContext) {
		this.mContext = mContext;
	}
	// Volley will by default retry causing multiple files to be loaded
	// change the default Retry Policy to stop this:
	/**
	 * The default socket timeout in milliseconds
	 */
	public static final int DEFAULT_TIMEOUT_MS = 10000;
	/**
	 * The default number of retries
	 */
	public static final int DEFAULT_MAX_RETRIES = 0;
	/**
	 * The default backoff multiplier
	 */
	public static final float DEFAULT_BACKOFF_MULTI = 0;
	private String filePath;
	private Handler mainHandler;

	// Request a track using the now playing track as the seed track
	// Gets called multiple times until track provided or fallback to album
	public void request_track_from_seed_track(String seed_track) {
		SharedPreferences settings = SharedPrefHelper.getSettings(mContext);
		String acc = settings.getString(PrefKeys.TP_ACCOUNT_ID_KEY, PrefDefaults.TP_ACCOUNT_ID_DEFAULT);  // <- Pull from preferences/GUI
		String device = settings.getString(PrefKeys.TP_DEVICE_ID_KEY, PrefDefaults.TP_DEVICE_ID_DEFAULT); // <- Pull from preferences/GUI
		String email = settings.getString(PrefKeys.TP_EMAIL_ADDRESS_KEY, PrefDefaults.TP_EMAIL_ADDRESS_DEFAULT);    // <- Pull from preferences/GUI
		String action = "play";

		long idempotency_key = System.currentTimeMillis() / 1000L;

		Log.d("VanillaICE", "## Requesting Track Using Seed:" + seed_track);
		// Write to Debug physical file
		ThirdPartyPlugins thirdPartyPlugins = new ThirdPartyPlugins(mContext);
		thirdPartyPlugins.appendLog(">> OUT >> " + seed_track);
		//
		RequestQueue requestQueue = Volley.newRequestQueue(mContext);

		JSONObject postData = new JSONObject();
		try {
			postData.put("email", email);
			postData.put("acc", acc);
			postData.put("player", device);
			postData.put("action", action);
			postData.put("current", seed_track);
			postData.put("idempotency", idempotency_key);
		} catch (JSONException e) {
			e.printStackTrace();
		}

		JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, nuberu_url, postData, new Response.Listener<JSONObject>() {
			@Override
			public void onResponse(JSONObject response) {
				Log.d("VanillaICE", "Response from Nuberu-START");
				Log.d("VanillaICE", String.valueOf(response));
				Log.d("VanillaICE", "Response from Nuberu-END");
				// Write to Debug physical file
				ThirdPartyPlugins thirdPartyPlugins = new ThirdPartyPlugins(mContext);
				thirdPartyPlugins.appendLog("<< IN << " + String.valueOf(response));
				//

				final String me_track;
				final String idempotency_return;

				try {
					me_track = response.getString("track");
					idempotency_return = response.getString("idempotency_return");
					String idempotency_check = Long.toString(idempotency_key);


					if (!me_track.contains("MusicIP API error") && idempotency_return.equals(idempotency_check) ) {
						// when API fails we get this sent and we record it as playing track

						thirdPartyPlugins.appendLog("<< PLAY << " + me_track);

						Song song = new Song(FileUtils.songIdFromFile(new File(me_track)));

						if (!TextUtils.isEmpty(me_track)) {
							song.title = me_track.substring(me_track.lastIndexOf('/') + 1);
						} else {
							song.title = me_track;
						}

						song.path = me_track;
						int mode;
						QueryTask query;
						mode = SongTimeline.MODE_ENQUEUE;

						if (song.id < 0) {
							query = MediaUtils.buildFileQuery(song.path, Song.FILLED_PROJECTION, false /* recursive */);
						} else {
							query = MediaUtils.buildQuery(MediaUtils.TYPE_SONG, song.id, Song.FILLED_PROJECTION, null);
						}

						query.mode = mode;
						PlaybackService service = PlaybackService.get(mContext);
						service.addSongs(query);

					} else {
						Log.d("VanillaICE", "Idempotency Token Not Matching");
					}
				} catch (JSONException e) {
					e.printStackTrace();
				}
			}
		}, new Response.ErrorListener() {
			@Override
			public void onErrorResponse(VolleyError error) {
				error.printStackTrace();
				// Write to Debug physical file
				ThirdPartyPlugins thirdPartyPlugins = new ThirdPartyPlugins(mContext);
				thirdPartyPlugins.appendLog("VolleyErrorResponse: " + error);
				//
				Log.d("VanillaICE", "VolleyErrored");
				Log.d("VanillaICE", String.valueOf(error));
			}
		});
		jsonObjectRequest.setRetryPolicy(new DefaultRetryPolicy(
				DEFAULT_TIMEOUT_MS,
				DEFAULT_MAX_RETRIES,
				DEFAULT_BACKOFF_MULTI));

		requestQueue.add(jsonObjectRequest);
	}

	// Send downgrade of track back to system
	public void send_downgrade_track() {
		SharedPreferences settings = SharedPrefHelper.getSettings(mContext);
		String acc = settings.getString(PrefKeys.TP_ACCOUNT_ID_KEY, PrefDefaults.TP_ACCOUNT_ID_DEFAULT);  // <- Pull from preferences/GUI
		String device = settings.getString(PrefKeys.TP_DEVICE_ID_KEY, PrefDefaults.TP_DEVICE_ID_DEFAULT); // <- Pull from preferences/GUI
		String email = settings.getString(PrefKeys.TP_EMAIL_ADDRESS_KEY, PrefDefaults.TP_EMAIL_ADDRESS_DEFAULT);    // <- Pull from preferences/GUI

		long idempotency_key = System.currentTimeMillis() / 1000L;

		PlaybackService service = PlaybackService.get(mContext);
		String seed_track = service.mCurrentSong.path;

		Log.d("VanillaICE", "## Downgrading Track:" + seed_track);

		RequestQueue requestQueue = Volley.newRequestQueue(mContext);

		JSONObject postData = new JSONObject();
		try {
			postData.put("email", email);
			postData.put("acc", acc);
			postData.put("player", device);
			postData.put("action", "track_update");
			postData.put("current", seed_track);
			postData.put("set_rating", "20");
			postData.put("idempotency", idempotency_key);
		} catch (JSONException e) {
			e.printStackTrace();
		}

		JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, nuberu_url, postData, new Response.Listener<JSONObject>() {
			@Override
			public void onResponse(JSONObject response) {
				Log.d("VanillaICE", "Response from Nuberu-START");
				Log.d("VanillaICE", String.valueOf(response));
				Log.d("VanillaICE", "Response from Nuberu-END");

				Toast.makeText(mContext, R.string.thirdparty_nuberu_downgrade, Toast.LENGTH_SHORT).show();

			}
		}, new Response.ErrorListener() {
			@Override
			public void onErrorResponse(VolleyError error) {
				error.printStackTrace();
				// Write to Debug physical file
				ThirdPartyPlugins thirdPartyPlugins = new ThirdPartyPlugins(mContext);
				thirdPartyPlugins.appendLog("VolleyErrorResponse: " + error);
				//
				Log.d("VanillaICE", "Volley Downgrade Errored");
				Log.d("VanillaICE", String.valueOf(error));
			}
		});
		jsonObjectRequest.setRetryPolicy(new DefaultRetryPolicy(
				DEFAULT_TIMEOUT_MS,
				DEFAULT_MAX_RETRIES,
				DEFAULT_BACKOFF_MULTI));

		requestQueue.add(jsonObjectRequest);
	}


	public void appendLog(String text)
	{
		final String log_output = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS) + "/vanillaICE.log";

		File logFile = new File(log_output);
		if (!logFile.exists())
		{
			try
			{
				logFile.createNewFile();
			}
			catch (IOException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		try
		{
			// Get the current date and time
			Date currentDate = new Date();
			Calendar calendar = Calendar.getInstance();
			calendar.setTime(currentDate);

			// Create a SimpleDateFormat instance with the desired format
			SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
			SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

			// Format the date and time objects into strings
			String dateString = dateFormat.format(currentDate);
			String timeString = timeFormat.format(calendar.getTime());

			//BufferedWriter for performance, true to set append to file flag
			BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, true));
			buf.append(dateString).append(String.valueOf(' ')).append(timeString).append(String.valueOf(' ')).append(text);
			buf.newLine();
			buf.close();
		}
		catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

    /*
    String filename = "/storage/emulated/0/Music/my_song.mp3";
	long mediaId = getMediaStoreIdFromFilename(this, filename, MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO);
     if (mediaId != -1) {
		// MediaStore ID found
		// Do something with the media ID
	} else {
		// File not found in MediaStore
	}
	*/
	public static long getMediaStoreIdFromFilename(Context context, String filename, int mediaType) {
		ContentResolver contentResolver = context.getContentResolver();
		Uri mediaUri;

		if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO) {
			mediaUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
		} else if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE) {
			mediaUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
		} else if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) {
			mediaUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
		} else {
			return -1; // Invalid media type
		}

		String[] projection = {MediaStore.MediaColumns._ID};
		String selection = MediaStore.MediaColumns.DATA + "=?";
		String[] selectionArgs = {filename};

		Cursor cursor = contentResolver.query(mediaUri, projection, selection, selectionArgs, null);

		if (cursor != null && cursor.moveToFirst()) {
			int idColumnIndex = cursor.getColumnIndex(MediaStore.MediaColumns._ID);
			long mediaId = cursor.getLong(idColumnIndex);
			cursor.close();
			return mediaId;
		}

		if (cursor != null) {
			cursor.close();
		}

		return -1; // File not found in MediaStore
	}




// End of class
}

