package Server.entities;

import Server.Server;

public class RedCroc extends Enemy {

    private Integer x;
    private Integer y;

    /** +1 = sube, -1 = baja */
    private int dir = +1;

    /** Extremos de la liana para esta X */
    private final int minLianaY;
    private final int maxLianaY;

    /** Contador de ticks para controlar la velocidad */
    private int tickCounter = 0;

    public RedCroc(Integer x, Integer y) {
        this.x = x;
        this.y = y;

        // Buscar todos los '|' en esta columna
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (int yy = Server.MIN_Y; yy <= Server.MAX_Y; yy++) {
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

    @Override
    public void tick(Integer minY, Integer maxY, Integer level) {
        tickCounter++;

        int lvl = (level == null || level < 1) ? 1 : level;
        int stepTicks = 6 - lvl;   // lvl=1 → 5, lvl=2 → 4, ..., lvl>=5 → 1
        if (stepTicks < 1) stepTicks = 1;

        if (tickCounter % stepTicks != 0) {
            return; // este tick no se mueve
        }

        int nextY = y + dir;

        if (nextY > maxLianaY || nextY < minLianaY) {
            dir = -dir;
            nextY = y + dir;
        }

        if (!Server.isLianaAt(x, nextY)) {
            return;
        }

        y = nextY;
    }


    @Override public Integer getX() { return x; }
    @Override public Integer getY() { return y; }
    @Override public String getType() { return "RED"; }
}
