import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/**
 * Bcv -- vision + toques para el combate de Day R. UNA captura -> todo el analisis (en vez de cientos de lecturas de pixel en sh).
 * Uso:  app_process /system/bin Bcv <comando> [args]        (RAW=archivo.raw para probar fuera del celular, sin toques)
 *   ver [2]                  estado completo (K=V). "2" = confirma el turno con una 2a captura 250 ms despues.
 *   tap X Y [@ms] X Y ...    toques (@ms = pausa)
 *   invocar [dir|auto]       selecciona al aliado (slot 3), elige casilla hacia dir/enemigos, doble toque, comprueba que aparece
 *   mover [dir|auto]         selecciona Mover, elige casilla verde hacia dir (auto = hacia el enemigo mas cercano), doble toque
 *   atacar [max]             selecciona el rifle y dispara a los enemigos en rango mientras haya puntos y usos (cambia de blanco solo)
 *   hab3                     turno del muneco: habilidad 3 al mejor enemigo (si no esta en enfriamiento)
 *   pasar ANTES              pulsa Terminar turno y espera a que el turno cambie (3 intentos)
 */
public class Bcv {
    static byte[] d; static int W, H;
    static final String DIR = "/data/local/tmp/";
    static String RAW = System.getenv("RAW");
    static int N = 8;
    static int jc = -1, jr = -1;
    // slots de la columna izquierda (px reales)
    static final int Y_MOVER = 347;
    // Filas de la columna izquierda (1,2,3 = y 499,688,871). El ORDEN de las armas cambia segun la batalla: se lee en la pantalla de armas (archivo 'slots').
    static int MROW = 0, PROW = 0, RROW = 0;   // 0 = desconocido (NO se asume ningun orden)
    static int Y_ARMA1 = 499, Y_RIFLE = 688, Y_ALIADO = 871; static final int Y_H3 = 871;
    static int rowY(int r) { return r == 1 ? 499 : r == 2 ? 688 : 871; }
    static boolean slotsOk() { return MROW >= 1 && PROW >= 1 && RROW >= 1 && MROW != PROW && PROW != RROW && MROW != RROW; }
    static void aplicaSlots() {
        if (!slotsOk()) return;
        Y_ARMA1 = rowY(PROW); Y_RIFLE = rowY(RROW); Y_ALIADO = rowY(MROW);   // Y_H3 NO depende del orden de armas: en el turno del muneco SU panel muestra sus habilidades y la 3a esta siempre abajo (871)
    }
    // Plantillas 25x25 (gris) del icono de cada arma, clara y oscurecida: M(muneco) P(pistola) R(rifle). El ORDEN de las armas cambia en cada batalla.
    static final String TPL_B64 = "l5mbnZuhn6KioKKjoqKhn6Ghn52enZqYl52ho6WlpKWkqKmoqKempqino6alpKKeoJ2bn6CfoqOlpKampqSknp+jo6KioaCen56dnKCio6SjpaGkp6ampqCblqWloqOho6Khnqmsra2tr7Csq7GysrKwob6dsK6traytrKqkp6epp6mpqKeoqqurnLqRjqqof4eSoaaln6GipaOjpKOkpaWln6i8TaCbWYy8uIuboKKkpaelpqWlp6empZm+mD6IQqHEwrKLgqSsr6+wsLGxsrKysrGXxFglQLTJxK6YenWsoKGko6Sjo6SkpKSiq7grKBqsvqyOdm9snZ+hoqGioqKjoqKioKy4OygvQZSIcm93cp6nqamqq6mqq6urq5qnrS0jTHc+Z2xwfIOkp6iqq6usq6ysrKpujGYqHB2JS0dugHqipJ6foKKhoqKjo6Bbbp6JKhsUJo41coOXnpudoKCgoaGioomDjpaDXEUdFBU/L2Cgn56aqqyrrK2uooKiwptHZEJOMBgVFxdKrKuppqChoqKaX3ivx4o9JDBVNTk7GB9loKSin5yanJ5/NymExHAxdqQqOExKaWKTn5+fnJuZnqCDWTSSvWBXNHyZJid9Pp6koqKioqCenKVnXoy+q1ElgEodKCsjK1OtrKurq6qop6RWV3mwf0s6QDo1PTYmKJCfn5+fnqCdm5qZm46mYkQ7Qzo5MkVAQ4efoaGgn56enZyamc6xZUA8QT88SEVAW5uqqqurqaiop6empKKU1II/STs7QjxITKCmpqempqampqalpKKgZ6HbYzQzQ0RFZZ6fn5+fn5+fn5+enp6cmnR0dHR0dHR0dHR0dHR0dHR0dHR0dHR0dHR1dXR1dXV0dXV0dHR0dHR1dXV1dXV0dHR0cHBwcHBwcHBwcHBwb29vb29vb29vb29wcG9vb29vb29vb29vb29vbGZvb29vb29vb292dnZ2dnZ2dnZ2dnZ2dWt8aHZ2dnR1dnZ1cXFxcXFxcXFxcXFwcGZ5X15wcFRYYWxxcW1tbW1tbW1tbW1tbWluezNqZztce3hcaW1vb29vb29vb29vb29lfWMpWyxqgH91W1dvdXV1dXV1dXV1dXV1Y4A5GCp2hIByY1BNdWxsbGxsbGxsbGxsa3B4HBoRcHxxXU1JSGxra2tra2tra2tra2lxeCcaHypgWUtJTUxrcXFxcXFxcXFxcXFmbXEeFzJOKENGSVFXcXJycnJycnJycnJwSFxDGxMTWjEuSFNQbnJra2tra2tra2tpPEhnWhwRDRldI0pXZmtrbGxsbGxsbGtaVl1iVjwtEw0OKR9AbGxsbHR0dHR0dGtVan9lL0ErMyAQDg8PMXR0dHRtbW1tZz9PcoJaKBcgOCMlJhAUQ2xubW5tampqVSQbVoBJIE1rHCQyMUVBYmpqampqam1tWDoiYHw/OSJSZBkaUylpbW1tbW1tbW1yRj5bfHA1GFQxExocFxw3dHR0dHR0dHRzOjlPc1MxJiomIygjGRpgampqampqamprbGZdbEAtJywmJSAtKixaa2tra2tra2tra2uHdEIqJyopJy8tKjxpcnJycnJycnJycnJyYYtVKTAmJysnLzNscXFxcXFxcXFxcXFxcUZpj0EiIiwsLURsbW1tbW1tbW1tbW1tbW2srK2tra6trq6urq6urq6trq6tra2trKysrK2urq6ura2urq6urq2trq6trq6uraytrKioqaipqampqampqainp6ioqKinqKenqKinqKioqaipqKipqKiop6enqKinqKioqKiosbKysrGys7Gws7S0tLOxsbOysrKysbKysampqaqpqqmpqamqqqqqqKipqampqamKi6ilpaWmpaWlpaWmpqampqalpqalpZ9UW1qcqKmoqampqampqaioqampqKipqJitkSAup7CwsLCwsbGxsbGxn66XaJSlrqJ2mXgkgK+jpKSkpKSkpaSjYDBMZVRMNWqCbX2OUqOko6Ojo6OYjYqTk1RBWV+pXTI+nKOkpKOjo6ytjnZMN09YbrqdfklEZWwrV62tra2trKyjUjhKTWFTjs27yqRTMSpoHIOqqampqaqpijI0UV9zipHLz5JiOyEdVGSioqKioqKioqOKUIh2dUl6kYRWUkgZHYyjo6OkpKOjo6OvhGK92HhsV11FWUs8OYKwrq+wsK+wr6+umHtk1b2OV0AuP0c0YX2RpaSkpKSkpaSkpKOZM19ONzU6Q0dFS5qHj6GhoqKioqKioaFjTEEuV1VISE5SdnuIdaOnp6enp6enp6amhJilq4w0IkheaqWXgaGtra2tra2tra2sq6GhoaGhm2paqI+hoaGhoaGioaKioqKioaKioqKio5eQVpFpoqOio6Ojo6Ojo6OjoqChrK2trq2Xf3Vhe66urq6urq6ura6smJeenKioqampom6AW42pqampqampqamokaBqTFKkpKSkpJhpdEt2paWlpaWlpaWlkqB0g31TdnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dXBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBvb29vcHBwb29vb29vb29vb29vb29vb29vdHR0dHR0dHR0dHR0dHR0dHR0dHR0dHR0dHNzc3Nzc3Nzc3Nzc3Nzc3NzcnNzc3NwYnNtbW1tbW1tbW1tbW1tbW1tbW1tbWxNPk9obm5ubm5ubm5ubm5ubm5ubm5ubmlkUR4aa3V1dXV1dXV1dXV1dXVwXnF1dW9UfFYUQnVtbW1tbW1tbW1tVSw/QCw0NlxfPkNQKGpta2tra2tra2tqYyknPEVmOSAnW2praWFra29vbFxFNC41P25lPTMyVEUgK29vb29vb29zUi01LDpCTH13eHM0JCJJFFV0dHR0dHR0WB4kL0BDUGSFjnBLLRkVMShla2tra2tra2pFK0lBTTlUampDMC8SEVNra2tra2tra2tyYURxgFBIQkk3PDMpFTxycnJycnJycnJybElDlYdgOTIhIzArLUpjcHBwcHBwcG9wb2NoJ1lKLiAhKC0pLGVZXWpqa2tra2pqampYOiEaJSs2LzMyQEpZTGVsbGxsbGxsbGxsQ1VdYFEhGCo1QGhcU2N0dHR0dHR0dHR0dGxsbGxsXjA7ZF5rZ2VsbGxsbGxsbGxsbGxqampqamFhNmdGampramtra2tra2tra2trcHBwcHBlVUdHTHBwcHBwcHBwcHBwa1tgXnNzc3NzZ0tTQGRzc3Nzc3Nzc3NzZmheR0psbGxsbGtFTy5JbGxsbW1tbW1tZWZALT0jbW1tbW1HOT9DVW1tbW1tbW1tbVlVcZhGWq6vr7Cvr6+vsLCwsLCwsLCwr7CwsK+urq2oqampqaqqqaqqqqmpqKioqKioqKioqKmpp6ioqKioqaioqKioqKemp6eop6inqKenqK6vr6+vr7CurrCwsLCvrq6wr6+vr66vrq6tra2ura6uraytrq6urqysrq6tlKqsqqWjpaWlpqalpqampqampqalpaalnJ2QuKKXpqampqempqampqampaanpqaknLpcmMeipqawsbGysbKysrKysrKysbKslp1Gr62xsrGwpqampqempqenp6enp6WcgnRzhLWmpqampqOjo6Oko6Oko6KjpKSWeV6IeZSjpKOjo6OoqKioqaeoqKmoqKigpFRSUnWpqampqKiora6ur6+vrq+vsK6WoGqVpoerr62urq6uraOjo6Ojo5qao6CSWWOio6Ojo6Ojo6Kio6OioqKiopKpon52jXaioqKioqKioqKioqKirK2srJSpeqFuRoSsra2traysraytraysq6ipqJR3SHJsWm6pqamoqamoqKmpqampqKehoZlwV1BfUG+ioqGioaGioqKioqKioqGho6OHhlduO36jo6Ojo6Oko6Ojo6Ojo6Ojo66vjFeES3uvr6+vr6+vr6+vr66vr6+vrq2ji09mTIaFo6Ojo6Ojo6Ojo6Ojo6Ojo6OjoIo+NXJ5kaKioqKioqKioqOioqKioqKiom8/OS13lqmpqampqampqaqqqqmpqampqKk6LzowgJeurq2trq6tra6urq6trq6traysLDc8QJakpKSkpKSkpKWlpaWlpaWkpKSkpClDRH+jpaSlpKWlpaWlpaWlpaWlpaWlpaV0dHR0dHR0dHR0dHR0dHR0dHR0dHR0dHR0dHR0dHR0dHR0dHR0dHR0dHR0dHR0dHR0dHBwcHBwcHBwcHBwcG9vb29vb29vb29vb3Bvb29vb29vb29vb29vb29vb29vb29vb29wdnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnBwcHBwcHBwcHBwcHBwcHBwcG1ZbHBqZWptbW1tbW1tbW1tbW1tbW1tbWlxYFl/aGltb29vb29vb29vb29vb29vb2hwYUNve25vb3V1dXV1dXV1dXV1dXV1dWllTzN/b3V1dXRsbGxsbGxsbGxsbGxsbFhUQVdeb2xsbGxsa2tra2tra2tra2tramBVPlZJZGtra2tra3JycnJycnJycnJycG1jMD42W3JycnJycnJwcHBwcHBwb3BwblpVUHBwZ3BwcHBwcHBwa2tra2trYmVrYmMwUmtra2tra2tra2tra2xsbGxqYHBrTkVLYGxsbGxsbGxsbGxsbGx1dXV1XF9FYkAsa3V1dXV1dXV1dXV1dXV0bG1sXkAvUEE1WGxsbWxsbW1tbW1tbWxtbGpqYUJDNzQxXWpqampqampqamtqampqampubl1PPkUtZ25ubm5ubm5ubm5ubm5ubm5uc3NPQUw5V3Nzc3Nzc3Nzc3Nzc3Nzc3NzcmpaMDY6WFhqampqampqampqampqampqamtlRyUhSlhma2tra2tra2tra2tra2tra2tsOyQkHVRic3Nzc3Nzc3Nzc3Nzc3Nzc3NzcyEhJx5UZnBwcHBwcHBwcHBwcHBwcHBwcHAeKCM7ZmxsbGxtbW1tbW1tbW1tbW1tbW1t";
    static double[][] TPL = null;
    static double[] zs(double[] a) { double m = 0; for (double v : a) m += v; m /= a.length; double s = 0; for (double v : a) s += (v - m) * (v - m); s = Math.sqrt(s / a.length) + 1e-6; double[] o = new double[a.length]; for (int i = 0; i < a.length; i++) o[i] = (a[i] - m) / s; return o; }
    static double[] featFila(int r) {
        int y = rowY(r); double[] f = new double[625];
        for (int by = 0; by < 25; by++) for (int bx = 0; bx < 25; bx++) {
            double acc = 0; for (int dy = 0; dy < 4; dy++) for (int dx = 0; dx < 4; dx++) { int[] p = px(160 + bx * 4 + dx, y - 60 + by * 4 + dy); acc += (p[0] * 299 + p[1] * 587 + p[2] * 114) / 1000.0; }
            f[by * 25 + bx] = acc / 16.0;
        }
        return zs(f);
    }
    /** Reconoce por el ICONO que arma hay en cada fila (muneco/pistola/rifle). true si es seguro (margen alto); si no, deja lo anterior. */
    static boolean detectaSlotsIconos() {
        try {
            if (TPL == null) { byte[] b = java.util.Base64.getDecoder().decode(TPL_B64); TPL = new double[6][625]; for (int t = 0; t < 6; t++) { double[] a = new double[625]; for (int i = 0; i < 625; i++) a[i] = b[t * 625 + i] & 0xff; TPL[t] = zs(a); } }
            double[][] sc = new double[3][3];
            for (int r = 1; r <= 3; r++) { double[] f = featFila(r); for (int k = 0; k < 3; k++) { double m = -9; for (int v = 0; v < 2; v++) { double[] t = TPL[k * 2 + v]; double c = 0; for (int i = 0; i < 625; i++) c += f[i] * t[i]; c /= 625; if (c > m) m = c; } sc[r - 1][k] = m; } }
            int[][] perms = {{0,1,2},{0,2,1},{1,0,2},{1,2,0},{2,0,1},{2,1,0}};
            double best = -99, second = -99; int[] bp = null;
            for (int[] p : perms) { double sm = sc[0][p[0]] + sc[1][p[1]] + sc[2][p[2]]; if (sm > best) { second = best; best = sm; bp = p; } else if (sm > second) second = sm; }
            double minSc = Math.min(sc[0][bp[0]], Math.min(sc[1][bp[1]], sc[2][bp[2]]));
            if (best - second < 1.0 || minSc < 0.35) { log("slots por icono: NO seguro (margen " + String.format("%.2f", best - second) + ", min " + String.format("%.2f", minSc) + ")"); return false; }
            int m = 0, pp = 0, rr = 0; for (int r = 0; r < 3; r++) { if (bp[r] == 0) m = r + 1; else if (bp[r] == 1) pp = r + 1; else rr = r + 1; }
            if (m != MROW || pp != PROW || rr != RROW) log("slots por icono: muneco=" + m + " pistola=" + pp + " rifle=" + rr + " (margen " + String.format("%.2f", best - second) + ")");
            MROW = m; PROW = pp; RROW = rr; aplicaSlots(); escribir("slots", m + " " + pp + " " + rr);
            return true;
        } catch (Exception e) { log("slots por icono: error " + e); return false; }
    }

