package com.example.liveplayer;

import android.app.Activity;
import android.os.Bundle;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.media3.exoplayer.DefaultLoadControl;

public class MainActivity extends Activity {
    private ExoPlayer player;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        PlayerView playerView = new PlayerView(this);
        setContentView(playerView);

        // تنظیمات بافر برای اینترنت بسیار ضعیف تا پخش سریع‌تر شروع شود
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
            .setBufferDurationsMs(1000, 10000, 1000, 1000)
            .build();

        player = new ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .build();
        
        playerView.setPlayer(player);

        // آدرس سرور خود را اینجا وارد کنید
        String liveUrl = "http://45.76.93.249/live/index.m3u8";
        MediaItem mediaItem = MediaItem.fromUri(liveUrl);
        
        player.setMediaItem(mediaItem);
        player.prepare();
        player.setPlayWhenReady(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) {
            player.release();
        }
    }
}
