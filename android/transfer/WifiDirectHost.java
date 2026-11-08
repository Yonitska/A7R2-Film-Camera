package org.hongen.sonylutprobe;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;

import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Starts the camera as the Wi-Fi Direct group owner through Sony's private
 * service. The call sequence follows the behaviour proven by ShotDirector
 * (MIT): enable Wi-Fi, enable Direct, wait for DIRECT_STATE_ENABLED, choose a
 * saved configuration, startGo, then read SSID/PSK/address from the group
 * creation broadcast.
 *
 * All Sony classes are accessed reflectively because they exist only in the
 * body firmware. This class never logs the WPA2 passphrase or pairing token. */
final class WifiDirectHost {
    interface Listener {
        void wifiDirectGroup(Network network);
        void wifiDirectStation(boolean connected,String detail);
        void wifiDirectFailed(String detail);
    }
    static final class Network {
        final String ssid,psk,ifName,address,prefixes;
        Network(String ssid,String psk,String ifName,String address,String prefixes){
            this.ssid=ssid;this.psk=psk;this.ifName=ifName;this.address=address;this.prefixes=prefixes;
        }
    }
    private static final String SERVICE="wifi-direct";
    private static final String ACTION_STATE="com.sony.wifi.direct.DIRECT_STATE_CHANGED_ACTION";
    private static final String ACTION_GROUP_OK="com.sony.wifi.direct.GROUP_CREATE_SUCCESS_ACTION";
    private static final String ACTION_GROUP_FAIL="com.sony.wifi.direct.GROUP_CREATE_FAILURE_ACTION";
    private static final String ACTION_STA_UP="com.sony.wifi.direct.STA_CONNECTED_ACTION";
    private static final String ACTION_STA_DOWN="com.sony.wifi.direct.STA_DISCONNECTED_ACTION";
    private static final String EXTRA_STATE="direct_state";
    private static final String EXTRA_CONFIG="direct_config";
    private static final String EXTRA_ERROR="error_code";
    private static final String EXTRA_STA="sta_addr";
    private static final int STATE_ENABLED=4;
    private static final long WATCHDOG_MS=12000L;

    private final Context context;
    private final Listener listener;
    private final Handler main=new Handler(Looper.getMainLooper());
    private WifiManager wifi;
    private Object direct;
    private BroadcastReceiver receiver;
    private boolean running,registered,wifiWasEnabled,startRequested,groupReady,failureSent,retryScheduled;
    private String detail="";

    WifiDirectHost(Context context,Listener listener){
        this.context=context.getApplicationContext();this.listener=listener;
    }

    void start(){
        if(running)return;
        running=true;detail="正在启动 Wi-Fi Direct";
        try{
            wifi=(WifiManager)context.getSystemService(Context.WIFI_SERVICE);
            direct=context.getSystemService(SERVICE);
            if(direct==null){
                fail("这台相机没有提供 Wi-Fi Direct 服务");return;
            }
            if(wifi!=null)wifiWasEnabled=wifi.isWifiEnabled();
        }catch(Throwable error){
            fail("无法读取 Wi-Fi 服务");return;
        }
        register();
        // register() reports a failure through the listener, which also
        // stops this host; do not continue enabling Direct in that case.
        if(!running)return;
        try{
            if(wifi!=null&&!wifiWasEnabled)wifi.setWifiEnabled(true);
            call(direct,"setDirectEnabled",new Class[]{boolean.class},new Object[]{Boolean.TRUE});
        }catch(Throwable error){
            fail("无法开启 Wi-Fi Direct");return;
        }
        main.postDelayed(watchdog,WATCHDOG_MS);
    }

