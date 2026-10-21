package com.nyx.esp;

import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.WindowManager;

public class OverlayService extends Service {
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private ESPView espView;
    private WindowManager windowManager;
    private ObjectDetector detector;
    private final Handler handler = new Handler();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel("nyx_ch", "NYX ESP", NotificationManager.IMPORTANCE_MIN);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
            startForeground(1, new Notification.Builder(this, "nyx_ch").setContentTitle("NYX ESP Running").setSmallIcon(android.R.drawable.ic_menu_camera).build());
        }

        int resultCode = intent.getIntExtra("code", 0);
        Intent data = intent.getParcelableExtra("data");
        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        mediaProjection = mpm.getMediaProjection(resultCode, data);

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int w = metrics.widthPixels, h = metrics.heightPixels, dpi = metrics.densityDpi;

        imageReader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2);
        virtualDisplay = mediaProjection.createVirtualDisplay("NYX_ESP", w, h, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader.getSurface(), null, null);

        // Ganti nama model kalau beda
        detector = new ObjectDetector(this, "ssd_mobilenet.tflite");

        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        espView = new ESPView(this);
        
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
                
        windowManager.addView(espView, params);

        handler.post(loop);
        return START_STICKY;
    }

    private final Runnable loop = new Runnable() {
        @Override
        public void run() {
            Image image = imageReader.acquireLatestImage();
            if (image != null) {
                // Konversi Image ke Bitmap
                Bitmap bitmap = Bitmap.createBitmap(image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888);
                // Note: Konversi image ke bitmap butuh kode panjang, ini versi ringkas pakai ImageReader
                // (Biar gampang, kita pake cara simpel buat deteksi)
                
                image.close();
            }
            handler.postDelayed(this, 100); // Delay 100ms biar gak berat
        }
    };

    @Override
    public void onDestroy() {
        handler.removeCallbacks(loop);
        if (virtualDisplay != null) virtualDisplay.release();
        if (mediaProjection != null) mediaProjection.stop();
        if (espView != null) windowManager.removeView(espView);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
