package com.example.liveplayer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.ui.PlayerView;
import java.io.File;

public class MainActivity extends Activity {
    private ExoPlayer player;
    private SimpleCache simpleCache;
    private LinearLayout menuLayout;
    private PlayerView playerView;
    private LinearLayout loadingLayout;
    private TextView loadingText;
    private ProgressBar progressBar;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updateProgressRunnable;

    private int selectedQuality = 240;
    private int selectedBitrate = 132;
    private long maxBufferedSoFar = 0; // برای جلوگیری از صفر شدن عدد هنگام قطعی

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cleanOldCache();

        FrameLayout rootLayout = new FrameLayout(this);
        rootLayout.setBackgroundColor(Color.parseColor("#121212"));

        menuLayout = new LinearLayout(this);
        menuLayout.setOrientation(LinearLayout.VERTICAL);
        menuLayout.setGravity(Gravity.CENTER);
        menuLayout.setPadding(50, 50, 50, 50);

        TextView titleText = new TextView(this);
        titleText.setText("تلویزیون زنده پرو");
        titleText.setTextColor(Color.WHITE);
        titleText.setTextSize(28);
        titleText.setTypeface(null, Typeface.BOLD);
        titleText.setGravity(Gravity.CENTER);
        titleText.setPadding(0, 0, 0, 80);

        Button btnLive = createBeautifulButton("پخش زنده (سریع و لحظه‌ای)", "#1E88E5", "#1565C0");
        btnLive.setOnClickListener(v -> showQualityDialog(false));

        Button btnBuffer = createBeautifulButton("پخش پایدار (ضد قطعی با ذخیره)", "#43A047", "#2E7D32");
        btnBuffer.setOnClickListener(v -> showQualityDialog(true));

        menuLayout.addView(titleText);
        menuLayout.addView(btnLive);
        menuLayout.addView(btnBuffer);

        playerView = new PlayerView(this);
        playerView.setVisibility(View.GONE);
        playerView.setBackgroundColor(Color.BLACK);
        
        loadingLayout = new LinearLayout(this);
        loadingLayout.setOrientation(LinearLayout.VERTICAL);
        loadingLayout.setGravity(Gravity.CENTER);
        loadingLayout.setBackgroundColor(Color.parseColor("#E6000000"));
        loadingLayout.setVisibility(View.GONE);

        progressBar = new ProgressBar(this);
        loadingText = new TextView(this);
        loadingText.setTextColor(Color.WHITE);
        loadingText.setTextSize(16);
        loadingText.setGravity(Gravity.CENTER);
        loadingText.setPadding(0, 30, 0, 0);

        loadingLayout.addView(progressBar);
        loadingLayout.addView(loadingText);

        rootLayout.addView(playerView, new FrameLayout.LayoutParams(-1, -1));
        rootLayout.addView(menuLayout, new FrameLayout.LayoutParams(-1, -1));
        rootLayout.addView(loadingLayout, new FrameLayout.LayoutParams(-1, -1));

