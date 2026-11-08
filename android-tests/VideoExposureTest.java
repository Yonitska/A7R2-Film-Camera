package org.hongen.sonylutprobe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Movie exposure session: mode policies, AUTO ISO and wheel routing. */
public final class VideoExposureTest {
    public static void main(String[] args)throws Exception{
        Fake driver=new Fake();
        VideoExposure exposure=new VideoExposure(driver,new VideoExposure.Log(){
            public void log(String name,String detail){}});
        exposure.open(VideoExposure.P);
        check("P".equals(exposure.modeLabel()),"P opened: "+exposure.modeLabel());
        check(driver.iso==0,"P keeps AUTO ISO: "+driver.iso);

        exposure.dial(1,0,1);
        check(driver.ev==1,"P front dial moves EV: "+driver.ev);
        check(driver.iso==100,"P gain wheel steps ISO from AUTO: "+driver.iso);

        exposure.setMode(VideoExposure.A);
        check(driver.iso==0,"A forces AUTO ISO: "+driver.iso);
        int aperture=driver.apertureIndex;
        exposure.dial(0,0,1);
        check(driver.apertureIndex>aperture,"A gain wheel opens aperture: "+aperture+"->"+driver.apertureIndex);
        int ev=driver.ev;
        exposure.dial(1,0,0);
        check(driver.ev!=ev,"A dials change EV: "+ev+"->"+driver.ev);

        exposure.setMode(VideoExposure.S);
        check(driver.iso==0,"S forces AUTO ISO: "+driver.iso);
        int shutter=driver.shutterIndex;
        exposure.dial(0,0,1);
        check(driver.shutterIndex>shutter,"S gain wheel changes shutter: "+shutter+"->"+driver.shutterIndex);
        check(exposure.modeLabel().equals("S"),"S label");

        exposure.setMode(VideoExposure.M);
        check(driver.iso>0,"M restores a manual ISO: "+driver.iso);
        exposure.dial(1,1,1);
        check(driver.apertureIndex>aperture,"M front dial is aperture");
        check(driver.shutterIndex>shutter,"M rear dial is shutter");
        check(driver.iso==200,"M gain wheel steps ISO: "+driver.iso);
        exposure.dial(0,0,-1);
        check(driver.iso==100,"M gain wheel reaches AUTO? expected 100 after one step down: "+driver.iso);
        exposure.dial(0,0,-1);
        check(driver.iso==0,"M gain wheel reaches AUTO 0: "+driver.iso);

        exposure.setWhiteBalance("daylight");
        check("daylight".equals(exposure.whiteBalance()),"WB applied");
        exposure.setEvIndex(3);
        check(driver.ev==3,"EV index applied: "+driver.ev);
        check(exposure.evLabel().startsWith("+1.0"),"EV label: "+exposure.evLabel());

        Map<String,String> p=driver.read();
        CaptureReadout readout=new CaptureReadout();
        readout.shutterNumerator=Integer.parseInt(p.get("shutter-speed-nume"));
        readout.shutterDenominator=Integer.parseInt(p.get("shutter-speed-deno"));
        readout.aperture=Integer.parseInt(p.get("aperture"));
        exposure.update(readout);
        check("M".equals(readout.manualMode),"readout mode M");
        check(readout.manualIso==0,"readout reports AUTO ISO as 0 in M");
        check("WB 日光".equals(readout.whiteBalanceText()),"readout WB: "+readout.whiteBalanceText());
        check(readout.exposureEvValid,"readout EV valid");

        boolean rejected=false;
        try{exposure.setWhiteBalance("custom");}catch(Exception expected){rejected=true;}
        check(rejected,"unlisted WB rejected");

        System.out.println("VideoExposureTest PASS: P/A/S/M ISO policy, EV/WB and gain-wheel routing");
    }
    private static void check(boolean condition,String message){
        if(!condition)throw new AssertionError(message);
    }

    static final class Fake implements ManualExposure.Driver{
        String scene="program-auto";
        int iso=0,ev=0,apertureIndex=4,shutterIndex=4,whiteBalanceIndex=0;
        final String[] apertures={"100","120","140","160","180","200","220","250","280","320"};
        final String[] shutters={"1/1000","1/500","1/250","1/125","1/60","1/30","1/15","1/8","1/4","1/2"};
        final String[] isos={"0","100","200","400","800","1600"};
        final String[] wbs={"auto","daylight","shade","cloudy-daylight","incandescent"};
        public Map<String,String> read(){
            Map<String,String> p=new LinkedHashMap<String,String>();
            p.put("scene-mode",scene);
            p.put("scene-mode-values","program-auto,aperture-priority,shutter-speed,manual-exposure");
            p.put("iso-sensitivity",Integer.toString(iso));
            p.put("iso-sensitivity-values",String.join(",",isos));
            p.put("whitebalance",wbs[whiteBalanceIndex]);
            p.put("whitebalance-values",String.join(",",wbs)+",custom,color-temp");
            p.put("exposure-compensation",Integer.toString(ev));
            p.put("min-exposure-compensation","-15");
            p.put("max-exposure-compensation","15");
            p.put("exposure-compensation-step","0.33333");
            p.put("aperture",apertures[apertureIndex]);
            p.put("shutter-speed-nume","1");
            p.put("shutter-speed-deno",shutters[shutterIndex].substring(2));
            p.put("af-mode","af-c");
            return p;
        }
        public void write(Map<String,String> patch)throws Exception{
            for(Map.Entry<String,String> e:patch.entrySet()){
                if("scene-mode".equals(e.getKey()))scene=e.getValue();
                else if("iso-sensitivity".equals(e.getKey()))iso=Integer.parseInt(e.getValue());
                else if("exposure-compensation".equals(e.getKey()))ev=Integer.parseInt(e.getValue());
                else if("whitebalance".equals(e.getKey()))whiteBalanceIndex=index(wbs,e.getValue());
                else if("aperture".equals(e.getKey()))apertureIndex=index(apertures,e.getValue());
            }
        }
        public void incrementAperture(){apertureIndex=Math.min(apertures.length-1,apertureIndex+1);}
        public void decrementAperture(){apertureIndex=Math.max(0,apertureIndex-1);}
        public void incrementShutterSpeed(){shutterIndex=Math.min(shutters.length-1,shutterIndex+1);}
        public void decrementShutterSpeed(){shutterIndex=Math.max(0,shutterIndex-1);}
        public void setIso(int value){iso=value;}
        public void setWhiteBalance(String value){whiteBalanceIndex=index(wbs,value);}
        public void setShootingPreview(String value){}
        public void setAutoExposureLock(boolean locked){}
        private static int index(String[] values,String value){
            for(int i=0;i<values.length;i++)if(values[i].equals(value))return i;
            return -1;
        }
    }
}
