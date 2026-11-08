package org.hongen.sonylutprobe;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Movie-mode exposure session: P/A/S/M, AUTO ISO, EV, WB and the gain wheel.
 *
 * The photo path ({@link ManualExposure}) always works from M and performs
 * stills-specific capture guards. Movie mode instead adopts whatever mode the
 * camera is in and needs P as a first-class state, so it uses this smaller
 * session over the same CameraEx driver.
 */
final class VideoExposure {
    interface Log { void log(String name,String detail); }

    static final String P="program-auto",A="aperture-priority",S="shutter-speed",M="manual-exposure";

    private final ManualExposure.Driver driver;
    private final Log log;
    private final List<Integer> isoValues=new ArrayList<Integer>();
    private final List<String> wbValues=new ArrayList<String>();
    private final List<String> modes=new ArrayList<String>();
    private int evMin,evMax,evIndex,isoIndex=-1,wbIndex=-1,lastManualIso;
    private float evStep;
    private String mode=A,actualMode=A,whiteBalance="";
    private boolean ready;

    private volatile Map<String,String> last;

    VideoExposure(ManualExposure.Driver driver,Log log){this.driver=driver;this.log=log;}

    private static Map<String,String> one(String key,String value){
        Map<String,String> patch=new LinkedHashMap<String,String>();patch.put(key,value);return patch;
    }
    private static int number(String value){
        try{return Integer.parseInt(value);}catch(RuntimeException e){return 0;}
    }
    private Map<String,String> read()throws Exception{last=driver.read();return last;}
    private void requireReady()throws IOException{if(!ready)throw new IOException("video exposure is not open");}

    void open(String requested)throws Exception{
        Map<String,String> p=read();
        parseModes(p);
        parseIso(p);
        parseWhiteBalance(p);
        parseExposureCompensation(p);
        String wanted=normalizeMode(requested,p);
        if(!wanted.equals(p.get("scene-mode"))){
            driver.write(one("scene-mode",wanted));
            p=read();
        }
        actualMode=p.get("scene-mode");
        if(wanted.equals(actualMode))mode=wanted;
        else{
            mode=normalizeMode(actualMode,p);
            log.log("video_mode_fallback","requested="+wanted+" actual="+actualMode+" using="+mode);
        }
        // P/A/S leave ISO to the meter; M keeps a concrete starting value that
        // the gain wheel can move through AUTO and back.
        if(!M.equals(mode)&&number(p.get("iso-sensitivity"))!=0){
            try{driver.setIso(0);}catch(Throwable e){log.log("video_iso_auto_error",String.valueOf(e));}
            p=read();
        }
        int iso=number(p.get("iso-sensitivity"));
        lastManualIso=iso>0?iso:chooseDefaultIso();
        isoIndex=isoValues.indexOf(Integer.valueOf(iso));
        if(isoIndex<0)isoIndex=ControlMath.nearest(isoValues,iso);
        whiteBalance=p.get("whitebalance");
        wbIndex=wbValues.indexOf(whiteBalance);
        if(evStep>0)evIndex=Math.max(evMin,Math.min(evMax,number(p.get("exposure-compensation"))));
        ready=true;
        log.log("video_exposure_open","mode="+mode+" scene="+p.get("scene-mode")
                +" iso="+p.get("iso-sensitivity")+" iso_values="+isoValues.size()
                +" wb="+whiteBalance+" wb_values="+wbValues.size()+" ev="+evLabel()
                +" modes="+modes);
    }

