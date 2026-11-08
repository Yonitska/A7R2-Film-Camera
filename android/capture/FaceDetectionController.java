package org.hongen.sonylutprobe;

import android.hardware.Camera;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Enables the camera's own face-priority autofocus.
 *
 * This is a clean-room use of the observed CameraEx ParametersModifier entry
 * point. It deliberately does not register a FaceDetectionListener and never
 * draws a face box: the vendor live view owns both detection and the overlay.
 * The first capture also writes a read-only EYEPROBE.TXT on the card.
 *
 * a7r2.51: the A7R II's face-detection-mode is already "auto" on this body,
 * so the only actual change the old code made was setFaceFrameRendering(true).
 * Every LUT session that asked for the vendor frame overlay ended in
 * ShutterListener CANCELED (direct shutter) or a takePicture that never wrote
 * a file, while sessions with the same mode and no overlay captured normally.
 * The overlay is therefore not requested: face-priority AF stays enabled and
 * the capture path keeps the parameters it had before the app opened.
 *
 * Eye AF: the A7R II reports no eye parameter and no callable eye setter in the
 * observed public surface. This class only records that evidence; it never
 * probes hidden setters and does not present a fake eye-AF switch.
 */
final class FaceDetectionController {
    interface Log { void event(String name,String detail); }
    private static final String REPORT="EYEPROBE.TXT";
    private final Object cameraEx;
    private final Camera camera;
    private final boolean enabled;
    private final File report;
    private final Log log;
    private Class<?> modifierType;
    private String originalMode="";
    private String modeError="";
    private String supportedModes="";
    private boolean modeApplied;

    FaceDetectionController(Object cameraEx,Camera camera,boolean enabled,File sd,Log log){
        this.cameraEx=cameraEx;this.camera=camera;this.enabled=enabled;this.log=log;
        this.report=sd==null?null:new File(new File(sd,"SONYLUT"),REPORT);
        try{modifierType=Class.forName("com.sony.scalar.hardware.CameraEx$ParametersModifier");}
        catch(Throwable error){modeError=cause(error);}
    }

    /** Apply before startPreview. Failures are recorded and never block capture. */
    void apply(String origin){
        if(!enabled)return;
        try{
            Camera.Parameters full=camera.getParameters();
            originalMode=value(full,"face-detection-mode");
            supportedModes=value(full,"face-detection-mode-values");
            readSupportedModes(full);
            // a7r2.51: this body already reports face-detection-mode "auto".
            // Re-writing the same value plus the vendor frame overlay was the
            // only parameter change the feature made, and every capture session
            // carrying it lost the direct shutter or the takePicture. Skip a
            // no-op write; only ask for face-priority AF when the camera is not
            // already in it. Never request the frame overlay.
            if(originalMode.length()>0&&"auto".equals(originalMode)){
                modeApplied=false;
            }else{
                try{setMode("auto");modeApplied=true;}
                catch(Throwable error){modeError=cause(error);}
            }
            if(log!=null){
                log.event("face_detection_applied","origin="+origin+" original="+originalMode
                    +" requested=auto readback="+readMode()+" rendering=suppressed_capture_safety"
                    +" mode_error="+modeError);
            }
        }catch(Throwable error){
            if(modeError.length()==0)modeError=cause(error);
            if(log!=null)log.event("face_detection_error",cause(error));
        }finally{
            writeProbe(origin);
        }
    }

    /** Restore the reported mode before CameraEx is released. The frame
     * overlay is never requested by this class, so there is nothing to turn
     * off; a7r2.51 removed the old setFaceFrameRendering(false) write because
     * any capture-time parameter transaction on this body risks the shutter. */
    void restore(){
        if(!enabled)return;
        if(!modeApplied){
            if(log!=null)log.event("face_detection_restored","mode="+readMode()
                +" requested="+originalMode+" writes=none rendering=untouched");
            return;
        }
        try{
            if(originalMode.length()>0)setMode(originalMode);
            if(log!=null)log.event("face_detection_restored","mode="+readMode()
                +" requested="+originalMode+" rendering=untouched");
        }catch(Throwable error){
            if(log!=null)log.event("face_detection_restore_error",cause(error));
        }
    }

