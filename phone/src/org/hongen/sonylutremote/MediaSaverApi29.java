package org.hongen.sonylutremote;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import org.hongen.sonylut.transfer.MediaEntry;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Loaded only on Android 10 and newer. Keeping the IS_PENDING/RELATIVE_PATH
 * constants in a separate class prevents Dalvik from resolving them on the
 * Android 6–9 legacy path. */
@TargetApi(29)
final class MediaSaverApi29 {
    private MediaSaverApi29(){}

    static MediaSaver.Saved save(Context context,MediaEntry entry,File source,String name)throws IOException {
        ContentResolver resolver=context.getContentResolver();
        boolean photo="photo".equals(entry.kind);
        Uri collection=photo?MediaStore.Images.Media.EXTERNAL_CONTENT_URI:MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        ContentValues values=new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME,name);
        values.put(MediaStore.MediaColumns.MIME_TYPE,entry.mime);
        values.put(MediaStore.MediaColumns.RELATIVE_PATH,
            (photo?Environment.DIRECTORY_PICTURES:Environment.DIRECTORY_MOVIES)+"/SonyLUT");
        values.put(MediaStore.MediaColumns.IS_PENDING,1);
        Uri uri=resolver.insert(collection,values);
        if(uri==null)throw new IOException("系统相册拒绝写入");
        boolean ok=false;
        try{
            InputStream input=new FileInputStream(source);
            OutputStream output=resolver.openOutputStream(uri,"w");
            if(output==null)throw new IOException("无法打开相册文件");
            try{
                byte[] buffer=new byte[128*1024];int read;
                while((read=input.read(buffer))>=0)if(read>0)output.write(buffer,0,read);
                output.flush();
            }finally{
                try{output.close();}catch(IOException ignored){}
                try{input.close();}catch(IOException ignored){}
            }
            ContentValues done=new ContentValues();
            done.put(MediaStore.MediaColumns.IS_PENDING,0);
            resolver.update(uri,done,null,null);
            ok=true;
            return new MediaSaver.Saved(name,photo?Environment.DIRECTORY_PICTURES+"/SonyLUT":Environment.DIRECTORY_MOVIES+"/SonyLUT");
        }finally{
            if(!ok)try{resolver.delete(uri,null,null);}catch(Exception ignored){}
        }
    }
}
