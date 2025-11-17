#include "client_constants.h"
#include "raylib.h"

/**
 * Dibuja un botón rectangular con texto centrado.
 *
 * @param rect    Rectángulo que define la posición y tamaño del botón.
 * @param text    Cadena de texto a mostrar en el botón.
 * @param hovered Indica si el mouse está encima del botón (para cambiar el color).
 */
static void draw_button(Rectangle rect, const char *text, bool hovered)
{
    Color bg = hovered ? (Color){ 70, 120, 200, 255 } : (Color){ 50, 80, 130, 255 };
    DrawRectangleRec(rect, bg);
    DrawRectangleLines((int)rect.x, (int)rect.y, (int)rect.width, (int)rect.height, RAYWHITE);

    int textWidth = MeasureText(text, 24);
    int textX = (int)(rect.x + rect.width/2 - textWidth/2);
    int textY = (int)(rect.y + rect.height/2 - 12);

    DrawText(text, textX, textY, 24, RAYWHITE);
}

/* ============================
 *  P A N T A L L A  I N I C I A L
 * ============================ */

/**
 * Muestra una pantalla con dos botones para seleccionar el rol.
 *
 * - Si se pulsa "JUGADOR", devuelve ROLE_PLAYER.
 * - Si se pulsa "ESPECTADOR", devuelve ROLE_SPECTATOR.
 * - Si se cierra la ventana, devuelve ROLE_NONE.
 *
 * @return Rol seleccionado o ROLE_NONE si se cierra la ventana.
 */
int run_role_selection_screen(void)
{
    int selectedRole = ROLE_NONE;

    Rectangle playerBtn  = { WINDOW_WIDTH/2.0f - 150, WINDOW_HEIGHT/2.0f - 60, 300, 50 };
    Rectangle spectBtn   = { WINDOW_WIDTH/2.0f - 150, WINDOW_HEIGHT/2.0f + 20, 300, 50 };

    while (!WindowShouldClose() && selectedRole == ROLE_NONE) {
        Vector2 mouse = GetMousePosition();
        bool hoverPlayer    = CheckCollisionPointRec(mouse, playerBtn);
        bool hoverSpectator = CheckCollisionPointRec(mouse, spectBtn);

        if (IsMouseButtonPressed(MOUSE_LEFT_BUTTON)) {
            if (hoverPlayer) {
                selectedRole = ROLE_PLAYER;
            } else if (hoverSpectator) {
                selectedRole = ROLE_SPECTATOR;
            }
        }

        BeginDrawing();
            ClearBackground((Color){ 20, 20, 30, 255 });

            DrawText("DonCEy Kong Jr - Cliente", 200, 80, 32, RAYWHITE);
            DrawText("Seleccione el modo de uso:", 230, 140, 20, LIGHTGRAY);

            draw_button(playerBtn, "JUGADOR", hoverPlayer);
            draw_button(spectBtn, "ESPECTADOR", hoverSpectator);

            DrawText("Cerrar ventana para salir", 10, WINDOW_HEIGHT - 30, 16, GRAY);
        EndDrawing();
    }

    return selectedRole;
}

/* ============================
 *  M O D O   J U G A D O R
 * ============================ */

/**
 * Bucle principal del cliente en modo jugador.
 *
 * - Envía un comando JOIN al servidor con un nombre fijo ("Jugador1").
 * - Dibuja una pantalla básica mientras la ventana esté abierta.
 *
 * @param state Puntero al estado del cliente con socket ya conectado.
 */
