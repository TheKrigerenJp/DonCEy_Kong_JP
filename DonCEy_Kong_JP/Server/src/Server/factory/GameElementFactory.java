package Server.factory;

import Server.entities.Enemy;
import Server.entities.Fruit;

/**
 * Fábrica abstracta para la creación de elementos del juego.
 * <p>
 * Define los métodos que cualquier fábrica concreta debe implementar para
 * crear enemigos y frutas. Esto permite cambiar fácilmente la familia de
 * objetos creados sin modificar el código que los utiliza.
 * </p>
 */
public abstract class GameElementFactory {

    /**
     * Crea un nuevo enemigo del tipo indicado.
     *
     * @param type  tipo de enemigo a crear (por ejemplo, {@code "RED"} o {@code "BLUE"})
     * @param liana columna o liana donde se colocará el enemigo
     * @param y     posición vertical inicial del enemigo
     * @return instancia concreta de {@link Enemy} correspondiente al tipo solicitado
     */
    public abstract Enemy createCrocodile(String type, int liana, int y);

    /**
     * Crea una nueva fruta con la posición y puntos indicados.
     *
     * @param liana  coordenada X o liana donde se ubicará la fruta
     * @param y      coordenada Y o altura donde se ubicará la fruta
     * @param points cantidad de puntos que otorgará la fruta
     * @return instancia concreta de {@link Fruit} creada por la fábrica
     */
    public abstract Fruit createFruit(int liana, int y, int points);
}


