package com.qing.mybluetoothapplication;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import java.util.Set;
import java.util.UUID;

public class BtService {

    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final int CONNECT_OUT_TIME = 10000;
    private static final String TAG = "<BtService>";

    //消息信号
    public static final int CONNECT_SUCCESS = 0x01;
    public static final int CONNECT_FAILURE = 0x02;
    public static final int DISCONNECT_SUCCESS = 0x03;
    public static final int SEND_SUCCESS = 0x04;
    public static final int SEND_FAILURE = 0x05;
    public static final int RECEIVE_SUCCESS = 0x06;
    public static final int RECEIVE_FAILURE = 0x07;
    public static final int START_DISCOVERY = 0x08;
    public static final int STOP_DISCOVERY = 0x09;
    public static final int DISCOVERY_DEVICE = 0x0A;
    public static final int DEVICE_BOND_NONE = 0x0B;
    public static final int DEVICE_BONDING = 0x0C;
    public static final int DEVICE_BONDED = 0x0D;

    //蓝牙
    private BluetoothAdapter btAdapter;
    private BtBroadcastReceiver btBroadcastReceiver;
    //连接设备的UUID
    public static final String MY_BLUETOOTH_UUID = "00001101-0000-1000-8000-00805F9B34FB";  //蓝牙通讯
    //当前要连接的设备
    private BluetoothDevice curBluetoothDevice;
    //发起连接的线程
    private ConnectThread connectThread;
    //管理连接的线程
    private ConnectedThread connectedThread;
    //当前设备连接状态
    private boolean curConnState = false;
    //当前设备与系统配对状态
    private boolean curBondState = false;
    private boolean isFirstTarget = true;
    //handler
    private Handler handler;
    private Context context;

    public BtService(Context context, Handler handler) {
        this.handler = handler;
        this.context = context;
        initBtAdapter();
        initBtBroadcast();
    }

    private void initBtAdapter() {
        btAdapter = BluetoothAdapter.getDefaultAdapter();
        if (btAdapter == null) {
            Toast.makeText(context, "当前手机设备不支持蓝牙", Toast.LENGTH_SHORT).show();
        } else {
            //手机设备支持蓝牙，判断蓝牙是否已开启
            if (btAdapter.isEnabled()) {
                Toast.makeText(context, "手机蓝牙已开启", Toast.LENGTH_SHORT).show();
            } else {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                context.startActivity(enableBtIntent);
            }
        }
    }

    //////////////////////////////////  搜索设备  ///////////////////////////////////////////////////
    public void searchBtDevice() {
        if (btAdapter.isDiscovering()) { //当前正在搜索设备...
            return;
        }
        //开始搜索
        btAdapter.startDiscovery();
    }

    //////////////////////////////////  配对/接触配对设备  ////////////////////////////////////////////

    /**
     * 执行绑定 反射
     *
     * @param bluetoothDevice 蓝牙设备
     * @return true 执行绑定 false 未执行绑定
     */
    public boolean boundDevice(BluetoothDevice bluetoothDevice) {
        if (bluetoothDevice == null) {
            Log.e(TAG, "boundDevice-->bluetoothDevice == null");
            return false;
        }

        try {
            return ClsUtils.createBond(BluetoothDevice.class, bluetoothDevice);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return true;
    }

    /**
     * 执行解绑  反射
     *
     * @param bluetoothDevice 蓝牙设备
     * @return true 执行解绑  false未执行解绑
     */
    public boolean disBoundDevice(BluetoothDevice bluetoothDevice) {
        if (bluetoothDevice == null) {
            Log.e(TAG, "disBoundDevice-->bluetoothDevice == null");
            return false;
        }
        try {
            return ClsUtils.removeBond(BluetoothDevice.class, bluetoothDevice);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return true;
    }

    //////////////////////////////////   连接设备   /////////////////////////////////////////////////

    public BluetoothDevice getPairedDevice(String addr) {
        Set<BluetoothDevice> pairedDevices = btAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                if (device.getAddress().equals(addr)) {
                    return device;
                }
            }
        }
        return null;
    }

