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

    /** Para hacerlo más lento: se mueve solo cada N ticks */
    private int tickCounter = 0;
    private static final int SPEED_DIVIDER = 2; // prueba con 2 o 3

    public RedCroc(Integer x, Integer y) {
        this.x = x;
        this.y = y;

        // Buscar todos los tiles '|' en esta columna y quedarnos con min y max
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (int yy = Server.MIN_Y; yy <= Server.MAX_Y; yy++) {
            if (Server.isLianaAt(x, yy)) {
                if (yy < minY) minY = yy;
                if (yy > maxY) maxY = yy;
            }
        }

        if (minY == Integer.MAX_VALUE) {
            // No hay liana en esta X: lo dejamos con un rango trivial
            minLianaY = y;
            maxLianaY = y;
            System.out.println("[RED] No se encontró liana en x=" + x);
        } else {
            minLianaY = minY;
            maxLianaY = maxY;
        }

        // Por si el admin lo puso mal, lo forzamos al rango de la liana
        if (y < minLianaY) this.y = minLianaY;
        if (y > maxLianaY) this.y = maxLianaY;
    }

    @Override
    public void tick(Integer minY, Integer maxY) {
        tickCounter++;
        if (tickCounter % SPEED_DIVIDER != 0) {
            return; // se queda quieto este tick → más lento
        }

        int nextY = y + dir;

        // Si llega a los extremos de la liana, cambia de dirección
        if (nextY > maxLianaY || nextY < minLianaY) {
            dir = -dir;
            nextY = y + dir;
        }

        // Seguridad extra: nunca salgas de las casillas '|'
        if (!Server.isLianaAt(x, nextY)) {
            return;
        }

        y = nextY;
    }

    @Override
    public Integer getX() {
        return x;
    }

    @Override
    public Integer getY() {
        return y;
    }

    @Override
    public String getType() {
        return "RED";
    }
}
