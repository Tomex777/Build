package com.homira.aod;
import android.content.*;
import android.os.*;
import android.view.*;
import java.time.*;
public final class Presentation {
    private final Context context;private final Window window;private final Surface surface;private final Store store;private final Handler handler=new Handler(Looper.getMainLooper());
    public Presentation(Context c,Window w,Surface s){context=c;window=w;surface=s;store=new Store(c);}
    private final Runnable update=new Runnable(){public void run(){int hour=LocalTime.now().getHour();boolean night=store.settings().getBoolean("night",true)&&(hour>=store.settings().getInt("nightStart",22)||hour<store.settings().getInt("nightEnd",7));float level=store.settings().getFloat(night?"nightBrightness":"brightness",night?.02f:.12f);WindowManager.LayoutParams lp=window.getAttributes();lp.screenBrightness=store.settings().getBoolean("deviceBrightness",false)?WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE:level;window.setAttributes(lp);surface.shift=store.settings().getBoolean("shift",true);surface.invalidate();handler.postDelayed(this,60000-System.currentTimeMillis()%60000);}};
    public void start(){update.run();}public void stop(){handler.removeCallbacksAndMessages(null);}
}
