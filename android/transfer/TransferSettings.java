package org.hongen.sonylutprobe;

import android.content.Context;

/** Durable product switches. Kept separate from capture preferences so the
 * foreground service can decide whether it is allowed to run without opening
 * the camera or the UI. */
final class TransferSettings {
    private static final String PREFS="sony_lut_transfer";
    private static final String ENABLED="enabled";
    private static final String PORT="port";
    private static final String FACE="face_detection";
    static final int DEFAULT_PORT=8080;

    private TransferSettings(){}

    static boolean enabled(Context context){
        return context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getBoolean(ENABLED,false);
    }
    static void setEnabled(Context context,boolean enabled){
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putBoolean(ENABLED,enabled).commit();
    }
    static int port(Context context){
        int value=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getInt(PORT,DEFAULT_PORT);
        return value>=1024&&value<=65535?value:DEFAULT_PORT;
    }
    static void setPort(Context context,int port){
        if(port<1024||port>65535)return;
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putInt(PORT,port).commit();
    }
    /** Native face-priority autofocus and the vendor face overlay. Enabled by
     * default to match the camera's own auto mode; capture restores whatever
     * value the body reported if this switch is off. */
    static boolean faceDetection(Context context){
        return context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getBoolean(FACE,true);
    }
    static void setFaceDetection(Context context,boolean enabled){
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putBoolean(FACE,enabled).commit();
    }
}
