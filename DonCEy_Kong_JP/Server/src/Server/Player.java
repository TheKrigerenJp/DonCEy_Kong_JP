package Server;

/**
 * Representa el estado lógico de un jugador dentro del servidor.
 * <p>
 * Almacena información como su identificador, nombre, posición en el mapa,
 * puntaje acumulado, ronda actual y si la partida ha terminado para él.
 * Esta clase no contiene lógica de red; únicamente modela los datos del jugador.
 * </p>
 */
public class Player {
    /** Identificador único del jugador dentro del servidor. */
    public final Integer id;
    /** Nombre o alias del jugador. */
    public final String name;
    /** Coordenadas actuales del jugador en el mapa (x, y). */
    public Integer x, y;
    /** Último número de secuencia de input reconocido/acknowledgeado por el servidor. */
    public Integer lastAckSeq;
    /** Puntaje acumulado por el jugador. */
    public Integer score;
    /** Ronda o nivel actual del jugador. */
    public Integer round;
    /** Indica si el juego ha terminado para este jugador. */
    public Boolean gameOver;   

    /**
     * Crea un nuevo jugador con los valores iniciales por defecto.
     *
     * @param id    identificador único asignado por el servidor
     * @param name  nombre o alias con el que el jugador se identifica
     */
    public Player(Integer id, String name) {
        this.id = id;
        this.name = name;
        this.x = 0;
        this.y = 0;
        this.lastAckSeq = 0;
        this.score = 0;
        this.round = 1;
        this.gameOver = false;
    }
}

