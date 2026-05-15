package pro.sketchware.utility;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Converts SVG XML to Android VectorDrawable XML.
 *
 * Supported SVG elements:
 *  - <svg>               → <vector> with viewport/size
 *  - <path>              → <path> with pathData, fill, stroke
 *  - <circle>            → <path> (converted to arc pathData)
 *  - <rect>              → <path> (converted to rect pathData)
 *  - <line>              → <path>
 *  - <polyline>          → <path>
 *  - <polygon>           → <path> (closed)
 *  - <g>                 → <group> with transform support
 *  - <ellipse>           → <path>
 */
public class SvgToVectorConverter {

    public static class ConversionResult {
        public final String vectorXml;
        public final List<String> warnings;

        ConversionResult(String xml, List<String> warnings) {
            this.vectorXml = xml;
            this.warnings = warnings;
        }
    }

    private final List<String> warnings = new ArrayList<>();

    public ConversionResult convert(String svgContent) throws Exception {
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(false);
        XmlPullParser parser = factory.newPullParser();
        parser.setInput(new StringReader(svgContent));

        StringBuilder vector = new StringBuilder();
        vector.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");

        parseDocument(parser, vector);

        return new ConversionResult(vector.toString(), warnings);
    }

    // ── Document parser ───────────────────────────────────────────────────────

