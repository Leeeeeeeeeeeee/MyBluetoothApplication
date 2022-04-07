package com.qing.mybluetoothapplication;

import static com.qing.mybluetoothapplication.BtService.*;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.ListPopupWindow;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

import javax.net.ssl.SNIHostName;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "<MainActivity>";
    private boolean curConnectState = false;
    private BtService btService;
    //列表Adapter
    private ArrayAdapter<String> devicesArrayAdapter;
    //DeviceList
    private ArrayList<BluetoothDevice> bluetoothDeviceArrayList = new ArrayList<>();
    private Button mBtnBt;
    private TextView mTvMsg;
    private ListPopupWindow lpwBtList;

    private StringBuffer btMsg = new StringBuffer();

    @RequiresApi(api = Build.VERSION_CODES.Q)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        checkPermission();

        //蓝牙按钮
        mBtnBt = findViewById(R.id.btn_scan);
        mBtnBt.setOnClickListener(this);
        //接受消息的视图
        mTvMsg = findViewById(R.id.tv_msg_receive);
        Handler handler = new MyHandler(this);
        //设备数组适配器
        btService = new BtService(this, handler);
        //设备数组适配器
        devicesArrayAdapter = new ArrayAdapter<String>(this, R.layout.item_device_name);
        //弹出列表
        lpwBtList = new ListPopupWindow(MainActivity.this);
        lpwBtList.setModal(true);
        lpwBtList.setAnchorView(mBtnBt);
        lpwBtList.setAdapter(devicesArrayAdapter);//用android内置布局，或设计自己的样式
        //设置项点击监听
        lpwBtList.setOnItemClickListener((adapterView, view, i, l) -> {
            //配对
            if (bluetoothDeviceArrayList.get(i).getBondState() == BluetoothDevice.BOND_NONE) {
                btService.boundDevice(bluetoothDeviceArrayList.get(i));
            }
            //连接
            else {
                btService.startConnectDevice(bluetoothDeviceArrayList.get(i));
            }
            lpwBtList.dismiss();
        });
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void checkPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            String[] strings = {Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE};
            ActivityCompat.requestPermissions(this, strings, 2);
        }
    }

    private class MyHandler extends Handler {


        final WeakReference<MainActivity> mWeakReference;

        public MyHandler(MainActivity activity) {

            this.mWeakReference = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {

            BluetoothDevice device;

            MainActivity activity = mWeakReference.get();

            switch (msg.what) {
                case START_DISCOVERY:
                    Log.d(TAG, "开始搜索设备...");
                    activity.mBtnBt.setText(getString(R.string.bt_scan_success));
                    break;

                case STOP_DISCOVERY:
                    Log.d(TAG, "停止搜索设备...");
                    if (devicesArrayAdapter.getCount() == 0) {
                        activity.mBtnBt.setText(getString(R.string.bt_scan_none));
                    }
                    break;

                case DISCOVERY_DEVICE:
                    device = (BluetoothDevice) msg.obj;
                    Log.d(TAG, "发现设备：" + device.getName());
                    bluetoothDeviceArrayList.add(device);
                    devicesArrayAdapter.add(device.getName());
                    devicesArrayAdapter.notifyDataSetChanged();
                    break;

                case CONNECT_FAILURE:
                    device = (BluetoothDevice) msg.obj;
                    Log.d(TAG, "连接失败：" + device.getName());
                    activity.mBtnBt.setText(getString(R.string.bt_connect_fail));
                    curConnectState = false;
                    break;

                case CONNECT_SUCCESS:
                    device = (BluetoothDevice) msg.obj;
                    Log.d(TAG, "连接成功：" + device.getName());
                    activity.mBtnBt.setText(device.getName());
                    curConnectState = true;
//                    SharedPreferences settings = getSharedPreferences(SETTINGS, MODE_PRIVATE);
//                    SharedPreferences.Editor editor = settings.edit();
//                    editor.putString(SETTINGS_DEVICE_ADDR, device.getAddress());
//                    editor.apply();
                    break;

                case DISCONNECT_SUCCESS:
                    Log.d(TAG, "断开连接成功");
                    activity.mBtnBt.setText(getString(R.string.bt_disconnect));
                    break;

                case SEND_FAILURE:
                    Log.d(TAG, "发送失败");
                    break;

                case SEND_SUCCESS:
                    Log.d(TAG, "发送成功");
                    break;

                case RECEIVE_FAILURE:
                    Log.d(TAG, "接收失败");
                    break;

                case RECEIVE_SUCCESS: {
                    String[] str = (String[]) msg.obj;
                    String data = str[2];
                    Log.e(TAG, "handleMessage: " + data);
                    btMsg.append(data+"\n");
                    activity.mTvMsg.setText(btMsg);
                }
                break;

                case DEVICE_BOND_NONE:
                    device = (BluetoothDevice) msg.obj;
                    Log.d(TAG, "解除配对：" + device.getName());
                    activity.mBtnBt.setText(getString(R.string.bt_pair_fail));
                    break;

                case DEVICE_BONDING:
                    device = (BluetoothDevice) msg.obj;
                    Log.d(TAG, "配对中：" + device.getName());
                    activity.mBtnBt.setText(getString(R.string.bt_pairing));
                    break;

                case DEVICE_BONDED:
                    device = (BluetoothDevice) msg.obj;
                    Log.d(TAG, "配对成功：" + device.getName());
                    btService.startConnectDevice(device);
                    activity.mBtnBt.setText(getString(R.string.bt_connecting));
                    break;
            }
        }
    }

    @Override
    public void onClick(View v) {
        if (curConnectState == false) {
            //先清除原来的列表
            bluetoothDeviceArrayList.clear();
            devicesArrayAdapter.clear();
            //开始扫描设备
            btService.searchBtDevice();
            //显示蓝牙列表
            lpwBtList.show();
        } else {
            btService.clearConnectedThread();
        }
    }
}