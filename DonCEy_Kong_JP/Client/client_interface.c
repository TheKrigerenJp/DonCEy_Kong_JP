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
 *  H E L P E R S   D E   M A P A
 * ============================ */

/**
 * Recibe el mapa lógico inicial desde el servidor y lo almacena en state->map.
 *
 * Protocolo esperado (en cualquier orden mezclado con otras líneas):
 *  - "MAP_SIZE <ancho> <alto>"  (obligatorio una vez)
 *  - "MAP_ROW <y> <fila_completa>" para cada fila
 *  - "MAP_END"                  (marca el final de la descripción del mapa)
 *
 * Esta función es robusta ante líneas adicionales (por ejemplo "STATE ..."):
 * las ignora hasta haber recibido "MAP_SIZE" y "MAP_END".
 *
 * @param state Puntero al estado del cliente ya conectado.
 * @return 0 si el mapa se recibió correctamente, -1 en caso de error.
 */
static int receive_initial_map(ClientState *state)
{
    char line[256];

    int width  = 0;
    int height = 0;
    int gotSize = 0;

    /* --- 1) Esperar MAP_SIZE --- */
    for (;;) {
        int len = recv_line(state->socket_fd, line, sizeof(line));
        if (len <= 0) {
            return -1;
        }

        if (sscanf(line, "MAP_SIZE %d %d", &width, &height) == 2) {
            gotSize = 1;
            break;
        }

        /* Cualquier otra cosa (por ej. STATE) se ignora aquí */
    }

    if (!gotSize ||
        width <= 0 || height <= 0 ||
        width > MAX_MAP_WIDTH || height > MAX_MAP_HEIGHT) {
        return -1;
    }

    state->map.width  = width;
    state->map.height = height;

    /* Inicializamos la matriz a vacío por si faltan filas */
    for (int y = 0; y < height; y++) {
        memset(state->map.tiles[y], '.', width);
        state->map.tiles[y][width] = '\0';
    }

    /* --- 2) Leer hasta MAP_END, recogiendo MAP_ROW --- */
    for (;;) {
        int len = recv_line(state->socket_fd, line, sizeof(line));
        if (len <= 0) {
            return -1;
        }

        if (strncmp(line, "MAP_END", 7) == 0) {
            break; /* ya terminamos */
        }

        int y = -1;
        char row[MAX_MAP_WIDTH + 1];

        if (sscanf(line, "MAP_ROW %d %s", &y, row) == 2) {
            if (y >= 0 && y < state->map.height) {
                strncpy(state->map.tiles[y], row, state->map.width);
                state->map.tiles[y][state->map.width] = '\0';
            }
        }
        /* Si no era MAP_ROW, se ignora (por ej. STATE) */
    }

    return 0;
}

/* Tile sólido donde el jugador puede apoyarse / estar de pie */
static int is_solid_tile_char(char t)
{
    return t == 'T' || t == '=' || t == '|' || t == 'S';
}

static int is_liana_char(char t)
{
    return t == '|';
}



/**
 * Solicita al servidor la lista de jugadores activos y la almacena en state->players.
 *
 * Protocolo esperado del servidor:
 *  - "PLAYERS_BEGIN"
 *  - "PLAYER <id> <name>"  (cero o más líneas)
 *  - "PLAYERS_END"
 *
 * Cualquier otra línea se ignora. Si no se recibe PLAYERS_BEGIN/END
 * correctamente, se considera error.
 *
 * @param state Puntero al estado del cliente con el socket conectado.
 * @return 0 si la lista se recibió correctamente, -1 en caso de error.
 */
static int fetch_player_list(ClientState *state)
{
    char line[256];
    int gotBegin = 0;

    state->numPlayers = 0;

    /* Enviar la solicitud al servidor */
    send_line(state->socket_fd, "LIST_PLAYERS\n");

    for (;;) {
        int len = recv_line(state->socket_fd, line, sizeof(line));
        if (len <= 0) {
            return -1;
        }

        if (strncmp(line, "PLAYERS_BEGIN", 13) == 0) {
            gotBegin = 1;
            continue;
        }

        if (strncmp(line, "PLAYERS_END", 11) == 0) {
            /* Termina el listado */
            break;
        }

        if (!gotBegin) {
            /* Si llega algo antes de PLAYERS_BEGIN, lo ignoramos */
            continue;
        }

        /* Intentar parsear "PLAYER <id> <name>" */
        int id = 0;
        char name[32];

        if (sscanf(line, "PLAYER %d %31s", &id, name) == 2) {
            if (state->numPlayers < MAX_PLAYERS) {
                state->players[state->numPlayers].id = id;
                strncpy(state->players[state->numPlayers].name, name, 31);
                state->players[state->numPlayers].name[31] = '\0';
                state->numPlayers++;
            }
        }
        /* Cualquier otra cosa se ignora */
    }

    return 0;
}