    // ---------------- captura ----------------
    static final byte[] BUF = new byte[16 + 2436 * 1080 * 4];
    static void cap() throws Exception {
        if (RAW != null) d = java.nio.file.Files.readAllBytes(new File(RAW).toPath());
        else {
            Process p = Runtime.getRuntime().exec(new String[]{"screencap"});
            InputStream in = p.getInputStream();
            int off = 0, n;
            while (off < BUF.length && (n = in.read(BUF, off, BUF.length - off)) > 0) off += n;
            byte[] resto = new byte[1 << 16]; while (in.read(resto) > 0) { }   // por si la pantalla fuera mayor
            p.waitFor(); d = BUF;
        }
        W = le(0); H = le(4);
    }
    static int le(int o) { return (d[o] & 255) | ((d[o + 1] & 255) << 8) | ((d[o + 2] & 255) << 16) | ((d[o + 3] & 255) << 24); }
    static boolean ok() { return W == 2436 && H == 1080 && d.length >= 16 + 2436 * 1080 * 4; }
    static int[] px(int x, int y) {
        if (x < 0) x = 0; if (y < 0) y = 0; if (x >= W) x = W - 1; if (y >= H) y = H - 1;
        int o = 16 + (y * W + x) * 4; return new int[]{d[o] & 255, d[o + 1] & 255, d[o + 2] & 255};
    }
    static boolean in(int v, int a, int b) { return v >= a && v <= b; }

