package org.hongen.sonylutprobe;

import java.util.LinkedHashMap;
import java.util.Map;

/** Applies a Recipe Lab recipe through the camera's own picture controls.
 * Based on Recipe Lab by André Domingues (GPL-3.0).
 *
 * The A7R II (and the A6000 Recipe Lab targets) evaluate Creative Style,
 * saturation/contrast/sharpness, white balance, DRO and Picture Effect in the
 * camera before JPEG encoding. A recipe therefore needs no LUT pass: the JPEG
 * saved by the shutter already carries the look. Unsupported keys are logged
 * and skipped, never treated as a failed shot. */
final class RecipeCameraLook {
    interface Driver {
        Map<String,String> read() throws Exception;
        void write(Map<String,String> patch) throws Exception;
    }
    interface Log { void log(String name,String detail); }

    private static final String[] SNAPSHOT_KEYS={
        "color-mode","saturation","contrast","sharpness","rgb-matrix-mode",
        "whitebalance","color-temperture-white-balance",
        "light-balance-for-white-balance","color-compensation-for-white-balance",
        "picture-effect","exposure-compensation","dro-mode","dro-level"
    };
    private static final String[] EFFECT_SUB_KEYS={
        "pe-soft-high-key-effect","pe-toy-camera-effect","pe-part-color-effect","pe-posterization-effect"
    };
    private static final String[] SUPPORT_KEYS={
        "color-mode-values","whitebalance-values","picture-effect-values","dro-mode-values"
    };

    private final Driver driver;
    private final Log log;
    private Map<String,String> snapshot;
    private Map<String,String> supported;
    private String applied="";

    RecipeCameraLook(Driver driver,Log log){this.driver=driver;this.log=log;}

    /** Remember the controls the recipe will replace. Call after the exposure
     * session has settled on its mode, immediately before the first apply. */
    void snapshot() throws Exception {
        Map<String,String> p=driver.read();
        Map<String,String> saved=new LinkedHashMap<String,String>();
        for(String key:SNAPSHOT_KEYS)if(p.containsKey(key))saved.put(key,p.get(key));
        for(String key:EFFECT_SUB_KEYS)if(p.containsKey(key))saved.put(key,p.get(key));
        snapshot=saved;
        Map<String,String> advertised=new LinkedHashMap<String,String>();
        for(String key:SUPPORT_KEYS)if(p.containsKey(key))advertised.put(key,p.get(key));
        supported=advertised;
        log.log("recipe_look_snapshot","keys="+saved+" supported="+advertised);
    }

    /** The exact live-view parameter patch, matching Recipe Lab's preview
     * mapping. Quality/storage keys are deliberately absent: the product
     * capture path owns JPEG and movie formatting. */
    static Map<String,String> parameters(RecipeData.Recipe recipe){
        Map<String,String> p=new LinkedHashMap<String,String>();
        p.put("color-mode",RecipeData.styleName(recipe.style));
        p.put("saturation",Integer.toString(clamp(recipe.sat,-3,3)));
        p.put("contrast",Integer.toString(clamp(recipe.con,-3,3)));
        p.put("sharpness",Integer.toString(clamp(recipe.sharp,-3,3)));
        p.put("rgb-matrix-mode","false");
        if(recipe.wbMode==14){
            p.put("whitebalance","color-temp");
            p.put("color-temperture-white-balance",Integer.toString(recipe.kelvin));
        }else if(recipe.wbMode==1)p.put("whitebalance","auto");
        p.put("light-balance-for-white-balance",Integer.toString(clamp(recipe.ab,-7,7)));
        p.put("color-compensation-for-white-balance",Integer.toString(clamp(-recipe.gm,-7,7)));
        p.put("picture-effect",RecipeData.peName(recipe.pe));
        String subKey=RecipeData.subKey(recipe.pe),subValue=RecipeData.subRuntime(recipe.pe,recipe.sub);
        if(subKey!=null&&subValue!=null)p.put(subKey,subValue);
        p.put("exposure-compensation",Integer.toString(clamp(recipe.ev,-15,15)));
        if(recipe.dro==6)p.put("dro-mode","auto");
        else if(recipe.dro==0)p.put("dro-mode","off");
        else{p.put("dro-mode","on");p.put("dro-level",Integer.toString(clamp(recipe.dro,1,5)));}
        return p;
    }

