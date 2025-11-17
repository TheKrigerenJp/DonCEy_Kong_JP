package Server.entities;

/**
 * Representa un cocodrilo azul en el juego.
 * <p>
 * Este enemigo se desplaza hacia abajo y, cuando sale del límite inferior,
 * reaparece en el límite superior de la zona de juego.
 * </p>
 */
public class BlueCroc extends Enemy {
    /** Liana o columna del mapa donde se ubica el cocodrilo. */
    private final Integer liana;
    /** Posición vertical actual del cocodrilo. */
    private Integer y;

    /**
     * Crea un nuevo cocodrilo azul.
     *
     * @param liana columna o liana donde se colocará el enemigo
     * @param y     posición vertical inicial del enemigo dentro de la liana
     */
    public BlueCroc(Integer liana, Integer y) {
        this.liana = liana;
        this.y = y;
    }

    /**
     * Actualiza la posición del cocodrilo azul, desplazándolo hacia abajo.
     * Si supera el límite inferior, su posición se reinicia en el límite superior.
     *
     * @param minY valor mínimo permitido de la coordenada Y
     * @param maxY valor máximo permitido de la coordenada Y
     */
    @Override
    public void tick(Integer minY, Integer maxY) {
        y++;
        if (y > maxY) {
            // “Cae” y reaparece arriba.
            y = minY;
        }
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
     * @return la cadena {@code "BLUE"}, que identifica a este enemigo como cocodrilo azul
     */
    @Override
    public String getType() {
        return "BLUE";
    }
}

