package org.hongen.sonylutprobe;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/** Product entry point. File and preview work stay off the UI thread. */
public final class LibraryActivity extends Activity {
    private static final int CAPTURE_REQUEST=1,GALLERY_REQUEST=2,VIDEO_REQUEST=3;
    private final Handler ui=new Handler();
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private CameraPage page;private LutLibrary library;private PhotoCatalog catalog;private LutPreview previews;
    private int navDepth=-1;
    private List<LutLibrary.Entry> luts=new ArrayList<LutLibrary.Entry>();
    private List<PhotoCatalog.Pair> photos=new ArrayList<PhotoCatalog.Pair>();
    private List<File> imports=new ArrayList<File>();private List<String> importResults=new ArrayList<String>();
    private final LutLibrary.Verifier verifier=new LutLibrary.Verifier(){public int inspect(File file)throws Exception{return LutBinaryCache.inspect(file);}};
    private final PreviewRequest previewRequests=new PreviewRequest();
    private String mode="loading",previous="library",notice="",previewId="",importFocusId,scanPath="";private StringBuilder scanReport;private int selection,lastSelection;private boolean busy,destroyed,importDone;private int photoReturnToken;
    private boolean displayStarted,exiting;private String selectedId;private int beautySkin,beautyDenoise;
    private String shootMode="lut";private int recipeGroup,recipeLocal,lastRecipeId=-1;
    private Bitmap[] recipeImages;private boolean recipeImagesLoading;
    private long captureLaunchAt;private int capturePrepareToken;
    // a7r2.51: one capture launch at a time. The video-return settle used to
    // leave the shelf interactive, so a manual press plus the delayed auto
    // launch could start two PhotoActivity sessions; one of them always lost
    // the camera. Tokens also revoke stale film-animation callbacks.
    private int captureLaunchToken;private boolean videoLaunchPending;private boolean processLaunchPending;
    private LutLibrary.Entry previewEntry;
    private NativePreviewProfile fittedPreview;
    private PreviewProfileStore.Choice previewChoice;
    private String fitError="";
    private boolean previewForCapture;
    private VideoCatalog videoCatalog;private VideoSelectionStore videoSelection;
    private List<VideoCatalog.Item> videos=new ArrayList<VideoCatalog.Item>();
    private Set<String> selectedVideos=new HashSet<String>();
    private String pairingCode="",pairingError="";private boolean pairingLoading;
    private SonyAppPolicy.ExitRequest exitRequest;
    private final Runnable transferPoll=new Runnable(){public void run(){
        if(!destroyed&&mode.equals("transfer")){render();ui.postDelayed(this,1000);}
    }};
    private final Runnable exitTimeout=new Runnable(){public void run(){
        if(!destroyed&&exiting&&displayStarted){
            if(exitRequest!=null)exitRequest.cancel();
            exiting=false;android.util.Log.e("LutUI","native_exit_not_acknowledged");
            page.busy=false;
            showExitFailure("相机未响应退出请求，可重试或打开测试连接。");
        }
    }};
    @Override public void onCreate(Bundle state){super.onCreate(state);
        Lang.init(this);
        try{UiFont.get(this);}catch(RuntimeException e){android.util.Log.e("LutLibrary","font_failed",e);android.widget.TextView error=new android.widget.TextView(this);error.setText("Font could not load. Please update A7R2 Film Camera.\nMENU: Exit");setContentView(error);return;}
        page=new CameraPage(this);setContentView(page);
        shootMode=getPreferences(0).getString("shoot_mode","lut");
        if(!shootMode.equals("recipe"))shootMode="lut";
        recipeGroup=UiList.clamp(RecipeMemory.camera(this,getPreferences(0).getInt("recipe_camera",0)),RecipeData.GROUPS.length);
        recipeLocal=UiList.clamp(RecipeMemory.local(this,recipeGroup,getPreferences(0).getInt("recipe_pick_"+recipeGroup,0)),RecipeData.countInGroup(recipeGroup));
        lastRecipeId=RecipeData.recipeAt(recipeGroup,recipeLocal);
        beautySkin=getPreferences(0).getInt("beauty_skin",0);
        beautyDenoise=getPreferences(0).getInt("beauty_denoise",0);
        page.tap=new CameraPage.Tap(){public void select(int row){selection=row;render();action();}};
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);initializeLibrary();
    }
    private void initializeLibrary(){
        previewRequests.next();previewId="";if(page!=null)page.preview=null;if(previews!=null){previews.clear();previews=null;}
        mode="loading";runTask("正在打开胶卷库",new Task(){public void run()throws Exception{
            previews=new LutPreview(LibraryActivity.this);library=new LutLibrary(ProductFiles.library());catalog=new PhotoCatalog(ProductFiles.photos(),Environment.getExternalStorageDirectory());
            library.retireBuiltinSamples(ProductFiles.builtins());
            String defaultId=null;
            for(BuiltinLuts.Entry entry:BuiltinLuts.read(getAssets().open("CATALOG.JSN"))){String id=installBuiltin(entry);if(entry.selected)defaultId=id;}
            selectedId=library.selectedId();if(library.find(selectedId)==null)selectedId=defaultId;luts=library.entries();
            photos=catalog.list();
        }},new Runnable(){public void run(){applyRoll();mode=homeMode();selection=homeSelection();render();requestPreview();}});
    }
    private String installBuiltin(BuiltinLuts.Entry item)throws Exception{
        LutLibrary.Entry existing=library.find(item.sha256);
        if(existing!=null){if(existing.size!=item.grid)throw new IOException("内置胶卷阶数不符");return existing.id;}
        File builtins=ProductFiles.builtins();LutLibrary.ensureDirectory(builtins);
        File input=Assets.extract(new File(getApplicationInfo().sourceDir),item.asset,builtins);
        LutLibrary.Entry entry=library.importFile(input,item.name,item.source,verifier).entry;
        if(!entry.id.equals(item.sha256)||entry.size!=item.grid)throw new IOException("内置胶卷与内容清单不符");return entry.id;
    }
    /** Roll counter shown on the shelf: total frames and the 36-frame roll. */
    private void applyRoll(){
        if(page==null)return;
        int count=0;
        for(PhotoCatalog.Pair pair:photos)if(pair.original.isFile()||pair.graded.isFile())count++;
        page.rollCount=count;
        page.rollNo=String.format(Locale.US,"%02d",count/36+1);
    }
    private void refreshRoll(){
        final PhotoCatalog store=catalog;
        if(store==null)return;
        io.execute(new Runnable(){public void run(){
            final List<PhotoCatalog.Pair> all;
            try{all=store.list();}catch(Exception e){android.util.Log.w("LutLibrary","roll_refresh_failed",e);return;}
            ui.post(new Runnable(){public void run(){if(destroyed||page==null)return;int before=page.rollCount;photos=all;applyRoll();
                if(before>=0&&page.rollCount>before)page.toast("胶卷已更新 · 共 "+page.rollCount+" 张");
                page.invalidate();}});
        }});
    }
    private int indexOf(String id){for(int i=0;i<luts.size();i++)if(luts.get(i).id.equals(id))return i;return 0;}
    private boolean recipeMode(){return "recipe".equals(shootMode);}
    private String homeMode(){return recipeMode()?"recipe_cameras":"library";}
    private int homeSelection(){return recipeMode()?UiList.clamp(recipeGroup,RecipeData.GROUPS.length):indexOf(selectedId);}
    private static boolean isHome(String mode){return "library".equals(mode)||"recipe_cameras".equals(mode);}
    private void rememberRecipe(){
        int group=UiList.clamp(recipeGroup,RecipeData.GROUPS.length);
        recipeLocal=UiList.clamp(recipeLocal,RecipeData.countInGroup(group));
        recipeGroup=group;lastRecipeId=RecipeData.recipeAt(group,recipeLocal);
        RecipeMemory.save(this,group,recipeLocal);
        getPreferences(0).edit().putInt("recipe_camera",group).putInt("recipe_pick_"+group,recipeLocal).commit();
    }
    /** Camera-card images ship inside the APK. Decode them once at half size:
     * the cards are small, and twelve full-size bitmaps would needlessly raise
     * the old panel's heap. */
    private void ensureRecipeImages(){
        if(recipeImages!=null||recipeImagesLoading)return;
        recipeImagesLoading=true;
        if(page!=null){page.cardImagesLoading="载入中";page.invalidate();}
        io.execute(new Runnable(){public void run(){
            final Bitmap[] loaded=new Bitmap[RecipeData.GROUPS.length];
            android.graphics.BitmapFactory.Options options=new android.graphics.BitmapFactory.Options();
            options.inSampleSize=2;
            for(int i=0;i<loaded.length;i++){
                try{loaded[i]=android.graphics.BitmapFactory.decodeStream(
                        getAssets().open("recipe/cameras/"+RecipeData.GROUPS[i].image+".jpg"),null,options);}
                catch(Exception e){android.util.Log.w("LutUI","recipe_camera_image_failed "+RecipeData.GROUPS[i].image,e);}
            }
            ui.post(new Runnable(){public void run(){
                recipeImagesLoading=false;
                if(destroyed){for(Bitmap bitmap:loaded)if(bitmap!=null)bitmap.recycle();return;}
                recipeImages=loaded;if(page!=null){page.cardImages=loaded;page.cardImagesLoading="";page.invalidate();}
            }});
        }});
    }
    interface Task {void run()throws Exception;}
    private void runTask(String label,final Task task,final Runnable complete){if(busy)return;busy=true;page.busy=true;page.previewMode=false;page.title=label;page.subtitle="";page.rows=new ArrayList<CameraPage.Row>();page.empty=label;page.note="";page.footer="处理中";page.invalidate();
        io.execute(new Runnable(){public void run(){try{task.run();ui.post(new Runnable(){public void run(){if(destroyed)return;busy=false;page.busy=false;complete.run();}});}
            catch(final Exception e){android.util.Log.e("LutLibrary","operation_failed",e);ui.post(new Runnable(){public void run(){if(destroyed)return;busy=false;page.busy=false;showNotice("操作未完成",explain(e));}});}}});
    }
    private static String explain(Exception e){String text=e.getMessage();if(text==null)text=e.getClass().getSimpleName();
        if(text.contains("truncated")||text.contains("too many cube rows"))return "数据行数与阶数不符，请重新导出 CUBE";
        if(text.contains("1D / combined"))return "只支持独立 3D LUT，请导出为 3D CUBE";
        if(text.contains("cube size"))return "支持 2–65 阶 3D LUT，请检查 LUT_3D_SIZE";
        if(text.contains("domain"))return "输入范围无效，请检查 DOMAIN_MIN / MAX";
        if(text.contains("cube data row")||text.contains("cube output"))return "CUBE 数值格式不正确，请重新导出";
        if(text.contains("ENOSPC"))return "存储空间不足，请检查 SD 卡剩余空间";
        return LutLibrary.label(text);
    }
    /** Durable trace of the video -> still handoff. Written synchronously and
     * flushed before the capture process starts, because that process can take
     * the camera down and leave its own PROBE.JSN empty (observed on a7r2.26). */
    private void handoffLog(String message){
        android.util.Log.i("LutUI","handoff "+message);
        try{
            File dir=new File(Environment.getExternalStorageDirectory(),"SONYLUT");
            if(!dir.isDirectory()&&!dir.mkdirs())return;
            FileOutputStream out=new FileOutputStream(new File(dir,"HANDOFF.TXT"),true);
            try{out.write((SystemClock.elapsedRealtime()+" "+message+"\n").getBytes("UTF-8"));out.flush();}
            finally{out.close();}
        }catch(Throwable ignored){}
    }
    private boolean videoProcessRunning(){
        try{
            android.app.ActivityManager am=(android.app.ActivityManager)getSystemService(ACTIVITY_SERVICE);
            java.util.List<android.app.ActivityManager.RunningAppProcessInfo> list=am.getRunningAppProcesses();
            if(list==null)return false;
            String name=getPackageName()+":video";
            for(android.app.ActivityManager.RunningAppProcessInfo item:list)if(name.equals(item.processName))return true;
        }catch(Throwable ignored){}
        return false;
    }
    private void showNotice(String title,String detail){previous=mode.equals("notice")?previous:mode;mode="notice";selection=0;notice=detail;render();page.title=title;page.invalidate();}
    private static int depthOf(String mode){
        if(isHome(mode))return 0;
        if(mode.equals("import")||mode.equals("gallery")||mode.equals("options")||mode.equals("about")
                ||mode.equals("shoot_mode")||mode.equals("transfer"))return 1;
        return 2;
    }
    private void render(){if(destroyed||busy)return;List<CameraPage.Row> rows=new ArrayList<CameraPage.Row>();
        if(!mode.equals("notice")){int d=depthOf(mode);page.setPageForward(navDepth<0||d>=navDepth);navDepth=d;}
        page.previewMode=mode.equals("library");page.cardGrid=false;page.section=mode.equals("library")?"胶卷库 · "+luts.size():"A7R2胶片相机";page.title="选择胶卷";page.subtitle="←→ / 转盘选择 · 中央键开始拍摄";page.empty="还没有胶卷";
        page.note="A7R2胶片相机 编译时间："+buildDate()+" · v"+version();page.footer="←→ 选择   ● 拍摄   回放键 相册   MENU 更多";
        page.emptyDetail="MENU → 导入胶卷 · 中央键确认";
        if(mode.equals("library")){
            for(LutLibrary.Entry e:luts)rows.add(new CameraPage.Row(e.name,e.source+(e.id.equals(selectedId)?" · 当前":""),e.size+"³"));
        }else if(mode.equals("recipe_cameras")){
            page.cardGrid=true;page.section="近似配方 · "+RecipeData.ALL.length;page.title="选择相机";page.subtitle="选相机直接进入取景 · 取景中按 C3 换胶片";
            page.empty="没有可用配方";page.emptyDetail="请检查应用安装是否完整";
            for(int g=0;g<RecipeData.GROUPS.length;g++){
                RecipeData.Group group=RecipeData.GROUPS[g];
                int local=UiList.clamp(RecipeMemory.local(this,g,0),RecipeData.countInGroup(g));
                int id=RecipeData.recipeAt(g,local);
                String film=id<0?"":Lang.recipeName(RecipeData.ALL[id]);
                rows.add(new CameraPage.Row(Lang.groupName(group),group.name,film.length()==0?RecipeData.countInGroup(g)+" 配方":film));
            }
            page.note="配方由 Recipe Lab 提供 · 直接写入相机 JPEG · 进入后 C3 可实时换胶片";
            page.footer="←→↑↓ 选择   ● 进入拍摄   回放键 相册   MENU 更多";
            page.emptyDetail="";ensureRecipeImages();
        }else if(mode.equals("shoot_mode")){
            page.section="拍摄模式";page.title="选择工作方式";page.subtitle="模式会记忆 · 两种方式都可随时切回";
            boolean lut=!recipeMode();
            rows.add(new CameraPage.Row("真 LUT 模式","先选胶卷 · RAW 冲洗 + 完整 3D LUT",lut?"当前":"选择"));
            rows.add(new CameraPage.Row("近似胶片配方模式","选相机直接取景 · C3 实时换胶片 · 相机直出",!lut?"当前":"选择"));
            rows.add(new CameraPage.Row("返回","保留当前模式","返回"));
            page.note="真 LUT：一次快门保存原片与冲洗成片 · 配方：只保存一张相机 JPEG";
            page.footer="↑↓ 选择   ● 切换   MENU 返回";
        }else if(mode.equals("import")){
            page.section="导入胶卷";page.title="加入新的胶卷";page.subtitle="CUBE 文件放 SD 根目录；文件名用英文 8.3，如 NEUTRAL.CUB";
            rows.add(new CameraPage.Row("扫描 LUTS 文件夹","检查文件并显示可导入的胶卷","扫描"));
            rows.add(new CameraPage.Row("返回胶卷库","已导入的胶卷可以继续使用","返回"));
            page.note="支持 2–65³ 独立 3D CUBE · 相机不支持中文/长文件名";page.footer="↑↓ 选择   ● 确认   MENU 返回";
        }else if(mode.equals("files")){
            page.section="导入胶卷";page.title=importDone?"导入完成":"发现 "+imports.size()+" 个胶卷";page.subtitle=importDone?notice:"可全部导入，也可只选择其中一个";
            rows.add(new CameraPage.Row(importDone?(importFocusId==null?"返回胶卷库":"使用导入的胶卷"):imports.isEmpty()?"重新扫描":"全部导入",importDone?"返回后已定位到新增胶卷":"完整校验后加入卡内胶卷库",importDone?"完成":"导入"));
            for(int i=0;i<imports.size();i++){String result=importResults.get(i);boolean bad=result.startsWith("失败");rows.add(new CameraPage.Row(imports.get(i).getName(),result,bad?"查看原因":result.equals("等待导入")?"导入":"已处理",!bad));}
            page.note=importDone?"新增、重复和失败文件已分别核对":notice;page.footer="↑↓ 选择   ● 确认   → 重扫   MENU 返回";
        }else if(mode.equals("gallery")){
            page.section="照片";page.title="拍过的胶卷";page.subtitle="真 LUT 成片与配方直出照片";page.empty="还没有照片";page.emptyDetail="拍下第一张照片后，在这里回看";
            SimpleDateFormat date=new SimpleDateFormat("MM.dd  HH:mm",Locale.CHINA);
            for(PhotoCatalog.Pair p:photos){
                String when=p.time>946684800000L?date.format(new Date(p.time)):"日期未设置";
                if(p.single)rows.add(new CameraPage.Row(p.lut,when+" · "+(p.camera.length()==0?"相机直出":p.camera)+" · "+p.graded.getName(),"直出"));
                else rows.add(new CameraPage.Row(p.lut,when+" · "+p.graded.getName(),p.grid+"³"));
            }
            page.note=photos.size()+" 张照片 · 配方直出为单张 JPEG，真 LUT 保留原片与成片";page.footer="↑↓ 选择   ● 打开   MENU 返回";
        }else if(mode.equals("options")){
            page.section="更多";page.title="拍摄之外";page.subtitle="A7R2胶片相机 编译时间："+buildDate()+" · v"+version();
            rows.add(new CameraPage.Row("拍摄模式",recipeMode()?"近似胶片配方 · 相机直出 · C3 换胶片":"真 LUT · 先选胶卷","设置"));
            rows.add(new CameraPage.Row("照片美化",recipeMode()?"真 LUT 成片使用 · 配方直出不受影响":"皮肤平滑 "+beautyLevel(beautySkin)+" · 降噪 "+beautyLevel(beautyDenoise),"设置"));
            boolean transfer=TransferSettings.enabled(this);
            rows.add(new CameraPage.Row("手机传输",transfer?transferStateLabel():"关闭 · 手机自动接收新照片","设置"));
            boolean face=TransferSettings.faceDetection(this);
            rows.add(new CameraPage.Row("人脸检测",face?"开启 · 相机原生人脸优先对焦":"关闭 · 使用相机原设置","设置"));
            rows.add(new CameraPage.Row("语言",Lang.label()+" · 五国语言","设置"));
            rows.add(new CameraPage.Row("导入胶卷","从 SD 卡 LUTS 文件夹加入胶卷","打开"));
            rows.add(new CameraPage.Row("使用说明","全部功能、按键与保存方式 · 42 条","查看"));
            rows.add(new CameraPage.Row("关于","原作者、移植与开源致谢","查看"));
            rows.add(new CameraPage.Row("UI 动画预览","胶片货架/快门/测光动效测试","打开"));
            rows.add(new CameraPage.Row("回到"+(recipeMode()?"相机列表":"胶卷库"),"继续选择或拍摄","返回"));
            rows.add(new CameraPage.Row("退出应用","回到相机原生拍摄界面","退出"));
            rows.add(new CameraPage.Row("当前胶卷的取景","真 LUT 模式使用 · 近似色彩或原色","设置"));
            if(SonyAppPolicy.hasDebugHelper(this))rows.add(new CameraPage.Row("测试连接","打开已安装的相机调试工具","打开"));
            page.note="Yonitska（Bilibili）移植增强 · 原作者：瓦尔基里（小红薯 ID） · GitHub: https://github.com/EnglandLobster/sony-lut-camera（基于这个地址修改）";page.footer="↑↓ 选择   ● 确认   MENU 返回";
        }else if(mode.equals("transfer")){
            renderTransfer(rows);
        }else if(mode.equals("transfer_videos")){
            page.section="手机传输";page.title="选择录像";page.subtitle="只传已勾选并写完的录像 · 手机端仍需点按下载";
            rows.add(new CameraPage.Row("刷新列表","重新扫描 DCIM、MP_ROOT 与 PRIVATE","刷新"));
            rows.add(new CameraPage.Row("全选已完成","把当前列表中的已完成录像加入传输","全选"));
            rows.add(new CameraPage.Row("清空选择","手机端录像列表会同时清空","清空"));
            SimpleDateFormat date=new SimpleDateFormat("MM.dd HH:mm",Locale.CHINA);
            for(VideoCatalog.Item item:videos){
                boolean picked=selectedVideos.contains(item.id);
                String when=item.time>946684800000L?date.format(new Date(item.time)):"日期未设置";
                String detail=humanBytes(item.size)+" · "+when+" · "+(item.ready?(picked?"已勾选":"未勾选"):"仍在写入，暂不能勾选");
                rows.add(new CameraPage.Row(item.name,detail,item.ready?(picked?"已选":"勾选"):"写入中",item.ready));
            }
            rows.add(new CameraPage.Row("返回","返回手机传输","返回"));
            page.note=videos.isEmpty()?"没有找到录像 · 请确认录像已保存在 DCIM 或 PRIVATE":(selectedVideos.size()+" 个已勾选 · 取消勾选后手机立即不可见");page.footer="↑↓ 选择   ● 勾选   MENU 返回";
        }else if(mode.equals("language")){
            page.section="语言";page.title="语言设置";page.subtitle="选择界面语言 · 更改立即生效";
            for(int i=0;i<Lang.CODES.length;i++){
                String itemCode=Lang.CODES[i];
                boolean current=itemCode.equals(Lang.code());
                rows.add(new CameraPage.Row(Lang.labelFor(itemCode),current?"当前":"",current?"当前":"选择"));
            }
            rows.add(new CameraPage.Row("返回","保留当前语言设置","返回"));
            page.note="语言偏好保存在应用内 · 不会改变相机系统语言";page.footer="↑↓ 选择   ● 应用   MENU 返回";
        }else if(mode.equals("about")){
            page.section="关于";page.title="A7R2胶片相机";page.subtitle="Yonitska（Bilibili）移植增强 · v"+version()+" · 仅支持 A7R2";
            rows.add(new CameraPage.Row("原作者  瓦尔基里 · 小红薯 ID","GitHub: https://github.com/EnglandLobster/sony-lut-camera · 开源 GPL-3.0-only · 基于这个地址修改","致谢"));
            rows.add(new CameraPage.Row("移植增强  Yonitska（Bilibili）","A7R2 适配 · 胶片界面与冲洗流程 · 手机传片与人脸检测","移植"));
            rows.add(new CameraPage.Row("Based on Recipe Lab by André Domingues","配方数据 v1.11.1 · GPL-3.0 · 特别感谢","致谢"));
            rows.add(new CameraPage.Row("配方数据  配方实验室 Recipe Lab","77 个近似胶片配方 · 相机参数映射来源","致谢"));
            rows.add(new CameraPage.Row("Recipe Lab 项目地址","github.com/voxivoid/recipe-lab · 开放配方数据","开源"));
            rows.add(new CameraPage.Row("借鉴  M-Film（佳能）","HNGSeries 作者与贡献者 · 胶片界面参考 · GPL-2.0","致谢"));
            rows.add(new CameraPage.Row("借鉴  胶片工坊 Film Studio","A5100 Film Studio 作者与贡献者 · 冲洗流程","致谢"));
            rows.add(new CameraPage.Row("借鉴  拍摄导演台 ShotDirector","项目作者与贡献者 · 摆姿/遥控交互 · MIT","致谢"));
            rows.add(new CameraPage.Row("参考  理光模组","bonyback1 · 机内色彩矩阵方法 · Apache-2.0","致谢"));
            rows.add(new CameraPage.Row("本项目同样开源","GPL-3.0-only · 源码随发行公开，欢迎继续改进","许可"));
            rows.add(new CameraPage.Row("特别感谢","原作者、移植者与上述开源项目贡献者","致谢"));
            page.note="感谢 Recipe Lab 开放配方数据；感谢原作者、移植者与所有参考项目";page.footer="MENU 返回";
        }else if(mode.equals("beauty")){
            page.section="照片美化";page.title="人像轻处理";page.subtitle="只作用于真 LUT 冲洗成片 · 原片保持不动";
            rows.add(new CameraPage.Row("皮肤平滑",beautyLevel(beautySkin)+" · "+(beautySkin==0?"不处理":"仅肤色区域"),"切换"));
            rows.add(new CameraPage.Row("降噪",beautyLevel(beautyDenoise)+" · "+(beautyDenoise==0?"不处理":"色度降噪"),"切换"));
            rows.add(new CameraPage.Row("返回","保留当前设置","返回"));
            page.note=(recipeMode()?"配方照片由相机直出，不会经过美颜或降噪 · ":"")+"42MP 全尺寸机内处理，可能增加数秒；默认关闭，不做 AI 磨皮";page.footer="↑↓ 选择   ● 切换   MENU 返回";
        }else if(mode.equals("preview_options")||mode.equals("preview_warning")||mode.equals("preview_error")){
            page.section="取景预览";page.title=previewEntry.name;page.subtitle=mode.equals("preview_warning")?"预览与成片可能有较明显差异":mode.equals("preview_error")?"近似预览暂未生成":"只改变取景方式，不改变照片效果";
            boolean enabled=previewChoice==null||previewChoice.enabled;
            rows.add(new CameraPage.Row(mode.equals("preview_error")?"重新生成近似预览":mode.equals("preview_warning")?"继续使用近似预览":"近似色彩预览",mode.equals("preview_error")?fitError:"使用机内自动拟合的矩阵与曲线",previewChoice!=null&&enabled?"当前":"选择"));
            rows.add(new CameraPage.Row(mode.equals("preview_warning")||mode.equals("preview_error")?"使用原色取景拍摄":"原色取景","照片仍按完整胶卷冲洗",previewChoice!=null&&!enabled?"当前":"选择"));
            rows.add(new CameraPage.Row("返回胶卷库","保留当前选择","返回"));
            page.note=previewChoice==null?"预览偏好无法读取，请重新选择":fittedPreview!=null&&fittedPreview.largeError()?"近似效果差异较大，仍可自由选择使用":"半按对焦时恢复原色 · 成片按完整胶卷冲洗";
            page.footer="↑↓ 选择   ● 确认   MENU 返回";
        }else if(mode.equals("exit_error")){
            page.section="退出应用";page.title="退出未完成";page.subtitle=notice;
            rows.add(new CameraPage.Row("重试退出","重新结束当前应用会话","重试"));
            if(SonyAppPolicy.hasDebugHelper(this))rows.add(new CameraPage.Row("打开测试连接","恢复连接以便检查与修复","打开"));
            rows.add(new CameraPage.Row("返回胶卷库","已有照片和胶卷保持不变","返回"));
            page.note="";page.footer="↑↓ 选择   ● 确认   MENU 返回";
        }else if(mode.equals("help")){
            page.section="使用说明";page.title="从装卷到成片";page.subtitle="选择胶卷 → 半按对焦 → 全按拍摄 → 回看成片";
            rows.add(new CameraPage.Row("选择胶卷","左右键或转盘切换 · 中央键开始拍摄","货架"));
            rows.add(new CameraPage.Row("胶片载入动画","选中后自动播放装卷、拉片与快门帘","动效"));
            rows.add(new CameraPage.Row("全局取景设置","MENU → 更多 → 当前胶卷的取景","预览"));
            rows.add(new CameraPage.Row("近似色彩预览","取景接近成片色调 · 每个胶卷单独记忆","预览"));
            rows.add(new CameraPage.Row("原色取景","取景保持原色 · 成片仍按完整胶卷冲洗","预览"));
            rows.add(new CameraPage.Row("拍摄模式","MENU → 更多 → 拍摄模式 · 真 LUT / 近似配方","模式"));
            rows.add(new CameraPage.Row("近似胶片配方","选相机直接取景 · 相机直出单张 JPEG","模式"));
            rows.add(new CameraPage.Row("配方实时换胶片","取景中按 C3 选择 · 再按 C3 确认 · 效果实时更新","配方"));
            rows.add(new CameraPage.Row("导入 3D LUT","CUBE 放 SD 根目录 LUTS 文件夹","胶卷"));
            rows.add(new CameraPage.Row("LUT 文件命名","英文 8.3 名，如 KODAK.CUB · 支持 2–65³","胶卷"));
            rows.add(new CameraPage.Row("半按对焦，全按拍摄","半按合焦 · 全按完成一次拍摄","拍照"));
            rows.add(new CameraPage.Row("对焦框始终显示","拍照与录像取景都常驻显示对焦框","对焦"));
            rows.add(new CameraPage.Row("选择对焦点","下键进入 · 方向键移动 · 中央键确认","对焦"));
            rows.add(new CameraPage.Row("取消对焦选择","MENU 或 BACK 取消并恢复原对焦点","对焦"));
            rows.add(new CameraPage.Row("曝光模式切换","C1 在 M → A → S → M 之间循环","曝光"));
            rows.add(new CameraPage.Row("M 档拨轮","前拨轮光圈 · 后拨轮快门 · 转盘 ISO","曝光"));
            rows.add(new CameraPage.Row("A/S 档拨轮","拨轮调快门或光圈 · 转盘调曝光补偿","曝光"));
            rows.add(new CameraPage.Row("自动 ISO 读数","AUTO 时显示实测值，例如 A1600","曝光"));
            rows.add(new CameraPage.Row("曝光补偿","取景时左右键调 EV · 中央键归零","曝光"));
            rows.add(new CameraPage.Row("白平衡","右键打开 WB 菜单 · 方向键选择 · 中央键确认","色彩"));
            rows.add(new CameraPage.Row("摆姿导演","C2 打开摆姿提示 · MENU 或返回键关闭","辅助"));
            rows.add(new CameraPage.Row("一次快门，两张 JPEG","原片与冲洗成片都保存在 DCIM","保存"));
            rows.add(new CameraPage.Row("暗房冲洗动画","拍摄后显示显影进度 · 取景保持可操作","保存"));
            rows.add(new CameraPage.Row("照片美化","MENU → 更多 → 照片美化 · 皮肤平滑/降噪","后期"));
            rows.add(new CameraPage.Row("手机自动传片","MENU → 更多 → 手机传输 · App 打开期间新照片后台发送","传输"));
            rows.add(new CameraPage.Row("手机连接相机","配套 App 内一键加入相机 Wi-Fi · 首次输入配对码","传输"));
            rows.add(new CameraPage.Row("录像传输","手机传输页勾选已完成录像 · 手机端点按下载","传输"));
            rows.add(new CameraPage.Row("人脸检测","MENU → 更多 → 人脸检测 · 使用相机原生人脸优先对焦","对焦"));
            rows.add(new CameraPage.Row("回放相册","按回放键打开 · 左右翻图 · 中央键对照原片","回放"));
            rows.add(new CameraPage.Row("接触印相总览","回放时按下键打开 4×3 缩略图","回放"));
            rows.add(new CameraPage.Row("回放放大与平移","C3 切换 1×/2× · 放大后方向键平移画面","回放"));
            rows.add(new CameraPage.Row("删除整组","删除键确认后移除原片、成片和缩略图","回放"));
            rows.add(new CameraPage.Row("视频录制","MOVIE 键或快门开始/停止 · 中央键不录制","录像"));
            rows.add(new CameraPage.Row("视频格式与色彩","MENU 选择格式 · 取景始终为近似胶卷色","录像"));
            rows.add(new CameraPage.Row("配方录像","配方写入相机 · 视频直接使用，无需 LUT","录像"));
            rows.add(new CameraPage.Row("录像待机自动对焦","空闲时每 1.6 秒对焦一次 · 半按立即合焦","录像"));
            rows.add(new CameraPage.Row("录像对焦点","待机下键选择 · 录制中方向键移动","录像"));
            rows.add(new CameraPage.Row("录像曝光与白平衡","C1 切模式 · 拨轮调整 · 右键白平衡","录像"));
            rows.add(new CameraPage.Row("录像转拍照保护","切换前等待录像接口完全释放，避免重启","稳定"));
            rows.add(new CameraPage.Row("胶片齿孔装饰","取景与回放的上下面板使用胶片边样式","界面"));
            rows.add(new CameraPage.Row("取景信息开关","上键隐藏或显示取景参数","界面"));
            rows.add(new CameraPage.Row("关于与致谢","MENU → 关于 查看作者与开源致谢","信息"));
            page.note="效果参考图不是实时取景；成片保持相机全尺寸 · 每页 4 条，继续按 ↓ 查看";page.footer="↑↓ 选择   MENU 返回";
        }else{
            page.section="提示";page.title="请检查后重试";page.subtitle=notice;page.note="已有胶卷和照片均已保留";
            rows.add(new CameraPage.Row(previous.equals("loading")?"重试":"返回",notice,"确认"));page.footer=previous.equals("loading")?"● 重试   MENU 退出":"● 返回   MENU 返回";
        }
        selection=UiList.clamp(selection,rows.size());page.rows=rows;page.selected=selection;page.setContentDescription(page.title);page.invalidate();
        android.util.Log.i("LutUI","page_ready mode="+mode+" selection="+selection+" rows="+rows.size());
    }
    private String version(){try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception e){return "UI 预览";}}
    private static int indexOfLanguage(String value){
        for(int i=0;i<Lang.CODES.length;i++)if(Lang.CODES[i].equals(value))return i;
        return 0;
    }
    private String buildDate(){return BuildInfo.DATE;}
    private void libraryPage(){mode="library";selection=UiList.clamp(lastSelection,luts.size());render();requestPreview();}
    private void recipeHome(){mode="recipe_cameras";selection=UiList.clamp(recipeGroup,RecipeData.GROUPS.length);render();}
    private void homePage(){if(recipeMode())recipeHome();else libraryPage();}
    /** Choosing a camera goes straight to the finder with its remembered film.
     * The film list is not a separate page; C3 inside the live view changes it. */
    private void openRecipeGroup(int group){
        recipeGroup=UiList.clamp(group,RecipeData.GROUPS.length);
        int fallback=getPreferences(0).getInt("recipe_pick_"+recipeGroup,0);
        recipeLocal=UiList.clamp(RecipeMemory.local(this,recipeGroup,fallback),RecipeData.countInGroup(recipeGroup));
        int id=RecipeData.recipeAt(recipeGroup,recipeLocal);
        if(id<0){if(page!=null)page.toast("这个相机没有可用配方");return;}
        startRecipeCapture(id);
    }
    private void requestPreview(){
        if(!mode.equals("library")||luts.isEmpty()||previews==null)return;
        final LutLibrary.Entry entry=luts.get(selection);if(entry.id.equals(previewId))return;previewId=entry.id;final long token=previewRequests.next();
        Bitmap cached=previews.cached(entry.id);
        if(cached!=null){previews.pin(entry.id);page.preview=cached;page.previewLoading=false;page.invalidate();return;}
        page.preview=null;page.previewLoading=true;page.previewMessage="效果参考";page.invalidate();
        io.execute(new Runnable(){public void run(){
            if(!previewRequests.current(token))return;
            try{final Bitmap rendered=previews.render(entry);ui.post(new Runnable(){public void run(){
                if(destroyed||!previewRequests.current(token)){rendered.recycle();return;}
                if(previews.store(entry.id,rendered)){previews.pin(entry.id);page.preview=rendered;page.previewLoading=false;page.invalidate();}
            }});}catch(final Exception e){ui.post(new Runnable(){public void run(){if(!destroyed&&previewRequests.current(token)){page.previewLoading=false;page.previewMessage="预览不可用";page.invalidate();android.util.Log.w("LutUI","lut_preview_failed",e);}}});}
        }});
    }
    private void scan(){mode="files";selection=0;runTask("正在扫描 SD 卡",new Task(){public void run()throws Exception{
        File folder=new File(Environment.getExternalStorageDirectory(),"LUTS");scanPath=folder.getAbsolutePath();
        StringBuilder report=new StringBuilder();
        report.append("time=").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.CHINA).format(new Date())).append('\n');
        try{imports=LutLibrary.scan(folder,report);}
        catch(Exception e){report.append("error=").append(e).append('\n');scanReport=report;writeScanReport(report);throw e;}
        scanReport=report;writeScanReport(report);
        android.util.Log.i("LutLibrary","lut_scan path="+scanPath+" count="+imports.size());
        importResults=new ArrayList<String>();for(File f:imports)importResults.add("等待导入");importDone=false;importFocusId=null;
    }},new Runnable(){public void run(){
        boolean unreadableCube=false;
        String reportText=scanReport==null?"":scanReport.toString();
        for(String line:reportText.split("\n")){
            String lower=line.toLowerCase(Locale.US);
            if((lower.contains(".cube")||lower.contains(".cub"))&&line.contains("file=false"))unreadableCube=true;
        }
        if(imports.isEmpty()&&unreadableCube)
            notice="相机读不到 CUBE：请改成英文 8.3 短名（如 NEUTRAL.CUB）后重插卡";
        else notice=imports.isEmpty()?"找到 0 个：CUBE 需在 "+scanPath+"（详见 SONYLUT/LUTSCAN.TXT）":"找到 "+imports.size()+" 个 · "+scanPath;
        render();
    }});}
    private void writeScanReport(StringBuilder report){
        try{
            File dir=new File(Environment.getExternalStorageDirectory(),"SONYLUT");
            if(!dir.isDirectory()&&!dir.mkdirs())return;
            FileOutputStream out=new FileOutputStream(new File(dir,"LUTSCAN.TXT"),false);
            try{out.write(report.toString().getBytes("UTF-8"));out.flush();}finally{out.close();}
        }catch(Throwable e){android.util.Log.w("LutLibrary","lutscan_report_failed",e);}
    }
    private void importFiles(final int index){runTask("正在校验并导入胶卷",new Task(){public void run()throws Exception{
        int added=0,existing=0,failed=0;importFocusId=null;
        for(int i=0;i<imports.size();i++){if(index>=0&&i!=index)continue;
            final String fileName=imports.get(i).getName();final String position=(i+1)+" / "+imports.size();
            ui.post(new Runnable(){public void run(){if(!destroyed&&busy){page.subtitle=position;page.empty=fileName;page.footer="正在导入，请勿取出 SD 卡";page.invalidate();}}});
            try{LutLibrary.Imported result=library.importFile(imports.get(i),null,"自定义胶卷",verifier);
                if(importFocusId==null)importFocusId=result.entry.id;
                ui.post(new Runnable(){public void run(){if(!destroyed&&busy){page.footer="正在生成近似预览 · 仅首次需要";page.invalidate();}}});
                String previewResult=" · 预览已生成";
                try{PreviewFitting.ensure(result.entry);}catch(Exception error){previewResult=" · 预览待重试";android.util.Log.w("SonyPreviewFit","import_fit_failed",error);}
                importResults.set(i,(result.added?"已导入 · ":"已存在 · ")+result.entry.name+previewResult);if(result.added)added++;else existing++;
            }catch(Exception e){failed++;importResults.set(i,"失败 · "+explain(e));android.util.Log.w("LutLibrary","import_failed "+imports.get(i).getName(),e);}
        }
        luts=library.entries();notice="新增 "+added+" · 已存在 "+existing+" · 失败 "+failed;importDone=index<0;
    }},new Runnable(){public void run(){if(index>=0&&importFocusId!=null){selectedId=importFocusId;lastSelection=indexOf(importFocusId);libraryPage();}else{selection=0;render();}if(page!=null&&notice.length()>0)page.toast(notice);}});}
    private void finishImport(){if(importFocusId!=null){selectedId=importFocusId;lastSelection=indexOf(importFocusId);}libraryPage();}
    private void gallery(){lastSelection=selection;mode="gallery";selection=0;runTask("正在打开照片",new Task(){public void run()throws Exception{
        List<PhotoCatalog.Pair> all=catalog.list();
        // Old receipts can survive a cleaned/recreated DCIM. Hide entries whose
        // photographs are gone instead of opening a dead pair.
        List<PhotoCatalog.Pair> existing=new ArrayList<PhotoCatalog.Pair>();
        for(PhotoCatalog.Pair pair:all)if(pair.original.isFile()||pair.graded.isFile())existing.add(pair);
        photos=existing;
    }},new Runnable(){public void run(){render();}});}
    private void openGallery(String pairId){openGallery(pairId,0);}
    private void openGallery(String pairId,int offset){startActivityForResult(new Intent(this,GalleryActivity.class).putExtra("pair_id",pairId).putExtra("page_offset",offset),GALLERY_REQUEST);}
    /** Start a new preparation/launch generation. Any older film-animation
     * callback or video-return settle timer becomes stale and must not open a
     * second capture activity. */
    private int beginCaptureLaunch(String reason){
        cancelVideoReturnWait(reason);
        busy=false;
        return ++captureLaunchToken;
    }
    private boolean captureLaunchCurrent(int token){return !destroyed&&token==captureLaunchToken;}
    /** A manual launch or leaving the shelf wins over the delayed post-video
     * auto launch. Without this the settle timer fired a second time. */
    private void cancelVideoReturnWait(String reason){
        if(!videoLaunchPending)return;
        videoLaunchPending=false;photoReturnToken++;busy=false;
        if(page!=null)page.busy=false;
        handoffLog("video_return_canceled reason="+reason);
    }
    /** Single-flight guard for PhotoActivity/VideoActivity starts. */
    private boolean claimProcessLaunch(String reason){
        if(processLaunchPending){handoffLog("process_launch_ignored reason="+reason);return false;}
        processLaunchPending=true;return true;
    }
    private void startCapture(final LutLibrary.Entry entry){
        final int launch=beginCaptureLaunch("start_capture");
        selectedId=entry.id;lastSelection=selection;
        previewEntry=entry;previewForCapture=true;fitError="";fittedPreview=null;
        final int prepare=++capturePrepareToken;
        // The film-load animation covers preparation: only the chosen
        // canister, then its leader, then the motion demo's shutter curtain.
        if(page!=null)page.startLoadingFilm(selection,entry.name,entry.size+"³");
        // The preparation task is quick, so being stuck on its label means the
        // task stalled (the old "正在加载照片" hang). Recover the UI instead
        // of leaving the busy page up forever.
        ui.postDelayed(new Runnable(){public void run(){
            if(!captureLaunchCurrent(launch)||prepare!=capturePrepareToken||!busy||page==null)return;
            capturePrepareToken++;busy=false;page.busy=false;
            handoffLog("prepare_timeout");
            page.cancelLoadingFilm();
            page.toast("拍照准备超时，请重试");mode="library";render();
        }},15000);
        runTask("正在加载胶卷",new Task(){public void run()throws Exception{
            library.select(entry.id);previewChoice=PreviewProfileStore.choice(entry);
            if(previewChoice.enabled)try{fittedPreview=PreviewFitting.ensure(entry);}catch(Exception e){fitError=explain(e);android.util.Log.w("SonyPreviewFit","prepare_failed",e);}
        }},new Runnable(){public void run(){
            if(!captureLaunchCurrent(launch)||prepare!=capturePrepareToken)return;
            capturePrepareToken++;
            // The global “当前胶卷的取景” setting is authoritative. Selecting
            // a film never opens a preview-mode menu: if the fitted profile is
            // missing or noisy, capture still starts with that stored choice.
            if(previewChoice!=null&&previewChoice.enabled&&fittedPreview==null)
                handoffLog("preview_unavailable lut="+entry.name+" detail="+fitError);
            handoffLog("prepare_done lut="+entry.name+" preview="+(previewChoice!=null&&previewChoice.enabled));
            if(page==null){launchCapture(entry);return;}
            page.finishLoadingFilm(new Runnable(){public void run(){
                if(!captureLaunchCurrent(launch))return;
                handoffLog("film_animation_done lut="+entry.name);
                launchCapture(entry);
            }});
        }});
    }
    private void launchVideo(String id){
        beginCaptureLaunch("start_video");
        if(!claimProcessLaunch("video"))return;
        mode="library";selection=indexOf(id);render();
        captureLaunchAt=SystemClock.elapsedRealtime();
        try{
            startActivityForResult(new Intent(LibraryActivity.this,VideoActivity.class).putExtra("lut_id",id),VIDEO_REQUEST);
        }catch(Throwable e){
            processLaunchPending=false;captureLaunchAt=0;handoffLog("video_launch_failed "+e);render();page.toast("录像模式启动失败");
        }
    }
    private void startRecipeCapture(int recipeId){
        if(recipeId<0||recipeId>=RecipeData.ALL.length)return;
        beginCaptureLaunch("start_recipe");
        if(!claimProcessLaunch("recipe_capture"))return;
        RecipeData.Recipe recipe=RecipeData.ALL[recipeId];
        recipeGroup=RecipeData.groupOf(recipeId);recipeLocal=RecipeData.localIndex(recipeId);lastRecipeId=recipeId;
        rememberRecipe();mode="recipe_cameras";selection=recipeGroup;render();
        captureLaunchAt=SystemClock.elapsedRealtime();
        handoffLog("recipe_capture_launch recipe="+recipe.name+" camera="+RecipeData.GROUPS[recipeGroup].name);
        try{
            startActivityForResult(new Intent(this,PhotoActivity.class)
                .putExtra("recipe_mode",true).putExtra("recipe_id",recipeId)
                .putExtra("recipe_name",Lang.recipeName(recipe)).putExtra("camera_name",Lang.groupName(RecipeData.GROUPS[recipeGroup]))
                .putExtra("film_name",Lang.recipeName(recipe)).putExtra("film_index",recipeId)
                .putExtra("face_detection",TransferSettings.faceDetection(this)),CAPTURE_REQUEST);
        }catch(Throwable e){
            processLaunchPending=false;captureLaunchAt=0;handoffLog("recipe_capture_launch_failed "+e);render();page.toast("配方拍摄模式启动失败");
        }
    }
    private void launchRecipeVideo(int recipeId){
        if(recipeId<0||recipeId>=RecipeData.ALL.length)return;
        beginCaptureLaunch("start_recipe_video");
        if(!claimProcessLaunch("recipe_video"))return;
        RecipeData.Recipe recipe=RecipeData.ALL[recipeId];
        recipeGroup=RecipeData.groupOf(recipeId);recipeLocal=RecipeData.localIndex(recipeId);lastRecipeId=recipeId;
        rememberRecipe();mode="recipe_cameras";selection=recipeGroup;render();
        captureLaunchAt=SystemClock.elapsedRealtime();
        handoffLog("recipe_video_launch recipe="+recipe.name+" camera="+RecipeData.GROUPS[recipeGroup].name);
        try{
            startActivityForResult(new Intent(this,VideoActivity.class)
                .putExtra("recipe_mode",true).putExtra("recipe_id",recipeId)
                .putExtra("recipe_name",Lang.recipeName(recipe)).putExtra("camera_name",Lang.groupName(RecipeData.GROUPS[recipeGroup])),VIDEO_REQUEST);
        }catch(Throwable e){processLaunchPending=false;captureLaunchAt=0;handoffLog("recipe_video_launch_failed "+e);render();page.toast("配方录像模式启动失败");}
    }
    private void relaunchSelected(){
        if(recipeMode()){
            int id=lastRecipeId>=0?lastRecipeId:RecipeData.recipeAt(recipeGroup,recipeLocal);
            startRecipeCapture(id);
        }else if(!luts.isEmpty())startCapture(luts.get(UiList.clamp(selection,luts.size())));
    }
    private void launchCapture(LutLibrary.Entry entry){
            if(!claimProcessLaunch("capture"))return;
            handoffLog("capture_launch lut="+entry.name);
            captureLaunchAt=SystemClock.elapsedRealtime();
            mode="library";selection=indexOf(entry.id);render();
            try{
                startActivityForResult(new Intent(LibraryActivity.this,PhotoActivity.class).putExtra("lut_id",entry.id)
                    .putExtra("film_name",entry.name).putExtra("film_index",indexOf(entry.id))
                    .putExtra("develop_animation",getIntent().getBooleanExtra("develop_animation",true))
                    .putExtra("develop_animation_alternate",getIntent().getBooleanExtra("develop_animation_alternate",false))
                    .putExtra("face_detection",TransferSettings.faceDetection(this)),CAPTURE_REQUEST);
            }catch(Throwable e){
                processLaunchPending=false;captureLaunchAt=0;handoffLog("capture_launch_failed "+e);
                if(page!=null){page.cancelLoadingFilm();render();page.toast("拍照模式启动失败");}
            }
    }
    private void previewSettings(){
        if(luts.isEmpty())return;previewForCapture=false;previewEntry=luts.get(UiList.clamp(lastSelection,luts.size()));fitError="";fittedPreview=null;
        runTask("正在读取预览设置",new Task(){public void run()throws Exception{
            try{previewChoice=PreviewProfileStore.choice(previewEntry);}catch(IOException damaged){previewChoice=null;notice="预览偏好无法读取，请重新选择";}
            try{fittedPreview=PreviewProfileStore.load(previewEntry);}catch(IOException e){fitError=explain(e);}
        }},new Runnable(){public void run(){mode="preview_options";selection=0;render();}});
    }
    private void choosePreview(final boolean enabled){
        final boolean capture=previewForCapture;fitError="";
        final int launch=capture?beginCaptureLaunch("preview_choice"):0;
        runTask(enabled?"正在准备近似预览":"正在保存取景方式",new Task(){public void run()throws Exception{
            if(enabled)try{fittedPreview=PreviewFitting.ensure(previewEntry);}catch(Exception e){fitError=explain(e);return;}
            PreviewProfileStore.choose(previewEntry,enabled,fittedPreview==null?0:fittedPreview.fitVersion);
            previewChoice=PreviewProfileStore.choice(previewEntry);
        }},new Runnable(){public void run(){
            if(fitError.length()>0){mode="preview_error";selection=0;render();return;}
            if(capture){
                if(!captureLaunchCurrent(launch))return;
                if(page==null){launchCapture(previewEntry);return;}
                int index=indexOf(previewEntry.id);
                page.startLoadingFilm(index,previewEntry.name,previewEntry.size+"³");
                page.finishLoadingFilm(new Runnable(){public void run(){
                    if(!captureLaunchCurrent(launch))return;
                    handoffLog("film_animation_done lut="+previewEntry.name);
                    launchCapture(previewEntry);
                }});
            }else{mode="preview_options";selection=enabled?0:1;render();}
        }});
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);
        if(request==CAPTURE_REQUEST||request==VIDEO_REQUEST)processLaunchPending=false;
        if(request==CAPTURE_REQUEST)handoffLog("capture_result code="+result+" data="+(data!=null));
        if(result!=RESULT_OK||data==null)return;
        if(request==CAPTURE_REQUEST&&data.getBooleanExtra("open_video",false)){
            if(data.getBooleanExtra("recipe_mode",false)){
                int recipe=data.getIntExtra("recipe_id",lastRecipeId);if(recipe>=0)lastRecipeId=recipe;
                launchRecipeVideo(recipe);
            }else{
                String id=data.getStringExtra("lut_id");if(id==null)id=selectedId;
                launchVideo(id);
            }
            return;
        }
        if(request==VIDEO_REQUEST&&data.getBooleanExtra("open_photo",false)){
            boolean recipe=data.getBooleanExtra("recipe_mode",false);
            String id=data.getStringExtra("lut_id");if(id==null)id=selectedId;
            if(recipe){
                int selected=data.getIntExtra("recipe_id",lastRecipeId);
                if(selected>=0){lastRecipeId=selected;recipeGroup=RecipeData.groupOf(selected);recipeLocal=RecipeData.localIndex(selected);}
                rememberRecipe();mode="recipe_cameras";selection=recipeGroup;render();
            }else{
                final int index=indexOf(id);selectedId=id;
                mode="library";selection=Math.max(0,index);render();
            }
            // The A7R II resets the camera if the still process opens while the
            // movie process/engine is still tearing down. Wait for the :video
            // process to be gone, then leave a settle window before opening.
            // Keep the shelf busy: letting a manual launch race the delayed
            // auto launch started two PhotoActivity sessions (and one always
            // lost the camera).
            videoLaunchPending=true;busy=true;
            if(page!=null){page.busy=true;page.title="正在切换到拍照模式";page.subtitle="正在等待录像接口释放";page.footer="请勿操作相机";page.empty="正在切换模式";page.note="";page.invalidate();}
            final int token=++photoReturnToken;
            handoffLog("video_return id="+id+" recipe="+recipe);
            waitForVideoExit(token,recipe?recipeLocal:indexOf(id),SystemClock.elapsedRealtime());
            return;
        }
        if(request==CAPTURE_REQUEST)refreshRoll();
        if(request==CAPTURE_REQUEST&&data.getBooleanExtra("open_gallery",false))openGallery(null,data.getIntExtra("page_offset",0));
        else if(request==GALLERY_REQUEST&&data.getBooleanExtra("return_capture",false)){
            if(recipeMode()){mode="recipe_cameras";selection=recipeGroup;render();}
            else{mode="library";selection=indexOf(selectedId);render();}
            relaunchSelected();
        }
        else if(request==GALLERY_REQUEST&&data.getBooleanExtra("photos_changed",false)){refreshRoll();if(mode.equals("gallery"))gallery();}
    }
    private void waitForVideoExit(final int token,final int index,final long started){
        if(destroyed||!videoLaunchPending||token!=photoReturnToken)return;
        long elapsed=SystemClock.elapsedRealtime()-started;
        if(videoProcessRunning()&&elapsed<8000){
            ui.postDelayed(new Runnable(){public void run(){waitForVideoExit(token,index,started);}},250);
            return;
        }
        handoffLog("video_process_gone elapsed="+elapsed+" running="+videoProcessRunning());
        if(page!=null){page.subtitle="录像接口已释放，正在打开拍照";page.invalidate();}
        ui.postDelayed(new Runnable(){public void run(){
            if(destroyed||!videoLaunchPending||token!=photoReturnToken)return;
            videoLaunchPending=false;busy=false;
            if(page!=null)page.busy=false;
            handoffLog("video_settle_done elapsed="+(SystemClock.elapsedRealtime()-started));
            if(recipeMode())startRecipeCapture(lastRecipeId);
            else if(!luts.isEmpty())launchCapture(luts.get(Math.max(0,Math.min(luts.size()-1,index))));
        }},2500);
    }
    private void beautyPage(){
        mode="beauty";selection=0;
        beautySkin=getPreferences(0).getInt("beauty_skin",0);
        beautyDenoise=getPreferences(0).getInt("beauty_denoise",0);
        render();
    }
    private void cycleBeauty(boolean skin){
        int value=skin?beautySkin:beautyDenoise;
        value=(value+1)%3;
        if(skin){beautySkin=value;getPreferences(0).edit().putInt("beauty_skin",value).commit();}
        else{beautyDenoise=value;getPreferences(0).edit().putInt("beauty_denoise",value).commit();}
        render();
    }
    private static String beautyLevel(int value){return value<=0?"关闭":value==1?"弱":"强";}
    private void shootModePage(){mode="shoot_mode";selection=recipeMode()?1:0;render();}
    private void setShootMode(String value){
        shootMode="recipe".equals(value)?"recipe":"lut";
        getPreferences(0).edit().putString("shoot_mode",shootMode).commit();
        lastSelection=recipeMode()?recipeGroup:indexOf(selectedId);
        navDepth=-1;mode=homeMode();selection=homeSelection();render();
        if(!recipeMode())requestPreview();
        if(page!=null)page.toast(recipeMode()?"已切换到近似胶片配方模式":"已切换到真 LUT 模式");
    }
    private void action(){if(busy||videoLaunchPending||processLaunchPending)return;
        if(mode.equals("library")){if(luts.isEmpty())return;startCapture(luts.get(selection));
        }else if(mode.equals("recipe_cameras")){openRecipeGroup(selection);
        }else if(mode.equals("shoot_mode")){
            if(selection==0)setShootMode("lut");
            else if(selection==1)setShootMode("recipe");
            else{mode="options";selection=0;render();}
        }else if(mode.equals("import")){if(selection==0)scan();else libraryPage();
        }else if(mode.equals("files")){if(selection==0){if(importDone)finishImport();else if(!imports.isEmpty())importFiles(-1);else scan();}
            else if(importResults.get(selection-1).startsWith("失败"))showNotice("这个文件未能导入",importResults.get(selection-1));else if(!importDone)importFiles(selection-1);
        }else if(mode.equals("gallery")){if(!photos.isEmpty())openGallery(photos.get(selection).id);
        }else if(mode.equals("options")){
            if(selection==0){shootModePage();return;}
            else if(selection==1){beautyPage();return;}
            else if(selection==2){openTransferPage();return;}
            else if(selection==3){
                boolean enabled=!TransferSettings.faceDetection(this);
                TransferSettings.setFaceDetection(this,enabled);
                render();page.toast(enabled?"人脸优先对焦已开启 · 下次拍照生效":"人脸检测已关闭 · 下次拍照恢复相机设置");
            }
            else if(selection==4){mode="language";selection=UiList.clamp(indexOfLanguage(Lang.code()),Lang.CODES.length);render();return;}
            else if(selection==5){if(recipeMode()){page.toast("请先在“拍摄模式”切换到真 LUT 模式");return;}mode="import";selection=0;}
            else if(selection==6){mode="help";selection=0;}
            else if(selection==7){mode="about";selection=0;}
            else if(selection==8){startActivity(new android.content.Intent(this,UiMotionDemoActivity.class));return;}
            else if(selection==9){homePage();return;}
            else if(selection==10){exitToCamera();return;}
            else if(selection==11){if(recipeMode()){page.toast("配方会直接写入照片，不使用取景拟合");return;}previewSettings();return;}
            else if(selection==12&&SonyAppPolicy.hasDebugHelper(this)){openDebugHelper();return;}
            else{previewSettings();return;}
            render();
        }else if(mode.equals("transfer")){
            if(selection==0)toggleTransfer();
            else if(selection==1){
                if(TransferSettings.enabled(this))TransferService.start(this);
                loadPairingCode();render();page.toast("已刷新传输状态");
            }
            else if(selection==6)transferVideosPage();
            else if(selection==7){
                if(videoSelection==null){page.toast("录像列表尚未载入");return;}
                runTask("正在清空录像选择",new Task(){public void run()throws Exception{
                    videoSelection.clear();selectedVideos=videoSelection.selectedIds();
                }},new Runnable(){public void run(){if(mode.equals("transfer"))render();}});
            }
            else if(selection==8){stopTransferPoll();mode="options";selection=2;render();}
        }else if(mode.equals("transfer_videos")){
            transferVideoAction();
        }else if(mode.equals("language")){
            if(selection>=0&&selection<Lang.CODES.length){
                Lang.set(this,Lang.CODES[selection]);
                render();page.toast(Lang.label()+" · "+Lang.t("语言已切换"));
            }else{mode="options";selection=4;render();}
        }else if(mode.equals("about")){mode="options";selection=7;render();
        }else if(mode.equals("beauty")){
            if(selection==0)cycleBeauty(true);
            else if(selection==1)cycleBeauty(false);
            else{mode="options";selection=1;render();}
        }else if(mode.equals("preview_options")||mode.equals("preview_warning")||mode.equals("preview_error")){
            if(selection==2)libraryPage();else choosePreview(selection==0);
        }else if(mode.equals("exit_error")){if(selection==0)exitToCamera();else if(selection==1&&SonyAppPolicy.hasDebugHelper(this))openDebugHelper();else homePage();
        }else if(mode.equals("help")){mode="options";selection=6;render();
        }else if(mode.equals("notice")){if(previous.equals("loading")){initializeLibrary();return;}mode=previous;selection=0;render();}
    }
    private void renderTransfer(List<CameraPage.Row> rows){
        TransferStatus.Snapshot status=TransferStatus.snapshot();
        boolean enabled=TransferSettings.enabled(this);
        boolean ready=enabled&&TransferStatus.READY.equals(status.state);
        page.section="手机传输";page.title=enabled?"相机 Wi-Fi 与配对":"手机自动接收照片";
        page.subtitle=enabled?"保持本页或取景器打开 · 手机连接相机本地 Wi-Fi":"开启后只在新照片拍完时后台发送";
        rows.add(new CameraPage.Row(enabled?"关闭手机传输":"开启手机传输",
            enabled?"● 关闭服务并恢复相机 Wi-Fi":"● 开启服务 · 手机即可扫描相机 Wi-Fi",enabled?"关闭":"开启"));
        rows.add(new CameraPage.Row("服务状态",enabled?transferStateLabel():"未开启 · 不会启动 Wi-Fi","刷新"));
        rows.add(new CameraPage.Row("Wi-Fi 名称(SSID)",!enabled?"开启后显示":status.ssid.length()==0?"等待相机创建 Wi-Fi Direct 组":status.ssid,"名称"));
        rows.add(new CameraPage.Row("Wi-Fi 密码",!enabled?"开启后显示":status.psk.length()==0?"等待相机生成一次性密码":status.psk,"密码"));
        rows.add(new CameraPage.Row("配对码",pairingLoading?"正在读取":pairingError.length()>0?"读取失败 · "+pairingError:pairingCode.length()==0?"等待 SD 卡":pairingCode,"配对"));
        int port=status.port>0?status.port:TransferSettings.port(this);
        String address=status.address.length()==0?(ready?"自动绑定 · 常用地址 192.168.122.1:"+port:"等待服务启动"):status.address+":"+port;
        rows.add(new CameraPage.Row("地址与端口",address,"网络"));
        rows.add(new CameraPage.Row("录像传输","已勾选 "+selectedVideos.size()+" 个 · 手机端点按下载","选择"));
        rows.add(new CameraPage.Row("清空录像选择","手机端不再显示任何录像","清空"));
        rows.add(new CameraPage.Row("返回","服务设置保持不变","返回"));
        page.note=pairingError.length()>0?"配对信息未能读取 · 请检查 SD 卡写入":ready?(status.phoneConnected?"手机已连接 · 新照片会自动出现在手机相册":"等待手机连接 · 首次需在手机输入配对码"):"真 LUT 只传成片 · 配方只传相机直出 · 原片永不发送";
        page.footer="↑↓ 选择   ● 确认   MENU 返回";
    }
    private String transferStateLabel(){
        TransferStatus.Snapshot status=TransferStatus.snapshot();
        if(!TransferSettings.enabled(this))return "关闭";
        if(TransferStatus.READY.equals(status.state))return (status.phoneConnected?"手机已连接":"等待手机连接")+" · 端口 "+(status.port>0?status.port:TransferSettings.port(this));
        if(TransferStatus.ERROR.equals(status.state))return "启动失败 · "+brief(status.detail,28);
        if(TransferStatus.STARTING.equals(status.state))return "正在启动相机 Wi-Fi…";
        if(TransferStatus.STOPPING.equals(status.state))return "正在关闭传输…";
        return "已开启 · 等待服务启动";
    }
    private static String brief(String text,int max){
        if(text==null)return "";
        text=text.replace('\n',' ').trim();
        return text.length()<=max?text:text.substring(0,max)+"…";
    }
    private static String humanBytes(long bytes){
        if(bytes>=1024L*1024L*1024L)return String.format(Locale.US,"%.2f GB",bytes/(1024.0*1024.0*1024.0));
        if(bytes>=1024L*1024L)return String.format(Locale.US,"%.1f MB",bytes/(1024.0*1024.0));
        if(bytes>=1024L)return String.format(Locale.US,"%.0f KB",bytes/1024.0);
        return bytes+" B";
    }
    private void openTransferPage(){
        mode="transfer";selection=0;pairingCode="";pairingError="";pairingLoading=false;
        if(TransferSettings.enabled(this))TransferService.start(this);
        render();startTransferPoll();loadPairingCode();
    }
    private void startTransferPoll(){
        ui.removeCallbacks(transferPoll);
        if(mode.equals("transfer"))ui.postDelayed(transferPoll,1000);
    }
    private void stopTransferPoll(){ui.removeCallbacks(transferPoll);}
    private void loadPairingCode(){
        if(pairingLoading)return;
        pairingLoading=true;
        io.execute(new Runnable(){public void run(){
            String code=null,error=null;
            try{code=new PairingStore(ProductFiles.transfer()).code();}
            catch(Exception e){error=explain(e);}
            final String finalCode=code,finalError=error;
            ui.post(new Runnable(){public void run(){
                pairingLoading=false;
                if(finalCode!=null)pairingCode=finalCode;else pairingError=finalError==null?"配对信息不可用":finalError;
                if(!destroyed&&mode.equals("transfer"))render();
            }});
        }});
    }
    private void toggleTransfer(){
        if(TransferSettings.enabled(this)){
            TransferSettings.setEnabled(this,false);stopTransferPoll();TransferService.stop(this);
            pairingCode="";pairingError="";
            page.toast("手机传输已关闭");
            mode="options";selection=2;render();
        }else{
            TransferSettings.setEnabled(this,true);
            TransferService.start(this);
            page.toast("手机传输已开启，正在准备相机 Wi-Fi");
            render();startTransferPoll();
        }
    }
    private void transferVideosPage(){
        stopTransferPoll();mode="transfer_videos";selection=0;
        page.section="手机传输";page.title="扫描录像";page.subtitle="正在读取 SD 卡 · 不启动相机";page.rows.clear();page.busy=true;page.invalidate();
        runTask("正在扫描录像",new Task(){public void run()throws Exception{
            videoCatalog=new VideoCatalog(Environment.getExternalStorageDirectory());
            videos=videoCatalog.scan();
            videoSelection=new VideoSelectionStore(videoCatalog,ProductFiles.transfer());
            selectedVideos=videoSelection.selectedIds();
        }},new Runnable(){public void run(){render();}});
    }
    private void toggleVideo(final VideoCatalog.Item item){
        if(item==null||!item.ready){page.toast("录像仍在写入，请稍后再选");return;}
        runTask("正在保存录像选择",new Task(){public void run()throws Exception{
            videoSelection.toggle(item);selectedVideos=videoSelection.selectedIds();
        }},new Runnable(){public void run(){if(mode.equals("transfer_videos"))render();}});
    }
    private void transferVideoAction(){
        if(selection==0){transferVideosPage();return;}
        if(selection==1){
            runTask("正在保存录像选择",new Task(){public void run()throws Exception{
                if(videoSelection==null)throw new IOException("录像列表尚未载入");
                videoSelection.selectAll(videos);selectedVideos=videoSelection.selectedIds();
            }},new Runnable(){public void run(){if(mode.equals("transfer_videos"))render();}});
            return;
        }
        if(selection==2){
            runTask("正在清空录像选择",new Task(){public void run()throws Exception{
                if(videoSelection==null)throw new IOException("录像列表尚未载入");
                videoSelection.clear();selectedVideos=videoSelection.selectedIds();
            }},new Runnable(){public void run(){if(mode.equals("transfer_videos"))render();}});
            return;
        }
        if(selection==3+videos.size()){openTransferPage();return;}
        int at=selection-3;
        if(at>=0&&at<videos.size())toggleVideo(videos.get(at));
    }
    private void exitToCamera(){
        if(exiting||busy)return;exiting=true;previewRequests.next();
        if(page!=null){page.busy=true;page.title="正在返回相机";page.subtitle="正在关闭手机传输并结束会话";page.invalidate();}
        // Close the HTTP port, Wi-Fi Direct group and foreground service before
        // asking Sony to bring its own camera back. Runtime.stopNow is bounded
        // (the server accept loop wakes every 800 ms); exit never waits forever.
        TransferService.stop(this);
        exitRequest=new SonyAppPolicy.ExitRequest(this,new SonyAppPolicy.ExitListener(){public void failed(Exception e){
            exiting=false;ui.removeCallbacks(exitTimeout);android.util.Log.e("LutUI","native_exit_failed",e);
            if(page!=null){page.busy=false;showExitFailure(explain(e));}
        }});
        ui.postDelayed(exitTimeout,12000);exitRequest.start();
    }
    private void showExitFailure(String detail){mode="exit_error";selection=0;notice=detail;render();}
    private void openDebugHelper(){
        try{SonyAppPolicy.openDebugHelper(this);}
        catch(RuntimeException error){android.util.Log.e("LutUI","debug_helper_start_failed",error);showExitFailure("测试连接未能打开，请检查安装状态。");}
    }
    @Override public void onBackPressed(){if(exiting)return;if(page==null){exitToCamera();return;}if(busy)return;if(page.loadingFilm())return;
        if(isHome(mode)){if(mode.equals("library"))lastSelection=selection;else rememberRecipe();mode="options";selection=0;render();}
        else if(mode.equals("notice")){if(previous.equals("loading"))exitToCamera();else action();}
        else if(mode.equals("help")){mode="options";selection=6;render();}
        else if(mode.equals("about")){mode="options";selection=7;render();}
        else if(mode.equals("transfer")){stopTransferPoll();mode="options";selection=2;render();}
        else if(mode.equals("transfer_videos")){openTransferPage();}
        else if(mode.equals("language")){mode="options";selection=4;render();}
        else if(mode.equals("beauty")){mode="options";selection=1;render();}
        else if(mode.equals("shoot_mode")){mode="options";selection=0;render();}
        else if(mode.equals("gallery")){homePage();}
        else if(mode.equals("preview_options")||mode.equals("preview_warning")||mode.equals("preview_error")){libraryPage();}
        else homePage();}
    private void moveSelection(int delta){if(page.rows.isEmpty())return;selection=UiList.move(selection,delta,page.rows.size());render();if(mode.equals("library"))requestPreview();}
    @Override public boolean dispatchKeyEvent(KeyEvent e){if(exiting)return true;int key=UiKeys.key(e),dial=UiKeys.dial(e),control=UiKeys.controlDial(e);if(key==UiKeys.NONE&&dial==0&&control==0)return super.dispatchKeyEvent(e);if(e.getAction()!=KeyEvent.ACTION_DOWN)return true;if(page==null){if(key==UiKeys.BACK)exitToCamera();return true;}if(busy||page.loadingFilm())return true;
        if(key==UiKeys.PLAY){openGallery(null);}
        else if(dial!=0)moveSelection(dial);
        else if(control!=0)moveSelection(control);
        else if(key==UiKeys.UP||key==UiKeys.DOWN){
            int delta=key==UiKeys.UP?-1:1;
            if(mode.equals("recipe_cameras"))delta*=3;
            moveSelection(delta);
        }
        // On the film shelf LEFT/RIGHT and the rotary dial all choose a stock.
        // The old shortcuts that opened playback/import from the shelf are
        // gone; those live under MENU and the playback key.
        else if(key==UiKeys.LEFT||key==UiKeys.RIGHT){
            if(mode.equals("library")||mode.equals("recipe_cameras"))moveSelection(key==UiKeys.LEFT?-1:1);
            else if(mode.equals("files")&&key==UiKeys.RIGHT&&e.getRepeatCount()==0)scan();
        }
        else if(e.getRepeatCount()==0){if(key==UiKeys.BACK)onBackPressed();else if(key==UiKeys.OK)action();}
        return true;
    }
    @Override protected void onResume(){super.onResume();
        // A launched capture process always returns through here (even when it
        // died before delivering a result). Re-arm the single-flight guard.
        processLaunchPending=false;
        if(recipeMode()&&!busy&&mode.equals("recipe_cameras")){
            // The capture process may have confirmed a different film with C3.
            int group=UiList.clamp(RecipeMemory.camera(this,recipeGroup),RecipeData.GROUPS.length);
            int local=UiList.clamp(RecipeMemory.local(this,group,recipeLocal),RecipeData.countInGroup(group));
            if(group!=recipeGroup||local!=recipeLocal){recipeGroup=group;recipeLocal=local;lastRecipeId=RecipeData.recipeAt(group,local);}
            selection=recipeGroup;render();
        }
        if(captureLaunchAt>0){
            long elapsed=SystemClock.elapsedRealtime()-captureLaunchAt;captureLaunchAt=0;
            handoffLog("library_resume after_capture_ms="+elapsed);
            // A capture process that dies before drawing returns us here almost
            // instantly; replace any stale busy frame with a usable page.
            if(elapsed<700&&!busy&&page!=null&&isHome(mode)){render();page.toast("拍照模式未就绪，请再试一次");}
        }
        if(exiting&&page!=null){page.busy=false;exiting=false;render();}exiting=false;
        // Returning from another activity must never leave the shelf showing a
        // stale "switching to photo mode" frame.
        if(page!=null&&!busy&&!videoLaunchPending&&!page.loadingFilm()&&isHome(mode))render();
        if(TransferSettings.enabled(this))TransferService.start(this);
        if(mode.equals("transfer"))startTransferPoll();
        SonyAppPolicy.register(this);if(page!=null)try{page.startDisplayTracking();displayStarted=true;}catch(ClassNotFoundException e){android.util.Log.i("LutLibrary","non-Sony UI preview");}catch(Exception e){showNotice("屏幕初始化失败",explain(e));}}
    @Override protected void onPause(){cancelVideoReturnWait("pause");ui.removeCallbacks(exitTimeout);stopTransferPoll();if(exitRequest!=null)exitRequest.cancel();if(exiting)android.util.Log.i("LutUI","native_exit_activity_paused");if(page!=null&&page.loadingFilm())page.cancelLoadingFilm();if(displayStarted){page.stopDisplayTracking();displayStarted=false;}super.onPause();}
    @Override protected void onDestroy(){destroyed=true;previewRequests.next();io.shutdown();ui.removeCallbacksAndMessages(null);
        if(recipeImages!=null){for(Bitmap bitmap:recipeImages)if(bitmap!=null&&!bitmap.isRecycled())bitmap.recycle();recipeImages=null;}
        if(page!=null){page.preview=null;page.cardImages=null;page.cancelLoadingFilm();}if(previews!=null)previews.clear();super.onDestroy();}
}
