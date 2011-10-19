package uk.ac.cam.cusf.squirrelradio;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

public class Sstv {

	private final static String TAG = "SquirrelRadio";
	
	// 8000Hz, 11025Hz, 44100Hz all work
	public final static int SAMPLE_RATE = 44100;
	
	// Frequencies in Hz
	public final static int BLACK_FREQ = 1500;
	public final static int WHITE_FREQ = 2300;
	public final static int SYNC_FREQ = 1200;
	public final static int GAP_FREQ = 1500;
	
	// Times in microseconds
	public final static int SYNC_TIME = 4862;
	public final static int GAP_TIME = 572;
	public final static int PIXEL_TIME = 456; // 572, or 456?
	
	public final static int WIDTH = 320;
	public final static int HEIGHT = 256;
	
	private double phase = 0;

	BitmapCreator creator;
	
	public Sstv() {
		super();
		creator = new BitmapCreator();
	}
	
	public File generateImage() {
		
		Bitmap image = creator.generate(); // Returns a 320x256 Bitmap
		
		if (image == null) {
			// Either the JPG couldn't be opened, or there isn't a new one in the directory since last time
			Log.i(TAG, "BitmapCreator returned null");
			return null;
		} else if (image.getHeight() != HEIGHT || image.getWidth() != WIDTH) {
			Log.e(TAG, "BitmapCreator returned Bitmap of invalid dimensions");
			return null;
		}
		
		phase = 0;
		
		WavBuilder wav = new WavBuilder(1, SAMPLE_RATE, imageLength()/2);
		if (!wav.isError()) {
			try {
				writeBitmap(image, wav);
			} catch (IOException e) {
				Log.e(TAG, "IOException", e);
				wav.error();
			}
		}
		
		wav.close();
		image = null; // Free up memory associated with bitmap
		
		return wav.getWavFile(); // Returns null is wav.isError() == true
	}
	
	private void writeBitmap(Bitmap bitmap, WavBuilder wav) throws IOException {
		
		BufferedOutputStream output = wav.getOutputStream();
		
		for (int y = 0; y < HEIGHT; y++) {
			
			syncSample(output);
			gapSample(output);
			
			for (int x = 0; x < WIDTH; x++) {
				pixelSample(getGreenPixel(bitmap, x,y), output);
			}
			
			gapSample(output);
	
			
			for (int x = 0; x < WIDTH; x++) {
				pixelSample(getBluePixel(bitmap, x,y), output);
			}
			
			gapSample(output);
			
			for (int x = 0; x < WIDTH; x++) {
				pixelSample(getRedPixel(bitmap, x,y), output);
			}		
			
			gapSample(output);
			
		}

	}
	
	private int imageLength() {
		return HEIGHT * rowLength();
	}
	
	private int rowLength() {
		
		int syncLength = 2 * (int)(SAMPLE_RATE * SYNC_TIME * Math.pow(10, -6));
		int gapLength = 4 * 2 * (int)(SAMPLE_RATE * GAP_TIME * Math.pow(10, -6));
		int pixelLength = WIDTH * 3 * 2 * (int)(SAMPLE_RATE * PIXEL_TIME * Math.pow(10, -6));
		
		int rowLength = syncLength + gapLength + pixelLength;
		
		return rowLength;
	}
	
	private void gapSample(BufferedOutputStream output) throws IOException {
		generateSample(GAP_FREQ, GAP_TIME, output);
	}
	
	private void syncSample(BufferedOutputStream output) throws IOException {
		generateSample(SYNC_FREQ, SYNC_TIME, output);
	}
	
	private void pixelSample(int lum, BufferedOutputStream output) throws IOException {
		generateSample(pixelFreq(lum), PIXEL_TIME, output);
	}

	private void generateSample(int freq, int len, BufferedOutputStream output) throws IOException {
		
		int numSamples = (int) (SAMPLE_RATE * len * Math.pow(10, -6));
		
		short val;
		
		double x = 2*Math.PI*freq/SAMPLE_RATE;
        for (int i = 0; i < numSamples; i++) {
            val = (short) (Math.sin(x*i+phase) * Short.MAX_VALUE);
            // Convert to 16 bit WAV PCM sound array
            output.write((byte) (val & 0x00ff)); // Lower byte
            output.write((byte) ((val & 0xff00) >>> 8)); // Upper byte    
        }
        
        phase = x*numSamples+phase;
        
        while (phase > 2*Math.PI) {
        	phase -= 2*Math.PI;
        }
        
	}
	
	private int pixelFreq(int lum) {
		return (int)(BLACK_FREQ + (WHITE_FREQ - BLACK_FREQ)*(1.0*lum/255));
	}
	
	private int getRedPixel(Bitmap bitmap, int x, int y) {
		return Color.red(bitmap.getPixel(x, y));
	}
	
	private int getGreenPixel(Bitmap bitmap, int x, int y) {
		return Color.green(bitmap.getPixel(x, y));
	}
	
	private int getBluePixel(Bitmap bitmap, int x, int y) {
		return Color.blue(bitmap.getPixel(x, y));
	}
	
}