    void stop(){
        if(!running&&direct==null&&receiver==null)return;
        running=false;
        main.removeCallbacks(watchdog);
        Object manager=direct;
        try{if(manager!=null)call(manager,"removeGroup",new Class[]{},new Object[]{});}catch(Throwable ignored){}
        try{if(manager!=null)call(manager,"setDirectEnabled",new Class[]{boolean.class},new Object[]{Boolean.FALSE});}catch(Throwable ignored){}
        try{if(wifi!=null&&!wifiWasEnabled)wifi.setWifiEnabled(false);}catch(Throwable ignored){}
        unregister();
        direct=null;groupReady=false;startRequested=false;retryScheduled=false;failureSent=false;
        detail="已关闭";
    }
    boolean isGroupReady(){return groupReady;}
    String detail(){return detail;}

    private void register(){
        if(registered)return;
        receiver=new BroadcastReceiver(){
            @Override public void onReceive(Context c,Intent intent){
                if(intent==null||intent.getAction()==null)return;
                String action=intent.getAction();
                if(ACTION_STATE.equals(action)){
                    int state=intent.getIntExtra(EXTRA_STATE,-1);
                    if(state==STATE_ENABLED)startGroup();
                }else if(ACTION_GROUP_OK.equals(action)){
                    readGroup(intent);
                }else if(ACTION_GROUP_FAIL.equals(action)){
                    int code=intent.getIntExtra(EXTRA_ERROR,-1);
                    fail("Wi-Fi Direct 群组创建失败（"+code+"）");
                }else if(ACTION_STA_UP.equals(action)){
                    String address=intent.getStringExtra(EXTRA_STA);
                    detail="手机已连接"+(address==null?"":" · "+address);
                    listener.wifiDirectStation(true,detail);
                }else if(ACTION_STA_DOWN.equals(action)){
                    detail="手机已断开 · 群组仍在等待连接";
                    listener.wifiDirectStation(false,detail);
                }
            }
        };
        IntentFilter filter=new IntentFilter();
        filter.addAction(ACTION_STATE);filter.addAction(ACTION_GROUP_OK);filter.addAction(ACTION_GROUP_FAIL);
        filter.addAction(ACTION_STA_UP);filter.addAction(ACTION_STA_DOWN);
        try{context.registerReceiver(receiver,filter);registered=true;}
        catch(Throwable error){receiver=null;fail("无法监听 Wi-Fi Direct 状态");}
    }
    private void unregister(){
        if(!registered||receiver==null)return;
        try{context.unregisterReceiver(receiver);}catch(Throwable ignored){}
        receiver=null;registered=false;
    }
    private void startGroup(){
        if(!running||groupReady||startRequested)return;
        startRequested=true;detail="正在创建 Wi-Fi Direct 群组";
        try{
            Object list=call(direct,"getConfigurations",new Class[]{},new Object[]{});
            if(!(list instanceof List)||((List<?>)list).isEmpty()){
                fail("相机没有可用的 Wi-Fi Direct 配置");return;
            }
            List<?> configurations=(List<?>)list;
            Object configuration=configurations.get(configurations.size()-1);
            Object id=call(configuration,"getNetworkId",new Class[]{},new Object[]{});
            if(!(id instanceof Integer)){fail("无法读取 Wi-Fi Direct 网络编号");return;}
            Object started=call(direct,"startGo",new Class[]{int.class},new Object[]{id});
            if(Boolean.FALSE.equals(started))
                android.util.Log.w("LutTransfer","wifi_direct_start_go_returned_false");
        }catch(Throwable error){
            startRequested=false;
            fail("启动 Wi-Fi Direct 群组失败");
        }
    }
    private void readGroup(Intent intent){
        Object configuration=null;
        try{configuration=intent.getParcelableExtra(EXTRA_CONFIG);}catch(Throwable ignored){}
        if(configuration==null){
            try{configuration=call(direct,"getCurrentConfiguration",new Class[]{},new Object[]{});}catch(Throwable ignored){}
        }
        if(configuration==null){fail("Wi-Fi Direct 群组已建立，但无法读取网络参数");return;}
        try{
            String ssid=text(call(configuration,"getSsid",new Class[]{},new Object[]{}));
            String psk=text(call(configuration,"getPreSharedKey",new Class[]{},new Object[]{}));
            String ifName=text(call(configuration,"getIfName",new Class[]{},new Object[]{}));
            String address=text(call(configuration,"getP2PIfAddress",new Class[]{},new Object[]{}));
            if(ssid.length()==0){fail("Wi-Fi Direct 未返回网络名称");return;}
            groupReady=true;startRequested=true;detail="群组已建立，等待手机连接";
            main.removeCallbacks(watchdog);
            listener.wifiDirectGroup(new Network(ssid,psk,ifName,address,prefixes(address)));
            android.util.Log.i("LutTransfer","wifi_direct_group ssid="+ssid
                +" psk="+(psk.length()>0?"set":"empty")+" address="+address+" if="+ifName);
        }catch(Throwable error){
            fail("无法读取 Wi-Fi Direct 网络参数");
        }
    }
    private final Runnable watchdog=new Runnable(){public void run(){
        if(!running||groupReady)return;
        if(!retryScheduled){
            int state=-1;
            try{Object value=call(direct,"getDirectState",new Class[]{},new Object[]{});
                if(value instanceof Integer)state=(Integer)value;}catch(Throwable ignored){}
            if(state==STATE_ENABLED){
                retryScheduled=true;
                startRequested=false;
                startGroup();
                main.postDelayed(watchdog,8000L);
                return;
            }
            fail("Wi-Fi Direct 启动超时，请重试");
            return;
        }
        fail("Wi-Fi Direct 群组创建超时，请重试");
    }};

