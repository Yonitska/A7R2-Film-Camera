package org.hongen.sonylutprobe;

import java.util.Locale;

/** Measured exposure values, independent of camera control and Android widgets. */
final class CaptureReadout {
    int shutterNumerator,shutterDenominator,aperture,autoIso,manualIso;
    boolean isoAuto;
    String whiteBalance="",manualMode="M";
    float exposureEv;
    boolean manual,exposureEvValid;
    int battery=-1;
    boolean charging;

    String shutterText(){
        if(shutterNumerator<=0||shutterDenominator<=0)return "--";
        int a=shutterNumerator,b=shutterDenominator;
        while(b!=0){int remainder=a%b;a=b;b=remainder;}
        int n=shutterNumerator/a,d=shutterDenominator/a;
        if(n==1&&d>1)return "1/"+d;
        return trim(String.format(Locale.US,"%.3f",(double)n/d))+"s";
    }
    String apertureText(){
        return aperture<=0?"F--":"F"+trim(String.format(Locale.US,"%.1f",aperture/100.0));
    }
    String isoText(){
        if(isoAuto)return autoIso>0?"AUTO "+autoIso:"ISO AUTO";
        int value=manualIso>0?manualIso:autoIso;
        return value>0?"ISO "+value:"ISO AUTO";
    }
    String evText(){
        return exposureEvValid?String.format(Locale.US,"%+.1f EV",exposureEv):"EV --";
    }
    String whiteBalanceText(){
        if(whiteBalance==null||whiteBalance.length()==0)return "WB --";
        if("auto".equals(whiteBalance))return "WB AUTO";
        if("daylight".equals(whiteBalance))return "WB 日光";
        if("shade".equals(whiteBalance))return "WB 阴影";
        if("cloudy-daylight".equals(whiteBalance))return "WB 阴天";
        if("incandescent".equals(whiteBalance))return "WB 白炽灯";
        if("flash".equals(whiteBalance))return "WB 闪光";
        if("color-temp".equals(whiteBalance))return "WB 色温";
        if("custom".equals(whiteBalance))return "WB 自定义";
        if(whiteBalance.startsWith("fluorescent"))return "WB 荧光灯";
        if(whiteBalance.startsWith("underwater"))return "WB 水下";
        return "WB "+whiteBalance.toUpperCase(Locale.US);
    }
    static int batteryPercent(int level,int scale){
        if(level<0||scale<=0||level>scale)return -1;
        return (int)((long)level*100/scale);
    }
    private static String trim(String value){
        while(value.endsWith("0")&&value.indexOf('.')>=0)value=value.substring(0,value.length()-1);
        if(value.endsWith("."))value=value.substring(0,value.length()-1);
        return value;
    }
    String state(){return "shutter="+shutterText()+" aperture="+apertureText()+" iso="+autoIso+" battery="+battery+" charging="+charging;}
}
