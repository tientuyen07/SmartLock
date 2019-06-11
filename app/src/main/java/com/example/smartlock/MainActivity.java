package com.example.smartlock;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothAdapter.LeScanCallback;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.Message;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;

import java.math.BigInteger;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class MainActivity extends Activity {
    public static final UUID CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    //    public static final UUID OAD_SERVICE_UUID = UUID.fromString("f000ffc0-0451-4000-b000-000000000000");
    public static final UUID bltServerUUID = UUID.fromString("0000fee7-0000-1000-8000-00805f9b34fb");
    //    public static final ParcelUuid findServerUUID = ParcelUuid.fromString("0000fee7-0000-1000-8000-00805f9b34fb");
    public static final UUID readDataUUID = UUID.fromString("000036f6-0000-1000-8000-00805f9b34fb");
    public static final UUID writeDataUUID = UUID.fromString("000036f5-0000-1000-8000-00805f9b34fb");
    Button buttonClose;
    Button buttonDianLiang; // Query Power
    Button buttonOpen;
    Button buttonSaoMiao; // Scanning
    volatile int caozuo = 0;
    byte[] gettoken;
    TextView info;
    static byte[] key = new byte[]{(byte) 32, (byte) 87, (byte) 47, (byte) 82,
            (byte) 54, (byte) 75, (byte) 63, (byte) 71,
            (byte) 48, (byte) 80, (byte) 65, (byte) 88,
            (byte) 17, (byte) 99, (byte) 45, (byte) 43};
    BluetoothAdapter mBluetoothAdapter;
    static BluetoothGatt mBluetoothGatt;
    private final BluetoothGattCallback mGattCallback;
    private LeScanCallback mLeScanCallback;
    private DataClass m_myData = new DataClass();
    Handler m_myHandler; // Xu ly da tien trinh
    volatile String m_nowMac;
    BluetoothGattCharacteristic readCharacteristic;
    byte[] token = new byte[4];
    static BluetoothGattCharacteristic writeCharacteristic;

    public class DataClass {
        public int count = 0;
        public BluetoothDevice device = null;
        public String address;
        public String name;
        public Integer rssi = Integer.valueOf(0);
        public String version = "";
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public MainActivity() {
        byte[] bArr = new byte[16];
        bArr[0] = (byte) 6;
        bArr[1] = (byte) 1;
        bArr[2] = (byte) 1;
        bArr[3] = (byte) 1;
        gettoken = bArr;
        m_nowMac = "";

        // Tạo đối tượng m_myHandler tự liên kết với thread hiện tại.
        /*
         * 1. Lấy 1 message token từ pool của handler (gọi obtainMessage).
         * 2. Có token, thread phụ ghi yêu cầu vào token, gửi cho handler (sendMessage),
         *    token được đặt vào pool để đợi handler xử lý.
         * 3. Có message trong pool, ----> xử lý message đó (handleMessage).
         * =======> handleMessage cần được viết lại để xử lý theo yêu cầu*/
        m_myHandler = new Handler(new C01001());

        mLeScanCallback = new C01012();
        mGattCallback = new C01023();
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Kiem tra device co ho tro BLE 4.0 hay khong?
        if (!getPackageManager().hasSystemFeature("android.hardware.bluetooth_le")) {
            Toast.makeText(this, "Not support BLE 4.0", Toast.LENGTH_SHORT).show();
            finish();
        }

        // Tao doi tuong BluetoothAdapter thong qua getSystemService
        // Neu khoi tao doi tuong (== null), -> Bluetooth Failure
        mBluetoothAdapter = ((BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth Failure", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Neu BLE duoc bat thi auto scan devices
        // Neu khong thi yeu cau bat bluetooth
        Log.i("TAG", "Start auto scan device!");
        if (mBluetoothAdapter.isEnabled()) {
            mBluetoothAdapter.startLeScan(mLeScanCallback); // mLeScanCallback = C01012()
        } else {
            // send request_code = 188  -----> request turn on Bluetooth
            startActivityForResult(new Intent("android.bluetooth.adapter.action.REQUEST_ENABLE"), 188);
        }
        info = (TextView) findViewById(R.id.textView);

        buttonSaoMiao = (Button) findViewById(R.id.button1); // Scanning
        buttonOpen = (Button) findViewById(R.id.button2); // Open Clock
        buttonDianLiang = (Button) findViewById(R.id.button3); // Query Power
        buttonClose = (Button) findViewById(R.id.button4); // Reset Motor

        buttonSaoMiao.setOnClickListener(new C01034());
        buttonOpen.setOnClickListener(new C01045());
        buttonDianLiang.setOnClickListener(new C01056());
        buttonClose.setOnClickListener(new C01067());
    }

    class C01001 implements Callback {
        C01001() {
        }

        // Doi tuong m_myHandler duoc tu dong gan voi main thread
        public boolean handleMessage(Message mes) {
            Log.i("TAG", "Show info device");
            Toast toast;
            switch (mes.what) {
                case 1:
                    DataClass access$0 = m_myData;
                    access$0.count++;
                    info.setText("Name：" + m_myData.device.getName()
                            + "\r\nSignal：" + String.valueOf(m_myData.rssi) + "dB"
                            + m_myData.version + "\r\nMAC：" + m_myData.address);
                    toast = Toast.makeText(MainActivity.this, "Unlock Success", Toast.LENGTH_SHORT);
                    toast.setGravity(17, 0, 0);
                    toast.show();
                    break;
                case 2:
                    toast = Toast.makeText(MainActivity.this, (String) mes.obj, Toast.LENGTH_SHORT);
                    toast.setGravity(17, 0, 0);
                    toast.show();
                    break;
                case 3:
                    info.setText("Name：" + m_myData.device.getName()
                            + "\r\nSignal：" + String.valueOf(m_myData.rssi) + "dB："
                            + m_myData.version + "\r\nMAC：" + m_myData.address);
                    break;
                case 4:
                    info.setText((String) mes.obj);
                    break;
            }
            return false;
        }
    }

    //codenew Support for C01012(); Lấy scanRecord ở dạng String
    public static String ByteArrayToString(byte[] ba) {
        StringBuilder hex = new StringBuilder(ba.length * 2);
        for (byte b : ba)
//            hex.append(b + " ");
            hex.append(String.format("%02X ", b));
        return hex.toString();
    }

    
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    class C01012 implements LeScanCallback {
        C01012() {
        }

        // onLeScan() chạy khi tìm thấy 1 thiết bị nào đó
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
            String record = ByteArrayToString(scanRecord);

            Log.i("TAG_DEBUG", record);

            if (scanRecord[5] == (byte) 1 && scanRecord[6] == (byte) 2) {
                String nowAddress = device.getAddress();
                if (nowAddress == m_myData.address) {
                    if (m_myData.rssi.intValue() != rssi) {
                        m_myData.rssi = Integer.valueOf(rssi);
                        // "sendEmptyMessage(what)" sends a Message containing only the 'what' value.
                        m_myHandler.sendEmptyMessage(3);
                    }
                } else if (rssi > m_myData.rssi.intValue() || m_myData.device == null) {
                    m_myData.device = device;
                    m_myData.name = device.getName();
                    m_myData.address = nowAddress;
                    m_myData.rssi = Integer.valueOf(rssi);
                    m_myData.count = 0;
                    m_myHandler.sendEmptyMessage(3);
                    Log.i("LeScan", device.toString());
                }
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    class C01023 extends BluetoothGattCallback {
        C01023() {
        }

        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == 2) {
                gatt.discoverServices();
            } else if (newState == 0) {
                mBluetoothGatt.disconnect();
                mBluetoothGatt.close();
                mBluetoothGatt = null;
            }
        }

        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            Log.w("TAG", "onServicesDiscovered");
            if (status == 0) {
                BluetoothGattService service = gatt.getService(bltServerUUID);
                readCharacteristic = service.getCharacteristic(readDataUUID);
                writeCharacteristic = service.getCharacteristic(writeDataUUID);
                gatt.setCharacteristicNotification(readCharacteristic, true);
                BluetoothGattDescriptor descriptor = readCharacteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG);
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                gatt.writeDescriptor(descriptor);
            }
        }

        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            SendData(gettoken);
        }

        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            byte[] x = new byte[16];
            System.arraycopy(characteristic.getValue(), 0, x, 0, 16);
            byte[] mingwen = Decrypt(x, key);
            if (mingwen != null && mingwen.length == 16) {
                if (mingwen[0] == (byte) 6 && mingwen[1] == (byte) 2) {
                    token[0] = mingwen[3];
                    token[1] = mingwen[4];
                    token[2] = mingwen[5];
                    token[3] = mingwen[6];
                    if (caozuo == 0) {
                        byte[] openLock = new byte[16];
                        openLock[0] = (byte) 5;
                        openLock[1] = (byte) 1;
                        openLock[2] = (byte) 6;
                        openLock[3] = (byte) 48;
                        openLock[4] = (byte) 48;
                        openLock[5] = (byte) 48;
                        openLock[6] = (byte) 48;
                        openLock[7] = (byte) 48;
                        openLock[8] = (byte) 48;
                        openLock[9] = token[0];
                        openLock[10] = token[1];
                        openLock[11] = token[2];
                        openLock[12] = token[3];
                        SendData(openLock);
                    } else if (caozuo == 1) {
                        byte[] closeLock = new byte[16];
                        closeLock[0] = (byte) 5;
                        closeLock[1] = (byte) 12;
                        closeLock[2] = (byte) 1;
                        closeLock[3] = (byte) 1;
                        closeLock[4] = token[0];
                        closeLock[5] = token[1];
                        closeLock[6] = token[2];
                        closeLock[7] = token[3];
                        SendData(closeLock);
                    } else if (caozuo == 2) {
                        byte[] dianLiang = new byte[16];
                        dianLiang[0] = (byte) 2;
                        dianLiang[1] = (byte) 1;
                        dianLiang[2] = (byte) 1;
                        dianLiang[3] = (byte) 1;
                        dianLiang[4] = token[0];
                        dianLiang[5] = token[1];
                        dianLiang[6] = token[2];
                        dianLiang[7] = token[3];
                        SendData(dianLiang);
                    }
                } else if (mingwen[0] == (byte) 5 && mingwen[1] == (byte) 2) {
                    if (mingwen[3] == (byte) 0) {
                        m_myHandler.sendMessage(m_myHandler.obtainMessage(1, 1, 1, gatt.getDevice().getAddress()));
                        return;
                    }
                    m_myHandler.sendMessage(m_myHandler.obtainMessage(2, 1, 1, "Unclock Failed"));
                } else if (mingwen[0] == (byte) 2 && mingwen[1] == (byte) 2) {
                    if (mingwen[3] == (byte) -1) {
                        m_myHandler.sendMessage(m_myHandler.obtainMessage(2, 1, 1, "Detecting Failed Power"));
                        return;
                    }
                    m_myHandler.sendMessage(m_myHandler.obtainMessage(2, 1, 1, "The amount of attention" + String.valueOf(mingwen[3])));
                } else if (mingwen[0] == (byte) 5 && mingwen[1] == (byte) 8) {
                    if (mingwen[3] == (byte) 0) {
                        m_myHandler.sendMessage(m_myHandler.obtainMessage(2, 1, 1, "Locked success"));
                        return;
                    }
                    m_myHandler.sendMessage(m_myHandler.obtainMessage(2, 1, 1, "Lock failure"));
                } else if (mingwen[0] != (byte) 5 || mingwen[1] != (byte) 13) {
                } else {
                    if (mingwen[3] == (byte) 0) {
                        m_myHandler.sendMessage(m_myHandler.obtainMessage(2, 1, 1, "1 Successful"));
                        return;
                    }
                    m_myHandler.sendMessage(m_myHandler.obtainMessage(2, 1, 1, "Reset Failure"));
                }
            }
        }
    }


    // Ham scan device
    class C01034 implements OnClickListener {
        C01034() {
        }

        @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
        public void onClick(View arg0) {
            // Neu dang ket noi thi huy ket noi va dong ket noi
            if (mBluetoothGatt != null) {
                mBluetoothGatt.disconnect();
                mBluetoothGatt.close();
                mBluetoothGatt = null;
            }

            m_myData.device = null;
            m_myData.address = "";
            m_myData.name = "";
            m_myData.count = 0;
            m_myData.version = "";
            info.setText(""); // textView trong main_activity
            mBluetoothAdapter.startLeScan(mLeScanCallback);
        }
    }

    // Open Lock
    class C01045 implements OnClickListener {
        C01045() {
        }

        @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
        public void onClick(View arg0) {
            if (m_myData.device == null) {
                Toast toast = Toast.makeText(MainActivity.this, "chua co thiet bi, hay quet", Toast.LENGTH_SHORT);
                toast.setGravity(17, 0, 0);
                toast.show();
                return;
            }
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
            caozuo = 0;
            if (mBluetoothGatt == null || writeCharacteristic == null) {
                mBluetoothGatt = m_myData.device.connectGatt(MainActivity.this, false, mGattCallback);
            } else if (mBluetoothGatt.getDevice().getAddress().equals(m_myData.address)) {
                byte[] openLock = new byte[16];
                openLock[0] = (byte) 5;
                openLock[1] = (byte) 1;
                openLock[2] = (byte) 6;
                openLock[3] = (byte) 48;
                openLock[4] = (byte) 48;
                openLock[5] = (byte) 48;
                openLock[6] = (byte) 48;
                openLock[7] = (byte) 48;
                openLock[8] = (byte) 48;
                openLock[9] = token[0];
                openLock[10] = token[1];
                openLock[11] = token[2];
                openLock[12] = token[3];
                SendData(openLock);
            } else {
                mBluetoothGatt.disconnect();
                mBluetoothGatt.close();
                mBluetoothGatt = null;
                mBluetoothGatt = m_myData.device.connectGatt(MainActivity.this, false, mGattCallback);
            }
        }
    }

    // Query Power
    class C01056 implements OnClickListener {
        C01056() {
        }

        @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
        public void onClick(View arg0) {
            if (m_myData.device == null) {
                Toast toast = Toast.makeText(MainActivity.this, "没有设备，please scan first", Toast.LENGTH_SHORT);
                toast.setGravity(17, 0, 0);
                toast.show();
                return;
            }
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
            caozuo = 2;
            if (mBluetoothGatt == null || writeCharacteristic == null) {
                mBluetoothGatt = m_myData.device.connectGatt(MainActivity.this, false, mGattCallback);
            } else if (mBluetoothGatt.getDevice().getAddress().equals(m_myData.address)) {
                byte[] dianLiang = new byte[16];
                dianLiang[0] = (byte) 2;
                dianLiang[1] = (byte) 1;
                dianLiang[2] = (byte) 1;
                dianLiang[3] = (byte) 1;
                dianLiang[4] = token[0];
                dianLiang[5] = token[1];
                dianLiang[6] = token[2];
                dianLiang[7] = token[3];
                SendData(dianLiang);
            } else {
                mBluetoothGatt.disconnect();
                mBluetoothGatt.close();
                mBluetoothGatt = null;
                mBluetoothGatt = m_myData.device.connectGatt(MainActivity.this, false, mGattCallback);
            }
        }
    }

    // Close or reset
    class C01067 implements OnClickListener {
        C01067() {
        }

        @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
        public void onClick(View arg0) {
            if (m_myData.device == null) {
                Toast toast = Toast.makeText(MainActivity.this, "Khong dat am khi quet lan dau", Toast.LENGTH_SHORT);
                toast.setGravity(17, 0, 0);
                toast.show();
                return;
            }
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
            caozuo = 1;
            if (mBluetoothGatt == null || writeCharacteristic == null) {
                mBluetoothGatt = m_myData.device.connectGatt(MainActivity.this, false, mGattCallback);
            } else if (mBluetoothGatt.getDevice().getAddress().equals(m_myData.address)) {
                byte[] closeLock = new byte[16];
                closeLock[0] = (byte) 5;
                closeLock[1] = (byte) 12;
                closeLock[2] = (byte) 1;
                closeLock[3] = (byte) 1;
                closeLock[4] = token[0];
                closeLock[5] = token[1];
                closeLock[6] = token[2];
                closeLock[7] = token[3];
                SendData(closeLock);
            } else {
                mBluetoothGatt.disconnect();
                mBluetoothGatt.close();
                mBluetoothGatt = null;
                mBluetoothGatt = m_myData.device.connectGatt(MainActivity.this, false, mGattCallback);
            }
        }
    }


    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public static void SendData(byte[] data) {
        byte[] miwen = Encrypt(data, key);
        if (miwen != null) {
            writeCharacteristic.setValue(miwen);
            mBluetoothGatt.writeCharacteristic(writeCharacteristic);
        }
    }

    public static byte[] Encrypt(byte[] sSrc, byte[] sKey) {
        try {
            SecretKeySpec skeySpec = new SecretKeySpec(sKey, "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
            cipher.init(1, skeySpec);
            return cipher.doFinal(sSrc);
        } catch (Exception e) {
            return null;
        }
    }

    public byte[] Decrypt(byte[] sSrc, byte[] sKey) {
        try {
            SecretKeySpec skeySpec = new SecretKeySpec(sKey, "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
            cipher.init(2, skeySpec);
            return cipher.doFinal(sSrc);
        } catch (Exception e) {
            return null;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == -1) {
            switch (requestCode) {
                case 188:
                    if (mBluetoothGatt != null) {
                        mBluetoothGatt.disconnect();
                        mBluetoothGatt.close();
                        mBluetoothGatt = null;
                    }
                    m_myData.device = null;
                    m_myData.address = "";
                    m_myData.count = 0;
                    m_myData.name = "";
                    m_myData.version = "";
                    info.setText("");
                    mBluetoothAdapter.startLeScan(mLeScanCallback);
                    return;
                default:
                    return;
            }
        } else if (requestCode == 188) {
            Toast.makeText(this, "Bluetooth is not enable!", Toast.LENGTH_SHORT).show();
            finish();
        }
    }
}
