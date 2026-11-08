package org.hongen.sonylutprobe;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashSet;
import java.util.Set;

/** Host checks for the generated five-language catalog and the Traditional
 * Chinese character fallback. The runtime Lang lookup is exercised on the
 * camera; this test keeps the generated data internally consistent. */
public final class I18nTest {
    public static void main(String[] args)throws Exception{
        check("簡體中文".equals(TraditionalChinese.convert("简体中文")),"traditional convert");
        check("語言設置".equals(TraditionalChinese.convert("语言设置")),"traditional char fallback");
        check("ABC 123".equals(TraditionalChinese.convert("ABC 123")),"ascii unchanged");

        int rows=0,rules=0;
        Set<String> sources=new HashSet<String>();
        BufferedReader reader=new BufferedReader(new FileReader("android-assets/I18N.TSV"));
        try{
            String line;
            while((line=reader.readLine())!=null){
                if(line.length()==0||line.charAt(0)=='#')continue;
                String[] cells=line.split("\t",-1);
                check(cells.length==5,"5 columns: "+line);
                for(int i=0;i<5;i++)check(cells[i].length()>0,"empty cell "+i+": "+cells[0]);
                check(sources.add(cells[0]),"duplicate source: "+cells[0]);
                int holes=count(cells[0],"{}");
                if(holes>0){
                    rules++;
                    for(int i=2;i<5;i++)
                        check(count(cells[i],"{}")==holes,"placeholder count: "+cells[0]);
                }
                rows++;
            }
        }finally{reader.close();}
        check(rows>=700,"catalog rows "+rows);
        check(rules>=40,"rule rows "+rules);
        System.out.println("PASS I18nTest rows="+rows+" rules="+rules);
    }

    private static int count(String value,String needle){
        int n=0,at=0;
        while((at=value.indexOf(needle,at))>=0){n++;at+=needle.length();}
        return n;
    }
    private static void check(boolean ok,String message){
        if(!ok)throw new AssertionError(message);
    }
}
