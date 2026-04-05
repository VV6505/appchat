package chatmulti;

import java.io.*;
import java.net.*;

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
                } else if (cmd.startsWith("IMG:")) {
                    handleImageTransfer(cmd.substring(4));
                }
            }
        } catch (IOException e) {
            System.out.println(clientName + " ngắt kết nối.");
        } finally {
            stop();
        }
    }

    private void handleImageTransfer(String fileName) throws IOException {
        long size = in.readLong();
        File dir = new File("server_storage/images");
        if (!dir.exists()) dir.mkdirs();
        
        File file = new File(dir, System.currentTimeMillis() + "_" + fileName);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            byte[] buffer = new byte[4096];
            int read;
            long remaining = size;
            while (remaining > 0 && (read = in.read(buffer, 0, (int)Math.min(buffer.length, remaining))) != -1) {
                fos.write(buffer, 0, read);
                remaining -= read;
            }
        }
        ChatServer.broadcast(clientName + " đã gửi một ảnh: " + fileName, "IMAGE");
    }

    public void sendMessage(String msg) {
        try { out.writeUTF(msg); out.flush(); } catch (Exception e) {}
    }

    public void stop() {
        isRunning = false;
        ChatServer.removeMember(this);
        try { socket.close(); } catch (IOException e) {}
    }

    public String getClientName() { return clientName; }
}