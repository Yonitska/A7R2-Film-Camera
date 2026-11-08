package org.hongen.sonylutprobe;

import java.io.IOException;

/** Exact platform profile for the two Sony memory configurations used by the
 * still-capture sequence. The map bytes are data consumed by Sony's loader. */
final class CaptureProfile {
    final String memory,pixels,label,mapAsset,mapRevision,firmwareMapPath;
    final int referenceMapBytes,firmwareMapBytes,width,height,timeoutMs,transitionTimeoutMs;
    final boolean uvSwap,manualControls,originalPreviewOnly;

    private CaptureProfile(String memory,String pixels,String label,String mapAsset,String mapRevision,
                           String firmwareMapPath,int referenceMapBytes,int firmwareMapBytes,
                           int width,int height,int timeoutMs,int transitionTimeoutMs,boolean uvSwap,
                           boolean manualControls,boolean originalPreviewOnly){
        this.memory=memory;this.pixels=pixels;this.label=label;
        this.mapAsset=mapAsset;this.mapRevision=mapRevision;this.firmwareMapPath=firmwareMapPath;
        this.referenceMapBytes=referenceMapBytes;this.firmwareMapBytes=firmwareMapBytes;
        this.width=width;this.height=height;this.timeoutMs=timeoutMs;
        this.transitionTimeoutMs=transitionTimeoutMs;this.uvSwap=uvSwap;
        this.manualControls=manualControls;this.originalPreviewOnly=originalPreviewOnly;
    }

    static CaptureProfile of(String memory,String pixels)throws IOException{
        if(!"ILDCSC_MUSASHI8Gb".equals(memory))
            throw new IOException("unvalidated platform "+memory+"/"+pixels);
        if("24".equals(pixels))
            return new CaptureProfile(memory,pixels,"24MP","MAP24R1.BIN","reference01",
                    "/system/etc/memmap/libILDCSC_MUSASHI8Gb24m_PictureEffectPlus_00_00.so",
                    1860,1020,6000,4000,25000,5000,false,false,false);
        if("42".equals(pixels))
            return new CaptureProfile(memory,pixels,"42MP","MAP42R1.BIN","reference42",
                    "/system/etc/memmap/libILDCSC_MUSASHI8Gb42m_PictureEffectPlus_00_00.so",
                    1980,1980,7952,5304,60000,8000,true,true,true);
        throw new IOException("unvalidated platform "+memory+"/"+pixels);
    }

    boolean supportsRevision(String revision){
        return "auto".equals(revision)||mapRevision.equals(revision);
    }

    String sizeText(){return width+"x"+height;}
}
