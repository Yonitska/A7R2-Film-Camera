package org.hongen.sonylutprobe;

import android.hardware.Camera;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/** CameraEx-backed exposure primitives; A7R II refreshes are relative. */
final class CameraExManualDriver implements ManualExposure.Driver,RecipeCameraLook.Driver {
    private final Object cameraEx;
    private final Camera camera;
    private final boolean movie;

    CameraExManualDriver(Object cameraEx,Camera camera){this(cameraEx,camera,false);}
    CameraExManualDriver(Object cameraEx,Camera camera,boolean movie){
        this.cameraEx=cameraEx;this.camera=camera;this.movie=movie;
    }

    public Map<String,String> read(){
        Map<String,String> result=new LinkedHashMap<String,String>();
        for(String item:camera.getParameters().flatten().split(";")){
            int i=item.indexOf('=');if(i>0)result.put(item.substring(0,i),item.substring(i+1));
        }
        return result;
    }
    public void write(Map<String,String> patch)throws Exception{
        if(movie)writeMovie(patch);else writeFull(patch);
    }
    private void writeFull(Map<String,String> patch){
        Camera.Parameters p=camera.getParameters();
        for(Map.Entry<String,String> e:patch.entrySet())p.set(e.getKey(),e.getValue());
        camera.setParameters(p);
    }
    /** Movie mode rejects a full Camera.Parameters commit (the video session
     * contains inhibited/read-only keys). Sony's own controllers use an empty
     * parameter object plus ParametersModifier, then tryIgnoreInhibit; use the
     * same path for ISO, scene mode, EV and WB while recording or in standby. */
    private void writeMovie(Map<String,String> patch)throws Exception{
        Camera.Parameters p=(Camera.Parameters)cameraEx.getClass()
                .getMethod("createEmptyParameters").invoke(cameraEx);
        Object modifier=null;
        try{
            modifier=cameraEx.getClass().getMethod("createParametersModifier",Camera.Parameters.class)
                    .invoke(cameraEx,p);
        }catch(NoSuchMethodException ignored){}
        p.set("isEmptyParameters","1");
        for(Map.Entry<String,String> e:patch.entrySet()){
            String key=e.getKey(),value=e.getValue();
            if("iso-sensitivity".equals(key)&&modifier!=null){
                modifier.getClass().getMethod("setISOSensitivity",int.class)
                        .invoke(modifier,Integer.valueOf(number(value)));
            }else if("scene-mode".equals(key)){
                p.setSceneMode(value);
            }else if("exposure-compensation".equals(key)){
                p.setExposureCompensation(number(value));
            }else if("whitebalance".equals(key)){
                p.setWhiteBalance(value);
            }else if("focus-mode".equals(key)){
                p.setFocusMode(value);
            }else if("af-mode".equals(key)&&modifier!=null){
                try{modifier.getClass().getMethod("setAutoFocusMode",String.class).invoke(modifier,value);}
                catch(NoSuchMethodException missing){p.set(key,value);}
            }else{
                p.set(key,value);
            }
        }
        if(modifier!=null)try{modifier.getClass().getMethod("tryIgnoreInhibit").invoke(modifier);}
        catch(Exception ignored){}
        try{camera.setParameters(p);return;}
        catch(RuntimeException emptyFailure){
            // Some firmware revisions reject an empty parameter object while a
            // movie session is armed. Retry with the current full snapshot;
            // never hide a real failure if both paths reject the patch.
            Camera.Parameters full=camera.getParameters();
            for(Map.Entry<String,String> e:patch.entrySet()){
                String key=e.getKey(),value=e.getValue();
                if("scene-mode".equals(key))full.setSceneMode(value);
                else if("exposure-compensation".equals(key))full.setExposureCompensation(number(value));
                else if("whitebalance".equals(key))full.setWhiteBalance(value);
                else if("focus-mode".equals(key))full.setFocusMode(value);
                else full.set(key,value);
            }
            try{camera.setParameters(full);}
            catch(RuntimeException fullFailure){
                throw new IllegalStateException("movie setParameters failed for "+patch
                        +" empty="+emptyFailure+" full="+fullFailure,fullFailure);
            }
            android.util.Log.i("SonyManual","movie_empty_rejected_patch="+patch+" error="+emptyFailure);
        }
    }
    public void incrementAperture()throws Exception{invoke("incrementAperture");}
    public void decrementAperture()throws Exception{invoke("decrementAperture");}
    public void incrementShutterSpeed()throws Exception{invoke("incrementShutterSpeed");}
    public void decrementShutterSpeed()throws Exception{invoke("decrementShutterSpeed");}
    private void invoke(String name)throws Exception{
        try{cameraEx.getClass().getMethod(name).invoke(cameraEx);}
        catch(NoSuchMethodException e){throw new UnsupportedOperationException("CameraEx."+name+" unavailable",e);}
    }

