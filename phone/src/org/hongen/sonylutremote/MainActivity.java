package org.hongen.sonylutremote;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.hongen.sonylut.transfer.MediaEntry;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** One-screen pairing and transfer control. The phone app never talks to the
 * camera until /v1/ping succeeds, so a joined Wi-Fi alone is not shown as a
 * working connection. */
public final class MainActivity extends Activity {
    private static final int REQUEST_PERMISSIONS=100;
    private static final int MAX_ROWS=40;
    private static final int INK=0xff211d18,CREAM=0xfff6f0e3,PAPER=0xfffffcf5,
        MUTED=0xff6e665c,ACCENT=0xffb8863b,RED=0xffa2453b,GREEN=0xff466b4a;

    private SettingsStore settings;
    private EditText ssidField,pskField,tokenField,hostField,portField;
    private CheckBox existingBox;
    private TextView statusText,summaryText,addressText,photoCount,videoCount,noteText;
    private LinearLayout photoList,videoList;
    private Button connectButton,stopButton,probeButton,syncButton,retryButton,includeButton;
    private String photoSignature="<init>",videoSignature="<init>";
    private boolean pendingConnect;

    private final SyncStatus.Listener statusListener=new SyncStatus.Listener(){
        public void syncStatusChanged(final SyncStatus.Snapshot snapshot){
            runOnUiThread(new Runnable(){public void run(){render(snapshot);}});
        }
    };

    public void onCreate(Bundle state){
        super.onCreate(state);
        settings=new SettingsStore(this);
        buildUi();
        loadFields();
        SyncStatus.addListener(statusListener);
        render(SyncStatus.snapshot());
    }
    public void onDestroy(){
        SyncStatus.removeListener(statusListener);
        super.onDestroy();
    }

