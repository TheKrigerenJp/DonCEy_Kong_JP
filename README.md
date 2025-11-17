Como compilar la parte de C:
gcc client_interface.c client_sockets.c -o client.exe -I C:\Users\Josepa\DonCEy_Kong_JP\DonCEy_Kong_JP\Client\lib\raylib\include -L C:\Users\Josepa\DonCEy_Kong_JP\DonCEy_Kong_JP\Client\lib\raylib\lib -lraylib -lws2_32  -lopengl32 -lgdi32 -lwinmm -std=c99
