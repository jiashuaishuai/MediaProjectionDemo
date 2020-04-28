package com.ts4.mediaprojectiondemo;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;


public class MainActivity extends AppCompatActivity {

    private String TAG = "MainActivity";
    private int result = 0;
    private Intent intent = null;
    private int REQUEST_MEDIA_PROJECTION = 1;
    private MediaProjectionManager mMediaProjectionManager;
    private static final int STORAGE_REQUEST_CODE = 102;

    private int type = ScreenshotsService.CAPTURE;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_REQUEST_CODE);
        }
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, STORAGE_REQUEST_CODE);
        }


        RadioGroup rg = findViewById(R.id.rg);
        RadioButton rbCapture = findViewById(R.id.rb_capture);
        RadioButton rbRecord = findViewById(R.id.rb_record);
        rg.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                switch (checkedId) {
                    case R.id.rb_capture:
                        type = ScreenshotsService.CAPTURE;
                        break;
                    case R.id.rb_record:
                        type = ScreenshotsService.START_RECORD_VIDEO;
                        break;
                }
            }
        });

    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_REQUEST_CODE) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                finish();
            }
        }
    }


    /**
     * 录屏
     *
     * @param view
     */
    public void recordVideo(View view) {
        sendMessage(ScreenshotsService.START_RECORD_VIDEO);
        recordTimer();
    }

    public void stopRecordVideo(View view) {
        sendMessage(ScreenshotsService.STOP_RECORD_VIDEO);
        Countdown.removeCallbacks(CountdownRun);
    }


    /**
     * 截图
     *
     * @param view
     */
    public void screenshot(View view) {
        sendMessage(ScreenshotsService.CAPTURE);

    }

    /**
     * 启动服务器，bindingService
     *
     * @param view
     */
    public void startService(View view) {
        mMediaProjectionManager = (MediaProjectionManager) getApplication().getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(mMediaProjectionManager.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION);
    }


    /**
     * 初始化服务
     *
     * @param v
     */
    public void initService(View v) {
        sendMessage(ScreenshotsService.INIT_SERVICE, type);
    }

    /**
     * 停止服务
     *
     * @param view
     */
    public void stopService(View view) {
        sendMessage(ScreenshotsService.STOP_SERVICE);
    }

    public void sendMessage(int code, Object content) {

        if (isConn) {
            Message msgFromClient = Message.obtain(null, code);

            if (content != null) {
                if (content instanceof Integer) {
                    msgFromClient.arg1 = (int) content;
                } else if (content instanceof String) {
                    Bundle bundle = new Bundle();
                    bundle.putString("content", (String) content);
                    msgFromClient.setData(bundle);
                }
            }

            msgFromClient.replyTo = mMessenger;
            try {
                mService.send(msgFromClient);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else {
            Toast.makeText(this, "意外终止", Toast.LENGTH_SHORT).show();
        }
    }

    public void sendMessage(int code) {
        sendMessage(code, null);
    }

    private Messenger mService;
    private boolean isConn;
    private Messenger mMessenger = new Messenger(new Handler() {
        @Override
        public void handleMessage(Message msgFromServer) {
            switch (msgFromServer.what) {
                case ScreenshotsService.CAPTURE_SUCCESS:
                    Bundle bundle = msgFromServer.getData();
                    String path = bundle.getString("path");
                    Log.d(TAG, "path:    " + path);
                    break;
                case ScreenshotsService.RECORD_TIMER:
                    int time = msgFromServer.arg1;
                    Log.d(TAG, "time:  " + time);
                    break;
                case ScreenshotsService.RECORD_VIDEO_SUCCESS:
                    Bundle videoBundle = msgFromServer.getData();
                    String videoPath = videoBundle.getString("VideoPath");
                    Log.d(TAG, "VideoPath:    " + videoPath);
                    break;
            }
            super.handleMessage(msgFromServer);
        }
    });

    private ServiceConnection mConn = new ServiceConnection() {

        //IBinder 对象，需要Bundle包装，传给Unity页面，和service进行通信
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mService = new Messenger(service);
            isConn = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
            isConn = false;
        }
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode != Activity.RESULT_OK) {
                return;
            } else if (data != null && resultCode != 0) {
                result = resultCode;
                intent = data;
                Intent intent = new Intent(getApplicationContext(), ScreenshotsService.class);
                intent.putExtra("code", resultCode);
                intent.putExtra("data", data);
                bindService(intent, mConn, Context.BIND_AUTO_CREATE);
            }
        }

    }

    Handler Countdown = new Handler();
    Runnable CountdownRun;
    private int recordTotalSize = 60 * 3;
    private int recordSize;//录制时长，单位秒

    /**
     * 计时器
     */
    private void recordTimer() {
        recordSize = 0;
        CountdownRun = new Runnable() {
            @Override
            public void run() {
                if (recordSize >= recordTotalSize)//录制满时长则退出
                {
                    stopRecordVideo(null);//停止录制
                    return;
                }

                Log.d(TAG, "录制中 time：" + recordSize + "总时长：" + recordTotalSize);
                recordSize++;
                Countdown.postDelayed(this, 1000);
            }
        };
        Countdown.post(CountdownRun);
    }

}
