package com.taehoon.cameraproject;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.os.Environment;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.jiangdg.usbcamera.UVCCameraHelper;
import com.jiangdg.usbcamera.utils.FileUtils;
import com.serenegiant.usb.CameraDialog;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.common.AbstractUVCCameraHandler;
import com.serenegiant.usb.widget.CameraViewInterface;
import com.taehoon.cameraproject.databinding.ActivityMainTestBinding;

import java.io.File;

public class MainActivity extends AppCompatActivity implements CameraDialog.CameraDialogParent, CameraViewInterface.Callback {
    private static final String TAG = "Debug";
    private static final String CLASS_NAME = MainActivity.class.getSimpleName();
    private static final String DEVICES_INFO = "DEVICES_INFO";
    private ActivityMainTestBinding binding;
    private MainViewModel mViewModel;

    private BroadcastReceiver mReceiver;
    private USBMonitor mUSBMonitor;
    private UVCCameraHelper mCameraHelper;
    private CameraViewInterface mUVCCameraView;
    private AlertDialog mDialog;
    private StringBuilder mDeviceInfoBuilder = new StringBuilder();

    private boolean isRequest;
    private boolean isPreview;

    private UVCCameraHelper.OnMyDevConnectListener listener = new UVCCameraHelper.OnMyDevConnectListener() {
        @Override
        public void onAttachDev(UsbDevice device) {
            // request open permission
            if (!isRequest) {
                isRequest = true;
                if (mCameraHelper != null) {
                    mCameraHelper.requestPermission(0);
                    showShortMsg(device.getDeviceName() + " is connected!");
                }
            }
        }

        @Override
        public void onDettachDev(UsbDevice device) {
            // close camera
            if (isRequest) {
                isRequest = false;
                mCameraHelper.closeCamera();
                showShortMsg(device.getDeviceName() + " is out!");
            }
        }

        @Override
        public void onConnectDev(UsbDevice device, boolean isConnected) {
            if (!isConnected) {
                showShortMsg("fail to connect");
                isPreview = false;
            } else {
                isPreview = true;
                showShortMsg("connecting");

                mDeviceInfoBuilder
                        .append("Id : ").append(device.getDeviceId()).append("\n")
                        .append("Product Id : ").append(device.getProductId()).append("\n")
                        .append("Vendor Id : ").append(device.getVendorId()).append("\n")
                        .append("Serial number : ").append(device.getSerialNumber()).append("\n")
                        .append("Name : ").append(device.getDeviceName()).append("\n")
                        .append("Product name : ").append(device.getProductName()).append("\n")
                        .append("Version : ").append(device.getVersion()).append("\n")
                        .append("Class : ").append(device.getDeviceClass()).append("\n")
                        .append("Sub class : ").append(device.getDeviceSubclass()).append("\n\n");

                Intent intent = new Intent(CLASS_NAME);
                intent.putExtra(DEVICES_INFO, mDeviceInfoBuilder.toString());
                LocalBroadcastManager.getInstance(MainActivity.this).sendBroadcast(intent);

                // need to wait UVCCamera initialize over
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(2500);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        Looper.prepare();
                        if (mCameraHelper != null && mCameraHelper.isCameraOpened()) {
                            // 원 제공되는 소스 코드는 Seek bar 제어 로직이 들어감
                        }
                        Looper.loop();
                    }
                }).start();
            }
        }

        @Override
        public void onDisConnectDev(UsbDevice device) {
            showShortMsg("disconnected");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(MainActivity.this, R.layout.activity_main_test);
        binding.setActivity(MainActivity.this);

        mViewModel = ViewModelFactory.getInstance(getApplication()).create(MainViewModel.class);
        mViewModel.initViewModel();

        mUVCCameraView = binding.uvcCameraTextureView;
        mUVCCameraView.setCallback(this);
        mCameraHelper = UVCCameraHelper.getInstance();
        mCameraHelper.setDefaultFrameFormat(UVCCameraHelper.FRAME_FORMAT_MJPEG);
        mCameraHelper.initUSBMonitor(this, mUVCCameraView, listener);

        mCameraHelper.setOnPreviewFrameListener(new AbstractUVCCameraHandler.OnPreViewResultListener() {
            @Override
            public void onPreviewResult(byte[] nv21Yuv) {
                Log.d(TAG, "onPreviewResult: " + nv21Yuv.length);
            }
        });

        IntentFilter filter = new IntentFilter();
        filter.addAction(CLASS_NAME);
        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String devicesInfo = intent.getStringExtra(DEVICES_INFO);
                binding.tvDeviceInfo.setText(devicesInfo);
            }
        };
        LocalBroadcastManager.getInstance(MainActivity.this).registerReceiver(mReceiver, filter);
    }

    @Override
    protected void onStart() {
        super.onStart();
        // step.2 register USB event broadcast
        if (mCameraHelper != null) {
            mCameraHelper.registerUSB();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // step.3 unregister USB event broadcast
        if (mCameraHelper != null) {
            mCameraHelper.unregisterUSB();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        FileUtils.releaseFile();
        // step4. release uvc camera resources
        if (mCameraHelper != null) {
            mCameraHelper.release();
        }
        LocalBroadcastManager.getInstance(MainActivity.this).unregisterReceiver(mReceiver);
    }

    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.bt_capture:
                if (mCameraHelper != null && mCameraHelper.isCameraOpened()) {
                    String imageFolderPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/USBCamera/images";
                    File imageFolder = new File(imageFolderPath);
                    if (!imageFolder.exists()) {
                        //noinspection ResultOfMethodCallIgnored
                        imageFolder.mkdir();
                    }
                    String imageFilePath = imageFolderPath + File.separator + System.currentTimeMillis() + UVCCameraHelper.SUFFIX_JPEG;
                    mCameraHelper.capturePicture(imageFilePath, null);
                }
                break;
        }
    }

    private void showShortMsg(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public USBMonitor getUSBMonitor() {
        return mCameraHelper.getUSBMonitor();
    }

    @Override
    public void onDialogResult(boolean canceled) {
        if (canceled) {
            showShortMsg("onDialogResult is canceled!");
        }
    }

    @Override
    public void onSurfaceCreated(CameraViewInterface view, Surface surface) {
        if (!isPreview && mCameraHelper.isCameraOpened()) {
            mCameraHelper.startPreview(mUVCCameraView);
            isPreview = true;
        }
    }

    @Override
    public void onSurfaceChanged(CameraViewInterface view, Surface surface, int width, int height) {

    }

    @Override
    public void onSurfaceDestroy(CameraViewInterface view, Surface surface) {
        if (isPreview && mCameraHelper.isCameraOpened()) {
            mCameraHelper.stopPreview();
            isPreview = false;
        }
    }
}
