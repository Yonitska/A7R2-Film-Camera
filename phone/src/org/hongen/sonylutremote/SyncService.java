package org.hongen.sonylutremote;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.RouteInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

import org.hongen.sonylut.transfer.MediaEntry;
import org.hongen.sonylut.transfer.TransferProtocol;

import java.io.File;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;

/** Foreground sync session: join the camera Wi-Fi, verify /v1/ping, poll the
 * photo manifest and download new photos. Videos are only queued by an
 * explicit user action. All download work happens on one worker thread. */
public final class SyncService extends Service {
    static final String ACTION_START="org.hongen.sonylutremote.START";
    static final String ACTION_STOP="org.hongen.sonylutremote.STOP";
    static final String ACTION_RECONNECT="org.hongen.sonylutremote.RECONNECT";
    static final String ACTION_RETRY_PROBE="org.hongen.sonylutremote.RETRY_PROBE";
    static final String ACTION_SYNC_NOW="org.hongen.sonylutremote.SYNC_NOW";
    static final String ACTION_RETRY_FAILED="org.hongen.sonylutremote.RETRY_FAILED";
    static final String ACTION_INCLUDE_SKIPPED="org.hongen.sonylutremote.INCLUDE_SKIPPED";
    static final String ACTION_DOWNLOAD_VIDEO="org.hongen.sonylutremote.DOWNLOAD_VIDEO";
    static final String EXTRA_ID="id";
    private static final String CHANNEL="camera_sync",TAG="SonyLutRemote";
    private static final int NOTIFICATION_ID=0x534c;
    private static final long PHOTO_POLL_MS=2000L,VIDEO_POLL_MS=5000L;
    private static final int MAX_STATUS_ITEMS=120;

    private final Object wake=new Object();
    private final Object publishLock=new Object();
    private final LinkedBlockingQueue<String> videoQueue=new LinkedBlockingQueue<String>();
    private volatile boolean stopRequested;
    private volatile int session;
    private volatile String connectionState=SyncStatus.IDLE;
    private volatile String message="正在准备传输服务";
    private volatile String ssid="",host="",model="";
    private volatile int port=8080;
    private volatile Network network;
    private volatile CameraClient client;
    private volatile List<MediaEntry> photos=Collections.emptyList();
    private volatile List<MediaEntry> videos=Collections.emptyList();
    private volatile String downloadId="",downloadName="";
    private volatile long downloadBytes,downloadTotal;
    private WifiConnector wifi;
    private LedgerStore ledger;
    private File partialDir;
    private Thread worker;
    private long lastPublish,lastNotify;

    static void start(Context context){send(context,ACTION_START,null);}
    static void stop(Context context){send(context,ACTION_STOP,null);}
    static void reconnect(Context context){send(context,ACTION_RECONNECT,null);}
    static void retryProbe(Context context){send(context,ACTION_RETRY_PROBE,null);}
    static void syncNow(Context context){send(context,ACTION_SYNC_NOW,null);}
    static void retryFailed(Context context){send(context,ACTION_RETRY_FAILED,null);}
    static void includeSkipped(Context context){send(context,ACTION_INCLUDE_SKIPPED,null);}
    static void downloadVideo(Context context,String id){
        if(id==null||!TransferProtocol.safeId(id))return;
        send(context,ACTION_DOWNLOAD_VIDEO,id);
    }
    private static void send(Context context,String action,String id){
        Intent intent=new Intent(context,SyncService.class).setAction(action);
        if(id!=null)intent.putExtra(EXTRA_ID,id);
        try{
            if(Build.VERSION.SDK_INT>=26)context.startForegroundService(intent);
            else context.startService(intent);
        }catch(RuntimeException error){
            Log.w(TAG,"service_start_failed "+error.getClass().getSimpleName());
        }
    }

