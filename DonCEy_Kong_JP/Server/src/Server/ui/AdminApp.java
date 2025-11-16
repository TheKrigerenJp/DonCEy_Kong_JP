package Server.ui;

import Server.Server;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Aplicación JavaFX para el administrador del juego DonCEy Kong Jr.
 * 
 * - Arranca el servidor en un hilo aparte.
 * - Muestra una ventana con controles para crear cocodrilos y frutas.
 */
public class AdminApp extends Application {

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

    public static void main(String[] args) {
        launch(args);
    }
}
