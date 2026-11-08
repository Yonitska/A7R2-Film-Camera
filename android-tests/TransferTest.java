package org.hongen.sonylutprobe;

import org.hongen.sonylut.transfer.HttpDate;
import org.hongen.sonylut.transfer.ManifestCodec;
import org.hongen.sonylut.transfer.MediaEntry;
import org.hongen.sonylut.transfer.RangeSpec;
import org.hongen.sonylut.transfer.TransferProtocol;
import org.hongen.sonylut.transfer.TransferServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Host-only loopback tests for the camera HTTP server and wire types. */
public final class TransferTest {
    private static final String TOKEN="TESTTOKEN123";
    private static final class Response {
        int status;
        final Map<String,String> headers=new HashMap<String,String>();
        byte[] body=new byte[0];
        String header(String name){return headers.get(name.toLowerCase(Locale.US));}
    }
    private static final class Source implements TransferServer.Source {
        final File photo,video;
        final List<MediaEntry> photos,videos;
        Source(File photo,File video)throws Exception{
            this.photo=photo;this.video=video;
            photos=Arrays.asList(entry("photo-P0000001","photo","lut",photo,"KODAK",1234));
            videos=Arrays.asList(entry("video-0123456789abcdef","video","video",video,"",5678));
        }
        public List<MediaEntry> photos(){return photos;}
        public List<MediaEntry> videos(){return videos;}
        public File resolve(String id){
            if("photo-P0000001".equals(id))return photo;
            if("video-0123456789abcdef".equals(id))return video;
            return null;
        }
    }
    private static MediaEntry entry(String id,String kind,String mode,File file,String lut,long time)throws Exception{
        return new MediaEntry(id,kind,mode,file.getName(),lut,time,file.length(),
            TransferProtocol.etag(file.lastModified(),file.length()),TransferProtocol.mediaMime(file.getName()));
    }
    public static void main(String[] args)throws Exception{
        protocolTypes();
        photoReceipts();
        File dir=File.createTempFile("sonylut-transfer","dir");
        if(!dir.delete()||!dir.mkdir())throw new AssertionError("temp dir");
        File photo=new File(dir,"GRADED.JPG"),video=new File(dir,"CLIP.MP4");
        final byte[] photoBytes=new byte[5000];
        for(int i=0;i<photoBytes.length;i++)photoBytes[i]=(byte)('A'+(i%26));
        write(photo,photoBytes);
        byte[] videoBytes=new byte[321];
        for(int i=0;i<videoBytes.length;i++)videoBytes[i]=(byte)(i&255);
        write(video,videoBytes);
        Source source=new Source(photo,video);
        TransferServer server=new TransferServer(TOKEN,source,"127.0.0.1","127.",0,null);
        int port=server.start();
        if(port<=0)throw new AssertionError("no port");
        try{
            expect(request(port,"GET",TransferProtocol.PING_PATH,null),401,"missing token");
            Response ping=request(port,"GET",TransferProtocol.PING_PATH,auth());
            expect(ping,200,"ping");
            if(!new String(ping.body,"UTF-8").contains("\"protocol\":1"))throw new AssertionError("ping body");
            Response photos=request(port,"GET",TransferProtocol.PHOTOS_PATH,auth());
            expect(photos,200,"photos");
            List<MediaEntry> list=ManifestCodec.decode(new String(photos.body,"UTF-8"));
            if(list.size()!=1||!"photo-P0000001".equals(list.get(0).id)
                    ||!"lut".equals(list.get(0).mode)||list.get(0).size!=photoBytes.length)
                throw new AssertionError("photo manifest");
            Response videos=request(port,"GET",TransferProtocol.VIDEOS_PATH,auth());
            expect(videos,200,"videos");
            if(ManifestCodec.decode(new String(videos.body,"UTF-8")).size()!=1)
                throw new AssertionError("video manifest");

            Response head=request(port,"HEAD",mediaPath("photo-P0000001"),auth());
            expect(head,200,"head");
            if(head.body.length!=0||!"bytes".equals(head.header("accept-ranges"))
                    ||head.header("etag")==null||head.header("last-modified")==null
                    ||Integer.parseInt(head.header("content-length"))!=photoBytes.length)
                throw new AssertionError("head headers");
            Response full=request(port,"GET",mediaPath("photo-P0000001"),auth());
            expect(full,200,"full");
            if(!Arrays.equals(photoBytes,full.body))throw new AssertionError("full body");
            Response range=request(port,"GET",mediaPath("photo-P0000001"),authed("range","bytes=10-19"));
            expect(range,206,"range");
            if(range.body.length!=10||range.body[0]!=photoBytes[10]
                    ||!RangeSpec.contentRange(10,19,photoBytes.length).equals(range.header("content-range")))
                throw new AssertionError("range slice");
            Response suffix=request(port,"GET",mediaPath("photo-P0000001"),authed("range","bytes=-5"));
            expect(suffix,206,"suffix");
            if(suffix.body.length!=5||suffix.body[4]!=photoBytes[photoBytes.length-1])
                throw new AssertionError("suffix slice");
            Response open=request(port,"GET",mediaPath("photo-P0000001"),authed("range","bytes=4990-"));
            expect(open,206,"open range");
            if(open.body.length!=10)throw new AssertionError("open slice");
            Response multi=request(port,"GET",mediaPath("photo-P0000001"),authed("range","bytes=0-1,3-4"));
            expect(multi,416,"multiple ranges");
            if(!RangeSpec.unsatisfiedRange(photoBytes.length).equals(multi.header("content-range")))
                throw new AssertionError("416 content-range");
            Response past=request(port,"GET",mediaPath("photo-P0000001"),authed("range","bytes=999999-"));
            expect(past,416,"unsatisfiable");
            Response known=request(port,"GET",mediaPath("photo-P0000001"),authed("if-range",
                TransferProtocol.etag(photo.lastModified(),photo.length()),"range","bytes=0-1"));
            expect(known,206,"matching if-range");
            Response stale=request(port,"GET",mediaPath("photo-P0000001"),authed("if-range",
                HttpDate.format(photo.lastModified()-60000),"range","bytes=0-1"));
            expect(stale,200,"stale if-range");
            if(stale.body.length!=photoBytes.length)throw new AssertionError("stale if-range body");
            Response missing=request(port,"GET",mediaPath("photo-NOPE"),auth());
            expect(missing,404,"unknown id");
            Response traversal=request(port,"GET","/v1/media/../PAIR.JSN",auth());
            expect(traversal,400,"path traversal");
            Response longHeader=request(port,"GET",TransferProtocol.PING_PATH,
                authed("x-long",repeat('a',3000)));
            expect(longHeader,400,"overlong header");
            Response videoFull=request(port,"GET",mediaPath("video-0123456789abcdef"),auth());
            expect(videoFull,200,"video full");
            if(!Arrays.equals(videoBytes,videoFull.body))throw new AssertionError("video body");

            final int p=port;
            List<Thread> threads=new ArrayList<Thread>();
            final List<String> failures=new ArrayList<String>();
            for(int i=0;i<3;i++)threads.add(new Thread(){public void run(){
                try{
                    Response concurrent=request(p,"GET",mediaPath("photo-P0000001"),auth());
                    if(concurrent.status!=200||!Arrays.equals(photoBytes,concurrent.body))
                        synchronized(failures){failures.add("concurrent body");}
                }catch(Exception error){synchronized(failures){failures.add(error.toString());}}
            }});
            for(Thread thread:threads)thread.start();
            for(Thread thread:threads)thread.join(5000);
            if(!failures.isEmpty())throw new AssertionError(failures.toString());
        }finally{server.stop();}
        try{
            request(port,"GET",TransferProtocol.PING_PATH,auth());
            throw new AssertionError("server still listening after stop");
        }catch(IOException expected){}

        TransferServer forbidden=new TransferServer(TOKEN,source,"127.0.0.1","10.",0,null);
        int forbiddenPort=forbidden.start();
        try{expect(request(forbiddenPort,"GET",TransferProtocol.PING_PATH,auth()),403,"source prefix guard");}
        finally{forbidden.stop();}
        System.out.println("PASS transfer receipts, protocol, manifest, 200/206/416/HEAD/401/403/404, ranges, If-Range, concurrency, stop");
    }


