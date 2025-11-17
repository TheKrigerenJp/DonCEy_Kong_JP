package Server;

import Server.entities.*;
import Server.factory.*;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
/**
 * Clase principal del servidor
 * <p>Esta clase implementa el patrón de diseño Singleton para asegurar
 * que solo exista una instancia del servidor en toda la aplicación.</p>
 *
 * <p>Se encarga de manejar la lógica central del juego, las conexiones con los
 * clientes, la administración de sesiones de juego y la comunicación con
 * espectadores.</p>
 */
public class Server {

    /**
     * Representa un evento de entrada (input) del jugador.
     * <p>Contiene la información necesaria para procesar una acción de movimiento
     * del cliente y su número de secuencia para reconciliación.</p>
     */
    static final class InputEvent {
        final int playerId, seq, dx, dy;

        /**
         * Crea un nuevo evento de entrada para un jugador.
         *
         * @param playerId identificador del jugador que generó el input
         * @param seq      número de secuencia del input para reconciliación
         * @param dx       desplazamiento horizontal solicitado por el cliente
         * @param dy       desplazamiento vertical solicitado por el cliente
         */
        InputEvent(int playerId, int seq, int dx, int dy) {
            this.playerId = playerId; this.seq = seq; this.dx = dx; this.dy = dy;
        }
    }

    /* ========= Singleton ========= */
    /**
     * La única instancia de la clase {@code Server} (patrón Singleton).
     */
    private static volatile Server instance;

    /**
     * Constructor privado para prevenir la instanciación externa.
     */
    private Server() {}

    /**
     * Obtiene la única instancia de la clase {@code Server}.
     *
     * <p>Utiliza el patrón Double-Checked Locking para garantizar la
     * seguridad de hilos al inicializar la instancia.</p>
     *
     * @return La única instancia disponible del servidor.
     */
    public static Server getInstance() {
        if (instance == null) {
            synchronized (Server.class) { if (instance == null) instance = new Server(); }
        }
        return instance;
    }

    /* ========= Sockets / pools ========= */
    /** El socket principal del servidor, usado para aceptar nuevas conexiones. */
    private ServerSocket serverSocket;
    /** El puerto TCP donde el servidor está escuchando conexiones. */
    private final int port = 5000;
    /** Pool de hilos para manejar la comunicación I/O con los {@link ClientHandler}. */
    private final ExecutorService pool = Executors.newCachedThreadPool();
    /** Lista de todos los manejadores de clientes conectados (jugadores y espectadores). */
    private final CopyOnWriteArrayList<ClientHandler> clients = new CopyOnWriteArrayList<>();

    /* ========= Estado básico ========= */
    /** Contador atómico para generar el siguiente ID único de jugador. */
    private final AtomicInteger nextId = new AtomicInteger(1);
    private final AtomicInteger tickSeq = new AtomicInteger(0);
    final ConcurrentHashMap<Integer, Player> players = new ConcurrentHashMap<>();
    final ConcurrentHashMap<ClientHandler, Integer> byClient = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<InputEvent> inputQueue = new ConcurrentLinkedQueue<>();

    /* ========= Jugadores vs espectadores ========= */
    /** Conjunto de {@link ClientHandler} que actualmente están actuando como jugadores. */
    private final Set<ClientHandler> playerClients =
            Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final ConcurrentHashMap<Integer, CopyOnWriteArrayList<ClientHandler>> spectatorsByPlayer =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, CopyOnWriteArrayList<ClientHandler>> waitingSpectatorsByPlayer =
            new ConcurrentHashMap<>();

    /* ========= Mapa / constantes ========= */
    public static final int MIN_X = 0;
    public static final int MAX_X = 10;
    public static final int MIN_Y = 0;
    public static final int MAX_Y = 10;

