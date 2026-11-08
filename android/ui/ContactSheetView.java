package org.hongen.sonylutprobe;

import android.content.Context;
import android.graphics.*;
import android.view.View;

import java.util.Collections;
import java.util.List;

/** Contact-print playback: a 4x3 sheet of saved thumbnail JPEGs.
 *
 * Thumbnails are produced once during capture, so opening the sheet never
 * decodes a 42MP original. Photos captured by older builds fall back to one
 * sampled decode and are then cached on the card. */
final class ContactSheetView extends View {
    interface Provider {
        Bitmap thumb(int index);
        void request(int index);
    }

    static final int COLS=4,ROWS=3,PAGE=COLS*ROWS;
    static final int THUMB_W=PhotoThumbs.GRID_W,THUMB_H=PhotoThumbs.GRID_H;

    private List<PhotoCatalog.Pair> photos=Collections.emptyList();
    private Provider provider;
    private int selected;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bmpPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint latin=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cjk=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF cell=new RectF();

    ContactSheetView(Context context){
        super(context);
        latin.setTypeface(FilmStyle.condensed(context));latin.setTextAlign(Paint.Align.LEFT);
        cjk.setTypeface(FilmStyle.cjk(context));cjk.setTextAlign(Paint.Align.LEFT);
        p.setTypeface(FilmStyle.cjk(context));
        stroke.setStyle(Paint.Style.STROKE);
        setBackgroundColor(FilmStyle.BG);
    }

    void setProvider(Provider value){provider=value;}
    void setPhotos(List<PhotoCatalog.Pair> value){
        photos=value==null?Collections.<PhotoCatalog.Pair>emptyList():value;
        if(selected>=photos.size())selected=Math.max(0,photos.size()-1);
        invalidate();
    }
    void setSelection(int value){
        selected=Math.max(0,Math.min(Math.max(0,photos.size()-1),value));
        invalidate();
    }
    int selection(){return selected;}

    private void text(Canvas c,Paint paint,String s,float x,float y,float size,int color){
        paint.setTextAlign(Paint.Align.LEFT);paint.setColor(color);paint.setTextSize(size);c.drawText(Lang.tr(s),x,y,paint);
    }
    private void right(Canvas c,Paint paint,String s,float x,float y,float size,int color){
        paint.setTextAlign(Paint.Align.RIGHT);paint.setColor(color);paint.setTextSize(size);c.drawText(Lang.tr(s),x,y,paint);
    }
    private void center(Canvas c,Paint paint,String s,float x,float y,float size,int color){
        paint.setTextAlign(Paint.Align.CENTER);paint.setColor(color);paint.setTextSize(size);c.drawText(Lang.tr(s),x,y,paint);
    }
    private static String fit(Paint paint,String s,float width,float size){
        if(s==null)return "";
        s=Lang.tr(s);
        paint.setTextSize(size);
        if(paint.measureText(s)<=width)return s;
        int n=paint.breakText(s,true,Math.max(0,width-paint.measureText("…")),null);
        return s.substring(0,n)+"…";
    }

