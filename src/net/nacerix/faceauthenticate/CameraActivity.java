package net.nacerix.faceauthenticate;

import android.support.v7.app.ActionBarActivity;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import android.annotation.SuppressLint;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;

@SuppressLint("NewApi")
@SuppressWarnings("deprecation")
public class CameraActivity extends ActionBarActivity implements CvCameraViewListener2 {
	// A tag for log output.
    private static final String TAG = CameraActivity.class.getSimpleName();
    
    // A key for storing the index of the active camera.
    private static final String STATE_CAMERA_INDEX = "cameraIndex";
    
    // A key for storing the index of the active image size.
    private static final String STATE_IMAGE_SIZE_INDEX = "imageSizeIndex";
    
    // An ID for items in the image size submenu.
	private static final int MENU_GROUP_ID_SIZE = 2;
    
    // The index of the active camera.
    private int mCameraIndex;
    
    // The index of the active image size.
    private int mImageSizeIndex;
    
    // Whether the active camera is front-facing.
    // If so, the camera view should be mirrored.
    private boolean mIsCameraFrontFacing;
    
    // The number of cameras on the device.
    private int mNumCameras;
    
    // The image sizes supported by the active camera.
    private List<Size> mSupportedImageSizes;
    
    // The camera view.
    private CameraBridgeViewBase mCameraView;
    
    // Whether the next camera frame should be saved as a photo.
    private boolean mIsPhotoPending;
    
    // A matrix that is used when saving photos.
    //private Mat mBgr;
    
    // Whether an asynchronous menu action is in progress.
    // If so, menu interaction should be disabled.
    private boolean mIsMenuLocked;
    
    //Rectangle used to focus on the face to recognize.
    private Point mCenterPoint;
    private int mFocusHeight;
    private int mFocusWidth;
    private final Scalar mLineColor = new Scalar(0, 255, 0);

    // The OpenCV loader callback.
    private BaseLoaderCallback mLoaderCallback = 
    		new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(final int status) {
            switch (status) {
            case LoaderCallbackInterface.SUCCESS:
                Log.d(TAG, "OpenCV loaded successfully");
                mCameraView.enableView();
                //mCameraView.enableFpsMeter();
                //mBgr = new Mat();
                break;
            default:
                super.onManagerConnected(status);
                break;
            }
        }
    };
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Log.i(TAG, "called onCreate");
	    super.onCreate(savedInstanceState);
	    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
	    
	    if (savedInstanceState != null) {
            mCameraIndex = savedInstanceState.getInt( STATE_CAMERA_INDEX, 0);
            mImageSizeIndex = savedInstanceState.getInt( STATE_IMAGE_SIZE_INDEX, 0);
        } else {
            mCameraIndex = 0;
            mImageSizeIndex = 0;
        }
        
        final Camera camera;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            CameraInfo cameraInfo = new CameraInfo();
            Camera.getCameraInfo(mCameraIndex, cameraInfo);
            mIsCameraFrontFacing = 
            		(cameraInfo.facing == CameraInfo.CAMERA_FACING_FRONT);
            mNumCameras = Camera.getNumberOfCameras();
            camera = Camera.open(mCameraIndex);
        } else { // pre-Gingerbread
            // Assume there is only 1 camera and it is rear-facing.
            mIsCameraFrontFacing = false;
            mNumCameras = 1;
            camera = Camera.open();
        }
        final Parameters parameters = camera.getParameters();
        camera.release();
        mSupportedImageSizes = parameters.getSupportedPreviewSizes();
        final Size size = mSupportedImageSizes.get(mImageSizeIndex);
        mCenterPoint = new Point( size.width/2, size.height/2);
        mFocusHeight = size.height/4;
        mFocusWidth = size.width/4;
        
        mCameraView = new JavaCameraView(this, mCameraIndex);
        mCameraView.setMaxFrameSize(size.width, size.height);
        mCameraView.setCvCameraViewListener(this);
        setContentView(mCameraView);
	}
	
	public void onSaveInstanceState(Bundle savedInstanceState) {
        // Save the current camera index.
        savedInstanceState.putInt(STATE_CAMERA_INDEX, mCameraIndex);
        
        // Save the current image size index.
        savedInstanceState.putInt(STATE_IMAGE_SIZE_INDEX, mImageSizeIndex);
        
        super.onSaveInstanceState(savedInstanceState);
    }
	
	// Suppress backward incompatibility errors because we provide
    // backward-compatible fallbacks.
    @SuppressLint("NewApi")
    @Override
    public void recreate() {
        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.HONEYCOMB) {
            super.recreate();
        } else {
            finish();
            startActivity(getIntent());
        }
    }
    
    @Override
    public void onPause() {
        if (mCameraView != null) {
            mCameraView.disableView();
        }
        super.onPause();
    }
    
    @Override
    public void onResume() {
        super.onResume();
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0,
                this, mLoaderCallback);
        mIsMenuLocked = false;
    }

    @Override
    public void onDestroy() {
        if (mCameraView != null) {
            mCameraView.disableView();
        }
        super.onDestroy();
    }

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.camera, menu);
		if (mNumCameras < 2) {
            // Remove the option to switch cameras, since there is
            // only 1.
            menu.removeItem(R.id.menu_next_camera);
        }