    /**
     * Representación del mapa del juego como una matriz de caracteres.
     * Cada fila representa una coordenada Y y cada columna una coordenada X.
     */
    private static final char[][] MAP = {
        "WWTWWTWTWWW".toCharArray(), // y=0
        "S==..=T..==".toCharArray(), // y=1
        "..|.......=".toCharArray(), // y=2
        "..|..===..=".toCharArray(), // y=3
        "..|..|.....".toCharArray(), // y=4
        "..|..|..===".toCharArray(), // y=5
        "..|..|.....".toCharArray(), // y=6
        "..===|.....".toCharArray(), // y=7
        ".....|..===".toCharArray(), // y=8
        ".....|.....".toCharArray(), // y=9
        "..GGG===...".toCharArray()  // y=10
    };


    /**
     * Obtiene el carácter de la celda del mapa en las coordenadas dadas.
     * <p>Si las coordenadas están fuera de los límites del mapa, se devuelve
     * el carácter {@code '.'} para indicar vacío.</p>
     *
     * @param x coordenada horizontal en el mapa
     * @param y coordenada vertical en el mapa
     * @return carácter que representa el contenido de la celda del mapa
     */
    private static char tileAt(int x, int y) {
        if (x < MIN_X || x > MAX_X || y < MIN_Y || y > MAX_Y) return '.';
        return MAP[y][x];
    }

    /**
     * Indica si la celda en las coordenadas especificadas corresponde a agua.
     *
     * @param x coordenada horizontal en el mapa
     * @param y coordenada vertical en el mapa
     * @return {@code true} si la celda contiene agua, {@code false} en caso contrario
     */
    private static boolean isWater(int x, int y) {
        return tileAt(x, y) == 'W';
    }

    // Donde el jugador PUEDE estar de pie / colgado
    /**
     * Determina si un carácter de mapa se considera una superficie sólida
     * sobre la cual el jugador puede estar de pie o colgado.
     *
     * @param t carácter del mapa a evaluar
     * @return {@code true} si el carácter representa una superficie sólida,
     *         {@code false} en caso contrario
     */
    private static boolean isSolidTile(char t) {
        return t == 'T'   // tierra que sale del agua
            || t == '='   // plataforma
            || t == '|'   // liana (colgado)
            || t == 'S'   // spawn
            || t == 'G';  // meta (jaula DK)
    }

    /* ========= Abstract Factory / plantillas ========= */
    /** Fábrica abstracta de elementos del juego (enemigos y frutas). */
    private final GameElementFactory factory = new DefaultFactory();
    /** Lista de enemigos “plantilla” compartidos entre sesiones. */
    private final CopyOnWriteArrayList<Enemy> templateEnemies = new CopyOnWriteArrayList<>();
    /** Lista de frutas “plantilla” compartidas entre sesiones. */
    private final CopyOnWriteArrayList<Fruit> templateFruits  = new CopyOnWriteArrayList<>();
    /** Sesiones de juego por jugador. */
    private final ConcurrentHashMap<Integer, GameSession> sessions = new ConcurrentHashMap<>();

    /* ========= Game Loop / Scheduler ========= */
    private final ScheduledExecutorService ticker = Executors.newSingleThreadScheduledExecutor();

