package org.hongen.sonylutprobe;

import java.util.ArrayList;
import java.util.List;

/** One pose card from the bundled ShotDirector content pack. Plain data, API 10 safe. */
final class PoseCard {
    final String id;
    final String[] cat;
    final String scene;
    final String[] pose;
    final String cam;
    final String light;
    final String[] kw;

    PoseCard(String id, String[] cat, String scene, String[] pose, String cam, String light, String[] kw) {
        this.id = id == null ? "" : id;
        this.cat = cat == null ? new String[0] : cat;
        this.scene = scene == null ? "" : scene;
        this.pose = pose == null ? new String[0] : pose;
        this.cam = cam == null ? "" : cam;
        this.light = light == null ? "" : light;
        this.kw = kw == null ? new String[0] : kw;
    }

    /** ShotDirector filters a card by any of its categories, not just the primary one. */
    boolean inCategory(String category) {
        for (int i = 0; i < cat.length; i++) {
            if (category.equals(cat[i])) return true;
        }
        return false;
    }

    String poseText() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < pose.length; i++) {
            if (b.length() > 0) b.append('\n');
            b.append(pose[i]);
        }
        return b.toString();
    }

    static List<PoseCard> filter(List<PoseCard> all, String category) {
        if ("全部".equals(category)) return new ArrayList<PoseCard>(all);
        List<PoseCard> out = new ArrayList<PoseCard>();
        for (int i = 0; i < all.size(); i++) {
            PoseCard c = all.get(i);
            if (c.inCategory(category)) out.add(c);
        }
        return out;
    }
}