    // ---------------- detectores de pantalla (portados de bc_det.sh) ----------------
    static boolean verde(int x, int y) { int[] p = px(x, y); return p[0] <= 30 && in(p[1], 45, 90) && p[2] <= 30; }
    static boolean crema(int x, int y) { int[] p = px(x, y); return p[0] >= 215 && p[1] >= 165 && p[2] >= 105; }
    static boolean oscuro(int x, int y) { int[] p = px(x, y); return p[0] + p[1] + p[2] < 110; }
    static boolean rango(int x, int y, int r0, int r1, int g0, int g1, int b0, int b1) { int[] p = px(x, y); return in(p[0], r0, r1) && in(p[1], g0, g1) && in(p[2], b0, b1); }
    static boolean victoria() { return verde(719, 365) && verde(1720, 365) && crema(923, 843); }
    static boolean mision() { return verde(814, 487) && verde(1622, 487) && crema(1011, 812); }
    static boolean perg() {
        return oscuro(1200, 45) && oscuro(1600, 45) && rango(1909, 646, 143, 158, 114, 125, 77, 92) && rango(1930, 196, 171, 186, 147, 158, 104, 120);
    }
    static boolean armas() {
        int k = 0; int[][] ps = {{974, 730}, {1827, 816}};
        for (int[] q : ps) { int[] p = px(q[0], q[1]); if (p[0] <= 50 && p[1] <= 45 && p[2] <= 30) k++; }
        int[] v = px(1848, 879);
        return k >= 2 && in(v[0], 130, 175) && in(v[1], 130, 175) && in(v[2], 50, 100);
    }
    static boolean enCombate() {
        int k = 0; int[] v = px(2113, 1035);
        if (in(v[0], 165, 195) && in(v[1], 165, 195) && in(v[2], 60, 100)) k++;
        v = px(305, 100); if (v[0] >= 170 && v[1] >= 235 && v[2] <= 60) k++;
        v = px(300, 155);
        if ((v[2] >= 230 && v[0] <= 140) || (in(v[0], 110, 145) && in(v[1], 110, 145) && in(v[2], 110, 145))) k++;
        return k >= 3;
    }
    /** MIO | MUNECO | OTRO | NO_COMBATE */
    static String turno() {
        if (!enCombate()) return "NO_COMBATE";
        int n = 0; int[][] ps = {{183, 146}, {207, 116}};
        for (int[] q : ps) { int[] p = px(q[0], q[1]); if (p[1] >= 215 && p[0] <= 150 && p[2] <= 110) n++; }
        if (n >= 1) return "MUNECO";
        int[] p = px(207, 116);
        return (p[0] >= 170 && p[1] >= 150 && p[2] >= 110) ? "MIO" : "OTRO";
    }
    /** icono del muneco (slot 3) a color = LIBRE (se puede invocar); gris = VIVO */
    static boolean aliadoIcono() {
        int g = 0; int[][] ps = {{152, Y_ALIADO - 61}, {262, Y_ALIADO + 64}};
        for (int[] q : ps) { int[] p = px(q[0], q[1]); if (Math.abs(p[0] - p[1]) <= 8 && Math.abs(p[1] - p[2]) <= 8 && p[0] >= 90) g++; }   // gris (con o sin la capa oscura del turno enemigo); a color tiene R-G>=20
        return g >= 1;
    }
    /** Muneco en juego = icono del slot 3 en gris (tambien bajo la capa oscura del turno enemigo). */
    static boolean aliadoVivo() { return aliadoIcono(); }   // solo el icono: una ficha verde puede ser OTRO aliado (el Mago del evento)
    static boolean rifleListo() { int[] p = px(152, Y_RIFLE - 55); return p[0] >= 190 && p[0] - p[2] >= 60; }
    static boolean h3Enfria() {
        int k = 0;
        for (int x = 295; x <= 335; x += 10) for (int y = Y_H3 - 66; y <= Y_H3 - 26; y += 10) { int[] p = px(x, y); if (p[0] >= 190 && p[1] <= 70 && p[2] <= 70) k++; }
        return k >= 2;
    }
    static int puntos() {
        int n = 0; int[] xs = {172, 225, 277, 329, 382, 434};
        for (int x : xs) { int[] p = px(x, 216); if (p[0] >= 220 && in(p[1], 140, 200) && p[2] <= 80) n++; }
        return n;
    }
    static String hashPantalla() { // huella de la pantalla (sin barra de arriba ni la flechita de abajo), para detectar si un click avanzo
        java.util.zip.CRC32 c = new java.util.zip.CRC32();
        int a = 16 + 100 * W * 4, len = 800 * W * 4;
        c.update(d, a, Math.min(len, d.length - a));
        return Long.toHexString(c.getValue());
    }

