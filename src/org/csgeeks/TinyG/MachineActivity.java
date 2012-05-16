package org.csgeeks.TinyG;

// Copyright 2012 Matthew Stock

import org.csgeeks.TinyG.Support.*;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Messenger;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class MachineActivity extends FragmentActivity {
	private static final String TAG = "TinyG";
	private TinyGMessenger tinyg;
	private ServiceConnection mConnection;
	private BroadcastReceiver mIntentReceiver;
	private int bindType = 0;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.machine);
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
		mConnection = new DriverServiceConnection();
		Context mContext = getApplicationContext();
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mContext);
		bindType = Integer.parseInt(settings.getString("tgfx_driver", "0"));

		if (savedInstanceState != null) {
			restoreState(savedInstanceState);
		}

		if (bindDriver(mConnection) == false) {
			Toast.makeText(this, "Binding service failed", Toast.LENGTH_SHORT)
					.show();
		}
	}

	private boolean bindDriver(ServiceConnection s) {
		switch (bindType) {
		case 0:
			return bindService(new Intent(this, TinyGNetwork.class),
					s, Context.BIND_AUTO_CREATE);
		case 1:
			return bindService(new Intent(TinyGDriver.USB_SERVICE),
					s, Context.BIND_AUTO_CREATE);
		default:
			return false;
		}
	}

	@Override
	public void onDestroy() {
		unbindService(mConnection);
		super.onDestroy();
	}

	@Override
	public void onResume() {
		IntentFilter updateFilter = new IntentFilter(TinyGDriver.MACHINE_CONFIG);
		mIntentReceiver = new TinyGServiceReceiver();
		registerReceiver(mIntentReceiver, updateFilter);
		super.onResume();
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putInt("bindType", bindType);
	}

	private void restoreState(Bundle inState) {
		bindType = inState.getInt("bindType");
	}

	@Override
	public void onPause() {
		unregisterReceiver(mIntentReceiver);
		super.onPause();
	}
	
	public void myClickHandler(View view) {
		// Just in case something happened, though it seems like this shouldn't
		// be possible.
		if (tinyg == null) {
			if (bindService(new Intent(this, TinyGDriver.class),
					mConnection, Context.BIND_AUTO_CREATE)) {
			} else {
				Toast.makeText(this, "Binding service failed",
						Toast.LENGTH_SHORT).show();
			}
			return;
		}
		switch (view.getId()) {
		case R.id.save:
			// TODO
			break;
		}
	}

	private class DriverServiceConnection implements ServiceConnection {
		public void onServiceConnected(ComponentName className, IBinder service) {
			tinyg = new TinyGMessenger(new Messenger(service));
			tinyg.send_command(TinyGDriver.GET_MACHINE);
		}

		public void onServiceDisconnected(ComponentName className) {
			tinyg = null;
		}
	}

	private class TinyGServiceReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			Bundle b = intent.getExtras();
			
			if (action.equals(TinyGDriver.MACHINE_CONFIG)) {
				Log.d(TAG, "MachineActivity got an intent");
				((TextView) findViewById(R.id.firmware_build)).setText(Float.toString(b.getFloat("firmware_build")));
				((TextView) findViewById(R.id.firmware_version)).setText(Float.toString(b.getFloat("firmware_version")));
				((EditText) findViewById(R.id.system_interval)).setText(Integer.toString(b.getInt("system_interval")));
			}
		}
	}
}
