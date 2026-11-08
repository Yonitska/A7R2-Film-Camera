package org.hongen.sonylutprobe;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * The bundled pose content pack (assets/pose/pose_pack.json, from ShotDirector's
 * factory pack). Loaded once; categories come from the cards and "全部" is first.
 * A broken pack becomes an error message rather than a crash.
 */
final class PosePack {
    final List<PoseCard> cards = new ArrayList<PoseCard>();
    final List<String> categories = new ArrayList<String>();
    String error;

    static PosePack load(Context context) {
        PosePack p = new PosePack();
        try {
            InputStream in = context.getAssets().open("POSE.JSN");
            BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            StringBuilder text = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) text.append(line);
            r.close();

            JSONObject root = new JSONObject(text.toString());
            JSONArray arr = root.getJSONArray("cards");
            LinkedHashSet<String> cats = new LinkedHashSet<String>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String[] cat = strings(o.opt("cat"));
                PoseCard card = new PoseCard(
                        str(o, "id"), cat, str(o, "scene"), strings(o.opt("pose")),
                        str(o, "cam"), str(o, "light"), strings(o.opt("kw")));
                p.cards.add(card);
                for (int k = 0; k < cat.length; k++) {
                    if (cat[k].length() > 0) cats.add(cat[k]);
                }
            }
            p.categories.add("全部");
            p.categories.addAll(cats);
            if (p.cards.size() == 0) p.error = "姿势包是空的";
        } catch (Throwable t) {
            p.error = String.valueOf(t.getMessage());
        }
        return p;
    }

    List<PoseCard> inCategory(String category) {
        return PoseCard.filter(cards, category);
    }

    private static String str(JSONObject o, String key) {
        String s = o.optString(key);
        return s == null ? "" : s;
    }

    /** The schema accepts a list or a bare string in every list field. */
    private static String[] strings(Object value) {
        if (value instanceof JSONArray) {
            JSONArray a = (JSONArray) value;
            String[] out = new String[a.length()];
            for (int i = 0; i < a.length(); i++) {
                String s = a.optString(i);
                out[i] = s == null ? "" : s;
            }
            return out;
        }
        if (value instanceof String) {
            String s = (String) value;
            return s.length() == 0 ? new String[0] : new String[] { s };
        }
        return new String[0];
    }
}