    // ---------------- tablero ----------------
    static char clas(int x, int y) {
        int[] p = px(x, y); int R = p[0], G = p[1], B = p[2];
        if (G >= R - 10 && G >= 145 && B <= 110) return 'V';
        if (R >= 185 && in(G, 105, 150) && B <= 95) return 'N';
        if (R >= 185 && G >= 152 && B >= 98 && R - G <= 45) return 'A';
        return '.';
    }
    static int[] acuerdo(int n) {
        int ok = 0, tot = 0;
        for (int r = 0; r < n; r++) for (int c = 0; c < n; c++) {
            char a = clas(709 + 1018 * c / n + 14, 30 + 1018 * r / n + 14);
            char b = clas(709 + 1018 * (c + 1) / n - 14, 30 + 1018 * (r + 1) / n - 14);
            if (a != '.' || b != '.') { tot++; if (a == b) ok++; }
        }
        return new int[]{ok, tot};
    }
    /** N del tablero a partir de las casillas iluminadas (si hay suficientes). Devuelve 0 si no se puede. */
    static int detectaN() {
        int best = 0, bestr = 0; int[] orden = {7, 8, 6, 9};
        for (int n : orden) {
            int[] a = acuerdo(n);
            if (a[1] >= 6 && a[0] * 100 / a[1] >= 60 && a[0] * 100 / a[1] > bestr) { bestr = a[0] * 100 / a[1]; best = n; }
            if (best != 0 && bestr >= 85) break;
        }
        return best;
    }
    static void log(String m) {
        System.out.println("# " + m);
        if (RAW != null) return;
        try (FileWriter w = new FileWriter(DIR + "bc.log", true)) { w.write(new java.text.SimpleDateFormat("HH:mm:ss.S").format(new Date()) + " J " + m + "\n"); } catch (Exception e) { }
    }
    static String leer(String f) { try { return new String(java.nio.file.Files.readAllBytes(new File(DIR + f).toPath())).trim(); } catch (Exception e) { return null; } }
    static void escribir(String f, String s) { if (RAW != null) return; try (FileWriter w = new FileWriter(DIR + f)) { w.write(s + "\n"); } catch (Exception e) { } }
    static void cargaEstado() {
        String s = leer("n_tablero"); if (s != null) try { N = Integer.parseInt(s); } catch (Exception e) { }
        if (System.getenv("N") != null) N = Integer.parseInt(System.getenv("N"));
        if (!detectaSlotsIconos() && !slotsOk()) { s = leer("slots"); if (s != null) try { String[] t = s.trim().split("\\s+"); if (t.length == 3) { MROW = Integer.parseInt(t[0]); PROW = Integer.parseInt(t[1]); RROW = Integer.parseInt(t[2]); } } catch (Exception e) { } }
        aplicaSlots();
        s = leer("jug_pos"); if (s != null) { String[] t = s.split("\\s+"); if (t.length == 2) try { jc = Integer.parseInt(t[0]); jr = Integer.parseInt(t[1]); } catch (Exception e) { } }
    }
    static void actualizaN() { if (System.getenv("N") != null) return; int n = detectaN(); if (n != 0 && n != N) { N = n; } if (n != 0) escribir("n_tablero", "" + N); }

    static class Tok { int c, r, x, y; boolean e; long dist; }
    static List<Tok> enemigos = new ArrayList<>();
    static List<Tok> otrosAliados = new ArrayList<>();
    static List<Tok> sinAro = new ArrayList<>();   // fichas oscuras sin aro: cajas/objetos   // fichas con aro verde (aliados): no se les dispara ni se persigue
    /** Aro de enemigo: la ficha de un enemigo lleva aro AZUL (escudo) y/o VERDE (vida) en la corona r=38..64. Una caja/barril en casilla naranja NO tiene ninguno: no es enemigo. */
    static boolean tieneAroEnemigo(int cx, int cy) {
        int g = 0, b = 0;
        for (int rad = 38; rad < 66; rad += 2) for (int ang = 0; ang < 360; ang += 15) {
            int x = cx + (int) Math.round(rad * Math.cos(Math.toRadians(ang))), y = cy + (int) Math.round(rad * Math.sin(Math.toRadians(ang)));
            int[] p = px(x, y);
            if (p[1] > p[0] + 12 && p[1] > p[2] + 30 && p[1] >= 40) g++;   // verde oliva de la barra de vida
            if (p[2] > p[0] + 35 && p[2] > p[1] + 15) b++;
        }
        return b >= 8 || g >= 6;
    }
    /** Fondo negro de la ficha (anillos r=46,50,54): >=78% oscuro. Objetos (rocas, cajas, manchas) quedan <=70%. */
    static boolean discoNegro(int cx, int cy) {
        int n = 0, d = 0;
        for (int rad = 46; rad <= 54; rad += 4) for (int ang = 0; ang < 360; ang += 12) {
            int[] p = px(cx + (int) Math.round(rad * Math.cos(Math.toRadians(ang))), cy + (int) Math.round(rad * Math.sin(Math.toRadians(ang))));
            n++; if (p[0] + p[1] + p[2] < 165) d++;
        }
        return d * 100 >= n * 78;
    }
    /** Interior verde (aliado): cuenta pixeles muy verdes (g>r+40 y g>b+40) en el disco r<=33 de la ficha. El aro verde de los enemigos es solo su barra de vida: NO cuenta. */
    static boolean interiorVerde(int cx, int cy) {
        int g = 0;
        for (int rad = 0; rad < 34; rad += 3) for (int ang = 0; ang < 360; ang += 30) {
            int x = cx + (int) Math.round(rad * Math.cos(Math.toRadians(ang))), y = cy + (int) Math.round(rad * Math.sin(Math.toRadians(ang)));
            int[] p = px(x, y);
            if (p[1] > p[0] + 40 && p[1] > p[2] + 40) g++;
        }
        return g >= 15;
    }
    static java.util.Set<String> lastMalos = new java.util.HashSet<>();   // blancos descartados (sin linea de tiro) del ultimo atacar
    static int ac = -1, ar = -1; // posicion del aliado (cara verde)
    static boolean aroEnJug = false;   // hay aro naranja tambien en el jugador (si el perfil dice MIO y ambos tienen aro, es turno MIO)
    static boolean escribeJug = true, aroEnAliado = false, aroEsEnemigo = false; // el aro naranja marca al personaje ACTIVO

    static boolean osc(int x, int y) { int[] p = px(x, y); return p[0] + p[1] + p[2] < 150 || (p[2] - p[0] >= 35 && p[0] < 90 && p[2] < 150); }   // oscuro o aro azul marino (enemigos tipo General)
    static boolean ora(int x, int y) { int[] p = px(x, y); return p[0] >= 170 && p[1] >= 90 && p[2] <= 50; }
    static boolean ora2(int x, int y) { int[] p = px(x, y); return p[0] >= 95 && p[2] <= 70 && p[0] - p[2] >= 80 && in(p[1], 55, 170); }   // aro naranja con sombra/oscurecido
    static boolean lit(int x, int y) {
        int[] p = px(x, y);
        return p[0] >= 185 && ((in(p[1], 105, 150) && p[2] <= 95) || (p[1] >= 152 && p[2] >= 98 && p[0] - p[1] <= 45));
    }
    /** Analiza fichas: jugador (aro naranja), aliado (cara verde), enemigos (ordenados: en rango primero, luego mas cerca del jugador). */
    static void analiza() {
        enemigos.clear(); ac = -1; ar = -1; aroEnAliado = false; aroEnJug = false; otrosAliados.clear(); sinAro.clear();
        List<int[]> fichas = new ArrayList<>();
        int pc = -1, pr = -1;
        for (int r = 0; r < N; r++) for (int c = 0; c < N; c++) {
            int cx = 709 + 1018 * (2 * c + 1) / (2 * N), cy = 30 + 1018 * (2 * r + 1) / (2 * N);
            int a = 1018 * 355 / (N * 1000), a7 = a * 7 / 10, b = 1018 * 455 / (N * 1000);
            int dd = 0; int[][] ej = {{a, 0}, {-a, 0}, {0, a}, {0, -a}};
            for (int[] q : ej) if (osc(cx + q[0], cy + q[1])) dd++;
            if (dd >= 1) { int[][] dg = {{a7, a7}, {-a7, a7}, {a7, -a7}, {-a7, -a7}}; for (int[] q : dg) if (osc(cx + q[0], cy + q[1])) dd++; }
            if (dd < 3) continue;
            int o = 0, b7 = b * 7 / 10; int[][] op = {{b, 0}, {-b, 0}, {0, b}, {0, -b}, {b7, b7}, {-b7, -b7}};
            int o2 = 0; for (int[] q : op) { if (ora(cx + q[0], cy + q[1])) o++; if (ora2(cx + q[0], cy + q[1])) o2++; }
            boolean aro = o >= 2 || o2 >= 2;
            if (dd < 5 && !aro && !tieneAroEnemigo(cx, cy)) continue;   // ficha clara (cara blanca, p. ej. Chupacabras): vale si lleva aro azul/verde   // ficha: >=5 de 8 puntos oscuros; el jugador (aro naranja) basta con >=3 (el icono de radiacion le tapa el anillo)
            int g = 0, u = 1018 * 140 / (N * 1000), v = 1018 * 195 / (N * 1000);
            int[][] gp = {{-u, -(u * 55 / 100)}, {0, -v}, {u, -(u * 55 / 100)}};
            for (int[] q : gp) { int[] p = px(cx + q[0], cy + q[1]); if (p[1] > p[0] + 60 && p[1] > p[2] + 100) g++; }
            if (g >= 1) { ac = c; ar = r; if (aro) aroEnAliado = true; }
            else if (aro) {
                if (aroEsEnemigo && jc >= 0) { if (c != jc || r != jr) fichas.add(new int[]{c, r, cx, cy}); }   // aro = blanco seleccionado (o enemigo activo): NO es el jugador
                else { pc = c; pr = r; aroEnJug = true; }
            }
            else fichas.add(new int[]{c, r, cx, cy});
        }
        if (pc >= 0 && escribeJug) { jc = pc; jr = pr; escribir("jug_pos", jc + " " + jr); }
        for (int[] f : fichas) {
            int c = f[0], r = f[1];
            if (c == jc && r == jr) continue; // jugador (posicion guardada)
            Tok t = new Tok(); t.c = c; t.r = r; t.x = f[2]; t.y = f[3];
            if (interiorVerde(t.x, t.y)) { otrosAliados.add(t); continue; }
            if (!tieneAroEnemigo(t.x, t.y) && !(discoNegro(t.x, t.y) && !(t.c == jc && t.r == jr))) { sinAro.add(t); continue; }   // v23: barra casi vacia/tapada por debuffs -> vale el fondo negro de la ficha (salvo la casilla del jugador)   // sin aro azul/verde = objeto (caja, barril...), NO enemigo aunque la casilla este naranja   // interior VERDE = aliado (granjero/mago...): no es un blanco
            t.e = lit(709 + 1018 * (c + 1) / N - 14, 30 + 1018 * (r + 1) / N - 14);
            t.dist = jc < 0 ? 0 : (long) (c - jc) * (c - jc) + (long) (r - jr) * (r - jr);
            enemigos.add(t);
        }
        Collections.sort(enemigos, (p, q) -> { if (p.e != q.e) return p.e ? -1 : 1; if (p.dist != q.dist) return Long.compare(p.dist, q.dist); if (p.c != q.c) return p.c - q.c; return p.r - q.r; });
    }
    static String listaEnemigos() {
        StringBuilder sb = new StringBuilder();
        for (Tok t : enemigos) sb.append(t.c).append(',').append(t.r).append(',').append(t.x).append(',').append(t.y).append(',').append(t.e ? 1 : 0).append(' ');
        return sb.toString().trim();
    }
    static String pant() {
        if (!ok()) return "VERTICAL";
        if (victoria()) return "VICTORIA";
        if (mision()) return "MISION";
        if (perg()) return "PERGAMINO";
        if (armas()) return "ARMAS";
        if (enCombate()) return "COMBATE";
        return "OTRA";
    }

