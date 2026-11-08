package org.hongen.sonylutprobe;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.hardware.Camera;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.LinkedHashMap;
import java.util.Map;

/** Staged CameraSequence capture. Independent of the legacy JPEG photo path.
 * preview: camera-only control; handoff: return RAW to Sony;
 * develop: Sony RAW->YC then Sony storeImage; develop-xfine changes only
 * JPEG quality to 95 for a single, transferred-image store. lut: preserve original, grade,
 * then store a second JPEG from the retained YC. init_only defaults to true;
 * init_stage defaults to defaults (no extra map). Configured is explicit.
 * continuous=true rearms the paired LUT session; verify_payloads=true enables
 * the full diagnostic scan. Neither changes pixels, JPEG quality or retention.
 * No JpegExporter, custom memory map, software codec, or auto shutter.
 */
public class PreJpegProbeActivity extends Activity implements SurfaceHolder.Callback {
    protected boolean productUi(){return false;}
    protected boolean previewIsolationProbe(){return false;}
    protected boolean automaticNativePreview(){return false;}
    private NativePreviewSession isolationPreview;
    private PreviewCaptureBarrier isolationBarrier;
    private AutoPreviewGate autoPreview;
    private CameraNativePreviewDriver previewDriver;
    private NativePreviewProfile automaticProfile;
    private LutLibrary.Entry selectedLut;
    private PhotoHud productHud;
    private volatile boolean developEnabledThisShot;
    private SimpleExposure exposure;
    // The recipe finder opens its exposure session on the control thread after
    // the live preview is running; the UI reads this from key handlers.
    private volatile ManualExposure manual;
    private AfPointController af;
    private final ManualExposure.Log manualLog=new ManualExposure.Log(){public void log(String name,String detail){
        // High-frequency exposure steps must not block the UI on SD writes.
        if(name.startsWith("manual_aperture")||name.startsWith("manual_shutter")||name.startsWith("manual_iso")
                ||name.startsWith("manual_ev")||name.startsWith("manual_dial")){
            android.util.Log.i("SonyManual",name+" "+detail);return;
        }
        event(name,detail);
    }};
    private final AfPointController.Log afLog=new AfPointController.Log(){public void log(String name,String detail){event(name,detail);}};
    private final java.util.Set<Integer> loggedDialScans=new java.util.HashSet<Integer>();
    private java.util.List<String> wbMenuValues=new java.util.ArrayList<String>();
    // Exposure keys only enqueue an intent. Camera parameter reads/writes run
    // on a dedicated looper so a slow Sony getParameters() can never delay the
    // key event or the next dial notch.
    private HandlerThread controlThread;private Handler controlHandler;
    private final Object controlLock=new Object();
    private int pendingAperture,pendingShutter,pendingIso,pendingWhiteBalance;
    private boolean controlClosed;
    private final Runnable applyControls=new Runnable(){public void run(){
        int front,rear,iso,wb;
        synchronized(controlLock){
            if(controlClosed)return;
            front=pendingAperture;rear=pendingShutter;iso=pendingIso;wb=pendingWhiteBalance;
            pendingAperture=pendingShutter=pendingIso=pendingWhiteBalance=0;
        }
        if(front==0&&rear==0&&iso==0&&wb==0)return;
        Throwable failure=null;
        synchronized(controlLock){
            if(controlClosed)return;
            ManualExposure session=manual;
            if(session==null)return;
            try{
                if(front!=0||rear!=0||iso!=0)session.dial(front,rear,iso);
                // The preview display mode survives ordinary exposure
                // commands; reasserting it after every dial costs another
                // camera round-trip and made the dial feel delayed.
                if(wb!=0){
                    while(wb!=0){session.whiteBalance(wb>0?1:-1);wb+=wb>0?-1:1;}
                    if(previewDriver!=null)previewDriver.rebaseWhiteBalance();
                }
            }catch(Throwable e){failure=e;}
        }
        final Throwable error=failure;
        ui.post(new Runnable(){public void run(){
            if(destroyed||closed)return;
            if(error!=null){manualError("dial",error);return;}
            if(productHud!=null&&manual!=null){manual.update(productHud.readout);productLive();}
        }});
        synchronized(controlLock){
            if(controlClosed)return;
            if(pendingAperture!=0||pendingShutter!=0||pendingIso!=0||pendingWhiteBalance!=0)controlHandler.post(this);
        }
    }};
    private CaptureTelemetry telemetry;
    private int pendingReceipts;
    private long recordedPairShot=-1;
    private boolean showingOriginal;
    private boolean playbackRequested;
    private int playbackOffset;
    private static boolean hasEnteredProbe;
    private final Handler ui=new Handler();
    private final PreJpegGate gate=new PreJpegGate();
    private HandlerThread thread;private Handler work;
    private SurfaceHolder holder;private TextView status;
    private Object cameraEx,sequence,filter;private Camera camera;
    /** Physical S1 state across a capture. The auto-preview gate clears its
     * own `held` flag when a shot starts; this lets the app re-arm while the
     * user keeps the half press down for the next shot. */
    private boolean s1Held;
    private Class<?> cameraType,sequenceType,rawType,imageType;
    private volatile boolean active,prepared,workerActive,destroyed;
    private boolean opening,closed,initOnly;private boolean surfaceAvailable;private String phase,mapPath,initStage,mapRevision,originalPictureSize;private CaptureProfile profile;private boolean uvSwap;private File run,sd;
    private boolean normalizeZoom,continuous,verifyPayloads,overlapOriginal,sourceReferenceEnabled,alternateOverlap,overlapThisShot;
    // Store notifications queue on the same worker as grading. The original
    // remains the sole pending store until confirmed, even with overlap enabled.
    private boolean lutApplied,previewColorAudited;private Bitmap sourceReference;
    private PhotoFiles photoFiles;
    private Object retainedImage;private long lutCache;private Bitmap lutReview;private int beautySkin,beautyDenoise;
    private File originalFile,gradedFile;private String originalQuality;
    // Recipe mode: one camera-native JPEG per shutter, no RAW sequence, no LUT.
    private RecipeData.Recipe recipe;private volatile RecipeCameraLook recipeLook;private File recipeFile;
    // Recipe startup writes its colour patch before startPreview, the same
    // window Recipe Lab uses from onResume. No ManualExposure session runs in
    // recipe mode: M-scene/ISO/shooting-preview writes followed by the
    // Creative Style/WB patch reset the A7R II at launch. Live C3 switches use
    // the same in-place commit; the preview is never stopped around it.
    private volatile boolean recipeApplyBusy;
    private String recipeCameraName="";private int recipeId=-1;private long recordedRecipeShot=-1;
    // C3 in the recipe finder: browse the camera group's films and apply each
    // one live. Only the camera's own controls are written; no LUT is run.
    private boolean filmSelecting;private int filmSelectIndex,filmSelectOriginal;
    private int filmApplyGeneration;private String filmApplyError="";
    // Guarded by controlLock. A C3 write and a capture arm must be mutually
    // exclusive: the old check ran before gate.arm() without a lock, so a film
    // patch could start after the gate was ARMED and write parameters while
    // Sony was exposing the frame. Repeated shots then wedged the body.
    private boolean filmApplyInProgress;
    // Latest film the HUD shows but the camera may not have applied yet. A
    // switch skipped during a capture is retried when the finder is READY.
    private volatile int filmApplyPendingId=-1,appliedRecipeId=-1;
    private ReviewView review;private boolean awaitFirstFrame;
    private long displayedAt,nextReadyAt,rearmAt;private boolean finishingRearm,reviewing;
    private FaceDetectionController faceDetection;
    private boolean reviewClearCheck;
    private Thread.UncaughtExceptionHandler previousExceptionHandler,exceptionHandler;
    private final Map<String,String> originalZoom=new LinkedHashMap<String,String>();
    private static final String[] ZOOM_KEYS={"smart-zoom","super-resolution-zoom","precision-zoom"};
    private long pressAt,rawAt,storeAt;private String timingOrigin="unset";
    // Wall-clock press time, used only to bound the fallback DCIM scan when a
    // Camera.takePicture capture produces no StoreImageComplete receipt.
    private volatile long recipePressWall;
    private FileOutputStream journal;private boolean journalClosed;private long journalMs,journalAtPress;
    // Recipe diagnostics never do card I/O on the UI or Sony callback threads.
    // getFD().sync() on this body's FUSE mount can stall while the capture
    // engine is running; a stalled UI thread there looks exactly like a body
    // hang. Every line is queued to its own thread and closed per write.
    private volatile HandlerThread journalThread;private volatile Handler journalHandler;
    // Receipt cataloguing (42 MP thumbnail decode + catalog JSON) must not
    // share the capture worker with takePicture()/store scanning.
    private volatile HandlerThread receiptThread;private volatile Handler receiptHandler;
    private volatile String lastStage="";private volatile long uiBeat;private volatile boolean heartbeatStop;
    // a7r2.45: R002BDF0 stopped the UI heartbeat 0.5 s after startRecipeShutter
    // was posted, while the preview callback thread kept running. The last
    // main-looper dispatch and a main-thread stack dump name the blocking
    // message if it happens again. The recipe finder now also runs without any
    // Sony callback listener (see registerCameraListeners), which is the only
    // configuration Recipe Lab itself has proven on this body.
    private volatile String uiMessage="";private volatile long uiMessageAt,uiMessageDoneAt;
    private volatile String previewCallbackThread="";private boolean looperTrace;
    private static final long RECIPE_SCAN_FIRST_MS=1200L;
    private static final long RECIPE_SCAN_RETRY_MS=800L;
    // 42 MP JPEGs can take longer to appear on a busy card. The scan now waits
    // up to 15 s (still inside the 20 s recovery window); the live write chip
    // makes this wait visible without the old darkroom page.
    private static final long RECIPE_SCAN_TOTAL_MS=15000L;
    private static final long RECIPE_SCAN_STABLE_MS=400L;
    // DCIM JPEGs present before the shot. Without a store callback the scan
    // must distinguish our new file by name, not only by the card's mtime
    // (Sony's FUSE can expose a coarse or shifted time). Worker-thread only.
    private volatile java.util.Set<String> recipeStoreBaseline;
    // Recipe S1/S2 calls run off the UI thread. A vendor AF transaction that
    // blocks must not stop the heartbeat/dispatch loop (R0034E27 wedged the
    // UI thread for 30+ s before the native key handler was removed).
    private volatile HandlerThread focusThread;private volatile Handler focusHandler;
    private boolean processingVisible,awaitProcessingFrame;private String processingText;
    private final CaptureProgress processingProgress=new CaptureProgress();
    private final Runnable processingTick=new Runnable(){public void run(){
        if(!active||destroyed||!processingVisible)return;
        long tenths=(SystemClock.elapsedRealtime()-pressAt)/100;
        if(productHud!=null){productHud.setVisibility(View.VISIBLE);productHud.mode="processing";productHud.detail=processingText;productHud.processingStage=processingProgress.stage();productHud.progress=(tenths/10)+"."+(tenths%10)+" 秒";
            productHud.footer=recipeMode()?"相机正在写入配方 JPEG，请保持开机":"正在保存原片与成片，请保持相机开机";productHud.update();status.setVisibility(View.GONE);}
        else status.setText(processingText+"\n"+(tenths/10)+"."+(tenths%10)+" 秒");
        // Updating the hidden legacy TextView would request a new layout and
        // defeat the product HUD's partial redraw while the negative is still.
        ui.postDelayed(this,250);
    }};
    private final Runnable restoreGraphics=new Runnable(){public void run(){
        if(!active||closed||destroyed||!processingVisible)return;
        try{
            review.showGraphicsOutput();awaitProcessingFrame=true;status.invalidate();
            event("graphics_restored","from_press_ms="+(SystemClock.elapsedRealtime()-pressAt));
            // The shutter curtain is only visible once Sony hands the graphics
            // plane back. Starting it at the shutter press meant the camera's
            // own blackout hid everything but the first frame (a7r2.27).
            if(productHud!=null)productHud.openCurtainDelayed(120);
        }catch(Exception e){
            // A display failure must not invalidate an in-flight photo/save.
            android.util.Log.e("SonyPreJpeg","graphics_restore_failed",e);
        }
    }};
    private boolean paired(){return "lut".equals(phase);}
    private boolean recipeMode(){return "recipe".equals(phase);}
    private boolean develops(){return "develop".equals(phase)||"develop-xfine".equals(phase)||paired();}
    private boolean extraFine(){return "develop-xfine".equals(phase)||paired();}
    private boolean referenceRevision(){return "auto".equals(mapRevision)||"reference01".equals(mapRevision)||"reference42".equals(mapRevision);}
    private int transitionTimeout(){return profile==null?5000:profile.transitionTimeoutMs;}
    private CaptureProfile platformProfile()throws Exception{
        Class<?> props=Class.forName("com.sony.scalar.sysutil.ScalarProperties");Method get=props.getMethod("getString",String.class);
        return CaptureProfile.of((String)get.invoke(null,"device.memory"),(String)get.invoke(null,"mem.rawimage.size.in.mega.pixel"));
    }
    private void readUvOrder(){
        uvSwap=profile.uvSwap;String source="profile";
        File file=new File(new File(sd,"SONYLUT"),"UVORDER.TXT");
        if(file.isFile())try{
            if(file.length()>32)throw new IOException("UVORDER.TXT too large");
            byte[] bytes=new byte[(int)file.length()];FileInputStream in=new FileInputStream(file);
            try{int total=0;while(total<bytes.length){int n=in.read(bytes,total,bytes.length-total);if(n<0)break;total+=n;}
                if(total!=bytes.length)throw new IOException("short UVORDER.TXT");}
            finally{in.close();}
            String value=new String(bytes,"US-ASCII").trim().toUpperCase(java.util.Locale.US);
            if("UYVY".equals(value)){uvSwap=false;source="file";}
            else if("VYUY".equals(value)){uvSwap=true;source="file";}
            else throw new IOException("UVORDER.TXT must contain UYVY or VYUY");
        }catch(Throwable e){event("uv_order_file_error",cause(e).toString());}
        event("uv_order","order="+(uvSwap?"VYUY":"UYVY")+" source="+source);
    }
    private final Runnable hideReviewStatus=new Runnable(){public void run(){
        if(active&&review!=null&&review.getVisibility()==View.VISIBLE)status.setVisibility(View.INVISIBLE);
    }};
    private final class StatusView extends TextView {
        StatusView(){super(PreJpegProbeActivity.this);}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);
            if(awaitProcessingFrame){awaitProcessingFrame=false;
                android.util.Log.i("SonyPreJpeg",SystemClock.elapsedRealtime()+" processing_frame from_press_ms="+(SystemClock.elapsedRealtime()-pressAt));
            }
        }
    }
    private final class ReviewView extends SonyReviewView {
        ReviewView(){super(PreJpegProbeActivity.this);}
        @Override protected void pixelAspectChanged(float value){if(productHud!=null){productHud.pixelAspect=value;productHud.update();}}
        @Override protected void displayFailed(Exception e){failure("display_geometry",e);}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            if(reviewClearCheck&&getWidth()>0&&getHeight()>0){
                reviewClearCheck=false;post(new Runnable(){public void run(){checkReviewClear();}});
            }
            if(awaitFirstFrame&&getDrawable()!=null){
                awaitFirstFrame=false;final long shownAt=SystemClock.elapsedRealtime();
                final long shot=gate.shot();displayedAt=shownAt;
                savedStatus("成片 "+seconds(shownAt-pressAt)+" 秒"+(continuous?" · 正在准备下一张":" · 已保存原片 + 冲洗成片"));
                work.post(new Runnable(){public void run(){
                    if(closed||shot!=gate.shot())return;
                    event("first_frame","shutter_to_displayed_ms="+(shownAt-pressAt)+" journal_write_ms="+journalWriteMs()+" journal_shot_ms="+(journalWriteMs()-journalAtPress)+" original="+originalFile+" graded="+gradedFile);
                    if(verifyPayloads)verifyPairPayloads();
                    else event("payload_verification_skipped","mode=normal saved_notification_and_header_checked=true");
                    if(continuous&&gate.state()==PreJpegGate.State.SAVED)ui.post(new Runnable(){public void run(){rearm(shot);}});
                }});
            }
        }
    }

    private synchronized void event(String name,String detail){
        String line=SystemClock.elapsedRealtime()+" "+name+" "+detail+" shot="+gate.shot();
        android.util.Log.i("SonyPreJpeg",line);
        if(run==null||journalClosed)return;
        if(recipeMode()){
            // Never touch the card from the UI thread or a Sony callback:
            // getFD().sync() on this body's FUSE mount can stall while the
            // capture engine is running, which freezes the whole transaction.
            // Queue the line instead; each queued write opens and closes the
            // file, the pattern that has survived every reset on this body.
            final File runDir=run;final Handler writer=journalHandler;
            final byte[] bytes=(line+"\n").getBytes(java.nio.charset.Charset.forName("UTF-8"));
            Runnable write=new Runnable(){public void run(){
                FileOutputStream out=null;
                try{out=new FileOutputStream(RunFiles.file(runDir,"PROBE.JSN"),true);out.write(bytes);}
                catch(Throwable ignored){}finally{if(out!=null)try{out.close();}catch(Throwable ignored){}}
            }};
            if(writer!=null)writer.post(write);else write.run();
            return;
        }
        long start=SystemClock.elapsedRealtime();
        try{
            // Keep one descriptor for the run. Each unbuffered write still
            // reaches the filesystem; avoid repeating SD path lookup/open/close.
            if(journal==null)journal=new FileOutputStream(RunFiles.file(run,"PROBE.JSN"),true);
            journal.write((line+"\n").getBytes("UTF-8"));
            // fsync the low-frequency launch/capture entries so the last line
            // survives a reset. LUT mode never syncs during live preview.
            if(name.startsWith("recipe")||"failed".equals(name)||"closed".equals(name)
                    ||"camera_open_begin".equals(name)||"preview_started".equals(name)){
                journal.flush();journal.getFD().sync();
            }
        }catch(IOException e){android.util.Log.e("SonyPreJpeg","journal_write_failed",e);}
        journalMs+=SystemClock.elapsedRealtime()-start;
    }
    private synchronized void closeJournal(){journalClosed=true;
        if(journalHandler!=null){
            final HandlerThread writerThread=journalThread;
            if(writerThread!=null)journalHandler.post(new Runnable(){public void run(){writerThread.quit();}});
            journalHandler=null;journalThread=null;
        }
        if(journal!=null)try{journal.close();}catch(IOException e){android.util.Log.e("SonyPreJpeg","journal_close_failed",e);}finally{journal=null;}
    }
    private synchronized long journalWriteMs(){return journalMs;}
    private static String seconds(long ms){return String.format(java.util.Locale.US,"%.3f",ms/1000.0);}
    private static String pixelSummary(android.graphics.Bitmap bitmap){
        if(bitmap==null||bitmap.isRecycled()||bitmap.getWidth()<1||bitmap.getHeight()<1)return "none";
        int r=0,g=0,b=0,n=0;
        for(int fy:new int[]{25,50,75})for(int fx:new int[]{25,50,75}){
            int pixel=bitmap.getPixel(bitmap.getWidth()*fx/100,bitmap.getHeight()*fy/100);
            r+=(pixel>>16)&255;g+=(pixel>>8)&255;b+=pixel&255;n++;
        }
        return (r/n)+","+(g/n)+","+(b/n);
    }
    /** One-shot diagnosis: the saved JPEG is Sony's correct reference. Compare
     * the fast capture preview against it to name an R/B or Cb/Cr output bug. */
    private void auditPreviewColor(File original,File graded)throws Exception{
        Bitmap decodedOriginal=null,decodedGraded=null;
        long start=SystemClock.elapsedRealtime();
        try{
            decodedOriginal=auditDecode(original);
            decodedGraded=auditDecode(graded);
            if(sourceReference==null||lutReview==null)throw new IOException("audit preview bitmap unavailable");
            java.util.List<int[]> sourcePreview=new java.util.ArrayList<int[]>(),sourceJpeg=new java.util.ArrayList<int[]>();
            java.util.List<int[]> gradedPreview=new java.util.ArrayList<int[]>(),gradedJpeg=new java.util.ArrayList<int[]>();
            collectAuditSamples(sourceReference,decodedOriginal,sourcePreview,sourceJpeg);
            collectAuditSamples(lutReview,decodedGraded,gradedPreview,gradedJpeg);
            if(sourcePreview.size()<4||gradedPreview.size()<4)throw new IOException("too few color samples");
            PreviewColorAudit.Result source=PreviewColorAudit.compare(
                    sourcePreview.toArray(new int[0][]),sourceJpeg.toArray(new int[0][]));
            PreviewColorAudit.Result gradedAudit=PreviewColorAudit.compare(
                    gradedPreview.toArray(new int[0][]),gradedJpeg.toArray(new int[0][]));
            event("preview_color_audit","ms="+(SystemClock.elapsedRealtime()-start)
                    +" source=["+source.summary()+" samples="+source.samples+" first="+source.first+"]"
                    +" graded=["+gradedAudit.summary()+" samples="+gradedAudit.samples+" first="+gradedAudit.first+"]");
        }finally{
            if(decodedOriginal!=null&&!decodedOriginal.isRecycled())decodedOriginal.recycle();
            if(decodedGraded!=null&&!decodedGraded.isRecycled())decodedGraded.recycle();
        }
    }
    private static void collectAuditSamples(android.graphics.Bitmap preview,android.graphics.Bitmap jpeg,
                                            java.util.List<int[]> previewSamples,java.util.List<int[]> jpegSamples){
        if(preview==null||jpeg==null||preview.getWidth()<1||jpeg.getWidth()<1)return;
        if((preview.getWidth()>preview.getHeight())!=(jpeg.getWidth()>jpeg.getHeight()))return;
        for(int fy=10;fy<=90;fy+=16)for(int fx=10;fx<=90;fx+=16){
            int pixel=preview.getPixel(preview.getWidth()*fx/100,preview.getHeight()*fy/100);
            int reference=jpeg.getPixel(jpeg.getWidth()*fx/100,jpeg.getHeight()*fy/100);
            int pr=(pixel>>16)&255,pg=(pixel>>8)&255,pb=pixel&255;
            int jr=(reference>>16)&255,jg=(reference>>8)&255,jb=reference&255;
            if(pr+pg+pb<45||jr+jg+jb<45)continue; // dark noise carries no color model
            previewSamples.add(new int[]{pr,pg,pb});
            jpegSamples.add(new int[]{jr,jg,jb});
        }
    }
    private static String exifSummary(File file){
        try{
            android.media.ExifInterface exif=new android.media.ExifInterface(file.getPath());
            String iso=exif.getAttribute("PhotographicSensitivity");
            if(iso==null)iso=exif.getAttribute("ISOSpeedRatings");
            return "iso="+iso+" f="+exif.getAttribute("FNumber")
                    +" shutter="+exif.getAttribute("ExposureTime")
                    +" program="+exif.getAttribute("ExposureProgram")
                    +" wb="+exif.getAttribute("WhiteBalance")
                    +" time="+exif.getAttribute("DateTimeOriginal");
        }catch(Throwable e){return "unreadable:"+e;}
    }
    private static android.graphics.Bitmap auditDecode(File file)throws IOException{
        if(!file.isFile())throw new IOException("audit JPEG missing");
        android.graphics.BitmapFactory.Options options=new android.graphics.BitmapFactory.Options();
        options.inJustDecodeBounds=true;android.graphics.BitmapFactory.decodeFile(file.getPath(),options);
        if(options.outWidth<=0||options.outHeight<=0)throw new IOException("audit JPEG bounds failed");
        int sample=1,longest=Math.max(options.outWidth,options.outHeight);
        while(longest/sample>1024)sample*=2;
        options.inJustDecodeBounds=false;options.inSampleSize=sample;
        android.graphics.Bitmap bitmap=android.graphics.BitmapFactory.decodeFile(file.getPath(),options);
        if(bitmap==null)throw new IOException("audit JPEG decode failed");
        return bitmap;
    }
    private void processing(final CaptureProgress.Stage stage,final String text){
        final long shot=gate.shot();
        ui.post(new Runnable(){public void run(){
        if(!active||destroyed||closed||!gate.busy()||!processingProgress.advance(shot,stage))return;
        processingText=text;
        android.util.Log.i("LutUI","capture_progress shot="+shot+" stage="+stage+" text="+text);
        if(!processingVisible){
            processingVisible=true;ui.removeCallbacks(hideReviewStatus);
            status.setLayoutParams(new FrameLayout.LayoutParams(-1,-1));
            status.setBackgroundColor(0xff181818);status.setGravity(Gravity.CENTER);status.setTextSize(24);
            status.setSingleLine(false);status.setMaxLines(3);status.setVisibility(View.VISIBLE);
        }
        ui.removeCallbacks(processingTick);processingTick.run();
    }});}
    private void endProcessing(){
        boolean wasVisible=processingVisible;
        if(productHud!=null)productHud.endDeveloping();
        processingVisible=false;awaitProcessingFrame=false;ui.removeCallbacks(processingTick);ui.removeCallbacks(restoreGraphics);
        if(!wasVisible)return;
        status.setLayoutParams(new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
        status.setBackgroundColor(0xb0000000);status.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);status.setTextSize(17);
    }
    private void message(final String value){ui.post(new Runnable(){public void run(){if(!destroyed){
        ui.removeCallbacks(hideReviewStatus);status.setVisibility(View.VISIBLE);status.setSingleLine(false);status.setMaxLines(3);status.setText(value);
        if(productHud!=null){status.setVisibility(View.GONE);
            if(gate.state()==PreJpegGate.State.READY&&!reviewing)productLive();
            else if(gate.busy()){productHud.mode="processing";productHud.detail=value.startsWith("接口等待")?"保存时间较长，请继续等待":value;productHud.progress="";productHud.update();}
        }
    }}});}
    private void savedStatus(String value){
        endProcessing();
        ui.removeCallbacks(hideReviewStatus);status.setSingleLine(true);status.setVisibility(View.VISIBLE);status.setText(value);
        if(productHud!=null){status.setVisibility(View.GONE);productHud.setVisibility(View.VISIBLE);productHud.mode="review";productHud.original=showingOriginal;
            productHud.detail="";
            productHud.footer="←→ / 拨轮翻图  ● 对照  ↑ 信息  半按继续";productHud.progress="";productHud.update();
        }else ui.postDelayed(hideReviewStatus,3500);
    }
    private static Throwable cause(Throwable e){while(e.getCause()!=null)e=e.getCause();return e;}
    private void failure(final String where,Throwable e){
        android.util.Log.e("SonyPreJpeg","failure stage="+where,e);
        final String failureReason=cause(e).getMessage();
        event("failed",where+" "+cause(e));gate.fail();message("测试停止："+where+"\n请保留当前页面，读取日志。");
        ui.post(new Runnable(){public void run(){
            if(autoPreview!=null){
                autoPreview.close();
                if(sequence!=null)try{sequenceType.getMethod("setReleaseLock",boolean.class).invoke(sequence,true);}
                catch(Exception lockError){event("failure_lock_error",cause(lockError).toString());}
            }
            endProcessing();
            if(productHud!=null){
                status.setVisibility(View.GONE);productHud.mode="error";productHud.detail="拍摄未完成 · "+where;
                productHud.progress=originalFile!=null?"原片已保存 · "+(failureReason==null?"":failureReason)
                        :(failureReason==null?"请返回胶卷库重新进入拍摄":failureReason);
                productHud.footer="MENU 返回胶卷库后重试";productHud.update();}
            if(active&&review!=null)try{review.showGraphicsOutput();}catch(Exception x){android.util.Log.e("SonyPreJpeg","error_graphics_restore_failed",x);}
        }});
    }
    @Override public void onCreate(Bundle state){super.onCreate(state);Lang.init(this);
        previousExceptionHandler=Thread.getDefaultUncaughtExceptionHandler();
        exceptionHandler=new Thread.UncaughtExceptionHandler(){public void uncaughtException(Thread thread,Throwable error){
            android.util.Log.e("SonyPreJpeg","uncaught thread="+thread.getName()+" shot="+gate.shot(),error);
            try{if(run!=null){PrintWriter out=new PrintWriter(new FileOutputStream(RunFiles.file(run,"CRASH.TXT"),true));
                try{out.println("thread="+thread.getName()+" state="+gate.state()+" shot="+gate.shot());error.printStackTrace(out);out.flush();}finally{out.close();}}}
            catch(Throwable ignored){}
            if(previousExceptionHandler!=null)previousExceptionHandler.uncaughtException(thread,error);
            else{android.os.Process.killProcess(android.os.Process.myPid());System.exit(10);}
        }};
        Thread.setDefaultUncaughtExceptionHandler(exceptionHandler);
        if(getIntent().getBooleanExtra("review_clear_check",false)){
            phase="review";initOnly=true;reviewClearCheck=true;createPage();
            lutReview=Bitmap.createBitmap(30,20,Bitmap.Config.ARGB_8888);
            review.setImageBitmap(lutReview);review.setVisibility(View.VISIBLE);
            message("显示组件检查 · 不打开拍摄接口");return;
        }
        final String reviewPhoto=getIntent().getStringExtra("review_photo");
        if(reviewPhoto!=null){
            // Inspect an already-saved result through the exact same display
            // layout. No CameraEx, capture sequence, LUT or new output file.
            phase="review";initOnly=true;createPage();
            work.post(new Runnable(){public void run(){loadExistingReview(reviewPhoto);}});return;
        }
        phase=getIntent().getStringExtra("phase");String token=getIntent().getStringExtra("probe_token");
        initOnly=getIntent().getBooleanExtra("init_only",true);
        initStage=getIntent().getStringExtra("init_stage");if(initStage==null)initStage="defaults";
        mapRevision=getIntent().getStringExtra("map_revision");if(mapRevision==null)mapRevision="firmware00";
        continuous=getIntent().getBooleanExtra("continuous",false);
        verifyPayloads=getIntent().getBooleanExtra("verify_payloads",false);
        overlapOriginal=getIntent().getBooleanExtra("overlap_original",false);
        sourceReferenceEnabled=getIntent().getBooleanExtra("source_reference",false);
        alternateOverlap=getIntent().getBooleanExtra("alternate_overlap",false);
        if(recipeMode()){
            recipeId=getIntent().getIntExtra("recipe_id",-1);
            if(recipeId<0||recipeId>=RecipeData.ALL.length){
                status=new TextView(this);status.setText("配方不可用。\n请返回胶卷库重新选择。");setContentView(status);return;
            }
            recipe=RecipeData.ALL[recipeId];
            String cameraName=getIntent().getStringExtra("camera_name");
            recipeCameraName=cameraName==null||cameraName.length()==0?RecipeData.GROUPS[recipe.group].zh:cameraName;
        }
        if(alternateOverlap&&(!continuous||!overlapOriginal)){
            status=new TextView(this);status.setText("交替测速需要开启连续保存重叠测试。");setContentView(status);return;
        }
        if((overlapOriginal&&!paired())||(sourceReferenceEnabled&&(!paired()||!verifyPayloads||continuous))){
            status=new TextView(this);status.setText("原片对照仅用于单张诊断；保存重叠仅用于双文件 LUT 流程。");setContentView(status);return;
        }
        if(continuous&&(!paired()||initOnly)){
            status=new TextView(this);status.setText("连续拍摄仅用于已验证的双文件 LUT 流程。");setContentView(status);return;
        }
        boolean freshEntry=!hasEnteredProbe;hasEnteredProbe=true;
        normalizeZoom=getIntent().getBooleanExtra("normalize_zoom",false);
        // The firmware 00_00 revision stalled in the A6300 probe. Use the
        // exact-platform reference asset instead: MAP24R1 for 24m, and the real
        // firmware-extracted MAP42R1 for the A7R II 42m profile. All EXTRA FINE
        // captures still require a fresh, explicitly selected reference session.
        if(!recipeMode()&&((!"firmware00".equals(mapRevision)&&!referenceRevision())||
                (extraFine()&&!initOnly&&(!referenceRevision()||
                !freshEntry||!getIntent().getBooleanExtra("fresh_session",false))))){
            status=new TextView(this);status.setText("此保存组合尚未开放测试。\n正常拍摄请打开 A7R2胶片相机。");setContentView(status);return;
        }
        if(getIntent().getBooleanExtra("restore_smart_zoom",false)&&(!"preview".equals(phase)||!initOnly)){
            status=new TextView(this);status.setText("设置恢复仅允许在无拍摄的预览检查中执行。");setContentView(status);return;
        }
        if((!"preview".equals(phase)&&!"handoff".equals(phase)&&!"recipe".equals(phase)&&!develops())||token==null||token.length()<8
                ||(!"defaults".equals(initStage)&&!"configured".equals(initStage))
                ||(!initOnly&&!"configured".equals(initStage))
                ||token.equals(getPreferences(0).getString("entered",null))
                ||!getPreferences(0).edit().putString("entered",token).commit()){
            status=new TextView(this);status.setText("单次测试已结束或缺少测试标识。\n正常拍照请打开 A7R2胶片相机。");setContentView(status);return;
        }
        createPage();
        work.post(new Runnable(){public void run(){prepare();}});
    }
    private void createPage(){
        SurfaceView surface=new SurfaceView(this);holder=surface.getHolder();holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);holder.addCallback(this);
        FrameLayout frame=new FrameLayout(this);frame.addView(surface,new FrameLayout.LayoutParams(-1,-1));
        review=new ReviewView();review.setBackgroundColor(Color.BLACK);review.setVisibility(View.INVISIBLE);
        frame.addView(review,new FrameLayout.LayoutParams(-1,-1));
        status=new StatusView();status.setTextSize(17);status.setMaxLines(3);status.setTextColor(Color.WHITE);
        status.setBackgroundColor(0xb0000000);status.setPadding(8,3,8,3);status.setText("检测相机拍摄接口…");
        frame.addView(status,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
        if(productUi()){productHud=new PhotoHud(this);frame.addView(productHud,new FrameLayout.LayoutParams(-1,-1));status.setVisibility(View.GONE);
            productHud.recipeMode=recipeMode();
            if(recipeMode())productHud.name=getIntent().getStringExtra("film_name");
            productHud.setFilmLoading(getIntent().getStringExtra("film_name"),getIntent().getIntExtra("film_index",0));}
        setContentView(frame);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        thread=new HandlerThread("prejpeg-probe");thread.start();work=new Handler(thread.getLooper());
        if(recipeMode())startRecipeDiagnostics();
    }
    private void loadExistingReview(String path){try{
        CaptureProfile p=platformProfile();
        File root=new File(Environment.getExternalStorageDirectory(),"DCIM").getCanonicalFile();
        final File file=new File(path).getCanonicalFile();
        if(!file.getPath().startsWith(root.getPath()+File.separator)||!file.getName().matches("[A-Z0-9_]{1,8}\\.JPG")||
                !PhotoFiles.jpegHeader(file,p.width,p.height))throw new IOException("review requires a saved "+p.sizeText()+" DCIM JPEG");
        BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=8;
        final Bitmap bitmap=BitmapFactory.decodeFile(file.getPath(),options);
        if(bitmap==null)throw new IOException("saved review decode failed");
        ui.post(new Runnable(){public void run(){
            if(destroyed||closed){bitmap.recycle();return;}
            lutReview=bitmap;review.setImageBitmap(bitmap);review.setVisibility(View.VISIBLE);
            savedStatus("已保存的冲洗成片");
            event("review_loaded","file="+file+" bitmap="+bitmap.getWidth()+"x"+bitmap.getHeight()+" camera_opened=false");
        }});
    }catch(Throwable e){failure("review_file",e);}
    }
    private void prepare(){try{
        sd=Environment.getExternalStorageDirectory();run=RunFiles.create(sd);event("prepare","phase="+phase+" init_only="+initOnly+" init_stage="+initStage+" run="+run+" pid="+android.os.Process.myPid()+" fresh_session="+getIntent().getBooleanExtra("fresh_session",false)+" continuous="+continuous+" verify_payloads="+verifyPayloads+" overlap_original="+overlapOriginal+" source_reference="+sourceReferenceEnabled+" probe_token="+getIntent().getStringExtra("probe_token"));
        if(!initOnly)photoFiles=new PhotoFiles(sd,"handoff".equals(phase)?PhotoFiles.Format.ARW:PhotoFiles.Format.JPEG,!develops()&&!recipeMode());
        profile=platformProfile();
        if(recipeMode()){
            // Recipes capture through Sony's ordinary JPEG pipeline; no LUT map
            // is loaded and no RAW sequence is opened.
            mapPath="none";
            event("recipe_profile","memory="+profile.memory+" pixels="+profile.pixels+" label="+profile.label
                    +" expected="+profile.sizeText()+" recipe="+recipe.name+" camera="+recipeCameraName);
        }else{
            File map;
            if(referenceRevision()){
                if(!profile.supportsRevision(mapRevision))
                    throw new IOException("map revision "+mapRevision+" does not match "+profile.label);
                // These bytes are the original exact-platform reference asset.
                // They are data consumed by Sony's map loader, not a shared library.
                map=Assets.extract(new File(getApplicationInfo().sourceDir),profile.mapAsset,getCacheDir());
                if(map.length()!=profile.referenceMapBytes)throw new IOException("reference map size mismatch");
            }else{
                map=new File(profile.firmwareMapPath);
                if(!map.isFile()||map.length()!=profile.firmwareMapBytes)throw new IOException("validated firmware map unavailable");
            }
            // Sony AV service resolves native paths outside the Android chroot.
            mapPath="/android"+map.getPath();event("profile","memory="+profile.memory+" pixels="+profile.pixels+" label="+profile.label
                    +" expected="+profile.sizeText()+" map_revision="+mapRevision+" resolved="+profile.mapRevision+" map="+mapPath+" map_bytes="+map.length());
            readUvOrder();
        }
        cameraType=Class.forName("com.sony.scalar.hardware.CameraEx");sequenceType=Class.forName("com.sony.scalar.hardware.CameraSequence");
        rawType=Class.forName("com.sony.scalar.hardware.CameraSequence$RawData");imageType=Class.forName("com.sony.scalar.graphics.OptimizedImage");
        if(paired()&&!initOnly){
            File custom=RunFiles.file(RunFiles.file(sd,"SONYLUT"),"CUSTOM.CUB");
            File lut;
            if(productUi()){
                selectedLut=new LutLibrary(ProductFiles.library()).find(getIntent().getStringExtra("lut_id"));
                if(selectedLut==null)throw new IOException("selected LUT is unavailable");
                lut=selectedLut.file;
                ui.post(new Runnable(){public void run(){if(!destroyed&&productHud!=null){productHud.name=selectedLut.name;productHud.tag="";productHud.update();}}});
            }else lut=custom.isFile()?custom:Assets.extract(new File(getApplicationInfo().sourceDir),"COLOR17.CUB",run);
            long loadStart=SystemClock.elapsedRealtime();
            if(productUi()){
                LutBinaryCache.verifySource(selectedLut);
                // Keep the fitted approximate look in the finder. The global
                // “当前胶卷的取景” choice still decides between fitted and the
                // camera's original colour; a missing/failed fit falls back to
                // original colour instead of blocking the shot.
                if(automaticNativePreview()&&PreviewProfileStore.choice(selectedLut).enabled){
                    try{
                        automaticProfile=PreviewProfileStore.load(selectedLut);
                        if(automaticProfile==null)automaticProfile=PreviewFitting.ensure(selectedLut);
                        event("preview_profile_loaded","fit_version="+automaticProfile.fitVersion+" mae="+automaticProfile.mae+" warning="+automaticProfile.largeError());
                    }catch(Exception error){
                        automaticProfile=null;
                        event("preview_profile_unavailable",cause(error).toString());
                        android.util.Log.w("SonyPreviewFit","capture_fit_failed",error);
                    }
                }
                event("lut_source_verified","ms="+(SystemClock.elapsedRealtime()-loadStart));
                lutCache=Native.prepareDeviceLutCompiled(lut.getPath(),LutBinaryCache.binary(lut).getPath(),selectedLut.id,4);
            }else lutCache=Native.prepareDeviceLut(lut.getPath(),4);
            event("lut_load",Native.deviceLutLoadInfo(lutCache)+" source_and_load_ms="+(SystemClock.elapsedRealtime()-loadStart));
            long bitmapStart=SystemClock.elapsedRealtime();lutReview=Bitmap.createBitmap(750,500,Bitmap.Config.ARGB_8888);
            if(sourceReferenceEnabled||productUi())sourceReference=Bitmap.createBitmap(750,500,Bitmap.Config.ARGB_8888);
            event("review_buffers_ready","ms="+(SystemClock.elapsedRealtime()-bitmapStart));
            event("lut_prepared","file="+lut+" workers=4 review=750x500");
        }
        stage("prepared");
        if(recipeMode())startHeartbeatWatch();
        prepared=true;ui.post(new Runnable(){public void run(){open();}});
    }catch(Throwable e){failure("prepare",e);}}
    private Object listener(String iface,InvocationHandler callback)throws Exception{
        Class<?> type=Class.forName(iface);return Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},callback);
    }
    private static Object objectMethod(Object proxy,Method method,Object[] args){
        if(method.getName().equals("hashCode"))return System.identityHashCode(proxy);
        if(method.getName().equals("equals"))return proxy==args[0];return "PreJpegProbeListener";
    }
    private void open(){
        if(!active||!prepared||opening||closed||holder==null||!surfaceAvailable)return;
        opening=true;
        stage("camera_open_begin");
        try{
            // Start EE after listener registration so its readiness event is not
            // lost during open(). Retain the user's existing shooting settings.
            // Declare still recording mode explicitly: the A7R II keeps its
            // movie engine selected after a video session, and opening with an
            // inherited movie mode reset the body on a7r2.26.
            Class<?> openType=Class.forName("com.sony.scalar.hardware.CameraEx$OpenOptions");Object options=openType.newInstance();
            // Recipe Lab opens with null OpenOptions and a default preview
            // session; the A7R II reset at launch when the recipe look was
            // written after startPreview in the no-preview session. Keep the
            // explicit still mode (video inheritance) but leave preview at
            // its default for recipes.
            boolean recipeOpen=recipeMode();
            if(!recipeOpen)openType.getMethod("setPreview",boolean.class).invoke(options,false);
            try{openType.getMethod("setRecordingMode",int.class).invoke(options,0);}catch(Throwable ignored){}
            try{openType.getMethod("setMovieMode",int.class).invoke(options,0);}catch(Throwable ignored){}
            event("camera_open_begin","preview="+(recipeOpen?"default":"false")+" inherit_setting=default recording_mode=still");loadingStage("正在连接相机",1);cameraEx=cameraType.getMethod("open",int.class,openType).invoke(null,0,options);
            camera=(Camera)cameraType.getMethod("getNormalCamera").invoke(cameraEx);
            // a7r2.52: drop any focus request the movie session left queued in
            // the camera service before the still session starts its preview.
            resetCameraFocus("open");
            Camera.Parameters p=camera.getParameters();Camera.Size size=p.getPictureSize();
            event("camera_parameters","drive="+p.get("drive-mode")+" storage="+p.get("storage-fmt")+" jpeg_quality="+p.get("jpeg-quality")+" size="+(size==null?"null":size.width+"x"+size.height));
            // Read-only state stamp: compares a normal still session with one
            // that follows a movie session when the direct shutter misbehaves.
            event("camera_focus_state","focus_mode="+p.get("focus-mode")+" af_mode="+p.get("af-mode")
                    +" area="+p.get("focus-area-type")+" point="+p.get("focus-point-x")+","+p.get("focus-point-y")
                    +" shooting_preview="+p.get("shooting-preview")+" scene="+p.get("scene-mode"));
            event("camera_zoom",zoomValues(p));
            if(!"single".equals(p.get("drive-mode"))||!"jpeg".equals(p.get("storage-fmt")))throw new IOException("probe requires existing single/JPEG settings");
            if(profile==null)throw new IOException("capture profile not prepared");
            if(size==null||size.width!=profile.width||size.height!=profile.height){
                String before=size==null?"null":size.width+"x"+size.height;
                originalPictureSize=size==null?null:before;
                p.setPictureSize(profile.width,profile.height);
                try{camera.setParameters(p);}
                catch(Throwable e){throw new IOException("cannot select "+profile.sizeText(),e);}
                Camera.Size applied=camera.getParameters().getPictureSize();
                if(applied==null||applied.width!=profile.width||applied.height!=profile.height)
                    throw new IOException("probe requires existing "+profile.sizeText()+" setting");
                event("picture_size_applied","requested="+profile.sizeText()+" original="+before+" actual="+applied.width+"x"+applied.height);
            }
            // Explicit recovery for the recorded 0.6.18 transaction: the native
            // close restored quality50 but force-stop left smart zoom disabled.
            // Run within this proven Activity lifecycle, never app_process.
            if(getIntent().getBooleanExtra("restore_smart_zoom",false)){
                if(p.getJpegQuality()!=50||!"false".equals(p.get("super-resolution-zoom"))||!"false".equals(p.get("precision-zoom"))
                        ||(!"true".equals(p.get("smart-zoom"))&&!"false".equals(p.get("smart-zoom"))))
                    throw new IOException("recovery preconditions differ from recorded original settings");
                if(!"true".equals(p.get("smart-zoom"))){
                    event("recorded_zoom_restore_begin","smart-zoom=true original_run=R54CCEF1");
                    p.set("smart-zoom","true");camera.setParameters(p);
                }
                if(!"true".equals(camera.getParameters().get("smart-zoom")))throw new IOException("smart zoom restore readback failed");
                event("recorded_zoom_restore_done",zoomValues(camera.getParameters()));
            }
            if("develop".equals(phase)&&p.getJpegQuality()!=50)throw new IOException("accepted Fine control requires jpeg-quality=50; current="+p.getJpegQuality());
            if(normalizeZoom){
                for(String key:ZOOM_KEYS){String value=p.get(key);if(value==null)throw new IOException("missing zoom capability "+key);
                    if(!"false".equals(value)){originalZoom.put(key,value);p.set(key,"false");}}
                if(!originalZoom.isEmpty()){
                    event("zoom_disable_begin",originalZoom.toString());camera.setParameters(p);
                    event("zoom_disable_done",zoomValues(camera.getParameters()));
                }
            }
            if(extraFine()){
                String supported=p.get("jpeg-quality-values");event("quality_capabilities","values="+supported);
                if(supported==null||!(","+supported.replace(" ","")+",").contains(",95,"))throw new IOException("EXTRA FINE not advertised; test skipped");
                originalQuality=p.get("jpeg-quality");if(originalQuality==null)throw new IOException("missing original JPEG quality");
                p.setJpegQuality(95);camera.setParameters(p);
                if(camera.getParameters().getJpegQuality()!=95)throw new IOException("EXTRA FINE readback failed");
                event("quality_applied","jpeg_quality=95 original="+originalQuality);
            }
            if(productUi()){
                loadingStage("正在恢复曝光设置",2);
                if(profile.manualControls&&recipeMode()){
                    // Recipe Lab's proven path on the same body: a plain live
                    // view with no scene/ISO/shooting-preview session. Read the
                    // controls the recipe will replace; write nothing until
                    // preview_started. Starting ManualExposure in M and then
                    // switching to A before the Creative Style/WB patch reset
                    // the A7R II at launch.
                    startManualControls();
                    prepareRecipeSession();
                    af=new AfPointController(new CameraAfDriver(cameraEx,camera),afLog);af.open();
                    seedRecipeReadout(p);
                    ui.post(new Runnable(){public void run(){if(productHud!=null)productLive();}});
                }else if(profile.manualControls){
                    startManualControls();
                    manual=new ManualExposure(new CameraExManualDriver(cameraEx,camera),manualLog);
                    manual.open();
                    stage("manual_open_done "+manual.mode());
                    af=new AfPointController(new CameraAfDriver(cameraEx,camera),afLog);af.open();
                    String savedMode=getPreferences(0).getString("exposure_mode",null);
                    if(savedMode!=null&&!savedMode.equals(manual.mode()))try{manual.selectMode(savedMode);}
                    catch(Throwable e){event("manual_mode_restore_error",cause(e).toString());}
                    event("manual_exposure_ready",manual.state());
                    ui.post(new Runnable(){public void run(){if(productHud!=null){productHud.manualExposure=true;
                        productLive();}}});
                }else{
                    exposure=new SimpleExposure(new CameraExposureDriver(camera));exposure.open();
                    event("automatic_exposure_ready",exposure.state());
                }
            }
            if(productUi())loadingStage("正在恢复对焦",2);
            registerCameraListeners();
            // Recipe readouts are seeded once from the parameters above; its
            // telemetry is battery-only because vendor exposure listeners are
            // another callback path into the UI thread and a camera-native
            // JPEG does not need them. The battery is a sticky Android
            // broadcast, which is the missing piece that left the recipe HUD
            // showing "--%" (a7r2.47).
            if(productUi()&&recipeMode()){
                telemetry=new CaptureTelemetry(this,ui,new CaptureTelemetry.Observer(){
                    public void changed(CaptureReadout values){
                        if(closed||destroyed||productHud==null)return;
                        CaptureReadout shown=productHud.readout;
                        if(shown==null){productHud.readout=values;shown=values;}
                        else{shown.battery=values.battery;shown.charging=values.charging;}
                        if(productHud.mode.equals("live")&&productHud.liveInfoVisible)productHud.update();
                    }
                });
                telemetry.openBatteryOnly(camera.getParameters());
                event("recipe_battery_ready","source=battery_broadcast vendor_listeners=0");
            }else if(productUi()){
                telemetry=new CaptureTelemetry(this,ui,new CaptureTelemetry.Observer(){
                    public void changed(CaptureReadout values){
                        if(closed||destroyed||productHud==null)return;
                        if(manual!=null)manual.updateManualFields(values);
                        productHud.readout=values;
                        if(productHud.mode.equals("live")&&productHud.liveInfoVisible)productHud.update();
                    }
                });
                telemetry.open(cameraEx,camera.getParameters());
            }
            loadingStage("正在准备取景",3);
            if(recipeMode()&&recipe!=null&&recipeLook!=null){
                // Recipe Lab writes the look from onResume, before the
                // SurfaceView starts the live view. Do the same: the launch
                // reset on a7r2.38–.41 happened when the Creative Style/WB
                // patch was committed against an already-running preview in
                // the no-preview session. A failed look must not stop the
                // live view; the finder stays usable.
                loadingStage("正在应用胶片",3);
                stage("recipe_preview_apply "+recipe.name);
                if(applyRecipeLook(recipe,"startup_preview"))appliedRecipeId=recipeId;
                else event("recipe_preview_apply_skipped","name="+recipe.name);
            }
            if(productUi()){
                // a7r2.54: no face-detection controller in recipe mode. The
                // recipe path is a bare camera.takePicture() transaction; the
                // extra ParametersModifier calls correlated with a returned
                // takePicture that never wrote a file (R00805E1). Keep the
                // recipe path free of vendor extras.
                if(recipeMode()){
                    if(getIntent().getBooleanExtra("face_detection",false))
                        event("face_detection_skipped","reason=recipe_capture_safety");
                }else{
                    // Native face-priority AF only. No FaceDetectionListener is
                    // registered, so a vendor callback can never enter and
                    // freeze this Activity's UI thread.
                    try{
                        faceDetection=new FaceDetectionController(cameraEx,camera,
                            getIntent().getBooleanExtra("face_detection",false),sd,
                            new FaceDetectionController.Log(){public void event(String name,String detail){
                                PreJpegProbeActivity.this.event(name,detail);
                            }});
                        faceDetection.apply("lut");
                    }catch(Throwable faceError){
                        faceDetection=null;
                        event("face_detection_unavailable",cause(faceError).toString());
                    }
                }
            }
            stage("preview_request");
            event("preview_request_begin","");camera.setPreviewDisplay(holder);camera.startPreview();event("preview_request_done","");
            // a7r2.53: the cancel at open ran before preview and failed on this
            // body ("cancelAutoFocus failed"). With the preview live, cancel
            // again so a request inherited from a movie session cannot keep
            // the AF executor busy when the direct shutter reaches S2.
            resetCameraFocus("preview");
            stage("preview_returned");
            if(recipeMode()&&recipe!=null){
                // a7r2.45: treat startPreview() returning as preview-ready, as
                // Recipe Lab does. No CameraEx preview notification (vendor
                // callback into this process) is required anymore.
                gate.preview();
                stage("recipe_preview_returned");
                ui.post(new Runnable(){public void run(){recipeUiReady();}});
                stage("recipe_ui_posted");
            }
            ui.postDelayed(new Runnable(){public void run(){if(!closed&&gate.state()==PreJpegGate.State.NEW){
                failure("preview_start_timeout",new IOException("no native preview notification; RAW registration skipped"));close();
            }}},transitionTimeout());
        }catch(Throwable e){failure("open",e);close();}
    }
    private void onPreviewStarted(){
        previewCallbackThread=Thread.currentThread().getName();
        event("preview_started","state="+gate.state()+" thread="+previewCallbackThread);
        stage("preview_started state="+gate.state());
        if(autoPreview!=null&&autoPreview.state()==AutoPreviewGate.State.WAITING){
            ui.post(new Runnable(){public void run(){if(active&&!closed)autoPreview.previewReady();}});return;
        }
        if(previewIsolationProbe()&&isolationBarrier!=null&&active&&!closed
                &&isolationBarrier.state()==PreviewCaptureBarrier.State.WAITING_PREVIEW){
            ui.post(new Runnable(){public void run(){try{
                if(!active||closed)return;
                if(isolationBarrier.previewReady()){
                    event("isolation_capture_ready","baseline_verified=true direct_shutter=true");
                    message("原色已恢复 · 现在可半按对焦、全按拍照");
                    if(productHud!=null){productHud.showPersistentLiveHint("隔离测试：原色已恢复 · 按快门拍一张");productHud.update();}
                }
            }catch(Exception e){isolationBarrier.fail();failure("isolation_unlock",e);}}});return;
        }
        if(active&&!closed&&gate.state()==PreJpegGate.State.REARMING){
            final long shot=gate.shot();
            ui.post(new Runnable(){public void run(){finishRearm(shot);}});return;
        }
        if(!active||closed||gate.state()!=PreJpegGate.State.NEW)return;
        gate.preview();
        // Return to the event loop before issuing the next synchronous vendor RPC.
        ui.post(new Runnable(){public void run(){
            if(!active||closed)return;
            if("preview".equals(phase)){initComplete("camera_only");return;}
            if(recipeMode()){recipeUiReady();return;}
            configureSequence();
        }});
    }
    /** UI half of preview readiness. Recipe mode enters this from open() as
     * soon as startPreview() returns (Recipe Lab's order); the other phases
     * still wait for Sony's preview notification. The look was committed
     * before startPreview, so nothing here touches the parameter set. */
    private void recipeUiReady(){
        if(!active||closed)return;
        stage("recipe_ui_begin");
        if(productHud!=null)productHud.showLiveHint("正在应用 "+Lang.recipeName(recipe)+" · 相机直出");
        startRecipeShutter();
        // a7r2.46: C1/dials/WB need an exposure session, but it must not run
        // before the recipe look and the live preview. openPreservingScene()
        // keeps the camera's P/A/S/M and the recipe's WB/EV: the launch reset
        // on this body came from an M switch before the Creative Style/WB
        // patch, not from exposure handling itself.
        scheduleRecipeExposureSession();
        // Snapshot the existing DCIM JPEGs on the worker so the post-shot scan
        // can identify the one file this capture creates.
        work.post(new Runnable(){public void run(){
            try{
                recipeStoreBaseline=listRecipeJpegs(sd);
                event("recipe_store_baseline","count="+recipeStoreBaseline.size());
            }catch(Throwable e){
                recipeStoreBaseline=null;
                event("recipe_store_baseline_error",cause(e).toString());
            }
        }});
    }
    /** Open the recipe finder's exposure session once the live view is up.
     * Unlike the LUT finder it never forces M: P/A/S stay untouched and M only
     * gets Sony's live-preview mode. Runs on the serialized control thread so
     * a film switch or a dial step can never overlap the vendor writes. */
    private void scheduleRecipeExposureSession(){
        final Handler handler=controlHandler;
        if(handler==null||manual!=null||!recipeMode())return;
        handler.postDelayed(new Runnable(){public void run(){
            synchronized(controlLock){
                if(controlClosed||!active||closed||manual!=null||!recipeMode())return;
            }
            // Never open the session while a recipe capture or a live film
            // switch owns the camera; Sony serializes parameters internally,
            // but a read/write during takePicture can still upset the capture.
            if(gate.state()!=PreJpegGate.State.READY||workerActive||reviewing){
                Handler next=controlHandler;
                if(next!=null)synchronized(controlLock){
                    if(!controlClosed&&active&&!closed&&manual==null)next.postDelayed(this,500L);
                }
                return;
            }
            ManualExposure session=null;
            try{
                event("recipe_exposure_begin","mode_preserved=true");
                session=new ManualExposure(new CameraExManualDriver(cameraEx,camera),manualLog);
                session.openPreservingScene();
                synchronized(controlLock){
                    if(controlClosed||!active||closed){
                        try{session.close();}catch(Throwable e){event("recipe_exposure_abort_close_error",cause(e).toString());}
                        return;
                    }
                    manual=session;
                }
                event("recipe_exposure_ready",session.state());
                final ManualExposure opened=session;
                ui.post(new Runnable(){public void run(){
                    if(destroyed||closed||manual!=opened||productHud==null)return;
                    opened.update(productHud.readout);productLive();
                    productHud.showLiveHint("曝光控制已就绪 · C1 模式 · 前/后拨轮 EV · 转盘 ISO");
                    productHud.update();
                }});
            }catch(Throwable e){
                event("recipe_exposure_error",cause(e).toString());
                if(session!=null)try{session.close();}catch(Throwable ignored){}
            }
        }},700L);
    }
    /** Read the controls a recipe will replace. This is the only recipe
     * camera read before startPreview; nothing is written yet. */
    private void prepareRecipeSession(){
        if(!recipeMode()||recipe==null||recipeLook!=null)return;
        try{
            stage("recipe_snapshot_begin");
            RecipeCameraLook look=new RecipeCameraLook(new CameraExManualDriver(cameraEx,camera),
                    new RecipeCameraLook.Log(){public void log(String name,String detail){manualLog.log(name,detail);}});
            look.snapshot();
            // Publish before the live patch: even if one key is rejected the
            // user can still open C3 and pick another film.
            recipeLook=look;
            event("recipe_snapshot_done","mode="+camera.getParameters().get("scene-mode"));
            stage("recipe_snapshot_done");
        }catch(Throwable e){
            event("recipe_snapshot_error",cause(e).toString());
            stage("recipe_snapshot_error "+cause(e));
        }
    }
    /** Commit the recipe to the live finder. Recipe Lab applies the same
     * Camera.Parameters patch after startPreview on this body; stopping the
     * preview around the write, or running an M-mode session first, made the
     * A7R II reset at launch. A refused patch leaves the camera's original
     * colour on and never blocks the shutter. */
    private boolean applyRecipeLook(RecipeData.Recipe target,String origin){
        RecipeCameraLook look=recipeLook;
        if(look==null||target==null)return false;
        recipeApplyBusy=true;
        try{
            event("recipe_apply_begin","origin="+origin+" recipe="+target.name);
            stage("recipe_apply_begin "+target.name);
            look.apply(target);
            event("recipe_look_ready","origin="+origin+" name="+target.name
                    +" camera="+recipeCameraName+" summary="+RecipeData.summary(target));
            stage("recipe_look_ready "+target.name);
            return true;
        }catch(Throwable e){
            event("recipe_apply_failed","origin="+origin+" "+cause(e));
            stage("recipe_apply_failed "+cause(e));
            return false;
        }finally{
            recipeApplyBusy=false;
        }
    }
    /** Rewrite a tiny marker file at every coarse recipe step. PROBE.JSN has
     * arrived empty after hard resets because fsync is a no-op on Sony's FUSE
     * mount; a fresh truncate/write/close per stage has survived those. */
    private void stage(String value){
        if(!recipeMode()||run==null)return;
        lastStage=value;
        final File runDir=run;final Handler writer=journalHandler;
        final String line=SystemClock.elapsedRealtime()+" "+value+" recipe="+(recipe==null?"?":recipe.name)+"\n";
        Runnable write=new Runnable(){public void run(){
            FileOutputStream out=null;
            try{out=new FileOutputStream(RunFiles.file(runDir,"STAGE.TXT"),false);out.write(line.getBytes("UTF-8"));}
            catch(Throwable ignored){}finally{if(out!=null)try{out.close();}catch(Throwable ignored){}}
        }};
        // Queued like the journal: no fsync and no open() on the caller. The
        // heartbeat thread copies lastStage to HEART.TXT every two seconds, so
        // a process that dies hard still leaves the step it reached.
        if(writer!=null)writer.post(write);else write.run();
    }
    /** Recipe-only diagnostics. The journal writer and the watchdog run on
     * their own threads so a stalled card or UI cannot hide the last step. */
    private void startRecipeDiagnostics(){
        journalThread=new HandlerThread("recipe-journal");journalThread.start();
        journalHandler=new Handler(journalThread.getLooper());
        focusThread=new HandlerThread("recipe-af");focusThread.start();
        focusHandler=new Handler(focusThread.getLooper());
        receiptThread=new HandlerThread("recipe-receipt");receiptThread.start();
        receiptHandler=new Handler(receiptThread.getLooper());
        installLooperTrace();
        uiBeat=SystemClock.elapsedRealtime();
        ui.post(uiHeartbeat);
    }
    /** Main-looper message tracing. Stores the dispatch banner of the message
     * that is currently running; the heartbeat thread copies it to HEART.TXT
     * together with the main-thread stack. No file I/O runs on the UI thread. */
    private void installLooperTrace(){
        try{
            Looper.getMainLooper().setMessageLogging(new android.util.Printer(){
                public void println(String line){
                    if(line==null)return;
                    long now=SystemClock.elapsedRealtime();
                    if(line.startsWith(">>>>>")){
                        uiMessage=line.length()>220?line.substring(0,220):line;
                        uiMessageAt=now;uiMessageDoneAt=0;
                    }else if(line.startsWith("<<<<<")){
                        uiMessageDoneAt=now;
                    }
                }
            });
            looperTrace=true;
        }catch(Throwable e){event("looper_trace_error",cause(e).toString());}
    }
    private void removeLooperTrace(){
        if(!looperTrace)return;looperTrace=false;
        try{Looper.getMainLooper().setMessageLogging(null);}catch(Throwable ignored){}
    }
    private static String stackOf(Thread target,int limit){
        if(target==null)return "none";
        try{
            StackTraceElement[] frames=target.getStackTrace();
            if(frames==null||frames.length==0)return "empty";
            StringBuilder text=new StringBuilder();
            for(int i=0;i<frames.length&&i<limit;i++){
                if(text.length()>0)text.append(" <- ");
                StackTraceElement frame=frames[i];
                text.append(frame.getClassName()).append('.').append(frame.getMethodName())
                        .append(':').append(frame.getLineNumber());
            }
            return text.toString();
        }catch(Throwable e){return "unavailable:"+e.getClass().getSimpleName();}
    }
    private static void writeRunFile(File runDir,String name,String text){
        FileOutputStream out=null;
        try{out=new FileOutputStream(RunFiles.file(runDir,name),false);out.write(text.getBytes("UTF-8"));}
        catch(Throwable ignored){}finally{if(out!=null)try{out.close();}catch(Throwable ignored){}}
    }
    /** S1/S2 camera calls stay off the UI thread; each runnable captures the
     * camera instance it was posted for and gives up if the session closed. */
    private void recipeFocus(final boolean cancel,final Camera target,final int scan){
        if(target==null)return;
        final Handler handler=focusHandler;
        Runnable call=new Runnable(){public void run(){
            if(closed||destroyed)return;
            long start=SystemClock.elapsedRealtime();
            try{
                if(cancel)target.cancelAutoFocus();else target.autoFocus(null);
                event(cancel?"recipe_autofocus_canceled":"recipe_autofocus",
                        "scan="+scan+" ms="+(SystemClock.elapsedRealtime()-start));
            }catch(Throwable x){
                event(cancel?"recipe_autofocus_cancel_error":"recipe_autofocus_error",cause(x).toString());
            }
        }};
        if(handler!=null)handler.post(call);else call.run();
    }
    private void stopRecipeFocus(){
        Handler handler=focusHandler;HandlerThread thread=focusThread;
        focusHandler=null;focusThread=null;
        if(handler!=null)handler.removeCallbacksAndMessages(null);
        if(thread!=null)thread.quit();
    }
    /** Release Sony's capture trigger and put the live view back. Runs on the
     * capture worker; safe when the preview is already running. */
    private void restartRecipePreview(String origin){
        final Object cx=cameraEx;final Camera cam=camera;
        if(cx!=null)try{
            cx.getClass().getMethod("cancelTakePicture").invoke(cx);
            event("recipe_trigger_cancel","origin="+origin+" shot="+gate.shot());
        }catch(Throwable e){event("recipe_trigger_cancel_error","origin="+origin+" "+cause(e));}
        if(cam==null)return;
        try{
            cam.startPreview();
            event("recipe_preview_resumed","origin="+origin+" gate="+gate.state());
        }catch(Throwable e){event("recipe_preview_resume_error","origin="+origin+" "+cause(e));}
    }
    private void postRecipePreviewRestart(final String origin){
        Runnable call=new Runnable(){public void run(){if(!closed&&!destroyed)restartRecipePreview(origin);}};
        Handler handler=work;
        if(handler!=null)handler.post(call);else call.run();
    }
    /** Queue the release half of the shutter pair. The capture worker serializes
     * it after takePicture(), so the trigger is cancelled only after the frame
     * has been requested; Recipe Lab and ShotDirector use the same press/release
     * pairing with Camera/CameraEx on this platform. */
    private void cancelRecipeTrigger(final String origin){
        final Object cx=cameraEx;
        if(cx==null)return;
        Runnable call=new Runnable(){public void run(){
            if(closed||destroyed)return;
            try{
                cx.getClass().getMethod("cancelTakePicture").invoke(cx);
                event("recipe_trigger_cancel","origin="+origin+" shot="+gate.shot());
            }catch(Throwable e){event("recipe_trigger_cancel_error","origin="+origin+" "+cause(e));}
        }};
        Handler handler=work;
        if(handler!=null)handler.post(call);else call.run();
    }
    /** Release Sony's capture trigger after a timeout. Off the UI thread. */
    private void cancelRecipeTakePicture(){
        final Object cx=cameraEx;
        if(cx==null)return;
        Runnable call=new Runnable(){public void run(){
            try{cx.getClass().getMethod("cancelTakePicture").invoke(cx);event("recipe_take_picture_cancel","");}
            catch(Throwable e){event("recipe_take_picture_cancel_error",cause(e).toString());}
        }};
        Handler handler=focusHandler;
        if(handler!=null)handler.post(call);else call.run();
    }
    private final Runnable uiHeartbeat=new Runnable(){public void run(){
        uiBeat=SystemClock.elapsedRealtime();
        if(!destroyed&&!heartbeatStop)ui.postDelayed(this,500);
    }};
    private void startHeartbeatWatch(){
        final File runDir=run;if(runDir==null)return;
        final Thread watch=new Thread(new Runnable(){public void run(){
            while(!heartbeatStop){
                long now=SystemClock.elapsedRealtime(),uiAge=Math.max(0,now-uiBeat);
                Thread mainThread=Looper.getMainLooper().getThread();
                String text=now+" ui_age_ms="+uiAge+" state="+gate.state()
                        +" stage="+lastStage+" recipe="+(recipe==null?"?":recipe.name)
                        +" worker="+workerActive+" thread="+Thread.currentThread().getName()
                        +" preview_cb="+(previewCallbackThread.length()==0?"?":previewCallbackThread)
                        +" ui_msg_running="+(uiMessageAt>uiMessageDoneAt)
                        +" ui_msg_age_ms="+(uiMessageAt<=0?-1:Math.max(0,now-uiMessageAt))
                        +" ui_msg="+uiMessage+"\n"
                        +"main "+stackOf(mainThread,16)+"\n"
                        +"probe "+stackOf(thread,8)+"\n"
                        +"control "+stackOf(controlThread,8)+"\n"
                        +"af "+stackOf(focusThread,8)+"\n"
                        +"journal "+stackOf(journalThread,6)+"\n";
                writeRunFile(runDir,"HEART.TXT",text);
                // A frozen UI thread gets a deeper dump the first time the
                // heartbeat notices it. This survives a battery pull.
                if(uiAge>=2000)writeRunFile(runDir,"STACK.TXT",now+" ui_age_ms="+uiAge
                        +" state="+gate.state()+" stage="+lastStage+" ui_msg="+uiMessage+"\n"
                        +"main "+stackOf(mainThread,48)+"\n"
                        +"probe "+stackOf(thread,48)+"\n"
                        +"control "+stackOf(controlThread,48)+"\n"
                        +"af "+stackOf(focusThread,48)+"\n"
                        +"journal "+stackOf(journalThread,24)+"\n");
                try{Thread.sleep(2000);}catch(InterruptedException e){return;}
            }
        }},"recipe-heartbeat");
        watch.setDaemon(true);watch.start();
    }
    /** Recipe mode mirrors Recipe Lab: once the preview is live, the ordinary
     * camera takePicture() writes one JPEG with the recipe already baked in.
     * Do not call CameraEx.startDirectShutter() here. That vendor call arms
     * Sony's own release flow, which this app never completes for recipes; on
     * the a7R II the combination reset the camera at launch (empty journals in
     * SONYLUT/R0234F89 and R002A143). */
    private void startRecipeShutter(){
        try{
            stage("recipe_shutter_entry");
            if(!active||closed||gate.state()!=PreJpegGate.State.PREVIEW)return;
            gate.previewReady();
            event("recipe_ready","direct_shutter=false path=takePicture lut=false camera="+recipeCameraName+" recipe="+recipe.name);
            stage("recipe_ready");
            loadingStage("配方就绪",3);
            if(productHud!=null){
                productHud.name=Lang.recipeName(recipe);productHud.tag="配方";
                productHud.showLiveHint(recipeCameraName+" · "+Lang.recipeSummary(recipe));
                productLive();
            }
        }catch(Throwable e){failure("recipe_ready",e);}
    }
    private void initComplete(String scope){
        event("init_done","scope="+scope+" shutter_enabled=false");message("初始化验证完成\n未触发拍摄，正在退出测试。");
        ui.postDelayed(new Runnable(){public void run(){close();finish();}},1500);
    }
    private void configureSequence(){
        try{
            // The A6300 ScalarDaemon rejects RAW mode while electronic/super-
            // resolution zoom is enabled. Its JNI error cleanup then aborts.
            // Check before calling the vendor entry, including init-only tests.
            Camera.Parameters checked=camera.getParameters();event("raw_preconditions",zoomValues(checked));
            for(String key:ZOOM_KEYS)if(!"false".equals(checked.get(key)))throw new IOException("RAW requires disabled "+key);
            gate.registering();
            event("sequence_open_begin","");sequence=sequenceType.getMethod("open",cameraType).invoke(null,cameraEx);event("sequence_open_done","");
            Class<?> optionsType=Class.forName("com.sony.scalar.hardware.CameraSequence$Options");
            Class<?> callback=Class.forName("com.sony.scalar.hardware.CameraSequence$ShutterSequenceCallback");
            Object rawListener=listener(callback.getName(),new InvocationHandler(){public Object invoke(Object proxy,Method method,Object[] args){
                if(method.getDeclaringClass()==Object.class)return objectMethod(proxy,method,args);
                if(method.getName().equals("onShutterSequence"))receiveRaw(args[0]);return null;
            }});
            // Match ImagingAdapterImpl.prepare(): first default registration and
            // unlock. The device wrapper disables the old mode before the second
            // callback registration. Keep both calls visible in the SD journal.
            Object defaults=optionsType.newInstance();
            optionsType.getMethod("setOption",String.class,boolean.class).invoke(defaults,"AUTO_RELEASE_LOCK_ENABLED",true);
            event("default_register_begin","no memory map; automatic release lock; shutter disabled");
            sequenceType.getMethod("setShutterSequenceCallback",callback,optionsType).invoke(sequence,rawListener,defaults);
            gate.defaultRegistered();event("default_register_done","");
            event("default_unlock_begin","");sequenceType.getMethod("setReleaseLock",boolean.class).invoke(sequence,false);
            gate.defaultUnlocked();event("default_unlock_done","");
            if("defaults".equals(initStage)){gate.defaultsComplete();initComplete("default_registration");return;}
            gate.configuring();
            Object options=optionsType.newInstance();
            optionsType.getMethod("setOption",String.class,String.class).invoke(options,"MEMORY_MAP_FILE",mapPath);
            optionsType.getMethod("setOption",String.class,int.class).invoke(options,"EXPOSURE_COUNT",1);
            optionsType.getMethod("setOption",String.class,int.class).invoke(options,"RECORD_COUNT",paired()?2:1);
            optionsType.getMethod("setOption",String.class,boolean.class).invoke(options,"AUTO_RELEASE_LOCK_ENABLED",true);
            event("register_begin","two-stage map="+mapRevision+" exposure=1 record="+(paired()?2:1)+" automatic release lock");
            sequenceType.getMethod("setShutterSequenceCallback",callback,optionsType).invoke(sequence,rawListener,options);event("register_done","");
            event("configured_unlock_begin","");sequenceType.getMethod("setReleaseLock",boolean.class).invoke(sequence,false);event("configured_unlock_done","");
            if(extraFine()){
                int quality=camera.getParameters().getJpegQuality();event("quality_after_register","jpeg_quality="+quality);
                if(quality!=95)throw new IOException("RAW registration changed requested EXTRA FINE quality");
            }
            if(initOnly){gate.registered(true);initComplete("raw_registration");return;}
            if(develops())prepareFilter();
            if(previewIsolationProbe()){
                gate.registered(false);reapplyManualPreview();prepareIsolationPreview();return;
            }
            if(automaticNativePreview()&&automaticProfile!=null){
                gate.registered(false);reapplyManualPreview();prepareAutomaticPreview();return;
            }
            gate.registered(false);reapplyManualPreview();cameraType.getMethod("startDirectShutter").invoke(cameraEx);
            sequenceType.getMethod("setReleaseLock",boolean.class).invoke(sequence,false);
            event("ready","phase="+phase+" continuous="+continuous);message(paired()?(continuous?"按快门拍照 · 连续会话\n成片后半按返回取景，继续拍下一张。\n原片保留 · "+profile.label+" · EXTRA FINE":"编码前 LUT 单次测试\n请正常半按对焦、全按拍一张。\n保留原片，两张均为 EXTRA FINE。"):
                ("handoff".equals(phase)?"接口验证 1：RAW 交还相机保存":(extraFine()?"单张 EXTRA FINE 保存验证":"接口验证 2：索尼显影后直接保存"))+"\n请正常半按对焦、全按拍一张。\n本次不添加 LUT。");
            maybeOpenGallery();
        }catch(Throwable e){failure("configure_sequence",e);close();}
    }
    private void prepareIsolationPreview()throws Exception{
        // DirectShutter has never been started in this branch. Keep the native
        // release lock too; a Java key filter is not an exposure barrier.
        sequenceType.getMethod("setReleaseLock",boolean.class).invoke(sequence,true);
        CameraNativePreviewDriver driver=new CameraNativePreviewDriver(cameraEx,camera);previewDriver=driver;
        isolationPreview=new NativePreviewSession(driver);isolationPreview.open();
        java.io.InputStream in=getAssets().open("native-preview/IDENT.PRV");NativePreviewProfile profile;
        try{profile=NativePreviewProfile.read(in,selectedLut.id);}finally{in.close();}
        isolationBarrier=new PreviewCaptureBarrier(new PreviewCaptureBarrier.Driver(){
            public void restoreAndVerify()throws Exception{
                isolationPreview.restore();isolationPreview.requireBaseline();
                event("isolation_baseline_restored","direct_shutter=false native_lock=true quality="+camera.getParameters().getJpegQuality());
            }
            public void restartPreview()throws Exception{
                camera.stopPreview();event("isolation_preview_restart","direct_shutter=false native_lock=true");camera.startPreview();
            }
            public void enableCapture()throws Exception{
                isolationPreview.requireBaseline();
                cameraType.getMethod("startDirectShutter").invoke(cameraEx);
                sequenceType.getMethod("setReleaseLock",boolean.class).invoke(sequence,false);
            }
        });
        isolationPreview.apply(profile);event("isolation_preview_ready","direct_shutter=false native_lock=true profile="+profile.cubeSha256);
        message("隔离测试：近似预览 · 快门关闭\n中央键恢复原色并准备拍摄");
        if(productHud!=null){productHud.showPersistentLiveHint("隔离测试：快门关闭 · 中央键准备拍摄");productHud.update();}
    }
    private void prepareIsolationCapture(){
        if(isolationBarrier==null||isolationBarrier.state()!=PreviewCaptureBarrier.State.PREVIEW_ONLY)return;
        try{
            isolationBarrier.prepare();
            ui.postDelayed(new Runnable(){public void run(){
                if(!closed&&isolationBarrier.state()==PreviewCaptureBarrier.State.WAITING_PREVIEW){
                    isolationBarrier.fail();failure("isolation_preview_timeout",new IOException("native preview did not acknowledge restored baseline"));
                }
            }},transitionTimeout());
        }catch(Exception e){isolationBarrier.fail();failure("isolation_restore",e);}
    }
    private void watchAutomaticTransition(){
        if(autoPreview==null)return;
        final AutoPreviewGate owner=autoPreview;final long token=owner.generation();
        ui.postDelayed(new Runnable(){public void run(){if(active&&!closed&&autoPreview==owner)owner.timeout(token);}},transitionTimeout());
    }
    private void prepareAutomaticPreview()throws Exception{
        previewDriver=new CameraNativePreviewDriver(cameraEx,camera);
        isolationPreview=new NativePreviewSession(previewDriver);isolationPreview.open();
        final NativePreviewProfile profile=automaticProfile;
        if(profile==null)throw new IOException("preview profile not prepared");
        final Object owner=cameraEx;
        autoPreview=new AutoPreviewGate(new AutoPreviewGate.Driver(){
            public void lock()throws Exception{sequenceType.getMethod("setReleaseLock",boolean.class).invoke(sequence,true);}
            public void stopDirect(final Runnable done)throws Exception{
                final String type="com.sony.scalar.hardware.CameraEx$DirectShutterStoppedCallback";
                Object callback=listener(type,new InvocationHandler(){public Object invoke(Object p,Method m,Object[] args){
                    if(m.getDeclaringClass()==Object.class)return objectMethod(p,m,args);
                    ui.post(new Runnable(){public void run(){if(!active||closed||cameraEx!=owner)return;
                        event("auto_preview_shutter_stopped","");done.run();}});return null;
                }});
                event("auto_preview_stop_requested","");
                cameraType.getMethod("stopDirectShutter",Class.forName(type)).invoke(cameraEx,callback);
                watchAutomaticTransition();
            }
            public void apply()throws Exception{
                isolationPreview.apply(profile);event("auto_preview_applied","direct_shutter=false native_lock=true lut="+profile.cubeSha256);
            }
            public void restore()throws Exception{
                event("auto_preview_restore_begin","");isolationPreview.restore();isolationPreview.requireBaseline();
                event("auto_preview_restored","direct_shutter=false native_lock=true quality="+camera.getParameters().getJpegQuality());
            }
            public void restartPreview()throws Exception{
                camera.stopPreview();camera.startPreview();watchAutomaticTransition();
            }
            public void enable()throws Exception{
                isolationPreview.requireBaseline();
                cameraType.getMethod("startDirectShutter").invoke(cameraEx);
                sequenceType.getMethod("setReleaseLock",boolean.class).invoke(sequence,false);
                event("auto_preview_capture_ready","baseline_verified=true direct_shutter=true");
            }
        },new AutoPreviewGate.Listener(){
            public void colored(){
                if(gate.state()==PreJpegGate.State.REARMING){
                    completeRearm(gate.shot());
                    // a7r2.54: if S1 is still held after a shot, prepare the
                    // next direct-shutter cycle now. Without this the gate
                    // stayed in PREVIEW and every S2 was swallowed until the
                    // user released and pressed the half shutter again.
                    if(s1Held&&active&&!closed&&!destroyed&&!reviewing
                            &&autoPreview!=null&&autoPreview.state()==AutoPreviewGate.State.PREVIEW){
                        event("auto_half_rearm","s1_held=true");
                        autoPreview.press();
                    }
                }
                if(!reviewing&&productHud!=null)productLive();
                event("auto_preview_ready","state="+gate.state());maybeOpenGallery();
            }
            public void ready(){
                if(gate.state()==PreJpegGate.State.READY&&!reviewing&&productHud!=null){productHud.hideLiveHint();productLive();}
                // a7r2.52: the full press can arrive while the gate is still
                // WAITING. S1/S2 are consumed by the gate in that window, so
                // the half-press guard never ran; apply it as soon as the
                // direct shutter is ready. UI thread: never block the gate.
                if(gate.state()==PreJpegGate.State.READY)ui.post(new Runnable(){public void run(){
                    ensureManualCaptureGuard("auto_preview_ready");
                    startLutFocus("auto_preview_ready");
                }});
            }
            public void failed(Exception error){failure("automatic_preview",error);}
        });
        autoPreview.start();
    }
    private void registerCameraListeners()throws Exception{
        // a7r2.45: a recipe finder registers NO CameraEx listener. Recipe Lab,
        // the only recipe implementation proven on this body, never registers
        // one either: it starts the preview and accepts the shutter directly.
        // Our listeners were the remaining main-thread callback source, and
        // R002BDF0 froze the UI thread exactly when a vendor callback was
        // delivered around startPreview(). The store receipt is replaced by a
        // bounded DCIM scan on the probe worker (scanRecipeStore).
        if(recipeMode()){event("camera_listeners_skipped","reason=recipe_callback_free");return;}
        final Object owner=cameraEx;
        for(final String kind:new String[]{"ShutterListener","StoreImageCompleteListener","PreviewStartListener"}){
            final String name="com.sony.scalar.hardware.CameraEx$"+kind;
            Object value=listener(name,new InvocationHandler(){public Object invoke(Object proxy,Method method,Object[] args){
                if(method.getDeclaringClass()==Object.class)return objectMethod(proxy,method,args);
                if(closed||cameraEx!=owner)return null;
                if(kind.equals("ShutterListener")){
                    int status=((Integer)args[0]).intValue();
                    // CameraEx.ShutterListener: OK=0, CANCELED=1, ERROR=2.
                    // A recipe JPEG is initiated by our own takePicture(), not
                    // by Sony's direct shutter. The callback can fire while the
                    // store is pending; arming a second time or canceling the
                    // gate from it wedges the capture.
                    if(recipeMode()){
                        event("recipe_shutter_callback_ignored","status="+status+" state="+gate.state());
                        return null;
                    }
                    event("shutter","status="+status);
                    if(status==0)arm("shutter_callback");
                    else if(status==1)cancelShutter();
                    else failure("shutter",new IOException("status "+status));
                }else if(kind.equals("StoreImageCompleteListener")){
                    try{
                        final String directory=(String)args[1].getClass().getField("DirectoryName").get(args[1]);
                        final String file=(String)args[1].getClass().getField("FileName").get(args[1]);
                        final long shot=gate.shot();
                        final long at=SystemClock.elapsedRealtime();event("stored_notification","value="+args[0]+" dir="+directory+" file="+file+" state="+gate.state());
                        stage("store_notified "+file);
                        work.post(new Runnable(){public void run(){
                            if(closed||shot!=gate.shot()){event("stale_store_ignored","event_shot="+shot+" file="+file);return;}
                            try{if(photoFiles.knownStore(directory,file)){event("duplicate_store_ignored","file="+file);return;}}
                            catch(IOException e){failure("store_path",e);return;}
                            verifyStored(directory,file,at);
                        }});
                    }catch(Throwable e){failure("store_notification",e);}
                }else onPreviewStarted();return null;
            }});
            cameraType.getMethod("set"+kind,Class.forName(name)).invoke(cameraEx,value);
        }
    }
    private void cancelShutter(){
        if(gate.state()!=PreJpegGate.State.ARMED){
            event("shutter_cancel_ignored","state="+gate.state());return;
        }
        if(recipeMode()){
            try{
                if(!gate.cancelShutter())return;
                endProcessing();reviewing=false;clearOriginalReview();
                review.setImageBitmap(null);review.setVisibility(View.INVISIBLE);
                if(active)review.showGraphicsOutput();
                if(productHud!=null){status.setVisibility(View.GONE);productLive();
                    productHud.detail="拍摄已取消 · 可重新半按对焦后拍摄";productHud.showLiveHint(productHud.detail);productHud.update();}
                if(manual!=null){manual.releaseCaptureGuard();reassertExposurePreview();}
                event("recipe_shutter_canceled","ready=true");
                maybeOpenGallery();
            }catch(Throwable e){failure("recipe_cancel_recovery",e);}
            return;
        }
        try{
            // Match Sony ImagingAdapterImpl.enableNextCapture: release the
            // automatic shutter lock after cancellation, retaining the filter.
            if(autoPreview==null)sequenceType.getMethod("setReleaseLock",boolean.class).invoke(sequence,false);
            if(!gate.cancelShutter())return;
            endProcessing();reviewing=false;clearOriginalReview();
            review.setImageBitmap(null);review.setVisibility(View.INVISIBLE);
            if(active)review.showGraphicsOutput();
            if(productHud!=null){status.setVisibility(View.GONE);productLive();
                productHud.detail="拍摄已取消 · 可重新半按对焦后拍摄";productHud.showLiveHint(productHud.detail);productHud.update();}
            else{status.setText("拍摄已取消 · 可重新拍摄");status.setVisibility(View.VISIBLE);}
            event("shutter_canceled","ready=true filter_preserved=true");
            if(autoPreview!=null){
                // If Sony rejected the re-armed shutter while the approximate
                // matrix was applied, fall back to the proven baseline-on-
                // half-press flow for the rest of this session.
                if(autoPreview.keepColorOnHalfPress()){
                    autoPreview.keepColorOnHalfPress(false);
                    event("half_color_disabled","reason=shutter_canceled");
                }
                autoPreview.shotCanceled();
            }
            if(manual!=null){manual.releaseCaptureGuard();reassertExposurePreview();}
            maybeOpenGallery();
        }catch(Throwable e){failure("cancel_recovery",e);}
    }
    private void arm(String origin){
        if(recipeMode()){armRecipe(origin);return;}
        if(gate.state()==PreJpegGate.State.READY&&autoPreview!=null
                &&autoPreview.state()!=AutoPreviewGate.State.READY){
            autoPreview.fail(new IOException("shutter before preview baseline barrier: "+origin));return;
        }
        // Finish any queued exposure step before Sony starts the RAW exposure.
        awaitManualControls();
        if(manual!=null)try{event("manual_pre_shot",manual.captureState());}
        catch(Throwable e){event("manual_pre_shot_error",cause(e).toString());}
        // a7r2.53: the finder kept the approximate matrix during half press.
        // Return to the colour baseline before this S2 is forwarded to Sony.
        // captureBaseline() requires READY, so it must run before shotStarted().
        if(autoPreview!=null&&autoPreview.ready())autoPreview.captureBaseline();
        if(autoPreview!=null&&autoPreview.state()==AutoPreviewGate.State.FAILED)return;
        if(gate.state()==PreJpegGate.State.READY&&autoPreview!=null&&!autoPreview.shotStarted()){
            autoPreview.fail(new IOException("shutter before preview baseline barrier: "+origin));return;
        }
        if(gate.arm()){
        if(af!=null&&af.active())try{af.confirm();if(productHud!=null)productHud.setAf(true,false,af.x(),af.y());}catch(Throwable e){event("af_confirm_error",cause(e).toString());}
        final long shot=gate.shot();processingProgress.begin(shot);pressAt=SystemClock.elapsedRealtime();timingOrigin=origin;journalAtPress=journalWriteMs();
        originalFile=null;gradedFile=null;displayedAt=0;nextReadyAt=0;awaitFirstFrame=false;lutApplied=false;
        overlapThisShot=overlapOriginal&&(!alternateOverlap||shot%2==0);
        event("armed","origin="+origin+" lut_cache_reused="+(shot>1)+" overlap_original="+overlapThisShot);
        ui.post(new Runnable(){public void run(){if(shot==gate.shot()){clearOriginalReview();reviewing=false;review.setVisibility(View.INVISIBLE);review.setImageBitmap(null);}}});
        developEnabledThisShot=productUi()&&getIntent().getBooleanExtra("develop_animation",true)
                &&(!getIntent().getBooleanExtra("develop_animation_alternate",false)||shot%2==1);
        if(productHud!=null){productHud.hideLiveHint();productHud.beginDeveloping(shot,developEnabledThisShot);}
        event("develop_animation_mode","enabled="+developEnabledThisShot);
        processing(CaptureProgress.Stage.CAPTURE,"正在拍摄");
        ui.postDelayed(new Runnable(){public void run(){if(gate.busyShot(shot)){
            endProcessing();
            if(active&&review!=null)try{review.showGraphicsOutput();}catch(Exception e){android.util.Log.e("SonyPreJpeg","timeout_graphics_restore_failed",e);}
            event("timeout","state="+gate.state()+" limit_ms="+profile.timeoutMs);message("接口等待超过 "+(profile.timeoutMs/1000)+" 秒\n请保留当前页面，读取日志。");
        }}},profile.timeoutMs);
    }}
    /** One recipe frame: arm the save gate, show the shutter/processing page,
     * and let Sony's completed-store callback deliver the JPEG path. */
    private void armRecipe(final String origin){
        if(!active||closed)return;
        stage("arm_begin "+origin);
        // Recipe dial writes are cosmetic for a camera-native JPEG. Do not
        // block the UI thread on the full 2.5 s LUT barrier before every shot:
        // a slow vendor control call made the finder look frozen when the user
        // pressed the shutter repeatedly.
        awaitManualControls(600);
        // A C3 film switch writes the same parameter set on the control
        // thread. Never start takePicture while that vendor transaction is
        // still in flight; the user can simply press the shutter again.
        boolean blocked=false;
        synchronized(controlLock){
            if(controlClosed||!active||closed)return;
            if(recipeApplyBusy||filmApplyInProgress)blocked=true;
            else if(!gate.arm())return;
        }
        if(blocked){
            event("recipe_arm_blocked","reason=film_apply_busy origin="+origin);
            stage("arm_blocked film_apply_busy");
            filmApplyPendingId=recipeId;
            if(productHud!=null){productHud.showLiveHint("胶片切换中 · 请再按快门");productHud.update();}
            return;
        }
        if(manual!=null)try{event("recipe_pre_shot",manual.captureState());}
        catch(Throwable e){event("recipe_pre_shot_error",cause(e).toString());}
        stage("armed");
        if(af!=null&&af.active())try{af.confirm();if(productHud!=null)productHud.setAf(true,false,af.x(),af.y());}catch(Throwable e){event("af_confirm_error",cause(e).toString());}
        final long shot=gate.shot();processingProgress.begin(shot);pressAt=SystemClock.elapsedRealtime();recipePressWall=System.currentTimeMillis();timingOrigin=origin;journalAtPress=journalWriteMs();
        recipeFile=null;displayedAt=0;nextReadyAt=0;awaitFirstFrame=false;
        event("recipe_armed","origin="+origin+" recipe="+recipe.name+" camera="+recipeCameraName+" lut=false");
        ui.post(new Runnable(){public void run(){if(shot==gate.shot()){reviewing=false;review.setVisibility(View.INVISIBLE);review.setImageBitmap(null);}}});
        developEnabledThisShot=false;
        // Recipe capture stays on the live finder: a shutter curtain and a
        // small write-time chip. The LUT darkroom page (safelight, negative,
        // develop stages) belongs to the two-file LUT path and is not part of
        // a camera-native JPEG.
        if(productHud!=null){productHud.hideLiveHint();productHud.beginRecipeCapture(shot);}
        final int limit=Math.min(profile.timeoutMs,20000);
        // Schedule recovery before starting the vendor capture. If the camera
        // service ever blocks inside takePicture(), the UI thread must still be
        // able to time out and return the finder instead of freezing.
        ui.postDelayed(new Runnable(){public void run(){
            if(!gate.busyShot(shot))return;
            if(gate.state()==PreJpegGate.State.ARMED){
                // A wedged capture is the one case where releasing the vendor
                // trigger helps. Run it on the focus looper: the worker may
                // still be blocked inside takePicture(), and the UI must not
                // be the thread that waits for Sony.
                cancelRecipeTakePicture();
                if(gate.cancelShutter()){
                    recoverRecipeLive("拍摄超时 · 可再次按下快门");
                    event("recipe_timeout_recovered","state=READY limit_ms="+limit+" origin="+origin);
                    return;
                }
            }
            // The scan already found the file and the worker is verifying it,
            // or Sony is still writing. Keep the live finder and show the
            // slower-write hint; never swap back to the darkroom page.
            if(productHud!=null)productHud.setRecipeCaptureText("相机写入较慢 · 请稍候");
            event("recipe_timeout_soft","state="+gate.state()+" limit_ms="+limit+" origin="+origin);
        }},limit);
        // Recipe Lab's own capture path: ask the camera for a normal JPEG with
        // the already-applied look. Sony's StoreImageComplete names the file.
        // Run it on the worker looper, never the UI thread.
        work.post(new Runnable(){public void run(){
            if(closed||gate.shot()!=shot||gate.state()!=PreJpegGate.State.ARMED){
                event("recipe_take_picture_skipped","state="+gate.state()+" stale="+(gate.shot()!=shot));
                stage("take_picture_skipped "+gate.state());
                return;
            }
            final Camera target=camera;
            if(target==null){event("recipe_take_picture_skipped","camera_null");stage("take_picture_skipped camera_null");return;}
            final long callBegin=SystemClock.elapsedRealtime();
            try{
                stage("take_picture_begin");
                // a7r2.54: do not enter Sony's still JPEG transaction while a
                // recipe S1 autoFocus is still driving. The native sequence
                // expects S1 to be released or its focus request to be idle;
                // an in-flight request made takePicture() return in ~16 ms
                // without writing a file (R00805E1).
                try{target.cancelAutoFocus();event("recipe_focus_cancel","before_take_picture");}
                catch(Throwable focusError){event("recipe_focus_cancel_error",focusError.toString());}
                target.takePicture(null,null,null);
                event("recipe_take_picture","call_ms="+(SystemClock.elapsedRealtime()-callBegin)
                        +" state="+gate.state()+" thread="+Thread.currentThread().getName());
                stage("take_picture_returned ms="+(SystemClock.elapsedRealtime()-callBegin));
                // Recipe mode registers no StoreImageComplete listener, so the
                // JPEG is discovered in DCIM. Start after the camera's normal
                // write latency and retry on the worker up to nine seconds.
                work.postDelayed(new Runnable(){public void run(){scanRecipeStore(shot,callBegin);}},RECIPE_SCAN_FIRST_MS);
            }catch(final Throwable e){
                event("recipe_take_picture_error",cause(e).toString());
                stage("take_picture_error "+cause(e));
                ui.post(new Runnable(){public void run(){
                    if(gate.shot()!=shot)return;
                    if(gate.cancelShutter())recoverRecipeLive("无法启动拍摄");
                    else if(gate.state()!=PreJpegGate.State.READY)failure("recipe_take_picture",e);
                }});
            }
        }});
    }
    /** Return the finder to a usable state without releasing the camera. The
     * old timeout left the gate ARMED, close() refused to run and the app could
     * only be recovered by pulling the battery. */
    private void recoverRecipeLive(String detail){
        endProcessing();reviewing=false;clearOriginalReview();
        if(review!=null){review.setImageBitmap(null);review.setVisibility(View.INVISIBLE);}
        if(active&&review!=null)try{review.showGraphicsOutput();}catch(Exception e){event("recipe_recovery_graphics_error",cause(e).toString());}
        if(manual!=null){manual.releaseCaptureGuard();reassertExposurePreview();}
        // A failed or timed-out bare takePicture can leave Sony's trigger
        // latched and the preview stopped. Release both before the finder
        // accepts another press.
        postRecipePreviewRestart("recover_live");
        if(productHud!=null){
            status.setVisibility(View.GONE);productLive();
            productHud.detail=detail;productHud.showLiveHint(detail);productHud.update();
        }
        reapplyPendingFilm();
    }
    private void receiveRaw(final Object raw){
        try{gate.raw();rawAt=SystemClock.elapsedRealtime();event("raw_received","from_press_ms="+(rawAt-pressAt));workerActive=true;
            processing(develops()?CaptureProgress.Stage.DEVELOP:CaptureProgress.Stage.SAVE,develops()?"正在显影":"正在保存原片");
            // RAW delivery confirms exposure is complete. Follow Sony's
            // progress-layout protocol: draw first, restore graphics 300ms later.
            ui.post(new Runnable(){public void run(){ui.removeCallbacks(restoreGraphics);ui.postDelayed(restoreGraphics,300);}});
            work.post(new Runnable(){public void run(){process(raw);}});
        }catch(Throwable e){
            // A rejected callback still transfers RAW ownership to us. Release
            // it so camera teardown cannot wait forever for an orphaned buffer.
            release(raw);event("unexpected_raw",cause(e).toString());
            if(gate.state()!=PreJpegGate.State.FAILED)failure("raw_state",e);
        }
    }
    private void process(Object raw){Object image=null;boolean rawTransferred=false,imageTransferred=false;
        try{
            if(develops()){
                Object f=filter;event("develop_begin","release_source=true");long begin=SystemClock.elapsedRealtime();
                f.getClass().getMethod("setSource",rawType,boolean.class).invoke(f,raw,true);
                rawTransferred=true;
                if(!((Boolean)f.getClass().getMethod("execute").invoke(f)).booleanValue())throw new IOException("develop returned false");
                image=f.getClass().getMethod("getOutput").invoke(f);if(image==null)throw new IOException("develop returned no image");
                event("develop_done","ms="+(SystemClock.elapsedRealtime()-begin)+" raw_valid="+rawType.getMethod("isValid").invoke(raw));
                f.getClass().getMethod("release").invoke(f);filter=null;
                int w=((Integer)imageType.getMethod("getWidth").invoke(image)).intValue(),h=((Integer)imageType.getMethod("getHeight").invoke(image)).intValue();
                int format=((Integer)imageType.getMethod("getImageFormat").invoke(image)).intValue();
                event("image","width="+w+" height="+h+" format="+format+" "+Native.deviceInfo(image));
                if(w!=profile.width||h!=profile.height||format!=0)throw new IOException("unexpected developed image contract "+w+"x"+h+"/"+format+" expected "+profile.sizeText());
                if(sourceReference!=null){
                    long referenceAt=SystemClock.elapsedRealtime();Native.devicePreview(image,sourceReference,uvSwap);
                    event("source_reference_snapshot","ms="+(SystemClock.elapsedRealtime()-referenceAt)+" source=developed_before_original_store sample=750x500_nearest color="+pixelSummary(sourceReference));
                    // Diagnostic PNGs are written only in the explicit
                    // source_reference flow. Product captures must never block
                    // on multi-megabyte SD writes while Sony waits for RAW.
                    publishDevelopPreview(false);
                }
                if(paired()){
                    gate.savingOriginal();retainedImage=image;storeAt=SystemClock.elapsedRealtime();
                    // Original preservation prepares grading; only the graded
                    // file's store is the final SAVE milestone in the product UI.
                    processing(CaptureProgress.Stage.GRADE,productUi()?"正在准备调色":"正在保存原片");
                    event("store_begin","type=YC role=original release_source=false");
                    sequenceType.getMethod("storeImage",imageType,boolean.class).invoke(sequence,image,false);
                }else{
                    gate.saving();storeAt=SystemClock.elapsedRealtime();event("store_begin","type=YC release_source=true");imageTransferred=true;
                    processing(CaptureProgress.Stage.SAVE,"正在保存原片");
                    sequenceType.getMethod("storeImage",imageType,boolean.class).invoke(sequence,image,true);
                }
            }else{
                gate.saving();storeAt=SystemClock.elapsedRealtime();event("store_begin","type=RAW release_source=true");rawTransferred=true;
                sequenceType.getMethod("storeImage",rawType,boolean.class).invoke(sequence,raw,true);
            }
            event("store_accepted","call_ms="+(SystemClock.elapsedRealtime()-storeAt));
            // Explicit experiment: CreateStream replies from CaptureDevExit
            // after encode/sort. The original completed-file event still gates
            // the second store. Never issue concurrent stores on this sequence.
            if(paired()&&overlapThisShot)applyRetainedLut("store_return_before_file_confirmation");
        }catch(Throwable e){failure("process",e);}
        finally{
            if(!imageTransferred&&image!=retainedImage)release(image);if(!rawTransferred)release(raw);workerActive=false;
            if(!active&&!gate.busy())ui.post(new Runnable(){public void run(){close();}});
        }
    }
    private void verifyStored(String directory,String name,long notifiedAt){
        if(recipeMode()){verifyRecipeStore(gate.shot(),directory,name,notifiedAt);return;}
        if(paired()){verifyPairStore(directory,name,notifiedAt);return;}
        try{
        if(gate.state()!=PreJpegGate.State.SAVING)throw new IOException("unexpected store event in "+gate.state());
        photoFiles.confirmStored(directory,name);File file=photoFiles.pollReady(true);
        String expected="handoff".equals(phase)?"ARW":"JPEG";
        if(file==null)throw new IOException("new "+expected+" file not visible: "+directory+name);
        event("verify_begin","expected="+expected+" file="+file+" bytes="+file.length());
        if(!photoFiles.complete(file))throw new IOException("saved "+expected+" structure or payload incomplete: "+file);
        long bytes=file.length();photoFiles.accepted(file);
        gate.saved();long total=SystemClock.elapsedRealtime()-pressAt;
        event("done","phase="+phase+" timing_origin="+timingOrigin+" total_ms="+total+" store_notification_ms="+(notifiedAt-pressAt)+" raw_ms="+(rawAt-pressAt)+" store_ms="+(notifiedAt-storeAt)+" bytes="+bytes+" file="+file);
        ui.post(new Runnable(){public void run(){endProcessing();}});
        message("验证成功："+expected+" 原片已保存\n保存 "+((notifiedAt-pressAt)/1000.0)+" 秒；核验 "+(total/1000.0)+" 秒\n请保留此页，读取测试结果。");
        // Keep the one-shot release lock until this probe exits; no second shot.
        if(!active)ui.post(new Runnable(){public void run(){close();}});
    }catch(Throwable e){failure("verify",e);}}
    /** Every DCIM JPEG path currently on the card, for the pre-shot baseline. */
    private static java.util.Set<String> listRecipeJpegs(File sd)throws IOException{
        java.util.Set<String> paths=new java.util.HashSet<String>();
        File[] dirs=new File(sd,"DCIM").listFiles();
        if(dirs==null)throw new IOException("cannot read DCIM");
        for(File dir:dirs){
            if(!dir.isDirectory())continue;
            File[] files=dir.listFiles();if(files==null)continue;
            for(File f:files){
                if(f.getName().toUpperCase(java.util.Locale.US).matches("[A-Z0-9_]{1,8}\\.JPG"))
                    paths.add(f.getAbsolutePath());
            }
        }
        return paths;
    }
    /** Newest JPEG in DCIM that was not present before the shot. Falls back to
     * the press-time window when the baseline could not be read. */
    private File findRecipeStoreCandidate(){
        java.util.Set<String> baseline=recipeStoreBaseline;
        File found=null;
        try{
            File[] dirs=new File(sd,"DCIM").listFiles();
            if(dirs==null)throw new IOException("cannot read DCIM");
            long newest=0;
            for(File dir:dirs){
                if(!dir.isDirectory())continue;
                File[] files=dir.listFiles();if(files==null)continue;
                for(File f:files){
                    String name=f.getName().toUpperCase(java.util.Locale.US);
                    if(!name.matches("[A-Z0-9_]{1,8}\\.JPG"))continue;
                    if(baseline!=null){
                        if(baseline.contains(f.getAbsolutePath()))continue;
                    }else{
                        long modified=f.lastModified();
                        if(modified<recipePressWall-4000L||modified>System.currentTimeMillis()+60000L)continue;
                    }
                    if(!PhotoFiles.jpegHeader(f,profile.width,profile.height))continue;
                    long modified=f.lastModified();
                    if(found==null||modified>newest){newest=modified;found=f;}
                }
            }
        }catch(Throwable e){
            event("recipe_store_scan_error",cause(e).toString());
            stage("store_scan_error "+cause(e));
        }
        return found;
    }
    /** Discovery for a Camera.takePicture capture whose store notification
     * never arrives (recipe mode registers no Sony listener). Runs only while
     * the gate is ARMED, on the worker thread. The candidate must be absent
     * from the pre-shot baseline, keep a stable size for one beat (the camera
     * may still be appending entropy data), and carry a valid JPEG header. */
    private void scanRecipeStore(final long shot,final long startedAt){
        if(closed||gate.shot()!=shot||gate.state()!=PreJpegGate.State.ARMED)return;
        stage("store_scan_begin");
        final File found=findRecipeStoreCandidate();
        if(found==null){
            long waited=SystemClock.elapsedRealtime()-startedAt;
            if(waited<RECIPE_SCAN_TOTAL_MS){
                event("recipe_store_scan_retry","waited_ms="+waited);
                work.postDelayed(new Runnable(){public void run(){scanRecipeStore(shot,startedAt);}},RECIPE_SCAN_RETRY_MS);
            }else{
                event("recipe_store_scan_miss","waited_ms="+waited+" state="+gate.state());
                stage("store_scan_miss");
                // Keep any late file out of the next shot's candidate set.
                try{recipeStoreBaseline=listRecipeJpegs(sd);}catch(Throwable ignored){}
            }
            return;
        }
        final long size=found.length();
        stage("store_scan_candidate "+found.getName()+" bytes="+size);
        work.postDelayed(new Runnable(){public void run(){
            if(closed||gate.shot()!=shot||gate.state()!=PreJpegGate.State.ARMED)return;
            try{
                long stable=found.length();
                if(stable!=size){event("recipe_store_scan_growing","bytes="+stable+" was="+size);scanRecipeStore(shot,startedAt);return;}
                event("recipe_store_scan_found","file="+found+" bytes="+stable
                        +" waited_ms="+(SystemClock.elapsedRealtime()-startedAt)+" state="+gate.state());
                stage("store_scan_found "+found.getName());
                File dir=found.getParentFile();
                verifyRecipeStore(shot,dir.getParentFile().getName()+"/"+dir.getName(),
                        found.getName(),SystemClock.elapsedRealtime());
            }catch(Throwable e){failure("recipe_store_scan",e);}
        }},RECIPE_SCAN_STABLE_MS);
    }
    private void verifyRecipeStore(final long shot,String directory,String name,long notifiedAt){workerActive=true;try{
        stage("store_verify "+name);
        // A late store can arrive after the timeout returned the gate to
        // READY. Keep the file and its catalog entry instead of failing, but
        // only move a gate that still belongs to this shot.
        boolean late=gate.state()==PreJpegGate.State.READY;
        if(!late&&(gate.state()!=PreJpegGate.State.ARMED||gate.shot()!=shot))
            throw new IOException("unexpected recipe store in "+gate.state()+" shot="+gate.shot()+" want="+shot);
        long checkBegin=SystemClock.elapsedRealtime();
        photoFiles.confirmStored(directory,name);File file=photoFiles.pollReady(true);
        if(file==null||!PhotoFiles.jpegHeader(file,profile.width,profile.height))
            throw new IOException("completed recipe JPEG header missing or invalid: "+directory+name);
        stage("store_header_ok "+name);
        long checkedAt=SystemClock.elapsedRealtime(),savedAt=SystemClock.elapsedRealtime();
        photoFiles.accepted(file);recipeFile=file;
        java.util.Set<String> baseline=recipeStoreBaseline;
        if(baseline!=null)baseline.add(file.getAbsolutePath());
        long display=Math.max(0,notifiedAt-pressAt),ready=Math.max(0,savedAt-pressAt);
        if(!late){
            if(!gate.recipeStoring(shot))throw new IOException("stale recipe store shot "+shot+" gate="+gate.shot());
            if(!gate.recipeReady(shot))throw new IOException("recipe store unlock failed for shot "+shot);
        }
        event("recipe_saved","file="+file+" bytes="+file.length()+" from_press_ms="+(notifiedAt-pressAt)
                +" notification_to_check_ms="+(checkBegin-notifiedAt)+" header_ms="+(checkedAt-checkBegin)
                +" late="+late+" shot="+shot+" recipe="+recipe.name+" camera="+recipeCameraName+" lut=false");
        recordRecipe(shot,file,display,ready);
        // a7r2.48: the recipe capture is a bare Camera.takePicture(null,null,null).
        // On this body the vendor trigger and the preview stay latched after the
        // first frame unless the shutter release cancels the trigger. A second
        // takePicture() then fails immediately ("takePicture failed") for every
        // later press. Recipe Lab and ShotDirector both pair S2-down takePicture
        // with S2-up cancelTakePicture; do the same here and restart the preview.
        // The gate is already READY, but any next takePicture() is posted to
        // this same worker, so it stays queued until the trigger and live view
        // have been restored. A late receipt must not cancel a newer shot that
        // has already armed while this one was being catalogued.
        if(gate.shot()==shot)restartRecipePreview("store_verified");
        else event("recipe_preview_restart_skipped","shot="+shot+" gate_shot="+gate.shot()+" state="+gate.state());
        ui.post(new Runnable(){public void run(){
            if(!active||destroyed){close();return;}
            // A late receipt may finish after the next capture has already
            // armed. Never let the previous shot's UI update tear down the
            // current shot's live capture chip or claim the next gate.
            if(gate.shot()!=shot||gate.state()!=PreJpegGate.State.READY)return;
            endProcessing();
            if(productHud!=null){productLive();productHud.showLiveHint("已保存 · "+Lang.recipeName(recipe)+" · 可直接使用");productHud.update();}
            reapplyPendingFilm();
            maybeOpenGallery();
        }});
    }catch(Throwable e){failure("recipe_store",e);}
    finally{workerActive=false;if(!active&&!gate.busy())ui.post(new Runnable(){public void run(){close();}});}}
    /** Catalog and contact print for the single camera-native JPEG. The file
     * already exists; this only records it for the app's gallery. */
    private void recordRecipe(final long shot,final File file,final long display,final long ready){
        if(recipe==null||file==null||recordedRecipeShot==shot)return;
        recordedRecipeShot=shot;
        final String name=Lang.recipeName(recipe),camera=recipeCameraName;pendingReceipts++;
        // Cataloguing decodes the 42 MP JPEG for a contact print. Keep it off
        // the capture worker: takePicture() and the DCIM scan are queued on
        // that worker, and a slow thumbnail made later shots start late while
        // the gate still showed the processing page.
        Runnable receipt=new Runnable(){public void run(){
            android.graphics.Bitmap thumb=null;
            try{
                PhotoCatalog.Pair pair=new PhotoCatalog(ProductFiles.photos(),sd).saveSingle(file,name,camera,display,ready,ReviewLoader.capturedAt(file));
                event("recipe_catalogued","id="+pair.id+" file="+file+" camera="+camera);
                thumb=PhotoThumbs.decodeSampled(file,PhotoThumbs.W,PhotoThumbs.H);
                if(thumb!=null)event("thumb_saved","id="+pair.id+" saved="+PhotoThumbs.save(ProductFiles.photos(),pair.id,thumb));
                else event("thumb_unavailable","id="+pair.id);
            }catch(final IOException e){
                event("catalog_failed",e.toString());
                ui.post(new Runnable(){public void run(){if(!destroyed&&productHud!=null){productHud.detail="照片已保存在 DCIM；回看记录保存失败";productHud.update();}}});
            }finally{
                if(thumb!=null)thumb.recycle();
                ui.post(new Runnable(){public void run(){pendingReceipts--;maybeOpenGallery();exitProductProcess();}});
            }
        }};
        Handler handler=receiptHandler;
        if(handler!=null)handler.post(receipt);else work.post(receipt);
    }
    private void verifyPairStore(String directory,String name,long notifiedAt){workerActive=true;try{
        boolean original=gate.state()==PreJpegGate.State.ORIGINAL_SAVING;
        if(!original&&gate.state()!=PreJpegGate.State.SAVING)throw new IOException("unexpected pair store in "+gate.state());
        long checkBegin=SystemClock.elapsedRealtime();
        photoFiles.confirmStored(directory,name);File file=photoFiles.pollReady(true);
        long headerBegin=SystemClock.elapsedRealtime();
        if(file==null||!PhotoFiles.jpegHeader(file,profile.width,profile.height))throw new IOException("completed pair JPEG header missing or invalid: "+directory+name);
        long checkedAt=SystemClock.elapsedRealtime();
        photoFiles.accepted(file);
        event(original?"original_saved":"graded_saved","file="+file+" bytes="+file.length()+" from_press_ms="+(notifiedAt-pressAt)+" store_ms="+(notifiedAt-storeAt)
                +" notification_to_check_ms="+(checkBegin-notifiedAt)+" file_lookup_ms="+(headerBegin-checkBegin)+" header_ms="+(checkedAt-headerBegin));
        if(original){
            originalFile=file;gate.originalSaved();
            if(!lutApplied)applyRetainedLut("original_file_confirmed");
            gate.savingGraded();storeAt=SystemClock.elapsedRealtime();event("store_begin","type=YC role=graded release_source=true");
            processing(CaptureProgress.Stage.SAVE,"正在保存成片");
            Object image=retainedImage;retainedImage=null;
            sequenceType.getMethod("storeImage",imageType,boolean.class).invoke(sequence,image,true);
            event("store_accepted","role=graded call_ms="+(SystemClock.elapsedRealtime()-storeAt));
        }else{
            if(originalFile==null||originalFile.equals(file))throw new IOException("graded save did not preserve distinct original");
            gradedFile=file;gate.saved();event("pair_saved","shutter_to_saved_ms="+(notifiedAt-pressAt));
            ui.post(new Runnable(){public void run(){
                if(!active||destroyed){close();return;}
                try{endProcessing();long start=SystemClock.elapsedRealtime();camera.stopPreview();long stopped=SystemClock.elapsedRealtime();
                    review.setImageBitmap(lutReview);reviewing=true;awaitFirstFrame=true;review.setVisibility(View.VISIBLE);review.invalidate();
                    android.util.Log.i("SonyPreJpeg","review_setup stop_preview_ms="+(stopped-start)+" view_ms="+(SystemClock.elapsedRealtime()-stopped));
                }
                catch(Throwable e){failure("show_review",e);}
            }});
            try{event("jpeg_exif","original["+exifSummary(originalFile)+"] graded["+exifSummary(gradedFile)+"]");}
            catch(Throwable e){event("jpeg_exif_error",cause(e).toString());}
            // Keep the full-JPEG color audit out of the normal product path
            // (two 20MP decodes); it runs only for the explicit diagnostic.
            if(sourceReferenceEnabled&&!previewColorAudited){previewColorAudited=true;try{
                auditPreviewColor(originalFile,gradedFile);
            }catch(Throwable e){event("preview_color_audit_error",cause(e).toString());}}
        }
    }catch(Throwable e){failure("pair_store",e);}
    finally{workerActive=false;if(!active&&!gate.busy())ui.post(new Runnable(){public void run(){close();}});}}
    private void applyRetainedLut(String trigger)throws Exception{
        if(lutApplied)throw new IllegalStateException("LUT applied twice");
        if(retainedImage==null||!((Boolean)imageType.getMethod("isValid").invoke(retainedImage)).booleanValue())
            throw new IOException("retained YC invalid before LUT");
        event("lut_begin","trigger="+trigger+" original_confirmed="+(originalFile!=null)+" no_jpeg_decode=true");
        long start=SystemClock.elapsedRealtime();
        int skin=Math.max(0,Math.min(2,getPreferences(0).getInt("beauty_skin",0)));
        int denoise=Math.max(0,Math.min(2,getPreferences(0).getInt("beauty_denoise",0)));
        beautySkin=skin;beautyDenoise=denoise;
        processing(CaptureProgress.Stage.GRADE,
                (skin>0||denoise>0)?"正在冲洗照片 · 美肤/降噪":"正在冲洗照片");
        String stats=(skin>0||denoise>0)
                ?Native.deviceLutReviewBeauty(retainedImage,lutCache,lutReview,uvSwap,skin,denoise)
                :Native.deviceLutReview(retainedImage,lutCache,lutReview,uvSwap);
        lutApplied=true;
        event("beauty_settings","skin="+skin+" denoise="+denoise+" applied="+(skin>0||denoise>0));
        event("lut_done","wall_ms="+(SystemClock.elapsedRealtime()-start)+" color="+pixelSummary(lutReview)+" "+stats);
        publishDevelopPreview(true);
    }
    // Runs only after the native preview writer has completed, on the same
    // worker. UI receives an owned copy; it never races native buffer reuse.
    private void publishDevelopPreview(final boolean graded){
        if(!developEnabledThisShot)return;
        final long shot=gate.shot();long start=SystemClock.elapsedRealtime();
        DevelopingPreview.Source ownedSource=null;Bitmap ownedBitmap=null;boolean posted=false;
        try{
            if(graded)ownedBitmap=DevelopingPreview.thumbnail(lutReview);
            else ownedSource=DevelopingPreview.createSource(sourceReference);
            final DevelopingPreview.Source source=ownedSource;final Bitmap bitmap=ownedBitmap;
            posted=ui.post(new Runnable(){public void run(){
                if(!active||destroyed||closed||shot!=gate.shot()||productHud==null){
                    if(source!=null)source.release();DevelopingPreview.recycle(bitmap);return;
                }
                if(graded)productHud.gradedDeveloping(shot,bitmap);else productHud.sourceDeveloping(shot,source);
            }});
            event("develop_preview_copy","role="+(graded?"graded":"source")+" ms="+(SystemClock.elapsedRealtime()-start)+" posted="+posted);
        }catch(RuntimeException e){developEnabledThisShot=false;event("develop_preview_disabled",e.toString());}
        catch(OutOfMemoryError e){developEnabledThisShot=false;event("develop_preview_disabled",e.toString());}
        finally{if(!posted){if(ownedSource!=null)ownedSource.release();DevelopingPreview.recycle(ownedBitmap);}}
    }
    private void verifyPairPayloads(){workerActive=true;try{
        long start=SystemClock.elapsedRealtime();
        if(!PhotoFiles.completeJpeg(originalFile)||!PhotoFiles.completeJpeg(gradedFile))throw new IOException("paired JPEG payload incomplete");
        event("pair_payloads_verified","after_display_ms="+(SystemClock.elapsedRealtime()-start)+" both_complete=true");
        if(sourceReference!=null){
            saveReference(sourceReference,"SOURCE.PNG");saveReference(lutReview,"GRADED.PNG");
            event("source_reference_ready","original="+originalFile+" graded="+gradedFile);
            // Sony refuses a second open of the SD journal while its writer
            // remains open. Export only this finished, single-shot journal.
            closeJournal();
            try{
                File exported=RunFiles.exportAdb(run,originalFile);
                event("source_reference_exported","directory="+exported+" original="+originalFile+" graded="+gradedFile);
                message("诊断完成 · 已保存原片与冲洗成片\n请保留此页，正在核对原片。");
            }catch(IOException e){
                android.util.Log.e("SonyPreJpeg","diagnostic_export_failed; photos already saved",e);
                event("diagnostic_export_failed",e.toString());
                message("原片与冲洗成片均已保存\n诊断导出失败，可稍后重试。");
            }
        }
    }catch(Throwable e){failure("pair_payloads",e);}
    finally{workerActive=false;if(!active)ui.post(new Runnable(){public void run(){close();}});}}

    private void saveReference(Bitmap bitmap,String name)throws IOException{
        FileOutputStream out=new FileOutputStream(RunFiles.file(run,name));
        try{if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("reference PNG failed");}
        finally{out.close();}
    }
    private void prepareFilter()throws Exception{
        if(filter!=null)throw new IllegalStateException("previous develop filter retained");
        event("filter_setup_begin","DEFAULT_DEVELOP");
        filter=Class.forName("com.sony.scalar.hardware.CameraSequence$DefaultDevelopFilter").newInstance();
        if(!((Boolean)filter.getClass().getMethod("isSupported").invoke(filter)).booleanValue())throw new IOException("DEFAULT_DEVELOP unsupported");
        event("filter_setup_done","supported=true");
    }
    private void checkReviewClear(){
        try{
            // Reproduce the old Android empty-bitmap contract with a plain
            // ImageView; catch the old fit failure before the system handler.
            ImageView legacy=new ImageView(this);legacy.setImageBitmap(null);
            android.graphics.drawable.Drawable empty=legacy.getDrawable();
            if(empty==null)throw new IllegalStateException("legacy Android empty drawable contract differs");
            int w=empty.getIntrinsicWidth(),h=empty.getIntrinsicHeight();boolean rejected=false;
            try{ReviewGeometry.fit(w,h,review.getWidth(),review.getHeight(),1);}
            catch(IllegalArgumentException expected){rejected=true;event("legacy_review_clear_reproduced","width="+w+" height="+h+" exception="+expected);}
            if(!rejected)throw new IllegalStateException("old null-bitmap fit did not fail");
            for(int i=0;i<8;i++){
                review.setImageBitmap(lutReview);review.setImageBitmap(null);
                if(review.getDrawable()!=null)throw new IllegalStateException("clear retained a drawable");
            }
            review.setImageBitmap(lutReview);
            event("review_clear_check_passed","cycles=8 camera_opened=false");message("显示组件检查通过 · 等待下一阶段");
        }catch(Throwable e){failure("review_clear_check",e);}
    }
    private void rearm(final long shot){
        if(!active||destroyed||closed||!continuous)return;
        if(!gate.beginRearm(shot))return;
        try{
            if(workerActive||retainedImage!=null||originalFile==null||gradedFile==null||originalFile.equals(gradedFile))
                throw new IllegalStateException("cannot rearm before distinct pair completes and buffers transfer");
            if(camera.getParameters().getJpegQuality()!=95)throw new IOException("EXTRA FINE changed between shots");
            prepareFilter();rearmAt=SystemClock.elapsedRealtime();
            event("next_preview_request","from_press_ms="+(rearmAt-pressAt)+" cache_preserved=true");
            // Keep the result visible while the native live view resumes behind
            // it. Only its PreviewStart notification allows the next release.
            camera.startPreview();
            ui.postDelayed(new Runnable(){public void run(){
                if(!closed&&gate.shot()==shot&&gate.state()==PreJpegGate.State.REARMING)
                    failure("next_preview_timeout",new IOException("Sony preview did not report ready"));
            }},transitionTimeout());
        }catch(Throwable e){failure("rearm",e);}
    }
    private void finishRearm(long shot){
        if(!active||closed||destroyed||finishingRearm||gate.shot()!=shot||gate.state()!=PreJpegGate.State.REARMING)return;
        finishingRearm=true;
        try{
            if(autoPreview!=null){autoPreview.shotFinished();return;}
            sequenceType.getMethod("setReleaseLock",boolean.class).invoke(sequence,false);
            completeRearm(shot);
        }catch(Throwable e){failure("unlock_next_shot",e);}
        finally{finishingRearm=false;}
    }
    private void completeRearm(long shot){
        try{
            if(!gate.rearmed(shot))throw new IllegalStateException("capture state changed while unlocking");
            nextReadyAt=SystemClock.elapsedRealtime();
            if(manual!=null)manual.releaseCaptureGuard();
            reassertExposurePreview();
            event("next_ready","shutter_to_ready_ms="+(nextReadyAt-pressAt)+" displayed_ms="+(displayedAt-pressAt)
                +" display_to_ready_ms="+(nextReadyAt-displayedAt)+" preview_resume_ms="+(nextReadyAt-rearmAt)+" cache_preserved=true");
            savedStatus("成片 "+seconds(displayedAt-pressAt)+" 秒 · 下张可拍 "+seconds(nextReadyAt-pressAt)+" 秒");
            if(productUi())recordPair();
            maybeOpenGallery();
        }catch(Throwable e){failure("unlock_next_shot",e);}
    }
    private void showViewfinder(){
        if(!reviewing||gate.state()!=PreJpegGate.State.READY)return;
        reviewing=false;review.setVisibility(View.INVISIBLE);review.setImageBitmap(null);
        ui.removeCallbacks(hideReviewStatus);status.setVisibility(View.VISIBLE);status.setText("按快门拍照 · 原片保留 · EXTRA FINE");
        clearOriginalReview();if(productHud!=null){status.setVisibility(View.GONE);productLive();}
        event("viewfinder_revealed","since_ready_ms="+(SystemClock.elapsedRealtime()-nextReadyAt));
    }
    private void loadingStage(String text,int step){
        if(productHud==null)return;
        productHud.setLoadingStage(text,step);
    }
    private void productLive(){
        if(productHud==null)return;
        boolean entering=!productHud.mode.equals("live");
        productHud.endRecipeCapture();
        productHud.setVisibility(View.VISIBLE);productHud.mode="live";
        if(manual!=null){
            manual.update(productHud.readout);
            productHud.manualExposure=true;
            String modeLabel=manual.label();
            productHud.liveExposure=modeLabel;
            productHud.liveCompensation="";
            String dials="A".equals(modeLabel)?"转盘 光圈 · 前/后拨轮 EV · ISO AUTO":
                    "S".equals(modeLabel)?"转盘 快门 · 前/后拨轮 EV · ISO AUTO":
                    "P".equals(modeLabel)?"转盘 ISO · 前/后拨轮 EV":
                    "前拨轮 光圈 · 后拨轮 快门 · 转盘 ISO";
            productHud.detail=modeLabel+" · "+dials;
            productHud.footer="C1 模式 · ↓ AF · → WB · ← 录像";
            if(recipeMode())recipeHud();
        }else{
            productHud.manualExposure=false;
            productHud.liveExposure=recipeMode()?"相机直出":exposure==null?"自动曝光":exposure.label();
            productHud.liveCompensation=recipeMode()?"":exposure==null?"-- EV":exposure.compensationLabel();
            productHud.detail=recipeMode()?"":("自动曝光    "+productHud.liveExposure);
            productHud.footer="";
            if(recipeMode())recipeHud();
        }
        // The spot AF box belongs to the finder, not just to the selector:
        // keep it on screen in every photo exposure mode.
        if(af!=null&&af.ready())productHud.setAf(true,af.active(),af.x(),af.y());
        else if(recipeMode())productHud.setAf(true,false,0,0);
        productHud.progress="";
        if(previewIsolationProbe()&&isolationBarrier!=null)
            productHud.showPersistentLiveHint(isolationBarrier.captureReady()
                ?"隔离测试：原色已恢复 · 按快门拍一张"
                :"隔离测试：快门关闭 · 中央键准备拍摄");
        else if(autoPreview!=null){
            if(autoPreview.state()==AutoPreviewGate.State.PREVIEW)
                productHud.showPersistentLiveHint("近似色彩预览 · 半按对焦，按下拍摄");
            else if(autoPreview.state()==AutoPreviewGate.State.WAITING||autoPreview.state()==AutoPreviewGate.State.RESTORING)
                productHud.showPersistentLiveHint("正在准备拍摄 · 请保持半按");
            else if(autoPreview.ready())productHud.showLiveHint("已恢复拍摄原色 · 按下快门拍摄");
        }
        else if(recipeMode()&&entering&&productHud.liveInfoVisible)productHud.showLiveHint(recipeCameraName+" · "+Lang.recipeSummary(recipe)+" · 相机直出");
        else if(manual!=null&&entering&&productHud.liveInfoVisible)productHud.showLiveHint("↓ AF · → WB · 前拨光圈 · 后拨快门 · 转盘 ISO");
        else if(entering&&productHud.liveInfoVisible)productHud.showLiveHint("拨轮调亮度 · 中央归零 · ↑ 隐藏信息");
        productHud.update();
    }
    /** a7r2.45: recipe mode no longer opens CaptureTelemetry (vendor listener
     * callbacks into the UI thread). The readouts are seeded once from the
     * parameters open() already read; recipes never change exposure. */
    private void seedRecipeReadout(Camera.Parameters p){
        if(productHud==null||p==null)return;
        CaptureReadout values=new CaptureReadout();
        try{values.shutterNumerator=Integer.parseInt(p.get("shutter-speed-nume"));}catch(Throwable ignored){}
        try{values.shutterDenominator=Integer.parseInt(p.get("shutter-speed-deno"));}catch(Throwable ignored){}
        try{values.aperture=Integer.parseInt(p.get("aperture"));}catch(Throwable ignored){}
        int iso=0;try{iso=Integer.parseInt(p.get("iso-sensitivity"));}catch(Throwable ignored){}
        if(iso<=0)values.isoAuto=true;else values.manualIso=iso;
        String wb=p.get("whitebalance");if(wb!=null)values.whiteBalance=wb;
        productHud.readout=values;
    }
    /** Recipe HUD in both cases: with an exposure session (not used on the
     * A7R II recipe path) and with the plain Recipe Lab-style finder. */
    private void recipeHud(){
        if(productHud==null||!recipeMode()||recipe==null)return;
        productHud.manualExposure=manual!=null;
        productHud.recipeMode=true;productHud.name=Lang.recipeName(recipe);productHud.tag="配方";
        int group=RecipeData.groupOf(recipeId);
        int count=RecipeData.countInGroup(group);
        int position=RecipeData.localIndex(recipeId)+1;
        productHud.detail=filmSelecting
                ?"实时预览 · "+recipeCameraName
                :recipeCameraName+" · "+Lang.recipeSummary(recipe);
        if(filmApplyError.length()>0)productHud.detail="胶片切换失败 · "+filmApplyError;
        productHud.footer=filmSelecting
                ?"快门/● 确认 · MENU 取消 · 转盘/←→ 换胶片"
                :"C3 胶片 · C1 模式 · ↓ AF · → WB · ← 录像 · C2 摆姿";
        String previous="",next="";
        if(filmSelecting&&count>0){
            int local=RecipeData.localIndex(recipeId);
            int before=RecipeData.recipeAt(group,(local-1+count)%count);
            int after=RecipeData.recipeAt(group,(local+1)%count);
            if(before>=0)previous=Lang.recipeName(RecipeData.ALL[before]);
            if(after>=0)next=Lang.recipeName(RecipeData.ALL[after]);
        }
        productHud.setFilmStatus(Lang.recipeName(recipe),position,count,filmSelecting,previous,next);
    }
    private void reapplyManualPreview(){
        if(manual==null)return;
        try{manual.enableExposurePreview();}
        catch(Throwable e){event("manual_preview_mode_error",cause(e).toString());}
    }
    /** C3 enters a modal film strip over the live view. Every step applies the
     * recipe through the serialized Sony control thread, so the finder shows
     * the look before the user confirms with a second C3 press. */
    private void openFilmSelection(){
        if(!recipeMode()||recipe==null||recipeLook==null||recipeApplyBusy||!manualAdjustable())return;
        if(PoseDirectorOverlay.isShowing()||(af!=null&&af.active()))return;
        int group=RecipeData.groupOf(recipeId);
        int count=RecipeData.countInGroup(group);
        if(count<=0)return;
        filmSelecting=true;filmSelectOriginal=recipeId;filmApplyError="";
        filmSelectIndex=RecipeData.localIndex(recipeId);
        if(filmSelectIndex<0||filmSelectIndex>=count)filmSelectIndex=0;
        event("film_menu_open","recipe="+recipe.name+" index="+filmSelectIndex+" count="+count+" camera="+recipeCameraName);
        productLive();
    }
    private void handleFilmKey(KeyEvent e){
        int key=e.getKeyCode(),scan=e.getScanCode();
        if(e.getAction()!=KeyEvent.ACTION_DOWN)return;
        int action=UiKeys.key(e);
        if(A7R2Keys.filmKey(scan,key)){
            if(e.getRepeatCount()==0){event("film_key_confirm","scan="+scan);confirmFilmSelection();}
            return;
        }
        // a7r2.47: a full shutter press or the centre OK button also confirms
        // the highlighted film, exactly like a second C3 press, and consumes
        // the key so confirming does not start a capture. Half-press (S1) is
        // left alone: the following S2 is the confirm.
        int code=scan!=0?scan:key;
        boolean fullShutter=code==518||key==518||key==KeyEvent.KEYCODE_CAMERA;
        if(fullShutter||action==UiKeys.OK){
            if(e.getRepeatCount()==0){
                event("film_confirm_key","key="+key+" scan="+scan+" shutter="+fullShutter);
                confirmFilmSelection();
            }
            return;
        }
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
        int group=RecipeData.groupOf(recipeId);
        int count=RecipeData.countInGroup(group);
        if(count<=0)return;
        filmSelectIndex=(filmSelectIndex+direction+count)%count;
        int id=RecipeData.recipeAt(group,filmSelectIndex);
        if(id<0||id>=RecipeData.ALL.length)return;
        recipeId=id;recipe=RecipeData.ALL[id];filmApplyError="";
        event("film_menu_step","recipe="+recipe.name+" index="+filmSelectIndex+"/"+count);
        productLive();
        filmApplyPendingId=id;
        scheduleFilmApply(id);
    }
    private void confirmFilmSelection(){
        if(!filmSelecting)return;
        filmSelecting=false;
        int group=RecipeData.groupOf(recipeId);
        int local=RecipeData.localIndex(recipeId);
        RecipeMemory.save(this,group,local);
        getPreferences(0).edit().putString("exposure_mode",manual==null?"aperture-priority":manual.mode()).commit();
        event("film_selected","recipe="+recipe.name+" camera="+recipeCameraName+" group="+group+" local="+local);
        productLive();
        if(productHud!=null){
            productHud.showLiveHint(appliedRecipeId==recipeId
                    ?"已启用 "+Lang.recipeName(recipe)+" · 相机直出"
                    :"已选择 "+Lang.recipeName(recipe)+" · 正在应用");
            productHud.update();
        }
    }
    private void cancelFilmSelection(){
        if(!filmSelecting)return;
        filmSelecting=false;
        if(filmSelectOriginal>=0&&filmSelectOriginal<RecipeData.ALL.length){
            recipeId=filmSelectOriginal;
            recipe=RecipeData.ALL[recipeId];
            filmApplyPendingId=recipeId;
            scheduleFilmApply(recipeId);
        }
        filmApplyError="";
        event("film_selection_canceled","recipe="+recipe.name);
        productLive();
    }
    /** Re-run a C3 switch that was requested while a capture owned the gate. */
    private void reapplyPendingFilm(){
        int id=filmApplyPendingId;
        if(id>=0&&id==recipeId&&recipeLook!=null
                &&gate.state()==PreJpegGate.State.READY)scheduleFilmApply(id);
    }
    private void scheduleFilmApply(final int id){
        final Handler handler=controlHandler;
        final RecipeCameraLook look=recipeLook;
        if(handler==null||look==null)return;
        final int generation;
        synchronized(controlLock){generation=++filmApplyGeneration;}
        // Coalesce wheel bursts: only the film left under the highlight is
        // written. The recipe is committed to the running preview exactly as
        // Recipe Lab does; stopping the preview around the patch reset the
        // body. A rejected patch keeps the previous look and is reported.
        handler.postDelayed(new Runnable(){public void run(){
            boolean applied=false;
            String error="";
            // Do not hold controlLock across the vendor call: close() uses the
            // same lock to stop this thread, and a wedged Sony RPC must never
            // turn into an unresponsive UI that needs a battery pull. The
            // in-progress flag, set and checked under the lock by armRecipe(),
            // is what keeps a film patch out of an armed capture.
            synchronized(controlLock){
                if(controlClosed||generation!=filmApplyGeneration)return;
                if(!active||closed||gate.state()!=PreJpegGate.State.READY||workerActive||reviewing){
                    filmApplyPendingId=id;
                    event("film_apply_deferred","recipe="+RecipeData.ALL[id].name+" state="+gate.state());
                    return;
                }
                filmApplyInProgress=true;
            }
            try{
                event("film_apply_begin","recipe="+RecipeData.ALL[id].name+" preview=live");
                stage("film_apply_begin "+RecipeData.ALL[id].name);
                applied=applyRecipeLook(RecipeData.ALL[id],"film");
                if(applied){
                    event("film_apply_done","recipe="+RecipeData.ALL[id].name);
                    stage("film_apply_done "+RecipeData.ALL[id].name);
                    // The live patch rewrote WB/EV behind the exposure session;
                    // re-read them so C1/dials never restore the previous film.
                    ManualExposure session=manual;
                    if(session!=null)try{session.refresh();}catch(Throwable e){event("manual_refresh_error",cause(e).toString());}
                }else error="参数写入被相机拒绝";
            }catch(Throwable e){
                error=cause(e).toString();
            }finally{
                synchronized(controlLock){
                    filmApplyInProgress=false;
                    if(applied&&id==filmApplyPendingId){filmApplyPendingId=-1;appliedRecipeId=id;}
                }
            }
            synchronized(controlLock){
                if(controlClosed||generation!=filmApplyGeneration)return;
            }
            final boolean ok=applied;
            final String failure=error;
            ui.post(new Runnable(){public void run(){
                if(destroyed||closed)return;
                if(!ok){
                    filmApplyError=failure;
                    event("film_apply_error","recipe="+RecipeData.ALL[id].name+" "+failure);
                    if(productHud!=null){productLive();productHud.showLiveHint("胶片切换失败");productHud.update();}
                    return;
                }
                if(recipeId==id){filmApplyError="";productLive();}
            }});
        }},200);
    }
    private void openWbMenu(){
        if(manual==null||productHud==null)return;
        wbMenuValues=manual.whiteBalanceValues();
        if(wbMenuValues.isEmpty()){productHud.showLiveHint("白平衡预设不可用");productHud.update();return;}
        String[] labels=new String[wbMenuValues.size()];
        for(int i=0;i<labels.length;i++)labels[i]=WhiteBalanceNames.label(wbMenuValues.get(i));
        int selected=wbMenuValues.indexOf(manual.whiteBalance());
        if(selected<0)selected=0;
        productHud.showWbMenu(labels,selected,"↑↓ 选择 · ● 确认 · →/MENU 取消");
        event("wb_menu_open","values="+wbMenuValues.size()+" current="+manual.whiteBalance());
    }
    private void handleWbMenuKey(KeyEvent e){
        if(e.getAction()!=KeyEvent.ACTION_DOWN)return;
        int key=e.getKeyCode(),scan=e.getScanCode();
        int action=UiKeys.key(e),dial=UiKeys.dial(e),control=UiKeys.controlDial(e);
        if(action==UiKeys.UP||dial==-1||control==-1){moveWb(-1);return;}
        if(action==UiKeys.DOWN||dial==1||control==1){moveWb(1);return;}
        if(action==UiKeys.OK){applyWb();return;}
        if(action==UiKeys.RIGHT||action==UiKeys.BACK||key==KeyEvent.KEYCODE_MENU||scan==514){
            productHud.closeWbMenu();return;
        }
    }
    private void moveWb(int direction){
        if(wbMenuValues.isEmpty())return;
        int selected=(productHud.wbSelected+direction+wbMenuValues.size())%wbMenuValues.size();
        productHud.setWbSelection(selected);
    }
    private void applyWb(){
        if(wbMenuValues.isEmpty()||productHud==null){if(productHud!=null)productHud.closeWbMenu();return;}
        String value=wbMenuValues.get(productHud.wbSelected);
        productHud.closeWbMenu();
        awaitManualControls();
        try{
            manual.setWhiteBalanceValue(value);
            if(previewDriver!=null)previewDriver.rebaseWhiteBalance();
            manual.update(productHud.readout);productLive();
            event("white_balance","value="+value+" via=menu");
        }catch(Throwable e){manualError("white_balance",e);}
    }
    /** Leave the still CameraSequence session before opening movie mode in a
     * separate process; Sony needs a fresh vendor session for the other mode. */
    private void openVideoMode(){
        if(workerActive||gate.busy()||!active||closed||reviewing)return;
        if(af!=null&&af.active())return;
        if(recipeMode()){
            if(recipe==null)return;
            event("video_handoff_begin","recipe="+recipe.name+" to=video lut=false");
            close();
            setResult(RESULT_OK,new android.content.Intent().putExtra("open_video",true)
                    .putExtra("recipe_mode",true).putExtra("recipe_id",recipeId)
                    .putExtra("recipe_name",Lang.recipeName(recipe)).putExtra("camera_name",recipeCameraName));
            finish();return;
        }
        if(selectedLut==null)return;
        final String id=selectedLut.id;
        event("video_handoff_begin","lut="+id+" to=video");
        close();
        setResult(RESULT_OK,new android.content.Intent().putExtra("open_video",true).putExtra("lut_id",id));
        finish();
    }
    /** a7r2.52 fallback for the WAITING race: when the full press lands before
     * AutoPreviewGate reaches READY, scan 517 was consumed by the gate and the
     * half-press guard never ran, leaving shooting-preview unlocked and the
     * direct shutter cancelled (status=1 in R0079E5F). The guard is idempotent
     * via ManualExposure.captureSafe(). */
    private void ensureManualCaptureGuard(String origin){
        if(manual==null||recipeMode()||reviewing||closed||destroyed||workerActive)return;
        if(gate.state()!=PreJpegGate.State.READY||manual.captureSafe())return;
        event("manual_guard_requested","origin="+origin);
        guardManualCapture(origin);
    }
    /** Cancel vendor focus before/after a CameraEx ownership handoff. A queued
     * movie AF request can outlive its process and block the next session. */
    private void resetCameraFocus(String origin){
        Camera target=camera;
        if(target==null)return;
        try{target.cancelAutoFocus();event("focus_canceled","origin="+origin);}
        catch(Throwable e){event("focus_cancel_error","origin="+origin+" "+cause(e));}
    }
    /** LUT half-press AF. The still path previously never asked the lens to
     * focus: Sony's direct shutter did it at S2. After a movie session the
     * vendor AF executor can still be busy, and the direct shutter then times
     * out (ShutterListener CANCELED 0.6-0.9 s after S2). Start a normal
     * autoFocus when the gate is ready, after cancelling any inherited
     * request, and cancel it when S1 is released. Runs on the serialized
     * control thread so it cannot interleave with the capture guard writes. */
    private void startLutFocus(final String origin){
        if(recipeMode())return;
        final Camera target=camera;
        if(target==null)return;
        final Handler handler=controlHandler;
        Runnable call=new Runnable(){public void run(){
            if(closed||destroyed||camera!=target)return;
            long start=SystemClock.elapsedRealtime();
            try{
                target.cancelAutoFocus();
                target.autoFocus(null);
                event("lut_focus_start",origin+" ms="+(SystemClock.elapsedRealtime()-start));
            }catch(Throwable e){event("lut_focus_error",origin+" "+cause(e));}
        }};
        if(handler!=null)handler.post(call);else call.run();
    }
    private void cancelLutFocus(final String origin){
        if(recipeMode())return;
        final Camera target=camera;
        if(target==null)return;
        final Handler handler=controlHandler;
        Runnable call=new Runnable(){public void run(){
            if(camera!=target)return;
            try{target.cancelAutoFocus();event("focus_canceled","origin="+origin);}
            catch(Throwable e){event("focus_cancel_error","origin="+origin+" "+cause(e));}
        }};
        if(handler!=null)handler.post(call);else call.run();
    }
    /** Sony's preview display mode can unlock AE. Before the camera is allowed
     * to meter the shot, restore the plain M state and the exact dial values.
     * This runs on the half-press, which precedes the full press. */
    private void guardManualCapture(String origin){
        if(manual==null)return;
        long start=SystemClock.elapsedRealtime();
        try{
            awaitManualControls();
            manual.prepareCapture();
            android.util.Log.i("SonyManual","capture_guard "+origin+" ms="+(SystemClock.elapsedRealtime()-start));
        }catch(Throwable e){event("manual_capture_guard_error",origin+" "+cause(e));}
    }
    /** Re-assert M-mode live exposure on the serialized control thread; this
     * is called from Sony's preview callback, not the UI thread. */
    private void reassertExposurePreview(){
        final Handler handler=controlHandler;
        if(handler==null||manual==null)return;
        ui.post(new Runnable(){public void run(){
            if(closed||destroyed||reviewing||workerActive||gate.state()!=PreJpegGate.State.READY)return;
            final Handler live=controlHandler;
            if(live==null)return;
            live.post(new Runnable(){public void run(){
                synchronized(controlLock){
                    if(controlClosed||workerActive||manual==null||manual.captureSafe()
                            ||gate.state()!=PreJpegGate.State.READY)return;
                    try{manual.enableExposurePreview();}
                    catch(Throwable e){event("manual_preview_mode_error",cause(e).toString());}
                }
            }});
        }});
    }
    private boolean manualAdjustable(){
        return gate.state()==PreJpegGate.State.READY&&!workerActive&&!reviewing
                &&(autoPreview==null||autoPreview.state()==AutoPreviewGate.State.PREVIEW);
    }
    private void startManualControls(){
        if(controlThread!=null)return;
        controlThread=new HandlerThread("sonylut-manual-controls");
        controlThread.start();controlHandler=new Handler(controlThread.getLooper());
    }
    private void queueManual(int front,int rear,int iso,int wb,int scan){
        Handler handler=controlHandler;
        if(handler==null){event("manual_control_unavailable","scan="+scan);return;}
        synchronized(controlLock){
            if(controlClosed)return;
            pendingAperture+=front;pendingShutter+=rear;pendingIso+=iso;pendingWhiteBalance+=wb;
        }
        if(loggedDialScans.add(Integer.valueOf(scan)))
            android.util.Log.i("SonyManual","manual_key scan="+scan+" front="+front+" rear="+rear+" iso="+iso+" wb="+wb);
        handler.post(applyControls);
    }
    /** Ordered barrier: every control task posted before the shutter has run
     * before the RAW request can start. Keeps CameraEx single-threaded. */
    private void awaitManualControls(){awaitManualControls(2500);}
    private void awaitManualControls(long limitMs){
        final Handler handler=controlHandler;if(handler==null)return;
        final java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);
        if(!handler.post(new Runnable(){public void run(){done.countDown();}}))return;
        try{
            if(!done.await(limitMs,java.util.concurrent.TimeUnit.MILLISECONDS))
                event("manual_control_wait_timeout","limit_ms="+limitMs);
        }catch(InterruptedException e){Thread.currentThread().interrupt();event("manual_control_wait_interrupted","");}
    }
    private void stopManualControls(){
        Handler handler;HandlerThread thread;
        synchronized(controlLock){
            if(controlClosed)return;
            controlClosed=true;
            pendingAperture=pendingShutter=pendingIso=pendingWhiteBalance=0;
            handler=controlHandler;thread=controlThread;controlHandler=null;controlThread=null;
        }
        if(handler!=null)handler.removeCallbacksAndMessages(null);
        if(thread!=null)thread.quit();
    }
    private void manualError(String where,Throwable e){
        Throwable root=cause(e);
        event("manual_error",where+" "+root);
        if(productHud!=null&&!closed){productHud.showLiveHint("操作未生效："+(root.getMessage()==null?root.getClass().getSimpleName():root.getMessage()));productHud.update();}
    }
    private void adjustExposure(int direction,boolean reset){
        if(!active||closed||reviewing||workerActive||gate.state()!=PreJpegGate.State.READY||exposure==null)return;
        try{exposure.adjust(direction,reset);event("exposure_adjusted",exposure.state());productLive();
            if(!productHud.liveInfoVisible)productHud.showLiveHint(productHud.liveExposure);}
        catch(Exception e){failure("exposure_adjust",e);}
    }
    private void clearOriginalReview(){showingOriginal=false;}
    private void maybeOpenGallery(){
        if(!playbackRequested||!productUi()||!active||closed||workerActive||pendingReceipts!=0||gate.state()!=PreJpegGate.State.READY)return;
        playbackRequested=false;
        event("playback_handoff","all_saves_complete=true receipt_writes=0");
        close(); // Complete vendor release before asking the library to open history.
        setResult(RESULT_OK,new android.content.Intent().putExtra("open_gallery",true).putExtra("page_offset",playbackOffset));finish();
    }
    private void compareOriginal(boolean original){
        if(!reviewing||gate.state()!=PreJpegGate.State.READY||productHud==null)return;
        long begin=SystemClock.elapsedRealtime();
        if(sourceReference==null)throw new IllegalStateException("original display cache missing");
        showingOriginal=original;review.setImageBitmap(original?sourceReference:lutReview);
        productHud.setVisibility(View.VISIBLE);productHud.original=original;productHud.detail="";productHud.update();
        event("review_compare","role="+(original?"original":"graded")+" switch_ms="+(SystemClock.elapsedRealtime()-begin)+" jpeg_decode=false");
    }
    private void recordPair(){
        if(selectedLut==null||originalFile==null||gradedFile==null||recordedPairShot==gate.shot())return;
        recordedPairShot=gate.shot();
        final File original=originalFile,graded=gradedFile;final long display=Math.max(0,displayedAt-pressAt),ready=Math.max(0,nextReadyAt-pressAt);
        final LutLibrary.Entry lut=selectedLut;pendingReceipts++;
        // Make the contact print from the finished LUT review on the UI thread
        // before any possible recycle, then write it with the receipt. The
        // gallery can then paint its contact sheet without decoding 42MP.
        Bitmap thumb=null;
        try{if(lutReview!=null)thumb=Bitmap.createScaledBitmap(lutReview,PhotoThumbs.W,PhotoThumbs.H,true);}
        catch(OutOfMemoryError e){event("thumb_oom",e.toString());}
        final Bitmap contactThumb=thumb;
        work.post(new Runnable(){public void run(){try{
            PhotoCatalog.Pair pair=new PhotoCatalog(ProductFiles.photos(),sd).save(original,graded,lut.name,lut.size,display,ready,ReviewLoader.capturedAt(original));
            event("pair_catalogued","original="+original+" graded="+graded);
            if(contactThumb!=null){
                boolean saved=PhotoThumbs.save(ProductFiles.photos(),pair.id,contactThumb);
                event("thumb_saved","id="+pair.id+" saved="+saved);
            }
        }catch(final IOException e){event("catalog_failed",e.toString());ui.post(new Runnable(){public void run(){if(!destroyed&&productHud!=null){productHud.detail="照片已保存在 DCIM；回看记录保存失败";productHud.update();}}});}
        finally{if(contactThumb!=null)contactThumb.recycle();ui.post(new Runnable(){public void run(){pendingReceipts--;maybeOpenGallery();exitProductProcess();}});}}});
    }
    private void exitProductProcess(){
        // No camera handles or receipt writes may outlive the isolated process.
        // Finishing it makes the next entry satisfy the established fresh-JNI guard.
        if(productUi()&&destroyed&&closed&&pendingReceipts==0){
            if(thread!=null)thread.quit();
            android.os.Process.killProcess(android.os.Process.myPid());
        }
    }
    private void release(Object value){if(value!=null)try{
        Method valid=value.getClass().getMethod("isValid");if(((Boolean)valid.invoke(value)).booleanValue())value.getClass().getMethod("release").invoke(value);
    }catch(Throwable e){event("release_error",cause(e).toString());}}
    private static String zoomValues(Camera.Parameters p){StringBuilder s=new StringBuilder();
        for(String key:ZOOM_KEYS)s.append(key).append('=').append(p.get(key)).append(' ');
        return s.append("zoom=").append(p.get("zoom")).toString();}
    private void close(){if(closed||workerActive||gate.busy())return;
        PoseDirectorOverlay.closeIfVisible();
        if(productUi())recordPair();
        closed=true;
        heartbeatStop=true;
        removeLooperTrace();
        stage("closed");
        if(autoPreview!=null)autoPreview.close();
        if(isolationBarrier!=null)isolationBarrier.fail();
        if(isolationPreview!=null)try{isolationPreview.close();event("isolation_closed","baseline_restored=true");}
            catch(Exception e){event("isolation_restore_error",cause(e).toString());}
        if(productHud!=null)productHud.endDeveloping();
        // A live film switch posted to the control thread must finish writing
        // before recipeLook.restore() rewrites the same keys.
        if(recipeLook!=null)awaitManualControls();
        filmSelecting=false;
        // No exposure worker may touch CameraEx after this point, and no
        // queued step is allowed to run while the original settings restore.
        stopManualControls();
        stopRecipeFocus();
        if(telemetry!=null){telemetry.close();telemetry=null;}
        release(retainedImage);retainedImage=null;
        if(filter!=null)try{filter.getClass().getMethod("release").invoke(filter);}catch(Throwable e){event("filter_release_error",cause(e).toString());}filter=null;
        // Cancel before the AF area and parameters are restored, so a late
        // vendor focus callback cannot rewrite them while CameraEx closes.
        resetCameraFocus("close");
        for(Object value:new Object[]{sequence,cameraEx})if(value!=null){
            // Recipe order: the exposure session restores its own pre-session
            // values first, then the look restores the user's pre-app values.
            // Restoring the look first let the session write the old film's
            // WB/EV back over the user's settings.
            if(value==cameraEx&&manual!=null){try{
                manual.close();event("manual_restored","manual settings readback matched");
            }catch(Throwable e){event("manual_restore_error",cause(e).toString());}manual=null;}
            if(value==cameraEx&&recipeLook!=null){try{
                recipeLook.restore();event("recipe_restored","camera controls restored");
            }catch(Throwable e){event("recipe_restore_error",cause(e).toString());}recipeLook=null;}
            if(value==cameraEx&&af!=null){try{
                af.close();event("af_restored","original AF area restored");
            }catch(Throwable e){event("af_restore_error",cause(e).toString());}af=null;}
            if(value==cameraEx&&faceDetection!=null){try{
                faceDetection.restore();event("face_detection_closed","mode restored before CameraEx release");
            }catch(Throwable e){event("face_detection_close_error",cause(e).toString());}faceDetection=null;}
            if(value==cameraEx&&exposure!=null){try{
                exposure.close();event("exposure_restored","original settings readback matched");exposure=null;
            }catch(Exception e){event("exposure_restore_error",cause(e).toString());}}
            if(value==cameraEx&&originalPictureSize!=null){try{
                String[] wh=originalPictureSize.split("x");Camera.Parameters restore=camera.getParameters();
                restore.setPictureSize(Integer.parseInt(wh[0]),Integer.parseInt(wh[1]));camera.setParameters(restore);
                event("picture_size_restored","size="+originalPictureSize+" readback="+camera.getParameters().get("picture-size"));originalPictureSize=null;
            }catch(Throwable e){event("picture_size_restore_error",cause(e).toString());}}
            if(value==cameraEx&&originalQuality!=null){try{
                Camera.Parameters restore=camera.getParameters();restore.set("jpeg-quality",originalQuality);camera.setParameters(restore);
                event("quality_restored","jpeg_quality="+camera.getParameters().get("jpeg-quality"));originalQuality=null;
            }catch(Throwable e){event("quality_restore_error",cause(e).toString());}}
            if(value==cameraEx&&!originalZoom.isEmpty()){
                try{
                    Camera.Parameters restore=camera.getParameters();for(Map.Entry<String,String> entry:originalZoom.entrySet())restore.set(entry.getKey(),entry.getValue());
                    event("zoom_restore_begin",originalZoom.toString());camera.setParameters(restore);
                    event("zoom_restore_done",zoomValues(camera.getParameters()));originalZoom.clear();
                }catch(Throwable e){event("zoom_restore_error",cause(e).toString());}
            }
            try{
            event("release_begin",value.getClass().getName());value.getClass().getMethod("release").invoke(value);event("release_done",value.getClass().getName());
            if(value==cameraEx&&previewDriver!=null)previewDriver.cameraReleased();
            }catch(Throwable e){event("close_error",cause(e).toString());}
        }
        sequence=null;cameraEx=null;camera=null;
        if(lutCache!=0){Native.releaseDeviceLut(lutCache);lutCache=0;}
        if(lutReview!=null){if(review!=null)review.setImageBitmap(null);lutReview.recycle();lutReview=null;}
        clearOriginalReview();
        if(sourceReference!=null){sourceReference.recycle();sourceReference=null;}
        event("closed","journal_write_ms="+journalWriteMs());closeJournal();if(destroyed&&thread!=null&&pendingReceipts==0)thread.quit();
        exitProductProcess();
    }
    @Override protected void onResume(){super.onResume();active=true;
        if(closed){finish();return;}
        if(productUi())SonyAppPolicy.register(this);
        // a7r2.45: the recipe finder never shows a review bitmap, so it does
        // not need DisplayManager. That listener is another vendor callback
        // into the UI thread and was the last non-camera one left at startup.
        if(review!=null&&!recipeMode())try{review.startDisplayTracking();}catch(Exception e){failure("display_geometry",e);close();return;}
        open();
    }
    @Override protected void onPause(){active=false;ui.removeCallbacks(hideReviewStatus);ui.removeCallbacks(processingTick);ui.removeCallbacks(restoreGraphics);if(review!=null)review.stopDisplayTracking();close();super.onPause();}
    @Override protected void onDestroy(){destroyed=true;close();stopManualControls();if(closed&&thread!=null&&pendingReceipts==0)thread.quit();
        if(Thread.getDefaultUncaughtExceptionHandler()==exceptionHandler)Thread.setDefaultUncaughtExceptionHandler(previousExceptionHandler);
        super.onDestroy();exitProductProcess();}
    @Override public void onBackPressed(){if(!workerActive&&!gate.busy())finish();}
    // Android's Surface.isValid() can stay false on the older API levels used
    // by PMCA even though the lifecycle callback has already delivered a
    // usable push-buffer surface. Follow the callbacks instead.
    public void surfaceCreated(SurfaceHolder h){
        if(h!=holder)return;surfaceAvailable=true;event("surface_created","");open();
    }
    public void surfaceChanged(SurfaceHolder h,int f,int w,int height){
        if(h!=holder)return;surfaceAvailable=true;event("surface_changed","width="+w+" height="+height);open();
    }
    public void surfaceDestroyed(SurfaceHolder h){
        if(h!=holder)return;surfaceAvailable=false;event("surface_destroyed","");close();
    }
    @Override public boolean dispatchKeyEvent(KeyEvent e){int key=e.getKeyCode(),scan=e.getScanCode();
        // The film strip is modal: it claims every key before the pose overlay
        // so C2 cannot open another layer and leave a pending apply behind it.
        if(productUi()&&recipeMode()&&filmSelecting){handleFilmKey(e);return true;}
        // C2 pose director overlays the live view and owns its own keys.
        if(PoseDirectorOverlay.handleKey(this,e))return true;
        if(productUi()&&productHud!=null&&productHud.wbMenuVisible){handleWbMenuKey(e);return true;}
        // Film strip: C3 opens it, C3 again confirms, MENU/BACK cancels and
        // dials/arrows browse. While open it owns every key so a stray shutter
        // or dial cannot start a capture mid-switch.
        if(productUi()&&recipeMode()){
            if(A7R2Keys.filmKey(scan,key)){
                if(e.getAction()==KeyEvent.ACTION_DOWN&&e.getRepeatCount()==0){
                    event("film_key","scan="+scan+" ready="+manualAdjustable());
                    if(manualAdjustable()&&!PoseDirectorOverlay.isShowing())openFilmSelection();
                }
                return true;
            }
        }
        if(automaticNativePreview()&&(UiKeys.shutter(e)||key==KeyEvent.KEYCODE_FOCUS||key==772||scan==772||key==774||scan==774)){
            if(autoPreview==null){
                if(gate.state()!=PreJpegGate.State.READY)return true;
            }else{
                event("auto_preview_key","key="+key+" scan="+scan+" action="+e.getAction()+" state="+autoPreview.state());
                boolean s1Key=key==516||scan==516||key==517||scan==517||key==KeyEvent.KEYCODE_FOCUS;
                if(s1Key){
                    if(e.getAction()==KeyEvent.ACTION_DOWN)s1Held=true;
                    else if(e.getAction()==KeyEvent.ACTION_UP)s1Held=false;
                }
                boolean half=key==516||scan==516||key==KeyEvent.KEYCODE_FOCUS;
                boolean anyHalfUp=e.getAction()==KeyEvent.ACTION_UP
                        &&(scan==516||key==516||scan==517||key==517||key==KeyEvent.KEYCODE_FOCUS);
                if(anyHalfUp)cancelLutFocus("half_release");
                boolean releaseHalf=key==772||scan==772||(half&&e.getAction()==KeyEvent.ACTION_UP);
                if(releaseHalf){autoPreview.release();return true;}
                if(e.getAction()==KeyEvent.ACTION_DOWN&&e.getRepeatCount()==0&&(half||key==518||scan==518||key==KeyEvent.KEYCODE_CAMERA)){
                    if(gate.state()==PreJpegGate.State.READY){
                        showViewfinder();autoPreview.press();productLive();
                    }
                }
                if(!autoPreview.ready())return true;
            }
        }
        if(previewIsolationProbe()&&(isolationBarrier==null||!isolationBarrier.captureReady())){
            if(UiKeys.shutter(e)){
                if(e.getAction()==KeyEvent.ACTION_DOWN)event("isolation_shutter_blocked","key="+key+" direct_shutter=false");
                return true;
            }
            if(UiKeys.key(e)==UiKeys.OK){
                if(e.getAction()==KeyEvent.ACTION_DOWN&&e.getRepeatCount()==0)prepareIsolationCapture();return true;
            }
        }
        if(productUi()){
            int action=UiKeys.key(e);
            if(UiKeys.deleteKey(e)){
                if(e.getAction()==KeyEvent.ACTION_DOWN&&e.getRepeatCount()==0)event("delete_live_ignored","scan="+scan);
                return true;
            }
            if(action==UiKeys.PLAY){
                if(e.getAction()==KeyEvent.ACTION_DOWN&&e.getRepeatCount()==0){
                    event("playback_key","key="+key+" scan="+scan+" state="+gate.state());
                    playbackOffset=0;playbackRequested=true;maybeOpenGallery();
                }
                return true;
            }
            int dial=UiKeys.dial(e);
            if(reviewing&&(dial!=0||action==UiKeys.LEFT||action==UiKeys.RIGHT||action==UiKeys.OK||action==UiKeys.UP||action==UiKeys.DOWN)){
                if(e.getAction()==KeyEvent.ACTION_DOWN&&(dial!=0||e.getRepeatCount()==0)){
                    int turn=dial!=0?dial:action==UiKeys.RIGHT?1:action==UiKeys.LEFT?-1:0;
                    if(turn<0){playbackOffset=1;playbackRequested=true;maybeOpenGallery();}
                    else if(action==UiKeys.OK)compareOriginal(!showingOriginal);
                    else if(action==UiKeys.UP&&gate.state()==PreJpegGate.State.READY)
                        productHud.setVisibility(productHud.getVisibility()==View.VISIBLE?View.INVISIBLE:View.VISIBLE);
                }
                return true;
            }
            // a7r2.46: the record button used to be swallowed in the recipe
            // finder. Hand off to the existing recipe video session.
            if(recipeMode()&&A7R2Keys.movieKey(scan,key)){
                if(e.getAction()==KeyEvent.ACTION_DOWN&&e.getRepeatCount()==0){
                    event("video_key","scan="+scan+" ready="+manualAdjustable());
                    if(manualAdjustable())openVideoMode();
                }
                return true;
            }
            // The recipe finder has no exposure session during its first
            // second, but AF point selection and video do not need one. Only
            // the mode/WB/dial branches require manual.
            if(!reviewing&&(manual!=null||recipeMode())){
                if(scan==622||key==622||scan==A7R2Keys.WB||key==A7R2Keys.WB){
                    boolean wbKey=scan==A7R2Keys.WB||key==A7R2Keys.WB;
                    if(e.getAction()==KeyEvent.ACTION_DOWN&&e.getRepeatCount()==0
                            &&manualAdjustable()&&(af==null||!af.active())){
                        ManualExposure session=manual;
                        if(session==null){
                            event("manual_not_ready","key="+(wbKey?"WB":"C1")+" scan="+scan);
                        }else if(wbKey){
                            openWbMenu();
                        }else{
                            awaitManualControls();
                            try{session.cycleMode();getPreferences(0).edit().putString("exposure_mode",session.mode()).commit();
                                session.update(productHud.readout);productLive();
                                event("manual_mode_ui",session.state());}
                            catch(Throwable x){manualError("mode_cycle",x);}
                        }
                    }
                    return true;
                }
                int front=UiKeys.frontDial(e),rear=UiKeys.rearDial(e),control=UiKeys.controlDial(e);
                if(e.getAction()==KeyEvent.ACTION_DOWN&&(front!=0||rear!=0||control!=0)){
                    if(manualAdjustable()){
                        if(manual==null)event("manual_not_ready","dial scan="+scan);
                        else queueManual(front,rear,control,0,scan);
                    }else event("manual_dial_ignored","scan="+scan+" state="+gate.state()+" worker="+workerActive);
                    return true;
                }
                if(af!=null&&af.active()){
                    if(e.getAction()==KeyEvent.ACTION_DOWN&&e.getRepeatCount()==0){
                        int dx=0,dy=0;
                        if(action==UiKeys.UP)dy=-1;else if(action==UiKeys.DOWN)dy=1;
                        else if(action==UiKeys.LEFT)dx=-1;else if(action==UiKeys.RIGHT)dx=1;
                        if(dx!=0||dy!=0){
                            try{af.move(dx,dy);productHud.setAf(true,true,af.x(),af.y());event("af_key","scan="+scan+" x="+af.x()+" y="+af.y());}
                            catch(Throwable x){manualError("af_move",x);}
                            return true;
                        }
                        if(action==UiKeys.OK){
                            try{
                                awaitManualControls();af.confirm();
                                productHud.setAf(true,false,af.x(),af.y());productLive();
                                event("af_confirm_key","scan="+scan+" x="+af.x()+" y="+af.y());
                            }catch(Throwable x){manualError("af_confirm",x);}
                            return true;
                        }
                        if(action==UiKeys.BACK){
                            try{awaitManualControls();af.cancel();}catch(Throwable x){manualError("af_cancel",x);}
                            productHud.setAf(true,false,af.x(),af.y());productLive();event("af_cancel_key","scan="+scan);return true;
                        }
                    }
                    if(action==UiKeys.UP||action==UiKeys.DOWN||action==UiKeys.LEFT||action==UiKeys.RIGHT||action==UiKeys.OK||action==UiKeys.BACK)return true;
                }
                if(e.getAction()==KeyEvent.ACTION_DOWN&&e.getRepeatCount()==0){
                    if(action==UiKeys.DOWN){
                        if(af==null)event("af_unavailable","scan="+scan);
                        else if(manualAdjustable())try{af.enter();productHud.setAf(true,true,af.x(),af.y());productHud.showPersistentLiveHint("AF 选择 · 方向键移动 · 中键确认 · MENU 取消");productHud.update();event("af_enter_key","scan="+scan+" x="+af.x()+" y="+af.y());}
                        catch(Throwable x){manualError("af_enter",x);}
                        return true;
                    }
                    if(action==UiKeys.RIGHT){
                        if(manual==null)event("manual_not_ready","key=RIGHT scan="+scan);
                        else if(manualAdjustable())openWbMenu();
                        return true;
                    }
                    if(action==UiKeys.LEFT){
                        if(manualAdjustable())openVideoMode();
                        return true;
                    }
                    if(action==UiKeys.OK)return true;
                }
            }
            if(action==UiKeys.UP){
                if(e.getAction()==KeyEvent.ACTION_DOWN&&e.getRepeatCount()==0&&gate.state()==PreJpegGate.State.READY){
                    productHud.liveInfoVisible=!productHud.liveInfoVisible;productHud.hideLiveHint();productHud.update();
                    event("viewfinder_info","visible="+productHud.liveInfoVisible);
                }
                return true;
            }
            if(manual==null&&dial!=0){
                if(e.getAction()==KeyEvent.ACTION_DOWN)adjustExposure(dial,false);
                return true;
            }
            if(manual==null&&(action==UiKeys.LEFT||action==UiKeys.RIGHT||action==UiKeys.OK)){
                if(e.getAction()==KeyEvent.ACTION_DOWN&&e.getRepeatCount()==0){
                    adjustExposure(action==UiKeys.LEFT?-1:1,action==UiKeys.OK);
                }
                return true;
            }
        }
        // The M-mode capture guard rewrites scene/ISO/preview before the
        // shutter. Recipe captures are camera-native and already proven on
        // this body; running the guard there would re-touch the settings that
        // a7r2.45 kept out of the verified launch path.
        if(manual!=null&&!recipeMode()&&!reviewing&&gate.state()==PreJpegGate.State.READY){
            boolean half=scan==516||scan==517||key==516||key==517||key==KeyEvent.KEYCODE_FOCUS;
            if(half&&e.getAction()==KeyEvent.ACTION_DOWN&&e.getRepeatCount()==0&&!manual.captureSafe())
                guardManualCapture("half_press");
            else if((half||key==772||scan==772)&&e.getAction()==KeyEvent.ACTION_UP)
                reassertExposurePreview();
        }
        if(key==KeyEvent.KEYCODE_BACK||key==KeyEvent.KEYCODE_MENU||scan==514){if(e.getAction()==KeyEvent.ACTION_UP)onBackPressed();return true;}
        boolean shutter=scan==516||scan==517||scan==518||key==516||key==517||key==518||key==KeyEvent.KEYCODE_CAMERA;
        if(shutter){
            if(recipeMode())stage("shutter_key action="+e.getAction()+" scan="+scan+" gate="+gate.state());
            if(recipeMode()){
                // Never let Sony's native key handler see S1/S2 in recipe mode.
                // R0034E27: preview and recipe startup completed, then the UI
                // thread entered super.dispatchKeyEvent() for S1 (scan 516)
                // and stayed there for 30+ s (HEART ui_age_ms=30870) until the
                // process was killed. LUT mode can forward the keys because it
                // owns a CameraSequence/direct-shutter session; a recipe has
                // none, so the vendor path runs its own capture transaction
                // against the just-written Creative Style/WB and wedges.
                // Recipe Lab never forwards S1/S2 either: it calls
                // camera.autoFocus(null) on S1 and takePicture() on S2.
                int code=scan!=0?scan:key;
                boolean half=code==516||code==517||key==KeyEvent.KEYCODE_FOCUS;
                boolean full=code==518||key==KeyEvent.KEYCODE_CAMERA;
                if(e.getAction()==KeyEvent.ACTION_DOWN&&e.getRepeatCount()==0){
                    if(half){
                        stage("recipe_s1_down gate="+gate.state());
                        if(gate.state()==PreJpegGate.State.READY&&!recipeApplyBusy)recipeFocus(false,camera,scan);
                    }else if(full){
                        stage("recipe_s2_down gate="+gate.state());
                        if(recipe==null){event("recipe_missing","shot_ignored");}
                        else if(gate.state()==PreJpegGate.State.READY){
                            if(continuous)showViewfinder();
                            if(productHud!=null)productHud.hideLiveHint();
                            arm("physical_full_press");
                        }
                    }
                }else if(e.getAction()==KeyEvent.ACTION_UP){
                    if(half){
                        stage("recipe_s1_up");
                        recipeFocus(true,camera,scan);
                    }else if(full){
                        stage("recipe_s2_up");
                        // a7r2.48: Sony keeps the capture trigger latched after
                        // a bare takePicture() until the shutter is released and
                        // cancelTakePicture() runs. Without this every shot after
                        // the first fails with RuntimeException: takePicture
                        // failed. The capture worker runs this after the queued
                        // takePicture(), so the trigger is never cancelled before
                        // the frame request.
                        cancelRecipeTrigger("physical_release");
                    }
                }
                return true;
            }
            if(e.getAction()==KeyEvent.ACTION_UP)return super.dispatchKeyEvent(e);
            if(gate.state()!=PreJpegGate.State.READY)return true;
            if(continuous)showViewfinder();
            if(productHud!=null)productHud.hideLiveHint();
            if(e.getAction()==KeyEvent.ACTION_DOWN&&e.getRepeatCount()==0&&(scan==518||key==518||key==KeyEvent.KEYCODE_CAMERA)){
                ensureManualCaptureGuard("full_press");
                arm("physical_full_press");
            }
        }
        if(recipeMode()){
            // Recipe Lab consumes keys it does not bind; forwarding them lets
            // Sony's vendor handler start unrelated camera transactions (S1
            // was enough to wedge the UI). MOVIE/C1/AEL/DISP are the ones seen
            // on this body; log the first press of each scan for diagnosis.
            if(e.getAction()==KeyEvent.ACTION_DOWN&&e.getRepeatCount()==0)
                event("recipe_key_swallowed","key="+key+" scan="+scan);
            return true;
        }
        return super.dispatchKeyEvent(e);
    }
}
