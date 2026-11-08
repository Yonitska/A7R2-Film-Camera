package org.hongen.sonylutprobe;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** M-mode exposure session for the A7R II; no Android types, host-testable. */
final class ManualExposure {
    interface Driver {
        Map<String,String> read() throws Exception;
        void write(Map<String,String> patch) throws Exception;
        void incrementAperture() throws Exception;
        void decrementAperture() throws Exception;
        void incrementShutterSpeed() throws Exception;
        void decrementShutterSpeed() throws Exception;
        void setIso(int iso) throws Exception;
        void setWhiteBalance(String value) throws Exception;
        void setShootingPreview(String value) throws Exception;
        void setAutoExposureLock(boolean locked) throws Exception;
    }
    interface Log { void log(String name,String detail); }

    private final Driver driver;
    private final Log log;
    private Map<String,String> original;
    private final List<Integer> isoValues=new ArrayList<Integer>();
    private final List<String> wbValues=new ArrayList<String>();
    private int isoIndex=-1,wbIndex=-1;
    static final String MODE_M="manual-exposure",MODE_A="aperture-priority",MODE_S="shutter-speed",MODE_P="program-auto";
    private boolean ready,aeLocked;private volatile boolean captureSafe;
    // Recipe finder opens without forcing M and may keep cycling through P.
    private boolean preserveScene;
    private String whiteBalance="",actualMode=MODE_M,selectedMode=MODE_M;
    private int evMin,evMax,evIndex,lastManualIso;private float evStep;
    // Written by the manual-control thread, read by the UI thread after a
    // posted refresh. The map itself is never mutated after assignment.
    private volatile Map<String,String> last;

    ManualExposure(Driver driver,Log log){this.driver=driver;this.log=log;}

    private static String required(Map<String,String> p,String key)throws IOException{
        String value=p.get(key);if(value==null)throw new IOException("missing exposure parameter: "+key);return value;
    }
    private static boolean supports(Map<String,String> p,String key,String value)throws IOException{
        return (","+required(p,key).replace(" ","")+",").contains(","+value+",");
    }
    private static Map<String,String> one(String key,String value){
        Map<String,String> patch=new LinkedHashMap<String,String>();patch.put(key,value);return patch;
    }
    private static int number(String value){
        try{return Integer.parseInt(value);}catch(RuntimeException e){return 0;}
    }
    private static String desiredShootingPreview(Map<String,String> p){
        String values=p.get("shooting-preview-values");
        if(values==null)return null;
        String list=","+values.replace(" ","")+",";
        if(list.contains(",iris_ss_iso,"))return "iris_ss_iso";
        if(list.contains(",iris_ss_iso_aeunlock,"))return "iris_ss_iso_aeunlock";
        return null;
    }
    private Map<String,String> read()throws Exception{last=driver.read();return last;}

