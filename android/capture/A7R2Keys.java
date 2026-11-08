package org.hongen.sonylutprobe;

/** A7R II scan codes. Pure so the direction table is host-testable. */
final class A7R2Keys {
    private A7R2Keys(){}

    static final int DIAL1_CW=525,DIAL1_CCW=526;
    static final int DIAL2_CW=528,DIAL2_CCW=529;
    static final int KURU_CW=522,KURU_CCW=523;
    static final int DIAL3_CW=635,DIAL3_CCW=634;
    static final int C1=622,C2=623,C3=659;
    static final int DELETE=595,SK2=513,AFMF=533,FOCUS=636;
    static final int CUSTOM=588,AF_MF_MODE=597,FOCUS_HOLD=603,WB=604,ISO=681;
    // A7R II buttons that report themselves instead of Sony's C3 scan 659.
    // R0038196 (a7r2.45) logged six presses of 588 while the user tried C3 in
    // the recipe finder; R004401C logged 620. Gallery has always accepted the
    // same family for playback zoom.
    static final int C3_ALT=620;
    static final int ZOOM_TELE=610,ZOOM_WIDE=611;
    // Record button. The recipe finder previously swallowed it; its own
    // handoff opens the video session with the selected recipe.
    static final int MOVIE=515;

    static int frontDial(int scan){
        if(scan==DIAL1_CW)return 1;
        if(scan==DIAL1_CCW)return -1;
        return 0;
    }
    static int rearDial(int scan){
        if(scan==DIAL2_CW)return 1;
        if(scan==DIAL2_CCW)return -1;
        return 0;
    }
    static int controlDial(int scan){
        if(scan==KURU_CW||scan==DIAL3_CW)return 1;
        if(scan==KURU_CCW||scan==DIAL3_CCW)return -1;
        return 0;
    }
    static boolean deleteKey(int scan){return scan==DELETE||scan==SK2;}
    static boolean zoomToggle(int scan){
        return scan==C1||scan==C2||scan==C3||scan==CUSTOM||scan==AFMF
                ||scan==AF_MF_MODE||scan==FOCUS||scan==FOCUS_HOLD||scan==WB||scan==ISO
                ||scan==ZOOM_TELE||scan==ZOOM_WIDE;
    }
    static boolean c3(int scan,int key){return scan==C3||key==C3;}
    /** C3 in the video session. The A7R II maps its physical C3 button to
     * scan 588 (CUSTOM) while recording; R008CFC6 logged three 588 presses
     * that video silenced as video_key_unhandled. Keep the alias narrow so
     * the focus/zoom fallbacks used by the still film strip cannot swallow a
     * video AF or zoom key. */
    static boolean filmC3(int scan,int key){
        return c3(scan,key)||scan==CUSTOM||scan==C3_ALT;
    }
    static boolean movieKey(int scan,int key){return scan==MOVIE||key==MOVIE;}
    /** Recipe finder's film-strip key. The A7R II's magnifier/custom buttons
     * report 588/620/636/610/611 instead of C3=659; all of them open the same
     * modal strip. C1/C2 are deliberately excluded: they keep their own
     * exposure/pose functions. */
    static boolean filmKey(int scan,int key){
        return c3(scan,key)||scan==C3_ALT||scan==CUSTOM||scan==FOCUS
                ||scan==AFMF||scan==ZOOM_TELE||scan==ZOOM_WIDE;
    }
}