    public void onCreate(){
        super.onCreate();
        createChannel();
        startForeground(NOTIFICATION_ID,buildNotification("正在准备传输服务"));
        ledger=new LedgerStore(new File(getFilesDir(),"transfer-ledger.properties"));
        partialDir=new File(getCacheDir(),"sonylut-parts");
        if(!partialDir.isDirectory()&&!partialDir.mkdirs())
            Log.w(TAG,"partial_dir_unavailable");
        wifi=new WifiConnector(this);
        updateStatus(true);
        worker=new Thread(new Runnable(){public void run(){workerLoop();}},"sonylut-sync-worker");
        worker.setDaemon(true);
        worker.start();
    }

    public int onStartCommand(Intent intent,int flags,int startId){
        String action=intent==null?ACTION_START:intent.getAction();
        if(ACTION_STOP.equals(action)){
            shutdownAndStop();
            return START_NOT_STICKY;
        }
        if(stopRequested)return START_NOT_STICKY;
        if(ACTION_START.equals(action)||ACTION_RECONNECT.equals(action)){
            restartSession();
        }else if(ACTION_RETRY_PROBE.equals(action)){
            retryProbe();
        }else if(ACTION_SYNC_NOW.equals(action)){
            wakeWorker();
        }else if(ACTION_RETRY_FAILED.equals(action)){
            ledger.retryFailed();
            wakeWorker();
        }else if(ACTION_INCLUDE_SKIPPED.equals(action)){
            ledger.includeSkipped();
            wakeWorker();
        }else if(ACTION_DOWNLOAD_VIDEO.equals(action)){
            String id=intent.getStringExtra(EXTRA_ID);
            if(id!=null&&TransferProtocol.safeId(id)){
                videoQueue.offer(id);
                wakeWorker();
            }
        }
        return START_NOT_STICKY;
    }

    public IBinder onBind(Intent intent){return null;}

    public void onDestroy(){
        boolean wasRunning=!stopRequested;
        stopRequested=true;
        session++;
        client=null;
        network=null;
        videoQueue.clear();
        if(wifi!=null){wifi.disconnect();wifi=null;}
        synchronized(wake){wake.notifyAll();}
        if(worker!=null)worker.interrupt();
        if(wasRunning)setStoppedStatus();
        try{stopForeground(true);}catch(RuntimeException ignored){}
        NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if(manager!=null)manager.cancel(NOTIFICATION_ID);
        super.onDestroy();
    }

    private void restartSession(){
        session++;
        client=null;
        network=null;
        downloadId="";
        downloadName="";
        downloadBytes=downloadTotal=0;
        photos=Collections.emptyList();
        videos=Collections.emptyList();
        videoQueue.clear();
        if(wifi==null)wifi=new WifiConnector(this);
        wifi.disconnect();
        SettingsStore settings=new SettingsStore(this);
        ssid=settings.ssid();
        port=settings.port();
        host=settings.host();
        String psk=settings.psk(),token=settings.token();
        if(ssid.length()==0||psk.length()<8||token.length()<8||host.length()==0){
            connectionState=SyncStatus.ERROR;
            message="请先填写并保存完整的配对信息";
            updateStatus(true);
            return;
        }
        connectionState=SyncStatus.CONNECTING;
        message="正在连接相机 Wi-Fi";
        updateStatus(true);
        wifi.connect(ssid,psk,new WifiConnector.Callback(){
            public void onWifiReady(Network network){wifiReady(network);}
            public void onWifiLost(String reason){wifiLost(reason);}
            public void onWifiFailed(String reason){wifiFailed(reason);}
        });
    }

