package com.damnwrapper32armv7.xaview;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import android.widget.GridView;
import android.widget.ProgressBar;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.BaseAdapter;
import android.view.ViewGroup;
import android.graphics.drawable.GradientDrawable;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.content.Context;

public class MainActivity extends Activity implements SurfaceHolder.Callback, SensorEventListener {

    static {
        System.loadLibrary("DamnWrapper32");
    }

    private TextView logTextView;
    private ScrollView scrollView;
    private FrameLayout rootLayout;
    private SurfaceView surfaceView;
    private boolean isRendering = false;

    private LinearLayout unpackLayout;
    private ProgressBar unpackProgress;
    private TextView unpackText;
    private TextView unpackAppName;
    private TextView unpackPkgName;
    private TextView unpackVersion;

    private LinearLayout deleteLayout;
    private ProgressBar deleteProgress;
    private TextView deleteText;
    private TextView deleteAppName;
    private TextView deletePkgName;
    private TextView deleteVersion;
    private GridView launcherGrid;
    private TextView noGamesText;
    private android.widget.Button aboutButton;
    private android.widget.Button settingsButton;
    private LinearLayout bottomButtonsLayout;
    private LinearLayout aboutLayout;
    private LinearLayout settingsLayout;
    private LinearLayout commandsLayout;
    private LinearLayout loggingFiltersLayout;
    private LinearLayout loggingSpamFiltersLayout;
    private LinearLayout gpuOffloadLayout;
    private android.widget.CheckBox ignoreIpaCheckbox;
    private android.widget.CheckBox onScreenDebugOverlayCheckbox;
    private android.widget.CheckBox showPerfOverlayCheckbox;
    private android.widget.CheckBox cpuRenderCheckbox;
    private android.widget.CheckBox nativeRootMmapCheckbox;
    private android.widget.Button esModeButton;
    private List<AppInfo> installedApps = new ArrayList<>();
    private java.util.HashSet<String> deletedInThisSession = new java.util.HashSet<>();
    private java.util.HashMap<String, String> availableIpas = new java.util.HashMap<>();

    private float scaleFactorX = 1f;
    private float scaleFactorY = 1f;

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor gyroscope;
    private boolean isSetupStarted = false;

    // --- ВАРИАНТЫ ВИДЕОПЛЕЕРА ---
    private FrameLayout videoContainer;
    private android.widget.VideoView videoView;
    private android.widget.Button skipButton;
    private android.os.Handler videoHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable hideSkipRunnable;
    private int activeVideoPtrId = 0;

    // Хранилище аудиоплееров: C++ Pointer ID -> Android MediaPlayer
    private HashMap<Integer, MediaPlayer> audioPlayers = new HashMap<>();
    private AudioTrack streamTrack;

