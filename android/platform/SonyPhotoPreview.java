package org.hongen.sonylutprobe;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Environment;
import android.os.SystemClock;
import java.io.*;
import java.lang.reflect.Method;
import java.util.*;

/** Reads Sony's existing screen-nail JPEG for history. No full image decode,
 * camera session, LUT cache, re-encode or mutation of the saved photo. */
final class SonyPhotoPreview {
    private final PhotoIndex index;
    private final Method screenNail,imageInfo;
    SonyPhotoPreview(final Context context)throws Exception{
        Class<?> media=Class.forName("com.sony.scalar.provider.AvindexStore$Images$Media");
        final Uri external=(Uri)media.getField("EXTERNAL_CONTENT_URI").get(null);
        imageInfo=media.getMethod("getImageInfo",String.class);
        screenNail=Class.forName("com.sony.scalar.graphics.AvindexGraphics").getMethod("getScreenNail",String.class);
        index=new PhotoIndex(new PhotoIndex.Query(){public List<PhotoIndex.Entry> read(String selection,String order,int limit)throws IOException{
            Cursor cursor=context.getContentResolver().query(external,new String[]{"_data","rec_order","dcf_folder_number","dcf_file_number"},selection,null,order);
            if(cursor==null)throw new IOException("相机照片索引不可用");
            List<PhotoIndex.Entry> rows=new ArrayList<PhotoIndex.Entry>();
            try{while(rows.size()<limit&&cursor.moveToNext())rows.add(new PhotoIndex.Entry(cursor.getString(0),cursor.getLong(1),cursor.getInt(2),cursor.getInt(3)));}
            finally{cursor.close();}return rows;
        }},new File(Environment.getExternalStorageDirectory(),"DCIM").getCanonicalFile());
    }
    synchronized Bitmap load(final File file)throws Exception{
        try{
        long begin=SystemClock.elapsedRealtime();
        if(!file.isFile())throw new IOException("照片已移动或 SD 卡不可用");
        PhotoIndex.Entry entry=new IndexReady().await(new IndexReady.Lookup(){public PhotoIndex.Entry read()throws IOException{return index.source(file);}},3000);
        long indexed=SystemClock.elapsedRealtime();
        Object info=imageInfo.invoke(null,entry.uri);if(info==null)throw new IOException("照片信息暂不可用");int orientation;
        try{orientation=((Integer)info.getClass().getMethod("getAttributeInt",String.class,int.class).invoke(info,"Orientation",1)).intValue();}
        finally{info.getClass().getMethod("recycle").invoke(info);}
        long metadata=SystemClock.elapsedRealtime();
        byte[] jpeg=(byte[])screenNail.invoke(null,entry.uri);
        if(jpeg==null||jpeg.length==0)throw new IOException("相机预览暂不可用，请重新打开照片");
        long read=SystemClock.elapsedRealtime();
        BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
        BitmapFactory.decodeByteArray(jpeg,0,jpeg.length,options);
        if(options.outWidth<=0||options.outHeight<=0)throw new IOException("照片预览尺寸无效");
        int sample=1;while(Math.max(options.outWidth,options.outHeight)/sample>1024)sample*=2;
        Bitmap bitmap=decodeNail(jpeg,sample);
        // Sony's screen nail is the cheap path. If ARGB cannot fit, a half-size
        // RGB_565 copy keeps playback alive; it is never allowed to escape as an
        // OutOfMemoryError, which would kill the camera host process.
        if(bitmap==null)throw new IOException("内存不足，无法读取预览");
        long decoded=SystemClock.elapsedRealtime();Bitmap result;
        try{result=ReviewLoader.rotate(bitmap,orientation);}
        catch(OutOfMemoryError e){
            if(!bitmap.isRecycled())bitmap.recycle();
            throw new IOException("内存不足，无法旋转预览");
        }
        android.util.Log.i("LutUI","gallery_decode file="+file.getName()+" index_ms="+(indexed-begin)+" info_ms="+(metadata-indexed)
            +" read_ms="+(read-metadata)+" decode_ms="+(decoded-read)+" rotate_ms="+(SystemClock.elapsedRealtime()-decoded)
            +" bitmap="+result.getWidth()+"x"+result.getHeight());
        return result;
        }catch(OutOfMemoryError e){
            android.util.Log.w("LutUI","gallery_nail_oom file="+file.getName(),e);
            throw new IOException("内存不足，无法读取预览");
        }
    }
    private static Bitmap decodeNail(byte[] jpeg,int sample){
        BitmapFactory.Options options=new BitmapFactory.Options();
        options.inSampleSize=Math.max(1,sample);
        options.inPreferredConfig=Bitmap.Config.ARGB_8888;options.inDither=false;
        try{return BitmapFactory.decodeByteArray(jpeg,0,jpeg.length,options);}
        catch(OutOfMemoryError first){
            android.util.Log.w("LutUI","gallery_nail_retry sample="+(sample*2)+" config=565");
            options=new BitmapFactory.Options();
            options.inSampleSize=Math.max(1,sample*2);
            options.inPreferredConfig=Bitmap.Config.RGB_565;options.inDither=false;
            try{return BitmapFactory.decodeByteArray(jpeg,0,jpeg.length,options);}
            catch(OutOfMemoryError second){return null;}
        }
    }

