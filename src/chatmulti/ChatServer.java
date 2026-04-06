package chatmulti;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;

public class ChatServer extends JFrame {
    public static final int TCP_PORT = 8888;
    public static final int DISCOVER_PORT = 8887;
    public static final String MCAST_ADDR = "230.0.0.1";
    public static final int MCAST_PORT = 9999;

    private ServerSocket serverSocket;
    private DatagramSocket discoverSocket;
    private static final Set<MemberProcessor> members = Collections.synchronizedSet(new HashSet<>());
    private static final List<String> history = Collections.synchronizedList(new ArrayList<>());
    private JTextArea logArea;
    private JButton btnStart, btnStop;
    private JLabel lblStatus;
    private JPanel pnlStatusBar;

    public ChatServer() {
        setTitle("Chat Server");
        setSize(640, 520);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        getContentPane().setBackground(UiTheme.WINDOW_BG);
        setLayout(new BorderLayout(0, 12));
        getRootPane().setBorder(new EmptyBorder(12, 14, 14, 14));

        JPanel pnlTop = new JPanel(new GridLayout(1, 2, 12, 0));
        pnlTop.setOpaque(false);

        JPanel pnlInfo = new JPanel(new GridLayout(2, 2, 8, 8));
        pnlInfo.setBackground(UiTheme.CARD);
        TitledBorder tb = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(UiTheme.BORDER, 1), "Cấu hình");
        tb.setTitleFont(UiTheme.uiFont(Font.BOLD, 12));
        tb.setTitleColor(UiTheme.TEXT);
        pnlInfo.setBorder(BorderFactory.createCompoundBorder(tb, new EmptyBorder(10, 12, 12, 12)));

        Font lf = UiTheme.uiFont(Font.PLAIN, 13);
        JLabel l1 = new JLabel("Địa chỉ IP (tham khảo):");
        l1.setFont(lf);
        l1.setForeground(UiTheme.TEXT);
        JTextField tfIp = new JTextField("192.168.1.6");
        tfIp.setFont(lf);
        tfIp.setBorder(BorderFactory.createCompoundBorder(new LineBorder(UiTheme.BORDER, 1), new EmptyBorder(6, 8, 6, 8)));
        JLabel l2 = new JLabel("Cổng TCP:");
        l2.setFont(lf);
        l2.setForeground(UiTheme.TEXT);
        JTextField tfPort = new JTextField(String.valueOf(TCP_PORT));
        tfPort.setFont(lf);
        tfPort.setBorder(BorderFactory.createCompoundBorder(new LineBorder(UiTheme.BORDER, 1), new EmptyBorder(6, 8, 6, 8)));
        pnlInfo.add(l1);
        pnlInfo.add(tfIp);
        pnlInfo.add(l2);
        pnlInfo.add(tfPort);

        pnlStatusBar = new JPanel(new BorderLayout());
        pnlStatusBar.setBackground(UiTheme.OFFLINE_BG);
        pnlStatusBar.setBorder(new LineBorder(UiTheme.BORDER, 1));
        lblStatus = new JLabel("Trạng thái: Đang tắt", SwingConstants.CENTER);
        lblStatus.setFont(UiTheme.uiFont(Font.BOLD, 14));
        lblStatus.setForeground(UiTheme.OFFLINE_TEXT);
        lblStatus.setBorder(new EmptyBorder(16, 12, 16, 12));
        pnlStatusBar.add(lblStatus, BorderLayout.CENTER);

        pnlTop.add(pnlInfo);
        pnlTop.add(pnlStatusBar);

        logArea = new JTextArea();
        logArea.setBackground(UiTheme.CARD);
        logArea.setForeground(UiTheme.TEXT);
        logArea.setCaretColor(UiTheme.TEXT);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        logArea.setEditable(false);
        logArea.setBorder(new EmptyBorder(10, 12, 10, 12));
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(new LineBorder(UiTheme.BORDER, 1));

        JPanel pnlBottom = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        pnlBottom.setOpaque(false);
        btnStart = new JButton("Start Server");
        btnStop = new JButton("Stop Server");
        UiTheme.stylePrimaryButton(btnStart);
        UiTheme.styleSecondaryButton(btnStop);
        btnStop.setEnabled(false);
        btnStart.addActionListener(e -> startServer());
        btnStop.addActionListener(e -> stopServer());
        pnlBottom.add(btnStart);
        pnlBottom.add(btnStop);

