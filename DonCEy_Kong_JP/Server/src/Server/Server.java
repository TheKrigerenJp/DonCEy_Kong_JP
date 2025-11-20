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
        final Integer playerId, seq, dx, dy;

        /**
         * Crea un nuevo evento de entrada para un jugador.
         *
         * @param playerId identificador del jugador que generó el input
         * @param seq      número de secuencia del input para reconciliación
         * @param dx       desplazamiento horizontal solicitado por el cliente
         * @param dy       desplazamiento vertical solicitado por el cliente
         */
        InputEvent(Integer playerId, Integer seq, Integer dx, Integer dy) {
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
    private final Integer port = 5000;
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
    /**
     * Mapa de listas de espectadores por jugador.
     *
     * <p>La clave del mapa es el {@code playerId} y el valor es una lista de
     * {@link ClientHandler} que están recibiendo actualizaciones del estado
     * de ese jugador.</p>
     *
     * <p>Esta estructura implementa el patrón de diseño <b>Observador</b>:
     * cada jugador (identificado por su {@code playerId}) actúa como
     * <em>sujeto observado</em>, mientras que los clientes registrados en la
     * lista asociada se comportan como <em>observadores</em>. En cada ciclo
     * de juego ({@link #tick()}), el servidor notifica el nuevo estado del
     * jugador a todos sus observadores mediante
     * {@link #sendToPlayerAndSpectators(Integer, String)}.</p>
     */
    private final ConcurrentHashMap<Integer, CopyOnWriteArrayList<ClientHandler>> spectatorsByPlayer =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, CopyOnWriteArrayList<ClientHandler>> waitingSpectatorsByPlayer =
            new ConcurrentHashMap<>();

    /* ========= Mapa / constantes ========= */
    public static final Integer MIN_X = 0;
    public static final Integer MAX_X = 13;
    public static final Integer MIN_Y = 0;
    public static final Integer MAX_Y = 13;

    /**
     * Representación del mapa del juego como una matriz de caracteres.
     * Cada fila representa una coordenada Y y cada columna una coordenada X.
     */
    private static final char[][] MAP = {
        "TTTWWTWTWWW....".toCharArray(), // y=0
        "S==..=T..==....".toCharArray(), // y=1
        ".....T=...=....".toCharArray(), // y=2
        "..|.......=....".toCharArray(), // y=3
        "..|..|.........".toCharArray(), // y=4
        "..|..|..===....".toCharArray(), // y=5
        "..|..|.........".toCharArray(), // y=6
        "..|==|.........".toCharArray(), // y=7
        ".....|.====....".toCharArray(), // y=8
        "...............".toCharArray(), // y=9
        "...............".toCharArray(), // y=10
        "...............".toCharArray(), // y=11
        "======.........".toCharArray(), // y=12
        "G..............".toCharArray(), // y=13
        "...............".toCharArray()  // y=14
        
        
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
    private static Character tileAt(Integer x, Integer y) {
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
    private static Boolean isWater(Integer x, Integer y) {
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
    private static Boolean isSolidTile(Character t) {
        return t == 'T'   // tierra que sale del agua
            || t == '='   // plataforma
            || t == '|'   // liana (colgado)
            || t == 'S';   // spawn
    }

     /**
     * Tiles que bloquean al subir (techo).
     * OJO: aquí NO incluimos 'G' para que la zona de ganar no bloquee el paso.
     */
    private static boolean isBlockingCeiling(Character t) {
        return t == 'T'   // tierra
            || t == '='   // plataforma
            || t == 'S';  // spawn (por si hay uno encima)
        // 'G' (meta) a propósito NO se incluye
    }


    private static boolean isLiana(Character t) {
        return t != null && t == '|';
    }

    /**
     * Indica si en la posición (x, y) del mapa hay una liana ('|').
     */
    public static boolean isLianaAt(Integer x, Integer y) {
        return tileAt(x, y) == '|';
    }


     /**
     * Devuelve true si el jugador tiene un bloque "sólido" justo debajo
     * (plataforma, tierra, liana, spawn o meta).
     * Ojo: el jugador está en (x, y) pero el bloque de apoyo está en (x, y-1).
     */
    private static boolean hasSolidBelow(Integer x, Integer y) {
        if (y <= MIN_Y) return false; // no hay nada más abajo
        Character below = tileAt(x, y - 1);
        return isSolidTile(below);
    }

    /**
     * Se considera "apoyado" cuando:
     *  - Está colgado de una liana (tile actual = '|'), o
     *  - Tiene un bloque sólido justo debajo.
     *
     * Esto cubre:
     *  - De pie sobre plataformas (jugador en '.', plataforma en y-1)
     *  - Colgado de lianas (jugador en '|')
     */
    private static boolean isSupported(Integer x, Integer y) {
        Character here = tileAt(x, y);
        // Si estoy parado en un tile sólido, ya con eso basta
        if (isSolidTile(here)) {
            return true;
        }

        // O si tengo un sólido justo debajo (estoy 1 casilla por encima de la plataforma)
        return hasSolidBelow(x, y);
    }

    private static boolean isPlatformLike(Character t) {
        return t != null && (t == '=' || t == 'T' || t == 'S');
    }

    /**
     * Indica si el jugador puede moverse una casilla hacia arriba desde (x,y),
     * sin atravesar un techo.
     */
    private static boolean canMoveUpFrom(Integer x, Integer y) {
        Character here  = tileAt(x, y);
        Character above = tileAt(x, y + 1);

        // Techo: cualquier tile sólido arriba que NO sea liana
        if (isSolidTile(above) && !isLiana(above)) {
            return false;
        }

        // Solo se puede subir si estás en liana o en una superficie sólida
        return isLiana(here) || isPlatformLike(here);
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

        ticker.scheduleAtFixedRate(this::tick, 125, 125, TimeUnit.MILLISECONDS);
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

    private Integer enemiesBroadcastCounter = 0;
    private static final Integer ENEMIES_BROADCAST_EVERY = 2;

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
        // 1) De TODOS los inputs pendientes, nos quedamos con el "mejor" de cada jugador:
        //    - Preferimos saltos (dy > 0) sobre movimientos normales.
        //    - A igual dy, preferimos el de mayor |dx| (p.ej. dx=±2 para salto horizontal).
        Map<Integer, InputEvent> bestInputByPlayer = new HashMap<>();

        InputEvent ev;
        while ((ev = inputQueue.poll()) != null) {
            if (ev == null) continue;

            InputEvent prev = bestInputByPlayer.get(ev.playerId);

            if (prev == null) {
                bestInputByPlayer.put(ev.playerId, ev);
            } else {
                // 1) Si el nuevo tiene más "dy" (es salto y el otro no), gana el nuevo
                if (ev.dy > prev.dy) {
                    bestInputByPlayer.put(ev.playerId, ev);
                }
                // 2) Si tienen el mismo dy, preferimos el que tenga |dx| más grande
                else if (ev.dy.equals(prev.dy)
                        && Math.abs(ev.dx) > Math.abs(prev.dx)) {
                    bestInputByPlayer.put(ev.playerId, ev);
                }
                // 3) En cualquier otro caso, mantenemos el anterior
            }
        }

        // Set para saber quién saltó en este tick (para la gravedad)
        Set<Integer> jumpedThisTick = new HashSet<>();

        // Aplicamos SOLO un movimiento por jugador en este tick
        for (InputEvent input : bestInputByPlayer.values()) {
            Player p = players.get(input.playerId);
            if (p == null) continue;

            Integer oldY = p.y;
            Integer nx   = p.x + input.dx;
            Integer ny   = p.y + input.dy;

            // Límites del mapa
            if (nx < MIN_X) nx = MIN_X;
            if (nx > MAX_X) nx = MAX_X;
            if (ny < MIN_Y) ny = MIN_Y;
            if (ny > MAX_Y) ny = MAX_Y;

            // Colisión con paredes (T y =)
            Character dest = tileAt(nx, ny);
            if (dest != null && (dest == 'T' || dest == '=')) {
                nx = p.x;
                ny = p.y;
            }

            p.x = nx;
            p.y = ny;
            p.lastAckSeq = Math.max(p.lastAckSeq, input.seq);

            // Si en este tick subió al menos 1 casilla, marcamos que "saltó"
            if (ny > oldY) {
                jumpedThisTick.add(input.playerId);
            }
        }

        // 1b) GRAVEDAD + agua (igual que ya lo tienes, usando jumpedThisTick)
        sessions.forEach((pid, session) -> {
            Player p = players.get(pid);
            if (p == null) return;

            if (!jumpedThisTick.contains(pid)) {
                if (p.y > MIN_Y && !hasSolidBelow(p.x, p.y)) {
                    p.y -= 1;
                }
            }

            if (isWater(p.x, p.y)) {
                handlePlayerHit(session, p);
            }
        });


        // 2) Simulación de enemigos + colisiones
        sessions.forEach((pid, session) -> {
            Player p = players.get(pid);
            if (p == null) return;

            boolean hitThisTick = false;

            // Lista temporal para eliminar blue crocs que llegan a y=0
            List<Enemy> toRemove = new ArrayList<>();
            Boolean enemiesChangedThisTick = false;

            for (Enemy e : session.enemies) {
                int oldEx = e.getX();
                int oldEy = e.getY();

                e.tick(MIN_Y, MAX_Y, p.round);

                // Si el enemigo dejó de estar activo (BlueCroc que llegó a y=0)
                if (!e.isActive()) {
                    toRemove.add(e);
                    enemiesChangedThisTick = true;
                    continue;
                }

                if (e.getX() != oldEx || e.getY() != oldEy){
                    enemiesChangedThisTick = true;
                }

                // Colisión exacta con el jugador (misma casilla)
                if (!hitThisTick
                        && e.getX().equals(p.x)
                        && e.getY().equals(p.y)) {
                    handlePlayerHit(session, p);
                    hitThisTick = true;
                }
            }

            if (!toRemove.isEmpty()) {
                session.enemies.removeAll(toRemove);
            }

            if (enemiesChangedThisTick){
                session.enemiesDirty = true;
            }

            // FRUTAS (tu código tal cual)
            boolean fruitsChanged = false;
            Iterator<Fruit> it = session.fruits.iterator();
            while (it.hasNext()) {
                Fruit f = it.next();
                if (f.getX().equals(p.x) && f.getY().equals(p.y)) {
                    p.score += f.getPoints();
                    it.remove();
                    fruitsChanged = true;
                }
            }
            if (fruitsChanged) {
                sendFruitsForPlayer(pid, session);
            }

            // META por tile...
            Character tileHere = tileAt(p.x, p.y);
            if (tileHere != null && tileHere == 'G') {
                p.round++;
                p.lives++;
                p.x = session.spawnX;
                p.y = session.spawnY;
                p.gameOver = false;
                resetFruitsFromTemplates(pid, session);
            }

            // 🔴 IMPORTANTE: enviar enemigos SIEMPRE en cada tick
            sendEnemiesForPlayer(pid, session);
        });



        // 2.5) Enviar enemigos solo cada ENEMIES_BROADCAST_EVERY ticks
        enemiesBroadcastCounter++;
        if (enemiesBroadcastCounter % ENEMIES_BROADCAST_EVERY == 0) {
            sessions.forEach((pid, session) -> {
                if (session.enemiesDirty) {
                    sendEnemiesForPlayer(pid, session);
                    session.enemiesDirty = false;
                }
            });
        }



        // 3) Notificar estado a los clientes
        Integer seq = tickSeq.incrementAndGet();
        players.forEach((id, p) -> {
            String state = String.format(
                    Locale.ROOT,
                    "STATE %d %d %d %d %d %d %d %b%n",
                    seq, id,
                    p.x, p.y,
                    p.score,
                    p.round,   // nivel
                    p.lives,   // vidas
                    p.gameOver
            );
            sendToPlayerAndSpectators(id, state);
        });


    }


    /* ========= Eventos desde ClientHandler ========= */

    /**
     * Maneja una solicitud de JOIN de un cliente para participar como jugador.
     *
     * <p>Flujo general:</p>
     * <ol>
     *     <li>Genera un nuevo identificador de jugador y crea la instancia
     *         correspondiente de {@link Player} y {@link GameSession}.</li>
     *     <li>Envía al cliente la línea de confirmación
     *         <code>JOINED &lt;playerId&gt;</code> seguida de la descripción
     *         completa del mapa lógico mediante {@link #sendMapTo(ClientHandler)}.
     *         En este punto, el cliente ya dispone de toda la información
     *         necesaria para dibujar el escenario.</li>
     *     <li>Registra finalmente al jugador en las estructuras internas
     *         {@link #players}, {@link #sessions} y {@link #byClient}, de modo
     *         que el bucle de juego {@link #tick()} pueda empezar a enviar
     *         mensajes de estado <code>STATE ...</code>.</li>
     * </ol>
     *
     * @param c
     *     Manejador del cliente que envía la solicitud de unión como jugador.
     * @param name
     *     Nombre de jugador solicitado. Se almacenará en la instancia
     *     correspondiente de {@link Player}.
     */
    public void onJoin(ClientHandler c, String name) {
    Integer id = nextId.getAndIncrement();
    Player p = new Player(id, name);

    // Crear sesión de juego (aún no registrada globalmente)
    GameSession session = new GameSession(id);
    session.loadFromTemplates(templateEnemies, templateFruits);

    // ==== POSICIONAR EN SPAWN ====
    p.x = session.spawnX;
    p.y = session.spawnY;
    p.gameOver = false;   // por si acaso
    // (p.round y p.lives ya los inicializaste en el constructor)
    // ==============================

    // 1) Notificar al cliente que se ha unido correctamente
    c.sendLine("JOINED " + id + "\n");

    // 2) Enviar la descripción del mapa lógico antes de que empiecen los STATE
    sendMapTo(c);

    // 3) Registrar al jugador en las estructuras globales
    players.put(id, p);
    byClient.put(c, id);
    playerClients.add(c);
    sessions.put(id, session);
    sendFruitsForPlayer(id, session);
    sendEnemiesForPlayer(id, session);

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
    public void onInput(ClientHandler c, Integer seq, Integer dx, Integer dy) {
        Integer id = byClient.get(c);
        if (id == null) {
            c.sendLine("ERR NOT_PLAYER\n");
            return;
        }

        Player p = players.get(id);
        if (p == null) {
            return;
        }

        // Permitimos movimientos de -1,0,+1 y saltos diagonales con dx = ±2
        if (Math.abs(dx) > 3 || Math.abs(dy) > 1) {
            c.sendLine("ERR STEP_TOO_BIG\n");
            return;
        }

        // Saltos diagonales: si dx = ±2 pero no sube, lo anulamos
        if (Math.abs(dx) == 2 && dy != 1) {
            dx = 0;
        }

        if (dy > 0) {
            // 1) Solo puede subir si está apoyado (sobre spawn, plataforma, liana, etc.)
            if (!isSupported(p.x, p.y)) {
                dx = 0;
                dy = 0;
            } else {
                // 2) No puede haber un “techo” sólido justo encima
                Character above = tileAt(p.x, p.y + 1);
                if (isSolidTile(above) && !isLiana(above)) {
                    dx = 0;
                    dy = 0;
                }
            }
        }

        if (dx == 0 && dy == 0) {
            return;
        }

        inputQueue.offer(new InputEvent(id, seq, dx, dy));
    }   




    /**
     * Maneja la solicitud de un cliente para unirse como espectador de un jugador.
     *
     * <p>Desde el punto de vista del patrón de diseño <b>Observador</b>, este
     * método realiza la operación de <em>suscripción</em> de un nuevo
     * observador:</p>
     * <ul>
     *     <li>El jugador identificado por {@code playerId} actúa como
     *         <em>sujeto observado</em> (su estado se actualizará en cada tick).</li>
     *     <li>El {@link ClientHandler} {@code c} se registra como uno de sus
     *         <em>observadores</em> en la estructura
     *         {@link #spectatorsByPlayer}.</li>
     * </ul>
     *
     * <p>Si el jugador ya existe, el cliente se añade directamente a la lista
     * de espectadores y se le envía la confirmación
     * <code>SPECTATE_OK &lt;playerId&gt;</code>, seguida del mapa lógico
     * mediante {@link #sendMapTo(ClientHandler)}. Si el jugador aún no existe,
     * el cliente se inserta en {@link #waitingSpectatorsByPlayer} y se le
     * responde <code>SPECTATE_WAIT &lt;playerId&gt;</code>.</p>
     *
     * @param c
     *     Manejador del cliente que solicita unirse como espectador.
     * @param playerId
     *     Identificador del jugador cuya sesión se desea observar.
     */
    public void onSpectate(ClientHandler c, Integer playerId) {
        Player p = players.get(playerId);
        if (p != null) {
            spectatorsByPlayer
                .computeIfAbsent(playerId, k -> new CopyOnWriteArrayList<>())
                .add(c);
            c.sendLine("SPECTATE_OK " + playerId + "\n");

            // Enviar la descripción del mapa lógico al nuevo espectador
            sendMapTo(c);

        } else {
            waitingSpectatorsByPlayer
                .computeIfAbsent(playerId, k -> new CopyOnWriteArrayList<>())
                .add(c);
            c.sendLine("SPECTATE_WAIT " + playerId + "\n");
        }
    }

    /**
     * Envía al cliente la lista de jugadores actualmente activos en el servidor.
     *
     * <p>El formato del mensaje enviado implementa un pequeño protocolo de
     * listado de jugadores:</p>
     *
     * <pre>
     * PLAYERS_BEGIN
     * PLAYER &lt;id&gt; &lt;name&gt;
     * PLAYER &lt;id&gt; &lt;name&gt;
     * ...
     * PLAYERS_END
     * </pre>
     *
     * <p>La intención es que un cliente (por ejemplo, en modo espectador)
     * pueda solicitar esta lista para permitir al usuario seleccionar a qué
     * jugador desea observar. Este método únicamente envía información al
     * {@link ClientHandler} indicado; no modifica el estado interno del
     * servidor.</p>
     *
     * @param clientHandler
     *     Manejador del cliente que solicitó la lista de jugadores. Si es
     *     {@code null}, este método no realiza ninguna acción.
     */
    public void onListPlayers(ClientHandler clientHandler) {
        if (clientHandler == null) {
            return;
        }

        clientHandler.sendLine("PLAYERS_BEGIN\n");

        // Recorremos el mapa de jugadores activos y enviamos un renglón por cada uno
        players.forEach((id, player) -> {
            // Usamos directamente el campo público 'name' del jugador
            String name = player.name;
            clientHandler.sendLine(
                    String.format(
                            java.util.Locale.ROOT,
                            "PLAYER %d %s%n",
                            id,
                            name
                    )
            );
        });

        clientHandler.sendLine("PLAYERS_END\n");
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
    private void endPlayerSession(Integer playerId) {
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
     * Restaura las frutas de la sesión a partir de las plantillas globales.
     * Se usa cuando el jugador llega a la meta para que reaparezcan en
     * las mismas coordenadas donde el administrador las configuró.
     */
        private void resetFruitsFromTemplates(Integer playerId, GameSession session) {
        if (session == null) return;

        session.fruits.clear();
        for (Fruit tf : templateFruits) {
            session.fruits.add(
                factory.createFruit(tf.getX(), tf.getY(), tf.getPoints())
            );
        }

        // Enviar al jugador la nueva lista de frutas
        sendFruitsForPlayer(playerId, session);
    }



    /**
     * Maneja cuando el jugador recibe daño (agua o enemigo).
     * - Resta 1 vida.
     * - Si aún le quedan vidas, respawnea en el spawn de la sesión.
     * - Si se queda sin vidas, marca gameOver = true.
     */
    private void handlePlayerHit(GameSession session, Player p) {
        if (p == null || session == null) return;

        if (p.lives > 0) {
            p.lives--;
        }

        if (p.lives <= 0) {
            // Sin vidas → Game Over, se queda donde esté
            p.gameOver = true;
        } else {
            // Aún tiene vidas → respawn en el spawn de la sesión
            p.x = session.spawnX;
            p.y = session.spawnY;
            p.gameOver = false;
        }
    }


    /**
     * Envía una línea de texto al jugador y a todos los espectadores asociados.
     *
     * <p>Este método actúa como la operación de "notificación" dentro del
     * patrón de diseño <b>Observador</b>. Para el {@code playerId} indicado,
     * se localiza:</p>
     * <ul>
     *     <li>El cliente jugador asociado, usando el mapa {@link #byClient}.</li>
     *     <li>Todos los clientes espectadores registrados en
     *         {@link #spectatorsByPlayer}.</li>
     * </ul>
     *
     * <p>A cada uno de estos observadores se le envía la misma línea
     * de texto, que típicamente corresponde a un mensaje de estado como
     * <code>STATE ...</code>. De esta forma, el jugador y sus espectadores
     * reciben una vista consistente del estado de la partida.</p>
     *
     * @param playerId
     *     Identificador del jugador cuya sesión es la fuente de la actualización.
     * @param line
     *     Línea de texto a enviar (debe incluir el salto de línea si se requiere).
     */
    private void sendToPlayerAndSpectators(Integer playerId, String line) {
        // a jugador:
        for (Map.Entry<ClientHandler,Integer> e : byClient.entrySet()) {
            if (e.getValue() == playerId) e.getKey().sendLine(line);
        }
        // a espectadores:
        CopyOnWriteArrayList<ClientHandler> ls = spectatorsByPlayer.get(playerId);
        if (ls != null) for (ClientHandler ch : ls) ch.sendLine(line);
    }

    /**
     * Envía al cliente indicado la representación completa del mapa lógico del juego.
     *
     * <p>El formato de los mensajes enviados es el siguiente:</p>
     * <ul>
     *     <li><code>MAP_SIZE &lt;ancho&gt; &lt;alto&gt;</code>:
     *         indica el número de columnas (ancho) y filas (alto) del mapa.</li>
     *     <li><code>MAP_ROW &lt;y&gt; &lt;cadena_tiles&gt;</code>:
     *         una línea por cada fila del mapa, donde <code>y</code> es la
     *         coordenada vertical y <code>cadena_tiles</code> es la fila tal como
     *         está definida en {@link #MAP}.</li>
     *     <li><code>MAP_END</code>: marca el final de la descripción del mapa.</li>
     * </ul>
     *
     * <p>La intención es que el cliente (jugador o espectador) reciba toda la
     * información necesaria para dibujar el escenario utilizando los mismos
     * caracteres que el servidor usa internamente:</p>
     * <ul>
     *     <li><code>'W'</code> → agua</li>
     *     <li><code>'T'</code> → tierra que sobresale del agua</li>
     *     <li><code>'='</code> → plataforma</li>
     *     <li><code>'|'</code> → liana</li>
     *     <li><code>'S'</code> → casilla de aparición (spawn)</li>
     *     <li><code>'G'</code> → meta o jaula de DK</li>
     *     <li><code>'.'</code> → vacío</li>
     * </ul>
     *
     * @param clientHandler
     *     Manejador del cliente al que se le enviará el mapa. Si es {@code null},
     *     este método no realiza ninguna acción.
     */
    private void sendMapTo(ClientHandler clientHandler) {
        if (clientHandler == null) {
            return;
        }

        // Dimensiones del mapa en coordenadas lógicas (tiles)
        Integer width  = MAX_X - MIN_X + 1;
        Integer height = MAX_Y - MIN_Y + 1;

        // Línea inicial con el tamaño del mapa
        clientHandler.sendLine(
                String.format(
                        java.util.Locale.ROOT,
                        "MAP_SIZE %d %d%n",
                        width,
                        height
                )
        );

        // Una línea por cada fila del mapa lógico
        for (Integer y = MIN_Y; y <= MAX_Y; y++) {
            String row = new String(MAP[y]);
            clientHandler.sendLine(
                    String.format(
                            java.util.Locale.ROOT,
                            "MAP_ROW %d %s%n",
                            y,
                            row
                    )
            );
        }

        // Marcador de fin de mapa
        clientHandler.sendLine("MAP_END\n");
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
     * Ejecuta un comando de administración.
     *
     * <p>Soporta dos modos:</p>
     * <ul>
     *     <li>Modificar las <b>plantillas globales</b> (comportamiento original).</li>
     *     <li>Modificar la <b>partida de un jugador específico</b> si se indica un ID.</li>
     * </ul>
     *
     * Formatos admitidos:
     * <pre>
     * ADMIN CROCODILE &lt;type&gt; &lt;liana&gt; [y] [playerId]
     * ADMIN FRUIT CREATE &lt;liana&gt; &lt;y&gt; &lt;pts&gt; [playerId]
     * ADMIN FRUIT DELETE &lt;liana&gt; &lt;y&gt; [playerId]
     * </pre>
     *
     * Si no se proporciona <code>playerId</code>, el cambio se aplica a las plantillas
     * {@link #templateEnemies} y {@link #templateFruits}. Si se proporciona, el cambio
     * se aplica únicamente a la sesión de juego correspondiente a ese jugador.
     *
     * @param line Línea de comando completa introducida por consola o por la UI.
     */
    public void runAdminCommand(String line) {
        try {
            if (line == null || line.trim().isEmpty()) {
                return;
            }

            String[] t = line.trim().split("\\s+");
            if (t.length < 2 || !"ADMIN".equalsIgnoreCase(t[0])) {
                System.out.println("[ADMIN] Comando inválido: " + line);
                return;
            }

            // ================== CROCODILE ==================
            if ("CROCODILE".equalsIgnoreCase(t[1])) {
                if (t.length < 4) {
                    System.out.println("[ADMIN] Uso: ADMIN CROCODILE <type> <liana> [y] [playerId]");
                    return;
                }

                String type  = t[2];
                Integer liana    = Integer.parseInt(t[3]);
                Integer y;
                Integer playerId = null;

                if (t.length == 4) {
                    // Sin Y ni playerId → usar MIN_Y, plantilla global
                    y = MIN_Y;
                } else if (t.length == 5) {
                    // Tiene Y, pero no playerId
                    y = Integer.parseInt(t[4]);
                } else {
                    // Tiene Y y playerId
                    y = Integer.parseInt(t[4]);
                    playerId = Integer.parseInt(t[5]);
                }

                if (playerId == null) {
                    // Plantilla global
                    templateEnemies.add(factory.createCrocodile(type, liana, y));
                    System.out.println("[ADMIN] CROCODILE " + type + " @" + liana + "," + y + " (plantilla)");
                } else {
                    // Solo para ese jugador
                    addEnemyToPlayerSession(playerId, type, liana, y);
                }
                return;
            }

            // ================== FRUIT ==================
            if ("FRUIT".equalsIgnoreCase(t[1]) && t.length >= 3) {
                String sub = t[2];

                // ---------- CREATE ----------
                if ("CREATE".equalsIgnoreCase(sub)) {
                    if (t.length < 6) {
                        System.out.println("[ADMIN] Uso: ADMIN FRUIT CREATE <liana> <y> <pts> [playerId]");
                        return;
                    }
                    Integer l   = Integer.parseInt(t[3]);
                    Integer y   = Integer.parseInt(t[4]);
                    Integer pts = Integer.parseInt(t[5]);
                    Integer playerId = (t.length >= 7) ? Integer.parseInt(t[6]) : null;

                    // Validar que la fruta se coloca en una casilla "pisable"
                    Character tile = tileAt(l, y);
                    if (tile != '.' && tile != '|' && tile != 'S' && tile != 'G') {
                        System.out.println("[ADMIN] No se puede crear fruta en un tile bloqueante (agua, tierra o plataforma).");
                        return;
                    }


                    if (playerId == null) {
                        templateFruits.add(factory.createFruit(l, y, pts));
                        System.out.println("[ADMIN] FRUIT +" + l + "," + y + " pts=" + pts + " (plantilla)");
                    } else {
                        addFruitToPlayerSession(playerId, l, y, pts);
                    }
                    return;
                }

                // ---------- DELETE ----------
                if ("DELETE".equalsIgnoreCase(sub)) {
                    if (t.length < 5) {
                        System.out.println("[ADMIN] Uso: ADMIN FRUIT DELETE <liana> <y> [playerId]");
                        return;
                    }
                    Integer l = Integer.parseInt(t[3]);
                    Integer y = Integer.parseInt(t[4]);
                    Integer playerId = (t.length >= 6) ? Integer.parseInt(t[5]) : null;

                    if (playerId == null) {
                        templateFruits.removeIf(f -> f.getX() == l && f.getY() == y);
                        System.out.println("[ADMIN] FRUIT -" + l + "," + y + " (plantilla)");
                    } else {
                        removeFruitFromPlayerSession(playerId, l, y);
                    }
                    return;
                }
            }

            System.out.println("[ADMIN] Comando no reconocido: " + line);
        } catch (Exception ex) {
            System.out.println("[ADMIN] Error al procesar comando: " + line);
            ex.printStackTrace();
        }
    }


    // =====================
    //  Helpers de Admin
    // =====================

    /**
     * Agrega un cocodrilo a la sesión de juego de un jugador específico.
     *
     * @param playerId ID del jugador cuya partida se quiere modificar.
     * @param type     Tipo de cocodrilo ("RED" o "BLUE").
     * @param liana    Coordenada X o liana donde se ubica el enemigo.
     * @param y        Coordenada Y inicial del enemigo.
     */
    private void addEnemyToPlayerSession(Integer playerId, String type, Integer liana, Integer y) {
        GameSession session = sessions.get(playerId);
        if (session == null) {
            System.out.println("[ADMIN] No existe sesión para jugador " + playerId);
            return;
        }

        Enemy enemy = factory.createCrocodile(type, liana, y);
        session.enemies.add(enemy);
        System.out.println("[ADMIN] CROCODILE " + type + " @" + liana + "," + y +
                " → jugador " + playerId);

        sendEnemiesForPlayer(playerId, session);
    }

    /**
     * Agrega una fruta a la sesión de juego de un jugador específico.
     *
     * @param playerId ID del jugador cuya partida se quiere modificar.
     * @param l        Coordenada X (liana) de la fruta.
     * @param y        Coordenada Y de la fruta.
     * @param pts      Puntos que otorga la fruta.
     */
    private void addFruitToPlayerSession(Integer playerId, Integer l, Integer y, Integer pts) {
        GameSession session = sessions.get(playerId);
        if (session == null) {
            System.out.println("[ADMIN] No existe sesión para jugador " + playerId);
            return;
        }

        Fruit fruit = factory.createFruit(l, y, pts);
        session.fruits.add(fruit);
        System.out.println("[ADMIN] FRUIT +" + l + "," + y + " pts=" + pts +
                " → jugador " + playerId);
        
        sendFruitsForPlayer(playerId, session);
    }

    /**
     * Elimina una fruta de la sesión de juego de un jugador específico.
     *
     * @param playerId ID del jugador cuya partida se quiere modificar.
     * @param l        Coordenada X (liana) de la fruta.
     * @param y        Coordenada Y de la fruta.
     */
    private void removeFruitFromPlayerSession(Integer playerId, Integer l, Integer y) {
        GameSession session = sessions.get(playerId);
        if (session == null) {
            System.out.println("[ADMIN] No existe sesión para jugador " + playerId);
            return;
        }

        session.fruits.removeIf(f -> f.getX() == l && f.getY() == y);
        System.out.println("[ADMIN] FRUIT -" + l + "," + y + " → jugador " + playerId);

        sendFruitsForPlayer(playerId, session);
    }

    /**
     * Envía al jugador (y sus espectadores) la lista completa de frutas
     * de su sesión actual.
     */
    private void sendFruitsForPlayer(Integer playerId, GameSession session) {
        if (session == null) return;

        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "FRUITS_BEGIN %d%n", playerId));
        for (Fruit f : session.fruits) {
            sb.append(String.format(Locale.ROOT,
                    "FRUIT %d %d %d%n",
                    f.getX(), f.getY(), f.getPoints()));
        }
        sb.append(String.format(Locale.ROOT, "FRUITS_END %d%n", playerId));

        sendToPlayerAndSpectators(playerId, sb.toString());
    }

    /**
     * Envía al jugador (y sus espectadores) la lista completa de enemigos
     * (cocodrilos) de su sesión actual.
     */
    private void sendEnemiesForPlayer(Integer playerId, GameSession session) {
        if (session == null) return;

        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "ENEMIES_BEGIN %d%n", playerId));

        for (Enemy e : session.enemies) {
            String type = e.getType();  // "RED" o "BLUE", según tu implementación
            if (type == null) type = "GENERIC";

            sb.append(String.format(
                    Locale.ROOT,
                    "ENEMY %s %d %d%n",
                    type,
                    e.getX(),
                    e.getY()
            ));
        }

        sb.append(String.format(Locale.ROOT, "ENEMIES_END %d%n", playerId));

        sendToPlayerAndSpectators(playerId, sb.toString());
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

