package chatmulti.ui;

import chatmulti.Server;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.IOException;

public final class ServerUI extends JFrame {
    private final DefaultListModel<String> onlineModel = new DefaultListModel<>();
    private final DefaultListModel<String> waitingModel = new DefaultListModel<>();
    private final DefaultListModel<String> roomsModel = new DefaultListModel<>();

    private final JTextArea logArea = new JTextArea();
    private final JTextField tfNewRoom = new JTextField(12);
    private final JComboBox<String> cbWaiting = new JComboBox<>();
    private final JComboBox<String> cbRooms = new JComboBox<>();
    private final JComboBox<String> cbUserInRoom = new JComboBox<>();
    private final JButton btnStart = new JButton("Start server");
    private final JButton btnStop = new JButton("Stop server");
    private final JButton btnCreateRoom = new JButton("Tạo phòng");
    private final JButton btnAddToRoom = new JButton("Add vào phòng");
    private final JButton btnDeleteRoom = new JButton("Xóa phòng");
    private final JButton btnRemoveFromRoom = new JButton("Xóa user ra khỏi phòng");
    private final JPanel pnlStatusBar;
    private final JLabel lblStatus;
    private final JLabel lblStatusDot;

    private Server server;

    public ServerUI() {
        super("Chat Server — TCP " + Server.TCP_PORT + " / UDP " + Server.UDP_PORT);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(960, 680);
        getContentPane().setBackground(UiTheme.PANEL);
        getRootPane().setBorder(new EmptyBorder(12, 14, 14, 14));
        setLayout(new BorderLayout(0, 12));

        pnlStatusBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        pnlStatusBar.setOpaque(true);
        pnlStatusBar.setBackground(UiTheme.OFFLINE_BG);
        pnlStatusBar.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(UiTheme.DIVIDER, 1),
                new EmptyBorder(14, 12, 14, 12)));
        lblStatusDot = new JLabel("●");
        lblStatusDot.setFont(UiTheme.uiFont(Font.PLAIN, 18));
        lblStatusDot.setForeground(new Color(0x888888));
        lblStatus = new JLabel("Trạng thái: Đang tắt");
        lblStatus.setFont(UiTheme.uiFont(Font.BOLD, 14));
        lblStatus.setForeground(UiTheme.OFFLINE_TEXT);
        pnlStatusBar.add(lblStatusDot);
        pnlStatusBar.add(lblStatus);

        JPanel lists = new JPanel(new GridLayout(1, 3, 12, 0));
        lists.setOpaque(false);
        lists.add(wrapList("User online", onlineModel, true));
        lists.add(wrapList("User đang chờ", waitingModel, false));
        lists.add(wrapList("Phòng (tên)", roomsModel, false));

        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        logArea.setBackground(UiTheme.LOG_BG);
        logArea.setForeground(UiTheme.LOG_TEXT);
        logArea.setCaretColor(UiTheme.LOG_TEXT);
        logArea.setBorder(new EmptyBorder(12, 14, 12, 14));
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(new LineBorder(UiTheme.DIVIDER, 1));

        JPanel admin = new JPanel(new GridBagLayout());
        admin.setBackground(UiTheme.WINDOW);
        TitledBorder tb = BorderFactory.createTitledBorder(
                new LineBorder(UiTheme.DIVIDER, 1), "Quản lý phòng");
        tb.setTitleFont(UiTheme.uiFont(Font.BOLD, 12));
        tb.setTitleColor(UiTheme.TEXT);
        admin.setBorder(BorderFactory.createCompoundBorder(tb, new EmptyBorder(10, 12, 12, 12)));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(6, 8, 6, 8);
        g.anchor = GridBagConstraints.WEST;
        Font lf = UiTheme.uiFont(Font.PLAIN, 13);

        int row = 0;
        g.gridx = 0;
        g.gridy = row;
        JLabel l1 = new JLabel("Tên phòng mới:");
        l1.setFont(lf);
        l1.setForeground(UiTheme.TEXT);
        admin.add(l1, g);
        g.gridx = 1;
        tfNewRoom.setFont(lf);
        tfNewRoom.setBorder(UiTheme.fieldBorder());
        admin.add(tfNewRoom, g);
        g.gridx = 2;
        UiTheme.stylePrimaryButton(btnCreateRoom);
        admin.add(btnCreateRoom, g);
        g.gridx = 3;
        UiTheme.styleSecondaryButton(btnDeleteRoom);
        admin.add(btnDeleteRoom, g);

        row++;
        g.gridx = 0;
        g.gridy = row;
        JLabel l2 = new JLabel("User chờ:");
        l2.setFont(lf);
        l2.setForeground(UiTheme.TEXT);
        admin.add(l2, g);
        g.gridx = 1;
        styleCombo(cbWaiting);
        admin.add(cbWaiting, g);

        row++;
        g.gridx = 0;
        g.gridy = row;
        JLabel l3 = new JLabel("Phòng:");
        l3.setFont(lf);
        l3.setForeground(UiTheme.TEXT);
        admin.add(l3, g);
        g.gridx = 1;
        styleCombo(cbRooms);
        admin.add(cbRooms, g);
        g.gridx = 2;
        UiTheme.stylePrimaryButton(btnAddToRoom);
        admin.add(btnAddToRoom, g);

        row++;
        g.gridx = 0;
        g.gridy = row;
        JLabel l4 = new JLabel("User trong phòng:");
        l4.setFont(lf);
        l4.setForeground(UiTheme.TEXT);
        admin.add(l4, g);
        g.gridx = 1;
        g.gridwidth = 1;
        styleCombo(cbUserInRoom);
        admin.add(cbUserInRoom, g);
        g.gridx = 2;
        UiTheme.styleSecondaryButton(btnRemoveFromRoom);
        admin.add(btnRemoveFromRoom, g);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        bottom.setOpaque(false);
        UiTheme.stylePrimaryButton(btnStart);
        UiTheme.styleSecondaryButton(btnStop);
        btnStop.setEnabled(false);
        bottom.add(btnStart);
        bottom.add(btnStop);

        JPanel north = new JPanel(new BorderLayout(0, 10));
        north.setOpaque(false);
        north.add(pnlStatusBar, BorderLayout.NORTH);
        north.add(lists, BorderLayout.CENTER);

        JPanel center = new JPanel(new BorderLayout(0, 10));
        center.setOpaque(false);
        center.add(admin, BorderLayout.NORTH);
        center.add(logScroll, BorderLayout.CENTER);

        add(north, BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        cbRooms.addActionListener(e -> refillUsersInRoomCombo());

        btnStart.addActionListener(e -> startServer());
        btnStop.addActionListener(e -> stopServer());
        btnCreateRoom.addActionListener(e -> {
            if (server == null || !server.isRunning()) return;
            String name = tfNewRoom.getText().trim();
            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Nhập tên phòng.", "Thiếu tên", JOptionPane.WARNING_MESSAGE);
                return;
            }
            server.createRoom(name);
            tfNewRoom.setText("");
        });
        btnDeleteRoom.addActionListener(e -> {
            if (server == null || !server.isRunning()) return;
            Object r = cbRooms.getSelectedItem();
            if (r == null || r.toString().trim().isEmpty()) {
                JOptionPane.showMessageDialog(this, "Chọn phòng cần xóa.", "Thiếu phòng", JOptionPane.WARNING_MESSAGE);
                return;
            }
            int ok = JOptionPane.showConfirmDialog(this,
                    "Xóa phòng \"" + r + "\"? Mọi user trong phòng sẽ về danh sách chờ.",
                    "Xác nhận", JOptionPane.OK_CANCEL_OPTION);
            if (ok == JOptionPane.OK_OPTION) server.deleteRoom(r.toString().trim());
        });
        btnAddToRoom.addActionListener(e -> {
            if (server == null || !server.isRunning()) return;
            Object w = cbWaiting.getSelectedItem();
            Object r = cbRooms.getSelectedItem();
            if (w == null || r == null) {
                JOptionPane.showMessageDialog(this, "Chọn user chờ và phòng.", "Thiếu lựa chọn", JOptionPane.WARNING_MESSAGE);
                return;
            }
            String user = w.toString().trim();
            String room = r.toString().trim();
            if (user.isEmpty() || room.isEmpty()) return;
            server.addWaitingUserToRoom(user, room);
        });
        btnRemoveFromRoom.addActionListener(e -> {
            if (server == null || !server.isRunning()) return;
            Object u = cbUserInRoom.getSelectedItem();
            if (u == null || u.toString().trim().isEmpty()) {
                JOptionPane.showMessageDialog(this, "Chọn user trong phòng.", "Thiếu user", JOptionPane.WARNING_MESSAGE);
                return;
            }
            server.removeUserFromRoom(u.toString().trim());
        });

        setLocationRelativeTo(null);
    }

    private static void styleCombo(JComboBox<String> cb) {
        cb.setFont(UiTheme.uiFont(Font.PLAIN, 13));
        cb.setPreferredSize(new Dimension(220, 32));
    }

    private JPanel wrapList(String title, DefaultListModel<String> model, boolean onlineDots) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(UiTheme.WINDOW);
        TitledBorder tb = BorderFactory.createTitledBorder(
                new LineBorder(UiTheme.DIVIDER, 1), title);
        tb.setTitleFont(UiTheme.uiFont(Font.BOLD, 12));
        tb.setTitleColor(UiTheme.TEXT);
        p.setBorder(BorderFactory.createCompoundBorder(tb, new EmptyBorder(8, 8, 8, 8)));
        JList<String> list = new JList<>(model);
        list.setFont(UiTheme.uiFont(Font.PLAIN, 13));
        list.setBackground(UiTheme.WINDOW);
        list.setForeground(UiTheme.TEXT);
        list.setSelectionBackground(UiTheme.LIST_SELECTION);
        if (onlineDots) {
            list.setCellRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                              boolean isSelected, boolean cellHasFocus) {
                    JLabel lb = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                    String t = value == null ? "" : value.toString();
                    lb.setText("<html><font color='#22AA44'>●</font>&nbsp;" + escapeHtml(t) + "</html>");
                    return lb;
                }
            });
        }
        p.add(new JScrollPane(list), BorderLayout.CENTER);
        return p;
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public void appendLog(String line) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(line + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void refillUsersInRoomCombo() {
        cbUserInRoom.removeAllItems();
        if (server == null || !server.isRunning()) return;
        Object r = cbRooms.getSelectedItem();
        if (r == null) return;
        for (String u : server.snapshotUsersInRoom(r.toString())) cbUserInRoom.addItem(u);
    }

    public void refreshListsFromServer() {
        SwingUtilities.invokeLater(() -> {
            onlineModel.clear();
            waitingModel.clear();
            roomsModel.clear();
            cbWaiting.removeAllItems();
            cbRooms.removeAllItems();
            if (server != null && server.isRunning()) {
                for (String u : server.snapshotOnline()) onlineModel.addElement(u);
                for (String u : server.snapshotWaiting()) {
                    waitingModel.addElement(u);
                    cbWaiting.addItem(u);
                }
                for (String r : server.snapshotRoomNames()) {
                    roomsModel.addElement(r);
                    cbRooms.addItem(r);
                }
            }
            refillUsersInRoomCombo();
        });
    }

    private void startServer() {
        if (server != null && server.isRunning()) return;
        server = new Server(this);
        try {
            server.start();
            btnStart.setEnabled(false);
            btnStop.setEnabled(true);
            pnlStatusBar.setBackground(UiTheme.ONLINE_BG);
            lblStatusDot.setForeground(new Color(0x22AA44));
            lblStatus.setForeground(UiTheme.ONLINE_TEXT);
            lblStatus.setText("Trạng thái: Đang chạy");
            refreshListsFromServer();
        } catch (IOException ex) {
            server = null;
            JOptionPane.showMessageDialog(this,
                    "Không mở được cổng (có thể đã bị chiếm):\n" + ex.getMessage(),
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void stopServer() {
        if (server != null) {
            server.stop();
            server = null;
        }
        btnStart.setEnabled(true);
        btnStop.setEnabled(false);
        pnlStatusBar.setBackground(UiTheme.OFFLINE_BG);
        lblStatusDot.setForeground(new Color(0x888888));
        lblStatus.setForeground(UiTheme.OFFLINE_TEXT);
        lblStatus.setText("Trạng thái: Đang tắt");
        refreshListsFromServer();
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        SwingUtilities.invokeLater(() -> new ServerUI().setVisible(true));
    }
}
