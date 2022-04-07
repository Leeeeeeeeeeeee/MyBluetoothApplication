package com.qing.mybluetoothapplication;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.Queue;

/**
 * 管理连接
 * 1、发送数据
 * 2、接收数据
 */
public class ConnectedThread extends Thread {
    private static final String TAG = "#ConnectedThread";
    private String DEVICE = "";
    private BluetoothDevice mmDevice;
    private BluetoothSocket mmSocket;
    private InputStream mmInStream;
    private OutputStream mmOutStream;
    //一次读取输入流的信息
    String readMessage = "";
    //分割的单个包数据的队列
    public Queue<String> sb3 = new LinkedList<>();
    //以多少个包为单位发往前端显示
    int senum = 1;
    //是否是主动断开
    private boolean isStop = false;
    //发起蓝牙连接的线程
    private ConnectThread connectThread;

    public void terminalClose(ConnectThread connectThread) {
        isStop = true;
        this.connectThread = connectThread;
    }

    public ConnectedThread(BluetoothSocket socket, BluetoothDevice device) {

        mmSocket = socket;

        mmDevice = device;

        DEVICE = device.getName();

        InputStream tmpIn = null;
        OutputStream tmpOut = null;

        //使用临时对象获取输入和输出流，因为成员流是静态类型

        //1、获取 InputStream 和 OutputStream
        try {
            tmpIn = socket.getInputStream();
            tmpOut = socket.getOutputStream();

        } catch (
                IOException e) {
            Log.e(TAG + DEVICE, "获取InputStream 和 OutputStream异常!");
        }

        mmInStream = tmpIn;
        mmOutStream = tmpOut;

        if (mmInStream != null) {
            Log.i(TAG + DEVICE, "已获取InputStream");
        }

        if (mmOutStream != null) {
            Log.i(TAG + DEVICE, "已获取OutputStream");
        }

    }

    public void run() {
        //最大缓存区 存放流
        byte[] buffer = new byte[1024 * 2];  //buffer store for the stream
        //从流的read()方法中读取的字节数
        int bytes = 0;  //bytes returned from read()

        //持续监听输入流直到发生异常
        while (!isStop) {
            try {

                if (mmInStream == null) {
                    Log.e(TAG + DEVICE, "run-->输入流mmInStream == null");
                    break;
                }
                //先判断是否有数据，有数据再读取
                if (mmInStream.available() != 0) {
                    //2、接收数据
                    bytes = mmInStream.read(buffer);  //从(mmInStream)输入流中(读取内容)读取的一定数量字节数,并将它们存储到缓冲区buffer数组中，bytes为实际读取的字节数
                    byte[] bs = buffer;
                    char[] c = new char[bytes];
                    //转换数据结构,去掉\n
                    readMessage += new String(bs, 0, bytes).replace("\r", "");
                    if (!readMessage.contains("\n"))
                        continue;
                    String ss[] = readMessage.substring(0, readMessage.lastIndexOf("\n")).split("\n");

                    //将以行为单位分割好的包字串加入队列
                    for (int i = 0; i < ss.length; i++)
                        if (ss[i].length() > 0)//入队
                            sb3.offer(ss[i]);
                    //将不足以构成一个完整数据包的串保存下来，后来的数据拼接到该串后面
                    readMessage = readMessage.substring(readMessage.lastIndexOf("\n") + 1, readMessage.length());

                    //将队列里的信息按顺序发往前端
                    String messag = "";
                    while (sb3.size() >= senum) {
                        for (int i = 0; i < senum; i++) {
                            //出队的同时赋值
                            String pack = sb3.poll();
                            //拼接成串发往前端
                            messag += pack;
                            //如果是一次发送多个包的数据，拿\n分割
                            if (i > 1)
                                messag += "\n";
                        }
                        //将收到的分隔的数据发往前端
                        if (onSendReceiveDataListener != null) {
                            onSendReceiveDataListener.onReceiveDataSuccess(new String[]{mmDevice.getName(), mmDevice.getAddress(), messag});  //成功收到消息
                        }
                        messag = "";
                    }
                }

            } catch (IOException e) {
                Log.e(TAG + DEVICE, "run-->接收消息异常！" + e.getMessage());
                if (onSendReceiveDataListener != null) {
                    onSendReceiveDataListener.onReceiveDataError("接收消息异常:" + e.getMessage());  //接收消息异常
                }
                //关闭流和socket
                boolean isClose = cancel();
                if (isClose) {
                    Log.e(TAG + DEVICE, "run-->接收消息异常,成功断开连接！");
                }
                break;
            }
        }
        //关闭流和socket
        boolean isClose = cancel();
        if (isClose) {
            Log.i(TAG + DEVICE, "run-->接收消息结束,断开连接！");
        }
    }

    //发送数据
    public boolean write(byte[] bytes) {
        try {

            if (mmOutStream == null) {
                Log.e(TAG + DEVICE, "mmOutStream == null");
                return false;
            }
            //发送数据
            mmOutStream.write(bytes);
            Log.i(TAG + DEVICE, "写入成功：" + new String(bytes, "UTF-8"));
            if (onSendReceiveDataListener != null) {
                onSendReceiveDataListener.onSendDataSuccess(bytes);  //发送数据成功回调
            }
            return true;

        } catch (Exception e) {
            Log.e(TAG + DEVICE, "写入失败：" + e.toString());
            if (onSendReceiveDataListener != null) {
                onSendReceiveDataListener.onSendDataError(bytes, "写入失败");  //发送数据失败回调
            }
            return false;
        }
    }

    /**
     * 释放
     *
     * @return true 断开成功  false 断开失败
     */
    public boolean cancel() {
        try {
            if (mmInStream != null) {
                mmInStream.close();  //关闭输入流
            }
            if (mmOutStream != null) {
                mmOutStream.close();  //关闭输出流
            }
            if (mmSocket != null) {
                mmSocket.close();   //关闭socket
            }
            if (connectThread != null) {
                connectThread.cancel();
            }

            connectThread = null;
            mmInStream = null;
            mmOutStream = null;
            mmSocket = null;

            Log.i(TAG + DEVICE, "cancel-->成功断开连接");
            return true;

        } catch (IOException e) {
            // 任何一部分报错，都将强制关闭socket连接
            mmInStream = null;
            mmOutStream = null;
            mmSocket = null;

            Log.e(TAG + DEVICE, "cancel-->断开连接异常！" + e.getMessage());
            return false;
        }
    }

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

    private OnSendReceiveDataListener onSendReceiveDataListener;

    public void setOnSendReceiveDataListener(OnSendReceiveDataListener onSendReceiveDataListener) {
        this.onSendReceiveDataListener = onSendReceiveDataListener;
    }

    //收发数据监听者
    public interface OnSendReceiveDataListener {
        void onSendDataSuccess(byte[] data);  //发送数据结束

        void onSendDataError(byte[] data, String errorMsg); //发送数据出错

        void onReceiveDataSuccess(String[] data);  //接收到数据

        void onReceiveDataError(String errorMsg);   //接收数据出错
    }

}
