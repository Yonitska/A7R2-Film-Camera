package org.hongen.sonylutprobe;

import java.util.ArrayList;
import java.util.List;

/** Last status of the main-process transfer runtime. The UI polls a snapshot;
 * the foreground service listens so its notification follows Wi-Fi Direct. */
final class TransferStatus {
    static final String OFF="off",STARTING="starting",READY="ready",ERROR="error",STOPPING="stopping";
    interface Listener{void transferStatusChanged(Snapshot snapshot);}
    static final class Snapshot {
        final String state,detail,ssid,psk,address;
        final int port;
        final boolean phoneConnected;
        final long updated;
        Snapshot(String state,String detail,String ssid,String psk,String address,int port,boolean phoneConnected){
            this.state=state==null?OFF:state;this.detail=detail==null?"":detail;
            this.ssid=ssid==null?"":ssid;this.psk=psk==null?"":psk;this.address=address==null?"":address;
            this.port=port;this.phoneConnected=phoneConnected;this.updated=System.currentTimeMillis();
        }
    }
    private static volatile Snapshot current=new Snapshot(OFF,"", "", "", "",0,false);
    private static final List<Listener> listeners=new ArrayList<Listener>();

    private TransferStatus(){}
    static Snapshot snapshot(){return current;}
    static synchronized void set(String state,String detail,String ssid,String psk,String address,int port,boolean phoneConnected){
        Snapshot next=new Snapshot(state,detail,ssid,psk,address,port,phoneConnected);
        current=next;
        Listener[] copy;
        synchronized(listeners){copy=listeners.toArray(new Listener[listeners.size()]);}
        for(Listener listener:copy)try{listener.transferStatusChanged(next);}catch(Throwable ignored){}
    }
    static synchronized void off(){set(OFF,"", "", "", "",0,false);}
    static synchronized void addListener(Listener listener){
        if(listener==null)return;
        synchronized(listeners){if(!listeners.contains(listener))listeners.add(listener);}
    }
    static synchronized void removeListener(Listener listener){
        if(listener==null)return;
        synchronized(listeners){listeners.remove(listener);}
    }
}