    void open()throws Exception{open(false);}
    /** Recipe finder: keep the camera's current P/A/S/M mode, white balance and
     * exposure compensation exactly as the recipe left them. Nothing is
     * written in P/A/S; only M needs Sony's live-preview mode so the dials
     * change the finder. The LUT finder keeps using open(), which forces M. */
    void openPreservingScene()throws Exception{open(true);}
    private void open(boolean preserve)throws Exception{
        if(original!=null)throw new IllegalStateException("manual exposure already open");
        Map<String,String> p=read();
        if(!preserve&&!supports(p,"scene-mode-values","manual-exposure"))throw new IOException("M mode is unavailable");
        String mode=required(p,"scene-mode");
        Map<String,String> saved=new LinkedHashMap<String,String>();
        saved.put("scene-mode",mode);
        for(String key:new String[]{"iso-sensitivity","whitebalance","exposure-compensation","shooting-preview"})
            if(p.containsKey(key))saved.put(key,p.get(key));
        if("manual-exposure".equals(mode)||"aperture-priority".equals(mode))
            if(p.containsKey("aperture"))saved.put("aperture",p.get("aperture"));
        if("manual-exposure".equals(mode)||"shutter-speed".equals(mode))
            for(String key:new String[]{"shutter-speed-nume","shutter-speed-deno"})
                if(p.containsKey(key))saved.put(key,p.get(key));
        original=saved;
        preserveScene=preserve;

        parseIso(p);
        parseWhiteBalance(p);
        parseExposureCompensation(p);
        if(preserve){
            selectedMode=MODE_M.equals(mode)?MODE_M:MODE_A.equals(mode)?MODE_A
                    :MODE_S.equals(mode)?MODE_S:MODE_P;
            actualMode=mode;
            // Leave AUTO ISO alone: recipes do not dictate ISO.
            int iso=number(p.get("iso-sensitivity"));
            lastManualIso=iso>0?iso:400;
            isoIndex=isoValues.indexOf(Integer.valueOf(iso));
            if(isoIndex<0)isoIndex=ControlMath.nearest(isoValues,iso);
            whiteBalance=p.get("whitebalance");
            wbIndex=wbValues.indexOf(whiteBalance);
            if(MODE_M.equals(selectedMode)&&p.containsKey("shooting-preview"))try{
                String desired=desiredShootingPreview(p);
                if(desired!=null&&!desired.equals(p.get("shooting-preview"))){
                    driver.setShootingPreview(desired);
                    p=read();
                }
                log.log("manual_shooting_preview","value="+p.get("shooting-preview")+" desired="+desired
                        +" scene="+p.get("scene-mode")+" aperture="+p.get("aperture")
                        +" iso="+p.get("iso-sensitivity")+" shutter="+p.get("shutter-speed-nume")+"/"+p.get("shutter-speed-deno"));
            }catch(Throwable e){log.log("manual_shooting_preview_error",String.valueOf(e));}
            ready=true;captureSafe=false;
            log.log("manual_open_recipe","scene="+actualMode+" selected="+selectedMode+" iso="+iso
                    +" wb="+whiteBalance+" ev="+compensationLabel()+" camera_untouched="
                    +(String.valueOf(p.get("scene-mode")).equals(saved.get("scene-mode"))
                    &&String.valueOf(p.get("whitebalance")).equals(saved.get("whitebalance")))
                    +" original="+original);
            return;
        }
        selectedMode=MODE_M;
        if(!MODE_M.equals(mode)){
            driver.write(one("scene-mode",MODE_M));
            p=read();
            actualMode=p.get("scene-mode");
            if(!MODE_M.equals(actualMode))
                log.log("manual_mode_fallback","requested="+MODE_M+" actual="+actualMode);
        }else actualMode=mode;
        selectedMode=actualMode;
        // Sony may reset ISO/WB while changing exposure mode. Restore the
        // user's white balance and choose a concrete manual ISO.
        String wantedWb=saved.get("whitebalance");
        if(wantedWb!=null&&!wantedWb.equals(p.get("whitebalance")))
            driver.setWhiteBalance(wantedWb);
        if(p.containsKey("exposure-compensation")&&!"0".equals(p.get("exposure-compensation")))
            driver.write(one("exposure-compensation","0"));
        p=read();
        evIndex=evStep>0?number(p.get("exposure-compensation")):0;
        int iso=number(p.get("iso-sensitivity"));
        if(iso<=0){
            int fallback=chooseDefaultIso();
            driver.setIso(fallback);
            p=read();
            iso=number(p.get("iso-sensitivity"));
            if(iso<=0)throw new IOException("manual ISO readback failed");
        }
        isoIndex=isoValues.indexOf(Integer.valueOf(iso));
        if(isoIndex<0)isoIndex=ControlMath.nearest(isoValues,iso);
        lastManualIso=iso>0?iso:0;
        whiteBalance=p.get("whitebalance");
        wbIndex=wbValues.indexOf(whiteBalance);
        // Let the live view reflect the exposure triangle in M mode. Without
        // this Sony keeps the preview at the previous/auto exposure even
        // though the captured image uses the new settings.
        if(p.containsKey("shooting-preview"))try{
            String desired=desiredShootingPreview(p);
            if(desired!=null){
                if(!desired.equals(p.get("shooting-preview"))){
                    driver.setShootingPreview(desired);
                    p=read();
                }
                log.log("manual_shooting_preview","value="+p.get("shooting-preview")+" desired="+desired
                        +" scene="+p.get("scene-mode")+" aperture="+p.get("aperture")
                        +" iso="+p.get("iso-sensitivity")+" shutter="+p.get("shutter-speed-nume")+"/"+p.get("shutter-speed-deno"));
            }else log.log("manual_shooting_preview_unavailable","values="+p.get("shooting-preview-values"));
        }catch(Throwable e){log.log("manual_shooting_preview_error",String.valueOf(e));}
        ready=true;captureSafe=false;
        log.log("manual_open","scene="+actualMode+" iso="+iso+" iso_values="+isoValues.size()
                +" wb="+whiteBalance+" wb_values="+wbValues.size()+" original="+original);
    }

