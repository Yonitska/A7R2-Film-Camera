package org.hongen.sonylutprobe;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * The in-camera pose director, as a translucent view over the live preview.
 *
 * C2 toggles it. A permanent category strip names every category; the four-way
 * pad and dials walk cards, left/right change category, MENU/centre/TRASH close.
 * The shutter is deliberately left alone, so the photographer can frame with
 * the card visible and still shoot.
 *
 * A card photo is read from POSE/ on the card (<card id>.jpg/png or
 * <1-based pack index>.jpg/png). The personal build also bundles the same
 * c001…c176 JPEGs in assets/POSE, used only when the card lookup fails, so a
 * camera whose FUSE mount refuses to list POSE/ still shows the photos.
 * There is deliberately no drawn silhouette: when a photo cannot be found the
 * box says so in text instead of showing a stick figure.
 */
final class PoseDirectorOverlay {
    private PoseDirectorOverlay() {}

    private static final int KEY_UP = 103, KEY_DOWN = 108, KEY_LEFT = 105, KEY_RIGHT = 106,
            KEY_ENTER = 232, KEY_MENU = 514, KEY_DELETE = 595, KEY_SK2 = 513,
            KEY_C2 = 623, KEY_DIAL_CW = 525, KEY_DIAL_CCW = 526;

    private static final int BG = 0xB8101014;
    private static final int TITLE = 0xFFF2B85C;
    private static final int BODY = 0xFFF2F2F2;
    private static final int META = 0xFFA9B4C4;
    private static final int FOOT = 0xFF7E8898;

    /** The list must stay fluid while a key repeats, but the photo has to
     * appear as soon as the user stops on a card. Decoding itself runs on the
     * loader thread, so a short debounce is enough. The old 1.7 s wait made
     * every card look broken when the user moved on before it elapsed. */
    private static final long IMAGE_DELAY_MS = 180;
    private static final int IMAGE_CACHE = 24;
    private static final String[] IMAGE_EXTENSIONS = {
            ".jpg", ".jpeg", ".png", ".bmp", ".webp"
    };

    private static WeakReference<Activity> sActivity = new WeakReference<Activity>(null);
    private static WeakReference<View> sView = new WeakReference<View>(null);
    private static Typeface sTypeface;
    private static PosePack sPack;
    private static TextView sTitle, sBody, sMeta, sFooter;
    private static LinearLayout sCategoryBar;
    private static FrameLayout sImageBox;
    private static ImageView sPhoto;
    private static TextView sPlaceholder;
    private static volatile Bitmap sBitmap;
    private static String sBitmapPath = "";
    /** Decoded cards, so flipping back and forth never decodes twice. The
     * placeholder and the photo are the only things drawn: this overlay has
     * no stick figure any more, by request. */
    private static final Map<String, Bitmap> sImageCache =
            new LinkedHashMap<String, Bitmap>(IMAGE_CACHE, 0.75f, true) {
                protected boolean removeEldestEntry(Map.Entry<String, Bitmap> eldest) {
                    // Drop the reference only. Recycling here raced the UI
                    // thread, which may have just taken this bitmap out of
                    // the map and be about to display it.
                    return size() > IMAGE_CACHE;
                }
            };
    private static int sCategory, sCard;
    private static float sDensity = 1f;
    private static final boolean[] sConsumed = new boolean[700];

