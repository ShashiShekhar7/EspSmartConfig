package com.shashi.espsmartconfig;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.location.LocationManagerCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.LocationManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.espressif.iot.esptouch.util.ByteUtil;
import com.espressif.iot.esptouch2.provision.TouchNetUtil;
import com.google.android.material.snackbar.Snackbar;

import java.net.InetAddress;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private static final int REQUEST_PERMISSION = 0x01;

    private TextView tvConnectedSSID;

    private WifiManager mWifiManager;

    private String mSsid;
    private byte[] mSsidBytes;
    private String mBssid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvConnectedSSID = findViewById(R.id.tv_connected_ssid);

        String[] permissions = {Manifest.permission.ACCESS_FINE_LOCATION};
        requestPermissions(permissions, REQUEST_PERMISSION);

        mWifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);

        EspSmartConfigApp.getInstance().observeBroadcast(this, broadcast -> {
            Log.d(TAG, "onCreate: Broadcast = " + broadcast);
            onWifiChanged();
        });

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                onWifiChanged();
            } else {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.esptouch1_location_permission_title)
                        .setMessage(R.string.esptouch1_location_permission_message)
                        .setCancelable(false)
                        .setPositiveButton(android.R.string.ok, (dialog, which) -> finish())
                        .show();
            }

            return;
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void onWifiChanged() {
        StateResult stateResult = check();
        mSsid = stateResult.ssid;
        CharSequence message = stateResult.message;
        boolean confirmEnable = false;
        if (stateResult.wifiConnected) {
            confirmEnable = true;
            if (stateResult.is5G) {
                message = getString(R.string.esptouch1_wifi_5g_message);
            }
        }
//        else {
//            if (mTask != null) {
//                mTask.cancelEsptouch();
//                mTask = null;
//                new AlertDialog.Builder(MainActivity.this)
//                        .setMessage(R.string.esptouch1_configure_wifi_change_message)
//                        .setNegativeButton(android.R.string.cancel, null)
//                        .show();
//            }
//            new AlertDialog.Builder(MainActivity.this)
//                    .setMessage(R.string.esptouch1_configure_wifi_change_message)
//                    .setNegativeButton(android.R.string.cancel, null)
//                    .show();
//        }

        tvConnectedSSID.setText(mSsid);
        showSnackBar(tvConnectedSSID.getRootView(), message.toString());
    }

    private StateResult check() {
        StateResult result = checkPermission();
        if (!result.permissionGranted) {
            return result;
        }
        result = checkLocation();
        result.permissionGranted = true;
        if (result.locationRequirement) {
            return result;
        }
        result = checkWifi();
        result.permissionGranted = true;
        result.locationRequirement = false;
        return result;
    }

    protected StateResult checkPermission() {
        StateResult result = new StateResult();
        result.permissionGranted = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            boolean locationGranted = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED;
            if (!locationGranted) {
                String[] splits = getString(R.string.esptouch_message_permission).split("\n");
                if (splits.length != 2) {
                    throw new IllegalArgumentException("Invalid String @RES esptouch_message_permission");
                }
                SpannableStringBuilder ssb = new SpannableStringBuilder(splits[0]);
                ssb.append('\n');
                SpannableString clickMsg = new SpannableString(splits[1]);
                ForegroundColorSpan clickSpan = new ForegroundColorSpan(0xFF0022FF);
                clickMsg.setSpan(clickSpan, 0, clickMsg.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
                ssb.append(clickMsg);
                result.message = ssb;
                return result;
            }
        }

        result.permissionGranted = true;
        return result;
    }

    protected StateResult checkLocation() {
        StateResult result = new StateResult();
        result.locationRequirement = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            LocationManager manager = getSystemService(LocationManager.class);
            boolean enable = manager != null && LocationManagerCompat.isLocationEnabled(manager);
            if (!enable) {
                result.message = getString(R.string.esptouch_message_location);
                return result;
            }
        }

        result.locationRequirement = false;
        return result;
    }

    protected StateResult checkWifi() {
        StateResult result = new StateResult();
        result.wifiConnected = false;
        WifiInfo wifiInfo = mWifiManager.getConnectionInfo();
        boolean connected = TouchNetUtil.isWifiConnected(mWifiManager);
        if (!connected) {
            result.message = getString(R.string.esptouch_message_wifi_connection);
            return result;
        }

        String ssid = TouchNetUtil.getSsidString(wifiInfo);
        int ipValue = wifiInfo.getIpAddress();
        if (ipValue != 0) {
            result.address = TouchNetUtil.getAddress(wifiInfo.getIpAddress());
        } else {
            result.address = TouchNetUtil.getIPv4Address();
            if (result.address == null) {
                result.address = TouchNetUtil.getIPv6Address();
            }
        }

        result.wifiConnected = true;
        result.message = "";
        result.is5G = TouchNetUtil.is5G(wifiInfo.getFrequency());
        if (result.is5G) {
            result.message = getString(R.string.esptouch_message_wifi_frequency);
        }
        result.ssid = ssid;
        result.ssidBytes = TouchNetUtil.getRawSsidBytesOrElse(wifiInfo, ssid.getBytes());
        result.bssid = wifiInfo.getBSSID();

        return result;
    }

    private void executeEsptouch() {
        byte[] ssid = mSsidBytes == null ? ByteUtil.getBytesByString(this.mSsid)
                : mSsidBytes;
        CharSequence pwdStr = mBinding.apPasswordEdit.getText();
        byte[] password = pwdStr == null ? null : ByteUtil.getBytesByString(pwdStr.toString());
        byte[] bssid = com.espressif.iot.esptouch.util.TouchNetUtil.parseBssid2bytes(this.mBssid);
        CharSequence devCountStr = mBinding.deviceCountEdit.getText();
        byte[] deviceCount = devCountStr == null ? new byte[0] : devCountStr.toString().getBytes();
        byte[] broadcast = {(byte) (mBinding.packageModeGroup.getCheckedRadioButtonId() == R.id.packageBroadcast
                ? 1 : 0)};

        if (mTask != null) {
            mTask.cancelEsptouch();
        }
        mTask = new EsptouchAsyncTask4(this);
        mTask.execute(ssid, bssid, password, deviceCount, broadcast);
    }

    public Snackbar showSnackBar(View view, String msg) {
        Snackbar snackbar = Snackbar.make(view, msg, Snackbar.LENGTH_INDEFINITE);

        snackbar.setAction("OK", v1 -> snackbar.dismiss());

        View view1 = snackbar.getView();
        view1.setBackgroundColor(Color.parseColor("#ff0000"));

        snackbar.show();
        return snackbar;
    }

    protected static class StateResult {
        public CharSequence message = null;

        public boolean permissionGranted = false;

        public boolean locationRequirement = false;

        public boolean wifiConnected = false;
        public boolean is5G = false;
        public InetAddress address = null;
        public String ssid = null;
        public byte[] ssidBytes = null;
        public String bssid = null;
    }
}