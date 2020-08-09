/*
  Android_Pebble_sd - Android alarm client for openseizuredetector..

  See http://openseizuredetector.org for more information.

  Copyright Graham Jones, 2015, 2016

  This file is part of pebble_sd.

  Android_Pebble_sd is free software: you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation, either version 3 of the License, or
  (at your option) any later version.

  Android_Pebble_sd is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with Android_pebble_sd.  If not, see <http://www.gnu.org/licenses/>.

*/
package uk.org.openseizuredetector;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;


/**
 * A data source that registers for BLE GATT notifications from a device and
 * waits to be notified of data being available.
 */
public class SdDataSourceBLE extends SdDataSource {
    private String TAG = "SdDataSourceBLE";
    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;
    private int mConnectionState = STATE_DISCONNECTED;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    public final static String ACTION_GATT_CONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.example.bluetooth.le.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
            "com.example.bluetooth.le.EXTRA_DATA";


    public static String SERV_DEV_INFO = "0000180a-0000-1000-8000-00805f9b34fb";
    public static String SERV_HEART_RATE = "0000180d-0000-1000-8000-00805f9b34fb";
    public static String SERV_OSD = "xxxxxxxxxxxxxxxxxxxx";
    public static String CHAR_HEART_RATE_MEASUREMENT = "00002a37-0000-1000-8000-00805f9b34fb";
    public static String CHAR_MANUF_NAME = "00002a29-0000-1000-8000-00805f9b34fb";
    public static String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";
    public static String CHAR_OSD_ACC_DATA = "xxxxxxxxxxxxxxxxxx";


    public final static UUID UUID_HEART_RATE_MEASUREMENT = UUID.fromString(CHAR_HEART_RATE_MEASUREMENT);


    public SdDataSourceBLE(Context context, Handler handler,
                           SdDataReceiver sdDataReceiver) {
        super(context, handler, sdDataReceiver);
        mName = "BLE";
        // Set default settings from XML files (mContext is set by super().
        PreferenceManager.setDefaultValues(mContext,
                R.xml.network_passive_datasource_prefs, true);
    }


    /**
     * Start the datasource updating - initialises from sharedpreferences first to
     * make sure any changes to preferences are taken into account.
     */
    public void start() {
        Log.i(TAG, "start()");
        super.start();
        mUtil.writeToSysLogFile("SdDataSourceBLE.start() - mBleDeviceAddr=" + mBleDeviceAddr);

        if (mBleDeviceAddr == "" || mBleDeviceAddr == null) {
            final Intent intent = new Intent(this.mContext, BLEScanActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(intent);
        }
        Log.i(TAG, "mBLEDevice is " + mBleDeviceName + ", Addr=" + mBleDeviceAddr);

        bleConnect();

    }

    private void bleConnect() {
        // Now we have selected a BLE Device, open the bluetooth adapter and connect to it.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
        }

        if (mBluetoothAdapter == null || mBleDeviceAddr == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(mBleDeviceAddr);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = device.connectGatt(mContext, false, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = mBleDeviceAddr;
        mConnectionState = STATE_CONNECTING;

    }

    private void bleDisconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;

    }

    /**
     * Stop the datasource from updating
     */
    public void stop() {
        Log.i(TAG, "stop()");
        mUtil.writeToSysLogFile("SDDataSourceBLE.stop()");

        bleDisconnect();
        super.stop();
    }


    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                mConnectionState = STATE_CONNECTED;
                mSdData.watchConnected = true;
                Log.i(TAG, "Connected to GATT server.");
                // Attempts to discover services after successful connection.
                Log.i(TAG, "Attempting to start service discovery:");
                mBluetoothGatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                mConnectionState = STATE_DISCONNECTED;
                mSdData.watchConnected = false;
                Log.i(TAG, "Disconnected from GATT server - retrying...");
                bleDisconnect();  // Tidy up connections
                bleConnect();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.v(TAG, "Services discovered");
                List<BluetoothGattService> serviceList = mBluetoothGatt.getServices();
                for (int i = 0; i < serviceList.size(); i++) {
                    String uuidStr = serviceList.get(i).getUuid().toString();
                    Log.v(TAG, "Service " + uuidStr);
                    List<BluetoothGattCharacteristic> gattCharacteristics =
                            serviceList.get(i).getCharacteristics();
                    if (uuidStr.equals(SERV_DEV_INFO)) {
                        Log.v(TAG, "Device Info Service Discovered");
                    } else if (uuidStr.equals(SERV_HEART_RATE)) {
                        Log.v(TAG, "Heart Rate Service Discovered");
                        for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                            String charUuidStr = gattCharacteristic.getUuid().toString();
                            if (charUuidStr.equals(CHAR_HEART_RATE_MEASUREMENT)) {
                                Log.v(TAG, "Subscribing to Heart Rate Measurement Change Notifications");
                                setCharacteristicNotification(gattCharacteristic, true);
                            }
                        }
                    } else if (uuidStr.equals(SERV_OSD)) {
                        Log.v(TAG, "OpenSeizureDetector Service Discovered");
                        for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                            String charUuidStr = gattCharacteristic.getUuid().toString();
                            if (charUuidStr.equals(CHAR_OSD_ACC_DATA)) {
                                Log.v(TAG, "Subscribing to Acceleration Data Change Notifications");
                                setCharacteristicNotification(gattCharacteristic,true);
                            }
                        }
                    }
                }
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        public void onDataReceived(BluetoothGattCharacteristic characteristic) {
            // FIXME - collect data until we have enough to do analysis, then use onDataReceived to process it.
            if (characteristic.getUuid().toString().equals(CHAR_HEART_RATE_MEASUREMENT)) {
                int flag = characteristic.getProperties();
                int format = -1;
                if ((flag & 0x01) != 0) {
                    format = BluetoothGattCharacteristic.FORMAT_UINT16;
                    //Log.d(TAG, "Heart rate format UINT16.");
                } else {
                    format = BluetoothGattCharacteristic.FORMAT_UINT8;
                    //Log.d(TAG, "Heart rate format UINT8.");
                }
                final int heartRate = characteristic.getIntValue(format, 1);
                Log.d(TAG, String.format("Received heart rate: %d", heartRate));
            }
            if (characteristic.getUuid().toString().equals(CHAR_OSD_ACC_DATA)) {
                Log.v(TAG,"Received OSD ACC DATA");
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            Log.v(TAG,"onCharacteristicRead");
            if (status == BluetoothGatt.GATT_SUCCESS) {
                onDataReceived(characteristic);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            Log.v(TAG,"Characteristic "+characteristic.getUuid()+" changed");
            onDataReceived(characteristic);
        }
    };


    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled        If true, enable notification.  False otherwise.
     */
    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                              boolean enabled) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        Log.v(TAG,"setCharacteristicNotification - Requesting notifications");
        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);

        // This is specific to Heart Rate Measurement.
        if (UUID_HEART_RATE_MEASUREMENT.equals(characteristic.getUuid())) {
            Log.v(TAG,"setCharacteristicNotification - running extra code for heart rate");
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                    UUID.fromString(GattAttributes.CLIENT_CHARACTERISTIC_CONFIG));
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            mBluetoothGatt.writeDescriptor(descriptor);
        }
    }

    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null) return null;

        return mBluetoothGatt.getServices();
    }


}









