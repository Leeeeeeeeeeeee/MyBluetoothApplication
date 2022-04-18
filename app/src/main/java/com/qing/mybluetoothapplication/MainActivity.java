package com.qing.mybluetoothapplication;

import static com.qing.mybluetoothapplication.BtService.*;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.ListPopupWindow;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

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

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.SNIHostName;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "<MainActivity>";
    private TabLayoutMediator tabLayoutMediator;
    private TabLayout tabLayout;

    @RequiresApi(api = Build.VERSION_CODES.Q)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        checkPermission();

        initData();
        initView();
    }

    private void initData() {

    }

    private void initView() {

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        tabLayout = findViewById(R.id.tab_layout);
        for (int i = 0; i < BtApplication.getInstance().tab.size(); i++) {
            tabLayout.addTab(tabLayout.newTab());
        }

        ViewPager2 viewPager2 = findViewById(R.id.viewpager2);
        viewPager2.setAdapter(new FragmentStateAdapter(this) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                return BtApplication.getInstance().fragments.get(position);
            }

            @Override
            public int getItemCount() {
                return BtApplication.getInstance().fragments.size();
            }
        });

        tabLayoutMediator = new TabLayoutMediator(tabLayout, viewPager2, true, true, (tab, position) -> tab.setText(BtApplication.getInstance().tab.get(position)));
        tabLayoutMediator.attach();

        TextView mTvAdd = findViewById(R.id.tv_add);
        mTvAdd.setOnClickListener(this);

        TextView mTvDelete = findViewById(R.id.tv_delete);
        mTvDelete.setOnClickListener(this);
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void checkPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            String[] strings = {Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE};
            ActivityCompat.requestPermissions(this, strings, 2);
        }
    }


    @Override
    protected void onDestroy() {
        tabLayoutMediator.detach();
        super.onDestroy();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.tv_add: {
                BtApplication.getInstance().addTab();
                tabLayout.addTab(tabLayout.newTab());
                tabLayoutMediator.detach();
                tabLayoutMediator.attach();
            }
            break;
            case R.id.tv_delete: {
                if (BtApplication.getInstance().tabSeq == 0)
                    return;
                BtApplication.getInstance().deleteTab();
                tabLayout.removeTabAt(BtApplication.getInstance().tabSeq);
                tabLayoutMediator.detach();
                tabLayoutMediator.attach();
            }
            break;
        }
    }
}