    private void parseModes(Map<String,String> p){
        modes.clear();
        String csv=p.get("scene-mode-values");
        if(csv!=null)for(String raw:csv.split(",")){
            String value=raw.trim();
            if(P.equals(value)||A.equals(value)||S.equals(value)||M.equals(value))modes.add(value);
        }
        if(modes.isEmpty())modes.add(A);
    }
    private void parseIso(Map<String,String> p){
        isoValues.clear();
        String csv=p.get("iso-sensitivity-values");
        boolean auto=false;
        if(csv!=null)for(String raw:csv.split(",")){
            int value=number(raw.trim());
            if(value==0)auto=true;
            else if(value>0&&!isoValues.contains(Integer.valueOf(value)))isoValues.add(Integer.valueOf(value));
        }
        if(auto)isoValues.add(0,Integer.valueOf(0));
        if(isoValues.isEmpty())isoValues.add(Integer.valueOf(0));
    }
    private void parseWhiteBalance(Map<String,String> p){
        wbValues.clear();
        wbValues.addAll(WhiteBalanceNames.cycleValues(p.get("whitebalance-values")));
    }
    private void parseExposureCompensation(Map<String,String> p){
        evMin=number(p.get("min-exposure-compensation"));
        evMax=number(p.get("max-exposure-compensation"));
        try{evStep=Float.parseFloat(p.get("exposure-compensation-step"));}
        catch(RuntimeException e){evStep=0;}
        if(evMin>0||evMax<0||!(evStep>0)||Float.isInfinite(evStep)){evMin=evMax=0;evStep=0;}
        evIndex=evStep>0?Math.max(evMin,Math.min(evMax,number(p.get("exposure-compensation")))):0;
    }
    private int chooseDefaultIso(){
        for(int value:new int[]{400,200,800,100})if(isoValues.contains(Integer.valueOf(value)))return value;
        for(int value:isoValues)if(value>=100)return value;
        return 0;
    }
    private String normalizeMode(String value,Map<String,String> p){
        for(String candidate:new String[]{P,A,S,M})
            if(candidate.equals(value))return value;
        if(value==null)return A;
        // Some bodies report symbolic aliases; map the common ones.
        String lower=value.toLowerCase(Locale.US);
        if(lower.contains("program"))return P;
        if(lower.contains("aperture"))return A;
        if(lower.contains("shutter"))return S;
        if(lower.contains("manual"))return M;
        return A;
    }

    /** Re-read the camera after an external writer (a Recipe Lab recipe)
     * changed EV, WB or DRO. Without this, the next mode/dial commit would
     * write the stale pre-recipe values back and silently undo the look. */
    void refresh()throws Exception{
        if(!ready)return;
        Map<String,String> p=read();
        actualMode=p.get("scene-mode");
        if(M.equals(actualMode)||A.equals(actualMode)||S.equals(actualMode)||P.equals(actualMode))mode=actualMode;
        parseIso(p);parseWhiteBalance(p);parseExposureCompensation(p);
        int iso=number(p.get("iso-sensitivity"));
        if(iso>0)lastManualIso=iso;
        isoIndex=isoValues.indexOf(Integer.valueOf(iso));
        if(isoIndex<0)isoIndex=ControlMath.nearest(isoValues,iso);
        whiteBalance=p.get("whitebalance");
        wbIndex=wbValues.indexOf(whiteBalance);
        log.log("video_exposure_refresh","mode="+mode+" scene="+actualMode+" iso="+iso
                +" wb="+whiteBalance+" ev="+evLabel());
    }

