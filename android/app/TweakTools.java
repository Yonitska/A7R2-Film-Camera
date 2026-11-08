package org.hongen.sonylutprobe;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.text.format.Formatter;
import android.util.Log;

import com.github.ma1co.openmemories.tweak.Condition;
import com.github.ma1co.openmemories.tweak.NativeProperty;
import com.github.ma1co.openmemories.tweak.Procfs;
import com.github.ma1co.openmemories.tweak.Shell;

/** Developer/Info support ported from OpenMemories Tweak v0.11 (MIT,
 * © 2016 ma1co). Runs on LibraryActivity's single I/O executor: the native
 * shell calls can block for up to the 2 s condition wait. */
final class TweakTools {
    private static final String TAG="SonyTweak";
    private static final String[] TELNET_CMD={"busybox","telnetd","-l","sh"};

    static final class Status {
        String model="",region="",serial="",platform="";
        boolean wifiEnabled,wifiConnected,adbRunning,telnetRunning;
        String wifiDetail="";
        String error="";
    }

    private final Context context;
    private final WifiManager wifi;
    private final ConnectivityManager connectivity;
    private boolean infoLoaded;
    private String model="",region="",serial="",platform="";
    private String loadError="";

    TweakTools(Context context){
        this.context=context.getApplicationContext();
        this.wifi=(WifiManager)this.context.getSystemService(Context.WIFI_SERVICE);
        this.connectivity=(ConnectivityManager)this.context.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    Status readStatus(){
        Status s=new Status();
        if(!infoLoaded)loadInfo();
        s.model=model;s.region=region;s.serial=serial;s.platform=platform;
        s.error=loadError;
        if(wifi!=null){
            s.wifiEnabled=wifi.getWifiState()==WifiManager.WIFI_STATE_ENABLED
                    ||wifi.getWifiState()==WifiManager.WIFI_STATE_ENABLING;
            try{
                NetworkInfo network=connectivity==null?null:connectivity.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                s.wifiConnected=network!=null&&network.isConnected();
                WifiInfo info=wifi.getConnectionInfo();
                String ssid=info==null?null:info.getSSID();
                if(ssid!=null&&ssid.length()>1&&ssid.startsWith("\"")&&ssid.endsWith("\""))
                    ssid=ssid.substring(1,ssid.length()-1);
                String ip=info==null?"0.0.0.0":Formatter.formatIpAddress(info.getIpAddress());
                s.wifiDetail=s.wifiConnected
                        ?((ssid==null||ssid.length()==0?"Wi-Fi":ssid)+" · "+ip)
                        :(s.wifiEnabled?"正在连接":"已关闭");
            }catch(Throwable e){s.wifiDetail=s.wifiEnabled?"Wi-Fi 已开启":"已关闭";}
        }else{
            s.wifiDetail="Wi-Fi 不可用";
        }
        try{
            s.telnetRunning=telnetPid()!=-1;
            s.adbRunning=adbPid()!=-1;
        }catch(Throwable e){
            if(s.error.length()==0)s.error=e.toString();
        }
        return s;
    }

    private void loadInfo(){
        infoLoaded=true;
        try{
            model=property(NativeProperty.Key.MODEL_NAME);
            region=property(NativeProperty.Key.BACKUP_REGION);
            serial=property(NativeProperty.Key.SERIAL_NUMBER);
            platform=property(NativeProperty.Key.ANDROID_PLATFORM_VERSION);
        }catch(Throwable e){
            loadError=e.getClass().getSimpleName()+(e.getMessage()==null?"":": "+e.getMessage());
            model=region=serial=platform="不可用";
            Log.w(TAG,"info_unavailable",e);
        }
    }
    private static String property(NativeProperty.Key key)throws Exception{
        NativeProperty value=new NativeProperty(key);
        if(!value.isAvailable())return "不可用";
        String text=value.getValue();
        return text==null?"":text;
    }

    boolean wifiEnabled(){
        return wifi!=null&&(wifi.getWifiState()==WifiManager.WIFI_STATE_ENABLED
                ||wifi.getWifiState()==WifiManager.WIFI_STATE_ENABLING);
    }
    void setWifi(boolean enabled){
        if(wifi!=null)wifi.setWifiEnabled(enabled);
    }

    private int telnetPid(){return Procfs.findProcess(TELNET_CMD);}
    private int adbPid(){return Procfs.findProcess(new String[]{nativePath()});}
    private String nativePath(){return context.getApplicationInfo().nativeLibraryDir+"/libadbd.so";}

    void setTelnet(final boolean enabled)throws Exception{
        int pid=telnetPid();
        if(enabled){
            if(pid==-1)Shell.exec("busybox telnetd -l sh");
        }else if(pid!=-1){
            Shell.exec("kill -HUP "+pid+" $(ps -o pid= --ppid "+pid+")");
        }
        Condition.waitFor(new Condition.Runnable(){
            public boolean run(){return (telnetPid()!=-1)==enabled;}
        },500,2000);
    }

    void setAdb(final boolean enabled)throws Exception{
        int pid=adbPid();
        if(enabled){
            if(pid==-1)Shell.execAndroid(nativePath()+" &");
        }else if(pid!=-1){
            Shell.exec("kill -HUP "+pid);
        }
        Condition.waitFor(new Condition.Runnable(){
            public boolean run(){return (adbPid()!=-1)==enabled;}
        },500,2000);
    }

    /** Stop the unauthenticated daemons but leave Wi-Fi for the next session. */
    void stopDaemons(){
        try{
            int pid=telnetPid();
            if(pid!=-1)Shell.exec("kill -HUP "+pid+" $(ps -o pid= --ppid "+pid+")");
        }catch(Throwable e){Log.w(TAG,"telnet_stop",e);}
        try{
            int pid=adbPid();
            if(pid!=-1)Shell.exec("kill -HUP "+pid);
        }catch(Throwable e){Log.w(TAG,"adb_stop",e);}
    }
}