    public void audioUnitStreamInit(int sampleRate, int channels) {
        if (streamTrack != null) {
            streamTrack.stop();
            streamTrack.release();
        }
        int channelConfig = (channels == 2) ? AudioFormat.CHANNEL_OUT_STEREO : AudioFormat.CHANNEL_OUT_MONO;
        int minSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT);
        streamTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT, minSize * 4, AudioTrack.MODE_STREAM);
        streamTrack.play();
    }

    public void audioUnitStreamWrite(byte[] data, int size) {
        if (streamTrack != null) {
            streamTrack.write(data, 0, size);
        }
    }

    // --- OPENAL СТЕЙТ ---
    private HashMap<Integer, byte[]> alBuffers = new HashMap<>();
    private HashMap<Integer, Integer> alBufferFreqs = new HashMap<>();
    private HashMap<Integer, Integer> alBufferChannels = new HashMap<>();
    private HashMap<Integer, Integer> alSourceToBuffer = new HashMap<>();
    private HashMap<Integer, AudioTrack> alSourceTracks = new HashMap<>();

    private static final String WORK_DIR = Environment.getExternalStorageDirectory() + "/DamnWrapper32_ARMv7/";
    private static final String APPS_DIR = WORK_DIR + "apps/";
    private static final String APPS_INSTALLED_DIR = WORK_DIR + "apps_installed/";
    private static final String SETUP_DIR = WORK_DIR + "setup/";

    public native void initWrapper(String workDir, String appBundlePath, String bundleId, boolean logRender, boolean logSound, boolean logFs, boolean logNet, boolean logTodo, boolean logRenderDebug, boolean logFuncList, boolean logHiddenClasses, boolean logOther, int spamFiltersMask, boolean onScreenDebugOverlay, boolean showPerfOverlay, boolean nativeRootMmap, int resWidth, int resHeight, int esMode, int gpuOffloadMask);
    public native void onSurfaceCreated(android.view.Surface surface);
    public native void onSurfaceChanged(int width, int height);
    public native void onTouchEventNative(int actionMasked, int pointerId, float x, float y);
    public native void onSensorChangedNative(int sensorType, float x, float y, float z);

    public native void onVideoFinishedNative(int ptrId);


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        }

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        rootLayout = new FrameLayout(this);
        rootLayout.setBackgroundColor(Color.BLACK);

        scrollView = new ScrollView(this);
        logTextView = new TextView(this);
        logTextView.setTextColor(Color.GREEN);
        logTextView.setTextSize(12f);
        logTextView.setTextIsSelectable(true);
        logTextView.setPadding(16, 16, 16, 16);

        scrollView.addView(logTextView);
        scrollView.setVisibility(View.GONE); // Скрываем логи изначально

        // UI Распаковки
        unpackLayout = new LinearLayout(this);
        unpackLayout.setOrientation(LinearLayout.VERTICAL);
        unpackLayout.setGravity(Gravity.CENTER);
        unpackLayout.setVisibility(View.GONE);
        
        unpackProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        unpackProgress.setLayoutParams(new LinearLayout.LayoutParams(600, 50));
        unpackText = new TextView(this);
        unpackText.setTextColor(Color.WHITE);
        unpackText.setPadding(0, 20, 0, 0);
        unpackAppName = new TextView(this);
        unpackAppName.setTextColor(Color.LTGRAY);
        unpackPkgName = new TextView(this);
        unpackPkgName.setTextColor(Color.LTGRAY);
        unpackVersion = new TextView(this);
        unpackVersion.setTextColor(Color.LTGRAY);
        unpackLayout.addView(unpackProgress);
        unpackLayout.addView(unpackText);
        unpackLayout.addView(unpackAppName);
        unpackLayout.addView(unpackPkgName);
        unpackLayout.addView(unpackVersion);

        // UI Удаления
        deleteLayout = new LinearLayout(this);
        deleteLayout.setOrientation(LinearLayout.VERTICAL);
        deleteLayout.setGravity(Gravity.CENTER);
        deleteLayout.setBackgroundColor(Color.parseColor("#CC000000"));
        deleteLayout.setVisibility(View.GONE);
        deleteLayout.setClickable(true);
        
        deleteProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        deleteProgress.setLayoutParams(new LinearLayout.LayoutParams(600, 50));
        deleteText = new TextView(this);
        deleteText.setTextColor(Color.WHITE);
        deleteText.setPadding(0, 20, 0, 0);
        deleteAppName = new TextView(this);
        deleteAppName.setTextColor(Color.LTGRAY);
        deletePkgName = new TextView(this);
        deletePkgName.setTextColor(Color.LTGRAY);
        deleteVersion = new TextView(this);
        deleteVersion.setTextColor(Color.LTGRAY);
        deleteLayout.addView(deleteProgress);
        deleteLayout.addView(deleteText);
        deleteLayout.addView(deleteAppName);
        deleteLayout.addView(deletePkgName);
        deleteLayout.addView(deleteVersion);

        // UI Лаунчера
        launcherGrid = new GridView(this);
        launcherGrid.setNumColumns(4);
        launcherGrid.setHorizontalSpacing(20);
        launcherGrid.setVerticalSpacing(40);
        launcherGrid.setPadding(40, 100, 40, 40);
        launcherGrid.setVisibility(View.GONE);

        noGamesText = new TextView(this);
        noGamesText.setText("Games not finded, put it in DamnWrapper32_ARMv7/apps");
        noGamesText.setTextColor(Color.LTGRAY);
        noGamesText.setTextSize(18f);
        noGamesText.setGravity(Gravity.CENTER);
        noGamesText.setVisibility(View.GONE);

        bottomButtonsLayout = new LinearLayout(this);
        bottomButtonsLayout.setOrientation(LinearLayout.HORIZONTAL);
        bottomButtonsLayout.setVisibility(View.GONE); // Скрываем по умолчанию (до конца сканирования)

        FrameLayout.LayoutParams bottomParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        bottomParams.gravity = Gravity.BOTTOM;
        bottomParams.setMargins(40, 0, 40, 100); // Отступы от краев экрана

        LinearLayout.LayoutParams btnLeftParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        btnLeftParams.setMargins(0, 0, 20, 0); // Отступ справа от About (между кнопками)

        LinearLayout.LayoutParams btnRightParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        btnRightParams.setMargins(20, 0, 0, 0); // Отступ слева от Settings (между кнопками)

        aboutButton = new android.widget.Button(this);
        aboutButton.setText("About");
        settingsButton = new android.widget.Button(this);
        settingsButton.setText("Settings");

        bottomButtonsLayout.addView(aboutButton, btnLeftParams);
        bottomButtonsLayout.addView(settingsButton, btnRightParams);

        aboutLayout = new LinearLayout(this);
        aboutLayout.setOrientation(LinearLayout.VERTICAL);
        aboutLayout.setGravity(Gravity.CENTER);
        aboutLayout.setBackgroundColor(Color.BLACK);
        aboutLayout.setVisibility(View.GONE);

        TextView aboutText = new TextView(this);
        aboutText.setTextColor(Color.WHITE);
        aboutText.setGravity(Gravity.CENTER);
        aboutText.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
        String aboutHtml = "DamnWrapper32 (ARMv7) - IOS wrapper by XaView<br><br>" +
                "Current IOS support:<br>" +
                "ARMv7 Only<br>" +
                "OpenGL ES 2.0 Only<br>" +
                "IOS 4.0 maximum in theory at this moment, but in fact IOS 3.1.3-3.2 have better support I guess<br><br>" +
                "If you want support me you can donate me Telegram stars:<br>" +
                "<a href=\"https://t.me/xaviewdnk\">https://t.me/xaviewdnk</a><br><br>" +
                "You can also support me via Steam, by giving gift or trade:<br>" +
                "<a href=\"https://steamcommunity.com/profiles/76561198886703080\">https://steamcommunity.com/profiles/76561198886703080</a>";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            aboutText.setText(android.text.Html.fromHtml(aboutHtml, android.text.Html.FROM_HTML_MODE_LEGACY));
        } else {
            aboutText.setText(android.text.Html.fromHtml(aboutHtml));
        }
        
        android.widget.Button aboutBackButton = new android.widget.Button(this);
        aboutBackButton.setText("Back");
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        backParams.setMargins(0, 50, 0, 0);
        
        android.widget.Button commandsListButton = new android.widget.Button(this);
        commandsListButton.setText("Commands list");
        LinearLayout.LayoutParams cmdBtnParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cmdBtnParams.setMargins(0, 20, 0, 0);

        aboutLayout.addView(aboutText);
        aboutLayout.addView(commandsListButton, cmdBtnParams);
        aboutLayout.addView(aboutBackButton, backParams);

        commandsLayout = new LinearLayout(this);
        commandsLayout.setOrientation(LinearLayout.VERTICAL);
        commandsLayout.setGravity(Gravity.CENTER);
        commandsLayout.setBackgroundColor(Color.BLACK);
        commandsLayout.setVisibility(View.GONE);

        TextView commandsText = new TextView(this);
        commandsText.setTextColor(Color.WHITE);
        commandsText.setBackgroundColor(Color.TRANSPARENT);
        commandsText.setGravity(Gravity.CENTER);
        commandsText.setTextIsSelectable(true);
        commandsText.setPadding(40, 40, 40, 40);
        commandsText.setText("-launch packagename_version\n*Example: -launch com.sega.smb2_2.0.0\n#Skips wrapper menus and launch app directly.");

        android.widget.Button commandsBackButton = new android.widget.Button(this);
        commandsBackButton.setText("Back");

        commandsLayout.addView(commandsText);
        commandsLayout.addView(commandsBackButton, backParams);

        commandsListButton.setOnClickListener(v -> {
            aboutLayout.setVisibility(View.GONE);
            commandsLayout.setVisibility(View.VISIBLE);
        });

        commandsBackButton.setOnClickListener(v -> {
            commandsLayout.setVisibility(View.GONE);
            aboutLayout.setVisibility(View.VISIBLE);
        });

        settingsLayout = new LinearLayout(this);
        settingsLayout.setOrientation(LinearLayout.VERTICAL);
        settingsLayout.setGravity(Gravity.CENTER);
        settingsLayout.setBackgroundColor(Color.BLACK);
        settingsLayout.setVisibility(View.GONE);

        android.widget.Button loggingFiltersButton = new android.widget.Button(this);
        loggingFiltersButton.setText("Logging filters");

        android.widget.Button loggingSpamFiltersButton = new android.widget.Button(this);
        loggingSpamFiltersButton.setText("Logging spam hide filters");

        loggingFiltersLayout = new LinearLayout(this);
        loggingFiltersLayout.setOrientation(LinearLayout.VERTICAL);
        loggingFiltersLayout.setGravity(Gravity.CENTER);
        loggingFiltersLayout.setBackgroundColor(Color.BLACK);
        loggingFiltersLayout.setVisibility(View.GONE);

        String[] filterNames = {"Render", "Sound", "File system", "Network, Bluetooth, GPS", "TODO, unimplemented, stubs", "Render debug logs (Outdated, OnScreen overlay replaced it partically)", "Functions list in logs", "Hidden classes list in logs", "Other"};
        String[] filterKeys = {"log_render", "log_sound", "log_fs", "log_net", "log_todo", "log_render_debug", "log_func_list", "log_hidden_classes", "log_other"};

        for (int i = 0; i < filterNames.length; i++) {
            android.widget.CheckBox cb = new android.widget.CheckBox(this);
            cb.setText(filterNames[i]);
            cb.setTextColor(Color.WHITE);
            cb.setChecked(getSharedPreferences("DamnPrefs", MODE_PRIVATE).getBoolean(filterKeys[i], false));
            final String key = filterKeys[i];
            cb.setOnCheckedChangeListener((btnView, isChecked) -> {
                getSharedPreferences("DamnPrefs", MODE_PRIVATE).edit().putBoolean(key, isChecked).apply();
            });
            loggingFiltersLayout.addView(cb);
        }

        android.widget.Button loggingFiltersBackButton = new android.widget.Button(this);
        loggingFiltersBackButton.setText("Back");
        loggingFiltersLayout.addView(loggingFiltersBackButton, backParams);

        loggingFiltersButton.setOnClickListener(v -> {
            settingsLayout.setVisibility(View.GONE);
            loggingFiltersLayout.setVisibility(View.VISIBLE);
        });

        loggingFiltersBackButton.setOnClickListener(v -> {
            loggingFiltersLayout.setVisibility(View.GONE);
            settingsLayout.setVisibility(View.VISIBLE);
        });

        loggingSpamFiltersLayout = new LinearLayout(this);
        loggingSpamFiltersLayout.setOrientation(LinearLayout.VERTICAL);
        loggingSpamFiltersLayout.setGravity(Gravity.CENTER);
        loggingSpamFiltersLayout.setBackgroundColor(Color.BLACK);
        loggingSpamFiltersLayout.setVisibility(View.GONE);

        String[] spamFilterKeys = {"spam_log_render", "spam_log_sound", "spam_log_fs", "spam_log_net", "spam_log_todo", "spam_log_render_debug", "spam_log_func_list", "spam_log_hidden_classes", "spam_log_other"};

        for (int i = 0; i < filterNames.length; i++) {
            android.widget.CheckBox cb = new android.widget.CheckBox(this);
            cb.setText(filterNames[i]);
            cb.setTextColor(Color.WHITE);
            cb.setChecked(getSharedPreferences("DamnPrefs", MODE_PRIVATE).getBoolean(spamFilterKeys[i], true));
            final String key = spamFilterKeys[i];
            cb.setOnCheckedChangeListener((btnView, isChecked) -> {
                getSharedPreferences("DamnPrefs", MODE_PRIVATE).edit().putBoolean(key, isChecked).apply();
            });
            loggingSpamFiltersLayout.addView(cb);
        }

        android.widget.Button loggingSpamFiltersBackButton = new android.widget.Button(this);
        loggingSpamFiltersBackButton.setText("Back");
        loggingSpamFiltersLayout.addView(loggingSpamFiltersBackButton, backParams);

        loggingSpamFiltersButton.setOnClickListener(v -> {
            settingsLayout.setVisibility(View.GONE);
            loggingSpamFiltersLayout.setVisibility(View.VISIBLE);
        });

        loggingSpamFiltersBackButton.setOnClickListener(v -> {
            loggingSpamFiltersLayout.setVisibility(View.GONE);
            settingsLayout.setVisibility(View.VISIBLE);
        });

        android.widget.Button gpuOffloadButton = new android.widget.Button(this);
        gpuOffloadButton.setText("GPU Offload Menu (Experimental)");
        
        gpuOffloadLayout = new LinearLayout(this);
        gpuOffloadLayout.setOrientation(LinearLayout.VERTICAL);
        gpuOffloadLayout.setGravity(Gravity.CENTER);
        gpuOffloadLayout.setBackgroundColor(Color.BLACK);
        gpuOffloadLayout.setVisibility(View.GONE);

        String[] gpuNames = {
            "1. EGL Context & SwapBuffers (Bit 0)", 
            "2. glClear (Bit 1)", 
            "3. Shaders & Uniforms (Bit 2)", 
            "4. Textures (Bit 3)", 
            "5. VBO, Attribs & glDraw (Bit 4)", 
            "6. States (Blend, Depth, Cull) (Bit 5)", 
            "7. FBO & Viewport (Bit 6)"
        };

        for (int i = 0; i < gpuNames.length; i++) {
            android.widget.CheckBox cb = new android.widget.CheckBox(this);
            cb.setText(gpuNames[i]);
            cb.setTextColor(Color.WHITE);
            cb.setChecked(getSharedPreferences("DamnPrefs", MODE_PRIVATE).getBoolean("gpu_bit_" + i, false));
            final int bitIdx = i;
            cb.setOnCheckedChangeListener((btnView, isChecked) -> {
                getSharedPreferences("DamnPrefs", MODE_PRIVATE).edit().putBoolean("gpu_bit_" + bitIdx, isChecked).apply();
            });
            gpuOffloadLayout.addView(cb);
        }

        android.widget.Button gpuOffloadBackButton = new android.widget.Button(this);
        gpuOffloadBackButton.setText("Back");
        gpuOffloadLayout.addView(gpuOffloadBackButton, backParams);

        gpuOffloadButton.setOnClickListener(v -> {
            settingsLayout.setVisibility(View.GONE);
            gpuOffloadLayout.setVisibility(View.VISIBLE);
        });

        gpuOffloadBackButton.setOnClickListener(v -> {
            gpuOffloadLayout.setVisibility(View.GONE);
            settingsLayout.setVisibility(View.VISIBLE);
        });

        ignoreIpaCheckbox = new android.widget.CheckBox(this);
        ignoreIpaCheckbox.setText("Ignore IPA in /apps for installation with same version and package name");
        ignoreIpaCheckbox.setTextColor(Color.WHITE);
        ignoreIpaCheckbox.setChecked(getSharedPreferences("DamnPrefs", MODE_PRIVATE).getBoolean("ignore_same_ipa", true));
        ignoreIpaCheckbox.setOnCheckedChangeListener((btnView, isChecked) -> {
            getSharedPreferences("DamnPrefs", MODE_PRIVATE).edit().putBoolean("ignore_same_ipa", isChecked).apply();
        });

        onScreenDebugOverlayCheckbox = new android.widget.CheckBox(this);
        onScreenDebugOverlayCheckbox.setText("OnScreen debug overlay");
        onScreenDebugOverlayCheckbox.setTextColor(Color.WHITE);
        onScreenDebugOverlayCheckbox.setChecked(getSharedPreferences("DamnPrefs", MODE_PRIVATE).getBoolean("onscreen_debug_overlay", false));
        onScreenDebugOverlayCheckbox.setOnCheckedChangeListener((btnView, isChecked) -> {
            getSharedPreferences("DamnPrefs", MODE_PRIVATE).edit().putBoolean("onscreen_debug_overlay", isChecked).apply();
        });

        showPerfOverlayCheckbox = new android.widget.CheckBox(this);
        showPerfOverlayCheckbox.setText("Show performance overlay (FPS)");
        showPerfOverlayCheckbox.setTextColor(Color.WHITE);
        showPerfOverlayCheckbox.setChecked(getSharedPreferences("DamnPrefs", MODE_PRIVATE).getBoolean("show_perf_overlay", false));
        showPerfOverlayCheckbox.setOnCheckedChangeListener((btnView, isChecked) -> {
            getSharedPreferences("DamnPrefs", MODE_PRIVATE).edit().putBoolean("show_perf_overlay", isChecked).apply();
        });

        cpuRenderCheckbox = new android.widget.CheckBox(this);
        cpuRenderCheckbox.setText("Program CPU render (Slow but safe)");
        cpuRenderCheckbox.setTextColor(Color.GRAY);
        cpuRenderCheckbox.setChecked(true);
        cpuRenderCheckbox.setEnabled(false);

        nativeRootMmapCheckbox = new android.widget.CheckBox(this);
        nativeRootMmapCheckbox.setText("Native ROOT mmap (Better compatability but need root access)");
        nativeRootMmapCheckbox.setTextColor(Color.RED);
        nativeRootMmapCheckbox.setChecked(getSharedPreferences("DamnPrefs", MODE_PRIVATE).getBoolean("native_root_mmap", false));
        nativeRootMmapCheckbox.setOnCheckedChangeListener((btnView, isChecked) -> {
            getSharedPreferences("DamnPrefs", MODE_PRIVATE).edit().putBoolean("native_root_mmap", isChecked).apply();
        });

        esModeButton = new android.widget.Button(this);
        int currentEsMode = getSharedPreferences("DamnPrefs", MODE_PRIVATE).getInt("es_mode", 2);
        esModeButton.setText("OpenGL ES Mode: " + (currentEsMode == 2 ? "2.0 ✅" : "1.1 ✅"));
        esModeButton.setOnClickListener(v_es -> {
            int mode = getSharedPreferences("DamnPrefs", MODE_PRIVATE).getInt("es_mode", 2);
            int newMode = mode == 2 ? 1 : 2;
            getSharedPreferences("DamnPrefs", MODE_PRIVATE).edit().putInt("es_mode", newMode).apply();
            esModeButton.setText("OpenGL ES Mode: " + (newMode == 2 ? "2.0 ✅" : "1.1 ✅"));
        });

        LinearLayout resLayout = new LinearLayout(this);
        resLayout.setOrientation(LinearLayout.HORIZONTAL);
        resLayout.setGravity(Gravity.CENTER);
        resLayout.setPadding(0, 20, 0, 20);

        android.widget.EditText widthInput = new android.widget.EditText(this);
        widthInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        widthInput.setTextColor(Color.WHITE);
        widthInput.setHintTextColor(Color.GRAY);
        widthInput.setText(String.valueOf(getSharedPreferences("DamnPrefs", MODE_PRIVATE).getInt("res_width", 480)));
        widthInput.setEms(4);

        TextView xText = new TextView(this);
        xText.setText(" x ");
        xText.setTextColor(Color.WHITE);
        xText.setTextSize(16f);

        android.widget.EditText heightInput = new android.widget.EditText(this);
        heightInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        heightInput.setTextColor(Color.WHITE);
        heightInput.setHintTextColor(Color.GRAY);
        heightInput.setText(String.valueOf(getSharedPreferences("DamnPrefs", MODE_PRIVATE).getInt("res_height", 320)));
        heightInput.setEms(4);

        android.widget.Button resResetButton = new android.widget.Button(this);
        resResetButton.setText("Reset");
        resResetButton.setOnClickListener(v_res -> {
            widthInput.setText("480");
            heightInput.setText("320");
        });

        android.widget.Button resApplyButton = new android.widget.Button(this);
        resApplyButton.setText("Apply");
        resApplyButton.setOnClickListener(v_res -> {
            try {
                int w = Integer.parseInt(widthInput.getText().toString());
                int h = Integer.parseInt(heightInput.getText().toString());
                if (w > 0 && h > 0) {
                    getSharedPreferences("DamnPrefs", MODE_PRIVATE).edit()
                            .putInt("res_width", w)
                            .putInt("res_height", h)
                            .apply();
                    android.widget.Toast.makeText(this, "Resolution saved", android.widget.Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {}
        });

        resLayout.addView(widthInput);
        resLayout.addView(xText);
        resLayout.addView(heightInput);
        resLayout.addView(resResetButton);
        resLayout.addView(resApplyButton);

        android.widget.Button settingsBackButton = new android.widget.Button(this);
        settingsBackButton.setText("Back");
        
        settingsLayout.addView(loggingFiltersButton);
        settingsLayout.addView(loggingSpamFiltersButton);
        settingsLayout.addView(gpuOffloadButton);
        settingsLayout.addView(resLayout);
        settingsLayout.addView(ignoreIpaCheckbox);
        settingsLayout.addView(onScreenDebugOverlayCheckbox);
        settingsLayout.addView(showPerfOverlayCheckbox);
        settingsLayout.addView(cpuRenderCheckbox);
        settingsLayout.addView(nativeRootMmapCheckbox);
        settingsLayout.addView(esModeButton);
        settingsLayout.addView(settingsBackButton, backParams);

        aboutButton.setOnClickListener(v -> {
            launcherGrid.setVisibility(View.GONE);
            bottomButtonsLayout.setVisibility(View.GONE);
            noGamesText.setVisibility(View.GONE);
            aboutLayout.setVisibility(View.VISIBLE);
        });

        settingsButton.setOnClickListener(v -> {
            launcherGrid.setVisibility(View.GONE);
            bottomButtonsLayout.setVisibility(View.GONE);
            noGamesText.setVisibility(View.GONE);
            settingsLayout.setVisibility(View.VISIBLE);
        });

        View.OnClickListener backAction = v -> {
            aboutLayout.setVisibility(View.GONE);
            settingsLayout.setVisibility(View.GONE);
            if (installedApps.isEmpty()) {
                noGamesText.setVisibility(View.VISIBLE);
            } else {
                launcherGrid.setVisibility(View.VISIBLE);
            }
            bottomButtonsLayout.setVisibility(View.VISIBLE);
        };
        aboutBackButton.setOnClickListener(backAction);
        settingsBackButton.setOnClickListener(backAction);

        rootLayout.addView(launcherGrid);
        rootLayout.addView(noGamesText);
        rootLayout.addView(unpackLayout);
        rootLayout.addView(deleteLayout);
        rootLayout.addView(scrollView);
        rootLayout.addView(aboutLayout);
        rootLayout.addView(commandsLayout);
        rootLayout.addView(settingsLayout);
        rootLayout.addView(loggingFiltersLayout);
        rootLayout.addView(loggingSpamFiltersLayout);
        rootLayout.addView(gpuOffloadLayout);
        rootLayout.addView(bottomButtonsLayout, bottomParams);

        TextView versionTextView = new TextView(this) {
            @Override
            protected void onDraw(android.graphics.Canvas canvas) {
                getPaint().setStyle(android.graphics.Paint.Style.STROKE);
                getPaint().setStrokeWidth(4f);
                setTextColor(Color.BLACK);
                super.onDraw(canvas);
                getPaint().setStyle(android.graphics.Paint.Style.FILL);
                setTextColor(Color.WHITE);
                super.onDraw(canvas);
            }
        };
        try {
            android.content.pm.PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            versionTextView.setText("DamnWrapper32 (ARMv7) v" + pInfo.versionName);
        } catch (Exception e) {
            versionTextView.setText("DamnWrapper32 (ARMv7)");
        }
        versionTextView.setTextSize(14f);
        versionTextView.setTypeface(Typeface.DEFAULT_BOLD);
        FrameLayout.LayoutParams verParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        verParams.gravity = Gravity.BOTTOM | Gravity.LEFT;
        verParams.setMargins(20, 20, 20, 20);
        rootLayout.addView(versionTextView, verParams);

        setContentView(rootLayout);

        try { new File(WORK_DIR + "damn32_log.txt").delete(); } catch(Exception e) {}

        String currentDateAndTime = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(new Date());
        addLog("=== Запуск DamnWrapper32 (ARMv7) ===");
        addLog("Дата и время: " + currentDateAndTime);

        final Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            String crashLog = "=== JAVA CRASH ===\n" + throwable.toString() + "\n";
            for (StackTraceElement element : throwable.getStackTrace()) {
                crashLog += "\tat " + element.toString() + "\n";
            }
            addLog(crashLog);
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable);
            } else {
                System.exit(2);
            }
        });

        checkPermissions();
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                Uri uri = Uri.fromParts("package", getPackageName(), null);
                intent.setData(uri);
                startActivityForResult(intent, 228);
                return;
            }
        } else if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
            return;
        }
        safeSetupDirectories();
    }

    private void safeSetupDirectories() {
        if (!isSetupStarted) {
            isSetupStarted = true;
            setupDirectories();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                safeSetupDirectories();
            } else {
                android.widget.Toast.makeText(this, "Permission required for DamnWrapper32 (ARMv7)!", android.widget.Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 228) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    safeSetupDirectories();
                } else {
                    android.widget.Toast.makeText(this, "Permission required for DamnWrapper32 (ARMv7)!", android.widget.Toast.LENGTH_LONG).show();
                    finish();
                }
            }
        }
    }

    private void setupDirectories() {
        new File(WORK_DIR).mkdirs();
        new File(APPS_DIR).mkdirs();
        new File(APPS_INSTALLED_DIR).mkdirs();
        new File(SETUP_DIR).mkdirs();
        try {
            File optionsFile = new File(WORK_DIR + "damn32_options.txt");
            if (!optionsFile.exists()) {
                optionsFile.createNewFile();
            }
        } catch (Exception e) {}
        addLog("Root: Больше не требуется, используется механизм Total Rebase.");
        extractSetup();
        loadWallpaper();
        new Thread(this::scanAndUnpackApps).start();
    }

    private void loadWallpaper() {
        File setupDir = new File(SETUP_DIR);
        File bestWallpaper = null;
        long latestTime = 0;
        if (setupDir.exists()) {
            File[] files = setupDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isFile() && f.lastModified() > latestTime) {
                        BitmapFactory.Options opt = new BitmapFactory.Options();
                        opt.inJustDecodeBounds = true;
                        BitmapFactory.decodeFile(f.getAbsolutePath(), opt);
                        if (opt.outWidth > 0 && opt.outHeight > 0) {
                            bestWallpaper = f;
                            latestTime = f.lastModified();
                        }
                    }
                }
            }
        }

        View wallpaperView = null;
        if (bestWallpaper != null) {
            wallpaperView = createWallpaperView(this, bestWallpaper);
        }

        if (wallpaperView != null) {
            rootLayout.addView(wallpaperView, 0, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        } else {
            TextView wallpaperHintText = new TextView(this);
            wallpaperHintText.setText("To use own wallpaper put image to DamnWrapper32_ARMv7/setup");
            wallpaperHintText.setTextColor(Color.GRAY);
            wallpaperHintText.setTextSize(10f);
            wallpaperHintText.setGravity(Gravity.CENTER);
            FrameLayout.LayoutParams hintParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
            hintParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            hintParams.bottomMargin = 280;
            // Устанавливаем index 0, чтобы текст был на заднем плане
            rootLayout.addView(wallpaperHintText, 0, hintParams);
        }
    }

    private void extractSetup() {
        try {
            String[] assets = getAssets().list("");
            for (String asset : assets) {
                try {
                    File outFile = new File(SETUP_DIR, asset);
                    if (!outFile.exists()) {
                        InputStream is = getAssets().open(asset);
                        FileOutputStream fos = new FileOutputStream(outFile);
                        byte[] buf = new byte[8192]; int len;
                        while ((len = is.read(buf)) > 0) fos.write(buf, 0, len);
                        is.close(); fos.close();
                    }
                } catch (Exception innerE) {}
            }
        } catch (Exception e) {}
    }

    private void startGameThread(String workDir, String appDirPath, String bundleId, boolean logRender, boolean logSound, boolean logFs, boolean logNet, boolean logTodo, boolean logRenderDebug, boolean logFuncList, boolean logHiddenClasses, boolean logOther, int spamMask, boolean onScreenDebugOverlay, boolean showPerfOverlay, boolean nativeRootMmap, int targetW, int targetH, int esMode, int gpuOffloadMask) {
        new Thread(() -> {
            if (nativeRootMmap) {
                try {
                    Runtime.getRuntime().exec(new String[]{"su", "-c", "setenforce 1"}).waitFor();
                    Thread.sleep(500);
                    Runtime.getRuntime().exec(new String[]{"su", "-c", "setenforce 0"}).waitFor();
                    Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "getenforce"});
                    java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()));
                    String selinuxStatus = reader.readLine();
                    if (selinuxStatus == null || (!selinuxStatus.toLowerCase().contains("permissive") && !selinuxStatus.toLowerCase().contains("disabled"))) {
                        runOnUiThread(() -> addLog("ОШИБКА: Не удалось перевести SELinux в Permissive!"));
                        return;
                    }
                } catch (Exception e) {
                    runOnUiThread(() -> addLog("ОШИБКА: Нет Root прав или сбой SELinux!"));
                    return;
                }
            }
            initWrapper(workDir, appDirPath, bundleId, logRender, logSound, logFs, logNet, logTodo, logRenderDebug, logFuncList, logHiddenClasses, logOther, spamMask, onScreenDebugOverlay, showPerfOverlay, nativeRootMmap, targetW, targetH, esMode, gpuOffloadMask);
        }).start();
    }

    private AppInfo peekIpaInfo(File zipFile) {
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(zipFile)) {
            java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                java.util.zip.ZipEntry entry = entries.nextElement();
                String entryName = entry.getName();
                if (entryName.startsWith("Payload/") && entryName.endsWith(".app/Info.plist")) {
                    int slashes = 0;
                    for (int i = 0; i < entryName.length(); i++) {
                        if (entryName.charAt(i) == '/') slashes++;
                    }
                    if (slashes == 2) {
                        File tempPlist = new File(APPS_INSTALLED_DIR, "temp_Info.plist");
                        try (InputStream is = zip.getInputStream(entry); FileOutputStream fos = new FileOutputStream(tempPlist)) {
                            byte[] buffer = new byte[8192]; int len;
                            while ((len = is.read(buffer)) > 0) fos.write(buffer, 0, len);
                        }
                        HashMap<String, Object> plist = BplistParser.parse(tempPlist);
                        tempPlist.delete();
                        
                        AppInfo info = new AppInfo();
                        info.bundleId = (String) plist.getOrDefault("CFBundleIdentifier", "unknown.app");
                        info.version = (String) plist.getOrDefault("CFBundleVersion", "1.0");
                        info.name = (String) plist.getOrDefault("CFBundleDisplayName", plist.getOrDefault("CFBundleName", "Unknown"));
                        return info;
                    }
                }
            }
        } catch (Exception e) {}
        return null;
    }

    private void scanAndUnpackApps() {
        try {
            File optionsFile = new File(WORK_DIR + "damn32_options.txt");
            if (optionsFile.exists()) {
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(optionsFile));
                String line = reader.readLine();
                reader.close();
                if (line != null && line.startsWith("-launch ")) {
                    String targetApp = line.substring(8).trim();
                    File targetDir = new File(APPS_INSTALLED_DIR, targetApp);
                    if (targetDir.exists() && targetDir.isDirectory()) {
                        File payload = new File(targetDir, "Payload");
                        File appDirToLaunch = null;
                        if (payload.exists() && payload.listFiles() != null) {
                            for (File appDir : payload.listFiles()) {
                                if (appDir.getName().endsWith(".app")) {
                                    appDirToLaunch = appDir;
                                    break;
                                }
                            }
                        }
                        if (appDirToLaunch != null) {
                            HashMap<String, Object> plist = BplistParser.parse(new File(appDirToLaunch, "Info.plist"));
                            String bundleId = (String) plist.getOrDefault("CFBundleIdentifier", "unknown");
                            File finalAppDir = appDirToLaunch;
                            runOnUiThread(() -> {
                                if (bottomButtonsLayout != null) bottomButtonsLayout.setVisibility(View.GONE);
                                scrollView.setVisibility(View.VISIBLE);
                                logTextView.setText("");
                                try { new File(WORK_DIR + "damn32_log.txt").delete(); } catch(Exception e) {}
                                addLog("=== Запуск DamnWrapper32 (ARMv7) (Auto-launch) ===");
                                addLog("Picked: " + finalAppDir.getAbsolutePath());
                                boolean logRender = getSharedPreferences("DamnPrefs", MODE_PRIVATE).getBoolean("log_render", false);
                                boolean logSound = getSharedPreferences("DamnPrefs", MODE_PRIVATE).getBoolean("log_sound", false);
                                boolean logFs = getSharedPreferences("DamnPrefs", MODE_PRIVATE).getBoolean("log_fs", false);
                                boolean logNet = getSharedPreferences("DamnPrefs", MODE_PRIVATE).getBoolean("log_net", false);
                                boolean logTodo = getSharedPreferences("DamnPrefs", MODE_PRIVATE).getBoolean("log_todo", false);
                                boolean logRenderDebug = getSharedPreferences("DamnPrefs", MODE_PRIVATE).getBoolean("log_render_debug", false);
                                boolean logFuncList = getSharedPreferences("DamnPrefs", MODE_PRIVATE).getBoolean("log_func_list", false);
                                boolean logHiddenClasses = getSharedPreferences("DamnPrefs", MODE_PRIVATE).getBoolean("log_hidden_classes", false);
                                boolean logOther = getSharedPreferences("DamnPrefs", MODE_PRIVATE).getBoolean("log_other", false);
                                boolean onScreenDebugOverlay = getSharedPreferences("DamnPrefs", MODE_PRIVATE).getBoolean("onscreen_debug_overlay", false);
                                boolean showPerfOverlay = getSharedPreferences("DamnPrefs", MODE_PRIVATE).getBoolean("show_perf_overlay", false);
                                boolean nativeRootMmap = getSharedPreferences("DamnPrefs", MODE_PRIVATE).getBoolean("native_root_mmap", false);
                                int targetW = getSharedPreferences("DamnPrefs", MODE_PRIVATE).getInt("res_width", 480);
                                int targetH = getSharedPreferences("DamnPrefs", MODE_PRIVATE).getInt("res_height", 320);
                                int esMode = getSharedPreferences("DamnPrefs", MODE_PRIVATE).getInt("es_mode", 2);
                                int tempSpamMask = 0;
                                String[] sfKeys = {"spam_log_render", "spam_log_sound", "spam_log_fs", "spam_log_net", "spam_log_todo", "spam_log_render_debug", "spam_log_func_list", "spam_log_hidden_classes", "spam_log_other"};
                                for (int j = 0; j < 9; j++) if (getSharedPreferences("DamnPrefs", MODE_PRIVATE).getBoolean(sfKeys[j], true)) tempSpamMask |= (1 << j);
                                final int finalSpamMask = tempSpamMask;
                                int gpuMask = 0;
                                for (int j = 0; j < 7; j++) if (getSharedPreferences("DamnPrefs", MODE_PRIVATE).getBoolean("gpu_bit_" + j, false)) gpuMask |= (1 << j);
                                startGameThread(WORK_DIR, finalAppDir.getAbsolutePath(), bundleId, logRender, logSound, logFs, logNet, logTodo, logRenderDebug, logFuncList, logHiddenClasses, logOther, finalSpamMask, onScreenDebugOverlay, showPerfOverlay, nativeRootMmap, targetW, targetH, esMode, gpuMask);
                            });
                            return;
                        }
                    }
                    final String failMsg = "Command error: no app " + targetApp + " founded";
                    runOnUiThread(() -> android.widget.Toast.makeText(MainActivity.this, failMsg, android.widget.Toast.LENGTH_LONG).show());
                }
            }
        } catch (Exception e) {}

        availableIpas.clear();
        File appsDir = new File(APPS_DIR);
        File[] ipaFiles = appsDir.listFiles((dir, name) -> name.endsWith(".ipa"));

        if (ipaFiles != null && ipaFiles.length > 0) {
            android.content.SharedPreferences prefs = getSharedPreferences("DamnPrefs", MODE_PRIVATE);
            int deleteChoice = prefs.getInt("delete_ipa", -1);
            
            if (deleteChoice == -1) {
                runOnUiThread(() -> {
                    new android.app.AlertDialog.Builder(MainActivity.this)
                        .setTitle("Delete IPA's after installation?")
                        .setMessage("(This applies to all IPA files)")
                        .setCancelable(false)
                        .setPositiveButton("Yes", (d, w) -> { prefs.edit().putInt("delete_ipa", 1).apply(); new Thread(this::scanAndUnpackApps).start(); })
                        .setNegativeButton("No", (d, w) -> { prefs.edit().putInt("delete_ipa", 0).apply(); new Thread(this::scanAndUnpackApps).start(); })
                        .show();
                });
                return; // Ждем выбора пользователя
            }

            for (File targetIpa : ipaFiles) {
                AppInfo peekInfo = peekIpaInfo(targetIpa);
                if (peekInfo != null) {
                    availableIpas.put(peekInfo.bundleId + "_" + peekInfo.version, targetIpa.getAbsolutePath());
                    if (deletedInThisSession.contains(peekInfo.bundleId + "_" + peekInfo.version)) {
                        continue;
                    }

                    File finalDir = new File(APPS_INSTALLED_DIR, peekInfo.bundleId + "_" + peekInfo.version);
                    boolean ignoreSameIpa = getSharedPreferences("DamnPrefs", MODE_PRIVATE).getBoolean("ignore_same_ipa", true);
                    if (finalDir.exists() && ignoreSameIpa) {
                        if (deleteChoice == 1) targetIpa.delete();
                        continue; // Пропускаем полную распаковку, приложение этой версии уже стоит
                    }
                    
                    runOnUiThread(() -> { 
                        unpackLayout.setVisibility(View.VISIBLE);
                        if (bottomButtonsLayout != null) bottomButtonsLayout.setVisibility(View.GONE); // Прячем кнопки при распаковке
                        unpackText.setText("Unpacking: " + targetIpa.getName()); 
                        unpackAppName.setText("App: " + peekInfo.name);
                        unpackPkgName.setText("Package: " + peekInfo.bundleId);
                        unpackVersion.setText("Version: " + peekInfo.version);
                        unpackProgress.setProgress(0); 
                    });
                } else {
                    runOnUiThread(() -> { 
                        unpackLayout.setVisibility(View.VISIBLE);
                        if (bottomButtonsLayout != null) bottomButtonsLayout.setVisibility(View.GONE); // Прячем кнопки при распаковке
                        unpackText.setText("Unpacking: " + targetIpa.getName()); 
                        unpackAppName.setText(""); unpackPkgName.setText(""); unpackVersion.setText("");
                        unpackProgress.setProgress(0); 
                    });
                }

                File tempExtractDir = new File(APPS_INSTALLED_DIR, "temp_extract_" + System.currentTimeMillis());
                unzipWithProgress(targetIpa, tempExtractDir);
                
                File payloadDir = new File(tempExtractDir, "Payload");
                if (payloadDir.exists() && payloadDir.listFiles() != null) {
                    for (File appDir : payloadDir.listFiles()) {
                        if (appDir.getName().endsWith(".app")) {
                            HashMap<String, Object> plist = BplistParser.parse(new File(appDir, "Info.plist"));
                            String bundleId = (String) plist.getOrDefault("CFBundleIdentifier", "unknown.app");
                            String version = (String) plist.getOrDefault("CFBundleVersion", "1.0");
                            File finalDir = new File(APPS_INSTALLED_DIR, bundleId + "_" + version);
                            
                            if (finalDir.exists()) deleteRecursive(finalDir);
                            tempExtractDir.renameTo(finalDir);
                            if (deleteChoice == 1) targetIpa.delete();
                            break;
                        }
                    }
                }
                if (tempExtractDir.exists()) deleteRecursive(tempExtractDir);
            }
        }

        // Сканируем установленные
        installedApps.clear();
        File installedDir = new File(APPS_INSTALLED_DIR);
        File[] gameFolders = installedDir.listFiles();

        android.content.SharedPreferences prefs = getSharedPreferences("DamnPrefs", MODE_PRIVATE);
        java.util.Map<String, ?> allEntries = prefs.getAll();
        for (java.util.Map.Entry<String, ?> entry : allEntries.entrySet()) {
            if (entry.getKey().startsWith("custom_name_")) {
                String folderName = entry.getKey().substring(12);
                if (!new File(APPS_INSTALLED_DIR, folderName).exists()) {
                    prefs.edit().remove(entry.getKey()).apply();
                }
            }
        }

        if (gameFolders != null) {
            for (File gameFolder : gameFolders) {
                if (!gameFolder.isDirectory()) continue;
                File payload = new File(gameFolder, "Payload");
                if (payload.exists() && payload.listFiles() != null) {
                    for (File appDir : payload.listFiles()) {
                        if (appDir.getName().endsWith(".app")) {
                            HashMap<String, Object> plist = BplistParser.parse(new File(appDir, "Info.plist"));
                            AppInfo info = new AppInfo();
                            info.bundleId = (String) plist.getOrDefault("CFBundleIdentifier", "unknown");
                            info.version = (String) plist.getOrDefault("CFBundleVersion", "1.0");
                            info.name = (String) plist.getOrDefault("CFBundleDisplayName", plist.getOrDefault("CFBundleName", gameFolder.getName()));
                            String customName = prefs.getString("custom_name_" + gameFolder.getName(), null);
                            if (customName != null) info.name = customName;
                            info.appDirPath = appDir.getAbsolutePath();
                            info.internalName = appDir.getName();
                            info.minOS = (String) plist.getOrDefault("MinimumOSVersion", "Unknown");
                            info.targetOS = (String) plist.getOrDefault("DTPlatformVersion", "Unknown");
                            Object familyObj = plist.get("UIDeviceFamily");
                            if (familyObj instanceof ArrayList) {
                                ArrayList famList = (ArrayList) familyObj;
                                boolean hasPhone = famList.contains(1) || famList.contains(1L) || famList.contains("1");
                                boolean hasPad = famList.contains(2) || famList.contains(2L) || famList.contains("2");
                                if (hasPhone && hasPad) info.deviceFamily = "Universal";
                                else if (hasPad) info.deviceFamily = "iPad";
                                else if (hasPhone) info.deviceFamily = "iPhone";
                                else info.deviceFamily = "Unknown";
                            } else if (familyObj != null) {
                                String f = familyObj.toString();
                                if (f.equals("1")) info.deviceFamily = "iPhone";
                                else if (f.equals("2")) info.deviceFamily = "iPad";
                                else if (f.equals("Universal")) info.deviceFamily = "Universal";
                                else info.deviceFamily = f;
                            } else {
                                info.deviceFamily = "iPhone";
                            }
                            
                            // Поиск иконки: Приоритет на iTunesArtwork (как правило, не CgBI и в высоком разрешении)
                            File artworkRoot = new File(gameFolder, "iTunesArtwork");
                            File artworkApp = new File(appDir, "iTunesArtwork");
                            if (artworkRoot.exists()) {
                                info.iconPath = artworkRoot.getAbsolutePath();
                            } else if (artworkApp.exists()) {
                                info.iconPath = artworkApp.getAbsolutePath();
                            } else {
                                String iconName = (String) plist.get("CFBundleIconFile");
                                if (iconName == null && plist.get("CFBundleIcons") instanceof HashMap) {
                                    HashMap icons = (HashMap) plist.get("CFBundleIcons");
                                    if (icons.get("CFBundlePrimaryIcon") instanceof HashMap) {
                                        HashMap primary = (HashMap) icons.get("CFBundlePrimaryIcon");
                                        if (primary.get("CFBundleIconFiles") instanceof ArrayList) {
                                            ArrayList list = (ArrayList) primary.get("CFBundleIconFiles");
                                            if (!list.isEmpty()) iconName = (String) list.get(list.size() - 1);
                                        }
                                    }
                                }
                                if (iconName != null) {
                                    if (!iconName.endsWith(".png")) iconName += ".png";
                                    info.iconPath = new File(appDir, iconName).getAbsolutePath();
                                }
                                
                                // Если по plist не нашлось или файла нет, перебираем жестко заданные имена
                                if (info.iconPath == null || !new File(info.iconPath).exists()) {
                                    String[] fallbacks = {"Icon@2x.png", "Icon-72.png", "Icon-72@2x.png", "Icon.png", "icon.png"};
                                    for (String f : fallbacks) {
                                        File fallbackFile = new File(appDir, f);
                                        if (fallbackFile.exists()) {
                                            info.iconPath = fallbackFile.getAbsolutePath();
                                            break;
                                        }
                                    }
                                }
                            }
                            installedApps.add(info);
                        }
                    }
                }
            }
        }

        runOnUiThread(() -> {
            unpackLayout.setVisibility(View.GONE);
            if (installedApps.isEmpty()) {
                noGamesText.setVisibility(View.VISIBLE);
            } else {
                launcherGrid.setVisibility(View.VISIBLE);
                launcherGrid.setAdapter(new AppsAdapter());
            }
            if (bottomButtonsLayout != null) bottomButtonsLayout.setVisibility(View.VISIBLE);
        });
    }

    private void unzipWithProgress(File zipFile, File targetDirectory) {
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(zipFile)) {
            targetDirectory.mkdirs();
            java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zip.entries();
            int totalFiles = zip.size();
            java.util.concurrent.atomic.AtomicInteger processedFiles = new java.util.concurrent.atomic.AtomicInteger(0);
            int[] lastReportedProgress = {0};
            int processors = Math.max(2, Runtime.getRuntime().availableProcessors());
            java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(processors);

            while (entries.hasMoreElements()) {
                java.util.zip.ZipEntry entry = entries.nextElement();
                executor.execute(() -> {
                    File file = new File(targetDirectory, entry.getName());
                    if (entry.isDirectory()) {
                        file.mkdirs();
                    } else {
                        file.getParentFile().mkdirs();
                        try (InputStream is = zip.getInputStream(entry);
                             FileOutputStream fos = new FileOutputStream(file)) {
                            byte[] buffer = new byte[65536]; int len;
                            while ((len = is.read(buffer)) > 0) fos.write(buffer, 0, len);
                        } catch (Exception e) {}
                    }
                    int current = processedFiles.incrementAndGet();
                    int progress = (int) ((current * 100f) / totalFiles);
                    synchronized (lastReportedProgress) {
                        if (progress > lastReportedProgress[0]) {
                            lastReportedProgress[0] = progress;
                            runOnUiThread(() -> unpackProgress.setProgress(progress));
                        }
                    }
                });
            }
            executor.shutdown();
            executor.awaitTermination(1, java.util.concurrent.TimeUnit.HOURS);
        } catch (Exception e) {}
    }

    private void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory())
            for (File child : fileOrDirectory.listFiles()) deleteRecursive(child);
        fileOrDirectory.delete();
    }

    // Класс-модель игры
    private static class AppInfo { String name, bundleId, version, iconPath, appDirPath, internalName, minOS, targetOS, deviceFamily; }

    // Адаптер сетки лаунчера
    private class AppsAdapter extends BaseAdapter {
        @Override public int getCount() { return installedApps.size(); }
        @Override public Object getItem(int i) { return installedApps.get(i); }
        @Override public long getItemId(int i) { return i; }
        @Override public View getView(int i, View view, ViewGroup viewGroup) {
            LinearLayout layout = new LinearLayout(MainActivity.this);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setGravity(Gravity.CENTER);
            
            AppInfo app = installedApps.get(i);
            ImageView icon = new ImageView(MainActivity.this);
            icon.setLayoutParams(new LinearLayout.LayoutParams(160, 160));
            icon.setClipToOutline(true);
            
            GradientDrawable fallbackBg = new GradientDrawable();
            fallbackBg.setColor(Color.DKGRAY);
            fallbackBg.setCornerRadius(30f);
            
            if (app.iconPath != null && new File(app.iconPath).exists()) {
                Bitmap bmp = BitmapFactory.decodeFile(app.iconPath);
                // Проверка на CgBI-проблему: если Android загрузил битмап, но он полностью прозрачный (сбой альфа-канала)
                if (bmp != null) {
                    boolean isSuspicious = false;
                    if (bmp.getWidth() > 0 && bmp.getHeight() > 0) {
                        int centerPixel = bmp.getPixel(bmp.getWidth() / 2, bmp.getHeight() / 2);
                        if (Color.alpha(centerPixel) == 0) { // Центр иконки IOS никогда не должен быть абсолютно прозрачным
                            isSuspicious = true;
                        }
                    }
                    if (!isSuspicious) {
                        icon.setImageBitmap(bmp);
                    } else {
                        setFallbackIcon(icon, fallbackBg);
                    }
                } else {
                    setFallbackIcon(icon, fallbackBg);
                }
            } else {
                setFallbackIcon(icon, fallbackBg);
            }

            FrameLayout iconContainer = new FrameLayout(MainActivity.this);
            iconContainer.setLayoutParams(new LinearLayout.LayoutParams(160, 160));
            icon.setLayoutParams(new FrameLayout.LayoutParams(160, 160));
            iconContainer.addView(icon);

            TextView versionText = new TextView(MainActivity.this) {
                @Override
                protected void onDraw(android.graphics.Canvas canvas) {
                    getPaint().setStyle(android.graphics.Paint.Style.STROKE);
                    getPaint().setStrokeWidth(4f);
                    setTextColor(Color.BLACK);
                    super.onDraw(canvas);
                    getPaint().setStyle(android.graphics.Paint.Style.FILL);
                    setTextColor(Color.WHITE);
                    super.onDraw(canvas);
                }
            };
            versionText.setText(app.version);
            versionText.setTextSize(10f);
            versionText.setSingleLine(true);
            versionText.setEllipsize(android.text.TextUtils.TruncateAt.END);
            versionText.setGravity(Gravity.CENTER);
            versionText.setTypeface(Typeface.DEFAULT_BOLD);
            versionText.setPadding(4, 0, 4, 0);
            FrameLayout.LayoutParams verParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
            verParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            verParams.bottomMargin = 2;
            iconContainer.addView(versionText, verParams);

            TextView name = new TextView(MainActivity.this);
            name.setText(app.name);
            name.setTextColor(Color.WHITE);
            name.setTextSize(12f);
            name.setGravity(Gravity.CENTER);
            name.setPadding(0, 10, 0, 0);
            name.setSingleLine(true);
            
            layout.addView(iconContainer); layout.addView(name);
            
            layout.setOnLongClickListener(v -> {
                boolean canReinstall = availableIpas.containsKey(app.bundleId + "_" + app.version);
                String[] options = canReinstall ? new String[]{"Rename app", "Delete app", "Reinstall"} : new String[]{"Rename app", "Delete app"};
                new android.app.AlertDialog.Builder(MainActivity.this)
                    .setTitle(app.name)
                    .setItems(options, (dialog, which) -> {
                        if (which == 0) {
                            android.widget.EditText input = new android.widget.EditText(MainActivity.this);
                            input.setText(app.name);
                            new android.app.AlertDialog.Builder(MainActivity.this)
                                .setTitle("Rename app")
                                .setView(input)
                                .setPositiveButton("OK", (d, w) -> {
                                    String newName = input.getText().toString();
                                    getSharedPreferences("DamnPrefs", MODE_PRIVATE).edit()
                                        .putString("custom_name_" + app.bundleId + "_" + app.version, newName).apply();
                                    app.name = newName;
                                    notifyDataSetChanged();
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                        } else if (which == 1) {
                            deleteAppWithProgress(app, false);
                        } else if (which == 2) {
                            deleteAppWithProgress(app, true);
                        }
                    })
                    .show();
                return true;
            });

            layout.setOnClickListener(v -> {
                launcherGrid.setVisibility(View.GONE);
                if (bottomButtonsLayout != null) bottomButtonsLayout.setVisibility(View.GONE);
                scrollView.setVisibility(View.VISIBLE); // Показываем логи
                
                logTextView.setText("");
                try { new File(WORK_DIR + "damn32_log.txt").delete(); } catch(Exception e) {}
                String currentDateAndTime = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(new Date());
                addLog("=== Запуск DamnWrapper32 (ARMv7) ===");
                addLog("Дата и время: " + currentDateAndTime);
                boolean isNativeMmap = getSharedPreferences("DamnPrefs", MODE_PRIVATE).getBoolean("native_root_mmap", false);
                if (isNativeMmap) {
                    addLog("ВНИМАНИЕ: Включен Native root mmap! Игра будет загружена по оригинальным адресам.");
                } else {
                    addLog("Механизм Total Rebase активен, игра будет загружена по динамическому смещению.");
                }

                if (!new File(SETUP_DIR, "Roboto-VariableFont_wdth,wght.ttf").exists()) {
                    addLog("HLE: ОШИБКА загрузки TTF шрифта! Проверьте наличие в папке setup.");
                }
                
                addLog("Picked: " + app.appDirPath);
                addLog("- Name: " + app.name);
                addLog("- Version: " + app.version);
                addLog("- Identifier: " + app.bundleId);
                addLog("- Internal name (canonical): " + app.internalName);
                addLog("- Minimum IOS version: " + app.minOS);
                addLog("- Target IOS version: " + app.targetOS);
                addLog("- Device family: " + app.deviceFamily);
                boolean logRender = getSharedPreferences("DamnPrefs", MODE_PRIVATE).getBoolean("log_render", false);
                boolean logSound = getSharedPreferences("DamnPrefs", MODE_PRIVATE).getBoolean("log_sound", false);
                boolean logFs = getSharedPreferences("DamnPrefs", MODE_PRIVATE).getBoolean("log_fs", false);
                boolean logNet = getSharedPreferences("DamnPrefs", MODE_PRIVATE).getBoolean("log_net", false);
                boolean logTodo = getSharedPreferences("DamnPrefs", MODE_PRIVATE).getBoolean("log_todo", false);
                boolean logRenderDebug = getSharedPreferences("DamnPrefs", MODE_PRIVATE).getBoolean("log_render_debug", false);
                boolean logFuncList = getSharedPreferences("DamnPrefs", MODE_PRIVATE).getBoolean("log_func_list", false);
                boolean logHiddenClasses = getSharedPreferences("DamnPrefs", MODE_PRIVATE).getBoolean("log_hidden_classes", false);
                boolean logOther = getSharedPreferences("DamnPrefs", MODE_PRIVATE).getBoolean("log_other", false);
                boolean onScreenDebugOverlay = getSharedPreferences("DamnPrefs", MODE_PRIVATE).getBoolean("onscreen_debug_overlay", false);
                boolean showPerfOverlay = getSharedPreferences("DamnPrefs", MODE_PRIVATE).getBoolean("show_perf_overlay", false);
                boolean nativeRootMmap = getSharedPreferences("DamnPrefs", MODE_PRIVATE).getBoolean("native_root_mmap", false);
                int targetW = getSharedPreferences("DamnPrefs", MODE_PRIVATE).getInt("res_width", 480);
                int targetH = getSharedPreferences("DamnPrefs", MODE_PRIVATE).getInt("res_height", 320);
                int esMode = getSharedPreferences("DamnPrefs", MODE_PRIVATE).getInt("es_mode", 2);
                int tempSpamMask = 0;
                String[] sfKeys = {"spam_log_render", "spam_log_sound", "spam_log_fs", "spam_log_net", "spam_log_todo", "spam_log_render_debug", "spam_log_func_list", "spam_log_hidden_classes", "spam_log_other"};
                for (int j = 0; j < 9; j++) if (getSharedPreferences("DamnPrefs", MODE_PRIVATE).getBoolean(sfKeys[j], true)) tempSpamMask |= (1 << j);
                final int finalSpamMask = tempSpamMask;
                int gpuMask = 0;
                for (int j = 0; j < 7; j++) if (getSharedPreferences("DamnPrefs", MODE_PRIVATE).getBoolean("gpu_bit_" + j, false)) gpuMask |= (1 << j);
                startGameThread(WORK_DIR, app.appDirPath, app.bundleId, logRender, logSound, logFs, logNet, logTodo, logRenderDebug, logFuncList, logHiddenClasses, logOther, finalSpamMask, onScreenDebugOverlay, showPerfOverlay, nativeRootMmap, targetW, targetH, esMode, gpuMask);
            });
            return layout;
        }

        private void setFallbackIcon(ImageView iv, GradientDrawable bg) {
            iv.setBackground(bg);
            iv.setImageTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
            // Рисуем восклицательный знак
            Bitmap bmp = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888);
            android.graphics.Canvas c = new android.graphics.Canvas(bmp);
            android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            p.setColor(Color.WHITE); p.setTextSize(80f); p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextAlign(android.graphics.Paint.Align.CENTER);
            c.drawText("!", 80, 110, p);
            iv.setImageBitmap(bmp);
        }
    }

    // Суперкомпактный парсер Apple Binary PList для Info.plist (Поддерживает Dict, Array, Strings)
    public static class BplistParser {
        public static HashMap<String, Object> parse(File file) {
            HashMap<String, Object> result = new HashMap<>();
            try {
                byte[] data = new byte[(int) file.length()];
                FileInputStream fis = new FileInputStream(file); fis.read(data); fis.close();
                if (data.length < 40 || !new String(data, 0, 8).equals("bplist00")) return extractXmlFallback(new String(data));
                
                int trailerOff = data.length - 32;
                int offsetSize = data[trailerOff + 6];
                int refSize = data[trailerOff + 7];
                int numObjects = (int) readInt(data, trailerOff + 12, 4);
                int topObj = (int) readInt(data, trailerOff + 20, 4);
                int offsetTableOff = (int) readInt(data, trailerOff + 28, 4);
                
                int[] offsets = new int[numObjects];
                for (int i = 0; i < numObjects; i++) offsets[i] = (int) readInt(data, offsetTableOff + i * offsetSize, offsetSize);
                
                Object top = decodeObj(data, offsets, topObj, refSize);
                if (top instanceof HashMap) return (HashMap<String, Object>) top;
            } catch (Exception e) {}
            return result;
        }

        private static HashMap<String, Object> extractXmlFallback(String xml) {
            HashMap<String, Object> res = new HashMap<>();
            String[] keys = {"CFBundleIdentifier", "CFBundleVersion", "CFBundleDisplayName", "CFBundleName", "CFBundleIconFile", "MinimumOSVersion", "DTPlatformVersion"};
            int uidIdx = xml.indexOf("<key>UIDeviceFamily</key>");
            if (uidIdx != -1) {
                int arrStart = xml.indexOf("<array>", uidIdx);
                int arrEnd = xml.indexOf("</array>", arrStart);
                if (arrStart > 0 && arrEnd > arrStart) {
                    String arrXml = xml.substring(arrStart, arrEnd);
                    if (arrXml.contains("1") && arrXml.contains("2")) res.put("UIDeviceFamily", "Universal");
                    else if (arrXml.contains("2")) res.put("UIDeviceFamily", "iPad");
                    else res.put("UIDeviceFamily", "iPhone");
                }
            }
            for (String k : keys) {
                int idx = xml.indexOf("<key>" + k + "</key>");
                if (idx != -1) {
                    int start = xml.indexOf("<string>", idx) + 8;
                    int end = xml.indexOf("</string>", start);
                    if (start > 7 && end > start) res.put(k, xml.substring(start, end));
                }
            }
            return res;
        }

        private static Object decodeObj(byte[] data, int[] offsets, int objIdx, int refSize) {
            int off = offsets[objIdx];
            int type = data[off] & 0xFF;
            int objType = type & 0xF0;
            if (objType == 0x10) {
                int len = (int) Math.pow(2, type & 0x0F);
                return readInt(data, off + 1, len);
            } else if (objType == 0x50 || objType == 0x60) {
                int len = type & 0x0F; int start = off + 1;
                if (len == 0x0F) {
                    int intType = data[++off] & 0xFF; int intLen = (int) Math.pow(2, intType & 0x0F);
                    len = (int) readInt(data, ++off, intLen); start = off + intLen;
                }
                try { return objType == 0x50 ? new String(data, start, len, "ASCII") : new String(data, start, len * 2, "UTF-16BE"); } catch(Exception e) { return ""; }
            } else if (objType == 0xD0) {
                int count = type & 0x0F; int start = off + 1;
                if (count == 0x0F) {
                    int intType = data[++off] & 0xFF; int intLen = (int) Math.pow(2, intType & 0x0F);
                    count = (int) readInt(data, ++off, intLen); start = off + intLen;
                }
                HashMap<String, Object> dict = new HashMap<>();
                for (int i = 0; i < count; i++) {
                    int keyRef = (int) readInt(data, start + i * refSize, refSize);
                    int valRef = (int) readInt(data, start + (count + i) * refSize, refSize);
                    Object key = decodeObj(data, offsets, keyRef, refSize);
                    Object val = decodeObj(data, offsets, valRef, refSize);
                    if (key != null && val != null) dict.put(key.toString(), val);
                }
                return dict;
            } else if (objType == 0xA0) {
                int count = type & 0x0F; int start = off + 1;
                if (count == 0x0F) {
                    int intType = data[++off] & 0xFF; int intLen = (int) Math.pow(2, intType & 0x0F);
                    count = (int) readInt(data, ++off, intLen); start = off + intLen;
                }
                ArrayList<Object> list = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    int valRef = (int) readInt(data, start + i * refSize, refSize);
                    list.add(decodeObj(data, offsets, valRef, refSize));
                }
                return list;
            }
            return null;
        }

        private static long readInt(byte[] data, int off, int len) {
            long res = 0; for (int i = 0; i < len; i++) res = (res << 8) | (data[off + i] & 0xFF); return res;
        }
    }

    public void showArchErrorPopup(String arch) {
        runOnUiThread(() -> {
            new android.app.AlertDialog.Builder(MainActivity.this)
                .setTitle("App not supported")
                .setMessage("This app is ARMv" + arch + " - it's not supported, use only ARMv7 apps")
                .setCancelable(false)
                .setPositiveButton("OK", (d, w) -> {
                    scrollView.setVisibility(View.GONE);
                    if (installedApps.isEmpty()) {
                        noGamesText.setVisibility(View.VISIBLE);
                    } else {
                        launcherGrid.setVisibility(View.VISIBLE);
                    }
                    if (bottomButtonsLayout != null) bottomButtonsLayout.setVisibility(View.VISIBLE);
                })
                .show();
        });
    }

    public void showDrmErrorPopup() {
        runOnUiThread(() -> {
            new android.app.AlertDialog.Builder(MainActivity.this)
                .setTitle("App not supported")
                .setMessage("IPA is encrypted, bad file")
                .setCancelable(false)
                .setPositiveButton("OK", (d, w) -> {
                    scrollView.setVisibility(View.GONE);
                    if (installedApps.isEmpty()) {
                        noGamesText.setVisibility(View.VISIBLE);
                    } else {
                        launcherGrid.setVisibility(View.VISIBLE);
                    }
                    if (bottomButtonsLayout != null) bottomButtonsLayout.setVisibility(View.VISIBLE);
                })
                .show();
        });
    }

    private void deleteAppWithProgress(AppInfo app, boolean isReinstall) {
        File targetDir = new File(APPS_INSTALLED_DIR, app.bundleId + "_" + app.version);
        if (!targetDir.exists()) return;
        
        deleteLayout.setVisibility(View.VISIBLE);
        launcherGrid.setVisibility(View.GONE);
        if (bottomButtonsLayout != null) bottomButtonsLayout.setVisibility(View.GONE);
        deleteText.setText(isReinstall ? "Reinstalling..." : "Deleting...");
        deleteAppName.setText("App: " + app.name);
        deletePkgName.setText("Package: " + app.bundleId);
        deleteVersion.setText("Version: " + app.version);
        deleteProgress.setProgress(0);

        new Thread(() -> {
            // counts[0] = total, counts[1] = deleted, counts[2] = lastReportedProgress
            int[] counts = new int[]{0, 0, 0};
            countFiles(targetDir, counts);
            deleteFilesProgress(targetDir, counts);
            targetDir.delete();
            getSharedPreferences("DamnPrefs", MODE_PRIVATE).edit().remove("custom_name_" + app.bundleId + "_" + app.version).apply();
            
            if (isReinstall) {
                deletedInThisSession.remove(app.bundleId + "_" + app.version);
            } else {
                deletedInThisSession.add(app.bundleId + "_" + app.version);
            }

            runOnUiThread(() -> {
                deleteLayout.setVisibility(View.GONE);
                new Thread(this::scanAndUnpackApps).start();
            });
        }).start();
    }
    
    private void countFiles(File dir, int[] counts) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            counts[0]++;
            if (f.isDirectory()) countFiles(f, counts);
        }
    }

    private void deleteFilesProgress(File dir, int[] counts) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) deleteFilesProgress(f, counts);
            f.delete();
            counts[1]++;
            int progress = (int) ((counts[1] * 100f) / counts[0]);
            if (progress > counts[2]) {
                counts[2] = progress;
                runOnUiThread(() -> deleteProgress.setProgress(progress));
            }
        }
    }

    public void addLogFromNative(String msg) { 
        runOnUiThread(() -> {
            logTextView.append(msg + "\n");
            scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
            Log.d("DamnWrapper32_ARMv7", msg);
        }); 
    }

    @SuppressLint("ClickableViewAccessibility")
    public void switchToRender() {
        runOnUiThread(() -> {
            if (isRendering) return;
            isRendering = true;
            addLog("Окно подготовлено. Ожидание вызовов отрисовки от игры...");

            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

            surfaceView = new SurfaceView(this);
            surfaceView.getHolder().addCallback(this);

            FrameLayout renderContainer = new FrameLayout(this);
            renderContainer.setBackgroundColor(Color.BLACK);

            DisplayMetrics metrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
            
            int screenW = Math.max(metrics.widthPixels, metrics.heightPixels);
            int screenH = Math.min(metrics.widthPixels, metrics.heightPixels);

            int targetW = getSharedPreferences("DamnPrefs", MODE_PRIVATE).getInt("res_width", 480);
            int targetH = getSharedPreferences("DamnPrefs", MODE_PRIVATE).getInt("res_height", 320);

            float scaleX_scr = (float) screenW / targetW;
            float scaleY_scr = (float) screenH / targetH;
            float scale = Math.min(scaleX_scr, scaleY_scr);

            int scaledWidth = (int) (targetW * scale);
            int scaledHeight = (int) (targetH * scale);

            scaleFactorX = (float) scaledWidth / targetW;
            scaleFactorY = (float) scaledHeight / targetH;

            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(scaledWidth, scaledHeight);
            params.gravity = Gravity.CENTER;
            surfaceView.setLayoutParams(params);

            surfaceView.setOnTouchListener((v, event) -> {
                int actionMasked = event.getActionMasked();
                float scaleX = (float) targetW / v.getWidth();
                float scaleY = (float) targetH / v.getHeight();

                if (actionMasked == MotionEvent.ACTION_MOVE) {
                    for (int i = 0; i < event.getPointerCount(); i++) {
                        int pId = event.getPointerId(i);
                        onTouchEventNative(actionMasked, pId, event.getX(i) * scaleX, event.getY(i) * scaleY);
                    }
                } else {
                    int pIndex = event.getActionIndex(); int pId = event.getPointerId(pIndex);
                    onTouchEventNative(actionMasked, pId, event.getX(pIndex) * scaleX, event.getY(pIndex) * scaleY);
                }
                return true;
            });

            renderContainer.addView(surfaceView);

            // --- ИНИЦИАЛИЗАЦИЯ ВИДЕО UI ---
            videoContainer = new FrameLayout(this);
            videoContainer.setBackgroundColor(Color.BLACK);
            videoContainer.setVisibility(View.GONE);

            videoView = new android.widget.VideoView(this);
            videoView.setZOrderMediaOverlay(true); // ФИКС Z-ORDER: Заставляет Surface видеоплеера рендериться над OpenGL и правильно прятаться
            videoView.setVisibility(View.GONE); // ФИКС: Явное скрытие Surface

            FrameLayout.LayoutParams vvParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
            vvParams.gravity = Gravity.CENTER;
            videoContainer.addView(videoView, vvParams);

            skipButton = new android.widget.Button(this);
            skipButton.setText("Skip");
            skipButton.setVisibility(View.GONE);
            FrameLayout.LayoutParams btnParams = new FrameLayout.LayoutParams(300, 150);
            btnParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            btnParams.bottomMargin = 80;
            videoContainer.addView(skipButton, btnParams);

            videoContainer.setOnTouchListener((v, ev) -> {
                if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                    skipButton.setVisibility(View.VISIBLE);
                    if (hideSkipRunnable != null) videoHandler.removeCallbacks(hideSkipRunnable);
                    hideSkipRunnable = () -> skipButton.setVisibility(View.GONE);
                    videoHandler.postDelayed(hideSkipRunnable, 3000);
                }
                return true;
            });

            skipButton.setOnClickListener(v -> stopVideo());
            videoView.setOnCompletionListener(mp -> stopVideo());

            renderContainer.addView(videoContainer, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

            setContentView(renderContainer);
            
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
        });
    }

    // ==========================================
    // AUDIO IMPLEMENTATION NATIVE CALLS
    // ==========================================
    public void audioInit(int ptrId, String path) {
        try {
            MediaPlayer player = new MediaPlayer();
            player.setDataSource(path);
            player.prepare();
            audioPlayers.put(ptrId, player);
            addLogFromNative("HLE Audio: Успешно загружен трек " + path);
        } catch (Exception e) {
            addLogFromNative("HLE Audio ОШИБКА: Не удалось загрузить " + path + ". Причина: " + e.getMessage());
        }
    }

    public void audioPlay(int ptrId) {
        MediaPlayer player = audioPlayers.get(ptrId);
        if (player != null && !player.isPlaying()) {
            player.start();
        }
    }

    public void audioPause(int ptrId) {
        MediaPlayer player = audioPlayers.get(ptrId);
        if (player != null && player.isPlaying()) {
            player.pause();
        }
    }

    public void audioSetLooping(int ptrId, boolean looping) {
        MediaPlayer player = audioPlayers.get(ptrId);
        if (player != null) {
            player.setLooping(looping);
        }
    }

    public boolean audioIsPlaying(int ptrId) {
        MediaPlayer player = audioPlayers.get(ptrId);
        return player != null && player.isPlaying();
    }

    public void audioRelease(int ptrId) {
        MediaPlayer player = audioPlayers.get(ptrId);
        if (player != null) {
            player.release();
            audioPlayers.remove(ptrId);
        }
    }

    public void audioStop(int ptrId) {
        MediaPlayer player = audioPlayers.get(ptrId);
        if (player != null) {
            if (player.isPlaying()) {
                player.pause();
            }
            player.seekTo(0);
        }
    }

    public void audioSetVolume(int ptrId, float volume) {
        MediaPlayer player = audioPlayers.get(ptrId);
        if (player != null) {
            player.setVolume(volume, volume);
        }
    }

    public float audioGetDuration(int ptrId) {
        MediaPlayer player = audioPlayers.get(ptrId);
        if (player != null) {
            return player.getDuration() / 1000.0f;
        }
        return 0.0f;
    }

    public float audioGetCurrentTime(int ptrId) {
        MediaPlayer player = audioPlayers.get(ptrId);
        if (player != null) {
            return player.getCurrentPosition() / 1000.0f;
        }
        return 0.0f;
    }

    public void audioSetCurrentTime(int ptrId, float time) {
        MediaPlayer player = audioPlayers.get(ptrId);
        if (player != null) {
            player.seekTo((int)(time * 1000.0f));
        }
    }

    // ==========================================
    // OPENAL NATIVE BRIDGES
    // ==========================================
    public void alBufferDataJava(int bufferId, int format, byte[] data, int freq) {
        alBuffers.put(bufferId, data);
        alBufferFreqs.put(bufferId, freq);
        // Форматы: AL_FORMAT_MONO8 = 0x1100, AL_FORMAT_MONO16 = 0x1101, AL_FORMAT_STEREO8 = 0x1102, AL_FORMAT_STEREO16 = 0x1103
        int channels = (format == 0x1102 || format == 0x1103) ? AudioFormat.CHANNEL_OUT_STEREO : AudioFormat.CHANNEL_OUT_MONO;
        int bitDepth = (format == 0x1101 || format == 0x1103) ? AudioFormat.ENCODING_PCM_16BIT : AudioFormat.ENCODING_PCM_8BIT;
        alBufferChannels.put(bufferId, channels | (bitDepth << 16));
        addLogFromNative("HLE OpenAL: Загружен буфер " + bufferId + ", размер: " + data.length + " байт, freq: " + freq);
    }

    public void alSourceiJava(int sourceId, int param, int value) {
        if (param == 0x1009) { // AL_BUFFER
            alSourceToBuffer.put(sourceId, value);
        }
    }

    public void alSourcePlayJava(int sourceId) {
        Integer bufferId = alSourceToBuffer.get(sourceId);
        if (bufferId == null) return;
        
        byte[] pcm = alBuffers.get(bufferId);
        Integer freq = alBufferFreqs.get(bufferId);
        Integer formatPack = alBufferChannels.get(bufferId);
        
        if (pcm == null || freq == null || formatPack == null) return;

        int channels = formatPack & 0xFFFF;
        int bitDepth = formatPack >> 16;

        AudioTrack track = alSourceTracks.get(sourceId);
        if (track != null) {
            track.stop();
            track.release();
        }

        try {
            int minSize = AudioTrack.getMinBufferSize(freq, channels, bitDepth);
            int trackSize = Math.max(minSize, pcm.length);

            track = new AudioTrack(AudioManager.STREAM_MUSIC, freq, channels, bitDepth, trackSize, AudioTrack.MODE_STATIC);
            track.write(pcm, 0, pcm.length);
            track.play();
            alSourceTracks.put(sourceId, track);
        } catch (Exception e) {
            addLogFromNative("HLE OpenAL ОШИБКА: " + e.getMessage());
        }
    }

    public void alSourceStopJava(int sourceId) {
        AudioTrack track = alSourceTracks.get(sourceId);
        if (track != null && track.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
            track.stop();
        }
    }

    public void alSourcefJava(int sourceId, int param, float value) {
        if (param == 0x100A) { // AL_GAIN (Громкость)
            AudioTrack track = alSourceTracks.get(sourceId);
            if (track != null) {
                track.setVolume(value);
            }
        }
    }

    // ==========================================
    // VIDEO NATIVE CALLS
    // ==========================================
    public void videoInit(int ptrId, String path) {
        runOnUiThread(() -> {
            activeVideoPtrId = ptrId;
            videoView.setVideoPath(path);
            addLogFromNative("HLE Video: Успешно инициализирован " + path);
        });
    }

    public void videoPlay(int ptrId) {
        runOnUiThread(() -> {
            activeVideoPtrId = ptrId;
            videoView.setVisibility(View.VISIBLE); // ФИКС: Явно возвращаем Surface
            videoContainer.setVisibility(View.VISIBLE);
            skipButton.setVisibility(View.GONE);
            videoView.start();
        });
    }

    public void videoStop(int ptrId) {
        runOnUiThread(this::stopVideo);
    }

    private void stopVideo() {
        if (videoContainer != null && videoContainer.getVisibility() == View.VISIBLE) {
            addLogFromNative("Java UI: Скрытие видеоплеера и освобождение Surface...");
            videoView.stopPlayback(); // Останавливаем и освобождаем буфер
            videoView.setVisibility(View.GONE); // Принудительно гасим окно VideoView
            videoContainer.setVisibility(View.GONE);
            if (hideSkipRunnable != null) videoHandler.removeCallbacks(hideSkipRunnable);
            
            int ptrId = activeVideoPtrId;
            new Thread(() -> {
                addLogFromNative("Java Thread: Вызов onVideoFinishedNative...");
                onVideoFinishedNative(ptrId);
            }).start();
        }
    }

    private void addLog(String msg) {
        android.content.SharedPreferences prefs = getSharedPreferences("DamnPrefs", MODE_PRIVATE);
        boolean anyLog = prefs.getBoolean("log_render", false) || prefs.getBoolean("log_sound", false) ||
                         prefs.getBoolean("log_fs", false) || prefs.getBoolean("log_net", false) ||
                         prefs.getBoolean("log_todo", false) || prefs.getBoolean("log_render_debug", false) ||
                         prefs.getBoolean("log_func_list", false) || prefs.getBoolean("log_hidden_classes", false) ||
                         prefs.getBoolean("log_other", false);
        if (!anyLog) return;

        if (!prefs.getBoolean("log_other", false)) {
            if (!msg.contains("ERROR") && !msg.contains("FATAL") && !(msg.contains("CRITICAL") && !msg.contains("[SIZE-CRITICAL]")) && !msg.contains("ОШИБКА") && !msg.contains("=== Запуск") && !msg.contains("Дата и время:")) {
                return;
            }
        }

        logTextView.append(msg + "\n");
        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
        Log.d("DamnWrapper32_ARMv7", msg);
        try {
            java.io.FileOutputStream fos = new java.io.FileOutputStream(WORK_DIR + "damn32_log.txt", true);
            fos.write((msg + "\n").getBytes());
            fos.close();
        } catch (Exception e) {}
    }


    @Override public void surfaceCreated(SurfaceHolder holder) { onSurfaceCreated(holder.getSurface()); }
    @Override public void surfaceChanged(SurfaceHolder holder, int f, int w, int h) { onSurfaceChanged(w, h); }
    @Override public void surfaceDestroyed(SurfaceHolder holder) {}

    @Override
    protected void onResume() {
        super.onResume();
        if (sensorManager != null) {
            if (accelerometer != null) sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
            if (gyroscope != null) sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!isRendering) return;
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            // iOS UIAccelerometer ожидает значения в G, но вектор гравитации строго инвертирован относительно Android
            onSensorChangedNative(1, -event.values[0] / 9.81f, -event.values[1] / 9.81f, -event.values[2] / 9.81f);
        } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            // iOS CMMotionManager ожидает значения в рад/с (как и дает Android)
            onSensorChangedNative(4, event.values[0], event.values[1], event.values[2]);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private View createWallpaperView(Context context, File file) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                android.graphics.ImageDecoder.Source source = android.graphics.ImageDecoder.createSource(file);
                android.graphics.drawable.Drawable drawable = android.graphics.ImageDecoder.decodeDrawable(source, new android.graphics.ImageDecoder.OnHeaderDecodedListener() {
                    @Override
                    public void onHeaderDecoded(android.graphics.ImageDecoder decoder, android.graphics.ImageDecoder.ImageInfo info, android.graphics.ImageDecoder.Source src) {
                        decoder.setAllocator(android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE);
                    }
                });
                ImageView iv = new ImageView(context);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                iv.setImageDrawable(drawable);
                if (drawable instanceof android.graphics.drawable.AnimatedImageDrawable) {
                    ((android.graphics.drawable.AnimatedImageDrawable) drawable).start();
                }
                return iv;
            }
        } catch (Exception e) {}
        
        FallbackWallpaperView fwv = new FallbackWallpaperView(context, file);
        if (fwv.isValid()) return fwv;
        return null;
    }

    private class FallbackWallpaperView extends View {
        private android.graphics.Movie mMovie;
        private Bitmap mBitmap;
        private long mMovieStart;

        public FallbackWallpaperView(Context context, File file) {
            super(context);
            try {
                byte[] bytes = new byte[(int) file.length()];
                FileInputStream fis = new FileInputStream(file);
                fis.read(bytes);
                fis.close();
                
                if (bytes.length > 3 && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F') {
                    mMovie = android.graphics.Movie.decodeByteArray(bytes, 0, bytes.length);
                    setLayerType(View.LAYER_TYPE_SOFTWARE, null);
                }
                if (mMovie == null || mMovie.duration() == 0) {
                    mMovie = null;
                    mBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                }
            } catch (Exception e) {}
        }
        
        public boolean isValid() { return mMovie != null || mBitmap != null; }

        @Override
        protected void onDraw(android.graphics.Canvas canvas) {
            if (mMovie != null) {
                long now = android.os.SystemClock.uptimeMillis();
                if (mMovieStart == 0) mMovieStart = now;
                int dur = mMovie.duration();
                if (dur == 0) dur = 1000;
                int relTime = (int)((now - mMovieStart) % dur);
                mMovie.setTime(relTime);
                float scaleX = (float) getWidth() / mMovie.width();
                float scaleY = (float) getHeight() / mMovie.height();
                float scale = Math.max(scaleX, scaleY);
                canvas.save();
                float dx = (getWidth() - mMovie.width() * scale) / 2f;
                float dy = (getHeight() - mMovie.height() * scale) / 2f;
                canvas.translate(dx, dy);
                canvas.scale(scale, scale);
                mMovie.draw(canvas, 0, 0);
                canvas.restore();
                invalidate();
            } else if (mBitmap != null) {
                float scaleX = (float) getWidth() / mBitmap.getWidth();
                float scaleY = (float) getHeight() / mBitmap.getHeight();
                float scale = Math.max(scaleX, scaleY);
                float dx = (getWidth() - mBitmap.getWidth() * scale) / 2f;
                float dy = (getHeight() - mBitmap.getHeight() * scale) / 2f;
                android.graphics.Matrix matrix = new android.graphics.Matrix();
                matrix.postScale(scale, scale);
                matrix.postTranslate(dx, dy);
                canvas.drawBitmap(mBitmap, matrix, null);
            }
        }
    }
}