    private void parseIso(Map<String,String> p)throws IOException{
        isoValues.clear();
        String csv=p.get("iso-sensitivity-values");
        boolean auto=false;
        if(csv!=null)for(String raw:csv.split(",")){
            int value=number(raw.trim());
            if(value==0)auto=true;
            else if(value>0&&!isoValues.contains(Integer.valueOf(value)))isoValues.add(Integer.valueOf(value));
        }
        if(auto)isoValues.add(0,Integer.valueOf(0));
        if(isoValues.isEmpty())throw new IOException("manual ISO is unavailable");
    }
    private void parseWhiteBalance(Map<String,String> p){
        wbValues.clear();wbValues.addAll(WhiteBalanceNames.cycleValues(p.get("whitebalance-values")));
    }
    private void parseExposureCompensation(Map<String,String> p){
        evMin=number(p.get("min-exposure-compensation"));evMax=number(p.get("max-exposure-compensation"));
        try{evStep=Float.parseFloat(p.get("exposure-compensation-step"));}
        catch(RuntimeException e){evStep=0;}
        if(evMin>0||evMax<0||!(evStep>0)||Float.isInfinite(evStep)){evMin=evMax=0;evStep=0;}
        evIndex=evStep>0?Math.max(evMin,Math.min(evMax,number(p.get("exposure-compensation")))):0;
    }
    private int chooseDefaultIso(){
        for(int value:new int[]{400,200,800,100})if(isoValues.contains(Integer.valueOf(value)))return value;
        for(int value:isoValues)if(value>=100)return value;
        return isoValues.get(0);
    }