    private void wifiReady(Network ready){
        if(stopRequested)return;
        network=ready;
        client=null;
        int token=++session;
        connectionState=SyncStatus.CONNECTING;
        message="已加入相机 Wi-Fi，正在查找传输服务";
        updateStatus(true);
        SettingsStore settings=new SettingsStore(this);
        ssid=settings.ssid();
        port=settings.port();
        host=settings.host();
        startProbe(token,ready,settings.host(),settings.port(),settings.token());
    }
    private void wifiLost(String reason){
        if(stopRequested)return;
        session++;
        client=null;
        network=null;
        connectionState=SyncStatus.ERROR;
        message=reason==null||reason.length()==0?"相机 Wi-Fi 已断开":reason;
        updateStatus(true);
    }
    private void wifiFailed(String reason){
        if(stopRequested)return;
        session++;
        client=null;
        network=null;
        connectionState=SyncStatus.ERROR;
        message=reason==null||reason.length()==0?"连接相机 Wi-Fi 失败":reason;
        updateStatus(true);
    }

    private void startProbe(final int probeSession,final Network ready,final String savedHost,
                            final int savedPort,final String token){
        Thread probe=new Thread(new Runnable(){public void run(){
            probe(probeSession,ready,savedHost,savedPort,token);
        }},"sonylut-probe");
        probe.setDaemon(true);
        probe.start();
    }

    private void probe(int probeSession,Network ready,String savedHost,int savedPort,String token){
        List<String> hosts=candidates(ready,savedHost);
        CameraClient found=null;
        String foundHost="",foundModel="";
        boolean authFailure=false;
        for(String candidate:hosts){
            if(stopRequested||probeSession!=session)return;
            CameraClient probe=new CameraClient(candidate,savedPort,token);
            try{
                String value=probe.model();
                found=probe;
                foundHost=candidate;
                foundModel=value;
                break;
            }catch(CameraClient.AuthException error){
                authFailure=true;
                break;
            }catch(IOException error){
                Log.i(TAG,"probe_failed "+candidate+" "+error.getClass().getSimpleName());
            }
        }
        if(stopRequested||probeSession!=session)return;
        if(found==null){
            connectionState=SyncStatus.ERROR;
            message=authFailure
                ?"配对码不正确，请在相机“手机传输”页核对后重新连接"
                :"已加入相机 Wi-Fi，但找不到传输服务。请确认相机 App 的“手机传输”已开启";
            updateStatus(true);
            return;
        }
        synchronized(publishLock){
            if(stopRequested||probeSession!=session)return;
            client=found;
            host=foundHost;
            model=foundModel;
            connectionState=SyncStatus.CONNECTED;
            message=foundModel.length()==0?"已连接相机":"已连接 "+foundModel;
            updateStatus(true);
        }
        wakeWorker();
    }

    private List<String> candidates(Network ready,String savedHost){
        Set<String> values=new LinkedHashSet<String>();
        addHost(values,savedHost);
        if(ready!=null){
            try{
                ConnectivityManager manager=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
                LinkProperties links=manager==null?null:manager.getLinkProperties(ready);
                if(links!=null){
                    for(RouteInfo route:links.getRoutes()){
                        if(route.getGateway() instanceof Inet4Address)
                            addHost(values,route.getGateway().getHostAddress());
                    }
                    for(LinkAddress link:links.getLinkAddresses()){
                        String candidate=networkHost(link);
                        if(candidate!=null)addHost(values,candidate);
                    }
                    for(InetAddress dns:links.getDnsServers()){
                        if(dns instanceof Inet4Address)addHost(values,dns.getHostAddress());
                    }
                }
            }catch(RuntimeException ignored){}
        }
        addHost(values,"192.168.122.1");
        List<String> result=new ArrayList<String>(values);
        return result.size()>6?result.subList(0,6):result;
    }
    private static void addHost(Set<String> values,String value){
        String host=SettingsStore.normalizeHost(value);
        if(host.length()>0&&!"0.0.0.0".equals(host)&&!"127.0.0.1".equals(host))values.add(host);
    }
    private static String networkHost(LinkAddress link){
        if(link==null||!(link.getAddress() instanceof Inet4Address))return null;
        int prefix=link.getPrefixLength();
        if(prefix<1||prefix>30)return null;
        byte[] data=link.getAddress().getAddress();
        int address=((data[0]&255)<<24)|((data[1]&255)<<16)|((data[2]&255)<<8)|(data[3]&255);
        int mask=0xffffffff<<(32-prefix);
        int candidate=(address&mask)|1;
        if(candidate==address)return null;
        return String.format(Locale.US,"%d.%d.%d.%d",
            (candidate>>>24)&255,(candidate>>>16)&255,(candidate>>>8)&255,candidate&255);
    }

