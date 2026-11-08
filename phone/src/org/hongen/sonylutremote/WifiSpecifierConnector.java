package org.hongen.sonylutremote;

import android.annotation.TargetApi;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.Build;

/** Android 10+ join path. This class is only instantiated reflectively when
 * the runtime is API 29 or newer, so older releases never load it. Android
 * always shows its own confirmation dialog; the app cannot bypass it. */
@TargetApi(Build.VERSION_CODES.Q)
final class WifiSpecifierConnector implements Connector {
    private static final int TIMEOUT_MS=60000;

    private final Context context;
    private final Object lock=new Object();
    private ConnectivityManager connectivity;
    private NetworkBinder binder;
    private volatile ConnectivityManager.NetworkCallback networkCallback;
    private volatile Callback callback;
    private volatile boolean active,connected;
    private volatile Network connectedNetwork;

    WifiSpecifierConnector(Context context){
        this.context=context.getApplicationContext();
    }

    public void connect(final String ssid,String psk,Callback target){
        synchronized(lock){
            callback=target;
            active=true;
            connected=false;
            connectedNetwork=null;
        }
        try{
            connectivity=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if(connectivity==null)throw new IllegalStateException("缺少网络服务");
            binder=new NetworkBinder(context);
            WifiNetworkSpecifier specifier=new WifiNetworkSpecifier.Builder()
                .setSsid(ssid).setWpa2Passphrase(psk).build();
            NetworkRequest request=new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setNetworkSpecifier(specifier)
                .build();
            ConnectivityManager.NetworkCallback callbackImpl=new ConnectivityManager.NetworkCallback(){
                public void onAvailable(Network network){
                    synchronized(lock){
                        if(!active)return;
                        if(binder==null||!binder.bind(network)){
                            failLocked("系统不允许绑定相机 Wi-Fi");
                            return;
                        }
                        connected=true;
                        connectedNetwork=network;
                        Callback local=callback;
                        if(local!=null)local.onWifiReady(network);
                    }
                }
                public void onLost(Network network){
                    synchronized(lock){
                        if(!connected||network==null||!network.equals(connectedNetwork))return;
                        connected=false;
                        connectedNetwork=null;
                        active=false;
                        if(binder!=null)binder.unbind();
                        Callback local=callback;
                        callback=null;
                        if(local!=null)local.onWifiLost("相机 Wi-Fi 已断开");
                    }
                    releaseRequest();
                }
                public void onUnavailable(){
                    synchronized(lock){
                        if(!active)return;
                        failLocked("未能加入相机 Wi-Fi，请在系统提示中允许连接并检查密码");
                    }
                    releaseRequest();
                }
            };
            networkCallback=callbackImpl;
            connectivity.requestNetwork(request,callbackImpl,TIMEOUT_MS);
        }catch(SecurityException error){
            fail("需要定位权限才能连接相机 Wi-Fi");
        }catch(Throwable error){
            fail("连接相机 Wi-Fi 失败："+brief(error));
        }
    }

    public void disconnect(){
        synchronized(lock){
            active=false;
            connected=false;
            connectedNetwork=null;
            callback=null;
            if(binder!=null){binder.unbind();binder=null;}
        }
        releaseRequest();
    }

    private void fail(String message){
        synchronized(lock){
            if(!active)return;
            failLocked(message);
        }
        releaseRequest();
    }
    private void failLocked(String message){
        active=false;
        connected=false;
        connectedNetwork=null;
        if(binder!=null)binder.unbind();
        Callback local=callback;
        callback=null;
        if(local!=null)local.onWifiFailed(message);
    }
    private void releaseRequest(){
        ConnectivityManager manager=connectivity;
        ConnectivityManager.NetworkCallback local=networkCallback;
        networkCallback=null;
        if(manager!=null&&local!=null){
            try{manager.unregisterNetworkCallback(local);}catch(RuntimeException ignored){}
        }
    }
    private static String brief(Throwable error){
        String message=error.getMessage();
        if(message==null||message.length()==0)message=error.getClass().getSimpleName();
        return message.length()>120?message.substring(0,120):message;
    }
}
