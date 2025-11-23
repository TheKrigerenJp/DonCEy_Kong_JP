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
    /** Vidas del jugador */
    public Integer lives;
    /** Último número de secuencia de input reconocido/acknowledgeado por el servidor. */
    public Integer lastAckSeq;
    /** Puntaje acumulado por el jugador. */
    public Integer score;
    /** Ronda o nivel actual del jugador. */
    public Integer round;
    /** Indica si el juego ha terminado para este jugador. */
    public Boolean gameOver;   

    /**
     * Crea un nuevo jugador.
     *
     * @param id identificador único
     * @param name nombre del jugador
     * @param startX posición inicial X
     * @param startY posición inicial Y
     */
    public Player(Integer id, String name) {
         /** @return id del jugador */
        this.id = id;
        /** @return nombre del jugador */
        this.name = name;
        /** @return posición horizontal */
        this.x = 0;
        /** @return posición vertical */
        this.y = 0;
        this.lastAckSeq = 0;
        /** @return puntaje del jugador */
        this.score = 0;
        /** @return ronda en la que esta el jugador */
        this.round = 1;
        /** @return si el jugador perdio o no */
        this.gameOver = false;
        /** @return cantidad de vidas del jugador */
        this.lives = 3;
    }
}