    private void parseDocument(XmlPullParser p, StringBuilder out) throws Exception {
        int event = p.getEventType();
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                String tag = p.getName().toLowerCase();
                if (tag.equals("svg")) {
                    writeSvgAsVector(p, out);
                    return;
                }
            }
            event = p.next();
        }
    }

    private void writeSvgAsVector(XmlPullParser p, StringBuilder out) throws Exception {
        // Parse SVG root attributes
        String viewBox = attr(p, "viewBox");
        String width   = attr(p, "width");
        String height  = attr(p, "height");

        float vpW = 24, vpH = 24;
        float dpW = 24, dpH = 24;

        if (viewBox != null && !viewBox.isEmpty()) {
            String[] parts = viewBox.trim().split("[,\\s]+");
            if (parts.length >= 4) {
                vpW = parseFloat(parts[2], 24);
                vpH = parseFloat(parts[3], 24);
                dpW = vpW;
                dpH = vpH;
            }
        }
        if (width  != null && !width.isEmpty())  dpW = parseDim(width,  vpW);
        if (height != null && !height.isEmpty()) dpH = parseDim(height, vpH);

        out.append("<vector xmlns:android=\"http://schemas.android.com/apk/res/android\"\n");
        out.append("    android:width=\"").append(fmt(dpW)).append("dp\"\n");
        out.append("    android:height=\"").append(fmt(dpH)).append("dp\"\n");
        out.append("    android:viewportWidth=\"").append(fmt(vpW)).append("\"\n");
        out.append("    android:viewportHeight=\"").append(fmt(vpH)).append("\">\n");

        // Parse children
        parseChildren(p, out, "svg", "#000000", "none", 1f);

        out.append("</vector>\n");
    }

    // ── Children parser ───────────────────────────────────────────────────────

    private void parseChildren(XmlPullParser p, StringBuilder out,
                               String parentTag,
                               String inheritFill, String inheritStroke,
                               float inheritOpacity) throws Exception {
        int event = p.next();
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.END_TAG &&
                    p.getName().equalsIgnoreCase(parentTag)) {
                break;
            }
            if (event == XmlPullParser.START_TAG) {
                String tag = p.getName().toLowerCase();
                String fill   = resolveColor(attr(p, "fill"),   inheritFill);
                String stroke = resolveColor(attr(p, "stroke"), inheritStroke);
                String strokeW = attr(p, "stroke-width");
                float opacity = parseFloat(attr(p, "opacity"), inheritOpacity);
                float fillOp  = parseFloat(attr(p, "fill-opacity"), 1f);

                switch (tag) {
                    case "path":
                        writePath(p, out, tag, attr(p, "d"), fill, stroke, strokeW, opacity * fillOp);
                        break;
                    case "rect":
                        writeRect(p, out, tag, fill, stroke, strokeW, opacity * fillOp);
                        break;
                    case "circle":
                        writeCircle(p, out, tag, fill, stroke, strokeW, opacity * fillOp);
                        break;
                    case "ellipse":
                        writeEllipse(p, out, tag, fill, stroke, strokeW, opacity * fillOp);
                        break;
                    case "line":
                        writeLine(p, out, tag, fill, stroke, strokeW, opacity);
                        break;
                    case "polyline":
                        writePolyline(p, out, tag, attr(p, "points"), fill, stroke, strokeW, false, opacity);
                        break;
                    case "polygon":
                        writePolyline(p, out, tag, attr(p, "points"), fill, stroke, strokeW, true, opacity * fillOp);
                        break;
                    case "g":
                        writeGroup(p, out, tag, fill, stroke, opacity);
                        break;
                    default:
                        warnings.add("Skipped unsupported element: <" + tag + ">");
                        skipElement(p, tag);
                        break;
                }
            }
            event = p.next();
        }
    }

    // ── Element writers ───────────────────────────────────────────────────────

    private void writePath(XmlPullParser p, StringBuilder out, String tag,
                           String d, String fill, String stroke, String strokeW,
                           float opacity) throws Exception {
        if (d == null || d.isEmpty()) { skipElement(p, tag); return; }
        out.append("    <path\n");
        out.append("        android:pathData=\"").append(d).append("\"\n");
        if (!isNone(fill)) {
            out.append("        android:fillColor=\"").append(applyOpacity(fill, opacity)).append("\"\n");
        } else {
            out.append("        android:fillColor=\"#00000000\"\n");
        }
        if (!isNone(stroke)) {
            out.append("        android:strokeColor=\"").append(stroke).append("\"\n");
            if (strokeW != null && !strokeW.isEmpty()) {
                out.append("        android:strokeWidth=\"").append(strokeW.replace("px","")).append("\"\n");
            }
        }
        out.append("        />\n");
        skipElement(p, tag);
    }

    private void writeRect(XmlPullParser p, StringBuilder out, String tag,
                           String fill, String stroke, String strokeW, float opacity) throws Exception {
        float x  = parseFloat(attr(p, "x"),  0);
        float y  = parseFloat(attr(p, "y"),  0);
        float w  = parseFloat(attr(p, "width"),  0);
        float h  = parseFloat(attr(p, "height"), 0);
        float rx = parseFloat(attr(p, "rx"), 0);
        float ry = parseFloat(attr(p, "ry"), rx);

        String d;
        if (rx > 0 || ry > 0) {
            // Rounded rect via arcs
            d = String.format("M %.2f,%.2f " +
                    "L %.2f,%.2f Q %.2f,%.2f %.2f,%.2f " +
                    "L %.2f,%.2f Q %.2f,%.2f %.2f,%.2f " +
                    "L %.2f,%.2f Q %.2f,%.2f %.2f,%.2f " +
                    "L %.2f,%.2f Q %.2f,%.2f %.2f,%.2f Z",
                    x+rx, y,
                    x+w-rx, y, x+w, y, x+w, y+ry,
                    x+w, y+h-ry, x+w, y+h, x+w-rx, y+h,
                    x+rx, y+h, x, y+h, x, y+h-ry,
                    x, y+ry, x, y, x+rx, y);
        } else {
            d = String.format("M %.2f,%.2f L %.2f,%.2f L %.2f,%.2f L %.2f,%.2f Z",
                    x, y, x+w, y, x+w, y+h, x, y+h);
        }
        writePath(p, out, tag, d, fill, stroke, strokeW, opacity);
    }

    private void writeCircle(XmlPullParser p, StringBuilder out, String tag,
                             String fill, String stroke, String strokeW, float opacity) throws Exception {
        float cx = parseFloat(attr(p, "cx"), 0);
        float cy = parseFloat(attr(p, "cy"), 0);
        float r  = parseFloat(attr(p, "r"),  0);
        // Circle as two arc commands
        String d = String.format(
                "M %.2f,%.2f A %.2f,%.2f 0 1,0 %.2f,%.2f A %.2f,%.2f 0 1,0 %.2f,%.2f Z",
                cx - r, cy,
                r, r, cx + r, cy,
                r, r, cx - r, cy);
        writePath(p, out, tag, d, fill, stroke, strokeW, opacity);
    }

    private void writeEllipse(XmlPullParser p, StringBuilder out, String tag,
                              String fill, String stroke, String strokeW, float opacity) throws Exception {
        float cx = parseFloat(attr(p, "cx"), 0);
        float cy = parseFloat(attr(p, "cy"), 0);
        float rx = parseFloat(attr(p, "rx"), 0);
        float ry = parseFloat(attr(p, "ry"), 0);
        String d = String.format(
                "M %.2f,%.2f A %.2f,%.2f 0 1,0 %.2f,%.2f A %.2f,%.2f 0 1,0 %.2f,%.2f Z",
                cx - rx, cy,
                rx, ry, cx + rx, cy,
                rx, ry, cx - rx, cy);
        writePath(p, out, tag, d, fill, stroke, strokeW, opacity);
    }

    private void writeLine(XmlPullParser p, StringBuilder out, String tag,
                           String fill, String stroke, String strokeW, float opacity) throws Exception {
        float x1 = parseFloat(attr(p, "x1"), 0);
        float y1 = parseFloat(attr(p, "y1"), 0);
        float x2 = parseFloat(attr(p, "x2"), 0);
        float y2 = parseFloat(attr(p, "y2"), 0);
        String d = String.format("M %.2f,%.2f L %.2f,%.2f", x1, y1, x2, y2);
        String stk = isNone(stroke) ? (isNone(fill) ? "#000000" : fill) : stroke;
        writePath(p, out, tag, d, "none", stk, strokeW != null ? strokeW : "1", opacity);
    }

    private void writePolyline(XmlPullParser p, StringBuilder out, String tag,
                               String points, String fill, String stroke,
                               String strokeW, boolean close, float opacity) throws Exception {
        if (points == null || points.isEmpty()) { skipElement(p, tag); return; }
        String[] pts = points.trim().split("[,\\s]+");
        StringBuilder d = new StringBuilder();
        for (int i = 0; i + 1 < pts.length; i += 2) {
            d.append(i == 0 ? "M " : "L ")
             .append(pts[i]).append(",").append(pts[i+1]).append(" ");
        }
        if (close) d.append("Z");
        writePath(p, out, tag, d.toString().trim(), fill, stroke, strokeW, opacity);
    }

    private void writeGroup(XmlPullParser p, StringBuilder out, String tag,
                            String fill, String stroke, float opacity) throws Exception {
        String transform = attr(p, "transform");
        boolean hasTransform = transform != null && !transform.isEmpty();

        if (hasTransform) {
            out.append("    <group>\n");
            // Basic translate support
            if (transform.startsWith("translate(")) {
                String inside = transform.replaceAll("translate\\(([^)]+)\\)", "$1");
                String[] parts = inside.split("[,\\s]+");
                float tx = parseFloat(parts.length > 0 ? parts[0] : "0", 0);
                float ty = parseFloat(parts.length > 1 ? parts[1] : "0", 0);
                out.insert(out.lastIndexOf("<group>"),
                        "    <group android:translateX=\"" + fmt(tx) +
                        "\" android:translateY=\"" + fmt(ty) + "\">\n".replace("    <group>", ""));
            }
        }

        parseChildren(p, out, tag, fill, stroke, opacity);

        if (hasTransform) out.append("    </group>\n");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String attr(XmlPullParser p, String name) {
        String val = p.getAttributeValue(null, name);
        if (val == null) val = p.getAttributeValue(null, name.toLowerCase());
        return val != null ? val.trim() : null;
    }

    private void skipElement(XmlPullParser p, String tag) throws Exception {
        int depth = 1;
        int event = p.next();
        while (event != XmlPullParser.END_DOCUMENT && depth > 0) {
            if (event == XmlPullParser.START_TAG) depth++;
            else if (event == XmlPullParser.END_TAG) {
                depth--;
                if (depth == 0 && p.getName().equalsIgnoreCase(tag)) break;
            }
            event = p.next();
        }
    }

    private String resolveColor(String color, String inherit) {
        if (color == null || color.isEmpty()) return inherit;
        if (color.equals("inherit")) return inherit;
        if (color.startsWith("#")) return color;
        if (color.equals("none")) return "none";
        if (color.equals("currentColor")) return inherit;
        // Named colors → hex
        switch (color.toLowerCase()) {
            case "black":   return "#000000";
            case "white":   return "#FFFFFF";
            case "red":     return "#FF0000";
            case "green":   return "#008000";
            case "blue":    return "#0000FF";
            case "gray": case "grey": return "#808080";
            case "transparent": return "none";
            default:
                warnings.add("Unknown color '" + color + "', using #000000");
                return "#000000";
        }
    }

    private boolean isNone(String color) {
        return color == null || color.equals("none") || color.isEmpty();
    }

    /** Apply opacity to a hex color by modifying its alpha channel */
    private String applyOpacity(String hex, float opacity) {
        if (opacity >= 1f || hex == null || !hex.startsWith("#")) return hex;
        int alpha = Math.round(opacity * 255);
        String alphaHex = String.format("%02X", alpha);
        if (hex.length() == 7) {
            return "#" + alphaHex + hex.substring(1);
        }
        return hex;
    }

    private float parseFloat(String s, float def) {
        if (s == null || s.isEmpty()) return def;
        try { return Float.parseFloat(s.replaceAll("[^0-9.\\-]", "")); }
        catch (NumberFormatException e) { return def; }
    }

    private float parseDim(String s, float def) {
        if (s == null || s.isEmpty()) return def;
        s = s.trim().replaceAll("[^0-9.\\-]", "");
        try { return Float.parseFloat(s); } catch (NumberFormatException e) { return def; }
    }

    private String fmt(float f) {
        if (f == Math.floor(f)) return String.valueOf((int) f);
        return String.format("%.2f", f);
    }
}
