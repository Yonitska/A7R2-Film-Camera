package org.hongen.sonylutprobe;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.FrameLayout;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A7R II movie mode: Sony CameraEx + MediaRecorder, with a supported-profile
 * picker. The 3D still LUT cannot be applied per video frame, so video uses the
 * same fitted matrix/gamma as live preview when available. */
public final class VideoActivity extends Activity implements SurfaceHolder.Callback {
    private static final String TAG="SonyVideo";
    private static final int REQ_CAMERA=0;
    private final Handler ui=new Handler();
    private HandlerThread thread;private Handler work;

    private FrameLayout root;private SurfaceView surface;private VideoHud hud;
    private SurfaceHolder holder;
    private Object cameraEx,recorder;private Camera camera;
    private Class<?> cameraType,openType,recorderType,parametersType;
    private final List<Object> recorderProxies=new ArrayList<Object>();
    private AfPointController af;
    private final AfPointController.Log afLog=new AfPointController.Log(){public void log(String name,String detail){event(name,detail);}};
    /** Guards the standby focus loop so a new camera request is not queued
     * while the previous one is still running. */
    private volatile boolean standbyFocusBusy;
    /** a7r2.52: one Camera.autoFocus request in flight at a time. The body
     * emits two scan codes per S1 press and its movie executor never calls
     * back for an overlapping request, which is what left movie AF dead. */
    private volatile boolean exitRequested;
    private boolean focusPending,focusQuiet;private long focusPendingAt,lastHalfFocusAt;private int focusGeneration;

    private String lutId,mediaId;private LutLibrary.Entry lutEntry;
    private boolean recipeMode;private RecipeData.Recipe recipe;private String recipeCameraName="";private RecipeCameraLook recipeLook;
    private boolean filmSelecting;private int filmSelectIndex,filmSelectOriginal;
    private int filmApplyGeneration;private String filmApplyError="";
    private NativePreviewSession look;private boolean lookApplied;private NativePreviewProfile lookProfile;
    private CameraNativePreviewDriver lookDriver;
    private VideoExposure exposure;private CameraExManualDriver exposureDriver;
    private CaptureTelemetry telemetry;private volatile int lastTelemetryAutoIso;
    private final List<String> afModes=new ArrayList<String>();private String afMode="";

    private final List<Profile> profiles=new ArrayList<Profile>();
    private int profileIndex;
    private static final int MENU_NONE=0,MENU_ROOT=1,MENU_FORMAT=2,MENU_WB=3,MENU_AF=4,MENU_EXPOSURE=5,MENU_EV=6,MENU_LOOK=7;
    private static final int[] ROOT_MODES={MENU_FORMAT,MENU_WB,MENU_AF,MENU_EXPOSURE,MENU_EV};
    private int menuMode=MENU_NONE;private final List<String> menuItems=new ArrayList<String>();
    private int startRetries;

    private volatile boolean active,opening,opened,closed,destroyed,exiting,recording,saving,prepared,preparing,starting,exitAfterSave,streamCompleted;
    /** When the recorder last reported ready; the movie engine starts preparing
     * asynchronously, so it must not be released immediately after that. */
    private volatile long preparedAt;private boolean exitAfterPrepare;private int exitWaitTicks;
    private long prepareToken;
    private final Map<String,String> originalSettings=new LinkedHashMap<String,String>();

    private File run;private FileOutputStream journal;private boolean journalClosed;
    private long recStartedAt;

