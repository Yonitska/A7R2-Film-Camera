package org.hongen.sonylutprobe;

import android.os.Environment;

import org.hongen.sonylut.transfer.MediaEntry;
import org.hongen.sonylut.transfer.TransferProtocol;
import org.hongen.sonylut.transfer.TransferServer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The only view of user content the HTTP server gets.
 *
 * Photo ids resolve through the durable capture receipts, and for a LUT pair
 * only the graded JPEG is ever indexed here. Videos resolve through the
 * explicit tick-list. Neither endpoint accepts a path. */
final class PhotoTransferSource implements TransferServer.Source {
    private final PhotoCatalog catalog;
    private final VideoSelectionStore videos;
    private final File dcim;
    private final Map<String,File> photoFiles=new HashMap<String,File>();
    private long photoCacheAt;
    private static final long CACHE_MS=2000L;

    PhotoTransferSource(PhotoCatalog catalog,VideoSelectionStore videos)throws IOException {
        this.catalog=catalog;
        this.videos=videos;
        this.dcim=new File(Environment.getExternalStorageDirectory(),"DCIM").getCanonicalFile();
    }

    public List<MediaEntry> photos()throws Exception {
        Map<String,File> files=photoMap(false);
        List<MediaEntry> result=new ArrayList<MediaEntry>();
        for(PhotoCatalog.Pair pair:catalog.listForTransfer()){
            if(result.size()>=TransferProtocol.MAX_ENTRIES)break;
            String id=idFor(pair);
            File graded=files.get(id);
            if(graded==null||!safePhoto(graded))continue;
            long size=graded.length();
            if(size<=0)continue;
            result.add(new MediaEntry(id,"photo",pair.single?"recipe":"lut",
                graded.getName(),pair.lut,pair.time,size,
                TransferProtocol.etag(graded.lastModified(),size),"image/jpeg"));
        }
        return result;
    }
    public List<MediaEntry> videos()throws Exception {
        List<MediaEntry> result=new ArrayList<MediaEntry>();
        for(VideoSelectionStore.Selection selection:videos.selected()){
            if(result.size()>=TransferProtocol.MAX_ENTRIES)break;
            File file=videos.resolve(selection.id,true);
            if(file==null)continue;
            long size=file.length();
            result.add(new MediaEntry(selection.id,"video","video",selection.name,"",selection.time,size,
                TransferProtocol.etag(file.lastModified(),size),TransferProtocol.mediaMime(file.getName())));
        }
        return result;
    }
    public File resolve(String id)throws Exception {
        if(id==null)return null;
        if(id.startsWith("photo-")){
            File file=photoMap(false).get(id);
            if(file==null){file=photoMap(true).get(id);}
            return file!=null&&safePhoto(file)?file:null;
        }
        if(id.startsWith("video-"))return videos.resolve(id,true);
        return null;
    }

    private synchronized Map<String,File> photoMap(boolean refresh)throws IOException {
        long now=System.currentTimeMillis();
        if(!refresh&&!photoFiles.isEmpty()&&now-photoCacheAt<CACHE_MS)return new HashMap<String,File>(photoFiles);
        if(refresh&&!photoFiles.isEmpty()&&now-photoCacheAt<200L)return new HashMap<String,File>(photoFiles);
        Map<String,File> fresh=new HashMap<String,File>();
        for(PhotoCatalog.Pair pair:catalog.listForTransfer()){
            if(fresh.size()>=TransferProtocol.MAX_ENTRIES)break;
            if(pair.graded.isFile()&&safePhoto(pair.graded))fresh.put(idFor(pair),pair.graded);
        }
        photoFiles.clear();photoFiles.putAll(fresh);photoCacheAt=now;
        return new HashMap<String,File>(photoFiles);
    }
    private static String idFor(PhotoCatalog.Pair pair){
        String receipt=pair.id.endsWith(".JSN")?pair.id.substring(0,pair.id.length()-4):pair.id;
        return TransferProtocol.photoId(receipt);
    }
    private boolean safePhoto(File file){
        try{
            File canonical=file.getCanonicalFile();
            if(!canonical.isFile()||!canonical.getName().matches("[A-Z0-9_]{1,8}\\.JPG"))return false;
            return canonical.getPath().startsWith(dcim.getPath()+File.separator);
        }catch(IOException e){return false;}
    }
}