    /** Write the recipe, read every value back and retry individual keys that
     * the firmware ignored. Remaining differences are reported but do not
     * block: the camera-native JPEG is still a valid approximation. */
    Map<String,String> apply(RecipeData.Recipe recipe)throws Exception {
        if(snapshot==null)throw new IllegalStateException("recipe look not snapshotted");
        applied=recipe.zh;
        // Start from the user's pre-recipe controls, then overlay this recipe.
        // Without the baseline, a live switch leaks the previous recipe's WB
        // and Picture Effect sub-option into a "keep WB / no effect" recipe.
        Map<String,String> wanted=new LinkedHashMap<String,String>(snapshot);
        wanted.putAll(parameters(recipe));
        StringBuilder skipped=new StringBuilder();
        filterAdvertised(wanted,skipped);
        // Redundant with the camera's current matrix mode; only clear it when
        // a previous LUT session actually left it on. Keeping the effect
        // sub-keys and colour temperature in the patch is deliberate: switching
        // back to a keep-WB / no-effect recipe must restore the user's
        // pre-recipe values (RecipeCameraLookTest).
        if(!"true".equals(snapshot.get("rgb-matrix-mode")))wanted.remove("rgb-matrix-mode");
        // Commit once, then read once. The old loop retried every ignored key
        // separately; on the A7R II that burst of vendor RPCs could leave the
        // camera service wedged while the finder was starting.
        log.log("recipe_look_write_begin","name="+recipe.name+" keys="+wanted.size()+" patch="+wanted);
        driver.write(wanted);
        log.log("recipe_look_write_done","name="+recipe.name);
        Map<String,String> p=driver.read();
        StringBuilder dropped=new StringBuilder();
        for(Map.Entry<String,String> entry:wanted.entrySet()){
            String key=entry.getKey(),value=entry.getValue();
            if(!value.equals(p.get(key)))
                dropped.append(key).append('=').append(p.get(key)).append(" (wanted ").append(value).append(") ");
        }
        log.log("recipe_look_applied","name="+recipe.name+" patch="+wanted
                +" after={color-mode="+p.get("color-mode")+",saturation="+p.get("saturation")
                +",contrast="+p.get("contrast")+",sharpness="+p.get("sharpness")
                +",whitebalance="+p.get("whitebalance")+",ev="+p.get("exposure-compensation")
                +",picture-effect="+p.get("picture-effect")+",dro="+p.get("dro-mode")+"/"+p.get("dro-level")+"}"
                +(skipped.length()>0?" unsupported="+skipped.toString().trim():"")
                +(dropped.length()>0?" dropped="+dropped.toString().trim():""));
        return p;
    }

    /** Remove enum values the body does not advertise. Writing an unknown
     * Creative Style/Picture Effect on this firmware is not merely ignored:
     * it can stall the parameter transaction. Missing *-values means the body
     * hides the capability list, so keep the key in that case. */
    private void filterAdvertised(Map<String,String> wanted,StringBuilder skipped){
        if(supported==null)return;
        if(!advertised("color-mode-values",wanted.get("color-mode"))){
            wanted.remove("color-mode");
            skipped.append("color-mode,");
        }
        String wb=wanted.get("whitebalance");
        if(!advertised("whitebalance-values",wb)){
            wanted.remove("whitebalance");
            if("color-temp".equals(wb))wanted.remove("color-temperture-white-balance");
            skipped.append("whitebalance,");
        }
        if(!advertised("picture-effect-values",wanted.get("picture-effect"))){
            wanted.remove("picture-effect");
            for(String key:EFFECT_SUB_KEYS)wanted.remove(key);
            skipped.append("picture-effect,");
        }
        if(!advertised("dro-mode-values",wanted.get("dro-mode"))){
            wanted.remove("dro-mode");wanted.remove("dro-level");
            skipped.append("dro-mode,");
        }
    }

    private boolean advertised(String valuesKey,String value){
        if(value==null)return true;
        String csv=supported.get(valuesKey);
        if(csv==null)return true;
        String list=","+csv.replace(" ","").replace(';',',').replace('|',',')+",";
        return list.contains(","+value+",");
    }

    /** Restore the user's controls. Best-effort: a missing or read-only key
     * must not abort camera teardown. */
    void restore()throws Exception {
        if(snapshot==null)return;
        Map<String,String> saved=snapshot;snapshot=null;
        try{
            driver.write(saved);
            Map<String,String> p=driver.read();
            StringBuilder differ=new StringBuilder();
            for(Map.Entry<String,String> entry:saved.entrySet())
                if(!entry.getValue().equals(p.get(entry.getKey())))
                    differ.append(entry.getKey()).append('=').append(p.get(entry.getKey())).append(' ');
            log.log("recipe_look_restored","applied="+applied+(differ.length()>0?" differs="+differ.toString().trim():""));
        }catch(Throwable e){
            log.log("recipe_look_restore_error",String.valueOf(e));
        }
    }

    String appliedName(){return applied;}
    private static Map<String,String> one(String key,String value){
        Map<String,String> patch=new LinkedHashMap<String,String>();patch.put(key,value);return patch;
    }
    private static int clamp(int value,int lo,int hi){return value<lo?lo:value>hi?hi:value;}
}
