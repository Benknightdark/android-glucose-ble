package fuse.cardinalhealth.com.bayerbletest;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.util.Arrays;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private BluetoothAdapter bluetoothAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();


        Button scanButton = (Button) findViewById(R.id.scan_button);

        scanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startScan();
            }
        });


    }

    private void startScan() {
        final BluetoothLeScanner scanner = bluetoothAdapter.getBluetoothLeScanner();
        final TextView t = (TextView) findViewById(R.id.status);

        t.setText("SCANNING");

        scanner.startScan(new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                if (result.getDevice().getName() != null && result.getDevice().getName().contains("Contour")) {
                    connectToDevice(result.getDevice());
                    scanner.stopScan(this);
                }
            }
        });
    }

    private void connectToDevice(BluetoothDevice device) {

        if (device.getBondState()== BluetoothDevice.BOND_NONE) {
            if (!device.createBond()) {
                throw new RuntimeException("Bonding Failed");
            }
        }

        device.connectGatt(this, true, new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(final BluetoothGatt gatt, int status, int newState) {
                final int s = status;
                final int n = newState;

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        final TextView t = (TextView) findViewById(R.id.status);
                        t.setText("STATUS =" + s + ", STATE =" + n);
                    }
                });

                if (newState == 2) {
                    gatt.discoverServices();
                }
            }

            @Override
            public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                Log.i("READ CHAR", characteristic.toString());
                final String value = characteristic.getStringValue(0);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        final TextView t = (TextView) findViewById(R.id.status);
                        t.setText("READ CHAR ="  + value);
                    }
                });
            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                Log.i("CHANGE CHAR", characteristic.toString());

                String val = "CONTEXT";
                if (characteristic.getUuid().equals(GLUCOSE_MEASUREMENT_UUID))
                    val = "GLUCOSE";


                final byte[] data = characteristic.getValue();
                final String value = val;

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        final TextView t = (TextView) findViewById(R.id.status);
                        t.setText("CHANGE CHAR ="  + value);
                        Log.i("VALUE", Arrays.toString(data));
                    }
                });
            }

            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                Log.i("WRITE CHAR ["+ status +"]", characteristic.toString());
            }

            @Override
            public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                if (descriptor.getCharacteristic().getUuid().equals(GLUCOSE_MEASUREMENT_UUID))
                    setGlucoseContextNotifications(gatt);
                if (descriptor.getCharacteristic().getUuid().equals(GLUCOSE_MEASUREMENT_CONTEXT_UUID))
                    setRACPNotifications(gatt);
                if (descriptor.getCharacteristic().getUuid().equals(GLUCOSE_RECORD_ACCESS_CONTROL_POINT_UUID))
                    startReading(gatt);
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                for (BluetoothGattService service : gatt.getServices()) {
                    if ((service == null) || (service.getUuid() == null)) {
                        continue;
                    }
                    if (service.getUuid().equals(GLUCOSE_SERVICE_ID_UUID)) {
                        setGlucoseNotifications(gatt);
                    }
                }
            }
        });


    }

    private void setGlucoseNotifications(BluetoothGatt mConnGatt) {
        BluetoothGattCharacteristic charGM =
                mConnGatt.getService(GLUCOSE_SERVICE_ID_UUID)
                        .getCharacteristic(GLUCOSE_MEASUREMENT_UUID);
        mConnGatt.setCharacteristicNotification(charGM, true);
        charGM.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        BluetoothGattDescriptor descGM = charGM.getDescriptor(NOTIFICATION_DESCRIPTOR);
        descGM.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);

        if (!mConnGatt.writeDescriptor(descGM)) {
            Log.d("ERROR", "descGM failed to write");
        }
    }

    private void setGlucoseContextNotifications(BluetoothGatt mConnGatt) {
        BluetoothGattCharacteristic charGM =
                mConnGatt.getService(GLUCOSE_SERVICE_ID_UUID)
                        .getCharacteristic(GLUCOSE_MEASUREMENT_CONTEXT_UUID);
        mConnGatt.setCharacteristicNotification(charGM, true);
        charGM.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        BluetoothGattDescriptor descGM = charGM.getDescriptor(NOTIFICATION_DESCRIPTOR);
        descGM.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);

        if (!mConnGatt.writeDescriptor(descGM)) {
            Log.d("ERROR", "descGM failed to write");
        }
    }

    private void setRACPNotifications(BluetoothGatt mConnGatt) {
        BluetoothGattCharacteristic charRACP =
                mConnGatt.getService(GLUCOSE_SERVICE_ID_UUID)
                        .getCharacteristic(GLUCOSE_RECORD_ACCESS_CONTROL_POINT_UUID);
        charRACP.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        mConnGatt.setCharacteristicNotification(charRACP, true);
        BluetoothGattDescriptor descRACP = charRACP.getDescriptor(NOTIFICATION_DESCRIPTOR);
        descRACP.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
        if (!mConnGatt.writeDescriptor(descRACP)) {
            Log.d("ERROR","descRACP failed to write");
        }
    }


    private void startReading(BluetoothGatt mConnGatt) {
        BluetoothGattCharacteristic charRACP =
                mConnGatt.getService(GLUCOSE_SERVICE_ID_UUID)
                        .getCharacteristic(GLUCOSE_RECORD_ACCESS_CONTROL_POINT_UUID);
        charRACP.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);

        byte[] data = new byte[2];
        data[0] = 0x01; // Report Stored records
        data[1] = 0x01; // All records
        charRACP.setValue(data);
        if (!mConnGatt.writeCharacteristic(charRACP)) {
            Log.d("ERROR", "write characteristic charRACP failed");
        }
    }

    // region Descriptors
    public  static final UUID NOTIFICATION_DESCRIPTOR = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    // endregion

    //Glucose Meter Service
    public static final UUID GLUCOSE_SERVICE_ID_UUID = UUID.fromString("00001808-0000-1000-8000-00805f9b34fb");
    public static final UUID GLUCOSE_DEVICE_INFORMATION_UUID = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb");

    //Glucose Meter - Device Information Mandatory Characteristics
    public static final UUID GLUCOSE_DEVICE_MANUFACTURER_NAME_UUID = UUID.fromString("00002a29-0000-1000-8000-00805f9b34fb");
    public static final UUID GLUCOSE_DEVICE_MODEL_NUMBER_UUID = UUID.fromString("00002a24-0000-1000-8000-00805f9b34fb");
    public static final UUID GLUCOSE_DEVICE_SYSTEM_ID_UUID = UUID.fromString("00002a23-0000-1000-8000-00805f9b34fb");

    //Glucose Meter Mandatory Characteristics
    public static final UUID GLUCOSE_MEASUREMENT_UUID = UUID.fromString("00002a18-0000-1000-8000-00805f9b34fb");
    public static final UUID GLUCOSE_FEATURE_UUID = UUID.fromString("00002a51-0000-1000-8000-00805f9b34fb");
    public static final UUID GLUCOSE_RECORD_ACCESS_CONTROL_POINT_UUID = UUID.fromString("00002a52-0000-1000-8000-00805f9b34fb");

    //Glucose Meter Optional Characteristics
    public static final UUID GLUCOSE_MEASUREMENT_CONTEXT_UUID = UUID.fromString("00002a34-0000-1000-8000-00805f9b34fb");
    //endregion
}
