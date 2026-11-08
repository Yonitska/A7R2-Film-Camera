package org.hongen.sonylutprobe;

import android.content.Context;
import android.graphics.*;
import android.view.MotionEvent;
import android.os.Handler;
import android.os.SystemClock;
import java.util.*;

/** Product page drawn as a film camera: a LUT shelf, contact cards and a
 * charcoal/amber status bar. Layout in 640x480 logical units; the physical
 * panel aspect is applied the same way as the capture HUD. */
final class CameraPage extends SonyReviewView {
    static final int BG=FilmStyle.BG,PANEL=FilmStyle.PANEL,PANEL2=FilmStyle.PANEL2,FOCUS=FilmStyle.PANEL2,
            LINE=FilmStyle.LINE,TEXT=FilmStyle.TEXT,MUTED=FilmStyle.MUTED,
            ACCENT=FilmStyle.ACCENT,ERROR=FilmStyle.ERROR;
    static final class Row {
        final String title,detail,badge;final boolean good;
        Row(String title,String detail,String badge){this(title,detail,badge,true);}
        Row(String title,String detail,String badge,boolean good){this.title=title;this.detail=detail;this.badge=badge;this.good=good;}
    }
    interface Tap {void select(int row);}
    String section="胶卷库",title="选择胶卷",subtitle="",note="",footer="",empty="",emptyDetail="MENU → 导入胶卷 · 中央键确认";
    List<Row> rows=new ArrayList<Row>();int selected;boolean busy,previewMode,previewLoading,cardGrid;Bitmap preview;String previewMessage="效果参考";Tap tap;
    int rollCount=-1;String rollNo="01";
    /** One representative image per camera group, loaded from assets off the
     * UI thread. Null entries simply draw the text card. */
    Bitmap[] cardImages;String cardImagesLoading="";
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Paint latin=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint big=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cjk=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cap=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokeP=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rr=new RectF();
    private final Handler ui=new Handler();
    private float pixelAspect=1,logicalWidth=640,scale=1;
    private float shelfPos=-1,shelfTarget=0;
    private final float[] shelfVel=new float[1];
    private boolean shelfAnimating;private long lastShelfStep;
    private String pageKey="";private long pageEnter;private boolean pageForward=true;
    private String focusKey="";private long focusAt=SystemClock.elapsedRealtime();
    private String toastText="";private long toastAt;
    // Film-load transition: the selected canister appears alone, its leader
    // is drawn out, then the shutter-curtain blink from the UI motion demo
    // blacks the panel while the capture Activity opens underneath.
    private boolean loadTransition;private long loadStart,loadShutterAt;
    private boolean loadReady,loadLaunched;private Runnable loadDone;
    private int loadIndex;private String loadTitle="",loadBadge="";
    private Bitmap previewSeen;private long previewAt;
    CameraPage(Context context){
        super(context);setBackgroundColor(BG);setFocusable(true);
        paint.setTypeface(FilmStyle.cjk(context));paint.setTextAlign(Paint.Align.LEFT);
        latin.setTypeface(FilmStyle.condensed(context));latin.setTextAlign(Paint.Align.LEFT);
        big.setTypeface(FilmStyle.condensed(context));big.setTextAlign(Paint.Align.LEFT);
        cjk.setTypeface(FilmStyle.cjk(context));cjk.setTextAlign(Paint.Align.LEFT);
        cap.setTypeface(FilmStyle.latinRegular(context));
        strokeP.setStyle(Paint.Style.STROKE);
    }
    @Override protected void pixelAspectChanged(float value){pixelAspect=value;invalidate();}
    private static float clamp(float v,float lo,float hi){return v<lo?lo:v>hi?hi:v;}
    private static int darken(int color,float k){
        int r=(int)(((color>>16)&255)*k),g=(int)(((color>>8)&255)*k),b=(int)((color&255)*k);
        return 0xff000000|(r<<16)|(g<<8)|b;
    }
    /** Dark ink on light film bands, cream ink on dark ones. */
    private static int bandInk(int color){
        int r=(color>>16)&255,g=(color>>8)&255,b=color&255;
        return (r*299+g*587+b*114)/1000>150?0xff241a10:0xfff2e7d3;
    }
    private String fit(Paint p,String s,float width,float size){return FilmStyle.fit(p,s,width,size);}
    private void text(Canvas c,Paint p,String s,float x,float y,float size,int color){FilmStyle.text(c,p,s,x,y,size,color);}
    private void right(Canvas c,Paint p,String s,float x,float y,float size,int color){FilmStyle.right(c,p,s,x,y,size,color);}
    private void center(Canvas c,Paint p,String s,float x,float y,float size,int color){FilmStyle.center(c,p,s,x,y,size,color);}
    private void round(Canvas c,float x,float y,float r,float b,int color,float radius){FilmStyle.round(c,paint,x,y,r,b,color,radius);}
    private void outline(Canvas c,float x,float y,float r,float b,int color,float radius){FilmStyle.outline(c,strokeP,x,y,r,b,color,radius);}
    private void badge(Canvas c,String value,float x,float y,boolean focus){if(value.length()==0)return;right(c,latin,fit(latin,value,96,14),x,y,14,focus?ACCENT:MUTED);}

