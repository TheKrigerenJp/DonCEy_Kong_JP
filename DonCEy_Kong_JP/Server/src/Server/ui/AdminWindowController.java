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
 * <p>Se inspira en el MainWindowController del proyecto viejo, y permite:</p>
 * <ul>
 *     <li>Crear cocodrilos rojos/azules en una liana y posición Y opcional.</li>
 *     <li>Crear frutas en coordenadas específicas con cierta cantidad de puntos.</li>
 *     <li>Eliminar frutas en coordenadas específicas.</li>
 * </ul>
 *
 * <p>Internamente construye comandos de texto del estilo:</p>
 * <pre>
 *   ADMIN CROCODILE RED 3 5
 *   ADMIN FRUIT CREATE 4 6 100
 *   ADMIN FRUIT DELETE 4 6
 * </pre>
 * y los envía al servidor mediante {@link Server#runAdminCommand(String)}.
 */
public class AdminWindowController implements Initializable {

    /* === Controles para selección de jugador === */

    /**
     * Campo de texto donde el administrador puede escribir el ID del jugador
     * cuya partida desea modificar.
     *
     * Si se deja vacío, los cambios se aplican a las plantillas globales
     * del servidor.
     */
    @FXML private TextField playerIdField;


    /* === Controles para cocodrilos === */

    /** Selector del tipo de cocodrilo (por ejemplo, "RED" o "BLUE"). */
    @FXML private ChoiceBox<String> crocTypeChoiceBox;
    /** Campo de texto para la liana (coordenada X) del cocodrilo. */
    @FXML private TextField crocLianaField;
    /** Campo de texto para la coordenada Y del cocodrilo (opcional). */
    @FXML private TextField crocYField;
    /** Botón que dispara la creación del cocodrilo. */
    @FXML private Button createCrocButton;

    /* === Controles para frutas === */

    /** Campo de texto para la liana (coordenada X) de la fruta. */
    @FXML private TextField fruitLianaField;
    /** Campo de texto para la coordenada Y de la fruta. */
    @FXML private TextField fruitYField;
    /** Campo de texto para los puntos que otorgará la fruta. */
    @FXML private TextField fruitPointsField;
    /** Botón para crear una nueva fruta. */
    @FXML private Button createFruitButton;
    /** Botón para eliminar una fruta existente. */
    @FXML private Button deleteFruitButton;

    /* === Log === */

    /** Área de texto donde se muestran los comandos enviados y mensajes de estado. */
    @FXML private TextArea logArea;

    /**
     * Inicializa los controles de la ventana de administración.
     * <p>
     * Se ejecuta automáticamente cuando se carga el FXML.
     * Configura la lista de tipos de cocodrilo y deja uno seleccionado por defecto.
     * También escribe un mensaje inicial en el log.
     * </p>
     *
     * @param location  URL del recurso FXML que se está cargando (puede ser {@code null})
     * @param resources conjunto de recursos de internacionalización (puede ser {@code null})
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        if (crocTypeChoiceBox != null) {
            crocTypeChoiceBox.setItems(FXCollections.observableArrayList("RED", "BLUE"));
            crocTypeChoiceBox.getSelectionModel().selectFirst();
        }
        appendLog("Interfaz de admin lista. Servidor arrancando...");
    }

    /* === Handlers de botones === */

    /**
     * Handler asociado al botón de creación de cocodrilos.
     * <p>
     * Lee el tipo de cocodrilo y la liana/Y desde la interfaz y construye
     * un comando de administración del tipo:
     * </p>
     * <pre>
     * ADMIN CROCODILE &lt;type&gt; &lt;liana&gt;
     * ADMIN CROCODILE &lt;type&gt; &lt;liana&gt; &lt;y&gt;
     * </pre>
     * <p>
     * Si la posición Y está vacía, se envía solo la liana y el servidor decide
     * la Y por defecto. En caso de entrada inválida, se muestra un diálogo
     * de error sin enviar el comando.
     * </p>
     *
     * No recibe parámetros ni devuelve valor; su efecto es enviar un comando
     * al servidor y registrar el comando en el log.
     */
    @FXML
    private void onCreateCrocodile() {
        String type        = crocTypeChoiceBox.getValue();
        String lianaText   = crocLianaField.getText().trim();
        String yText       = crocYField.getText().trim();
        String playerIdTxt = (playerIdField != null) ? playerIdField.getText().trim() : "";

        if (lianaText.isEmpty()) {
            showError("Debe indicar la liana (x) para el cocodrilo.");
            return;
        }

        StringBuilder cmd = new StringBuilder("ADMIN CROCODILE ")
                .append(type)
                .append(" ")
                .append(lianaText);

        if (!yText.isEmpty()) {
            cmd.append(" ").append(yText);
        }
        if (!playerIdTxt.isEmpty()) {
            cmd.append(" ").append(playerIdTxt);
        }

        String command = cmd.toString();
        Server.getInstance().runAdminCommand(command);
        appendLog("→ " + command);
    }

    /**
     * Handler asociado al botón de creación de frutas.
     * <p>
     * Lee liana, Y y puntos desde la interfaz y construye un comando
     * del tipo:
     * </p>
     * <pre>
     * ADMIN FRUIT CREATE &lt;liana&gt; &lt;y&gt; &lt;points&gt;
     * </pre>
     * <p>
     * Si alguno de los campos está vacío, se muestra un diálogo de error y
     * no se envía el comando al servidor.
     * </p>
     *
     * No recibe parámetros ni devuelve valor; su efecto es enviar un comando
     * al servidor y registrar el comando en el log.
     */
    @FXML
    private void onCreateFruit() {
        String lianaText   = fruitLianaField.getText().trim();
        String yText       = fruitYField.getText().trim();
        String pointsText  = fruitPointsField.getText().trim();
        String playerIdTxt = (playerIdField != null) ? playerIdField.getText().trim() : "";

        if (lianaText.isEmpty() || yText.isEmpty() || pointsText.isEmpty()) {
            showError("Debe indicar liana (x), y y puntos para la fruta.");
            return;
        }

        StringBuilder cmd = new StringBuilder("ADMIN FRUIT CREATE ")
                .append(lianaText).append(" ")
                .append(yText).append(" ")
                .append(pointsText);

        if (!playerIdTxt.isEmpty()) {
            cmd.append(" ").append(playerIdTxt);
        }

        String command = cmd.toString();
        Server.getInstance().runAdminCommand(command);
        appendLog("→ " + command);
    }

    /**
     * Handler asociado al botón de eliminación de frutas.
     * <p>
     * Lee la liana y la Y desde la interfaz y construye un comando
     * del tipo:
     * </p>
     * <pre>
     * ADMIN FRUIT DELETE &lt;liana&gt; &lt;y&gt;
     * </pre>
     * <p>
     * Si alguno de los campos está vacío, se muestra un diálogo de error
     * y no se envía el comando al servidor.
     * </p>
     *
     * No recibe parámetros ni devuelve valor; su efecto es enviar un comando
     * al servidor y registrar el comando en el log.
     */
    @FXML
    private void onDeleteFruit() {
        String lianaText   = fruitLianaField.getText().trim();
        String yText       = fruitYField.getText().trim();
        String playerIdTxt = (playerIdField != null) ? playerIdField.getText().trim() : "";

        if (lianaText.isEmpty() || yText.isEmpty()) {
            showError("Debe indicar liana (x) y y de la fruta a borrar.");
            return;
        }

        StringBuilder cmd = new StringBuilder("ADMIN FRUIT DELETE ")
                .append(lianaText).append(" ")
                .append(yText);

        if (!playerIdTxt.isEmpty()) {
            cmd.append(" ").append(playerIdTxt);
        }

        String command = cmd.toString();
        Server.getInstance().runAdminCommand(command);
        appendLog("→ " + command);
    }

    /* === Utils de UI === */

    /**
     * Agrega un mensaje al área de log de la interfaz.
     * <p>
     * Si ya hay texto en el log, inserta una nueva línea antes de añadir
     * el nuevo mensaje. Además, escribe el mismo mensaje en la salida
     * estándar con el prefijo {@code [ADMIN-UI]}.
     * </p>
     *
     * @param msg mensaje a mostrar en el log y en la consola
     */
    private void appendLog(String msg) {
        if (logArea != null) {
            if (!logArea.getText().isEmpty()) {
                logArea.appendText("\n");
            }
            logArea.appendText(msg);
        }
        System.out.println("[ADMIN-UI] " + msg);
    }

    /**
     * Muestra un cuadro de diálogo de error y registra el mensaje en el log.
     * <p>
     * Este método:
     * </p>
     * <ul>
     *     <li>Llama a {@link #appendLog(String)} con el prefijo {@code "ERROR: "}.</li>
     *     <li>Muestra un {@link Alert} de tipo {@link Alert.AlertType#ERROR}
     *         con un título y encabezado fijos y el mensaje recibido.</li>
     * </ul>
     *
     * @param msg descripción del error que se desea informar al usuario
     */
    private void showError(String msg) {
        appendLog("ERROR: " + msg);
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText("Entrada inválida");
        alert.setContentText(msg);
        alert.showAndWait();
    }
}
