package org.hongen.sonylutprobe;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import android.widget.ImageView;
import java.lang.reflect.*;

/** Bitmap display that honors the active Sony panel/finder's physical aspect. */
class SonyReviewView extends ImageView {
    private Object displayManager;
    private Class<?> managerType;
    private static final long ZOOM_ANIMATION_MS=220;
    private float pixelAspect=1,zoom=1,panX=0,panY=0;
    private boolean displayKnown;
    private SonyGraphicsPrecision graphicsPrecision;
    private final Matrix fitMatrix=new Matrix();
    // Full-42MP playback: a region tile decoded straight from the original
    // JPEG. The tile covers the viewport plus a margin and is positioned with
    // the same zoom/pan transform as a whole image, so paging and zooming stay
    // continuous while only a few MB are ever allocated.
    private boolean tiled;
    private int imageWidth,imageHeight;          // oriented display size of the base image
    private int tileOrientation,tileRawWidth,tileRawHeight,tileSample;
    private Rect tileRawRect;private float[] tileBounds;
    // Slow panel: animate the matrix ourselves at a fixed 16ms cadence. Sony's
    // own ImageView scale animation recomputes the whole drawable each frame.
    private final Handler animationHandler=new Handler();
    private Runnable zoomTick;private Runnable zoomDone;
    private long zoomStarted;private float zoomFrom,zoomTo;

    SonyReviewView(Context context){super(context);setScaleType(ScaleType.MATRIX);}

    void startDisplayTracking()throws Exception{
        if(displayManager!=null)return;
        managerType=Class.forName("com.sony.scalar.hardware.avio.DisplayManager");
        displayManager=managerType.newInstance();
        try{
            graphicsPrecision=new SonyGraphicsPrecision();graphicsPrecision.acquire();
            Class<?> listenerType=Class.forName("com.sony.scalar.hardware.avio.DisplayManager$DisplayEventListener");
            Object listener=Proxy.newProxyInstance(listenerType.getClassLoader(),new Class<?>[]{listenerType},new InvocationHandler(){
                public Object invoke(Object proxy,Method method,Object[] args){
                    if(method.getDeclaringClass()==Object.class){
                        if(method.getName().equals("hashCode"))return System.identityHashCode(proxy);
                        if(method.getName().equals("equals"))return proxy==args[0];
                        return "ReviewDisplayListener";
                    }
                    post(new Runnable(){public void run(){
                        if(displayManager==null)return;
                        try{readDisplay();}catch(Exception e){displayKnown=false;setVisibility(INVISIBLE);displayFailed(e);}
                    }});
                    return null;
                }
            });
            managerType.getMethod("setDisplayStatusListener",listenerType).invoke(displayManager,listener);
            readDisplay();
        }catch(Exception e){stopDisplayTracking();throw e;}
    }

