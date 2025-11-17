package Server.ui;

import Server.Server;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Aplicación JavaFX para el administrador del juego DonCEy Kong Jr.
 * <p>
 * Esta clase:
 * <ul>
 *     <li>Arranca el servidor en un hilo aparte.</li>
 *     <li>Carga y muestra la ventana de administración definida en el FXML.</li>
 * </ul>
 * Extiende {@link Application}, por lo que su punto de inicio gráfico es
 * el método {@link #start(Stage)}.
 */
public class AdminApp extends Application {

    /**
     * Punto de entrada de JavaFX.
     * <p>
     * Carga el archivo FXML de la ventana principal, crea la escena y la muestra
     * en el {@link Stage} recibido. Además, inicia el servidor en segundo plano
     * llamando a {@link #startServerInBackground()}.
     * </p>
     *
     * @param stage ventana principal de la aplicación donde se colocará la escena
     * @throws Exception si ocurre algún error al cargar el FXML o configurar la escena
     */
    @Override
    public void start(Stage stage) throws Exception {
        // Carga el FXML de la ventana principal
        FXMLLoader loader = new FXMLLoader(getClass().getResource("admin_window.fxml"));
        Scene scene = new Scene(loader.load());
        stage.setTitle("DonCEy Kong Jr. - Admin");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();

        // Arrancamos el servidor en un hilo en segundo plano
        startServerInBackground();
    }

    /**
     * Inicia el servidor de juego en un hilo separado.
     * <p>
     * Este método crea un hilo tipo "daemon" que invoca
     * {@link Server#getInstance()} seguido de {@link Server#start()}.
     * Al ser daemon, no impide que la aplicación JavaFX termine
     * cuando se cierre la ventana.
     * </p>
     *
     * No recibe parámetros ni retorna ningún valor.
     */
    private void startServerInBackground() {
        Thread t = new Thread(() -> {
            try {
                Server.getInstance().start(); // usa tu start() actual
            } catch (IOException e) {
                e.printStackTrace();
            }
        }, "Server-Main-Thread");
        t.setDaemon(true); // para que no bloquee el cierre de la app
        t.start();
    }

    /**
     * Método main estándar de una aplicación JavaFX.
     * <p>
     * Delegará el control al método {@link #start(Stage)} a través de
     * la llamada {@link #launch(String...)}.
     * </p>
     *
     * @param args argumentos de la línea de comandos (no utilizados actualmente)
     */
    public static void main(String[] args) {
        launch(args);
    }
}