    private void setMode(final String mode)throws Exception {
        applyPatch(new Patch(){public void write(Object modifier)throws Exception{
            modifierType.getMethod("setFaceDetectionMode",String.class).invoke(modifier,mode);
        }});
    }
    private interface Patch { void write(Object modifier)throws Exception; }
    private void applyPatch(Patch patch)throws Exception {
        if(cameraEx==null||camera==null||modifierType==null)throw new IOException("Sony ParameterModifier unavailable");
        Camera.Parameters empty=(Camera.Parameters)cameraEx.getClass().getMethod("createEmptyParameters").invoke(cameraEx);
        empty.set("isEmptyParameters","1");
        Object modifier=cameraEx.getClass().getMethod("createParametersModifier",Camera.Parameters.class).invoke(cameraEx,empty);
        patch.write(modifier);
        camera.setParameters(empty);
    }
    private String readMode(){
        try{return value(camera.getParameters(),"face-detection-mode");}
        catch(Throwable error){return "";}
    }
    private void readSupportedModes(Camera.Parameters p){
        try{
            if(modifierType==null)return;
            Object modifier=cameraEx.getClass().getMethod("createParametersModifier",Camera.Parameters.class).invoke(cameraEx,p);
            Object result=modifierType.getMethod("getSupportedFaceDetectionModes").invoke(modifier);
            if(result instanceof List){
                List<?> list=(List<?>)result;StringBuilder out=new StringBuilder();
                for(Object item:list){if(out.length()>0)out.append(',');out.append(item);}
                supportedModes=out.toString();
            }
            Object current=modifierType.getMethod("getFaceDetectionMode").invoke(modifier);
            if(current!=null&&originalMode.length()==0)originalMode=current.toString();
        }catch(Throwable ignored){/* p.get fallback already supplied the values */}
    }

