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
