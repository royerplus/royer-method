import java.io.*;
import java.util.*;

/** Verificacion de posicion y viaje en el celular (sin Python/OpenCV).
 *  refsave NOMBRE ESC   : guarda la vista actual (centrada en la parada tras la lupa) como referencia de NOMBRE (ESC = px de pantalla por px de mosaico en ese momento).
 *  pos NOMBRE           : compara la pantalla con la referencia, en varias escalas; imprime  POS dx dy score r esc  (dx,dy = donde aparece la parada respecto al centro; tocar (1218+dx,540+dy) la alcanza).
 *  ruta DESDE HASTA     : ruta segura (evita sitios E) usando ruta.txt ("n x y tipo" por linea); imprime  H n x y  por cada waypoint.
 *  paso x0 y0 x1 y1 esc : toque para ir de (x0,y0) a (x1,y1) en coordenadas de ruta; imprime  TAP X Y SLEEP S F  (F<1 = tramo parcial). */
public class Pos {
    static final String DIR = System.getenv("POSDIR") != null ? System.getenv("POSDIR") : "/data/local/tmp/";
    static final int CX = 1218, CY = 540;
    static int gray(int x, int y) { if (x < 0) x = 0; if (y < 0) y = 0; if (x >= Bcv.W) x = Bcv.W - 1; if (y >= Bcv.H) y = Bcv.H - 1; int o = 16 + (y * Bcv.W + x) * 4; return ((Bcv.d[o] & 255) * 30 + (Bcv.d[o + 1] & 255) * 59 + (Bcv.d[o + 2] & 255) * 11) / 100; }

