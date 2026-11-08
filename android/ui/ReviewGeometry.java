package org.hongen.sonylutprobe;

/** Fit an image in physical screen coordinates, including non-square OSD pixels. */
final class ReviewGeometry {
    static float[] fit(int imageWidth,int imageHeight,int viewWidth,int viewHeight,float pixelAspect){
        if(imageWidth<=0||imageHeight<=0||viewWidth<=0||viewHeight<=0||
                !(pixelAspect>0)||Float.isInfinite(pixelAspect))throw new IllegalArgumentException("invalid review geometry");
        float sy=Math.min(viewWidth*pixelAspect/imageWidth,(float)viewHeight/imageHeight);
        float sx=sy/pixelAspect;
        return new float[]{sx,sy,(viewWidth-imageWidth*sx)/2f,(viewHeight-imageHeight*sy)/2f};
    }
    /** Fit plus centered zoom/pan. Pan is ignored when the zoomed image is smaller than the view. */
    static float[] zoomFit(int imageWidth,int imageHeight,int viewWidth,int viewHeight,float pixelAspect,
                           float zoom,float panX,float panY){
        if(!(zoom>=1)||Float.isInfinite(zoom)||Float.isNaN(zoom))
            throw new IllegalArgumentException("invalid review zoom");
        float[] base=fit(imageWidth,imageHeight,viewWidth,viewHeight,pixelAspect);
        float sx=base[0]*zoom,sy=base[1]*zoom;
        float width=imageWidth*sx,height=imageHeight*sy;
        float x=zoom*base[2]+(1-zoom)*viewWidth/2f;
        float y=zoom*base[3]+(1-zoom)*viewHeight/2f;
        if(width<=viewWidth)x=(viewWidth-width)/2f;
        else x=Math.max(viewWidth-width,Math.min(0,x+panX));
        if(height<=viewHeight)y=(viewHeight-height)/2f;
        else y=Math.max(viewHeight-height,Math.min(0,y+panY));
        return new float[]{sx,sy,x,y};
    }
}
