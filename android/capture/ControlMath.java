package org.hongen.sonylutprobe;

import java.util.List;

/** Pure stepping/clamping rules shared by the A7R II controls and tests. */
final class ControlMath {
    private ControlMath(){}

    static int step(int index,int size,int direction){
        if(size<=0)return -1;
        int next=index+(direction<0?-1:direction>0?1:0);
        return Math.max(0,Math.min(size-1,next));
    }

    static int nearest(List<Integer> values,int value){
        if(values==null||values.isEmpty())return -1;
        int best=0;long distance=Long.MAX_VALUE;
        for(int i=0;i<values.size();i++){
            long d=Math.abs((long)values.get(i)-value);
            if(d<distance){distance=d;best=i;}
        }
        return best;
    }

    static int clampAf(int value){return Math.max(-1000,Math.min(1000,value));}

    static int afCoordinate(int value,int pixels){
        return (int)Math.round((clampAf(value)+1000)*pixels/2000.0);
    }

    static float panLimit(float content,float view){return Math.max(0,(content-view)/2f);}

    static float clampPan(float pan,float content,float view){
        float limit=panLimit(content,view);
        return Math.max(-limit,Math.min(limit,pan));
    }

    /** Playback zoom stops, chosen so every step is visibly distinct on the
     * A7R II panel without requiring a 42MP bitmap in memory. */
    static final float[] ZOOM_LEVELS={1f,1.5f,2f,3f,4f,6f,8f};
    static int zoomIndex(float zoom){
        int best=0;
        for(int i=1;i<ZOOM_LEVELS.length;i++)
            if(Math.abs(ZOOM_LEVELS[i]-zoom)<Math.abs(ZOOM_LEVELS[best]-zoom))best=i;
        return best;
    }
    static float zoomStep(float zoom,int direction){
        int index=zoomIndex(zoom);
        if(direction>0)index=Math.min(ZOOM_LEVELS.length-1,index+1);
        else if(direction<0)index=Math.max(0,index-1);
        return ZOOM_LEVELS[index];
    }
}
