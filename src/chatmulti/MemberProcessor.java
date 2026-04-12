package chatmulti;

import java.io.*;
import java.net.*;
import java.nio.file.Files;

public class MemberProcessor implements Runnable {
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private String clientName;
    private volatile boolean isRunning = true;

    public MemberProcessor(Socket socket) {
        this.socket = socket;
        try {
            this.in = new DataInputStream(socket.getInputStream());
            this.out = new DataOutputStream(socket.getOutputStream());
        } catch (IOException e) { e.printStackTrace(); }
    }

    @Override
    public void run() {
        try {
            this.clientName = in.readUTF();
            ChatServer.sendHistoryTo(this);
            ChatServer.broadcast("[Hệ thống]: " + clientName + " đã tham gia.", "SYSTEM");
            ChatServer.updateUserList();

            while (isRunning) {
                String cmd = in.readUTF();
                if (cmd.startsWith("MSG:")) {
                    ChatServer.broadcast(clientName + ": " + cmd.substring(4), "TEXT");
                } else if (cmd.startsWith("IMG:") || cmd.startsWith("FILE:")) {
                    handleFile(cmd);
                }
            }
        } catch (IOException e) {
            ChatServer.removeMember(this);
            ChatServer.updateUserList();
        }
    }

    private void handleFile(String cmd) throws IOException {
        String type = cmd.startsWith("IMG:") ? "IMAGE" : "FILE";
        String fileName = cmd.substring(4);
        long size = in.readLong();
        File dir = new File("server_storage/" + type.toLowerCase() + "s");
        if (!dir.exists()) dir.mkdirs();
        File file = new File(dir, System.currentTimeMillis() + "_" + fileName);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            byte[] buf = new byte[4096];
            int read; long remaining = size;
            while (remaining > 0 && (read = in.read(buf, 0, (int)Math.min(buf.length, remaining))) != -1) {
                fos.write(buf, 0, read); remaining -= read;
            }
        }
        if ("IMAGE".equals(type)) {
            final long maxImg = 5L * 1024 * 1024;
            if (size > maxImg) {
                ChatServer.broadcast(clientName + " — ảnh quá lớn (tối đa 5MB): " + fileName, "TEXT");
                return;
            }
            byte[] raw = Files.readAllBytes(file.toPath());
            ChatServer.broadcastImage(clientName, fileName, raw);
        } else {
            ChatServer.broadcast(clientName + " đã gửi " + type + ": " + fileName, type);
        }
    }

    public void sendMessage(String msg) { try { out.writeUTF(msg); } catch (Exception e) {} }

    public void sendLive(String typePipeMsg) {
        try {
            out.writeUTF("LIVE:" + typePipeMsg);
        } catch (Exception e) { }
    }

    public void sendImageChunk(String sender, String fileName, byte[] data) {
        try {
            out.writeUTF("IMGCHUNK");
            out.writeUTF(sender);
            out.writeUTF(fileName);
            out.writeInt(data.length);
            out.write(data);
            out.flush();
        } catch (IOException e) { }
    }

    public void stop() { isRunning = false; try { socket.close(); } catch (Exception e) {} }
    public String getClientName() { return clientName; }
}