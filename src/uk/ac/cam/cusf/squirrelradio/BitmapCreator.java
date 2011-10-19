package uk.ac.cam.cusf.squirrelradio;

import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Comparator;

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

    private final String DIRECTORY = "SquirrelSSTV";

    private String lastName = "";

    private File lastPhoto() {
        File directory = new File(Environment.getExternalStorageDirectory(),
                "SquirrelCamera");
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
        return files[0];
        /*
         * Random random = new Random(); return
         * files[random.nextInt(files.length)];
         */
    }

    private boolean altitudeBit(double altitude, int i) {
        int num = (int) (altitude / 100);
        return ((num >> i) & 1) == 1;
    }

    public Bitmap generate() {

        File lastPhoto = lastPhoto();
        if (lastPhoto == null || lastName.equals(lastPhoto.getName())) {
            return null;
        }
        // Don't need this for stock images
        lastName = lastPhoto.getName();

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
            ExifInterface exif = new ExifInterface(lastPhoto.getAbsolutePath());
            // double altitude = exif.getAltitude(0);
            double altitude = exif.getAttributeDouble("GPSAltitude", 0);
            // This doesn't work - need to work out own parsing for altitude
            // (copy source from 2.3)

            for (int i = 0; i < 9; i++) {
                if (altitudeBit(altitude, i)) {
                    for (int x = 230 - i * 10; x < 240 - i * 10; x++) {
                        for (int y = 12; y < 16; y++) {
                            bitmap.setPixel(x, y, Color.BLACK);
                        }
                    }

                }
            }
        } catch (IOException e) {
            Log.e(TAG, "IOException in ExifInterface");
        }

        // Photo

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 4;

        Bitmap s = BitmapFactory.decodeFile(lastPhoto.getAbsolutePath(),
                options);
        if (s == null)
            return null;
        Canvas canvas = new Canvas(bitmap);

        Matrix matrix = new Matrix();
        matrix.preScale(320f / s.getWidth(), 240f / s.getHeight());
        matrix.postTranslate(0, 16);
        canvas.drawBitmap(s, matrix, null);

        saveBitmap(bitmap, lastPhoto.getName());
        return bitmap;

    }

    public void saveBitmap(Bitmap bitmap, String filename) {
        File exportDir = new File(Environment.getExternalStorageDirectory(),
                DIRECTORY);
        if (!exportDir.exists()) {
            exportDir.mkdirs();
        }
        File bitmapFile = new File(Environment.getExternalStorageDirectory(),
                DIRECTORY + "/" + filename);
        if (bitmapFile.exists())
            return;
        try {
            OutputStream output = new FileOutputStream(bitmapFile);
            bitmap.compress(CompressFormat.JPEG, 85, output);
            output.close();
        } catch (IOException e) {
            Log.e(TAG, "IOException in saveBitmap", e);
        }
    }

}
