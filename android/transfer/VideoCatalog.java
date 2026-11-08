package org.hongen.sonylutprobe;

import android.os.Environment;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Read-only scan of finished camera videos. The transfer page never opens a
 * camera session; it lists files the body already wrote and the user ticks. */
final class VideoCatalog {
    static final long SETTLE_MS=3000L;
    static final int MAX_LIST=400;
    static final class Item {
        final String id,name;
        final File file;
        final long size,time;
        final boolean ready;
        Item(String id,String name,File file,long size,long time,boolean ready){
            this.id=id;this.name=name;this.file=file;this.size=size;this.time=time;this.ready=ready;
        }
    }
    private final File sd;
    private final List<File> roots=new ArrayList<File>();

    VideoCatalog(File sd)throws IOException {
        this.sd=sd.getCanonicalFile();
        add(new File(this.sd,"DCIM"));
        add(new File(this.sd,"MP_ROOT"));
        add(new File(this.sd,"PRIVATE/M4ROOT/CLIP"));
        add(new File(this.sd,"PRIVATE/AVCHD/BDMV/STREAM"));
    }
    private void add(File root)throws IOException {
        File canonical=root.getCanonicalFile();
        if(canonical.isDirectory())roots.add(canonical);
    }

    List<Item> scan(){
        long now=System.currentTimeMillis();
        List<Item> found=new ArrayList<Item>();
        Set<String> seen=new HashSet<String>();
        for(File root:roots)collect(root,root,0,now,found,seen);
        Collections.sort(found,new Comparator<Item>(){public int compare(Item a,Item b){
            return a.time==b.time?a.name.compareToIgnoreCase(b.name):a.time<b.time?1:-1;
        }});
        if(found.size()>MAX_LIST)return new ArrayList<Item>(found.subList(0,MAX_LIST));
        return found;
    }
    private void collect(File root,File dir,int depth,long now,List<Item> out,Set<String> seen){
        if(depth>5)return;
        File[] files=dir.listFiles();
        if(files==null)return;
        for(File file:files){
            if(out.size()>=MAX_LIST)return;
            String name=file.getName();
            if(name.startsWith(".")||name.equalsIgnoreCase("SONYLUT")||name.equalsIgnoreCase("THUMB"))continue;
            if(file.isDirectory()){collect(root,file,depth+1,now,out,seen);continue;}
            if(!videoName(name)||!file.isFile())continue;
            File canonical;
            try{canonical=file.getCanonicalFile();}catch(IOException e){continue;}
            if(!inside(root,canonical))continue;
            long size=file.length();
            long modified=file.lastModified();
            if(size<=0||!seen.add(canonical.getPath()))continue;
            boolean ready=modified>0&&modified<=now&&now-modified>=SETTLE_MS;
            out.add(new Item(idFor(canonical),displayName(canonical),canonical,size,modified,ready));
        }
    }
    private static String displayName(File file){
        File parent=file.getParentFile();
        return parent==null?file.getName():parent.getName()+"/"+file.getName();
    }
    static String idFor(File file){
        try{
            String path;
            try{path=file.getCanonicalPath();}catch(IOException e){path=file.getAbsolutePath();}
            MessageDigest digest=MessageDigest.getInstance("SHA-1");
            byte[] hash=digest.digest(path.getBytes("UTF-8"));
            StringBuilder out=new StringBuilder("video-");
            for(int i=0;i<8;i++)out.append(String.format(Locale.US,"%02x",hash[i]&255));
            return out.toString();
        }catch(Exception ignored){
            return "video-"+Integer.toHexString(file.getAbsolutePath().hashCode());
        }
    }
    boolean allows(File file){
        try{
            File canonical=file.getCanonicalFile();
            if(!canonical.isFile()||!videoName(canonical.getName()))return false;
            for(File root:roots)if(inside(root,canonical))return true;
        }catch(IOException ignored){}
        return false;
    }
    static boolean videoName(String name){
        String lower=name.toLowerCase(Locale.US);
        return lower.endsWith(".mp4")||lower.endsWith(".mts")||lower.endsWith(".m2ts");
    }
    private static boolean inside(File root,File file){
        String base=root.getPath(),path=file.getPath();
        return path.equals(base)||path.startsWith(base+File.separator);
    }
    File sd(){return sd;}
    static File externalRoot(){return Environment.getExternalStorageDirectory();}
}
