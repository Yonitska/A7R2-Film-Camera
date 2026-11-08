package org.hongen.sonylutprobe;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import android.os.SystemClock;

/** Video standby/record overlay and supported-profile picker, drawn in the
 * same charcoal/amber film language as the photo viewfinder. */
final class VideoHud extends View {
    String status="正在准备录像",format="正在读取相机格式",hint="",error="";
    String menuTitle="选择录像格式";
    String modeText="P",shutterText="--",apertureText="F--",isoText="ISO --",evText="EV --",wbText="WB --";
    boolean recording,saving,menuOpen,afVisible,afSelecting;int afX,afY;
    boolean filmSelecting,filmActive;String filmName="";int filmPosition,filmCount;
    int seconds;
    String[] rows=new String[0];int selected;String menuFooter="↑↓/拨轮 选择 · ● 应用 · MENU 取消";
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cjk=new Paint(Paint.ANTI_ALIAS_FLAG);
    private long menuAt;
    VideoHud(Context context){
        super(context);
        p.setTypeface(FilmStyle.cjk(context));p.setTextAlign(Paint.Align.LEFT);
        cjk.setTypeface(FilmStyle.cjk(context));cjk.setTextAlign(Paint.Align.LEFT);
    }
    void setState(String status,String format,String hint){this.status=status;this.format=format;this.hint=hint;invalidate();}
    void setRecording(boolean value){recording=value;if(!value)seconds=0;invalidate();}
    void setSeconds(int value){seconds=value;invalidate();}
    void setSaving(boolean value){saving=value;invalidate();}
    void setError(String value){error=value==null?"":value;invalidate();}
    void setMenu(boolean open,String[] items,int selection,String footer){
        menuOpen=open;rows=items==null?new String[0]:items;selected=selection;
        if(footer!=null&&footer.length()>0)menuFooter=footer;
        if(open)menuAt=SystemClock.uptimeMillis();
        invalidate();
    }
    private static int fade(int color,float k){
        int a=(int)(((color>>>24)&255)*k);
        return (a<<24)|(color&0xffffff);
    }
    void setSelection(int value){selected=value;invalidate();}
    void setMenuTitle(String value){if(value!=null&&value.length()>0)menuTitle=value;}
    void setExposure(String mode,String shutter,String aperture,String iso,String ev,String wb){
        modeText=mode;shutterText=shutter;apertureText=aperture;isoText=iso;evText=ev;wbText=wb;invalidate();
    }
    void setAf(boolean visible,boolean selecting,int x,int y){afVisible=visible;afSelecting=selecting;afX=x;afY=y;invalidate();}
    /** The current film name occupies the free centre of the top bar. In
     * selection it becomes a numbered chip, mirroring the photo finder. */
    void setFilmStatus(String film,int position,int count,boolean selecting){
        filmName=film==null?"":film;
        filmPosition=Math.max(0,position);filmCount=Math.max(0,count);
        filmSelecting=selecting;filmActive=filmName.length()>0;invalidate();
    }
    private String fit(String value,float width,float size){
        if(value==null)return "";
        value=Lang.tr(value);
        p.setTextSize(size);if(p.measureText(value)<=width)return value;
        return value.substring(0,p.breakText(value,true,Math.max(0,width-p.measureText("…")),null))+"…";
    }
    private void text(Canvas c,String value,float x,float y,float size,int color){
        value=Lang.tr(value);
        p.setTextAlign(Paint.Align.LEFT);p.setColor(color);p.setTextSize(size);c.drawText(value,x,y,p);
    }
    private void right(Canvas c,String value,float x,float y,float size,int color){
        value=Lang.tr(value);
        p.setTextAlign(Paint.Align.RIGHT);p.setColor(color);p.setTextSize(size);c.drawText(value,x,y,p);
    }
    private void center(Canvas c,String value,float x,float y,float size,int color){
        value=Lang.tr(value);
        p.setTextAlign(Paint.Align.CENTER);p.setColor(color);p.setTextSize(size);c.drawText(value,x,y,p);
    }
    /** Live text sits on top of the camera feed, so it gets a dark stroke. */
    private void live(Canvas c,String value,float x,float y,float size,int color,Paint.Align align){
        value=Lang.tr(value);
        p.setTextAlign(align);p.setTextSize(size);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.5f);p.setColor(0xe0000000);c.drawText(value,x,y,p);
        p.setStyle(Paint.Style.FILL);p.setColor(color);c.drawText(value,x,y,p);
    }
    private String time(){
        int s=Math.max(0,seconds);
        return (s/60<10?"0":"")+(s/60)+":"+(s%60<10?"0":"")+(s%60);
    }
    @Override protected void onDraw(Canvas c){
        float scale=getHeight()/480f;if(scale<=0)return;
        c.save();c.scale(scale,scale);float w=getWidth()/scale;
        p.setStyle(Paint.Style.FILL);
        // Top status strip.
        p.setColor(0x990d0c0a);c.drawRect(0,0,w,52,p);
        p.setColor(FilmStyle.ACCENT);c.drawRect(0,50,w,52,p);
        // Sprocket silhouettes along the lower edge of the top bar.
        p.setColor(0xff2b241d);
        for(float x=8;x<w-8;x+=38)c.drawRoundRect(new RectF(x,43,x+14,49),1.5f,1.5f,p);
        p.setColor(0xcc1d1a16);c.drawRoundRect(new RectF(14,9,48,37),4,4,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.2f);p.setColor(FilmStyle.ACCENT);
        c.drawRoundRect(new RectF(14.6f,9.6f,47.4f,36.4f),4,4,p);p.setStyle(Paint.Style.FILL);
        center(c,modeText,31,29,20,FilmStyle.ACCENT);
        if(recording){
            // One-second blink without an extra timer: VideoActivity sets the
            // counter every second, and the parity drives the LED.
            p.setColor(seconds%2==0?FilmStyle.RED:0xff6e2622);c.drawCircle(64,23,6,p);
            FilmStyle.sevenSeg(c,p,time(),78,35,20,0xffff7a6e);
            live(c,"REC",157,32,13,FilmStyle.RED,Paint.Align.LEFT);
        }else{
            live(c,saving?"保存中":"待机",64,32,17,saving?FilmStyle.ACCENT:FilmStyle.TEXT,Paint.Align.LEFT);
        }
        if(filmActive)drawFilmTitle(c,w);
        if(!filmSelecting)
            live(c,fit(menuOpen?"录像格式":status,filmActive?w*0.27f:w-300,14),w-28,31,14,FilmStyle.MUTED,Paint.Align.RIGHT);
        if(error.length()>0){
            p.setColor(0xcc421c1c);c.drawRect(0,58,w,110,p);
            p.setColor(FilmStyle.RED);c.drawRect(0,58,4,110,p);
            text(c,fit(error,w-72,17),28,90,17,FilmStyle.ERROR);
        }
        // Bottom strip: hint/format lines, then seven-segment exposure row.
        if(hint.length()>0){
            String hintText=fit(hint,w-160,13);
            p.setTextSize(13);
            p.setColor(0x990d0c0a);c.drawRoundRect(new RectF(20,382,Math.min(w-20,20+p.measureText(hintText)+22),414),5,5,p);
            live(c,hintText,32,403,13,FilmStyle.TEXT,Paint.Align.LEFT);
        }
        right(c,fit(format,Math.min(w/2,300),12),w-28,403,12,FilmStyle.MUTED);
        p.setColor(0xa80d0c0a);c.drawRect(0,420,w,480,p);
        p.setColor(recording?FilmStyle.RED:FilmStyle.ACCENT);c.drawRect(0,420,w,422,p);
        // Matching sprocket silhouettes above the exposure readout.
        p.setColor(0xff2b241d);
        for(float x=8;x<w-8;x+=38)c.drawRoundRect(new RectF(x,423,x+14,429),1.5f,1.5f,p);
        drawExposure(c,w);
        drawAf(c,w);
        if(menuOpen)drawMenu(c,w);
        c.restore();
    }
    private void drawFilmTitle(Canvas c,float w){
        if(filmName.length()==0)return;
        if(!filmSelecting){
            center(c,fit(filmName,w*0.34f,15),w/2f,31,15,FilmStyle.TEXT);
            return;
        }
        String position=filmCount>0
                ?String.format(java.util.Locale.US,"%02d/%02d",Math.min(filmPosition,filmCount),filmCount)
                :"";
        String title=position.length()>0?"胶片 "+position+"  "+filmName:"胶片  "+filmName;
        p.setTextSize(16);String shown=fit(title,w*0.5f,16);
        float tw=p.measureText(shown),cx=w/2f;
        float left=cx-tw/2f-10,right=cx+tw/2f+10;
        p.setColor(0xcc1d1a16);c.drawRoundRect(new RectF(left,8,right,38),5,5,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.2f);p.setColor(FilmStyle.ACCENT);
        c.drawRoundRect(new RectF(left+.6f,8.6f,right-.6f,37.4f),5,5,p);p.setStyle(Paint.Style.FILL);
        center(c,shown,cx,31,16,FilmStyle.ACCENT);
    }
    private void drawExposure(Canvas c,float w){
        float cell=w/5f;
        String[] labels={"SHUTTER","APERTURE","ISO","EV","WB"};
        for(int i=0;i<labels.length;i++)center(c,labels[i],cell*(i+.5f),438,9,FilmStyle.MUTED);
        float baseline=470;
        drawSeven(c,shutterCode(shutterText),cell*.5f,baseline,cell-14);
        drawSeven(c,apertureText,cell*1.5f,baseline,cell-14);
        drawSeven(c,isoCode(isoText),cell*2.5f,baseline,cell-14);
        drawSeven(c,evCode(evText),cell*3.5f,baseline,cell-14);
        drawSeven(c,FilmStyle.wbCode(wbText),cell*4.5f,baseline,cell-14);
    }
    /** All digital-tube readouts, including letters/slash/plus/seconds. */
    private void drawSeven(Canvas c,String value,float cx,float baseline,float maxWidth){
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
    private void drawMenu(Canvas c,float w){
        float left=Math.max(34,w/2-190),right=Math.min(w-34,w/2+190),top=86,bottom=394;
        long t=SystemClock.uptimeMillis()-menuAt;
        float k=FilmStyle.easeOut(t/260f);
        if(k<1f)postInvalidateDelayed(33);
        float alpha=.25f+.75f*k;
        c.save();c.translate(0,(1-k)*14);
        p.setColor(fade(0xf21a1714,alpha));c.drawRoundRect(new RectF(left,top,right,bottom),8,8,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);p.setColor(fade(FilmStyle.ACCENT,alpha));
        c.drawRoundRect(new RectF(left+.5f,top+.5f,right-.5f,bottom-.5f),8,8,p);p.setStyle(Paint.Style.FILL);
        text(c,fit(menuTitle,right-left-44,22),left+22,top+32,22,fade(FilmStyle.TEXT,alpha));
        p.setColor(fade(FilmStyle.ACCENT,alpha));c.drawRect(left+22,top+40,left+22+60*k,top+42,p);
        int visible=8,first=Math.max(0,Math.min(Math.max(0,rows.length-visible),selected-visible/2));
        for(int i=first;i<rows.length&&i<first+visible;i++){
            float y=top+58+(i-first)*27;boolean focus=i==selected;
            if(focus){p.setColor(fade(0xff2b241d,alpha));c.drawRoundRect(new RectF(left+12,y-19,right-12,y+7),5,5,p);
                p.setColor(fade(FilmStyle.ACCENT,alpha));c.drawRect(left+12,y-19,left+15,y+7,p);}
            text(c,fit((i+1)+"  "+rows[i],right-left-64,17),left+26,y,17,fade(focus?FilmStyle.ACCENT:FilmStyle.TEXT,alpha));
        }
        text(c,fit(menuFooter,right-left-44,12),left+22,bottom-18,12,fade(FilmStyle.MUTED,alpha));
        c.restore();
    }
}
