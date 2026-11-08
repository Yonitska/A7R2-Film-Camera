package org.hongen.sonylutprobe;

import android.hardware.Camera;
import android.util.Log;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Commits flexible-spot AF through Sony's parameter/modifier paths.
 * The camera quantizes the point to its AF grid, so equality is not required:
 * a value that moved in the requested direction is a success. */
final class CameraAfDriver implements AfPointController.Driver {
    private static final String TAG="SonyManual";
    private final Object cameraEx;
    private final Camera camera;

    CameraAfDriver(Object cameraEx,Camera camera){this.cameraEx=cameraEx;this.camera=camera;}

    public Map<String,String> read(){
        return flatten(camera.getParameters());
    }
    public int[] setFocus(String area,int x,int y)throws Exception{
        int[] before=point(read());
        int[] actual;

        Camera.Parameters direct=camera.getParameters();
        direct.set("focus-area-type",area);
        direct.set("focus-point-x",Integer.toString(x));
        direct.set("focus-point-y",Integer.toString(y));
        camera.setParameters(direct);
        Map<String,String> now=read();actual=point(now);
        if(applied(area,x,y,before,actual,now))return actual;

        // Sony's own flexible-position menu can commit the point without
        // touching the already-flexible area.
        try{
            Camera.Parameters pointOnly=camera.getParameters();
            pointOnly.set("focus-point-x",Integer.toString(x));
            pointOnly.set("focus-point-y",Integer.toString(y));
            camera.setParameters(pointOnly);
            now=read();actual=point(now);
            if(applied(area,x,y,before,actual,now))return actual;
        }catch(Throwable e){Log.w(TAG,"af_point_only_failed",e);}

        // Some bodies accept the first changed axis in a transaction and defer
        // the other one. Retry each axis alone with the area mode included.
        now=read();
        if(y!=number(now.get("focus-point-y"))){
            try{
                Camera.Parameters vertical=camera.getParameters();
                vertical.set("focus-area-type",area);
                vertical.set("focus-point-y",Integer.toString(y));
                camera.setParameters(vertical);
                now=read();actual=point(now);
                if(applied(area,x,y,before,actual,now))return actual;
            }catch(Throwable e){Log.w(TAG,"af_vertical_failed",e);}
        }
        now=read();
        if(x!=number(now.get("focus-point-x"))){
            try{
                Camera.Parameters horizontal=camera.getParameters();
                horizontal.set("focus-area-type",area);
                horizontal.set("focus-point-x",Integer.toString(x));
                camera.setParameters(horizontal);
                now=read();actual=point(now);
                if(applied(area,x,y,before,actual,now))return actual;
            }catch(Throwable e){Log.w(TAG,"af_horizontal_failed",e);}
        }

        if(modifier(area,x,y,false)){
            now=read();actual=point(now);
            if(applied(area,x,y,before,actual,now))return actual;
        }
        if(modifier(area,x,y,true)){
            now=read();actual=point(now);
            if(applied(area,x,y,before,actual,now))return actual;
        }

        Map<String,String> finalState=read();
        actual=point(finalState);
        throw new IOException("AF readback differs requested="+area+" "+x+","+y
                +" actual="+finalState.get("focus-area-type")+" "+actual[0]+","+actual[1]
                +" before="+before[0]+","+before[1]);
    }
    private boolean modifier(String area,int x,int y,boolean pointFirst)throws Exception{
        Camera.Parameters p=(Camera.Parameters)cameraEx.getClass().getMethod("createEmptyParameters").invoke(cameraEx);
        Object m=cameraEx.getClass().getMethod("createParametersModifier",Camera.Parameters.class).invoke(cameraEx,p);
        p.set("isEmptyParameters","1");
        if(pointFirst){
            m.getClass().getMethod("setFocusPoint",int.class,int.class).invoke(m,x,y);
            m.getClass().getMethod("setFocusAreaMode",String.class).invoke(m,area);
        }else{
            m.getClass().getMethod("setFocusAreaMode",String.class).invoke(m,area);
            m.getClass().getMethod("setFocusPoint",int.class,int.class).invoke(m,x,y);
        }
        m.getClass().getMethod("tryIgnoreInhibit").invoke(m);
        camera.setParameters(p);
        return true;
    }
    /** Success means the area matches and every requested coordinate changed.
     * Sony rounds to its AF grid (sometimes by several units, including at the
     * frame edge), so exact equality or the same sign must not be required. */
    private static boolean applied(String area,int x,int y,int[] before,int[] actual,Map<String,String> state){
        if(!area.equals(state.get("focus-area-type")))return false;
        if(!"flex-spot".equals(area))return true;
        return moved(x,before[0],actual[0])&&moved(y,before[1],actual[1]);
    }
    private static boolean moved(int requested,int before,int actual){
        return requested==before||actual!=before;
    }
    private static int[] point(Map<String,String> p){
        return new int[]{number(p.get("focus-point-x")),number(p.get("focus-point-y"))};
    }
    private static Map<String,String> flatten(Camera.Parameters parameters){
        Map<String,String> result=new LinkedHashMap<String,String>();
        for(String item:parameters.flatten().split(";")){
            int i=item.indexOf('=');if(i>0)result.put(item.substring(0,i),item.substring(i+1));
        }
        return result;
    }
    private static int number(String value){try{return Integer.parseInt(value);}catch(RuntimeException e){return 0;}}
}