/**
 * Muestra una pantalla de selección de jugador para modo espectador.
 *
 * - Internamente llama a fetch_player_list() para pedir la lista al servidor.
 * - Si no hay jugadores activos, devuelve -1.
 * - El usuario puede moverse con flechas ARRIBA/ABAJO y confirmar con ENTER.
 * - Con ESC se cancela y se devuelve -1 (volver al menú principal).
 *
 * @param state Puntero al estado del cliente (usa el socket para pedir la lista).
 * @return ID del jugador seleccionado en caso de éxito, o -1 si se cancela
 *         o no hay jugadores activos.
 */
static int select_spectator_target(ClientState *state)
{
    if (fetch_player_list(state) != 0) {
        return -1;
    }

    if (state->numPlayers == 0) {
        /* No hay jugadores para observar */
        while (!WindowShouldClose()) {
            BeginDrawing();
                ClearBackground((Color){ 10, 10, 30, 255 });
                DrawText("No hay jugadores activos para espectar.",
                         80, 200, 20, RAYWHITE);
                DrawText("Presiona ESC para volver al menu.",
                         80, 240, 18, LIGHTGRAY);
            EndDrawing();

            if (IsKeyPressed(KEY_ESCAPE)) {
                break;
            }
        }
        return -1;
    }

    int selected = 0;

    while (!WindowShouldClose()) {
        if (IsKeyPressed(KEY_UP)) {
            selected--;
            if (selected < 0) selected = state->numPlayers - 1;
        }
        if (IsKeyPressed(KEY_DOWN)) {
            selected++;
            if (selected >= state->numPlayers) selected = 0;
        }

        if (IsKeyPressed(KEY_ENTER)) {
            return state->players[selected].id;
        }

        if (IsKeyPressed(KEY_ESCAPE)) {
            /* Cancelar selección y volver al menú principal */
            return -1;
        }

        BeginDrawing();
            ClearBackground((Color){ 10, 10, 30, 255 });

            DrawText("Selecciona jugador para espectar",
                     60, 60, 24, RAYWHITE);
            DrawText("Flechas ARRIBA/ABAJO para moverte, ENTER para escoger, ESC para volver",
                     60, 100, 16, LIGHTGRAY);

            for (int i = 0; i < state->numPlayers; i++) {
                int y = 150 + i * 30;
                Color color = (i == selected) ? YELLOW : RAYWHITE;

                char line[128];
                snprintf(line, sizeof(line), "ID %d - %s",
                         state->players[i].id,
                         state->players[i].name);

                DrawText(line, 80, y, 20, color);
            }

        EndDrawing();
    }

    return -1;
}


/* ============================
 *  D I B U J A R   E S C E N A
 * ============================ */

/**
 * Dibuja el mapa y la posición del jugador utilizando raylib.
 *
 * - Cada celda del mapa se representa como un rectángulo de color distinto
 *   según el carácter recibido del servidor.
 * - El jugador se dibuja como un rectángulo de color destacado encima.
 *
 * @param state Puntero al estado actual del cliente (mapa + posición jugador).
 */
