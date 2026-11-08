package org.hongen.sonylutremote;

import org.hongen.sonylut.transfer.MediaEntry;
import org.hongen.sonylut.transfer.MediaManifest;
import org.hongen.sonylut.transfer.TransferProtocol;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/** Tiny HTTP client for the camera's local server. It uses the same pure-Java
 * manifest codec as the camera and streams media straight to a resumable
 * .part file. No path from the phone is ever sent to the camera. */
final class CameraClient {
    static class CameraException extends IOException {
        final int code;
        CameraException(int code,String message){super(message);this.code=code;}
    }
    static final class AuthException extends CameraException {
        AuthException(String message){super(401,message);}
    }
    interface Progress { void progress(long bytes,long total); }

    private static final Charset UTF8=Charset.forName("UTF-8");
    private final String host;
    private final int port;
    private final String token;

    CameraClient(String host,int port,String token){
        String value=host==null?"":host.trim();
        if(value.startsWith("http://"))value=value.substring(7);
        if(value.startsWith("https://"))value=value.substring(8);
        int slash=value.indexOf('/');if(slash>=0)value=value.substring(0,slash);
        int colon=value.indexOf(':');
        if(colon>0&&value.indexOf(':')==value.lastIndexOf(':')){
            try{port=Integer.parseInt(value.substring(colon+1));}catch(NumberFormatException ignored){}
            value=value.substring(0,colon);
        }
        this.host=value;this.port=port;this.token=token==null?"":token.trim();
    }
    String host(){return host;}
    int port(){return port;}
    String model()throws IOException {
        String body=request(TransferProtocol.PING_PATH);
        if(body.indexOf("\"protocol\":1")<0)throw new IOException("相机协议版本不匹配");
        int at=body.indexOf("\"model\":\"");
        if(at<0)return "";
        int start=at+9,end=body.indexOf('"',start);
        return end>start?body.substring(start,end):"";
    }
    List<MediaEntry> photos()throws IOException {
        return MediaManifest.decode(request(TransferProtocol.PHOTOS_PATH)).entries();
    }
    List<MediaEntry> videos()throws IOException {
        return MediaManifest.decode(request(TransferProtocol.VIDEOS_PATH)).entries();
    }
    MediaEntry find(List<MediaEntry> entries,String id){
        if(entries==null||id==null)return null;
        for(MediaEntry entry:entries)if(id.equals(entry.id))return entry;
        return null;
    }

    /** Download or resume one manifest entry. The .part and its .meta survive
     * failures; the meta file binds the partial bytes to one ETag. */
    void download(MediaEntry entry,File part,File meta,Progress progress)throws IOException {
        if(entry==null||!TransferProtocol.safeId(entry.id))throw new IOException("无效媒体编号");
        if(entry.size<0)throw new IOException("无效媒体大小");
        File parent=part.getParentFile();
        if(parent!=null&&!parent.isDirectory()&&!parent.mkdirs())throw new IOException("无法建立下载目录");
        String previousEtag=readMeta(meta);
        if(!entry.etag.equals(previousEtag)){
            part.delete();meta.delete();
            writeMeta(meta,entry.etag,entry.size);
        }
        long existing=part.isFile()?part.length():0;
        if(existing>entry.size){part.delete();existing=0;}
        if(existing==entry.size&&entry.size>0){
            validate(entry,part);
            return;
        }
        HttpURLConnection connection=open(TransferProtocol.MEDIA_PREFIX+entry.id,true);
        try{
            if(existing>0){
                connection.setRequestProperty("Range","bytes="+existing+"-");
                connection.setRequestProperty("If-Range",entry.etag);
            }
            int status=connection.getResponseCode();
            if(status==401||status==403)throw new AuthException("配对码或访问权限不正确");
            if(status==416){
                part.delete();
                throw new CameraException(status,"文件范围失效，请重试");
            }
            if(status!=200&&status!=206)throw new CameraException(status,error(connection));
            String responseEtag=connection.getHeaderField("ETag");
            if(responseEtag!=null&&!responseEtag.equals(entry.etag)){
                part.delete();
                meta.delete();
                throw new IOException("相机文件已变化，请重新同步");
            }
            if(status==206){
                String range=connection.getHeaderField("Content-Range");
                long start=contentRangeStart(range);
                if(start!=existing)throw new IOException("相机续传位置不一致");
            }else{
                existing=0;
            }
            if(status==200){
                RandomAccessFile truncate=new RandomAccessFile(part,"rw");
                try{truncate.setLength(0);}finally{truncate.close();}
            }
            InputStream input=connection.getInputStream();
            FileOutputStream output=new FileOutputStream(part,status==206);
            byte[] buffer=new byte[64*1024];
            long written=existing;
            long last=0;
            try{
                int read;
                while((read=input.read(buffer))>=0){
                    if(read==0)continue;
                    if(written+read>entry.size)throw new IOException("相机文件大于清单大小");
                    output.write(buffer,0,read);
                    written+=read;
                    if(written-last>=512*1024||written==entry.size){
                        last=written;
                        if(progress!=null)progress.progress(written,entry.size);
                    }
                }
                output.getFD().sync();
            }finally{
                try{output.close();}catch(IOException ignored){}
                try{input.close();}catch(IOException ignored){}
            }
            if(part.length()!=entry.size)throw new IOException("下载不完整："+part.length()+"/"+entry.size);
            validate(entry,part);
            if(progress!=null)progress.progress(entry.size,entry.size);
        }finally{
            connection.disconnect();
        }
    }
    void discard(File part,File meta){part.delete();meta.delete();}