    private static void photoReceipts()throws Exception{
        File sd=File.createTempFile("sonylut-sd","dir");
        if(!sd.delete()||!sd.mkdir())throw new AssertionError("sd temp");
        File dcim=new File(sd,"DCIM/100MSDCF");if(!dcim.mkdirs())throw new AssertionError("dcim");
        File original=new File(dcim,"ORIG.JPG"),graded=new File(dcim,"GRADED.JPG"),single=new File(dcim,"RECIPE.JPG");
        write(original,new byte[]{1,2,3});write(graded,new byte[]{4,5,6});write(single,new byte[]{7,8,9});
        File receipts=File.createTempFile("sonylut-receipts","dir");
        if(!receipts.delete()||!receipts.mkdir())throw new AssertionError("receipts temp");
        PhotoCatalog catalog=new PhotoCatalog(receipts,sd);
        PhotoCatalog.Pair lut=catalog.save(original,graded,"KODAK",17,10,20,1234567890L);
        PhotoCatalog.Pair recipe=catalog.saveSingle(single,"Pro Neg Std","X-T3",10,20,1234567891L);
        if(lut.single||!lut.graded.equals(graded.getCanonicalFile())||!lut.original.equals(original.getCanonicalFile()))
            throw new AssertionError("LUT receipt paths");
        if(!recipe.single||!recipe.graded.equals(single.getCanonicalFile())||!recipe.original.equals(recipe.graded))
            throw new AssertionError("recipe receipt paths");
        File damaged=new File(receipts,"PAAAAAAA.JSN");
        write(damaged,"original=/not/on/card.JPG\n".getBytes("UTF-8"));
        try{catalog.list();throw new AssertionError("strict gallery accepted damaged receipt");}
        catch(IOException expected){}
        List<PhotoCatalog.Pair> found=catalog.listForTransfer();
        if(found.size()!=2)throw new AssertionError("transfer list size "+found.size());
        boolean sawLut=false,sawRecipe=false;
        for(PhotoCatalog.Pair pair:found){
            if(pair.single){
                sawRecipe=true;
                if(!"Pro Neg Std".equals(pair.lut)||!pair.graded.getName().equals("RECIPE.JPG"))
                    throw new AssertionError("recipe transfer shape");
            }else{
                sawLut=true;
                if(!"GRADED.JPG".equals(pair.graded.getName())||"ORIG.JPG".equals(pair.graded.getName()))
                    throw new AssertionError("LUT transfer must expose graded only");
            }
        }
        if(!sawLut||!sawRecipe)throw new AssertionError("receipt kinds");
    }

