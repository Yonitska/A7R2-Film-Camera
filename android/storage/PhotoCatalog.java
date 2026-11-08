package org.hongen.sonylutprobe;

import java.io.*;
import java.util.*;

/** Small pair receipts. Capture appends; Gallery may delete a whole pair on request. */
final class PhotoCatalog {
    static final class Pair {
        final String id,lut,camera;final int grid;final long time,displayMs,readyMs;final File original,graded;final boolean single;
        Pair(String id,String lut,int grid,long time,long displayMs,long readyMs,File original,File graded){
            this(id,lut,"",grid,time,displayMs,readyMs,original,graded,false);
        }
        Pair(String id,String lut,String camera,int grid,long time,long displayMs,long readyMs,File original,File graded,boolean single){
            this.id=id;this.lut=lut;this.camera=camera;this.grid=grid;this.time=time;this.displayMs=displayMs;this.readyMs=readyMs;
            this.original=original;this.graded=graded;this.single=single;
        }
    }
    private final File root,dcim;
    PhotoCatalog(File root,File sd)throws IOException {this.root=root;dcim=new File(sd,"DCIM").getCanonicalFile();
        // Sony FuFsys reports an empty directory as an enumeration error.
        LutLibrary.directoryNote(root,"FORMAT.INI","format=1\ncontent=A7R2 Film Camera photo-pair receipts; photographs remain in DCIM\n");
    }
    private File photo(String path)throws IOException {File f=new File(path).getCanonicalFile();if(!f.getPath().startsWith(dcim.getPath()+File.separator)||!f.getName().matches("[A-Z0-9_]{1,8}\\.JPG"))throw new IOException("照片路径无效");return f;}
    synchronized Pair save(File original,File graded,String lut,int grid,long displayMs,long readyMs)throws IOException {
        return save(original,graded,lut,grid,displayMs,readyMs,System.currentTimeMillis());
    }
    synchronized Pair save(File original,File graded,String lut,int grid,long displayMs,long readyMs,long capturedAt)throws IOException {
        original=photo(original.getPath());graded=photo(graded.getPath());if(original.equals(graded))throw new IOException("原片与成片必须独立保存");
        long time=capturedAt;String id="P"+UUID.randomUUID().toString().replace("-","").substring(0,7).toUpperCase(Locale.US);
        Properties p=new Properties();p.setProperty("original",original.getPath());p.setProperty("graded",graded.getPath());p.setProperty("lut",lut);
        p.setProperty("grid",Integer.toString(grid));p.setProperty("time",Long.toString(time));p.setProperty("display",Long.toString(displayMs));p.setProperty("ready",Long.toString(readyMs));
        File temp=new File(root,id+".TMP"),dest=new File(root,id+".JSN");
        if(temp.exists()||dest.exists())throw new IOException("照片记录文件重名，请重试");
        try{LutLibrary.writeProperties(temp,p);if(!temp.renameTo(dest))throw new IOException("照片记录保存失败");}finally{temp.delete();}
        return new Pair(id,lut,grid,time,displayMs,readyMs,original,graded);
    }
    /** Recipe capture stores one camera-native JPEG. There is no original /
     * graded pair and no LUT receipt, but it shares the gallery receipt,
     * thumbnail and delete paths. */
    synchronized Pair saveSingle(File photo,String recipe,String camera,long displayMs,long readyMs)throws IOException {
        return saveSingle(photo,recipe,camera,displayMs,readyMs,System.currentTimeMillis());
    }
    synchronized Pair saveSingle(File photo,String recipe,String camera,long displayMs,long readyMs,long capturedAt)throws IOException {
        File file=photo(photo.getPath());
        String id="P"+UUID.randomUUID().toString().replace("-","").substring(0,7).toUpperCase(Locale.US);
        Properties p=new Properties();p.setProperty("original",file.getPath());p.setProperty("single","true");
        p.setProperty("camera",camera==null?"":camera);p.setProperty("lut",recipe==null?"配方":recipe);
        p.setProperty("grid","0");p.setProperty("time",Long.toString(capturedAt));
        p.setProperty("display",Long.toString(displayMs));p.setProperty("ready",Long.toString(readyMs));
        File temp=new File(root,id+".TMP"),dest=new File(root,id+".JSN");
        if(temp.exists()||dest.exists())throw new IOException("照片记录文件重名，请重试");
        try{LutLibrary.writeProperties(temp,p);if(!temp.renameTo(dest))throw new IOException("照片记录保存失败");}finally{temp.delete();}
        return new Pair(id,recipe==null?"配方":recipe,camera==null?"":camera,0,capturedAt,displayMs,readyMs,file,file,true);
    }
    /** Delete both photographs and the receipt. Receipt is last so a partial
     * failure remains visible and retryable. Paths are re-validated against
     * the stored receipt, not trusted from the UI. */
    synchronized void delete(Pair pair)throws IOException {
        if(pair==null)throw new IOException("missing photo pair");
        String receiptName=pair.id.endsWith(".JSN")?pair.id:pair.id+".JSN";
        if(!receiptName.matches("P[0-9A-F]{7}\\.JSN"))throw new IOException("invalid photo receipt");
        File receipt=new File(root,receiptName).getCanonicalFile();
        if(!root.getCanonicalFile().equals(receipt.getParentFile()))throw new IOException("photo receipt outside catalog");
        File original=photo(pair.original.getPath()),graded=photo(pair.graded.getPath());
        if(pair.single){
            if(receipt.isFile()){
                Properties p=LutLibrary.readProperties(receipt);
                String originalPath=p.getProperty("original");
                if(originalPath==null)throw new IOException("photo receipt is incomplete");
                if(!photo(originalPath).equals(original))
                    throw new IOException("photo receipt does not match selected photo");
            }
            if(original.exists()&&!original.delete())throw new IOException("cannot delete photo "+original.getName());
        }else{
            if(original.equals(graded))throw new IOException("photo pair aliases one file");
            if(receipt.isFile()){
                Properties p=LutLibrary.readProperties(receipt);
                String originalPath=p.getProperty("original"),gradedPath=p.getProperty("graded");
                if(originalPath==null||gradedPath==null)throw new IOException("photo receipt is incomplete");
                File recordedOriginal=photo(originalPath),recordedGraded=photo(gradedPath);
                if(!recordedOriginal.equals(original)||!recordedGraded.equals(graded))
                    throw new IOException("photo receipt does not match selected pair");
            }
            if(original.exists()&&!original.delete())throw new IOException("cannot delete original "+original.getName());
            if(graded.exists()&&!graded.delete())throw new IOException("cannot delete graded "+graded.getName());
        }
        if(receipt.exists()&&!receipt.delete())throw new IOException("cannot delete photo receipt "+receiptName);
        // Contact prints are best-effort: a failed delete must not leave the
        // pair visible. Keep this dependency-free for the host test build.
        String base=receiptName.substring(0,receiptName.length()-4);
        File thumb=new File(new File(root,"THUMB"),base+".JPG");
        if(thumb.isFile())thumb.delete();
    }
    synchronized List<Pair> list()throws IOException {
        File[] files=root.listFiles();if(files==null)throw new IOException("无法读取照片记录");List<Pair> pairs=new ArrayList<Pair>();
        for(File file:files){if(!file.getName().matches("P[0-9A-F]{7}\\.JSN"))continue;
            Properties p=LutLibrary.readProperties(file);
            try{
                boolean single="true".equals(p.getProperty("single"));
                File original=photo(p.getProperty("original"));
                File graded=single?original:photo(p.getProperty("graded"));
                pairs.add(new Pair(file.getName(),p.getProperty("lut","LUT"),p.getProperty("camera",""),
                    Integer.parseInt(p.getProperty("grid","0")),Long.parseLong(p.getProperty("time")),
                    Long.parseLong(p.getProperty("display")),Long.parseLong(p.getProperty("ready")),original,graded,single));
            }
            catch(RuntimeException e){throw new IOException("照片记录损坏："+file.getName());}
        }
        Collections.sort(pairs,new Comparator<Pair>(){public int compare(Pair a,Pair b){return b.graded.getPath().compareTo(a.graded.getPath());}});return pairs;
    }
    /** Transfer-only listing. One receipt left behind by a removed DCIM must
     * not hide every other photograph, so damaged or incomplete receipts are
     * skipped here. The gallery keeps using strict list(). Only the final
     * graded JPEG is offered: the LUT original is never exposed. */
    synchronized List<Pair> listForTransfer()throws IOException {
        File[] files=root.listFiles();if(files==null)throw new IOException("无法读取照片记录");
        List<Pair> pairs=new ArrayList<Pair>();
        for(File file:files){
            if(!file.getName().matches("P[0-9A-F]{7}\\.JSN"))continue;
            try{
                Properties p=LutLibrary.readProperties(file);
                boolean single="true".equals(p.getProperty("single"));
                File original=photo(p.getProperty("original"));
                File graded=single?original:photo(p.getProperty("graded"));
                if(!graded.isFile()||graded.length()<=0)continue;
                long time=Long.parseLong(p.getProperty("time"));
                int grid=Integer.parseInt(p.getProperty("grid","0"));
                long display=Long.parseLong(p.getProperty("display","0"));
                long ready=Long.parseLong(p.getProperty("ready","0"));
                pairs.add(new Pair(file.getName(),p.getProperty("lut","LUT"),p.getProperty("camera",""),
                    grid,time,display,ready,original,graded,single));
            }catch(Exception ignored){/* corrupt or incomplete receipt: skip */ }
        }
        Collections.sort(pairs,new Comparator<Pair>(){public int compare(Pair a,Pair b){
            int byTime=a.time==b.time?0:a.time<b.time?1:-1;
            return byTime!=0?byTime:b.graded.getPath().compareTo(a.graded.getPath());
        }});
        return pairs;
    }
}
