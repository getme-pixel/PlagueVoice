package com.plaguevoice;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import androidx.core.app.NotificationCompat;

/**
 * Foreground service that keeps the voice changer alive in the background.
 *
 * It is declared with android:stopWithTask="true" and also stops itself in onTaskRemoved(),
 * so it only turns off when the app is fully closed (swiped away / force stopped).
 */
public class VoiceService extends Service {

    public static final String ACTION_START = "com.plaguevoice.action.START";
    public static final String ACTION_STOP = "com.plaguevoice.action.STOP";
    public static final String ACTION_UPDATE = "com.plaguevoice.action.UPDATE";

    public static final String EXTRA_IN = "extra_input_id";
    public static final String EXTRA_OUT = "extra_output_id";
    public static final String EXTRA_PITCH = "extra_pitch";
    public static final String EXTRA_GROWL = "extra_growl";
    public static final String EXTRA_REVERB = "extra_reverb";

    private static final String CHANNEL_ID = "plague_voice";
    private static final int NOTIF_ID = 4242;

    private AudioEngine engine;
    private PowerManager.WakeLock wakeLock;

    @Override
    public void onCreate() {
        super.onCreate();
        engine = new AudioEngine(this);
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = (intent != null && intent.getAction() != null)
                ? intent.getAction() : ACTION_START;

        if (ACTION_STOP.equals(action)) {
            shutdown();
            stopSelf();
            return START_NOT_STICKY;
        }

        float pitch = intent != null ? intent.getFloatExtra(EXTRA_PITCH, -7f) : -7f;
        float growl = intent != null ? intent.getFloatExtra(EXTRA_GROWL, 0.55f) : 0.55f;
        float reverb = intent != null ? intent.getFloatExtra(EXTRA_REVERB, 0.35f) : 0.35f;
        int inId = intent != null ? intent.getIntExtra(EXTRA_IN, -1) : -1;
        int outId = intent != null ? intent.getIntExtra(EXTRA_OUT, -1) : -1;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, buildNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(NOTIF_ID, buildNotification());
        }

        if (ACTION_UPDATE.equals(action)) {
            engine.configure(pitch, growl, reverb);
        } else {
            engine.stop();
            engine.configure(pitch, growl, reverb);
            engine.start(inId, outId);
        }

        acquireWakeLock();
        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        shutdown();
        stopSelf();
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        shutdown();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void shutdown() {
        if (engine != null) {
            engine.stop();
        }
        releaseWakeLock();
        stopForeground(true);
    }

    private void acquireWakeLock() {
        if (wakeLock == null) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PlagueVoice:audio");
                wakeLock.setReferenceCounted(false);
            }
        }
        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire();
        }
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel ch = new NotificationChannel(
                        CHANNEL_ID,
                        getString(R.string.channel_name),
                        NotificationManager.IMPORTANCE_LOW);
                ch.setDescription(getString(R.string.channel_desc));
                ch.setShowBadge(false);
                nm.createNotificationChannel(ch);
            }
        }
    }

    private Notification buildNotification() {
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            piFlags |= PendingIntent.FLAG_IMMUTABLE;
        }

        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentPi = PendingIntent.getActivity(this, 0, open, piFlags);

        Intent stop = new Intent(this, VoiceService.class);
        stop.setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 1, stop, piFlags);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notif_title))
                .setContentText(getString(R.string.notif_text))
                .setSmallIcon(R.drawable.ic_stat_voice)
                .setContentIntent(contentPi)
                .addAction(0, getString(R.string.notif_stop), stopPi)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build();
    }
}
