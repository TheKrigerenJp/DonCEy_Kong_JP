package Server.entities;

/**
 * Representa a un enemigo genérico del juego.
 * <p>
 * Las subclases de {@code Enemy} definen el comportamiento específico de
 * cada tipo de enemigo (por ejemplo, cocodrilos rojos o azules), así como
 * su forma de moverse dentro del área de juego.
 * </p>
 */
public abstract class Enemy {

    /**
     * Actualiza el estado y/o posición del enemigo dentro de los límites permitidos.
     *
     * @param minY valor mínimo permitido de la coordenada vertical (por ejemplo, borde superior de la zona de juego)
     * @param maxY valor máximo permitido de la coordenada vertical (por ejemplo, borde inferior de la zona de juego)
     */
    public abstract void tick(Integer minY, Integer maxY);

    /**
     * Obtiene la posición horizontal actual del enemigo.
     *
     * @return coordenada X del enemigo (por ejemplo, índice de liana o columna del mapa)
     */
    public abstract Integer getX();

    /**
     * Obtiene la posición vertical actual del enemigo.
     *
     * @return coordenada Y del enemigo (por ejemplo, fila o altura dentro del mapa)
     */
    public abstract Integer getY();

    /**
     * Obtiene el tipo de enemigo.
     *
     * @return una cadena que identifica el tipo de enemigo, por ejemplo
     *         {@code "RED"} o {@code "BLUE"}.
     */
    public abstract String getType();


    /**
     * Por defecto todos los enemigos están activos.
     * BlueCroc lo sobrescribirá para poder "desaparecer".
     */
    public Boolean isActive() {
        return true;
    }
}
