package org.hongen.sonylutprobe;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Five-language UI layer for the A7R II build.
 *
 * Translations live in assets/I18N.TSV. The first column is the Simplified
 * Chinese source text written by the UI; the next four columns are
 * Traditional Chinese, English, Japanese and Korean. A blank cell means the
 * caller falls back to English (for Japanese/Korean), then to the source.
 *
 * Most screens build their labels every frame, so the lookup is deliberately
 * cheap: an exact HashMap hit, then a bounded cache for interpolated labels.
 * No camera API or font work happens here. */
final class Lang {
    static final String ZH_CN="zh-CN",ZH_TW="zh-TW",EN="en",JA="ja",KO="ko";
    static final String[] CODES={ZH_CN,ZH_TW,EN,JA,KO};
    private static final String[] LABELS={"简体中文","繁體中文","English","日本語","한국어"};
    private static final String[] SHORT={"简中","繁中","EN","日本語","한국어"};
    private static final String PREFS="sony_lut_camera_language";
    private static final String KEY="language";
    private static final String ASSET="I18N.TSV";

    private static final Map<String,String[]> EXACT=new HashMap<String,String[]>();
    private static final List<Rule> RULES=new ArrayList<Rule>();
    private static final Map<String,String> CACHE=new HashMap<String,String>();
    private static String code=ZH_CN;
    private static boolean loaded;

    private Lang(){}

    static synchronized void init(Context context){
        if(context==null)return;
        if(!loaded){
            try{
                loaded=true;
                BufferedReader reader=new BufferedReader(new InputStreamReader(
                        context.getAssets().open(ASSET),"UTF-8"));
                try{
                    String line;
                    while((line=reader.readLine())!=null){
                        if(line.length()==0||line.charAt(0)=='#')continue;
                        String[] cells=line.split("\t",-1);
                        if(cells.length<5)continue;
                        String source=unescape(cells[0]);
                        if(source.length()==0)continue;
                        String[] values=new String[]{
                                unescape(cells[1]),unescape(cells[2]),
                                unescape(cells[3]),unescape(cells[4])};
                        if(source.indexOf("{}")>=0)RULES.add(new Rule(source,values));
                        else EXACT.put(source,values);
                    }
                }finally{reader.close();}
            }catch(Throwable error){
                android.util.Log.w("LutLang","i18n_load_failed",error);
                EXACT.clear();RULES.clear();
            }
        }
        String saved=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
                .getString(KEY,ZH_CN);
        code=valid(saved)?saved:ZH_CN;
    }

    static synchronized String code(){return code;}
    static synchronized boolean isChinese(){return ZH_CN.equals(code)||ZH_TW.equals(code);}
    static synchronized boolean isSimplified(){return ZH_CN.equals(code);}
    static String label(){return labelFor(code);}
    static String labelFor(String value){
        for(int i=0;i<CODES.length;i++)if(CODES[i].equals(value))return LABELS[i];
        return LABELS[0];
    }
    static String shortLabel(){return shortFor(code);}
    static String shortFor(String value){
        for(int i=0;i<CODES.length;i++)if(CODES[i].equals(value))return SHORT[i];
        return SHORT[0];
    }

    static synchronized void set(Context context,String value){
        if(!valid(value))return;
        code=value;CACHE.clear();
        if(context!=null)context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
                .edit().putString(KEY,value).commit();
    }

    static String t(String source){return tr(source);}

    /** Translate any label built by the UI. Source-language strings pass
     * through untouched, so Chinese diagnostics in logs are never altered. */
    static String tr(String source){
        if(source==null||source.length()==0)return source;
        String target=code;
        if(ZH_CN.equals(target))return source;
        String cached;
        synchronized(Lang.class){
            cached=CACHE.get(source);
        }
        if(cached!=null)return cached;

        String translated=null;
        String[] exact;
        synchronized(Lang.class){exact=EXACT.get(source);}
        if(exact!=null)translated=pick(exact,target);

        if(translated==null){
            List<Rule> rules;
            synchronized(Lang.class){rules=RULES;}
            for(int i=0;i<rules.size();i++){
                translated=rules.get(i).apply(source,target);
                if(translated!=null)break;
            }
        }

        if(translated==null)translated=compound(source,target);
        if(translated==null&&ZH_TW.equals(target))translated=TraditionalChinese.convert(source);
        if(translated==null&&(JA.equals(target)||KO.equals(target))){
            String[] english;
            synchronized(Lang.class){english=EXACT.get(source);}
            if(english!=null)translated=pick(english,EN);
        }
        if(translated==null||translated.length()==0)translated=source;
        else if(translated!=source&&!ZH_TW.equals(target))
            translated=punctuation(translated,target);

        synchronized(Lang.class){
            if(CACHE.size()>768)CACHE.clear();
            CACHE.put(source,translated);
        }
        return translated;
    }

