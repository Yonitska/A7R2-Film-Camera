package org.hongen.sonylutprobe;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

/** Last film chosen for each camera group.
 *
 * LibraryActivity, PhotoActivity (:capture) and VideoActivity (:video) run in
 * different processes, where Android's SharedPreferences caches are not
 * coherent. A tiny properties file in the app's files directory is shared by
 * all three processes and also survives an abrupt camera reset. */
final class RecipeMemory {
    private static final String FILE="recipe-selection.txt";
    private static final String CAMERA="camera";

    private RecipeMemory(){}

    static int camera(Context context,int fallback){
        return read(context,CAMERA,fallback);
    }

    static int local(Context context,int group,int fallback){
        return read(context,"pick_"+group,fallback);
    }

    static void save(Context context,int group,int local){
        Properties values=readAll(context);
        values.setProperty(CAMERA,Integer.toString(group));
        values.setProperty("pick_"+group,Integer.toString(local));
        File file=file(context);
        FileOutputStream out=null;
        try{
            out=new FileOutputStream(file,false);
            values.store(out,"sony-lut recipe selection");
            out.flush();
        }catch(Exception ignored){
        }finally{
            if(out!=null)try{out.close();}catch(Exception ignored){}
        }
    }

    private static int read(Context context,String key,int fallback){
        String value=readAll(context).getProperty(key);
        if(value==null)return fallback;
        try{return Integer.parseInt(value.trim());}catch(RuntimeException e){return fallback;}
    }

    private static Properties readAll(Context context){
        Properties values=new Properties();
        FileInputStream in=null;
        try{
            File file=file(context);
            if(file.isFile()&&file.canRead()){
                in=new FileInputStream(file);
                values.load(in);
            }
        }catch(Exception ignored){
        }finally{
            if(in!=null)try{in.close();}catch(Exception ignored){}
        }
        return values;
    }

    private static File file(Context context){
        return new File(context.getFilesDir(),FILE);
    }
}
