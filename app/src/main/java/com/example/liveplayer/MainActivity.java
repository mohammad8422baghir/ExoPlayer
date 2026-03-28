package com.example.liveplayer;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.media3.common.MediaItem;
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
    private TextView loadingText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // پاکسازی خودکار حافظه موقت در هنگام باز شدن برنامه
        cleanOldCache();

        // ساخت رابط کاربری (منو و پلیر)
        FrameLayout rootLayout = new FrameLayout(this);
        rootLayout.setBackgroundColor(Color.BLACK);

        menuLayout = new LinearLayout(this);
        menuLayout.setOrientation(LinearLayout.VERTICAL);
        menuLayout.setGravity(Gravity.CENTER);
        
        Button btnLive = new Button(this);
        btnLive.setText("۱. پخش زنده (بدون تاخیر)");
        btnLive.setOnClickListener(v -> startStream(false));
        
        Button btnBuffer = new Button(this);
        btnBuffer.setText("۲. پخش پایدار (ذخیره هوشمند قبل از پخش)");
        btnBuffer.setOnClickListener(v -> startStream(true));

        menuLayout.addView(btnLive);
        menuLayout.addView(btnBuffer);

        playerView = new PlayerView(this);
        playerView.setVisibility(View.GONE);

        loadingText = new TextView(this);
        loadingText.setTextColor(Color.WHITE);
        loadingText.setTextSize(16);
        loadingText.setGravity(Gravity.CENTER);
        loadingText.setVisibility(View.GONE);

        rootLayout.addView(playerView, new FrameLayout.LayoutParams(-1, -1));
        rootLayout.addView(menuLayout, new FrameLayout.LayoutParams(-1, -1));
        rootLayout.addView(loadingText, new FrameLayout.LayoutParams(-1, -1));

        setContentView(rootLayout);
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

        // تنظیمات حافظه پنهان با استفاده از کلاس جدید
        File cacheDir = new File(getCacheDir(), "media_cache");
        simpleCache = new SimpleCache(cacheDir, new LeastRecentlyUsedCacheEvictor(50 * 1024 * 1024), new StandaloneDatabaseProvider(this));
        
        DataSource.Factory dataSourceFactory = new CacheDataSource.Factory()
                .setCache(simpleCache)
                .setUpstreamDataSourceFactory(new DefaultHttpDataSource.Factory());

        // تنظیم کیفیت پیش‌فرض روی 240p
        DefaultTrackSelector trackSelector = new DefaultTrackSelector(this);
        trackSelector.setParameters(trackSelector.buildUponParameters().setMaxVideoSize(Integer.MAX_VALUE, 240).build());

        // تنظیمات دانلود هوشمند بر اساس انتخاب کاربر
        DefaultLoadControl.Builder loadControlBuilder = new DefaultLoadControl.Builder();
        if (isBufferedMode) {
            loadingText.setVisibility(View.VISIBLE);
            // در حالت پایدار: 60 ثانیه دانلود کن قبل از اینکه پخش را شروع کنی
            loadControlBuilder.setBufferDurationsMs(60000, 120000, 60000, 60000);
        } else {
            // حالت زنده عادی
            loadControlBuilder.setBufferDurationsMs(2000, 10000, 2000, 2000);
        }

        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(dataSourceFactory))
                .setTrackSelector(trackSelector)
                .setLoadControl(loadControlBuilder.build())
                .build();

        playerView.setPlayer(player);

        if (isBufferedMode) {
            player.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    if (playbackState == Player.STATE_BUFFERING) {
                        loadingText.setVisibility(View.VISIBLE);
                        long buffered = player.getBufferedPosition() / 1000;
                        loadingText.setText("در حال دریافت فایل‌ها برای اتصال پایدار...\n" + buffered + " ثانیه ذخیره شد");
                    } else if (playbackState == Player.STATE_READY) {
                        loadingText.setVisibility(View.GONE);
                    }
                }
            });
        }

        // آدرس Master Playlist
        String liveUrl = "http://45.76.93.249/live/master.m3u8";
        player.setMediaItem(MediaItem.fromUri(liveUrl));
        player.prepare();
        player.setPlayWhenReady(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) { player.release(); }
        if (simpleCache != null) { simpleCache.release(); }
    }
}
