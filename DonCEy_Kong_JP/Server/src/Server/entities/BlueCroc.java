package Server.entities;

public class BlueCroc extends Enemy {

    private Integer x;
    private Integer y;

    private boolean active = true;

    private int tickCounter = 0;
    private static final int SPEED_DIVIDER = 2; // o 3

    public BlueCroc(Integer x, Integer y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public void tick(Integer minY, Integer maxY) {
        if (!active) return;

        tickCounter++;
        if (tickCounter % SPEED_DIVIDER != 0) {
            return;
        }

        if (y > minY) {
            y = y - 1;  // baja una casilla
        } else {
            active = false; // llegó a y = 0 → desaparece
        }
    }

    @Override
    public Boolean isActive() { return active; }

    @Override
    public Integer getX() { return x; }

    @Override
    public Integer getY() { return y; }

    @Override
    public String getType() { return "BLUE"; }
}
