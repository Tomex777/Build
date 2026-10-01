package com.homira.aod;
import android.app.Activity;
import android.os.*;
import android.view.*;
import android.widget.*;
public final class PreviewActivity extends Activity {
    public Surface surface;private Presentation presentation;
    @Override public void onCreate(Bundle state){super.onCreate(state);Store store=new Store(this);String mode=getIntent().getStringExtra("mode");boolean bedside="Charging".equals(mode),locked="Lock screen".equals(mode);if(locked){if(Build.VERSION.SDK_INT>=27)setShowWhenLocked(true);else getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);}
        if(bedside)getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        Domain.Theme theme=getIntent().hasExtra("theme")?store.find(getIntent().getStringExtra("theme")):store.current(bedside);surface=new Surface(this,theme);surface.ambient=bedside||locked;surface.allowBackground=bedside&&store.settings().getBoolean("chargingBackground",false);surface.followSchedules=getIntent().getBooleanExtra("followSchedules",false);surface.safeRegion=getIntent().getBooleanExtra("safe",false);FrameLayout root=new FrameLayout(this);root.addView(surface,new FrameLayout.LayoutParams(-1,-1));android.widget.Button close=new android.widget.Button(this);close.setText("Close");close.setTextColor(0xffa8e9d1);close.setBackgroundColor(0x20000000);close.setContentDescription("Close display");FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(100,60,Gravity.BOTTOM|Gravity.END);lp.setMargins(0,0,16,48);root.addView(close,lp);close.setOnClickListener(v->finish());setContentView(root);presentation=new Presentation(this,getWindow(),surface);
    }
    @Override protected void onResume(){super.onResume();presentation.start();}
    @Override protected void onPause(){presentation.stop();super.onPause();}
}