        setContentView(rootLayout);
    }

    private Button createBeautifulButton(String text, String colorStart, String colorEnd) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(16);
        btn.setPadding(0, 40, 0, 40);
        
        GradientDrawable shape = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.parseColor(colorStart), Color.parseColor(colorEnd)});
        shape.setCornerRadius(30f);
        btn.setBackground(shape);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, 40);
        btn.setLayoutParams(params);
        return btn;
    }

    private void showQualityDialog(boolean isBufferedMode) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("تنظیمات کیفیت");
        builder.setMessage("کیفیت پیش‌فرض روی 240p (بهینه برای اینترنت ضعیف) تنظیم شده است.\nآیا مایل به تغییر آن هستید؟");
        
        builder.setPositiveButton("پخش مستقیم", (dialog, which) -> {
            selectedQuality = 240;
            selectedBitrate = 132;
            startStream(isBufferedMode);
        });

        builder.setNegativeButton("تغییر کیفیت", (dialog, which) -> {
            String[] qualities = {"144p (بسیار کم‌حجم)", "240p (پیشنهادی)", "360p (کیفیت بالا)"};
            AlertDialog.Builder qBuilder = new AlertDialog.Builder(this);
            qBuilder.setTitle("یک کیفیت را انتخاب کنید:");
            qBuilder.setItems(qualities, (dialogInterface, i) -> {
                if (i == 0) { selectedQuality = 144; selectedBitrate = 66; }
                else if (i == 1) { selectedQuality = 240; selectedBitrate = 132; }
                else { selectedQuality = 360; selectedBitrate = 314; }
                startStream(isBufferedMode);
            });
            qBuilder.show();
        });
        builder.show();
    }

    private void cleanOldCache() {
        File cacheDir = new File(getCacheDir(), "media_cache");
        if (cacheDir.exists() && cacheDir.isDirectory()) {
            for (File file : cacheDir.listFiles()) { file.delete(); }
        }
    }

    private void startStream(boolean isBufferedMode) {
        menuLayout.setVisibility(View.GONE);
        playerView.setVisibility(View.VISIBLE);
        maxBufferedSoFar = 0; // ریست کردن برای شروع جدید

        File cacheDir = new File(getCacheDir(), "media_cache");
        simpleCache = new SimpleCache(cacheDir, new LeastRecentlyUsedCacheEvictor(100 * 1024 * 1024), new StandaloneDatabaseProvider(this));
        
        // افزایش تایم‌اوت برای اینترنت‌های نوسان‌دار (۲۰ ثانیه)
        DefaultHttpDataSource.Factory httpDataSourceFactory = new DefaultHttpDataSource.Factory()
                .setConnectTimeoutMs(20000)
                .setReadTimeoutMs(20000)
                .setAllowCrossProtocolRedirects(true);

        DataSource.Factory dataSourceFactory = new CacheDataSource.Factory()
                .setCache(simpleCache)
                .setUpstreamDataSourceFactory(httpDataSourceFactory);

        DefaultTrackSelector trackSelector = new DefaultTrackSelector(this);
        trackSelector.setParameters(trackSelector.buildUponParameters().setMaxVideoSize(Integer.MAX_VALUE, selectedQuality).build());

        DefaultLoadControl.Builder loadControlBuilder = new DefaultLoadControl.Builder();
        int targetBufferSeconds = 0;
        
        if (isBufferedMode) {
            loadingLayout.setVisibility(View.VISIBLE);
            targetBufferSeconds = 120;
            loadControlBuilder.setBufferDurationsMs(targetBufferSeconds * 1000, 300000, targetBufferSeconds * 1000, targetBufferSeconds * 1000);
        } else {
            loadControlBuilder.setBufferDurationsMs(2000, 10000, 2000, 2000);
        }

        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(dataSourceFactory))
                .setTrackSelector(trackSelector)
                .setLoadControl(loadControlBuilder.build())
                .build();

        playerView.setPlayer(player);
        playerView.setShowNextButton(false);
        playerView.setShowPreviousButton(false);
        playerView.setShowFastForwardButton(false);
        playerView.setShowRewindButton(false);
        View timeBar = playerView.findViewById(androidx.media3.ui.R.id.exo_progress);
        if (timeBar != null) { timeBar.setVisibility(View.GONE); }
        View timeText = playerView.findViewById(androidx.media3.ui.R.id.exo_position);
        if (timeText != null) { timeText.setVisibility(View.GONE); }

        if (isBufferedMode) {
            final int finalTarget = targetBufferSeconds;
            updateProgressRunnable = new Runnable() {
                @Override
                public void run() {
                    if (player != null && loadingLayout.getVisibility() == View.VISIBLE) {
                        long bufferedMs = player.getTotalBufferedDuration();
                        long bufferedSec = bufferedMs / 1000;
                        
                        // جلوگیری از صفر شدن عدد هنگام قطعی
                        if (bufferedSec > maxBufferedSoFar) {
                            maxBufferedSoFar = bufferedSec;
                        }

                        if (player.getPlaybackState() == Player.STATE_IDLE) {
                            loadingText.setText("ارتباط اینترنت بسیار ضعیف است...\nدر حال تلاش مجدد برای اتصال...");
                        } else {
                            double downloadedKb = (maxBufferedSoFar * (selectedBitrate / 8.0));
                            String sizeTxt = downloadedKb > 1024 ? 
                                String.format("%.2f مگابایت", downloadedKb / 1024) : 
                                String.format("%.0f کیلوبایت", downloadedKb);

                            loadingText.setText("در حال ایجاد اتصال پایدار برای اینترنت ضعیف...\n\n" +
                                    "زمان ذخیره شده: " + maxBufferedSoFar + " از " + finalTarget + " ثانیه\n" +
                                    "حجم دریافت شده: " + sizeTxt);
                        }

                        if (maxBufferedSoFar >= finalTarget || player.getPlaybackState() == Player.STATE_READY) {
                            loadingLayout.setVisibility(View.GONE);
                        } else {
                            handler.postDelayed(this, 1000);
                        }
                    }
                }
            };
            handler.post(updateProgressRunnable);
        }

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_READY && loadingLayout.getVisibility() == View.VISIBLE) {
                    loadingLayout.setVisibility(View.GONE);
                }
            }

            // سیستم هوشمند اتصال مجدد در صورت قطعی اینترنت
            @Override
            public void onPlayerError(PlaybackException error) {
                if (loadingLayout.getVisibility() == View.VISIBLE) {
                    loadingText.setText("اختلال موقت در اینترنت!\nدر حال برقراری مجدد ارتباط...");
                }
                
                // بعد از 3 ثانیه به طور خودکار دوباره تلاش می کند
                handler.postDelayed(() -> {
                    if (player != null) {
                        if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                            player.seekToDefaultPosition();
                        }
                        player.prepare();
                    }
                }, 3000);
            }
        });

        String liveUrl = "http://45.76.93.249/live/master.m3u8";
        player.setMediaItem(MediaItem.fromUri(liveUrl));
        player.prepare();
        player.setPlayWhenReady(true);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null && updateProgressRunnable != null) {
            handler.removeCallbacks(updateProgressRunnable);
        }
        if (player != null) { player.release(); }
        if (simpleCache != null) { simpleCache.release(); }
    }
}