    private void buildUi(){
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(CREAM);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad=dp(18);
        root.setPadding(pad,dp(22),pad,dp(30));
        scroll.addView(root,new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title=text("Sony LUT 手机传片",24,INK,true);
        root.addView(title);
        TextView subtitle=text("A7R2 · 相机本地 Wi-Fi · 只接收相机允许传输的成片",13,MUTED,false);
        subtitle.setPadding(0,dp(4),0,dp(16));
        root.addView(subtitle);

        LinearLayout statusCard=card();
        statusText=text("未启动",18,INK,true);
        summaryText=text("",14,MUTED,false);
        summaryText.setPadding(0,dp(6),0,0);
        addressText=text("",12,MUTED,false);
        addressText.setPadding(0,dp(6),0,0);
        statusCard.addView(statusText);
        statusCard.addView(summaryText);
        statusCard.addView(addressText);
        root.addView(statusCard);

        LinearLayout actionRow=row();
        connectButton=button("连接并同步");
        stopButton=button("停止");
        actionRow.addView(connectButton,weight(1));
        actionRow.addView(space(dp(8)));
        actionRow.addView(stopButton,new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(actionRow);

        LinearLayout actionRow2=row();
        probeButton=smallButton("重新检测服务");
        syncButton=smallButton("立即检查");
        retryButton=smallButton("重试失败");
        actionRow2.addView(probeButton,weight(1));
        actionRow2.addView(space(dp(6)));
        actionRow2.addView(syncButton,weight(1));
        actionRow2.addView(space(dp(6)));
        actionRow2.addView(retryButton,weight(1));
        root.addView(actionRow2);

        noteText=text("Android 10 及以上连接相机 Wi-Fi 时会显示系统确认框，App 无法绕过。传输只在相机 App 打开期间可用。",12,MUTED,false);
        noteText.setPadding(0,dp(10),0,dp(16));
        root.addView(noteText);

        root.addView(section("配对信息"));
        ssidField=field("相机 Wi-Fi 名称（SSID）",InputType.TYPE_CLASS_TEXT,false);
        pskField=field("Wi-Fi 密码（8–63 位）",
            InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD,false);
        tokenField=field("配对码（相机“手机传输”页显示）",
            InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS|InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,false);
        hostField=field("相机地址（例如 192.168.122.1）",
            InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI,false);
        portField=field("端口",InputType.TYPE_CLASS_NUMBER,false);
        root.addView(labeled("SSID",ssidField));
        root.addView(labeled("Wi-Fi 密码",pskField));
        root.addView(labeled("配对码",tokenField));
        root.addView(labeled("地址",hostField));
        root.addView(labeled("端口",portField));
        existingBox=new CheckBox(this);
        existingBox.setText("首次连接时也传输相机里已有的照片");
        existingBox.setTextColor(INK);
        existingBox.setTextSize(14);
        existingBox.setPadding(0,dp(4),0,dp(4));
        root.addView(existingBox);

        LinearLayout saveRow=row();
        Button saveButton=button("保存配对信息");
        saveButton.setOnClickListener(new View.OnClickListener(){public void onClick(View view){
            if(saveFields(false))toast("配对信息已保存");
        }});
        includeButton=button("加入已跳过的照片");
        includeButton.setOnClickListener(new View.OnClickListener(){public void onClick(View view){
            confirmIncludeSkipped();
        }});
        saveRow.addView(saveButton,weight(1));
        saveRow.addView(space(dp(8)));
        saveRow.addView(includeButton,weight(1));
        root.addView(saveRow);

        LinearLayout photoHeader=row();
        photoHeader.addView(section("照片"));
        photoCount=text("",12,MUTED,false);
        photoCount.setGravity(Gravity.END);
        photoHeader.addView(photoCount,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        root.addView(photoHeader);
        photoList=new LinearLayout(this);
        photoList.setOrientation(LinearLayout.VERTICAL);
        root.addView(photoList);

        LinearLayout videoHeader=row();
        videoHeader.addView(section("录像"));
        videoCount=text("",12,MUTED,false);
        videoCount.setGravity(Gravity.END);
        videoHeader.addView(videoCount,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        root.addView(videoHeader);
        TextView videoHint=text("录像需先在相机“手机传输 → 录像传输”里勾选；这里不会自动下载。",12,MUTED,false);
        videoHint.setPadding(0,0,0,dp(6));
        root.addView(videoHint);
        videoList=new LinearLayout(this);
        videoList.setOrientation(LinearLayout.VERTICAL);
        root.addView(videoList);

        TextView footer=text("本地测试版 · GPL-3.0-only · 不对外分发",11,MUTED,false);
        footer.setPadding(0,dp(20),0,0);
        root.addView(footer);

        connectButton.setOnClickListener(new View.OnClickListener(){public void onClick(View view){
            connectPressed();
        }});
        stopButton.setOnClickListener(new View.OnClickListener(){public void onClick(View view){
            SyncService.stop(MainActivity.this);
        }});
        probeButton.setOnClickListener(new View.OnClickListener(){public void onClick(View view){
            SyncService.retryProbe(MainActivity.this);
        }});
        syncButton.setOnClickListener(new View.OnClickListener(){public void onClick(View view){
            SyncService.syncNow(MainActivity.this);
        }});
        retryButton.setOnClickListener(new View.OnClickListener(){public void onClick(View view){
            SyncService.retryFailed(MainActivity.this);
            toast("失败的条目会在下次检查时重试");
        }});
        setContentView(scroll);
    }

    private void loadFields(){
        ssidField.setText(settings.ssid());
        pskField.setText(settings.psk());
        tokenField.setText(settings.token());
        hostField.setText(settings.host().length()==0?"192.168.122.1":settings.host());
        portField.setText(Integer.toString(settings.port()));
        existingBox.setChecked(settings.includeExisting());
    }

    private boolean saveFields(boolean quiet){
        try{
            settings.save(value(ssidField),value(pskField),value(tokenField),value(hostField),
                value(portField),existingBox.isChecked());
            hostField.setText(settings.host());
            portField.setText(Integer.toString(settings.port()));
            tokenField.setText(settings.token());
            return true;
        }catch(IllegalArgumentException error){
            if(!quiet)toast(error.getMessage());
            return false;
        }
    }

    private void connectPressed(){
        if(!saveFields(false))return;
        if(!hasPermissions()){
            pendingConnect=true;
            requestPermissions(missingPermissions().toArray(new String[0]),REQUEST_PERMISSIONS);
            return;
        }
        SyncService.start(this);
    }

    private boolean hasPermissions(){
        for(String permission:missingPermissions())if(!granted(permission))return false;
        return true;
    }
    private List<String> missingPermissions(){
        List<String> needed=new ArrayList<String>();
        if(!granted(Manifest.permission.ACCESS_FINE_LOCATION))needed.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if(!granted(Manifest.permission.ACCESS_COARSE_LOCATION))needed.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        if(Build.VERSION.SDK_INT<=28&&!granted(Manifest.permission.WRITE_EXTERNAL_STORAGE))
            needed.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        return needed;
    }
    private boolean granted(String permission){
        return checkSelfPermission(permission)==PackageManager.PERMISSION_GRANTED;
    }
    public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results){
        super.onRequestPermissionsResult(requestCode,permissions,results);
        if(requestCode!=REQUEST_PERMISSIONS)return;
        boolean connect=pendingConnect;
        pendingConnect=false;
        if(!hasPermissions()){
            toast("需要定位权限连接相机 Wi-Fi；Android 10 及以下还需要存储权限保存照片");
            return;
        }
        if(connect)SyncService.start(this);
    }

    private void confirmIncludeSkipped(){
        new AlertDialog.Builder(this)
            .setTitle("加入已跳过的照片")
            .setMessage("首次连接时被记为“已跳过”的照片会重新加入下载队列。相机里的原片和未允许的 RAW 文件仍然不会被传输。")
            .setPositiveButton("加入",new DialogInterface.OnClickListener(){public void onClick(DialogInterface dialog,int which){
                SyncService.includeSkipped(MainActivity.this);
                toast("已加入，等待下次检查");
            }})
            .setNegativeButton("取消",null)
            .show();
    }

    private void render(final SyncStatus.Snapshot snapshot){
        String label=stateLabel(snapshot.state);
        statusText.setText(label);
        statusText.setTextColor(SyncStatus.ERROR.equals(snapshot.state)?RED:
            snapshot.connected()?GREEN:INK);
        String message=snapshot.message.length()==0?"":snapshot.message;
        SyncStatus.Item downloading=downloading(snapshot);
        if(downloading!=null){
            StringBuilder progress=new StringBuilder("正在传输：").append(downloading.media.name);
            if(downloading.total>0)progress.append("  ").append(downloading.bytes*100/downloading.total).append("%")
                .append("  ").append(human(downloading.bytes)).append(" / ").append(human(downloading.total));
            else progress.append("  ").append(human(downloading.bytes));
            if(SyncStatus.ERROR.equals(snapshot.state)&&message.length()>0)
                progress.append("\n").append(message);
            summaryText.setText(progress.toString());
        }else{
            summaryText.setText(message.length()==0?"尚未连接相机":message);
        }
        addressText.setText(snapshot.host.length()==0
            ?(snapshot.ssid.length()==0?"":("相机 Wi-Fi："+snapshot.ssid))
            :("相机 Wi-Fi："+(snapshot.ssid.length()==0?"—":snapshot.ssid)+"  ·  地址："+snapshot.host+":"+snapshot.port));

        boolean running=snapshot.running;
        boolean connected=snapshot.connected();
        connectButton.setText(connected?"重新连接并同步":"连接并同步");
        stopButton.setEnabled(running);
        probeButton.setEnabled(running);
        syncButton.setEnabled(connected);
        retryButton.setEnabled(running);
        includeButton.setEnabled(running);

        photoCount.setText(counts(snapshot.photos));
        videoCount.setText(counts(snapshot.videos));
        String nextPhotoSignature=signature(snapshot.photos);
        if(!nextPhotoSignature.equals(photoSignature)){
            photoSignature=nextPhotoSignature;
            rebuildPhotos(snapshot.photos,connected);
        }
        String nextVideoSignature=signature(snapshot.videos)+"|"+connected;
        if(!nextVideoSignature.equals(videoSignature)){
            videoSignature=nextVideoSignature;
            rebuildVideos(snapshot.videos,connected);
        }
    }

    private void rebuildPhotos(List<SyncStatus.Item> items,boolean connected){
        photoList.removeAllViews();
        if(items.isEmpty()){
            photoList.addView(empty("还没有相机传来的照片。相机拍完并写入传输记录后会出现在这里。"));
            return;
        }
        int limit=Math.min(items.size(),MAX_ROWS);
        for(int i=0;i<limit;i++)photoList.addView(photoRow(items.get(i)));
        if(items.size()>limit)photoList.addView(empty("仅显示最近 "+limit+" 项，共 "+items.size()+" 项。"));
    }
    private void rebuildVideos(List<SyncStatus.Item> items,boolean connected){
        videoList.removeAllViews();
        if(items.isEmpty()){
            videoList.addView(empty("相机没有勾选录像。先在相机的“手机传输 → 录像传输”中勾选。"));
            return;
        }
        int limit=Math.min(items.size(),MAX_ROWS);
        for(int i=0;i<limit;i++)videoList.addView(videoRow(items.get(i),connected));
        if(items.size()>limit)videoList.addView(empty("仅显示最近 "+limit+" 项，共 "+items.size()+" 项。"));
    }

    private View photoRow(SyncStatus.Item item){
        LinearLayout box=cardRow();
        LinearLayout left=new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        TextView name=text(item.media.name,15,INK,true);
        name.setSingleLine(true);
        TextView detail=text(mediaDetail(item),12,MUTED,false);
        detail.setPadding(0,dp(3),0,0);
        left.addView(name);
        left.addView(detail);
        box.addView(left,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        box.addView(meta(item,true),new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        return box;
    }
    private View videoRow(final SyncStatus.Item item,boolean connected){
        LinearLayout box=cardRow();
        LinearLayout left=new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        TextView name=text(item.media.name,15,INK,true);
        name.setSingleLine(true);
        TextView detail=text(human(item.media.size)+" · "+time(item.media.time),12,MUTED,false);
        detail.setPadding(0,dp(3),0,0);
        left.addView(name);
        left.addView(detail);
        box.addView(left,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));

        LinearLayout right=new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.END);
        TextView state=meta(item,false);
        right.addView(state);
        Button download=smallButton("下载");
        boolean done="done".equals(item.state);
        boolean downloading="downloading".equals(item.state);
        download.setText(done?"已下载":downloading?"传输中":"下载");
        download.setEnabled(connected&&!done&&!downloading);
        download.setOnClickListener(new View.OnClickListener(){public void onClick(View view){
            confirmVideo(item.media);
        }});
        right.addView(download);
        box.addView(right);
        return box;
    }

    private void confirmVideo(final MediaEntry entry){
        new AlertDialog.Builder(this)
            .setTitle("下载录像")
            .setMessage(entry.name+"\n"+human(entry.size)+"\n\n传输期间请保持相机 App 打开。")
            .setPositiveButton("下载",new DialogInterface.OnClickListener(){public void onClick(DialogInterface dialog,int which){
                SyncService.downloadVideo(MainActivity.this,entry.id);
                toast("已加入下载队列");
            }})
            .setNegativeButton("取消",null)
            .show();
    }

    private TextView meta(SyncStatus.Item item,boolean photo){
        String state;
        if("downloading".equals(item.state)){
            state=item.total>0?("传输中 "+item.bytes*100/item.total+"%"):"传输中";
        }else if("done".equals(item.state))state="已保存";
        else if("skipped".equals(item.state))state="已跳过";
        else if("failed".equals(item.state))state="失败";
        else state="等待传输";
        if("failed".equals(item.state)&&item.detail.length()>0)state+=" · "+item.detail;
        TextView view=text(state,12,"failed".equals(item.state)?RED:"done".equals(item.state)?GREEN:MUTED,false);
        view.setGravity(Gravity.END);
        return view;
    }
    private String mediaDetail(SyncStatus.Item item){
        StringBuilder out=new StringBuilder();
        if("recipe".equals(item.media.mode))out.append("配方直出");
        else out.append("LUT 成片");
        if(item.media.lut.length()>0)out.append(" · ").append(item.media.lut);
        out.append(" · ").append(human(item.media.size));
        out.append(" · ").append(time(item.media.time));
        return out.toString();
    }

    private String counts(List<SyncStatus.Item> items){
        int waiting=0,done=0,failed=0,skipped=0,active=0;
        for(SyncStatus.Item item:items){
            if("done".equals(item.state))done++;
            else if("failed".equals(item.state))failed++;
            else if("skipped".equals(item.state))skipped++;
            else if("downloading".equals(item.state))active++;
            else waiting++;
        }
        StringBuilder out=new StringBuilder();
        if(active>0)out.append("传输 ").append(active).append(" · ");
        out.append("待传 ").append(waiting).append(" · 已保存 ").append(done);
        if(failed>0)out.append(" · 失败 ").append(failed);
        if(skipped>0)out.append(" · 跳过 ").append(skipped);
        return out.toString();
    }
    private String signature(List<SyncStatus.Item> items){
        StringBuilder out=new StringBuilder();
        int limit=Math.min(items.size(),MAX_ROWS);
        for(int i=0;i<limit;i++){
            SyncStatus.Item item=items.get(i);
            out.append(item.media.id).append(':').append(item.state).append(':').append(item.detail).append('|');
        }
        if(items.size()>limit)out.append('#').append(items.size());
        return out.toString();
    }
    private SyncStatus.Item downloading(SyncStatus.Snapshot snapshot){
        for(SyncStatus.Item item:snapshot.photos)if("downloading".equals(item.state))return item;
        for(SyncStatus.Item item:snapshot.videos)if("downloading".equals(item.state))return item;
        return null;
    }
    private static String stateLabel(String state){
        if(SyncStatus.CONNECTING.equals(state))return "正在连接";
        if(SyncStatus.CONNECTED.equals(state))return "已连接";
        if(SyncStatus.SYNCING.equals(state))return "正在同步";
        if(SyncStatus.ERROR.equals(state))return "连接异常";
        if(SyncStatus.STOPPED.equals(state))return "传输已停止";
        return "未启动";
    }
    private static String time(long millis){
        if(millis<=0)return "";
        return new SimpleDateFormat("MM-dd HH:mm",Locale.getDefault()).format(new Date(millis));
    }
    private static String human(long bytes){
        if(bytes<1024)return bytes+" B";
        if(bytes<1024L*1024L)return String.format(Locale.US,"%.1f KB",bytes/1024.0);
        if(bytes<1024L*1024L*1024L)return String.format(Locale.US,"%.1f MB",bytes/(1024.0*1024.0));
        return String.format(Locale.US,"%.2f GB",bytes/(1024.0*1024.0*1024.0));
    }

    private void toast(String message){
        Toast.makeText(this,message,Toast.LENGTH_SHORT).show();
    }
    private static String value(EditText field){return field.getText().toString().trim();}

    private TextView section(String value){
        TextView view=text(value,16,ACCENT,true);
        view.setPadding(0,dp(20),0,dp(8));
        return view;
    }
    private TextView empty(String value){
        TextView view=text(value,13,MUTED,false);
        view.setPadding(dp(2),dp(8),0,dp(8));
        return view;
    }
    private TextView text(String value,int size,int color,boolean bold){
        TextView view=new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if(bold)view.setTypeface(view.getTypeface(),android.graphics.Typeface.BOLD);
        return view;
    }
    private EditText field(String hint,int inputType,boolean multiLine){
        EditText field=new EditText(this);
        field.setHint(hint);
        field.setHintTextColor(0xff9b9184);
        field.setTextColor(INK);
        field.setTextSize(15);
        field.setInputType(inputType);
        field.setSingleLine(!multiLine);
        field.setBackgroundColor(PAPER);
        field.setPadding(dp(10),dp(8),dp(10),dp(8));
        return field;
    }
    private View labeled(String label,View field){
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0,dp(4),0,dp(4));
        TextView name=text(label,12,MUTED,false);
        name.setPadding(dp(2),0,0,dp(3));
        box.addView(name);
        box.addView(field,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        return box;
    }
    private Button button(String value){
        Button button=new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTextColor(Color.WHITE);
        GradientDrawable shape=new GradientDrawable();
        shape.setColor(0xff2c261f);
        shape.setCornerRadius(dp(10));
        button.setBackground(shape);
        button.setPadding(dp(12),0,dp(12),0);
        return button;
    }
    private Button smallButton(String value){
        Button button=new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextSize(12);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(6),dp(6),dp(6),dp(6));
        return button;
    }
    private LinearLayout card(){
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14),dp(14),dp(14),dp(14));
        GradientDrawable shape=new GradientDrawable();
        shape.setColor(PAPER);
        shape.setCornerRadius(dp(14));
        shape.setStroke(dp(1),0x33b8863b);
        box.setBackground(shape);
        return box;
    }
    private LinearLayout cardRow(){
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(12),dp(9),dp(12),dp(9));
        GradientDrawable shape=new GradientDrawable();
        shape.setColor(PAPER);
        shape.setCornerRadius(dp(10));
        box.setBackground(shape);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin=dp(6);
        box.setLayoutParams(params);
        return box;
    }
    private LinearLayout row(){
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(0,dp(6),0,0);
        return box;
    }
    private static LinearLayout.LayoutParams weight(float weight){
        return new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,weight);
    }
    private View space(int width){
        View view=new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(width,1));
        return view;
    }
    private int dp(int value){
        return Math.round(getResources().getDisplayMetrics().density*value);
    }
}