    /**
     * 开始连接设备
     *
     * @param bluetoothDevice 蓝牙设备
     */
    public void startConnectDevice(final BluetoothDevice bluetoothDevice) {
        if (bluetoothDevice == null) {
            Log.e(TAG, "startConnectDevice-->bluetoothDevice == null");
            return;
        }
        if (btAdapter == null) {
            Log.e(TAG, "startConnectDevice-->bluetooth3Adapter == null");
            return;
        }
        curBluetoothDevice = bluetoothDevice;
        //发起连接
        connectThread = new ConnectThread(btAdapter, curBluetoothDevice, MY_UUID);
        connectThread.setOnBluetoothConnectListener(new ConnectThread.OnBluetoothConnectListener() {
            @Override
            public void onStartConn(BluetoothDevice device) {
                Log.d(TAG, "startConnectDevice-->开始连接..." + device.getName() + "-->" + device.getAddress());
            }


            @Override
            public void onConnSuccess(BluetoothDevice device, BluetoothSocket bluetoothSocket) {
                //移除连接超时
                handler.removeCallbacks(connectOuttimeRunnable);
                Log.d(TAG, "startConnectDevice-->移除连接超时");
                Log.w(TAG, "startConnectDevice-->连接成功");

                Message message = new Message();
                message.what = CONNECT_SUCCESS;
                message.obj = device;
                handler.sendMessage(message);

                //标记当前连接状态为true
                curConnState = true;
                //管理连接，收发数据
                managerConnectSendReceiveData(bluetoothSocket, curBluetoothDevice);
            }

            @Override
            public void onConnFailure(BluetoothDevice device, String errorMsg) {
                Log.e(TAG, "startConnectDevice-->" + errorMsg);

                Message message = new Message();
                message.what = CONNECT_FAILURE;
                message.obj = device;
                handler.sendMessage(message);

                //标记当前连接状态为false
                curConnState = false;

                //断开管理连接
                clearConnectedThread();
            }
        });
        connectThread.start();
        //设置连接超时时间
        handler.postDelayed(connectOuttimeRunnable, CONNECT_OUT_TIME);

    }

    ////////////////////////////////////// 断开连接  //////////////////////////////////////////////