    private String request(String path)throws IOException {
        HttpURLConnection connection=open(path,false);
        try{
            int status=connection.getResponseCode();
            if(status==401)throw new AuthException("配对码不正确");
            if(status!=200)throw new CameraException(status,error(connection));
            InputStream input=connection.getInputStream();
            try{return readText(input,8*1024*1024);}finally{input.close();}
        }finally{connection.disconnect();}
    }
    private HttpURLConnection open(String path,boolean media)throws IOException {
        if(host.length()==0)throw new IOException("未设置相机地址");
        if(port<1||port>65535)throw new IOException("端口无效");
        if(token.length()==0)throw new IOException("未设置配对码");
        URL url=new URL("http",host,port,path);
        HttpURLConnection connection=(HttpURLConnection)url.openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(media?30000:10000);
        connection.setUseCaches(false);
        connection.setRequestProperty("X-SonyLut-Token",token);
        connection.setRequestProperty("Accept",media?"*/*":"text/plain");
        connection.setRequestProperty("User-Agent","SonyLutRemote/1");
        return connection;
    }
    private static String readText(InputStream input,long limit)throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        byte[] buffer=new byte[4096];int read,total=0;
        while((read=input.read(buffer))>=0){
            total+=read;if(total>limit)throw new IOException("响应过大");
            out.write(buffer,0,read);
        }
        return out.toString(UTF8.name());
    }
    private static String error(HttpURLConnection connection){
        try{
            InputStream input=connection.getErrorStream();
            if(input==null)return "HTTP "+connection.getResponseCode();
            try{return "HTTP "+connection.getResponseCode()+": "+readText(input,1024).trim();}
            finally{input.close();}
        }catch(Exception ignored){return "HTTP 请求失败";}
    }
    private static long contentRangeStart(String value)throws IOException {
        if(value==null||!value.startsWith("bytes "))throw new IOException("缺少续传响应头");
        int dash=value.indexOf('-',6),slash=value.indexOf('/',6);
        if(dash<0||slash<dash)throw new IOException("续传响应头无效");
        try{return Long.parseLong(value.substring(6,dash).trim());}
        catch(NumberFormatException error){throw new IOException("续传位置无效",error);}
    }
    private static String readMeta(File meta){
        if(!meta.isFile())return "";
        Properties p=new Properties();
        try{
            FileInputStream input=new FileInputStream(meta);
            try{p.load(input);}finally{input.close();}
            return p.getProperty("etag","");
        }catch(Exception ignored){return "";}
    }
    private static void writeMeta(File meta,String etag,long size){
        Properties p=new Properties();
        p.setProperty("format","1");p.setProperty("etag",etag);p.setProperty("size",Long.toString(size));
        try{
            FileOutputStream out=new FileOutputStream(meta);
            try{p.store(out,"SonyLutRemote partial");out.getFD().sync();}finally{out.close();}
        }catch(IOException ignored){/* the part remains usable within this session */}
    }
    private static void validate(MediaEntry entry,File part)throws IOException {
        long size=part.length();
        if(size!=entry.size)throw new IOException("文件大小不符");
        if(entry.size==0)throw new IOException("空文件");
        String name=entry.name==null?"":entry.name.toLowerCase(Locale.US);
        if("photo".equals(entry.kind)){
            RandomAccessFile input=new RandomAccessFile(part,"r");
            try{
                input.seek(size-2);
                int a=input.read(),b=input.read();
                if(a!=0xff||b!=0xd9)throw new IOException("JPEG 结束标记缺失");
            }finally{input.close();}
        }else if(name.endsWith(".mp4")){
            byte[] head=readHead(part,12);
            if(head.length<8||head[4]!='f'||head[5]!='t'||head[6]!='y'||head[7]!='p')
                throw new IOException("MP4 文件头无效");
        }else if(name.endsWith(".mts")||name.endsWith(".m2ts")){
            byte[] head=readHead(part,8);
            if(head.length<5||((head[0]&255)!=0x47&&(head[4]&255)!=0x47))
                throw new IOException("MTS 文件头无效");
        }
    }
    private static byte[] readHead(File file,int count)throws IOException {
        byte[] data=new byte[count];
        FileInputStream input=new FileInputStream(file);
        try{
            int offset=0;
            while(offset<count){int read=input.read(data,offset,count-offset);if(read<0)break;offset+=read;}
            if(offset==count)return data;
            byte[] smaller=new byte[offset];System.arraycopy(data,0,smaller,0,offset);return smaller;
        }finally{input.close();}
    }
}