    // ---------------- toques (inyeccion igual que Zoom.java) ----------------
    static Object inputMgr; static Method inject; static Class<?> meC, ppC, pcC; static long down;
    static void initInput() throws Exception {
        if (inputMgr != null) return;
        meC = Class.forName("android.view.MotionEvent");
        ppC = Class.forName("android.view.MotionEvent$PointerProperties");
        pcC = Class.forName("android.view.MotionEvent$PointerCoords");
        Object binder = Class.forName("android.os.ServiceManager").getMethod("getService", String.class).invoke(null, "input");
        Class<?> stub = Class.forName("android.hardware.input.IInputManager$Stub");
        inputMgr = stub.getMethod("asInterface", Class.forName("android.os.IBinder")).invoke(null, binder);
        for (Method m : inputMgr.getClass().getMethods()) if (m.getName().equals("injectInputEvent") && m.getParameterTypes().length == 2) inject = m;
        if (inject == null) throw new RuntimeException("sin injectInputEvent");
    }
    static void send(int action, float x, float y) throws Exception {
        long now = (Long) Class.forName("android.os.SystemClock").getMethod("uptimeMillis").invoke(null);
        Object pp = ppC.getDeclaredConstructor().newInstance(); ppC.getField("id").setInt(pp, 0); ppC.getField("toolType").setInt(pp, 1);
        Object pc = pcC.getDeclaredConstructor().newInstance();
        pcC.getField("x").setFloat(pc, x); pcC.getField("y").setFloat(pc, y); pcC.getField("pressure").setFloat(pc, 1f); pcC.getField("size").setFloat(pc, 1f);
        Object ap = Array.newInstance(ppC, 1); Array.set(ap, 0, pp);
        Object ac2 = Array.newInstance(pcC, 1); Array.set(ac2, 0, pc);
        Method obtain = meC.getMethod("obtain", long.class, long.class, int.class, int.class, Array.newInstance(ppC, 0).getClass(), Array.newInstance(pcC, 0).getClass(),
                int.class, int.class, float.class, float.class, int.class, int.class, int.class, int.class);
        Object ev = obtain.invoke(null, down, now, action, 1, ap, ac2, 0, 0, 1f, 1f, 0, 0, 0x1002, 0);
        inject.invoke(inputMgr, ev, 0);
    }
    static void tap(int x, int y) throws Exception {
        if (RAW != null) { System.out.println("# tap " + x + " " + y); return; }
        initInput();
        down = (Long) Class.forName("android.os.SystemClock").getMethod("uptimeMillis").invoke(null);
        send(0, x, y); Thread.sleep(35); send(1, x, y);
    }
    static void doble(int x, int y) throws Exception { tap(x, y); Thread.sleep(180); tap(x, y); }
    static void pausa(int ms) throws Exception { if (RAW == null) Thread.sleep(ms); }

    // ---------------- comandos ----------------
    static String turnoSeguro() {   // perfil + aro naranja del tablero: si el perfil dice MIO pero el aro esta en el muneco, es del muneco
        String t = turno();
        if (t.equals("MIO")) { cargaEstado(); boolean w = escribeJug; escribeJug = false; analiza(); escribeJug = w; if (aroEnAliado && !aroEnJug) return "MUNECO"; }
        return t;
    }
    static void imprimeVer(boolean confirma) throws Exception {
        cap(); String p = pant();
        System.out.println("PANT=" + p);
        if (ok()) System.out.println("HASH=" + hashPantalla());
        if (p.equals("COMBATE")) {
            String t = turnoSeguro();
            if (confirma && (t.equals("MIO") || t.equals("MUNECO"))) { pausa(250); cap(); if (!turnoSeguro().equals(t)) t = "OTRO"; }
            cargaEstado(); actualizaN(); escribeJug = t.equals("MIO"); aroEsEnemigo = !t.equals("MIO"); analiza(); escribeJug = true; aroEsEnemigo = false;
            System.out.println("TURNO=" + t);
            System.out.println("AP=" + puntos());
            System.out.println("RIFLE=" + (rifleListo() ? 1 : 0));
            System.out.println("COSTE=" + costoRifle());
            System.out.println("SLOT=" + slotSeleccionado());
            System.out.println("CARTEL=" + (cartelImposible() ? 1 : 0));
            System.out.println("ALIADO=" + (aliadoVivo() ? "VIVO" : "LIBRE"));
            System.out.println("H3=" + (h3Enfria() ? "ENFRIA" : "LISTA"));
            System.out.println("N=" + N);
            System.out.println("JUG=" + (jc < 0 ? "?" : jc + "," + jr));
            System.out.println("ALI=" + (ac < 0 ? "?" : ac + "," + ar));
            System.out.println("ENE='" + listaEnemigos() + "'");
        }
    }

