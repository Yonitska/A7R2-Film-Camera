package org.hongen.sonylutprobe;
import android.os.Bundle;
import java.util.UUID;

/** Reuses the accepted pre-JPEG pipeline, with a fixed production contract.
 * The manifest isolates its process: Sony RAW initialization requires a fresh
 * vendor JNI session after leaving capture, while consecutive shots share it. */
public final class PhotoActivity extends PreJpegProbeActivity {
    @Override protected boolean productUi(){return true;}
    @Override protected boolean automaticNativePreview(){return true;}
    @Override public void onCreate(Bundle state){
        boolean recipe=getIntent().getBooleanExtra("recipe_mode",false);
        if(recipe){
            // One camera-native JPEG per shutter: no RAW sequence, LUT, map,
            // overlap save or develop animation.
            getIntent().putExtra("phase","recipe").putExtra("init_only",false).putExtra("init_stage","configured")
                .putExtra("map_revision","auto").putExtra("fresh_session",true).putExtra("normalize_zoom",true)
                .putExtra("continuous",false).putExtra("verify_payloads",false).putExtra("overlap_original",false)
                .putExtra("source_reference",false).putExtra("alternate_overlap",false)
                .putExtra("develop_animation",false).putExtra("probe_token",UUID.randomUUID().toString());
        }else{
            getIntent().putExtra("phase","lut").putExtra("init_only",false).putExtra("init_stage","configured")
                .putExtra("map_revision","auto").putExtra("fresh_session",true).putExtra("normalize_zoom",true)
                .putExtra("continuous",true).putExtra("verify_payloads",false).putExtra("overlap_original",true)
                .putExtra("source_reference",false).putExtra("alternate_overlap",false).putExtra("probe_token",UUID.randomUUID().toString());
        }
        super.onCreate(state);
    }
}
