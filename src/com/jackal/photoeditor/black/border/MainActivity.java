package com.jackal.photoeditor.black.border;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.Toast;

public class MainActivity extends Activity implements OnClickListener {
	private static final int SELECT_PHOTO = 100;
	private static final int DOWN_SAMPLE_BOUNDARY = 1536;
	private static final String TAG = "Photo_Border";
	private static long gMaxBoundary = -1;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main_layout);

		// Get the max heap size to get the max boundary to avoid OutOfMemory exception.
		long maxMemory = Runtime.getRuntime().maxMemory();
		gMaxBoundary = (long) Math.pow((maxMemory / 4), 0.5);
		Log.d(TAG, "The maxMemory: " + maxMemory + ", maxBoundary: " + gMaxBoundary);

		Button button = (Button) findViewById(R.id.startButton);
		button.setOnClickListener(this);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main_layout, menu);
		return true;
	}

	@Override
	public void onClick(View v) {
		Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
		photoPickerIntent.setType("image/*");
		startActivityForResult(photoPickerIntent, SELECT_PHOTO);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode,
			Intent imageReturnedIntent) {
		super.onActivityResult(requestCode, resultCode, imageReturnedIntent);

		switch (requestCode) {
		case SELECT_PHOTO:
			if (resultCode == RESULT_OK) {
				Uri selectedImageUri = imageReturnedIntent.getData();

				if (selectedImageUri != null) {
					new AsyncBitmapBuilder().execute(selectedImageUri);
					Button button = (Button) findViewById(R.id.startButton);
					button.setEnabled(false);
				}
			}
		}
	}

	private class AsyncBitmapBuilder extends AsyncTask<Uri, Integer, Boolean> {

		@Override
		protected void onPostExecute(Boolean result) {
			if (result) {
				Toast.makeText(getApplicationContext(), R.string.toastDone, Toast.LENGTH_SHORT).show();
			} else {
				Toast.makeText(getApplicationContext(), R.string.toastFail, Toast.LENGTH_SHORT).show();
			}
			Button button = (Button) findViewById(R.id.startButton);
			button.setEnabled(true);

			super.onPostExecute(result);
		}

		@Override
		protected Boolean doInBackground(Uri... params) {
			String selectedImagePath = getPath(params[0]);

			if (selectedImagePath == null) { return false; }

			Log.d(TAG, "File Uri: " + params[0].toString() + ", Path: " + selectedImagePath);

			// Just decode image size
			BitmapFactory.Options o = new BitmapFactory.Options();
			o.inJustDecodeBounds = true;
			BitmapFactory.decodeFile(selectedImagePath, o);

			// Get the boundary
			int width_tmp = o.outWidth, height_tmp = o.outHeight;
			Log.d(TAG, "[INFO] width: " + width_tmp + ", height: " + height_tmp);
			if (width_tmp == height_tmp) {return false; } // do nothing if the photo is already square

			int boundary = width_tmp > height_tmp ? width_tmp : height_tmp;
			Log.d(TAG, "The boundary of the photo: " + boundary);

			if (boundary < 1) { return false; }

			// set inPurgeable to true hence they can be purged if the system needs to reclaim memory.
			o = new BitmapFactory.Options();
			o.inPurgeable = true;
			o.inInputShareable = true;
			o.inSampleSize = 1;

			// If the boundary is bigger than the max boundary, down sampling the source bitmap
			while (boundary > gMaxBoundary || boundary > DOWN_SAMPLE_BOUNDARY) {
				o.inSampleSize *= 2;
				boundary /= 2;
			}
			Log.d(TAG, "The new boundary of the photo: " + boundary);

			if (o.inSampleSize >= 2) {
				width_tmp /= o.inSampleSize;
				height_tmp /= o.inSampleSize;
			}

			// reflection to set the hidden variable for allocating memory to native not heap memory.
			try { BitmapFactory.Options.class.getField("inNativeAlloc").setBoolean(o, true); }
			catch (Exception e) { e.printStackTrace(); }

			Bitmap srcBitmap = BitmapFactory.decodeFile(selectedImagePath, o);

			if (srcBitmap == null) {
				Log.d(TAG, "The src Bitmap is NULL");
				return false;
			}

			// Create a 1:1 size blank bitmap.
			Bitmap bitmap = Bitmap.createBitmap(boundary, boundary, Config.ARGB_8888);
			Canvas canvas = new Canvas(bitmap);
			Paint paint = new Paint();
			// fill out the bitmap with black and put the source bitmap at the center
			RadioButton rBlack = (RadioButton) findViewById(R.id.radioBlack);
			if (rBlack.isChecked()) { paint.setColor(Color.BLACK); }
			else { paint.setColor(Color.WHITE); }

			canvas.drawRect(new Rect(0, 0, boundary, boundary), paint);
			canvas.drawBitmap(srcBitmap, ((boundary - width_tmp) / 2), ((boundary - height_tmp) / 2), paint);
			canvas.save(Canvas.ALL_SAVE_FLAG);
			canvas.restore();
			srcBitmap.recycle();

			// Create the Black_Border folder in the sdcard if need and the file name is the timestamp of current time
			File photoBorderDirectory = new File(Environment.getExternalStorageDirectory().getPath() + "/Photo_Border/");
			photoBorderDirectory.mkdirs();
			SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
			String currentDateandTime = sdf.format(new Date());
			File myDrawFile = new File(photoBorderDirectory, currentDateandTime + ".jpg");

			FileOutputStream fos = null;

			try {
				Log.d(TAG, "File will be saved at " + myDrawFile.getPath());
				fos = new FileOutputStream(myDrawFile);
				bitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos);
				fos.flush();
				fos.close();

				// Let media scanner scan this file in order to show up in album.
				Intent i = new Intent();
				i.setAction("android.intent.action.MEDIA_SCANNER_SCAN_FILE");
				i.setData(Uri.fromFile(myDrawFile));
				sendBroadcast(i);
			} catch (Exception e) { e.printStackTrace(); }

			bitmap.recycle();
			System.gc();

			return true;
		}

		private String getPath(Uri uri) {
			String[] projection = { MediaStore.Images.Media.DATA };
			String resultPath = null;
			Cursor cursor = null;
			cursor = getContentResolver().query(uri, projection, null, null, null);
			if (cursor != null) {
				int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
				cursor.moveToFirst();
				resultPath = cursor.getString(column_index);
			}
			cursor.close();
			return resultPath;
		}
	}
}
