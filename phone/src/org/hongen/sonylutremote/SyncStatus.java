package org.hongen.sonylutremote;

import org.hongen.sonylut.transfer.MediaEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Last sync state for the Activity and foreground notification. */
final class SyncStatus {
    static final String IDLE="idle",CONNECTING="connecting",CONNECTED="connected",
        SYNCING="syncing",ERROR="error",STOPPED="stopped";
    interface Listener { void syncStatusChanged(Snapshot snapshot); }
    static final class Item {
        final MediaEntry media;
        final String state,detail;
        final long bytes,total;
        Item(MediaEntry media,String state,long bytes,long total,String detail){
            this.media=media;this.state=state;this.bytes=bytes;this.total=total;this.detail=detail==null?"":detail;
        }
    }
    static final class Snapshot {
        final String state,message,ssid,host;
        final int port;
        final boolean running;
        final long updated;
        final List<Item> photos,videos;
        Snapshot(String state,String message,String ssid,String host,int port,boolean running,
                 List<Item> photos,List<Item> videos){
            this.state=state==null?IDLE:state;this.message=message==null?"":message;
            this.ssid=ssid==null?"":ssid;this.host=host==null?"":host;this.port=port;this.running=running;
            this.updated=System.currentTimeMillis();
            this.photos=immutable(photos);this.videos=immutable(videos);
        }
        boolean connected(){return CONNECTED.equals(state)||SYNCING.equals(state);}
        private static List<Item> immutable(List<Item> items){
            if(items==null)return Collections.emptyList();
            return Collections.unmodifiableList(new ArrayList<Item>(items));
        }
    }
    private static volatile Snapshot current=new Snapshot(IDLE,"", "", "",8080,false,null,null);
    private static final List<Listener> listeners=new ArrayList<Listener>();

    private SyncStatus(){}
    static Snapshot snapshot(){return current;}
    static void set(String state,String message,String ssid,String host,int port,boolean running,
                    List<Item> photos,List<Item> videos){
        Snapshot next=new Snapshot(state,message,ssid,host,port,running,photos,videos);
        current=next;
        Listener[] copy;
        synchronized(listeners){copy=listeners.toArray(new Listener[listeners.size()]);}
        for(Listener listener:copy)try{listener.syncStatusChanged(next);}catch(Throwable ignored){}
    }
    static void addListener(Listener listener){
        if(listener==null)return;
        synchronized(listeners){if(!listeners.contains(listener))listeners.add(listener);}
    }
    static void removeListener(Listener listener){
        if(listener==null)return;
        synchronized(listeners){listeners.remove(listener);}
    }
}