    static String dirAuto() {
        if (jc < 0 || enemigos.isEmpty()) return "derecha";
        double mc = 0, mr = 0; for (Tok t : enemigos) { mc += t.c; mr += t.r; } mc /= enemigos.size(); mr /= enemigos.size();
        double dx = mc - jc, dy = mr - jr;
        if (Math.abs(dx) >= Math.abs(dy)) return dx >= 0 ? "derecha" : "izquierda"; return dy >= 0 ? "abajo" : "arriba";
    }
    static String dirHaciaMasCercano() {
        if (jc < 0 || enemigos.isEmpty()) return null;
        Tok t = enemigos.get(0); int best = Integer.MAX_VALUE; // el mas cercano al jugador, sin importar el rango
        for (Tok q : enemigos) { int dd = (q.c - jc) * (q.c - jc) + (q.r - jr) * (q.r - jr); if (dd < best) { best = dd; t = q; } }
        int dc = t.c - jc, dr = t.r - jr;
        if (Math.abs(dc) >= Math.abs(dr)) return dc >= 0 ? "derecha" : "izquierda"; return dr >= 0 ? "abajo" : "arriba";
    }
    /** casilla de la clase cl ('N' aliado, 'V' mover) mas extrema hacia dir (empate: la mas cercana al centro, luego la primera). */
    static int[] eligeCasilla(char cl, String dir) {
        int best = -99999, br = -1, bc = -1;
        for (int r = 0; r < N; r++) for (int c = 0; c < N; c++) {
            if (clas(709 + 1018 * (c + 1) / N - 14, 30 + 1018 * (r + 1) / N - 14) != cl) continue;
            int dr = Math.abs(r - N / 2), dc = Math.abs(2 * c - N + 1), s;
            switch (dir) {
                case "izquierda": s = (N - 1 - c) * 100 - dr; break;
                case "arriba": s = (N - 1 - r) * 100 - dc; break;
                case "abajo": s = r * 100 - dc; break;
                default: s = c * 100 - dr;
            }
            if (s > best) { best = s; br = r; bc = c; }
        }
        return br < 0 ? null : new int[]{br, bc};
    }
    /** casilla de la clase cl que deja al jugador lo MAS CERCA posible de algun enemigo (distancia euclidiana al cuadrado entre casillas;
     *  empate: la mas cercana al enemigo mas proximo al jugador, luego la de menor recorrido). null si no hay casillas. */
    static int[] eligeCasillaCercaEnemigo(char cl) {
        if (enemigos.isEmpty()) return null;
        Tok mas = enemigos.get(0); long bm = Long.MAX_VALUE;
        if (jc >= 0) for (Tok q : enemigos) { long dd = (long) (q.c - jc) * (q.c - jc) + (long) (q.r - jr) * (q.r - jr); if (dd < bm) { bm = dd; mas = q; } }
        long best = Long.MAX_VALUE, best2 = Long.MAX_VALUE, best3 = Long.MAX_VALUE; int br = -1, bc = -1;
        for (int r = 0; r < N; r++) for (int c = 0; c < N; c++) {
            if (clas(709 + 1018 * (c + 1) / N - 14, 30 + 1018 * (r + 1) / N - 14) != cl) continue;
            long d1 = Long.MAX_VALUE; for (Tok q : enemigos) { long dd = (long) (q.c - c) * (q.c - c) + (long) (q.r - r) * (q.r - r); if (dd < d1) d1 = dd; }
            long d2 = (long) (mas.c - c) * (mas.c - c) + (long) (mas.r - r) * (mas.r - r);
            long d3 = jc < 0 ? 0 : (long) (jc - c) * (jc - c) + (long) (jr - r) * (jr - r);
            if (d1 < best || (d1 == best && (d2 < best2 || (d2 == best2 && d3 < best3)))) { best = d1; best2 = d2; best3 = d3; br = r; bc = c; }
        }
        if (br < 0) return null;
        if (jc >= 0) { long actual = Long.MAX_VALUE; for (Tok q : enemigos) { long dd = (long) (q.c - jc) * (q.c - jc) + (long) (q.r - jr) * (q.r - jr); if (dd < actual) actual = dd; }
            if (best >= actual) return new int[]{-1, -1}; }   // ninguna casilla acerca mas que la posicion actual
        return new int[]{br, bc};
    }
    static int tx(int c) { return 709 + 1018 * (2 * c + 1) / (2 * N); }
    static int ty(int r) { return 30 + 1018 * (2 * r + 1) / (2 * N); }

    static String invocar(String dir) throws Exception {
        cap(); if (!ok()) return "NO_HORIZONTAL";
        if (!turno().equals("MIO")) return "NO_ES_MI_TURNO";
        cargaEstado(); actualizaN(); analiza();
        if (dir == null || dir.equals("auto")) dir = dirAuto();
        for (int intento = 1; intento <= 3; intento++) {
            tap(300, Y_ALIADO); pausa(450);
            cap(); if (!ok()) return "NO_HORIZONTAL";
            if (slotSeleccionado() != MROW) {   // el muneco NO quedo seleccionado: no tocar el tablero (seria un disparo con otra arma)
                log("invocar: el slot del muneco (" + MROW + ") no quedo seleccionado (marco en " + slotSeleccionado() + "): reintento " + intento); pausa(400); continue;
            }
            actualizaN();
            int[] t = eligeCasilla('N', dir);
            if (t == null) { System.out.println("# invocar intento " + intento + ": SIN_CASILLAS"); continue; }
            doble(tx(t[1]), ty(t[0]));
            for (int w = 0; w < 6; w++) { pausa(250); cap(); if (!ok()) return "NO_HORIZONTAL"; if (aliadoVivo()) return "OK " + t[0] + "," + t[1] + " dir=" + dir + " N=" + N; }
            System.out.println("# invocar intento " + intento + ": no aparecio el aliado");
        }
        return "FALLA";
    }

    static String mover(String dir) throws Exception {
        cap(); if (!ok()) return "NO_HORIZONTAL";
        if (!turno().equals("MIO")) return "NO_ES_MI_TURNO";
        cargaEstado(); actualizaN(); analiza();
        boolean auto = dir == null || dir.equals("auto");
        if (auto) { dir = dirHaciaMasCercano(); if (dir == null) return "SIN_ENEMIGOS_O_JUGADOR"; }
        tap(300, Y_MOVER); pausa(450);
        cap(); if (!ok()) return "NO_HORIZONTAL";
        actualizaN();
        int[] t = auto ? eligeCasillaCercaEnemigo('V') : eligeCasilla('V', dir);
        if (t == null) return "SIN_CASILLAS";
        if (t[0] < 0) return "SIN_MEJORA";   // no hay casilla verde que me acerque mas al enemigo
        doble(tx(t[1]), ty(t[0])); pausa(300);
        jc = t[1]; jr = t[0]; escribir("jug_pos", jc + " " + jr);
        return "OK " + t[0] + "," + t[1] + (auto ? " (casilla mas cercana al enemigo)" : " dir=" + dir);
    }

