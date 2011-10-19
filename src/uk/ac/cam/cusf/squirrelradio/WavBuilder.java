package uk.ac.cam.cusf.squirrelradio;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import android.os.Environment;
import android.util.Log;

public class WavBuilder {

    public static final String TAG = "SquirrelRadio";

    private final String DIRECTORY = "SquirrelAudio";
    private final String FILENAME;

    private boolean CLOSED = false;
    private boolean ERROR = false;

    File file;
    BufferedOutputStream bufferedOutput;

    public WavBuilder(int channels, int rate, int samples) {

        FILENAME = System.currentTimeMillis() + ".wav";

        try {
            File exportDir = new File(
                    Environment.getExternalStorageDirectory(), DIRECTORY);
            if (!exportDir.exists()) {
                exportDir.mkdirs();
            }

            file = new File(Environment.getExternalStorageDirectory(),
                    DIRECTORY + "/" + FILENAME);
            if (file.exists()) {
                file.delete();
            }

            bufferedOutput = new BufferedOutputStream(
                    new FileOutputStream(file));

            byte[] header = new byte[44];
            WavHeader.writeHeader(header, channels, rate, 2, samples);

            bufferedOutput.write(header);

        } catch (FileNotFoundException e) {
            Log.e(TAG, "FileNotFoundException", e);
            ERROR = true;
        } catch (IOException e) {
            Log.e(TAG, "IOException", e);
            ERROR = true;
        }

    }

    public BufferedOutputStream getOutputStream() {
        return bufferedOutput;
    }

    public void close() {
        if (!ERROR) {
            try {
                if (bufferedOutput != null)
                    bufferedOutput.close();
                CLOSED = true;
            } catch (IOException e) {
                Log.e(TAG, "IOException", e);
                ERROR = true;
            }
        }

        if (ERROR) {
            try {
                // Try to delete the file
                if (file != null)
                    file.delete();
            } catch (Exception e) {
                // Do nothing...
            }
        }
    }

    public File getWavFile() {
        File wavFile = null;
        if (CLOSED && !ERROR)
            wavFile = file;
        return wavFile;
    }

    public boolean isError() {
        return ERROR;
    }

    public void error() {
        ERROR = true;
    }

}