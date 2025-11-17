package Server;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Maneja la comunicación de un único cliente conectado al servidor.
 * <p>
 * Esta clase se ejecuta en su propio hilo (implementa {@link Runnable}) y
 * se encarga de leer comandos de texto enviados por el cliente, interpretarlos
 * y delegar la lógica correspondiente al {@link Server}.
 * </p>
 */
public class ClientHandler implements Runnable {
    /** Socket TCP asociado a este cliente. */
    private final Socket socket;
    /** Referencia al servidor principal para notificar eventos. */
    private final Server server;
    /** Lector de texto desde el socket del cliente. */
    private BufferedReader in;
    /** Escritor de texto hacia el socket del cliente. */
    private BufferedWriter out;

    /**
     * Crea un nuevo manejador de cliente a partir de un socket aceptado.
     *
     * @param socket socket TCP ya aceptado, conectado con el cliente
     * @param server instancia del servidor que gestionará los eventos recibidos
     */
    public ClientHandler(Socket socket, Server server) {
        this.socket = socket;
        this.server = server;
        try {
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            this.out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Bucle principal de atención al cliente.
     * <p>
     * Envía inicialmente un mensaje de bienvenida y luego queda a la espera
     * de comandos de texto. Dependiendo del prefijo de cada línea se invocan
     * distintos métodos del servidor:
     * </p>
     * <ul>
     *     <li>{@code JOIN &lt;nombre&gt;} → {@link Server#onJoin(ClientHandler, String)}</li>
     *     <li>{@code INPUT &lt;seq&gt; &lt;dx&gt; &lt;dy&gt;} → {@link Server#onInput(ClientHandler, int, int, int)}</li>
     *     <li>{@code SPECTATE &lt;idJugador&gt;} → {@link Server#onSpectate(ClientHandler, int)}</li>
     *     <li>{@code PING} → responde con {@code PONG}</li>
     *     <li>{@code QUIT} → responde con {@code BYE} y cierra la conexión</li>
     * </ul>
     * Si ocurre un error de E/S o el cliente se desconecta, la conexión
     * se cierra y el servidor es notificado.
     */
    @Override
    public void run() {
        try {
            sendLine("WELCOME\n");

            String line;
            socket.setSoTimeout(20000); // 20 s de inactividad máx. 
            while ((line = in.readLine()) != null) {
                System.out.println("[JAVA] <- " + line);

                if (line.startsWith("JOIN ")) {
                    server.onJoin(this, line.substring(5).trim());
                } else if (line.startsWith("INPUT ")) {
                    // INPUT <seq> <dx> <dy>
                    String[] t = line.split("\\s+");
                    if (t.length >= 4) {
                        int seq = Integer.parseInt(t[1]);
                        int dx  = Integer.parseInt(t[2]);
                        int dy  = Integer.parseInt(t[3]);
                        server.onInput(this, seq, dx, dy);
                    } else {
                        sendLine("ERR BAD_INPUT\n");
                    }
                } else if (line.startsWith("SPECTATE ")) {
                    try {
                        int pid = Integer.parseInt(line.substring(9).trim());
                        server.onSpectate(this, pid);
                    } catch (NumberFormatException e) {
                        sendLine("ERR BAD_SPECTATE\n");
                    }
                } else if (line.equalsIgnoreCase("PING")) {
                    sendLine("PONG\n");
                } else if (line.equalsIgnoreCase("QUIT")) {
                    sendLine("BYE\n");
                    break;
                } else {
                    sendLine("ERR UNKNOWN\n");
                }
            }
        } catch (IOException e) {
            System.out.println("[JAVA] Cliente desconectado: " + e.getMessage());
        } finally {
            close();
            server.removeClient(this);
        }
    }

    /**
     * Envía de forma thread-safe una línea de texto al cliente.
     *
     * @param s cadena a enviar; debe incluir el carácter de nueva línea
     *          {@code '\\n'} si se requiere terminar la línea.
     */
    public synchronized void sendLine(String s) {
        try {
            out.write(s);
            out.flush();
        } catch (IOException ignored) {}
    }

    /**
     * Cierra de forma ordenada los recursos asociados al cliente:
     * lector, escritor y socket.
     * <p>
     * Cualquier excepción producida durante el cierre es ignorada
     * intencionalmente, ya que el objetivo principal es liberar recursos.
     * </p>
     */
    public void close() {
        try { if (in != null) in.close(); } catch (IOException ignored) {}
        try { if (out != null) out.close(); } catch (IOException ignored) {}
        try { if (socket != null && !socket.isClosed()) socket.close(); } catch (IOException ignored) {}
    }
}