    static String recipeName(RecipeData.Recipe recipe){
        if(recipe==null)return "";
        if(ZH_TW.equals(code))return TraditionalChinese.convert(recipe.zh);
        return isChinese()?recipe.zh:recipe.name;
    }
    static String groupName(RecipeData.Group group){
        if(group==null)return "";
        if(ZH_TW.equals(code))return TraditionalChinese.convert(group.zh);
        return isChinese()?group.zh:group.name;
    }
    /** Compact camera-setting line under the recipe name. The Chinese path
     * keeps the established wording; other languages use the Recipe Lab
     * English control names so the line stays readable without inventing
     * camera terms that Sony's menus do not use. */
    static String recipeSummary(RecipeData.Recipe r){
        if(r==null)return "";
        if(ZH_TW.equals(code))return TraditionalChinese.convert(RecipeData.summary(r));
        if(isChinese())return RecipeData.summary(r);
        StringBuilder s=new StringBuilder();
        if(r.isEffect()){
            s.append(english(RecipeData.peName(r.pe)));
            String sub=RecipeData.subRuntime(r.pe,r.sub);
            if(sub!=null&&sub.length()>0)s.append(' ').append(english(sub));
        }else{
            s.append(english(RecipeData.styleName(r.style))).append("  ")
                    .append(r.sat>0?"+":"").append(r.sat).append('/')
                    .append(r.con>0?"+":"").append(r.con);
        }
        if(r.ev!=0)s.append("  ").append(RecipeData.evLabel(r.ev));
        if(r.dro!=6)s.append(r.dro==0?"  DRO Off":"  DRO Lv"+r.dro);
        if(r.wbMode==14)s.append("  ").append(r.kelvin).append('K');
        if(r.ab!=0)s.append("  ").append(r.ab>0?"A"+r.ab:"B"+(-r.ab));
        if(r.gm!=0)s.append("  ").append(r.gm>0?"G"+r.gm:"M"+(-r.gm));
        return s.toString();
    }
    private static String english(String key){
        if(key==null)return "";
        StringBuilder out=new StringBuilder(key.length());
        boolean upper=true;
        for(int i=0;i<key.length();i++){
            char ch=key.charAt(i);
            if(ch=='-'||ch=='_'||ch=='.'){out.append(' ');upper=true;continue;}
            if(upper){out.append(Character.toUpperCase(ch));upper=false;}
            else out.append(ch);
        }
        return out.toString();
    }

    /** Labels are frequently assembled from a translated fragment plus a
     * number or a product name: "已拍 12 张", "胶卷库 · 9". Translate those
     * pieces in place. Only use the result when every piece that contains Han
     * characters is known, so a missing entry cannot produce a half-English
     * screen; the exact lookup and regex rules above still win. */
    private static String compound(String source,String target){
        StringBuilder out=new StringBuilder(source.length()+16);
        int start=0,translated=0;
        for(int i=0;i<=source.length();i++){
            boolean end=i==source.length(),separator=!end&&isSeparator(source.charAt(i));
            if(!end&&!separator)continue;
            if(i>start){
                String piece=source.substring(start,i);
                String value=piece(source,piece,target);
                if(value==null){
                    if(hasHan(piece))return null;
                    value=piece;
                }else translated++;
                out.append(value);
            }
            if(!end)out.append(source.charAt(i));
            start=i+1;
        }
        return translated==0?null:out.toString();
    }
    private static String piece(String whole,String part,String target){
        if(!hasHan(part))return null;
        String[] values;
        synchronized(Lang.class){values=EXACT.get(part);}
        String value=values==null?null:pick(values,target);
        if(value==null){
            List<Rule> rules;
            synchronized(Lang.class){rules=RULES;}
            for(int i=0;i<rules.size();i++){
                value=rules.get(i).apply(part,target);
                if(value!=null)break;
            }
        }
        if(value==null&&(JA.equals(target)||KO.equals(target))){
            String[] english;
            synchronized(Lang.class){english=EXACT.get(part);}
            if(english!=null)value=pick(english,EN);
        }
        return value;
    }
    /** Compound labels keep the source separators. Swap Chinese punctuation
     * for ASCII in English/Korean and for the Japanese comma in Japanese so a
     * half-translated line still reads naturally. */
    private static String punctuation(String value,String target){
        boolean japanese=JA.equals(target);
        StringBuilder out=new StringBuilder(value.length()+8);
        for(int i=0;i<value.length();i++){
            char ch=value.charAt(i);
            String rep=null;
            if(ch=='，')rep=japanese?"、":", ";
            else if(ch=='。')rep=japanese?"。":". ";
            else if(ch=='：')rep=japanese?"：":": ";
            else if(ch=='；')rep=japanese?"；":"; ";
            else if(ch=='！')rep=japanese?"！":"! ";
            else if(ch=='？')rep=japanese?"？":"? ";
            if(rep==null){out.append(ch);continue;}
            if(rep.endsWith(" ")&&i+1<value.length()&&value.charAt(i+1)==' ')
                rep=rep.substring(0,rep.length()-1);
            out.append(rep);
        }
        return out.toString();
    }
    private static boolean isSeparator(char ch){
        return ch==' '||ch=='：'||ch=='，'||ch=='、'||ch=='/'||ch=='·'
                ||ch=='（'||ch=='）'||ch=='('||ch==')'||ch=='「'||ch=='」'
                ||ch=='“'||ch=='”'||ch=='\n'||ch=='\t';
    }
    private static boolean hasHan(String value){
        for(int i=0;i<value.length();i++){
            char ch=value.charAt(i);
            if(ch>=0x3400&&ch<=0x9fff)return true;
        }
        return false;
    }

