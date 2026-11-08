package org.hongen.sonylutremote;

import org.hongen.sonylut.transfer.MediaEntry;
import org.hongen.sonylut.transfer.TransferProtocol;
import org.hongen.sonylut.transfer.TransferServer;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

/** Host-only checks for the phone ledger and resumable client. */
public final class PhoneSyncTest {
    private static final String TOKEN="TESTTOKEN123";

    public static void main(String[] args)throws Exception{
        ledger();
        client();
        rangeResume();
        System.out.println("PASS phone ledger, client, ETag reset, Range resume");
    }

    private static void ledger()throws Exception{
        File dir=tempDir("sonylut-ledger");
        File file=new File(dir,"ledger.properties");
        MediaEntry a=entry("photo-A","photo","lut","A.JPG","e1",100);
        MediaEntry b=entry("photo-B","photo","recipe","B.JPG","e1",200);
        MediaEntry c=entry("photo-C","photo","lut","C.JPG","e1",300);
        LedgerStore store=new LedgerStore(file);
        store.baselineIfNeeded(Arrays.asList(a,b),false);
        check(LedgerStore.SKIPPED.equals(store.stateOf(a)),"baseline skips existing A");
        check(LedgerStore.SKIPPED.equals(store.stateOf(b)),"baseline skips existing B");
        check(!store.shouldAutoDownload(a),"skipped photo is not auto downloaded");
        check(store.shouldAutoDownload(c),"new photo is auto downloaded");
        store.markDone(c);
        check(store.isDone(c),"done photo");
        MediaEntry changed=entry("photo-C","photo","lut","C.JPG","e2",300);
        check(!store.isDone(changed),"changed ETag is a new revision");
        check(store.stateOf(changed).length()==0,"changed ETag has no record");
        store.markFailed(a,"network");
        check(LedgerStore.FAILED.equals(store.stateOf(a)),"failed state");
        check("network".equals(store.detailOf(a)),"failure detail");
        LedgerStore reloaded=new LedgerStore(file);
        check(LedgerStore.FAILED.equals(reloaded.stateOf(a)),"ledger persists");
        reloaded.retryFailed();
        check(reloaded.shouldAutoDownload(a),"retry clears failure");
        reloaded.includeSkipped();
        check(reloaded.shouldAutoDownload(b),"include clears skipped");
        check(reloaded.count(Arrays.asList(a,b),"")==2,"count new entries");
        System.out.println("  ledger: baseline/skip/done/etag/changed/failed/retry/persist OK");
    }

    private static void client()throws Exception{
        File dir=tempDir("sonylut-client");
        File photo=new File(dir,"GRADED.JPG"),video=new File(dir,"CLIP.MP4");
        byte[] photoBytes=new byte[5000];
        for(int i=0;i<photoBytes.length;i++)photoBytes[i]=(byte)('A'+(i%26));
        photoBytes[photoBytes.length-2]=(byte)0xff;
        photoBytes[photoBytes.length-1]=(byte)0xd9;
        write(photo,photoBytes);
        byte[] videoBytes=new byte[321];
        for(int i=0;i<videoBytes.length;i++)videoBytes[i]=(byte)(i&255);
        videoBytes[4]='f';videoBytes[5]='t';videoBytes[6]='y';videoBytes[7]='p';
        write(video,videoBytes);
        Source source=new Source(photo,video);
        TransferServer server=new TransferServer(TOKEN,source,"127.0.0.1","127.",0,null);
        int port=server.start();
        if(port<=0)throw new AssertionError("server port");
        try{
            CameraClient client=new CameraClient("127.0.0.1",port,TOKEN);
            check("ILCE-7RM2".equals(client.model()),"ping model");
            List<MediaEntry> photos=client.photos();
            check(photos.size()==1&&"photo-P0000001".equals(photos.get(0).id),"photo manifest");
            check(client.videos().size()==1,"video manifest");
            File part=new File(dir,"full.part"),meta=new File(dir,"full.meta");
            client.download(photos.get(0),part,meta,null);
            check(Arrays.equals(photoBytes,read(part)),"download bytes");
            check(meta.isFile()&&part.length()==photoBytes.length,"download leaves meta");
            boolean auth=false;
            try{new CameraClient("127.0.0.1",port,"WRONGTOKEN").model();}
            catch(CameraClient.AuthException expected){auth=true;}
            check(auth,"wrong token rejected");
            MediaEntry entry=photos.get(0);
            File part2=new File(dir,"stale.part"),meta2=new File(dir,"stale.meta");
            write(part2,Arrays.copyOf(photoBytes,100));
            writeMeta(meta2,"\"stale\"");
            client.download(entry,part2,meta2,null);
            check(Arrays.equals(photoBytes,read(part2)),"stale ETag restarts from zero");
            System.out.println("  client: ping/manifests/full/auth/etag reset OK");
        }finally{
            server.stop();
        }
    }

