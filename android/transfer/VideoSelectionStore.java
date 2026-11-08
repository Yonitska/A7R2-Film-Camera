package org.hongen.sonylutprobe;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/** Explicit allow-list of videos the user chose to expose to the phone.
 *
 * A selection is a path plus the exact size/mtime seen in the UI. The HTTP
 * source revalidates both against the current file and refuses changed or
 * unfinished recordings. Unselected files never appear in a manifest. */
final class VideoSelectionStore {
    private static final String FILE="VIDEOS.JSN",TEMP="VIDEOS.TMP";
    static final class Selection {
        final String id,name,path;
        final long size,time;
        Selection(String id,String name,String path,long size,long time){
            this.id=id;this.name=name;this.path=path;this.size=size;this.time=time;
        }
    }
    private final VideoCatalog catalog;
    private final File dir,file,temp;

    VideoSelectionStore(VideoCatalog catalog,File dir)throws IOException {
        this.catalog=catalog;
        this.dir=dir.getCanonicalFile();
        LutLibrary.ensureDirectory(this.dir);
        file=new File(this.dir,FILE);temp=new File(this.dir,TEMP);
    }

    synchronized List<Selection> selected(){
        List<Selection> result=new ArrayList<Selection>();
        for(Selection selection:load())if(current(selection)!=null)result.add(selection);
        return result;
    }
    synchronized Set<String> selectedIds(){
        Set<String> ids=new HashSet<String>();
        for(Selection selection:selected())ids.add(selection.id);
        return ids;
    }
    synchronized boolean isSelected(String id){
        if(id==null)return false;
        for(Selection selection:selected())if(id.equals(selection.id))return true;
        return false;
    }
    /** Returns the file only when the selection still names the same finished
     * file. Size or mtime drift means a recording was resumed or replaced. */
    synchronized File resolve(String id,boolean requireFinished){
        if(!TransferProtocolHelper.validVideoId(id))return null;
        for(Selection selection:load()){
            if(!id.equals(selection.id))continue;
            File file=current(selection);
            if(file==null)return null;
            if(requireFinished&&!finished(file))return null;
            return file;
        }
        return null;
    }
    synchronized void toggle(VideoCatalog.Item item)throws IOException {
        if(item==null||!item.ready)throw new IOException("录像还没有写完");
        List<Selection> list=load();Selection existing=null;
        for(Selection selection:list)if(item.id.equals(selection.id)){existing=selection;break;}
        if(existing!=null)list.remove(existing);
        else list.add(new Selection(item.id,item.name,item.file.getCanonicalPath(),item.size,item.time));
        save(list);
    }
    synchronized void selectAll(List<VideoCatalog.Item> items)throws IOException {
        List<Selection> list=load();
        Set<String> present=new HashSet<String>();
        for(VideoCatalog.Item item:items)if(item.ready)present.add(item.id);
        List<Selection> kept=new ArrayList<Selection>();
        for(Selection selection:list)if(present.contains(selection.id))kept.add(selection);
        for(VideoCatalog.Item item:items){
            if(!item.ready)continue;
            boolean exists=false;
            for(Selection selection:kept)if(item.id.equals(selection.id)){exists=true;break;}
            if(!exists)kept.add(new Selection(item.id,item.name,item.file.getCanonicalPath(),item.size,item.time));
        }
        save(kept);
    }
    synchronized void clear()throws IOException {save(new ArrayList<Selection>());}

    private File current(Selection selection){
        try{
            File file=new File(selection.path);
            if(!file.isFile())return null;
            File canonical=file.getCanonicalFile();
            if(!catalog.allows(canonical))return null;
            if(canonical.length()!=selection.size||canonical.lastModified()!=selection.time)return null;
            return canonical;
        }catch(Exception ignored){return null;}
    }
    private static boolean finished(File file){
        long now=System.currentTimeMillis(),modified=file.lastModified();
        return modified>0&&modified<=now&&now-modified>=VideoCatalog.SETTLE_MS;
    }
    private List<Selection> load(){
        List<Selection> result=new ArrayList<Selection>();
        if(!file.isFile())return result;
        try{
            Properties p=LutLibrary.readProperties(file);
            int count=Integer.parseInt(p.getProperty("count","0"));
            if(count<0||count>VideoCatalog.MAX_LIST)return result;
            Set<String> seenIds=new HashSet<String>(),seenPaths=new HashSet<String>();
            for(int i=0;i<count;i++){
                String id=p.getProperty("v"+i+".id",""),path=p.getProperty("v"+i+".path","");
                String name=p.getProperty("v"+i+".name","");
                long size,time;
                try{size=Long.parseLong(p.getProperty("v"+i+".size","-1"));
                    time=Long.parseLong(p.getProperty("v"+i+".time","-1"));}
                catch(NumberFormatException e){continue;}
                if(!TransferProtocolHelper.validVideoId(id)||path.length()==0||size<0||time<0)continue;
                if(!seenIds.add(id)||!seenPaths.add(path))continue;
                Selection selection=new Selection(id,name,path,size,time);
                if(current(selection)!=null)result.add(selection);
            }
        }catch(Exception ignored){/* unreadable allow-list means no videos, never everything */}
        Collections.sort(result,new Comparator<Selection>(){public int compare(Selection a,Selection b){
            return a.time==b.time?a.name.compareToIgnoreCase(b.name):a.time<b.time?1:-1;
        }});
        return result;
    }
    private void save(List<Selection> list)throws IOException {
        Properties p=new Properties();
        int count=0;
        for(Selection selection:list){
            String prefix="v"+count+".";
            p.setProperty(prefix+"id",selection.id);
            p.setProperty(prefix+"name",selection.name);
            p.setProperty(prefix+"path",selection.path);
            p.setProperty(prefix+"size",Long.toString(selection.size));
            p.setProperty(prefix+"time",Long.toString(selection.time));
            count++;
        }
        p.setProperty("count",Integer.toString(count));
        p.setProperty("format","1");
        LutLibrary.ensureDirectory(dir);
        LutLibrary.writeProperties(temp,p);
        if(file.exists()&&!file.delete()){
            temp.delete();
            throw new IOException("无法更新录像选择");
        }
        if(!temp.renameTo(file)){
            temp.delete();
            throw new IOException("无法保存录像选择");
        }
    }

    /** Local id syntax shared with the HTTP layer without exposing it publicly. */
    private static final class TransferProtocolHelper {
        static boolean validVideoId(String id){
            return id!=null&&id.length()>=7&&id.length()<=64
                &&id.startsWith("video-")&&id.matches("video-[0-9a-f]{8,60}");
        }
    }
}
