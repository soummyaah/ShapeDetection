package com.example.sd.shapeidentifier;

import android.content.Intent;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.nfc.Tag;
import android.opengl.Matrix;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private int PICK_IMAGE_REQUEST = 1;
    private String TAG = "ShapeIdentifier.MainActivity";
    TextView textTargetUri;
    ImageView targetImage;
    Mat matTargetImage;
    List<MatOfPoint> contours;
    List<Boolean> isMarked;
    Mat hierarchy;
    int x,y;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        textTargetUri = (TextView)findViewById(R.id.targeturi);
        targetImage = (ImageView)findViewById(R.id.imageView);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent();
                intent.setType("image/*");
                intent.setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(Intent.createChooser(intent, "Select Picture"), PICK_IMAGE_REQUEST);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {

            Uri uri = data.getData();
            Log.d(TAG, uri.toString());

            Toast.makeText(getApplicationContext(),
                    "ImageView: " + targetImage.getWidth() + " x " + targetImage.getHeight(),
                    Toast.LENGTH_LONG).show();
            Log.d(TAG, "ImageView: " + targetImage.getWidth() + " x " + targetImage.getHeight());
            Bitmap bitmap;
            bitmap = decodeSampledBitmapFromUri(
                    uri,
                    targetImage.getWidth(), targetImage.getHeight());

            /*if(bitmap == null){
                Toast.makeText(getApplicationContext(), "the image data could not be decoded", Toast.LENGTH_LONG).show();

            }else{
                Toast.makeText(getApplicationContext(),
                        "Decoded Bitmap: " + bitmap.getWidth() + " x " + bitmap.getHeight(),
                        Toast.LENGTH_LONG).show();
                targetImage.setImageBitmap(bitmap);
            }*/

            matTargetImage = new Mat (bitmap.getWidth(), bitmap.getHeight(), CvType.CV_8UC1);
            Utils.bitmapToMat(bitmap, matTargetImage);
            Imgproc.cvtColor(matTargetImage, matTargetImage, Imgproc.COLOR_RGB2GRAY);
//            Imgproc.threshold(matTargetImage, matTargetImage, 128, 255, Imgproc.THRESH_BINARY);
            Imgproc.adaptiveThreshold(matTargetImage, matTargetImage, 255, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY, 11, 2);
            Imgproc.erode(matTargetImage, matTargetImage, new Mat());
//            Imgproc.dilate(matTargetImage, matTargetImage, new Mat());
            contours = new ArrayList<MatOfPoint>();
            hierarchy = new Mat();
            Imgproc.findContours(matTargetImage, contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);
            Imgproc.cvtColor(matTargetImage, matTargetImage, Imgproc.COLOR_GRAY2BGR);
            Collections.sort(contours, new CustomComparator());
            isMarked = new ArrayList<>();
            for(int contouridx = 0; contouridx < contours.size(); contouridx++) {
                isMarked.add(false);
            }
            for(int contouridx = 0; contouridx < contours.size(); contouridx++) {
                if(contours.get(contouridx).size().area() > 5) {
                    Imgproc.drawContours(matTargetImage, contours, contouridx, new Scalar(255, 0, 0), 2);
                }
            }

            hierarchy.release();
            Log.d(TAG, " " + contours.size());
            Log.d(TAG, contours.toString());
            Bitmap trial = Bitmap.createBitmap(matTargetImage.cols(), matTargetImage.rows(),Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(matTargetImage, trial);
            targetImage.setImageBitmap(trial);

            targetImage.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {

                        // Get touched location according to image.
                        float eventX = event.getX();
                        float eventY = event.getY();
                        float[] eventXY = new float[]{eventX, eventY};
                        android.graphics.Matrix invertMatrix = new android.graphics.Matrix();
                        ((ImageView) targetImage).getImageMatrix().invert(invertMatrix);
                        invertMatrix.mapPoints(eventXY);

                        int x = Integer.valueOf((int) eventXY[0]);
                        int y = Integer.valueOf((int) eventXY[1]);

                        Log.d(TAG, "touched position: "
                                + String.valueOf(eventX) + " / "
                                + String.valueOf(eventY));
                        Log.d(TAG, "touched position: "
                                + String.valueOf(x) + " / "
                                + String.valueOf(y));

                        Drawable imgDrawable = ((ImageView) targetImage).getDrawable();
                        Bitmap bitmap = ((BitmapDrawable) imgDrawable).getBitmap();
                        Log.d(TAG,
                                "drawable size: "
                                        + String.valueOf(bitmap.getWidth()) + " / "
                                        + String.valueOf(bitmap.getHeight()));
                        //Limit x, y range within bitmap
                        if (x < 0) {
                            x = 0;
                        } else if (x > bitmap.getWidth() - 1) {
                            x = bitmap.getWidth() - 1;
                        }

                        if (y < 0) {
                            y = 0;
                        } else if (y > bitmap.getHeight() - 1) {
                            y = bitmap.getHeight() - 1;
                        }
                        // Find minimum contour, color it and mark it.
                        org.opencv.core.Point p = new org.opencv.core.Point(x, y);
                        for (int contouridx = 0; contouridx < contours.size(); contouridx++) {
                            if (isMarked.get(contouridx) == false) {
                                MatOfPoint2f m = new MatOfPoint2f(contours.get(contouridx).toArray());
                                double r = Imgproc.pointPolygonTest(m, p, false);
                                if (r == 1) {
                                    Imgproc.drawContours(matTargetImage, contours, contouridx, new Scalar(0, 255, 0), 2);
                                    isMarked.set(contouridx, true);
                                    break;
                                }
                            }
                        }
                        Bitmap trial = Bitmap.createBitmap(matTargetImage.cols(), matTargetImage.rows(), Bitmap.Config.ARGB_8888);
                        Utils.matToBitmap(matTargetImage, trial);
                        targetImage.setImageBitmap(trial);
                    }
                    return true;
                }

//                    int startX = 0, startY = 0;
            });
        }
    }

    public class CustomComparator implements Comparator<MatOfPoint> {
        @Override
        public int compare(MatOfPoint o1, MatOfPoint o2) {
            Rect ra = Imgproc.boundingRect(o1);
            Rect rb = Imgproc.boundingRect(o2);
            if(ra.area()>rb.area()) {
                return 1;
            } else if(ra.area()< rb.area()) {
                return -1;
            } else {
                if(ra.x > rb.x) {
                    return 1;
                } else if(ra.x < rb.x) {
                    return -1;
                } else {
                    return 0;
                }
            }
        }
    }

    public Bitmap decodeSampledBitmapFromUri(Uri uri, int reqWidth, int reqHeight) {

        Bitmap bm = null;

        try{
            // First decode with inJustDecodeBounds=true to check dimensions
            final BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(getContentResolver().openInputStream(uri), null, options);

            // Calculate inSampleSize
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

            // Decode bitmap with inSampleSize set
            options.inJustDecodeBounds = false;
            bm = BitmapFactory.decodeStream(getContentResolver().openInputStream(uri), null, options);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            Toast.makeText(getApplicationContext(), e.toString(), Toast.LENGTH_LONG).show();
        }

        return bm;
    }

    public int calculateInSampleSize(
            BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            if (width > height) {
                inSampleSize = Math.round((float)height / (float)reqHeight);
            } else {
                inSampleSize = Math.round((float)width / (float)reqWidth);
            }
        }
        return inSampleSize;
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

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");
//                    imageMat=new Mat();
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    public void onResume()
    {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }
}
