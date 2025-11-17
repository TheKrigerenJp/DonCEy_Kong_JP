#include "client_constants.h"

/* ============================
 *  S O C K E T S  (WinSock)
 * ============================ */

/**
 * Crea un SOCKET TCP y se conecta al servidor especificado.
 *
 * @param ip   Cadena con la dirección IP del servidor.
 * @param port Puerto TCP del servidor.
 * @return     SOCKET conectado en caso de éxito, o INVALID_SOCKET en error.
 */
SOCKET create_and_connect_socket(const char *ip, int port)
{
    SOCKET fd;
    struct sockaddr_in addr;

    fd = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if (fd == INVALID_SOCKET) {
        printf("Error al crear socket: %ld\n", WSAGetLastError());
        return INVALID_SOCKET;
    }

    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_port = htons((u_short)port);

    // inet_pton para convertir IP string -> binario
    if (inet_pton(AF_INET, ip, &addr.sin_addr) <= 0) {
        printf("Error en inet_pton\n");
        closesocket(fd);
        return INVALID_SOCKET;
    }

    if (connect(fd, (struct sockaddr *)&addr, sizeof(addr)) == SOCKET_ERROR) {
        printf("No se pudo conectar al servidor %s:%d. Error: %ld\n",
               ip, port, WSAGetLastError());
        closesocket(fd);
        return INVALID_SOCKET;
    }

    printf("Conectado a %s:%d\n", ip, port);
    return fd;
}

/**
 * Envía una línea de texto al servidor.
 *
 * @param socket_fd Descriptor de socket conectado.
 * @param line      Cadena a enviar. No se agrega '\n' automáticamente.
 */
void send_line(SOCKET socket_fd, const char *line)
{
    int len = (int)strlen(line);
    int sent = send(socket_fd, line, len, 0);
    if (sent == SOCKET_ERROR) {
        printf("Error al enviar datos: %ld\n", WSAGetLastError());
    }
}

/**
 * Recibe una línea de texto desde el servidor (terminada en '\n').
 *
 * La función lee del socket carácter por carácter usando recv(), hasta
 * encontrar un salto de línea '\n' o hasta llenar el búfer menos uno
 * (para el terminador '\0').
 *
 * También elimina un posible '\r' final típico de líneas con "\r\n".
 *
 * @param socket_fd  Descriptor de socket WinSock desde el que se lee.
 * @param buffer     Búfer de destino donde se almacenará la línea.
 * @param bufferSize Tamaño total del búfer en bytes. Debe ser > 1.
 * @return Número de caracteres leídos (sin contar el '\0') en caso de éxito,
 *         o -1 si la conexión se cerró o se produjo un error.
 */
int recv_line(SOCKET socket_fd, char *buffer, int bufferSize)
{
    if (bufferSize <= 1) {
        return -1;
    }

    int total = 0;
    char c;

    while (total < bufferSize - 1) {
        int ret = recv(socket_fd, &c, 1, 0);
        if (ret == 0) {
            /* Conexión cerrada por el servidor */
            return -1;
        }
        if (ret < 0) {
            /* Error en recv() */
            int err = WSAGetLastError();
            if (err == WSAEINTR) {
                continue; /* reintentar si fue interrumpido */
            }
            return -1;
        }

        if (c == '\n') {
            break; /* fin de línea */
        }

        buffer[total++] = c;
    }

    buffer[total] = '\0';

    /* Eliminar '\r' final si viene de "\r\n" */
    if (total > 0 && buffer[total - 1] == '\r') {
        buffer[total - 1] = '\0';
        total--;
    }

    return total;
}



/**
 * Cierra un socket WinSock.
 *
 * @param socket_fd Descriptor de socket que se desea cerrar.
 *                  Si es INVALID_SOCKET, no se hace nada.
 */
void close_socket(SOCKET socket_fd)
{
    if (socket_fd != INVALID_SOCKET) {
        closesocket(socket_fd);
    }
}
