package com.github.ma1co.openmemories.tweak;

import android.os.Process;

/** MIT, from OpenMemories Tweak v0.11 (© 2016 ma1co). Runs commands through
 * libtweak in the camera's GNU/Linux environment. */
public class Shell {
    static {
        System.loadLibrary("tweak");
    }

    private static native void nativeExec(String command) throws NativeException;

    public static void exec(String command) throws NativeException {
        try{
            if(command.getBytes("UTF-8").length>255)
                throw new NativeException("native command exceeds 255 bytes");
        }catch(java.io.UnsupportedEncodingException ignored){}
        nativeExec(command);
    }

    public static void execAndroid(String command) throws NativeException {
        int pid = Process.myPid();
        exec("ps e --no-heading " + pid + " | (" +
                 "read _ _ _ _ _ e;" +
                 "export $e;" +
                 "echo $ANDROID_PROPERTY_WORKSPACE | (" +
                     "IFS=, read f _;" +
                     "eval \"exec $f< /proc/" + pid + "/fd/$f\";" +
                     "/bin/busybox chroot /android " + command +
                 ")" +
             ")");
    }
}
