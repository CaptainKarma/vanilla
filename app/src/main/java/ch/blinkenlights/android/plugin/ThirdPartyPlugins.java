package ch.blinkenlights.android.plugin;

import static ch.blinkenlights.android.plugin.ThirdParty.nuberu_url;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.EditTextPreference;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import java.io.File;
import java.util.Map;

import ch.blinkenlights.android.vanilla.FileUtils;
import ch.blinkenlights.android.vanilla.MediaUtils;
import ch.blinkenlights.android.vanilla.PlaybackService;
import ch.blinkenlights.android.vanilla.PrefDefaults;
import ch.blinkenlights.android.vanilla.PrefKeys;
import ch.blinkenlights.android.vanilla.QueryTask;
import ch.blinkenlights.android.vanilla.SharedPrefHelper;
import ch.blinkenlights.android.vanilla.Song;
import ch.blinkenlights.android.vanilla.SongTimeline;

public class ThirdPartyPlugins {
	private Context mContext;
//	public long idempotency_key;

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

	// Request a track using the now playing track as the seed track
	public void request_track_from_seed_track(String seed_track) {
		SharedPreferences settings = SharedPrefHelper.getSettings(mContext);
		String acc = settings.getString(PrefKeys.TP_ACCOUNT_ID_KEY, PrefDefaults.TP_ACCOUNT_ID_DEFAULT);  // <- Pull from preferences/GUI
		String device = settings.getString(PrefKeys.TP_DEVICE_ID_KEY, PrefDefaults.TP_DEVICE_ID_DEFAULT); // <- Pull from preferences/GUI
		String email = settings.getString(PrefKeys.TP_EMAIL_ADDRESS_KEY, PrefDefaults.TP_EMAIL_ADDRESS_DEFAULT);    // <- Pull from preferences/GUI
		String action = "play";

		long idempotency_key = System.currentTimeMillis() / 1000L;

		Log.d("VanillaICE", "## Requesting Track Using Seed:" + seed_track);

		String postUrl = nuberu_url;
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

		JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, postUrl, postData, new Response.Listener<JSONObject>() {
			@Override
			public void onResponse(JSONObject response) {
				Log.d("VanillaICE", "Response from Nuberu-START");
				Log.d("VanillaICE", String.valueOf(response));
				Log.d("VanillaICE", "Response from Nuberu-END");
				final String me_track;
				final String idempotency_return;

				try {
					me_track = response.getString("track");
					idempotency_return = response.getString("idempotency_return");
					String idempotency_check = Long.toString(idempotency_key);

					if (me_track.contains("MusicIP API error")) {        // when API fails we get this sent and we record it as playing track
						Log.d("VanillaICE", "Nuberu ERROR Response as:" + me_track);
					} else {
						// if the return token matches what we sent out
						if (idempotency_return.equals(idempotency_check)) {
							Log.d("VanillaICE", "OK Response Adding Track: " + me_track);
							Log.d("VanillaICE", "Idempotency Token Matched: " + idempotency_return);

							Song song = new Song(FileUtils.songIdFromFile(new File(me_track)));
							if (!TextUtils.isEmpty(me_track))
								song.title = me_track.substring(me_track.lastIndexOf('/') + 1);
							else
								song.title = me_track;
							song.path = me_track;
							int mode;
							QueryTask query;
							mode = SongTimeline.MODE_ENQUEUE;
//
//							// This code is not reached unless mSong is filled and non-empty
							if (song.id < 0) {
								query = MediaUtils.buildFileQuery(song.path, Song.FILLED_PROJECTION, false /* recursive */);
							} else {
								query = MediaUtils.buildQuery(MediaUtils.TYPE_SONG, song.id, Song.FILLED_PROJECTION, null);
							}

							query.mode = mode;

							PlaybackService service = PlaybackService.get(mContext);
							service.addSongs(query);
						} else {
							Log.d("VanillaICE", "OK Response but Idempotency Token Not Matching: " + idempotency_return);
						}
					}
				} catch (JSONException e) {
					e.printStackTrace();
				}
			}
		}, new Response.ErrorListener() {
			@Override
			public void onErrorResponse(VolleyError error) {
				error.printStackTrace();
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
}
