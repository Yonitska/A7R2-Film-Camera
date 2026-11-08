package org.hongen.sonylutprobe;

import android.content.Context;
import android.graphics.*;

/** Original film-camera visual system for the A7R II port.
 *
 * Charcoal panels, cream type and an amber highlight; seven-segment digits for
 * exposure readouts. Only Canvas operations confirmed to work on this camera
 * are used (rects, lines, circles, round rects, text, bitmaps). */
final class FilmStyle {
    static final int BG=0xff171513,PANEL=0xff1d1a16,PANEL2=0xff211d18,
            LINE=0xff3a352f,DIM=0xff5d5549,TEXT=0xfff2ead8,MUTED=0xff948a7b,
            ACCENT=0xffe8b23a,RED=0xffd9473f,GREEN=0xff9fbf8a,ERROR=0xffd98a7a,
            SHADOW=0x55000000;
    static final int[] CAN_COLOURS={0xffd7a33b,0xffb53d34,0xffd8b23f,0xff4f8b72,
            0xff8c8580,0xff3f6fa8,0xffa8a8a8,0xff7a5a8e,0xffb8773a,0xff4a7b86};

    private FilmStyle(){}

    static Typeface latin(Context c){
        try{return Typeface.create(Typeface.SANS_SERIF,Typeface.BOLD);}
        catch(Throwable e){return Typeface.DEFAULT_BOLD;}
    }
    static Typeface latinRegular(Context c){
        try{return Typeface.create(Typeface.SANS_SERIF,Typeface.NORMAL);}
        catch(Throwable e){return Typeface.DEFAULT;}
    }
    static Typeface condensed(Context c){
        try{return Typeface.create("sans-serif-condensed",Typeface.BOLD);}
        catch(Throwable e){return latin(c);}
    }
    static Typeface cjk(Context c){return UiFont.get(c);}
    static Typeface mono(Context c){return Typeface.create(Typeface.MONOSPACE,Typeface.BOLD);}

    static float clamp(float v,float lo,float hi){return v<lo?lo:v>hi?hi:v;}
    static float easeOut(float t){t=clamp(t,0,1);float u=1-t;return 1-u*u*u;}
    static float easeInOut(float t){t=clamp(t,0,1);return t<.5f?4*t*t*t:1-(-2*t+2)*(-2*t+2)*(-2*t+2)/2;}
    static float spring(float value,float target,float velocity[],float dt,float w,float z){
        float h=Math.min(dt,0.05f),remaining=h;
        while(remaining>0){
            float step=Math.min(remaining,0.008f);
            float a=w*w*(target-value)-2*z*w*velocity[0];
            velocity[0]+=a*step;value+=velocity[0]*step;remaining-=step;
        }
        if(Math.abs(target-value)<0.0015f&&Math.abs(velocity[0])<0.02f){value=target;velocity[0]=0;}
        return value;
    }

