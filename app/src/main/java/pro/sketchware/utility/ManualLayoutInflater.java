package pro.sketchware.utility;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textview.MaterialTextView;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses an Android layout XML string and builds a real View hierarchy.
 * Works on ALL Android API levels because it does NOT use LayoutInflater
 * (which requires a compiled XmlBlock parser, not a plain XmlPullParser).
 */
public class ManualLayoutInflater {

    private static final String NS_ANDROID = "http://schemas.android.com/apk/res/android";
    private final Context context;
    public final List<String> warnings = new ArrayList<>();

    public ManualLayoutInflater(Context context) {
        this.context = context;
    }

    // ── Public entry point ────────────────────────────────────────────────────

    public View inflate(String xml) throws Exception {
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(true);
        XmlPullParser p = factory.newPullParser();
        p.setInput(new StringReader(xml));

        int event = p.getEventType();
        while (event != XmlPullParser.START_TAG && event != XmlPullParser.END_DOCUMENT) {
            event = p.next();
        }
        if (event == XmlPullParser.END_DOCUMENT) throw new Exception("Empty XML");
        return parseView(p, null);
    }

    // ── Core recursive parser ─────────────────────────────────────────────────

    private View parseView(XmlPullParser p, ViewGroup parent) throws Exception {
        String tag = p.getName();
        View view = createView(tag);
        if (view == null) {
            warnings.add("Unknown view <" + tag + "> — replaced with FrameLayout");
            view = new FrameLayout(context);
        }

        applyAttributes(p, view, parent);

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            int depth = 1;
            int event = p.next();
            while (event != XmlPullParser.END_DOCUMENT && depth > 0) {
                if (event == XmlPullParser.START_TAG) {
                    View child = parseView(p, group);
                    if (child != null) group.addView(child);
                    depth = 1; // parseView consumed END_TAG
                } else if (event == XmlPullParser.END_TAG) {
                    depth--;
                } else {
                    event = p.next();
                }
            }
        } else {
            // Consume until matching END_TAG
            skipToEndTag(p, tag);
        }

