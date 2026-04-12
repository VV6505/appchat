package chatmulti;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

public final class UDPServer implements Runnable {
    private static final String PREFIX_REGISTER = "REGISTER|";
    private static final String PREFIX_ROOM_MSG = "ROOM_MSG|";

    private final Server server;
    private volatile DatagramSocket socket;

    public UDPServer(Server server) {
        this.server = server;
    }

    @Override
    public void run() {
        try {
            socket = new DatagramSocket(Server.UDP_PORT);
            socket.setReuseAddress(true);
        } catch (SocketException e) {
            server.log("UDP bind failed on " + Server.UDP_PORT + ": " + e.getMessage());
            return;
        }

        byte[] buf = new byte[65507];
        while (!Thread.currentThread().isInterrupted()) {
            DatagramSocket ds = socket;
            if (ds == null || ds.isClosed()) break;
            DatagramPacket p = new DatagramPacket(buf, buf.length);
            try {
                ds.receive(p);
            } catch (IOException e) {
                if (socket != null && !socket.isClosed()) server.log("UDP receive: " + e.getMessage());
                break;
            }
            int len = p.getLength();
            if (len <= 0) continue;
            String msg = new String(p.getData(), 0, len, StandardCharsets.UTF_8).trim();
            if (msg.startsWith(PREFIX_REGISTER) && msg.length() > PREFIX_REGISTER.length()) {
                String user = msg.substring(PREFIX_REGISTER.length()).trim();
                if (!user.isEmpty() && !user.contains("|"))
                    server.registerUdpEndpoint(user, (InetSocketAddress) p.getSocketAddress());
                continue;
            }
            if (msg.startsWith(PREFIX_ROOM_MSG)) {
                handleRoomMsg(msg, (InetSocketAddress) p.getSocketAddress());
            }
        }
    }

    private void handleRoomMsg(String msg, InetSocketAddress from) {
        String rest = msg.substring(PREFIX_ROOM_MSG.length());
        String[] parts = rest.split("\\|", 3);
        if (parts.length < 3) return;
        String room = parts[0].trim();
        String user = parts[1].trim();
        String payload = parts[2];
        if (room.isEmpty() || user.isEmpty()) return;
        if (!server.isUsernameOnline(user)) return;
        InetSocketAddress reg = server.getUdpEndpoint(user);
        if (reg == null || !reg.equals(from)) return;
        if (!server.isUserInRoom(user, room)) return;

        String forward = PREFIX_ROOM_MSG + room + "|" + user + "|" + payload;
        byte[] data = forward.getBytes(StandardCharsets.UTF_8);
        if (data.length > 65507) return;

        Set<String> members = server.getRoomMembers(room);
        DatagramSocket ds = socket;
        if (ds == null || ds.isClosed()) return;

        for (String member : members) {
            if (member.equals(user)) continue;
            InetSocketAddress to = server.getUdpEndpoint(member);
            if (to == null) continue;
            try {
                ds.send(new DatagramPacket(data, data.length, to));
            } catch (IOException ignored) {
            }
        }
    }

    public void close() {
        DatagramSocket ds = socket;
        socket = null;
        if (ds != null && !ds.isClosed()) ds.close();
    }
}
