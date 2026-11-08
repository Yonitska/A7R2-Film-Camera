package org.hongen.sonylutprobe;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Host test for the M-mode/AF control state machines. */
public class ManualExposureTest {
    static final class Fake implements ManualExposure.Driver,AfPointController.Driver {
        final Map<String,String> p=new LinkedHashMap<String,String>();
        boolean ignoreIso;
        Fake(){
            p.put("scene-mode-values","program-auto,aperture-priority,manual-exposure");
            p.put("scene-mode","aperture-priority");
            p.put("iso-sensitivity","0");
            p.put("iso-sensitivity-values","0,100,200,400,800");
            p.put("whitebalance","auto");
            p.put("whitebalance-values","auto,daylight,shade,custom,color-temp,incandescent");
            p.put("exposure-compensation","0");
            p.put("aperture","200");
            p.put("shutter-speed-nume","1");
            p.put("shutter-speed-deno","250");
            p.put("focus-area-type","multi");
            p.put("focus-point-x","0");
            p.put("focus-point-y","0");
            p.put("shooting-preview-values","off,iris_ss_iso_aeunlock");
            p.put("shooting-preview","off");
            p.put("min-exposure-compensation","-15");
            p.put("max-exposure-compensation","15");
            p.put("exposure-compensation-step","0.33333");
            p.put("exposure-compensation","0");
        }
        public Map<String,String> read(){return new LinkedHashMap<String,String>(p);}
        public void write(Map<String,String> patch){p.putAll(patch);}
        public void incrementAperture(){p.put("aperture",Integer.toString(Integer.parseInt(p.get("aperture"))+20));}
        public void decrementAperture(){p.put("aperture",Integer.toString(Integer.parseInt(p.get("aperture"))-20));}
        public void incrementShutterSpeed(){p.put("shutter-speed-deno",Integer.toString(Integer.parseInt(p.get("shutter-speed-deno"))/2));}
        public void decrementShutterSpeed(){p.put("shutter-speed-deno",Integer.toString(Integer.parseInt(p.get("shutter-speed-deno"))*2));}
        public void setIso(int iso){if(!ignoreIso)p.put("iso-sensitivity",Integer.toString(iso));}
        public void setWhiteBalance(String value){p.put("whitebalance",value);}
        public void setShootingPreview(String value){p.put("shooting-preview",value);}
        public void setAutoExposureLock(boolean locked){p.put("auto-exposure-lock",locked?"on":"off");}
        // Sony quantizes the vertical point to its AF grid. v2.5 must accept
        // the readback instead of reporting a failed move.
        public int[] setFocus(String area,int x,int y){
            // Quantization applies to the flexible spot; the multi-area restore
            // stores the original point unchanged.
            int keptY="flex-spot".equals(area)?Math.round(y/10f)*10:y;
            p.put("focus-area-type",area);p.put("focus-point-x",Integer.toString(x));p.put("focus-point-y",Integer.toString(keptY));
            return new int[]{x,keptY};
        }
    }
    private static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception{
        ManualExposure.Log log=new ManualExposure.Log(){public void log(String n,String d){}};
        Fake f=new Fake();
        ManualExposure m=new ManualExposure(f,log);
        m.open();
        require("manual-exposure".equals(f.p.get("scene-mode")),"M mode not entered");
        require("iris_ss_iso_aeunlock".equals(f.p.get("shooting-preview")),"A7R II live exposure preview");
        require("400".equals(f.p.get("iso-sensitivity")),"default manual ISO");
        require(m.aperture(1),"aperture did not move");
        require("220".equals(f.p.get("aperture")),"aperture value");
        require(m.shutter(1),"shutter did not move");
        require("125".equals(f.p.get("shutter-speed-deno")),"shutter value");
        require(m.iso(1),"ISO did not move");
        require("800".equals(f.p.get("iso-sensitivity")),"ISO value");
        require(m.whiteBalance(1),"WB did not move");
        require("daylight".equals(f.p.get("whitebalance")),"WB value");
        CaptureReadout r=new CaptureReadout();m.update(r);
        require(r.manual&&r.manualIso==800&&"daylight".equals(r.whiteBalance)&&r.aperture==220,"readout");
        require(!m.captureSafe(),"preview state is not capture-safe by default");
        f.p.put("scene-mode","program-auto");
        m.prepareCapture();
        require(m.captureSafe(),"capture guard state");
        require("manual-exposure".equals(f.p.get("scene-mode")),"capture guard restores M");
        require("off".equals(f.p.get("shooting-preview")),"capture guard restores the saved preview mode");
        require("800".equals(f.p.get("iso-sensitivity"))&&"220".equals(f.p.get("aperture"))
                &&"125".equals(f.p.get("shutter-speed-deno")),"capture guard reasserts dial values");
        require("on".equals(f.p.get("auto-exposure-lock")),"capture guard locks AE");
        require(m.captureState().contains("safe=true"),"capture state");
        m.releaseCaptureGuard();
        require(!m.captureSafe()&&"on".equals(f.p.get("auto-exposure-lock")),"capture guard keeps AE locked until preview resumes");
        m.enableExposurePreview();
        require(!m.captureSafe()&&"off".equals(f.p.get("auto-exposure-lock"))
                &&"iris_ss_iso_aeunlock".equals(f.p.get("shooting-preview")),"live preview resumes and unlocks AE");
        m.close();
        require("aperture-priority".equals(f.p.get("scene-mode")),"mode not restored");
        require("0".equals(f.p.get("iso-sensitivity")),"ISO not restored");
        require("auto".equals(f.p.get("whitebalance")),"WB not restored");
        require("200".equals(f.p.get("aperture")),"aperture not restored");
        require("off".equals(f.p.get("shooting-preview")),"live exposure preview not restored");

        Fake noManual=new Fake();noManual.p.put("scene-mode-values","program-auto,aperture-priority");
        boolean rejected=false;try{new ManualExposure(noManual,log).open();}catch(IOException expected){rejected=true;}
        require(rejected,"missing M mode accepted");

        Fake ignore=new Fake();ignore.ignoreIso=true;ManualExposure bad=new ManualExposure(ignore,log);
        rejected=false;try{bad.open();}catch(IOException expected){rejected=true;}
        require(rejected,"ISO readback mismatch accepted");

        Fake af=new Fake();af.p.put("focus-point-x","2");af.p.put("focus-point-y","2");
        AfPointController a=new AfPointController(af,new AfPointController.Log(){public void log(String n,String d){}});
        a.open();a.enter();
        require("multi".equals(af.p.get("focus-area-type")),"AF enter must not touch the camera");
        a.move(1,0);a.move(0,1);
        require(a.x()==102&&a.y()==102,"AF local move");
        require("multi".equals(af.p.get("focus-area-type")),"AF arrows must stay local");
        a.move(20,20);
        require(a.x()==1000&&a.y()==1000,"AF clamp");
        a.confirm();
        require(!a.active()&&a.touched(),"AF confirm state");
        require("flex-spot".equals(af.p.get("focus-area-type")),"AF commit area");
        require(a.x()==1000&&a.y()==1000,"AF commit coordinates");
        a.close();
        require("multi".equals(af.p.get("focus-area-type"))&&"2".equals(af.p.get("focus-point-x"))&&"2".equals(af.p.get("focus-point-y")),"AF restore");

        Fake quantized=new Fake();quantized.p.put("focus-point-x","2");quantized.p.put("focus-point-y","2");
        AfPointController q=new AfPointController(quantized,new AfPointController.Log(){public void log(String n,String d){}});
        q.open();q.enter();q.move(0,1);q.confirm();
        require(q.y()==100&&"100".equals(quantized.p.get("focus-point-y")),"AF quantized readback adopted "+q.y());
        q.close();
        require("multi".equals(quantized.p.get("focus-area-type"))&&"2".equals(quantized.p.get("focus-point-y")),"AF quantized restore");

        Fake live=new Fake();live.p.put("focus-point-x","2");live.p.put("focus-point-y","2");
        AfPointController l=new AfPointController(live,new AfPointController.Log(){public void log(String n,String d){}});
        l.open();l.liveMove(1,0);
        require("flex-spot".equals(live.p.get("focus-area-type"))&&"102".equals(live.p.get("focus-point-x")),"live movie AF commits immediately");
        l.liveMove(0,1);require("100".equals(live.p.get("focus-point-y")),"live movie AF moves vertical readback="+live.p.get("focus-point-y"));
        l.close();
        require("multi".equals(live.p.get("focus-area-type"))&&"2".equals(live.p.get("focus-point-x"))&&"2".equals(live.p.get("focus-point-y")),"live movie AF restores original area");

        AfPointController cancel=new AfPointController(af,new AfPointController.Log(){public void log(String n,String d){}});
        cancel.open();cancel.enter();cancel.move(1,0);cancel.cancel();
        require("multi".equals(af.p.get("focus-area-type"))&&"2".equals(af.p.get("focus-point-x")),"AF cancel must not write when untouched");

        Fake modes=new Fake();
        ManualExposure mm=new ManualExposure(modes,log);
        mm.open();
        require("M".equals(mm.label()),"default M mode");
        mm.cycleMode();
        require("A".equals(mm.label())&&"aperture-priority".equals(modes.p.get("scene-mode")),"A mode");
        require("0".equals(modes.p.get("iso-sensitivity")),"A mode forces auto ISO");
        String beforeAperture=modes.p.get("aperture");
        mm.dial(0,0,1);
        require(!beforeAperture.equals(modes.p.get("aperture")),"A-mode gain wheel moves aperture");
        mm.dial(0,1,0);
        CaptureReadout mr=new CaptureReadout();mm.update(mr);
        require(mr.exposureEvValid&&Math.abs(mr.exposureEv-0.33333f)<.01f,"A-mode EV readout "+mr.exposureEv);
        require("1".equals(modes.p.get("exposure-compensation")),"A-mode rear dial writes EV");
        mm.cycleMode();
        require("S".equals(mm.label())&&"shutter-speed".equals(modes.p.get("scene-mode")),"S mode");
        require("0".equals(modes.p.get("iso-sensitivity")),"S mode forces auto ISO");
        String beforeShutter=modes.p.get("shutter-speed-deno");
        mm.dial(0,0,1);
        require(!beforeShutter.equals(modes.p.get("shutter-speed-deno")),"S-mode gain wheel moves shutter");
        mm.dial(1,0,0);
        require("2".equals(modes.p.get("exposure-compensation")),"S-mode front dial writes EV");
        mm.cycleMode();
        require("M".equals(mm.label()),"M mode after cycle");
        require("400".equals(modes.p.get("iso-sensitivity")),"M mode restores manual ISO");
        mm.dial(0,0,-3);
        require("0".equals(modes.p.get("iso-sensitivity")),"M-mode control dial can select AUTO ISO");
        mm.close();
        require("aperture-priority".equals(modes.p.get("scene-mode")),"original mode restored");

        // a7r2.46 recipe finder: open after the live preview without forcing
        // M or touching the recipe's WB/EV/ISO.
        Fake recipe=new Fake();
        recipe.p.put("iso-sensitivity","0");
        recipe.p.put("whitebalance","daylight");
        recipe.p.put("exposure-compensation","2");
        ManualExposure rm=new ManualExposure(recipe,log);
        rm.openPreservingScene();
        require("aperture-priority".equals(recipe.p.get("scene-mode")),"recipe open must keep A");
        require("A".equals(rm.label()),"recipe label keeps A");
        require("daylight".equals(recipe.p.get("whitebalance")),"recipe open must keep WB");
        require("2".equals(recipe.p.get("exposure-compensation")),"recipe open must keep EV");
        require("0".equals(recipe.p.get("iso-sensitivity")),"recipe open must keep AUTO ISO");
        require("off".equals(recipe.p.get("shooting-preview")),"recipe A open must not touch preview");
        rm.dial(0,0,1);
        require("0".equals(recipe.p.get("iso-sensitivity")),"A-mode wheel keeps AUTO ISO");
        rm.cycleMode();
        require("S".equals(rm.label()),"recipe A -> S");
        rm.cycleMode();
        require("P".equals(rm.label())&&"program-auto".equals(recipe.p.get("scene-mode")),"recipe S -> P");
        rm.dial(0,0,1);
        require("100".equals(recipe.p.get("iso-sensitivity")),"P-mode wheel selects ISO");
        rm.dial(1,0,0);
        require("3".equals(recipe.p.get("exposure-compensation")),"P-mode dial writes EV");
        rm.cycleMode();
        require("M".equals(rm.label())&&"100".equals(recipe.p.get("iso-sensitivity")),"recipe P -> M keeps last ISO");
        rm.close();
        require("aperture-priority".equals(recipe.p.get("scene-mode")),"recipe mode restored");
        require("daylight".equals(recipe.p.get("whitebalance"))&&"2".equals(recipe.p.get("exposure-compensation"))
                &&"0".equals(recipe.p.get("iso-sensitivity"))&&"off".equals(recipe.p.get("shooting-preview")),"recipe values restored");

        Fake program=new Fake();
        program.p.put("scene-mode","program-auto");
        ManualExposure pmode=new ManualExposure(program,log);
        pmode.openPreservingScene();
        require("P".equals(pmode.label())&&"program-auto".equals(program.p.get("scene-mode")),"recipe P preserved");
        pmode.close();
        require("program-auto".equals(program.p.get("scene-mode")),"recipe P restored");
        require(A7R2Keys.frontDial(525)==1&&A7R2Keys.frontDial(526)==-1,"front dial");
        require(A7R2Keys.rearDial(528)==1&&A7R2Keys.rearDial(529)==-1,"rear dial");
        require(A7R2Keys.controlDial(522)==1&&A7R2Keys.controlDial(634)==-1,"control dial");
        require(A7R2Keys.deleteKey(595)&&A7R2Keys.deleteKey(513),"delete keys");
        require(A7R2Keys.zoomToggle(659)&&A7R2Keys.zoomToggle(610)&&A7R2Keys.zoomToggle(623),"zoom keys");
        require(A7R2Keys.zoomToggle(597)&&A7R2Keys.zoomToggle(588),"C3/custom fallback keys");
        require(A7R2Keys.filmKey(659,0)&&A7R2Keys.filmKey(588,0)&&A7R2Keys.filmKey(620,0)
                &&A7R2Keys.filmKey(636,0),"recipe film keys");
        require(!A7R2Keys.filmKey(622,0)&&!A7R2Keys.filmKey(623,0),"C1/C2 keep exposure/pose");
        require(A7R2Keys.movieKey(515,0)&&!A7R2Keys.movieKey(516,0),"movie key");
        require(A7R2Keys.filmC3(659,0)&&A7R2Keys.filmC3(588,0)&&A7R2Keys.filmC3(620,0),
                "video C3 accepts the camera's 588 alias");
        require(!A7R2Keys.filmC3(610,0)&&!A7R2Keys.filmC3(636,0)&&!A7R2Keys.filmC3(622,0),
                "video C3 does not steal zoom/focus/C1");
        require("WB 日光".equals(WhiteBalanceNames.label("daylight")),"WB label");
        require(WhiteBalanceNames.cycleValues("auto,daylight,custom,color-temp,shade").size()==3,"WB filter");
        require(ControlMath.step(0,3,-1)==0&&ControlMath.step(0,3,1)==1&&ControlMath.step(2,3,1)==2,"step");
        require(ControlMath.clampAf(-2000)==-1000&&ControlMath.clampAf(2000)==1000,"AF clamp math");
        require(ControlMath.afCoordinate(0,2000)==1000,"AF coordinate");
        require(ControlMath.zoomStep(1f,1)==1.5f&&ControlMath.zoomStep(2f,-1)==1.5f,"zoom step");
        require(ControlMath.zoomStep(8f,1)==8f&&ControlMath.zoomStep(1f,-1)==1f,"zoom limits");
        require(ControlMath.zoomStep(3.4f,1)==4f&&ControlMath.zoomStep(3.8f,-1)==3f,"zoom nearest level");
        System.out.println("PASS manual M/ISO/WB, AF local move/quantized restore, key table, WB labels, clamp and zoom math");
    }
}