    boolean setMode(String requested)throws Exception{
        requireReady();
        Map<String,String> p=read();
        String next=normalizeMode(requested,p);
        if(!modes.contains(next))throw new IOException("exposure mode unavailable: "+next);
        if(next.equals(mode))return false;
        if(M.equals(mode)){
            int iso=number(p.get("iso-sensitivity"));
            if(iso>0)lastManualIso=iso;
        }
        driver.write(one("scene-mode",next));
        p=read();
        if(!next.equals(p.get("scene-mode")))
            throw new IOException("video mode switch failed: "+next+" actual="+p.get("scene-mode"));
        mode=next;actualMode=next;
        int wantedIso=M.equals(next)?lastManualIso:0;
        if(number(p.get("iso-sensitivity"))!=wantedIso)
            try{driver.setIso(wantedIso);}catch(Throwable e){log.log("video_mode_iso_error",String.valueOf(e));}
        p=read();
        // Sony may reset WB along with the exposure mode. A WB readback
        // failure must not undo an otherwise valid mode switch.
        if(whiteBalance.length()>0&&!whiteBalance.equals(p.get("whitebalance"))){
            try{driver.setWhiteBalance(whiteBalance);}catch(Throwable e){log.log("video_mode_wb_error",String.valueOf(e));}
            p=read();
        }
        if(M.equals(mode)&&evStep>0&&number(p.get("exposure-compensation"))!=0){
            try{driver.write(one("exposure-compensation","0"));}catch(Throwable e){log.log("video_mode_ev_error",String.valueOf(e));}
            p=read();
        }
        if(evStep>0)evIndex=Math.max(evMin,Math.min(evMax,number(p.get("exposure-compensation"))));
        isoIndex=isoValues.indexOf(Integer.valueOf(number(p.get("iso-sensitivity"))));
        if(isoIndex<0)isoIndex=ControlMath.nearest(isoValues,number(p.get("iso-sensitivity")));
        log.log("video_mode_switch","mode="+mode+" scene="+p.get("scene-mode")
                +" iso="+p.get("iso-sensitivity")+" aperture="+p.get("aperture")
                +" shutter="+p.get("shutter-speed-nume")+"/"+p.get("shutter-speed-deno")
                +" wb="+p.get("whitebalance")+" ev="+evLabel());
        return true;
    }

    /** Front/rear dials and the gain (control) wheel. M: front aperture, rear
     * shutter, gain ISO. A: gain aperture, dials EV. S: gain shutter, dials EV.
     * P: gain ISO, dials EV. P/A/S never take manual ISO from a mode switch. */
    boolean dial(int front,int rear,int control)throws Exception{
        requireReady();
        if(front==0&&rear==0&&control==0)return false;
        Map<String,String> before=last;if(before==null)before=read();
        int isoBefore=number(before.get("iso-sensitivity"));
        String apertureBefore=before.get("aperture");
        String shutterBefore=shutter(before);
        int evBefore=number(before.get("exposure-compensation"));
        boolean changed=false;
        int evDelta=front+rear;
        if(evDelta!=0&&evStep>0&&!M.equals(mode)){
            int target=Math.max(evMin,Math.min(evMax,evBefore+evDelta));
            if(target!=evBefore){
                driver.write(one("exposure-compensation",Integer.toString(target)));
                evIndex=target;changed=true;
            }
        }
        if(control!=0){
            if(M.equals(mode)||P.equals(mode)){
                int value=isoTarget(isoBefore,control);
                if(value!=isoBefore){
                    driver.setIso(value);
                    if(value>0)lastManualIso=value;
                    changed=true;
                }
            }else if(A.equals(mode)){steps(true,control);changed=true;}
            else if(S.equals(mode)){steps(false,control);changed=true;}
        }
        if(M.equals(mode)){
            if(front!=0){steps(true,front);changed=true;}
            if(rear!=0){steps(false,rear);changed=true;}
        }
        Map<String,String> after=read();
        int isoAfter=number(after.get("iso-sensitivity"));
        isoIndex=isoValues.indexOf(Integer.valueOf(isoAfter));
        if(isoIndex<0)isoIndex=ControlMath.nearest(isoValues,isoAfter);
        if(evStep>0)evIndex=Math.max(evMin,Math.min(evMax,number(after.get("exposure-compensation"))));
        log.log("video_dial","mode="+mode+" front="+front+" rear="+rear+" turn="+control
                +" iso="+isoBefore+"->"+isoAfter+" aperture="+apertureBefore+"->"+after.get("aperture")
                +" shutter="+shutterBefore+"->"+shutter(after)
                +" ev="+evBefore+"->"+after.get("exposure-compensation"));
        return changed;
    }
    private void steps(boolean aperture,int count)throws Exception{
        while(count>0){if(aperture)driver.incrementAperture();else driver.incrementShutterSpeed();count--;}
        while(count<0){if(aperture)driver.decrementAperture();else driver.decrementShutterSpeed();count++;}
    }
    private int isoTarget(int current,int steps){
        int index=isoValues.indexOf(Integer.valueOf(current));
        if(index<0)index=ControlMath.nearest(isoValues,current>0?current:0);
        int next=Math.max(0,Math.min(isoValues.size()-1,index+steps));
        return isoValues.get(next).intValue();
    }
    private static String shutter(Map<String,String> p){
        return p.get("shutter-speed-nume")+"/"+p.get("shutter-speed-deno");
    }

