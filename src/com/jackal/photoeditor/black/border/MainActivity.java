package com.jackal.photoeditor.black.border;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.jackal.photoeditor.black.bounder.R;

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
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;

//import android.view.Menu;

public class MainActivity extends Activity implements OnClickListener {
	private static final int SELECT_PHOTO = 100;
	private static final String TAG = "Black_Border";

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main_layout);

		Button button = (Button) findViewById(R.id.startButton);
		button.setOnClickListener(this);
	}

	@Override
	public void onClick(View v) {
		Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
		photoPickerIntent.setType("image/*");
		startActivityForResult(photoPickerIntent, SELECT_PHOTO);
	}

	// @Override
	// public boolean onCreateOptionsMenu(Menu menu) {
	// getMenuInflater().inflate(R.menu.main_layout, menu);
	// return true;
	// }

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
			int boundary = width_tmp > height_tmp ? width_tmp : height_tmp;
			boolean landScape = true;
			if (boundary == height_tmp) { landScape = false; }

			Log.d(TAG, "The boundary of the photo: " + boundary);

			if (boundary < 1) { return false; }

			o = new BitmapFactory.Options();
			o.inPurgeable = true;

			try { BitmapFactory.Options.class.getField("inNativeAlloc").setBoolean(o, true); }
			catch (Exception e) { e.printStackTrace(); }

			Bitmap srcBitmap = BitmapFactory.decodeFile(selectedImagePath, o);

			if (srcBitmap == null) {
				Log.d(TAG, "The src Bitmap is NULL");
				return false;
			}

			Bitmap bitmap = Bitmap.createBitmap(boundary, boundary, Config.ARGB_8888);
			Canvas canvas = new Canvas(bitmap);
			Paint paint = new Paint();
			paint.setColor(Color.BLACK);
			canvas.drawRect(new Rect(0, 0, boundary, boundary), paint);
			if (landScape) { canvas.drawBitmap(srcBitmap, 0, (boundary - height_tmp) / 2, paint); }
			else { canvas.drawBitmap(srcBitmap, (boundary - width_tmp) / 2, 0, paint); }

			canvas.save(Canvas.ALL_SAVE_FLAG);
			canvas.restore();

			SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
			String currentDateandTime = sdf.format(new Date());

			File myDrawFile = new File(Environment.getExternalStorageDirectory().getPath() + "/Black_Border_" + currentDateandTime + ".jpg");
			FileOutputStream fos = null;

			try {
				fos = new FileOutputStream(myDrawFile);
				bitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos);
				fos.flush();
				fos.close();
			} catch (Exception e) { e.printStackTrace(); }

			srcBitmap.recycle();
			bitmap.recycle();

			return true;
		}

		private String getPath(Uri uri) {
			String[] projection = { MediaStore.Images.Media.DATA };
			String resultPath = null;
			Cursor cursor = managedQuery(uri, projection, null, null, null);
			if (cursor != null) {
				int column_index = cursor
						.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
				cursor.moveToFirst();
				resultPath = cursor.getString(column_index);
				cursor.close();
				cursor = null;
			}

			return resultPath;
		}
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
}
