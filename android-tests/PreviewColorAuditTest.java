package org.hongen.sonylutprobe;

public final class PreviewColorAuditTest {
    private static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args){
        int[][] reference={
            {207,103,54},{30,160,90},{80,90,200},{220,200,60},{120,120,120},
            {170,60,90},{40,70,130},{230,140,70},{90,180,170},{200,90,60}
        };
        int[][] identity=new int[reference.length][];
        int[][] rb=new int[reference.length][];
        int[][] chroma=new int[reference.length][];
        for(int i=0;i<reference.length;i++){
            identity[i]=reference[i].clone();
            rb[i]=new int[]{reference[i][2],reference[i][1],reference[i][0]};
            chroma[i]=PreviewColorAudit.chromaSwap(reference[i][0],reference[i][1],reference[i][2]);
        }
        require(PreviewColorAudit.compare(identity,reference).best()==PreviewColorAudit.IDENTITY,"identity model");
        require(PreviewColorAudit.compare(rb,reference).best()==PreviewColorAudit.RB_SWAP,"red/blue model");
        require(PreviewColorAudit.compare(chroma,reference).best()==PreviewColorAudit.CHROMA_SWAP,"chroma model");
        // The A7R II report: an orange chair (about 227,139,69) was displayed
        // as blue (69,139,227). This is the exact red/blue byte swap, not a
        // chroma swap (which would also change green).
        int[] shown=PreviewColorAudit.expected(227,139,69,PreviewColorAudit.RB_SWAP);
        require(Math.abs(shown[0]-69)<=1&&Math.abs(shown[1]-139)<=1&&Math.abs(shown[2]-227)<=1,
                "orange chair became the observed blue: "+shown[0]+","+shown[1]+","+shown[2]);
        PreviewColorAudit.Result result=PreviewColorAudit.compare(chroma,reference);
        require(result.samples==reference.length&&result.first.length()>0,"sample summary");
        System.out.println("PASS preview color audit distinguishes identity, red/blue and chroma swaps ("+result.summary()+")");
    }
}