    private final Runnable tick=new Runnable(){public void run(){
        if(!recording||hud==null)return;
        hud.setSeconds((int)((SystemClock.elapsedRealtime()-recStartedAt)/1000));
        ui.postDelayed(this,500);
    }};
    /** The A7R II's movie engine starts AFC while a clip is recording, but in
     * standby it stays at the last lock. Re-arm the camera's one-shot AF on a
     * short cadence so the finder keeps focusing before the shutter is hit. */
    private final Runnable standbyAfc=new Runnable(){public void run(){
        if(closed||destroyed)return;
        boolean idle=active&&opened&&!opening&&!recording&&!saving&&!preparing&&!starting&&!exiting&&!exitRequested
                &&hud!=null&&!hud.menuOpen&&!filmSelecting&&af!=null&&!af.active()&&!standbyFocusBusy;
        if(idle)triggerVideoFocus("standby_afc",true);
        ui.postDelayed(this,1600);
    }};

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        Lang.init(this);
        lutId=getIntent().getStringExtra("lut_id");
        recipeMode=getIntent().getBooleanExtra("recipe_mode",false);
        if(recipeMode){
            int id=getIntent().getIntExtra("recipe_id",-1);
            if(id<0||id>=RecipeData.ALL.length)recipeMode=false;
            else{recipe=RecipeData.ALL[id];recipeCameraName=getIntent().getStringExtra("camera_name");
                if(recipeCameraName==null||recipeCameraName.length()==0)recipeCameraName=Lang.groupName(RecipeData.GROUPS[recipe.group]);}
        }
        thread=new HandlerThread("sony-video");thread.start();work=new Handler(thread.getLooper());
        root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);
        surface=new SurfaceView(this);root.addView(surface,new FrameLayout.LayoutParams(-1,-1));
        surface.getHolder().addCallback(this);
        hud=new VideoHud(this);root.addView(hud,new FrameLayout.LayoutParams(-1,-1));
        setContentView(root);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        work.post(new Runnable(){public void run(){createRun();}});
    }

    private void createRun(){
        try{run=RunFiles.create(Environment.getExternalStorageDirectory());}
        catch(Throwable e){Log.w(TAG,"video_run_unavailable",e);}
        event("video_mode",(recipeMode?"recipe="+recipe.name+" camera="+recipeCameraName:"lut="+lutId)+" pid="+android.os.Process.myPid());
    }

    public void surfaceCreated(SurfaceHolder h){holder=h;startOpen();}
    public void surfaceChanged(SurfaceHolder h,int f,int w,int height){holder=h;startOpen();}
    public void surfaceDestroyed(SurfaceHolder h){}

    private void startOpen(){
        if(opening||opened||closed||!active)return;
        opening=true;
        work.post(new Runnable(){public void run(){
            try{
                openCamera();
                applyLook();
                createAndPrepareRecorder();
                ui.post(new Runnable(){public void run(){
                    opening=false;opened=true;
                    hud.setVisibility(android.view.View.VISIBLE);
                    // The AF spot is permanent finder chrome in movie mode,
                    // in standby as well as while a clip is being recorded.
                    if(af!=null)hud.setAf(true,false,af.x(),af.y());
                    refreshFilmStatus();
                    hud.setState("正在准备录像格式","","请稍候");
                    ui.removeCallbacks(standbyAfc);ui.postDelayed(standbyAfc,900);
                }});
            }catch(Throwable e){
                opening=false;
                fail("open",e);
            }
        }});
    }

    private void openCamera()throws Exception{
        mediaId=externalMediaId();
        cameraType=Class.forName("com.sony.scalar.hardware.CameraEx");
        openType=Class.forName("com.sony.scalar.hardware.CameraEx$OpenOptions");
        Object options=openType.newInstance();
        openType.getMethod("setPreview",boolean.class).invoke(options,false);
        openType.getMethod("setInheritSetting",boolean.class).invoke(options,true);
        openType.getMethod("setRecordingMode",int.class).invoke(options,1);
        openType.getMethod("setTargetMedia",String.class).invoke(options,mediaId);
        event("camera_open_begin","media="+mediaId+" recording_mode=1");
        cameraEx=cameraType.getMethod("open",int.class,openType).invoke(null,REQ_CAMERA,options);
        camera=(Camera)cameraType.getMethod("getNormalCamera").invoke(cameraEx);
        Camera.Parameters p=camera.getParameters();
        for(String key:new String[]{"scene-mode","iso-sensitivity","exposure-compensation","shooting-preview",
                "whitebalance","af-mode","metering-mode","picture-size","storage-fmt"})
            originalSettings.put(key,p.get(key));
        p=camera.getParameters();
        p.set("scene-mode","program-auto");
        p.set("iso-sensitivity","0");
        p.set("exposure-compensation","0");
        p.set("metering-mode","multi");
        camera.setParameters(p);
        Camera.Parameters after=camera.getParameters();
        event("camera_movie_ready","scene="+after.get("scene-mode")+" iso="+after.get("iso-sensitivity")
                +" ev="+after.get("exposure-compensation")+" format="+after.get("video-format")
                +" original="+originalSettings);
        StringBuilder movieParams=new StringBuilder();
        for(String item:after.flatten().split(";")){
            int eq=item.indexOf('=');if(eq<=0)continue;
            String k=item.substring(0,eq).toLowerCase(java.util.Locale.US);
            if(k.contains("exposure")||k.contains("iso")||k.contains("shutter")||k.contains("aperture")
                    ||k.contains("iris")||k.contains("scene")||k.contains("movie")||k.contains("gain")
                    ||k.contains("program")||k.contains("nd-filter")){
                if(movieParams.length()>0)movieParams.append(';');
                movieParams.append(item);
                if(movieParams.length()>6000)break;
            }
        }
        event("movie_parameters",movieParams.toString());
        camera.setPreviewDisplay(holder);
        camera.startPreview();
        af=new AfPointController(new CameraAfDriver(cameraEx,camera),afLog);af.open();
        event("video_af_ready","x="+af.x()+" y="+af.y());
        loadAfModes(camera.getParameters());
        exposureDriver=new CameraExManualDriver(cameraEx,camera,true);
        exposure=new VideoExposure(exposureDriver,new VideoExposure.Log(){
            public void log(String name,String detail){event(name,detail);}});
        String savedMode=getPreferences(0).getString("video_exposure_mode",
                getPreferences(0).getString("exposure_mode",VideoExposure.M));
        try{exposure.open(savedMode);}
        catch(Throwable e){event("video_exposure_error",root(e).toString());}
        try{
            Camera.Parameters focus= camera.getParameters();
            event("video_focus_parameters","focus-mode="+focus.get("focus-mode")
                    +" values="+focus.get("focus-mode-values")+" af-mode="+focus.get("af-mode")
                    +" af-values="+focus.get("af-mode-values")+" area="+focus.get("focus-area-type"));
            String focusMode=focus.get("focus-mode");
            if(focusMode==null||"manual".equals(focusMode)||"fixed".equals(focusMode)
                    ||"infinity".equals(focusMode)||"edof".equals(focusMode)){
                exposureDriver.write(oneSetting("focus-mode","auto"));
                String actual=camera.getParameters().get("focus-mode");
                event("video_focus_mode_set","requested=auto actual="+actual);
                if(!"auto".equals(actual)){
                    exposureDriver.write(oneSetting("focus-mode","continuous-video"));
                    event("video_focus_mode_set","requested=continuous-video actual="+camera.getParameters().get("focus-mode"));
                }
            }
        }catch(Throwable e){event("video_focus_mode_error",root(e).toString());}
        telemetry=new CaptureTelemetry(this,ui,new CaptureTelemetry.Observer(){
            public void changed(CaptureReadout v){
                lastTelemetryAutoIso=v.autoIso;
                if(exposure!=null)exposure.update(v);
                hud.setExposure(v.manualMode,v.shutterText(),v.apertureText(),v.isoText(),v.evText(),v.whiteBalanceText());
            }});
        try{telemetry.open(cameraEx,camera.getParameters());}
        catch(Throwable e){event("video_telemetry_error",root(e).toString());}
        publishExposure();
        opened=true;
    }
    private static String externalMediaId()throws Exception{
        Class<?> store=Class.forName("com.sony.scalar.provider.AvindexStore");
        String[] ids=(String[])store.getMethod("getExternalMediaIds").invoke(null);
        if(ids==null||ids.length==0||ids[0]==null)throw new IllegalStateException("相机外部媒体不可用");
        return ids[0];
    }

    private void createAndPrepareRecorder()throws Exception{
        recorderType=Class.forName("com.sony.scalar.media.MediaRecorder");
        parametersType=Class.forName("com.sony.scalar.media.MediaRecorder$Parameters");
        releaseRecorder();
        Object value=recorderType.newInstance();
        call(value,"setVideoSource",new Class<?>[]{int.class},1);
        call(value,"setOutputMedia",new Class<?>[]{String.class},mediaId);
        call(value,"setCamera",new Class<?>[]{cameraType},cameraEx);
        recorder=value;
        registerListeners();
        if(profiles.isEmpty())discoverProfiles();
        if(profiles.isEmpty())throw new IllegalStateException("相机没有报告可用的录像格式");
        if(profileIndex<0||profileIndex>=profiles.size())profileIndex=defaultProfile();
        applyProfile(profiles.get(profileIndex));
        prepared=false;preparing=true;preparedAt=0;
        final long token=++prepareToken;
        try{call(recorder,"prepare",new Class<?>[]{});}
        catch(Throwable e){preparing=false;event("recorder_prepare_error",root(e).toString());throw e;}
        event("recorder_prepare_return","profile="+profileLabel());
        // The vendor callback arrives on this worker's looper. Never block that
        // looper: a bounded fallback marks a synchronous prepare ready.
        work.postDelayed(new Runnable(){public void run(){
            if(token!=prepareToken||prepared)return;
            prepared=true;preparing=false;preparedAt=SystemClock.elapsedRealtime();
            event("recorder_ready_fallback","profile="+profileLabel());
            ui.post(new Runnable(){public void run(){if(!closed&&!destroyed)readyHud();}});
        }},1200);
    }

    private void discoverProfiles()throws Exception{
        Object supported=null;
        try{supported=call(recorder,"getSupportedParameters",new Class<?>[]{});}
        catch(Throwable missing){event("supported_parameters_error",root(missing).toString());}
        if(supported==null)supported=call(recorder,"getParameters",new Class<?>[]{});
        if(supported==null)throw new IllegalStateException("无法读取录像参数");
        List<?> raw=(List<?>)call(supported,"getSupportedCamcoderProfiles",new Class<?>[]{});
        if(raw==null)return;
        for(Object item:raw){
            String size=field(item,"videoSize"),rate=field(item,"videoFrameRate");
            String bitrate=field(item,"videoEncodingBitRate"),aspect=field(item,"videoAspectRatio");
            String format=field(item,"outputFormat");
            if(size==null||size.length()==0)continue;
            profiles.add(new Profile(format,aspect,bitrate,rate,size));
        }
        Collections.sort(profiles,new Comparator<Profile>(){public int compare(Profile a,Profile b){
            int byFormat=a.rank()-b.rank();if(byFormat!=0)return byFormat;
            long ab=a.bits(),bb=b.bits();if(ab!=bb)return ab>bb?-1:1;
            return b.size.compareTo(a.size);
        }});
        profileIndex=defaultProfile();
        String saved=getPreferences(0).getString("video_profile",null);
        if(saved!=null)for(int i=0;i<profiles.size();i++)if(profiles.get(i).label().equals(saved)){profileIndex=i;break;}
        StringBuilder line=new StringBuilder();
        for(Profile p:profiles){if(line.length()>0)line.append(" | ");line.append(p.label());}
        event("profiles","count="+profiles.size()+" default="+profileLabel()+" values="+line);
    }
    private int defaultProfile(){
        int best=0;
        for(int i=0;i<profiles.size();i++){
            Profile p=profiles.get(i);
            if(p.rank()==0&&(best==0||p.bits()>profiles.get(best).bits()))best=i;
        }
        return best;
    }
    private static String field(Object target,String name){
        try{Field f=target.getClass().getField(name);Object v=f.get(target);return v==null?null:String.valueOf(v);}
        catch(Throwable e){return null;}
    }
    private void applyProfile(Profile p)throws Exception{
        Object params=null;
        try{params=call(recorder,"getParameters",new Class<?>[]{});}catch(Throwable ignored){}
        if(params==null)params=parametersType.newInstance();
        call(params,"setOutputFormat",new Class<?>[]{String.class},p.output());
        if(p.aspect!=null)call(params,"setVideoAspectRatio",new Class<?>[]{String.class},p.aspect);
        if(p.bitrate!=null)call(params,"setVideoEncodingBitRate",new Class<?>[]{String.class},p.bitrate);
        if(p.frame!=null)call(params,"setVideoFrameRate",new Class<?>[]{String.class},p.frame);
        if(p.size!=null)call(params,"setVideoSize",new Class<?>[]{String.class},p.size);
        call(recorder,"setParameters",new Class<?>[]{parametersType},params);
        String flat="";
        try{flat=String.valueOf(call(params,"flatten",new Class<?>[]{}));}catch(Throwable ignored){}
        event("recorder_profile","label="+p.label()+" output="+p.output()+" params="+flat);
    }
    private String profileLabel(){return profiles.isEmpty()?"无格式":profiles.get(Math.max(0,Math.min(profiles.size()-1,profileIndex))).label();}
    private void readyHud(){readyHud(true);}
    private void readyHud(boolean clearError){
        if(clearError)hud.setError("");
        if(recording){
            hud.setState("录像中",profileLabel(),"快门 停止 · 方向键 切换焦点 · MENU 设置");
            return;
        }
        if(preparing||!prepared){
            hud.setState("正在准备录像格式",profileLabel(),"请稍候");
            return;
        }
        String look=recipeMode?""
                :(lutEntry==null?" · "+Lang.t("近似胶卷"):" · "+Lang.t("近似胶卷")+" "+lutEntry.name);
        refreshFilmStatus();
        hud.setState(Lang.t("录像待机")+look,profileLabel(),recipeMode
                ?(filmApplyError.length()>0?Lang.t("胶片切换失败")+" · "+Lang.tr(filmApplyError)
                    :"快门 录制 · C3 胶片 · MENU 设置 · ↓ AF · ← 拍照")
                :Lang.t("快门 录制 · MENU 设置 · ↓ AF · ")+Lang.tr(exposureHint())+Lang.t(" · ← 拍照"));
    }
    private String exposureHint(){
        String mode=exposure==null?VideoExposure.M:exposure.mode();
        if(VideoExposure.M.equals(mode))return "前 光圈 · 后 快门 · 增益轮 ISO";
        if(VideoExposure.A.equals(mode))return "增益轮 光圈 · 前后轮 EV";
        if(VideoExposure.S.equals(mode))return "增益轮 快门 · 前后轮 EV";
        return "增益轮 ISO · 前后轮 EV";
    }

    private void registerListeners()throws Exception{
        recorderProxies.clear();
        recorderProxies.add(listener("OnPreparedListener",new Listener(){
            public void call(String name,Object[] args){
                prepared=true;preparing=false;preparedAt=SystemClock.elapsedRealtime();
                event("recorder_prepared_callback","profile="+profileLabel());
                ui.post(new Runnable(){public void run(){if(!closed&&!destroyed)readyHud();}});
            }}));
        recorderProxies.add(listener("OnRecordListener",new Listener(){
            public void call(String name,Object[] args){
                if("onStarted".equals(name))ui.post(new Runnable(){public void run(){
                    starting=false;recording=true;saving=false;streamCompleted=false;prepared=true;startRetries=0;recStartedAt=SystemClock.elapsedRealtime();
                    hud.setRecording(true);hud.setSaving(false);
                    hud.setState("录像中",profileLabel(),"快门 停止 · 方向键 切换焦点 · MENU 设置");
                    event("recording_started","profile="+profileLabel());ui.removeCallbacks(tick);ui.post(tick);
                    // Re-arm the movie AF once the clip is live; some A7R II
                    // sessions stop the finder AF drive at start().
                    triggerVideoFocus("record_start");
                }});
                else if("onStopped".equals(name))ui.post(new Runnable(){public void run(){
                    recording=false;ui.removeCallbacks(tick);hud.setAf(false,false,0,0);hud.setRecording(false);
                    if(!streamCompleted){saving=true;hud.setSaving(true);
                        hud.setState("正在保存视频",profileLabel(),"请稍候，保存完成后可再次录制");}
                    event("recording_stopped","");
                }});
            }}));
        recorderProxies.add(listener("OnStreamWriteListener",new Listener(){
            public void call(String name,Object[] args){
                if("onCompleted".equals(name)){
                    event("stream_write_completed","");
                    ui.post(new Runnable(){public void run(){
                        saving=false;prepared=false;recording=false;preparing=true;streamCompleted=true;ui.removeCallbacks(tick);
                        hud.setSaving(false);hud.setRecording(false);
                        if(exitAfterSave){exitAfterSave=false;exitToPhoto();return;}
                        hud.setState("已保存",profileLabel(),"正在准备下一段录像…");
                        event("video_saved","");
                        work.post(new Runnable(){public void run(){
                            try{createAndPrepareRecorder();}
                            catch(Throwable e){prepared=false;preparing=false;fail("next_recorder",e);}
                        }});
                    }});
                }
            }}));
        recorderProxies.add(listener("OnErrorListener",new Listener(){
            public void call(String name,Object[] args){
                final int code=args!=null&&args.length>0&&args[0] instanceof Integer?(Integer)args[0]:-1;
                event("recorder_error","code="+code);
                ui.post(new Runnable(){public void run(){
                    recording=false;saving=false;starting=false;ui.removeCallbacks(tick);hud.setRecording(false);hud.setSaving(false);
                    hud.setError("录像错误 "+code);hud.setState("录像待机",profileLabel(),"可重试或按 ← 返回拍照");
                }});
            }}));
        recorderProxies.add(listener("OnRecTimeListener",new Listener(){
            public void call(String name,Object[] args){
                if(args!=null&&args.length>0&&args[0] instanceof Integer){
                    final int value=(Integer)args[0];
                    ui.post(new Runnable(){public void run(){if(recording)hud.setSeconds(value);}});
                }
            }}));
    }
    private interface Listener{void call(String name,Object[] args);}
    private Object listener(final String simpleName,final Listener callback)throws Exception{
        final Class<?> type=Class.forName("com.sony.scalar.media.MediaRecorder$"+simpleName);
        Object proxy=Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},new InvocationHandler(){
            public Object invoke(Object proxy,Method method,Object[] args){
                if(method.getDeclaringClass()==Object.class){
                    if(method.getName().equals("hashCode"))return System.identityHashCode(proxy);
                    if(method.getName().equals("equals"))return proxy==args[0];
                    return "SonyVideo:"+simpleName;
                }
                try{callback.call(method.getName(),args);}catch(Throwable e){Log.w(TAG,"listener "+method.getName(),e);}
                Class<?> value=method.getReturnType();
                if(value==boolean.class)return Boolean.FALSE;
                if(value==int.class)return Integer.valueOf(0);
                if(value==long.class)return Long.valueOf(0L);
                return null;
            }
        });
        String setter="set"+simpleName.substring(0,2)+simpleName.substring(2);
        call(recorder,setter,new Class<?>[]{type},proxy);
        return proxy;
    }

    private void applyLook(){
        if(recipeMode){
            applyRecipeLook();
            return;
        }
        if(lutId==null)return;
        try{
            lutEntry=new LutLibrary(ProductFiles.library()).find(lutId);
            if(lutEntry==null){event("look_skipped","no_lut");return;}
            // Movie viewfinding is always the approximate film look. Unlike
            // stills (where the A7R II tints its OSD when the matrix is set),
            // here the tint belongs to the recorded framing and the previous
            // opt-out made the standby finder look like a different film.
            event("video_look_choice","enabled=true forced=true");
            enableLook();
        }catch(Throwable e){
            lookApplied=false;event("look_error",root(e).toString());
            Log.w(TAG,"video_look_failed",e);
        }
    }
    /** Recipe video uses the camera's own picture controls, exactly like the
     * recipe still path. There is no LUT or fitted preview matrix: the live
     * finder and the encoded clip both come from the camera. */
    private void applyRecipeLook(){
        if(recipe==null||cameraEx==null||camera==null)return;
        try{
            recipeLook=new RecipeCameraLook(new CameraExManualDriver(cameraEx,camera,true),
                    new RecipeCameraLook.Log(){public void log(String name,String detail){event(name,detail);}});
            recipeLook.snapshot();
            recipeLook.apply(recipe);
            if(exposure!=null)exposure.refresh();
            event("recipe_look_ready","camera="+recipeCameraName+" recipe="+recipe.name
                    +" parameters="+RecipeData.summary(recipe));
        }catch(Throwable e){
            event("recipe_look_error",root(e).toString());
            Log.w(TAG,"video_recipe_look_failed",e);
        }
    }
    private void refreshFilmStatus(){
        if(hud==null)return;
        if(recipeMode&&recipe!=null){
            int count=RecipeData.countInGroup(RecipeData.groupOf(recipeIdOf()));
            hud.setFilmStatus(Lang.recipeName(recipe),recipeLocalOf()+1,count,filmSelecting);
        }else hud.setFilmStatus("",0,0,false);
    }
    private int recipeIdOf(){return indexOfRecipe(recipe);}
    private int recipeLocalOf(){
        int id=recipeIdOf();
        return id<0?0:RecipeData.localIndex(id);
    }
    private void openFilmSelection(){
        if(!recipeMode||recipe==null||recipeLook==null||closed||destroyed||!opened||opening
                ||recording||saving||preparing||starting||exiting
                ||hud==null||hud.menuOpen||(af!=null&&af.active())
                ||PoseDirectorOverlay.isShowing())return;
        int id=recipeIdOf();
        if(id<0)return;
        int group=RecipeData.groupOf(id),count=RecipeData.countInGroup(group);
        if(count<=0)return;
        filmSelecting=true;filmSelectOriginal=id;filmApplyError="";
        filmSelectIndex=RecipeData.localIndex(id);
        if(filmSelectIndex<0||filmSelectIndex>=count)filmSelectIndex=0;
        event("video_film_menu_open","recipe="+recipe.name+" index="+filmSelectIndex+" count="+count);
        refreshFilmStatus();
        hud.setError("");
        hud.setState("选择胶片",profileLabel(),"C3 确认 · MENU 取消 · 拨轮/←→ 换胶片");
    }
    private void handleFilmKey(KeyEvent e){
        if(e.getAction()!=KeyEvent.ACTION_DOWN)return;
        int key=e.getKeyCode(),scan=e.getScanCode();
        if(A7R2Keys.filmC3(scan,key)){
            if(e.getRepeatCount()==0)confirmFilmSelection();
            return;
        }
        int action=UiKeys.key(e);
        if(action==UiKeys.BACK||key==KeyEvent.KEYCODE_BACK||key==KeyEvent.KEYCODE_MENU||scan==514){
            if(e.getRepeatCount()==0)cancelFilmSelection();
            return;
        }
        int dial=UiKeys.dial(e),control=UiKeys.controlDial(e);
        if(action==UiKeys.UP||action==UiKeys.LEFT||dial==-1||control==-1){moveFilm(-1);return;}
        if(action==UiKeys.DOWN||action==UiKeys.RIGHT||dial==1||control==1){moveFilm(1);return;}
    }
    private void moveFilm(int direction){
        if(!filmSelecting||recipe==null)return;
        int current=recipeIdOf();
        if(current<0)return;
        int group=RecipeData.groupOf(current),count=RecipeData.countInGroup(group);
        if(count<=0)return;
        filmSelectIndex=(filmSelectIndex+direction+count)%count;
        int id=RecipeData.recipeAt(group,filmSelectIndex);
        if(id<0||id>=RecipeData.ALL.length)return;
        recipe=RecipeData.ALL[id];filmApplyError="";
        event("video_film_menu_step","recipe="+recipe.name+" index="+filmSelectIndex+"/"+count);
        refreshFilmStatus();
        scheduleFilmApply(id);
    }
    private void confirmFilmSelection(){
        if(!filmSelecting)return;
        int id=recipeIdOf();
        if(id<0){cancelFilmSelection();return;}
        filmSelecting=false;
        int group=RecipeData.groupOf(id),local=RecipeData.localIndex(id);
        RecipeMemory.save(this,group,local);
        getPreferences(0).edit().putInt("recipe_pick_"+group,local).commit();
        event("video_film_selected","recipe="+recipe.name+" camera="+recipeCameraName+" group="+group+" local="+local);
        refreshFilmStatus();
        hud.setError("");
        readyHud();
    }
    private void cancelFilmSelection(){
        if(!filmSelecting)return;
        filmSelecting=false;
        if(filmSelectOriginal>=0&&filmSelectOriginal<RecipeData.ALL.length){
            recipe=RecipeData.ALL[filmSelectOriginal];
            scheduleFilmApply(filmSelectOriginal);
        }
        filmApplyError="";
        event("video_film_selection_canceled","recipe="+recipe.name);
        refreshFilmStatus();
        hud.setError("");
        readyHud();
    }
    private void scheduleFilmApply(final int id){
        final RecipeCameraLook look=recipeLook;
        if(look==null||id<0||id>=RecipeData.ALL.length)return;
        final int generation=++filmApplyGeneration;
        work.post(new Runnable(){public void run(){
            if(closed||generation!=filmApplyGeneration||!recipeMode||recipeLook!=look)return;
            if(recording||saving||preparing||starting||exiting)return;
            try{
                recipeLook.apply(RecipeData.ALL[id]);
                if(exposure!=null)exposure.refresh();
                publishExposure();
                event("video_film_applied","recipe="+RecipeData.ALL[id].name+" generation="+generation);
                ui.post(new Runnable(){public void run(){
                    if(closed||destroyed)return;
                    if(recipeIdOf()==id){
                        filmApplyError="";
                        refreshFilmStatus();
                        if(!recording&&!filmSelecting)readyHud();
                    }
                }});
            }catch(Throwable e){
                final String failure=root(e).toString();
                event("video_film_apply_error","recipe="+RecipeData.ALL[id].name+" "+failure);
                ui.post(new Runnable(){public void run(){
                    if(closed||destroyed)return;
                    filmApplyError=failure;
                    hud.setError("胶片切换失败："+failure);
                    if(!recording&&!filmSelecting)readyHud(false);
                }});
            }
        }});
    }
    private void enableLook()throws Exception{
        if(lutEntry==null)throw new IllegalStateException("胶卷不可用");
        if(lookProfile==null){
            lookProfile=PreviewProfileStore.load(lutEntry);
            if(lookProfile==null){
                event("look_fit_begin","lut="+lutEntry.name);
                lookProfile=PreviewFitting.ensure(lutEntry);
            }
        }
        if(lookProfile==null)throw new IllegalStateException("没有可用的近似预览");
        if(look==null){
            CameraNativePreviewDriver driver=new CameraNativePreviewDriver(cameraEx,camera);
            lookDriver=driver;look=new NativePreviewSession(driver);look.open();
        }
        look.apply(lookProfile);lookApplied=true;
        event("look_applied","lut="+lutEntry.name+" mae="+lookProfile.mae+" warning="+lookProfile.largeError());
    }
    private void setVideoLook(final boolean enabled){
        work.post(new Runnable(){public void run(){
            try{
                if(enabled)enableLook();
                else if(look!=null){look.restore();lookApplied=false;}
                getPreferences(0).edit().putBoolean("video_look",enabled).commit();
                event("video_look_toggle","enabled="+enabled+" applied="+lookApplied);
                publishExposure();
                ui.post(new Runnable(){public void run(){afterSetting(false);}});
            }catch(Throwable e){
                final String message=root(e).getMessage();
                final String fallback=root(e).getClass().getSimpleName();
                event("video_look_toggle_error",root(e).toString());
                ui.post(new Runnable(){public void run(){
                    hud.setError("取景色彩切换失败："+(message==null?fallback:message));
                    afterSetting(true);
                }});
            }
        }});
    }

    private static int indexOfRecipe(RecipeData.Recipe target){
        for(int i=0;i<RecipeData.ALL.length;i++)if(RecipeData.ALL[i]==target)return i;
        return -1;
    }
    private static int number(String value){
        try{return Integer.parseInt(value);}catch(RuntimeException e){return 0;}
    }
    private static Map<String,String> oneSetting(String key,String value){
        Map<String,String> patch=new LinkedHashMap<String,String>();patch.put(key,value);return patch;
    }
    private void publishExposure(){
        if(exposure==null)return;
        // VideoExposure keeps a snapshot from the commit that just happened.
        // Reading Camera.getParameters() again here added a full round-trip
        // and made the HUD text arrive one dial behind the image.
        final CaptureReadout r=exposure.readout();
        ui.post(new Runnable(){public void run(){
            if(closed||destroyed)return;
            if(r.autoIso==0&&r.manualIso==0)r.autoIso=lastTelemetryAutoIso;
            hud.setExposure(r.manualMode,r.shutterText(),r.apertureText(),r.isoText(),r.evText(),r.whiteBalanceText());
        }});
    }

    private void loadAfModes(Camera.Parameters p){
        afModes.clear();
        String csv=p.get("af-mode-values");
        if(csv!=null)for(String raw:csv.split(",")){
            String value=raw.trim().toLowerCase(java.util.Locale.US);
            if(value.length()>0&&!afModes.contains(value))afModes.add(value);
        }
        String current=p.get("af-mode");
        afMode=current==null?"":current.trim().toLowerCase(java.util.Locale.US);
        event("video_af_modes","values="+afModes+" current="+afMode);
    }
    private static String afModeLabel(String value){
        if(value==null||value.length()==0)return "AF --";
        if("af-a".equals(value))return "AF-A 自动";
        if("af-s".equals(value))return "AF-S 单次";
        if("af-c".equals(value))return "AF-C 连续";
        if("movie".equals(value))return "MOVIE 连续 AF";
        if("dmf".equals(value))return "DMF 全时手动";
        if("mf".equals(value))return "MF 手动";
        return value.toUpperCase(java.util.Locale.US);
    }
    /** Movie mode inherits the movie AF mode, but a half press must ask the
     * camera to run a focus cycle. Sony's own executor uses Camera.autoFocus. */
    private void triggerVideoFocus(final String origin){triggerVideoFocus(origin,false);}
    private void triggerVideoFocus(final String origin,final boolean quiet){
        work.post(new Runnable(){public void run(){
            if(closed||camera==null||exitRequested)return;
            if(quiet){
                if(recording||saving||preparing||starting||exiting||hud==null||hud.menuOpen
                        ||(af!=null&&af.active()))return;
                if(standbyFocusBusy)return;
            }else{
                long now=SystemClock.elapsedRealtime();
                // One S1 press reaches this app as scan 516 and scan 517. The
                // A7R II movie executor accepts a single request; the second,
                // overlapping autoFocus never ran and never called back. Only
                // merge while the first request is still pending, so a real
                // second half press after S1 release is not swallowed.
                if(focusPending&&now-lastHalfFocusAt<450){
                    event("video_focus_merged",origin+" since_ms="+(now-lastHalfFocusAt));return;
                }
                lastHalfFocusAt=now;
            }
            long now=SystemClock.elapsedRealtime();
            if(focusPending){
                long age=now-focusPendingAt;
                boolean reclaim;
                if(!quiet){
                    // a7r2.56: every user half press gets a fresh drive. S1
                    // release no longer cancels, so the previous request has
                    // had the time between presses to focus; replace it now.
                    reclaim=true;
                }else{
                    // The standby loop may only replace its own request, and
                    // never the user's half-press.
                    reclaim=focusQuiet&&age>=1400;
                }
                if(!reclaim){
                    event("video_focus_skipped",origin+" age_ms="+age+" quiet_pending="+focusQuiet);return;
                }
                focusGeneration++;focusPending=false;
                try{camera.cancelAutoFocus();event("video_focus_cancel_stale",origin+" age_ms="+age);}
                catch(Throwable e){event("video_focus_cancel_error",root(e).toString());}
            }
            focusPending=true;focusQuiet=quiet;focusPendingAt=now;
            final int generation=++focusGeneration;
            if(quiet){
                standbyFocusBusy=true;
                // Some Sony camera builds do not invoke the callback for a
                // cancelled/queued AF request; never let the loops wedge.
                ui.postDelayed(new Runnable(){public void run(){standbyFocusBusy=false;}},2600);
            }
            try{
                // ShotDirector (MIT) uses the callback-less form on this body;
                // the A7R II's movie wrapper never delivered autoFocus()
                // callbacks in R00685E2 (11 requests, 0 results), so do not
                // depend on them. The S1 release / stale timer clears pending.
                event("video_focus_begin",origin);
                camera.autoFocus(null);
            }catch(Throwable e){
                if(generation==focusGeneration)focusPending=false;
                if(quiet)standbyFocusBusy=false;
                event("video_focus_error",root(e).toString());
            }
        }});
    }
    /** Cancel a request the vendor still holds. Called before the movie
     * session restores its parameters; otherwise the queued focus can survive
     * into the next still session and leave the shutter unarmable. */
    private void cancelVideoFocus(String origin){
        if(camera==null)return;
        focusGeneration++;focusPending=false;
        try{camera.cancelAutoFocus();event("video_focus_canceled",origin);}
        catch(Throwable e){event("video_focus_cancel_error",origin+" "+root(e).toString());}
    }
    private void applyAfMode(final String value){
        work.post(new Runnable(){public void run(){
            try{
                if(exposureDriver==null)throw new IllegalStateException("摄像机不可用");
                Map<String,String> patch=new LinkedHashMap<String,String>();patch.put("af-mode",value);
                exposureDriver.write(patch);
                String actual=camera.getParameters().get("af-mode");
                if(actual!=null)actual=actual.trim().toLowerCase(java.util.Locale.US);
                if(!value.equals(actual))throw new IllegalStateException("对焦模式回读 "+actual+" 期望 "+value);
                afMode=actual;
                event("video_af_mode_set","value="+value);
                publishExposure();
                ui.post(new Runnable(){public void run(){afterSetting(false);}});
            }catch(Throwable e){
                final String message=root(e).getMessage();
                final String fallback=root(e).getClass().getSimpleName();
                event("video_af_mode_error",root(e).toString());
                ui.post(new Runnable(){public void run(){
                    hud.setError("对焦模式设置失败："+(message==null?fallback:message));
                    afterSetting(true);
                }});
            }
        }});
    }
    private void applyWhiteBalance(final String value){
        work.post(new Runnable(){public void run(){
            try{
                if(exposure==null)throw new IllegalStateException("曝光控制不可用");
                exposure.setWhiteBalance(value);
                if(lookDriver!=null)try{lookDriver.rebaseWhiteBalance();}
                catch(Throwable e){event("look_wb_rebase_error",root(e).toString());}
                publishExposure();
                ui.post(new Runnable(){public void run(){afterSetting(false);}});
            }catch(Throwable e){
                final String message=root(e).getMessage();
                final String fallback=root(e).getClass().getSimpleName();
                event("video_wb_error",root(e).toString());
                ui.post(new Runnable(){public void run(){
                    hud.setError("白平衡设置失败："+(message==null?fallback:message));
                    afterSetting(true);
                }});
            }
        }});
    }
    /** Same mode order as the photo C1 key: M -> A -> S -> M. */
    private void cycleVideoExposureMode(){
        if(exposure==null){
            hud.setError("曝光控制不可用");
            return;
        }
        work.post(new Runnable(){public void run(){
            try{
                VideoExposure session=exposure;
                if(session==null)throw new IllegalStateException("曝光控制不可用");
                String current=session.mode();
                String next=VideoExposure.M.equals(current)?VideoExposure.A
                        :VideoExposure.A.equals(current)?VideoExposure.S:VideoExposure.M;
                session.setMode(next);
                event("video_mode_cycle","from="+current+" to="+next);
                getPreferences(0).edit().putString("video_exposure_mode",next).commit();
                publishExposure();
                ui.post(new Runnable(){public void run(){afterSetting(false);}});
            }catch(Throwable e){
                final String message=root(e).getMessage();
                final String fallback=root(e).getClass().getSimpleName();
                event("video_mode_cycle_error",root(e).toString());
                ui.post(new Runnable(){public void run(){
                    hud.setError("曝光模式切换失败："+(message==null?fallback:message));
                }});
            }
        }});
    }
    private void applyExposureMode(final String mode){
        work.post(new Runnable(){public void run(){
            try{
                if(exposure==null)throw new IllegalStateException("曝光控制不可用");
                exposure.setMode(mode);
                getPreferences(0).edit().putString("video_exposure_mode",mode).commit();
                publishExposure();
                ui.post(new Runnable(){public void run(){afterSetting(false);}});
            }catch(Throwable e){
                final String message=root(e).getMessage();
                final String fallback=root(e).getClass().getSimpleName();
                event("video_exposure_mode_error",root(e).toString());
                ui.post(new Runnable(){public void run(){
                    hud.setError("曝光模式设置失败："+(message==null?fallback:message));
                    afterSetting(true);
                }});
            }
        }});
    }
    private void applyEvIndex(final int index){
        work.post(new Runnable(){public void run(){
            try{
                if(exposure==null)throw new IllegalStateException("曝光控制不可用");
                exposure.setEvIndex(index);
                publishExposure();
                ui.post(new Runnable(){public void run(){afterSetting(false);}});
            }catch(Throwable e){
                final String message=root(e).getMessage();
                final String fallback=root(e).getClass().getSimpleName();
                event("video_ev_error",root(e).toString());
                ui.post(new Runnable(){public void run(){
                    hud.setError("曝光补偿设置失败："+(message==null?fallback:message));
                    afterSetting(true);
                }});
            }
        }});
    }
    private void applyVideoDial(final int front,final int rear,final int control){
        work.post(new Runnable(){public void run(){
            if(closed||exposure==null)return;
            try{
                if(exposure.dial(front,rear,control))publishExposure();
            }catch(Throwable e){
                final String message=root(e).getMessage();
                final String fallback=root(e).getClass().getSimpleName();
                event("video_dial_error",root(e).toString());
                ui.post(new Runnable(){public void run(){
                    hud.setError("曝光调整失败："+(message==null?fallback:message));
                }});
            }
        }});
    }
    private void afterSetting(boolean preserveError){
        if(!preserveError)hud.setError("");
        if(recording){
            hud.setState("录像中",profileLabel(),"快门 停止 · 方向键 切换焦点 · MENU 设置");
            return;
        }
        readyHud(!preserveError);
    }

    /** While a clip is recording, the arrow keys are the live AF point control.
     * Each step commits the Sony flexible-spot point so AF-C can re-focus at
     * once. The box stays visible between moves. */
    private void moveVideoFocusLive(final int dx,final int dy){
        work.post(new Runnable(){public void run(){
            if(closed||af==null)return;
            try{
                af.liveMove(dx,dy);
                final int x=af.x(),y=af.y();
                ui.post(new Runnable(){public void run(){
                    if(closed||destroyed)return;
                    hud.setError("");
                    hud.setAf(true,false,x,y);
                }});
            }catch(Throwable e){
                final String message=root(e).getMessage();
                event("video_af_live_error",root(e).toString());
                ui.post(new Runnable(){public void run(){
                    hud.setError("实时对焦点切换失败："+(message==null?"相机拒绝":message));
                }});
            }
        }});
    }
    private void startRecording(){
        if(closed||exiting||opening||saving||recording||starting)return;
        if(af!=null&&af.active())try{
            af.confirm();hud.setAf(true,false,af.x(),af.y());triggerVideoFocus("record_af_confirm");}
        catch(Throwable e){hud.setError("对焦点确认失败："+root(e).getMessage());return;}
        starting=true;streamCompleted=false;
        if(preparing||!prepared){starting=false;hud.setState("正在准备录像格式",profileLabel(),"请稍候");return;}
        work.post(new Runnable(){public void run(){startNow();}});
    }
    private void startNow(){
        try{
            event("start_begin","profile="+profileLabel());
            call(recorder,"start",new Class<?>[]{});
            ui.post(new Runnable(){public void run(){hud.setState("正在开始录像",profileLabel(),"");}});
        }catch(Throwable e){
            starting=false;
            if(startRetries<1&&!closed){
                startRetries++;prepared=false;preparing=true;
                event("start_retry",root(e).toString());
                ui.post(new Runnable(){public void run(){
                    hud.setError("准备尚未完成，正在重试，请稍后再按录像");
                    hud.setState("正在准备录像格式",profileLabel(),"请稍候");
                }});
                work.postDelayed(new Runnable(){public void run(){
                    try{createAndPrepareRecorder();}
                    catch(Throwable next){preparing=false;fail("start_retry_prepare",next);}
                }},900);
            }else fail("start",e);
        }
    }
    private void stopRecording(){
        if(!recording||saving)return;
        ui.post(new Runnable(){public void run(){
            saving=true;hud.setSaving(true);hud.setState("正在停止并保存",profileLabel(),"");
        }});
        work.post(new Runnable(){public void run(){
            try{
                event("stop_begin","");
                call(recorder,"stop",new Class<?>[]{});
                event("stop_return","");
            }catch(Throwable e){fail("stop",e);}
        }});
    }

    private void openRootMenu(){
        if(exiting)return;
        if(preparing||saving||starting){
            hud.setError("请等待当前操作完成");
            return;
        }
        hud.setError("");
        menuMode=MENU_ROOT;
        menuItems.clear();
        String wb=exposure==null?"--":WhiteBalanceNames.label(exposure.whiteBalance());
        if(wb.startsWith("WB "))wb=wb.substring(3);
        menuItems.add(Lang.t("录像格式")+"  "+profileLabel());
        menuItems.add(Lang.t("白平衡")+"  "+Lang.tr(wb));
        menuItems.add(Lang.t("对焦模式")+"  "+(afModes.isEmpty()?Lang.t("不支持"):Lang.tr(afModeLabel(afMode))));
        menuItems.add(Lang.t("曝光模式")+"  "+(exposure==null?"--":Lang.tr(VideoExposure.modeLabel(exposure.mode()))));
        menuItems.add(Lang.t("曝光补偿")+"  "+(exposure==null?"--":exposure.evLabel()));
        showMenu("录像设置",0,"↑↓ 选择 · ● 进入 · MENU/← 返回");
        event("video_menu_root","recording="+recording+" exposure="+(exposure==null?"n/a":exposure.mode()));
    }
    private void showMenu(String title,int selected,String footer){
        hud.setMenuTitle(title);
        hud.setMenu(true,menuItems.toArray(new String[menuItems.size()]),selected,footer);
    }
    private void openSubmenu(int kind){
        menuMode=kind;menuItems.clear();
        int selected=0;
        if(kind==MENU_FORMAT){
            if(recording||preparing){hud.setError("录像中或准备中不能切换格式");openRootMenu();return;}
            if(profiles.isEmpty()){hud.setError("相机没有报告可用的录像格式");openRootMenu();return;}
            for(Profile profile:profiles)menuItems.add(profile.label());
            selected=Math.max(0,Math.min(profiles.size()-1,profileIndex));
            showMenu("选择录像格式",selected,"↑↓ 选择 · ● 应用 · MENU/← 返回");
        }else if(kind==MENU_WB){
            if(exposure==null){hud.setError("曝光控制不可用");openRootMenu();return;}
            List<String> values=exposure.whiteBalanceValues();
            if(values.isEmpty()){hud.setError("相机没有报告白平衡预设");openRootMenu();return;}
            for(String value:values)menuItems.add(WhiteBalanceNames.label(value));
            selected=Math.max(0,values.indexOf(exposure.whiteBalance()));
            showMenu("白平衡",selected,"↑↓ 选择 · ● 应用 · MENU/← 返回");
        }else if(kind==MENU_AF){
            if(afModes.isEmpty()){hud.setError("相机没有报告对焦模式");openRootMenu();return;}
            for(String value:afModes)menuItems.add(afModeLabel(value));
            selected=Math.max(0,afModes.indexOf(afMode));
            showMenu("对焦模式",selected,"↑↓ 选择 · ● 应用 · MENU/← 返回");
        }else if(kind==MENU_EXPOSURE){
            if(exposure==null){hud.setError("曝光控制不可用");openRootMenu();return;}
            List<String> modes=exposure.availableModes();
            for(String value:modes)menuItems.add(VideoExposure.modeLabel(value));
            selected=Math.max(0,modes.indexOf(exposure.mode()));
            showMenu("曝光模式",selected,"↑↓ 选择 · ● 应用 · MENU/← 返回");
        }else if(kind==MENU_EV){
            if(exposure==null||!exposure.evAvailable()){hud.setError("相机没有报告曝光补偿");openRootMenu();return;}
            for(int i=exposure.evMin();i<=exposure.evMax();i++)
                menuItems.add(VideoExposure.evLabel(i,exposure.evStep()));
            selected=Math.max(0,exposure.evIndex()-exposure.evMin());
            showMenu("曝光补偿",selected,"↑↓ 选择 · ● 应用 · MENU/← 返回");
        }else if(kind==MENU_LOOK){
            if(lutEntry==null){hud.setError("当前没有可用胶卷");openRootMenu();return;}
            menuItems.add("近似胶卷（拟合矩阵，和照片胶卷不完全相同）");
            menuItems.add("原色（相机原始色彩，和照片取景一致）");
            selected=lookApplied?0:1;
            showMenu("取景胶卷",selected,"↑↓ 选择 · ● 应用 · MENU/← 返回");
        }else{closeMenu();readyHud();return;}
        event("video_menu_open","kind="+kind+" count="+menuItems.size());
    }
    private void closeMenu(){
        menuMode=MENU_NONE;menuItems.clear();
        hud.setMenu(false,null,0,null);
    }
    private void menuBack(){
        if(menuMode==MENU_ROOT||menuMode==MENU_NONE){closeMenu();readyHud();}
        else openRootMenu();
    }
    private void applyMenuSelection(){
        int kind=menuMode,selected=hud.selected;
        if(kind==MENU_ROOT){
            if(selected>=0&&selected<ROOT_MODES.length)openSubmenu(ROOT_MODES[selected]);
            else closeMenu();
            return;
        }
        if(selected<0||selected>=menuItems.size()){closeMenu();readyHud();return;}
        if(kind==MENU_FORMAT){
            closeMenu();applyFormat(selected);return;
        }
        if(kind==MENU_WB){
            List<String> values=exposure==null?new ArrayList<String>():exposure.whiteBalanceValues();
            closeMenu();
            if(selected>=values.size()){readyHud();return;}
            applyWhiteBalance(values.get(selected));return;
        }
        if(kind==MENU_AF){
            List<String> values=afModes;
            closeMenu();
            if(selected>=values.size()){readyHud();return;}
            applyAfMode(values.get(selected));return;
        }
        if(kind==MENU_EXPOSURE){
            List<String> values=exposure==null?new ArrayList<String>():exposure.availableModes();
            closeMenu();
            if(selected>=values.size()){readyHud();return;}
            applyExposureMode(values.get(selected));return;
        }
        if(kind==MENU_EV){
            VideoExposure session=exposure;
            closeMenu();
            if(session==null){readyHud();return;}
            applyEvIndex(session.evMin()+selected);return;
        }
        if(kind==MENU_LOOK){
            closeMenu();
            setVideoLook(selected==0);return;
        }
        closeMenu();readyHud();
    }
    private void applyFormat(int selected){
        if(recording||preparing||saving||selected<0||selected>=profiles.size()||selected==profileIndex){
            readyHud();return;
        }
        profileIndex=selected;prepared=false;recording=false;saving=false;preparing=true;startRetries=0;
        getPreferences(0).edit().putString("video_profile",profiles.get(selected).label()).commit();
        hud.setState("正在切换格式",profileLabel(),"");
        event("format_select","index="+selected+" label="+profileLabel());
        work.post(new Runnable(){public void run(){
            try{createAndPrepareRecorder();}
            catch(Throwable e){preparing=false;fail("format_prepare",e);}
        }});
    }

    private void requestExit(){
        if(exiting)return;
        // a7r2.52: stop asking the movie engine for focus the moment the user
        // leaves. A standby request fired 271 ms before release in R00685E2
        // and was still queued when the still process opened the camera.
        exitRequested=true;
        ui.removeCallbacks(standbyAfc);
        if(recording){
            exitAfterSave=true;hud.setError("");
            hud.setState("正在停止并返回","","请稍候，视频保存完成后返回拍照");
            stopRecording();return;
        }
        if(saving){
            exitAfterSave=true;hud.setError("");
            hud.setState("正在保存并返回","","请稍候");return;
        }
        // The A7R II allocates the 4K movie pipeline asynchronously after
        // prepare() returns. Releasing it while that pipeline is still being
        // built left the movie engine wedged: the next still-camera process
        // reset the camera (empty PROBE.JSN, boot uptime 104 s). Always let
        // the recorder settle, then release.
        exitAfterPrepare=true;exitWaitTicks=0;
        hud.setError("");hud.setState("正在关闭录像接口","","请稍候，随后返回拍照");
        event("video_exit_wait","preparing="+preparing+" prepared="+prepared
                +" settle_ms="+(preparedAt>0?Math.max(0,preparedAt+1800-SystemClock.elapsedRealtime()):0));
        continueExit();
    }
    /** Keep polling until prepare finished and the movie engine has had time
     * to settle; bounded so a failed prepare can never trap the user. */
    private void continueExit(){
        if(exiting||destroyed)return;
        if(recording||saving){exitAfterPrepare=false;requestExit();return;}
        long settle=preparedAt>0?preparedAt+1800L-SystemClock.elapsedRealtime():0;
        if((preparing||settle>0)&&exitWaitTicks++<25){
            ui.postDelayed(new Runnable(){public void run(){continueExit();}},200);
            return;
        }
        exitAfterPrepare=false;
        exitToPhoto();
    }
    private void exitToPhoto(){
        if(recording||saving){hud.setError("请先停止录像并等待保存完成");return;}
        if(exiting)return;
        exiting=true;hud.setState("正在关闭录像接口","","");
        event("video_exit_release","prepared="+prepared+" settle_done=true");
        ui.removeCallbacks(tick);ui.removeCallbacks(standbyAfc);
        work.post(new Runnable(){public void run(){
            releaseEverything();
            // Give the camera service time to tear down the movie engine before
            // the still-capture process opens CameraEx again.
            ui.postDelayed(new Runnable(){public void run(){
                Intent result=new Intent().putExtra("open_photo",true).putExtra("lut_id",lutId);
                if(recipeMode&&recipe!=null)result.putExtra("recipe_mode",true)
                        .putExtra("recipe_id",indexOfRecipe(recipe)).putExtra("recipe_name",Lang.recipeName(recipe))
                        .putExtra("camera_name",recipeCameraName);
                setResult(RESULT_OK,result);
                finish();
                // The movie process must be gone before the capture process
                // opens the camera; a lingering owner is what made the second
                // open unsafe. The library also waits for this process.
                ui.postDelayed(new Runnable(){public void run(){
                    android.os.Process.killProcess(android.os.Process.myPid());
                }},1000);
            }},2500);
        }});
    }

    private void releaseRecorder(){
        Object value=recorder;recorder=null;prepared=false;
        if(value==null)return;
        try{call(value,"reset",new Class<?>[]{});}catch(Throwable ignored){}
        try{call(value,"release",new Class<?>[]{});}catch(Throwable ignored){}
        recorderProxies.clear();
    }
    private void releaseEverything(){
        if(closed)return;closed=true;
        try{releaseRecorder();}catch(Throwable ignored){}
        if(telemetry!=null){try{telemetry.close();}catch(Throwable e){event("video_telemetry_close_error",root(e).toString());}telemetry=null;}
        if(af!=null){try{af.close();}catch(Throwable e){event("video_af_restore_error",root(e).toString());}af=null;}
        cancelVideoFocus("release");
        if(recipeLook!=null){
            try{recipeLook.restore();event("recipe_restored","");}
            catch(Throwable e){event("recipe_restore_error",root(e).toString());}
            recipeLook=null;
        }
        exposure=null;exposureDriver=null;lookDriver=null;
        if(look!=null){
            try{look.close();}catch(Throwable e){event("look_restore_error",root(e).toString());}
            look=null;lookApplied=false;lookProfile=null;
        }
        try{
            if(camera!=null){
                Camera.Parameters p=camera.getParameters();
                for(Map.Entry<String,String> e:originalSettings.entrySet()){
                    if(e.getValue()==null)p.remove(e.getKey());else p.set(e.getKey(),e.getValue());
                }
                camera.setParameters(p);
                camera.stopPreview();
            }
        }catch(Throwable e){event("camera_restore_error",root(e).toString());}
        try{if(cameraEx!=null)cameraType.getMethod("release").invoke(cameraEx);}
        catch(Throwable e){event("camera_release_error",root(e).toString());}
        cameraEx=null;camera=null;opened=false;
        event("video_closed","");
        closeJournal();
    }

    private void fail(final String where,Throwable error){
        final Throwable cause=root(error);
        event("video_failure",where+" "+cause);
        ui.post(new Runnable(){public void run(){
            recording=false;saving=false;preparing=false;starting=false;ui.removeCallbacks(tick);hud.setRecording(false);hud.setSaving(false);
            hud.setError(where+"："+(cause.getMessage()==null?cause.getClass().getSimpleName():cause.getMessage()));
            hud.setState("录像不可用",profileLabel(),"按 ← 返回拍照");
        }});
    }
    private static Throwable root(Throwable error){
        Throwable cause=error;
        while(cause.getCause()!=null&&cause.getCause()!=cause)cause=cause.getCause();
        return cause;
    }
    private static Object call(Object target,String name,Class<?>[] types,Object... args)throws Exception{
        if(target==null)throw new IllegalStateException("missing target for "+name);
        Method method=target.getClass().getMethod(name,types);
        try{return method.invoke(target,args);}
        catch(InvocationTargetException e){Throwable cause=e.getCause();if(cause instanceof Exception)throw (Exception)cause;throw new RuntimeException(cause);}
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e){
        // The film strip is modal and must stay above the pose overlay.
        if(recipeMode&&filmSelecting){handleFilmKey(e);return true;}
        if(PoseDirectorOverlay.handleKey(this,e))return true;
        int key=e.getKeyCode(),scan=e.getScanCode();
        int action=UiKeys.key(e);
        int dial=UiKeys.dial(e),control=UiKeys.controlDial(e);
        int front=UiKeys.frontDial(e),rear=UiKeys.rearDial(e);
        boolean fullShutter=scan==518||key==518||key==KeyEvent.KEYCODE_CAMERA;
        boolean halfShutter=scan==516||key==516||scan==517||key==517||key==KeyEvent.KEYCODE_FOCUS;
        // A7R II's dedicated MOVIE button reports scan 515 with key code 0.
        // It was previously logged as video_key_unhandled; treat it exactly
        // like the shutter's full press so it starts and stops recording.
        boolean recordButton=scan==515||key==515;
        // a7r2.56: do not cancel the focus drive when S1 is released. The
        // request needs time to complete; the next half press replaces it.
        // (a7r2.53 cancelled here, which left short taps with no focus at
        // all; a7r2.52's cancel was below the generic UP return and never ran.)
        if(halfShutter&&e.getAction()==KeyEvent.ACTION_UP&&!filmSelecting)
            event("video_focus_release","half_up");
        if(af!=null&&af.active()){
            if(e.getAction()!=KeyEvent.ACTION_DOWN)return true;
            if(action==UiKeys.UP){af.move(0,-1);hud.setAf(true,true,af.x(),af.y());return true;}
            if(action==UiKeys.DOWN){af.move(0,1);hud.setAf(true,true,af.x(),af.y());return true;}
            if(action==UiKeys.LEFT){af.move(-1,0);hud.setAf(true,true,af.x(),af.y());return true;}
            if(action==UiKeys.RIGHT){af.move(1,0);hud.setAf(true,true,af.x(),af.y());return true;}
            if(action==UiKeys.OK){
                try{af.confirm();hud.setAf(true,false,af.x(),af.y());
                    event("video_af_confirm","x="+af.x()+" y="+af.y());
                    triggerVideoFocus("af_confirm");}
                catch(Throwable x){hud.setError("对焦点确认失败："+root(x).getMessage());}
                return true;
            }
            if(action==UiKeys.BACK||key==KeyEvent.KEYCODE_BACK||key==KeyEvent.KEYCODE_MENU||scan==514){
                try{af.cancel();hud.setAf(true,false,af.x(),af.y());
                    event("video_af_cancel","");}
                catch(Throwable x){hud.setError("对焦恢复失败："+root(x).getMessage());}
                return true;
            }
            if(fullShutter||recordButton){
                try{af.confirm();hud.setAf(true,false,af.x(),af.y());
                    triggerVideoFocus("af_shutter_confirm");toggleRecording();}
                catch(Throwable x){hud.setError("对焦点确认失败："+root(x).getMessage());}
                return true;
            }
            if(halfShutter){
                if(e.getAction()==KeyEvent.ACTION_DOWN)triggerVideoFocus("af_half_press");
                return true;
            }
            return true;
        }
        if(hud.menuOpen){
            if(e.getAction()!=KeyEvent.ACTION_DOWN)return (fullShutter||halfShutter||action==UiKeys.OK)?true:super.dispatchKeyEvent(e);
            // (film strip never opens over a menu)
            if(action==UiKeys.UP||dial==-1||control==-1||front==-1||rear==-1){moveMenu(-1);return true;}
            if(action==UiKeys.DOWN||dial==1||control==1||front==1||rear==1){moveMenu(1);return true;}
            if(action==UiKeys.RIGHT){
                if(menuMode==MENU_ROOT&&hud.selected>=0&&hud.selected<ROOT_MODES.length)
                    openSubmenu(ROOT_MODES[hud.selected]);
                else applyMenuSelection();
                return true;
            }
            if(action==UiKeys.OK||fullShutter){applyMenuSelection();return true;}
            if(action==UiKeys.BACK||action==UiKeys.LEFT||key==KeyEvent.KEYCODE_BACK
                    ||key==KeyEvent.KEYCODE_MENU||scan==514){
                menuBack();return true;
            }
            return true;
        }
        // C3 opens the live film strip. While it is open it owns every key,
        // including the shutter, so a clip cannot start mid-switch.
        if(recipeMode&&A7R2Keys.filmC3(scan,key)){
            if(e.getAction()==KeyEvent.ACTION_DOWN&&e.getRepeatCount()==0){
                if(recording||saving||preparing||starting)hud.setError("请等待当前录像操作完成后再换胶片");
                else openFilmSelection();
            }
            return true;
        }
        if(e.getAction()!=KeyEvent.ACTION_DOWN)return true;
        if(scan==622||key==622){
            if(e.getRepeatCount()==0&&!preparing&&!saving&&!starting&&!exiting)cycleVideoExposureMode();
            return true;
        }
        if(recording){
            int dx=0,dy=0;
            if(action==UiKeys.UP)dy=-1;
            else if(action==UiKeys.DOWN)dy=1;
            else if(action==UiKeys.LEFT)dx=-1;
            else if(action==UiKeys.RIGHT)dx=1;
            if(dx!=0||dy!=0){moveVideoFocusLive(dx,dy);return true;}
        }
        if(action==UiKeys.RIGHT){
            if(!preparing&&!saving&&!starting&&!exiting&&exposure!=null)openSubmenu(MENU_WB);
            return true;
        }
        if(key==KeyEvent.KEYCODE_MENU||scan==514){
            openRootMenu();
            return true;
        }
        if(front!=0||rear!=0||control!=0){
            if(!saving&&!starting&&!exiting&&exposure!=null)applyVideoDial(front,rear,control);
            return true;
        }
        if(action==UiKeys.LEFT||action==UiKeys.BACK||key==KeyEvent.KEYCODE_BACK){requestExit();return true;}
        if(action==UiKeys.DOWN&&!recording&&!saving&&!preparing&&af!=null){
            try{af.enter();hud.setError("");hud.setAf(true,true,af.x(),af.y());
                event("video_af_enter","x="+af.x()+" y="+af.y());}
            catch(Throwable x){hud.setError("对焦点选择失败："+root(x).getMessage());}
            return true;
        }
        if(recordButton){event("video_record_key","scan="+scan);toggleRecording();return true;}
        if(fullShutter){toggleRecording();return true;}
        if(action==UiKeys.OK)return true; // centre button never records; shutter only
        if(halfShutter){ // focused by a one-shot camera request, never a still shutter
            if(e.getAction()==KeyEvent.ACTION_DOWN)triggerVideoFocus("half_press");
            return true;
        }
        if(action==UiKeys.UP||action==UiKeys.DOWN||action==UiKeys.RIGHT||dial!=0||control!=0)return true;
        event("video_key_unhandled","key="+key+" scan="+scan);
        return true;
    }
    private void moveMenu(int direction){
        int size=menuItems.size();
        if(size==0)return;
        int index=(hud.selected+direction+size)%size;
        hud.setSelection(index);
    }
    private void toggleRecording(){if(recording)stopRecording();else startRecording();}

    @Override protected void onResume(){super.onResume();active=true;if(holder!=null&&!opened&&!opening)startOpen();
        if(opened){ui.removeCallbacks(standbyAfc);ui.postDelayed(standbyAfc,900);}}
    @Override protected void onPause(){active=false;ui.removeCallbacks(tick);ui.removeCallbacks(standbyAfc);super.onPause();}
    @Override protected void onDestroy(){
        destroyed=true;ui.removeCallbacks(tick);ui.removeCallbacks(standbyAfc);PoseDirectorOverlay.closeIfVisible();
        if(!closed){work.post(new Runnable(){public void run(){releaseEverything();thread.quit();}});}
        else if(thread!=null)thread.quit();
        super.onDestroy();
    }

    private synchronized void event(String name,String detail){
        String line=SystemClock.elapsedRealtime()+" "+name+" "+detail;
        Log.i(TAG,line);
        if(run==null||journalClosed)return;
        try{
            if(journal==null)journal=new FileOutputStream(RunFiles.file(run,"VIDEO.JSN"),true);
            journal.write((line+"\n").getBytes("UTF-8"));
            // No getFD().sync() here: this runs on the UI thread and Sony's
            // FUSE mount can stall a sync while the movie engine is running.
            // The frame log is diagnostic only; a hard reset may lose its tail.
            journal.flush();
        }catch(Throwable e){Log.w(TAG,"video_journal_failed",e);}
    }
    private synchronized void closeJournal(){
        journalClosed=true;
        if(journal!=null)try{journal.close();}catch(Throwable ignored){}finally{journal=null;}
    }

    static final class Profile{
        final String format,aspect,bitrate,frame,size;
        Profile(String format,String aspect,String bitrate,String frame,String size){
            this.format=format;this.aspect=aspect;this.bitrate=bitrate;this.frame=frame;this.size=size;
        }
        int rank(){
            String f=(format==null?"":format).toUpperCase(java.util.Locale.US);
            if(f.contains("4K")||size.startsWith("3840")||size.startsWith("4096"))return 0;
            if(f.contains("XAVC"))return 1;
            if(f.contains("AVCHD"))return 2;
            if(f.contains("MP4")||f.contains("MPEG"))return 3;
            return 4;
        }
        String output(){
            String f=(format==null?"":format).toUpperCase(java.util.Locale.US);
            if((f.length()==0)&&(size.startsWith("3840")||size.startsWith("4096")))return "XAVCS_4K";
            if(f.contains("XAVCS_4K")||f.contains("XAVC_S_4K")||f.contains("4K"))return "XAVCS_4K";
            if(f.contains("XAVCS")||f.contains("XAVC_S"))return "XAVCS";
            if(f.contains("AVCHD"))return "AVCHD";
            if(f.contains("MP4")||f.contains("MPEG"))return "MPEG4";
            return format==null?"XAVCS":format;
        }
        long bits(){
            if(bitrate==null)return 0;
            try{
                long value=Long.parseLong(bitrate.replaceAll("[^0-9]",""));
                return value<1000?value*1000000:value;
            }catch(RuntimeException e){return 0;}
        }
        String bitrateLabel(){
            long b=bits();
            if(b>=1000000)return (b/1000000)+"M";
            if(b>0)return b+"bps";
            return "";
        }
        String sizeLabel(){
            String s=size.toUpperCase(java.util.Locale.US);
            if(s.contains("4K")||s.startsWith("3840")||s.startsWith("4096"))return "3840×2160";
            if(s.contains("FHD")||s.startsWith("1920"))return "1920×1080";
            if(s.contains("720")||s.startsWith("1280"))return "1280×720";
            if(s.startsWith("VGA")||s.startsWith("640"))return "640×480";
            return size;
        }
        String frameLabel(){
            if(frame==null)return "";
            String f=frame.toUpperCase(java.util.Locale.US);
            if(f.endsWith("P")||f.endsWith("I"))return frame;
            return frame+"p";
        }
        String formatLabel(){
            switch(rank()){
                case 0:return "XAVC S 4K";
                case 1:return "XAVC S";
                case 2:return "AVCHD";
                case 3:return "MP4";
                default:return format==null?"视频":format;
            }
        }
        String label(){
            StringBuilder b=new StringBuilder(formatLabel());
            if(size!=null&&size.length()>0)b.append(" · ").append(sizeLabel());
            if(frameLabel().length()>0)b.append(" · ").append(frameLabel());
            if(bitrateLabel().length()>0)b.append(" · ").append(bitrateLabel());
            return b.toString();
        }
    }
}
