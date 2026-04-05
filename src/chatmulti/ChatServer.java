package chatmulti;

import javax.swing.*;
import javax.swing.border.TitledBorder;
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
        setTitle("Server Frame");
        setSize(600, 500);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Top: Config
        JPanel pnlTop = new JPanel(new GridLayout(1, 2));
        pnlTop.setBorder(new TitledBorder("Server Config"));
        JPanel pnlInfo = new JPanel(new GridLayout(2, 2));
        pnlInfo.add(new JLabel("IP Address:")); pnlInfo.add(new JTextField("192.168.1.6"));
        pnlInfo.add(new JLabel("Port:")); pnlInfo.add(new JTextField(String.valueOf(TCP_PORT)));
        
        JPanel pnlStatus = new JPanel(new BorderLayout());
        pnlStatus.setBackground(Color.CYAN);
        lblStatus = new JLabel("Status: OFFLINE", SwingConstants.CENTER);
        pnlStatus.add(lblStatus, BorderLayout.CENTER);
        pnlTop.add(pnlInfo); pnlTop.add(pnlStatus);

        // Center: Log
        logArea = new JTextArea();
        logArea.setBackground(Color.BLACK);
        logArea.setForeground(Color.GREEN);
        logArea.setEditable(false);

        // Bottom: Buttons
        JPanel pnlBottom = new JPanel();
        btnStart = new JButton("Start Server");
        btnStop = new JButton("Stop Server");
        btnStop.setEnabled(false);
        btnStart.addActionListener(e -> startServer());
        btnStop.addActionListener(e -> stopServer());
        pnlBottom.add(btnStart); pnlBottom.add(btnStop);

        add(pnlTop, BorderLayout.NORTH);
        add(new JScrollPane(logArea), BorderLayout.CENTER);
        add(pnlBottom, BorderLayout.SOUTH);
    }

    private void startServer() {
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(TCP_PORT);
                SwingUtilities.invokeLater(() -> {
                    btnStart.setEnabled(false); btnStop.setEnabled(true);
                    lblStatus.setText("Status: RUNNING..."); logArea.append("SERVER STARTED ON PORT " + TCP_PORT + "\n");
                });
                while (!serverSocket.isClosed()) {
                    Socket s = serverSocket.accept();
                    MemberProcessor mp = new MemberProcessor(s);
                    members.add(mp);
                    new Thread(mp).start();
                }
            } catch (IOException e) { logArea.append("Server stopped.\n"); }
        }).start();
    }

    private void stopServer() {
        try {
            broadcast("SHUTDOWN|Server closed.", "SYSTEM");
            for (MemberProcessor mp : members) mp.stop();
            members.clear();
            serverSocket.close();
            btnStart.setEnabled(true); btnStop.setEnabled(false);
            lblStatus.setText("Status: OFFLINE");
        } catch (Exception e) {}
    }

    public static void broadcast(String msg, String type) {
        String data = type + "|" + msg;
        if (!type.equals("SYSTEM")) history.add(data);
        try (DatagramSocket ds = new DatagramSocket()) {
            byte[] b = data.getBytes("UTF-8");
            ds.send(new DatagramPacket(b, b.length, InetAddress.getByName(MCAST_ADDR), MCAST_PORT));
        } catch (Exception e) {}
    }

    public static void sendHistoryTo(MemberProcessor mp) {
        synchronized (history) { for (String h : history) mp.sendMessage("HISTORY:" + h); }
    }

    public static void updateUserList() {
        StringBuilder sb = new StringBuilder("UPDATE_USERS:");
        for (MemberProcessor m : members) sb.append(m.getClientName()).append(",");
        broadcast(sb.toString(), "SYSTEM");
    }

    public static void removeMember(MemberProcessor mp) { members.remove(mp); }
    public static void main(String[] args) { new ChatServer().setVisible(true); }
}