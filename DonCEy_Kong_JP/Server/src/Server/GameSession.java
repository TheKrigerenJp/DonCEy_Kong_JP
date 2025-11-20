package Server;

//import java.util.Iterator; NO SE ESTA USANDO
import java.util.List;
import java.util.ArrayList;

import Server.entities.Enemy;
import Server.entities.Fruit;
import Server.entities.RedCroc;
import Server.entities.BlueCroc;
import Server.entities.SimpleFruit;

/**
 * Representa la sesión de juego individual asociada a un jugador.
 * <p>
 * En esta sesión se almacena la información de spawn, meta y las listas
 * de enemigos y frutas que se simulan específicamente para el jugador.
 * Además, permite cargar estos elementos a partir de plantillas definidas
 * en el servidor.
 * </p>
 */
public class GameSession {
    /** ID del jugador al que pertenece esta sesión. */
    public final Integer playerId;
    /** Coordenadas de aparición (spawn) del jugador en el mapa. */
    public Integer spawnX, spawnY;
    /** Coordenadas de la meta (goal) para el jugador. */
    public Integer goalX, goalY;
    /** Lista de enemigos activos en la sesión de este jugador. */
    public final List<Enemy> enemies = new ArrayList<>();
    /** Lista de frutas activas en la sesión de este jugador. */
    public final List<Fruit> fruits  = new ArrayList<>();
    /** Hay enemigos o no */
    public Boolean hasEnemyChanges = false;

    /**
     * “Velocidad lógica” de enemigos para este jugador (pasos por tick).
     * Puede ajustarse para aumentar o disminuir la dificultad de la sesión.
     */
    public Integer enemySpeedSteps = 1;

    /**
     * Crea una nueva sesión de juego para el jugador indicado,
     * inicializando las posiciones de spawn y meta por defecto.
     *
     * @param playerId identificador del jugador dueño de esta sesión
     */
    public GameSession(Integer playerId) { 
        this.playerId = playerId;
        this.spawnX = 0;  // S está en x=0, y=1
        this.spawnY = 1;
        this.goalX  = 2;  // “GGG” empieza en x=2, y=10
        this.goalY  = 10; 
    }

    /**
     * Carga los enemigos y frutas de la sesión a partir de listas plantilla.
     * <p>
     * Este método limpia las listas actuales de {@link #enemies} y
     * {@link #fruits}, y crea nuevas instancias basadas en las plantillas
     * recibidas, de forma que cada sesión tenga sus propios objetos.
     * </p>
     *
     * @param tplEnemies lista plantilla de enemigos definida a nivel de servidor
     * @param tplFruits  lista plantilla de frutas definida a nivel de servidor
     */
    public void loadFromTemplates(List<Enemy> tplEnemies, List<Fruit> tplFruits) {
        enemies.clear();
        fruits.clear();

        for (Enemy e : tplEnemies) {
            String type = e.getType();
            // usar nombres simples gracias a los imports
            enemies.add("RED".equalsIgnoreCase(type)
                    ? new RedCroc(e.getX(), e.getY())
                    : new BlueCroc(e.getX(), e.getY()));
        }
        for (Fruit f : tplFruits) {
            fruits.add(new SimpleFruit(f.getX(), f.getY(), f.getPoints()));
        }
    }
}
