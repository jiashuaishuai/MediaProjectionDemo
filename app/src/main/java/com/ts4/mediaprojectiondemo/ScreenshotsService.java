package com.ts4.mediaprojectiondemo;


import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.WindowManager;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;

public class ScreenshotsService extends Service {
    public int mResultCode = 0;
    public Intent mResultData = null;
    public MediaProjectionManager mMediaProjectionManager;
    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;
    private int windowWidth = 0;
    private int windowHeight = 0;


    public static final int INIT_SERVICE = 0x111;
    public static final int STOP_SERVICE = 0x112;


    public static final int CAPTURE = 0x110;
    public static final int CAPTURE_SUCCESS = 0x1113;

    public static final int START_RECORD_VIDEO = 0x120;
    public static final int STOP_RECORD_VIDEO = 0x124;
    public static final int RECORD_VIDEO_SUCCESS = 0x123;

    public static final int RECORD_TIMER = 0x130;

    private Messenger mMessenger;
    private String TAG = "ScreenshotsService";
    private DisplayMetrics metrics;
    private int mScreenDensity;
    private ImageReader mImageReader;
    private MediaRecorder mediaRecorder;

    private SimpleDateFormat dateFormat;
    private String strDate;
    private String rootDirectory;
    private String nameImage;
    public String nameVideo;

    public boolean recordRun;

    private int type;



    static class MessageHandler extends Handler {
        ScreenshotsService service;

        public MessageHandler(ScreenshotsService s) {
            service = s;
        }

        @Override
        public void handleMessage(Message msg) {

            switch (msg.what) {
                case CAPTURE:
                    service.startCapture(msg);
                    break;
                case START_RECORD_VIDEO:
                    service.startRecord();
                    break;
                case STOP_RECORD_VIDEO:
                    service.stopRecord(msg);
                    break;
                case INIT_SERVICE:
                    service.initVirtualDisplay(msg.arg1);
                    break;
                case STOP_SERVICE:
                    service.stopCaptureService();
                    break;

            }

        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        dateFormat = new SimpleDateFormat("yyyy_MM_dd_hh_mm_ss");
        strDate = dateFormat.format(new java.util.Date());
        rootDirectory = Environment.getExternalStorageDirectory().getPath() + "/Pictures/";

        mMessenger = new Messenger(new MessageHandler(this));
        mMediaProjectionManager = (MediaProjectionManager) getApplication().getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        WindowManager mWindowManager = (WindowManager) getApplication().getSystemService(Context.WINDOW_SERVICE);
        Point realSize = new Point();
        mWindowManager.getDefaultDisplay().getRealSize(realSize);
        windowWidth = realSize.x;
        windowHeight = realSize.y;
        metrics = new DisplayMetrics();
        mWindowManager.getDefaultDisplay().getMetrics(metrics);
        mScreenDensity = metrics.densityDpi;

    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        mResultCode = intent.getIntExtra("code", -1);
        mResultData = intent.getParcelableExtra("data");
        return mMessenger.getBinder();
    }


    public void stopCaptureService() {
        if (mImageReader != null) {
            mImageReader.close();
            mImageReader = null;
        }
        if (mediaRecorder != null) {
            try {
                mediaRecorder.setOnErrorListener(null);
                mediaRecorder.setOnInfoListener(null);
                mediaRecorder.setPreviewDisplay(null);
                mediaRecorder.stop();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                mediaRecorder.reset();
                mediaRecorder.release();
                mediaRecorder = null;
            }


        }
        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
            mVirtualDisplay = null;
        }
        if (mMediaProjection != null) {
            mMediaProjection.stop();
            mMediaProjection = null;
        }
        stopForeground(true);

    }


    /**
     * 初始化截屏服务   type：服务类型，截屏CAPTURE，录屏START_RECORD_VIDEO
     */
    public void initVirtualDisplay(int type) {
        this.type = type;
        if (mMediaProjection == null) {
            setUpMediaProjection();
        }
        if (type == CAPTURE) {
            if (mImageReader == null || mVirtualDisplay == null)//截取屏幕需要先初始化  virtualDisplay
                virtualDisplay();
        }
    }

    private void setUpMediaProjection() {
        createNotificationChannel();//构建通知栏，适配api 29,小于29可以不用，
        mMediaProjection = mMediaProjectionManager.getMediaProjection(mResultCode, mResultData);
        Log.d(TAG, "mMediaProjection 初始化成功");
    }

    private void virtualDisplay() {
        mVirtualDisplay = mMediaProjection.createVirtualDisplay("screen-mirror",
                windowWidth, windowHeight, mScreenDensity, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                getSurface(), null, null);
    }