    static String fit(Paint p,String value,float width,float size){
        if(value==null)return "";
        value=Lang.tr(value);
        p.setTextSize(size);
        if(p.measureText(value)<=width)return value;
        int n=p.breakText(value,true,Math.max(0,width-p.measureText("…")),null);
        return value.substring(0,n)+"…";
    }
    static void text(Canvas c,Paint p,String value,float x,float y,float size,int color){
        p.setTextAlign(Paint.Align.LEFT);p.setColor(color);p.setTextSize(size);c.drawText(Lang.tr(value),x,y,p);
    }
    static void right(Canvas c,Paint p,String value,float x,float y,float size,int color){
        p.setTextAlign(Paint.Align.RIGHT);p.setColor(color);p.setTextSize(size);c.drawText(Lang.tr(value),x,y,p);
    }
    static void center(Canvas c,Paint p,String value,float x,float y,float size,int color){
        p.setTextAlign(Paint.Align.CENTER);p.setColor(color);p.setTextSize(size);c.drawText(Lang.tr(value),x,y,p);
    }
    static void round(Canvas c,Paint p,float x,float y,float r,float b,int color,float radius){
        p.setStyle(Paint.Style.FILL);p.setColor(color);
        c.drawRoundRect(new RectF(x,y,r,b),radius,radius,p);
    }
    static void outline(Canvas c,Paint p,float x,float y,float r,float b,int color,float radius){
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);p.setColor(color);
        c.drawRoundRect(new RectF(x+.5f,y+.5f,r-.5f,b-.5f),radius,radius,p);
        p.setStyle(Paint.Style.FILL);
    }
    /** Top status bar: brand ring, page name on the left, live value right. */
    static void topBar(Canvas c,Paint p,float w,String section,String value){
        p.setStyle(Paint.Style.FILL);p.setColor(BG);c.drawRect(0,0,w,46,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);p.setColor(ACCENT);c.drawCircle(26,23,8,p);
        p.setStyle(Paint.Style.FILL);
        String brand=Lang.tr("A7R2胶片相机");
        p.setTextAlign(Paint.Align.LEFT);p.setColor(TEXT);p.setTextSize(13);
        float brandWidth=p.measureText(brand);
        c.drawText(brand,44,28,p);
        float separator=44+brandWidth+10;
        p.setColor(DIM);c.drawText("|",separator,28,p);
        text(c,p,section,separator+14,28,14,ACCENT);
        if(value!=null&&value.length()>0)right(c,p,value,w-24,28,13,MUTED);
        p.setColor(LINE);c.drawRect(0,45,w,46,p);
    }
    /** Bottom hint bar: small key caps followed by a Chinese action label. */
    static void keyHint(Canvas c,Paint cap,Paint label,Paint cjk,String glyph,float x,float y,String text){
        cap.setStyle(Paint.Style.FILL);cap.setColor(PANEL2);
        float gw=Math.max(20,glyph.length()*8+10);
        c.drawRoundRect(new RectF(x,y-14,x+gw,y+4),4,4,cap);
        FilmStyle.text(c,label,glyph,x+5,y-1,10,TEXT);
        FilmStyle.text(c,cjk,text,x+gw+6,y,11,MUTED);
    }
    static void bottomBar(Canvas c,Paint p,float w,float h){
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xff13110f);c.drawRect(0,h-40,w,h,p);
        p.setColor(LINE);c.drawRect(0,h-41,w,h-40,p);
    }
    static void battery(Canvas c,Paint p,int percent,boolean charging,float x,float y){
        int color=percent>=0&&percent<=15?ERROR:TEXT;
        p.setStyle(Paint.Style.FILL);p.setColor(0xc0000000);c.drawRect(x-2,y-2,x+29,y+15,p);
        p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.5f);
        c.drawRect(x,y,x+24,y+13,p);p.setStyle(Paint.Style.FILL);c.drawRect(x+24,y+4,x+27,y+9,p);
        if(percent>0)c.drawRect(x+3,y+3,x+3+18*percent/100f,y+10,p);
        String label=percent<0?"--%":percent+"%";
        text(c,p,label,x+35,y+13,17,color);
        if(charging)text(c,p,"+",x-12,y+13,16,ACCENT);
    }
    /** Width of a seven-segment string at the given height. Used to centre
     * values without going through a font measurement. */
    static float sevenWidth(String value,float height){
        if(value==null)return 0;
        float w=height*.56f,t=Math.max(1.6f,height*.10f),gap=height*.035f,total=0;
        for(int i=0;i<value.length();i++){
            char ch=value.charAt(i);
            if(ch==' ')total+=w*.55f;
            else if(ch=='.')total+=t+gap;
            else if(ch==':')total+=2*t+2*gap;
            else if(ch=='"')total+=w*.62f+gap;
            else total+=w+gap;
        }
        return total;
    }
    /** Seven-segment digits drawn as rects: no font dependency and cheap for
     * the camera's software Canvas. Supports 0-9, A-F/H/I/L/N/O/P/S/T/U/Y,
     * and - + : / . " space. */
    static void sevenSeg(Canvas c,Paint p,String value,float x,float y,float height,int color){
        p.setStyle(Paint.Style.FILL);p.setColor(color);
        float w=height*.56f,t=Math.max(1.6f,height*.10f),gap=height*.035f;
        float cursor=x;
        for(int i=0;i<value.length();i++){
            char ch=value.charAt(i);
            if(ch==' ')cursor+=w*.55f;
            else if(ch==':'){
                c.drawRect(cursor+w*.30f,y-height*.66f,cursor+w*.30f+t,y-height*.66f+t,p);
                c.drawRect(cursor+w*.30f,y-height*.30f,cursor+w*.30f+t,y-height*.30f+t,p);
                cursor+=t+gap*2;
            }else if(ch=='.'){
                c.drawRect(cursor,y-t,cursor+t,y,p);cursor+=t+gap;
            }else if(ch=='"'){
                // Seconds mark: two short strokes at the top, like a real tube.
                float bh=height*.16f;
                c.drawRect(cursor+w*.34f,y-height+t,cursor+w*.34f+t*1.3f,y-height+t+bh,p);
                c.drawRect(cursor+w*.68f,y-height+t,cursor+w*.68f+t*1.3f,y-height+t+bh,p);
                cursor+=w*.62f+gap;
            }else if(ch=='/'){
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(t);p.setColor(color);
                c.drawLine(cursor+w*.88f,y,cursor+w*.12f,y-height,p);
                p.setStyle(Paint.Style.FILL);
                cursor+=w+gap;
            }else if(ch=='+'){
                float mid=y-height*.46f;
                c.drawRect(cursor+t,mid-.8f,cursor+w-t,mid+.8f,p);
                c.drawRect(cursor+w/2-t/2,mid-height*.30f,cursor+w/2+t/2,mid+height*.30f,p);
                cursor+=w+gap;
            }else if(ch=='-'){
                // Middle bar: reads as a minus sign rather than an underline.
                seg(c,p,cursor,y,w,t,gap,false,false,false,false,false,false,true);cursor+=w+gap;
            }else if((ch>='0'&&ch<='9')||letter(ch)){
                segFor(ch,c,p,cursor,y,w,t,gap);cursor+=w+gap;
            }
        }
    }
    private static boolean letter(char value){
        return "ABCDEFHILNOPSTUY".indexOf(Character.toUpperCase(value))>=0;
    }
    private static void segFor(char raw,Canvas c,Paint p,float x,float y,float w,float t,float gap){
        char d=Character.toUpperCase(raw);
        boolean a,b,cc,dd,e,f,g;
        if(d>='0'&&d<='9'){
            // Correct seven-segment sets for 0-9. Verify any change against a
            // real tube: 1 has only b/c, 2 has no lower-right, 4 has f, 5 has
            // no upper-right, 7 has only a/b/c, 9 has no e and keeps d.
            a="02356789".indexOf(d)>=0;b="01234789".indexOf(d)>=0;
            cc="013456789".indexOf(d)>=0;dd="0235689".indexOf(d)>=0;
            e="0268".indexOf(d)>=0;f="045689".indexOf(d)>=0;
            g="2345689".indexOf(d)>=0;
        }else{
            // Segment table for the letters used by the digital readouts.
            // Verify against a tube: D has no upper-left, F uses a/e/f/g,
            // S no lower-left, and T needs the middle bar.
            b="ABDHNOPUY".indexOf(d)>=0;
            cc="ABDHNOUY".indexOf(d)>=0;
            f="ABCEFHILOPSTUY".indexOf(d)>=0;
            if(d=='I'){
                c.drawRect(x+w/2-t/2,y-w*1.78f+t+gap,x+w/2+t/2,y-t-gap,p);
                return;
            }
            a="ABCEFOPS".indexOf(d)>=0;
            dd="BCDELOSTU".indexOf(d)>=0;
            e="ABCDEFHILOTU".indexOf(d)>=0;
            g="ABDEFHNOPSTY".indexOf(d)>=0;
        }
        // a top, b upper-right, c lower-right, d bottom, e lower-left, f upper-left, g middle
        seg(c,p,x,y,w,t,gap,a,b,cc,dd,e,f,g);
    }
    /** Any white-balance string (raw key or "WB 日光" display text) as a
     * compact seven-segment code. */
    static String wbCode(String value){
        if(value==null)return "---";
        String v=value.toLowerCase(java.util.Locale.US);
        if(v.contains("auto")||v.contains("自动"))return "AUt";
        if(v.contains("daylight")||v.contains("日光"))return "dAY";
        if(v.contains("cloudy")||v.contains("阴天"))return "CLd";
        if(v.contains("shade")||v.contains("阴影"))return "SHd";
        if(v.contains("incandescent")||v.contains("白炽"))return "InC";
        if(v.contains("flash")||v.contains("闪光"))return "FLS";
        if(v.contains("color-temp")||v.contains("色温"))return "tEP";
        if(v.contains("fluorescent")||v.contains("荧光"))return "FLU";
        if(v.contains("underwater")||v.contains("水下"))return "Und";
        if(v.contains("custom")||v.contains("自定义"))return "CUS";
        return "---";
    }
    private static void seg(Canvas c,Paint p,float x,float y,float w,float t,float gap,
                            boolean a,boolean b,boolean cc,boolean d,boolean e,boolean f,boolean g){
        float top=y-w*1.78f,mid=top+(y-top)/2f,bot=y;
        if(a)c.drawRect(x+t+gap,top,x+w-t-gap,top+t,p);
        if(g)c.drawRect(x+t+gap,mid-t/2,x+w-t-gap,mid+t/2,p);
        if(d)c.drawRect(x+t+gap,bot-t,x+w-t-gap,bot,p);
        if(f)c.drawRect(x,top+t+gap,x+t,mid-gap,p);
        if(b)c.drawRect(x+w-t,top+t+gap,x+w,mid-gap,p);
        if(e)c.drawRect(x,mid+gap,x+t,bot-t-gap,p);
        if(cc)c.drawRect(x+w-t,mid+gap,x+w,bot-t-gap,p);
    }
    static int canColour(int index){return CAN_COLOURS[Math.abs(index)%CAN_COLOURS.length];}
}
