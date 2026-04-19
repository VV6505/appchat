package chatmulti.ui;

import chatmulti.Server;
import java.io.*;
import java.net.Socket;

/**
 * Xử lý kết nối TCP của admin client.
 * Giao thức:
 *   Client → Server (lệnh):
 *     ADMIN_CMD|CREATE_ROOM|<room>
 *     ADMIN_CMD|DELETE_ROOM|<room>
 *     ADMIN_CMD|ADD_USER|<user>|<room>
 *     ADMIN_CMD|KICK_USER|<user>
 *     ADMIN_CMD|REQUEST_SNAPSHOT
 *
 *   Server → Client (push events):
 *     SNAPSHOT|ONLINE:u1,u2,...|WAITING:u3,...|ROOMS:r1=u1;u2;,r2=u3;,...
 *     USER_CONNECTED|<user>
 *     USER_DISCONNECTED|<user>
 *     USER_ROOM_CHANGED|<user>|<from>|<to>
 *     ROOM_CREATED|<room>
 *     ROOM_DELETED|<room>
 *     ADMIN_ACK|<msg>
 *     ADMIN_ERR|<msg>
 */
public final class AdminHandler {
    private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;
    private final Server server;
    private volatile boolean closed;

    public AdminHandler(Socket socket, DataInputStream in, DataOutputStream out, Server server) {
        this.socket = socket;
        this.in = in;
        this.out = out;
        this.server = server;
    }

    public void run() {
        // Gửi ngay snapshot hiện tại
        server.sendAdminSnapshot(this);

        try {
            while (!closed) {
                String line;
                try {
                    line = in.readUTF().trim();
                } catch (EOFException | java.net.SocketException e) {
                    break;
                }
                if (!line.startsWith("ADMIN_CMD|")) continue;
                String rest = line.substring("ADMIN_CMD|".length());
                String[] parts = rest.split("\\|", -1);
                if (parts.length == 0) continue;
                String cmd = parts[0];
                switch (cmd) {
                    case "CREATE_ROOM":
                        if (parts.length >= 2 && !parts[1].isBlank()) {
                            server.createRoom(parts[1].trim());
                            sendEvent("ADMIN_ACK|Tạo phòng: " + parts[1].trim());
                        } else sendEvent("ADMIN_ERR|Thiếu tên phòng");
                        break;
                    case "DELETE_ROOM":
                        if (parts.length >= 2 && !parts[1].isBlank()) {
                            server.deleteRoom(parts[1].trim());
                            sendEvent("ADMIN_ACK|Xóa phòng: " + parts[1].trim());
                        } else sendEvent("ADMIN_ERR|Thiếu tên phòng");
                        break;
                    case "ADD_USER":
                        if (parts.length >= 3 && !parts[1].isBlank() && !parts[2].isBlank()) {
                            server.addWaitingUserToRoom(parts[1].trim(), parts[2].trim());
                            sendEvent("ADMIN_ACK|Thêm " + parts[1].trim() + " vào " + parts[2].trim());
                        } else sendEvent("ADMIN_ERR|Thiếu user hoặc phòng");
                        break;
                    case "KICK_USER":
                        if (parts.length >= 2 && !parts[1].isBlank()) {
                            server.removeUserFromRoom(parts[1].trim());
                            sendEvent("ADMIN_ACK|Kick " + parts[1].trim() + " về waiting");
                        } else sendEvent("ADMIN_ERR|Thiếu tên user");
                        break;
                    case "REQUEST_SNAPSHOT":
                        server.sendAdminSnapshot(this);
                        break;
                    default:
                        sendEvent("ADMIN_ERR|Unknown command: " + cmd);
                }
            }
        } catch (IOException ignored) {
        } finally {
            closed = true;
            try { socket.close(); } catch (IOException ignored) {}
            server.log("Admin disconnected");
        }
    }

    public void sendEvent(String event) {
        if (closed) return;
        synchronized (this) {
            if (closed) return;
            try {
                out.writeUTF(event);
                out.flush();
            } catch (IOException ignored) {
                closed = true;
            }
        }
    }

    public void close() {
        closed = true;
        try { socket.close(); } catch (IOException ignored) {}
    }
}