    // ---- shelf motion ----
    private final Runnable shelfTick=new Runnable(){public void run(){
        long now=SystemClock.elapsedRealtime();
        float dt=Math.min(.05f,Math.max(.001f,(now-lastShelfStep)/1000f));lastShelfStep=now;
        shelfPos=FilmStyle.spring(shelfPos,selected,shelfVel,dt,18f,.72f);
        if(Math.abs(selected-shelfPos)<.002f&&Math.abs(shelfVel[0])<.02f){
            shelfPos=selected;shelfVel[0]=0;shelfAnimating=false;invalidate();
        }else{invalidateShelf();ui.postDelayed(this,33);}
    }};
    private void invalidateShelf(){
        if(scale<=0)return;
        invalidate(0,(int)(136*scale),getWidth(),(int)(402*scale));
    }
    private void ensureShelfMotion(){
        if(!previewMode){shelfAnimating=false;return;}
        if(shelfPos<0){shelfPos=selected;return;}
        if(!shelfAnimating&&Math.abs(selected-shelfPos)>.001f){
            shelfAnimating=true;lastShelfStep=SystemClock.elapsedRealtime();ui.post(shelfTick);
        }
    }
    @Override protected void onDetachedFromWindow(){ui.removeCallbacks(shelfTick);ui.removeCallbacks(toastTick);super.onDetachedFromWindow();}
    private final Runnable toastTick=new Runnable(){public void run(){
        if(toastText.length()==0)return;
        if(SystemClock.elapsedRealtime()-toastAt<2100){invalidate(0,(int)(340*scale),getWidth(),(int)(420*scale));ui.postDelayed(this,40);}
        else{toastText="";invalidate();}
    }};
    /** Forward (deeper page) or back. Only affects the next page change. */
    void setPageForward(boolean forward){pageForward=forward;}
    /** Transient status line above the bottom bar, like a camera's own toast. */
    void toast(String text){
        if(text==null||text.length()==0)return;
        toastText=text;toastAt=SystemClock.elapsedRealtime();
        ui.removeCallbacks(toastTick);ui.postDelayed(toastTick,40);invalidate();
    }