    /**
     * 断开已有的连接
     */
    public void clearConnectedThread() {
        Log.d(TAG, "clearConnectedThread-->即将断开");

        //connectedThread断开已有连接
        if (connectedThread == null) {
            Log.e(TAG, "clearConnectedThread-->connectedThread == null");
            return;
        }
        connectedThread.terminalClose(connectThread);

        //等待线程运行完后再断开
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                connectedThread.cancel();  //释放连接

                connectedThread = null;
            }
        }, 10);

        Log.w(TAG, "clearConnectedThread-->成功断开连接");
        Message message = new Message();
        message.what = DISCONNECT_SUCCESS;
        message.obj = curBluetoothDevice;
        handler.sendMessage(message);

    }

    //连接超时
    private Runnable connectOuttimeRunnable = new Runnable() {
        @Override
        public void run() {
            Log.e(TAG, "startConnectDevice-->连接超时");

            Message message = new Message();
            message.what = CONNECT_FAILURE;
            message.obj = curBluetoothDevice;
            handler.sendMessage(message);

            //标记当前连接状态为false
            curConnState = false;
            //断开管理连接
            clearConnectedThread();
        }
    };

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////  管理已有连接、收发数据  ///////////////////////////////////////

    /**
     * 管理已建立的连接，收发数据
     *
     * @param bluetoothSocket 已建立的连接
     */
    public void managerConnectSendReceiveData(BluetoothSocket bluetoothSocket, BluetoothDevice device) {
        //管理已有连接
        connectedThread = new ConnectedThread(bluetoothSocket, device);
        connectedThread.start();
        connectedThread.setOnSendReceiveDataListener(new ConnectedThread.OnSendReceiveDataListener() {
            @Override
            public void onSendDataSuccess(byte[] data) {
                Log.w(TAG, "发送数据成功,长度" + data.length + "->" + bytes2HexString(data, data.length));
                Message message = new Message();
                message.what = SEND_SUCCESS;
                message.obj = "发送数据成功,长度" + data.length + "->" + bytes2HexString(data, data.length);
                handler.sendMessage(message);
            }

            @Override
            public void onSendDataError(byte[] data, String errorMsg) {
                Log.e(TAG, "发送数据出错,长度" + data.length + "->" + bytes2HexString(data, data.length));
                Message message = new Message();
                message.what = SEND_FAILURE;
                message.obj = "发送数据出错,长度" + data.length + "->" + bytes2HexString(data, data.length);
                handler.sendMessage(message);
            }

            @Override
            public void onReceiveDataSuccess(String[] data) {
                Message message = new Message();
                message.what = RECEIVE_SUCCESS;
                message.obj = data;
                handler.sendMessage(message);
            }

            @Override
            public void onReceiveDataError(String errorMsg) {
                Log.e(TAG, "接收数据出错：" + errorMsg);
                Message message = new Message();
                message.what = RECEIVE_FAILURE;
                message.obj = "接收数据出错：" + errorMsg;
                handler.sendMessage(message);
            }
        });
    }

    /////////////////////////////////   发送数据  /////////////////////////////////////////////////

    /**
     * 发送数据
     *
     * @param data  要发送的数据 字符串
     * @param isHex 是否是16进制字符串
     * @return true 发送成功  false 发送失败
     */
    public boolean sendData(String data, boolean isHex) {
        if (connectedThread == null) {
            Log.e(TAG, "sendData:string -->connectedThread == null");
            return false;
        }
        if (data == null || data.length() == 0) {
            Log.e(TAG, "sendData:string-->要发送的数据为空");
            return false;
        }

        if (isHex) {  //是16进制字符串
            data.replace(" ", "");  //取消空格
            //检查16进制数据是否合法
            if (data.length() % 2 != 0) {
                //不合法，最后一位自动填充0
                String lasts = "0" + data.charAt(data.length() - 1);
                data = data.substring(0, data.length() - 2) + lasts;
            }
            Log.d(TAG, "sendData:string -->准备写入：" + data);  //加空格显示
            return connectedThread.write(hexString2Bytes(data));
        }

        //普通字符串
        Log.d(TAG, "sendData:string -->准备写入：" + data);
        return connectedThread.write(data.getBytes());
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////   数据类型转换  //////////////////////////////////////////////////

    /**
     * 字节数组-->16进制字符串
     *
     * @param b      字节数组
     * @param length 字节数组长度
     * @return 16进制字符串 有空格类似“0A D5 CD 8F BD E5 F8”
     */
    public static String bytes2HexString(byte[] b, int length) {
        StringBuffer result = new StringBuffer();
        String hex;
        for (int i = 0; i < length; i++) {
            hex = Integer.toHexString(b[i] & 0xFF);
            if (hex.length() == 1) {
                hex = '0' + hex;
            }
            result.append(hex.toUpperCase()).append(" ");
        }
        return result.toString();
    }

    /**
     * hexString2Bytes
     * 16进制字符串-->字节数组
     *
     * @param src 16进制字符串
     * @return 字节数组
     */
    public static byte[] hexString2Bytes(String src) {
        int l = src.length() / 2;
        byte[] ret = new byte[l];
        for (int i = 0; i < l; i++) {
            ret[i] = (byte) Integer
                    .valueOf(src.substring(i * 2, i * 2 + 2), 16).byteValue();
        }
        return ret;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////   蓝牙广播  /////////////////////////////////////////////////////

    /**
     * 初始化蓝牙广播
     */
    private void initBtBroadcast() {
        //注册广播接收
        btBroadcastReceiver = new BtBroadcastReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED); //开始扫描
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);//扫描结束
        intentFilter.addAction(BluetoothDevice.ACTION_FOUND);//搜索到设备
        intentFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED); //配对状态监听
        context.registerReceiver(btBroadcastReceiver, intentFilter);
    }

    public void unregisterBroadcastReceiver() {
        context.unregisterReceiver(btBroadcastReceiver);
    }

    /**
     * 蓝牙广播接收器
     */
    private class BtBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (TextUtils.equals(action, BluetoothAdapter.ACTION_DISCOVERY_STARTED)) { //开启搜索
                Log.d(TAG, "开启搜索...");
                Message message = new Message();
                message.what = START_DISCOVERY;
                handler.sendMessage(message);

            } else if (TextUtils.equals(action, BluetoothAdapter.ACTION_DISCOVERY_FINISHED)) {//完成搜素
                Log.d(TAG, "停止搜索...");
                Message message = new Message();
                message.what = STOP_DISCOVERY;
                handler.sendMessage(message);

            } else if (TextUtils.equals(action, BluetoothDevice.ACTION_FOUND)) {  //3.0搜索到设备
                //蓝牙设备
                BluetoothDevice bluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                //信号强度
                int rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);

                Log.w(TAG, "扫描到设备：" + bluetoothDevice.getName() + "-->" + bluetoothDevice.getAddress());
                if (bluetoothDevice.getName() != null) {
                    Message message = new Message();
                    message.what = DISCOVERY_DEVICE;
                    message.obj = bluetoothDevice;
                    handler.sendMessage(message);
                }

            } else if (TextUtils.equals(action, BluetoothDevice.ACTION_BOND_STATE_CHANGED)) {
                BluetoothDevice bluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                int bondSate = bluetoothDevice.getBondState();
                switch (bondSate) {
                    case BluetoothDevice.BOND_NONE:
                        Log.d(TAG, "已解除配对");
                        Message message1 = new Message();
                        message1.what = DEVICE_BOND_NONE;
                        message1.obj = bluetoothDevice;
                        handler.sendMessage(message1);
                        break;

                    case BluetoothDevice.BOND_BONDING:
                        Log.d(TAG, "正在配对...");
                        Message message2 = new Message();
                        message2.what = DEVICE_BONDING;
                        message2.obj = bluetoothDevice;
                        handler.sendMessage(message2);
                        break;

                    case BluetoothDevice.BOND_BONDED:
                        Log.d(TAG, "已配对");
                        Message message3 = new Message();
                        message3.what = DEVICE_BONDED;
                        message3.obj = bluetoothDevice;
                        handler.sendMessage(message3);
                        break;
                }
            }
        }
    }
}
