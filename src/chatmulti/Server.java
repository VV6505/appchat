package chatmulti;

import chatmulti.ui.ServerUI;

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

    private final ServerUI ui;
    private volatile ServerSocket tcpSocket;
    private volatile UDPServer udpServer;
    private volatile Thread acceptThread;
    private volatile boolean running;
    private volatile boolean shuttingDown;

    private final Map<String, ClientHandler> onlineByName = new ConcurrentHashMap<>();
    private final Set<String> waitingUsers = ConcurrentHashMap.newKeySet();
    private final Map<String, Set<String>> rooms = new ConcurrentHashMap<>();
    private final Map<String, String> userToRoom = new ConcurrentHashMap<>();
    private final Map<String, InetSocketAddress> udpByUser = new ConcurrentHashMap<>();

    public Server(ServerUI ui) {
        this.ui = ui;
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
                ClientHandler h = new ClientHandler(s, this);
                new Thread(h, "chatmulti-client").start();
            } catch (IOException e) {
                if (running) log("Accept error: " + e.getMessage());
                break;
            }
        }
    }

    public void stop() {
        shuttingDown = true;
        running = false;
        if (udpServer != null) udpServer.close();
        udpServer = null;
        for (ClientHandler h : new ArrayList<>(onlineByName.values())) h.closeSocket();
        onlineByName.clear();
        waitingUsers.clear();
        rooms.clear();
        userToRoom.clear();
        udpByUser.clear();
        ServerSocket ss = tcpSocket;
        tcpSocket = null;
        if (ss != null) {
            try {
                ss.close();
            } catch (IOException ignored) {
            }
        }
        pushUi();
        log("Server stopped.");
    }

    void log(String line) {
        if (ui != null) ui.appendLog(line);
    }

    void onClientReady(ClientHandler handler, String username) {
        onlineByName.put(username, handler);
        waitingUsers.add(username);
        log(username + " connected");
        pushUi();
    }

    void registerUdpEndpoint(String username, InetSocketAddress addr) {
        if (!onlineByName.containsKey(username)) return;
        udpByUser.put(username, addr);
    }

    boolean isUsernameOnline(String name) {
        return onlineByName.containsKey(name);
    }

    boolean isUserInRoom(String username, String room) {
        Set<String> m = rooms.get(room);
        return m != null && m.contains(username);
    }

    Set<String> getRoomMembers(String room) {
        Set<String> m = rooms.get(room);
        return m == null ? Collections.emptySet() : Collections.unmodifiableSet(new LinkedHashSet<>(m));
    }

    InetSocketAddress getUdpEndpoint(String username) {
        return udpByUser.get(username);
    }

    void removeClient(ClientHandler handler, String username) {
        if (username == null) return;
        onlineByName.remove(username, handler);
        waitingUsers.remove(username);
        udpByUser.remove(username);
        String room = userToRoom.remove(username);
        if (room != null) {
            Set<String> set = rooms.get(room);
            if (set != null) set.remove(username);
            /* Phòng vẫn giữ dù không còn ai — chỉ admin mới xóa phòng */
        }
        if (!shuttingDown) {
            log(username + " left (disconnect)");
            broadcastTcp("SYSTEM|" + username + " đã rời khỏi nhóm");
        }
        pushUi();
    }

    void broadcastTcp(String line) {
        for (ClientHandler h : new ArrayList<>(onlineByName.values())) h.sendUtf(line);
    }

    /** Gửi file/ảnh trong phòng (TCP), tới mọi thành viên khác người gửi. */
    void broadcastRoomFile(String fromUser, String fileName, byte[] data) {
        String room = userToRoom.get(fromUser);
        if (room == null) return;
        for (String member : getRoomMembers(room)) {
            if (member.equals(fromUser)) continue;
            ClientHandler h = onlineByName.get(member);
            if (h != null) h.sendFileRecv(fromUser, fileName, data);
        }
    }

    void handleTcpFileUpload(String username, String fileName, byte[] data) {
        if (data == null || data.length == 0) return;
        if (data.length > MAX_FILE_BYTES) {
            log(username + " — file quá lớn (tối đa 5MB): " + fileName);
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
        pushUi();
    }

    /** Xóa phòng: mọi user trong phòng trở lại chờ; gửi BACK_TO_WAITING. */
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
        pushUi();
    }

    /** Đưa user đang trong phòng về danh sách chờ. */
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
        pushUi();
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
        pushUi();
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

    void pushUi() {
        if (ui != null) ui.refreshListsFromServer();
    }
}
