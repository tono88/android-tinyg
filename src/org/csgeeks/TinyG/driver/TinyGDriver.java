package org.csgeeks.TinyG.driver;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;

import org.csgeeks.TinyG.R;
import org.csgeeks.TinyG.system.Machine;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

public class TinyGDriver extends Service {
	private Machine machine;
    private NotificationManager mNM;
	private ListenerTask mListener;
	private String tgfx_hostname;
	private int tgfx_port;
	private boolean ready;
	private InputStream is;
	private OutputStream os;
	private Socket socket;

	private static final String TAG = "TinyG";
	public static final String CMD_GET_OK_PROMPT = "{\"gc\":\"?\"}\n";
	public static final String CMD_GET_STATUS_REPORT = "{\"sr\":null}\n";
	public static final String CMD_ZERO_ALL_AXIS = "{\"gc\":\"g92x0y0z0a0\"}\n";
	public static final String CMD_DISABLE_LOCAL_ECHO = "{\"ee\":0}\n";
	public static final String CMD_SET_STATUS_UPDATE_INTERVAL = "{\"si\":150}\n";
	public static final String CMD_GET_MACHINE_SETTINGS = "{\"sys\":null}\n";
	public static final String CMD_SET_UNIT_MM = "{\"gc\":\"g21\"}\n";
	public static final String CMD_SET_UNIT_INCHES = "{\"gc\":\"g20\"}\n";
	public static final String CMD_GET_X_AXIS = "{\"x\":null}\n";
	public static final String CMD_GET_Y_AXIS = "{\"y\":null}\n";
	public static final String CMD_GET_Z_AXIS = "{\"z\":null}\n";
	public static final String CMD_GET_A_AXIS = "{\"a\":null}\n";
	public static final String CMD_GET_B_AXIS = "{\"b\":null}\n";
	public static final String CMD_GET_C_AXIS = "{\"c\":null}\n";
	public static final String CMD_GET_MOTOR_1_SETTINGS = "{\"1\":null}\n";
	public static final String CMD_GET_MOTOR_2_SETTINGS = "{\"2\":null}\n";
	public static final String CMD_GET_MOTOR_3_SETTINGS = "{\"3\":null}\n";
	public static final String CMD_GET_MOTOR_4_SETTINGS = "{\"4\":null}\n";
	private static final String STATUS_REPORT = "{\"sr\":{";
	private static final String CMD_PAUSE = "!\n";
	private static final String CMD_RESUME = "~\n";

    public class NetworkBinder extends Binder {
        public TinyGDriver getService() {
            return TinyGDriver.this;
        }
    }
    
    @Override
    public void onCreate() {
        mNM = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
		machine = new Machine();
		ready = false;
		Context mContext = getApplicationContext();
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mContext);
		tgfx_hostname = settings.getString("tgfx_hostname", "127.0.0.1");
		tgfx_port = Integer.parseInt(settings.getString("tgfx_port", "4444"));
		
		new ConnectTask().execute(0);
		Toast.makeText(this, "Connecting...", Toast.LENGTH_SHORT).show();

