package org.hongen.sonylutprobe;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/** Contact-print thumbnails written while a photo pair is catalogued.
 *
 * Playback can paint the grid from these 480x320 JPEGs without decoding a
 * 42MP original. FuFsys only accepts 8.3 names, so they live in
 * SONYLUT/PHOTOS/THUMB/Pxxxxxxx.JPG and are invisible to PhotoCatalog. */
final class PhotoThumbs {
    static final int W=480,H=320;
    static final int GRID_W=144,GRID_H=96;

    private PhotoThumbs(){}

    static File dir(File photos){return new File(photos,"THUMB");}

    static File file(File photos,String id){
        if(photos==null||id==null)return null;
        String base=id.endsWith(".JSN")?id.substring(0,id.length()-4):id;
        if(!base.matches("P[0-9A-F]{7}"))return null;
        return new File(dir(photos),base+".JPG");
    }

    /** Writes a JPEG atomically through an 8.3 temp name. Called on the
     * catalog worker right after the receipt, off the UI thread. */
    static boolean save(File photos,String id,Bitmap bitmap){
        File dest=file(photos,id);
        if(dest==null||bitmap==null)return false;
        File parent=dest.getParentFile();
        if(parent!=null&&!parent.exists()&&!parent.mkdirs())return false;
        File temp=new File(parent,dest.getName().substring(0,4)+"T.JPG");
        FileOutputStream out=null;
        try{
            out=new FileOutputStream(temp);
            if(!bitmap.compress(Bitmap.CompressFormat.JPEG,82,out))return false;
            out.flush();out.close();out=null;
            if(dest.isFile()&&!dest.delete())return false;
            return temp.renameTo(dest);
        }catch(IOException e){return false;}
        finally{
            if(out!=null)try{out.close();}catch(IOException ignored){}
            temp.delete();
        }
    }

    static Bitmap load(File photos,String id,int targetW,int targetH){
        File f=file(photos,id);
        return f!=null&&f.isFile()?decodeSampled(f,targetW,targetH):null;
    }

    /** Fastest JPEG path: power-of-two inSampleSize, then one exact resize.
     * Used both for saved contact prints and for photos captured before this
     * version existed. */
    static Bitmap decodeSampled(File file,int targetW,int targetH){
        if(file==null||!file.isFile()||targetW<=0||targetH<=0)return null;
        BitmapFactory.Options o=new BitmapFactory.Options();
        o.inJustDecodeBounds=true;
        BitmapFactory.decodeFile(file.getPath(),o);
        if(o.outWidth<=0||o.outHeight<=0)return null;
        int sample=1;
        while(o.outWidth/(sample*2)>=targetW&&o.outHeight/(sample*2)>=targetH)sample*=2;
        o.inJustDecodeBounds=false;o.inSampleSize=sample;
        o.inPreferredConfig=Bitmap.Config.ARGB_8888;
        Bitmap bitmap;
        try{bitmap=BitmapFactory.decodeFile(file.getPath(),o);}
        catch(OutOfMemoryError e){return null;}
        if(bitmap==null)return null;
        if(bitmap.getWidth()>targetW||bitmap.getHeight()>targetH){
            float k=Math.min(targetW/(float)bitmap.getWidth(),targetH/(float)bitmap.getHeight());
            int w=Math.max(1,Math.round(bitmap.getWidth()*k)),h=Math.max(1,Math.round(bitmap.getHeight()*k));
            Bitmap scaled=Bitmap.createScaledBitmap(bitmap,w,h,true);
            if(scaled!=bitmap)bitmap.recycle();
            bitmap=scaled;
        }
        return bitmap;
    }
}
