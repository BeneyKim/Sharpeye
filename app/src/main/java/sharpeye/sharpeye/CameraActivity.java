package sharpeye.sharpeye;

import android.Manifest;
import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.Image.Plane;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.*;
import android.preference.PreferenceActivity;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomSheetBehavior;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.Size;
import android.view.*;
import android.widget.*;
import com.crashlytics.android.Crashlytics;
import io.fabric.sdk.android.Fabric;
import sharpeye.sharpeye.data.SharedPreferencesHelper;
import sharpeye.sharpeye.utils.ImageUtils;
import sharpeye.sharpeye.utils.Logger;
import sharpeye.sharpeye.tflite.FrameBuffer;

import java.nio.ByteBuffer;

public abstract class CameraActivity extends AppCompatActivity
    implements OnImageAvailableListener, NavigationView.OnNavigationItemSelectedListener, View.OnClickListener {
  private static final Logger LOGGER = new Logger();

  private static final boolean RELEASE = !BuildConfig.DEBUG;

  private static final int PERMISSIONS_REQUEST = 1;

  private static final String PERMISSION_CAMERA = Manifest.permission.CAMERA;
  private static final String PERMISSION_STORAGE = Manifest.permission.WRITE_EXTERNAL_STORAGE;

  private boolean debug = false;

  private Handler handler;
  private HandlerThread handlerThread;
  private boolean isProcessingFrame = false;
  private byte[][] yuvBytes = new byte[3][];
  private int[] rgbBytes = null;
  private int[] rgbBytesBuffer = null;
  private int yRowStride;

  protected int previewWidth = 0;
  protected int previewHeight = 0;

  private Runnable postInferenceCallback;
  private Runnable imageConverter;

  protected static Size DESIRED_PREVIEW_SIZE = new Size(640, 480);

  private LinearLayout bottomSheetLayout;
  private LinearLayout gestureLayout;
  private BottomSheetBehavior sheetBehavior;

  protected TextView frameValueTextView, cropValueTextView, inferenceTimeTextView;
  protected ImageView bottomSheetArrowImageView;
  private TextView threadsTextView;

  protected FrameBuffer frameBuffer;

  @Override
  protected void onCreate(final Bundle savedInstanceState) {
    LOGGER.d("onCreate " + this);
    super.onCreate(null);
    Fabric.with(this, new Crashlytics());
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

    setContentView(R.layout.activity_camera);

    Toolbar toolbar = findViewById(R.id.toolbar);

    DrawerLayout drawer = findViewById(R.id.drawer_layout);
    ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
            this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
    drawer.addDrawerListener(toggle);

    toggle.syncState();

    NavigationView navigationView = findViewById(R.id.nav_view);
    navigationView.setNavigationItemSelectedListener(this);
    navigationView.setItemIconTintList(null);
    if (SharedPreferencesHelper.INSTANCE.getSharedPreferencesBoolean(this,"dark_theme_on",false))
    {
      navigationView.setBackgroundColor(getColor(R.color.colorBackgroundDark));
      navigationView.setItemTextColor(getColorStateList(R.color.colorTextDark));
    }
    else
    {
      navigationView.setBackgroundColor(getColor(R.color.colorBackground));
      navigationView.setItemTextColor(getColorStateList(R.color.colorText));
    }

    frameBuffer = new FrameBuffer();

    if (hasPermission()) {
      setFragment();
    } else {
      requestPermission();
    }
    threadsTextView = findViewById(R.id.threads);
    ImageView plusImageView = findViewById(R.id.plus);
    ImageView minusImageView = findViewById(R.id.minus);
    bottomSheetLayout = findViewById(R.id.bottom_sheet_layout);
    gestureLayout = findViewById(R.id.gesture_layout);
    sheetBehavior = BottomSheetBehavior.from(bottomSheetLayout);
    bottomSheetArrowImageView = findViewById(R.id.bottom_sheet_arrow);


    ViewTreeObserver vto = gestureLayout.getViewTreeObserver();
    vto.addOnGlobalLayoutListener(
        new ViewTreeObserver.OnGlobalLayoutListener() {
          @Override
          public void onGlobalLayout() {
            gestureLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            //                int width = bottomSheetLayout.getMeasuredWidth();
            int height = gestureLayout.getMeasuredHeight();

            sheetBehavior.setPeekHeight(height);
          }
        });
    sheetBehavior.setHideable(false);

    sheetBehavior.setBottomSheetCallback(
        new BottomSheetBehavior.BottomSheetCallback() {
          @Override
          public void onStateChanged(@NonNull View bottomSheet, int newState) {
            switch (newState) {
              case BottomSheetBehavior.STATE_HIDDEN:
                break;
              case BottomSheetBehavior.STATE_EXPANDED:
                {
                  bottomSheetArrowImageView.setImageResource(R.drawable.ic_chevron_down);
                }
                break;
              case BottomSheetBehavior.STATE_COLLAPSED:
                {
                  bottomSheetArrowImageView.setImageResource(R.drawable.ic_chevron_up);
                }
                break;
              case BottomSheetBehavior.STATE_DRAGGING:
                break;
              case BottomSheetBehavior.STATE_SETTLING:
                bottomSheetArrowImageView.setImageResource(R.drawable.ic_chevron_up);
                break;
            }
          }

          @Override
          public void onSlide(@NonNull View bottomSheet, float slideOffset) {}
        });

    frameValueTextView = findViewById(R.id.frame_info);
    cropValueTextView = findViewById(R.id.crop_info);
    inferenceTimeTextView = findViewById(R.id.inference_info);

    plusImageView.setOnClickListener(this);
    minusImageView.setOnClickListener(this);
    bottomSheetLayout.setVisibility(((debug) ? View.VISIBLE : View.INVISIBLE));
  }

  protected int[] getRgbBytes() {
    imageConverter.run();
    return rgbBytes;
  }

  public boolean imageAvailableProcess(final Image image) {
    if (image == null) {
      return false;
    }

    final Plane[] planes = image.getPlanes();
    fillBytes(planes, yuvBytes);
    yRowStride = planes[0].getRowStride();
    final int uvRowStride = planes[1].getRowStride();
    final int uvPixelStride = planes[1].getPixelStride();
    image.close();

    ImageUtils.convertYUV420ToARGB8888(
            yuvBytes[0],
            yuvBytes[1],
            yuvBytes[2],
            previewWidth,
            previewHeight,
            yRowStride,
            uvRowStride,
            uvPixelStride,
            rgbBytesBuffer);

    long frameTime = System.currentTimeMillis();
    frameBuffer.addFrame(rgbBytesBuffer.clone(), frameTime);

    if (isProcessingFrame) {
      return false;
    }
    isProcessingFrame = true;
    Trace.beginSection("imageAvailable");
    runInBackground(new Runnable() {
      @Override
      public void run() {
        imageConverter =
                new Runnable() {
                  @Override
                  public void run() {
                    ImageUtils.convertYUV420ToARGB8888(
                            yuvBytes[0],
                            yuvBytes[1],
                            yuvBytes[2],
                            previewWidth,
                            previewHeight,
                            yRowStride,
                            uvRowStride,
                            uvPixelStride,
                            rgbBytes);
                            frameBuffer.setDetectionFrame(rgbBytes.clone(), System.currentTimeMillis());
                  }
                };

        postInferenceCallback =
                new Runnable() {
                  @Override
                  public void run() {
                    isProcessingFrame = false;
                  }
                };
        processImage();
      }
    });

    return true;
  }

  /**
   * Callback for Camera2 API
   */
  @Override
  public void onImageAvailable(final ImageReader reader) {
    //We need wait until we have some size from onPreviewSizeChosen
    if (previewWidth == 0 || previewHeight == 0) {
      return;
    }
    if (rgbBytes == null) {
      rgbBytes = new int[previewWidth * previewHeight];
    }

    if (rgbBytesBuffer == null) {
      rgbBytesBuffer = new int[previewWidth * previewHeight];
    }
    try {
      final Image image = reader.acquireLatestImage();
      imageAvailableProcess(image);

    } catch (final Exception e) {
      LOGGER.e(e, "Exception!");
      Trace.endSection();
      return;
    }
    Trace.endSection();
  }

  @Override
  public synchronized void onStart() {
    LOGGER.d("onStart " + this);
    super.onStart();
  }

  @Override
  public synchronized void onResume() {
    LOGGER.d("onResume " + this);
      NavigationView navigationView = findViewById(R.id.nav_view);
    if (SharedPreferencesHelper.INSTANCE.getSharedPreferencesBoolean(this,"dark_theme_on",false))
    {
      navigationView.setBackgroundColor(getColor(R.color.colorBackgroundDark));
      navigationView.setItemTextColor(getColorStateList(R.color.colorTextDark));
    }
    else
    {
      navigationView.setBackgroundColor(getColor(R.color.colorBackground));
      navigationView.setItemTextColor(getColorStateList(R.color.colorText));
    }
    super.onResume();

    handlerThread = new HandlerThread("inference");
    handlerThread.start();
    handler = new Handler(handlerThread.getLooper());
  }

  @Override
  public synchronized void onPause() {
    LOGGER.d("onPause " + this);

    super.onPause();
  }

  @Override
  public synchronized void onStop() {
    LOGGER.d("onStop " + this);
    super.onStop();
  }

  @Override
  public synchronized void onDestroy() {
    LOGGER.d("onDestroy " + this);
    if (!isFinishing()) {
      LOGGER.d("Requesting finish");
      //finish();
    }

    handlerThread.quitSafely();
    try {
      handlerThread.join();
      handlerThread = null;
      handler = null;
    } catch (final InterruptedException e) {
      LOGGER.e(e, "Exception!");
    }
    super.onDestroy();
  }

  protected synchronized void runInBackground(final Runnable r) {
    if (handler != null) {
      handler.post(r);
    }
  }

  @Override
  public void onRequestPermissionsResult(
          final int requestCode, final String[] permissions, final int[] grantResults) {
    if (requestCode == PERMISSIONS_REQUEST) {
      if (grantResults.length > 0
          && grantResults[0] == PackageManager.PERMISSION_GRANTED
          && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
        setFragment();
      } else {
        requestPermission();
      }
    }
  }

  private boolean hasPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      return checkSelfPermission(PERMISSION_CAMERA) == PackageManager.PERMISSION_GRANTED &&
          checkSelfPermission(PERMISSION_STORAGE) == PackageManager.PERMISSION_GRANTED;
    } else {
      return true;
    }
  }

  private void requestPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      if (shouldShowRequestPermissionRationale(PERMISSION_CAMERA) ||
          shouldShowRequestPermissionRationale(PERMISSION_STORAGE)) {
        Toast.makeText(CameraActivity.this,
            "Camera AND storage permission are required for this demo", Toast.LENGTH_LONG).show();
      }
      requestPermissions(new String[] {PERMISSION_CAMERA, PERMISSION_STORAGE}, PERMISSIONS_REQUEST);
    }
  }

  private String chooseCamera() {
    final CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
    try {
      for (final String cameraId : manager.getCameraIdList()) {
        final CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

        // We don't use a front facing camera in this sample.
        final Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
        if (facing != null && facing != CameraCharacteristics.LENS_FACING_BACK) {
          continue;
        }

        final StreamConfigurationMap map =
            characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

        if (map == null) {
          continue;
        }

        return cameraId;
      }
    } catch (CameraAccessException e) {
      LOGGER.e(e, "Not allowed to access camera");
    }

    return null;
  }

  protected void setFragment() {
    String cameraId = chooseCamera();
    if (cameraId == null) {
      Toast.makeText(this, "No Camera Detected", Toast.LENGTH_SHORT).show();
      finish();
    }

    CameraConnectionFragment camera2Fragment =
        CameraConnectionFragment.newInstance(
            new CameraConnectionFragment.ConnectionCallback() {
              @Override
              public void onPreviewSizeChosen(final Size size, final int rotation) {
                previewHeight = size.getHeight();
                previewWidth = size.getWidth();
                Log.e("CameraActivity", "PreviewTextureSize="+ size.getWidth() +"x"+ size.getHeight());
                CameraActivity.this.onPreviewSizeChosen(size, rotation);
              }
            },
            this,
            getLayoutId(),
            getDesiredPreviewFrameSize());

    camera2Fragment.setCamera(cameraId);

    getFragmentManager()
        .beginTransaction()
        .replace(R.id.container, camera2Fragment)
        .commit();
    Log.e("CameraActivity", "PreviewTexturePlaced");
  }

  protected void fillBytes(final Plane[] planes, final byte[][] yuvBytes) {
    // Because of the variable row stride it's not possible to know in
    // advance the actual necessary dimensions of the yuv planes.
    for (int i = 0; i < planes.length; ++i) {
      final ByteBuffer buffer = planes[i].getBuffer();
      if (yuvBytes[i] == null) {
        LOGGER.d("Initializing buffer %d at size %d", i, buffer.capacity());
        yuvBytes[i] = new byte[buffer.capacity()];
      }
      buffer.get(yuvBytes[i]);
    }
  }

  public boolean isDebug() {
    return debug;
  }

  public void requestRender() {
    if (bottomSheetLayout != null) {
      bottomSheetLayout.setVisibility(((debug) ? View.VISIBLE : View.INVISIBLE));
      bottomSheetLayout.postInvalidate();
    }
  }

  @Override
  public boolean onKeyDown(final int keyCode, final KeyEvent event) {
    if (!RELEASE) {
      if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP
              || keyCode == KeyEvent.KEYCODE_BUTTON_L1 || keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
        debug = !debug;
        requestRender();
        return super.onKeyDown(keyCode, event);
      }
    }
    return super.onKeyDown(keyCode, event);
  }

  protected void readyForNextImage() {
    if (postInferenceCallback != null) {
      postInferenceCallback.run();
    }
  }

  protected int getScreenOrientation() {
    switch (getWindowManager().getDefaultDisplay().getRotation()) {
      case Surface.ROTATION_270:
        return 270;
      case Surface.ROTATION_180:
        return 180;
      case Surface.ROTATION_90:
        return 90;
      default:
        return 0;
    }
  }

  @Override
  public void onClick(View v) {
    if (v.getId() == R.id.plus) {
      String threads = threadsTextView.getText().toString().trim();
      int numThreads = Integer.parseInt(threads);
      if (numThreads >= 9) return;
      numThreads++;
      threadsTextView.setText(String.valueOf(numThreads));
      setNumThreads(numThreads);
    } else if (v.getId() == R.id.minus) {
      String threads = threadsTextView.getText().toString().trim();
      int numThreads = Integer.parseInt(threads);
      if (numThreads == 1) {
        return;
      }
      numThreads--;
      threadsTextView.setText(String.valueOf(numThreads));
      setNumThreads(numThreads);
    }
  }

  protected void showFrameInfo(String frameInfo) {
    frameValueTextView.setText(frameInfo);
  }

  protected void showCropInfo(String cropInfo) {
    cropValueTextView.setText(cropInfo);
  }

  protected void showInference(String inferenceTime) {
    inferenceTimeTextView.setText(inferenceTime);
  }

  @Override
  public void onBackPressed() {
    DrawerLayout drawer = findViewById(R.id.drawer_layout);
    if (drawer.isDrawerOpen(GravityCompat.START)) {
      drawer.closeDrawer(GravityCompat.START);
    } else {
      super.onBackPressed();
    }
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    // Inflate the menu; this adds items to the action bar if it is present.
    getMenuInflater().inflate(R.menu.camera, menu);
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

  @SuppressWarnings("StatementWithEmptyBody")
  @Override
  public boolean onNavigationItemSelected(MenuItem item) {
    // Handle navigation view item clicks here.
    int id = item.getItemId();

    if (id == R.id.nav_danger) {
      Intent intent = new Intent(this, SettingsActivity.class);
      intent.putExtra(PreferenceActivity.EXTRA_SHOW_FRAGMENT,
              SettingsActivity.DangersPreferenceFragment.class.getName());
      intent.putExtra(PreferenceActivity.EXTRA_NO_HEADERS, true);
      startActivity(intent);
    } else if (id == R.id.nav_signs) {
      Intent intent = new Intent(this, SettingsActivity.class);
      intent.putExtra(PreferenceActivity.EXTRA_SHOW_FRAGMENT,
              SettingsActivity.SignsPreferenceFragment.class.getName());
      intent.putExtra(PreferenceActivity.EXTRA_NO_HEADERS, true);
      startActivity(intent);
    } else if (id == R.id.nav_assist) {
      Intent intent = new Intent(this, SettingsActivity.class);
      intent.putExtra(PreferenceActivity.EXTRA_SHOW_FRAGMENT,
              SettingsActivity.VocalPreferenceFragment.class.getName());
      intent.putExtra(PreferenceActivity.EXTRA_NO_HEADERS, true);
      startActivity(intent);
    } else if (id == R.id.nav_report) {
      Intent intent = new Intent(this, SettingsActivity.class);
      intent.putExtra(PreferenceActivity.EXTRA_SHOW_FRAGMENT,
              SettingsActivity.ReportFragment.class.getName());
      intent.putExtra(PreferenceActivity.EXTRA_NO_HEADERS, true);
      startActivity(intent);
    } else if (id == R.id.nav_about) {
      Intent intent = new Intent(this, SettingsActivity.class);
      intent.putExtra(PreferenceActivity.EXTRA_SHOW_FRAGMENT,
              SettingsActivity.AboutFragment.class.getName());
      intent.putExtra(PreferenceActivity.EXTRA_NO_HEADERS, true);
      startActivity(intent);
    } else if (id == R.id.nav_parameters) {
      Intent intent = new Intent(this, SettingsActivity.class);
      intent.putExtra(PreferenceActivity.EXTRA_SHOW_FRAGMENT,
              SettingsActivity.SettingsPreferenceFragment.class.getName());
      intent.putExtra(PreferenceActivity.EXTRA_NO_HEADERS, true);
      startActivity(intent);
    }
    return true;
  }

  protected abstract void processImage();

  protected abstract void onPreviewSizeChosen(final Size size, final int rotation);
  protected abstract int getLayoutId();
  protected abstract Size getDesiredPreviewFrameSize();

  protected abstract void setNumThreads(int numThreads);
}
