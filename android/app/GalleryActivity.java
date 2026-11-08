package org.hongen.sonylutprobe;
import android.app.Activity;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.FrameLayout;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Browses recorded photo pairs without opening the camera interfaces. */
public final class GalleryActivity extends Activity {
    private final Handler ui=new Handler();private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final ExecutorService detailIo=Executors.newSingleThreadExecutor(new ThreadFactory(){public Thread newThread(Runnable r){Thread t=new Thread(r,"lut-gallery-detail");t.setDaemon(true);return t;}});
    private final ExecutorService thumbIo=Executors.newSingleThreadExecutor(new ThreadFactory(){public Thread newThread(Runnable r){Thread t=new Thread(r,"lut-gallery-thumbs");t.setDaemon(true);return t;}});
    private SonyReviewView image;private PhotoHud hud;private ContactSheetView sheet;private List<PhotoCatalog.Pair> photos;
    private SonyPhotoPreview loader;private PhotoCatalog catalog;private int index,direction=1;
    private final Object loaderLock=new Object();
    private Thread.UncaughtExceptionHandler previousHandler;
    private boolean original,destroyed,reading,active,leaving,infoVisible=true,zoomed,deleteConfirm,deleting,photosChanged,sheetOpen;
    private final Map<String,Bitmap> thumbs=new LinkedHashMap<String,Bitmap>(16,.75f,true);
    private final Set<String> thumbLoading=new HashSet<String>(),thumbFailed=new HashSet<String>();
    private long thumbBytes;
    private int staleCount;
    private float zoomTarget=1f;
    private String desiredKey,displayedKey,drawKey,detailKey;private long requestAt,drawAt;private boolean drawHit;
    private final Set<String> failedPrefetch=new HashSet<String>();
    private final Set<String> attempted=new HashSet<String>();
    private final Set<String> detailLoading=new HashSet<String>();
    // Full-42MP zoom detail: one raw-JPEG region tile at a time. The gallery
    // heap is only 24 MB, so a whole-42MP bitmap is impossible; the tile path
    // decodes just what the panel shows.
    private Bitmap tileBitmap;private String tileKey,tileLoadingKey;private float tileZoom;
    private final Runnable tileRefresh=new Runnable(){public void run(){if(active&&!destroyed&&zoomed)ensureTile();}};
    // 1x playback quality: Sony's screen nail is a compressed preview. Decode
    // the original JPEG once at ~1/8 scale (994x663, 2.6 MB for 42MP) and swap
    // it in for the nail. One entry only — the gallery heap is 24 MB.
    private Bitmap baseBitmap;private String baseKey,baseLoadingKey;
    private final Runnable baseUpgrade=new Runnable(){public void run(){ensureBase();}};
    private final PreviewCache<Bitmap> cache=new PreviewCache<Bitmap>(7L*1024*1024,new PreviewCache.Disposal<Bitmap>(){public void release(Bitmap bitmap){bitmap.recycle();}});
    private final PreviewCache<Bitmap> detailCache=new PreviewCache<Bitmap>(8L*1024*1024,new PreviewCache.Disposal<Bitmap>(){public void release(Bitmap bitmap){bitmap.recycle();}});
    @Override public void onCreate(Bundle saved){super.onCreate(saved);
        Lang.init(this);
        // The camera can reset while playback decodes images. Keep a durable
        // Java-side trace next to KEYLOG.TXT so the next report shows the last
        // step reached, even when the framework log is lost.
        previousHandler=Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler(){
            public void uncaughtException(Thread thread,Throwable error){
                try{
                    File dir=new File(Environment.getExternalStorageDirectory(),"SONYLUT");
                    PrintWriter out=new PrintWriter(new FileOutputStream(new File(dir,"GALLERY.TXT"),true));
                    try{out.println(SystemClock.elapsedRealtime()+" crash thread="+thread.getName()+" pid="+android.os.Process.myPid());
                        error.printStackTrace(out);out.flush();}finally{out.close();}
                }catch(Throwable ignored){}
                if(error instanceof OutOfMemoryError){
                    // Killing this process also tears down the PMCA host and
                    // shows up as a camera reset. Leave playback instead; the
                    // decode paths already reclaim memory and convert OOM to a
                    // normal error, so this is only the last-resort net.
                    android.util.Log.w("LutUI","gallery_oom_leave",error);
                    try{ui.post(new Runnable(){public void run(){
                        if(destroyed)return;
                        active=false;
                        if(hud!=null){hud.mode="error";hud.detail="内存不足，已停止回放";
                            hud.progress="照片仍在内存卡中，请重新进入回放";hud.footer="MENU 返回";hud.update();}
                        finish();
                    }});}catch(Throwable ignored){}
                    return;
                }
                if(previousHandler!=null)previousHandler.uncaughtException(thread,error);
                else android.os.Process.killProcess(android.os.Process.myPid());
            }});
        galleryLog("create");
        FrameLayout frame=new FrameLayout(this);frame.setBackgroundColor(Color.BLACK);
        hud=new PhotoHud(this);image=new SonyReviewView(this){@Override protected void pixelAspectChanged(float value){hud.pixelAspect=value;hud.update();}
            @Override protected void onDraw(Canvas c){super.onDraw(c);if(drawKey!=null&&drawKey.equals(displayedKey)){android.util.Log.i("LutUI","gallery_frame file="+new File(drawKey).getName()+" request_to_draw_ms="+(SystemClock.elapsedRealtime()-drawAt)+" cache_hit="+drawHit+" cache_bytes="+cache.bytes());drawKey=null;}}};
        frame.addView(image,new FrameLayout.LayoutParams(-1,-1));frame.addView(hud,new FrameLayout.LayoutParams(-1,-1));
        sheet=new ContactSheetView(this);sheet.setProvider(new ContactSheetView.Provider(){
            public Bitmap thumb(int position){return thumbnailAt(position);}
            public void request(int position){requestThumbnail(position);}
        });sheet.setVisibility(View.GONE);frame.addView(sheet,new FrameLayout.LayoutParams(-1,-1));setContentView(frame);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);hud.detail="正在打开照片";
        io.execute(new Runnable(){public void run(){try{loader=new SonyPhotoPreview(GalleryActivity.this);catalog=new PhotoCatalog(ProductFiles.photos(),Environment.getExternalStorageDirectory());
            final List<PhotoCatalog.Pair> all=catalog.list();
            // Receipts outlive photographs. A cleaned/recreated DCIM leaves
            // old receipts pointing at deleted files; never let the first stale
            // receipt hide every later valid photo.
            final List<PhotoCatalog.Pair> loaded=new ArrayList<PhotoCatalog.Pair>();
            for(PhotoCatalog.Pair pair:all)if(pair.original.isFile()||pair.graded.isFile())loaded.add(pair);
            final int stale=all.size()-loaded.size();
            android.util.Log.i("LutUI","gallery_catalog receipts="+all.size()+" existing="+loaded.size()+" stale="+stale);
            ui.post(new Runnable(){public void run(){if(destroyed)return;photos=loaded;staleCount=stale;String id=getIntent().getStringExtra("pair_id");for(int i=0;i<photos.size();i++)if(photos.get(i).id.equals(id)){index=i;break;}
                index=Math.max(0,Math.min(Math.max(0,photos.size()-1),index+getIntent().getIntExtra("page_offset",0)));load();}});
        }catch(final Exception e){ui.post(new Runnable(){public void run(){error(e);}});}}});
    }
    private void error(Exception e){if(destroyed)return;hud.mode="error";hud.detail=e.getMessage()==null?"照片读取失败":e.getMessage();hud.progress="";hud.footer="MENU 返回照片列表";hud.update();}
    /** Step trace for reboot diagnosis: one short line per stage, appended to
     * SONYLUT/GALLERY.TXT. Never called from a hot draw path. */
    private void galleryLog(String message){
        android.util.Log.i("LutUI","gallery_step "+message);
        Runtime runtime=Runtime.getRuntime();
        final String line=SystemClock.elapsedRealtime()+" "+message
                +" heap="+(runtime.totalMemory()-runtime.freeMemory())/1024+"K"
                +" max="+runtime.maxMemory()/1024+"K\n";
        try{
            io.execute(new Runnable(){public void run(){
                try{
                    File dir=new File(Environment.getExternalStorageDirectory(),"SONYLUT");
                    if(!dir.exists())dir.mkdirs();
                    FileOutputStream out=new FileOutputStream(new File(dir,"GALLERY.TXT"),true);
                    try{out.write(line.getBytes("UTF-8"));out.flush();}finally{out.close();}
                }catch(Throwable x){android.util.Log.w("LutUI","gallery_log_failed",x);}
            }});
        }catch(java.util.concurrent.RejectedExecutionException ignored){}
    }
    private File selectedFile(){
        PhotoCatalog.Pair p=photos.get(index);
        File file=original?p.original:p.graded;
        if(!file.isFile()){
            File other=original?p.graded:p.original;
            if(other.isFile())file=other;
        }
        return file;
    }
    private void load(){
        deleteConfirm=false;deleting=false;
        if(sheet!=null)sheet.setPhotos(photos);
        if(photos==null||photos.isEmpty()){
            zoomed=false;zoomTarget=1f;detailKey=null;detailCache.pin(null);image.setImageBitmap(null);image.resetZoom();dropBase();
            hud.mode="error";hud.name="照片";
            if(staleCount>0){
                hud.detail="照片文件已不在 SD 卡";
                hud.progress=staleCount+" 组拍摄记录失效（DCIM 被清理或照片被移动）";
            }else{
                hud.detail="还没有照片";hud.progress="拍下第一张照片后，在这里回看";
            }
            hud.footer="MENU 返回";hud.update();return;
        }
        PhotoCatalog.Pair selected=photos.get(index);
        if(selected.single)original=false;
        else if(original&&!selected.original.isFile()&&selected.graded.isFile())original=false;
        else if(!original&&!selected.graded.isFile()&&selected.original.isFile())original=true;
        zoomed=false;zoomTarget=1f;image.resetZoom();
        desiredKey=selectedFile().getPath();requestAt=SystemClock.elapsedRealtime();drawKey=null;failedPrefetch.remove(desiredKey);attempted.clear();
        Bitmap hit=cache.get(desiredKey);
        if(hit!=null)show(hit,true);
        else{hud.setVisibility(View.VISIBLE);hud.mode=displayedKey==null?"loading":"review";hud.name=photos.get(index).lut;
            hud.detail="正在打开"+(selected.single?"配方照片":(original?"原片":"冲洗成片"));hud.tag="加载中";hud.progress="";hud.update();}
        schedule();
    }
    private void show(Bitmap bitmap,boolean hit){
        PhotoCatalog.Pair pair=photos.get(index);
        // A zoom can start before the screen nail finishes. If the detail
        // bitmap already arrived for this photo, never downgrade it (and never
        // reset the user's zoom) when the slower nail completes.
        boolean detailDisplayed=desiredKey.equals(detailKey);
        boolean samePhoto=desiredKey.equals(displayedKey);
        boolean firstShown=displayedKey==null;
        cache.pin(desiredKey);displayedKey=desiredKey;
        drawKey=desiredKey;drawAt=requestAt;drawHit=hit;
        if(!detailDisplayed&&!(zoomed&&samePhoto)){
            image.setImageBitmap(bitmap);image.invalidate();
            // The previous high-resolution bitmap is no longer on screen, so
            // it may now be evicted safely by a stale detail completion.
            detailCache.pin(null);detailKey=null;
        }
        // Storage is newest-first; display positions follow capture order, oldest = 1.
        int ordinal=photos.size()-index;
        hud.setVisibility(infoVisible?View.VISIBLE:View.INVISIBLE);hud.mode="review";hud.name=pair.lut;hud.tag=ordinal+" / "+photos.size();hud.original=original;hud.single=pair.single;
        updateHud();
        android.util.Log.i("LutUI","gallery_selected file="+selectedFile().getName()+" ordinal="+ordinal+" total="+photos.size()+" cache_hit="+hit+" request_ms="+(SystemClock.elapsedRealtime()-requestAt));
        // Playback starts from the fast screen nail. At 1x the nail is already
        // higher than panel resolution; the original JPEG is consulted only
        // for zoom, as region tiles.
        ui.removeCallbacks(detailUpgrade);
        if(firstShown)hud.openCurtain();
        // At fit size Sony's 1024 px screen nail already covers this 640x480
        // panel. Original-resolution detail is fetched as region tiles only
        // while zoomed (see ensureTile).
        releaseTile();
        if(baseKey!=null&&!baseKey.equals(desiredKey))dropBase();
        if(zoomed){ui.removeCallbacks(tileRefresh);ui.postDelayed(tileRefresh,60);}
        else if(!detailDisplayed){ui.removeCallbacks(baseUpgrade);ui.postDelayed(baseUpgrade,120);}
    }
    private final Runnable detailUpgrade=new Runnable(){public void run(){
        if(!active||destroyed||zoomed||sheetOpen)return;
    }};
    private void updateHud(){
        if(hud==null||photos==null||photos.isEmpty())return;
        PhotoCatalog.Pair pair=photos.get(index);
        if(pair.single){
            hud.detail=selectedFile().getName()+" · 相机直出 JPEG"+(pair.camera.length()==0?"":" · "+pair.camera)+(zoomed?" · 放大 "+zoomText(zoomTarget):"");
            hud.footer=zoomed?"转盘缩放  方向键平移  C3/放大键还原":
                    "←→/拨轮 翻图  转盘/C3 放大  ↑ 信息  ↓ 印相  删除键删除";
        }else{
            hud.detail=selectedFile().getName()+" · EXTRA FINE"+(zoomed?" · 放大 "+zoomText(zoomTarget):"");
            hud.footer=zoomed?"转盘缩放  方向键平移  C3/放大键还原  ● 对照":
                    "←→/拨轮 翻图  转盘/C3 放大  ● 对照  ↑ 信息  ↓ 印相  删除键删除";
        }
        hud.update();
    }
    private static String zoomText(float zoom){
        return (Math.abs(zoom-Math.round(zoom))<.01f?Integer.toString(Math.round(zoom)):
                String.format(Locale.US,"%.1f",zoom))+"x";
    }
    private void toggleZoom(){
        if(photos==null||photos.isEmpty())return;
        zoomTo(zoomed?1f:2f);
    }
    private void zoomTo(float target){
        if(photos==null||photos.isEmpty()||destroyed)return;
        float level=Math.max(1f,Math.min(8f,target));
        zoomTarget=level;zoomed=level>1.001f;
        image.animateZoom(level,new Runnable(){public void run(){if(!destroyed&&hud!=null)updateHud();onZoomSettled();}});
        updateHud();
        if(!zoomed)releaseTile();
        android.util.Log.i("LutUI","gallery_zoom target="+level+" detail="+(tileKey==null?"nail":"tile"));
    }
    private void onZoomSettled(){
        if(destroyed)return;
        if(!zoomed)restoreBase();else ensureTile();
    }
    /** Return to the whole-photo view after a zoom-out. Prefer the decoded
     * original-quality base over Sony's compressed screen nail. */
    private void restoreBase(){
        String key=desiredKey;
        Bitmap base=key!=null&&key.equals(baseKey)?baseBitmap:null;
        boolean fromOriginal=base!=null;
        if(base==null&&key!=null)base=cache.get(key);
        if(base!=null){
            image.setImageBitmap(base);
            if(fromOriginal)detailKey=key;else cache.pin(key);
            displayedKey=key;drawKey=null;
            releaseTile();
            hud.update();
            if(!fromOriginal){ui.removeCallbacks(baseUpgrade);ui.postDelayed(baseUpgrade,150);}
        }else if(key!=null){
            ui.removeCallbacks(tileRefresh);
            schedule();
        }
    }
    /** Recycle a tile only once it is no longer the visible drawable. */
    private void releaseTile(){
        if(tileBitmap==null)return;
        if(active&&!destroyed&&image!=null&&image.displays(tileBitmap))return;
        tileBitmap.recycle();tileBitmap=null;tileKey=null;
    }
    private void dropBase(){
        Bitmap old=baseBitmap;baseBitmap=null;baseKey=null;
        if(old!=null&&(image==null||!image.displays(old)))old.recycle();
    }
    /** Swap the screen nail for a whole-image decode of the saved JPEG. The
     * nail is Sony's compressed preview; decoding the original at 1/8 scale
     * restores 42MP-derived detail without exceeding the 24 MB gallery heap. */
    private void ensureBase(){
        if(!active||destroyed||zoomed||sheetOpen||deleteConfirm||leaving)return;
        if(photos==null||photos.isEmpty()||loader==null)return;
        final String key=desiredKey;if(key==null)return;
        if(key.equals(baseKey)&&baseBitmap!=null){
            if(!image.displays(baseBitmap)){
                image.replaceImageBitmap(baseBitmap);detailKey=key;displayedKey=key;drawKey=null;hud.update();
            }
            return;
        }
        if(baseLoadingKey!=null)return;
        final File file=new File(key);
        // The 42 MP decode needs a clean arena. Contact prints, neighbouring
        // prefetched nails and the idle detail cache are all expendable and
        // together were enough to exhaust the 24 MB heap (GALLERY.TXT OOM).
        clearThumbs();
        reclaimNails(key);
        detailCache.pin(null);detailCache.clear();
        baseLoadingKey=key;
        galleryLog("base_begin file="+file.getName()+" heap="+heapKb()+"K");
        try{
            detailIo.execute(new Runnable(){public void run(){
                Bitmap loaded=null;Exception failure=null;
                try{loaded=loader.loadBase(file);}
                catch(OutOfMemoryError e){failure=new IOException("内存不足，无法读取原图");}
                catch(Exception e){failure=e;}
                final Bitmap base=loaded;final Exception error=failure;
                ui.post(new Runnable(){public void run(){
                    baseLoadingKey=null;
                    if(destroyed){if(base!=null)base.recycle();return;}
                    if(error!=null){galleryLog("base_failed file="+file.getName()+" "+error);return;}
                    if(base==null)return;
                    if(!key.equals(desiredKey)||zoomed||sheetOpen||deleteConfirm||!active){
                        base.recycle();galleryLog("base_obsolete file="+file.getName());return;
                    }
                    if(baseBitmap!=null&&baseBitmap!=base&&!image.displays(baseBitmap))baseBitmap.recycle();
                    baseBitmap=base;baseKey=key;
                    image.replaceImageBitmap(base);detailKey=key;displayedKey=key;drawKey=null;
                    // The nail is no longer on screen; drop its cache slot before
                    // the next page's decode starts.
                    cache.remove(key);
                    galleryLog("base_ready file="+file.getName()+" bitmap="+base.getWidth()+"x"+base.getHeight()+" heap="+heapKb()+"K");
                    hud.update();
                }});
            }});
        }catch(java.util.concurrent.RejectedExecutionException e){
            baseLoadingKey=null;
            android.util.Log.w("LutUI","gallery_base_rejected file="+file.getName());
        }
    }
    /** Decode the currently visible part of the original JPEG at a resolution
     * matched to the panel. The 42MP file never lands in memory as a whole. */
    private void ensureTile(){
        if(!active||destroyed||loader==null||photos==null||photos.isEmpty()||!zoomed)return;
        final String key=desiredKey;
        if(key==null)return;
        final float[] visible=image.visibleFraction(.22f);
        if(visible==null)return;
        if(key.equals(tileKey)&&Math.abs(image.zoom()-tileZoom)<.05f&&image.tileCovers(visible))return;
        if(tileLoadingKey!=null)return;
        final File file=new File(key);
        tileLoadingKey=key;
        // The nail cache and the contact-sheet thumbs can together hold more
        // than 16 MB. Free everything except the photo being inspected before
        // allocating a detail tile on the 24 MB heap.
        clearThumbs();
        cache.retain(key);
        galleryLog("tile_begin file="+file.getName()+" visible="+visible[0]+","+visible[1]+","+visible[2]+","+visible[3]);
        try{
            detailIo.execute(new Runnable(){public void run(){
                Bitmap loaded=null;Exception failure=null;
                int orientation=1,rawW=0,rawH=0,sample=1;int[] rect=null,oriented=null;
                try{
                    int[] raw=SonyPhotoPreview.rawSize(file);
                    rawW=raw[0];rawH=raw[1];orientation=SonyPhotoPreview.orientation(file);
                    if(!PhotoTiles.supported(orientation))throw new IOException("照片方向不支持原图放大");
                    oriented=PhotoTiles.orientedSize(rawW,rawH,orientation);
                    rect=PhotoTiles.rawBounds(rawW,rawH,orientation,
                            visible[0]*oriented[0],visible[1]*oriented[1],
                            visible[2]*oriented[0],visible[3]*oriented[1]);
                    sample=PhotoTiles.tileSample(rect[2]-rect[0],rect[3]-rect[1],1400,1600000);
                    loaded=loader.loadRegion(file,rect,sample);
                }catch(OutOfMemoryError e){failure=new IOException("内存不足，无法放大原图");}
                catch(Exception e){failure=e;}
                final Bitmap tile=loaded;final Exception error=failure;
                final int o=orientation,rw=rawW,rh=rawH,sm=sample;final int[] rr=rect,os=oriented;
                ui.post(new Runnable(){public void run(){
                    tileLoadingKey=null;
                    if(destroyed){if(tile!=null)tile.recycle();return;}
                    if(error!=null){galleryLog("tile_failed file="+file.getName()+" "+error);return;}
                    if(tile==null)return;
                    if(!key.equals(desiredKey)||!zoomed){tile.recycle();galleryLog("tile_obsolete file="+file.getName());return;}
                    Bitmap old=tileBitmap;
                    image.setTileBitmap(tile,os[0],os[1],o,rw,rh,new android.graphics.Rect(rr[0],rr[1],rr[2],rr[3]),sm);
                    tileBitmap=tile;tileKey=key;tileZoom=image.zoom();
                    if(old!=null&&old!=tile&&!image.displays(old))old.recycle();
                    galleryLog("tile_ready file="+file.getName()+" sample="+sm+" bitmap="+tile.getWidth()+"x"+tile.getHeight());
                    android.util.Log.i("LutUI","gallery_tile_ready file="+file.getName()+" zoom="+zoomTarget
                            +" bitmap="+tile.getWidth()+"x"+tile.getHeight()+" heap_used="+heapKb()+"K");
                    if(!image.tileCovers(image.visibleFraction(0f))){
                        ui.removeCallbacks(tileRefresh);ui.postDelayed(tileRefresh,120);
                    }
                }});
            }});
        }catch(java.util.concurrent.RejectedExecutionException e){
            tileLoadingKey=null;
            android.util.Log.w("LutUI","gallery_tile_rejected file="+file.getName());
        }
    }
    private static int heapKb(){
        Runtime runtime=Runtime.getRuntime();
        return (int)((runtime.totalMemory()-runtime.freeMemory())/1024);
    }
    /** Drop cached screen nails without ever recycling the bitmap currently
     * on screen. The photo being decoded is kept when it is already cached;
     * otherwise the displayed frame (which may still be the previous page)
     * stays pinned while obsolete neighbours are released. */
    private void reclaimNails(String keep){
        if(keep!=null&&cache.contains(keep))cache.retain(keep);
        else if(displayedKey!=null&&cache.contains(displayedKey))cache.retain(displayedKey);
        else cache.clear();
    }
    private boolean knownKey(String key){
        if(photos==null||key==null)return false;
        for(PhotoCatalog.Pair pair:photos)
            if(key.equals(pair.original.getPath())||key.equals(pair.graded.getPath()))return true;
        return false;
    }
    private void ensureDetail(){
        if(!active||destroyed||loader==null||photos==null||photos.isEmpty())return;
        final String key=desiredKey;
        if(key==null)return;
        Bitmap cached=detailCache.get(key);
        if(cached!=null){showDetail(key,cached);return;}
        if(detailLoading.contains(key))return;
        detailLoading.add(key);
        final File file=new File(key);
        galleryLog("detail_begin file="+file.getName());
        try{
            detailIo.execute(new Runnable(){public void run(){
                Bitmap loaded=null;Exception failure=null;
                try{loaded=loader.loadDetail(file);}
                catch(OutOfMemoryError e){failure=new IOException("内存不足，无法加载原图");}
                catch(Exception e){failure=e;}
                final Bitmap detail=loaded;final Exception error=failure;
                ui.post(new Runnable(){public void run(){
                    detailLoading.remove(key);
                    if(destroyed){if(detail!=null)detail.recycle();return;}
                    if(error!=null){galleryLog("detail_failed file="+file.getName()+" "+error);return;}
                    if(!knownKey(key)){if(detail!=null)detail.recycle();return;}
                    galleryLog("detail_ready file="+file.getName()+" "+(detail==null?"null":detail.getWidth()+"x"+detail.getHeight()));
                    if(detail==null)return;
                    if(!detailCache.put(key,detail,(long)detail.getRowBytes()*detail.getHeight())){
                        detail.recycle();return;
                    }
                    if(key.equals(desiredKey)&&active)showDetail(key,detail);
                }});
            }});
        }catch(java.util.concurrent.RejectedExecutionException e){
            detailLoading.remove(key);
            android.util.Log.w("LutUI","gallery_detail_rejected file="+file.getName());
        }
    }
    private void showDetail(String key,Bitmap detail){
        if(destroyed||photos==null||!key.equals(desiredKey)||!active)return;
        detailCache.pin(key);
        image.replaceImageBitmap(detail);
        detailKey=key;
        updateHud();
    }
    private void openSheet(){
        if(photos==null||photos.isEmpty())return;
        ui.removeCallbacks(detailUpgrade);ui.removeCallbacks(tileRefresh);ui.removeCallbacks(baseUpgrade);
        if(zoomed)zoomTo(1f);
        thumbFailed.clear();
        sheetOpen=true;sheet.setPhotos(photos);sheet.setSelection(index);
        hud.setVisibility(View.INVISIBLE);sheet.setVisibility(View.VISIBLE);sheet.invalidate();
        galleryLog("contacts_open index="+index+" total="+photos.size());
        android.util.Log.i("LutUI","gallery_contacts_open index="+index+" total="+photos.size());
    }
    private void closeSheet(){
        if(!sheetOpen)return;
        sheetOpen=false;sheet.setVisibility(View.GONE);
        if(hud!=null)hud.setVisibility(infoVisible?View.VISIBLE:View.INVISIBLE);
        galleryLog("contacts_close");
        android.util.Log.i("LutUI","gallery_contacts_close");
        schedule();
    }
    private void openSheetSelection(){
        int chosen=sheet.selection();closeSheet();
        if(chosen>=0&&chosen<photos.size()){
            if(chosen!=index){index=chosen;direction=1;original=false;load();}
            // Even when the contact sheet opens the frame already behind it,
            // play the shutter so the transition has a visible entrance.
            hud.openCurtain();
            galleryLog("contacts_selected index="+chosen);
        }
    }
    private Bitmap thumbnailAt(int position){
        if(photos==null||position<0||position>=photos.size())return null;
        String id=photos.get(position).id;
        Bitmap bitmap=thumbs.get(id);
        if(bitmap==null&&!thumbLoading.contains(id)&&!thumbFailed.contains(id))requestThumbnail(position);
        return bitmap;
    }
    private void requestThumbnail(int position){
        if(destroyed||leaving||photos==null||position<0||position>=photos.size())return;
        final PhotoCatalog.Pair pair=photos.get(position);final String id=pair.id;
        if(thumbs.containsKey(id)||thumbLoading.contains(id)||thumbFailed.contains(id))return;
        thumbLoading.add(id);
        final File root=ProductFiles.photos();
        galleryLog("thumb_request id="+id+" pos="+position);
        try{
            thumbIo.execute(new Runnable(){public void run(){
                Bitmap small=null;boolean backfilled=false;
                try{
                    // Preferred: the 480x320 contact print written during
                    // capture. It is a small JPEG and never touches the 42MP
                    // original.
                    small=PhotoThumbs.load(root,id,ContactSheetView.THUMB_W,ContactSheetView.THUMB_H);
                    if(small==null){
                        // Photo captured before thumbs existed. Use the same
                        // Sony screen nail that page playback already reads,
                        // not a full JPEG decode: repeated 42MP decodes while
                        // the contact sheet fills were resetting the camera.
                        File source=pair.graded.isFile()?pair.graded:(pair.original.isFile()?pair.original:null);
                        Bitmap nail=null;backfilled=true;
                        if(source!=null&&loader!=null){
                            try{synchronized(loaderLock){nail=loader.load(source);}}
                            catch(Exception e){android.util.Log.w("LutUI","gallery_thumb_nail_failed file="+source.getName(),e);}
                        }
                        if(nail!=null){
                            Bitmap contact=null;
                            try{
                                contact=Bitmap.createScaledBitmap(nail,PhotoThumbs.W,PhotoThumbs.H,true);
                                if(contact!=null){
                                    PhotoThumbs.save(root,id,contact);
                                    galleryLog("thumb_backfill id="+id+" ok="+(contact!=null));
                                }
                                small=Bitmap.createScaledBitmap(nail,ContactSheetView.THUMB_W,ContactSheetView.THUMB_H,true);
                            }finally{
                                if(contact!=null&&contact!=nail)contact.recycle();
                                if(nail!=small)nail.recycle();
                            }
                        }
                    }
                }catch(OutOfMemoryError e){
                    android.util.Log.w("LutUI","gallery_thumb_oom",e);galleryLog("thumb_oom id="+id);
                }catch(Throwable e){
                    android.util.Log.w("LutUI","gallery_thumb_failed",e);galleryLog("thumb_error id="+id+" "+e);
                }
                // Sony's screen-nail service is shared with the camera. Bursting
                // a whole contact sheet at it can stall the media stack, so a
                // backfilled (non-cached) nail gets a short breath between reads.
                if(backfilled)try{Thread.sleep(160);}catch(InterruptedException ignored){}
                final Bitmap thumb=small;
                ui.post(new Runnable(){public void run(){
                    thumbLoading.remove(id);
                    if(destroyed){if(thumb!=null)thumb.recycle();return;}
                    if(thumb==null){thumbFailed.add(id);galleryLog("thumb_missing id="+id);return;}
                    addThumb(id,thumb);
                    galleryLog("thumb_ready id="+id+" bytes="+(thumb.getRowBytes()*thumb.getHeight()));
                    if(sheetOpen)sheet.invalidate();
                }});
            }});
        }catch(java.util.concurrent.RejectedExecutionException e){
            thumbLoading.remove(id);
        }
    }
    private void addThumb(String id,Bitmap bitmap){
        Bitmap old=thumbs.put(id,bitmap);
        if(old!=null)old.recycle();
        thumbBytes+=bitmap.getRowBytes()*bitmap.getHeight();
        Iterator<Map.Entry<String,Bitmap>> it=thumbs.entrySet().iterator();
        while(thumbBytes>3L*1024*1024&&thumbs.size()>1&&it.hasNext()){
            Map.Entry<String,Bitmap> entry=it.next();
            if(entry.getKey().equals(id))continue;
            thumbBytes-=entry.getValue().getRowBytes()*entry.getValue().getHeight();
            entry.getValue().recycle();it.remove();
        }
    }
    private void clearThumbs(){
        for(Bitmap bitmap:thumbs.values())bitmap.recycle();
        thumbs.clear();thumbBytes=0;thumbLoading.clear();
    }
    private void pan(int dx,int dy){
        if(!zoomed)return;
        // Arrow keys move the viewing window, not the picture: RIGHT/DOWN
        // reveal the right/lower part of the frame, so the bitmap shifts the
        // other way. The dial still zooms (control wheel, a7r2.31).
        image.panBy(-dx*(image.getWidth()/7f),-dy*(image.getHeight()/7f));
        updateHud();
        ui.removeCallbacks(tileRefresh);ui.postDelayed(tileRefresh,150);
        android.util.Log.i("LutUI","gallery_pan dx="+dx+" dy="+dy+" zoom="+image.zoom());
    }
    private void showDeleteConfirm(){
        deleteConfirm=true;hud.mode="review";
        PhotoCatalog.Pair pair=photos.get(index);
        hud.detail=pair.single?"删除这张配方照片？":"删除整组？原片 + 冲洗成片都会删除";
        hud.footer="● 删除   MENU/BACK 取消";hud.update();
        android.util.Log.i("LutUI","gallery_delete_confirm index="+index+" pair="+(photos.isEmpty()?"none":photos.get(index).id));
    }
    private void startDelete(){
        if(photos==null||photos.isEmpty()||deleting)return;
        final PhotoCatalog.Pair pair=photos.get(index);deleting=true;
        hud.detail=pair.single?"正在删除照片…":"正在删除整组…";hud.footer="请稍候";hud.update();
        io.execute(new Runnable(){public void run(){final Exception[] failure=new Exception[1];
            try{catalog.delete(pair);}
            catch(Exception e){failure[0]=e;}
            ui.post(new Runnable(){public void run(){
                deleting=false;deleteConfirm=false;
                if(destroyed)return;
                if(failure[0]!=null){error(failure[0]);return;}
                photosChanged=true;
                image.setImageBitmap(null);cache.clear();detailCache.pin(null);detailCache.clear();
                dropBase();baseLoadingKey=null;
                tileKey=null;tileLoadingKey=null;releaseTile();
                detailKey=null;detailLoading.clear();zoomTarget=1f;zoomed=false;
                ui.removeCallbacks(tileRefresh);ui.removeCallbacks(baseUpgrade);
                photos.remove(index);
                hud.toast((pair.single?"已删除照片 · 还剩 ":"已删除整组 · 还剩 ")+photos.size()+" 张");
                if(photos.isEmpty()){load();return;}
                index=Math.max(0,Math.min(photos.size()-1,index));
                load();
                android.util.Log.i("LutUI","gallery_deleted pair="+pair.id+" remaining="+photos.size());
            }});
        }});
    }
    private void addCandidate(List<File> files,int position,boolean source){
        if(position>=0&&position<photos.size()){
            PhotoCatalog.Pair p=photos.get(position);File file=source?p.original:p.graded;
            if(file!=null&&!files.contains(file))files.add(file);
        }
    }
    private List<File> plan(){
        List<File> files=new ArrayList<File>();addCandidate(files,index,original);
        // Navigation always opens the graded photo, even when comparing an original.
        addCandidate(files,index+direction,false);addCandidate(files,index-direction,false);
        addCandidate(files,index,!original);
        if(index==0||index==photos.size()-1)addCandidate(files,index+2*direction,false);
        return files;
    }
    private void schedule(){
        if(!active||destroyed||reading||photos==null||photos.isEmpty()||sheetOpen||deleteConfirm||leaving)return;
        List<File> window=plan();
        // Refresh existing members in priority order before inserting another
        // member. Otherwise eager prefetch evicts its own next/previous page.
        for(int i=window.size()-1;i>=0;i--)cache.get(window.get(i).getPath());
        File candidate=null;
        for(File f:window)if(!cache.contains(f.getPath())&&!failedPrefetch.contains(f.getPath())
                &&(f.getPath().equals(desiredKey)||!attempted.contains(f.getPath()))){candidate=f;break;}
        if(candidate==null)return;
        final File file=candidate;final String key=file.getPath();
        int used=heapKb(),maxKb=(int)(Runtime.getRuntime().maxMemory()/1024);
        // Prefetch only while there is room for a decode plus its rotated copy.
        // The photo the user is actually looking at is always allowed, after
        // dropping every cache that can be rebuilt.
        if(!key.equals(desiredKey)&&used>maxKb-7*1024){
            galleryLog("prefetch_skip file="+file.getName()+" heap="+used+"K");
            return;
        }
        if(key.equals(desiredKey)&&used>maxKb-6*1024){
            clearThumbs();
            reclaimNails(desiredKey);
            detailCache.pin(null);detailCache.clear();
            galleryLog("nail_reclaim file="+file.getName()+" heap="+used+"K");
        }
        reading=true;
        galleryLog("nail_begin file="+file.getName()+" heap="+heapKb()+"K");
        // Exactly one native read in flight. On completion choose from the latest
        // desired page, never enqueue a history of obsolete key presses.
        io.execute(new Runnable(){public void run(){Bitmap result=null;Exception failure=null;
            try{synchronized(loaderLock){result=loader.load(file);}}
            catch(OutOfMemoryError e){failure=new IOException("内存不足，无法读取预览");}
            catch(Exception e){failure=e;}
            final Bitmap loaded=result;final Exception error=failure;
            ui.post(new Runnable(){public void run(){reading=false;attempted.add(key);
                if(destroyed){if(loaded!=null)loaded.recycle();return;}
                if(leaving){if(loaded!=null)loaded.recycle();finish();return;}
                if(error!=null){failedPrefetch.add(key);android.util.Log.w("LutUI","gallery_load_failed file="+file.getName(),error);
                    if(key.equals(desiredKey))error(error);
                }else{
                    boolean wanted=false;for(File f:plan())if(f.getPath().equals(key)){wanted=true;break;}
                    List<File> current=plan();for(int i=current.size()-1;i>=0;i--)cache.get(current.get(i).getPath());
                    if(!wanted){loaded.recycle();android.util.Log.i("LutUI","gallery_obsolete file="+file.getName());}
                    else if(cache.put(key,loaded,(long)loaded.getRowBytes()*loaded.getHeight())){
                        android.util.Log.i("LutUI","gallery_cached file="+file.getName()+" cache_bytes="+cache.bytes());
                        if(key.equals(desiredKey))show(loaded,false);
                    }else{failedPrefetch.add(key);if(key.equals(desiredKey))error(new IOException("照片预览超过缓存容量"));}
                }
                schedule();
            }});
        }});
    }
    private void leave(boolean capture){
        if(leaving)return;leaving=true;active=false;
        // Do not let a background full-JPEG decode hold the SD card while the
        // capture process reopens the camera.
        detailIo.shutdownNow();thumbIo.shutdownNow();ui.removeCallbacks(detailUpgrade);
        if(capture||photosChanged){
            android.content.Intent result=new android.content.Intent();
            if(capture)result.putExtra("return_capture",true);
            if(photosChanged)result.putExtra("photos_changed",true);
            setResult(RESULT_OK,result);
        }
        // Drain the one native preview read before another Activity opens the
        // capture pipeline. No abandoned prefetch work crosses that boundary.
        if(!reading)finish();else{hud.footer=capture?"正在返回拍摄…":"正在返回胶卷库…";hud.update();}
    }
    @Override public void onBackPressed(){leave(false);}
    private void turnPage(int delta){
        if(photos==null||photos.isEmpty())return;
        int next=Math.max(0,Math.min(photos.size()-1,index+delta));
        if(next!=index){direction=delta>0?1:-1;index=next;original=false;load();}
    }
    private final Set<String> loggedKeys=new HashSet<String>();
    private void logKey(final KeyEvent e){
        final String id=e.getKeyCode()+"/"+e.getScanCode();
        if(!loggedKeys.add(id))return; // one SD write per distinct key, never per repeat
        android.util.Log.i("LutUI","gallery_key key="+e.getKeyCode()+" scan="+e.getScanCode());
        final String line=SystemClock.elapsedRealtime()+" key="+e.getKeyCode()+" scan="+e.getScanCode()
                +" action="+e.getAction()+" repeat="+e.getRepeatCount()+"\n";
        try{
            io.execute(new Runnable(){public void run(){
                try{
                    File dir=new File(Environment.getExternalStorageDirectory(),"SONYLUT");
                    FileOutputStream out=new FileOutputStream(new File(dir,"KEYLOG.TXT"),true);
                    try{out.write(line.getBytes("UTF-8"));out.flush();}finally{out.close();}
                }catch(Throwable x){android.util.Log.w("LutUI","gallery_key_log_failed",x);}
            }});
        }catch(java.util.concurrent.RejectedExecutionException x){android.util.Log.w("LutUI","gallery_key_log_rejected",x);}
    }
    @Override public boolean dispatchKeyEvent(KeyEvent e){
        if(leaving)return true;
        if(e.getAction()==KeyEvent.ACTION_DOWN)logKey(e);
        int k=UiKeys.key(e),dial=UiKeys.dial(e),control=UiKeys.controlDial(e),scan=e.getScanCode();
        if(deleteConfirm){
            if(e.getAction()==KeyEvent.ACTION_DOWN&&e.getRepeatCount()==0){
                if(k==UiKeys.OK)startDelete();
                else if(k==UiKeys.BACK){deleteConfirm=false;load();}
            }
            return true;
        }
        if(sheetOpen){
            if(e.getAction()!=KeyEvent.ACTION_DOWN)return true;
            if(k==UiKeys.OK){openSheetSelection();return true;}
            if(k==UiKeys.BACK){closeSheet();return true;}
            if(k==UiKeys.PLAY||UiKeys.shutter(e)){leave(true);return true;}
            int n=photos==null?0:photos.size(),selected=sheet.selection();
            if(n==0)return true;
            int next=selected;
            // The sheet is drawn newest-first left-to-right, so index grows to
            // the right. a7r2.29: LEFT previously advanced the index and moved
            // the highlight the opposite way on screen.
            if(k==UiKeys.LEFT)next=selected-1;
            else if(k==UiKeys.RIGHT)next=selected+1;
            else if(k==UiKeys.DOWN)next=selected+ContactSheetView.COLS;
            else if(k==UiKeys.UP)next=selected-ContactSheetView.COLS;
            else if(dial!=0)next=selected+dial;
            // The rear control wheel moves along the contact sheet row, like
            // LEFT/RIGHT, instead of jumping a whole row (a7r2.31).
            else if(control!=0)next=selected+control;
            else return true;
            sheet.setSelection(Math.max(0,Math.min(n-1,next)));
            return true;
        }
        if(e.getAction()==KeyEvent.ACTION_DOWN&&e.getRepeatCount()==0&&UiKeys.deleteKey(e)){
            if(!deleting&&!reading&&photos!=null&&!photos.isEmpty())showDeleteConfirm();
            android.util.Log.i("LutUI","gallery_delete_key scan="+scan+" key="+e.getKeyCode());
            return true;
        }
        if(e.getAction()==KeyEvent.ACTION_DOWN&&e.getRepeatCount()==0&&UiKeys.zoomToggle(e)){
            toggleZoom();android.util.Log.i("LutUI","gallery_zoom_key scan="+scan+" target="+zoomTarget);
            return true;
        }
        if(k==UiKeys.PLAY||UiKeys.shutter(e)){
            if(e.getAction()==KeyEvent.ACTION_DOWN&&e.getRepeatCount()==0)leave(true);
            return true;
        }
        if(photos==null||photos.isEmpty())return k==UiKeys.NONE?super.dispatchKeyEvent(e):true;
        if(zoomed){
            if(e.getAction()!=KeyEvent.ACTION_DOWN)return true;
            if(control!=0){zoomTo(ControlMath.zoomStep(zoomTarget,control));return true;}
            if(k==UiKeys.UP){pan(0,-1);return true;}
            if(k==UiKeys.DOWN){pan(0,1);return true;}
            if(k==UiKeys.LEFT){pan(-1,0);return true;}
            if(k==UiKeys.RIGHT){pan(1,0);return true;}
            if(k==UiKeys.BACK){zoomTo(1f);return true;}
            if(dial!=0){pan(dial,0);return true;}
            if(k==UiKeys.OK)return true;
            return true;
        }
        // The control wheel (not the front/rear dials) is the zoom control.
        if(control!=0){if(e.getAction()==KeyEvent.ACTION_DOWN)zoomTo(ControlMath.zoomStep(1f,control));return true;}
        // Catalogue is newest-first: LEFT/RIGHT move through it in capture
        // order like the contact sheet (index+1 is to the right / older).
        if(dial!=0){if(e.getAction()==KeyEvent.ACTION_DOWN)turnPage(dial);return true;}
        if(k==UiKeys.NONE)return super.dispatchKeyEvent(e);if(e.getAction()!=KeyEvent.ACTION_DOWN||e.getRepeatCount()!=0)return true;
        if(k==UiKeys.BACK){leave(false);return true;}
        if(k==UiKeys.OK){
            if(photos.get(index).single){hud.toast("配方照片由相机直出，没有原片对照");return true;}
            original=!original;infoVisible=true;hud.toast(original?"显示原片":"显示冲洗成片");load();
        }
        else if(k==UiKeys.UP){infoVisible=!infoVisible;if(hud.mode.equals("review"))hud.setVisibility(infoVisible?View.VISIBLE:View.INVISIBLE);
            android.util.Log.i("LutUI","gallery_info visible="+infoVisible);}
        else if(k==UiKeys.DOWN)openSheet();
        else if(k==UiKeys.LEFT||k==UiKeys.RIGHT)turnPage(k==UiKeys.LEFT?-1:1);
        return true;
    }
    @Override protected void onResume(){super.onResume();active=true;SonyAppPolicy.register(this);try{image.startDisplayTracking();schedule();}catch(Exception e){error(e);}}
    @Override protected void onPause(){active=false;image.stopDisplayTracking();super.onPause();}
    @Override protected void onDestroy(){galleryLog("destroy");destroyed=true;drawKey=null;io.shutdown();detailIo.shutdownNow();thumbIo.shutdownNow();ui.removeCallbacks(detailUpgrade);ui.removeCallbacks(tileRefresh);ui.removeCallbacks(baseUpgrade);
        image.setImageBitmap(null);cache.clear();detailCache.pin(null);detailCache.clear();clearThumbs();
        dropBase();baseLoadingKey=null;
        tileLoadingKey=null;releaseTile();
        if(previousHandler!=null)Thread.setDefaultUncaughtExceptionHandler(previousHandler);
        super.onDestroy();}
}
