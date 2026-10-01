package com.homira.aod;
import android.service.notification.*;
import android.app.Notification;
import android.content.*;
import android.graphics.drawable.Drawable;
import java.util.*;

public final class Notifications extends NotificationListenerService {
    public static final Map<String,Item> items=Collections.synchronizedMap(new LinkedHashMap<>());
    public static class Item {String key,pkg,title;Drawable icon;boolean sensitive;}
    private void add(StatusBarNotification n){Item i=new Item();i.key=n.getKey();i.pkg=n.getPackageName();i.sensitive=n.getNotification().visibility!=Notification.VISIBILITY_PUBLIC;i.title=i.sensitive?"":n.getNotification().extras.getCharSequence(Notification.EXTRA_TEXT,"").toString();try{i.icon=n.getNotification().getSmallIcon().loadDrawable(this);}catch(Exception ignored){}items.put(i.key,i);}
    private void notifyChange(){sendBroadcast(new Intent("com.homira.aod.STATE").setPackage(getPackageName()));}
    @Override public void onListenerConnected(){items.clear();try{for(StatusBarNotification n:getActiveNotifications())add(n);}catch(SecurityException ignored){}notifyChange();}
    @Override public void onListenerDisconnected(){items.clear();notifyChange();}
    @Override public void onNotificationPosted(StatusBarNotification n){add(n);notifyChange();}
    @Override public void onNotificationRemoved(StatusBarNotification n){items.remove(n.getKey());notifyChange();}
    public static boolean granted(Context c){String enabled=android.provider.Settings.Secure.getString(c.getContentResolver(),"enabled_notification_listeners");ComponentName ours=new ComponentName(c,Notifications.class);if(enabled==null)return false;for(String value:enabled.split(":"))if(ours.equals(ComponentName.unflattenFromString(value)))return true;return false;}
}
