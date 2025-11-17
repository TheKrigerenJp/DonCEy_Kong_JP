package Server.factory;

import Server.entities.BlueCroc;
import Server.entities.Enemy;
import Server.entities.Fruit;
import Server.entities.RedCroc;
import Server.entities.SimpleFruit;

/**
 * Implementación por defecto de la fábrica de elementos del juego.
 * <p>
 * Esta fábrica crea cocodrilos rojos o azules y frutas simples según los
 * parámetros recibidos. Es una implementación concreta de
 * {@link GameElementFactory}.
 * </p>
 */
public class DefaultFactory extends GameElementFactory {

    /**
     * Crea un nuevo cocodrilo según el tipo indicado.
     * <ul>
     *     <li>Si {@code type} es igual a {@code "RED"} (ignorando mayúsculas/minúsculas),
     *         se crea un {@link RedCroc}.</li>
     *     <li>En cualquier otro caso, se crea un {@link BlueCroc}.</li>
     * </ul>
     *
     * @param type  tipo de cocodrilo a crear ({@code "RED"} o {@code "BLUE"})
     * @param liana columna o liana donde se colocará el enemigo
     * @param y     posición vertical inicial del enemigo
     * @return instancia de {@link RedCroc} o {@link BlueCroc}, según el tipo
     */
    @Override
    public Enemy createCrocodile(String type, Integer liana, Integer y) {
        if ("RED".equalsIgnoreCase(type)) {
            return new RedCroc(liana, y);
        }
        return new BlueCroc(liana, y);
    }

    /**
     * Crea una nueva fruta simple con la posición y puntos indicados.
     *
     * @param liana  coordenada X o liana donde se ubicará la fruta
     * @param y      coordenada Y o altura donde se ubicará la fruta
     * @param points cantidad de puntos que otorgará la fruta
     * @return instancia de {@link SimpleFruit} creada con los parámetros dados
     */
    @Override
    public Fruit createFruit(Integer liana, Integer y, Integer points) {
        return new SimpleFruit(liana, y, points);
    }
}