//        int numSupportedImageSizes = mSupportedImageSizes.size();
//        if (numSupportedImageSizes > 1) {
//            final SubMenu sizeSubMenu = menu.addSubMenu(
//                    R.string.menu_image_size);
//            for (int i = 0; i < numSupportedImageSizes; i++) {
//                final Size size = mSupportedImageSizes.get(i);
//                sizeSubMenu.add(MENU_GROUP_ID_SIZE, i, Menu.NONE,
//                        String.format("%dx%d", size.width,
//                                size.height));
//            }
//        }
        return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		
		if (mIsMenuLocked) {
            return true;
        }
        if (item.getGroupId() == MENU_GROUP_ID_SIZE) {
            mImageSizeIndex = item.getItemId();
            recreate();
            
            return true;
        }
        switch (item.getItemId()) {
	        case R.id.menu_next_camera:
	            mIsMenuLocked = true;
	            
	            // With another camera index, recreate the activity.
	            mCameraIndex++;
	            if (mCameraIndex == mNumCameras) {
	                mCameraIndex = 0;
	            }
	            mImageSizeIndex = 0;
	            recreate();
	            
	            return true;
	        case R.id.menu_authenticate:
	            mIsMenuLocked = true;
	            
	            // Next frame, take the photo.
	            mIsPhotoPending = true;
	            
	            return true;
	        default:
	            return super.onOptionsItemSelected(item);
        }
	}

	@Override
	public void onCameraViewStarted(int width, int height) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onCameraViewStopped() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
		final Mat rgba = inputFrame.rgba();
		
		int x = (int) (mCenterPoint.x - mFocusWidth);
		int y = (int) (mCenterPoint.y - mFocusHeight);
		//int x = (int)mCenterPoint.x;
		//int y = (int)mCenterPoint.y;
		final Rect focusArea = new Rect(x, y, mFocusWidth*2, mFocusHeight*2);
		
		ArrayList<Point> focusPoints = new ArrayList<Point>();
		focusPoints.add(focusArea.tl());
		focusPoints.add(new Point(focusArea.tl().x, focusArea.br().y));
		focusPoints.add(focusArea.br());
		focusPoints.add(new Point(focusArea.br().x, focusArea.tl().y));
		
		Imgproc.line(rgba, focusPoints.get(0), focusPoints.get(1), mLineColor, 1);
		Imgproc.line(rgba, focusPoints.get(1), focusPoints.get(2), mLineColor, 1);
		Imgproc.line(rgba, focusPoints.get(2), focusPoints.get(3), mLineColor, 1);
		Imgproc.line(rgba, focusPoints.get(3), focusPoints.get(0), mLineColor, 1);
        
        if (mIsPhotoPending) {
            mIsPhotoPending = false;
            authenticate(rgba, focusArea);
        }
        
        if (mIsCameraFrontFacing) {
            // Mirror (horizontally flip) the preview.
            Core.flip(rgba, rgba, 1);
        }
        
        return rgba;
	}
	
	private void authenticate(final Mat rgba, final Rect roi) {
		return ;
    }
}