static void
draw_game_scene(const ClientState *state)
{
    const int tileSize = 40;  /* tamaño en píxeles de cada tile */

    /* Calcular offset para centrar el mapa en la ventana */
    int mapPixelWidth  = state->map.width  * tileSize;
    int mapPixelHeight = state->map.height * tileSize;

    int offsetX = (WINDOW_WIDTH  - mapPixelWidth)  / 2;
    int offsetY = (WINDOW_HEIGHT - mapPixelHeight) / 2;

    /* Dibujar fondo */
    ClearBackground((Color){ 10, 10, 30, 255 });

    /* Dibujar tiles */
    for (int y = 0; y < state->map.height; y++) {
        for (int x = 0; x < state->map.width; x++) {
            char t = state->map.tiles[y][x];
            Color c;

            switch (t) {
                case 'W': c = (Color){ 30, 60, 200, 255 }; break;  /* Agua */
                case 'T': c = (Color){ 120, 80, 40, 255 }; break;  /* Tierra */
                case '=': c = (Color){ 100, 100, 100, 255 }; break;/* Plataforma */
                case '|': c = (Color){ 50, 150, 60, 255 }; break;  /* Liana */
                case 'S': c = (Color){ 200, 200, 50, 255 }; break; /* Spawn */
                case 'G': c = (Color){ 200, 120, 50, 255 }; break; /* Meta */
                default:  c = (Color){ 20, 20, 30, 255 }; break;   /* Vacío */
            }

            /* OJO: en el servidor y=0 es la fila inferior, aquí invertimos Y */
            int drawX = offsetX + x * tileSize;
            int drawY = offsetY + (state->map.height - 1 - y) * tileSize;

            DrawRectangle(drawX, drawY, tileSize, tileSize, c);
            DrawRectangleLines(drawX, drawY, tileSize, tileSize, (Color){ 10, 10, 10, 255 });
        }
    }

    /* Dibujar jugador (si tenemos posición válida) */
    if (state->playerId != 0) {
        int px = state->playerX;
        int py = state->playerY;

        int drawX = offsetX + px * tileSize;
        int drawY = offsetY + (state->map.height - 1 - py) * tileSize;

        DrawRectangle(drawX + 5, drawY + 5,
                      tileSize - 10, tileSize - 10,
                      (Color){ 230, 30, 60, 255 });
    }

    /* HUD sencillo (abajo a la izquierda) */
    DrawText("DonCEy Kong Jr - Cliente", 10, 10, 20, RAYWHITE);

    char hud[128];
    snprintf(hud, sizeof(hud),
             "ID: %d  Nivel: %d  Vidas: %d  Score: %d  GameOver: %s",
             state->playerId,
             state->level,
             state->lives,
             state->score,
             state->gameOver ? "SI" : "NO");
    DrawText(hud, 10, WINDOW_HEIGHT - 30, 16, LIGHTGRAY);
    /* === Overlay de GAME OVER === */
    if (state->gameOver) {
        /* Fondo oscuro semitransparente encima de todo */
        DrawRectangle(0, 0, WINDOW_WIDTH, WINDOW_HEIGHT,
                      (Color){ 0, 0, 0, 200 });

        /* Mensaje grande */
        const char *msg = "¡HAS PERDIDO!";
        int titleFont   = 40;
        int msgWidth    = MeasureText(msg, titleFont);
        int msgX        = (WINDOW_WIDTH - msgWidth) / 2;
        int msgY        = WINDOW_HEIGHT / 2 - 80;
        DrawText(msg, msgX, msgY, titleFont, RAYWHITE);

        /* Botón "Volver a jugar" */
        const char *btnText   = "Volver a jugar";
        int         btnFont   = 24;
        int         btnWidth  = MeasureText(btnText, btnFont) + 40;
        int         btnHeight = 50;
        int         btnX      = (WINDOW_WIDTH - btnWidth) / 2;
        int         btnY      = msgY + titleFont + 30;

        DrawRectangle(btnX, btnY, btnWidth, btnHeight,
                      (Color){ 50, 80, 130, 255 });
        DrawRectangleLines(btnX, btnY, btnWidth, btnHeight, RAYWHITE);

        int textX = btnX + (btnWidth  - MeasureText(btnText, btnFont)) / 2;
        int textY = btnY + (btnHeight - btnFont) / 2;
        DrawText(btnText, textX, textY, btnFont, RAYWHITE);
    }
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
 * Flujo:
 *  1) Envía un comando JOIN con un nombre fijo.
 *  2) Busca en las líneas del servidor una respuesta "JOINED <id>" y
 *     guarda el playerId asociado.
 *  3) Recibe el mapa inicial mediante receive_initial_map(), que también
 *     es robusta ante líneas extra.
 *  4) Entra en un bucle donde:
 *      - Lee líneas del tipo "STATE seq id x y score gameOver".
 *      - Actualiza la posición y puntuación del jugador.
 *      - Envía inputs al servidor según las teclas pulsadas.
 *      - Dibuja el mapa y la posición del jugador con raylib.
 *
 * @param state Puntero al estado del cliente ya inicializado y con el socket
 *              conectado al servidor.
 */
