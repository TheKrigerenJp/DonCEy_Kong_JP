package Server.ui;

import Server.Server;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * Controlador de la ventana de administración.
 *
 * Se inspira en el MainWindowController del proyecto viejo:
 * - Permite crear cocodrilos rojos/azules.
 * - Permite crear y eliminar frutas en ciertas coordenadas.
 * 
 * Internamente construye comandos tipo:
 *   ADMIN CROCODILE RED 3 5
 *   ADMIN FRUIT CREATE 4 6 100
 *   ADMIN FRUIT DELETE 4 6
 * y los envía al servidor.
 */
public class AdminWindowController implements Initializable {

    /* === Controles para cocodrilos === */
    @FXML private ChoiceBox<String> crocTypeChoiceBox;
    @FXML private TextField crocLianaField;
    @FXML private TextField crocYField;
    @FXML private Button createCrocButton;

    /* === Controles para frutas === */
    @FXML private TextField fruitLianaField;
    @FXML private TextField fruitYField;
    @FXML private TextField fruitPointsField;
    @FXML private Button createFruitButton;
    @FXML private Button deleteFruitButton;

    /* === Log === */
    @FXML private TextArea logArea;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Tipos de cocodrilo como en el proyecto nuevo: RED / BLUE
        crocTypeChoiceBox.setItems(FXCollections.observableArrayList("RED", "BLUE"));
        crocTypeChoiceBox.getSelectionModel().selectFirst();

        appendLog("Interfaz de admin lista. Servidor arrancando...");
    }

    /* === Handlers de botones === */

    @FXML
    private void onCreateCrocodile() {
        String type = crocTypeChoiceBox.getValue();
        String lianaText = crocLianaField.getText().trim();
        String yText = crocYField.getText().trim();

        if (lianaText.isEmpty()) {
            showError("Debe indicar la liana (x) para el cocodrilo.");
            return;
        }

        String cmd;
        if (yText.isEmpty()) {
            // Usa MIN_Y interno del servidor (como cuando no se especifica)
            cmd = "ADMIN CROCODILE " + type + " " + lianaText;
        } else {
            cmd = "ADMIN CROCODILE " + type + " " + lianaText + " " + yText;
        }

        Server.getInstance().runAdminCommand(cmd);
        appendLog("→ " + cmd);
    }

    @FXML
    private void onCreateFruit() {
        String lianaText = fruitLianaField.getText().trim();
        String yText = fruitYField.getText().trim();
        String pointsText = fruitPointsField.getText().trim();

        if (lianaText.isEmpty() || yText.isEmpty() || pointsText.isEmpty()) {
            showError("Debe indicar liana, y y puntos para crear una fruta.");
            return;
        }

        String cmd = "ADMIN FRUIT CREATE " + lianaText + " " + yText + " " + pointsText;
        Server.getInstance().runAdminCommand(cmd);
        appendLog("→ " + cmd);
    }

    @FXML
    private void onDeleteFruit() {
        String lianaText = fruitLianaField.getText().trim();
        String yText = fruitYField.getText().trim();

        if (lianaText.isEmpty() || yText.isEmpty()) {
            showError("Debe indicar liana y y para eliminar una fruta.");
            return;
        }

        String cmd = "ADMIN FRUIT DELETE " + lianaText + " " + yText;
        Server.getInstance().runAdminCommand(cmd);
        appendLog("→ " + cmd);
    }

    /* === Utils de UI === */

    private void appendLog(String msg) {
        if (logArea != null) {
            if (!logArea.getText().isEmpty()) {
                logArea.appendText("\n");
            }
            logArea.appendText(msg);
        }
        System.out.println("[ADMIN-UI] " + msg);
    }

    private void showError(String msg) {
        appendLog("ERROR: " + msg);
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText("Entrada inválida");
        alert.setContentText(msg);
        alert.showAndWait();
    }
}

