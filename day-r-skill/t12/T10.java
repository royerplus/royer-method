public class T10 { public static void main(String[] x) throws Exception { int N=Integer.parseInt(x[0]); int c=Integer.parseInt(x[1]), r=Integer.parseInt(x[2]); Bcv.N=N; Bcv.cap();
 int cx = 709 + 1018 * (2 * c + 1) / (2 * N), cy = 30 + 1018 * (2 * r + 1) / (2 * N);
 int a = 1018 * 355 / (N * 1000), a7 = a * 7 / 10, b = 1018 * 455 / (N * 1000);
 int dd = 0; int[][] ej = {{a, 0}, {-a, 0}, {0, a}, {0, -a}}; for (int[] q : ej) if (Bcv.osc(cx + q[0], cy + q[1])) dd++;
 int d1=dd; if (dd >= 1) { int[][] dg = {{a7, a7}, {-a7, a7}, {a7, -a7}, {-a7, -a7}}; for (int[] q : dg) if (Bcv.osc(cx + q[0], cy + q[1])) dd++; }
 System.out.println("cell "+c+","+r+" centro="+cx+","+cy+" a="+a+" dd_ejes="+d1+" dd_total="+dd+" interiorVerde="+Bcv.interiorVerde(cx,cy)+" tieneAroEnemigo="+Bcv.tieneAroEnemigo(cx,cy));
 for (int[] q : ej) { int[] p=Bcv.px(cx+q[0],cy+q[1]); System.out.println("  eje "+q[0]+","+q[1]+" rgb="+p[0]+","+p[1]+","+p[2]); } } }