    // ---- drawing ----
    private void background(Canvas c,float w){
        paint.setStyle(Paint.Style.FILL);paint.setColor(BG);c.drawRect(0,0,w,480,paint);
        paint.setColor(0xff12110f);c.drawRect(0,46,w,440,paint);
        paint.setColor(LINE);c.drawRect(0,45,w,46,paint);
        paint.setColor(0xff13110f);c.drawRect(0,440,w,480,paint);
        paint.setColor(LINE);c.drawRect(0,439,w,440,paint);
        // Perforations along the top of the content area.
        paint.setColor(0xff2b241d);
        for(float x=8;x<w-8;x+=42)c.drawRect(x,50,x+16,55,paint);
    }
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);scale=getHeight()/480f;if(scale<=0)return;
        logicalWidth=getWidth()*pixelAspect/scale;
        float w=logicalWidth;
        c.save();c.scale(scale/pixelAspect,scale);
        if(loadTransition){drawLoadingFilm(c,logicalWidth);c.restore();return;}
        background(c,w);
        String value=previewMode?(rows.isEmpty()?"空库":("卷 "+rollNo+" · "+(rollCount<0?"--":rollCount+" 张")))
                :(rows.isEmpty()?"":(Math.min(selected+1,rows.size())+" / "+rows.size()));
        FilmStyle.topBar(c,paint,w,section,value);
        ensureShelfMotion();
        long now=SystemClock.elapsedRealtime();
        String key=section+"|"+title+"|"+(previewMode?1:0)+"|"+(cardGrid?2:0)+"|"+rows.size();
        boolean changed=!key.equals(pageKey);
        if(changed){pageKey=key;pageEnter=now;}
        // 340ms push/pop: the new page slides in from the navigation direction
        // while a short black dip settles. The shelf keeps its own spring.
        float enter=FilmStyle.easeOut((now-pageEnter)/340f);
        if(enter<1f)postInvalidateDelayed(16);
        float shift=(1-enter)*(pageForward?26f:-26f);
        boolean shelf=previewMode&&!rows.isEmpty();
        String fk=key+"|"+selected;
        if(!fk.equals(focusKey)){focusKey=fk;focusAt=now;}
        float grow=FilmStyle.easeOut((now-focusAt)/180f);
        // On the shelf the spring tick already repaints the dots; do not add a
        // second full-screen animation on top of the carousel.
        if(grow<1f&&!shelf)postInvalidateDelayed(16);
        c.save();c.translate(shift,0);
        if(shelf)drawShelfPage(c,w,grow);
        else if(cardGrid)drawCardGridPage(c,w,grow);
        else drawListPage(c,w,grow);
        c.restore();
        if(enter<1f){
            paint.setStyle(Paint.Style.FILL);paint.setColor(0xff000000);
            paint.setAlpha((int)((1-enter)*110));c.drawRect(0,0,w,480,paint);paint.setAlpha(255);
        }
        drawToast(c,w,now);
        c.restore();
    }

    private void drawShelfPage(Canvas c,float w,float grow){
        float previewLeft=w-268,previewRight=w-24;
        float columnRight=previewLeft-18;
        float center=28+(columnRight-28)/2f;
        // Heading. The LUT grid type moved off the canister label and now
        // follows the page title, e.g. "选择胶卷  胶卷类型 25³".
        text(c,latin,"SHELF",28,78,11,ACCENT);
        Row focusRow=rows.get(Math.max(0,Math.min(rows.size()-1,selected)));
        String type=focusRow.badge==null?"":focusRow.badge.trim();
        String tag=type.length()==0?"":"胶卷类型 "+type;
        latin.setTextSize(13);float tagWidth=tag.length()==0?0:latin.measureText(tag);
        float titleRoom=columnRight-56-28-(tagWidth>0?tagWidth+14:0);
        String fittedTitle=fit(cjk,title,Math.max(64,titleRoom),28);
        text(c,cjk,fittedTitle,28,106,28,TEXT);
        if(tagWidth>0)text(c,latin,tag,28+cjk.measureText(fittedTitle)+12,104,13,ACCENT);
        text(c,cjk,fit(cjk,subtitle,columnRight-56,13),28,128,13,MUTED);
        // Preview card
        drawPreview(c,previewLeft,64,previewRight,378);
        // Carousel
        float base=344;
        for(int pass=0;pass<2;pass++){
            for(int i=0;i<rows.size();i++){
                float d=i-shelfPos,ad=Math.abs(d);
                if(ad>2.05f)continue;
                boolean focus=ad<.35f;
                if((pass==1)!=focus)continue;
                float sc=1f/(1f+.55f*ad);
                float cx=center+d*118f*sc;
                if(cx<28-60||cx>columnRight+60)continue;
                drawCanister(c,cx,base,i,sc,focus);
            }
        }
        // Position dots (window around the selection)
        int count=rows.size(),window=Math.min(count,9),first=Math.max(0,Math.min(count-window,selected-window/2));
        float dots=window*11,dx=center-dots/2+5;
        for(int i=first;i<first+window;i++){
            paint.setColor(i==selected?ACCENT:FilmStyle.DIM);
            c.drawCircle(dx+(i-first)*11,388,i==selected?2.2f+grow:2.2f,paint);
        }
        Row row=rows.get(Math.max(0,Math.min(rows.size()-1,selected)));
        text(c,cjk,fit(cjk,row.detail,columnRight-56,12),28,372,12,MUTED);
        // Roll card under the preview
        drawRoll(c,previewLeft,392,previewRight,428);
        if(note.length()>0)text(c,cjk,fit(cjk,note,(previewRight-28)*1f,10),28,436,10,FilmStyle.DIM);
    }
    private void drawToast(Canvas c,float w,long now){
        if(toastText.length()==0)return;
        long t=now-toastAt;
        if(t>2000){toastText="";return;}
        float in=FilmStyle.easeOut(t/180f);
        float out=t>1600?1f-FilmStyle.easeOut((t-1600)/400f):1f;
        float alpha=Math.min(in,out);
        String msg=fit(paint,toastText,w-120,15);
        paint.setTextSize(15);
        float width=Math.min(w-40,paint.measureText(msg)+46),left=(w-width)/2,y=402-(1-in)*8;
        int a=(int)(255*alpha);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor((a<<24)|(FilmStyle.PANEL&0xffffff));
        rr.set(left,y-30,left+width,y);c.drawRoundRect(rr,7,7,paint);
        paint.setColor((a<<24)|(ACCENT&0xffffff));c.drawRect(left+2,y-26,left+5,y-6,paint);
        FilmStyle.text(c,cjk,msg,left+16,y-10,15,(a<<24)|(TEXT&0xffffff));
    }
    private void drawPreview(Canvas c,float left,float top,float right,float bottom){
        round(c,left,top,right,bottom,PANEL,8);outline(c,left,top,right,bottom,LINE,8);
        float pad=8,iw=right-left-pad*2,ih=bottom-top-pad*2-22;
        if(preview!=null){
            Rect cb=c.getClipBounds();float hscale=scale/pixelAspect;
            if(cb==null||cb.right>left*hscale&&cb.left<right*hscale){
                long now=SystemClock.elapsedRealtime();
                if(preview!=previewSeen){previewSeen=preview;previewAt=now;}
                float fade=FilmStyle.easeOut((now-previewAt)/220f);
                if(fade<1f)invalidate((int)(left*hscale),(int)(top*scale),(int)(right*hscale),(int)(bottom*scale));
                paint.setColor(0xff0a0d0f);c.drawRect(left+pad,top+pad,left+pad+iw,top+pad+ih,paint);
                float f=Math.min(iw/preview.getWidth(),ih/preview.getHeight());
                float dw=preview.getWidth()*f,dh=preview.getHeight()*f;
                paint.setAlpha((int)(255*fade));
                c.drawBitmap(preview,null,new RectF(left+pad+(iw-dw)/2,top+pad+(ih-dh)/2,
                        left+pad+(iw+dw)/2,top+pad+(ih+dh)/2),paint);
                paint.setAlpha(255);
            }
        }else{
            previewSeen=null;
            text(c,cjk,fit(cjk,previewLoading?"正在生成效果参考":previewMessage,iw-16,15),
                    left+pad+8,top+pad+ih/2,15,MUTED);
        }
        right(c,latin,previewLoading?"LOADING":"PREVIEW",right-12,bottom-9,9,previewLoading?ACCENT:FilmStyle.DIM);
    }
    private void drawRoll(Canvas c,float left,float top,float right,float bottom){
        round(c,left,top,right,bottom,PANEL2,6);outline(c,left,top,right,bottom,LINE,6);
        text(c,latin,"ROLL",left+12,top+12,8,ACCENT);
        text(c,big,rollNo,left+12,top+31,18,TEXT);
        right(c,cjk,rollCount<0?"正在读取":("已拍 "+rollCount+" 张"),right-12,top+18,10,MUTED);
        float trackLeft=left+64,trackRight=right-12,y=top+34;
        paint.setColor(LINE);c.drawRect(trackLeft,y,trackRight,y+2,paint);
        int total=36,shown=rollCount<0?0:Math.max(0,rollCount);
        if(shown>0){
            int frame=((shown-1)%total)+1;
            paint.setColor(ACCENT);c.drawRect(trackLeft,y,trackLeft+(trackRight-trackLeft)*frame/total,y+2,paint);
            for(int i=1;i<total;i+=6){float x=trackLeft+(trackRight-trackLeft)*i/total;c.drawRect(x,y-2,x,y+4,paint);}
        }
    }
    private void drawCanister(Canvas c,float cx,float base,int idx,float sc,boolean focus){
        int colour=idx<rows.size()?FilmStyle.canColour(idx):FilmStyle.CAN_COLOURS[0];
        Row row=idx<rows.size()?rows.get(idx):null;
        drawCanisterAt(c,cx,base,sc,focus,colour,row==null?"":row.title,row==null?"":row.badge);
    }
    private void drawCanisterAt(Canvas c,float cx,float base,float sc,boolean focus,int colour,String title,String badge){
        c.save();c.translate(cx,base);c.scale(sc,sc);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(FilmStyle.SHADOW);rr.set(-52,-4,52,10);c.drawRoundRect(rr,9,7,paint);
        paint.setColor(0xff2a2722);rr.set(-46,-170,46,0);c.drawRoundRect(rr,14,14,paint);
        paint.setColor(colour);rr.set(-41,-165,41,-6);c.drawRoundRect(rr,11,11,paint);
        paint.setColor(0xff151310);c.drawRect(-41,-120,41,-118,paint);
        paint.setColor(0xffc9c2b4);c.drawRect(-41,-166,41,-159,paint);c.drawRect(-41,-13,41,-6,paint);
        paint.setColor(0xff2a2722);rr.set(-41,-174,41,-158);c.drawRoundRect(rr,7,7,paint);
        paint.setColor(0xff6d675c);rr.set(-34,-172,34,-160);c.drawRoundRect(rr,5,5,paint);
        paint.setColor(0xff161310);rr.set(-13,-171,13,-162);c.drawRoundRect(rr,4,4,paint);
        paint.setColor(0x33ffffff);c.drawRect(-36,-161,-30,-9,paint);
        // Real film bodies carry a cream paper label between two thin colour
        // bands. The old build printed the title on a flat yellow body, which
        // read as a large yellow information panel; keep the film colour as a
        // narrow band and let the paper label carry the type.
        paint.setColor(0x1fffffff);c.drawRect(-41,-165,-33,-6,paint);
        paint.setColor(0x22000000);c.drawRect(33,-165,41,-6,paint);
        paint.setColor(0xffe7dcc8);c.drawRect(-40,-158,40,-58,paint);
        paint.setColor(darken(colour,.72f));c.drawRect(-40,-158,40,-150,paint);c.drawRect(-40,-66,40,-58,paint);
        if(title!=null&&title.length()>0){
            // Both label lines carry the film name. The LUT grid size no
            // longer appears here; it lives beside the page title instead.
            String[] lines=canisterNameLines(title);
            canisterLine(c,lines[0],-131,focus,0xff211910);
            canisterLine(c,lines[1],-106,focus,0xff211910);
        }
        center(c,latin,"35-36",0,-73,7,0xff5a4a3a);
        if(focus&&!shelfAnimating){
            strokeP.setStyle(Paint.Style.STROKE);strokeP.setStrokeWidth(1.5f);strokeP.setColor(0x99e8b23a);
            rr.set(-49,-176,49,2);c.drawRoundRect(rr,2,2,strokeP);
            strokeP.setStyle(Paint.Style.FILL);
        }
        c.restore();
    }
    /** Split a film name across the canister's two label lines. A name that
     * fits stays on line one; otherwise the break goes at the space nearest
     * the middle so both lines keep whole words. */
    private String[] canisterNameLines(String value){
        String full=value==null?"":value.trim();
        if(full.length()==0)return new String[]{"",""};
        latin.setTextSize(16);
        if(latin.measureText(full)<=74)return new String[]{full,""};
        int split=-1;
        for(int i=1;i<full.length()-1;i++){
            if(full.charAt(i)==' '&&(split<0||Math.abs(i-full.length()/2f)<Math.abs(split-full.length()/2f)))split=i;
        }
        if(split<=0)split=Math.max(1,Math.min(full.length()-1,Math.round(full.length()/2f)));
        return new String[]{full.substring(0,split).trim(),full.substring(split).trim()};
    }
    private void canisterLine(Canvas c,String text,float y,boolean focus,int color){
        if(text==null||text.length()==0)return;
        float size=focus?16:13;
        while(size>9){latin.setTextSize(size);if(latin.measureText(text)<=74)break;size-=1;}
        center(c,latin,fit(latin,text,74,size),0,y,size,color);
    }
    // ---- film-load transition ----
    void startLoadingFilm(int index,String title,String badge){
        loadTransition=true;loadStart=SystemClock.elapsedRealtime();loadShutterAt=0;
        loadReady=false;loadLaunched=false;loadDone=null;
        loadIndex=Math.max(0,index);loadTitle=title==null?"":title;loadBadge=badge==null?"":badge;
        previewMode=false;busy=true;invalidate();
    }
    void finishLoadingFilm(Runnable done){
        if(!loadTransition){if(done!=null)ui.post(done);return;}
        loadDone=done;loadReady=true;invalidate();
    }
    void cancelLoadingFilm(){
        loadTransition=false;loadDone=null;loadReady=false;loadLaunched=false;loadShutterAt=0;invalidate();
    }
    boolean loadingFilm(){return loadTransition;}
    /** Only the chosen canister, then its leader is pulled across the panel.
     * Once preparation finishes, the motion-demo curtain closes; the capture
     * Activity is launched at full black so its loading page is never seen. */
    private void drawLoadingFilm(Canvas c,float w){
        long now=SystemClock.elapsedRealtime(),age=now-loadStart;
        paint.setStyle(Paint.Style.FILL);paint.setColor(BG);c.drawRect(0,0,w,480,paint);
        float fade=FilmStyle.easeOut(age/380f);
        float pull=loadShutterAt>0?1f:FilmStyle.easeInOut((age-380f)/820f);
        float sc=1.28f*(0.90f+0.10f*fade);
        float base=330f,cx=w/2f-92f*pull;
        int colour=FilmStyle.canColour(loadIndex);
        if(pull>0.002f)drawLeader(c,cx+46f*sc,base,pull,w,colour);
        drawCanisterAt(c,cx,base,sc,fade>.45f,colour,loadTitle,loadBadge);
        int alpha=(int)(255*FilmStyle.easeOut((age-100f)/420f));
        if(alpha>0){
            String msg="正在加载胶卷";
            cjk.setTextSize(18);
            float tw=cjk.measureText(msg);
            paint.setColor((alpha<<24)|(ACCENT&0xffffff));
            c.drawCircle(w/2f-tw/2-18,418,3.2f+1.2f*(float)Math.sin(age*0.012),paint);
            text(c,cjk,msg,w/2f-tw/2,425,18,(alpha<<24)|(TEXT&0xffffff));
        }
        if(loadShutterAt==0){
            if(loadReady&&age>=1420f)loadShutterAt=now;
            else postInvalidateDelayed(16);
        }
        if(loadShutterAt>0){
            long t=now-loadShutterAt;
            drawLoadShutter(c,w,t);
            if(!loadLaunched&&t>=420){
                loadLaunched=true;Runnable done=loadDone;
                if(done!=null)ui.post(done);
            }
            if(t<800)postInvalidateDelayed(16);
        }
    }
    private void drawLeader(Canvas c,float x0,float base,float pull,float w,int colour){
        float top=base-168f,height=118f;
        float right=x0+(w-40f-x0)*pull;
        if(right<=x0+1)return;
        paint.setColor(0xff17140f);c.drawRect(x0,top,right,top+height,paint);
        for(float x=x0;x<right;x+=56f){
            float r=Math.min(right,x+56f);
            paint.setColor(0xff201c16);c.drawRect(x+3,top+18,r-3,top+height-18,paint);
            paint.setColor((0x30<<24)|(colour&0xffffff));c.drawRect(x+3,top+18,r-3,top+height-18,paint);
            paint.setColor(0xff0d0b09);c.drawRect(r-1,top+13,r+1,top+height-13,paint);
        }
        paint.setColor(0xffc9c2b4);
        for(float x=x0+6;x<right-8;x+=20f){
            c.drawRect(x,top+6,Math.min(right-2,x+10),top+12,paint);
            c.drawRect(x,top+height-12,Math.min(right-2,x+10),top+height-6,paint);
        }
        paint.setColor(0xff2b241d);c.drawRect(x0,top,right,top+2,paint);c.drawRect(x0,top+height-2,right,top+height,paint);
        paint.setColor(0x99e8b23a);c.drawRect(right-2,top,right,top+height,paint);
    }
    private void drawLoadShutter(Canvas c,float w,long t){
        float close=t<360?FilmStyle.easeInOut(t/360f):1f;
        float curtain=(480/2f+2)*close;
        paint.setColor(0xff060605);c.drawRect(0,0,w,curtain,paint);c.drawRect(0,480-curtain,w,480,paint);
        paint.setColor(0xff2b241d);c.drawRect(0,Math.max(0,curtain-6),w,curtain,paint);
        c.drawRect(0,480-curtain,w,Math.min(480,480-curtain+6),paint);
        if(close<.995f){
            strokeP.setStyle(Paint.Style.STROKE);strokeP.setStrokeWidth(2);strokeP.setColor(0x664c4438);
            for(int i=0;i<6;i++){
                float y=curtain+6+(480-2*curtain-12)*i/5f;
                c.drawLine(0,y,w,y+20,strokeP);
            }
            strokeP.setStyle(Paint.Style.FILL);
        }else{
            paint.setColor(0x99e8b23a);c.drawRect(0,239,w,241,paint);
        }
    }
    /** Camera-first recipe selection: six photo cards per page. The card
     * images are representative camera photos, not product photographs or
     * film-canister artwork. */
    private void drawCardGridPage(Canvas c,float w,float grow){
        text(c,latin,section.toUpperCase(Locale.US),28,78,11,ACCENT);
        text(c,cjk,fit(cjk,title,w-56,28),28,106,28,TEXT);
        text(c,cjk,fit(cjk,subtitle,w-56,13),28,128,13,MUTED);
        int first=UiList.pageStart(selected,rows.size(),6),last=Math.min(rows.size(),first+6);
        float x0=28,x1=w-28,gap=10;
        float cw=(x1-x0-gap*2)/3,ch=112;
        for(int i=first;i<last;i++){
            int slot=i-first,row=slot/3,col=slot%3;
            float x=x0+col*(cw+gap),y=144+row*(ch+10);
            drawCard(c,rows.get(i),i==selected,x,y,x+cw,y+ch,i,grow);
        }
        if(rows.isEmpty()){
            round(c,28,148,w-28,336,PANEL,8);outline(c,28,148,w-28,336,LINE,8);
            text(c,cjk,fit(cjk,empty,w-104,23),50,214,23,TEXT);
            text(c,cjk,fit(cjk,busy?"请稍候，正在处理…":emptyDetail,w-104,16),50,254,16,MUTED);
        }
        drawNote(c,w);
        FilmStyle.bottomBar(c,paint,w,480);
        text(c,cjk,fit(cjk,footer,w-56,15),28,462,15,TEXT);
        paint.setColor(ACCENT);c.drawRect(20,452,23,469,paint);
    }
    private void drawCard(Canvas c,Row row,boolean focus,float x,float y,float right,float bottom,int index,float grow){
        round(c,x,y,right,bottom,focus?PANEL2:PANEL,7);
        outline(c,x,y,right,bottom,focus?ACCENT:LINE,7);
        if(focus&&grow>0){paint.setColor(ACCENT);c.drawRoundRect(new RectF(x,y+10,x+4*grow,y+40),2,2,paint);}
        float pad=6,iw=right-x-pad*2,ih=66;
        paint.setColor(0xff0a0d0f);c.drawRect(x+pad,y+pad,x+pad+iw,y+pad+ih,paint);
        Bitmap image=cardImages!=null&&index>=0&&index<cardImages.length?cardImages[index]:null;
        if(image!=null&&!image.isRecycled()){
            c.save();c.clipRect(x+pad,y+pad,x+pad+iw,y+pad+ih);
            float f=Math.max(iw/image.getWidth(),ih/image.getHeight());
            float dw=image.getWidth()*f,dh=image.getHeight()*f;
            c.drawBitmap(image,null,new RectF(x+pad+(iw-dw)/2,y+pad+(ih-dh)/2,
                    x+pad+(iw+dw)/2,y+pad+(ih+dh)/2),paint);
            c.restore();
        }else{
            text(c,cjk,cardImagesLoading.length()>0?"载入中":"相机",x+pad+8,y+pad+ih/2+5,14,FilmStyle.DIM);
        }
        paint.setColor(0x55000000);c.drawRect(x+pad,y+pad+ih-16,x+pad+iw,y+pad+ih,paint);
        text(c,cjk,fit(cjk,row.title,iw-12,17),x+pad+6,y+pad+ih-4,17,0xfff2e7d3);
        text(c,cjk,fit(cjk,row.detail,Math.max(32,right-x-24),11),x+11,y+94,11,focus?0xffc2c8c7:MUTED);
        badge(c,row.badge,right-10,y+104,focus);
    }
    private void drawListPage(Canvas c,float w,float grow){
        text(c,latin,section.toUpperCase(Locale.US),28,78,11,ACCENT);
        text(c,cjk,fit(cjk,title,w-56,28),28,106,28,TEXT);
        text(c,cjk,fit(cjk,subtitle,w-56,13),28,128,13,MUTED);
        int first=UiList.pageStart(selected,rows.size(),4),last=Math.min(rows.size(),first+4);
        for(int i=first;i<last;i++)row(c,rows.get(i),i==selected,28,148+(i-first)*62,w-28,56,w-180,grow);
        if(rows.isEmpty()){
            round(c,28,148,w-28,336,PANEL,8);outline(c,28,148,w-28,336,LINE,8);
            text(c,cjk,fit(cjk,empty,w-104,23),50,214,23,TEXT);
            text(c,cjk,fit(cjk,busy?"请稍候，正在处理…":emptyDetail,w-104,16),50,254,16,MUTED);
        }
        drawNote(c,w);
        FilmStyle.bottomBar(c,paint,w,480);
        text(c,cjk,fit(cjk,footer,w-56,15),28,462,15,TEXT);
        paint.setColor(ACCENT);c.drawRect(20,452,23,469,paint);
    }
    private void row(Canvas c,Row row,boolean focus,float x,float y,float right,float height,float textWidth,float grow){
        round(c,x,y,right,y+height,focus?PANEL2:PANEL,7);
        outline(c,x,y,right,y+height,focus?ACCENT:LINE,7);
        if(focus&&grow>0){paint.setColor(ACCENT);c.drawRoundRect(new RectF(x,y+12,x+4*grow,y+height-12),2,2,paint);}
        float available=Math.min(textWidth,right-x-160);
        text(c,cjk,fit(cjk,row.title,available,19),x+17,y+24,19,TEXT);
        text(c,cjk,fit(cjk,row.detail,available,12),x+17,y+44,12,focus?0xffc2c8c7:MUTED);
        badge(c,row.badge,right-16,y+33,focus);
    }
    private void drawNote(Canvas c,float w){
        if(note.length()>0)text(c,cjk,fit(cjk,note,w-118,12),28,420,12,MUTED);
        if(rows.size()>4)right(c,latin,(selected+1)+" / "+rows.size(),w-28,420,12,ACCENT);
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        if(event.getAction()!=MotionEvent.ACTION_UP||scale<=0)return true;
        float x=event.getX()/scale,y=event.getY()/scale;
        if(previewMode&&!rows.isEmpty()){
            float columnRight=logicalWidth-268-18,center=28+(columnRight-28)/2f;
            if(y>140&&y<400&&x<columnRight){
                int index=Math.round(shelfPos+(x-center)/118f);
                index=Math.max(0,Math.min(rows.size()-1,index));
                if(tap!=null&&!busy)tap.select(index);
            }
            return true;
        }
        if(cardGrid){
            float x0=28,x1=logicalWidth-28,gap=10,cw=(x1-x0-gap*2)/3,ch=112;
            int first=UiList.pageStart(selected,rows.size(),6);
            int col=(int)((x-x0)/(cw+gap)),row=(int)((y-144)/(ch+10));
            if(x>=x0&&x<x1&&y>=144&&y<144+2*(ch+10)-10&&col>=0&&col<3&&row>=0&&row<2){
                int index=first+row*3+col;
                if(index>=0&&index<rows.size()&&tap!=null&&!busy)tap.select(index);
            }
            return true;
        }
        int index=UiList.pageStart(selected,rows.size(),4)+(int)((y-148)/62);
        if(y>=148&&y<396&&index>=0&&index<rows.size()&&tap!=null&&!busy)tap.select(index);
        return true;
    }
}
