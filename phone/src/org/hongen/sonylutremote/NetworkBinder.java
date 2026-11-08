package org.hongen.sonylutremote;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;

/** Pins this process to the camera's local Wi-Fi for all transfers. Once
 * bound, Android does not send our sockets over mobile data. */
final class NetworkBinder {
    private final ConnectivityManager connectivity;
    private Network bound;

    NetworkBinder(Context context) {
        connectivity=(ConnectivityManager)context.getApplicationContext()
            .getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    synchronized boolean bind(Network network) {
        if(network==null||connectivity==null)return false;
        try{
            if(!connectivity.bindProcessToNetwork(network))return false;
            bound=network;
            return true;
        }catch(RuntimeException error){
            return false;
        }
    }

    synchronized void unbind() {
        if(connectivity==null||bound==null)return;
        try{connectivity.bindProcessToNetwork(null);}catch(RuntimeException ignored){}
        bound=null;
    }
}