    /** Re-assert the live exposure preview after RAW/CameraSequence registration. */
    void enableExposurePreview()throws Exception{
        if(!ready)return;
        if(aeLocked){
            try{driver.setAutoExposureLock(false);aeLocked=false;}
            catch(Throwable e){log.log("manual_ae_unlock_error",String.valueOf(e));}
        }
        Map<String,String> p=read();
        String desired=desiredShootingPreview(p);
        if(desired==null)return;
        if(!desired.equals(p.get("shooting-preview"))){
            driver.setShootingPreview(desired);
            p=read();
            log.log("manual_shooting_preview_reapplied","value="+p.get("shooting-preview")+" desired="+desired);
        }
        captureSafe=false;
    }
    /** Re-read parameters changed outside this session (a Recipe Lab look
     * writes White Balance and exposure compensation directly). Without this
     * the next prepareCapture() would restore the pre-recipe values. */
    void refresh()throws Exception{
        if(!ready)return;
        captureSafe=false;
        Map<String,String> p=read();
        String scene=p.get("scene-mode");
        if(MODE_M.equals(scene)||MODE_A.equals(scene)||MODE_S.equals(scene)||MODE_P.equals(scene))
            selectedMode=scene;
        actualMode=scene;
        parseIso(p);
        parseWhiteBalance(p);
        parseExposureCompensation(p);
        int iso=number(p.get("iso-sensitivity"));
        isoIndex=isoValues.indexOf(Integer.valueOf(iso));
        if(isoIndex<0)isoIndex=ControlMath.nearest(isoValues,iso);
        if(iso>0)lastManualIso=iso;
        whiteBalance=p.get("whitebalance");
        wbIndex=wbValues.indexOf(whiteBalance);
        if(evStep>0)evIndex=Math.max(evMin,Math.min(evMax,number(p.get("exposure-compensation"))));
        else evIndex=0;
        log.log("manual_refresh","scene="+scene+" iso="+iso+" wb="+whiteBalance+" ev="+compensationLabel()+" state="+state());
    }
    /** Put the camera into a deterministic state before a shutter press: the
     * preview display mode can unlock AE, and any mode/parameter path that
     * reset the settings must not leak into the captured exposure.
     * M keeps its manual pair and locks AE; A/S keep their user parameter and
     * EV but leave the other parameter to the camera's meter. */
    void prepareCapture()throws Exception{
        requireReady();
        String mode=selectedMode;
        Map<String,String> p=read();
        String before="mode="+mode+" scene="+p.get("scene-mode")+" preview="+p.get("shooting-preview")
                +" iso="+p.get("iso-sensitivity")+" aperture="+p.get("aperture")
                +" shutter="+p.get("shutter-speed-nume")+"/"+p.get("shutter-speed-deno")
                +" ev="+p.get("exposure-compensation");
        String savedPreview=original==null?null:original.get("shooting-preview");
        if(savedPreview!=null&&!savedPreview.equals(p.get("shooting-preview"))){
            driver.setShootingPreview(savedPreview);
            p=read();
        }
        if(!mode.equals(p.get("scene-mode"))){
            driver.write(one("scene-mode",mode));
            p=read();
        }
        Map<String,String> wanted=new LinkedHashMap<String,String>();
        if(last!=null){
            boolean keepIso=MODE_M.equals(mode)||MODE_P.equals(mode);
            if(keepIso&&last.containsKey("iso-sensitivity"))wanted.put("iso-sensitivity",last.get("iso-sensitivity"));
            else wanted.put("iso-sensitivity","0"); // A/S always auto ISO
            if(last.containsKey("whitebalance"))wanted.put("whitebalance",last.get("whitebalance"));
            if((MODE_M.equals(mode)||MODE_A.equals(mode))&&last.containsKey("aperture"))
                wanted.put("aperture",last.get("aperture"));
            if(MODE_M.equals(mode)||MODE_S.equals(mode))
                for(String key:new String[]{"shutter-speed-nume","shutter-speed-deno"})
                    if(last.containsKey(key))wanted.put(key,last.get(key));
        }
        if(MODE_M.equals(mode))wanted.put("exposure-compensation","0");
        else if(evStep>0)wanted.put("exposure-compensation",Integer.toString(evIndex));
        if(!wanted.isEmpty())driver.write(wanted);
        p=read();
        String iso=wanted.get("iso-sensitivity");
        if(iso!=null&&!iso.equals(p.get("iso-sensitivity"))){
            driver.setIso(number(iso));p=read();
        }
        String ae="n/a";
        if(MODE_M.equals(mode)&&number(iso)>0){
            // The preview mode is literally "...aeunlock". Freeze metering at
            // the exposure the user dialed so the shutter cannot re-derive
            // F9/ISO16000. ISO AUTO must stay free for the camera to expose.
            ae="error";
            try{driver.setAutoExposureLock(true);aeLocked=true;p=read();ae="locked";}
            catch(Throwable e){log.log("manual_ae_lock_error",String.valueOf(e));}
        }else if(aeLocked){
            try{driver.setAutoExposureLock(false);aeLocked=false;ae="released";}
            catch(Throwable e){log.log("manual_ae_unlock_error",String.valueOf(e));}
        }
        captureSafe=true;
        log.log("manual_capture_guard","mode="+mode+" scene="+p.get("scene-mode")+" iso="+p.get("iso-sensitivity")
                +" aperture="+p.get("aperture")+" shutter="+p.get("shutter-speed-nume")+"/"+p.get("shutter-speed-deno")
                +" preview="+p.get("shooting-preview")+" wb="+p.get("whitebalance")+" ev="+p.get("exposure-compensation")
                +" ae="+ae+" ae_readback="+p.get("ae-lock")+" wanted="+wanted
                +" before=["+before+"]");
        if(!mode.equals(p.get("scene-mode")))
            throw new IOException("capture guard could not restore "+mode);
    }
    boolean captureSafe(){return captureSafe;}
    /** Leave capture-safe mode without forcing the preview display back on;
     * the next dial step or a fresh half-press decides that. */
    void releaseCaptureGuard(){captureSafe=false;}
    String captureState()throws Exception{
        Map<String,String> p=read();
        return "mode="+selectedMode+" scene="+p.get("scene-mode")+" iso="+p.get("iso-sensitivity")
                +" aperture="+p.get("aperture")+" shutter="+p.get("shutter-speed-nume")+"/"+p.get("shutter-speed-deno")
                +" preview="+p.get("shooting-preview")+" ev="+compensationLabel()+" ae="+p.get("ae-lock")
                +" safe="+captureSafe;
    }
    String mode(){return selectedMode;}
    String modeLabel(){return label();}
    String compensationLabel(){
        return evStep>0?String.format(Locale.US,"%+.1f EV",evIndex*evStep):"EV --";
    }
    /** C1 order in the LUT finder: M -> A -> S -> M. The recipe finder keeps
     * the camera's own mode and adds P at the end: M -> A -> S -> P -> M.
     * Reassert the user's manual ISO/WB and the live preview after Sony
     * applies the new mode. */
    void cycleMode()throws Exception{
        if(MODE_M.equals(selectedMode))switchMode(MODE_A);
        else if(MODE_A.equals(selectedMode))switchMode(MODE_S);
        else if(MODE_S.equals(selectedMode))switchMode(preserveScene?MODE_P:MODE_M);
        else switchMode(MODE_M);
    }
    void selectMode(String requested)throws Exception{
        String mode=MODE_M.equals(requested)?MODE_M:MODE_A.equals(requested)?MODE_A
                :MODE_S.equals(requested)?MODE_S:MODE_P.equals(requested)?MODE_P:null;
        if(mode==null)throw new IOException("unknown exposure mode "+requested);
        switchMode(mode);
    }
    private void switchMode(String next)throws Exception{
        requireReady();
        captureSafe=false;
        Map<String,String> p=read();
        int currentIso=number(p.get("iso-sensitivity"));
        if(MODE_M.equals(selectedMode))lastManualIso=currentIso;
        if(!next.equals(p.get("scene-mode"))){
            driver.write(one("scene-mode",next));
            p=read();
        }
        if(!next.equals(p.get("scene-mode")))
            throw new IOException("mode switch failed: "+next+" actual="+p.get("scene-mode"));
        selectedMode=next;actualMode=next;
        if(last!=null&&last.containsKey("whitebalance")){
            driver.write(one("whitebalance",last.get("whitebalance")));
            p=read();
        }
        // S/A leave ISO and the other exposure parameter to the camera. P
        // keeps whatever ISO Sony picked so program auto stays automatic.
        if(MODE_M.equals(next)){
            if(number(p.get("iso-sensitivity"))!=lastManualIso){driver.setIso(lastManualIso);p=read();}
        }else if(MODE_A.equals(next)||MODE_S.equals(next)){
            if(number(p.get("iso-sensitivity"))!=0){driver.setIso(0);p=read();}
        }
        try{enableExposurePreview();}catch(Throwable e){log.log("manual_mode_preview_error",String.valueOf(e));}
        p=read();
        if(!selectedMode.equals(p.get("scene-mode"))){
            driver.write(one("scene-mode",selectedMode));
            p=read();
        }
        int iso=number(p.get("iso-sensitivity"));
        isoIndex=isoValues.indexOf(Integer.valueOf(iso));
        if(isoIndex<0)isoIndex=ControlMath.nearest(isoValues,iso);
        if(iso>0)lastManualIso=iso;
        if(evStep>0)evIndex=Math.max(evMin,Math.min(evMax,number(p.get("exposure-compensation"))));
        log.log("manual_mode_cycle","mode="+selectedMode+" scene="+p.get("scene-mode")
                +" iso="+p.get("iso-sensitivity")+" aperture="+p.get("aperture")
                +" shutter="+p.get("shutter-speed-nume")+"/"+p.get("shutter-speed-deno")
                +" ev="+compensationLabel());
    }
    /** Route a dial burst by mode and use one final camera read. In A/S the
     * control wheel (the gain wheel) changes the one manual exposure value and
     * ISO stays AUTO; the front/rear dials give exposure compensation. */
    boolean dial(int front,int rear,int control)throws Exception{
        requireReady();
        if(front==0&&rear==0&&control==0)return false;
        Map<String,String> before=last;if(before==null)before=read();
        int isoBefore=number(before.get("iso-sensitivity"));
        int apertureBefore=number(before.get("aperture"));
        int shutterNBefore=number(before.get("shutter-speed-nume"));
        int shutterDBefore=number(before.get("shutter-speed-deno"));
        int evBefore=number(before.get("exposure-compensation"));
        boolean changed=false;
            if(MODE_M.equals(selectedMode)){
                if(front!=0){for(int i=0;i<Math.abs(front);i++){if(front>0)driver.incrementAperture();else driver.decrementAperture();}changed=true;}
                if(rear!=0){for(int i=0;i<Math.abs(rear);i++){if(rear>0)driver.incrementShutterSpeed();else driver.decrementShutterSpeed();}changed=true;}
                if(control!=0){
                    int value=isoTarget(isoBefore,control);
                    if(value!=isoBefore){driver.setIso(value);lastManualIso=value;changed=true;}
                }
            }else{
                if(control!=0){
                    if(MODE_P.equals(selectedMode)){
                        // Program auto: the gain wheel takes over ISO, exactly
                        // like M; program shift itself is not exposed by
                        // CameraEx.
                        int value=isoTarget(isoBefore,control);
                        if(value!=isoBefore){driver.setIso(value);lastManualIso=value;changed=true;}
                    }else{
                        if(MODE_A.equals(selectedMode))for(int i=0;i<Math.abs(control);i++){if(control>0)driver.incrementAperture();else driver.decrementAperture();}
                        else for(int i=0;i<Math.abs(control);i++){if(control>0)driver.incrementShutterSpeed();else driver.decrementShutterSpeed();}
                        changed=true;
                    }
                }
                int evDelta=front+rear;
                if(evDelta!=0&&evStep>0){
                    int target=Math.max(evMin,Math.min(evMax,evBefore+evDelta));
                    if(target!=evBefore){driver.write(one("exposure-compensation",Integer.toString(target)));evIndex=target;changed=true;}
                }
            }
            Map<String,String> after=read();
            int isoAfter=number(after.get("iso-sensitivity"));
            isoIndex=isoValues.indexOf(Integer.valueOf(isoAfter));
            if(isoIndex<0)isoIndex=ControlMath.nearest(isoValues,isoAfter);
            log.log("manual_dial","mode="+selectedMode+" front="+front+" rear="+rear+" turn="+control
                    +" iso="+isoBefore+"->"+isoAfter+" aperture="+apertureBefore+"->"+after.get("aperture")
                    +" shutter="+shutterNBefore+"/"+shutterDBefore+"->"+after.get("shutter-speed-nume")+"/"+after.get("shutter-speed-deno")
                    +" ev="+evBefore+"->"+after.get("exposure-compensation"));
            return changed;
    }
    private int isoTarget(int current,int steps){
        int index=isoValues.indexOf(Integer.valueOf(current));
        if(index<0)index=ControlMath.nearest(isoValues,current>0?current:0);
        int next=Math.max(0,Math.min(isoValues.size()-1,index+steps));
        return isoValues.get(next).intValue();
    }
    boolean evSteps(int steps)throws Exception{
        requireReady();
        if(steps==0)return false;
        if(evStep<=0)throw new IOException("exposure compensation is unavailable");
        int current=number(read().get("exposure-compensation"));
        int target=Math.max(evMin,Math.min(evMax,current+steps));
        if(target!=current){
            driver.write(one("exposure-compensation",Integer.toString(target)));
            int actual=number(read().get("exposure-compensation"));
            if(actual!=target)throw new IOException("EV readback "+actual+" expected "+target);
            evIndex=target;
        }else evIndex=current;
        log.log("manual_ev","steps="+steps+" index="+evIndex+" label="+compensationLabel());
        return target!=current;
    }
    boolean aperture(int direction)throws Exception{return apertureSteps(direction);}
    /** Apply a whole dial burst in one camera transaction/readback pair. */
    boolean apertureSteps(int steps)throws Exception{
        requireReady();
        if(steps==0)return false;
        Map<String,String> before=last;if(before==null)before=read();
        int beforeValue=number(before.get("aperture")),count=steps;
        while(steps>0){driver.incrementAperture();steps--;}
        while(steps<0){driver.decrementAperture();steps++;}
        int after=number(read().get("aperture"));
        log.log("manual_aperture","steps="+count+" before="+beforeValue+" after="+after);
        return after!=beforeValue;
    }
    boolean shutter(int direction)throws Exception{return shutterSteps(direction);}
    boolean shutterSteps(int steps)throws Exception{
        requireReady();
        if(steps==0)return false;
        Map<String,String> before=last;if(before==null)before=read();
        String b=before.get("shutter-speed-nume")+"/"+before.get("shutter-speed-deno");int count=steps;
        while(steps>0){driver.incrementShutterSpeed();steps--;}
        while(steps<0){driver.decrementShutterSpeed();steps++;}
        Map<String,String> after=read();
        String a=after.get("shutter-speed-nume")+"/"+after.get("shutter-speed-deno");
        log.log("manual_shutter","steps="+count+" before="+b+" after="+a);
        return !a.equals(b);
    }
    boolean iso(int direction)throws Exception{return isoSteps(direction);}
    boolean isoSteps(int steps)throws Exception{
        requireReady();
        if(steps==0)return false;
        Map<String,String> before=last;if(before==null)before=read();
        int current=number(before.get("iso-sensitivity"));
        int index=isoValues.indexOf(Integer.valueOf(current));
        if(index<0)index=ControlMath.nearest(isoValues,current>0?current:400);
        int next=Math.max(0,Math.min(isoValues.size()-1,index+steps));
        int value=isoValues.get(next);
        driver.setIso(value);
        int readback=number(read().get("iso-sensitivity"));
        if(readback!=value)throw new IOException("ISO readback "+readback+" expected "+value);
        isoIndex=next;
        log.log("manual_iso","steps="+steps+" value="+value+" index="+next+"/"+isoValues.size());
        return true;
    }
    boolean whiteBalance(int direction)throws Exception{
        requireReady();
        if(wbValues.isEmpty())throw new IOException("white balance presets are unavailable");
        String current=read().get("whitebalance");
        int index=wbValues.indexOf(current);
        if(index<0)index=0;
        int next=direction==0?index:ControlMath.step(index,wbValues.size(),direction);
        String value=wbValues.get(next);
        driver.setWhiteBalance(value);
        String readback=read().get("whitebalance");
        if(!value.equals(readback))throw new IOException("WB readback "+readback+" expected "+value);
        wbIndex=next;whiteBalance=value;
        log.log("manual_wb","dir="+direction+" value="+value+" index="+next+"/"+wbValues.size());
        return true;
    }

