package chatmulti.ui;

import chatmulti.Server;
import java.io.*;
import java.net.Socket;

public final class ClientHandler implements Runnable {
    private static final String PREFIX_CONNECT = "CONNECT|";

    private final Socket socket;
    private final Server server;
    private final String firstLine; // đã đọc bởi InitialHandshake
    private final DataInputStream in;
    private final DataOutputStream out;
    private String username;
    private volatile boolean closed;

    /** Constructor mới: nhận DataInputStream/DataOutputStream đã tạo + dòng đầu đã đọc */
    public ClientHandler(Socket socket, Server server, String firstLine,
                         DataInputStream in, DataOutputStream out) {
        this.socket = socket;
        this.server = server;
        this.firstLine = firstLine;
        this.in = in;
        this.out = out;
    }

    /** Constructor cũ (dùng cho ServerUI local nếu cần) */
    public ClientHandler(Socket socket, Server server) {
        this.socket = socket;
        this.server = server;
        this.firstLine = null;
        this.in = null;
        this.out = null;
    }

    @Override
    public void run() {
        try {
            DataInputStream dataIn;
            DataOutputStream dataOut;

            if (in != null && out != null) {
                dataIn = in;
                dataOut = out;
            } else {
                dataIn = new DataInputStream(socket.getInputStream());
                dataOut = new DataOutputStream(socket.getOutputStream());
            }

            // Xử lý dòng đầu (CONNECT|username)
            String first = (firstLine != null) ? firstLine.trim() : dataIn.readUTF().trim();
            if (!first.startsWith(PREFIX_CONNECT) || first.length() <= PREFIX_CONNECT.length()) {
                sendRaw(dataOut, "ERROR|Expected CONNECT|username");
                return;
            }
            String name = first.substring(PREFIX_CONNECT.length()).trim();
            if (name.isEmpty() || name.contains("|")) {
                sendRaw(dataOut, "ERROR|Invalid username");
                return;
            }
            // Không cho dùng tên admin
            if (Server.ADMIN_USERNAME.equalsIgnoreCase(name)) {
                sendRaw(dataOut, "ERROR|Username không được dùng");
                return;
            }
            if (server.isUsernameOnline(name)) {
                sendRaw(dataOut, "ERROR|Duplicate username");
                return;
            }
            this.username = name;

            // Gán out trước khi gọi onClientReady để sendUtf hoạt động
            synchronized (this) {
                // Assign internal out field via reflection-free approach: dùng local variable
            }

            // Lưu dataOut để sendUtf dùng
            this.cachedOut = dataOut;

            server.onClientReady(this, name);
            sendRaw(dataOut, "OK_WAITING");

            while (!closed) {
                String cmd = dataIn.readUTF();
                if (cmd == null) break;
                cmd = cmd.trim();
                if (cmd.isEmpty()) continue;
                if ("FILE".equals(cmd)) {
                    String fn = dataIn.readUTF();
                    long sz = dataIn.readLong();
                    if (sz < 0) continue;
                    if (sz > Server.MAX_FILE_BYTES) {
                        drainFully(dataIn, sz);
                        continue;
                    }
                    byte[] buf = new byte[(int) sz];
                    dataIn.readFully(buf);
                    server.handleTcpFileUpload(username, fn, buf);
                }
            }
        } catch (EOFException ignored) {
        } catch (IOException ignored) {
        } finally {
            closed = true;
            try { socket.close(); } catch (IOException ignored) {}
            if (username != null) server.removeClient(this, username);
        }
    }

    // Dùng để tránh phải refactor toàn bộ — lưu DataOutputStream sau khi tạo
    private volatile DataOutputStream cachedOut;

    private static void sendRaw(DataOutputStream d, String line) {
        try { d.writeUTF(line); d.flush(); } catch (IOException ignored) {}
    }

    public void sendUtf(String line) {
        DataOutputStream d = cachedOut;
        if (d == null || closed) return;
        synchronized (this) {
            if (closed) return;
            try {
                d.writeUTF(line);
                d.flush();
            } catch (IOException ignored) {}
        }
    }

    public void sendFileRecv(String fromUser, String fileName, byte[] data) {
        DataOutputStream d = cachedOut;
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
            } catch (IOException ignored) {}
        }
    }

    public void closeSocket() {
        closed = true;
        try { socket.close(); } catch (IOException ignored) {}
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
