package org.hongen.sonylutremote;

import android.content.Context;
import android.net.Network;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

/** Selects the Wi-Fi join path for the current Android release and serialises
 * callbacks on the main thread. Android 10+ uses WifiNetworkSpecifier; older
 * releases use the legacy addNetwork/enableNetwork path. */
final class WifiConnector {
    interface Callback {
        void onWifiReady(Network network);
        void onWifiLost(String reason);
        void onWifiFailed(String reason);
    }

    private final Context context;
    private final Handler main=new Handler(Looper.getMainLooper());
    private Connector implementation;
    private Callback callback;
    private int generation;

    WifiConnector(Context context) {
        this.context=context.getApplicationContext();
    }

    static boolean systemConfirmationRequired() {
        return Build.VERSION.SDK_INT>=29;
    }

    void connect(final String ssid,final String psk,final Callback target) {
        disconnect();
        callback=target;
        final int token=++generation;
        try{
            if(Build.VERSION.SDK_INT>=29){
                java.lang.reflect.Constructor<?> constructor=Class
                    .forName("org.hongen.sonylutremote.WifiSpecifierConnector")
                    .getDeclaredConstructor(Context.class);
                constructor.setAccessible(true);
                implementation=(Connector)constructor.newInstance(context);
            }else{
                implementation=new LegacyWifiConnector(context);
            }
            implementation.connect(ssid,psk,new Connector.Callback(){
                public void onWifiReady(final Network network){
                    main.post(new Runnable(){public void run(){
                        if(token!=generation)return;
                        if(callback!=null)callback.onWifiReady(network);
                    }});
                }
                public void onWifiLost(final String reason){
                    main.post(new Runnable(){public void run(){
                        if(token!=generation)return;
                        if(callback!=null)callback.onWifiLost(reason);
                    }});
                }
                public void onWifiFailed(final String reason){
                    main.post(new Runnable(){public void run(){
                        if(token!=generation)return;
                        if(callback!=null)callback.onWifiFailed(reason);
                    }});
                }
            });
        }catch(Throwable error){
            Connector local=implementation;
            implementation=null;
            if(local!=null)try{local.disconnect();}catch(Throwable ignored){}
            if(token==generation&&callback!=null)callback.onWifiFailed("无法连接相机 Wi-Fi："+brief(error));
        }
    }

    void disconnect() {
        generation++;
        callback=null;
        Connector local=implementation;
        implementation=null;
        if(local!=null)try{local.disconnect();}catch(Throwable ignored){}
    }

    private static String brief(Throwable error){
        String message=error.getMessage();
        if(message==null||message.length()==0)message=error.getClass().getSimpleName();
        return message.length()>120?message.substring(0,120):message;
    }
}
