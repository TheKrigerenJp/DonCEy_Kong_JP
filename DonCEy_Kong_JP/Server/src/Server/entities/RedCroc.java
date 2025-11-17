package Server.entities;

/**
 * Representa un cocodrilo rojo en el juego.
 * <p>
 * Este enemigo se mueve hacia arriba y hacia abajo entre dos límites verticales.
 * Cuando alcanza uno de los extremos, invierte su dirección de movimiento.
 * </p>
 */
public class RedCroc extends Enemy {
    /** Liana o columna del mapa donde se ubica el cocodrilo. */
    private final int liana;
    /** Posición vertical actual del cocodrilo. */
    private int y;
    /** Dirección de movimiento: +1 hacia abajo, -1 hacia arriba. */
    private int dir = +1;

    /**
     * Crea un nuevo cocodrilo rojo.
     *
     * @param liana columna o liana donde se colocará el enemigo
     * @param y     posición vertical inicial del enemigo dentro de la liana
     */
    public RedCroc(int liana, int y) {
        this.liana = liana;
        this.y = y;
    }

    /**
     * Actualiza la posición del cocodrilo rojo dentro de los límites indicados.
     * <ul>
     *     <li>Si llega al límite superior ({@code minY}), cambia la dirección hacia abajo.</li>
     *     <li>Si llega al límite inferior ({@code maxY}), cambia la dirección hacia arriba.</li>
     * </ul>
     *
     * @param minY valor mínimo permitido de la coordenada Y
     * @param maxY valor máximo permitido de la coordenada Y
     */
    @Override
    public void tick(int minY, int maxY) {
        y += dir;
        if (y <= minY) {
            y = minY;
            dir = +1;
        }
        if (y >= maxY) {
            y = maxY;
            dir = -1;
        }
    }

    /**
     * Obtiene la posición horizontal actual del cocodrilo.
     *
     * @return coordenada X (liana o columna) donde se encuentra el cocodrilo
     */
    @Override
    public int getX() {
        return liana;
    }

    /**
     * Obtiene la posición vertical actual del cocodrilo.
     *
     * @return coordenada Y actual del cocodrilo
     */
    @Override
    public int getY() {
        return y;
    }

    /**
     * Devuelve el tipo de enemigo.
     *
     * @return la cadena {@code "RED"}, que identifica a este enemigo como cocodrilo rojo
     */
    @Override
    public String getType() {
        return "RED";
    }
}
