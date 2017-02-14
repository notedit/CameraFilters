package dot.cc.camerafilters;

/**
 * Created by xiang on 14/02/2017.
 */


import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.Toast;

import cc.dot.camerafilters.base.FilterManager;
import cc.dot.camerafilters.entity.FilterInfo;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;


/**
 * Created by xiang on 28/12/2016.
 */

public class GLCameraView extends GLSurfaceView  implements GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener{


    private SurfaceTexture surfaceTexture;

    private Camera mCamera;
    private ByteBuffer mGLPreviewBuffer;
    private int mPreviewRotation = 90;

    private FilterManager  filterManager;

    private Context mContext;

    private int mTextureId = 0;

    private SurfaceTexture mSurfaceTexture;

    private final float[] mSTMatrix = new float[16];

    private int mIncomingWidth, mIncomingHeight;

    private int mSurfaceWidth, mSurfaceHeight;

    private float mMvpScaleX = 1f, mMvpScaleY = 1f;

    private int mCamId = Camera.CameraInfo.CAMERA_FACING_FRONT;


    private TextureFrameAvailableListener mListener;

    private IntBuffer mGLFboBuffer;

    private ByteBuffer mRGBABuffer;

    private int mPrefixedSizeWidth = 320;

    private int mPrefixedSizeHeight = 240;


    public interface TextureFrameAvailableListener {

        void onFrameDataAvailable(byte[] buffer, int width, int height);

    }



    public GLCameraView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mContext = context;
        setEGLContextClientVersion(2);
        setRenderer(this);
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

        filterManager = FilterManager.builder().context(mContext)
                .defaultFilter(new FilterInfo(false, 1))
                .build();
    }


    public GLCameraView(Context context) {
        super(context);
        mContext = context;
        setEGLContextClientVersion(2);
        setRenderer(this);
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

        filterManager = FilterManager.builder().context(mContext)
                .defaultFilter(new FilterInfo(false, 1))
                .build();


    }


    public void setListenner(TextureFrameAvailableListener listener) {

        mListener = listener;
    }


    public void setCameraId(int id) {
        mCamId = id;
    }

    public int getCameraId() {
        return mCamId;
    }

    public boolean startCamera() {

        if (mCamera != null) {

            return false;
        }

        if (mCamId > (Camera.getNumberOfCameras() - 1) || mCamId < 0) {

            Toast.makeText(mContext,"Unsupported CamId ",Toast.LENGTH_SHORT).show();
            return false;
        }


        mCamera = Camera.open(mCamId);

        Camera.Parameters params = mCamera.getParameters();

        Camera.Size size = mCamera.new Size(mIncomingWidth, mIncomingHeight);

        Log.e("AAAA", params.getSupportedPreviewSizes() + "");

        if (!params.getSupportedPreviewSizes().contains(size) || !params.getSupportedPictureSizes().contains(size)) {

            Toast.makeText(mContext,"Unsupported resolution  ",Toast.LENGTH_SHORT).show();
            return false;
        }

        params.setPreviewFormat(ImageFormat.NV21);
        params.setPreviewSize(mIncomingWidth,mIncomingHeight);

        mCamera.setParameters(params);

        mCamera.setDisplayOrientation(90);


        try {
            mCamera.setPreviewTexture(mSurfaceTexture);
        } catch (IOException e) {
            e.printStackTrace();
            stopCamera();
            return false;
        }

        mCamera.startPreview();

        return true;
    }


    public void stopCamera(){

        if (mCamera != null) {
            mCamera.setPreviewCallback(null);
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
    }



    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {

        //GLES20.glDisable(GL10.GL_DITHER);
        //GLES20.glClearColor(0, 0, 0, 0);


        filterManager.initialize();

        mTextureId = filterManager.createTexture();

        mSurfaceTexture = new SurfaceTexture(mTextureId);

        mSurfaceTexture.setOnFrameAvailableListener(this);

    }


    public void setCameraPreviewSize(int width, int height) {
        mIncomingWidth = width;
        mIncomingHeight = height;

        float scaleHeight = mSurfaceWidth / (width * 1f / height * 1f);
        float surfaceHeight = mSurfaceHeight;

        mMvpScaleX = 1f;
        mMvpScaleY = scaleHeight / surfaceHeight;
        filterManager.scaleMVPMatrix(mMvpScaleX, mMvpScaleY);


    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {

        filterManager.updateSurfaceSize(width,height);

        mSurfaceWidth = width;
        mSurfaceHeight = height;




    }



    @Override
    public void onDrawFrame(GL10 gl) {

        GLES20.glViewport(0, 0, mSurfaceWidth, mSurfaceHeight);

        mSurfaceTexture.updateTexImage();

        mSurfaceTexture.getTransformMatrix(mSTMatrix);

        filterManager.drawFrame(mTextureId, mSTMatrix, mIncomingWidth, mIncomingHeight);

        if (mGLFboBuffer == null){
            mGLFboBuffer = IntBuffer.allocate(mSurfaceWidth * mSurfaceHeight);
        }

        if (mRGBABuffer == null) {
            mRGBABuffer = ByteBuffer.allocate(mSurfaceWidth * mSurfaceHeight * 4);
        }

        mGLFboBuffer.rewind();

        gl.glPixelStorei(GL10.GL_PACK_ALIGNMENT, 1);
        gl.glReadPixels(0, 0, mSurfaceWidth, mSurfaceHeight,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE, mGLFboBuffer);

        Log.e(" =====","after glreadPixels " + mGLFboBuffer.remaining() + " " + mGLFboBuffer.capacity());


        //Log.e(" =====","after flip " + mGLFboBuffer.remaining() + " " + mGLFboBuffer.capacity());

        if (mListener != null) {

            mRGBABuffer.clear();
            mRGBABuffer.rewind();
            mRGBABuffer.asIntBuffer().put(mGLFboBuffer);

            //mListener.onFrameDataAvailable(filterManager.getGLFboBuffer(),mIncomingWidth,mIncomingHeight);
            mListener.onFrameDataAvailable(mRGBABuffer.array(),mSurfaceWidth,mSurfaceHeight);
            Log.e("=====", "mRGBABuffer lenght " + mRGBABuffer.array().length);
        }

    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {

        requestRender();

    }
}