void run_player_mode(ClientState *state)
{
    const char *playerName = "Jugador1";  // Más adelante se puede leer de la UI.
    char cmd[128];

    // Enviar JOIN al servidor
    snprintf(cmd, sizeof(cmd), "JOIN %s\n", playerName);
    send_line(state->socket_fd, cmd);

    while (!WindowShouldClose()) {
        BeginDrawing();
            ClearBackground((Color){ 10, 10, 30, 255 });
            DrawText("Modo JUGADOR", 280, 80, 32, RAYWHITE);
            DrawText("Conectado al servidor.", 260, 140, 20, LIGHTGRAY);
            DrawText("TODO: implementar juego (movimiento, render, etc.)", 80, 200, 20, GOLD);
            DrawText("Presione ESC o cierre la ventana para salir.", 180, 260, 18, GRAY);
        EndDrawing();
    }
}

/* ============================
 *  M O D O   E S P E C T A D O R
 * ============================ */

/**
 * Bucle principal del cliente en modo espectador.
 *
 * - Envía un comando SPECTATE para observar al jugador con ID fijo (1).
 * - Muestra una pantalla de estado mientras la ventana esté abierta.
 *
 * @param state Puntero al estado del cliente con socket ya conectado.
 */
void run_spectator_mode(ClientState *state)
{
    int playerId = 1;   // ID fijo por ahora
    char cmd[128];

    snprintf(cmd, sizeof(cmd), "SPECTATE %d\n", playerId);
    send_line(state->socket_fd, cmd);

    while (!WindowShouldClose()) {
        BeginDrawing();
            ClearBackground((Color){ 10, 10, 30, 255 });
            DrawText("Modo ESPECTADOR", 260, 80, 32, RAYWHITE);
            DrawText("Conectado al servidor.", 260, 140, 20, LIGHTGRAY);
            DrawText("Observando jugador con ID = 1", 230, 180, 20, LIGHTGRAY);
            DrawText("TODO: recibir estado y dibujar el juego.", 160, 220, 20, GOLD);
            DrawText("Presione ESC o cierre la ventana para salir.", 180, 260, 18, GRAY);
        EndDrawing();
    }
}

/* ============================
 *  P U N T O   D E   E N T R A D A
 * ============================ */

/**
 * Punto de entrada principal del cliente.
 *
 * Flujo:
 *  1. Inicializar WinSock (WSAStartup).
 *  2. Crear ventana de Raylib.
 *  3. Mostrar pantalla de selección de rol.
 *  4. Si se selecciona un rol, conectar al servidor.
 *  5. Ejecutar el modo correspondiente (jugador / espectador).
 *  6. Cerrar socket, ventana y limpiar WinSock.
 *
 * @return 0 en salida normal, 1 en caso de error de WinSock o conexión.
 */
int main(void)
{
    ClientState state;
    state.socket_fd = INVALID_SOCKET;
    state.role = ROLE_NONE;
    state.connected = 0;

    // --- Inicializar WinSock ---
    WSADATA wsa;
    int wsaResult = WSAStartup(MAKEWORD(2, 2), &wsa);
    if (wsaResult != 0) {
        printf("WSAStartup fallo: %d\n", wsaResult);
        return 1;
    }

    InitWindow(WINDOW_WIDTH, WINDOW_HEIGHT, "DonCEy Kong Jr - Cliente");
    SetTargetFPS(60);

    // 1) Pantalla inicial: escoger Jugador o Espectador
    state.role = run_role_selection_screen();

    if (state.role == ROLE_NONE) {
        CloseWindow();
        WSACleanup();
        return 0;
    }

    // 2) Conectar al servidor Java
    state.socket_fd = create_and_connect_socket(SERVER_IP, SERVER_PORT);
    if (state.socket_fd == INVALID_SOCKET) {
        CloseWindow();
        WSACleanup();
        return 1;
    }
    state.connected = 1;

    // 3) Ejecutar modo según rol
    if (state.role == ROLE_PLAYER) {
        run_player_mode(&state);
    } else if (state.role == ROLE_SPECTATOR) {
        run_spectator_mode(&state);
    }

    // 4) Cerrar recursos
    if (state.connected) {
        close_socket(state.socket_fd);
    }
    CloseWindow();

    WSACleanup();    // Finalizar WinSock
    return 0;
}