    private static void protocolTypes()throws Exception{
        MediaEntry odd=new MediaEntry("photo-P0000001","photo","recipe","a\tb\nc\\d","LUT\tX",5,7,
            "\"etag\"","image/jpeg");
        List<MediaEntry> decoded=ManifestCodec.decode(ManifestCodec.encode(Arrays.asList(odd)));
        if(decoded.size()!=1||!odd.name.equals(decoded.get(0).name)||!odd.lut.equals(decoded.get(0).lut))
            throw new AssertionError("manifest escaping");
        rejectManifest("SONYLUT-MANIFEST 1\nphoto-P1\tphoto\tvideo\tx\t\t0\t1\t\"e\"\timage/jpeg\n");
        rejectManifest("bad header\n");
        rejectManifest("SONYLUT-MANIFEST 1\n../x\tphoto\tlut\tx\t\t0\t1\t\"e\"\timage/jpeg\n");
        if(RangeSpec.parse(null,10)!=null)throw new AssertionError("no range");
        if(RangeSpec.parse("items=0-1",10)!=null)throw new AssertionError("foreign unit");
        RangeSpec spec=RangeSpec.parse("bytes=-20",10);
        if(spec.start!=0||spec.end!=9||spec.length()!=10)throw new AssertionError("suffix clamp");
        spec=RangeSpec.parse("bytes=2-999",10);
        if(spec.start!=2||spec.end!=9)throw new AssertionError("end clamp");
        for(String bad:new String[]{"bytes=","bytes=1-0","bytes=10-","bytes=-0","bytes=0-1,2-3","bytes=a-b","bytes=0--1"})
            try{RangeSpec.parse(bad,10);throw new AssertionError("accepted "+bad);}
            catch(IllegalArgumentException expected){}
        String date=HttpDate.format(1234567890000L);
        if(!"Fri, 13 Feb 2009 23:31:30 GMT".equals(date)||HttpDate.parse(date)!=1234567890000L)
            throw new AssertionError("http date: "+date);
        if(HttpDate.parse("not a date")!=-1)throw new AssertionError("invalid date accepted");
    }
    private static void rejectManifest(String text)throws Exception{
        try{ManifestCodec.decode(text);throw new AssertionError("manifest accepted");}
        catch(IOException expected){}
    }
    private static Map<String,String> auth(){return headers(TransferProtocol.TOKEN_HEADER,TOKEN);}
    private static Map<String,String> authed(String... pairs){
        Map<String,String> result=auth();
        for(int i=0;i+1<pairs.length;i+=2)result.put(pairs[i],pairs[i+1]);
        return result;
    }
    private static Map<String,String> headers(String... pairs){
        Map<String,String> result=new HashMap<String,String>();
        for(int i=0;i+1<pairs.length;i+=2)result.put(pairs[i],pairs[i+1]);
        return result;
    }
    private static String mediaPath(String id){return TransferProtocol.MEDIA_PREFIX+id;}
    private static String repeat(char c,int n){StringBuilder out=new StringBuilder(n);for(int i=0;i<n;i++)out.append(c);return out.toString();}
    private static void write(File file,byte[] data)throws Exception{
        FileOutputStream out=new FileOutputStream(file);try{out.write(data);}finally{out.close();}
    }
    private static Response request(int port,String method,String path,Map<String,String> extra)throws IOException{
        Socket socket=new Socket();
        socket.connect(new InetSocketAddress("127.0.0.1",port),3000);
        socket.setSoTimeout(5000);
        try{
            StringBuilder request=new StringBuilder();
            request.append(method).append(' ').append(path).append(" HTTP/1.1\r\n")
                .append("Host: 127.0.0.1\r\n");
            if(extra!=null)for(Map.Entry<String,String> entry:extra.entrySet())
                request.append(entry.getKey()).append(": ").append(entry.getValue()).append("\r\n");
            request.append("\r\n");
            OutputStream out=socket.getOutputStream();out.write(request.toString().getBytes("UTF-8"));out.flush();
            InputStream in=socket.getInputStream();
            String status=readLine(in);
            if(status==null)throw new IOException("no response");
            String[] parts=status.split(" ",3);
            if(parts.length<2)throw new IOException("bad status "+status);
            Response response=new Response();response.status=Integer.parseInt(parts[1]);
            String line;
            while((line=readLine(in))!=null&&line.length()>0){
                int colon=line.indexOf(':');
                if(colon>0)response.headers.put(line.substring(0,colon).trim().toLowerCase(Locale.US),line.substring(colon+1).trim());
            }
            String length=response.header("content-length");
            if(!"HEAD".equals(method)&&length!=null){
                int n=Integer.parseInt(length);byte[] body=new byte[n];int offset=0;
                while(offset<n){int read=in.read(body,offset,n-offset);if(read<0)throw new IOException("short body");offset+=read;}
                response.body=body;
            }
            return response;
        }finally{socket.close();}
    }
    private static String readLine(InputStream in)throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        while(true){
            int value=in.read();
            if(value<0)return out.size()==0?null:out.toString("UTF-8");
            if(value=='\n'){
                byte[] bytes=out.toByteArray();int n=bytes.length;
                if(n>0&&bytes[n-1]=='\r')n--;
                return new String(bytes,0,n,"UTF-8");
            }
            out.write(value);
        }
    }
    private static void expect(Response response,int status,String what){
        if(response.status!=status)throw new AssertionError(what+": expected "+status+", got "+response.status);
    }
}
