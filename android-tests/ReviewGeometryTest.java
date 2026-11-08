package org.hongen.sonylutprobe;

public final class ReviewGeometryTest {
    private static void near(float a,float b){if(Math.abs(a-b)>.001f)throw new AssertionError(a+" != "+b);}
    private static void fits(int iw,int ih,int vw,int vh,float pixelAspect){
        float[] f=ReviewGeometry.fit(iw,ih,vw,vh,pixelAspect);
        near((iw*f[0]*pixelAspect)/(ih*f[1]),(float)iw/ih);
        if(f[2]<-.001||f[3]<-.001||iw*f[0]>vw+.001||ih*f[1]>vh+.001)throw new AssertionError("crop");
    }
    public static void main(String[] args){
        float panel=16f/9*480/640;
        float[] p=ReviewGeometry.fit(750,500,640,480,panel);
        near(p[0],.72f);near(p[1],.96f);near(p[2],50);near(p[3],0);
        float[] finder=ReviewGeometry.fit(750,500,640,480,1);
        near(finder[0],finder[1]);near(finder[2],0);near(finder[3],26.666666f);
        for(int[] size:new int[][]{{750,500},{500,750},{800,800},{854,480}}){
            fits(size[0],size[1],640,480,panel);
            fits(size[0],size[1],640,480,1);
            fits(size[0],size[1],640,393,panel);
        }
        float[] identity=ReviewGeometry.zoomFit(750,500,640,480,panel,1,0,0);
        for(int i=0;i<4;i++)near(identity[i],p[i]);
        float[] zoom=ReviewGeometry.zoomFit(750,500,640,480,panel,2,0,0);
        near(zoom[0],p[0]*2);near(zoom[1],p[1]*2);near(zoom[2],-220);near(zoom[3],-240);
        near(ReviewGeometry.zoomFit(750,500,640,480,panel,2,1000,1000)[2],0);
        near(ReviewGeometry.zoomFit(750,500,640,480,panel,2,-1000,-1000)[2],-440);
        near(ReviewGeometry.zoomFit(750,500,640,480,panel,2,-1000,-1000)[3],-480);
        near(ReviewGeometry.zoomFit(1000,100,640,480,1,2,0,0)[2],-320);
        near(ReviewGeometry.zoomFit(1000,100,640,480,1,2,0,0)[3],176);
        System.out.println("PASS physical aspect, no crop and centered/clamped 2x zoom: wide panel, square-pixel finder, portrait and reduced viewport");
    }
}
