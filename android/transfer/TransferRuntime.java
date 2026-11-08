package org.hongen.sonylutprobe;

import android.content.Context;
import android.os.Environment;

import org.hongen.sonylut.transfer.TransferServer;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/** Main-process owner of the Wi-Fi Direct group and HTTP server.
 *
 * The capture and video processes never participate: they only leave durable
 * files behind, and this runtime indexes those files on demand. Start/stop are
 * serialized; server startup runs on a background worker so enabling transfer
 * never blocks the remote UI. */
final class TransferRuntime implements WifiDirectHost.Listener {
    private static volatile TransferRuntime current;
    private final Context app;
    private final Object lock=new Object();
    private ExecutorService io;
    private WifiDirectHost host;
    private TransferServer server;
    private PairingStore pairing;
    private boolean running,stopping,phoneConnected;
    private String ssid="",psk="",address="",prefixes="",detail="";
    private int port;

    private TransferRuntime(Context context){app=context.getApplicationContext();}
    static TransferRuntime start(Context context){
        TransferRuntime runtime;
        synchronized(TransferRuntime.class){
            if(current==null||!current.running)current=new TransferRuntime(context);
            runtime=current;
        }
        runtime.startInternal();
        return runtime;
    }
    static TransferRuntime current(){return current;}
    static void stopNow(Context context){
        TransferRuntime runtime=current;
        if(runtime!=null)runtime.stopInternal();
        else TransferStatus.off();
    }

    private void startInternal(){
        synchronized(lock){
            if(running)return;
            running=true;stopping=false;phoneConnected=false;
            io=Executors.newSingleThreadExecutor(new ThreadFactory(){
                public Thread newThread(Runnable runnable){
                    Thread thread=new Thread(runnable,"sonylut-transfer");
                    thread.setDaemon(true);return thread;
                }
            });
            detail="正在准备配对与 Wi-Fi Direct";
            TransferStatus.set(TransferStatus.STARTING,detail,"", "", "",0,false);
        }
        try{
            pairing=new PairingStore(app);
            pairing.token();
            synchronized(lock){
                if(!running){stopping=false;return;}
                // Start while holding the lifecycle lock so a concurrent stop
                // cannot slip between constructing the host and enabling
                // Wi-Fi Direct, which would leave an orphan group behind.
                host=new WifiDirectHost(app,this);
                host.start();
            }
        }catch(IOException error){
            fail("无法保存配对信息，请检查 SD 卡");
        }catch(Throwable error){
            fail("传输服务无法启动");
        }
    }

    private void stopInternal(){
        WifiDirectHost stopHost;
        TransferServer stopServer;
        ExecutorService stopIo;
        synchronized(lock){
            if(!running&&server==null&&host==null){TransferStatus.off();return;}
            stopping=true;running=false;
            stopHost=host;host=null;
            stopServer=server;server=null;
            stopIo=io;io=null;
            port=0;phoneConnected=false;
            TransferStatus.set(TransferStatus.STOPPING,"正在关闭手机传输","", "", "",0,false);
        }
        if(stopServer!=null)stopServer.stop();
        if(stopHost!=null)stopHost.stop();
        if(stopIo!=null)stopIo.shutdownNow();
        synchronized(lock){
            stopping=false;ssid="";psk="";address="";prefixes="";detail="";
        }
        TransferStatus.off();
        android.util.Log.i("LutTransfer","runtime_stopped");
    }