    private void readDisplay()throws Exception{
        String device=(String)managerType.getMethod("getActiveDevice").invoke(displayManager);
        if(device==null||device.equals(managerType.getField("DEVICE_ID_NONE").get(null)))return;
        Object info=managerType.getMethod("getDeviceInfo",String.class).invoke(displayManager,device);
        int aspect=info.getClass().getField("aspect").getInt(info);
        String[] names={"ASPECT_RATIO_3_2","ASPECT_RATIO_4_3","ASPECT_RATIO_5_3","ASPECT_RATIO_11_9","ASPECT_RATIO_16_9"};
        float[] ratios={3f/2,4f/3,5f/3,11f/9,16f/9};
        float physical=0;
        for(int i=0;i<names.length;i++)if(aspect==managerType.getField(names[i]).getInt(null))physical=ratios[i];
        Display display=((WindowManager)getContext().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        int width=display.getWidth(),height=display.getHeight();
        if(!(physical>0)||width<=0||height<=0)throw new IllegalStateException("unknown Sony display geometry: "+device+"/"+aspect);
        pixelAspect=physical*height/width;displayKnown=true;
        pixelAspectChanged(pixelAspect);
        Log.i("SonyReview","display device="+device+" framebuffer="+width+"x"+height+" physical_aspect="+physical+" pixel_aspect="+pixelAspect);
        fit();
    }

    protected void displayFailed(Exception e){Log.e("SonyReview","display_changed_failed",e);}
    protected void pixelAspectChanged(float value){}

    void showGraphicsOutput()throws Exception{
        // Recipe capture runs without display tracking: the live view is the
        // finder and no review bitmap is shown, so there is nothing to unmute.
        Object manager=displayManager;
        if(manager==null)return;
        // Sony ProgressLayout restores OSD output 300ms after making its UI
        // visible. Capture can mute this plane independently of Android views.
        managerType.getMethod("controlGraphicsOutputAll",boolean.class).invoke(manager,true);
    }

    void stopDisplayTracking(){
        Object manager=displayManager;displayManager=null;displayKnown=false;
        if(graphicsPrecision!=null){try{graphicsPrecision.release();graphicsPrecision=null;}
            catch(Exception e){Log.e("SonyReview","graphics_precision_restore_failed",e);}}
        if(manager==null)return;
        try{managerType.getMethod("releaseDisplayStatusListener").invoke(manager);}
        catch(Exception e){Log.w("SonyReview","listener_release_failed",e);}
        finally{
            try{managerType.getMethod("finish").invoke(manager);}
            catch(Exception e){Log.w("SonyReview","display_finish_failed",e);}
        }
    }

    void setZoom(float factor,float x,float y){
        stopZoomAnimation();
        zoom=factor<1?1:factor;panX=x;panY=y;fit();
    }
    void resetZoom(){stopZoomAnimation();zoom=1;panX=panY=0;fit();}
    /** Ease-out zoom used by C3 and the control dial. */
    void animateZoom(float factor,Runnable done){
        stopZoomAnimation();
        float target=factor<1?1:factor;
        if(Math.abs(target-zoom)<.001f){
            if(target<=1.001f){panX=panY=0;}
            fit();if(done!=null)done.run();return;
        }
        zoomFrom=zoom;zoomTo=target;zoomStarted=SystemClock.elapsedRealtime();zoomDone=done;
        zoomTick=new Runnable(){public void run(){
            float t=(SystemClock.elapsedRealtime()-zoomStarted)/(float)ZOOM_ANIMATION_MS;
            if(t>=1f){
                zoom=zoomTo;if(zoom<=1.001f){panX=panY=0;}zoomTick=null;fit();invalidate();
                Runnable finished=zoomDone;zoomDone=null;if(finished!=null)finished.run();
                return;
            }
            float eased=1f-(1f-t)*(1f-t)*(1f-t);
            zoom=zoomFrom+(zoomTo-zoomFrom)*eased;
            fit();invalidate();
            animationHandler.postDelayed(this,16);
        }};
        animationHandler.post(zoomTick);
    }
    private void stopZoomAnimation(){
        if(zoomTick!=null){animationHandler.removeCallbacks(zoomTick);zoomTick=null;}
        zoomDone=null;
    }
    float zoom(){return zoom;}
    boolean zoomed(){return zoom>1.001f;}
    void panBy(float dx,float dy){
        if(zoomTick!=null){zoom=zoomTo;stopZoomAnimation();fit();} // finish zoom first
        if(!zoomed())return;
        Drawable image=getDrawable();
        if(image==null||getWidth()==0||getHeight()==0)return;
        int iw=tiled?imageWidth:image.getIntrinsicWidth(),ih=tiled?imageHeight:image.getIntrinsicHeight();
        float[] base=ReviewGeometry.fit(iw,ih,getWidth(),getHeight(),pixelAspect);
        float contentW=iw*base[0]*zoom,contentH=ih*base[1]*zoom;
        panX=ControlMath.clampPan(panX+dx,contentW,getWidth());
        panY=ControlMath.clampPan(panY+dy,contentH,getHeight());
        fit();
    }
    @Override public void setImageBitmap(Bitmap bitmap){
        stopZoomAnimation();
        // Android 4.1 wraps even null in a BitmapDrawable whose intrinsic
        // dimensions are invalid. Clearing is an empty-view operation, not a fit.
        clearTile();
        if(bitmap==null){zoom=1;panX=panY=0;super.setImageDrawable(null);fitMatrix.reset();setImageMatrix(fitMatrix);return;}
        zoom=1;panX=panY=0;imageWidth=bitmap.getWidth();imageHeight=bitmap.getHeight();
        super.setImageBitmap(bitmap);fit();
    }
    /** Swap in the higher-resolution copy of the same photo without losing
     * the user's zoom/pan position. */
    void replaceImageBitmap(Bitmap bitmap){
        if(bitmap==null){setImageBitmap(null);return;}
        clearTile();imageWidth=bitmap.getWidth();imageHeight=bitmap.getHeight();
        super.setImageBitmap(bitmap);fit();
        Log.i("SonyReview","replace_detail image="+bitmap.getWidth()+"x"+bitmap.getHeight()+" zoom="+zoom);
    }
    private void clearTile(){tiled=false;tileRawRect=null;tileBounds=null;}
    /** True when this exact bitmap is the current drawable (safe to recycle
     * only when it is not). */
    boolean displays(Bitmap bitmap){
        Drawable drawable=getDrawable();
        return drawable instanceof BitmapDrawable&&((BitmapDrawable)drawable).getBitmap()==bitmap;
    }
    /** Show a raw JPEG region in place of the sampled image. The tile is
     * positioned by source coordinates, so it stays registered with the
     * zoom/pan transform. imageW/imageH are the oriented full-image size. */
    void setTileBitmap(Bitmap tile,int imageW,int imageH,int orientation,
                       int rawWidth,int rawHeight,Rect rawRect,int sample){
        if(tile==null)return;
        super.setImageBitmap(tile);
        imageWidth=imageW;imageHeight=imageH;
        tiled=true;tileOrientation=orientation;tileRawWidth=rawWidth;tileRawHeight=rawHeight;
        tileRawRect=new Rect(rawRect);tileSample=sample;
        tileBounds=PhotoTiles.orientedBounds(rawWidth,rawHeight,orientation,
                new int[]{rawRect.left,rawRect.top,rawRect.right,rawRect.bottom});
        fit();
        Log.i("SonyReview","tile_attach bitmap="+tile.getWidth()+"x"+tile.getHeight()+" sample="+sample
                +" raw="+rawRect.left+","+rawRect.top+" "+rawRect.width()+"x"+rawRect.height()
                +" oriented="+imageW+"x"+imageH+" zoom="+zoom);
    }
    /** Visible rectangle as fractions of the oriented full image, expanded by
     * margin on every side. Used to choose the next tile to decode. */
    float[] visibleFraction(float margin){
        if(!displayKnown||getWidth()==0||getHeight()==0||imageWidth<=0||imageHeight<=0)return null;
        float[] view=ReviewGeometry.zoomFit(imageWidth,imageHeight,getWidth(),getHeight(),pixelAspect,zoom,panX,panY);
        if(!(view[0]>0)||!(view[1]>0))return null;
        float left=(-view[2])/view[0]/imageWidth;
        float top=(-view[3])/view[1]/imageHeight;
        float right=(getWidth()-view[2])/view[0]/imageWidth;
        float bottom=(getHeight()-view[3])/view[1]/imageHeight;
        float mx=Math.max(0,margin)*(right-left),my=Math.max(0,margin)*(bottom-top);
        return new float[]{Math.max(0,left-mx),Math.max(0,top-my),
                Math.min(1,right+mx),Math.min(1,bottom+my)};
    }
    /** Whether the attached tile already covers the given visible fractions. */
    boolean tileCovers(float[] visible){
        if(!tiled||tileBounds==null||visible==null||imageWidth<=0||imageHeight<=0)return false;
        float[] bounds={tileBounds[0]/imageWidth,tileBounds[1]/imageHeight,
                tileBounds[2]/imageWidth,tileBounds[3]/imageHeight};
        return PhotoTiles.inside(bounds,visible,0.002f);
    }
    int tileOrientation(){return tileOrientation;}
    int tileRawWidth(){return tileRawWidth;}
    int tileRawHeight(){return tileRawHeight;}
    int tileSample(){return tileSample;}
    boolean tiled(){return tiled;}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);fit();}
    @Override protected void onDetachedFromWindow(){stopZoomAnimation();super.onDetachedFromWindow();}

    private void fit(){
        Drawable image=getDrawable();
        if(!displayKnown||image==null||getWidth()==0||getHeight()==0)return;
        if(tiled&&tileRawRect!=null){
            float[] view=ReviewGeometry.zoomFit(imageWidth,imageHeight,getWidth(),getHeight(),pixelAspect,zoom,panX,panY);
            // bitmap pixel -> raw JPEG pixel -> oriented image pixel -> view
            Matrix tileMatrix=new Matrix();
            tileMatrix.setScale(tileSample,tileSample);
            tileMatrix.postTranslate(tileRawRect.left,tileRawRect.top);
            tileMatrix.postConcat(orientationMatrix(tileOrientation,tileRawWidth,tileRawHeight));
            Matrix viewMatrix=new Matrix();
            viewMatrix.setScale(view[0],view[1]);viewMatrix.postTranslate(view[2],view[3]);
            tileMatrix.postConcat(viewMatrix);
            fitMatrix.set(tileMatrix);setImageMatrix(fitMatrix);
            Log.i("SonyReview","fit_tile image="+imageWidth+"x"+imageHeight+" view="+getWidth()+"x"+getHeight()
                    +" zoom="+zoom+" scale="+view[0]+","+view[1]+" offset="+view[2]+","+view[3]);
            return;
        }
        int w=image.getIntrinsicWidth(),h=image.getIntrinsicHeight();
        float[] fit=ReviewGeometry.zoomFit(w,h,getWidth(),getHeight(),pixelAspect,zoom,panX,panY);
        fitMatrix.setScale(fit[0],fit[1]);fitMatrix.postTranslate(fit[2],fit[3]);setImageMatrix(fitMatrix);
        Log.i("SonyReview","fit image="+w+"x"+h+" view="+getWidth()+"x"+getHeight()+" zoom="+zoom+" scale="+fit[0]+","+fit[1]+" offset="+fit[2]+","+fit[3]);
    }
    /** Raw JPEG pixels -> oriented pixels, matching ReviewLoader.rotate(). */
    private static Matrix orientationMatrix(int orientation,int rawWidth,int rawHeight){
        Matrix m=new Matrix();
        if(orientation==3){m.setRotate(180);m.postTranslate(rawWidth,rawHeight);}
        else if(orientation==6){m.setRotate(90);m.postTranslate(rawHeight,0);}
        else if(orientation==8){m.setRotate(270);m.postTranslate(0,rawWidth);}
        return m;
    }
}