void run_player_mode(ClientState *state)
{
    char line[256];
    char cmd[128];

    /* 1) Enviar JOIN con un nombre de jugador fijo por ahora */
    snprintf(cmd, sizeof(cmd), "JOIN Jugador1\n");
    send_line(state->socket_fd, cmd);

    /* 2) Buscar "JOINED <id>" en lo que vaya mandando el servidor */
    state->playerId = 0;
    for (;;) {
        int len = recv_line(state->socket_fd, line, sizeof(line));
        if (len <= 0) {
            return;
        }

        int id = 0;
        if (sscanf(line, "JOINED %d", &id) == 1) {
            state->playerId = id;
            break;
        }

        /* Cualquier otra línea antes de JOINED se ignora */
    }

    /* 3) Recibir mapa inicial */
    if (receive_initial_map(state) != 0) {
        return;
    }

    /* Inicializar HUD / estado básico */
    state->playerX  = 0;
    state->playerY  = 0;
    state->score    = 0;
    state->gameOver = 0;

    int seq = 0;  /* número de secuencia para INPUT */

    /* 4) Bucle de juego */
    while (!WindowShouldClose()) {

        // Salir de la partida y volver al menú con ESC
        if (IsKeyPressed(KEY_ESCAPE)) {
            break;
        }

        /* --- Leer estado del servidor --- */
        int len = recv_line(state->socket_fd, line, sizeof(line));
        if (len <= 0) {
            break; /* desconexión o error */
        }

        /* Esperamos líneas del tipo:
         * STATE <seq> <id> <x> <y> <score> <true/false>
         */
        char tag[16];
        char gameOverStr[8];
        int  s, pid, x, y, score, level, lives;
        if (sscanf(line, "%15s %d %d %d %d %d %d %d %7s",
                   tag, &s, &pid, &x, &y, &score, &level, &lives, gameOverStr) == 9 &&
            strcmp(tag, "STATE") == 0) {

            if (pid == state->playerId) {
                state->playerX  = x;
                state->playerY  = y;
                state->score    = score;
                state->level    = level;
                state->lives    = lives;
                state->gameOver = (strcmp(gameOverStr, "true") == 0);
            }
        }

        /* --- Procesar input local y enviarlo al servidor --- */
        
                
        int dx = 0;
        int dy = 0;

        /* === Leer tiles alrededor del jugador === */
        char current = '.';  /* tile donde está el jugador */
        char below   = '.';  /* tile justo debajo (y-1)     */
        char above   = '.';  /* tile justo arriba (y+1)     */

        if(!state->gameOver){

            
            if (state->playerY >= 0 && state->playerY < state->map.height &&
                state->playerX >= 0 && state->playerX < state->map.width) {
                current = state->map.tiles[state->playerY][state->playerX];
            }
            if (state->playerY - 1 >= 0 &&
                state->playerY - 1 < state->map.height &&
                state->playerX >= 0 && state->playerX < state->map.width) {
                below = state->map.tiles[state->playerY - 1][state->playerX];
            }
            if (state->playerY + 1 >= 0 &&
                state->playerY + 1 < state->map.height &&
                state->playerX >= 0 && state->playerX < state->map.width) {
                above = state->map.tiles[state->playerY + 1][state->playerX];
            }

            int solid_current =
                (current == 'T' || current == '=' || current == '|' ||
                current == 'S');
            int solid_below =
                (below   == 'T' || below   == '=' || below   == '|' ||
                below   == 'S');

            /* "Apoyado" = estoy en un tile sólido O tengo un sólido justo debajo
            (caso de estar visualmente sobre la plataforma/liana). */
            int supported = solid_current || solid_below;

            /* Hay "techo" si justo arriba hay plataforma/tierra/spawn/meta */
            int hasCeilingAbove =
                (above == 'T' || above == '=' || above == 'S');

            int onLianaTile = (current == '|');  /* para trepar con ↑/↓ */

            /* === SALTO CON ESPACIO ===
            * - SPACE solo       -> (dx = 0, dy = +1)
            * - SPACE + LEFT     -> (dx = -2, dy = +1)
            * - SPACE + RIGHT    -> (dx = +2, dy = +1)
            * Solo si está apoyado y no hay techo encima.
            */
            if (IsKeyPressed(KEY_SPACE) && supported && !hasCeilingAbove) {
                dy = +1;

                if (IsKeyDown(KEY_LEFT)) {
                    dx = -2;
                } else if (IsKeyDown(KEY_RIGHT)) {
                    dx = +2;
                } else {
                    dx = 0; /* salto vertical */
                }
            } else {
                /* === Movimiento normal sin SPACE === */

                /* Izquierda / derecha siempre permitidas en el piso o liana */
                if (IsKeyDown(KEY_LEFT))  dx = -1;
                if (IsKeyDown(KEY_RIGHT)) dx = +1;

                /* ↑ / ↓ SOLO para trepar liana, y respetando techo */
                if (onLianaTile && !hasCeilingAbove && IsKeyDown(KEY_UP)) {
                    dy = +1;   /* subir por la liana */
                } else if (onLianaTile && IsKeyDown(KEY_DOWN)) {
                    dy = -1;   /* bajar por la liana */
                }
            }

            /* Enviamos INPUT solo si realmente hay movimiento */
            if (dx != 0 || dy != 0) {
                seq++;
                snprintf(cmd, sizeof(cmd), "INPUT %d %d %d\n", seq, dx, dy);
                send_line(state->socket_fd, cmd);
            }

        }

        /* --- Dibujar escena --- */
        BeginDrawing();
            draw_game_scene(state);
        EndDrawing();

        /* Si la partida terminó, revisar clic en el botón "Volver a jugar" */
        if (state->gameOver) {
            /* Debe usar la MISMA geometría que en draw_game_scene */

            const char *msg = "¡HAS PERDIDO!";
            int titleFont   = 40;
            int msgWidth    = MeasureText(msg, titleFont);
            int msgX        = (WINDOW_WIDTH - msgWidth) / 2;
            int msgY        = WINDOW_HEIGHT / 2 - 80;

            const char *btnText   = "Volver a jugar";
            int         btnFont   = 24;
            int         btnWidth  = MeasureText(btnText, btnFont) + 40;
            int         btnHeight = 50;
            int         btnX      = (WINDOW_WIDTH - btnWidth) / 2;
            int         btnY      = msgY + titleFont + 30;

            Rectangle btnRect = {
                (float)btnX, (float)btnY,
                (float)btnWidth, (float)btnHeight
            };

            Vector2 mouse = GetMousePosition();

            if (IsMouseButtonPressed(MOUSE_LEFT_BUTTON) &&
                CheckCollisionPointRec(mouse, btnRect)) {

                /* Salimos del bucle de juego.
                 * En main() se cerrará el socket y se volverá al menú,
                 * desde donde el usuario puede entrar otra vez como JUGADOR.
                 */
                break;
            }
        }

    }
}