    /** EXIF orientation of the saved JPEG. */
    static int orientation(File file){
        try{return new ExifInterface(file.getPath()).getAttributeInt(ExifInterface.TAG_ORIENTATION,1);}
        catch(Throwable e){android.util.Log.w("LutUI","gallery_tile_exif_failed",e);return 1;}
    }
    /** Raw size before EXIF rotation, read from the JPEG headers only. */
    static int[] rawSize(File file)throws IOException{
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
        BitmapFactory.decodeFile(file.getPath(),bounds);
        if(bounds.outWidth<=0||bounds.outHeight<=0)throw new IOException("原图尺寸读取失败");
        return new int[]{bounds.outWidth,bounds.outHeight};
    }
    /** Decode one rectangle of the original JPEG. This is the only path that
     * can reach the full 42MP detail: the gallery panel is 640x480 and a
     * complete ARGB decode would be 161 MB, far beyond the 24 MB app heap. */
    synchronized Bitmap loadRegion(File file,int[] rawRect,int sample)throws IOException{
        long begin=SystemClock.elapsedRealtime();
        if(!file.isFile())throw new IOException("照片已移动或 SD 卡不可用");
        BitmapRegionDecoder decoder=null;
        try{
            decoder=BitmapRegionDecoder.newInstance(file.getPath(),false);
            BitmapFactory.Options options=new BitmapFactory.Options();
            options.inSampleSize=Math.max(1,sample);
            options.inPreferredConfig=Bitmap.Config.ARGB_8888;
            options.inDither=false;
            Bitmap bitmap=decoder.decodeRegion(new android.graphics.Rect(
                    rawRect[0],rawRect[1],rawRect[2],rawRect[3]),options);
            if(bitmap==null)throw new IOException("原图区域解码失败");
            android.util.Log.i("LutUI","gallery_tile file="+file.getName()+" raw="+rawRect[0]+","+rawRect[1]
                    +" "+rawRect[2]+"x"+rawRect[3]+" sample="+sample+" bitmap="+bitmap.getWidth()+"x"
                    +bitmap.getHeight()+" decode_ms="+(SystemClock.elapsedRealtime()-begin));
            return bitmap;
        }catch(OutOfMemoryError e){
            throw new IOException("内存不足，无法放大原图");
        }finally{
            if(decoder!=null)try{decoder.recycle();}catch(Throwable ignored){}
        }
    }
    /** Whole-image decode for 1x playback. Sony's screen nail is a compressed
     * preview; a 1/8-scale decode of a 42MP file is ~994x663 (2.6 MB ARGB) —
     * higher than the 640x480 panel yet far inside the gallery's 24 MB heap. */
    synchronized Bitmap loadBase(File file)throws Exception{
        long begin=SystemClock.elapsedRealtime();
        if(!file.isFile())throw new IOException("照片已移动或 SD 卡不可用");
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
        BitmapFactory.decodeFile(file.getPath(),bounds);
        if(bounds.outWidth<=0||bounds.outHeight<=0)throw new IOException("原图解码失败");
        int sample=PhotoTiles.sampleFor(bounds.outWidth,bounds.outHeight,1400);
        BitmapFactory.Options options=new BitmapFactory.Options();
        options.inSampleSize=sample;options.inPreferredConfig=Bitmap.Config.ARGB_8888;options.inDither=false;
        Bitmap bitmap;
        try{bitmap=BitmapFactory.decodeFile(file.getPath(),options);}
        catch(OutOfMemoryError e){throw new IOException("内存不足，无法读取原图");}
        if(bitmap==null)throw new IOException("原图解码失败");
        int orientation=orientation(file);
        long decoded=SystemClock.elapsedRealtime();
        Bitmap result=ReviewLoader.rotate(bitmap,orientation);
        android.util.Log.i("LutUI","gallery_base file="+file.getName()+" source="+bounds.outWidth+"x"+bounds.outHeight
            +" sample="+sample+" bitmap="+result.getWidth()+"x"+result.getHeight()+" decode_ms="+(decoded-begin)
            +" rotate_ms="+(SystemClock.elapsedRealtime()-decoded));
        return result;
    }
    /** Full-JPEG decode for zooming. Sony's screen nail is only ~1024px, which
     * turns 4-8x playback zoom into mush. A 1/4-scale decode of the 42MP file
     * is ~2000px and still fits comfortably in the gallery heap. */
    synchronized Bitmap loadDetail(File file)throws Exception{
        long begin=SystemClock.elapsedRealtime();
        if(!file.isFile())throw new IOException("照片已移动或 SD 卡不可用");
        BitmapFactory.Options bounds=new BitmapFactory.Options();
        bounds.inJustDecodeBounds=true;
        BitmapFactory.decodeFile(file.getPath(),bounds);
        if(bounds.outWidth<=0||bounds.outHeight<=0)throw new IOException("原图解码失败");
        int sample=1,longest=Math.max(bounds.outWidth,bounds.outHeight);
        while(longest/sample>2048)sample*=2;
        BitmapFactory.Options options=new BitmapFactory.Options();
        options.inSampleSize=sample;options.inPreferredConfig=Bitmap.Config.ARGB_8888;options.inDither=false;
        Bitmap bitmap;
        try{bitmap=BitmapFactory.decodeFile(file.getPath(),options);}
        catch(OutOfMemoryError e){
            options.inSampleSize=Math.max(2,sample*2);
            try{bitmap=BitmapFactory.decodeFile(file.getPath(),options);}
            catch(OutOfMemoryError again){throw new IOException("内存不足，无法加载原图");}
        }
        if(bitmap==null)throw new IOException("原图解码失败");
        int orientation=1;
        try{orientation=new ExifInterface(file.getPath()).getAttributeInt(ExifInterface.TAG_ORIENTATION,1);}
        catch(Throwable e){android.util.Log.w("LutUI","gallery_detail_exif_failed",e);}
        long decoded=SystemClock.elapsedRealtime();
        Bitmap result=ReviewLoader.rotate(bitmap,orientation);
        android.util.Log.i("LutUI","gallery_detail file="+file.getName()+" source="+bounds.outWidth+"x"+bounds.outHeight
            +" sample="+sample+" bitmap="+result.getWidth()+"x"+result.getHeight()+" decode_ms="+(decoded-begin)
            +" rotate_ms="+(SystemClock.elapsedRealtime()-decoded));
        return result;
    }
}
