package org.hongen.sonylutprobe;

/** Identifies a channel-order bug in a native preview bitmap by comparing it
 * with the saved JPEG, which Sony encodes correctly. Pure Java so the
 * classifier is host-testable; the caller supplies RGB samples. */
final class PreviewColorAudit {
    static final int IDENTITY=0,RB_SWAP=1,CHROMA_SWAP=2;
    static final String[] NAMES={"identity","rb_swap","chroma_swap"};

    static final class Result {
        final long identity,rbSwap,chromaSwap;final int samples;final String first;
        Result(long identity,long rbSwap,long chromaSwap,int samples,String first){
            this.identity=identity;this.rbSwap=rbSwap;this.chromaSwap=chromaSwap;
            this.samples=samples;this.first=first;
        }
        int best(){return identity<=rbSwap&&identity<=chromaSwap?IDENTITY:rbSwap<=chromaSwap?RB_SWAP:CHROMA_SWAP;}
        String summary(){return NAMES[best()]+" identity="+identity+" rb="+rbSwap+" chroma="+chromaSwap;}
    }

    private PreviewColorAudit(){}

    static Result compare(int[][] preview,int[][] jpeg){
        if(preview==null||jpeg==null||preview.length!=jpeg.length)
            throw new IllegalArgumentException("preview/jpeg sample mismatch");
        long identity=0,rb=0,chroma=0;StringBuilder first=new StringBuilder();int used=0;
        for(int i=0;i<preview.length;i++){
            int[] p=preview[i],j=jpeg[i];
            if(p==null||j==null||p.length<3||j.length<3)throw new IllegalArgumentException("invalid sample");
            identity+=error(p,j[0],j[1],j[2],IDENTITY);
            rb+=error(p,j[0],j[1],j[2],RB_SWAP);
            chroma+=error(p,j[0],j[1],j[2],CHROMA_SWAP);
            if(used<3)first.append('(').append(p[0]).append(',').append(p[1]).append(',').append(p[2])
                    .append("<-").append(j[0]).append(',').append(j[1]).append(',').append(j[2]).append(')');
            used++;
        }
        return new Result(identity,rb,chroma,used,first.toString());
    }
    static long error(int[] actual,int r,int g,int b,int model){
        int[] expected=expected(r,g,b,model);
        long dr=actual[0]-expected[0],dg=actual[1]-expected[1],db=actual[2]-expected[2];
        return dr*dr+dg*dg+db*db;
    }
    static int[] expected(int r,int g,int b,int model){
        if(model==RB_SWAP)return new int[]{b,g,r};
        if(model==CHROMA_SWAP)return chromaSwap(r,g,b);
        return new int[]{r,g,b};
    }
    /** The RGB a decoder produces when it swaps Cb and Cr. */
    static int[] chromaSwap(int r,int g,int b){
        double y=.299*r+.587*g+.114*b;
        double u=-.168736*r-.331264*g+.5*b;
        double v=.5*r-.418688*g-.081312*b;
        return new int[]{clamp(y+1.402*u),clamp(y-.344136*u-.714136*v),clamp(y+1.772*v)};
    }
    private static int clamp(double value){return (int)Math.max(0,Math.min(255,Math.round(value)));}
}
