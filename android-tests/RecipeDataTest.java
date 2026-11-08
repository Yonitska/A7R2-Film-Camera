package org.hongen.sonylutprobe;

import java.util.Map;

/** Offline contract for the imported Recipe Lab table and its camera mapping. */
public class RecipeDataTest {
    public static void main(String[] args) {
        require(RecipeData.ALL.length==77,"77 imported recipes");
        require(RecipeData.GROUPS.length==12,"12 camera groups");
        int total=0;
        for(int group=0;group<RecipeData.GROUPS.length;group++){
            int count=RecipeData.countInGroup(group);
            require(count>0,"group "+group+" has recipes");
            total+=count;
            int start=RecipeData.firstInGroup(group);
            for(int local=0;local<count;local++){
                int id=RecipeData.recipeAt(group,local);
                require(id==start+local,"recipeAt is contiguous");
                require(RecipeData.groupOf(id)==group,"recipeAt group");
                require(RecipeData.localIndex(id)==local,"localIndex inverse");
                require(RecipeData.ALL[id].group==group,"table group");
            }
        }
        require(total==RecipeData.ALL.length,"group counts cover the table");

        for(RecipeData.Recipe recipe:RecipeData.ALL){
            require(recipe.name!=null&&recipe.name.length()>0,"recipe name");
            require(recipe.zh!=null&&recipe.zh.length()>0,"recipe Chinese name");
            require(RecipeData.summary(recipe).length()>0,"recipe summary");
            require(recipe.wbMode==0||recipe.wbMode==1||recipe.wbMode==14,"white balance mode");
            if(recipe.wbMode==14)
                require(recipe.kelvin>=2500&&recipe.kelvin<=9900&&recipe.kelvin%100==0,"kelvin "+recipe.name);
            require(recipe.sat>=-3&&recipe.sat<=3,"saturation "+recipe.name);
            require(recipe.con>=-3&&recipe.con<=3,"contrast "+recipe.name);
            require(recipe.sharp>=-3&&recipe.sharp<=3,"sharpness "+recipe.name);
            require(recipe.ev>=-15&&recipe.ev<=15,"exposure "+recipe.name);
            require(recipe.dro==0||recipe.dro==6||(recipe.dro>=1&&recipe.dro<=5),"DRO "+recipe.name);
            Map<String,String> parameters=RecipeCameraLook.parameters(recipe);
            require(RecipeData.styleName(recipe.style).equals(parameters.get("color-mode")),"style mapping");
            require(RecipeData.peName(recipe.pe).equals(parameters.get("picture-effect")),"effect mapping");
            if(recipe.isEffect()&&RecipeData.subKey(recipe.pe)!=null)
                require(RecipeData.subRuntime(recipe.pe,recipe.sub)!=null,"effect sub-option "+recipe.name);
            require(Integer.toString(recipe.ev).equals(parameters.get("exposure-compensation")),"EV mapping");
            if(recipe.wbMode==14){
                require("color-temp".equals(parameters.get("whitebalance")),"kelvin WB mode");
                require(Integer.toString(recipe.kelvin).equals(parameters.get("color-temperture-white-balance")),
                        "kelvin unit is the raw camera parameter ("+recipe.name+"="+recipe.kelvin+")");
            }else if(recipe.wbMode==1){
                require("auto".equals(parameters.get("whitebalance")),"auto WB mode");
                require(!parameters.containsKey("color-temperture-white-balance"),"no kelvin in auto WB");
            }else{
                require(!parameters.containsKey("whitebalance"),"keep WB leaves whitebalance untouched");
            }
            require(Integer.toString(recipe.ab).equals(parameters.get("light-balance-for-white-balance")),"A-B mapping");
            require(Integer.toString(-recipe.gm).equals(parameters.get("color-compensation-for-white-balance")),
                    "G-M sign is inverted for the camera");
        }

        // Explicit guards for the two mappings that are easy to invert.
        RecipeData.Recipe guard=new RecipeData.Recipe(0,"guard","守卫",2,5,-5,5,14,5600,3,-2,5,4,3,2);
        Map<String,String> parameters=RecipeCameraLook.parameters(guard);
        require("vivid".equals(parameters.get("color-mode")),"style guard");
        require("3".equals(parameters.get("saturation")),"saturation clamp");
        require("-3".equals(parameters.get("contrast")),"contrast clamp");
        require("3".equals(parameters.get("sharpness")),"sharpness clamp");
        require("5600".equals(parameters.get("color-temperture-white-balance")),"kelvin guard");
        require("3".equals(parameters.get("light-balance-for-white-balance")),"A-B guard");
        require("2".equals(parameters.get("color-compensation-for-white-balance")),"G-M guard");
        require("soft-high-key".equals(parameters.get("picture-effect")),"effect guard");
        require("green".equals(parameters.get("pe-soft-high-key-effect")),"effect sub-option guard");
        require("4".equals(parameters.get("exposure-compensation")),"EV guard");
        require("on".equals(parameters.get("dro-mode"))&&"3".equals(parameters.get("dro-level")),"DRO guard");
        System.out.println("PASS: 77 Recipe Lab recipes, 12 groups, Kelvin units, WB keep/auto, A-B/G-M and effect mapping");
    }
    private static void require(boolean value,String label){if(!value)throw new AssertionError(label);}
}
