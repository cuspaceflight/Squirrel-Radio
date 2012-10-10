package uk.ac.cam.cusf.squirrelradio;

import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;

import android.content.Context;
import android.util.Log;

public class AudioFile {
    
    public final static String TAG = "AudioFile";

    private String filename;
    private double length;
    
    public AudioFile(String filename, double length) {
        this.filename = filename;
        this.length = length;
    }
    
    public FileDescriptor getFileDescriptor(Context context) {
        try {
            return context.openFileInput(filename).getFD();
        } catch (FileNotFoundException e) {
            Log.e(TAG, "FileNotFoundException in getFileDescriptor()", e);
            return null;
        } catch (IOException e) {
            Log.e(TAG, "IOException in getFileDescriptor()", e);
            return null;
        }
    }
    
    public String getFilename() {
        return filename;
    }
    
    public double getLength() {
        return length;
    }
    
    public boolean delete(Context context) {
        return context.deleteFile(filename);
    }
    
    
}
