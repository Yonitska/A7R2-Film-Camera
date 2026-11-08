package org.hongen.sonylutprobe;

import java.util.LinkedHashMap;
import java.util.Map;

/** Live film switching must start from the user's pre-recipe controls, not
 * from the previously applied recipe. Regression for WB / Picture Effect
 * sub-option leaking into the next recipe. */
public final class RecipeCameraLookTest {
    private static final class FakeDriver implements RecipeCameraLook.Driver{
        final Map<String,String> map=new LinkedHashMap<String,String>();
        public Map<String,String> read(){return new LinkedHashMap<String,String>(map);}
        public void write(Map<String,String> patch){map.putAll(patch);}
    }
    private static void eq(String what,String expected,String actual){
        if(expected==null?actual!=null:!expected.equals(actual))
            throw new AssertionError(what+": expected "+expected+" got "+actual);
    }
    public static void main(String[] args)throws Exception{
        FakeDriver driver=new FakeDriver();
        driver.map.put("color-mode","standard");
        driver.map.put("saturation","0");
        driver.map.put("contrast","0");
        driver.map.put("sharpness","0");
        driver.map.put("rgb-matrix-mode","false");
        driver.map.put("whitebalance","daylight");
        driver.map.put("color-temperture-white-balance","5500");
        driver.map.put("light-balance-for-white-balance","0");
        driver.map.put("color-compensation-for-white-balance","0");
        driver.map.put("picture-effect","off");
        driver.map.put("pe-toy-camera-effect","normal");
        driver.map.put("exposure-compensation","0");
        driver.map.put("dro-mode","off");
        driver.map.put("dro-level","1");
        RecipeCameraLook look=new RecipeCameraLook(driver,new RecipeCameraLook.Log(){
            public void log(String name,String detail){}});
        look.snapshot();

        RecipeData.Recipe kelvinToy=new RecipeData.Recipe(0,"KELVIN TOY","开尔文玩具",2,3,2,1,14,5000,2,-1,1,1,6,2);
        look.apply(kelvinToy);
        eq("kelvin whitebalance","color-temp",driver.map.get("whitebalance"));
        eq("kelvin value","5000",driver.map.get("color-temperture-white-balance"));
        eq("kelvin style","vivid",driver.map.get("color-mode"));
        eq("toy effect","toy-camera",driver.map.get("picture-effect"));
        eq("toy sub","warm",driver.map.get("pe-toy-camera-effect"));

        // Keep-WB / no-effect recipe: every key it does not set must come back
        // to the snapshot taken before any recipe was applied.
        RecipeData.Recipe keepWb=new RecipeData.Recipe(0,"KEEP WB","保留白平衡",1,0,0,0,0,0,0,0,0,0,0,0);
        look.apply(keepWb);
        eq("baseline whitebalance","daylight",driver.map.get("whitebalance"));
        eq("baseline kelvin","5500",driver.map.get("color-temperture-white-balance"));
        eq("baseline style","standard",driver.map.get("color-mode"));
        eq("baseline saturation","0",driver.map.get("saturation"));
        eq("baseline contrast","0",driver.map.get("contrast"));
        eq("baseline sharpness","0",driver.map.get("sharpness"));
        eq("no effect","off",driver.map.get("picture-effect"));
        eq("baseline sub-option","normal",driver.map.get("pe-toy-camera-effect"));
        eq("baseline ev","0",driver.map.get("exposure-compensation"));
        eq("baseline dro","off",driver.map.get("dro-mode"));

        // Switching again after a keep-WB recipe still works.
        look.apply(kelvinToy);
        eq("second kelvin whitebalance","color-temp",driver.map.get("whitebalance"));
        eq("second kelvin sub","warm",driver.map.get("pe-toy-camera-effect"));

        look.restore();
        eq("restore whitebalance","daylight",driver.map.get("whitebalance"));
        eq("restore picture effect","off",driver.map.get("picture-effect"));
        eq("restore style","standard",driver.map.get("color-mode"));
        System.out.println("RecipeCameraLookTest PASS: baseline overlay clears prior WB/effect on live switch");
    }
}
