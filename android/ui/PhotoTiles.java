package org.hongen.sonylutprobe;

/** Coordinate mapping for full-42MP playback tiles.
 *
 * The gallery panel is only 640x480 and the app heap is 24 MB, so a complete
 * 7952x5304 ARGB bitmap (161 MB) can never be decoded. Instead the original
 * JPEG is read with BitmapRegionDecoder: only the visible rectangle, at a
 * sample size that roughly matches the panel, is ever allocated. These helpers
 * convert between the rotated coordinates the user sees and the raw JPEG
 * coordinates expected by BitmapRegionDecoder. */
final class PhotoTiles {
    private PhotoTiles(){}
    /** EXIF orientations handled by the tile path. The flipped variants fall
     * back to the sampled whole-image decode. */
    static boolean supported(int orientation){
        return orientation==1||orientation==3||orientation==6||orientation==8;
    }
    /** [width,height] after EXIF rotation. */
    static int[] orientedSize(int rawWidth,int rawHeight,int orientation){
        return orientation==6||orientation==8?new int[]{rawHeight,rawWidth}:new int[]{rawWidth,rawHeight};
    }
    /** Oriented pixel bounds -> clamped raw JPEG rectangle [l,t,r,b]. */
    static int[] rawBounds(int rawWidth,int rawHeight,int orientation,
                           float left,float top,float right,float bottom){
        long l=Math.round(left),t=Math.round(top),r=Math.round(right),b=Math.round(bottom);
        if(orientation==6){long nl=t,nt=rawHeight-r,nr=b,nb=rawHeight-l;l=nl;t=nt;r=nr;b=nb;}
        else if(orientation==8){long nl=rawWidth-b,nt=l,nr=rawWidth-t,nb=r;l=nl;t=nt;r=nr;b=nb;}
        else if(orientation==3){long nl=rawWidth-r,nt=rawHeight-b,nr=rawWidth-l,nb=rawHeight-t;l=nl;t=nt;r=nr;b=nb;}
        l=clamp(l,0,rawWidth-1);t=clamp(t,0,rawHeight-1);
        r=clamp(r,l+1,rawWidth);b=clamp(b,t+1,rawHeight);
        return new int[]{(int)l,(int)t,(int)r,(int)b};
    }
    /** Raw JPEG rectangle -> oriented float bounds [l,t,r,b]. */
    static float[] orientedBounds(int rawWidth,int rawHeight,int orientation,int[] raw){
        float l=raw[0],t=raw[1],r=raw[2],b=raw[3];
        if(orientation==6)return new float[]{rawHeight-b,l,rawHeight-t,r};
        if(orientation==8)return new float[]{t,rawWidth-r,b,rawWidth-l};
        if(orientation==3)return new float[]{rawWidth-r,rawHeight-b,rawWidth-l,rawHeight-t};
        return new float[]{l,t,r,b};
    }
    /** Smallest power-of-two subsample that keeps the tile within maxSide. */
    static int sampleFor(float width,float height,int maxSide){
        if(maxSide<1)maxSide=1;
        int sample=1;
        while(width/sample>maxSide||height/sample>maxSide)sample*=2;
        return sample;
    }
    /** Tile sample size: start from maxSide, then sharpen while the decoded
     * tile still fits the pixel budget. Keeps zoom detail at roughly one
     * decoded pixel per panel pixel without ever approaching a full decode. */
    static int tileSample(int width,int height,int maxSide,long budgetPixels){
        int sample=sampleFor(width,height,maxSide);
        while(sample>1){
            int next=sample/2;
            if(((long)(width/next))*(height/next)>budgetPixels)break;
            sample=next;
        }
        return sample;
    }
    static boolean inside(float[] outer,float[] inner,float slack){
        return inner[0]>=outer[0]-slack&&inner[1]>=outer[1]-slack
                &&inner[2]<=outer[2]+slack&&inner[3]<=outer[3]+slack;
    }
    private static long clamp(long value,long lo,long hi){return value<lo?lo:value>hi?hi:value;}
}