    private static String pick(String[] values,String target){
        int index=indexOf(target);
        if(index<0)return null;
        if(index<values.length&&values[index].length()>0)return values[index];
        return null;
    }
    private static int indexOf(String target){
        if(ZH_TW.equals(target))return 0;
        if(EN.equals(target))return 1;
        if(JA.equals(target))return 2;
        if(KO.equals(target))return 3;
        return -1;
    }
    private static boolean valid(String value){
        for(int i=0;i<CODES.length;i++)if(CODES[i].equals(value))return true;
        return false;
    }

    /** One interpolated label: "胶卷已更新 · 共 {} 张". */
    private static final class Rule {
        final String source;
        final String[] values;
        final String[] parts;
        Pattern pattern;

        Rule(String source,String[] values){
            this.source=source;this.values=values;
            this.parts=source.split("\\{\\}",-1);
        }

        String apply(String text,String target){
            return apply(text,target,4);
        }

        /** Captured parts are often labels themselves: "胶片切换失败 · {}"
         * receives "曝光模式切换失败：不支持", and the shutter hint receives a
         * film name. Translate a captured part when it still contains Han
         * characters; numbers, paths and Latin names pass through unchanged.
         * The depth bound stops a malformed table from recursing forever. */
        String apply(String text,String target,int depth){
            String value=pick(values,target);
            if(value==null&&(JA.equals(target)||KO.equals(target)))value=pick(values,EN);
            if(value==null)return null;
            if(pattern==null){
                StringBuilder regex=new StringBuilder("^");
                for(int i=0;i<parts.length;i++){
                    if(i>0)regex.append("(.*?)");
                    regex.append(Pattern.quote(parts[i]));
                }
                regex.append('$');
                pattern=Pattern.compile(regex.toString(),Pattern.DOTALL);
            }
            Matcher matcher=pattern.matcher(text);
            if(!matcher.matches())return null;
            StringBuilder out=new StringBuilder();
            String[] outParts=value.split("\\{\\}",-1);
            int groups=matcher.groupCount();
            for(int i=0;i<outParts.length;i++){
                out.append(outParts[i]);
                if(i<groups){
                    String group=matcher.group(i+1);
                    if(group!=null&&depth>0&&hasHan(group))
                        group=dynamic(group,target,depth-1);
                    out.append(group);
                }
            }
            return out.toString();
        }
    }

    /** Translate one value captured by a Rule, without touching the cache. */
    private static String dynamic(String text,String target,int depth){
        if(text==null||text.length()==0)return text;
        String[] exact;
        synchronized(Lang.class){exact=EXACT.get(text);}
        String value=exact==null?null:pick(exact,target);
        if(value==null){
            List<Rule> rules;
            synchronized(Lang.class){rules=RULES;}
            for(int i=0;i<rules.size();i++){
                value=rules.get(i).apply(text,target,depth);
                if(value!=null)break;
            }
        }
        if(value==null)value=compound(text,target);
        if(value==null&&(JA.equals(target)||KO.equals(target))&&exact!=null)
            value=pick(exact,EN);
        return value==null?text:value;
    }

    private static String unescape(String value){
        if(value.indexOf('\\')<0)return value;
        StringBuilder out=new StringBuilder(value.length());
        for(int i=0;i<value.length();i++){
            char ch=value.charAt(i);
            if(ch=='\\'&&i+1<value.length()){
                char next=value.charAt(++i);
                if(next=='n')out.append('\n');
                else if(next=='t')out.append('\t');
                else if(next=='r')out.append('\r');
                else out.append(next);
            }else out.append(ch);
        }
        return out.toString();
    }
}
