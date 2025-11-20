package Server.entities;

public class BlueCroc extends Enemy {

    private Integer x;
    private Integer y;
    private boolean active = true;

    private int tickCounter = 0;

    public BlueCroc(Integer x, Integer y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public void tick(Integer minY, Integer maxY, Integer level) {
        if (!active) return;

        tickCounter++;

        int lvl = (level == null || level < 1) ? 1 : level;
        int stepTicks = 6 - lvl;
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


    @Override public Boolean isActive() { return active; }
    @Override public Integer getX() { return x; }
    @Override public Integer getY() { return y; }
    @Override public String getType() { return "BLUE"; }
}
