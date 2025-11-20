package Server.entities;

import Server.Server;

/**
 * Representa un cocodrilo rojo en el juego.
 * <p>
 * Este enemigo se mueve hacia arriba y hacia abajo entre dos límites verticales.
 * Cuando alcanza uno de los extremos, invierte su dirección de movimiento.
 * </p>
 */
public class RedCroc extends Enemy {
    /** Liana o columna del mapa donde se ubica el cocodrilo. */
    private final Integer liana;
    /** Posición vertical actual del cocodrilo. */
    private Integer y;
    /** Posición horizontal actual del cocodrilo */
    private Integer x;
    /** Dirección de movimiento: +1 hacia abajo, -1 hacia arriba. */
    private Integer dir = +1;
    /** Contador de ticks para bajar la velocidad */
    private Integer tickCounter = 0;
    /** Se mueve solo cada 3 ticks -> más lento. */
    private static final Integer SPEED_DIVIDER = 3;

    /**
     * Crea un nuevo cocodrilo rojo.
     *
     * @param liana columna o liana donde se colocará el enemigo
     * @param y     posición vertical inicial del enemigo dentro de la liana
     */
    public RedCroc(Integer liana, Integer y) {
        this.liana = liana;
        this.y = y;
        this.x = x;
        System.out.println("[ENEMY] RedCroc creado fuera de liana en " + x + "," + y);
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
    public void tick(Integer minY, Integer maxY) {
        tickCounter++;
        // Solo se mueve cuando tickCounter es múltiplo de SPEED_DIVIDER
        if (tickCounter % SPEED_DIVIDER != 0) {
            return;
        }

        Integer nextY = y + dir;

        // Si se sale del mapa o intenta salirse de la liana, invertimos dirección
        if (nextY < minY || nextY > maxY || !Server.isLianaAt(x, nextY)) {
            dir = -dir;
            nextY = y + dir;

            // Si aún así no hay casilla válida, no se mueve este tick
            if (nextY < minY || nextY > maxY || !Server.isLianaAt(x, nextY)) {
                return;
            }
        }

        // En este punto, sabemos que (x, nextY) sigue siendo una liana
        y = nextY;
    }

    /**
     * Obtiene la posición horizontal actual del cocodrilo.
     *
     * @return coordenada X (liana o columna) donde se encuentra el cocodrilo
     */
    @Override
    public Integer getX() {
        return liana;
    }

    /**
     * Obtiene la posición vertical actual del cocodrilo.
     *
     * @return coordenada Y actual del cocodrilo
     */
    @Override
    public Integer getY() {
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