        add(pnlTop, BorderLayout.NORTH);
        add(logScroll, BorderLayout.CENTER);
        add(pnlBottom, BorderLayout.SOUTH);
    }

    private void startServer() {
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(TCP_PORT);
                try {
                    discoverSocket = new DatagramSocket(DISCOVER_PORT);
                    discoverSocket.setBroadcast(true);
                    new Thread(this::runDiscoverLoop, "chatmulti-discover").start();
                } catch (IOException ex) {
                    SwingUtilities.invokeLater(() ->
                            logArea.append("WARN: Không mở được UDP discovery port " + DISCOVER_PORT + " — client chỉ kết nối được bằng nhập IP.\n"));
                }
                SwingUtilities.invokeLater(() -> {
                    btnStart.setEnabled(false); btnStop.setEnabled(true);
                    pnlStatusBar.setBackground(UiTheme.SUCCESS_BG);
                    lblStatus.setForeground(UiTheme.SUCCESS_TEXT);
                    lblStatus.setText("Trạng thái: Đang chạy");
                    logArea.append("SERVER STARTED ON PORT " + TCP_PORT + "\n");
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

    private void runDiscoverLoop() {
        DatagramSocket ds = discoverSocket;
        if (ds == null) return;
        byte[] buf = new byte[512];
        while (serverSocket != null && !serverSocket.isClosed()) {
            try {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                ds.receive(p);
                String msg = new String(p.getData(), 0, p.getLength(), "UTF-8").trim();
                if ("CHATMULTI_DISCOVER".equals(msg)) {
                    byte[] out = ("CHATMULTI_HERE|" + TCP_PORT).getBytes("UTF-8");
                    ds.send(new DatagramPacket(out, out.length, p.getAddress(), p.getPort()));
                }
            } catch (SocketException e) {
                break;
            } catch (IOException e) {
                break;
            }
        }
    }

    private void stopServer() {
        try {
            broadcast("SHUTDOWN|Server closed.", "SYSTEM");
            for (MemberProcessor mp : members) mp.stop();
            members.clear();
            if (discoverSocket != null && !discoverSocket.isClosed()) discoverSocket.close();
            discoverSocket = null;
            serverSocket.close();
            btnStart.setEnabled(true); btnStop.setEnabled(false);
            pnlStatusBar.setBackground(UiTheme.OFFLINE_BG);
            lblStatus.setForeground(UiTheme.OFFLINE_TEXT);
            lblStatus.setText("Trạng thái: Đang tắt");
        } catch (Exception e) {}
    }

    public static void broadcast(String msg, String type) {
        String data = type + "|" + msg;
        if (!type.equals("SYSTEM")) history.add(data);
        try (MulticastSocket ms = new MulticastSocket()) {
            try {
                ms.setTimeToLive(32);
            } catch (Exception ignored) { }
            byte[] b = data.getBytes("UTF-8");
            ms.send(new DatagramPacket(b, b.length, InetAddress.getByName(MCAST_ADDR), MCAST_PORT));
        } catch (Exception e) { }
        synchronized (members) {
            for (MemberProcessor mp : new ArrayList<>(members)) {
                mp.sendLive(data);
            }
        }
    }

    public static void broadcastImage(String sender, String fileName, byte[] data) {
        String msg = sender + " đã gửi ảnh: " + fileName;
        String line = "IMAGE|" + msg;
        history.add(line);
        try (MulticastSocket ms = new MulticastSocket()) {
            try {
                ms.setTimeToLive(32);
            } catch (Exception ignored) { }
            byte[] b = line.getBytes(StandardCharsets.UTF_8);
            ms.send(new DatagramPacket(b, b.length, InetAddress.getByName(MCAST_ADDR), MCAST_PORT));
        } catch (Exception e) { }
        synchronized (members) {
            for (MemberProcessor mp : new ArrayList<>(members)) {
                mp.sendImageChunk(sender, fileName, data);
            }
        }
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