    private Surface getSurface() {
        if (type == CAPTURE) {
            initImageReader();
            return mImageReader.getSurface();
        } else {
            initMediaRecorder();
            return mediaRecorder.getSurface();
        }
    }

    private void initImageReader() {
        mImageReader = ImageReader.newInstance(windowWidth, windowHeight, 0x1, 2); //ImageFormat.RGB_565
    }


    private void initMediaRecorder() {
        mediaRecorder = new MediaRecorder();
//        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);


        strDate = dateFormat.format(new java.util.Date());
        nameVideo = rootDirectory + strDate + ".mp4";
        mediaRecorder.setOutputFile(nameVideo);
        mediaRecorder.setVideoSize(windowWidth, windowHeight);


        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
//        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);


        mediaRecorder.setVideoEncodingBitRate(5 * windowWidth * windowHeight);
        mediaRecorder.setVideoFrameRate(30);
        try {
            mediaRecorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 停止录制
     *
     * @param msg
     */
    public void stopRecord(Message msg) {
        try {
            if (recordRun) {
                if (mediaRecorder != null) {
                    mediaRecorder.setOnErrorListener(null);
                    mediaRecorder.setOnInfoListener(null);
                    mediaRecorder.setPreviewDisplay(null);
                    mediaRecorder.stop();
                }
                if (mVirtualDisplay != null) {
                    mVirtualDisplay.release();
                }
                Log.d(TAG, "停止录制");
                //录制完成通知客户端
                Message toClient = Message.obtain(msg);
                toClient.what = RECORD_VIDEO_SUCCESS;
                Bundle bundle = new Bundle();
                bundle.putString("VideoPath", nameVideo);
                toClient.setData(bundle);
                msg.replyTo.send(toClient);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (mediaRecorder != null) {
                mediaRecorder.reset();
                mediaRecorder.release();
                mediaRecorder = null;
            }
            mVirtualDisplay = null;
            recordRun = false;
        }
    }

    /**
     * 开始录制
     * mediaRecorder  每次录制完毕后再次录制需要重新初始化所有参数，，暂时无解
     *
     */

    public void startRecord() {
        if (!recordRun) {
            virtualDisplay();
            mediaRecorder.start();
            recordRun = true;
            Log.d(TAG, "开始录制");

        } else {
            Log.d(TAG, "录制进行中");
        }
    }



    private void startCapture(Message msg) {

        //client message
        Message msgToClient = Message.obtain(msg);
        Bundle bundle = new Bundle();
        msgToClient.what = CAPTURE_SUCCESS;
        strDate = dateFormat.format(new java.util.Date());
        nameImage = rootDirectory + strDate + ".png";
        bundle.putString("path", nameImage);
        msgToClient.setData(bundle);
        //捕获bitmap
        Image image = mImageReader.acquireLatestImage();
        int width = image.getWidth();
        int height = image.getHeight();
        final Image.Plane[] planes = image.getPlanes();
        final ByteBuffer buffer = planes[0].getBuffer();
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int rowPadding = rowStride - pixelStride * width;
        Bitmap bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(buffer);
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height);
        image.close();
        Log.i(TAG, "捕获图像数据");

        if (bitmap != null) {
            try {
                File fileImage = new File(nameImage);
                if (!fileImage.exists()) {
                    fileImage.createNewFile();
                    Log.i(TAG, "创建文件");
                }
                FileOutputStream out = new FileOutputStream(fileImage);
                if (out != null) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                    out.flush();
                    out.close();

                    //send message 通知 client
                    msg.replyTo.send(msgToClient);
                    //通知更新图库
                    Intent media = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                    Uri contentUri = Uri.fromFile(fileImage);
                    media.setData(contentUri);
                    this.sendBroadcast(media);
                    Log.i(TAG, "保存图片");

                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (RemoteException e) {
                e.printStackTrace();
            } finally {
                bitmap.recycle();
            }
        }

    }

    private void createNotificationChannel() {
        Notification.Builder builder = new Notification.Builder(getApplicationContext()); //获取一个Notification构造器
        builder.setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher)) // 设置下拉列表中的图标(大图标)
                //.setContentTitle("SMI InstantView") // 设置下拉列表里的标题
                .setSmallIcon(R.mipmap.ic_launcher) // 设置状态栏内的小图标
                .setContentText("is running......") // 设置上下文内容
                .setWhen(System.currentTimeMillis()); // 设置该通知发生的时间

        /*以下是对Android 8.0的适配*/
        //普通notification适配
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId("notification_id");
        }
        //前台服务notification适配
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel channel = new NotificationChannel("notification_id", "notification_name", NotificationManager.IMPORTANCE_LOW);
            notificationManager.createNotificationChannel(channel);
        }

        Notification notification = builder.build(); // 获取构建好的Notification
        startForeground(110, notification);

    }

}
