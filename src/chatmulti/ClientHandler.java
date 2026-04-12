package chatmulti;

import java.io.*;
import java.net.Socket;

public final class ClientHandler implements Runnable {
    private static final String PREFIX_CONNECT = "CONNECT|";

    private final Socket socket;
    private final Server server;
    private DataInputStream in;
    private DataOutputStream out;
    private String username;
    private volatile boolean closed;

    public ClientHandler(Socket socket, Server server) {
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        try {
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());

            String first = in.readUTF();
            first = first.trim();
            if (!first.startsWith(PREFIX_CONNECT) || first.length() <= PREFIX_CONNECT.length()) {
                sendUtf("ERROR|Expected CONNECT|username");
                return;
            }
            String name = first.substring(PREFIX_CONNECT.length()).trim();
            if (name.isEmpty() || name.contains("|")) {
                sendUtf("ERROR|Invalid username");
                return;
            }
            if (server.isUsernameOnline(name)) {
                sendUtf("ERROR|Duplicate username");
                return;
            }
            this.username = name;
            server.onClientReady(this, name);
            sendUtf("OK_WAITING");

            while (!closed) {
                String cmd = in.readUTF();
                if (cmd == null) break;
                cmd = cmd.trim();
                if (cmd.isEmpty()) continue;
                if ("FILE".equals(cmd)) {
                    String fn = in.readUTF();
                    long sz = in.readLong();
                    if (sz < 0) continue;
                    if (sz > Server.MAX_FILE_BYTES) {
                        drainFully(in, sz);
                        continue;
                    }
                    byte[] buf = new byte[(int) sz];
                    in.readFully(buf);
                    server.handleTcpFileUpload(username, fn, buf);
                }
            }
        } catch (EOFException ignored) {
        } catch (IOException ignored) {
        } finally {
            closed = true;
            try {
                socket.close();
            } catch (IOException ignored) {
            }
            if (username != null) server.removeClient(this, username);
        }
    }

    public void sendUtf(String line) {
        DataOutputStream d = out;
        if (d == null || closed) return;
        synchronized (this) {
            if (closed) return;
            try {
                d.writeUTF(line);
                d.flush();
            } catch (IOException ignored) {
            }
        }
    }

    public void sendFileRecv(String fromUser, String fileName, byte[] data) {
        DataOutputStream d = out;
        if (d == null || closed || data == null) return;
        synchronized (this) {
            if (closed) return;
            try {
                d.writeUTF("FILE_RECV");
                d.writeUTF(fromUser);
                d.writeUTF(fileName);
                d.writeInt(data.length);
                d.write(data);
                d.flush();
            } catch (IOException ignored) {
            }
        }
    }

    public void closeSocket() {
        closed = true;
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    private static void drainFully(DataInputStream in, long bytes) throws IOException {
        byte[] dump = new byte[8192];
        long left = bytes;
        while (left > 0) {
            int n = in.read(dump, 0, (int) Math.min(dump.length, left));
            if (n < 0) break;
            left -= n;
        }
    }
}