    /** mapa de bordes de la region (x0,y0,w,h) de pantalla a resolucion 1/f, con el aro del personaje (centro de pantalla) a cero. */
    static float[][] edges(int x0, int y0, int w, int h, int f) {
        int gw = w / f, gh = h / f; float[][] g = new float[gh][gw];
        for (int j = 0; j < gh; j++) for (int i = 0; i < gw; i++) { int s = 0; for (int v = 0; v < f; v++) for (int u = 0; u < f; u++) s += gray(x0 + i * f + u, y0 + j * f + v); g[j][i] = s / (float) (f * f); }
        float[][] b = new float[gh][gw];                                   // blur 3x3
        for (int j = 0; j < gh; j++) for (int i = 0; i < gw; i++) { float s = 0; int n = 0; for (int v = -1; v <= 1; v++) for (int u = -1; u <= 1; u++) { int jj = j + v, ii = i + u; if (jj >= 0 && jj < gh && ii >= 0 && ii < gw) { s += g[jj][ii]; n++; } } b[j][i] = s / n; }
        float[][] e = new float[gh][gw]; double tot = 0;
        for (int j = 1; j < gh - 1; j++) for (int i = 1; i < gw - 1; i++) {
            float gx = (b[j - 1][i + 1] + 2 * b[j][i + 1] + b[j + 1][i + 1]) - (b[j - 1][i - 1] + 2 * b[j][i - 1] + b[j + 1][i - 1]);
            float gy = (b[j + 1][i - 1] + 2 * b[j + 1][i] + b[j + 1][i + 1]) - (b[j - 1][i - 1] + 2 * b[j - 1][i] + b[j - 1][i + 1]);
            float m = (float) Math.sqrt(gx * gx + gy * gy); e[j][i] = m; tot += m; }
        float mean = (float) (tot / Math.max(1, (gw - 2) * (gh - 2))) + 1e-6f;
        float cxm = (CX - x0) / (float) f, cym = (CY - y0) / (float) f, rm = 75f / f;
        for (int j = 0; j < gh; j++) for (int i = 0; i < gw; i++) { e[j][i] /= mean; float dx = i - cxm, dy = j - cym; if (dx * dx + dy * dy < rm * rm) e[j][i] = 0; }
        return e;
    }
    static void saveRef(String name, double esc) throws Exception {
        float[][] t = edges(CX - 300, CY - 200, 600, 400, 2);
        try (DataOutputStream o = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(DIR + "ref_" + name + ".bin")))) {
            o.writeInt(t[0].length); o.writeInt(t.length); o.writeDouble(esc);
            for (float[] r : t) for (float v : r) o.writeByte(Math.min(255, Math.round(v * 40f)));
        }
    }
    static float[][] resample(float[][] s, double r) {                       // escala r alrededor del centro
        int h = s.length, w = s[0].length, nh = (int) Math.round(h * r), nw = (int) Math.round(w * r); float[][] o = new float[nh][nw];
        for (int j = 0; j < nh; j++) for (int i = 0; i < nw; i++) {
            double sy = (j + 0.5) / r - 0.5, sx = (i + 0.5) / r - 0.5; int y0 = (int) Math.floor(sy), x0 = (int) Math.floor(sx); double fy = sy - y0, fx = sx - x0;
            float a = at(s, y0, x0), b = at(s, y0, x0 + 1), c = at(s, y0 + 1, x0), d = at(s, y0 + 1, x0 + 1);
            o[j][i] = (float) (a * (1 - fx) * (1 - fy) + b * fx * (1 - fy) + c * (1 - fx) * fy + d * fx * fy); }
        return o;
    }
    static float at(float[][] s, int y, int x) { return (y < 0 || x < 0 || y >= s.length || x >= s[0].length) ? 0 : s[y][x]; }
    static float[][] pool(float[][] s, int f) { int h = s.length / f, w = s[0].length / f; float[][] o = new float[h][w]; for (int j = 0; j < h; j++) for (int i = 0; i < w; i++) { float a = 0; for (int v = 0; v < f; v++) for (int u = 0; u < f; u++) a += s[j * f + v][i * f + u]; o[j][i] = a / (f * f); } return o; }
    static float[][] zeromean(float[][] t) { double s = 0; int n = 0; for (float[] r : t) for (float v : r) { s += v; n++; } float m = (float) (s / n); float[][] o = new float[t.length][t[0].length]; for (int j = 0; j < t.length; j++) for (int i = 0; i < t[0].length; i++) o[j][i] = t[j][i] == 0 ? 0 : t[j][i] - m; return o; }
    /** mejor desplazamiento (tx,ty de la esquina de t dentro de win) y score NCC aproximado; busca en [tx0-rx, tx0+rx] x [ty0-ry, ty0+ry] */
    static double[] ncc(float[][] win, float[][] t, int tx0, int ty0, int rx, int ry) {
        int th = t.length, tw = t[0].length, wh = win.length, ww = win[0].length; double tn = 0; for (float[] r : t) for (float v : r) tn += v * v; tn = Math.sqrt(tn) + 1e-9;
        double bs = -1; int bx = 0, by = 0;
        for (int oy = Math.max(0, ty0 - ry); oy <= Math.min(wh - th, ty0 + ry); oy++) for (int ox = Math.max(0, tx0 - rx); ox <= Math.min(ww - tw, tx0 + rx); ox++) {
            double s = 0, w2 = 0;
            for (int j = 0; j < th; j++) { float[] tr = t[j], wr = win[oy + j]; for (int i = 0; i < tw; i++) { float wv = wr[ox + i]; s += tr[i] * wv; w2 += wv * wv; } }
            double sc = s / (tn * (Math.sqrt(w2) + 1e-9)); if (sc > bs) { bs = sc; bx = ox; by = oy; } }
        return new double[]{bx, by, bs};
    }
    /** devuelve {dx, dy, score, r, esc} o null */
    static double[] medir(String name) throws Exception {
        File fr = new File(DIR + "ref_" + name + ".bin"); if (!fr.exists()) return null;
        float[][] ref; double esc0;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(fr)))) { int w = in.readInt(), h = in.readInt(); esc0 = in.readDouble(); ref = new float[h][w]; for (int j = 0; j < h; j++) for (int i = 0; i < w; i++) ref[j][i] = (in.readUnsignedByte()) / 40f; }
        int WX = CX - 700, WY = CY - 400, WW = 1400, WH = 800;               // ventana de busqueda (pantalla)
        float[][] win2 = edges(WX, WY, WW, WH, 2), win8 = pool(win2, 4);      // 700x400 y 175x100
        double bestS = -1, bestR = 1; int bx8 = 0, by8 = 0;
        for (double r = 0.84; r <= 1.2001; r += 0.03) {
            float[][] ts = resample(ref, r), tz = zeromean(pool(ts, 4));
            int tw = tz[0].length, th = tz.length; if (tw >= win8[0].length || th >= win8.length) continue;
            int tx0 = (win8[0].length - tw) / 2, ty0 = (win8.length - th) / 2;
            double[] b = ncc(win8, tz, tx0, ty0, 40, 24);
            if (b[2] > bestS) { bestS = b[2]; bestR = r; bx8 = (int) b[0] - tx0; by8 = (int) b[1] - ty0; }
        }
        // refinar: escala fina y posicion a 1/2
        double bs2 = -1, br2 = bestR; int bdx = 0, bdy = 0;
        for (double r = bestR - 0.03; r <= bestR + 0.0301; r += 0.01) {
            float[][] tz = zeromean(resample(ref, r)); int tw = tz[0].length, th = tz.length; if (tw >= win2[0].length || th >= win2.length) continue;
            int tx0 = (win2[0].length - tw) / 2, ty0 = (win2.length - th) / 2;
            double[] b = ncc(win2, tz, tx0 + bx8 * 4, ty0 + by8 * 4, 6, 6);
            if (b[2] > bs2) { bs2 = b[2]; br2 = r; bdx = ((int) b[0] - tx0) * 2; bdy = ((int) b[1] - ty0) * 2; }
        }
        return new double[]{bdx, bdy, bs2, br2, esc0 * br2};
    }
    // ---------- ruta ----------
    static List<double[]> pts = new ArrayList<>(); static List<String> ids = new ArrayList<>(); static List<String> tip = new ArrayList<>();
    static void cargaRuta() throws Exception { try (BufferedReader br = new BufferedReader(new FileReader(DIR + "ruta.txt"))) { String l; while ((l = br.readLine()) != null) { String[] p = l.trim().split("\\s+"); if (p.length < 4) continue; ids.add(p[0]); pts.add(new double[]{Double.parseDouble(p[1]), Double.parseDouble(p[2])}); tip.add(p[3]); } } }
    static double dseg(double[] a, double[] b, double[] c) { double vx = b[0] - a[0], vy = b[1] - a[1], L = vx * vx + vy * vy; double t = L == 0 ? 0 : Math.max(0, Math.min(1, ((c[0] - a[0]) * vx + (c[1] - a[1]) * vy) / L)); return Math.hypot(a[0] + t * vx - c[0], a[1] + t * vy - c[1]); }
    static String ruta(String desde, String hasta) throws Exception {
        cargaRuta(); int a = ids.indexOf(desde), b = ids.indexOf(hasta); double[] pa = null;
        if (a < 0 && desde.contains(",")) { String[] q = desde.split(","); pa = new double[]{Double.parseDouble(q[0]), Double.parseDouble(q[1])}; ids.add("aqui"); pts.add(pa); tip.add("V"); a = ids.size() - 1; }
        if (a < 0 || b < 0) return "NO_ENCONTRADO";
        int n = pts.size(); double[] dist = new double[n]; int[] prev = new int[n]; Arrays.fill(dist, 1e18); Arrays.fill(prev, -1); boolean[] done = new boolean[n]; dist[a] = 0;
        for (int it = 0; it < n; it++) { int u = -1; for (int i = 0; i < n; i++) if (!done[i] && (u < 0 || dist[i] < dist[u])) u = i; if (u < 0 || dist[u] >= 1e17) break; done[u] = true; if (u == b) break;
            for (int v = 0; v < n; v++) { if (v == u || done[v]) continue; if (tip.get(v).equals("E") && v != b) continue; double len = Math.hypot(pts.get(u)[0] - pts.get(v)[0], pts.get(u)[1] - pts.get(v)[1]); if (len > 500) continue;
                boolean libre = true; for (int e = 0; e < n && libre; e++) if (tip.get(e).equals("E") && e != b && e != u && dseg(pts.get(u), pts.get(v), pts.get(e)) <= 50) libre = false;
                if (libre && dist[u] + len < dist[v]) { dist[v] = dist[u] + len; prev[v] = u; } } }
        if (prev[b] < 0 && a != b) return "SIN_RUTA_SEGURA";
        LinkedList<Integer> cam = new LinkedList<>(); for (int v = b; v >= 0; v = prev[v]) cam.addFirst(v);
        StringBuilder sb = new StringBuilder(); for (int v : cam) sb.append("H ").append(ids.get(v)).append(' ').append(Math.round(pts.get(v)[0] * 10) / 10.0).append(' ').append(Math.round(pts.get(v)[1] * 10) / 10.0).append('\n'); return sb.toString().trim();
    }
    static String paso(double x0, double y0, double x1, double y1, double esc) {
        double vx = (x1 - x0) * 1.5 * esc, vy = (y1 - y0) * 1.5 * esc, f = 1.0; double tx = CX + vx, ty = CY + vy;
        while (!(tx >= 560 && tx <= 1900 && ty >= 200 && ty <= 880)) { f -= 0.02; if (f <= 0.05) { tx = CX; ty = CY; break; } tx = CX + vx * f; ty = CY + vy * f; }
        return "TAP " + Math.round(tx) + " " + Math.round(ty) + " SLEEP " + Math.round(3 + Math.hypot(vx * f, vy * f) / 80) + " F " + String.format(Locale.US, "%.2f", f);
    }
    static String run(String[] a) throws Exception {
        switch (a[0]) {
            case "refsave": Bcv.cap(); if (!Bcv.ok()) return "NO_HORIZONTAL"; saveRef(a[1], Double.parseDouble(a[2])); return "REF_OK " + a[1];
            case "pos": { Bcv.cap(); if (!Bcv.ok()) return "NO_HORIZONTAL"; double[] m = medir(a[1]); if (m == null) return "SIN_REF " + a[1];
                return String.format(Locale.US, "POS %d %d %.2f %.3f %.4f", (int) m[0], (int) m[1], m[2], m[3], m[4]); }
            case "quedan": { Bcv.cap(); if (!Bcv.ok()) return "NO_HORIZONTAL"; int[] xs = {268, 436, 603, 770, 938, 1106, 1274, 1442}, ys = {270, 438, 605, 773}; int n = 0; StringBuilder sb = new StringBuilder();
                for (int y : ys) for (int x : xs) { double sum = 0, sum2 = 0; int c = 0; for (int v = -30; v < 30; v += 3) for (int u = -40; u < 40; u += 3) { int g = gray(x + u, y + v); sum += g; sum2 += g * g; c++; } double sd = Math.sqrt(Math.max(0, sum2 / c - (sum / c) * (sum / c))); if (sd > 12) { n++; sb.append('1'); } else sb.append('0'); }
                return "QUEDAN " + n + " " + sb; }
            case "loc": { Bcv.cap(); if (!Bcv.ok()) return "NO_HORIZONTAL"; String bn = null; double[] bm = null; File[] fs = new File(DIR).listFiles();
                if (fs != null) for (File f : fs) { String n = f.getName(); if (!n.startsWith("ref_") || !n.endsWith(".bin")) continue; n = n.substring(4, n.length() - 4); double[] m = medir(n); if (m != null && (bm == null || m[2] > bm[2])) { bm = m; bn = n; } }
                if (bm == null || bm[2] < 0.45) return "PERDIDO"; return String.format(Locale.US, "LOC %s %d %d %.2f %.3f %.4f", bn, (int) bm[0], (int) bm[1], bm[2], bm[3], bm[4]); }
            case "ruta": return ruta(a[1], a[2]);
            case "paso": return paso(Double.parseDouble(a[1]), Double.parseDouble(a[2]), Double.parseDouble(a[3]), Double.parseDouble(a[4]), Double.parseDouble(a[5]));
            default: return "comando?";
        }
    }
}
