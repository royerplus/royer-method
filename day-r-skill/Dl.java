import java.io.*; import java.net.*;
/** Dl URL DESTINO : descarga (sigue redirecciones) y guarda. Imprime DL_OK <bytes> | DL_ERROR <msg>. */
public class Dl { public static void main(String[] a) { try {
  String u = a[0]; HttpURLConnection c = null;
  for (int i = 0; i < 5; i++) { c = (HttpURLConnection) new URL(u).openConnection(); c.setConnectTimeout(15000); c.setReadTimeout(30000); c.setInstanceFollowRedirects(false); int s = c.getResponseCode(); if (s >= 300 && s < 400) { u = new URL(new URL(u), c.getHeaderField("Location")).toString(); continue; } if (s != 200) { System.out.println("DL_ERROR http " + s); return; } break; }
  long n = 0; try (InputStream in = c.getInputStream(); FileOutputStream o = new FileOutputStream(a[1])) { byte[] b = new byte[65536]; int r; while ((r = in.read(b)) > 0) { o.write(b, 0, r); n += r; } }
  System.out.println("DL_OK " + n);
} catch (Exception e) { System.out.println("DL_ERROR " + e); } } }
