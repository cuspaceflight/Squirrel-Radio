package uk.ac.cam.cusf.squirrelradio;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.IOException;

import android.content.Context;
import android.util.Log;

public class WavBuilder {

    public static final String TAG = "SquirrelRadio";

    private final String filename;

    private boolean CLOSED = false;
    private boolean ERROR = false;

    BufferedOutputStream bufferedOutput;

    private Context context;
    private AudioFile audioFile;
    
    public WavBuilder(int channels, int rate, int samples, Context context) {

        this.context = context;
        
        filename = System.currentTimeMillis() + ".wav";
        double length = 1.0 * samples / rate;
        audioFile = new AudioFile(filename, length);
        
        deleteAll(); // Delete any existing wav files in internal memory

        try {

            bufferedOutput = new BufferedOutputStream(
                      context.openFileOutput(filename, Context.MODE_PRIVATE));

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
                context.deleteFile(filename);
            } catch (Exception e) {
                // Do nothing...
            }
        }
    }

    public AudioFile getAudioFile() {
        if (CLOSED && !ERROR) {
            return audioFile;
        } else {
            return null;
        }
    }

    public boolean isError() {
        return ERROR;
    }

    public void error() {
        ERROR = true;
    }
    
    private void deleteAll() {
        File dir = context.getFilesDir();
        FileFilter filter = new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                String name = pathname.getAbsolutePath();
                int pos = name.lastIndexOf('.');
                if (pos == -1) {
                    return false;
                } else {
                    String ext = name.substring(pos + 1);
                    if (ext.equalsIgnoreCase("wav"))
                        return true;
                }

                return false;
            }
        };
        File[] files = dir.listFiles(filter);
        for (File file : files) {
            Log.i(TAG, "Deleting " + file.getAbsolutePath());
            file.delete();
        }
    }

}