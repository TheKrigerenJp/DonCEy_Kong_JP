package Server.entities;

/**
 * Representa una fruta genérica que puede aparecer en el juego.
 * <p>
 * Las subclases de {@code Fruit} definen el tipo de fruta y
 * los puntos que otorga al jugador al ser recogida.
 * </p>
 */
public abstract class Fruit {

    /**
     * Obtiene la posición horizontal de la fruta.
     *
     * @return coordenada X de la fruta (por ejemplo, índice de liana o columna del mapa)
     */
    public abstract int getX();

    /**
     * Obtiene la posición vertical de la fruta.
     *
     * @return coordenada Y de la fruta (por ejemplo, fila o altura dentro del mapa)
     */
    public abstract int getY();

    /**
     * Obtiene la cantidad de puntos que otorga la fruta al ser recogida.
     *
     * @return número de puntos que suma al marcador del jugador
     */
    public abstract int getPoints();
}
