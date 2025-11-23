package Server.entities;

import Server.Server;

/**
 * Representa un cocodrilo rojo que se mueve verticalmente entre los límites de su liana.
 */
public class RedCroc extends Enemy {

    /** Posición horizontal */
    private Integer x;
    /** Posición vertical */
    private Integer y;

    /** +1 = sube, -1 = baja */
    private Integer dir = +1;

    /** Extremos de la liana para esta X */
    private final Integer minLianaY;
    private final Integer maxLianaY;

    /** Contador de ticks para controlar la velocidad */
    private Integer tickCounter = 0;

    /**
     * Crea un cocodrilo rojo en la liana indicada.
     *
     * @param x coordenada horizontal
     * @param startY posición inicial vertical
     * @param minY límite inferior de movimiento
     * @param maxY límite superior de movimiento
     */
    public RedCroc(Integer x, Integer y) {
        this.x = x;
        this.y = y;

        // Buscar todos los '|' en esta columna
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (Integer yy = Server.MIN_Y; yy <= Server.MAX_Y; yy++) {
            if (Server.isLianaAt(x, yy)) {
                if (yy < minY) minY = yy;
                if (yy > maxY) maxY = yy;
            }
        }

        if (minY == Integer.MAX_VALUE) {
            // No hay liana -> rango trivial
            minLianaY = y;
            maxLianaY = y;
            System.out.println("[RED] No se encontró liana en x=" + x);
        } else {
            minLianaY = minY;
            maxLianaY = maxY;
        }

        // Asegurarnos de que empieza dentro del rango
        if (this.y < minLianaY) this.y = minLianaY;
        if (this.y > maxLianaY) this.y = maxLianaY;
    }

    /**
     * Actualiza el movimiento del cocodrilo rojo.
     * Se mueve entre los límites de la liana y cambia de dirección al tocar un borde.
     */
    @Override
    public void tick(Integer minY, Integer maxY, Integer level) {
        tickCounter++;

        Integer lvl = (level == null || level < 1) ? 1 : level;
        Integer stepTicks = 6 - lvl;   // lvl=1 → 5, lvl=2 → 4, ..., lvl>=5 → 1
        if (stepTicks < 1) stepTicks = 1;

        if (tickCounter % stepTicks != 0) {
            return; // este tick no se mueve
        }

        Integer nextY = y + dir;

        if (nextY > maxLianaY || nextY < minLianaY) {
            dir = -dir;
            nextY = y + dir;
        }

        if (!Server.isLianaAt(x, nextY)) {
            return;
        }

        y = nextY;
    }


    /** {@inheritDoc} */
    @Override public Integer getX() { return x; }
    /** {@inheritDoc} */
    @Override public Integer getY() { return y; }
    /** {@inheritDoc} */
    @Override public String getType() { return "RED"; }
}