    /** Evidence-only report. It lists public API names and camera parameter
     * keys, never credentials, file paths or captured image data. */
    private void writeProbe(String origin){
        if(report==null||report.isFile())return;
        StringBuilder out=new StringBuilder();
        try{
            Map<String,String> params=flatten(camera.getParameters());
            List<String> eyeKeys=keys(params,"eye"),faceKeys=keys(params,"face"),focusKeys=keys(params,"focus");
            Class<?> exType=safeClass("com.sony.scalar.hardware.CameraEx");
            List<String> eyeMethods=methods(exType,"eye");
            List<String> faceMethods=methods(exType,"face");
            List<String> modifierEye=modifierType==null?Collections.<String>emptyList():methods(modifierType,"eye");
            List<String> modifierFace=modifierType==null?Collections.<String>emptyList():methods(modifierType,"face");
            List<String> focusMethods=methods(exType,"focus","af-");
            List<String> faceInfo=fields(safeClass("com.sony.scalar.meta.FaceInfo"));
            List<String> faceListener=methods(safeClass("com.sony.scalar.hardware.CameraEx$FaceDetectionListener"));
            int eyeSurface=eyeKeys.size()+eyeMethods.size()+modifierEye.size();

            out.append("SONYLUT EYE PROBE 1\n");
            out.append("generated=").append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",java.util.Locale.US).format(new java.util.Date())).append('\n');
            out.append("origin=").append(origin).append('\n');
            out.append("face-requested=true\n");
            out.append("face-original=").append(originalMode).append('\n');
            out.append("face-supported=").append(supportedModes).append('\n');
            out.append("face-readback=").append(readMode()).append('\n');
            out.append("face-mode-error=").append(modeError).append('\n');
            out.append("face-rendering-requested=false\n");
            out.append("face-rendering-suppressed=capture_safety\n");
            out.append("face-mode-write=").append(modeApplied?"auto":"none_already_auto").append('\n');
            out.append("face-rendering-getter=unavailable-no-readback\n");
            out.append("eye-parameter-count=").append(eyeKeys.size()).append('\n');
            out.append("face-parameter-count=").append(faceKeys.size()).append('\n');
            out.append("focus-parameter-count=").append(focusKeys.size()).append('\n');
            out.append("eye-parameter-keys=").append(join(eyeKeys)).append('\n');
            out.append("face-parameter-keys=").append(join(faceKeys)).append('\n');
            out.append("focus-parameter-keys=").append(join(focusKeys)).append('\n');
            out.append("eye-api-methods=").append(join(eyeMethods)).append('\n');
            out.append("face-api-methods=").append(join(faceMethods)).append('\n');
            out.append("modifier-eye-methods=").append(join(modifierEye)).append('\n');
            out.append("modifier-face-methods=").append(join(modifierFace)).append('\n');
            out.append("focus-api-methods=").append(join(focusMethods)).append('\n');
            out.append("faceinfo-fields=").append(join(faceInfo)).append('\n');
            out.append("face-listener-methods=").append(join(faceListener)).append('\n');
            out.append("eye-af-verdict=").append(eyeSurface==0?"unavailable-no-interface":"unverified-no-setter-invoked").append('\n');
            out.append("eye-af-note=STILL_IMAGE_EYE_START_AF is shutter-start AF, not subject Eye AF; read-only probe never invokes hidden setters\n");
            out.append("face-note=camera-native face-priority AF only; the app requests no vendor frame overlay, registers no FaceDetectionListener and draws no box\n");
            out.append("== relevant Camera.Parameters ==\n");
            for(Map.Entry<String,String> entry:params.entrySet())out.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
            write(out.toString());
            if(log!=null)log.event("eye_probe_written","path="+report.getPath()+" eye_surface="+eyeSurface);
        }catch(Throwable error){
            if(log!=null)log.event("eye_probe_error",cause(error));
        }
    }
    private void write(String text)throws IOException {
        File dir=report.getParentFile();
        if(dir!=null&&!dir.isDirectory()&&!dir.mkdirs())throw new IOException("cannot create SONYLUT");
        FileOutputStream stream=new FileOutputStream(report);
        BufferedWriter writer=new BufferedWriter(new OutputStreamWriter(stream,"UTF-8"));
        try{writer.write(text);writer.flush();}finally{writer.close();}
    }
    private static Map<String,String> flatten(Camera.Parameters p){
        Map<String,String> result=new TreeMap<String,String>();
        for(String item:p.flatten().split(";")){
            int at=item.indexOf('=');if(at<=0)continue;
            String key=item.substring(0,at);
            String lower=key.toLowerCase(java.util.Locale.US);
            if(!lower.contains("face")&&!lower.contains("eye")&&!lower.contains("focus")&&!lower.contains("af-mode")
                &&!lower.contains("tracking")&&!lower.contains("subject"))continue;
            result.put(key,item.substring(at+1));
        }
        return result;
    }
    private static Class<?> safeClass(String name){
        try{return Class.forName(name);}
        catch(Throwable ignored){return Object.class;}
    }
    private static List<String> keys(Map<String,String> params,String term){
        List<String> result=new ArrayList<String>();
        for(String key:params.keySet())if(key.toLowerCase(java.util.Locale.US).contains(term))result.add(key);
        Collections.sort(result);return result;
    }
    private static List<String> methods(Class<?> type,String... terms){
        List<String> result=new ArrayList<String>();
        try{
            for(Method method:type.getDeclaredMethods()){
                String name=method.getName().toLowerCase(java.util.Locale.US);
                boolean match=false;
                for(String term:terms)if(name.contains(term)){match=true;break;}
                if(match)result.add(signature(method));
            }
        }catch(Throwable ignored){}
        Collections.sort(result);return result;
    }
    private static List<String> fields(Class<?> type){
        List<String> result=new ArrayList<String>();
        try{for(Field field:type.getDeclaredFields())result.add(field.getName()+":"+field.getType().getSimpleName());}
        catch(Throwable ignored){}
        Collections.sort(result);return result;
    }
    private static String signature(Method method){
        StringBuilder out=new StringBuilder(method.getReturnType().getSimpleName()).append(' ')
            .append(method.getName()).append('(');
        Class<?>[] types=method.getParameterTypes();
        for(int i=0;i<types.length;i++){if(i>0)out.append(',');out.append(types[i].getSimpleName());}
        return out.append(')').toString();
    }
    private static String join(List<String> values){
        StringBuilder out=new StringBuilder();for(String value:values){if(out.length()>0)out.append(", ");out.append(value);}return out.toString();
    }
    private static String value(Camera.Parameters p,String key){
        try{String value=p.get(key);return value==null?"":value;}catch(Throwable ignored){return "";}
    }
    private static String cause(Throwable error){
        Throwable current=error;
        while(current.getCause()!=null&&current.getCause()!=current)current=current.getCause();
        String message=current.getMessage();
        return current.getClass().getSimpleName()+(message==null?"":": "+message);
    }
}