    private static void rangeResume()throws Exception{
        File dir=tempDir("sonylut-range");
        byte[] data=new byte[5000];
        for(int i=0;i<data.length;i++)data[i]=(byte)('a'+(i%26));
        data[data.length-2]=(byte)0xff;
        data[data.length-1]=(byte)0xd9;
        File photo=new File(dir,"P0000001.JPG");
        write(photo,data);
        String etag=TransferProtocol.etag(photo.lastModified(),data.length);
        MediaEntry entry=new MediaEntry("photo-P0000001","photo","lut","P0000001.JPG","TEST",
            1L,data.length,etag,"image/jpeg");
        File part=new File(dir,"resume.part"),meta=new File(dir,"resume.meta");
        write(part,Arrays.copyOf(data,1000));
        writeMeta(meta,etag);

        RangeServer server=new RangeServer(data,etag,1000);
        Thread thread=new Thread(server,"range-test-server");
        thread.setDaemon(true);
        thread.start();
        CameraClient client=new CameraClient("127.0.0.1",server.port(),TOKEN);
        IOException failure=null;
        try{client.download(entry,part,meta,null);}
        catch(IOException error){failure=error;}
        thread.join(5000);
        Throwable serverFailure=server.failure.get();
        if(serverFailure!=null)throw new AssertionError("range server",serverFailure);
        if(failure!=null)throw new AssertionError("resume download",failure);
        check(thread.isAlive()==false,"range server finished");
        check(Arrays.equals(data,read(part)),"resumed file equals original");
        System.out.println("  resume: Range + If-Range, 206, final bytes OK");
    }

    private static final class Source implements TransferServer.Source {
        private final File photo,video;
        private final List<MediaEntry> photos,videos;
        Source(File photo,File video)throws Exception{
            this.photo=photo;this.video=video;
            photos=Arrays.asList(entry("photo-P0000001","photo","lut",photo.getName(),"TEST",
                1234L,photo.length(),TransferProtocol.etag(photo.lastModified(),photo.length()),"image/jpeg"));
            videos=Arrays.asList(entry("video-0123456789abcdef","video","video",video.getName(),"",
                5678L,video.length(),TransferProtocol.etag(video.lastModified(),video.length()),"video/mp4"));
        }
        public List<MediaEntry> photos(){return photos;}
        public List<MediaEntry> videos(){return videos;}
        public File resolve(String id){
            if("photo-P0000001".equals(id))return photo;
            if("video-0123456789abcdef".equals(id))return video;
            return null;
        }
    }

    private static final class RangeServer implements Runnable {
        private final byte[] data;
        private final String etag;
        private final int start;
        private final ServerSocket socket;
        final AtomicReference<Throwable> failure=new AtomicReference<Throwable>();
        RangeServer(byte[] data,String etag,int start)throws Exception{
            this.data=data;this.etag=etag;this.start=start;
            socket=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"));
        }
        int port(){return socket.getLocalPort();}
        public void run(){
            Socket client=null;
            try{
                client=socket.accept();
                client.setSoTimeout(5000);
                BufferedReader reader=new BufferedReader(new InputStreamReader(
                    client.getInputStream(),"UTF-8"));
                String request=reader.readLine();
                if(request==null||!request.startsWith("GET /v1/media/photo-P0000001 "))
                    throw new AssertionError("request line: "+request);
                Map<String,String> headers=new HashMap<String,String>();
                String line;
                while((line=reader.readLine())!=null&&line.length()>0){
                    int colon=line.indexOf(':');
                    if(colon>0)headers.put(line.substring(0,colon).trim().toLowerCase(Locale.US),
                        line.substring(colon+1).trim());
                }
                check(("bytes="+start+"-").equals(headers.get("range")),"partial request sends Range");
                check(etag.equals(headers.get("if-range")),"partial request sends If-Range");
                check(TOKEN.equals(headers.get("x-sonylut-token")),"partial request keeps token");
                int length=data.length-start;
                String head="HTTP/1.1 206 Partial Content\r\n"
                    +"Content-Length: "+length+"\r\n"
                    +"Content-Type: image/jpeg\r\n"
                    +"Content-Range: bytes "+start+"-"+(data.length-1)+"/"+data.length+"\r\n"
                    +"ETag: "+etag+"\r\nConnection: close\r\n\r\n";
                OutputStream out=client.getOutputStream();
                out.write(head.getBytes("UTF-8"));
                out.write(data,start,length);
                out.flush();
            }catch(Throwable error){
                failure.set(error);
            }finally{
                if(client!=null)try{client.close();}catch(IOException ignored){}
                try{socket.close();}catch(IOException ignored){}
            }
        }
    }

    private static MediaEntry entry(String id,String kind,String mode,String name,String etag,long time){
        return entry(id,kind,mode,name,"TEST",time,100L,etag,"image/jpeg");
    }
    private static MediaEntry entry(String id,String kind,String mode,String name,String lut,
                                    long time,long size,String etag,String mime){
        return new MediaEntry(id,kind,mode,name,lut,time,size,etag,mime);
    }
    private static File tempDir(String prefix)throws IOException{
        File dir=File.createTempFile(prefix,"-dir");
        if(!dir.delete()||!dir.mkdir())throw new IOException("temp dir");
        return dir;
    }
    private static void write(File file,byte[] data)throws IOException{
        FileOutputStream out=new FileOutputStream(file);
        try{out.write(data);}finally{out.close();}
    }
    private static byte[] read(File file)throws IOException{
        FileInputStream input=new FileInputStream(file);
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try{
            byte[] buffer=new byte[4096];int count;
            while((count=input.read(buffer))>=0)if(count>0)out.write(buffer,0,count);
        }finally{input.close();}
        return out.toByteArray();
    }
    private static void writeMeta(File file,String etag)throws IOException{
        Properties p=new Properties();
        p.setProperty("format","1");
        p.setProperty("etag",etag);
        p.setProperty("size","5000");
        FileOutputStream out=new FileOutputStream(file);
        try{p.store(out,"test");out.getFD().sync();}finally{out.close();}
    }
    private static void check(boolean condition,String message){
        if(!condition)throw new AssertionError(message);
    }

    private PhoneSyncTest(){}
}