    /** Coste en puntos de accion de un disparo del rifle = circulos naranja (reloj de arena) en su slot. Varia (1 o 2) segun la pelea. */
    static int costoRifle() {
        int n = 0; int[] xs = {308, 360, 413};
        for (int x : xs) { int[] p = px(x - 14, Y_RIFLE - 51); if (p[0] >= 220 && in(p[1], 140, 200) && p[2] <= 80) n++; }
        return Math.max(1, n);
    }
    /** Slot de la columna izquierda con marco naranja (seleccionado): 0 = Mover (o ninguno visible), 1 = pistola, 2 = rifle, 3 = muneco; -1 = no se ve ningun marco. */
    static int slotSeleccionado() {
        int y = 225; int encontrado = -1;
        while (y < 995) {
            int[] p = px(300, y);
            if (p[0] >= 200 && in(p[1], 90, 190) && p[2] <= 70) {
                int y0 = y; while (y < 995) { int[] q = px(300, y); if (q[0] >= 200 && in(q[1], 90, 190) && q[2] <= 70) y++; else break; }
                if (y - y0 <= 3) { encontrado = y0 < 405 ? 0 : y0 < 597 ? 1 : y0 < 783 ? 2 : 3; }   // linea fina = marco (los relojes de arena son trazos gruesos)
            } else y++;
        }
        return encontrado;
    }
    /** Toca un slot y COMPRUEBA que quedo seleccionado (hasta 3 intentos). Si ya lo esta, no lo toca (tocarlo de nuevo podria deseleccionarlo). */
    static boolean seleccionaSlot(int slot, int ySlot) throws Exception {
        cap(); if (!ok()) return false;
        if (slotSeleccionado() == slot) return true;
        for (int t = 0; t < 3; t++) {
            tap(300, ySlot); pausa(500);
            cap(); if (!ok()) return false;
            int s = slotSeleccionado();
            if (s == slot) return true;
            if (s < 0 && casillasIluminadas() >= 3) { log("sin marco visible pero hay casillas de rango iluminadas: doy por seleccionado el slot " + slot); return true; }
            log("slot " + slot + " no quedo seleccionado (marco en slot " + s + "): reintento " + (t + 1));
            pausa(400);
        }
        return false;
    }
    /** Numero de casillas del tablero con color de rango (verde/naranja/amarillo palido): >=3 indica que hay un arma/accion seleccionada. */
    static int casillasIluminadas() {
        int n = 0;
        for (int r = 0; r < N; r++) for (int c = 0; c < N; c++)
            if (clas(709 + 1018 * (c + 1) / N - 14, 30 + 1018 * (r + 1) / N - 14) != '.') n++;
        return n;
    }
    /** Cartel oscuro "Imposible atacar al objetivo" (franja horizontal en el centro del tablero): el blanco esta fuera de linea de tiro, aunque este a distancia. */
    static boolean cartelImposible() {
        int n = 0; int[] ys = {462, 466, 470, 508, 512, 516}; int[] xs = {800, 900, 1000, 1100, 1200, 1300, 1400, 1500, 1600};
        for (int y : ys) for (int x : xs) { int[] p = px(x, y); if (p[0] <= 95 && p[1] <= 80 && p[2] <= 60 && p[0] >= p[1] && p[1] >= p[2] - 5 && p[0] + p[1] + p[2] < 260) n++; }
        return n >= 30;
    }
    static boolean recienInvocado = false;
    static int dbgN = 0;
    static void guardaDebug(String nombre) {
        if (RAW != null) return;
        try (FileOutputStream fo = new FileOutputStream(DIR + nombre + "_" + (dbgN++ % 3 + 1) + ".raw")) { fo.write(d); } catch (Exception e) { }
    }
    static boolean hayFicha(int c, int r) { for (Tok q : enemigos) if (q.c == c && q.r == r) return true; return false; }

    /** Dispara con el rifle ENFOCADO en un solo blanco hasta que muera (su ficha desaparece), antes de pasar a otro. Rafagas de hasta 2 disparos (doble toque rapido sobre la MISMA casilla) entre capturas. */
    static String atacar(int max) throws Exception {
        if (recienInvocado) { pausa(600); recienInvocado = false; }   // solo tras invocar: deja que termine la animacion del muneco antes de tocar el slot
        if (!seleccionaSlot(RROW, Y_RIFLE)) { log("NO pude seleccionar el rifle"); guardaDebug("sinrifle"); return "NO_SELECCIONA_RIFLE 0"; }
        int shots = 0, ne = 0, fallback = 0, vacio = 0, resel = 0; boolean apOk = true, primera = true;
        java.util.Set<String> malos = new java.util.HashSet<>(); lastMalos = malos;
        int fc = -1, fr = -1, fx = 0, fy = 0; boolean probando = false;      // blanco fijado
        for (int it = 0; it < max + 12; it++) {
            if (!ok()) return "NO_HORIZONTAL " + shots;
            if (!turno().equals("MIO")) return "FIN_TURNO_AUTO " + shots;
            if (!rifleListo()) return "AGOTADO " + shots;
            { int sel = slotSeleccionado(); if (sel >= 1 && sel != RROW && resel < 3) { resel++; log("el arma seleccionada es el slot " + sel + " y no el rifle: reselecciono"); if (!seleccionaSlot(RROW, Y_RIFLE)) return "NO_SELECCIONA_RIFLE " + shots; } }
            int ap = puntos();
            if (primera && ap == 0) { pausa(500); cap(); ap = puntos(); if (ap == 0) apOk = false; }
            primera = false;
            int coste = costoRifle();
            if (apOk && ap < coste) return "SIN_PUNTOS " + shots + " ap=" + ap + " coste=" + coste;
            if (shots >= max) return "LIMITE " + shots;
            cargaEstado(); actualizaN(); escribeJug = false; aroEsEnemigo = true; analiza(); escribeJug = true; aroEsEnemigo = false;
            // ¿sigue vivo el blanco fijado?
            if (fc >= 0 && !hayFicha(fc, fr)) {   // v24: no darlo por muerto por una sola captura (destello/dano en animacion): espera y recaptura
                pausa(500); cap(); cargaEstado(); actualizaN(); escribeJug = false; aroEsEnemigo = true; analiza(); escribeJug = true; aroEsEnemigo = false;
                if (hayFicha(fc, fr)) log("blanco " + fc + "," + fr + " reaparece tras recaptura (no estaba muerto)");
            }
            if (fc >= 0 && !hayFicha(fc, fr)) { log("blanco " + fc + "," + fr + " ELIMINADO tras " + shots + " disparos"); fc = -1; ne = 0; }
            if (fc < 0) {
                Tok obj = null; probando = false;
                for (Tok q : enemigos) if (q.e && !malos.contains(q.c + "," + q.r)) { obj = q; break; }
                if (obj == null) {
                    if (enemigos.isEmpty() && vacio < 2) { vacio++; log("lista de enemigos vacia: releo (" + vacio + "/2)"); pausa(400); cap(); continue; }
                    if (fallback < 3) {   // la deteccion de rango pudo fallar: pruebo con el mas cercano no probado
                        for (Tok q : enemigos) if (!malos.contains(q.c + "," + q.r)) { obj = q; break; }
                        if (obj != null) { fallback++; probando = true; log("sin blanco marcado en rango: pruebo al mas cercano " + obj.c + "," + obj.r); }
                    }
                }
                if (obj == null) { log("SIN_BLANCO ap=" + ap + " coste=" + coste + " enemigos(col,fila,x,y,rango)=" + listaEnemigos() + " malos=" + malos); guardaDebug("sinblanco"); return "SIN_BLANCO " + shots + " ap=" + ap; }
                fc = obj.c; fr = obj.r; fx = obj.x; fy = obj.y; ne = 0;
                log("blanco fijado " + fc + "," + fr + " (rango=" + (obj.e ? 1 : 0) + ") slots m=" + MROW + " p=" + PROW + " r=" + RROW); guardaDebug("blanco");
            }
            vacio = 0;
            if (cartelImposible()) { for (int w = 0; w < 8 && cartelImposible(); w++) { pausa(250); cap(); if (!ok()) return "NO_HORIZONTAL " + shots; } }   // cartel de un intento anterior: esperar a que se vaya
            int rafaga = apOk ? Math.max(1, Math.min(2, ap / coste)) : 1;
            for (int b = 0; b < rafaga; b++) { doble(fx, fy); if (b < rafaga - 1) pausa(1000); }
            pausa(300); cap(); if (!ok()) return "NO_HORIZONTAL " + shots;
            if (!turno().equals("MIO")) return "FIN_TURNO_AUTO " + (shots + rafaga);
            if (cartelImposible()) { malos.add(fc + "," + fr); log("blanco " + fc + "," + fr + ": IMPOSIBLE atacar al objetivo (sin linea de tiro): lo descarto"); fc = -1; continue; }
            int ap2 = puntos();
            if (apOk && ap2 >= ap) { pausa(400); cap(); if (!ok()) return "NO_HORIZONTAL " + shots; if (!turno().equals("MIO")) return "FIN_TURNO_AUTO " + shots; ap2 = puntos(); }   // el contador puede ir con retraso
            int gastados = apOk ? (ap - ap2) / coste : rafaga;
            if (gastados > 0 || !apOk) { shots += Math.max(gastados, 1); ne = 0; log("rafaga a " + fc + "," + fr + ": ap " + ap + " -> " + ap2 + " (" + Math.max(gastados, 1) + " disparos)"); }
            else { ne++; log("rafaga a " + fc + "," + fr + " SIN efecto (" + ne + ")"); if (ne >= (probando ? 1 : 2)) { malos.add(fc + "," + fr); fc = -1; } }
        }
        return "LIMITE " + shots;
    }

