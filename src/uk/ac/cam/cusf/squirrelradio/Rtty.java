package uk.ac.cam.cusf.squirrelradio;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Formatter;

import android.content.Intent;
import android.location.Location;
import android.os.BatteryManager;
import android.os.SystemClock;
import android.util.Log;

public class Rtty {

    private final static String TAG = "SquirrelRadio";

    public final static String CALL_SIGN = "NEXUS";

    // (CENTRE_FREQ + FREQ_SHIFT/2) / BAUD_RATE must be integer
    public final static int CENTRE_FREQ = 1075;
    public final static int FREQ_SHIFT = 350;
    public final static int BAUD_RATE = 50;

    // 8000Hz, 11025Hz, 44100Hz all work
    public final static int SAMPLE_RATE = 44100;

    // Length (bits) of lock-on tone before messages
    public final static int LOCK_ON = 300;

    // Minimum time (ms) between transmissions
    public final static int MIN_GAP = 5000;

    private final byte[] mark;
    private final byte[] space;

    private Encryptor encryptor = new Encryptor();

    // Initialises the mark and space 16 bit sound arrays
    public Rtty() {

        int markFreq = CENTRE_FREQ + FREQ_SHIFT / 2;
        int spaceFreq = CENTRE_FREQ - FREQ_SHIFT / 2;

        mark = generateTone(markFreq);
        space = generateTone(spaceFreq);

    }

    // Generates a tone of particular frequency (1 bit long at specified baud
    // rate)
    private byte[] generateTone(double freq) {

        int numSamples = SAMPLE_RATE / BAUD_RATE;
        double[] sample = new double[numSamples];
        byte[] output = new byte[2 * numSamples];

        // Create sine-wave sample
        double x = 2 * Math.PI * freq / SAMPLE_RATE;
        for (int i = 0; i < numSamples; i++) {
            sample[i] = Math.sin(x * i);
        }

        // Convert to 16 bit WAV PCM sound array
        int j = 0;
        for (final double dVal : sample) {
            final short val = (short) (dVal * Short.MAX_VALUE);
            output[j++] = (byte) (val & 0x00ff); // Lower byte
            output[j++] = (byte) ((val & 0xff00) >>> 8); // Upper byte
        }

        return output;
    }

    // Takes string and returns RTTY audio sample, complete with start and stop
    // bits
    private File generateWav(String text) {

        boolean[] bits = createBits(text);

        int numSamples = mark.length * (bits.length + LOCK_ON);
        WavBuilder wav = new WavBuilder(1, SAMPLE_RATE, numSamples / 2);

        BufferedOutputStream output = wav.getOutputStream();

        if (!wav.isError()) {
            try {
                // Lock-on bits
                for (int k = 0; k < LOCK_ON; k++) {
                    output.write(mark);
                }

                // Data bits
                for (boolean bit : bits) {
                    byte[] tone = bit ? mark : space;
                    output.write(tone);
                }
            } catch (IOException e) {
                Log.e(TAG, "IOException", e);
                wav.error();
            }
        }

        wav.close();

        return wav.getWavFile();
    }

    // Takes string and returns RS232 (serial) bits
    private boolean[] createBits(String msg) {
        byte[] bytes = msg.getBytes();
        boolean[] bits = new boolean[bytes.length * (Byte.SIZE + 3)];

        int j = 0;

        for (byte b : bytes) {

            bits[j++] = false; // Start bit

            int val = b;
            for (int i = 0; i < 8; i++) {
                // Least-significant bit first
                bits[j++] = ((val & 1) == 0) ? false : true;
                val >>= 1;
            }

            // Stop bits
            bits[j++] = true;
            bits[j++] = true;

        }

        return bits;
    }

    public File createRtty(Location location, Intent battery) {

        Date gpsDate = new Date(location.getTime());
        SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");

        long realtime = SystemClock.elapsedRealtime();

        String msg = CALL_SIGN;
        msg += "," + realtime;
        msg += "," + dateFormat.format(gpsDate);
        msg += "," + String.format("%.6f", location.getLatitude());
        msg += "," + String.format("%.6f", location.getLongitude());
        msg += "," + String.format("%.0f", location.getAltitude());

        /*
         * if (battery != null) { int level =
         * battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1); int scale =
         * battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1); int temperature
         * = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1); int
         * health = battery.getIntExtra(BatteryManager.EXTRA_HEALTH,
         * BatteryManager.BATTERY_HEALTH_UNKNOWN);
         * 
         * String secure = String.format("%.1f",(100.0*level)/scale); secure +=
         * ","+String.valueOf(temperature); secure +=
         * ","+String.valueOf(health);
         * 
         * encryptor.setKeyPosition(realtime); msg +=
         * ","+encryptor.encrypt(secure); } else { msg += ","; }
         */

        msg += "*" + crc(msg);
        msg += "\r\n";

        msg = "$$" + msg;

        Log.i(TAG, msg);

        return generateWav(msg);

    }

    private String crc(String s) {
        char x = 0xFFFF;

        char[] chars = s.toCharArray();

        for (int i = 0; i < chars.length; i++) {
            x = (char) (x ^ (chars[i] << 8));

            for (int j = 0; j < 8; j++) {
                if ((x & 0x8000) > 0) {
                    x = (char) ((x << 1) ^ 0x1021);
                } else {
                    x <<= 1;
                }
            }

        }

        Formatter formatter = new Formatter();
        formatter.format("%04X", (int) x);

        return formatter.out().toString();
    }

}
