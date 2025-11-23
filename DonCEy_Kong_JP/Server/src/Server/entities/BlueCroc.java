package Server.entities;

/**
 * Representa un cocodrilo azul que se desplaza verticalmente hacia arriba.
 * <p>
 * Cuando llega al límite superior permitido, deja de estar activo.
 * </p>
 */
public class BlueCroc extends Enemy {

    /** Posición horizontal */
    private Integer x;
    /** Posición vertical */
    private Integer y;
    /** Indica si el enemigo sigue existiendo en el mapa */
    private Boolean active = true;
    /** Contador interno de ticks usado para animación o movimiento */
    private Integer tickCounter = 0;

    /**
     * Construye un nuevo cocodrilo azul.
     *
     * @param x posición horizontal inicial
     * @param y posición vertical inicial
     */
    public BlueCroc(Integer x, Integer y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Actualiza el movimiento del cocodrilo azul.
     * Se mueve hacia arriba y desaparece si alcanza el límite.
     *
     * @param minY coordenada vertical mínima permitida
     * @param maxY coordenada vertical máxima permitida
     */
    @Override
    public void tick(Integer minY, Integer maxY, Integer level) {
        if (!active) return;

        tickCounter++;

        Integer lvl = (level == null || level < 1) ? 1 : level;
        Integer stepTicks = 6 - lvl;
        if (stepTicks < 1) stepTicks = 1;

        if (tickCounter % stepTicks != 0) {
            return;
        }

        if (y > minY) {
            y = y - 1;
        } else {
            active = false;
        }
    }

    /** {@inheritDoc} */
    @Override public Boolean isActive() { return active; }
    /** {@inheritDoc} */
    @Override public Integer getX() { return x; }
    /** {@inheritDoc} */
    @Override public Integer getY() { return y; }
    /** {@inheritDoc} */
    @Override public String getType() { return "BLUE"; }
}