    public void wifiDirectGroup(WifiDirectHost.Network network){
        synchronized(lock){
            if(!running||stopping)return;
            ssid=network.ssid;psk=network.psk;address=network.address;prefixes=network.prefixes;
            detail="群组已建立，正在启动传输服务";
        }
        PairingStore store;
        final WifiDirectHost.Network ready=network;
        synchronized(lock){store=pairing;}
        if(store!=null)try{store.rememberNetwork(network.ssid,network.address,TransferSettings.port(app));}
        catch(IOException error){android.util.Log.w("LutTransfer","pairing_network_save_failed",error);}
        TransferStatus.set(TransferStatus.STARTING,"群组已建立，正在启动传输服务",
            network.ssid,network.psk,network.address,0,false);
        ExecutorService worker;
        synchronized(lock){
            if(!running||stopping)return;
            worker=io;
        }
        if(worker!=null)try{worker.execute(new Runnable(){public void run(){startServer(ready);}});}
        catch(Throwable error){fail("传输任务无法启动");}
    }
    public void wifiDirectStation(boolean connected,String message){
        boolean serverReady;
        synchronized(lock){
            if(!running||stopping)return;
            phoneConnected=connected;detail=message;
            serverReady=server!=null&&port>0;
        }
        publish(serverReady?TransferStatus.READY:TransferStatus.STARTING,detail);
    }
    public void wifiDirectFailed(String message){
        fail(message);
    }

    private void startServer(WifiDirectHost.Network network){
        TransferServer candidate=null;
        String token;
        PairingStore store;
        synchronized(lock){
            if(!running||stopping)return;
            store=pairing;
        }
        if(store==null){fail("配对信息不可用");return;}
        try{
            token=store.token();
            java.io.File sd=Environment.getExternalStorageDirectory();
            PhotoCatalog catalog=new PhotoCatalog(ProductFiles.photos(),sd);
            VideoCatalog videoCatalog=new VideoCatalog(sd);
            VideoSelectionStore selection=new VideoSelectionStore(videoCatalog,ProductFiles.transfer());
            PhotoTransferSource source=new PhotoTransferSource(catalog,selection);
            candidate=new TransferServer(token,source,bindAddress(network.address),network.prefixes,
                TransferSettings.port(app),new TransferServer.Listener(){
                    public void log(String name,String eventDetail){
                        android.util.Log.i("LutTransfer",name+" "+eventDetail);
                    }
                });
            synchronized(lock){
                if(!running||stopping){candidate.stop();return;}
                server=candidate;
            }
            int actual=candidate.start();
            synchronized(lock){
                if(!running||stopping){candidate.stop();return;}
                port=actual;detail=phoneConnected?"手机已连接 · 正在同步":"服务已就绪 · 等待手机连接";
            }
            try{store.rememberNetwork(network.ssid,network.address,actual);}catch(IOException ignored){}
            publish(TransferStatus.READY,detail);
            android.util.Log.i("LutTransfer","runtime_ready port="+actual+" address="+bindAddress(network.address)
                +" same_network_guard="+(network.prefixes.length()>0));
        }catch(Exception error){
            if(candidate!=null)candidate.stop();
            android.util.Log.w("LutTransfer","runtime_server_start_failed",error);
            fail("传输服务启动失败，请重试");
        }
    }

    private void fail(String message){
        WifiDirectHost stopHost;
        TransferServer stopServer;
        ExecutorService stopIo;
        synchronized(lock){
            if(stopping)return;
            stopping=true;running=false;
            stopHost=host;host=null;
            stopServer=server;server=null;
            stopIo=io;io=null;
            detail=message;port=0;phoneConnected=false;
        }
        if(stopServer!=null)stopServer.stop();
        if(stopHost!=null)stopHost.stop();
        if(stopIo!=null)stopIo.shutdownNow();
        synchronized(lock){stopping=false;}
        TransferStatus.set(TransferStatus.ERROR,message,ssid,psk,address,0,false);
        android.util.Log.w("LutTransfer","runtime_failed");
    }

    private void publish(String state,String message){
        String s,p,a;
        int actualPort;
        boolean connected;
        synchronized(lock){
            if(!running||stopping)return;
            s=ssid;p=psk;a=address;actualPort=port;connected=phoneConnected;
        }
        TransferStatus.set(state,message,s,p,a,actualPort,connected);
    }
    private static String bindAddress(String address){
        if(address==null)return null;
        String value=address.trim();
        int percent=value.indexOf('%');
        if(percent>=0)value=value.substring(0,percent);
        return value.length()==0?null:value;
    }
}