    /**
     * Inicializa el socket del servidor, lo enlaza al puerto y comienza a escuchar
     * conexiones.
     * <p>También inicia el *Game Loop* ({@link #tick()}) y el hilo de la consola
     * de administración.</p>
     * @throws IOException Si ocurre un error al abrir el socket del servidor.
     */
    public void start() throws IOException {
        if (serverSocket != null && !serverSocket.isClosed()) return;

        serverSocket = new ServerSocket();
        serverSocket.bind(new InetSocketAddress("127.0.0.1", port));
        System.out.println("[JAVA] Servidor escuchando en puerto " + port + " ...");

        ticker.scheduleAtFixedRate(this::tick, 100, 100, TimeUnit.MILLISECONDS);
        new Thread(this::adminLoop, "AdminConsole").start();

        while (!serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true);
                System.out.println("[JAVA] Cliente conectado: " + socket.getRemoteSocketAddress());
                ClientHandler handler = new ClientHandler(socket, this);
                clients.add(handler);
                pool.submit(handler);
            } catch (SocketException se) {
                System.out.println("[JAVA] Aceptación detenida: " + se.getMessage());
                break;
            }
        }
    }

    /* ========= TICK: procesa inputs, simula enemigos y notifica ========= */
    /**
     * El ciclo principal de simulación del juego (Game Loop).
     *
     * <p>Se ejecuta a una tasa fija (controlada por {@link #ticker}) y realiza
     * tres pasos principales:</p>
     * <ol>
     * <li>Procesar los inputs de la {@link #inputQueue} y actualizar la posición de los {@link Player}.</li>
     * <li>Simular los enemigos y chequear eventos de juego (colisiones, recoger frutas, meta).</li>
     * <li>Enviar el nuevo estado del juego a los jugadores y espectadores.</li>
     * </ol>
     */
    private void tick() {
        // 1) inputs → posiciones (con límites) + ack
        InputEvent ev;
        while ((ev = inputQueue.poll()) != null) {
            Player p = players.get(ev.playerId);
            if (p == null) continue;
            int nx = p.x + ev.dx, ny = p.y + ev.dy;
            if (nx < MIN_X) nx = MIN_X; if (nx > MAX_X) nx = MAX_X;
            if (ny < MIN_Y) ny = MIN_Y; if (ny > MAX_Y) ny = MAX_Y;
            p.x = nx; p.y = ny; p.lastAckSeq = Math.max(p.lastAckSeq, ev.seq);
        }

        // 1b) GRAVEDAD + agua
        sessions.forEach((pid, session) -> {
            Player p = players.get(pid);
            if (p == null) return;

            // Gravedad: si no hay soporte debajo, cae una casilla
            if (p.y > MIN_Y) {
                char below = tileAt(p.x, p.y - 1);
                if (!isSolidTile(below)) {
                    p.y -= 1;
                }
            }

            // Si llegó al agua (ya sea por caída o por movimiento directo) -> respawn
            if (isWater(p.x, p.y)) {
                p.x = session.spawnX;
                p.y = session.spawnY;
                p.gameOver = false;
            }
        });

        // 2) Simulación de enemigos + colisiones
        sessions.forEach((pid, session) -> {
            Player p = players.get(pid);
            if (p == null) return;

            // Enemigos (ejemplo simplificado, se puede usar enemySpeedSteps)
            for (Enemy e : session.enemies) {
                e.tick(MIN_Y, MAX_Y);
                if (e.getX() == p.x && e.getY() == p.y) {
                    p.gameOver = true;
                }
            }

            // Frutas (ejemplo simple: si colisiona, suma puntos y se quita la fruta)
            Iterator<Fruit> it = session.fruits.iterator();
            while (it.hasNext()) {
                Fruit f = it.next();
                if (f.getX() == p.x && f.getY() == p.y) {
                    p.score += f.getPoints();
                    it.remove();
                }
            }

            // Meta (si llega a la meta, se puede marcar como ronda superada)
            if (p.x == session.goalX && p.y == session.goalY) {
                p.round++;
            }
        });

        // 3) Notificar estado a los clientes
        int seq = tickSeq.incrementAndGet();
        players.forEach((id, p) -> {
            String state = String.format(
                    Locale.ROOT,
                    "STATE %d %d %d %d %d %b%n",
                    seq, id, p.x, p.y, p.score, p.gameOver
            );
            sendToPlayerAndSpectators(id, state);
        });
    }

    /* ========= Eventos desde ClientHandler ========= */

    /**
     * Maneja una solicitud de JOIN de un cliente.
     *
     * @param c       manejador del cliente que envía la solicitud
     * @param name    nombre de jugador solicitado
     */
    public void onJoin(ClientHandler c, String name) {
        int id = nextId.getAndIncrement();
        Player p = new Player(id, name);
        players.put(id, p);
        byClient.put(c, id);
        playerClients.add(c);

        // Crear sesión de juego
        GameSession session = new GameSession(id);

        // Cargar plantillas (enemigos y frutas) en esta sesión
        session.loadFromTemplates(templateEnemies, templateFruits);

        sessions.put(id, session);

        c.sendLine("JOINED " + id + "\n");
        System.out.println("[JAVA] JOIN -> id=" + id + " name=" + name);
    }

    /**
     * Maneja una entrada de movimiento desde un cliente jugador.
     *
     * @param c   manejador del cliente que envía la entrada
     * @param seq número de secuencia del input (para reconciliación)
     * @param dx  desplazamiento horizontal (-1, 0 o +1)
     * @param dy  desplazamiento vertical (-1, 0 o +1)
     */
    public void onInput(ClientHandler c, int seq, int dx, int dy) {
        Integer id = byClient.get(c);
        if (id == null) { c.sendLine("ERR NOT_PLAYER\n"); return; }

        // rate limit si ya lo tenías...
        if (Math.abs(dx) > 1 || Math.abs(dy) > 1) { c.sendLine("ERR STEP_TOO_BIG\n"); return; }
        inputQueue.offer(new InputEvent(id, seq, dx, dy));
    }

    /**
     * Maneja la solicitud de un cliente para unirse como espectador.
     *
     * <p>Si el jugador existe, lo añade a la lista de espectadores. Si no,
     * lo añade a la lista de espera ({@link #waitingSpectatorsByPlayer}).</p>
     *
     * @param c El manejador del cliente que solicita ser espectador.
     * @param playerId El ID del jugador al que desea observar.
     */
    public void onSpectate(ClientHandler c, int playerId) {
        Player p = players.get(playerId);
        if (p != null) {
            spectatorsByPlayer
                .computeIfAbsent(playerId, k -> new CopyOnWriteArrayList<>())
                .add(c);
            c.sendLine("SPECTATE_OK " + playerId + "\n");
        } else {
            waitingSpectatorsByPlayer
                .computeIfAbsent(playerId, k -> new CopyOnWriteArrayList<>())
                .add(c);
            c.sendLine("SPECTATE_WAIT " + playerId + "\n");
        }
    }

    /**
     * Maneja la desconexión lógica de un cliente.
     *
     * @param c manejador de cliente que se está desconectando
     */
    public void onQuit(ClientHandler c) {
        Integer id = byClient.remove(c);

        if (id != null) {
            endPlayerSession(id);
        } else {
            spectatorsByPlayer.forEach((pid, ls) -> ls.remove(c));
            waitingSpectatorsByPlayer.forEach((pid, ls) -> ls.remove(c));
        }
    }

    /* ========= Utilidades ========= */
    /**
     * Finaliza la sesión de juego de un jugador específico.
     * <p>Elimina el {@link Player} y la {@link GameSession}, notifica a sus
     * espectadores, y fuerza el cierre de la conexión del {@link ClientHandler}
     * asociado si aún está conectado.</p>
     *
     * @param playerId El ID del jugador cuya sesión debe terminar.
     */
    private void endPlayerSession(int playerId) {
        GameSession s = sessions.get(playerId);
        if (s != null) {
            CopyOnWriteArrayList<ClientHandler> specs = spectatorsByPlayer.remove(playerId);
            if (specs != null) {
                for (ClientHandler ch : specs) {
                    ch.sendLine("END " + playerId + "\n");
                }
            }
        }
        // cerrar también el clientHandler asociado, si existe
        ClientHandler toClose = null;
        for (Map.Entry<ClientHandler,Integer> e : byClient.entrySet()) {
            if (e.getValue() == playerId) { toClose = e.getKey(); break; }
        }
        if (toClose != null) {
            playerClients.remove(toClose);
            toClose.sendLine("BYE\n");
            toClose.close();
            clients.remove(toClose);
        }
        players.remove(playerId);
        sessions.remove(playerId);
        System.out.println("[JAVA] Fin de sesión -> id=" + playerId);
    }

    /**
     * Envía una línea de texto a un jugador y a todos los espectadores asociados.
     * <p>Si el jugador no existe o no tiene clientes asociados, la llamada no
     * realiza ninguna acción.</p>
     *
     * @param playerId identificador del jugador dueño de la sesión
     * @param line     línea de texto a enviar (debe incluir el salto de línea si se requiere)
     */
    private void sendToPlayerAndSpectators(int playerId, String line) {
        // a jugador:
        for (Map.Entry<ClientHandler,Integer> e : byClient.entrySet()) {
            if (e.getValue() == playerId) e.getKey().sendLine(line);
        }
        // a espectadores:
        CopyOnWriteArrayList<ClientHandler> ls = spectatorsByPlayer.get(playerId);
        if (ls != null) for (ClientHandler ch : ls) ch.sendLine(line);
    }

    /**
     * Envía una línea de texto a todos los clientes conectados al servidor.
     *
     * @param line línea de texto a enviar (debe incluir el salto de línea si se requiere)
     */
    public void broadcast(String line) {
        for (ClientHandler ch : clients) ch.sendLine(line);
    }

    /**
     * Notifica al servidor que un cliente debe ser removido de la lista global.
     *
     * @param c cliente a remover
     */
    void removeClient(ClientHandler c) {
        clients.remove(c);
        onQuit(c);
        System.out.println("[JAVA] Cliente removido. Conectados: " + clients.size());
    }

    /**
     * Detiene ordenadamente el servidor.
     * <p>Cierra el socket principal, detiene el hilo de administración,
     * apaga el planificador de ticks y cierra todas las conexiones activas
     * con los clientes.</p>
     */
    public void stop() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {}

        ticker.shutdownNow();
        pool.shutdownNow();
        for (ClientHandler ch : clients) {
            ch.close();
        }
        System.out.println("[JAVA] Servidor detenido.");
    }

    /* ========= Consola Admin (Abstract Factory) ========= */
    /**
     * Bucle de ejecución para la consola de administración.
     * <p>Lee comandos desde la entrada estándar (System.in) y los procesa
     * en {@link #handleAdminCommand(String)}.</p>
     */
    private void adminLoop() {
        try (Scanner sc = new Scanner(System.in)) {
            while (true) {
                String line = sc.nextLine().trim();
                if (line.equalsIgnoreCase("exit")) { stop(); break; }
                runAdminCommand(line);
            }
        } catch (Exception ignored) {}
    }

    /**
     * Procesa un comando introducido en la consola de administración.
     * <p>Permite, por ejemplo, listar jugadores activos, sesiones o
     * cerrar el servidor con comandos específicos.</p>
     *
     * @param line comando completo leído desde la consola de administración
     */
    public void runAdminCommand(String line) {
        if (line.equalsIgnoreCase("players")) {
            System.out.println("Jugadores conectados:");
            players.forEach((id, p) -> System.out.println(" - " + id + " " + p.name));
        } else if (line.equalsIgnoreCase("sessions")) {
            System.out.println("Sesiones activas:");
            sessions.forEach((id, s) -> System.out.println(" - " + id));
        } else if (line.equalsIgnoreCase("broadcast test")) {
            broadcast("SERVER MSG: test\n");
        } else {
            System.out.println("Comando desconocido: " + line);
        }
    }

    /**
     * Método principal del servidor.
     * <p>Configura un hook de apagado para detener correctamente el socket
     * del servidor (llamando a {@link #stop()}) cuando la JVM se detiene
     * (por ejemplo, con CTRL+C) e inicia el servidor.</p>
     *
     * @param args Argumentos de la línea de comandos (no utilizados).
     */
    public static void main(String[] args) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[JAVA] Shutdown hook → cerrando servidor...");
            Server.getInstance().stop();
        }));
        try { Server.getInstance().start(); } catch (IOException e) { e.printStackTrace(); }
    }
}

