package com.shashi.espsmartconfig;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.LocationManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;

import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

public class EspSmartConfigApp extends Application {
    private static final String TAG = "ApplicationClass";
    private static EspSmartConfigApp app;

    private MutableLiveData<String> mBroadcastData;

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) {
                return;
            }

            switch (action) {
                case WifiManager
                        .NETWORK_STATE_CHANGED_ACTION:
                case LocationManager
                            .PROVIDERS_CHANGED_ACTION:
                    mBroadcastData.setValue(action);
                break;
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate: ");
        app = this;
        mBroadcastData = new MutableLiveData<>();
        IntentFilter filter = new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            filter.addAction(LocationManager.PROVIDERS_CHANGED_ACTION);
        }
        registerReceiver(mReceiver, filter);
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        unregisterReceiver(mReceiver);
    }

    public static EspSmartConfigApp getInstance() {
        return app;
    }

    public void observeBroadcast(LifecycleOwner owner, Observer<String> observer) {
        mBroadcastData.observe(owner, observer);
    }
}