    void update(CaptureReadout readout){
        if(!ready)return;
        try{
            Map<String,String> p=last;
            if(p==null)p=read();
            readout.manual=true;
            readout.aperture=number(p.get("aperture"));
            readout.shutterNumerator=number(p.get("shutter-speed-nume"));
            readout.shutterDenominator=number(p.get("shutter-speed-deno"));
            readout.autoIso=0;
            updateManualFields(readout);
        }catch(Exception e){log.log("manual_readout_error",String.valueOf(e));}
    }
    /** Merge only fields telemetry cannot supply; keep the camera's fresh
     * measured shutter/aperture in A/S modes. */
    void updateManualFields(CaptureReadout readout){
        if(!ready)return;
        Map<String,String> p=last;
        if(p==null)return;
        readout.manual=true;
        readout.manualIso=number(p.get("iso-sensitivity"));
        readout.isoAuto=readout.manualIso<=0;
        whiteBalance=p.get("whitebalance");readout.whiteBalance=whiteBalance;
        readout.manualMode=label();
        readout.exposureEvValid=evStep>0;
        readout.exposureEv=evStep>0?evIndex*evStep:0;
    }

    String label(){
        if("manual-exposure".equals(actualMode))return "M";
        if("aperture-priority".equals(actualMode))return "A";
        if("shutter-speed".equals(actualMode))return "S";
        if("program-auto".equals(actualMode))return "P";
        return actualMode==null?"M":actualMode;
    }
    String whiteBalance(){return whiteBalance;}
    List<String> whiteBalanceValues(){return new ArrayList<String>(wbValues);}
    void setWhiteBalanceValue(String value)throws Exception{
        requireReady();
        if(!wbValues.contains(value))throw new IOException("unsupported white balance "+value);
        driver.setWhiteBalance(value);
        Map<String,String> p=read();
        String actual=p.get("whitebalance");
        if(!value.equals(actual))throw new IOException("WB readback "+actual+" expected "+value);
        whiteBalance=value;wbIndex=wbValues.indexOf(value);
        log.log("manual_wb_set","value="+value+" index="+wbIndex+"/"+wbValues.size());
    }
    int isoIndex(){return isoIndex;}
    List<Integer> isoValues(){return isoValues;}
    boolean ready(){return ready;}

