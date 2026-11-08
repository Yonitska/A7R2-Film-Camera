package org.hongen.sonylutprobe;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** A7R II flexible-spot selection in Sony's scalar -1000..1000 coordinates.
 * Arrow movement is local and instant; the camera is written once on confirm. */
final class AfPointController {
    interface Driver {
        Map<String,String> read();
        /** Commits one point and returns the quantized coordinates Sony kept. */
        int[] setFocus(String area,int x,int y)throws Exception;
    }
    interface Log { void log(String name,String detail); }

    private static final int STEP=100;
    private final Driver driver;
    private final Log log;
    private Map<String,String> original;
    private int x,y;
    private boolean active,ready,changed;

    AfPointController(Driver driver,Log log){this.driver=driver;this.log=log;}

    void open(){
        if(ready)return;
        Map<String,String> p=driver.read();
        x=ControlMath.clampAf(number(p.get("focus-point-x")));
        y=ControlMath.clampAf(number(p.get("focus-point-y")));
        ready=true;
    }
    private void saveOriginal(){
        if(original!=null)return;
        Map<String,String> p=driver.read();
        original=new LinkedHashMap<String,String>();
        for(String key:new String[]{"focus-area-type","focus-point-x","focus-point-y","focus-area-flexible-spot-size"})
            if(p.containsKey(key))original.put(key,p.get(key));
    }
    void enter(){
        open();
        saveOriginal();
        // No camera call here: entering the selector must be instant, and an
        // abandoned selection must not disturb the camera's current AF mode.
        active=true;
        log.log("af_enter","x="+x+" y="+y+" camera_untouched=true original="+original);
    }
    /** Movie recording: move the point and commit immediately so the camera's
     * continuous AF can re-focus while the clip is still being written. */
    void liveMove(int dx,int dy)throws Exception{
        open();
        saveOriginal();
        int nextX=ControlMath.clampAf(x+dx*STEP),nextY=ControlMath.clampAf(y+dy*STEP);
        if(nextX==x&&nextY==y)return;
        x=nextX;y=nextY;
        int[] actual=driver.setFocus("flex-spot",x,y);
        x=actual[0];y=actual[1];changed=true;
        log.log("af_live_move","dx="+dx+" dy="+dy+" x="+x+" y="+y);
    }
    /** UI-only movement. Returns false only at the -1000/1000 boundary. */
    boolean move(int dx,int dy){
        if(!active)return false;
        int nextX=ControlMath.clampAf(x+dx*STEP),nextY=ControlMath.clampAf(y+dy*STEP);
        if(nextX==x&&nextY==y)return false;
        x=nextX;y=nextY;
        log.log("af_move","dx="+dx+" dy="+dy+" x="+x+" y="+y);
        return true;
    }
    void confirm()throws Exception{
        if(!active)return;
        int[] actual=driver.setFocus("flex-spot",x,y);
        x=actual[0];y=actual[1];changed=true;active=false;
        log.log("af_confirm","x="+x+" y="+y+" quantized=true");
    }
    void cancel()throws Exception{
        active=false;
        if(original!=null&&changed){
            driver.setFocus(original.get("focus-area-type"),number(original.get("focus-point-x")),number(original.get("focus-point-y")));
            log.log("af_cancel","restored");
        }else if(original!=null){
            x=number(original.get("focus-point-x"));y=number(original.get("focus-point-y"));
            log.log("af_cancel","camera_untouched=true");
        }
        changed=false;original=null;
    }
    void close()throws Exception{
        active=false;
        if(original!=null){
            boolean restore=changed;
            if(restore)driver.setFocus(original.get("focus-area-type"),number(original.get("focus-point-x")),number(original.get("focus-point-y")));
            original=null;changed=false;
            log.log("af_close","restored="+restore);
        }
    }
    int x(){return x;}
    int y(){return y;}
    boolean active(){return active;}
    boolean ready(){return ready;}
    boolean touched(){return original!=null;}
    private static int number(String value){try{return Integer.parseInt(value);}catch(RuntimeException e){return 0;}}
}
