package org.hongen.sonylutprobe;

import android.app.Activity;
import android.graphics.*;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** A deliberately small, self-contained motion prototype for the M-Film-style
 * visual system. It never opens CameraEx or the photo/video pipelines.
 *
 * Entering the page draws one static frame only. Animation starts on demand so
 * a graphics problem cannot put the camera into a continuous draw loop. C1 runs
 * an eight-second benchmark at 30/60/unlimited fps and writes MOTION.JSN. */
public final class UiMotionDemoActivity extends Activity {
    private static final String TAG="SonyMotion";
    private MotionView view;
    private File run;
    private Thread.UncaughtExceptionHandler previousHandler;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        Lang.init(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON|WindowManager.LayoutParams.FLAG_FULLSCREEN);
        try{run=RunFiles.create(Environment.getExternalStorageDirectory());}
        catch(Throwable e){android.util.Log.w(TAG,"motion_run_unavailable",e);}
        event("motion_create","screen="+getResources().getDisplayMetrics().widthPixels+"x"
                +getResources().getDisplayMetrics().heightPixels);
        previousHandler=Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler(){
            public void uncaughtException(Thread t,Throwable e){
                event("motion_crash",t.getName()+" "+e);
                if(run!=null){
                    try{
                        File file=RunFiles.file(run,"CRASH.TXT");
                        PrintWriter out=new PrintWriter(new FileOutputStream(file,true));
                        try{out.println("thread="+t.getName());e.printStackTrace(out);out.flush();}finally{out.close();}
                    }catch(Throwable ignored){}
                }
                if(previousHandler!=null)previousHandler.uncaughtException(t,e);
            }});
        try{
            event("motion_font","begin");
            view=new MotionView(this);
            event("motion_view","constructed");
            setContentView(view);
            event("motion_content","set");
            view.invalidate();
        }catch(Throwable e){
            event("motion_setup_error",e.toString());
            android.widget.TextView fallback=new android.widget.TextView(this);
            fallback.setTextColor(0xfff2ead8);fallback.setBackgroundColor(0xff171513);
            fallback.setText(Lang.tr("动画原型无法初始化：\n")+e);
            setContentView(fallback);
        }
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e){
        if(e.getAction()!=KeyEvent.ACTION_DOWN)return true;
        int key=e.getKeyCode(),scan=e.getScanCode();
        int action=UiKeys.key(e);
        int dial=UiKeys.dial(e),front=UiKeys.frontDial(e),rear=UiKeys.rearDial(e),control=UiKeys.controlDial(e);
        boolean shutter=scan==518||key==518||key==KeyEvent.KEYCODE_CAMERA;
        if(key==KeyEvent.KEYCODE_BACK||key==KeyEvent.KEYCODE_MENU||scan==514){finish();return true;}
        if(view==null)return true;
        if(scan==622||key==622){view.benchmarkNextRate();return true;}
        if(scan==623||key==623){view.minimalBenchmark();return true;}
        if(action==UiKeys.LEFT||front<0||rear<0||dial<0){view.move(-1);return true;}
        if(action==UiKeys.RIGHT||front>0||rear>0||dial>0){view.move(1);return true;}
        if(action==UiKeys.UP){view.evStep(1);return true;}
        if(action==UiKeys.DOWN){view.evStep(-1);return true;}
        if(control!=0){view.evStep(control);return true;}
        if(shutter||action==UiKeys.OK){view.triggerShutter();return true;}
        return true;
    }

    private synchronized void event(String name,String detail){
        String line=SystemClock.elapsedRealtime()+" "+name+" "+detail;
        android.util.Log.i(TAG,line);
        if(run==null)return;
        try{
            // Open/close every line: the camera's FuFsys has no durable fsync,
            // but closing forces the visible file length even after a reset.
            FileOutputStream out=new FileOutputStream(RunFiles.file(run,"MOTION.JSN"),true);
            try{out.write((line+"\n").getBytes("UTF-8"));out.flush();}finally{out.close();}
        }catch(Throwable e){android.util.Log.w(TAG,"motion_journal_failed",e);}
    }

    @Override protected void onResume(){super.onResume();if(view!=null)view.resume();}
    @Override protected void onPause(){if(view!=null)view.pause();super.onPause();}
    @Override protected void onDestroy(){
        if(view!=null){view.pause();event("motion_summary",view.summary());}
        super.onDestroy();
    }

    private static final class MotionView extends View implements Runnable {
        private static final int W=640,H=480;
        private static final int BG=0xff171513,TEXT=0xfff2ead8,MUTED=0xff948a7b,
                ACCENT=0xffe8b23a,RED=0xffd9473f,DIM=0xff5d5549;
        private static final int[] FILM_COLOURS={0xffd7a33b,0xffb53d34,0xffd8b23f,0xff4f8b72,
                0xff8c8580,0xff3f6fa8,0xffa8a8a8};
        private static final String[] BRANDS={"KODAK","KODAK","KODAK","FUJI","KODAK","CINESTILL","FUJI"};
        private static final String[] NAMES={"PORTRA 400","GOLD 200","EKTAR 100","VELVIA 50",
                "TRI-X 400","800T","ACROS 100"};
        private static final String[] SUBTITLES={"Soft warm skin · C-41 · 36 EXP",
                "Golden hour · C-41 · 36 EXP","Punchy colour · C-41 · 36 EXP",
                "Vivid slide · E-6 · 36 EXP","Gritty contrast · B&W · 36 EXP",
                "Tungsten night · C-41 · 36 EXP","Fine grain · B&W · 36 EXP"};
        private static final String[] SHORT_NAMES={"PORTRA","GOLD","EKTAR","VELVIA","TRI-X","800T","ACROS"};
        private static final String[] NUMBERS={"400","200","100","50","400","","100"};
        private static final String[] TABS={"COLOUR","SLIDE","B&W","CINE"};

        private final Handler ui=new Handler();
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint cnPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint titlePaint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint seven=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint cap=new Paint(Paint.ANTI_ALIAS_FLAG);
                private final RectF dst=new RectF();private final RectF rr=new RectF();
        private final SimpleDateFormat clock=new SimpleDateFormat("HH:mm",Locale.US);
        private final float[] frameSamples=new float[120];
        private int frameSampleCount,frameSampleAt;
        private int selected=0;private float pos=0,vel=0;
        private float ev=0,evVel=0,evTarget=0;
        private int frames=1;private final int totalFrames=36;
        private static final int[] RATES={30,60,0};private int benchIndex=0,benchRate=30;private final int normalRate=30;
        private long lastTick,lastDraw,lastStats,toastAt,titleAt,shutterAt,benchmarkUntil,lastBenchLog;
        private boolean titleAnim,toastVisible;
        private String toast="";
        private boolean shutterRunning,shutterCounted;
        private boolean active,running,benchmark,minimal;private long minimalUntil;private float minPhase;
        private final Rect dirty=new Rect(),clip=new Rect();
        private boolean fullDirty,statsChanged;
        private double bgMs,shelfMs,textMs,statsMs;
        private float drawMax,drawCount;
        private float fps;private int fpsFrames;private long fpsAt;
        private String timeText="",fpsText="--",statsLine="FPS --   DRAW --",
                rateText="30 FPS",rateLine="30 FPS",frameText="01 / 36",evText="+0.0 EV",
                filmLine="PORTRA 400  ·  ISO 400";private int evShown;
        private boolean firstDraw;
        private final Activity owner;

        MotionView(Activity owner){
            super(owner);
            this.owner=owner;
            Typeface cjk=UiFont.get(owner);
            textPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF,Typeface.NORMAL));
            textPaint.setTextAlign(Paint.Align.LEFT);
            cnPaint.setTypeface(cjk);cnPaint.setTextAlign(Paint.Align.LEFT);
            titlePaint.setTypeface(Typeface.create(Typeface.SANS_SERIF,Typeface.BOLD));
            titlePaint.setTextAlign(Paint.Align.CENTER);
            seven.setTypeface(Typeface.create(Typeface.MONOSPACE,Typeface.BOLD));
            seven.setTextAlign(Paint.Align.LEFT);
            stroke.setStyle(Paint.Style.STROKE);stroke.setStrokeWidth(1.5f);
            frameText=String.format(Locale.US,"%02d / %02d",frames,totalFrames);
            evText=String.format(Locale.US,"%+.1f EV",ev);
            setFocusable(true);setFocusableInTouchMode(true);
            event("motion_scene","vector=true canisters="+NAMES.length);
        }

        private void event(String name,String detail){
            if(owner instanceof UiMotionDemoActivity)((UiMotionDemoActivity)owner).event(name,detail);
        }
        private static float clamp(float v,float lo,float hi){return v<lo?lo:v>hi?hi:v;}
        private static float easeOut(float t){t=clamp(t,0,1);float u=1-t;return 1-u*u*u;}
        private static float easeInOut(float t){t=clamp(t,0,1);return t<.5f?4*t*t*t:1-(-2*t+2)*(-2*t+2)*(-2*t+2)/2;}
        void resume(){active=true;}
        void pause(){active=false;ui.removeCallbacks(this);running=false;}
        private void kick(){
            if(!active||running)return;
            running=true;fullDirty=true;lastTick=System.nanoTime();
            fpsAt=System.currentTimeMillis();fpsFrames=0;
            ui.post(this);
        }
        int rate(){return benchmark?benchRate:normalRate;}
        void benchmarkNextRate(){
            minimal=false;
            if(benchmark){
                benchmark=false;rateText="30 FPS";rateLine="30 FPS";
                event("motion_bench_stop","rate="+benchRate+" "+summary());kick();return;
            }
            benchRate=RATES[benchIndex];benchIndex=(benchIndex+1)%RATES.length;
            rateText=benchRate==0?"UNCAPPED":benchRate+" FPS";rateLine=rateText+" BENCH";
            benchmark=true;benchmarkUntil=System.currentTimeMillis()+8000;lastBenchLog=0;
            resetStats();
            event("motion_bench_start","rate="+benchRate+" duration_ms=8000");
            kick();
        }
        void minimalBenchmark(){
            if(minimal){minimal=false;rateLine="30 FPS";event("motion_min_stop",summary());kick();return;}
            benchmark=false;minimal=true;minimalUntil=System.currentTimeMillis()+8000;minPhase=0;lastBenchLog=0;
            rateLine="MIN 30";resetStats();
            event("motion_min_start","duration=8000");kick();
        }
        private void resetStats(){
            bgMs=shelfMs=textMs=statsMs=0;drawCount=0;drawMax=0;
            frameSampleCount=0;frameSampleAt=0;fpsFrames=0;fpsAt=System.currentTimeMillis();
        }
        void move(int delta){
            int next=selected+delta;
            if(next<0||next>=NAMES.length)return;
            selected=next;toast=BRANDS[selected]+"  "+NAMES[selected];
            toastAt=System.currentTimeMillis();toastVisible=true;
            filmLine=NAMES[selected]+"  ·  ISO 400";
            titleAnim=true;titleAt=System.currentTimeMillis();
            kick();
        }
        void evStep(int steps){evTarget=clamp(evTarget+steps*0.333f,-2f,2f);kick();}
        void triggerShutter(){
            if(shutterRunning)return;
            shutterRunning=true;shutterCounted=false;shutterAt=System.currentTimeMillis();
            toastVisible=false;kick();
        }

        /** True when the current clip touches the horizontal band. */
        private boolean band(int top,int bottom){return clip.bottom>top&&clip.top<bottom;}
        private void fill(Canvas c,int top,int bottom,int color){
            int t=Math.max(top,clip.top),b=Math.min(bottom,clip.bottom);
            if(b<=t)return;
            p.setColor(color);c.drawRect(0,t,W,b,p);
        }
        // drawBackground paints one flat colour per band. The moving shelf only
        // damages y=84..312, so the camera never rasterises the full panel while
        // the carousel spins.
        private void drawBackground(Canvas c){
            fill(c,0,44,BG);
            fill(c,44,45,0xff3a352f);
            fill(c,45,80,0xff12100e);
            fill(c,80,258,0xff12110f);
            fill(c,258,260,0xff1d1a16);
            fill(c,260,262,0xff2b241d);
            fill(c,262,265,0xff1d1a16);
            if(band(265,273)){
                fill(c,265,273,0xff1d1a16);
                p.setColor(0xff2b241d);
                for(int x=0;x<W;x+=64)c.drawRect(x,265,Math.min(x+12,W),273,p);
            }
            fill(c,273,291,0xff1d1a16);
            if(band(291,299)){
                fill(c,291,299,0xff1d1a16);
                p.setColor(0xff2b241d);
                for(int x=0;x<W;x+=64)c.drawRect(x,291,Math.min(x+12,W),299,p);
            }
            fill(c,299,300,0xff1d1a16);
            fill(c,300,302,0xff2b241d);
            fill(c,302,304,0xff1d1a16);
            fill(c,304,386,0xff12110f);
            fill(c,386,440,0xff13110f);
            fill(c,440,480,0xff13110f);
        }
        /** Static typography and key hints. Hidden while the carousel moves so
         * motion frames only pay for the canisters, title and metering HUD. */
        private void drawChrome(Canvas c){
            if(band(0,84)){
                if(!running){
                    stroke.setColor(ACCENT);stroke.setStrokeWidth(2);c.drawCircle(26,22,8,stroke);
                    textPaint.setColor(TEXT);textPaint.setTextSize(15);c.drawText(Lang.tr("A7R2胶片相机"),44,27,textPaint);
                    textPaint.setColor(DIM);c.drawText("|",136,27,textPaint);
                    textPaint.setColor(ACCENT);c.drawText("SHELF",154,27,textPaint);
                    textPaint.setColor(MUTED);textPaint.setTextSize(13);c.drawText("UI MOTION DEMO",220,27,textPaint);
                    textPaint.setTextAlign(Paint.Align.RIGHT);c.drawText(timeText,616,27,textPaint);textPaint.setTextAlign(Paint.Align.LEFT);
                    float tx=220;textPaint.setTextSize(13);
                    for(int i=0;i<TABS.length;i++){
                        textPaint.setColor(i==0?ACCENT:DIM);c.drawText(TABS[i],tx,70,textPaint);
                        if(i==0){p.setColor(ACCENT);c.drawRect(tx,76,tx+42,78,p);}
                        tx+=92;
                    }
                }
            }
            if(band(386,440)){
                // The scale is part of the metering instrument, not chrome.
                stroke.setStrokeWidth(1);stroke.setColor(DIM);c.drawLine(300,420,500,420,stroke);
                for(int i=0;i<=12;i++){float x=300+200*i/12f;float hh=i%3==0?5:3;stroke.setColor(i==6?TEXT:DIM);c.drawLine(x,420-hh,x,420+hh,stroke);}
                if(!running){
                    textPaint.setTextSize(9);textPaint.setColor(MUTED);
                    c.drawText("FRAME",22,404,textPaint);c.drawText("APERTURE",236,404,textPaint);c.drawText("METER",300,404,textPaint);
                    textPaint.setColor(TEXT);textPaint.setTextSize(16);c.drawText("f/2.8",236,426,textPaint);
                }
            }
            if(band(440,480)&&!running){
                key(c,"◀ ▶",16,458,"货架");key(c,"▲ ▼",112,458,"测光");
                key(c,"SET",208,458,"快门");key(c,"C1",304,458,"基准");
                key(c,"C2",400,458,"纯色");key(c,"MENU",496,458,"返回");
            }
            textPaint.setColor(TEXT);
        }
        private void drawTop(Canvas c){
            textPaint.setTextAlign(Paint.Align.RIGHT);textPaint.setColor(MUTED);textPaint.setTextSize(13);
            c.drawText(timeText,616,27,textPaint);textPaint.setTextAlign(Paint.Align.LEFT);
        }
        @Override public void run(){
            if(!active||!running)return;
            long nowNano=System.nanoTime();float dt=(nowNano-lastTick)/1000000f;lastTick=nowNano;
            if(dt<0)dt=0;if(dt>50)dt=50;
            stepSprings(dt);
            long now=System.currentTimeMillis();
            if(shutterRunning){
                long t=now-shutterAt;
                if(t>=1250)shutterRunning=false;
                else if(t>=500&&!shutterCounted){
                    shutterCounted=true;frames=frames%totalFrames+1;
                    frameText=String.format(Locale.US,"%02d / %02d",frames,totalFrames);
                    toast="FRAME "+frames+" / "+totalFrames;toastAt=now;toastVisible=true;
                }
            }
            if(titleAnim&&now-titleAt>380)titleAnim=false;
            if(toastVisible&&now-toastAt>1700)toastVisible=false;
            if(benchmark){
                if(now>=benchmarkUntil){
                    int endedRate=benchRate;benchmark=false;rateText="30 FPS";rateLine="30 FPS";
                    event("motion_bench_end","rate="+endedRate+" "+summary());
                }else if(now-lastBenchLog>=2000){
                    lastBenchLog=now;event("motion_bench",summary());
                }
            }
            if(minimal){
                minPhase+=dt*0.004f;
                if(now>=minimalUntil){
                    minimal=false;rateLine="30 FPS";event("motion_min_end",summary());
                }else if(now-lastBenchLog>=2000){
                    lastBenchLog=now;event("motion_min",summary());
                }
            }
            int interval=minimal?33:(rate()==30?33:(rate()==60?16:0));
            if(now-lastStats>=500){lastStats=now;statsChanged=true;}
            if(interval==0||nowNano-lastDraw>=interval*1000000L){
                lastDraw=nowNano;
                if(fullDirty){fullDirty=false;invalidate();}
                else if(shutterRunning||minimal)invalidate();
                else{
                    dirty.setEmpty();
                    if(shelfMoving())dirty.union(0,84,W,312);
                    if(titleAnim)dirty.union(0,312,W,386);
                    if(evMoving())dirty.union(0,386,W,440);
                    if(toastVisible)dirty.union(0,84,W,312);
                    if(benchmark)dirty.union(0,84,W,440);
                    if(statsChanged)dirty.union(0,0,W,84);
                    if(dirty.isEmpty())dirty.union(0,84,W,312);
                    invalidate(dirty);
                }
            }
            boolean moving=benchmark||minimal||shutterRunning||!settled()||titleAnim||toastVisible;
            if(moving){ui.postDelayed(this,16);}
            else{running=false;event("motion_settle",summary());invalidate();}
        }
        private boolean shelfMoving(){return Math.abs(selected-pos)>=.0015f||Math.abs(vel)>=.02f;}
        private boolean evMoving(){return Math.abs(evTarget-ev)>=.0015f||Math.abs(evVel)>=.02f;}
        private boolean settled(){
            return Math.abs(selected-pos)<.0015f&&Math.abs(vel)<.02f
                    &&Math.abs(evTarget-ev)<.0015f&&Math.abs(evVel)<.02f;
        }
        private void stepSprings(float dt){
            float remaining=dt;
            while(remaining>0){
                float h=(remaining>8?8:remaining)/1000f;
                float w=6.2831853f*3.0f;
                float a=w*w*(selected-pos)-2*.72f*w*vel;vel+=a*h;pos+=vel*h;
                a=w*w*(evTarget-ev)-2*.72f*w*evVel;evVel+=a*h;ev+=evVel*h;
                remaining-=8;
            }
            if(Math.abs(selected-pos)<.0015f&&Math.abs(vel)<.02f){pos=selected;vel=0;}
            if(Math.abs(evTarget-ev)<.0015f&&Math.abs(evVel)<.02f){ev=evTarget;evVel=0;}
        }

        @Override protected void onDraw(Canvas c){
            long start=System.nanoTime();
            super.onDraw(c);
            Rect cb=c.getClipBounds();
            float sx=getWidth()/(float)W,sy=getHeight()/(float)H;
            clip.set(0,cb==null?0:(int)Math.floor(cb.top/sy),W,cb==null?H:(int)Math.ceil(cb.bottom/sy));
            c.save();c.scale(sx,sy);
            long t0=System.nanoTime();
            if(minimal)drawMinimal(c);
            else drawBackground(c);
            long t1=System.nanoTime();
            if(!minimal){
                if(band(84,312)){drawShelf(c);drawToast(c);}
                long t2=System.nanoTime();
                if(band(312,386))drawTitle(c);
                if(band(386,440))drawHud(c);
                drawChrome(c);
                drawShutter(c);
                long t3=System.nanoTime();
                if(band(0,84))drawStats(c);
                long t4=System.nanoTime();
                bgMs+=(t1-t0)/1000000.0;shelfMs+=(t2-t1)/1000000.0;
                textMs+=(t3-t2)/1000000.0;statsMs+=(t4-t3)/1000000.0;
            }else{
                long t2=System.nanoTime();drawStats(c);long t3=System.nanoTime();
                bgMs+=(t1-t0)/1000000.0;statsMs+=(t3-t2)/1000000.0;
            }
            c.restore();
            statsChanged=false;
            float ms=(System.nanoTime()-start)/1000000f;
            drawCount+=1;if(ms>drawMax)drawMax=ms;
            frameSamples[frameSampleAt]=ms;frameSampleAt=(frameSampleAt+1)%frameSamples.length;
            if(frameSampleCount<frameSamples.length)frameSampleCount++;
            if(!firstDraw){
                firstDraw=true;
                event("motion_hw","view="+hw());
                event("motion_first_draw","ms="+String.format(Locale.US,"%.2f",ms)
                        +" bg="+String.format(Locale.US,"%.2f",bgMs)
                        +" shelf="+String.format(Locale.US,"%.2f",shelfMs)
                        +" text="+String.format(Locale.US,"%.2f",textMs)
                        +" stats="+String.format(Locale.US,"%.2f",statsMs));
            }
            fpsFrames++;
            long now=System.currentTimeMillis();
            if(now-fpsAt>=500){
                fps=fpsFrames*1000f/(now-fpsAt);fpsFrames=0;fpsAt=now;
                fpsText=String.format(Locale.US,"%.1f",fps);timeText=clock.format(new Date());
                double sum=0;for(int i=0;i<frameSampleCount;i++)sum+=frameSamples[i];
                statsLine="FPS "+fpsText+"   DRAW "+String.format(Locale.US,"%.1f",sum/Math.max(1,frameSampleCount))+"ms";
            }
        }
        private boolean hw(){try{return isHardwareAccelerated();}catch(Throwable e){return false;}}
        private void drawMinimal(Canvas c){
            p.setColor(BG);c.drawRect(0,0,W,H,p);
            float x=W/2f+(float)Math.sin(minPhase)*180f;
            p.setColor(ACCENT);c.drawCircle(x,230,72,p);
            p.setColor(0xff2b2721);c.drawRect(60,360,580,362,p);
            p.setColor(0xff4f8b72);c.drawRect(60,366,60+440*((minPhase%1f+1f)%1f),372,p);
        }
        private void drawShelf(Canvas c){
            for(int pass=0;pass<2;pass++){
                for(int i=0;i<NAMES.length;i++){
                    float d=i-pos,ad=Math.abs(d);
                    if(ad>2.05f)continue;
                    boolean focus=ad<.35f;
                    if((pass==1)!=focus)continue;
                    float scale=1f/(1f+.50f*ad);
                    float cx=W/2f+d*172f*scale;
                    drawCanister(c,cx,278,i,scale,focus);
                }
            }
            float start=W/2f-(NAMES.length-1)*5;
            for(int i=0;i<NAMES.length;i++){p.setColor(i==selected?ACCENT:DIM);c.drawCircle(start+i*10,300,i==selected?3.2f:2.2f,p);}
        }
        private void drawCanister(Canvas c,float cx,float base,int idx,float scale,boolean focus){
            c.save();c.translate(cx,base);c.scale(scale,scale);
            p.setColor(0x55000000);rr.set(-68,-4,68,12);c.drawRoundRect(rr,10,8,p);
            p.setColor(0xff2a2722);rr.set(-58,-190,58,0);c.drawRoundRect(rr,16,16,p);
            p.setColor(FILM_COLOURS[idx]);rr.set(-52,-184,52,-6);c.drawRoundRect(rr,12,12,p);
            p.setColor(0xff151310);c.drawRect(-52,-138,52,-136,p);
            p.setColor(0xffc9c2b4);c.drawRect(-52,-186,52,-178,p);c.drawRect(-52,-14,52,-6,p);
            p.setColor(0xff2a2722);rr.set(-52,-194,52,-176);c.drawRoundRect(rr,8,8,p);
            p.setColor(0xff6d675c);rr.set(-43,-192,43,-179);c.drawRoundRect(rr,6,6,p);
            p.setColor(0xff161310);rr.set(-16,-191,16,-181);c.drawRoundRect(rr,5,5,p);
            p.setColor(0x33ffffff);c.drawRect(-45,-180,-38,-10,p);
            if(focus){
                textPaint.setTextAlign(Paint.Align.CENTER);textPaint.setColor(0xff1c1710);
                textPaint.setTextSize(10);c.drawText(BRANDS[idx],0,-156,textPaint);
                textPaint.setTextSize(19);c.drawText(SHORT_NAMES[idx],0,-92,textPaint);
                textPaint.setTextSize(26);c.drawText(NUMBERS[idx],0,-58,textPaint);
                textPaint.setTextSize(8);textPaint.setColor(0xffe9dfcd);c.drawText("35-36",0,-30,textPaint);
                textPaint.setTextAlign(Paint.Align.LEFT);
                stroke.setColor(0x99e8b23a);stroke.setStrokeWidth(1.5f);rr.set(-60,-196,60,4);c.drawRoundRect(rr,2,2,stroke);
            }
            c.restore();
        }
        private void drawTitle(Canvas c){
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setColor(ACCENT);textPaint.setTextSize(11);c.drawText(BRANDS[selected],320,323,textPaint);
            titlePaint.setColor(TEXT);titlePaint.setTextSize(38);
            float k=1f;if(titleAnim)k=easeOut((System.currentTimeMillis()-titleAt)/380f);
            titlePaint.setAlpha((int)(255*clamp(k,0,1)));
            c.drawText(NAMES[selected],320,356+(1-k)*8,titlePaint);titlePaint.setAlpha(255);
            textPaint.setColor(MUTED);textPaint.setTextSize(15);c.drawText(SUBTITLES[selected],320,379,textPaint);
            textPaint.setTextAlign(Paint.Align.LEFT);
        }
        private void drawHud(Canvas c){
            int et=Math.round(ev*10);if(et!=evShown){evShown=et;evText=String.format(Locale.US,"%+.1f EV",ev);}
            seven.setColor(RED);seven.setTextSize(24);c.drawText(frameText,20,429,seven);
            float nx=300+200*(clamp(ev,-2,2)+2)/4f;
            stroke.setColor(RED);stroke.setStrokeWidth(2);
            c.drawLine(nx,418,nx,410,stroke);c.drawLine(nx,410,nx-5,414,stroke);c.drawLine(nx,410,nx+5,414,stroke);
            textPaint.setColor(ACCENT);textPaint.setTextSize(11);c.drawText(evText,300,437,textPaint);
            textPaint.setTextAlign(Paint.Align.RIGHT);textPaint.setColor(MUTED);textPaint.setTextSize(12);
            c.drawText(filmLine,616,414,textPaint);c.drawText(rateText+"  ·  C1 BENCH",616,431,textPaint);
            textPaint.setTextAlign(Paint.Align.LEFT);
        }
        private void key(Canvas c,String glyph,float x,float y,String label){
            cap.setColor(0xff2b2721);rr.set(x,y-14,x+glyph.length()*8+10,y+4);c.drawRoundRect(rr,4,4,cap);
            textPaint.setColor(TEXT);textPaint.setTextSize(10);c.drawText(glyph,x+5,y-1,textPaint);
            cnPaint.setColor(MUTED);cnPaint.setTextSize(11);c.drawText(label,x+glyph.length()*8+16,y,cnPaint);
        }
        private void drawToast(Canvas c){
            if(!toastVisible)return;
            long age=System.currentTimeMillis()-toastAt;if(age>1700){toastVisible=false;return;}
            float k=easeOut(age/250f)*(age>1400?1-(age-1400)/300f:1);
            textPaint.setTextAlign(Paint.Align.CENTER);textPaint.setTextSize(12);
            float w=textPaint.measureText(toast)+34;
            p.setColor(0xcc17140f);rr.set(320-w/2,90,320+w/2,116);c.drawRoundRect(rr,13,13,p);
            stroke.setColor(0x99e8b23a);stroke.setStrokeWidth(1);rr.set(320-w/2+.5f,90.5f,320+w/2-.5f,115.5f);c.drawRoundRect(rr,13,13,stroke);
            textPaint.setColor(ACCENT);textPaint.setAlpha((int)(255*clamp(k,0,1)));
            c.drawText(toast,320,108,textPaint);textPaint.setAlpha(255);textPaint.setTextAlign(Paint.Align.LEFT);
        }
        private void drawShutter(Canvas c){
            if(!shutterRunning)return;
            long t=System.currentTimeMillis()-shutterAt;float close;
            if(t<360)close=easeInOut(t/360f);
            else if(t<520)close=1;
            else if(t<1250)close=1-easeOut((t-520)/730f);
            else close=0;
            if(close<=.001f)return;
            float curtain=(H/2f+2)*close;
            p.setColor(0xff060605);c.drawRect(0,0,W,curtain,p);c.drawRect(0,H-curtain,W,H,p);
            p.setColor(0xff2b241d);c.drawRect(0,Math.max(0,curtain-6),W,curtain,p);
            c.drawRect(0,H-curtain,W,Math.min(H,H-curtain+6),p);
            if(close<.995f){
                stroke.setColor(0x664c4438);stroke.setStrokeWidth(2);
                for(int i=0;i<6;i++){
                    float yy=curtain+6+(H-2*curtain-12)*i/5f;
                    c.drawLine(0,yy,W,yy+20,stroke);
                }
            }
            if(close>.96f){p.setColor(0x99e8b23a);c.drawRect(0,H/2f-1,W,H/2f+1,p);}
        }
        private void drawStats(Canvas c){
            textPaint.setTextAlign(Paint.Align.RIGHT);textPaint.setTextSize(10);
            textPaint.setColor(fps>=28?0xff9fbf8a:fps>0?ACCENT:RED);
            c.drawText(statsLine,616,58,textPaint);
            textPaint.setColor(DIM);c.drawText(rateLine,616,72,textPaint);
            textPaint.setTextAlign(Paint.Align.LEFT);
        }
        String summary(){
            double n=Math.max(1,drawCount);
            return "rate="+rate()+" fps="+String.format(Locale.US,"%.1f",fps)
                    +" frames="+(int)drawCount+" worst_draw_ms="+String.format(Locale.US,"%.2f",drawMax)
                    +" bg_ms="+String.format(Locale.US,"%.2f",bgMs/n)
                    +" shelf_ms="+String.format(Locale.US,"%.2f",shelfMs/n)
                    +" text_ms="+String.format(Locale.US,"%.2f",textMs/n)
                    +" stats_ms="+String.format(Locale.US,"%.2f",statsMs/n)
                    +" hw="+hw();
        }
    }
}
