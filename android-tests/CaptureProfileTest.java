package org.hongen.sonylutprobe;

import java.io.IOException;

/** Exact platform contract for the 24m and 42m Sony memory configurations. */
public class CaptureProfileTest {
    public static void main(String[] args) throws Exception {
        CaptureProfile a6300=CaptureProfile.of("ILDCSC_MUSASHI8Gb","24");
        require("MAP24R1.BIN".equals(a6300.mapAsset),"A6300 map asset");
        require("reference01".equals(a6300.mapRevision),"A6300 map revision");
        require(a6300.referenceMapBytes==1860&&a6300.firmwareMapBytes==1020,"A6300 map sizes");
        require(a6300.width==6000&&a6300.height==4000,"A6300 dimensions");
        require(!a6300.uvSwap,"A6300 UYVY order");
        require(!a6300.manualControls,"A6300 keeps automatic exposure");
        require(!a6300.originalPreviewOnly,"A6300 keeps approximate live preview");
        require(a6300.supportsRevision("auto")&&a6300.supportsRevision("reference01"),"A6300 revisions");
        require(!a6300.supportsRevision("reference42"),"A6300 rejects 42m revision");

        CaptureProfile a7r2=CaptureProfile.of("ILDCSC_MUSASHI8Gb","42");
        require("MAP42R1.BIN".equals(a7r2.mapAsset),"A7R II map asset");
        require("reference42".equals(a7r2.mapRevision),"A7R II map revision");
        require(a7r2.referenceMapBytes==1980&&a7r2.firmwareMapBytes==1980,"A7R II map sizes");
        require(a7r2.width==7952&&a7r2.height==5304,"A7R II dimensions");
        require(a7r2.uvSwap,"A7R II VYUY order");
        require(a7r2.manualControls,"A7R II manual exposure");
        require(a7r2.originalPreviewOnly,"A7R II keeps original live view (no OSD tint)");
        require(a7r2.timeoutMs>=45000&&a7r2.transitionTimeoutMs>=5000,"A7R II timeouts");
        require(a7r2.supportsRevision("auto")&&a7r2.supportsRevision("reference42"),"A7R II revisions");
        require(!a7r2.supportsRevision("reference01"),"A7R II rejects 24m revision");

        rejects("ILDCSC_MUSASHI4Gb","24");
        rejects("ILDCSC_MUSASHI8Gb","20");
        rejects("ILDCSC_MUSASHI8Gb","36");
        rejects("ILDCSC_MUSASHI8Gb",null);
        System.out.println("PASS: 24m/42m profile dimensions, maps, revisions and timeouts; unrelated memory configurations rejected");
    }
    private static void rejects(String memory,String pixels)throws Exception{
        try{CaptureProfile.of(memory,pixels);throw new AssertionError("accepted "+memory+"/"+pixels);}
        catch(IOException expected){}
    }
    private static void require(boolean value,String label){if(!value)throw new AssertionError(label);}
}
