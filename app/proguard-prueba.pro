# El harness debug usa estas APIs al medir la variante optimizada .prueba.
# Las reglas solo se aplican a prueba; release conserva su optimizacion normal.
-keep class com.royermethod.app.editor.** { *; }
-keep class kotlin.** { *; }