/* ============================
 *  M O D O   E S P E C T A D O R
 * ============================ */

/**
 * Bucle principal del cliente en modo espectador.
 *
 * Flujo:
 *  1) Envía "SPECTATE <playerId>" (por ahora un ID fijo, p.ej. 1).
 *  2) Busca en las líneas del servidor una respuesta "SPECTATE_OK <playerId>"
 *     e inicializa state->playerId con ese valor.
 *     - Si recibe "SPECTATE_WAIT <playerId>", devuelve y finaliza el modo
 *       espectador (el jugador aún no existe).
 *  3) Recibe el mapa inicial mediante receive_initial_map(), que es robusta
 *     ante líneas adicionales.
 *  4) Entra en un bucle donde:
 *      - Lee líneas "STATE seq id x y score gameOver".
 *      - Si el id coincide con state->playerId, actualiza la posición y
 *        puntuación del jugador observado.
 *      - Dibuja el mapa y la posición del jugador usando raylib.
 *
 * @param state Puntero al estado del cliente ya inicializado y con el socket
 *              conectado al servidor.
 */
void run_spectator_mode(ClientState *state)
{
    char line[256];
    char cmd[128];

    /* 1) Dejar que el usuario escoja a quién espectar */
    int targetId = select_spectator_target(state);
    if (targetId < 0) {
        /* Usuario canceló o no hay jugadores activos */
        return;
    }

    /* 2) Enviar SPECTATE <targetId> al servidor */
    snprintf(cmd, sizeof(cmd), "SPECTATE %d\n", targetId);
    send_line(state->socket_fd, cmd);

    /* 3) Esperar "SPECTATE_OK <id>" o "SPECTATE_WAIT <id>" */
    state->playerId = 0;
    for (;;) {
        int len = recv_line(state->socket_fd, line, sizeof(line));
        if (len <= 0) {
            /* Desconexión / error */
            return;
        }

        int id = 0;
        if (sscanf(line, "SPECTATE_OK %d", &id) == 1) {
            state->playerId = id;
            break; /* listo, seguimos con el mapa */
        }

        if (sscanf(line, "SPECTATE_WAIT %d", &id) == 1) {
            /* El jugador todavía no existe o se fue justo ahora */
            return;
        }

        /* Cualquier otra línea se ignora en esta fase */
    }

    /* 4) Recibir mapa inicial (robusto ante líneas extra) */
    if (receive_initial_map(state) != 0) {
        return;
    }

    state->playerX  = 0;
    state->playerY  = 0;
    state->score    = 0;
    state->gameOver = 0;

    /* 5) Bucle de renderizado en modo espectador */
    while (!WindowShouldClose()) {

        /* Permitir salir al menú principal con ESC */
        if (IsKeyPressed(KEY_ESCAPE)) {
            break;
        }

        int len = recv_line(state->socket_fd, line, sizeof(line));
        if (len <= 0) {
            break; /* desconexión o error */
        }

        /* Podemos recibir muchas cosas, pero solo nos interesa STATE */
        char tag[16];
        char gameOverStr[8];
        int  s, pid, x, y, score;

        if (sscanf(line, "%15s %d %d %d %d %d %7s",
                   tag, &s, &pid, &x, &y, &score, gameOverStr) == 7 &&
            strcmp(tag, "STATE") == 0) {

            if (pid == state->playerId) {
                state->playerX  = x;
                state->playerY  = y;
                state->score    = score;
                state->gameOver = (strcmp(gameOverStr, "true") == 0);
            }
        }
        /* Si no es STATE, lo ignoramos */

        BeginDrawing();
            draw_game_scene(state);
        EndDrawing();
    }

    /* Al salir del bucle, simplemente volvemos al menú principal */
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
 *  3. Entrar en un bucle principal donde:
 *      3.1. Se muestra la pantalla de selección de rol.
 *      3.2. Si se elige JUGADOR o ESPECTADOR:
 *           - Se crea una conexión al servidor.
 *           - Se ejecuta el modo correspondiente (jugador / espectador).
 *           - Al salir de ese modo (por ejemplo con ESC), se cierra el socket
 *             y se regresa a la pantalla de selección.
 *      3.3. Si se cierra la ventana o no se elige ningún rol, se sale del bucle.
 *  4. Cerrar ventana y limpiar WinSock.
 *
 * @return 0 en salida normal, 1 en caso de error de WinSock o conexión.
 */
int main(void)
{
    ClientState state;
    // Inicializamos toda la estructura a cero por seguridad
    memset(&state, 0, sizeof(ClientState));
    state.socket_fd = INVALID_SOCKET;
    state.role      = ROLE_NONE;
    state.connected = 0;

    // --- Inicializar WinSock ---
    WSADATA wsa;
    int wsaResult = WSAStartup(MAKEWORD(2, 2), &wsa);
    if (wsaResult != 0) {
        printf("WSAStartup fallo: %d\n", wsaResult);
        return 1;
    }

    // --- Inicializar ventana Raylib ---
    InitWindow(WINDOW_WIDTH, WINDOW_HEIGHT, "DonCEy Kong Jr - Cliente");
    SetTargetFPS(60);
    // Evitar que ESC cierre la ventana automáticamente
    SetExitKey(KEY_NULL);

    int running = 1;

    while (running && !WindowShouldClose()) {

        // 1) Mostrar pantalla inicial: escoger Jugador o Espectador
        state.role = run_role_selection_screen();

        // Si se cerró la ventana dentro de la pantalla de rol o no se eligió nada:
        if (state.role == ROLE_NONE || WindowShouldClose()) {
            break;
        }

        // 2) Conectar al servidor Java
        state.socket_fd = create_and_connect_socket(SERVER_IP, SERVER_PORT);
        if (state.socket_fd == INVALID_SOCKET) {
            printf("No se pudo conectar al servidor.\n");
            // Si quieres, puedes mostrar un texto en pantalla en vez de salir
            running = 0;
            break;
        }
        state.connected = 1;

        // 3) Ejecutar modo según rol seleccionado
        if (state.role == ROLE_PLAYER) {
            run_player_mode(&state);
        } else if (state.role == ROLE_SPECTATOR) {
            run_spectator_mode(&state);
        }

        // 4) Al salir del modo (por ESC o por desconexión), cerramos socket
        if (state.connected && state.socket_fd != INVALID_SOCKET) {
            close_socket(state.socket_fd);
            state.socket_fd = INVALID_SOCKET;
            state.connected = 0;
        }

        // Importante: aquí NO cerramos la ventana.
        // El while se repite y volvemos a mostrar el menú.
    }

    // 5) Cerrar ventana y limpiar WinSock
    if (!WindowShouldClose()) {
        // Si sales del while por una condición propia, igual cerramos la ventana
        // (si ya está cerrada, Raylib lo maneja internamente).
    }
    CloseWindow();
    WSACleanup();

    return 0;
}

