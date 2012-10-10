package uk.ac.cam.cusf.squirrelradio;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Random;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Bitmap.CompressFormat;
import android.media.ExifInterface;
import android.os.Environment;
import android.util.Log;

public class BitmapCreator {

    public final static String TAG = "SquirrelRadio";
    
    private final static String CAMERA_PACKAGE = "uk.ac.cam.cusf.squirrelcamera";
    private final static String FILENAME = "sstv.jpg";

    private String hash;
    
    private Context context;
    
    public BitmapCreator(Context context) {
        this.context = context;
    }
    
    private File lastPhoto() {
        try {
            Context ctx = context.createPackageContext(CAMERA_PACKAGE, 0);
            try {
                FileInputStream fis = ctx.openFileInput(FILENAME);
                if (fis == null) return null;
                String hash = md5(fis);
                if (this.hash != null && this.hash.equals(hash)) {
                    Log.i(TAG, "No new SSTV file to transmit");
                    return null;
                } else {
                    this.hash = hash;
                    File directory = ctx.getFilesDir();
                    File photo = new File(directory, FILENAME);
                    if (photo.exists()) {
                        return photo;
                    } else {
                        return null;
                    }
                }
            } catch (FileNotFoundException e) {
                Log.e(TAG, "FileNotFoundException " + FILENAME, e);
                return null;
            }
            
        } catch (NameNotFoundException e) {
            Log.e(TAG, "NameNotFoundException", e);
            return null;
        }
        
    }
    
    private File stockPhoto() {
        File directory = new File(Environment.getExternalStorageDirectory(),
                "stock");
        FileFilter filter = new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                String name = pathname.getAbsolutePath();
                int pos = name.lastIndexOf('.');
                if (pos == -1) {
                    return false;
                } else {
                    String ext = name.substring(pos + 1);
                    if (ext.equalsIgnoreCase("jpg"))
                        return true;
                }

                return false;
            }
        };
        File[] files = directory.listFiles(filter);
        if (files == null || files.length == 0)
            return null;
        Arrays.sort(files, new Comparator<File>() {
            public int compare(File f1, File f2) {
                return Long.valueOf(f2.lastModified()).compareTo(
                        f1.lastModified());
            }
        });
        
        Random random = new Random();
        return files[random.nextInt(files.length)];
        
    }

    public Bitmap generate() {

        File photo = lastPhoto();
        
        if (photo == null) {
            // Try a stock photo for demo purposes
            photo = stockPhoto();
        }
        
        if (photo == null) return null;

        Bitmap bitmap = Bitmap.createBitmap(320, 256, Bitmap.Config.ARGB_8888);

        // Header

        for (int x = 0; x < 240; x++) {
            int lum = (int) ((255.0 * x) / 240);
            for (int y = 0; y < 16; y++) {
                bitmap.setPixel(x, y, Color.rgb(lum, lum, lum));
            }
        }

        for (int i = 0; i <= 7; i++) {
            for (int x = 240 + 10 * i; x < 250 + 10 * i; x++) {
                for (int y = 0; y < 16; y++) {
                    bitmap.setPixel(x, y, Color.rgb(255 * ((i >> 2) & 1),
                            255 * ((i >> 1) & 1), 255 * (i & 1)));
                }
            }
        }

        // Altitude info

        try {
            ExifInterface exif = new ExifInterface(photo.getAbsolutePath());
            double altitude = exif.getAltitude(0);

            double scale = (altitude / 50000);
            if (scale > 1) scale = 1;
            
            for (int x = 120; x < 240; x = x + (int)((240 - 120) * 0.2)) {
                for (int y = 12; y < 16; y++) {
                    bitmap.setPixel(x, y, Color.BLACK);
                    bitmap.setPixel(x + 1, y, Color.BLACK);
                }
            }
            
            for (int x = 120; x < 120 + (240 - 120) * scale; x++) {
                for (int y = 4; y < 12; y++) {
                    bitmap.setPixel(x, y, Color.BLACK);
                }
            }
            
        } catch (IOException e) {
            Log.e(TAG, "IOException in ExifInterface");
        }

        // Photo

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 4;

        Bitmap s = BitmapFactory.decodeFile(photo.getAbsolutePath(),
                options);
        
        if (s == null) {
            Log.e(TAG, "decodeFile returned null");
            return null;
        }
        Canvas canvas = new Canvas(bitmap);

        Matrix matrix = new Matrix();
        matrix.preScale(320f / s.getWidth(), 240f / s.getHeight());
        matrix.postTranslate(0, 16);
        canvas.drawBitmap(s, matrix, null);
        
        saveBitmap(bitmap, "generated_" + photo.getName());
        return bitmap;

    }

    public void saveBitmap(Bitmap bitmap, String filename) {
        context.deleteFile(filename);
        try {
            OutputStream output = context.openFileOutput(filename, 3);
            bitmap.compress(CompressFormat.JPEG, 85, output);
            output.close();
        } catch (IOException e) {
            Log.e(TAG, "IOException in saveBitmap", e);
        }

        File directory = context.getFilesDir();
        File sstv = new File(directory, filename);
        
        Intent intent = new Intent();
        intent.setAction("uk.ac.cam.cusf.intent.Tweet");
        intent.putExtra("message", "SSTV image");
        intent.putExtra("path", sstv.getPath());
        context.sendBroadcast(intent);
        
    }
    
    private String md5(FileInputStream fis) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e1) {
            Log.e(TAG, "NoSuchAlgorithmException in md5()", e1);
            return null;
        }

        byte[] buf = new byte[8192];
        int len;
        try {
            while ((len = fis.read(buf)) != -1) {
                md.update(buf, 0, len);
            }
            fis.close();
        } catch (IOException e) {
            Log.e(TAG, "IOException in md5()", e);
            return null;
        }

        byte[] digest = md.digest();
        
        BigInteger bi = new BigInteger(1, digest);
        String hex = String.format("%0" + (digest.length << 1) + "X", bi);
        Log.i(TAG, "MD5: " + hex);
        
        return hex;
    }

}
