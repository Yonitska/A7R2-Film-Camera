package org.hongen.sonylutremote;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Android 6–9 join path: add/update a WPA2 configuration, enable it, then wait
 * for the Wi-Fi transport to report the requested SSID before binding. */
final class LegacyWifiConnector implements Connector {
    private static final long TIMEOUT_MS=30000L;
    private static final long POLL_MS=500L;

    private final Context context;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Set<Network> wifiNetworks=Collections.synchronizedSet(new HashSet<Network>());
    private WifiManager wifi;
    private ConnectivityManager connectivity;
    private NetworkBinder binder;
    private ConnectivityManager.NetworkCallback networkCallback;
    private Callback callback;
    private String targetSsid="";
    private boolean active,connected;
    private Network connectedNetwork;
    private long deadline;

    private final Runnable poll=new Runnable(){public void run(){
        if(!active||connected)return;
        if(SystemClock.elapsedRealtime()>deadline){fail("连接相机 Wi-Fi 超时，请检查名称和密码");return;}
        checkSsid();
        if(active&&!connected)handler.postDelayed(this,POLL_MS);
    }};
    private final Runnable timeout=new Runnable(){public void run(){
        if(active&&!connected)fail("连接相机 Wi-Fi 超时，请检查名称和密码");
    }};

    LegacyWifiConnector(Context context){
        this.context=context.getApplicationContext();
    }

    public void connect(String ssid,String psk,Callback target){
        callback=target;
        targetSsid=ssid==null?"":ssid;
        active=true;
        connected=false;
        deadline=SystemClock.elapsedRealtime()+TIMEOUT_MS;
        try{
            wifi=(WifiManager)context.getSystemService(Context.WIFI_SERVICE);
            connectivity=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if(wifi==null||connectivity==null){fail("系统不支持 Wi-Fi 连接");return;}
            binder=new NetworkBinder(context);
            if(!wifi.isWifiEnabled())wifi.setWifiEnabled(true);
            int networkId=configure(targetSsid,psk);
            if(networkId<0){fail("无法保存相机 Wi-Fi 配置，请授予定位权限");return;}
            if(!wifi.enableNetwork(networkId,true)){fail("系统拒绝连接相机 Wi-Fi");return;}
            wifi.saveConfiguration();
            wifi.reconnect();
            NetworkRequest request=new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
            networkCallback=new ConnectivityManager.NetworkCallback(){
                public void onAvailable(Network network){onWifiNetwork(network);}
                public void onCapabilitiesChanged(Network network,NetworkCapabilities capabilities){
                    /* the self-scheduling poll re-checks the SSID */
                }
                public void onLost(Network network){onWifiGone(network);}
            };
            connectivity.registerNetworkCallback(request,networkCallback);
            handler.postDelayed(timeout,TIMEOUT_MS);
            handler.post(poll);
        }catch(SecurityException error){
            fail("需要定位权限才能连接相机 Wi-Fi");
        }catch(RuntimeException error){
            fail("连接相机 Wi-Fi 失败："+brief(error));
        }
    }

    public void disconnect(){
        active=false;
        handler.removeCallbacks(poll);
        handler.removeCallbacks(timeout);
        if(networkCallback!=null&&connectivity!=null){
            try{connectivity.unregisterNetworkCallback(networkCallback);}catch(RuntimeException ignored){}
        }
        networkCallback=null;
        if(binder!=null){binder.unbind();binder=null;}
        wifiNetworks.clear();
        connectedNetwork=null;
        connected=false;
        callback=null;
    }

    private void onWifiNetwork(final Network network){
        if(!active)return;
        wifiNetworks.add(network);
    }
    private void onWifiGone(final Network network){
        handler.post(new Runnable(){public void run(){
            wifiNetworks.remove(network);
            if(connected&&network!=null&&network.equals(connectedNetwork)){
                connected=false;
                connectedNetwork=null;
                if(binder!=null)binder.unbind();
                active=false;
                handler.removeCallbacks(poll);
                handler.removeCallbacks(timeout);
                Callback local=callback;
                callback=null;
                if(local!=null)local.onWifiLost("相机 Wi-Fi 已断开");
            }
        }});
    }

    private void checkSsid(){
        if(!active||connected)return;
        WifiInfo info;
        try{info=wifi.getConnectionInfo();}catch(RuntimeException error){return;}
        if(info==null||!targetSsid.equals(unquote(info.getSSID())))return;
        Network chosen=null;
        synchronized(wifiNetworks){
            for(Network network:wifiNetworks){
                NetworkCapabilities capabilities=connectivity.getNetworkCapabilities(network);
                if(capabilities!=null&&capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)){
                    chosen=network;
                    break;
                }
            }
        }
        if(chosen==null)return;
        if(binder==null||!binder.bind(chosen)){fail("系统不允许绑定相机 Wi-Fi");return;}
        connected=true;
        connectedNetwork=chosen;
        handler.removeCallbacks(poll);
        handler.removeCallbacks(timeout);
        Callback local=callback;
        if(local!=null)local.onWifiReady(chosen);
    }

    private int configure(String ssid,String psk){
        String quoted=quote(ssid);
        WifiConfiguration config=null;
        try{
            List<WifiConfiguration> configured=wifi.getConfiguredNetworks();
            if(configured!=null){
                for(WifiConfiguration candidate:configured){
                    if(candidate!=null&&ssid.equals(unquote(candidate.SSID))){config=candidate;break;}
                }
            }
        }catch(RuntimeException ignored){}
        boolean existing=config!=null;
        if(!existing)config=new WifiConfiguration();
        config.SSID=quoted;
        config.preSharedKey=quote(psk);
        config.allowedAuthAlgorithms.clear();
        config.allowedKeyManagement.clear();
        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
        config.allowedProtocols.clear();
        config.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
        config.allowedPairwiseCiphers.clear();
        config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
        config.allowedGroupCiphers.clear();
        config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
        config.hiddenSSID=false;
        int id=-1;
        if(existing){
            id=config.networkId;
            if(wifi.updateNetwork(config)<0){
                if(id>=0)wifi.removeNetwork(id);
                id=wifi.addNetwork(config);
            }
        }else{
            id=wifi.addNetwork(config);
        }
        return id;
    }

    private void fail(String message){
        if(!active)return;
        active=false;
        handler.removeCallbacks(poll);
        handler.removeCallbacks(timeout);
        if(networkCallback!=null&&connectivity!=null){
            try{connectivity.unregisterNetworkCallback(networkCallback);}catch(RuntimeException ignored){}
        }
        networkCallback=null;
        if(binder!=null){binder.unbind();binder=null;}
        connected=false;
        connectedNetwork=null;
        wifiNetworks.clear();
        Callback local=callback;
        callback=null;
        if(local!=null)local.onWifiFailed(message);
    }

    private static String quote(String value){return "\""+(value==null?"":value)+"\"";}
    private static String unquote(String value){
        if(value==null)return "";
        if(value.length()>=2&&value.charAt(0)=='"'&&value.charAt(value.length()-1)=='"')
            return value.substring(1,value.length()-1);
        return value;
    }
    private static String brief(Throwable error){
        String message=error.getMessage();
        if(message==null||message.length()==0)message=error.getClass().getSimpleName();
        return message.length()>120?message.substring(0,120):message;
    }
}