    private void retryProbe(){
        Network ready=network;
        if(ready==null){
            message="相机 Wi-Fi 尚未连接，请先点“连接并同步”";
            connectionState=SyncStatus.ERROR;
            updateStatus(true);
            return;
        }
        SettingsStore settings=new SettingsStore(this);
        ssid=settings.ssid();
        port=settings.port();
        host=settings.host();
        client=null;
        int probeSession=++session;
        connectionState=SyncStatus.CONNECTING;
        message="正在重新检测相机传输服务";
        updateStatus(true);
        startProbe(probeSession,ready,settings.host(),settings.port(),settings.token());
    }

    private void workerLoop(){
        long lastPhoto=0,lastVideo=0;
        while(!stopRequested){
            try{
                CameraClient active=client;
                String videoId=null;
                boolean worked=false;
                if(active!=null&&!videoQueue.isEmpty())videoId=videoQueue.poll();
                long now=SystemClock.elapsedRealtime();
                if(active!=null){
                    if(videoId!=null){
                        downloadVideo(active,videoId);
                        worked=true;
                        lastVideo=0;
                    }
                    if(stopRequested)break;
                    if(now-lastPhoto>=PHOTO_POLL_MS){
                        syncPhotos(active);
                        lastPhoto=SystemClock.elapsedRealtime();
                        worked=true;
                    }
                    if(stopRequested)break;
                    if(SystemClock.elapsedRealtime()-lastVideo>=VIDEO_POLL_MS){
                        refreshVideos(active);
                        lastVideo=SystemClock.elapsedRealtime();
                        worked=true;
                    }
                }
                if(!worked)synchronized(wake){wake.wait(500);}
            }catch(InterruptedException error){
                if(stopRequested)break;
            }catch(Throwable error){
                Log.w(TAG,"worker_error "+error.getClass().getSimpleName());
                try{synchronized(wake){wake.wait(1000);}}catch(InterruptedException ignored){break;}
            }
        }
    }

    private void syncPhotos(CameraClient active){
        List<MediaEntry> current;
        try{
            current=active.photos();
        }catch(CameraClient.AuthException error){
            failAuth();
            return;
        }catch(IOException error){
            message="暂时无法读取照片列表，正在重试";
            updateStatus(false);
            return;
        }
        if(stopRequested||client!=active)return;
        photos=current;
        SettingsStore settings=new SettingsStore(this);
        ledger.baselineIfNeeded(current,settings.includeExisting());
        updateStatus(false);
        for(MediaEntry entry:current){
            if(stopRequested||client!=active)return;
            if(!ledger.shouldAutoDownload(entry))continue;
            downloadOne(active,entry);
        }
    }

    private void downloadOne(CameraClient active,MediaEntry entry){
        if(entry==null||!TransferProtocol.safeId(entry.id))return;
        if(!partialDir.isDirectory()&&!partialDir.mkdirs()){
            ledger.markFailed(entry,"无法建立临时目录");
            return;
        }
        File part=new File(partialDir,entry.id+".part");
        File meta=new File(partialDir,entry.id+".meta");
        downloadId=entry.id;
        downloadName=entry.name;
        downloadBytes=0;
        downloadTotal=entry.size;
        message="正在传输相机照片";
        updateStatus(true);
        try{
            active.download(entry,part,meta,new CameraClient.Progress(){
                public void progress(long bytes,long total){
                    downloadBytes=bytes;
                    downloadTotal=total;
                    updateStatus(false);
                }
            });
            if(stopRequested)return;
            MediaSaver.save(this,entry,part);
            part.delete();
            meta.delete();
            ledger.markDone(entry);
            message="已保存 "+entry.name;
        }catch(CameraClient.AuthException error){
            failAuth();
        }catch(IOException error){
            ledger.markFailed(entry,brief(error));
            message="传输失败："+brief(error);
        }catch(RuntimeException error){
            ledger.markFailed(entry,brief(error));
            message="保存失败："+brief(error);
        }finally{
            downloadId="";
            downloadName="";
            downloadBytes=downloadTotal=0;
            updateStatus(true);
        }
    }

