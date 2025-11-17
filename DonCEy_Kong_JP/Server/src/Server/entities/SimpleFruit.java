package Server.entities;

/**
 * Implementación simple de una fruta del juego.
 * <p>
 * Esta fruta tiene una posición fija (liana y altura) y una cantidad de puntos
 * asociada que se otorga al jugador cuando la recoge.
 * </p>
 */
public class SimpleFruit extends Fruit {
    /** Columna o liana donde se ubica la fruta. */
    private final int liana;
    /** Posición vertical de la fruta. */
    private final int y;
    /** Puntos que otorga la fruta al ser recogida. */
    private final int points;

    /**
     * Crea una fruta simple.
     *
     * @param liana  coordenada X o liana donde se ubicará la fruta
     * @param y      coordenada Y o altura donde se ubicará la fruta
     * @param points cantidad de puntos que otorga al jugador
     */
    public SimpleFruit(int liana, int y, int points) {
        this.liana = liana;
        this.y = y;
        this.points = points;
    }

    /**
     * Obtiene la posición horizontal de la fruta.
     *
     * @return coordenada X (liana o columna) donde se encuentra la fruta
     */
    @Override
    public int getX() {
        return liana;
    }

    /**
     * Obtiene la posición vertical de la fruta.
     *
     * @return coordenada Y donde se encuentra la fruta
     */
    @Override
    public int getY() {
        return y;
    }

    /**
     * Obtiene la cantidad de puntos que otorga la fruta.
     *
     * @return número de puntos sumados al marcador del jugador
     */
    @Override
    public int getPoints() {
        return points;
    }
}

