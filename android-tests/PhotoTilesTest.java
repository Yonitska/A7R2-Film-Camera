package org.hongen.sonylutprobe;

/** Host checks for the full-42MP playback tile mapping. */
final class PhotoTilesTest {
    private static int checks;
    private static void check(boolean condition,String message){
        checks++;
        if(!condition)throw new AssertionError(message);
    }
    private static void near(float a,float b,String message){
        check(Math.abs(a-b)<1.01f,message+" ("+a+" vs "+b+")");
    }
    private static void roundTrip(int rawW,int rawH,int orientation,float[] rect,String label){
        int[] raw=PhotoTiles.rawBounds(rawW,rawH,orientation,rect[0],rect[1],rect[2],rect[3]);
        float[] back=PhotoTiles.orientedBounds(rawW,rawH,orientation,raw);
        near(back[0],rect[0],label+" left");
        near(back[1],rect[1],label+" top");
        near(back[2],rect[2],label+" right");
        near(back[3],rect[3],label+" bottom");
    }
    public static void main(String[] args){
        int rawW=7952,rawH=5304;
        int[] portrait=PhotoTiles.orientedSize(rawW,rawH,6);
        check(portrait[0]==5304&&portrait[1]==7952,"orientation 6 swaps dimensions");
        int[] landscape=PhotoTiles.orientedSize(rawW,rawH,1);
        check(landscape[0]==7952&&landscape[1]==5304,"orientation 1 keeps dimensions");

        for(int orientation:new int[]{1,3,6,8}){
            int[] size=PhotoTiles.orientedSize(rawW,rawH,orientation);
            int[] raw=PhotoTiles.rawBounds(rawW,rawH,orientation,0,0,size[0],size[1]);
            check(raw[0]==0&&raw[1]==0&&raw[2]==rawW&&raw[3]==rawH,
                    "full frame maps to the whole JPEG for "+orientation);
            roundTrip(rawW,rawH,orientation,new float[]{100,200,3000,2200},"sub rect "+orientation);
            roundTrip(rawW,rawH,orientation,new float[]{0,0,1200,900},"corner "+orientation);
            roundTrip(rawW,rawH,orientation,
                    new float[]{size[0]-800,size[1]-600,size[0],size[1]},"far corner "+orientation);
        }

        check(!PhotoTiles.supported(2)&&!PhotoTiles.supported(5),"flipped orientations fall back");
        check(PhotoTiles.supported(1)&&PhotoTiles.supported(8),"normal/diagonal orientations supported");

        check(PhotoTiles.sampleFor(7952,5304,1400)==8,"maxSide subsample");
        int s=PhotoTiles.tileSample(2863,1910,1400,1600000);
        check(s==2,"zoom tile sharpens within budget: "+s);
        check(((long)(2863/s))*(1910/s)<=1600000,"zoom tile stays inside budget");

        int count=0;
        for(int zoom=2;zoom<=8;zoom++){
            float visibleWidth=rawW/1.44f/zoom;
            float visibleHeight=rawH/1.44f/zoom;
            int sample=PhotoTiles.tileSample((int)visibleWidth,(int)visibleHeight,1400,1600000);
            long bytes=(long)(visibleWidth/sample)*(int)(visibleHeight/sample)*4;
            check(bytes<=6_500_000,"zoom "+zoom+" tile bytes="+bytes);
            count++;
        }
        check(count==7,"all zoom levels considered");
        System.out.println("PhotoTilesTest PASS checks="+checks);
    }
}
