package uk.ac.cam.cusf.squirrelradio;

import java.io.FileDescriptor;
import java.io.IOException;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnInfoListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.Process;
import android.util.Log;

public class RadioService extends Service {

    private final static String TAG = "SquirrelRadio";
    private final static String THREAD_NAME = "SquirrelRadioThread";

    private final static int STREAM_TYPE = AudioManager.STREAM_MUSIC;
    
    public final static String DESCENT_BROADCAST = "uk.ac.cam.cusf.intent.action.DESCENT";

    // Minimum delay between transmissions, in milliseconds
    public static int DELAY = 10000;

    // Ratio of RTTY to SSTV transmissions (ie. TX_RATIO : 1)
    public final static int TX_RATIO = 3;

    private Rtty rtty;
    private Sstv sstv;

    private NotificationManager nManager;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private BroadcastReceiver batteryListener;
    private BroadcastReceiver descentListener;

    private Intent battery;
    private boolean descent = false;
    private boolean landed = false;

    private HandlerThread thread;

    private MediaPlayer mediaPlayer;
    
    private Context context;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {

        context = this.getApplicationContext();
        
        rtty = new Rtty(context);
        sstv = new Sstv(context);

        AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        audioManager.setStreamSolo(STREAM_TYPE, true);
        int streamVol = audioManager.getStreamMaxVolume(STREAM_TYPE);
        audioManager.setStreamVolume(STREAM_TYPE, streamVol, 0);

        initMediaPlayer();

        nManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
            }

            @Override
            public void onProviderDisabled(String provider) {
            }

            @Override
            public void onProviderEnabled(String provider) {
            }