    boolean setWhiteBalance(String value)throws Exception{
        requireReady();
        if(!wbValues.contains(value))throw new IOException("unsupported white balance "+value);
        driver.setWhiteBalance(value);
        Map<String,String> p=read();
        String actual=p.get("whitebalance");
        if(!value.equals(actual))throw new IOException("WB readback "+actual+" expected "+value);
        whiteBalance=value;wbIndex=wbValues.indexOf(value);
        log.log("video_wb_set","value="+value+" index="+wbIndex+"/"+wbValues.size());
        return true;
    }
    boolean setEvIndex(int index)throws Exception{
        requireReady();
        if(evStep<=0)throw new IOException("exposure compensation is unavailable");
        int target=Math.max(evMin,Math.min(evMax,index));
        int current=number(read().get("exposure-compensation"));
        if(target==current){evIndex=target;return false;}
        driver.write(one("exposure-compensation",Integer.toString(target)));
        Map<String,String> p=read();
        int actual=number(p.get("exposure-compensation"));
        if(actual!=target)throw new IOException("EV readback "+actual+" expected "+target);
        evIndex=target;
        log.log("video_ev_set","index="+target+" label="+evLabel());
        return true;
    }
    boolean evAvailable(){return evStep>0;}
    int evMin(){return evMin;}
    int evMax(){return evMax;}
    int evIndex(){return evIndex;}
    float evStep(){return evStep;}
    String evLabel(){return evStep>0?String.format(Locale.US,"%+.1f EV",evIndex*evStep):"EV --";}
    static String evLabel(int index,float step){
        return step>0?String.format(Locale.US,"%+.1f EV",index*step):"EV --";
    }
    List<String> availableModes(){return new ArrayList<String>(modes);}
    List<String> whiteBalanceValues(){return new ArrayList<String>(wbValues);}
    String whiteBalance(){return whiteBalance;}
    String mode(){return mode;}
    String modeLabel(){
        if(P.equals(mode))return "P";
        if(A.equals(mode))return "A";
        if(S.equals(mode))return "S";
        if(M.equals(mode))return "M";
        return mode;
    }
    static String modeLabel(String mode){
        if(P.equals(mode))return "P 自动";
        if(A.equals(mode))return "A 光圈优先";
        if(S.equals(mode))return "S 快门优先";
        if(M.equals(mode))return "M 手动";
        return mode==null?"":mode;
    }
    /** Snapshot for the HUD without another camera round-trip. The last map
     * is refreshed by every dial/mode/WB/EV commit. */
    CaptureReadout readout(){
        CaptureReadout r=new CaptureReadout();
        if(!ready)return r;
        Map<String,String> p=last;if(p==null)return r;
        r.shutterNumerator=number(p.get("shutter-speed-nume"));
        r.shutterDenominator=number(p.get("shutter-speed-deno"));
        r.aperture=number(p.get("aperture"));
        r.autoIso=0;
        update(r);
        return r;
    }
    void update(CaptureReadout readout){
        if(!ready)return;
        Map<String,String> p=last;
        if(p==null)return;
        readout.manual=true;
        readout.manualMode=modeLabel();
        readout.manualIso=number(p.get("iso-sensitivity"));
        readout.isoAuto=readout.manualIso<=0;
        readout.whiteBalance=p.get("whitebalance");
        readout.exposureEvValid=evStep>0;
        readout.exposureEv=evStep>0?evIndex*evStep:0;
    }
    String state()throws Exception{
        Map<String,String> p=last;if(p==null)p=read();
        return "mode="+mode+" scene="+p.get("scene-mode")+" iso="+p.get("iso-sensitivity")
                +" aperture="+p.get("aperture")+" shutter="+shutter(p)
                +" wb="+p.get("whitebalance")+" ev="+evLabel()+" af="+p.get("af-mode");
    }
}
