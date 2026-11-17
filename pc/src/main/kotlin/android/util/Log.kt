package android.util

/** El registro del telefono va aqui a la consola, solo avisos y errores. */
object Log {
    const val VERBOSE = 2
    const val DEBUG = 3
    const val INFO = 4
    const val WARN = 5
    const val ERROR = 6
    fun v(t: String, m: String) = 0
    fun d(t: String, m: String) = 0
    fun i(t: String, m: String) = 0
    fun w(t: String, m: String): Int { System.err.println("$t: $m"); return 0 }
    fun w(t: String, m: String, e: Throwable): Int { System.err.println("$t: $m: $e"); return 0 }
    fun e(t: String, m: String): Int { System.err.println("$t: $m"); return 0 }
    fun e(t: String, m: String, e: Throwable): Int { System.err.println("$t: $m: $e"); return 0 }
    fun isLoggable(t: String, nivel: Int) = false
}
