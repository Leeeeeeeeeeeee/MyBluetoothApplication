package com.qing.mybluetoothapplication;

import android.app.Application;
import android.os.Bundle;

import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BtApplication extends Application {

    private static BtApplication instance;

    public static BtApplication getInstance() {
        return instance;
    }

    public int tabSeq = 0;

    public Map<Integer, String> tab = new HashMap<>();

    public List<Fragment> fragments = new ArrayList<>();


    @Override
    public void onCreate() {
        super.onCreate();
        this.instance = this;
        initData();
    }

    private void initData() {
        tab.put(0, "设备1");
        fragments.add(getNewestDeviceFragment());
    }

    public void addTab() {
        tab.put(++tabSeq, "设备" + (tabSeq + 1));
        fragments.add(getNewestDeviceFragment());
    }

    public void deleteTab() {
        tab.put(--tabSeq, "设备" + (tabSeq + 1));
        fragments.remove(tabSeq);
    }

    private DeviceFragment getNewestDeviceFragment() {
        Bundle bundle = new Bundle();
        bundle.putInt("tabSeq", tabSeq);
        DeviceFragment fragment = new DeviceFragment();
        fragment.setArguments(bundle);
        return fragment;
    }

}