        // Display a notification about us starting.  We put an icon in the status bar.
        showNotification();
    }

    @Override
    public void onDestroy() {
        // Cancel the persistent notification.
        mNM.cancel(R.string.local_service_started);

        // Tell the user we stopped.
        Toast.makeText(this, R.string.local_service_stopped, Toast.LENGTH_SHORT).show();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    // This is the object that receives interactions from clients.  See
    // RemoteService for a more complete example.
    private final IBinder mBinder = new NetworkBinder();

    private void showNotification() {
        // In this sample, we'll use the same text for the ticker and the expanded notification
        CharSequence text = getText(R.string.local_service_started);

        // Set the icon, scrolling text and timestamp
        Notification notification = new Notification(R.drawable.stat_sample, text,
                System.currentTimeMillis());

        // The PendingIntent to launch our activity if the user selects this notification
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, TinyGDriver.class), 0);

        // Set the info for the views that show in the notification panel.
        notification.setLatestEventInfo(this, getText(R.string.local_service_label),
                       text, contentIntent);

        // Send the notification.
        // We use a layout id because it is a unique number.  We use it later to cancel.
        mNM.notify(R.string.local_service_started, notification);
    }

	private class ConnectTask extends AsyncTask<Integer, Integer, RetCode> {
		@Override
		protected RetCode doInBackground(Integer... params) {
			Log.d(TAG,
					"Starting connect to " + tgfx_hostname + " in background");
			try {
				socket = new Socket(tgfx_hostname, tgfx_port);
				os = socket.getOutputStream();
				is = socket.getInputStream();
			} catch (UnknownHostException e) {
				socket = null;
				Log.e(TAG,"Socket: " + e.getMessage());
				return new RetCode(false, e.getMessage());
			} catch (IOException e) {
				socket = null;
				Log.e(TAG,"Socket: " + e.getMessage());
				return new RetCode(false, e.getMessage());
			}
			return new RetCode(true, null);
		}

		protected void onPostExecute(RetCode res) {
			if (res.result) {
				Toast.makeText(TinyGDriver.this, "Connected", Toast.LENGTH_SHORT)
						.show();

				mListener = new ListenerTask();
				mListener.execute(new InputStream[] { is });
				Log.i(TAG, "Listener started");
				write(TinyGDriver.CMD_DISABLE_LOCAL_ECHO);
				write(TinyGDriver.CMD_GET_OK_PROMPT);
				write(TinyGDriver.CMD_SET_STATUS_UPDATE_INTERVAL);
				write(TinyGDriver.CMD_GET_OK_PROMPT);
				write(TinyGDriver.CMD_GET_STATUS_REPORT);
				write(TinyGDriver.CMD_GET_OK_PROMPT);
			} else {
				CharSequence c = res.message;
				Toast.makeText(TinyGDriver.this, c, Toast.LENGTH_SHORT).show();
				stopSelf();
			}
		}
	}

	private class ListenerTask extends AsyncTask<InputStream, String, Void> {

		@Override
		protected Void doInBackground(InputStream... params) {
			byte[] buffer = new byte[1024];
			InputStream is = params[0];
			int b;
			int idx = 0;
			try {
				while (!isCancelled()) {
					if ((b = is.read()) == -1) {
						break;
					}
					buffer[idx++] = (byte) b;
					if (b == '\n') {
						publishProgress(new String(buffer, 0, idx));
						idx = 0;
					}
				}
			} catch (IOException e) {
				Log.e(TAG, "listener read: " + e.getMessage());
			}
			return null;
		}

		@Override
		protected void onProgressUpdate(String... values) {
			if (values.length > 0) {
				Log.i(TAG, "onProgressUpdate: " + values[0].length()
						+ " bytes received.");
				if (processJSON(values[0])) {
					// TODO: Post update to GUI.  Use messaging?
				}
			}
		}

		@Override
		protected void onCancelled() {
			Log.i(TAG, "ListenerTask cancelled");
		}
	}

	public Machine getMachine() {
		return machine;
	}

	public boolean processJSON(String string) {
		try {
			JSONObject json = new JSONObject(string);
			if (json.has("sr")) {
				JSONObject sr = json.getJSONObject("sr");
				// We should do this based on the machine configuration.
				// Hardcoded for now.
				machine.getAxisByName("X").setWork_position(
						(float) sr.getDouble("posx"));
				machine.getAxisByName("Y").setWork_position(
						(float) sr.getDouble("posy"));
				machine.getAxisByName("Z").setWork_position(
						(float) sr.getDouble("posz"));
				machine.getAxisByName("A").setWork_position(
						(float) sr.getDouble("posa"));
				machine.setLine_number(sr.getInt("line"));
				machine.setVelocity((float) sr.getDouble("vel"));
				machine.setUnits(sr.getInt("unit"));
				machine.setMotionMode(sr.getInt("momo"));
				machine.setMachineState(sr.getInt("stat"));
				ready = true;
				return true;
			}
			if (json.has("gc")) {
				JSONObject gc = json.getJSONObject("gc");
				String gcode = gc.getString("gc");
				if (gc.getString("msg").equals("OK")) {
					if (gcode.regionMatches(0, "G20", 0, 3)) {
						// Hack
						Log.i(TAG, "Got confirmation of unit change to inches");

					}
					if (gcode.regionMatches(0, "G21", 0, 3)) {
						// Hack
						Log.i(TAG, "Got confirmation of unit change to mm");

					}
					if (gcode.regionMatches(0, "G92", 0, 3)) {
						// Should set the local screen value based on this.
						// Instead, I'm going to cheat
						// and set it when we send the zero command...
						Log.i(TAG, "Got confirmation of zeroing");
					}
				}
			}
			if (json.has("sys")) {

			}
		} catch (JSONException e) {
			Log.e(TAG,
					"Received malformed JSON: " + string + ": "
							+ e.getMessage());
		}
		return false;
	}

	public boolean isReady() {
		return ready;
	}

	public RetCode disconnect() {
		if (mListener != null) {
			mListener.cancel(true);	
		}
		if (socket != null) {
			try {
				Log.d(TAG,"closing socket");
				socket.close();
				is.close();
				os.close();
			} catch (IOException e) {
				Log.e(TAG, "Close: " + e.getMessage());
				return new RetCode(false, e.getMessage());
			}
			is = null;
			os = null;
			socket = null;
			ready = false;
		}
		Log.d(TAG,"disconnect done");
		return new RetCode(true, null);
	}

	public void write(String message) {
		try {
			Log.d(TAG,"writing to tinyg: " + message);
			os.write(message.getBytes());
		} catch (IOException e) {
			Log.e(TAG, "write: " + e.getMessage());
		}
	}

	public RetCode connect() {
		return new RetCode(true, null);
	}
}