    private void downloadVideo(CameraClient active,String id){
        MediaEntry entry=find(videos,id);
        if(entry==null){
            try{entry=find(active.videos(),id);}catch(CameraClient.AuthException error){failAuth();return;}
            catch(IOException error){
                message="无法读取录像列表："+brief(error);
                updateStatus(true);
                return;
            }
        }
        if(entry==null){
            message="该录像已不在相机勾选列表中，请在相机上重新勾选";
            updateStatus(true);
            return;
        }
        if(ledger.isDone(entry)){
            message="该录像已经下载过";
            updateStatus(true);
            return;
        }
        if(!partialDir.isDirectory()&&!partialDir.mkdirs()){
            ledger.markFailed(entry,"无法建立临时目录");
            message="无法建立临时目录";
            updateStatus(true);
            return;
        }
        File part=new File(partialDir,entry.id+".part");
        File meta=new File(partialDir,entry.id+".meta");
        downloadId=entry.id;
        downloadName=entry.name;
        downloadBytes=0;
        downloadTotal=entry.size;
        message="正在传输相机照片";
        updateStatus(true);
        try{
            active.download(entry,part,meta,new CameraClient.Progress(){
                public void progress(long bytes,long total){
                    downloadBytes=bytes;
                    downloadTotal=total;
                    updateStatus(false);
                }
            });
            if(stopRequested)return;
            MediaSaver.save(this,entry,part);
            part.delete();
            meta.delete();
            ledger.markDone(entry);
            message="已保存录像 "+entry.name;
        }catch(CameraClient.AuthException error){
            failAuth();
        }catch(IOException error){
            ledger.markFailed(entry,brief(error));
            message="录像传输失败："+brief(error);
        }catch(RuntimeException error){
            ledger.markFailed(entry,brief(error));
            message="录像保存失败："+brief(error);
        }finally{
            downloadId="";
            downloadName="";
            downloadBytes=downloadTotal=0;
            updateStatus(true);
        }
    }

    private void refreshVideos(CameraClient active){
        try{
            videos=active.videos();
            updateStatus(false);
        }catch(CameraClient.AuthException error){
            failAuth();
        }catch(IOException error){
            /* keep the last list; the photo loop reports connection trouble */
        }
    }

    private void failAuth(){
        session++;
        client=null;
        connectionState=SyncStatus.ERROR;
        message="配对码不正确，请核对相机“手机传输”页后重新连接";
        updateStatus(true);
    }

    private void updateStatus(boolean force){
        if(stopRequested)return;
        long now=System.currentTimeMillis();
        synchronized(publishLock){
            if(!force&&now-lastPublish<500)return;
            lastPublish=now;
            List<MediaEntry> photoSnapshot=photos,videoSnapshot=videos;
            String state=connectionState;
            if(SyncStatus.CONNECTED.equals(state)&&downloadId.length()>0)state=SyncStatus.SYNCING;
            List<SyncStatus.Item> photoItems=items(photoSnapshot);
            List<SyncStatus.Item> videoItems=items(videoSnapshot);
            SyncStatus.set(state,message,ssid,host,port,true,photoItems,videoItems);
            if(force||now-lastNotify>=1000){
                lastNotify=now;
                NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
                if(manager!=null)manager.notify(NOTIFICATION_ID,buildNotification(notificationText(state,photoSnapshot)));
            }
        }
    }