    static String hab3(boolean yaCapturado) throws Exception {
        if (!yaCapturado) { cap(); if (!ok()) return "NO_HORIZONTAL"; if (!turno().equals("MUNECO")) return "NO_ES_TURNO_MUNECO"; }
        if (h3Enfria()) return "ENFRIA";
        cargaEstado();
        tap(300, Y_H3); pausa(350);
        cap(); if (!ok()) return "NO_HORIZONTAL";
        actualizaN(); escribeJug = false; aroEsEnemigo = true; analiza(); escribeJug = true; aroEsEnemigo = false;
        if (enemigos.isEmpty()) return "SIN_ENEMIGOS";
        Tok o = enemigos.get(0);
        doble(o.x, o.y); pausa(250);
        return "OK " + o.c + "," + o.r + " rango=" + (o.e ? 1 : 0);
    }

    static String pasar(String antes) throws Exception {
        for (int n = 1; n <= 3; n++) {
            long t0 = System.currentTimeMillis(); tap(2113, 1035);
            for (int t = 0; t < 4; t++) {
                pausa(120); cap(); if (!ok()) return "NO_HORIZONTAL";
                String q = turno();
                if (!q.equals(antes)) { log("pase " + antes + "->" + q + " en " + (System.currentTimeMillis() - t0) + " ms (" + (t + 1) + " lecturas, toque " + n + ")"); return "OK " + antes + "->" + q; }
            }
            log("pase no registrado tras " + (System.currentTimeMillis() - t0) + " ms (toque " + n + "): reintento");
        }
        return "FALLO";
    }

    /** Turno completo mio. primer=1: invoca y pasa (sin disparar ni mover). Devuelve linea resumen. */
    static String turnoMio(boolean primer) throws Exception {
        long tIni = System.currentTimeMillis();
        cap(); if (!ok()) return "NO_HORIZONTAL";
        if (!turno().equals("MIO")) return "NO_MIO";
        cargaEstado(); if (!slotsOk()) { log("SLOTS_DESCONOCIDOS: no reconozco las armas por su icono: no actuo"); return "SLOTS_DESCONOCIDOS"; }
        actualizaN(); analiza();
        int ap0 = puntos(); boolean libre = !aliadoVivo();
        if (ap0 == 0 && !rifleListo()) { pausa(500); cap(); if (!ok()) return "NO_HORIZONTAL"; ap0 = puntos(); if (ap0 == 0 && !rifleListo()) return "NO_MIO"; }   // transicion de turno, no es mi turno todavia   // icono gris o ficha verde en el tablero
        log("TURNO MIO ap=" + ap0 + " rifle=" + (rifleListo() ? 1 : 0) + " muneco=" + (libre ? "LIBRE" : "VIVO") + " N=" + N + " jug=" + jc + "," + jr + " enemigos=" + enemigos.size());
        if (libre) {
            String r = invocar("auto"); log("invocar -> " + r); recienInvocado = true;
            if (!r.startsWith("OK")) { if (primer) return "INVOCACION_FALLA"; }
            else if (primer) { log("turno 1: muneco invocado -> paso sin disparar ni mover"); pausa(500); return "PASE " + pasar("MIO"); }
        } else if (primer) { log("turno 1 pero el muneco ya esta en el tablero: paso sin disparar"); return "PASE " + pasar("MIO"); }
        int movido = 0; String r = "";
        for (int ronda = 0; ronda < 6; ronda++) {
            r = atacar(6); log("atacar -> " + r);
            if (r.startsWith("FIN_TURNO_AUTO")) return "PASE_AUTO " + r;
            if (r.startsWith("NO_HORIZONTAL")) return r;
            if (r.startsWith("SIN_BLANCO")) {
                boolean hayRango = false; for (Tok t : enemigos) if (t.e && !lastMalos.contains(t.c + "," + t.r)) hayRango = true;   // en rango Y con tiro posible; uno en celda iluminada pero sin linea de tiro NO cuenta: hay que moverse
                if (hayRango) { log("hay enemigos EN RANGO con tiro posible: NO me muevo"); log("nadie a tiro: paso"); break; }
                if (movido == 0) {
                    movido = 1; String m = mover("auto"); log("mover -> " + m);
                    if (m.startsWith("OK")) continue;
                }
                log("nadie a tiro: paso"); break;
            }
            if (r.startsWith("LIMITE") || r.startsWith("SIN_EFECTO")) { continue; }
            break;   // AGOTADO / SIN_PUNTOS: no quedan disparos posibles
        }
        String rp = pasar("MIO"); log("turno completo en " + (System.currentTimeMillis() - tIni) + " ms"); return "PASE " + rp;
    }
    static String turnoMuneco() throws Exception {
        long t0 = System.currentTimeMillis();
        cap(); if (!ok()) return "NO_HORIZONTAL";
        if (!turno().equals("MUNECO")) return "NO_MUNECO";
        cargaEstado(); if (!slotsOk()) { log("SLOTS_DESCONOCIDOS: no reconozco las armas por su icono: no actuo"); return "SLOTS_DESCONOCIDOS"; }
        String r = hab3(true); log("TURNO MUNECO hab3 -> " + r + " (" + (System.currentTimeMillis() - t0) + " ms)");
        if (r.startsWith("OK")) pausa(400);
        return "PASE " + pasar("MUNECO");
    }

    static void exec(String[] a) throws Exception {
        String cmd = a.length > 0 ? a[0] : "ver";
        switch (cmd) {
            case "ver": imprimeVer(a.length > 1 && a[1].equals("2")); break;
            case "tap": {
                for (int i = 1; i < a.length; ) {
                    if (a[i].startsWith("@")) { pausa(Integer.parseInt(a[i].substring(1))); i++; }
                    else { tap(Integer.parseInt(a[i]), Integer.parseInt(a[i + 1])); i += 2; }
                }
                System.out.println("OK"); break; }
            case "invocar": System.out.println(invocar(a.length > 1 ? a[1] : "auto")); break;
            case "mover": System.out.println(mover(a.length > 1 ? a[1] : "auto")); break;
            case "atacar": System.out.println(atacar(a.length > 1 ? Integer.parseInt(a[1]) : 6)); break;
            case "hab3": System.out.println(hab3(false)); break;
            case "pasar": System.out.println(pasar(a.length > 1 ? a[1] : "MIO")); break;
            case "turno_mio": System.out.println(turnoMio(a.length > 1 && a[1].equals("1"))); break;
            case "turno_muneco": System.out.println(turnoMuneco()); break;
            case "ping": System.out.println("PONG"); break;
            default: System.out.println("comando?");
        }
    }

    /** Servicio: se queda en memoria (evita ~1 s de arranque de la JVM por orden). Orden en bcv.cmd (se borra al leerla), respuesta en bcv.res; latido en bcv.alive. */
    static void serve() throws Exception {
        java.io.PrintStream real = System.out;
        File cmd = new File(DIR + "bcv.cmd"), alive = new File(DIR + "bcv.alive");
        long ult = 0;
        while (true) {
            long now = System.currentTimeMillis();
            if (now - ult > 1000) { try { if (!alive.exists()) alive.createNewFile(); alive.setLastModified(now); } catch (Exception e) { } ult = now; }
            if (!cmd.exists()) { Thread.sleep(25); continue; }
            String line = leer("bcv.cmd"); cmd.delete();
            if (line == null || line.isEmpty()) continue;
            if (line.equals("quit")) { alive.delete(); return; }
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            System.setOut(new java.io.PrintStream(bo, true));
            try { exec(line.split("\\s+")); } catch (Throwable t) { System.out.println("ERR " + t); }
            System.out.flush(); System.setOut(real);
            File tmp = new File(DIR + "bcv.res.tmp");
            try (FileOutputStream fo = new FileOutputStream(tmp)) { fo.write(bo.toByteArray()); }
            tmp.renameTo(new File(DIR + "bcv.res"));
        }
    }

    public static void main(String[] a) throws Exception {
        if (a.length > 0 && a[0].equals("serve")) { serve(); return; }
        exec(a);
    }
}
