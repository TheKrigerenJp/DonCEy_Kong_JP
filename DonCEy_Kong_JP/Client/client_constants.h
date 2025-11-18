#ifndef CLIENT_CONSTANTS_H
#define CLIENT_CONSTANTS_H

// ==== Desactivar partes de WinAPI que chocan con raylib ====
// Estas macros deben definirse ANTES de incluir winsock2.h / windows.h
#define WIN32_LEAN_AND_MEAN   // Reduce lo que incluye windows.h
#define NOGDI                 // Evita GDI (Rectangle, etc.)
#define NOUSER                // Evita User32 (CloseWindow, ShowCursor, DrawText macros)

#include <winsock2.h>
#include <ws2tcpip.h>

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

// ---------------- Mapa lógico recibido del servidor ----------------

/**
 * Representa el mapa lógico enviado por el servidor.
 *
 * - width  : número de columnas válidas.
 * - height : número de filas válidas.
 * - tiles  : matriz de caracteres con el contenido por celda.
 *            Cada carácter coincide con los usados por el servidor:
 *            'W', 'T', '=', '|', 'S', 'G' o '.'.
 */
#define MAX_MAP_WIDTH   32
#define MAX_MAP_HEIGHT  32

typedef struct {
    int width;
    int height;
    char tiles[MAX_MAP_HEIGHT][MAX_MAP_WIDTH + 1]; /* +1 por seguridad con '\0' */
} GameMap;


#define MAX_PLAYERS 16

typedef struct {
    int  id;
    char name[32];
} PlayerInfo;


// ---------------- Constantes generales de ventana ----------------

/**
 * Ancho de la ventana principal del cliente en píxeles.
 */
#define WINDOW_WIDTH   800

/**
 * Alto de la ventana principal del cliente en píxeles.
 */
#define WINDOW_HEIGHT  450

// ---------------- Constantes de conexión al servidor ----------------

/**
 * Dirección IP del servidor de juego al que se conecta el cliente.
 */
#define SERVER_IP      "127.0.0.1"

/**
 * Puerto TCP del servidor de juego.
 */
#define SERVER_PORT    5000

// ---------------- Roles del cliente ----------------

/**
 * Valor para indicar que aún no se ha seleccionado ningún rol.
 */
#define ROLE_NONE       0

/**
 * Rol de cliente jugador.
 */
#define ROLE_PLAYER     1

/**
 * Rol de cliente espectador.
 */
#define ROLE_SPECTATOR  2


// ---------------- Estado del cliente ----------------

/**
 * Estructura que representa el estado lógico del cliente.
 * 
 * Campos:
 *  - socket_fd : descriptor de socket WinSock asociado al servidor.
 *  - role      : rol actual del cliente (ROLE_PLAYER o ROLE_SPECTATOR).
 *  - connected : indica si el socket está conectado (1) o no (0).
 *  - map       : copia local del mapa lógico enviado por el servidor.
 *  - playerId  : identificador asignado por el servidor (JOINED <id>).
 *  - playerX   : coordenada X lógica del jugador (en tiles).
 *  - playerY   : coordenada Y lógica del jugador (en tiles).
 *  - score     : puntuación actual del jugador.
 *  - gameOver  : indica si el servidor marca la partida como terminada.
 */
typedef struct {
    SOCKET socket_fd;
    int    role;
    int    connected;

    GameMap map;

    int playerId;
    int playerX;
    int playerY;
    int score;
    int level;   /* nivel actual enviado por el servidor */
    int lives;   /* vidas restantes */
    int gameOver;

    int        numPlayers;
    PlayerInfo players[MAX_PLAYERS];

} ClientState;



// ---------------- Prototipos: funciones de sockets ----------------

/**
 * Crea un socket TCP y lo conecta al servidor indicado.
 *
 * @param ip   Cadena con la dirección IP del servidor (por ejemplo "127.0.0.1").
 * @param port Puerto TCP del servidor.
 * @return     Un SOCKET válido conectado en caso de éxito,
 *             o INVALID_SOCKET en caso de error (se imprime el error por consola).
 */
SOCKET create_and_connect_socket(const char *ip, int port);

/**
 * Envía una línea de texto al servidor a través del socket indicado.
 *
 * @param socket_fd Descriptor de socket WinSock válido.
 * @param line      Cadena de texto a enviar. La función no agrega '\n',
 *                  por lo que el llamador decide si lo incluye.
 */
void send_line(SOCKET socket_fd, const char *line);

/**
 * Cierra un socket WinSock de forma segura.
 *
 * @param socket_fd Descriptor de socket a cerrar. Si es INVALID_SOCKET,
 *                  la función no realiza ninguna acción.
 */
void close_socket(SOCKET socket_fd);

/**
 * Recibe una línea de texto desde el servidor (terminada en '\n').
 *
 * La función lee del socket carácter por carácter hasta encontrar
 * un '\n' o hasta llenar el búfer (dejando siempre espacio para '\0').
 *
 * @param socket_fd  Descriptor de socket WinSock desde el que se lee.
 * @param buffer     Búfer de destino donde se almacenará la línea.
 * @param bufferSize Tamaño total del búfer en bytes.
 * @return Número de caracteres leídos (sin contar el '\0') en caso de éxito,
 *         o -1 si la conexión se cerró o hubo un error.
 */
int recv_line(SOCKET socket_fd, char *buffer, int bufferSize);


// ---------------- Prototipos: funciones de interfaz ----------------

/**
 * Muestra la pantalla inicial para seleccionar el rol del cliente.
 *
 * La pantalla ofrece dos botones:
 *  - "JUGADOR" → devuelve ROLE_PLAYER
 *  - "ESPECTADOR" → devuelve ROLE_SPECTATOR
 *
 * @return ROLE_PLAYER, ROLE_SPECTATOR o ROLE_NONE si se cierra la ventana.
 */
int run_role_selection_screen(void);

/**
 * Ejecuta el bucle principal del cliente en modo jugador.
 *
 * @param state Puntero al estado del cliente ya inicializado y con el socket
 *              conectado al servidor.
 */
void run_player_mode(ClientState *state);

/**
 * Ejecuta el bucle principal del cliente en modo espectador.
 *
 * @param state Puntero al estado del cliente ya inicializado y con el socket
 *              conectado al servidor.
 */
void run_spectator_mode(ClientState *state);

#endif //CLIENT_CONSTANTS_H