    private List<SyncStatus.Item> items(List<MediaEntry> entries){
        List<SyncStatus.Item> result=new ArrayList<SyncStatus.Item>();
        if(entries==null)return result;
        int limit=Math.min(entries.size(),MAX_STATUS_ITEMS);
        for(int i=0;i<limit;i++){
            MediaEntry entry=entries.get(i);
            String state;
            long bytes=0,total=0;
            if(entry.id.equals(downloadId)){
                state="downloading";
                bytes=downloadBytes;
                total=downloadTotal;
            }else{
                state=ledger.stateOf(entry);
                if(state.length()==0)state="waiting";
            }
            String detail=LedgerStore.FAILED.equals(state)?ledger.detailOf(entry):"";
            result.add(new SyncStatus.Item(entry,state,bytes,total,detail));
        }
        return result;
    }

    private String notificationText(String state,List<MediaEntry> photoSnapshot){
        if(SyncStatus.SYNCING.equals(state)&&downloadId.length()>0){
            int percent=downloadTotal>0?(int)Math.min(100,downloadBytes*100/downloadTotal):0;
            return "正在传输 "+downloadName+" "+percent+"%";
        }
        if(message.length()>0)return message;
        if(SyncStatus.CONNECTED.equals(state))return "已连接，等待新照片";
        return "相机传输";
    }

    private Notification buildNotification(String text){
        Intent open=new Intent(this,MainActivity.class);
        PendingIntent content=PendingIntent.getActivity(this,0,open,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Intent stop=new Intent(this,SyncService.class).setAction(ACTION_STOP);
        PendingIntent stopAction=PendingIntent.getService(this,1,stop,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder=Build.VERSION.SDK_INT>=26
            ?Api26.builder(this):new Notification.Builder(this);
        builder.setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Sony LUT 手机传输")
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(content)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel,"停止",stopAction);
        return builder.build();
    }
    private void createChannel(){
        if(Build.VERSION.SDK_INT>=26)Api26.ensureChannel(this);
    }

    /** Kept separate so Android 6/7 never resolves a class that does not exist
     * before API 26, even while verifying SyncService. */
    private static final class Api26 {
        static void ensureChannel(Context context){
            NotificationManager manager=(NotificationManager)context.getSystemService(NOTIFICATION_SERVICE);
            if(manager==null)return;
            android.app.NotificationChannel channel=new android.app.NotificationChannel(
                CHANNEL,"相机传输",NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("相机照片与录像的本地 Wi-Fi 传输");
            channel.setShowBadge(false);
            manager.createNotificationChannel(channel);
        }
        static Notification.Builder builder(Context context){
            return new Notification.Builder(context,CHANNEL);
        }
    }

    private void shutdownAndStop(){
        if(!stopRequested){
            stopRequested=true;
            session++;
            client=null;
            network=null;
            videoQueue.clear();
            if(wifi!=null){wifi.disconnect();wifi=null;}
            synchronized(wake){wake.notifyAll();}
            if(worker!=null)worker.interrupt();
            setStoppedStatus();
        }
        try{stopForeground(true);}catch(RuntimeException ignored){}
        NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if(manager!=null)manager.cancel(NOTIFICATION_ID);
        stopSelf();
    }
    private void setStoppedStatus(){
        SyncStatus.set(SyncStatus.STOPPED,"传输已停止",ssid,host,port,false,
            Collections.<SyncStatus.Item>emptyList(),Collections.<SyncStatus.Item>emptyList());
    }
    private void wakeWorker(){
        synchronized(wake){wake.notifyAll();}
    }

    private static MediaEntry find(List<MediaEntry> entries,String id){
        if(entries==null||id==null)return null;
        for(MediaEntry entry:entries)if(id.equals(entry.id))return entry;
        return null;
    }
    private static String brief(Throwable error){
        String value=error.getMessage();
        if(value==null||value.length()==0)value=error.getClass().getSimpleName();
        return value.length()>160?value.substring(0,160):value;
    }
}