    @Override protected void onDraw(Canvas c){
        float scale=getHeight()/480f;if(scale<=0)return;
        float w=getWidth()/scale;
        c.save();c.scale(scale,scale);
        p.setStyle(Paint.Style.FILL);
        // Charcoal body with a slimmer top strip than the main pages: the
        // sheet is mostly picture area.
        p.setColor(FilmStyle.BG);c.drawRect(0,0,w,480,p);
        p.setColor(0xff121110);c.drawRect(0,24,w,470,p);
        p.setColor(0xff13110f);c.drawRect(0,0,w,24,p);
        p.setColor(FilmStyle.ACCENT);c.drawRect(0,22,w,24,p);
        // Sprocket perforations along the top edge, like a film strip.
        p.setColor(0xff2b241d);
        for(float x=8;x<w-8;x+=38)c.drawRect(x,7,x+14,13,p);

        text(c,latin,"CONTACTS",20,44,14,FilmStyle.ACCENT);
        String count=photos.isEmpty()?"0 张":(photos.size()+" 张");
        right(c,cjk,count,w-20,44,12,FilmStyle.MUTED);

        float left=20,right=w-20,gap=8;
        float cw=(right-left-gap*(COLS-1))/COLS;
        float ch=cw*2f/3f;
        float top=58,rowGap=10;
        int page=selected/PAGE,first=page*PAGE;
        int last=Math.min(photos.size(),first+PAGE);
        for(int slot=0;slot<PAGE&&first+slot<last;slot++){
            int i=first+slot,row=slot/COLS,col=slot%COLS;
            float x=left+col*(cw+gap),y=top+row*(ch+rowGap);
            boolean focus=i==selected;
            drawCell(c,x,y,cw,ch,i,focus);
        }
        if(photos.isEmpty()){
            center(c,cjk,"还没有照片",w/2,230,22,FilmStyle.TEXT);
            center(c,cjk,"拍摄后在这里查看接触印相",w/2,262,14,FilmStyle.MUTED);
        }

        // Footer: selected frame, page position and the film language key hint.
        p.setColor(0xff13110f);c.drawRect(0,386,w,480,p);
        p.setColor(FilmStyle.LINE);c.drawRect(0,385,w,386,p);
        if(!photos.isEmpty()&&selected<photos.size()){
            PhotoCatalog.Pair pair=photos.get(selected);
            String ordinal="第 "+String.format(java.util.Locale.US,"%02d",photos.size()-selected)+" 帧";
            text(c,cjk,ordinal,20,412,13,FilmStyle.TEXT);
            text(c,cjk,fit(cjk,pair.lut,w*.55f,13),92,412,13,FilmStyle.MUTED);
            int pages=Math.max(1,(photos.size()+PAGE-1)/PAGE);
            right(c,latin,"PAGE "+(page+1)+"/"+pages,w-20,412,11,FilmStyle.ACCENT);
            p.setColor(FilmStyle.ACCENT);c.drawRect(20,424,23,426,p);
            text(c,cjk,"←→↑↓ 选择   ● 打开   MENU/BACK 关闭",32,456,13,FilmStyle.MUTED);
        }else{
            text(c,cjk,"MENU 返回",20,456,13,FilmStyle.MUTED);
        }
        c.restore();
    }

    private void drawCell(Canvas c,float x,float y,float cw,float ch,int i,boolean focus){
        p.setColor(focus?0xff24201a:0xff1d1a16);
        c.drawRect(x,y,x+cw,y+ch,p);
        p.setColor(0xff0a0d0f);c.drawRect(x+3,y+3,x+cw-3,y+ch-3,p);
        Bitmap bitmap=provider==null?null:provider.thumb(i);
        if(bitmap!=null){
            // Thumbnails are stored at exactly this cell size (144x96 on the
            // 640x480 panel), so this is a 1:1 blit with no filtering cost.
            cell.set(x,y,x+cw,y+ch);
            c.drawBitmap(bitmap,null,cell,bmpPaint);
        }else{
            if(provider!=null)provider.request(i);
            // Film icon placeholder while the sampled decode is in flight.
            p.setColor(0xff2a241d);
            c.drawRect(x+12,y+ch*.30f,x+cw-12,y+ch*.70f,p);
            p.setColor(0xff51483b);
            c.drawRect(x+14,y+ch*.31f,x+18,y+ch*.33f,p);
            c.drawRect(x+14,y+ch*.67f,x+18,y+ch*.69f,p);
            c.drawRect(x+cw-18,y+ch*.31f,x+cw-14,y+ch*.33f,p);
            c.drawRect(x+cw-18,y+ch*.67f,x+cw-14,y+ch*.69f,p);
        }
        // Frame number tab in the lower right, like a contact sheet.
        String label=String.format(java.util.Locale.US,"%02d",photos.size()-i);
        latin.setTextSize(10);float tw=latin.measureText(label)+10;
        p.setColor(0xb80d0c0a);c.drawRect(x+cw-tw-4,y+ch-16,x+cw-4,y+ch-4,p);
        right(c,latin,label,x+cw-9,y+ch-6,10,focus?FilmStyle.ACCENT:0xffd9d1c2);
        if(focus){
            stroke.setStrokeWidth(2);stroke.setColor(FilmStyle.ACCENT);
            c.drawRect(x+1,y+1,x+cw-1,y+ch-1,stroke);
            p.setColor(FilmStyle.ACCENT);c.drawRect(x+4,y+ch-3,x+cw-4,y+ch,p);
        }
    }
}