            @Override
            public void onStatusChanged(String provider, int status,
                    Bundle extras) {
            }
        };

        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                10000, 0, locationListener);

        batteryListener = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(Intent.ACTION_BATTERY_CHANGED))
                    battery = intent;
            }
        };

        IntentFilter actionChanged = new IntentFilter();
        actionChanged.addAction(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(batteryListener, actionChanged);
        
        descentListener = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(DESCENT_BROADCAST)) {
                    Log.i(TAG, "DESCENT_BROADCAST received!");
                    Bundle extras = intent.getExtras();
                    if (extras.getBoolean("descent", false)) {
                        Log.i(TAG, "Descending");
                        descent = true;
                        DELAY = 500;
                    } else if (extras.getBoolean("landed", false)) {
                        Log.i(TAG, "Landed");
                        landed = true;
                        DELAY = 60000;
                    }
                }
            }
        };

        actionChanged = new IntentFilter();
        actionChanged.addAction(DESCENT_BROADCAST);
        registerReceiver(descentListener, actionChanged);

        thread = new TxHandlerThread(THREAD_NAME, Process.THREAD_PRIORITY_AUDIO);

    }

    public int onStartCommand(Intent intent, int flags, int startId) {

        if (!thread.isAlive()) {
            if (thread.getState() == Thread.State.TERMINATED) {
                Log.i(TAG,
                        "Radio thread is in terminated state; creating new instance");
                thread = new TxHandlerThread(THREAD_NAME,
                        Process.THREAD_PRIORITY_AUDIO);
            }
            thread.start();
            Log.i(TAG, "Radio thread started");
            showNotification();
        } else {
            Log.i(TAG, "Radio thread is already alive");
        }
        
        RadioStatus.setRunning(true);

        return START_STICKY;
    }

    private class TxHandlerThread extends HandlerThread implements
            Handler.Callback {

        public final static int RTTY = 0;
        public final static int SSTV = 1;

        private Handler handler;

        int txCount = 0;

        public TxHandlerThread(String name, int priority) {
            super(name, priority);
        }

        @Override
        public boolean handleMessage(Message msg) {
            AudioFile audio;
            int duration = -1;

            switch (msg.what) {

            case RTTY:

                Log.i(TAG, "Preparing RTTY");

                Location location = locationManager
                        .getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (location == null) {
                    Log.i(TAG,
                            "getLastKnownLocation() returned null, using default Location object instead");
                    location = new Location(LocationManager.GPS_PROVIDER);
                }
                
                // This call blocks for a while
                audio = rtty.createRtty(location, battery);
                
                if (audio == null) {
                    Log.i(TAG, "createRtty() returned null");
                } else {
                    try {
                        duration = transmit(audio);
                    } catch (Exception e) {
                        Log.e(TAG, "transmit() threw unknown Exception", e);
                        mediaPlayer = null;
                        duration = 0;
                    }
                }

                break;

            case SSTV:

                if (descent) {
                    Log.i(TAG, "Generate image & hopefully Tweet...");
                    sstv.creator.generate();
                    break;
                }
                
                Log.i(TAG, "Preparing SSTV");

                // This call blocks for quite some time
                audio = sstv.generateImage();
                
                if (audio == null) {
                    Log.i(TAG, "generateImage() returned null");
                } else {
                    try {
                        duration = transmit(audio);
                    } catch (Exception e) {
                        Log.e(TAG, "transmit() threw unknown Exception", e);
                        mediaPlayer = null;
                        duration = 0;
                    }
                }

                break;
            }

            handler.sendMessageDelayed(getNext(), (duration >= 0 ? duration
                    + DELAY : 0));

            return false;
        }

        @Override
        protected void onLooperPrepared() {
            handler = new Handler(this.getLooper(), this);
            handler.sendMessageDelayed(getNext(), DELAY);
        }

        private Message getNext() {
            txCount++;
            
            Intent intent = new Intent();
            intent.setAction("uk.ac.cam.cusf.intent.ALIVE");
            context.sendBroadcast(intent);
            
            if (landed) {
                return handler.obtainMessage(RTTY);
            } else if (txCount % (TX_RATIO + 1) == TX_RATIO) {
                return handler.obtainMessage(SSTV);
            } else {
                return handler.obtainMessage(RTTY);
            }
        }

    }

    private int transmit(final AudioFile audio) throws Exception {

        FileDescriptor fd = audio.getFileDescriptor(context);
        
        if (fd == null) {
            Log.e(TAG, "Null WAV File supplied to transmit()");
            return 0;
        }

        if (mediaPlayer == null)
            initMediaPlayer();

        if (mediaPlayer.isPlaying()) {
            return mediaPlayer.getDuration() - mediaPlayer.getCurrentPosition();
        }

        mediaPlayer.setOnCompletionListener(new OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                mp.reset();
                // Delete audio file to save internal memory
                audio.delete(context);
            }
        });

        try {
            mediaPlayer.setDataSource(fd);
            mediaPlayer.prepare();
            mediaPlayer.start();
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "IllegalArgumentException in transmit()", e);
            return 0;
        } catch (IllegalStateException e) {
            Log.e(TAG, "IllegalStateException in transmit()", e);
            // Release and instantiate new MediaPlayer object just in case
            mediaPlayer.release();
            initMediaPlayer();
            return 0;
        } catch (IOException e) {
            Log.e(TAG, "IOException in transmit()", e);
            return 0;
        }

        Log.i(TAG, "Transmitting " + audio.getFilename() + ", "
                + mediaPlayer.getDuration() + "ms");

        return mediaPlayer.getDuration();
    }

    private void initMediaPlayer() {

        mediaPlayer = new MediaPlayer();

        mediaPlayer.setAudioStreamType(STREAM_TYPE);
        mediaPlayer.setVolume(1f, 1f);

        mediaPlayer.setOnErrorListener(new OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                Log.e(TAG, "MediaPlayer.onErrorListener (What: " + what
                        + ", Extra: " + extra + ")");
                // mp.reset();
                if (what == MediaPlayer.MEDIA_ERROR_SERVER_DIED) {
                    mp.release();
                    initMediaPlayer();
                }
                return true;
            }
        });

        mediaPlayer.setOnInfoListener(new OnInfoListener() {
            @Override
            public boolean onInfo(MediaPlayer mp, int what, int extra) {
                Log.e(TAG, "MediaPlayer.onInfoListener (What: " + what
                        + ", Extra: " + extra + ")");
                return false;
            }
        });

    }

    private void stopMediaPlayer() {
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            mediaPlayer.release();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "RadioService onDestroy()");
        thread.quit();
        stopMediaPlayer();
        locationManager.removeUpdates(locationListener);
        unregisterReceiver(batteryListener);
        unregisterReceiver(descentListener);
        nManager.cancel(1);
        
        RadioStatus.setRunning(false);
    }

    private void showNotification() {
        Notification notification = new Notification(R.drawable.icon,
                "SquirrelRadio", System.currentTimeMillis());
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, SquirrelRadio.class), 0);
        notification.setLatestEventInfo(this, "SquirrelRadio",
                "SquirrelRadio is now running in the background!",
                contentIntent);
        notification.flags |= Notification.FLAG_NO_CLEAR
                | Notification.FLAG_ONGOING_EVENT;
        nManager.notify(1, notification);
    }

}