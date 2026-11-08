package org.hongen.sonylutremote;

import org.hongen.sonylut.transfer.MediaEntry;
import org.hongen.sonylut.transfer.TransferProtocol;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Properties;

/** Phone-side download ledger. It is intentionally independent of Android so
 * the skip/retry policy can be tested on a workstation.
 *
 * "No record" means a new photo the sync service may download. The first
 * successful photo manifest marks everything already on the camera as
 * "skipped" unless the user chose to sync existing photos. Videos are never
 * auto-downloaded; their records only prove an explicit download completed. */
final class LedgerStore {
    static final String SKIPPED="skipped",DONE="done",FAILED="failed";
    static final class Record {
        final String id,state,etag,name,kind,detail;
        final long time;
        Record(String id,String state,String etag,String name,String kind,long time,String detail){
            this.id=id;this.state=state;this.etag=etag;this.name=name;this.kind=kind;this.time=time;this.detail=detail;
        }
    }
    private static final int MAX_RECORDS=20000;
    private final File file,temp;
    private final LinkedHashMap<String,Record> records=new LinkedHashMap<String,Record>();
    private boolean initialized;
    private boolean loaded;

    LedgerStore(File file){
        this.file=file;
        this.temp=new File(file.getParentFile(),file.getName()+".tmp");
    }

    synchronized String stateOf(MediaEntry entry){
        if(entry==null)return "";
        load();
        Record record=records.get(entry.id);
        if(record==null)return "";
        if(DONE.equals(record.state)&&!record.etag.equals(entry.etag))return "";
        return record.state;
    }
    synchronized boolean shouldAutoDownload(MediaEntry entry){
        String state=stateOf(entry);
        return state.length()==0;
    }
    synchronized boolean isDone(MediaEntry entry){
        return DONE.equals(stateOf(entry));
    }
    synchronized void baselineIfNeeded(List<MediaEntry> photos,boolean includeExisting){
        load();
        if(initialized)return;
        if(!includeExisting){
            for(MediaEntry entry:photos){
                if(entry==null||!records.containsKey(entry.id))skip(entry);
            }
        }
        initialized=true;
        save();
    }
    synchronized void markDone(MediaEntry entry){
        load();
        records.put(entry.id,new Record(entry.id,DONE,entry.etag,entry.name,entry.kind,entry.time,""));
        save();
    }
    synchronized void markFailed(MediaEntry entry,String detail){
        load();
        records.put(entry.id,new Record(entry.id,FAILED,entry.etag,entry.name,entry.kind,entry.time,
            detail==null?"":detail));
        save();
    }
    synchronized void includeSkipped(){
        load();
        List<String> remove=new ArrayList<String>();
        for(Record record:records.values())if(SKIPPED.equals(record.state))remove.add(record.id);
        for(String id:remove)records.remove(id);
        save();
    }
    synchronized void retryFailed(){
        load();
        List<String> remove=new ArrayList<String>();
        for(Record record:records.values())if(FAILED.equals(record.state))remove.add(record.id);
        for(String id:remove)records.remove(id);
        save();
    }
    synchronized int count(List<MediaEntry> entries,String state){
        int count=0;
        for(MediaEntry entry:entries)if(state.equals(stateOf(entry)))count++;
        return count;
    }
    synchronized String detailOf(MediaEntry entry){
        load();
        Record record=entry==null?null:records.get(entry.id);
        return record==null?"":record.detail;
    }

    private void skip(MediaEntry entry){
        records.put(entry.id,new Record(entry.id,SKIPPED,entry.etag,entry.name,entry.kind,entry.time,""));
    }
    private void load(){
        if(loaded)return;
        loaded=true;
        if(!file.isFile())return;
        Properties p=new Properties();
        try{
            FileInputStream input=new FileInputStream(file);
            try{p.load(input);}finally{input.close();}
            initialized="true".equals(p.getProperty("initialized"));
            int count=(int)number(p.getProperty("count","0"));
            if(count<0||count>MAX_RECORDS)return;
            for(int i=0;i<count;i++){
                String prefix="r"+i+".";
                String id=p.getProperty(prefix+"id","");
                String state=p.getProperty(prefix+"state","");
                String etag=p.getProperty(prefix+"etag","");
                String name=p.getProperty(prefix+"name","");
                String kind=p.getProperty(prefix+"kind","");
                String detail=p.getProperty(prefix+"detail","");
                long time=number(p.getProperty(prefix+"time","0"));
                if(!TransferProtocol.safeId(id)||time<0||etag.length()>128||name.length()>255)continue;
                if(!SKIPPED.equals(state)&&!DONE.equals(state)&&!FAILED.equals(state))continue;
                if(!("photo".equals(kind)||"video".equals(kind)))continue;
                records.put(id,new Record(id,state,etag,name,kind,time,detail.length()>300?detail.substring(0,300):detail));
            }
        }catch(Exception ignored){
            records.clear();
            initialized=false;
        }
    }
    private void save(){
        Properties p=new Properties();
        p.setProperty("format","1");
        p.setProperty("initialized",initialized?"true":"false");
        p.setProperty("count",Integer.toString(records.size()));
        int index=0;
        for(Record record:records.values()){
            String prefix="r"+index+".";
            p.setProperty(prefix+"id",record.id);
            p.setProperty(prefix+"state",record.state);
            p.setProperty(prefix+"etag",record.etag);
            p.setProperty(prefix+"name",record.name);
            p.setProperty(prefix+"kind",record.kind);
            p.setProperty(prefix+"time",Long.toString(record.time));
            p.setProperty(prefix+"detail",record.detail==null?"":record.detail);
            index++;
        }
        try{
            File parent=file.getParentFile();
            if(parent!=null&&!parent.isDirectory()&&!parent.mkdirs())throw new IOException("ledger directory");
            FileOutputStream out=new FileOutputStream(temp);
            try{p.store(out,"SonyLutRemote ledger 1");out.getFD().sync();}finally{out.close();}
            if(file.exists()&&!file.delete())throw new IOException("ledger replace");
            if(!temp.renameTo(file))throw new IOException("ledger rename");
        }catch(IOException error){
            System.err.println("SonyLutRemote ledger_save_failed: "+error.getMessage());
            temp.delete();
        }
    }
    private static long number(String value){
        try{return Long.parseLong(value);}catch(Exception ignored){return -1;}
    }
}