    private void fail(String message){
        if(failureSent||!running)return;
        failureSent=true;detail=message;
        main.removeCallbacks(watchdog);
        android.util.Log.w("LutTransfer","wifi_direct_failed detail="+message);
        listener.wifiDirectFailed(message);
    }
    private static Object call(Object target,String name,Class[] types,Object[] args)throws Exception{
        if(target==null)throw new IllegalStateException("service unavailable");
        Method method=target.getClass().getMethod(name,types);
        method.setAccessible(true);
        return method.invoke(target,args);
    }
    private static String text(Object value){return value instanceof String?(String)value:"";}

    /** The phone comes from the P2P link, so every plausible local IPv4 /24 is
     * allowed. When the body hides its interface address we keep only the two
     * documented group-owner prefixes; the token still authenticates. */
    private static String prefixes(String address){
        Set<String> prefixes=new LinkedHashSet<String>();
        addPrefix(prefixes,address);
        if(prefixes.isEmpty()){
            prefixes.add("192.168.122.");
            prefixes.add("192.168.49.");
        }
        try{
            Enumeration<NetworkInterface> interfaces=NetworkInterface.getNetworkInterfaces();
            while(interfaces!=null&&interfaces.hasMoreElements()){
                NetworkInterface item=interfaces.nextElement();
                if(item==null||item.isLoopback())continue;
                Enumeration<InetAddress> addresses=item.getInetAddresses();
                while(addresses!=null&&addresses.hasMoreElements()){
                    InetAddress next=addresses.nextElement();
                    String host=next==null?null:next.getHostAddress();
                    if(next!=null&&next.isSiteLocalAddress())addPrefix(prefixes,host);
                }
            }
        }catch(Throwable ignored){}
        StringBuilder out=new StringBuilder();
        int count=0;
        for(String prefix:prefixes){
            if(count>=8)break;
            if(out.length()>0)out.append(',');
            out.append(prefix);
            count++;
        }
        return out.toString();
    }
    private static void addPrefix(Set<String> prefixes,String address){
        if(address==null)return;
        String value=address.trim();
        int percent=value.indexOf('%');
        if(percent>=0)value=value.substring(0,percent);
        String[] parts=value.split("\\.");
        if(parts.length!=4)return;
        for(String part:parts){
            if(part.length()==0||part.length()>3)return;
            try{int n=Integer.parseInt(part);if(n<0||n>255)return;}catch(NumberFormatException e){return;}
        }
        prefixes.add(parts[0]+"."+parts[1]+"."+parts[2]+".");
    }
}
