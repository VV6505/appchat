package chatmulti;

import chatmulti.ui.AdminHandler;
import chatmulti.ui.ClientHandler;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class Server {
    public static final int TCP_PORT = 5000;
    public static final int UDP_PORT = 6000;
    public static final long MAX_FILE_BYTES = 5L * 1024 * 1024;

    // ── Admin credential (hardcoded) ──────────────────────────────────────────
    public static final String ADMIN_USERNAME = "admin";
    public static final String ADMIN_PASSWORD = "admin123";

    private volatile ServerSocket tcpSocket;
    private volatile UDPServer udpServer;
    private volatile Thread acceptThread;
    private volatile boolean running;
    private volatile boolean shuttingDown;

    private volatile boolean autoJoinDefaultRoom;
    private volatile String defaultRoomName = "lobby";

    private final Map<String, ClientHandler> onlineByName = new ConcurrentHashMap<>();
    private final Set<String> waitingUsers = ConcurrentHashMap.newKeySet();
    private final Map<String, Set<String>> rooms = new ConcurrentHashMap<>();
    private final Map<String, String> userToRoom = new ConcurrentHashMap<>();
    private final Map<String, InetSocketAddress> udpByUser = new ConcurrentHashMap<>();

    // ── Admin TCP connections (separate handler set) ──────────────────────────
    private final Set<AdminHandler> adminHandlers = ConcurrentHashMap.newKeySet();

    public Server() {
        // headless — log ra System.out
    }

    // ── Headless entry point ──────────────────────────────────────────────────
    public static void main(String[] args) {
        System.out.println("[chatmulti] Starting server in headless mode...");
        Server server = new Server();
        try {
            server.start();
            System.out.println("[chatmulti] Server running. TCP=" + TCP_PORT + " UDP=" + UDP_PORT
                    + " | Admin user='" + ADMIN_USERNAME + "'");
            // Block mãi mãi — Azure / systemd sẽ kill process khi cần
            Thread.currentThread().join();
        } catch (IOException e) {
            System.err.println("[chatmulti] Failed to start: " + e.getMessage());
            System.exit(1);
        } catch (InterruptedException ignored) {
        }
    }

    public void setAutoJoinDefaultRoom(boolean enabled, String roomName) {
        this.autoJoinDefaultRoom = enabled;
        if (roomName != null && !roomName.isBlank()) {
            this.defaultRoomName = roomName.trim();
        }
    }

    public boolean isRunning() {
        return running;
    }

    public void start() throws IOException {
        if (running) return;
        shuttingDown = false;
        tcpSocket = new ServerSocket(TCP_PORT);
        udpServer = new UDPServer(this);
        new Thread(udpServer, "chatmulti-udp").start();
        running = true;
        acceptThread = new Thread(this::acceptLoop, "chatmulti-tcp-accept");
        acceptThread.start();
        log("Server listening TCP " + TCP_PORT + ", UDP " + UDP_PORT);
    }

    private void acceptLoop() {
        ServerSocket ss = tcpSocket;
        while (running && ss != null && !ss.isClosed()) {
            try {
                Socket s = ss.accept();
                // Handshake: đọc dòng đầu tiên để phân biệt admin vs user thường
                new Thread(new InitialHandshake(s, this), "chatmulti-init").start();
            } catch (IOException e) {
                if (running) log("Accept error: " + e.getMessage());
                break;
            }
        }
    }

    /** Đọc dòng đầu tiên từ socket và quyết định route tới ClientHandler hay AdminHandler */
    static final class InitialHandshake implements Runnable {
        private final Socket socket;
        private final Server server;

        InitialHandshake(Socket socket, Server server) {
            this.socket = socket;
            this.server = server;
        }

        @Override
        public void run() {
            try {
                java.io.DataInputStream in = new java.io.DataInputStream(socket.getInputStream());
                java.io.DataOutputStream out = new java.io.DataOutputStream(socket.getOutputStream());
                String first = in.readUTF().trim();

                if (first.startsWith("ADMIN_LOGIN|")) {
                    // Format: ADMIN_LOGIN|password
                    String password = first.substring("ADMIN_LOGIN|".length()).trim();
                    if (!ADMIN_PASSWORD.equals(password)) {
                        out.writeUTF("ADMIN_ERROR|Sai mật khẩu admin");
                        out.flush();
                        socket.close();
                        return;
                    }
                    out.writeUTF("ADMIN_OK");
                    out.flush();
                    server.log("Admin connected from " + socket.getRemoteSocketAddress());
                    AdminHandler ah = new AdminHandler(socket, in, out, server);
                    server.adminHandlers.add(ah);
                    ah.run(); // chạy trực tiếp trên thread này
                    server.adminHandlers.remove(ah);
                } else if (first.startsWith("CONNECT|")) {
                    // User thường — chuyển sang ClientHandler
                    ClientHandler ch = new ClientHandler(socket, server, first, in, out);
                    ch.run();
                } else {
                    out.writeUTF("ERROR|Unknown handshake");
                    out.flush();
                    socket.close();
                }
            } catch (IOException ignored) {
                try { socket.close(); } catch (IOException ignored2) {}
            }
        }
    }

    public void stop() {
        shuttingDown = true;
        running = false;
        if (udpServer != null) udpServer.close();
        udpServer = null;
        for (AdminHandler ah : new ArrayList<>(adminHandlers)) ah.close();
        adminHandlers.clear();
        for (ClientHandler h : new ArrayList<>(onlineByName.values())) h.closeSocket();
        onlineByName.clear();
        waitingUsers.clear();
        rooms.clear();
        userToRoom.clear();
        udpByUser.clear();
        ServerSocket ss = tcpSocket;
        tcpSocket = null;
        if (ss != null) {
            try { ss.close(); } catch (IOException ignored) {}
        }
        log("Server stopped.");
    }

    public void log(String line) {
        System.out.println("[" + java.time.LocalTime.now().withNano(0) + "] " + line);
    }

    public void onClientReady(ClientHandler handler, String username) {
        onlineByName.put(username, handler);
        waitingUsers.add(username);
        log(username + " connected");
        broadcastAdminEvent("USER_CONNECTED|" + username);
        if (autoJoinDefaultRoom && defaultRoomName != null && !defaultRoomName.isEmpty()) {
            addWaitingUserToRoom(username, defaultRoomName);
        }
    }

    void registerUdpEndpoint(String username, InetSocketAddress addr) {
        if (!onlineByName.containsKey(username)) return;
        udpByUser.put(username, addr);
    }

    public boolean isUsernameOnline(String name) {
        return onlineByName.containsKey(name);
    }

    public boolean isUserInRoom(String username, String room) {
        Set<String> m = rooms.get(room);
        return m != null && m.contains(username);
    }

    public Set<String> getRoomMembers(String room) {
        Set<String> m = rooms.get(room);
        return m == null ? Collections.emptySet() : Collections.unmodifiableSet(new LinkedHashSet<>(m));
    }

    public InetSocketAddress getUdpEndpoint(String username) {
        return udpByUser.get(username);
    }

    public void removeClient(ClientHandler handler, String username) {
        if (username == null) return;
        onlineByName.remove(username, handler);
        waitingUsers.remove(username);
        udpByUser.remove(username);
        String room = userToRoom.remove(username);
        if (room != null) {
            Set<String> set = rooms.get(room);
            if (set != null) set.remove(username);
        }
        if (!shuttingDown) {
            log(username + " left (disconnect)");
            broadcastTcp("SYSTEM|" + username + " đã rời khỏi nhóm");
        }
        broadcastAdminEvent("USER_DISCONNECTED|" + username);
    }

    void broadcastTcp(String line) {
        for (ClientHandler h : new ArrayList<>(onlineByName.values())) h.sendUtf(line);
    }

    void broadcastRoomFile(String fromUser, String fileName, byte[] data) {
        String room = userToRoom.get(fromUser);
        if (room == null) return;
        for (String member : getRoomMembers(room)) {
            if (member.equals(fromUser)) continue;
            ClientHandler h = onlineByName.get(member);
            if (h != null) h.sendFileRecv(fromUser, fileName, data);
        }
    }

    public void handleTcpFileUpload(String username, String fileName, byte[] data) {
        if (data == null || data.length == 0) return;
        if (data.length > MAX_FILE_BYTES) {
            log(username + " — file quá lớn: " + fileName);
            return;
        }
        if (userToRoom.get(username) == null) {
            log(username + " — bỏ qua file (chưa trong phòng): " + fileName);
            return;
        }
        broadcastRoomFile(username, fileName, data);
    }

    public void createRoom(String name) {
        String r = name == null ? "" : name.trim();
        if (r.isEmpty()) return;
        rooms.computeIfAbsent(r, k -> ConcurrentHashMap.newKeySet());
        log("Room created: " + r);
        broadcastAdminEvent("ROOM_CREATED|" + r);
    }

    public void deleteRoom(String roomName) {
        String r = roomName == null ? "" : roomName.trim();
        if (r.isEmpty()) return;
        Set<String> members = rooms.remove(r);
        if (members == null) return;
        for (String u : new ArrayList<>(members)) {
            userToRoom.remove(u);
            waitingUsers.add(u);
            ClientHandler h = onlineByName.get(u);
            if (h != null) h.sendUtf("BACK_TO_WAITING");
        }
        log("Room deleted: " + r);
        broadcastAdminEvent("ROOM_DELETED|" + r);
    }

    public void removeUserFromRoom(String username) {
        String u = username == null ? "" : username.trim();
        if (u.isEmpty()) return;
        String room = userToRoom.remove(u);
        if (room == null) return;
        Set<String> set = rooms.get(room);
        if (set != null) set.remove(u);
        waitingUsers.add(u);
        ClientHandler h = onlineByName.get(u);
        if (h != null) h.sendUtf("BACK_TO_WAITING");
        log(u + " removed from room " + room);
        broadcastAdminEvent("USER_ROOM_CHANGED|" + u + "|" + room + "|waiting");
    }

    public void addWaitingUserToRoom(String username, String roomName) {
        String u = username == null ? "" : username.trim();
        String r = roomName == null ? "" : roomName.trim();
        if (u.isEmpty() || r.isEmpty()) return;
        if (!waitingUsers.contains(u)) return;
        rooms.computeIfAbsent(r, k -> ConcurrentHashMap.newKeySet());
        waitingUsers.remove(u);
        userToRoom.put(u, r);
        rooms.get(r).add(u);
        ClientHandler h = onlineByName.get(u);
        if (h != null) {
            h.sendUtf("JOINED_ROOM|" + r);
            broadcastTcp("SYSTEM|" + u + " đã vào phòng " + r);
        }
        log(u + " joined " + r);
        broadcastAdminEvent("USER_ROOM_CHANGED|" + u + "|waiting|" + r);
    }

    /** Gửi event tới tất cả AdminHandler đang kết nối */
    public void broadcastAdminEvent(String event) {
        for (AdminHandler ah : new ArrayList<>(adminHandlers)) {
            ah.sendEvent(event);
        }
    }

    /** Gửi full state snapshot tới một AdminHandler */
    public void sendAdminSnapshot(AdminHandler ah) {
        StringBuilder sb = new StringBuilder("SNAPSHOT");
        sb.append("|ONLINE:");
        for (String u : snapshotOnline()) sb.append(u).append(",");
        sb.append("|WAITING:");
        for (String u : snapshotWaiting()) sb.append(u).append(",");
        sb.append("|ROOMS:");
        for (String r : snapshotRoomNames()) {
            sb.append(r).append("=");
            for (String u : snapshotUsersInRoom(r)) sb.append(u).append(";");
            sb.append(",");
        }
        ah.sendEvent(sb.toString());
    }

    public List<String> snapshotOnline() {
        List<String> list = new ArrayList<>(onlineByName.keySet());
        Collections.sort(list);
        return list;
    }

    public List<String> snapshotWaiting() {
        List<String> list = new ArrayList<>(waitingUsers);
        Collections.sort(list);
        return list;
    }

    public List<String> snapshotRoomNames() {
        List<String> list = new ArrayList<>(rooms.keySet());
        Collections.sort(list);
        return list;
    }

    public List<String> snapshotUsersInRoom(String room) {
        if (room == null || room.isEmpty()) return Collections.emptyList();
        Set<String> m = rooms.get(room.trim());
        if (m == null) return Collections.emptyList();
        List<String> list = new ArrayList<>(m);
        Collections.sort(list);
        return list;
    }

}
