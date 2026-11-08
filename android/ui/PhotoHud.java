package org.hongen.sonylutprobe;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import android.os.SystemClock;

/** Overlay only: never takes space away from the image or viewfinder.
 *
 * Film-camera visual language: charcoal status strips, amber highlight,
 * seven-segment exposure digits and a darkroom light for the develop stage. */
final class PhotoHud extends View {
    private static final String[] PROCESSING_LABELS={"拍摄","显影","调色","保存"};
    private static final String[] RECIPE_LABELS={"拍摄","写入","保存","完成"};
    String mode="loading",name="A7R2胶片相机",detail="正在准备拍摄",footer="",progress="",tag="";
    CaptureProgress.Stage processingStage=CaptureProgress.Stage.CAPTURE;
    boolean liveInfoVisible=true;
    /** Recipe capture has no darkroom stages: JPEG is written by the camera. */
    boolean recipeMode;
    String liveExposure="",liveCompensation="+0.0 EV";
    CaptureReadout readout=new CaptureReadout();
    private String liveHint="";
    private boolean attached;
    private final DevelopingPreview developingPreview=new DevelopingPreview();
    private boolean animationQueued,imageDirty,wasMoving,fullFramePending=true;
    private String lastMode="";
    private long lastDevelopFrameAt,developFrames,developCpuNs,developMaxNs,developShot;
    private boolean liveShutterRunning;private long liveShutterAt;
    private String toastText="";private long toastAt;private long wbMenuAt;
    void beginDeveloping(long shot,boolean enabled){
        endDeveloping();developingPreview.begin(shot,enabled);developShot=shot;
        developFrames=developCpuNs=developMaxNs=0;
    }
    void sourceDeveloping(long shot,DevelopingPreview.Source source){
        if(developingPreview.source(shot,source,SystemClock.uptimeMillis())){imageDirty=true;update();}
    }
    void gradedDeveloping(long shot,Bitmap bitmap){if(developingPreview.graded(shot,bitmap,SystemClock.uptimeMillis())){imageDirty=true;update();}}
    void endDeveloping(){
        if(developFrames>0)android.util.Log.i("LutUI","develop_render shot="+developShot+" frames="+developFrames
                +" cpu_ms="+(developCpuNs/1000000.0)+" max_wall_ms="+(developMaxNs/1000000.0)+" bytes="+developingPreview.bytes());
        developFrames=0;developingPreview.end();stopAnimation();removeCallbacks(curtainStart);fullFramePending=true;
    }
    private void stopAnimation(){removeCallbacks(animateDeveloping);animationQueued=false;lastDevelopFrameAt=0;wasMoving=false;}
    private boolean processingVisible(){return attached&&getVisibility()==VISIBLE&&getWindowVisibility()==VISIBLE&&mode.equals("processing");}
    private void requestDevelopFrame(){
        if(animationQueued||!processingVisible())return;
        animationQueued=true;postDelayed(animateDeveloping,Math.max(0,DevelopTimeline.FRAME_MS-(SystemClock.uptimeMillis()-lastDevelopFrameAt)));
    }
    private final RectF frame=new RectF();
    private final Runnable clearLiveHint=new Runnable(){public void run(){hideLiveHint();}};
    private final Runnable liveShutterTick=new Runnable(){public void run(){
        if(!liveShutterRunning||SystemClock.uptimeMillis()-liveShutterAt>460){liveShutterRunning=false;invalidate();return;}
        invalidate();postDelayed(this,16);
    }};
    /** Shutter opens over the current frame; used when playback opens a photo. */
    void openCurtain(){
        liveShutterAt=SystemClock.uptimeMillis();liveShutterRunning=true;
        removeCallbacks(liveShutterTick);postDelayed(liveShutterTick,16);
    }
    private final Runnable curtainStart=new Runnable(){public void run(){
        liveShutterAt=SystemClock.uptimeMillis();liveShutterRunning=true;
        removeCallbacks(liveShutterTick);postDelayed(liveShutterTick,16);
    }};
    /** Hold the opening curtain until Sony returns the graphics plane.
     * Starting it at the shutter press means the camera's own blackout hides
     * all but the first frame (observed on a7r2.27). */
    void openCurtainDelayed(long delayMs){
        liveShutterRunning=false;removeCallbacks(liveShutterTick);removeCallbacks(curtainStart);
        postDelayed(curtainStart,Math.max(0,delayMs));
    }
    private final Runnable animateDeveloping=new Runnable(){public void run(){
        animationQueued=false;if(!processingVisible())return;
        lastDevelopFrameAt=SystemClock.uptimeMillis();
        boolean moving=developingPreview.moving(lastDevelopFrameAt);
        if(fullFramePending||getHeight()==0)invalidate();
        else{
            // Keep the static negative intact while the capture worker runs
            // LUT. Clock/stage ticks only damage the controls below the photo.
            int top=(int)((imageDirty||moving||wasMoving?76:390)*getHeight()/480f);
            invalidate(0,top,getWidth(),getHeight());
        }
        fullFramePending=false;imageDirty=false;wasMoving=moving;
        if(moving)requestDevelopFrame();
    }};
    boolean original,single;float pixelAspect=1;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint latin=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cjk=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cap=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokeP=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rr=new RectF();
    boolean manualExposure,afVisible,afSelecting;int afX,afY;
    /** Recipe finder: film name sits in the free top-centre space. While C3
     * selection is open it also shows the position inside the camera group. */
    boolean filmSelecting;String filmHudName="";int filmHudPosition,filmHudCount;
    private String filmHudPrevious="",filmHudNext="";
    // a7r2.47: a dial turn slides the neighbouring canisters through the strip.
    // Three vector cards only, rendered for ~190 ms at 30 fps; no bitmaps.
    private static final long FILM_SLIDE_MS=190L;
    private long filmAnimAt;private int filmAnimDirection;
    // Recipe capture feedback is part of the live finder: a shutter curtain and
    // a small write-time chip. The old darkroom page and its develop stages
    // belong to LUT mode and must never appear for a camera-native JPEG.
    private boolean recipeCapturing;private long recipeCaptureAt;private String recipeCaptureText="正在写入照片";
    void setFilmStatus(String film,int position,int count,boolean selecting){
        setFilmStatus(film,position,count,selecting,"","");
    }
    void setFilmStatus(String film,int position,int count,boolean selecting,String previous,String next){
        long now=SystemClock.uptimeMillis();
        int old=filmHudPosition;boolean wasSelecting=filmSelecting;
        filmHudName=film==null?"":film;
        filmHudPrevious=previous==null?"":previous;
        filmHudNext=next==null?"":next;
        filmHudPosition=Math.max(0,position);filmHudCount=Math.max(0,count);
        if(selecting&&!wasSelecting){filmAnimAt=now;filmAnimDirection=0;}
        else if(selecting&&wasSelecting&&count>0&&position!=old){
            filmAnimDirection=position>old?1:-1;filmAnimAt=now;
        }
        if(wasSelecting!=selecting)fullFramePending=true;
        filmSelecting=selecting;update();
    }
    void beginRecipeCapture(long shot){
        endRecipeCapture();recipeCapturing=true;recipeCaptureAt=SystemClock.uptimeMillis();
        recipeCaptureText="正在写入照片";openCurtain();
        fullFramePending=true;update();
        removeCallbacks(recipeCaptureTick);postDelayed(recipeCaptureTick,150);
    }
    void setRecipeCaptureText(String text){
        if(text==null||text.length()==0||text.equals(recipeCaptureText))return;
        recipeCaptureText=text;update();
    }
    void endRecipeCapture(){
        if(!recipeCapturing)return;
        recipeCapturing=false;removeCallbacks(recipeCaptureTick);
        fullFramePending=true;update();
    }
    private final Runnable recipeCaptureTick=new Runnable(){public void run(){
        if(!recipeCapturing)return;
        int h=getHeight();
        if(h>0)invalidate(0,h*44/480,getWidth(),h*84/480);
        postDelayed(this,150);
    }};
    int loadingStep;String loadingStage="正在装卷",filmName="";int filmIndex;
    void setLoadingStage(String stage,int step){
        if(stage==null)return;
        loadingStage=stage;loadingStep=Math.max(0,Math.min(3,step));
        fullFramePending=true;update();
    }
    void setFilmLoading(String film,int index){
        filmName=film==null?"":film;filmIndex=Math.max(0,index);fullFramePending=true;update();
    }
    boolean wbMenuVisible;String[] wbItems=new String[0];int wbSelected;String wbFooter="↑↓ 选择 · ● 确认 · →/MENU 取消";
    PhotoHud(Context context){
        super(context);
        cjk.setTypeface(FilmStyle.cjk(context));cjk.setTextAlign(Paint.Align.LEFT);
        latin.setTypeface(FilmStyle.condensed(context));latin.setTextAlign(Paint.Align.LEFT);
        p.setTypeface(FilmStyle.cjk(context));p.setTextAlign(Paint.Align.LEFT);
        cap.setTypeface(FilmStyle.latinRegular(context));
        strokeP.setStyle(Paint.Style.STROKE);strokeP.setStrokeJoin(Paint.Join.ROUND);
    }
    void update(){
        boolean entering=!mode.equals(lastMode);
        if(entering){
            fullFramePending=true;lastMode=mode;
            if(mode.equals("live")&&attached){
                // Shutter opens over the live view once, like a film camera
                // coming up from black. Only two rects per frame.
                // "processing" is armed separately by the capture flow, after
                // Sony restores its graphics plane, so the animation is seen.
                liveShutterAt=SystemClock.uptimeMillis();liveShutterRunning=true;
                removeCallbacks(liveShutterTick);postDelayed(liveShutterTick,16);
            }
        }
        if(processingVisible())requestDevelopFrame();
        else{stopAnimation();invalidate();}
    }
    private final Runnable toastTick=new Runnable(){public void run(){
        if(toastText.length()==0)return;
        if(SystemClock.uptimeMillis()-toastAt<2100){invalidate();postDelayed(this,40);}
        else{toastText="";invalidate();}
    }};
    /** Short confirmation drawn over the image, independent of mode. */
    void toast(String text){
        if(text==null||text.length()==0)return;
        toastText=text;toastAt=SystemClock.uptimeMillis();
        removeCallbacks(toastTick);postDelayed(toastTick,40);invalidate();
    }
    void showLiveHint(String value){showPersistentLiveHint(value);postDelayed(clearLiveHint,3000);}
    void showPersistentLiveHint(String value){removeCallbacks(clearLiveHint);liveHint=value;invalidate();}
    void hideLiveHint(){removeCallbacks(clearLiveHint);liveHint="";invalidate();}
    void setAf(boolean visible,boolean selecting,int x,int y){
        afVisible=visible;afSelecting=selecting;afX=x;afY=y;fullFramePending=true;update();
    }
    void showWbMenu(String[] items,int selected,String footer){
        wbMenuVisible=true;wbItems=items==null?new String[0]:items;wbSelected=selected;
        if(footer!=null&&footer.length()>0)wbFooter=footer;
        wbMenuAt=SystemClock.uptimeMillis();
        fullFramePending=true;update();
    }
    void setWbSelection(int selected){wbSelected=selected;fullFramePending=true;update();}
    void closeWbMenu(){if(!wbMenuVisible)return;wbMenuVisible=false;fullFramePending=true;update();}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();attached=true;fullFramePending=true;update();}
    @Override protected void onVisibilityChanged(View changed,int visibility){super.onVisibilityChanged(changed,visibility);if(changed==this){fullFramePending=true;update();}}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);fullFramePending=true;update();}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);fullFramePending=true;update();}
    @Override protected void onDetachedFromWindow(){attached=false;endDeveloping();endRecipeCapture();removeCallbacks(clearLiveHint);removeCallbacks(liveShutterTick);removeCallbacks(curtainStart);removeCallbacks(toastTick);super.onDetachedFromWindow();}
    private void chip(Canvas c,String value,float x,float y,float maxWidth,int size,boolean alignRight){
        String label=fit(value,maxWidth-20,size);p.setTextSize(size);float width=p.measureText(label)+20;
        float left=alignRight?x-width:x;
        p.setColor(0xb8141210);c.drawRoundRect(new RectF(left,y,left+width,y+32),5,5,p);
        p.setColor(FilmStyle.ACCENT);c.drawRect(left,y+30,left+width,y+32,p);
        text(c,label,left+10,y+21,size,FilmStyle.TEXT);
    }
    private void battery(Canvas c,float x,float y){FilmStyle.battery(c,p,readout.battery,readout.charging,x,y);}
    private void label(Canvas c,String value,float x,float y,int size,int color,Paint.Align align){
        value=Lang.tr(value);
        p.setTextAlign(align);p.setTextSize(size);
        if(mode.equals("live")){
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.5f);p.setColor(0xe0000000);
            c.drawText(value,x,y,p);p.setStyle(Paint.Style.FILL);
        }
        p.setColor(color);c.drawText(value,x,y,p);
    }
    private void text(Canvas c,String value,float x,float y,int size,int color){label(c,value,x,y,size,color,Paint.Align.LEFT);}
    private void right(Canvas c,String value,float x,float y,int size,int color){label(c,value,x,y,size,color,Paint.Align.RIGHT);}
    private void center(Canvas c,String value,float x,float y,int size,int color){label(c,value,x,y,size,color,Paint.Align.CENTER);}
    private String fit(String s,float width,int size){return FilmStyle.fit(p,s,width,size);}

    /** Staged film-loading page for the capture process. The old screen was
     * plain charcoal, which read as "the animation ended but nothing is
     * happening". Keep the canister and film-edge language while CameraEx
     * opens; stages advance between the blocking Sony calls. */
    private void drawLoading(Canvas c,float w){
        p.setStyle(Paint.Style.FILL);p.setColor(FilmStyle.BG);c.drawRect(0,0,w,480,p);
        p.setColor(0xff12110f);c.drawRect(0,0,w,46,p);c.drawRect(0,432,w,480,p);
        p.setColor(FilmStyle.LINE);c.drawRect(0,45,w,46,p);c.drawRect(0,431,w,432,p);
        p.setColor(0xff2b241d);
        for(float x=8;x<w-8;x+=38){
            c.drawRoundRect(new RectF(x,14,x+14,20),1.5f,1.5f,p);
            c.drawRoundRect(new RectF(x,452,x+14,458),1.5f,1.5f,p);
        }
        text(c,"FILM LOADING",28,31,12,FilmStyle.ACCENT);
        right(c,fit("A7R2 · "+name,w*0.46f,12),w-28,31,12,FilmStyle.MUTED);
        float cx=w/2f;
        // Film leader behind the canister: the same shape the shelf animation
        // pulls out, so the hand-over to this page reads as one continuous
        // loading sequence rather than a second, unrelated screen.
        float stripTop=176,stripBottom=286;
        p.setColor(0xff17140f);c.drawRect(24,stripTop,w-24,stripBottom,p);
        for(float x=28;x<w-52;x+=52){
            p.setColor(0xff201c16);c.drawRect(x+3,stripTop+12,x+49,stripBottom-12,p);
            p.setColor(0xff0d0b09);c.drawRect(x+50,stripTop+8,x+52,stripBottom-8,p);
            p.setColor(0xffc9c2b4);
            c.drawRect(x+6,stripTop+4,x+16,stripTop+9,p);c.drawRect(x+6,stripBottom-9,x+16,stripBottom-4,p);
        }
        drawLoadingCanister(c,cx,314,1.22f);
        center(c,fit(loadingStage,w-120,20),cx,352,20,FilmStyle.TEXT);
        String film=filmName.length()>0?filmName:name;
        center(c,fit(film,w-140,14),cx,375,14,FilmStyle.MUTED);
        String[] steps={"装卷","相机","设置","取景"};
        float start=cx-2*74+37;
        for(int i=0;i<4;i++){
            float x=start+i*74;
            p.setColor(i<loadingStep?FilmStyle.ACCENT:i==loadingStep?0xff8a6a2a:FilmStyle.DIM);
            c.drawCircle(x,400,i==loadingStep?4.5f:3.2f,p);
            center(c,steps[i],x,421,11,i<=loadingStep?FilmStyle.TEXT:FilmStyle.DIM);
        }
    }
    private void drawLoadingCanister(Canvas c,float cx,float base,float sc){
        c.save();c.translate(cx,base);c.scale(sc,sc);
        p.setStyle(Paint.Style.FILL);
        p.setColor(FilmStyle.SHADOW);rr.set(-52,-4,52,10);c.drawRoundRect(rr,9,7,p);
        p.setColor(0xff2a2722);rr.set(-46,-170,46,0);c.drawRoundRect(rr,14,14,p);
        int colour=FilmStyle.canColour(filmIndex);
        p.setColor(colour);rr.set(-41,-165,41,-6);c.drawRoundRect(rr,11,11,p);
        p.setColor(0xffc9c2b4);c.drawRect(-41,-166,41,-159,p);c.drawRect(-41,-13,41,-6,p);
        p.setColor(0xff2a2722);rr.set(-41,-174,41,-158);c.drawRoundRect(rr,7,7,p);
        p.setColor(0xff6d675c);rr.set(-34,-172,34,-160);c.drawRoundRect(rr,5,5,p);
        p.setColor(0xff161310);rr.set(-13,-171,13,-162);c.drawRoundRect(rr,4,4,p);
        p.setColor(0xffe7dcc8);c.drawRect(-40,-158,40,-58,p);
        p.setColor(0x22000000);c.drawRect(-40,-158,40,-150,p);c.drawRect(-40,-66,40,-58,p);
        center(c,"35-36",0,-92,15,0xff5a4a3a);
        c.restore();
    }

    private void developing(Canvas c,float w){
        p.setColor(0xff1a0c0a);c.drawRect(0,0,w,480,p);
        // Darkroom safelight in the upper right.
        p.setColor(0x1cd9473f);c.drawCircle(w-96,90,86,p);
        p.setColor(0x33d9473f);c.drawCircle(w-96,90,42,p);
        p.setColor(0xffd9473f);c.drawCircle(w-96,90,7,p);
        text(c,"DARKROOM",28,36,14,FilmStyle.RED);
        right(c,fit(name,w/2-50,15),w-28,36,15,FilmStyle.MUTED);
        p.setColor(0xff3a1c16);c.drawRect(28,54,w-28,55,p);
        float center=w/2,fw=Math.min(450,w-96),fh=fw*2/3,left=center-fw/2,top=82;
        frame.set(left,top,left+fw,top+fh);
        p.setColor(0xff100806);c.drawRect(frame,p);
        // Paper border around the developing print.
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);p.setColor(0xff4a211a);c.drawRect(frame,p);p.setStyle(Paint.Style.FILL);
        if(developingPreview.hasSource()&&!c.quickReject(frame,Canvas.EdgeType.BW)){
            long wall=System.nanoTime(),cpu=android.os.Debug.threadCpuTimeNanos();
            try{developingPreview.draw(c,frame,SystemClock.uptimeMillis());}
            catch(RuntimeException e){android.util.Log.w("LutUI","develop_draw_disabled",e);developingPreview.end();}
            catch(OutOfMemoryError e){android.util.Log.w("LutUI","develop_draw_disabled",e);developingPreview.end();}
            developCpuNs+=android.os.Debug.threadCpuTimeNanos()-cpu;
            developMaxNs=Math.max(developMaxNs,System.nanoTime()-wall);developFrames++;
        }
        // A safelight scan line travels with the reveal boundary while the
        // graded copy fades in; then the print is settled.
        long now=SystemClock.uptimeMillis();
        float reveal=developingPreview.reveal(now);
        if(reveal>0f&&reveal<1f){
            float ly=frame.top+frame.height()*reveal;
            p.setColor(0x33d9473f);c.drawRect(frame.left,ly-6,frame.right,ly+6,p);
            p.setColor(0xffd9473f);c.drawRect(frame.left,ly-1,frame.right,ly+1,p);
            p.setColor(0xffffb0a4);c.drawRect(frame.left,ly,frame.right,ly+.6f,p);
        }
        center(c,fit(detail,w-96,18),center,412,18,FilmStyle.TEXT);
        // Film-frame progress: four sprocketed frames light up in sequence.
        int active=processingStage.ordinal();float fw2=Math.min(300,w-160)/4f,start=center-fw2*2,y=432;
        String[] labels=recipeMode?RECIPE_LABELS:PROCESSING_LABELS;
        for(int i=0;i<4;i++){
            float x=start+i*fw2+6;
            p.setColor(i<active?0xff5a2a20:i==active?0xff8a3a2c:0xff2a1712);
            c.drawRoundRect(new RectF(x,y,x+fw2-12,y+30),4,4,p);
            p.setColor(i<=active?0xffd9473f:0xff3a201a);
            c.drawRect(x+4,y+3,x+7,y+7,p);c.drawRect(x+fw2-19,y+3,x+fw2-16,y+7,p);
            c.drawRect(x+4,y+23,x+7,y+27,p);c.drawRect(x+fw2-19,y+23,x+fw2-16,y+27,p);
            center(c,labels[i],x+(fw2-12)/2,y+21,11,i<=active?FilmStyle.TEXT:FilmStyle.DIM);
        }
        right(c,progress,w-28,409,16,FilmStyle.RED);
    }
    @Override protected void onDraw(Canvas c){
        float scale=getHeight()/480f;if(scale<=0)return;float w=getWidth()*pixelAspect/scale;
        c.save();c.scale(scale/pixelAspect,scale);
        boolean full=mode.equals("processing")||mode.equals("loading")||mode.equals("error");
        if(full){
            if(mode.equals("processing"))developing(c,w);
            else if(mode.equals("loading")){
                // CameraEx takes a few seconds to open. Keep the film-loading
                // language here instead of a blank panel: the canister stays on
                // screen and the stage label advances between blocking calls.
                drawLoading(c,w);
            }else{
                p.setColor(FilmStyle.BG);c.drawRect(0,0,w,480,p);
                FilmStyle.topBar(c,cjk,w,mode.equals("error")?"错误":"正在准备",null);
                text(c,fit(detail,w-72,28),36,190,28,mode.equals("error")?FilmStyle.ERROR:FilmStyle.TEXT);
                text(c,fit(progress,w-72,18),36,240,18,FilmStyle.ACCENT);
                text(c,fit(name,w-72,16),36,282,16,FilmStyle.MUTED);
                text(c,fit(footer,w-72,15),36,443,15,FilmStyle.MUTED);
            }
        }else if(mode.equals("live")){
            if(liveInfoVisible){
                p.setColor(0x880d0c0a);c.drawRect(0,0,w,44,p);
                p.setColor(0x33100e0c);c.drawRect(0,44,w,48,p);
                // Mode badge and current film.
                // Mode badge: dark chip with a hairline amber frame, matching
                // the film-paper labels instead of a solid yellow block.
                p.setColor(0xcc1d1a16);c.drawRoundRect(new RectF(14,10,48,38),4,4,p);
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.2f);p.setColor(FilmStyle.ACCENT);
                c.drawRoundRect(new RectF(14.6f,10.6f,47.4f,37.4f),4,4,p);p.setStyle(Paint.Style.FILL);
                center(c,manualExposure&&liveExposure.length()>0?liveExposure:"P",31,30,20,FilmStyle.ACCENT);
                if(recipeMode)drawFilmTitle(c,w);
                else text(c,fit(name,w-230,17),60,31,17,FilmStyle.TEXT);
                battery(c,w-104,17);
                // Right-hand metering scale.
                drawMeter(c,w);
                // Bottom seven-segment strip.
                p.setColor(0xa80d0c0a);c.drawRect(0,420,w,480,p);
                p.setColor(FilmStyle.ACCENT);c.drawRect(0,420,w,422,p);
                // Sprocket perforations borrowed from the contact sheet, so
                // the finder's lower chrome reads as the edge of a film strip.
                p.setColor(0xff2b241d);
                for(float x=8;x<w-8;x+=38)c.drawRoundRect(new RectF(x,423,x+14,429),1.5f,1.5f,p);
                drawExposureStrip(c,w);
            }
            if(recipeMode&&!liveInfoVisible&&filmSelecting)drawFilmTitle(c,w);
            if(recipeMode&&filmSelecting)drawFilmStrip(c,w);
            if(recipeCapturing)drawRecipeCapture(c,w);
            if(footer.length()>0)label(c,fit(footer,w-56,13),28,410,13,FilmStyle.TEXT,Paint.Align.LEFT);
            drawAf(c,w);
            if(wbMenuVisible)drawWbMenu(c,w);
            if(liveHint.length()>0&&!wbMenuVisible)chip(c,liveHint,12,370,w-24,14,false);
        }else{
            boolean hasDetail=detail.length()>0,hasTag=tag.length()>0;
            p.setColor(0xb00d0c0a);c.drawRect(0,0,w,58,p);
            p.setColor(FilmStyle.ACCENT);c.drawRect(0,56,120,58,p);
            text(c,"A7R2",24,25,12,FilmStyle.ACCENT);text(c,fit(name,w-(hasTag?230:48),20),24,47,20,FilmStyle.TEXT);
            if(hasTag)right(c,fit(tag,180,17),w-24,40,17,FilmStyle.ACCENT);
            // The review footer is a film edge too: charcoal stock with a
            // sprocket row, rather than the old solid amber band.
            float barTop=hasDetail?392:436;
            p.setColor(0xe60d0c0a);c.drawRect(0,barTop,w,480,p);
            p.setColor(FilmStyle.ACCENT);c.drawRect(0,barTop,w,barTop+2,p);
            p.setColor(0xff2b241d);
            for(float x=8;x<w-8;x+=38)c.drawRoundRect(new RectF(x,barTop+4,x+14,barTop+10),1.5f,1.5f,p);
            if(hasDetail)text(c,fit(detail,w-48,17),24,418,17,FilmStyle.TEXT);
            text(c,fit(footer,w-48,14),24,hasDetail?452:462,14,FilmStyle.MUTED);
            if(mode.equals("review")&&!single){
                // Original / LUT switch: a dark paper tab with a thin amber
                // underline for the active half. Avoids the previous solid
                // yellow block that dominated the playback page.
                float x=24,y=78,bw=184,bh=34;
                p.setColor(0xd81d1a16);c.drawRoundRect(new RectF(x,y,x+bw,y+bh),6,6,p);
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);p.setColor(FilmStyle.LINE);
                c.drawRoundRect(new RectF(x+.5f,y+.5f,x+bw-.5f,y+bh-.5f),6,6,p);p.setStyle(Paint.Style.FILL);
                p.setColor(0xff2b241d);
                c.drawRoundRect(new RectF(original?x+3:x+bw/2+2,y+3,original?x+bw/2-2:x+bw-3,y+bh-3),4,4,p);
                p.setColor(FilmStyle.ACCENT);
                if(original)c.drawRect(x+10,y+bh-5,x+bw/2-10,y+bh-3,p);
                else c.drawRect(x+bw/2+10,y+bh-5,x+bw-10,y+bh-3,p);
                center(c,"原片",x+bw/4,y+23,15,original?FilmStyle.ACCENT:FilmStyle.MUTED);
                center(c,"冲洗成片",x+bw*3/4,y+23,15,original?FilmStyle.MUTED:FilmStyle.ACCENT);
            }
        }
        drawToast(c,w);
        drawLiveShutter(c,w);
        c.restore();
    }
    private void drawFilmTitle(Canvas c,float w){
        String value=filmHudName.length()>0?filmHudName:name;
        if(value.length()==0)return;
        if(!filmSelecting){
            center(c,fit(value,w*0.46f,17),w/2f,31,17,FilmStyle.TEXT);
            return;
        }
        String position=filmHudCount>0
                ?String.format(java.util.Locale.US,"%02d/%02d",Math.min(filmHudPosition,filmHudCount),filmHudCount)
                :"";
        String title=position.length()>0?"胶片 "+position+"  "+value:"胶片  "+value;
        p.setTextSize(16);String shown=fit(title,w*0.5f,16);
        float tw=p.measureText(shown),cx=w/2f;
        float left=cx-tw/2-10,right=cx+tw/2+10;
        p.setColor(0xcc1d1a16);c.drawRoundRect(new RectF(left,8,right,38),5,5,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.2f);p.setColor(FilmStyle.ACCENT);
        c.drawRoundRect(new RectF(left+.6f,8.6f,right-.6f,37.4f),5,5,p);p.setStyle(Paint.Style.FILL);
        center(c,shown,cx,31,16,FilmStyle.ACCENT);
    }
    /** Horizontal contact strip for C3 selection. The three cells are the
     * previous/current/next recipe inside the same camera group; a dial or
     * arrow step slides them by one cell with a short ease-out. */
    private void drawFilmStrip(Canvas c,float w){
        long now=SystemClock.uptimeMillis();
        long age=filmAnimAt<=0?FILM_SLIDE_MS:now-filmAnimAt;
        float k=FilmStyle.easeOut(age/(float)FILM_SLIDE_MS);
        float slide=(1-k)*filmAnimDirection*38f;
        float top=48,bottom=126,cy=(top+bottom)/2f+(1-k)*6f;
        p.setStyle(Paint.Style.FILL);p.setColor(0xcc0d0c0a);c.drawRect(0,top,w,bottom,p);
        p.setColor(FilmStyle.ACCENT);c.drawRect(0,top,w,top+1.5f,p);
        p.setColor(FilmStyle.LINE);c.drawRect(0,bottom-1,w,bottom,p);
        p.setColor(0xff2b241d);
        for(float x=8;x<w-8;x+=38){
            c.drawRoundRect(new RectF(x,top+6,x+14,top+12),1.5f,1.5f,p);
            c.drawRoundRect(new RectF(x,bottom-12,x+14,bottom-6),1.5f,1.5f,p);
        }
        float cell=Math.min(196f,w*.30f),cx=w/2f;
        int count=Math.max(1,filmHudCount);
        String[] names={filmHudPrevious,filmHudName,filmHudNext};
        c.save();c.translate(slide,0);
        for(int i=0;i<3;i++){
            if(i!=1&&names[i].length()==0)continue;
            float width=i==1?cell*1.24f:cell;
            float left=i==1?cx-width/2f:i==0?cx-cell*1.62f:cx+cell*.62f;
            float height=i==1?62f:50f,y=cy-height/2f;
            p.setColor(i==1?0xff241f19:0xb8141210);
            c.drawRoundRect(new RectF(left,y,left+width,y+height),6,6,p);
            if(i==1){
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.4f);p.setColor(FilmStyle.ACCENT);
                c.drawRoundRect(new RectF(left+.7f,y+.7f,left+width-.7f,y+height-.7f),6,6,p);
                p.setStyle(Paint.Style.FILL);
            }
            int position=i==0?filmHudPosition-1:i==1?filmHudPosition:filmHudPosition+1;
            int local=((position-1)%count+count)%count;
            p.setColor(FilmStyle.canColour(local));
            c.drawRect(left+7,y+8,left+10,y+height-8,p);
            text(c,String.format(java.util.Locale.US,"%02d",local+1),left+16,y+(i==1?18:15),9,
                    i==1?FilmStyle.ACCENT:FilmStyle.DIM);
            text(c,fit(names[i],width-26,i==1?16:13),left+16,y+(i==1?41:34),i==1?16:13,
                    i==1?FilmStyle.TEXT:FilmStyle.MUTED);
        }
        c.restore();
        if(k<1f)postInvalidateDelayed(33);
    }
    /** Live write-time chip used by recipe capture. No darkroom stage, no
     * processing progress: the camera has already written a normal JPEG. */
    private void drawRecipeCapture(Canvas c,float w){
        long now=SystemClock.uptimeMillis(),elapsed=now-recipeCaptureAt;
        String seconds=String.format(java.util.Locale.US,"%.1f",elapsed/1000f);
        String label=elapsed>7000?"相机写入较慢 · 请稍候":recipeCaptureText+" · "+seconds+"s";
        p.setTextSize(14);
        float width=Math.min(w-56,p.measureText(label)+52),left=(w-width)/2,y=48;
        p.setStyle(Paint.Style.FILL);p.setColor(0xcc1a1714);
        c.drawRoundRect(new RectF(left,y,left+width,y+30),6,6,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.1f);p.setColor(FilmStyle.ACCENT);
        c.drawRoundRect(new RectF(left+.6f,y+.6f,left+width-.6f,y+29.4f),6,6,p);
        p.setStyle(Paint.Style.FILL);
        // Three film-edge marks light in sequence, a cheap "advance" cue.
        int phase=(int)((now%1200)/400);
        for(int i=0;i<3;i++){
            float dotX=left+12+i*8;
            p.setColor(i==phase?FilmStyle.ACCENT:0xff4a4034);
            c.drawRoundRect(new RectF(dotX,y+12,dotX+4,y+18),1,1,p);
        }
        text(c,label,left+40,y+20,14,FilmStyle.TEXT);
    }
    private void drawToast(Canvas c,float w){
        if(toastText.length()==0)return;
        long t=SystemClock.uptimeMillis()-toastAt;
        if(t>2000){toastText="";return;}
        float in=FilmStyle.easeOut(t/170f),out=t>1600?1f-FilmStyle.easeOut((t-1600)/400f):1f;
        float alpha=Math.min(in,out);
        String msg=fit(toastText,w-110,15);
        p.setTextSize(15);
        float width=Math.min(w-40,p.measureText(msg)+46),left=(w-width)/2,y=352-(1-in)*8;
        int a=(int)(255*alpha);
        p.setStyle(Paint.Style.FILL);
        p.setColor((a<<24)|(0xff1a1714&0xffffff));
        c.drawRoundRect(new RectF(left,y-30,left+width,y),7,7,p);
        p.setColor((a<<24)|(FilmStyle.ACCENT&0xffffff));c.drawRect(left+2,y-26,left+5,y-6,p);
        text(c,msg,left+16,y-10,15,(a<<24)|(FilmStyle.TEXT&0xffffff));
    }
    private void drawExposureStrip(Canvas c,float w){
        String shutter,aperture,iso,ev,wb;
        if(manualExposure){
            shutter=readout.shutterText();aperture=readout.apertureText();iso=readout.isoText();
            ev=readout.evText();wb=readout.whiteBalanceText();
        }else{
            shutter=readout.shutterText();aperture=readout.apertureText();iso=readout.isoText();
            ev=liveCompensation;wb="";
        }
        float cell=wb.length()>0?w/5f:w/4f;
        String[] labels={"SHUTTER","APERTURE","ISO",wb.length()>0?"EV":"COMP",wb.length()>0?"WB":null};
        for(int i=0;i<labels.length;i++){
            if(labels[i]==null)continue;
            center(c,labels[i],cell*(i+.5f),438,9,FilmStyle.MUTED);
        }
        float baseline=470;
        drawSevenCell(c,shutterCode(shutter),cell*.5f,baseline,cell-14);
        drawSevenCell(c,aperture,cell*1.5f,baseline,cell-14);
        drawSevenCell(c,isoCode(iso),cell*2.5f,baseline,cell-14);
        drawSevenCell(c,evCode(ev),cell*3.5f,baseline,cell-14);
        if(wb.length()>0)drawSevenCell(c,FilmStyle.wbCode(wb),cell*4.5f,baseline,cell-14);
    }
    /** All digital-tube readouts, including letters/slash/plus/seconds. */
    private void drawSevenCell(Canvas c,String value,float cx,float baseline,float maxWidth){
        if(value==null||value.length()==0)return;
        float height=26,w=FilmStyle.sevenWidth(value,height);
        if(w>maxWidth){height*=maxWidth/w;w=maxWidth;}
        FilmStyle.sevenSeg(c,p,value,cx-w/2,baseline,height,FilmStyle.RED);
    }
    private static String shutterCode(String value){
        if(value==null||value.length()==0)return "--";
        return value.endsWith("s")?value.substring(0,value.length()-1)+"\"":value;
    }
    private static String isoCode(String value){
        if(value==null||value.length()==0)return "--";
        String upper=value.toUpperCase(java.util.Locale.US);
        String digits=upper.replaceAll("[^0-9]","");
        if(upper.contains("AUTO"))return digits.length()==0?"A--":"A"+digits;
        return digits.length()==0?"--":digits;
    }
    private static String evCode(String value){
        if(value==null)return "--";
        String v=value.replace("EV","").trim();
        return v.length()==0?"--":v;
    }
    private void drawMeter(Canvas c,float w){
        float x=w-24,y0=96,y1=356;
        p.setColor(0x66000000);c.drawRect(x-2,y0-8,x+2,y1+8,p);
        for(int i=0;i<=12;i++){
            float y=y0+(y1-y0)*i/12f;boolean major=i%3==0;
            p.setColor(i==6?FilmStyle.ACCENT:FilmStyle.MUTED);
            c.drawRect(x-(major?7:4),y,x,y+1,p);
        }
        float ev=readout.exposureEvValid?readout.exposureEv:parseEv(liveCompensation);
        float k=(FilmStyle.clamp(ev,-2,2)+2)/4f;
        float py=y1-(y1-y0)*k;
        p.setColor(FilmStyle.RED);
        c.drawLine(x-12,py,x-2,py-5,p);c.drawLine(x-12,py,x-2,py+5,p);c.drawLine(x-2,py-5,x-2,py+5,p);
    }
    private static float parseEv(String value){
        if(value==null)return 0;
        try{return Float.parseFloat(value.replace("EV","").trim());}catch(RuntimeException e){return 0;}
    }
    private void drawAf(Canvas c,float w){
        if(!afVisible)return;
        float ax=ControlMath.afCoordinate(afX,Math.max(1,(int)w));
        float ay=ControlMath.afCoordinate(afY,480);
        float half=afSelecting?42:28,halfH=afSelecting?31:21;
        ax=Math.max(half+4,Math.min(w-half-4,ax));ay=Math.max(halfH+4,Math.min(480-halfH-4,ay));
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);
        p.setColor(afSelecting?FilmStyle.ACCENT:FilmStyle.TEXT);
        c.drawRect(ax-half,ay-halfH,ax+half,ay+halfH,p);
        c.drawLine(ax-8,ay,ax+8,ay,p);c.drawLine(ax,ay-8,ax,ay+8,p);
        p.setStyle(Paint.Style.FILL);
        if(afSelecting)center(c,"AF",ax,ay-halfH-8,13,FilmStyle.ACCENT);
    }
    private static int fade(int color,float k){
        int a=(int)(((color>>>24)&255)*k);
        return (a<<24)|(color&0xffffff);
    }
    private void drawWbMenu(Canvas c,float w){
        float left=Math.max(34,w/2-190),right=Math.min(w-34,w/2+190),top=86,bottom=394;
        long t=SystemClock.uptimeMillis()-wbMenuAt;
        float k=FilmStyle.easeOut(t/220f);
        if(k<1f)postInvalidateDelayed(33);
        float alpha=.3f+.7f*k;
        c.save();c.translate(0,(1-k)*12);
        p.setColor(fade(0xf21a1714,alpha));c.drawRoundRect(new RectF(left,top,right,bottom),8,8,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);p.setColor(fade(FilmStyle.ACCENT,alpha));
        c.drawRoundRect(new RectF(left+.5f,top+.5f,right-.5f,bottom-.5f),8,8,p);p.setStyle(Paint.Style.FILL);
        text(c,"白平衡",left+22,top+32,22,fade(FilmStyle.TEXT,alpha));
        p.setColor(fade(FilmStyle.ACCENT,alpha));c.drawRect(left+22,top+40,left+22+60*k,top+42,p);
        int visible=8,first=Math.max(0,Math.min(Math.max(0,wbItems.length-visible),wbSelected-visible/2));
        for(int i=first;i<wbItems.length&&i<first+visible;i++){
            float y=top+58+(i-first)*27;boolean focus=i==wbSelected;
            if(focus){p.setColor(fade(0xff2b241d,alpha));c.drawRoundRect(new RectF(left+12,y-19,right-12,y+7),5,5,p);
                p.setColor(fade(FilmStyle.ACCENT,alpha));c.drawRect(left+12,y-19,left+15,y+7,p);}
            text(c,fit(wbItems[i],right-left-80,17),left+26,y,17,fade(focus?FilmStyle.ACCENT:FilmStyle.TEXT,alpha));
        }
        text(c,fit(wbFooter,right-left-44,12),left+22,bottom-18,12,fade(FilmStyle.MUTED,alpha));
        c.restore();
    }
    private void drawLiveShutter(Canvas c,float w){
        if(!liveShutterRunning)return;
        long t=SystemClock.uptimeMillis()-liveShutterAt;float close;
        if(t<220)close=FilmStyle.easeInOut(t/220f);
        else if(t<300)close=1;
        else close=1-FilmStyle.easeOut((t-300)/160f);
        if(close<=.001f)return;
        float curtain=(480/2f+2)*close;
        p.setColor(0xff060605);c.drawRect(0,0,w,curtain,p);c.drawRect(0,480-curtain,w,480,p);
        p.setColor(0xff2b241d);c.drawRect(0,Math.max(0,curtain-5),w,curtain,p);
        c.drawRect(0,480-curtain,w,Math.min(480,480-curtain+5),p);
        if(close>.95f){p.setColor(FilmStyle.ACCENT);c.drawRect(0,239,w,241,p);}
    }
}
