package Server;

/**
 * Clase de utilidad para pruebas o tareas administrativas.
 * <p>
 * Su método {@link #main(String[])} imprime en consola los argumentos
 * recibidos por línea de comandos, útil para verificar cómo se está
 * invocando la aplicación desde el entorno de ejecución.
 * </p>
 */
public class Admin {

    /**
     * Punto de entrada de la aplicación de administración.
     * <p>
     * Imprime en la salida estándar los primeros argumentos recibidos
     * por la línea de comandos. El contenido exacto de los argumentos
     * depende de cómo se ejecute el programa.
     * </p>
     *
     * @param args argumentos de la línea de comandos; se espera que al menos
     *             existan tres posiciones ({@code args[0]}, {@code args[1]},
     *             {@code args[2]}), que serán impresas en consola.
     */
    public static void main(String[] args) {
        System.out.println(args[0]);
        System.out.println(Integer.valueOf(1));
        System.out.println(args[1]);
        System.out.println(args[2]);
        System.out.println(2);
        System.out.println(Integer.valueOf(2));
    }
}
