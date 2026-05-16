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
 * Builds a real Android View hierarchy directly from an XML layout string.
 *
 * Parser invariant:
 *   - On entry to parseView(), parser is AT the element's START_TAG.
 *   - On exit from parseView(), parser is AT the element's END_TAG
 *     (or the self-closing tag position).
 *
 * This invariant allows clean recursion without losing parser position.
 */
public class ManualLayoutInflater {

    private static final String NS = "http://schemas.android.com/apk/res/android";

    private final Context context;
    public final List<String> warnings = new ArrayList<>();

    public ManualLayoutInflater(Context context) {
        this.context = context;
    }

    // ── Entry point ───────────────────────────────────────────────────────────

    public View inflate(String xml) throws Exception {
        if (xml == null || xml.trim().isEmpty()) throw new Exception("Empty XML");

        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(true);
        XmlPullParser p = factory.newPullParser();
        p.setInput(new StringReader(xml));

        // Advance to first START_TAG
        int event = p.getEventType();
        while (event != XmlPullParser.START_TAG) {
            if (event == XmlPullParser.END_DOCUMENT)
                throw new Exception("No root element found");
            event = p.next();
        }

        return parseView(p, null);
    }

    // ── Recursive view parser ─────────────────────────────────────────────────

    /**
     * Parser is AT START_TAG on entry.
     * Parser is AT END_TAG on exit.
     */
    private View parseView(XmlPullParser p, ViewGroup parent) throws Exception {
        String tag = p.getName();
        if (tag == null) tag = "FrameLayout"; // safety

        boolean isSelfClosing = p.isEmptyElementTag();

        View view = createView(tag);
        if (view == null) {
            warnings.add("Unknown <" + tag + "> replaced with FrameLayout");
            view = new FrameLayout(context);
        }

        applyAttributes(p, view, parent);

        if (isSelfClosing) {
            // Self-closing tag: parser stays at this position (treated as END_TAG)
            return view;
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            // Move into children
            int event = p.next();
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.END_TAG) {
                    // This is OUR closing tag — stop
                    break;
                }
                if (event == XmlPullParser.START_TAG) {
                    // Child element — recurse (child will consume its own END_TAG)
                    View child = parseView(p, group);
                    group.addView(child);
                    // After parseView returns, parser is at child's END_TAG
                    // Advance past it to get next sibling or our END_TAG
                    event = p.next();
                } else {
                    event = p.next();
                }
            }
            // Parser is now AT our END_TAG
        } else {
            // Leaf node: consume everything until matching END_TAG
            advanceToEndTag(p, tag);
            // Parser is now AT our END_TAG
        }

        return view;
    }

    /** Advance parser until we find the END_TAG matching the given element name. */
    private void advanceToEndTag(XmlPullParser p, String elementTag) throws Exception {
        int depth = 1;
        int event = p.next();
        while (event != XmlPullParser.END_DOCUMENT && depth > 0) {
            if (event == XmlPullParser.START_TAG) depth++;
            else if (event == XmlPullParser.END_TAG) {
                depth--;
                if (depth == 0) break; // this is our END_TAG
            }
            if (depth > 0) event = p.next();
        }
    }

    // ── View factory ──────────────────────────────────────────────────────────

    private View createView(String fullTag) {
        // Strip package prefix for matching
        String tag = fullTag.contains(".")
                ? fullTag.substring(fullTag.lastIndexOf('.') + 1)
                : fullTag;

        switch (tag) {
            // Layouts
            case "LinearLayout":         return new LinearLayout(context);
            case "RelativeLayout":       return new RelativeLayout(context);
            case "FrameLayout":          return new FrameLayout(context);
            case "ScrollView":           return new ScrollView(context);
            case "HorizontalScrollView": return new HorizontalScrollView(context);
            case "GridLayout":           return new GridLayout(context);
            case "ConstraintLayout":     return new FrameLayout(context); // approximated
            case "CoordinatorLayout":    return new FrameLayout(context);
            case "AppBarLayout":         { LinearLayout ll = new LinearLayout(context); ll.setBackgroundColor(0xFF6200EE); return ll; }

            // Text
            case "TextView":             return new TextView(context);
            case "MaterialTextView":     return new MaterialTextView(context);
            case "EditText":             return new EditText(context);
            case "TextInputEditText":    return new TextInputEditText(context);
            case "AutoCompleteTextView": return new AutoCompleteTextView(context);
            case "MultiAutoCompleteTextView": return new MultiAutoCompleteTextView(context);

            // Buttons
            case "Button":               return new Button(context);
            case "MaterialButton":       return new MaterialButton(context);
            case "ImageButton":          return new ImageButton(context);
            case "CheckBox":             return new CheckBox(context);
            case "RadioButton":          return new RadioButton(context);
            case "RadioGroup":           return new RadioGroup(context);
            case "Switch":               return new Switch(context);
            case "ToggleButton":         return new ToggleButton(context);

            // Image
            case "ImageView":            return new ImageView(context);

            // Progress
            case "ProgressBar":          return new ProgressBar(context);
            case "SeekBar":              return new SeekBar(context);
            case "RatingBar":            return new RatingBar(context);

            // Lists / Scroll
            case "ListView":             return new ListView(context);
            case "GridView":             return new GridView(context);
            case "NestedScrollView":     return new ScrollView(context);
            case "RecyclerView": {
                FrameLayout fl = new FrameLayout(context);
                fl.setBackgroundColor(0x22000000);
                TextView tv = new TextView(context);
                tv.setText("[RecyclerView]");
                tv.setGravity(Gravity.CENTER);
                tv.setPadding(16, 32, 16, 32);
                fl.addView(tv);
                return fl;
            }

            // Material
            case "CardView":
            case "MaterialCardView":     return new MaterialCardView(context);
            case "TextInputLayout":      return new TextInputLayout(context);
            case "Toolbar":
            case "MaterialToolbar": {
                LinearLayout ll = new LinearLayout(context);
                ll.setBackgroundColor(0xFF6200EE);
                ll.setMinimumHeight(dp(56));
                return ll;
            }
            case "TabLayout": {
                FrameLayout fl = new FrameLayout(context);
                fl.setBackgroundColor(0xFF6200EE);
                fl.setMinimumHeight(dp(48));
                return fl;
            }
            case "FloatingActionButton": {
                Button b = new Button(context);
                b.setText("FAB");
                return b;
            }

            // Misc
            case "View":                 return new View(context);
            case "Space":                return new Space(context);
            case "include":              return new FrameLayout(context); // placeholder
            case "ViewStub":             return new View(context);
            case "WebView": {
                TextView tv = new TextView(context);
                tv.setText("[WebView]");
                tv.setGravity(Gravity.CENTER);
                tv.setBackgroundColor(0xFFF5F5F5);
                return tv;
            }

            default:
                // Try full class name
                try {
                    Class<?> cls = Class.forName(fullTag);
                    return (View) cls.getConstructor(Context.class).newInstance(context);
                } catch (Exception ignored) {}
                return null;
        }
    }

    // ── Attribute application ─────────────────────────────────────────────────

    private void applyAttributes(XmlPullParser p, View view, ViewGroup parent) {
        // Layout size
        int w = parseDim(attr(p, "layout_width"), ViewGroup.LayoutParams.WRAP_CONTENT);
        int h = parseDim(attr(p, "layout_height"), ViewGroup.LayoutParams.WRAP_CONTENT);

        // Margins
        int ma  = parsePx(attr(p, "layout_margin"), -1);
        int ml  = parsePx(attr(p, "layout_marginStart"),
                   parsePx(attr(p, "layout_marginLeft"),  ma < 0 ? 0 : ma));
        int mt  = parsePx(attr(p, "layout_marginTop"),    ma < 0 ? 0 : ma);
        int mr  = parsePx(attr(p, "layout_marginEnd"),
                   parsePx(attr(p, "layout_marginRight"), ma < 0 ? 0 : ma));
        int mb  = parsePx(attr(p, "layout_marginBottom"), ma < 0 ? 0 : ma);

        float weight = parseFloat(attr(p, "layout_weight"), 0f);

        // Build LayoutParams
        if (parent instanceof LinearLayout) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h, weight);
            lp.setMargins(ml, mt, mr, mb);
            view.setLayoutParams(lp);
        } else if (parent instanceof RelativeLayout) {
            RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(w, h);
            lp.setMargins(ml, mt, mr, mb);
            view.setLayoutParams(lp);
        } else {
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h);
            lp.setMargins(ml, mt, mr, mb);
            String lg = attr(p, "layout_gravity");
            if (lg != null) lp.gravity = parseGravity(lg);
            view.setLayoutParams(lp);
        }

        // Padding
        int pa = parsePx(attr(p, "padding"), -1);
        int pl = parsePx(attr(p, "paddingStart"),
                  parsePx(attr(p, "paddingLeft"),   pa < 0 ? 0 : pa));
        int pt = parsePx(attr(p, "paddingTop"),     pa < 0 ? 0 : pa);
        int pr = parsePx(attr(p, "paddingEnd"),
                  parsePx(attr(p, "paddingRight"),  pa < 0 ? 0 : pa));
        int pb = parsePx(attr(p, "paddingBottom"),  pa < 0 ? 0 : pa);
        if (pl+pt+pr+pb > 0 || pa >= 0) view.setPadding(pl, pt, pr, pb);

        // Background
        applyBackground(view, attr(p, "background"));

        // Elevation
        String elev = attr(p, "elevation");
        if (elev != null) view.setElevation(parsePx(elev, 0));

        // Visibility
        String vis = attr(p, "visibility");
        if ("gone".equals(vis))      view.setVisibility(View.GONE);
        else if ("invisible".equals(vis)) view.setVisibility(View.INVISIBLE);

        // Alpha
        String alpha = attr(p, "alpha");
        if (alpha != null) try { view.setAlpha(Float.parseFloat(alpha)); } catch (Exception ignored) {}

        // Min width/height
        String minW = attr(p, "minWidth");
        if (minW != null) view.setMinimumWidth(parsePx(minW, 0));
        String minH = attr(p, "minHeight");
        if (minH != null) view.setMinimumHeight(parsePx(minH, 0));

        // LinearLayout extras
        if (view instanceof LinearLayout) {
            LinearLayout ll = (LinearLayout) view;
            String orient = attr(p, "orientation");
            ll.setOrientation("horizontal".equals(orient)
                    ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
            String grav = attr(p, "gravity");
            if (grav != null) ll.setGravity(parseGravity(grav));
            String divider = attr(p, "showDividers");
            // divider not supported in simple renderer
        }

        // TextView / Button extras
        if (view instanceof TextView) {
            TextView tv = (TextView) view;
            String text = attr(p, "text");
            if (text != null) tv.setText(text);

            String tc = attr(p, "textColor");
            if (tc != null && tc.startsWith("#")) {
                try { tv.setTextColor(Color.parseColor(tc)); } catch (Exception ignored) {}
            }

            String ts = attr(p, "textSize");
            if (ts != null) tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, parseDimRaw(ts, 14f));

            String tStyle = attr(p, "textStyle");
            if (tStyle != null) {
                int s = Typeface.NORMAL;
                if (tStyle.contains("bold"))   s |= Typeface.BOLD;
                if (tStyle.contains("italic")) s |= Typeface.ITALIC;
                tv.setTypeface(null, s);
            }

            String hint = attr(p, "hint");
            if (hint != null && view instanceof EditText) ((EditText) tv).setHint(hint);

            String grav = attr(p, "gravity");
            if (grav != null) tv.setGravity(parseGravity(grav));

            if ("true".equals(attr(p, "singleLine"))) {
                tv.setSingleLine(true);
                tv.setEllipsize(TextUtils.TruncateAt.END);
            }

            String maxLines = attr(p, "maxLines");
            if (maxLines != null) try { tv.setMaxLines(Integer.parseInt(maxLines)); } catch (Exception ignored) {}

            String lines = attr(p, "lines");
            if (lines != null) try { tv.setLines(Integer.parseInt(lines)); } catch (Exception ignored) {}

            String allCaps = attr(p, "textAllCaps");
            if ("false".equals(allCaps)) tv.setAllCaps(false);
        }

        // ImageView extras
        if (view instanceof ImageView) {
            String st = attr(p, "scaleType");
            if (st != null) {
                try {
                    ((ImageView) view).setScaleType(
                            ImageView.ScaleType.valueOf(st.toUpperCase()));
                } catch (Exception ignored) {}
            }
        }
    }

    private void applyBackground(View view, String bg) {
        if (bg == null || bg.isEmpty()) return;
        if (bg.startsWith("#")) {
            try { view.setBackgroundColor(Color.parseColor(bg)); } catch (Exception ignored) {}
        }
        // @drawable/ → already sanitized to empty, ignore
        // @color/ → already sanitized to #hex, handled above
    }

    // ── Attribute helpers ─────────────────────────────────────────────────────

    private String attr(XmlPullParser p, String name) {
        String v = p.getAttributeValue(NS, name);
        if (v == null) v = p.getAttributeValue(null, name);
        return (v != null) ? v.trim() : null;
    }

    private int parseDim(String raw, int def) {
        if (raw == null) return def;
        if ("match_parent".equals(raw) || "fill_parent".equals(raw))
            return ViewGroup.LayoutParams.MATCH_PARENT;
        if ("wrap_content".equals(raw))
            return ViewGroup.LayoutParams.WRAP_CONTENT;
        return parsePx(raw, def);
    }

    private int parsePx(String raw, int def) {
        if (raw == null || raw.isEmpty()) return def;
        try {
            String digits = raw.replaceAll("[^0-9.\\-]", "");
            if (digits.isEmpty()) return def;
            float val = Float.parseFloat(digits);
            if (raw.endsWith("sp")) {
                return Math.round(TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_SP, val,
                        context.getResources().getDisplayMetrics()));
            }
            if (raw.endsWith("px")) return (int) val;
            // dp (default)
            return Math.round(TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, val,
                    context.getResources().getDisplayMetrics()));
        } catch (Exception e) { return def; }
    }

    private float parseDimRaw(String raw, float def) {
        if (raw == null) return def;
        try { return Float.parseFloat(raw.replaceAll("[^0-9.\\-]", "")); }
        catch (Exception e) { return def; }
    }

    private float parseFloat(String raw, float def) {
        if (raw == null) return def;
        try { return Float.parseFloat(raw.trim()); } catch (Exception e) { return def; }
    }

    private int parseGravity(String raw) {
        if (raw == null) return Gravity.NO_GRAVITY;
        int g = 0;
        if (raw.contains("center_horizontal")) g |= Gravity.CENTER_HORIZONTAL;
        if (raw.contains("center_vertical"))   g |= Gravity.CENTER_VERTICAL;
        if (raw.equals("center"))              g |= Gravity.CENTER;
        if (raw.contains("start") || raw.contains("left")) g |= Gravity.START;
        if (raw.contains("end")   || raw.contains("right")) g |= Gravity.END;
        if (raw.contains("top"))    g |= Gravity.TOP;
        if (raw.contains("bottom")) g |= Gravity.BOTTOM;
        if (raw.contains("fill_horizontal")) g |= Gravity.FILL_HORIZONTAL;
        if (raw.contains("fill_vertical"))   g |= Gravity.FILL_VERTICAL;
        return g == 0 ? Gravity.NO_GRAVITY : g;
    }

    private int dp(int val) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, val,
                context.getResources().getDisplayMetrics()));
    }
}
