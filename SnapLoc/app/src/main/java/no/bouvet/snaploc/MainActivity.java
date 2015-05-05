package no.bouvet.snaploc;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;


public class MainActivity extends ActionBarActivity {

    private static final int REQUEST_IMAGE_CAPTURE = 1;

    private static final String BITMAP_STORAGE_KEY = "viewbitmap";
    private static final String PHOTO_PATH_STORAGE_KEY = "photopath";

    private String mCurrentPhotoPath;
    private Bitmap mCurrentPhoto;

    private ImageView mImageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mImageView = (ImageView) findViewById(R.id.thumb);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    // Some lifecycle callbacks so that the image can survive orientation change
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString(PHOTO_PATH_STORAGE_KEY, mCurrentPhotoPath);
        outState.putParcelable(BITMAP_STORAGE_KEY, mCurrentPhoto);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mCurrentPhoto = savedInstanceState.getParcelable(BITMAP_STORAGE_KEY);
        mCurrentPhotoPath = savedInstanceState.getString(PHOTO_PATH_STORAGE_KEY);
        mImageView.setImageBitmap(mCurrentPhoto);
    }

    // Launch
    public void onLocate(View view) {
        Intent intent = new Intent(this, MapsActivity.class);

        try {
            if (mCurrentPhotoPath == null) {
                fakePhoto();
            }

            ExifInterface exif = new ExifInterface(mCurrentPhotoPath);
            Location location = ExifUtils.getLocation(exif);
            intent.putExtra(MapsActivity.LOCATION, location);

        } catch (IOException e) {
            e.printStackTrace();
        }

        startActivity(intent);
    }


    // Image capture
    public void onCapture(View view) {
        if (Environment.getExternalStorageState().equals(android.os.Environment.MEDIA_MOUNTED)) {
            dispatchTakePictureIntent();
        } else {
            // Fake photo here, in case of emulator or device without SD card...
            fakePhoto();

            // Fake callback, to continue normal flow
            onActivityResult(REQUEST_IMAGE_CAPTURE, RESULT_OK, null);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            updateThumbnail();

            try {
                ExifInterface exif = new ExifInterface(mCurrentPhotoPath);
                Location location = ExifUtils.getLocation(exif);

                if (location == null) {
                    Future<Location> newLocation = new LocationHelper(getApplicationContext()).getLastBestLocation(100, 100);

                    try {
                        location = newLocation.get();
                    } catch (ExecutionException e) {
                        e.printStackTrace();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                    ExifUtils.setLocation(exif, location);
                    exif.saveAttributes();
                }
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            try {
                File file = createImageFile();
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(file));

                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            } catch (IOException e) {
                e.printStackTrace();

                Toast.makeText(getApplicationContext(), "Could not create image file...", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void updateThumbnail() {
        // Get the dimensions of the View
        int targetW = mImageView.getWidth() != 0 ? mImageView.getWidth() : 800;
        int targetH = mImageView.getHeight() != 0 ? mImageView.getHeight() : 600;

        // Get the dimensions of the bitmap
        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        bmOptions.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(mCurrentPhotoPath, bmOptions);

        int photoW = bmOptions.outWidth;
        int photoH = bmOptions.outHeight;

        // Determine how much to scale down the image
        int scaleFactor = Math.min(photoW / targetW, photoH / targetH);

        // Decode the image file into a Bitmap sized to fill the View
        bmOptions.inJustDecodeBounds = false;
        bmOptions.inSampleSize = scaleFactor;
        bmOptions.inPurgeable = true;

        Bitmap bitmap = BitmapFactory.decodeFile(mCurrentPhotoPath, bmOptions);

        mCurrentPhoto = bitmap;
        mImageView.setImageBitmap(bitmap);
    }

    private File createImageFile() throws IOException {
        // Create an image file name
//        File storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File storageDir = getApplicationContext().getExternalFilesDir(null);

        File image = File.createTempFile(
                "test-",        /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        // Save a file: path for use with ACTION_VIEW intents
        mCurrentPhotoPath = image.getAbsolutePath();

        return image;
    }

    private void fakePhoto() {
        // Try-with-resources requires more recent SDK...
        try {
            InputStream in = null;

            try {
                in = getAssets().open("IMG_3460.JPG");

                OutputStream out = null;

                try {
                    File fakeFile = createImageFile();
                    out = new FileOutputStream(fakeFile);
                    byte[] buffer = new byte[1024];
                    int count;
                    while ((count = in.read(buffer)) > 0) {
                        out.write(buffer, 0, count);
                    }
                } finally {
                    if (out != null) {
                        out.close();
                    }
                }
            } finally {
                if (in != null) {
                    in.close();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
