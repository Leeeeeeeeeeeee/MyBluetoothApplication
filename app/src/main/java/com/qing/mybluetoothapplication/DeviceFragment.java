package com.qing.mybluetoothapplication;

import static com.qing.mybluetoothapplication.BtService.CONNECT_FAILURE;
import static com.qing.mybluetoothapplication.BtService.CONNECT_SUCCESS;
import static com.qing.mybluetoothapplication.BtService.DEVICE_BONDED;
import static com.qing.mybluetoothapplication.BtService.DEVICE_BONDING;
import static com.qing.mybluetoothapplication.BtService.DEVICE_BOND_NONE;
import static com.qing.mybluetoothapplication.BtService.DISCONNECT_SUCCESS;
import static com.qing.mybluetoothapplication.BtService.DISCOVERY_DEVICE;
import static com.qing.mybluetoothapplication.BtService.RECEIVE_FAILURE;
import static com.qing.mybluetoothapplication.BtService.RECEIVE_SUCCESS;
import static com.qing.mybluetoothapplication.BtService.SEND_FAILURE;
import static com.qing.mybluetoothapplication.BtService.SEND_SUCCESS;
import static com.qing.mybluetoothapplication.BtService.START_DISCOVERY;
import static com.qing.mybluetoothapplication.BtService.STOP_DISCOVERY;

import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.ListPopupWindow;
import androidx.fragment.app.Fragment;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

public class DeviceFragment extends Fragment implements View.OnClickListener {

    private static final String TAG = "<DeviceFragment";
    private static final int CAPACITY = 2048;
    private int tabSeq;
    private String title;
    private StringBuffer btMsgReceive = new StringBuffer(CAPACITY);
    private StringBuffer btMsgSend = new StringBuffer(CAPACITY);
    private boolean curConnectState = false;
    private BtService btService;
    //列表Adapter
    private ArrayAdapter<String> devicesArrayAdapter;
    //DeviceList
    private ArrayList<BluetoothDevice> bluetoothDeviceArrayList = new ArrayList<>();
    private Button mBtnBt;
    private TextView mTvMsgReceive, mTvMsgSend;
    private ListPopupWindow lpwBtList;
    private EditText mEtMsgSend;
    private Handler handler;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initData();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_device, container, false);
    }

    @Override
    public void onStart() {
        super.onStart();
        initView();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateView();
    }

    private void initData() {

        Bundle bundle = getArguments();
        if (bundle != null) {
            tabSeq = bundle.getInt("tabSeq", -1);
            title = String.valueOf("设备" + (tabSeq + 1));
        }
        handler = new MyHandler(this);
    }

    private void initView() {
        //蓝牙按钮
        mBtnBt = getView().findViewById(R.id.btn_scan);
        mBtnBt.setOnClickListener(this);
        //设备数组适配器
        btService = new BtService(getActivity(), handler);
        //设备数组适配器
        devicesArrayAdapter = new ArrayAdapter<String>(getActivity(), R.layout.item_device_name);
        //弹出列表
        lpwBtList = new ListPopupWindow(getActivity());
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
        //接受消息的视图
        mTvMsgReceive = getView().findViewById(R.id.tv_msg_receive);
        Button mBtnSend = getView().findViewById(R.id.btn_send);
        //发送消息
        mTvMsgSend = getView().findViewById(R.id.tv_msg_send);
        mBtnSend.setOnClickListener(this);
        mEtMsgSend = getView().findViewById(R.id.et_send);
    }

    private void updateView() {
        ((MainActivity) getActivity()).getSupportActionBar().setTitle(title);

    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_scan:
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
                break;
            case R.id.btn_send: {
                String msgSend = mEtMsgSend.getText().toString();
                if (btMsgSend.length() >= CAPACITY) {
                    btMsgSend.delete(0, 100);
                }
                btMsgSend.append(msgSend).append("\n");
                mTvMsgSend.setText(btMsgSend);
                btService.sendData(msgSend, false);
            }
            break;
        }
    }


    private class MyHandler extends Handler {


        final WeakReference<DeviceFragment> mWeakReference;

        public MyHandler(DeviceFragment fragment) {

            this.mWeakReference = new WeakReference<>(fragment);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {

            BluetoothDevice device;

            DeviceFragment fragment = mWeakReference.get();

            switch (msg.what) {
                case START_DISCOVERY:
                    Log.d(TAG, "开始搜索设备...");
                    fragment.mBtnBt.setText(getString(R.string.bt_scan_success));
                    break;

                case STOP_DISCOVERY:
                    Log.d(TAG, "停止搜索设备...");
                    if (devicesArrayAdapter.getCount() == 0) {
                        fragment.mBtnBt.setText(getString(R.string.bt_scan_none));
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
                    fragment.mBtnBt.setText(getString(R.string.bt_connect_fail));
                    curConnectState = false;
                    break;

                case CONNECT_SUCCESS:
                    device = (BluetoothDevice) msg.obj;
                    Log.d(TAG, "连接成功：" + device.getName());
                    fragment.mBtnBt.setText(device.getName());
                    curConnectState = true;
//                    SharedPreferences settings = getSharedPreferences(SETTINGS, MODE_PRIVATE);
//                    SharedPreferences.Editor editor = settings.edit();
//                    editor.putString(SETTINGS_DEVICE_ADDR, device.getAddress());
//                    editor.apply();
                    break;

                case DISCONNECT_SUCCESS:
                    Log.d(TAG, "断开连接成功");
                    fragment.mBtnBt.setText(getString(R.string.bt_disconnect));
                    break;

                case SEND_FAILURE:
                    Log.d(TAG, "发送失败");
                    Toast.makeText(getActivity(), "发送失败", Toast.LENGTH_SHORT).show();
                    break;

                case SEND_SUCCESS:
                    Log.d(TAG, "发送成功");
                    Toast.makeText(getActivity(), "发送成功", Toast.LENGTH_SHORT).show();
                    break;

                case RECEIVE_FAILURE:
                    Log.d(TAG, "接收失败");
                    Toast.makeText(getActivity(), "接收失败", Toast.LENGTH_SHORT).show();
                    break;

                case RECEIVE_SUCCESS: {
                    String[] str = (String[]) msg.obj;
                    String data = str[2];
                    if (btMsgReceive.length() >= CAPACITY) {
                        btMsgReceive.delete(0, 100);
                    }
                    btMsgReceive.append(data).append("\n");
                    fragment.mTvMsgReceive.setText(btMsgReceive);
                }
                break;

                case DEVICE_BOND_NONE:
                    device = (BluetoothDevice) msg.obj;
                    Log.d(TAG, "解除配对：" + device.getName());
                    fragment.mBtnBt.setText(getString(R.string.bt_pair_fail));
                    break;

                case DEVICE_BONDING:
                    device = (BluetoothDevice) msg.obj;
                    Log.d(TAG, "配对中：" + device.getName());
                    fragment.mBtnBt.setText(getString(R.string.bt_pairing));
                    break;

                case DEVICE_BONDED:
                    device = (BluetoothDevice) msg.obj;
                    Log.d(TAG, "配对成功：" + device.getName());
                    btService.startConnectDevice(device);
                    fragment.mBtnBt.setText(getString(R.string.bt_connecting));
                    break;
            }
        }
    }

}
