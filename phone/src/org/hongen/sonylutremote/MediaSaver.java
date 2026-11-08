package org.hongen.sonylutremote;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.os.Build;
import android.os.Environment;

import org.hongen.sonylut.transfer.MediaEntry;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/** Saves a verified .part file into the public gallery/movies. On Android 10+
 * MediaStore owns the destination; older releases use a public app folder and
 * a media scan. The source .part is removed by the sync service only after
 * this method reports success. */
final class MediaSaver {
    static final class Saved {
        final String name,where;
        Saved(String name,String where){this.name=name;this.where=where;}
    }

    static Saved save(Context context,MediaEntry entry,File source)throws IOException {
        if(entry==null||source==null||!source.isFile())throw new IOException("待保存文件不存在");
        if(source.length()!=entry.size)throw new IOException("待保存文件大小不符");
        String name=displayName(entry);
        if(Build.VERSION.SDK_INT>=29)return MediaSaverApi29.save(context,entry,source,name);
        return legacy(context,entry,source,name);
    }

    private static Saved legacy(Context context,MediaEntry entry,File source,String name)throws IOException {
        File root=Environment.getExternalStoragePublicDirectory("photo".equals(entry.kind)
            ?Environment.DIRECTORY_PICTURES:Environment.DIRECTORY_MOVIES);
        File dir=new File(root,"SonyLUT");
        if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("无法创建相册目录");
        File target=unique(dir,name);
        File temp=new File(dir,target.getName()+".tmp");
        copy(source,temp);
        if(target.exists()&&!target.delete()){temp.delete();throw new IOException("无法替换同名文件");}
        if(!temp.renameTo(target)){temp.delete();throw new IOException("无法保存到相册");}
        MediaScannerConnection.scanFile(context,new String[]{target.getAbsolutePath()},new String[]{entry.mime},null);
        return new Saved(target.getName(),target.getParent());
    }
    private static void copy(File source,File dest)throws IOException {
        InputStream input=new FileInputStream(source);
        FileOutputStream output=new FileOutputStream(dest);
        try{
            byte[] buffer=new byte[128*1024];int read;
            while((read=input.read(buffer))>=0)if(read>0)output.write(buffer,0,read);
            output.getFD().sync();
        }finally{
            try{output.close();}catch(IOException ignored){}
            try{input.close();}catch(IOException ignored){}
        }
    }
    private static File unique(File dir,String name){
        File target=new File(dir,name);
        if(!target.exists())return target;
        int dot=name.lastIndexOf('.');
        String base=dot>0?name.substring(0,dot):name,ext=dot>0?name.substring(dot):"";
        for(int i=2;i<1000;i++){
            File candidate=new File(dir,base+" ("+i+")"+ext);
            if(!candidate.exists())return candidate;
        }
        return new File(dir,base+"-"+System.currentTimeMillis()+ext);
    }
    static String displayName(MediaEntry entry){
        String original=entry.name==null?"":entry.name;
        String ext=extension(original,entry.mime);
        String base=original;
        int dot=base.lastIndexOf('.');
        if(dot>0)base=base.substring(0,dot);
        StringBuilder safe=new StringBuilder();
        for(int i=0;i<base.length()&&safe.length()<80;i++){
            char c=base.charAt(i);
            if((c>='a'&&c<='z')||(c>='A'&&c<='Z')||(c>='0'&&c<='9')||c=='-'||c=='_')safe.append(c);
            else if(c==' '||c=='.')safe.append('_');
        }
        if(safe.length()==0)safe.append("SonyLUT");
        String id=entry.id;
        int dash=id.lastIndexOf('-');
        if(dash>=0)id=id.substring(dash+1);
        StringBuilder cleanId=new StringBuilder();
        for(int i=0;i<id.length()&&i<16;i++){
            char c=id.charAt(i);
            if((c>='a'&&c<='z')||(c>='A'&&c<='Z')||(c>='0'&&c<='9'))cleanId.append(c);
        }
        return "SonyLUT_"+safe+(cleanId.length()>0?"_"+cleanId:"")+ext;
    }
    private static String extension(String name,String mime){
        String lower=name.toLowerCase(Locale.US);
        for(String ext:new String[]{".jpg",".jpeg",".mp4",".mts",".m2ts"})
            if(lower.endsWith(ext))return ".jpg".equals(ext)||".jpeg".equals(ext)?".jpg":ext;
        if("image/jpeg".equals(mime))return ".jpg";
        if("video/mp4".equals(mime))return ".mp4";
        if("video/mp2t".equals(mime))return ".mts";
        return ".bin";
    }
}
