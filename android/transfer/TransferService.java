package org.hongen.sonylutprobe;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

/** Main-process foreground service. It owns Wi-Fi Direct and the HTTP server
 * while the film library or a capture Activity is in front. */
public final class TransferService extends Service {
    static final String ACTION_START="org.hongen.sonylutprobe.transfer.START";
    static final String ACTION_STOP="org.hongen.sonylutprobe.transfer.STOP";
    private static final int NOTIFICATION_ID=731;
    private static final String CHANNEL="sonylut-transfer";
    private final Handler main=new Handler(Looper.getMainLooper());
    private TransferStatus.Listener statusListener;
    private boolean foreground;

    static void start(Context context){
        Intent intent=new Intent(context,TransferService.class).setAction(ACTION_START);
        try{context.startService(intent);}catch(Throwable error){
            android.util.Log.w("LutTransfer","service_start_failed",error);
        }
    }
    static void stop(Context context){
        TransferRuntime.stopNow(context);
        try{context.stopService(new Intent(context,TransferService.class));}catch(Throwable ignored){}
    }

    @Override public void onCreate(){
        super.onCreate();
        statusListener=new TransferStatus.Listener(){public void transferStatusChanged(final TransferStatus.Snapshot snapshot){
            main.post(new Runnable(){public void run(){updateNotification(snapshot);}});
        }};
        TransferStatus.addListener(statusListener);
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        String action=intent==null?null:intent.getAction();
        if(ACTION_STOP.equals(action)||!TransferSettings.enabled(this)){
            TransferRuntime.stopNow(this);
            stopSelfResult(startId);
            return START_NOT_STICKY;
        }
        if(!foreground){
            try{
                startForeground(NOTIFICATION_ID,notification(TransferStatus.snapshot()));
                foreground=true;
            }catch(Throwable error){
                android.util.Log.w("LutTransfer","foreground_start_failed",error);
            }
        }
        TransferRuntime.start(this);
        return START_NOT_STICKY;
    }
    @Override public void onDestroy(){
        TransferStatus.removeListener(statusListener);
        TransferRuntime.stopNow(this);
        if(foreground)try{stopForeground(true);}catch(Throwable ignored){}
        foreground=false;
        super.onDestroy();
        android.util.Log.i("LutTransfer","service_destroyed");
    }
    @Override public void onTaskRemoved(Intent rootIntent){
        TransferRuntime.stopNow(this);
        stopSelf();
        super.onTaskRemoved(rootIntent);
    }
    @Override public IBinder onBind(Intent intent){return null;}

    private void updateNotification(TransferStatus.Snapshot snapshot){
        if(!foreground)return;
        try{
            NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            manager.notify(NOTIFICATION_ID,notification(snapshot));
        }catch(Throwable error){
            android.util.Log.w("LutTransfer","notification_update_failed",error);
        }
    }
    private Notification notification(TransferStatus.Snapshot snapshot){
        if(Build.VERSION.SDK_INT>=26)Api26.ensureChannel(this);
        Notification.Builder builder=Build.VERSION.SDK_INT>=26
            ?Api26.builder(this):new Notification.Builder(this);
        Intent open=new Intent(this,LibraryActivity.class)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        builder.setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("A7R2胶片相机 · 手机传输")
            .setContentText(notificationText(snapshot))
            .setContentIntent(PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT))
            .setOngoing(true)
            .setOnlyAlertOnce(true);
        if(Build.VERSION.SDK_INT>=16)return builder.build();
        return builder.getNotification();
    }
    private static String notificationText(TransferStatus.Snapshot snapshot){
        if(TransferStatus.ERROR.equals(snapshot.state))return "连接失败 · 打开应用查看";
        if(TransferStatus.STARTING.equals(snapshot.state))return "正在启动相机 Wi-Fi";
        if(TransferStatus.READY.equals(snapshot.state))
            return snapshot.phoneConnected?"手机已连接 · 新照片会自动传输":"服务已就绪 · 等待手机连接";
        if(TransferStatus.STOPPING.equals(snapshot.state))return "正在关闭传输";
        return "手机传输已停止";
    }

    /** Kept in a separate class so Android 4.1 never resolves a channel type. */
    private static final class Api26 {
        static void ensureChannel(Context context){
            NotificationManager manager=(NotificationManager)context.getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel channel=new NotificationChannel(CHANNEL,"手机传输",NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("相机与手机之间的照片和已勾选录像传输");
            channel.setShowBadge(false);
            manager.createNotificationChannel(channel);
        }
        static Notification.Builder builder(Context context){
            return new Notification.Builder(context,CHANNEL);
        }
    }
}
