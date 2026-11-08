package org.hongen.sonylutprobe;

/** Offline recipe table imported from Recipe Lab v1.11.1 (GPL-3.0).
 * Based on Recipe Lab by André Domingues.
 * Source: https://github.com/voxivoid/recipe-lab
 * These are camera-native approximations: capture writes them into Sony
 * Creative Style / WB / DRO / Picture Effect controls, so no LUT is run. */
final class RecipeData {
    private RecipeData(){}
    static final class Group { final String name,zh,image; Group(String name,String zh,String image){this.name=name;this.zh=zh;this.image=image;} }
    static final class Recipe {
        final int group,style,sat,con,sharp,wbMode,kelvin,ab,gm,pe,ev,dro,sub;
        final String name,zh;
        Recipe(int group,String name,String zh,int style,int sat,int con,int sharp,int wbMode,int kelvin,int ab,int gm,int pe,int ev,int dro,int sub){
            this.group=group;this.name=name;this.zh=zh;this.style=style;this.sat=sat;this.con=con;this.sharp=sharp;
            this.wbMode=wbMode;this.kelvin=kelvin;this.ab=ab;this.gm=gm;this.pe=pe;this.ev=ev;this.dro=dro;this.sub=sub;
        }
        boolean isEffect(){return pe!=0;}
    }
    static final Group[] GROUPS={
        new Group("Sony","索尼","sony"),
        new Group("Fuji Sim","富士模拟","fuji-sim"),
        new Group("Fuji Film","富士胶片","fuji-film"),
        new Group("Kodak","柯达","kodak"),
        new Group("Cine","电影","cine"),
        new Group("Ricoh GR","理光 GR","ricoh-gr"),
        new Group("Leica","徕卡","leica"),
        new Group("Hasselblad","哈苏","hasselblad"),
        new Group("Canon / Nikon","佳能 / 尼康","canon-nikon"),
        new Group("Pana / Olympus","松下 / 奥林巴斯","pana-olympus"),
        new Group("Other Stocks","其他胶片","other-stocks"),
        new Group("Ilford","伊尔福","ilford"),
    };
    static final int[] GROUP_START=new int[GROUPS.length],GROUP_COUNT=new int[GROUPS.length];
    static final Recipe[] ALL={
        new Recipe(0,"FACTORY (ST)","出厂标准",1,0,0,0,1,0,0,0,0,0,6,0),
        new Recipe(0,"Sony PT (portrait)","索尼 PT（人像）",4,0,0,0,1,0,0,0,0,0,6,0),
        new Recipe(0,"Sony NT (neutral)","索尼 NT（中性）",3,0,0,0,1,0,0,0,0,0,6,0),
        new Recipe(0,"Sony VV (vivid)","索尼 VV（鲜艳）",2,0,0,0,1,0,0,0,0,0,6,0),
        new Recipe(0,"Sony VV2","索尼 VV2",2,2,1,0,1,0,0,0,0,0,6,0),
        new Recipe(0,"Sony FL (film-like)","索尼 FL（胶片风格）",3,-3,-1,0,1,0,1,0,0,0,6,0),
        new Recipe(0,"Sony IN (instant)","索尼 IN（即时）",3,-3,-3,0,1,0,0,-1,0,0,6,0),
        new Recipe(0,"Sony SH (soft high-key)","索尼 SH（柔和高调）",9,-2,-2,0,1,0,1,0,5,3,6,0),
        new Recipe(1,"Provia","PROVIA 标准",1,1,0,0,1,0,0,0,0,0,6,0),
        new Recipe(1,"Velvia","Velvia 鲜艳",2,3,2,0,1,0,0,0,0,0,6,0),
        new Recipe(1,"Astia","ASTIA 柔和",4,0,-1,0,1,0,1,0,0,1,6,0),
        new Recipe(1,"Classic Chrome","经典正片",1,-1,2,0,1,0,3,1,0,-1,6,0),
        new Recipe(1,"Classic Negative","经典负片",1,-3,3,1,1,0,-1,1,0,0,6,0),
        new Recipe(1,"Nostalgic Neg","怀旧负片",1,1,0,0,1,0,3,0,0,0,6,0),
        new Recipe(1,"Reala Ace","REALA ACE",1,0,1,0,1,0,0,0,0,0,6,0),
        new Recipe(1,"Pro Neg Std","PRO Neg. 标准",4,-2,-1,0,1,0,0,0,0,0,6,0),
        new Recipe(1,"Pro Neg Hi","PRO Neg. 高",4,-2,1,0,1,0,0,0,0,0,6,0),
        new Recipe(1,"Eterna","ETERNA 影院",3,-3,-2,-1,1,0,0,0,0,-1,3,0),
        new Recipe(1,"Eterna Bleach Bypass","ETERNA 漂白效果",3,-3,3,0,1,0,0,0,0,-1,0,0),
        new Recipe(1,"Acros","ACROS",6,0,1,1,1,0,0,0,0,0,6,0),
        new Recipe(1,"Acros +Ye (yellow filter)","ACROS +Ye（黄色滤镜）",6,0,1,1,14,4000,0,0,0,0,6,0),
        new Recipe(1,"Acros +R (red filter)","ACROS +R（红色滤镜）",6,0,0,0,14,2500,0,0,7,0,6,0),
        new Recipe(1,"Acros +G (green filter)","ACROS +G（绿色滤镜）",6,0,1,1,14,5600,0,4,0,0,6,0),
        new Recipe(1,"Sepia","棕褐色",14,0,0,0,1,0,0,0,0,0,6,0),
        new Recipe(2,"Fuji Pro 400H","富士 Pro 400H",9,-2,-2,0,1,0,-1,1,0,2,6,0),
        new Recipe(2,"Fuji Fortia 50","富士 Fortia 50",2,3,2,0,1,0,-1,0,0,-1,6,0),
        new Recipe(2,"Fuji Superia 400","富士 Superia 400",1,1,1,0,1,0,1,1,0,1,6,0),
        new Recipe(2,"Fuji C200","富士 C200",1,0,0,0,1,0,-1,1,0,0,6,0),
        new Recipe(2,"Fuji Natura 1600","富士 Natura 1600",4,-2,-2,0,1,0,1,0,0,1,6,0),
        new Recipe(3,"Kodak Portra 160","柯达 Portra 160",4,-3,-2,0,1,0,1,-1,0,2,6,0),
        new Recipe(3,"Kodak Portra 400","柯达 Portra 400",4,-1,-1,0,1,0,3,1,0,2,6,0),
        new Recipe(3,"Kodak Portra 800","柯达 Portra 800",4,-2,-2,0,1,0,1,1,0,1,6,0),
        new Recipe(3,"Kodak Gold 200","柯达 Gold 200",1,2,1,0,1,0,3,1,0,1,6,0),
        new Recipe(3,"Kodak Ultra Max 400","柯达 Ultra Max 400",1,0,0,0,1,0,0,1,0,1,6,0),
        new Recipe(3,"Kodak Color Plus 200","柯达 Color Plus 200",1,1,1,0,1,0,2,1,0,1,6,0),
        new Recipe(3,"Kodak Ektar 100","柯达 Ektar 100",1,3,1,1,1,0,0,0,0,-1,6,0),
        new Recipe(3,"Kodak Ektachrome E100","柯达 Ektachrome E100",7,3,1,0,1,0,-1,0,0,-1,6,0),
        new Recipe(3,"Kodachrome 64","Kodachrome 64",8,3,2,1,1,0,-1,0,0,-1,6,0),
        new Recipe(3,"Kodak Vision3 500T (daylight)","柯达 Vision3 500T（日光）",3,-1,0,0,14,3200,0,0,0,1,3,0),
        new Recipe(3,"Kodak Vision 200T (Asteroid City)","柯达 Vision 200T（小行星城）",3,-2,-3,0,14,5000,2,3,0,1,3,0),
        new Recipe(3,"Kodak Tri-X 400","柯达 Tri-X 400",6,0,2,2,1,0,0,0,0,1,6,0),
        new Recipe(3,"Kodak T-Max","柯达 T-Max",6,0,2,3,1,0,0,0,0,0,6,0),
        new Recipe(3,"Kodak Tri-X 1600 (pushed)","柯达 Tri-X 1600（增感）",6,0,0,0,1,0,0,0,7,1,6,0),
        new Recipe(4,"Cinestill 50D (Blue Velvet)","CineStill 50D（蓝丝绒）",1,-2,-1,0,14,5500,-1,-1,0,0,6,0),
        new Recipe(4,"Cinestill 800T","CineStill 800T",3,0,0,0,14,3200,0,-1,0,1,6,0),
        new Recipe(4,"Classic Cinema","经典电影",1,0,-1,0,14,6000,2,0,0,0,3,0),
        new Recipe(4,"Rec709 Video (flat-ish)","Rec.709 视频（低对比）",3,-2,-2,0,1,0,0,0,0,0,5,0),
        new Recipe(5,"GR Positive Film","GR 正片",1,3,2,0,1,0,2,0,0,-1,6,0),
        new Recipe(5,"GR Negative Film","GR 负片",3,-2,1,0,1,0,-1,1,0,1,6,0),
        new Recipe(5,"GR Bleach Bypass","GR 漂白效果",3,-3,3,1,1,0,0,0,0,0,0,0),
        new Recipe(5,"GR Retro","GR 复古",1,-3,-1,0,1,0,3,-1,4,0,6,0),
        new Recipe(5,"GR Cross Process","GR 正负逆冲",2,2,2,0,1,0,-2,4,0,0,6,0),
        new Recipe(5,"GR Hi-Contrast B&W","GR 高对比度黑白",6,0,3,1,1,0,0,0,7,0,6,0),
        new Recipe(5,"GR Hard Monotone","GR 硬调黑白",6,0,2,2,1,0,0,0,0,0,6,0),
        new Recipe(5,"GR Soft Monotone","GR 柔调黑白",6,0,-2,-1,1,0,0,0,0,0,6,0),
        new Recipe(6,"Leica Contemporary","徕卡 当代",1,1,1,0,1,0,0,0,0,0,6,0),
        new Recipe(6,"Leica Classic","徕卡 经典",1,-1,2,0,1,0,1,0,0,0,6,0),
        new Recipe(6,"Leica Eternal","徕卡 永恒",3,-3,-1,0,1,0,1,0,0,0,3,0),
        new Recipe(6,"Leica Monochrom","徕卡 黑白",6,0,2,1,1,0,0,0,0,0,6,0),
        new Recipe(7,"Hasselblad HNCS Natural","哈苏 HNCS 自然",3,-1,-1,0,1,0,0,0,0,0,6,0),
        new Recipe(8,"Canon Standard","佳能 标准",1,1,1,0,1,0,1,-1,0,0,6,0),
        new Recipe(8,"Canon Portrait","佳能 人像",4,0,0,-1,1,0,1,-1,0,0,6,0),
        new Recipe(8,"Canon Faithful","佳能 可靠设置",3,0,0,0,1,0,0,0,0,0,6,0),
        new Recipe(8,"Nikon Flat","尼康 平面",3,-3,-3,-1,1,0,0,0,0,0,5,0),
        new Recipe(8,"Nikon Vivid","尼康 鲜艳",2,1,1,1,1,0,0,0,0,0,6,0),
        new Recipe(9,"Pana L.Monochrome D","松下 L.黑白 D",6,0,3,1,1,0,0,0,0,0,6,0),
        new Recipe(9,"Pana L.ClassicNeo","松下 L.经典 Neo",3,-3,-1,0,1,0,2,0,0,1,6,0),
        new Recipe(9,"Olympus Pop Art","奥林巴斯 浓郁色调",2,3,3,0,1,0,0,0,0,0,6,0),
        new Recipe(9,"Olympus Pale & Light","奥林巴斯 淡化及增亮",9,-3,-2,0,1,0,0,0,0,2,6,0),
        new Recipe(10,"Agfa Vista 200","爱克发 Vista 200",1,2,1,0,1,0,2,-1,0,1,6,0),
        new Recipe(10,"Agfa Ultra 100","爱克发 Ultra 100",2,3,1,0,1,0,0,0,0,0,6,0),
        new Recipe(10,"Polaroid / Instax","宝丽来 / instax",1,-3,-2,0,1,0,1,-2,4,1,6,0),
        new Recipe(11,"Ilford HP5","伊尔福 HP5",6,0,1,0,1,0,0,0,0,1,6,0),
        new Recipe(11,"Ilford FP4","伊尔福 FP4",6,0,1,1,1,0,0,0,0,0,6,0),
        new Recipe(11,"Ilford Delta 100","伊尔福 Delta 100",6,0,1,1,1,0,0,0,0,0,6,0),
        new Recipe(11,"Ilford Delta 3200","伊尔福 Delta 3200",6,0,3,-2,1,0,0,0,0,2,6,0),
        new Recipe(11,"Ilford Pan F 50","伊尔福 Pan F 50",6,0,2,2,1,0,0,0,0,0,6,0),
    };
    static {
        for(int g=0;g<GROUPS.length;g++)GROUP_START[g]=-1;
        for(int i=0;i<ALL.length;i++){int g=ALL[i].group;if(GROUP_START[g]<0)GROUP_START[g]=i;GROUP_COUNT[g]++;}
    }
    static int groupOf(int recipe){return recipe>=0&&recipe<ALL.length?ALL[recipe].group:0;}
    static int firstInGroup(int group){return group>=0&&group<GROUPS.length?GROUP_START[group]:0;}
    static int countInGroup(int group){return group>=0&&group<GROUPS.length?GROUP_COUNT[group]:0;}
    static int localIndex(int recipe){int g=groupOf(recipe);return recipe-firstInGroup(g);}
    static int recipeAt(int group,int local){int p=firstInGroup(group)+local;return p>=0&&p<ALL.length&&groupOf(p)==group?p:-1;}
    static final String[] STYLE_NAMES={"standard","vivid","neutral","portrait","landscape","mono","clear","deep","light","sunset","night","red-leaves","sepia"};
    static final String[] STYLE_ZH={"标准","鲜艳","中性","人像","风景","黑白","清澈","浓郁","淡雅","黄昏","夜景","秋叶","棕褐色"};
    static final String[] PE_KEYS={"off","toy-camera","pop-color","posterization","retro-photo","soft-high-key","part-color","rough-mono","soft-focus","hdr-art","richtone-mono","miniature","illust","watercolor"};
    static final String[] PE_ZH={"关闭","玩具相机","流行色彩","色调分离","复古照片","柔和高调","局部彩色","高对比度黑白","柔焦","HDR 绘画","丰富色调黑白","微缩景观","插图","水彩画"};
    static String styleName(int style){switch(style){case 1:return "standard";case 2:return "vivid";case 3:return "neutral";case 4:return "portrait";case 5:return "landscape";case 6:return "mono";case 7:return "clear";case 8:return "deep";case 9:return "light";case 10:return "sunset";case 11:return "night";case 12:return "red-leaves";case 14:return "sepia";default:return "standard";}}
    static String styleZh(int style){int i=0;String n=styleName(style);for(String s:STYLE_NAMES)if(s.equals(n))return STYLE_ZH[i];else i++;return "标准";}
    static String peName(int pe){return pe>=0&&pe<PE_KEYS.length?PE_KEYS[pe]:"off";}
    static String peZh(int pe){return pe>=0&&pe<PE_ZH.length?PE_ZH[pe]:"关闭";}
    static String subKey(int pe){switch(pe){case 5:return "pe-soft-high-key-effect";case 1:return "pe-toy-camera-effect";case 6:return "pe-part-color-effect";case 3:return "pe-posterization-effect";default:return null;}}
    static String[] subValues(int pe){switch(pe){case 5:return new String[]{"blue","pink","green"};case 1:return new String[]{"normal","cool","warm","green","magenta"};case 6:return new String[]{"red","green","blue","yellow"};case 3:return new String[]{"posterization-color","posterization-bw"};default:return null;}}
    static String subRuntime(int pe,int sub){String[] v=subValues(pe);if(v==null||sub<0||sub>=v.length)return null;return v[sub].equals("posterization-bw")?"posterization-mono":v[sub];}
    static String subZh(int pe,int sub){String[] v=subValues(pe);if(v==null||sub<0||sub>=v.length)return "";String s=v[sub];if(s.equals("blue"))return "蓝色";if(s.equals("pink"))return "粉色";if(s.equals("green"))return "绿色";if(s.equals("normal"))return "标准";if(s.equals("cool"))return "冷色";if(s.equals("warm"))return "暖色";if(s.equals("magenta"))return "洋红";if(s.equals("red"))return "红色";if(s.equals("yellow"))return "黄色";if(s.equals("posterization-color"))return "彩色";if(s.equals("posterization-bw"))return "黑白";return s;}
    static String evLabel(int ev){if(ev==0)return "0";int a=Math.abs(ev);String f=a%3==0?".0":a%3==1?".3":".7";return (ev>0?"+":"-")+(a/3)+f;}
    static String summary(Recipe r){
        StringBuilder s=new StringBuilder();
        if(r.isEffect()){s.append(peZh(r.pe));String sub=subZh(r.pe,r.sub);if(sub.length()>0)s.append(" ").append(sub);}
        else{s.append(styleZh(r.style)).append("  ").append(r.sat>0?"+":"").append(r.sat).append("/").append(r.con>0?"+":"").append(r.con);}
        if(r.ev!=0)s.append("  ").append(evLabel(r.ev));
        if(r.dro!=6)s.append(r.dro==0?"  DRO 关":"  DRO Lv"+r.dro);
        if(r.wbMode==14)s.append("  ").append(r.kelvin).append("K");
        if(r.ab!=0)s.append("  ").append(r.ab>0?"A"+r.ab:"B"+(-r.ab));
        if(r.gm!=0)s.append("  ").append(r.gm>0?"G"+r.gm:"M"+(-r.gm));
        return s.toString();
    }
}
