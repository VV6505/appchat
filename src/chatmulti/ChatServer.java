package chatmulti;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List;

public class ChatServer extends JFrame {
    public static final int TCP_PORT = 8888;
    public static final String MCAST_ADDR = "230.0.0.1";
    public static final int MCAST_PORT = 9999;

    private ServerSocket serverSocket;
    private static final Set<MemberProcessor> members = Collections.synchronizedSet(new HashSet<>());
    private static final List<String> history = Collections.synchronizedList(new ArrayList<>());
    
    private JTextArea logArea;
    private JButton btnStart, btnStop;
    private JLabel lblStatus;

    public ChatServer() {
        setTitle("Server Control Center");
        setSize(550, 500);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(new Color(15, 15, 25));

        // Header
        JPanel header = new JPanel(new GridLayout(2, 1));
        header.setOpaque(false);
        JLabel title = new JLabel("SERVER CONFIG", SwingConstants.CENTER);
        title.setForeground(new Color(0, 212, 170));
        title.setFont(new Font("Segoe UI", Font.BOLD, 20));
        lblStatus = new JLabel("● OFFLINE", SwingConstants.CENTER);
        lblStatus.setForeground(Color.GRAY);
        header.add(title);
        header.add(lblStatus);

        // Console Log
        logArea = new JTextArea();
        logArea.setBackground(new Color(10, 10, 15));
        logArea.setForeground(new Color(0, 212, 170));
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 12));

        // Buttons
        btnStart = new JButton("START SERVER");
        btnStop = new JButton("STOP SERVER");
        btnStop.setEnabled(false);
        btnStart.addActionListener(e -> startServer());
        btnStop.addActionListener(e -> stopServer());

        JPanel btnPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        btnPanel.setOpaque(false);
        btnPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        btnPanel.add(btnStart); btnPanel.add(btnStop);

        add(header, BorderLayout.NORTH);
        add(new JScrollPane(logArea), BorderLayout.CENTER);
        add(btnPanel, BorderLayout.SOUTH);
        setLocationRelativeTo(null);
    }

    private void startServer() {
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(TCP_PORT);
                SwingUtilities.invokeLater(() -> {
                    btnStart.setEnabled(false); btnStop.setEnabled(true);
                    lblStatus.setText("● RUNNING"); lblStatus.setForeground(new Color(0, 212, 170));
                    logArea.append("[Hệ thống]: Server đã khởi động trên cổng " + TCP_PORT + "\n");
                });
                while (!serverSocket.isClosed()) {
                    Socket s = serverSocket.accept();
                    MemberProcessor mp = new MemberProcessor(s);
                    members.add(mp);
                    new Thread(mp).start();
                }
            } catch (IOException e) { logArea.append("[!] Server đã dừng.\n"); }
        }).start();
    }

    private void stopServer() {
        try {
            broadcast("SHUTDOWN|Server đang đóng...", "SYSTEM");
            synchronized (members) { for (MemberProcessor mp : members) mp.stop(); }
            members.clear();
            if (serverSocket != null) serverSocket.close();
            btnStart.setEnabled(true); btnStop.setEnabled(false);
            lblStatus.setText("● OFFLINE"); lblStatus.setForeground(Color.GRAY);
        } catch (IOException e) { e.printStackTrace(); }
    }

    public static void broadcast(String msg, String type) {
        String data = type + "|" + msg;
        if (!type.equals("SYSTEM")) history.add(data);
        try (DatagramSocket ds = new DatagramSocket()) {
            byte[] buf = data.getBytes("UTF-8");
            ds.send(new DatagramPacket(buf, buf.length, InetAddress.getByName(MCAST_ADDR), MCAST_PORT));
        } catch (Exception e) {}
    }

    public static void sendHistoryTo(MemberProcessor mp) {
        synchronized (history) { for (String h : history) mp.sendMessage("HISTORY:" + h); }
    }

    public static void updateUserList() {
        StringBuilder sb = new StringBuilder("UPDATE_USERS:");
        synchronized (members) { for (MemberProcessor m : members) sb.append(m.getClientName()).append(","); }
        broadcast(sb.toString(), "SYSTEM");
    }

    public static void removeMember(MemberProcessor mp) { members.remove(mp); }
    public static void main(String[] args) { new ChatServer().setVisible(true); }
}