        return view;
    }

    private void skipToEndTag(XmlPullParser p, String tag) throws Exception {
        int depth = 1;
        int event = p.next();
        while (event != XmlPullParser.END_DOCUMENT && depth > 0) {
            if (event == XmlPullParser.START_TAG) depth++;
            else if (event == XmlPullParser.END_TAG) depth--;
            if (depth > 0) event = p.next();
        }
    }

    // ── View instantiation ────────────────────────────────────────────────────

    private View createView(String tag) {
        // Remove package prefix if short (e.g. "View", "TextView")
        switch (tag.contains(".") ? tag.substring(tag.lastIndexOf('.') + 1) : tag) {
            // Layouts
            case "LinearLayout":          return new LinearLayout(context);
            case "RelativeLayout":        return new RelativeLayout(context);
            case "FrameLayout":           return new FrameLayout(context);
            case "ScrollView":            return new ScrollView(context);
            case "HorizontalScrollView":  return new HorizontalScrollView(context);
            case "GridLayout":            return new GridLayout(context);
            case "ConstraintLayout":      return new FrameLayout(context); // approx
            case "CoordinatorLayout":     return new FrameLayout(context);

            // Text views
            case "TextView":             return new TextView(context);
            case "MaterialTextView":     return new MaterialTextView(context);
            case "EditText":             return new EditText(context);
            case "TextInputEditText":    return new TextInputEditText(context);
            case "AutoCompleteTextView": return new AutoCompleteTextView(context);

            // Buttons
            case "Button":               return new Button(context);
            case "MaterialButton":       return new MaterialButton(context);
            case "ImageButton":          return new ImageButton(context);
            case "CheckBox":             return new CheckBox(context);
            case "RadioButton":          return new RadioButton(context);
            case "Switch":               return new Switch(context);
            case "ToggleButton":         return new ToggleButton(context);

            // Images
            case "ImageView":            return new ImageView(context);

            // Progress / Seek
            case "ProgressBar":          return new ProgressBar(context);
            case "SeekBar":              return new SeekBar(context);

            // Lists
            case "ListView":             return new ListView(context);
            case "RecyclerView":         { FrameLayout fl = new FrameLayout(context); fl.setBackgroundColor(0x33000000); return fl; }

            // Material
            case "CardView":
            case "MaterialCardView":     return new MaterialCardView(context);
            case "TextInputLayout":      return new TextInputLayout(context);
            case "RadioGroup":           return new RadioGroup(context);

            // Containers
            case "View":                 return new View(context);
            case "Space":                return new Space(context);
            case "TabLayout":            { FrameLayout fl = new FrameLayout(context); fl.setBackgroundColor(0xFF6200EE); return fl; }

            default:
                // Try fully-qualified class name
                try {
                    Class<?> cls = Class.forName(tag);
                    return (View) cls.getConstructor(Context.class).newInstance(context);
                } catch (Exception e) {
                    return null;
                }
        }
    }

    // ── Attribute application ─────────────────────────────────────────────────

    private void applyAttributes(XmlPullParser p, View view, ViewGroup parent) {
        // --- Layout params ---
        String rawW = attr(p, "layout_width");
        String rawH = attr(p, "layout_height");
        int w = parseDim(rawW, ViewGroup.LayoutParams.WRAP_CONTENT);
        int h = parseDim(rawH, ViewGroup.LayoutParams.WRAP_CONTENT);

        int marginL = parseDimPx(attr(p, "layout_marginStart"), parseDimPx(attr(p, "layout_marginLeft"), 0));
        int marginT = parseDimPx(attr(p, "layout_marginTop"), 0);
        int marginR = parseDimPx(attr(p, "layout_marginEnd"), parseDimPx(attr(p, "layout_marginRight"), 0));
        int marginB = parseDimPx(attr(p, "layout_marginBottom"), 0);
        int marginAll = parseDimPx(attr(p, "layout_margin"), -1);
        if (marginAll >= 0) { marginL = marginT = marginR = marginB = marginAll; }

        int weight = 0;
        String rawWeight = attr(p, "layout_weight");
        if (rawWeight != null) { try { weight = (int) Float.parseFloat(rawWeight); } catch (Exception ignored) {} }

        ViewGroup.LayoutParams lp;
        if (parent instanceof LinearLayout) {
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(w, h, weight);
            llp.setMargins(marginL, marginT, marginR, marginB);
            lp = llp;
        } else if (parent instanceof RelativeLayout) {
            RelativeLayout.LayoutParams rlp = new RelativeLayout.LayoutParams(w, h);
            rlp.setMargins(marginL, marginT, marginR, marginB);
            lp = rlp;
        } else {
            FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(w, h);
            flp.setMargins(marginL, marginT, marginR, marginB);
            String lg = attr(p, "layout_gravity");
            if (lg != null) flp.gravity = parseGravity(lg);
            lp = flp;
        }
        view.setLayoutParams(lp);

        // --- Padding ---
        int padAll = parseDimPx(attr(p, "padding"), -1);
        int padL = parseDimPx(attr(p, "paddingStart"), parseDimPx(attr(p, "paddingLeft"), padAll >= 0 ? padAll : 0));
        int padT = parseDimPx(attr(p, "paddingTop"), padAll >= 0 ? padAll : 0);
        int padR = parseDimPx(attr(p, "paddingEnd"), parseDimPx(attr(p, "paddingRight"), padAll >= 0 ? padAll : 0));
        int padB = parseDimPx(attr(p, "paddingBottom"), padAll >= 0 ? padAll : 0);
        view.setPadding(padL, padT, padR, padB);

        // --- Background color ---
        String bg = attr(p, "background");
        if (bg != null && bg.startsWith("#")) {
            try { view.setBackgroundColor(Color.parseColor(bg)); } catch (Exception ignored) {}
        }

        // --- Visibility ---
        String vis = attr(p, "visibility");
        if ("gone".equals(vis)) view.setVisibility(View.GONE);
        else if ("invisible".equals(vis)) view.setVisibility(View.INVISIBLE);

        // --- Elevation ---
        String elev = attr(p, "elevation");
        if (elev != null) view.setElevation(parseDimPx(elev, 0));

        // --- LinearLayout specific ---
        if (view instanceof LinearLayout) {
            LinearLayout ll = (LinearLayout) view;
            String orient = attr(p, "orientation");
            if ("horizontal".equals(orient)) ll.setOrientation(LinearLayout.HORIZONTAL);
            else ll.setOrientation(LinearLayout.VERTICAL);
            String grav = attr(p, "gravity");
            if (grav != null) ll.setGravity(parseGravity(grav));
        }

        // --- TextView / Button / CheckBox ---
        if (view instanceof TextView) {
            TextView tv = (TextView) view;
            String text = attr(p, "text");
            if (text != null) tv.setText(text);
            String textColor = attr(p, "textColor");
            if (textColor != null && textColor.startsWith("#")) {
                try { tv.setTextColor(Color.parseColor(textColor)); } catch (Exception ignored) {}
            }
            String textSize = attr(p, "textSize");
            if (textSize != null) {
                float sp = parseRawDim(textSize, 14);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
            }
            String textStyle = attr(p, "textStyle");
            if (textStyle != null) {
                int style = Typeface.NORMAL;
                if (textStyle.contains("bold")) style |= Typeface.BOLD;
                if (textStyle.contains("italic")) style |= Typeface.ITALIC;
                tv.setTypeface(null, style);
            }
            String hint = attr(p, "hint");
            if (hint != null && view instanceof EditText) ((EditText) tv).setHint(hint);
            String gravity = attr(p, "gravity");
            if (gravity != null) tv.setGravity(parseGravity(gravity));
            String lines = attr(p, "maxLines");
            if (lines != null) try { tv.setMaxLines(Integer.parseInt(lines)); } catch (Exception ignored) {}
            String ems = attr(p, "singleLine");
            if ("true".equals(ems)) { tv.setSingleLine(true); tv.setEllipsize(TextUtils.TruncateAt.END); }
        }

        // --- ImageView ---
        if (view instanceof ImageView) {
            String scaleType = attr(p, "scaleType");
            if (scaleType != null) {
                try { ((ImageView) view).setScaleType(ImageView.ScaleType.valueOf(scaleType.toUpperCase())); }
                catch (Exception ignored) {}
            }
        }

        // --- ScrollView ---
        if (view instanceof ScrollView) {
            String fillVp = attr(p, "fillViewport");
            ((ScrollView) view).setFillViewport("true".equals(fillVp));
        }
    }

    // ── Attribute helpers ─────────────────────────────────────────────────────

    private String attr(XmlPullParser p, String name) {
        String v = p.getAttributeValue(NS_ANDROID, name);
        if (v == null) v = p.getAttributeValue(null, name);
        return v != null ? v.trim() : null;
    }

    private int parseDim(String raw, int def) {
        if (raw == null) return def;
        if ("match_parent".equals(raw) || "fill_parent".equals(raw)) return ViewGroup.LayoutParams.MATCH_PARENT;
        if ("wrap_content".equals(raw)) return ViewGroup.LayoutParams.WRAP_CONTENT;
        return parseDimPx(raw, def);
    }

    private int parseDimPx(String raw, int def) {
        if (raw == null || raw.isEmpty()) return def;
        try {
            if (raw.endsWith("dp") || raw.endsWith("dip")) {
                float dp = Float.parseFloat(raw.replace("dp","").replace("dip","").trim());
                return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                        context.getResources().getDisplayMetrics()));
            }
            if (raw.endsWith("sp")) {
                float sp = Float.parseFloat(raw.replace("sp","").trim());
                return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp,
                        context.getResources().getDisplayMetrics()));
            }
            if (raw.endsWith("px")) {
                return (int) Float.parseFloat(raw.replace("px","").trim());
            }
            return (int) Float.parseFloat(raw.trim());
        } catch (Exception e) { return def; }
    }

    private float parseRawDim(String raw, float def) {
        if (raw == null) return def;
        try { return Float.parseFloat(raw.replaceAll("[^0-9.]", "")); }
        catch (Exception e) { return def; }
    }

    private int parseGravity(String raw) {
        if (raw == null) return Gravity.NO_GRAVITY;
        int g = Gravity.NO_GRAVITY;
        if (raw.contains("center_horizontal") || raw.contains("center")) g |= Gravity.CENTER_HORIZONTAL;
        if (raw.contains("center_vertical") || raw.contains("center")) g |= Gravity.CENTER_VERTICAL;
        if (raw.contains("start") || raw.contains("left")) g |= Gravity.START;
        if (raw.contains("end") || raw.contains("right")) g |= Gravity.END;
        if (raw.contains("top")) g |= Gravity.TOP;
        if (raw.contains("bottom")) g |= Gravity.BOTTOM;
        return g;
    }
}