    void close()throws Exception{
        ready=false;
        captureSafe=false;
        preserveScene=false;
        if(aeLocked){
            aeLocked=false;
            try{driver.setAutoExposureLock(false);}
            catch(Throwable e){log.log("manual_ae_unlock_error",String.valueOf(e));}
        }
        if(original==null)return;
        Map<String,String> saved=original;original=null;
        String originalMode=saved.get("scene-mode");
        Map<String,String> current=read();
        if(originalMode!=null&&!originalMode.equals(current.get("scene-mode")))
            driver.write(one("scene-mode",originalMode));
        Map<String,String> restore=new LinkedHashMap<String,String>(saved);
        restore.remove("scene-mode");
        // shooting-preview must go back through the same Sony modifier used
        // to set it; a bare Camera.Parameters key is ignored on the A7R II.
        String restorePreview=restore.remove("shooting-preview");
        if(!restore.isEmpty())driver.write(restore);
        if(restorePreview!=null)driver.setShootingPreview(restorePreview);
        Map<String,String> readback=read();
        StringBuilder mismatches=new StringBuilder();
        for(Map.Entry<String,String> e:saved.entrySet())
            if(!e.getValue().equals(readback.get(e.getKey())))
                mismatches.append(e.getKey()).append('=').append(readback.get(e.getKey())).append(' ');
        if(mismatches.length()>0)throw new IOException("manual restore differs: "+mismatches.toString().trim());
        log.log("manual_close","scene="+readback.get("scene-mode")+" iso="+readback.get("iso-sensitivity")
                +" wb="+readback.get("whitebalance"));
    }

    private void requireReady(){
        if(!ready)throw new IllegalStateException("manual exposure not ready");
    }
    String state(){
        return String.format(Locale.US,"mode=%s scene=%s iso_index=%d/%d wb_index=%d/%d ev=%s ready=%s",
                selectedMode,actualMode,isoIndex,isoValues.size(),wbIndex,wbValues.size(),compensationLabel(),ready);
    }
}