    /** The photo is decoded only after the highlight settles. The generation
     * token makes a late decode a no-op; no UI or Sony call ever waits for the
     * SD card. */
    private static final Handler sUi = new Handler(Looper.getMainLooper());
    private static final ExecutorService sLoader =
            Executors.newSingleThreadExecutor(new ThreadFactory() {
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "pose-images");
                    t.setDaemon(true);
                    return t;
                }
            });
    private static volatile int sImageGeneration;
    private static PoseCard sPendingCard;
    private static String sFooterBase = "";
    private static String sImageNote = "";
    private static File sPoseDir;
    private static boolean sPoseDirScanned;
    private static long sPoseDirScannedAt;
    private static String sScanReport = "";
    /** Last decode failure detail, written to SONYLUT/POSELOAD.TXT. */
    private static volatile String sLastDecodeError = "";
    private static final Map<String, File> sImageIndex = new HashMap<String, File>();
    /** Bundle fallback: SD/POSE still wins, but the camera can still show the
     * photo when its FUSE mount refuses to list the folder. The packaged set
     * is generated from POSE/c001…c176 at build time. */
    private static final Map<String, String> sAssetIndex = new HashMap<String, String>();
    private static boolean sAssetIndexLoaded;
    private static final Runnable sLoadDelayed = new Runnable() {
        public void run() { startPendingLoad(); }
    };

    public static boolean handleKey(Activity activity, KeyEvent event) {
        if (event == null || activity == null) return false;
        int scan = event.getScanCode();
        int action = event.getAction();

        if (scan == KEY_C2) {
            if (action == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                if (isVisible()) hide();
                else show(activity);
            }
            return true;
        }

        if (action == KeyEvent.ACTION_UP) {
            if (scan >= 0 && scan < sConsumed.length && sConsumed[scan]) {
                sConsumed[scan] = false;
                return true;
            }
            return false;
        }
        if (action != KeyEvent.ACTION_DOWN) return false;

        if (!isVisible()) {
            if (scan >= 0 && scan < sConsumed.length && sConsumed[scan]) return true;
            return false;
        }

        if (scan == KEY_UP || scan == KEY_DIAL_CCW) { stepCard(-1); consume(scan); return true; }
        if (scan == KEY_DOWN || scan == KEY_DIAL_CW) { stepCard(1); consume(scan); return true; }
        if (scan == KEY_LEFT) { stepCategory(-1); consume(scan); return true; }
        if (scan == KEY_RIGHT) { stepCategory(1); consume(scan); return true; }
        if (scan == KEY_ENTER || scan == KEY_MENU || scan == KEY_DELETE || scan == KEY_SK2) {
            hide();
            consume(scan);
            return true;
        }
        return false;
    }

    private static void consume(int scan) {
        if (scan >= 0 && scan < sConsumed.length) sConsumed[scan] = true;
    }

    private static boolean isVisible() {
        View v = sView.get();
        return v != null && v.getParent() != null;
    }

    private static void show(Activity activity) {
        if (isVisible()) return;
        Activity previous = sActivity.get();
        if (previous != activity) {
            sView = new WeakReference<View>(null);
            sTitle = sBody = sMeta = sFooter = null;
            sCategoryBar = null;
            sImageBox = null;
            sPhoto = null;
            sPlaceholder = null;
            releaseBitmap();
            clearCache();
        }
        sActivity = new WeakReference<Activity>(activity);
        sDensity = activity.getResources().getDisplayMetrics().density;
        if (sTypeface == null) {
            try {
                // The UI font now carries Traditional Chinese, kana and
                // Hangul; the old POSE subset did not. Keep POSE.TTF only as
                // a last-resort fallback if the packaged font is damaged.
                sTypeface = UiFont.get(activity);
            } catch (Throwable t) {
                try {
                    sTypeface = Typeface.createFromAsset(activity.getAssets(), "POSE.TTF");
                } catch (Throwable ignored) {
                    sTypeface = Typeface.DEFAULT;
                }
            }
        }
        if (sPack == null) sPack = PosePack.load(activity);

        View view = sView.get();
        if (view == null) {
            view = build(activity);
            sView = new WeakReference<View>(view);
        }
        // Reopening the overlay is also the cheap way to pick up files copied
        // to the card while the camera was running.
        sPoseDirScanned = false;
        sPoseDirScannedAt = 0;
        sImageIndex.clear();
        sScanReport = "";
        ViewGroup parent = (ViewGroup) view.getParent();
        if (parent != null) parent.removeView(view);
        activity.addContentView(view, new FrameLayout.LayoutParams(-1, -1));
        render();
    }

    static void closeIfVisible() { hide(); }

    static boolean isShowing() { return isVisible(); }

    private static void hide() {
        cancelPendingImage();
        View view = sView.get();
        if (view == null) return;
        ViewGroup parent = (ViewGroup) view.getParent();
        if (parent != null) parent.removeView(view);
        releaseBitmap();
        sImageNote = "";
    }

    private static View build(Activity activity) {
        FrameLayout root = new FrameLayout(activity);
        root.setBackgroundColor(BG);
        int padH = dp(14), padV = dp(8);
        root.setPadding(padH, padV, padH, dp(6));

        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        root.addView(box);

        sTitle = text(activity, 14, TITLE, true);
        sCategoryBar = new LinearLayout(activity);
        sCategoryBar.setOrientation(LinearLayout.HORIZONTAL);
        sCategoryBar.setGravity(Gravity.LEFT);

        sBody = text(activity, 22, BODY, false);
        sBody.setLineSpacing(dp(3), 1f);
        sMeta = text(activity, 12, META, false);
        sFooter = text(activity, 10, FOOT, false);

        sImageBox = new FrameLayout(activity);
        sImageBox.setBackgroundColor(0x22000000);
        sPhoto = new ImageView(activity);
        sPhoto.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        sPhoto.setVisibility(View.GONE);
        sPlaceholder = text(activity, 12, META, false);
        sPlaceholder.setGravity(Gravity.CENTER);
        setText(sPlaceholder, "正在载入…");
        sImageBox.addView(sPhoto, new FrameLayout.LayoutParams(-1, -1));
        sImageBox.addView(sPlaceholder, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams imageLp = new LinearLayout.LayoutParams(dp(190), -1);
        imageLp.rightMargin = dp(12);
        content.addView(sImageBox, imageLp);

        LinearLayout textCol = new LinearLayout(activity);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.addView(sBody, new LinearLayout.LayoutParams(-1, 0, 1f));
        textCol.addView(sMeta, new LinearLayout.LayoutParams(-1, -2));
        content.addView(textCol, new LinearLayout.LayoutParams(0, -1, 1f));

        box.addView(sTitle, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(-1, -2);
        barLp.topMargin = dp(2);
        box.addView(sCategoryBar, barLp);
        LinearLayout.LayoutParams contentLp = new LinearLayout.LayoutParams(-1, 0, 1f);
        contentLp.topMargin = dp(4);
        box.addView(content, contentLp);
        LinearLayout.LayoutParams footLp = new LinearLayout.LayoutParams(-1, -2);
        footLp.topMargin = dp(4);
        box.addView(sFooter, footLp);
        return root;
    }

    private static TextView text(Context context, int sp, int color, boolean bold) {
        TextView v = new TextView(context);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        v.setTextColor(color);
        v.setTypeface(sTypeface == null ? Typeface.DEFAULT : sTypeface,
                bold ? Typeface.BOLD : Typeface.NORMAL);
        v.setGravity(Gravity.LEFT);
        return v;
    }

    private static void render() {
        if (sTitle == null || sBody == null) return;
        if (sPack == null) sPack = PosePack.load(sTitle.getContext());
        if (sPack.error != null) {
            setText(sTitle, "摆姿导演台");
            setText(sBody, "姿势库读取失败");
            setText(sMeta, sPack.error);
            setFooter("C2 关闭");
            renderCategories();
            cancelPendingImage();
            showPlaceholder("姿势库读取失败");
            return;
        }
        if (sPack.categories.size() == 0) {
            setText(sTitle, "摆姿导演台");
            setText(sBody, "姿势包是空的");
            setText(sMeta, "");
            setFooter("C2 关闭");
            renderCategories();
            cancelPendingImage();
            showPlaceholder("没有姿势卡");
            return;
        }
        if (sCategory < 0) sCategory = 0;
        if (sCategory >= sPack.categories.size()) sCategory = sPack.categories.size() - 1;
        String category = sPack.categories.get(sCategory);
        renderCategories();
        List<PoseCard> list = sPack.inCategory(category);
        if (list.size() == 0) {
            setText(sTitle, "摆姿导演台");
            setText(sBody, "这个分类还没有姿势卡");
            setText(sMeta, "");
            setFooter("C2 关闭 · 左右 换分类");
            cancelPendingImage();
            showPlaceholder("这个分类还没有姿势卡");
            return;
        }
        if (sCard < 0) sCard = 0;
        if (sCard >= list.size()) sCard = list.size() - 1;
        PoseCard card = list.get(sCard);

        String scene = card.scene.length() == 0 ? "" : "    " + card.scene;
        setText(sTitle, Lang.t("摆姿导演台") + "    " + (sCard + 1) + "/" + list.size() + scene);
        String pose = card.poseText();
        setText(sBody, pose.length() == 0 ? "（这张卡没有姿势要点）" : pose);
        sBody.setTextSize(TypedValue.COMPLEX_UNIT_SP, pose.length() > 40 ? 20 : 22);

        StringBuilder meta = new StringBuilder();
        if (card.cam.length() > 0) meta.append(Lang.t("机位  ")).append(card.cam);
        if (card.light.length() > 0) {
            if (meta.length() > 0) meta.append('\n');
            meta.append(Lang.t("光位  ")).append(card.light);
        }
        if (card.kw.length > 0) {
            if (meta.length() > 0) meta.append('\n');
            meta.append(Lang.t("关键词  "));
            for (int i = 0; i < card.kw.length; i++) {
                if (i > 0) meta.append(" / ");
                meta.append(card.kw[i]);
            }
        }
        setText(sMeta, meta.toString());
        setFooter("C2 关闭 · 上下/拨轮 翻卡 · 左右 换分类 · 快门可拍");
        renderImage(card);
    }

    private static void setFooter(String base) {
        sFooterBase = base == null ? "" : base;
        if (sFooter == null) return;
        setText(sFooter, sImageNote.length() == 0 ? sFooterBase : sFooterBase + " · " + sImageNote);
    }

    private static void setText(TextView view, String value) {
        if (view != null) view.setText(Lang.tr(value == null ? "" : value));
    }

    private static void renderCategories() {
        if (sCategoryBar == null) return;
        Context context = sCategoryBar.getContext();
        sCategoryBar.removeAllViews();
        for (int i = 0; i < sPack.categories.size(); i++) {
            String name = Lang.tr(sPack.categories.get(i));
            TextView t = text(context, 11, i == sCategory ? TITLE : FOOT, i == sCategory);
            t.setText(i == sCategory ? "【" + name + "】" : name);
            t.setPadding(dp(3), 0, dp(3), 0);
            sCategoryBar.addView(t, new LinearLayout.LayoutParams(-2, -2));
        }
    }

    /** Show a already-decoded card immediately; otherwise show a neutral
     * loading state and decode in the background. No stick figure is ever
     * drawn: a missing photo is reported as text, not as a placeholder body. */
    private static void renderImage(final PoseCard card) {
        if (sPhoto == null || sPlaceholder == null) return;
        final int generation = ++sImageGeneration;
        sUi.removeCallbacks(sLoadDelayed);
        sPendingCard = card;
        sImageNote = "";
        setFooter(sFooterBase);
        sPhoto.setImageDrawable(null);
        sPhoto.setVisibility(View.GONE);
        releaseBitmap();
        Bitmap cached = cachedImage(card);
        if (cached != null) {
            applyLoaded(card, cached, "cache", "", generation, true);
            return;
        }
        showPlaceholder("正在载入…");
        sUi.postDelayed(sLoadDelayed, IMAGE_DELAY_MS);
    }

    private static void cancelPendingImage() {
        sImageGeneration++;
        sPendingCard = null;
        sUi.removeCallbacks(sLoadDelayed);
    }

    private static void startPendingLoad() {
        final PoseCard card = sPendingCard;
        if (card == null) return;
        final Context context = sPhoto == null ? null : sPhoto.getContext();
        final int generation = sImageGeneration;
        tracePose("card=" + card.id + " load_start gen=" + generation
                + " visible=" + isVisible());
        sLoader.execute(new Runnable() {
            public void run() {
                sLastDecodeError = "";
                File file = findImage(card);
                Bitmap bitmap = file == null ? null : decode(file, dp(190), dp(320));
                String asset = null;
                String assetError = "";
                if (bitmap == null) {
                    for (String candidate : assetCandidates(context, card)) {
                        Bitmap decoded = decodeAsset(context, candidate, dp(190), dp(320));
                        if (decoded != null) { bitmap = decoded; asset = candidate; break; }
                        assetError = sLastDecodeError;
                    }
                }
                final String note;
                if (bitmap != null) note = "";
                else if (file == null && asset == null) note = missingNote(card);
                else note = "POSE 图片无法解码";
                final Bitmap ready = bitmap;
                final String path = bitmap == null ? ""
                        : file != null && asset == null ? file.getAbsolutePath()
                        : "asset:" + asset;
                if (ready != null) cacheImage(card, ready);
                tracePose("card=" + card.id + " indexed=" + sImageIndex.size()
                        + " assets=" + sAssetIndex.size()
                        + " file=" + (file == null ? "-" : file.getAbsolutePath())
                        + " asset=" + (asset == null ? "-" : asset)
                        + " bitmap=" + sizeOf(bitmap)
                        + " decode_error=" + (sLastDecodeError.length() == 0 ? "-" : sLastDecodeError)
                        + " asset_error=" + (assetError.length() == 0 ? "-" : assetError));
                sUi.post(new Runnable() {
                    public void run() {
                        applyLoaded(card, ready, path, note, generation, false);
                    }
                });
            }
        });
    }

    /** Runs on the UI thread. A late decode is still useful for the cache, so
     * only the screen is skipped when the user has moved on. The old code also
     * required the generation to match; a re-render of the same card (or a
     * decode that finished while the overlay was reopening) was thrown away
     * even though it was exactly the image that should be on screen. */
    private static void applyLoaded(PoseCard card, Bitmap ready, String path,
                                    String note, int generation, boolean fromCache) {
        if (sPhoto == null || sPlaceholder == null) return;
        if (card != sPendingCard || !isVisible()) {
            tracePose("card=" + card.id + " cached=" + (ready != null)
                    + " discarded=stale gen=" + generation + " current=" + sImageGeneration
                    + " pending=" + (sPendingCard == null ? "-" : sPendingCard.id)
                    + " visible=" + isVisible());
            return;
        }
        sImageNote = note;
        setFooter(sFooterBase);
        if (ready == null) {
            sPhoto.setImageDrawable(null);
            sPhoto.setVisibility(View.GONE);
            setText(sPlaceholder, note.length() == 0 ? "无图片" : note);
            sPlaceholder.setVisibility(View.VISIBLE);
            tracePose("card=" + card.id + " ui=failed note=" + note);
            return;
        }
        releaseBitmap();
        sBitmap = ready;
        sBitmapPath = path;
        sPlaceholder.setVisibility(View.GONE);
        sPhoto.setImageBitmap(ready);
        sPhoto.setVisibility(View.VISIBLE);
        tracePose("card=" + card.id + " ui=shown path=" + path + (fromCache ? " cached=1" : ""));
    }

    private static String cacheKey(PoseCard card) {
        String id = card == null ? null : card.id;
        return id == null ? "@" + System.identityHashCode(card) : id;
    }

    private static Bitmap cachedImage(PoseCard card) {
        synchronized (sImageCache) {
            Bitmap bitmap = sImageCache.get(cacheKey(card));
            return bitmap == null || bitmap.isRecycled() ? null : bitmap;
        }
    }

    private static void cacheImage(PoseCard card, Bitmap bitmap) {
        synchronized (sImageCache) {
            sImageCache.put(cacheKey(card), bitmap);
        }
    }

    private static void clearCache() {
        synchronized (sImageCache) {
            for (Bitmap bitmap : sImageCache.values()) {
                if (bitmap != sBitmap) bitmap.recycle();
            }
            sImageCache.clear();
        }
    }

    private static String missingNote(PoseCard card) {
        if (sPoseDir == null) return "SD/POSE 不可读且无内置图 · 见 SONYLUT/POSE.TXT";
        return "未找到 " + card.id + " 图片（SD 与内置）· 见 SONYLUT/POSE.TXT";
    }

    private static void showPlaceholder(String message) {
        if (sPhoto != null) {
            sPhoto.setImageDrawable(null);
            sPhoto.setVisibility(View.GONE);
        }
        releaseBitmap();
        if (sPlaceholder == null) return;
        setText(sPlaceholder, message == null ? "" : message);
        sPlaceholder.setVisibility(message == null || message.length() == 0
                ? View.GONE : View.VISIBLE);
    }

    /** Resolve the first readable POSE folder and index its stems once. The
     * A7R II can expose the same card under /storage/sdcard0, /mnt/sdcard and
     * Environment.getExternalStorageDirectory(); accept every alias and match
     * the directory and file names without regard to case. */
    private static File findImage(PoseCard card) {
        try {
            prepareIndex();
            File found = lookup(card.id);
            if (found == null) {
                int ordinal = sPack == null ? -1 : sPack.cards.indexOf(card) + 1;
                if (ordinal > 0) found = lookup(String.valueOf(ordinal));
            }
            return found;
        } catch (Throwable t) {
            sScanReport = "POSE 索引失败: " + t;
            return null;
        }
    }

    /** Bundled images use the same c001 naming as the SD folder. */
    private static String findAsset(Context context, PoseCard card) {
        if (context == null) return null;
        loadAssetIndex(context);
        String[] keys = matchKeys(card);
        for (String key : keys) {
            String name = sAssetIndex.get(key);
            if (name != null) return name;
        }
        return null;
    }

    private static synchronized void loadAssetIndex(Context context) {
        if (sAssetIndexLoaded) return;
        sAssetIndexLoaded = true;
        try {
            String[] names = context.getAssets().list("POSE");
            if (names == null) return;
            for (String name : names) {
                int dot = name.lastIndexOf('.');
                if (dot <= 0) continue;
                String ext = name.substring(dot).toLowerCase(Locale.US);
                boolean image = false;
                for (int i = 0; i < IMAGE_EXTENSIONS.length; i++) {
                    if (ext.equals(IMAGE_EXTENSIONS[i])) { image = true; break; }
                }
                if (!image) continue;
                String stem = name.substring(0, dot).trim().toLowerCase(Locale.US);
                if (stem.length() > 0) sAssetIndex.put(stem, "POSE/" + name);
            }
        } catch (Throwable ignored) {
        }
    }

    private static String[] matchKeys(PoseCard card) {
        String id = card.id == null ? "" : card.id.trim().toLowerCase(Locale.US);
        int ordinal = sPack == null ? -1 : sPack.cards.indexOf(card) + 1;
        if (ordinal <= 0) return new String[]{id};
        String padded = String.format(Locale.US, "%03d", ordinal);
        String padded4 = String.format(Locale.US, "%04d", ordinal);
        return new String[]{id, padded, padded4, Integer.toString(ordinal),
                "c" + padded, "c" + padded4};
    }

    /** Try the indexed names first, then a short list of literal paths. The
     * APK ships assets/POSE/c001.jpg; the direct attempts guard against a
     * stale index or an assets listing that comes back empty on this
     * firmware. */
    private static String[] assetCandidates(Context context, PoseCard card) {
        List<String> out = new ArrayList<String>();
        String indexed = findAsset(context, card);
        if (indexed != null) out.add(indexed);
        String id = card.id == null ? "" : card.id.trim().toLowerCase(Locale.US);
        int ordinal = sPack == null ? -1 : sPack.cards.indexOf(card) + 1;
        if (id.length() > 0) {
            for (int i = 0; i < IMAGE_EXTENSIONS.length; i++) {
                addCandidate(out, "POSE/" + id + IMAGE_EXTENSIONS[i]);
            }
        }
        if (ordinal > 0) {
            String padded = String.format(Locale.US, "%03d", ordinal);
            String[] stems = {padded, "c" + padded, String.valueOf(ordinal)};
            for (int k = 0; k < stems.length; k++) {
                for (int i = 0; i < IMAGE_EXTENSIONS.length; i++) {
                    addCandidate(out, "POSE/" + stems[k] + IMAGE_EXTENSIONS[i]);
                }
            }
        }
        return out.toArray(new String[out.size()]);
    }

    private static void addCandidate(List<String> out, String path) {
        if (!out.contains(path)) out.add(path);
    }

    private static Bitmap decodeAsset(Context context, String path, int reqW, int reqH) {
        if (context == null || path == null) return null;
        InputStream in = null;
        try {
            in = context.getAssets().open(path);
            ByteArrayOutputStream buffer = new ByteArrayOutputStream(16384);
            byte[] chunk = new byte[8192];
            int count;
            while ((count = in.read(chunk)) > 0) buffer.write(chunk, 0, count);
            byte[] data = buffer.toByteArray();
            if (data.length == 0) { sLastDecodeError = path + ": empty"; return null; }
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                sLastDecodeError = path + ": bad bounds bytes=" + data.length
                        + " w=" + bounds.outWidth + " h=" + bounds.outHeight;
                return null;
            }
            int sample = 1;
            while (bounds.outWidth / sample > reqW * 2 || bounds.outHeight / sample > reqH * 2) {
                sample *= 2;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
            if (bitmap == null) sLastDecodeError = path + ": decode null bytes=" + data.length;
            return bitmap;
        } catch (Throwable t) {
            sLastDecodeError = path + ": " + t;
            return null;
        } finally {
            if (in != null) try { in.close(); } catch (Throwable ignored) {}
        }
    }

    private static String sizeOf(Bitmap bitmap) {
        return bitmap == null ? "null" : bitmap.getWidth() + "x" + bitmap.getHeight();
    }

    private static void tracePose(String line) {
        android.util.Log.i("LutPose", line);
        try {
            File sd = Environment.getExternalStorageDirectory();
            File dir = new File(sd, "SONYLUT");
            if (!dir.isDirectory() && !dir.mkdirs()) return;
            FileOutputStream out = new FileOutputStream(new File(dir, "POSELOAD.TXT"), true);
            try {
                String time = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
                out.write((time + " " + line + "\n").getBytes("UTF-8"));
                out.flush();
            } finally {
                out.close();
            }
        } catch (Throwable ignored) {
        }
    }

    private static File lookup(String raw) {
        if (raw == null) return null;
        String key = raw.trim().toLowerCase(Locale.US);
        if (key.length() == 0) return null;
        File direct = sImageIndex.get(key);
        if (direct != null) return direct;
        if (key.matches("c?\\d+")) {
            String digits = key.charAt(0) == 'c' ? key.substring(1) : key;
            int value;
            try { value = Integer.parseInt(digits); } catch (RuntimeException e) { return null; }
            String[] aliases = {
                    String.format(Locale.US, "%03d", value),
                    Integer.toString(value),
                    String.format(Locale.US, "c%03d", value)
            };
            for (String alias : aliases) {
                File f = sImageIndex.get(alias);
                if (f != null) return f;
            }
            // Also accept pose_001.jpg / pose-1.jpg style names, but only as a
            // last resort so an exact c001.jpg always wins.
            for (Map.Entry<String, File> entry : sImageIndex.entrySet()) {
                String stem = entry.getKey();
                int end = stem.length();
                while (end > 0 && Character.isDigit(stem.charAt(end - 1))) end--;
                if (end == stem.length()) continue;
                try {
                    if (Integer.parseInt(stem.substring(end)) != value) continue;
                } catch (RuntimeException e) {
                    continue;
                }
                String prefix = stem.substring(0, end);
                if (prefix.length() == 0 || prefix.endsWith("_") || prefix.endsWith("-")
                        || prefix.endsWith("pose") || prefix.endsWith("c")) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    private static synchronized void prepareIndex() {
        // Recheck periodically so a card inserted or a folder copied while the
        // app is open is picked up without a restart. A missing folder is
        // retried more often than an already-indexed one.
        if (sPoseDirScanned && System.currentTimeMillis() - sPoseDirScannedAt
                < (sPoseDir == null ? 8000 : 5000)) return;
        sPoseDirScanned = true;
        sPoseDirScannedAt = System.currentTimeMillis();
        sPoseDir = null;
        sImageIndex.clear();
        List<File> roots = candidateRoots();
        StringBuilder report = new StringBuilder("roots=");
        for (int i = 0; i < roots.size(); i++) {
            if (i > 0) report.append(',');
            report.append(roots.get(i).getAbsolutePath());
        }
        // Prefer a direct POSE folder, then a case variant. Listing one level
        // also finds DCIM/POSE or a folder copied inside another top folder.
        for (int r = 0; r < roots.size() && sPoseDir == null; r++) {
            File root = roots.get(r);
            File direct = new File(root, "POSE");
            if (direct.isDirectory() && direct.canRead()) { sPoseDir = direct; break; }
            File[] children = root.listFiles();
            if (children == null) continue;
            for (int i = 0; i < children.length && sPoseDir == null; i++) {
                File child = children[i];
                if (!child.isDirectory()) continue;
                if ("pose".equalsIgnoreCase(child.getName()) && child.canRead()) {
                    sPoseDir = child;
                    break;
                }
                File nested = new File(child, "POSE");
                if (nested.isDirectory() && nested.canRead()) sPoseDir = nested;
            }
        }
        if (sPoseDir == null) {
            if (sPhoto != null) loadAssetIndex(sPhoto.getContext());
            report.append("; no POSE dir; bundled=").append(sAssetIndex.size());
            sScanReport = report.toString();
            writeScanReport(sScanReport);
            return;
        }
        report.append("; dir=").append(sPoseDir.getAbsolutePath())
                .append("; read=").append(sPoseDir.canRead())
                .append("; exec=").append(sPoseDir.canExecute());
        int count = indexImages(sPoseDir, 2, report);
        if (sPhoto != null) loadAssetIndex(sPhoto.getContext());
        report.append("; files=").append(count).append("; indexed=").append(sImageIndex.size())
                .append("; bundled=").append(sAssetIndex.size());
        sScanReport = report.toString();
        writeScanReport(sScanReport);
    }

    /** Index image stems under POSE, also accepting category subfolders
     * (POSE/站姿/c001.jpg). Top-level files stay authoritative because they
     * are visited first. The report keeps the raw entry count: a real folder
     * that lists 181 names but indexes 0 points at the extension filter,
     * while a null/empty listing points at the camera's FUSE mount. */
    private static int indexImages(File dir, int depth, StringBuilder report) {
        File[] files = dir.listFiles();
        if (files == null) {
            // File.list() uses the same readdir syscall but lets us
            // distinguish "denied" from "empty" in the report.
            String[] names = dir.list();
            report.append("; list=").append(dir.getAbsolutePath()).append(" null")
                    .append(names == null ? "/null" : "/names=" + names.length);
            if (names == null || names.length == 0) return 0;
            files = new File[names.length];
            for (int i = 0; i < names.length; i++) files[i] = new File(dir, names[i]);
        }
        int count = 0, entries = 0;
        StringBuilder sample = new StringBuilder();
        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            if (file == null) continue;
            entries++;
            if (sample.length() < 120 && entries <= 8) {
                if (sample.length() > 0) sample.append(',');
                sample.append(file.isDirectory() ? "/" + file.getName() : file.getName());
            }
            boolean directory = file.isDirectory();
            if (!directory && file.getName().indexOf('.') < 0) {
                // The same FUSE mode quirk hides directories from isDirectory().
                // Probe with listFiles(); a regular file returns null.
                File[] probe = file.listFiles();
                directory = probe != null;
            }
            if (directory) {
                if (depth > 0) count += indexImages(file, depth - 1, report);
                continue;
            }
            // This camera's FUSE mount reports regular files with a mode that
            // makes File.isFile() false (the POSE listing showed c001.jpg but
            // zero images). The extension is the only reliable filter here;
            // BitmapFactory still opens the path normally.
            String name = file.getName();
            int dot = name.lastIndexOf('.');
            if (dot <= 0) continue;
            String ext = name.substring(dot).toLowerCase(Locale.US);
            boolean image = false;
            for (int k = 0; k < IMAGE_EXTENSIONS.length; k++) {
                if (ext.equals(IMAGE_EXTENSIONS[k])) { image = true; break; }
            }
            if (!image) continue;
            String stem = name.substring(0, dot).trim().toLowerCase(Locale.US);
            if (stem.length() == 0) continue;
            indexStem(stem, file);
            count++;
        }
        report.append("; list=").append(dir.getAbsolutePath())
                .append(" entries=").append(entries)
                .append(" images=").append(count)
                .append(" sample=").append(sample.length() == 0 ? "-" : sample.toString());
        return count;
    }

    /** Index a stem under every naming form the pose pack may use. The
     * user's card holds C0001…C176 while the bundled set and the pack ids use
     * c001…c176; the camera's FAT layer may also lower-case long names. A
     * literal index lookup missed those files and left the sketch visible. */
    private static void indexStem(String stem, File file) {
        if (!sImageIndex.containsKey(stem)) sImageIndex.put(stem, file);
        String digits = null;
        if (stem.matches("c\\d+")) digits = stem.substring(1);
        else if (stem.matches("\\d+")) digits = stem;
        if (digits == null || digits.length() > 6) return;
        int ordinal;
        try { ordinal = Integer.parseInt(digits); }
        catch (NumberFormatException ignored) { return; }
        if (ordinal <= 0) return;
        String plain = Integer.toString(ordinal);
        String p3 = String.format(Locale.US, "%03d", ordinal);
        String p4 = String.format(Locale.US, "%04d", ordinal);
        addStemAlias(plain, file);
        addStemAlias(p3, file);
        addStemAlias(p4, file);
        addStemAlias("c" + p3, file);
        addStemAlias("c" + p4, file);
    }

    private static void addStemAlias(String key, File file) {
        if (!sImageIndex.containsKey(key)) sImageIndex.put(key, file);
    }

    private static List<File> candidateRoots() {
        Set<String> seen = new HashSet<String>();
        List<File> roots = new ArrayList<File>();
        addRoot(roots, seen, Environment.getExternalStorageDirectory());
        addRoot(roots, seen, new File("/mnt/sdcard"));
        addRoot(roots, seen, new File("/storage/sdcard0"));
        addRoot(roots, seen, new File("/storage/sdcard1"));
        addRoot(roots, seen, new File("/sdcard"));
        addRoot(roots, seen, new File("/storage/emulated/0"));
        addRoot(roots, seen, new File("/mnt/ext_card"));
        addRoot(roots, seen, new File("/mnt/extSdCard"));
        addRoot(roots, seen, new File("/storage/extSdCard"));
        addRoot(roots, seen, new File("/mnt/sdcard1"));
        // Generic volume enumeration: unknown mounts still get a chance.
        addVolumes(roots, seen, new File("/storage"));
        addVolumes(roots, seen, new File("/mnt"));
        return roots;
    }

    private static void addVolumes(List<File> roots, Set<String> seen, File parent) {
        File[] children = parent.listFiles();
        if (children == null) return;
        for (int i = 0; i < children.length; i++) {
            File child = children[i];
            if (child != null && child.isDirectory() && child.canRead()) addRoot(roots, seen, child);
        }
    }

    private static void addRoot(List<File> roots, Set<String> seen, File root) {
        if (root == null || !root.isDirectory()) return;
        String path;
        try { path = root.getCanonicalPath(); } catch (Throwable t) { path = root.getAbsolutePath(); }
        if (seen.add(path)) roots.add(root);
    }

    private static void writeScanReport(String report) {
        try {
            File sd = Environment.getExternalStorageDirectory();
            File dir = new File(sd, "SONYLUT");
            if (!dir.isDirectory() && !dir.mkdirs()) return;
            FileOutputStream out = new FileOutputStream(new File(dir, "POSE.TXT"), false);
            try {
                String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
                out.write((time + "\n" + report + "\n").getBytes("UTF-8"));
                out.flush();
            } finally {
                out.close();
            }
        } catch (Throwable ignored) {
        }
        android.util.Log.i("LutPose", report);
    }

    private static Bitmap decode(File file, int reqW, int reqH) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
            int sample = 1;
            while (bounds.outWidth / sample > reqW * 2 || bounds.outHeight / sample > reqH * 2) {
                sample *= 2;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void releaseBitmap() {
        if (sPhoto != null) sPhoto.setImageDrawable(null);
        // The decode cache owns the pixels; only drop the screen reference so
        // flipping back to this card can reuse the bitmap without a decode.
        sBitmap = null;
        sBitmapPath = "";
    }

    private static void stepCard(int dir) {
        if (sPack == null || sPack.categories.size() == 0) return;
        String category = sPack.categories.get(sCategory);
        List<PoseCard> list = sPack.inCategory(category);
        if (list.size() == 0) return;
        sCard = (sCard + list.size() + dir) % list.size();
        render();
    }

    private static void stepCategory(int dir) {
        if (sPack == null || sPack.categories.size() == 0) return;
        sCategory = (sCategory + sPack.categories.size() + dir) % sPack.categories.size();
        sCard = 0;
        render();
    }

    private static int dp(float value) {
        return (int) (value * sDensity + 0.5f);
    }
}
