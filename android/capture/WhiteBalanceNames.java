package org.hongen.sonylutprobe;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Short WB labels and the safe preset cycle. */
final class WhiteBalanceNames {
    private WhiteBalanceNames(){}

    static String label(String value){
        if(value==null||value.length()==0)return "WB --";
        if("auto".equals(value))return "WB AUTO";
        if("daylight".equals(value))return "WB 日光";
        if("shade".equals(value))return "WB 阴影";
        if("cloudy-daylight".equals(value))return "WB 阴天";
        if("incandescent".equals(value))return "WB 白炽灯";
        if("flash".equals(value))return "WB 闪光";
        if("color-temp".equals(value))return "WB 色温";
        if("custom".equals(value))return "WB 自定义";
        if(value.startsWith("fluorescent"))return "WB 荧光灯";
        if(value.startsWith("underwater"))return "WB 水下";
        return "WB "+value.toUpperCase(Locale.US);
    }

    /** Presets that need no extra colour-temperature/custom capture step. */
    static List<String> cycleValues(String csv){
        List<String> values=new ArrayList<String>();
        if(csv==null)return values;
        for(String raw:csv.split(",")){
            String value=raw.trim();
            if(value.length()==0||"custom".equals(value)||"color-temp".equals(value)
                    ||value.startsWith("custom"))continue;
            if(!values.contains(value))values.add(value);
        }
        return values;
    }
}