    public void setIso(int iso)throws Exception{
        write(one("iso-sensitivity",Integer.toString(iso)));
        int actual=number(read().get("iso-sensitivity"));
        if(actual==iso)return;
        if(movie){
            // Movie mode may apply the value asynchronously or expose the
            // nearest native step; the caller's final read is authoritative.
            android.util.Log.i("SonyManual","movie_iso_readback wanted="+iso+" actual="+actual);
            return;
        }
        // Sony's own ISO controller commits through an empty Parameters object
        // plus ParametersModifier. Some bodies ignore the bare key.
        Method createEmpty=cameraEx.getClass().getMethod("createEmptyParameters");
        Camera.Parameters p=(Camera.Parameters)createEmpty.invoke(cameraEx);
        Object modifier=cameraEx.getClass().getMethod("createParametersModifier",Camera.Parameters.class).invoke(cameraEx,p);
        p.set("isEmptyParameters","1");
        modifier.getClass().getMethod("setISOSensitivity",int.class).invoke(modifier,iso);
        modifier.getClass().getMethod("tryIgnoreInhibit").invoke(modifier);
        camera.setParameters(p);
    }
    public void setWhiteBalance(String value)throws Exception{
        write(one("whitebalance",value));
        if(!value.equals(read().get("whitebalance"))&&!movie){
            Camera.Parameters p=camera.getParameters();p.setWhiteBalance(value);camera.setParameters(p);
        }
    }
    /** Sony's own M-mode live view uses the empty-parameter modifier setter;
     * writing the bare Camera.Parameters key alone is ignored by the A7R II. */
    public void setShootingPreview(String value)throws Exception{
        try{
            Method createEmpty=cameraEx.getClass().getMethod("createEmptyParameters");
            Camera.Parameters p=(Camera.Parameters)createEmpty.invoke(cameraEx);
            Object modifier=cameraEx.getClass().getMethod("createParametersModifier",Camera.Parameters.class).invoke(cameraEx,p);
            p.set("isEmptyParameters","1");
            modifier.getClass().getMethod("setShootingPreviewMode",String.class).invoke(modifier,value);
            modifier.getClass().getMethod("tryIgnoreInhibit").invoke(modifier);
            camera.setParameters(p);
            if(value.equals(read().get("shooting-preview")))return;
        }catch(Throwable e){
            android.util.Log.w("SonyManual","setShootingPreviewMode unavailable; trying bare key",e);
        }
        write(one("shooting-preview",value));
    }
    /** Freeze/unfreeze metering through Sony's own modifier. The preview
     * mode on the A7R II unlocks AE, which can otherwise re-derive the
     * captured exposure after the app has set M aperture/shutter/ISO. */
    public void setAutoExposureLock(boolean locked)throws Exception{
        try{
            Method createEmpty=cameraEx.getClass().getMethod("createEmptyParameters");
            Camera.Parameters p=(Camera.Parameters)createEmpty.invoke(cameraEx);
            Object modifier=cameraEx.getClass().getMethod("createParametersModifier",Camera.Parameters.class).invoke(cameraEx,p);
            p.set("isEmptyParameters","1");
            modifier.getClass().getMethod("setAutoExposureLock",boolean.class).invoke(modifier,locked);
            modifier.getClass().getMethod("tryIgnoreInhibit").invoke(modifier);
            camera.setParameters(p);
            return;
        }catch(Throwable e){
            android.util.Log.w("SonyManual","setAutoExposureLock modifier unavailable",e);
        }
        Camera.Parameters p=camera.getParameters();
        p.set("ae-lock",locked?"locked":"unlocked");
        camera.setParameters(p);
    }
    private static Map<String,String> one(String key,String value){
        Map<String,String> patch=new LinkedHashMap<String,String>();patch.put(key,value);return patch;
    }
    private static int number(String value){try{return Integer.parseInt(value);}catch(RuntimeException e){return 0;}}
}
