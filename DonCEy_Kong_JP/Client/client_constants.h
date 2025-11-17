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
 *  - socket_fd: descriptor de socket WinSock asociado al servidor.
 *  - role: rol actual del cliente (ROLE_PLAYER o ROLE_SPECTATOR).
 *  - connected: indica si el socket está conectado (1) o no (0).
 */
typedef struct {
    SOCKET socket_fd;
    int role;
    int connected;
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

#endif // CLIENT_CONSTANTS_H
