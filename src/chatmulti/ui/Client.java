package chatmulti.ui;

import chatmulti.Server;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class Client extends JFrame {
    // ── Tab indices ───────────────────────────────────────────────────────────
    private static final int TAB_LOGIN = 0;
    private static final int TAB_WAIT  = 1;
    private static final int TAB_CHAT  = 2;
    private static final int TAB_ADMIN = 3;

    private static final String PH_MSG = "Nhập tin nhắn...";

    private static final String[] STICKERS = {"👍", "❤️", "😂", "🔥", "🎉", "👋", "✨", "🙏", "😊", "💯"};
    private static final String[] INLINE_EMOJIS = {
            "😀","😃","😄","😁","😅","🤣","😂","🙂","😉","😊","😍","🥰",
            "😘","😋","😎","🤩","🥳","😇","🤔","😴","👍","👎","👏","🙌",
            "🙏","💪","❤️","💔","🔥","✨","⭐","💯","🎉","🎁","✅","❌"
    };

    private final JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);

    // ── Login fields ──────────────────────────────────────────────────────────
    private final JTextField tfUser    = new JTextField(16);
    private final JTextField tfHost    = new JTextField(18);
    private final JTextField tfTcpPort = new JTextField(6);
    private final JTextField tfUdpPort = new JTextField(6);
    /** Ô mật khẩu — chỉ dùng khi đăng nhập admin */
    private final JPasswordField tfAdminPass = new JPasswordField(12);
    private final JLabel lblAdminPass = new JLabel("Mật khẩu admin:");

    // ── Wait tab ──────────────────────────────────────────────────────────────
    private final JPanel waitPanel  = new JPanel(new BorderLayout());
    private final JLabel lblWaitMain = new JLabel("Bạn hãy đợi một chút nhé.", SwingConstants.CENTER);
    private final JLabel lblWaitSub  = new JLabel("Đang chờ server phân phòng...", SwingConstants.CENTER);
    private int dotPhase;
    private Timer waitTimer;

    // ── Chat tab ──────────────────────────────────────────────────────────────
    private final JPanel chatOuter   = new JPanel(new BorderLayout(0, 0));
    private final JLabel lblRoomTitle = new JLabel("Phòng: —");
    private JPanel chatFeed;
    private JScrollPane chatScroll;
    private final JTextField tfMsg = new JTextField();

    // ── Admin tab ─────────────────────────────────────────────────────────────
    private final DefaultListModel<String> adminOnlineModel  = new DefaultListModel<>();
    private final DefaultListModel<String> adminWaitingModel = new DefaultListModel<>();
    private final DefaultListModel<String> adminRoomsModel   = new DefaultListModel<>();
    private final JTextArea adminLogArea = new JTextArea();
    private final JTextField tfAdminNewRoom = new JTextField(14);
    private final JComboBox<String> cbAdminWaiting = new JComboBox<>();
    private final JComboBox<String> cbAdminRooms   = new JComboBox<>();
    private final JComboBox<String> cbAdminInRoom  = new JComboBox<>();

    // ── Network ───────────────────────────────────────────────────────────────
    private volatile Socket          tcpSocket;
    private volatile DataInputStream tcpIn;
    private volatile DataOutputStream tcpOut;
    private volatile DatagramSocket  udpSocket;
    private volatile Thread          tcpReaderThread;
    private volatile Thread          udpReaderThread;
    private volatile String          username;
    private volatile String          currentRoom;
    private volatile String          serverHost;
    private volatile boolean         closing;
    private volatile boolean         isAdmin;

    // ── Admin TCP connection (separate socket) ────────────────────────────────
    private volatile Socket           adminTcpSocket;
    private volatile DataInputStream  adminTcpIn;
    private volatile DataOutputStream adminTcpOut;
    private volatile Thread           adminReaderThread;

    // ─────────────────────────────────────────────────────────────────────────
    public Client() {
        super("Chat Client");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(820, 620);
        getContentPane().setBackground(UiTheme.PANEL);
        getRootPane().setBorder(new EmptyBorder(0, 12, 12, 12));
        setLayout(new BorderLayout(0, 0));
        add(UiTheme.accentBar(), BorderLayout.NORTH);

        tfTcpPort.setText(String.valueOf(Server.TCP_PORT));
        tfUdpPort.setText(String.valueOf(Server.UDP_PORT));
        tfHost.setToolTipText("IP public VPS hoặc 127.0.0.1 khi test local");
        tfAdminPass.setToolTipText("Chỉ điền nếu bạn là admin");

        addPlaceholder(tfUser, "Tên của bạn");
        addPlaceholder(tfHost, "192.168.x.x");

        tabs.setFont(UiTheme.uiFont(Font.PLAIN, 13));
        tabs.setBackground(UiTheme.SURFACE);
        tabs.addTab("Login",     wrapWithDivider(buildLoginCard()));
        tabs.addTab("Chờ phòng", wrapWithDivider(buildWaitCard()));
        tabs.addTab("Chat",      wrapWithDivider(buildChatCard()));
        tabs.addTab("⚙ Quản trị", wrapWithDivider(buildAdminCard()));

        tabs.setEnabledAt(TAB_WAIT,  false);
        tabs.setEnabledAt(TAB_CHAT,  false);
        tabs.setEnabledAt(TAB_ADMIN, false);

        tabs.addChangeListener(e -> {
            if (tabs.getSelectedIndex() == TAB_CHAT && currentRoom == null)
                tabs.setSelectedIndex(TAB_WAIT);
        });

        add(tabs, BorderLayout.CENTER);

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) {
                closing = true;
                stopNetworking();
            }
        });
        setLocationRelativeTo(null);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private static JPanel wrapWithDivider(JPanel inner) {
        JPanel w = new JPanel(new BorderLayout(0, 0));
        w.setOpaque(false);
        JPanel line = new JPanel();
        line.setPreferredSize(new Dimension(0, 3));
        line.setBackground(UiTheme.PRIMARY);
        w.add(line, BorderLayout.NORTH);
        inner.setOpaque(true);
        w.add(inner, BorderLayout.CENTER);
        return w;
    }

    private static void addPlaceholder(JTextField tf, String ph) {
        tf.setForeground(UiTheme.MUTED);
        tf.setText(ph);
        tf.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                if (ph.equals(tf.getText())) { tf.setText(""); tf.setForeground(UiTheme.TEXT); }
            }
            @Override public void focusLost(FocusEvent e) {
                if (tf.getText().isEmpty()) { tf.setText(ph); tf.setForeground(UiTheme.MUTED); }
            }
        });
    }

    private boolean fieldIsPlaceholder(JTextField tf, String ph) {
        return ph.equals(tf.getText().trim()) ||
               (tf.getForeground().equals(UiTheme.MUTED) && tf.getText().equals(ph));
    }

    private String textOrEmpty(JTextField tf, String ph) {
        if (fieldIsPlaceholder(tf, ph)) return "";
        return tf.getText().trim();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  BUILD LOGIN CARD  — thêm trường password admin
    // ═══════════════════════════════════════════════════════════════════════════
    private JPanel buildLoginCard() {
        JPanel wrap = new JPanel(new GridBagLayout());
        wrap.setBackground(UiTheme.WINDOW);
        wrap.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(UiTheme.BORDER_BLUE, 1),
                new EmptyBorder(28, 32, 28, 32)));

        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(8, 10, 8, 10);
        Font lf = UiTheme.uiFont(Font.PLAIN, 13);

        int y = 0;
        // Username
        g.gridx = 0; g.gridy = y; g.anchor = GridBagConstraints.LINE_END;
        JLabel la = new JLabel("Username:"); la.setFont(lf); la.setForeground(UiTheme.TEXT); wrap.add(la, g);
        g.gridx = 1; g.anchor = GridBagConstraints.LINE_START;
        tfUser.setFont(lf); tfUser.setBorder(UiTheme.fieldBorder()); wrap.add(tfUser, g);

        // IP server
        y++;
        g.gridx = 0; g.gridy = y; g.anchor = GridBagConstraints.LINE_END;
        JLabel lb = new JLabel("IP server:"); lb.setFont(lf); lb.setForeground(UiTheme.TEXT); wrap.add(lb, g);
        g.gridx = 1; g.anchor = GridBagConstraints.LINE_START;
        tfHost.setFont(lf); tfHost.setBorder(UiTheme.fieldBorder()); wrap.add(tfHost, g);

        // TCP Port
        y++;
        g.gridx = 0; g.gridy = y; g.anchor = GridBagConstraints.LINE_END;
        JLabel lc = new JLabel("Cổng TCP:"); lc.setFont(lf); lc.setForeground(UiTheme.TEXT); wrap.add(lc, g);
        g.gridx = 1;
        tfTcpPort.setFont(lf); tfTcpPort.setBorder(UiTheme.fieldBorder()); wrap.add(tfTcpPort, g);

        // UDP Port
        y++;
        g.gridx = 0; g.gridy = y; g.anchor = GridBagConstraints.LINE_END;
        JLabel ld = new JLabel("Cổng UDP:"); ld.setFont(lf); ld.setForeground(UiTheme.TEXT); wrap.add(ld, g);
        g.gridx = 1;
        tfUdpPort.setFont(lf); tfUdpPort.setBorder(UiTheme.fieldBorder()); wrap.add(tfUdpPort, g);

        // ── Divider + admin section ──────────────────────────────────────────
        y++;
        g.gridx = 0; g.gridy = y; g.gridwidth = 2; g.anchor = GridBagConstraints.CENTER;
        JSeparator sep = new JSeparator();
        sep.setForeground(UiTheme.DIVIDER);
        wrap.add(sep, g);
        g.gridwidth = 1;

        y++;
        g.gridx = 0; g.gridy = y; g.anchor = GridBagConstraints.LINE_END;
        lblAdminPass.setFont(UiTheme.uiFont(Font.ITALIC, 12));
        lblAdminPass.setForeground(UiTheme.MUTED);
        wrap.add(lblAdminPass, g);
        g.gridx = 1; g.anchor = GridBagConstraints.LINE_START;
        tfAdminPass.setFont(lf);
        tfAdminPass.setBorder(UiTheme.fieldBorder());
        tfAdminPass.setToolTipText("Để trống nếu là user thường");
        wrap.add(tfAdminPass, g);

        // Hint label
        y++;
        g.gridx = 0; g.gridy = y; g.gridwidth = 2; g.anchor = GridBagConstraints.CENTER;
        JLabel hint = new JLabel("Điền mật khẩu admin để vào tab Quản trị");
        hint.setFont(UiTheme.uiFont(Font.ITALIC, 11));
        hint.setForeground(UiTheme.MUTED);
        wrap.add(hint, g);
        g.gridwidth = 1;

        // Buttons
        y++;
        g.gridx = 0; g.gridy = y; g.gridwidth = 2; g.anchor = GridBagConstraints.CENTER;
        JButton btn = new JButton("Connect");
        UiTheme.stylePrimaryButton(btn);
        btn.addActionListener(e -> doConnect(false));
        JButton btnQuick = new JButton("Quick Connect");
        UiTheme.styleSecondaryButton(btnQuick);
        btnQuick.addActionListener(e -> doConnect(true));
        JPanel pnlButtons = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        pnlButtons.setOpaque(false);
        pnlButtons.add(btn);
        pnlButtons.add(btnQuick);
        wrap.add(pnlButtons, g);

        JPanel holder = new JPanel(new GridBagLayout());
        holder.setBackground(UiTheme.PANEL);
        holder.add(wrap, new GridBagConstraints());
        return holder;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  BUILD WAIT CARD
    // ═══════════════════════════════════════════════════════════════════════════
    private JPanel buildWaitCard() {
        waitPanel.setBackground(UiTheme.WAIT_BG);
        waitPanel.setBorder(new EmptyBorder(32, 24, 32, 24));
        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        JLabel icon = new JLabel("◎", SwingConstants.CENTER);
        icon.setAlignmentX(Component.CENTER_ALIGNMENT);
        icon.setFont(UiTheme.uiFont(Font.PLAIN, 52));
        icon.setForeground(UiTheme.WAIT_ACCENT);
        lblWaitMain.setAlignmentX(Component.CENTER_ALIGNMENT);
        lblWaitMain.setFont(UiTheme.uiFont(Font.BOLD, 17));
        lblWaitMain.setForeground(UiTheme.WAIT_ACCENT);
        lblWaitSub.setAlignmentX(Component.CENTER_ALIGNMENT);
        lblWaitSub.setFont(UiTheme.uiFont(Font.PLAIN, 14));
        lblWaitSub.setForeground(UiTheme.WAIT_ACCENT);
        lblWaitSub.setBorder(new EmptyBorder(8, 0, 0, 0));
        center.add(Box.createVerticalGlue());
        center.add(icon);
        center.add(Box.createRigidArea(new Dimension(0, 16)));
        center.add(lblWaitMain);
        center.add(lblWaitSub);
        center.add(Box.createVerticalGlue());
        waitPanel.add(center, BorderLayout.CENTER);
        JButton btnDisc = new JButton("Rời khỏi phòng");
        UiTheme.styleSecondaryButton(btnDisc);
        btnDisc.addActionListener(e -> disconnectToLogin());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.CENTER));
        south.setOpaque(false);
        south.add(btnDisc);
        waitPanel.add(south, BorderLayout.SOUTH);
        JPanel holder = new JPanel(new BorderLayout());
        holder.setBackground(UiTheme.PANEL);
        holder.add(waitPanel, BorderLayout.CENTER);
        return holder;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  BUILD CHAT CARD
    // ═══════════════════════════════════════════════════════════════════════════
    private JPanel buildChatCard() {
        chatOuter.setBackground(UiTheme.PANEL);
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(true);
        header.setBackground(UiTheme.SURFACE);
        header.setBorder(new EmptyBorder(10, 14, 10, 14));
        lblRoomTitle.setFont(UiTheme.uiFont(Font.BOLD, 15));
        lblRoomTitle.setForeground(UiTheme.TEXT);
        header.add(lblRoomTitle, BorderLayout.WEST);
        JButton btnDisc = new JButton("Rời khỏi phòng");
        UiTheme.styleSecondaryButton(btnDisc);
        btnDisc.addActionListener(e -> disconnectToLogin());
        header.add(btnDisc, BorderLayout.EAST);
        chatFeed = new JPanel();
        chatFeed.setLayout(new BoxLayout(chatFeed, BoxLayout.Y_AXIS));
        chatFeed.setBackground(UiTheme.WINDOW);
        chatScroll = new JScrollPane(chatFeed);
        chatScroll.setBorder(new LineBorder(UiTheme.BORDER_BLUE, 1));
        chatScroll.getVerticalScrollBar().setUnitIncrement(16);
        JPanel inputBar = new JPanel(new BorderLayout(8, 0));
        inputBar.setOpaque(true);
        inputBar.setBackground(UiTheme.PANEL);
        inputBar.setBorder(new EmptyBorder(10, 0, 0, 0));
        tfMsg.setFont(UiTheme.uiFont(Font.PLAIN, 14));
        tfMsg.setOpaque(true);
        tfMsg.setBackground(UiTheme.WINDOW);
        tfMsg.setBorder(new EmptyBorder(6, 10, 6, 10));
        addPlaceholder(tfMsg, PH_MSG);
        JButton btnEmoji = new JButton("😀");
        UiTheme.styleChatMiniButton(btnEmoji);
        btnEmoji.setToolTipText("Chèn emoji");
        btnEmoji.addActionListener(e -> showInlineEmojiPopup(btnEmoji));
        JButton btnMore = new JButton("⋯");
        UiTheme.styleChatMiniButton(btnMore);
        btnMore.setFont(UiTheme.uiFont(Font.BOLD, 16));
        JPopupMenu attachMenu = new JPopupMenu();
        JMenuItem miFile = new JMenuItem("📎  Tệp");
        miFile.setFont(UiTheme.uiFont(Font.PLAIN, 13));
        miFile.addActionListener(ev -> pickAndSendFile());
        JMenuItem miSticker = new JMenuItem("🏷  Nhãn dán");
        miSticker.setFont(UiTheme.uiFont(Font.PLAIN, 13));
        miSticker.addActionListener(ev -> showStickerPicker());
        attachMenu.add(miFile);
        attachMenu.add(miSticker);
        btnMore.addActionListener(e -> attachMenu.show(btnMore, 0, btnMore.getHeight()));
        JButton send = new JButton("Gửi");
        UiTheme.stylePrimaryButton(send);
        send.addActionListener(e -> sendTextUdp());
        JPanel east = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        east.setOpaque(false);
        east.add(btnEmoji);
        east.add(btnMore);
        east.add(send);
        JPanel composeBar = new JPanel(new BorderLayout(8, 0));
        composeBar.setOpaque(true);
        composeBar.setBackground(UiTheme.WINDOW);
        composeBar.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(UiTheme.BORDER_BLUE, 1, true),
                new EmptyBorder(6, 8, 6, 8)));
        composeBar.add(tfMsg, BorderLayout.CENTER);
        composeBar.add(east, BorderLayout.EAST);
        inputBar.add(composeBar, BorderLayout.CENTER);
        chatOuter.add(header, BorderLayout.NORTH);
        chatOuter.add(chatScroll, BorderLayout.CENTER);
        chatOuter.add(inputBar, BorderLayout.SOUTH);
        tfMsg.addActionListener(e -> sendTextUdp());
        JPanel holder = new JPanel(new BorderLayout());
        holder.setBackground(UiTheme.PANEL);
        holder.add(chatOuter, BorderLayout.CENTER);
        return holder;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  BUILD ADMIN CARD
    // ═══════════════════════════════════════════════════════════════════════════
    private JPanel buildAdminCard() {
        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBackground(UiTheme.PANEL);
        root.setBorder(new EmptyBorder(12, 14, 14, 14));

        // ── Top: 3 danh sách ─────────────────────────────────────────────────
        JPanel lists = new JPanel(new GridLayout(1, 3, 10, 0));
        lists.setOpaque(false);
        lists.add(adminWrapList("User online", adminOnlineModel, true));
        lists.add(adminWrapList("User đang chờ", adminWaitingModel, false));
        lists.add(adminWrapList("Danh sách phòng", adminRoomsModel, false));

        // ── Middle: bảng điều khiển ───────────────────────────────────────────
        JPanel ctrl = new JPanel(new GridBagLayout());
        ctrl.setBackground(UiTheme.WINDOW);
        TitledBorder tb = BorderFactory.createTitledBorder(
                new LineBorder(UiTheme.DIVIDER, 1), "Quản lý phòng");
        tb.setTitleFont(UiTheme.uiFont(Font.BOLD, 12));
        tb.setTitleColor(UiTheme.TEXT);
        ctrl.setBorder(BorderFactory.createCompoundBorder(tb, new EmptyBorder(10, 12, 12, 12)));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(5, 8, 5, 8);
        g.anchor = GridBagConstraints.WEST;
        Font lf = UiTheme.uiFont(Font.PLAIN, 13);

        int row = 0;

        // Row 0: tạo phòng
        g.gridx = 0; g.gridy = row;
        JLabel l1 = new JLabel("Tên phòng mới:"); l1.setFont(lf); l1.setForeground(UiTheme.TEXT); ctrl.add(l1, g);
        g.gridx = 1;
        tfAdminNewRoom.setFont(lf); tfAdminNewRoom.setBorder(UiTheme.fieldBorder()); ctrl.add(tfAdminNewRoom, g);
        g.gridx = 2;
        JButton btnCreate = new JButton("Tạo phòng");
        UiTheme.stylePrimaryButton(btnCreate);
        btnCreate.addActionListener(e -> {
            String r = tfAdminNewRoom.getText().trim();
            if (r.isEmpty()) return;
            adminSendCmd("CREATE_ROOM|" + r);
            tfAdminNewRoom.setText("");
        });
        ctrl.add(btnCreate, g);
        g.gridx = 3;
        JButton btnDelete = new JButton("Xóa phòng");
        UiTheme.styleSecondaryButton(btnDelete);
        btnDelete.addActionListener(e -> {
            Object r = cbAdminRooms.getSelectedItem();
            if (r == null || r.toString().isBlank()) return;
            int ok = JOptionPane.showConfirmDialog(this,
                    "Xóa phòng \"" + r + "\"? Mọi user sẽ về waiting.",
                    "Xác nhận", JOptionPane.OK_CANCEL_OPTION);
            if (ok == JOptionPane.OK_OPTION) adminSendCmd("DELETE_ROOM|" + r.toString().trim());
        });
        ctrl.add(btnDelete, g);

        // Row 1: add user vào phòng
        row++;
        g.gridx = 0; g.gridy = row;
        JLabel l2 = new JLabel("User chờ:"); l2.setFont(lf); l2.setForeground(UiTheme.TEXT); ctrl.add(l2, g);
        g.gridx = 1;
        styleAdminCombo(cbAdminWaiting); ctrl.add(cbAdminWaiting, g);

        row++;
        g.gridx = 0; g.gridy = row;
        JLabel l3 = new JLabel("Phòng:"); l3.setFont(lf); l3.setForeground(UiTheme.TEXT); ctrl.add(l3, g);
        g.gridx = 1;
        cbAdminRooms.addActionListener(e -> refillAdminInRoomCombo());
        styleAdminCombo(cbAdminRooms); ctrl.add(cbAdminRooms, g);
        g.gridx = 2;
        JButton btnAdd = new JButton("Add vào phòng");
        UiTheme.stylePrimaryButton(btnAdd);
        btnAdd.addActionListener(e -> {
            Object w = cbAdminWaiting.getSelectedItem();
            Object r = cbAdminRooms.getSelectedItem();
            if (w == null || r == null) return;
            adminSendCmd("ADD_USER|" + w.toString().trim() + "|" + r.toString().trim());
        });
        ctrl.add(btnAdd, g);

        // Row 3: kick user
        row++;
        g.gridx = 0; g.gridy = row;
        JLabel l4 = new JLabel("User trong phòng:"); l4.setFont(lf); l4.setForeground(UiTheme.TEXT); ctrl.add(l4, g);
        g.gridx = 1;
        styleAdminCombo(cbAdminInRoom); ctrl.add(cbAdminInRoom, g);
        g.gridx = 2;
        JButton btnKick = new JButton("Kick về waiting");
        UiTheme.styleSecondaryButton(btnKick);
        btnKick.addActionListener(e -> {
            Object u = cbAdminInRoom.getSelectedItem();
            if (u == null || u.toString().isBlank()) return;
            adminSendCmd("KICK_USER|" + u.toString().trim());
        });
        ctrl.add(btnKick, g);

        // Row 4: refresh
        row++;
        g.gridx = 0; g.gridy = row; g.gridwidth = 4; g.anchor = GridBagConstraints.EAST;
        JButton btnRefresh = new JButton("🔄 Làm mới");
        UiTheme.styleSecondaryButton(btnRefresh);
        btnRefresh.addActionListener(e -> adminSendCmd("REQUEST_SNAPSHOT"));
        ctrl.add(btnRefresh, g);
        g.gridwidth = 1; g.anchor = GridBagConstraints.WEST;

        // ── Bottom: admin log ─────────────────────────────────────────────────
        adminLogArea.setEditable(false);
        adminLogArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        adminLogArea.setBackground(UiTheme.LOG_BG);
        adminLogArea.setForeground(UiTheme.LOG_TEXT);
        adminLogArea.setBorder(new EmptyBorder(8, 10, 8, 10));
        JScrollPane logScroll = new JScrollPane(adminLogArea);
        logScroll.setBorder(new LineBorder(UiTheme.DIVIDER, 1));
        logScroll.setPreferredSize(new Dimension(0, 140));

        JPanel topHalf = new JPanel(new BorderLayout(0, 10));
        topHalf.setOpaque(false);
        topHalf.add(lists, BorderLayout.NORTH);
        topHalf.add(ctrl, BorderLayout.CENTER);

        root.add(topHalf, BorderLayout.CENTER);
        root.add(logScroll, BorderLayout.SOUTH);

        return root;
    }

    private static void styleAdminCombo(JComboBox<String> cb) {
        cb.setFont(UiTheme.uiFont(Font.PLAIN, 13));
        cb.setPreferredSize(new Dimension(200, 30));
    }

    private JPanel adminWrapList(String title, DefaultListModel<String> model, boolean dots) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(UiTheme.WINDOW);
        TitledBorder tb = BorderFactory.createTitledBorder(new LineBorder(UiTheme.DIVIDER, 1), title);
        tb.setTitleFont(UiTheme.uiFont(Font.BOLD, 11));
        tb.setTitleColor(UiTheme.TEXT);
        p.setBorder(BorderFactory.createCompoundBorder(tb, new EmptyBorder(4, 6, 6, 6)));
        JList<String> list = new JList<>(model);
        list.setFont(UiTheme.uiFont(Font.PLAIN, 12));
        list.setBackground(UiTheme.WINDOW);
        list.setForeground(UiTheme.TEXT);
        list.setFixedCellHeight(22);
        if (dots) {
            list.setCellRenderer(new DefaultListCellRenderer() {
                @Override public Component getListCellRendererComponent(JList<?> l, Object v,
                        int i, boolean sel, boolean foc) {
                    JLabel lb = (JLabel) super.getListCellRendererComponent(l, v, i, sel, foc);
                    lb.setText("<html><font color='#22AA44'>●</font>&nbsp;" + v + "</html>");
                    return lb;
                }
            });
        }
        JScrollPane sp = new JScrollPane(list);
        sp.setPreferredSize(new Dimension(0, 130));
        p.add(sp, BorderLayout.CENTER);
        return p;
    }

    private void refillAdminInRoomCombo() {
        cbAdminInRoom.removeAllItems();
        Object r = cbAdminRooms.getSelectedItem();
        if (r == null) return;
        // Lấy từ model
        String roomName = r.toString();
        // Sẽ được cập nhật khi nhận SNAPSHOT
    }

    private void adminAppendLog(String line) {
        SwingUtilities.invokeLater(() -> {
            adminLogArea.append(line + "\n");
            adminLogArea.setCaretPosition(adminLogArea.getDocument().getLength());
        });
    }

    // ── Gửi lệnh admin ────────────────────────────────────────────────────────
    private void adminSendCmd(String cmd) {
        DataOutputStream out = adminTcpOut;
        if (out == null) return;
        try {
            synchronized (out) {
                out.writeUTF("ADMIN_CMD|" + cmd);
                out.flush();
            }
        } catch (IOException ex) {
            adminAppendLog("[Lỗi] Không gửi được lệnh: " + ex.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  CONNECT LOGIC
    // ═══════════════════════════════════════════════════════════════════════════
    private void doConnect(boolean isQuick) {
        String u = textOrEmpty(tfUser, "Tên của bạn");
        if (u.isEmpty() || u.contains("|")) {
            JOptionPane.showMessageDialog(this, "Username không hợp lệ.", "Lỗi", JOptionPane.WARNING_MESSAGE);
            return;
        }

        final String host;
        final int tcpPort;
        final int udpPort;

        if (isQuick) {
            host    = "192.168.2.18";
            tcpPort = Server.TCP_PORT;
            udpPort = Server.UDP_PORT;
        } else {
            String hostRaw = textOrEmpty(tfHost, "192.168.x.x");
            host = hostRaw.isEmpty() ? "192.168.2.18" : hostRaw;
            try {
                tcpPort = Integer.parseInt(tfTcpPort.getText().trim());
                udpPort = Integer.parseInt(tfUdpPort.getText().trim());
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Port không hợp lệ.", "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }

        // Kiểm tra mật khẩu admin
        String adminPass = new String(tfAdminPass.getPassword()).trim();
        boolean tryAdmin = !adminPass.isEmpty();

        stopNetworking();
        username   = u;
        serverHost = host;
        closing    = false;

        new Thread(() -> {
            try {
                // ── Kết nối TCP chính (user thường) ─────────────────────────
                Socket s = new Socket();
                s.connect(new InetSocketAddress(host, tcpPort), 15000);
                DataOutputStream out = new DataOutputStream(s.getOutputStream());
                DataInputStream in   = new DataInputStream(s.getInputStream());

                if (tryAdmin) {
                    // Gửi handshake admin
                    out.writeUTF("ADMIN_LOGIN|" + adminPass);
                    out.flush();
                    String resp = in.readUTF().trim();
                    if (resp.startsWith("ADMIN_ERROR|")) {
                        String err = resp.substring("ADMIN_ERROR|".length());
                        s.close();
                        SwingUtilities.invokeLater(() ->
                            JOptionPane.showMessageDialog(Client.this,
                                "Sai mật khẩu admin: " + err, "Lỗi", JOptionPane.ERROR_MESSAGE));
                        stopNetworking();
                        return;
                    }
                    // Admin đăng nhập thành công — KHÔNG cần kết nối user thường
                    adminTcpSocket = s;
                    adminTcpIn     = in;
                    adminTcpOut    = out;
                    isAdmin        = true;
                    SwingUtilities.invokeLater(() -> {
                        setTitle("Chat Client — [ADMIN]");
                        tabs.setEnabledAt(TAB_LOGIN, false);
                        tabs.setEnabledAt(TAB_WAIT,  false);
                        tabs.setEnabledAt(TAB_CHAT,  false);
                        tabs.setEnabledAt(TAB_ADMIN, true);
                        tabs.setSelectedIndex(TAB_ADMIN);
                        adminAppendLog("✅ Đã kết nối admin vào " + host + ":" + tcpPort);
                    });
                    // Start admin reader
                    adminReaderThread = new Thread(this::adminReadLoop, "chatmulti-admin-reader");
                    adminReaderThread.setDaemon(true);
                    adminReaderThread.start();

                } else {
                    // User thường
                    out.writeUTF("CONNECT|" + u);
                    out.flush();
                    String line = in.readUTF().trim();
                    if (line.startsWith("ERROR|")) {
                        String err = line.length() > 6 ? line.substring(6) : "Lỗi";
                        s.close();
                        SwingUtilities.invokeLater(() ->
                            JOptionPane.showMessageDialog(Client.this, err,
                                "Không kết nối được", JOptionPane.ERROR_MESSAGE));
                        stopNetworking();
                        return;
                    }
                    if (!"OK_WAITING".equals(line)) {
                        s.close();
                        SwingUtilities.invokeLater(() ->
                            JOptionPane.showMessageDialog(Client.this,
                                "Phản hồi không hợp lệ: " + line, "Lỗi", JOptionPane.ERROR_MESSAGE));
                        return;
                    }
                    DatagramSocket ds = new DatagramSocket();
                    byte[] reg = ("REGISTER|" + u).getBytes(StandardCharsets.UTF_8);
                    ds.send(new DatagramPacket(reg, reg.length, InetAddress.getByName(host), udpPort));

                    tcpSocket = s;
                    tcpIn     = in;
                    tcpOut    = out;
                    udpSocket = ds;
                    isAdmin   = false;

                    SwingUtilities.invokeLater(() -> {
                        setTitle("Chat Client — " + u);
                        tabs.setEnabledAt(TAB_LOGIN, false);
                        tabs.setEnabledAt(TAB_WAIT,  true);
                        tabs.setEnabledAt(TAB_CHAT,  false);
                        tabs.setEnabledAt(TAB_ADMIN, false);
                        tabs.setSelectedIndex(TAB_WAIT);
                        showWaitUi();
                    });

                    tcpReaderThread = new Thread(this::tcpReadLoop, "chatmulti-client-tcp");
                    tcpReaderThread.setDaemon(true);
                    tcpReaderThread.start();
                }

            } catch (Exception ex) {
                String msg = (ex instanceof ConnectException || ex instanceof SocketTimeoutException)
                        ? "Kết nối thất bại: Server chưa chạy hoặc sai IP"
                        : "Lỗi kết nối: " + (ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
                SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(Client.this, msg, "Lỗi", JOptionPane.ERROR_MESSAGE));
                stopNetworking();
            }
        }, "chatmulti-connect").start();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  ADMIN READ LOOP — nhận events từ server
    // ═══════════════════════════════════════════════════════════════════════════
    private void adminReadLoop() {
        try {
            DataInputStream in = adminTcpIn;
            if (in == null) return;
            while (!closing) {
                String line;
                try {
                    line = in.readUTF().trim();
                } catch (EOFException | java.net.SocketException e) {
                    break;
                }
                processAdminEvent(line);
            }
        } catch (IOException ignored) {
        } finally {
            if (!closing) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this,
                            "Mất kết nối admin.", "Ngắt kết nối", JOptionPane.WARNING_MESSAGE);
                    disconnectToLogin();
                });
            }
        }
    }

    private void processAdminEvent(String event) {
        if (event.startsWith("SNAPSHOT|")) {
            parseAndApplySnapshot(event);
        } else if (event.startsWith("USER_CONNECTED|")) {
            String u = event.substring("USER_CONNECTED|".length());
            SwingUtilities.invokeLater(() -> adminAppendLog("🔵 Kết nối: " + u));
        } else if (event.startsWith("USER_DISCONNECTED|")) {
            String u = event.substring("USER_DISCONNECTED|".length());
            SwingUtilities.invokeLater(() -> adminAppendLog("⚫ Rời: " + u));
        } else if (event.startsWith("USER_ROOM_CHANGED|")) {
            String rest = event.substring("USER_ROOM_CHANGED|".length());
            SwingUtilities.invokeLater(() -> adminAppendLog("↔ Chuyển phòng: " + rest.replace("|", " → ")));
        } else if (event.startsWith("ROOM_CREATED|")) {
            String r = event.substring("ROOM_CREATED|".length());
            SwingUtilities.invokeLater(() -> adminAppendLog("➕ Phòng mới: " + r));
        } else if (event.startsWith("ROOM_DELETED|")) {
            String r = event.substring("ROOM_DELETED|".length());
            SwingUtilities.invokeLater(() -> adminAppendLog("🗑 Xóa phòng: " + r));
        } else if (event.startsWith("ADMIN_ACK|")) {
            String msg = event.substring("ADMIN_ACK|".length());
            SwingUtilities.invokeLater(() -> adminAppendLog("✅ " + msg));
        } else if (event.startsWith("ADMIN_ERR|")) {
            String msg = event.substring("ADMIN_ERR|".length());
            SwingUtilities.invokeLater(() -> adminAppendLog("❌ " + msg));
        }
        // Sau mỗi event quan trọng, xin snapshot mới
        if (!event.startsWith("ADMIN_ACK") && !event.startsWith("ADMIN_ERR")) {
            adminSendCmd("REQUEST_SNAPSHOT");
        }
    }

    /**
     * SNAPSHOT|ONLINE:u1,u2,...|WAITING:u3,...|ROOMS:r1=u1;u2;,r2=u3;,...
     */
    private void parseAndApplySnapshot(String snap) {
        // Tách các section
        String[] sections = snap.split("\\|");
        java.util.List<String> online  = new java.util.ArrayList<>();
        java.util.List<String> waiting = new java.util.ArrayList<>();
        java.util.Map<String, java.util.List<String>> rooms = new java.util.LinkedHashMap<>();

        for (String sec : sections) {
            if (sec.startsWith("ONLINE:")) {
                for (String u : sec.substring(7).split(","))
                    if (!u.isBlank()) online.add(u.trim());
            } else if (sec.startsWith("WAITING:")) {
                for (String u : sec.substring(8).split(","))
                    if (!u.isBlank()) waiting.add(u.trim());
            } else if (sec.startsWith("ROOMS:")) {
                for (String entry : sec.substring(6).split(",")) {
                    if (entry.isBlank()) continue;
                    int eq = entry.indexOf('=');
                    if (eq < 0) { rooms.put(entry.trim(), new java.util.ArrayList<>()); continue; }
                    String rName = entry.substring(0, eq).trim();
                    java.util.List<String> members = new java.util.ArrayList<>();
                    for (String m : entry.substring(eq + 1).split(";"))
                        if (!m.isBlank()) members.add(m.trim());
                    rooms.put(rName, members);
                }
            }
        }

        // Snapshot của selected room để tái chọn
        final Object selRoom = cbAdminRooms.getSelectedItem();

        final java.util.List<String> fOnline  = online;
        final java.util.List<String> fWaiting = waiting;
        final java.util.Map<String, java.util.List<String>> fRooms = rooms;

        SwingUtilities.invokeLater(() -> {
            adminOnlineModel.clear();
            adminWaitingModel.clear();
            adminRoomsModel.clear();
            cbAdminWaiting.removeAllItems();
            cbAdminRooms.removeAllItems();

            for (String u : fOnline)  adminOnlineModel.addElement(u);
            for (String u : fWaiting) { adminWaitingModel.addElement(u); cbAdminWaiting.addItem(u); }
            for (String r : fRooms.keySet()) { adminRoomsModel.addElement(r); cbAdminRooms.addItem(r); }

            // Giữ lại lựa chọn phòng cũ nếu vẫn còn
            if (selRoom != null) cbAdminRooms.setSelectedItem(selRoom);

            // Cập nhật combo user trong phòng
            cbAdminInRoom.removeAllItems();
            Object cur = cbAdminRooms.getSelectedItem();
            if (cur != null) {
                java.util.List<String> members = fRooms.get(cur.toString());
                if (members != null) for (String m : members) cbAdminInRoom.addItem(m);
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  WAIT / CHAT UI helpers
    // ═══════════════════════════════════════════════════════════════════════════
    private void showWaitUi() {
        dotPhase = 0;
        if (waitTimer != null) waitTimer.stop();
        lblWaitSub.setText("Đang chờ server phân phòng...");
        waitTimer = new Timer(450, e -> {
            dotPhase = (dotPhase + 1) % 4;
            String dots = dotPhase == 0 ? "" : dotPhase == 1 ? "." : dotPhase == 2 ? ".." : "...";
            lblWaitMain.setText("Bạn hãy đợi một chút nhé" + dots);
        });
        waitTimer.start();
    }

    // ── TCP read loop (user thường) ───────────────────────────────────────────
    private void tcpReadLoop() {
        try {
            DataInputStream in = tcpIn;
            if (in == null) return;
            while (!closing) {
                String line = in.readUTF().trim();
                if (line.startsWith("JOINED_ROOM|") && line.length() > "JOINED_ROOM|".length()) {
                    String room = line.substring("JOINED_ROOM|".length()).trim();
                    currentRoom = room;
                    SwingUtilities.invokeLater(() -> switchToChat(room));
                } else if ("BACK_TO_WAITING".equals(line)) {
                    currentRoom = null;
                    SwingUtilities.invokeLater(this::backToWaitingUi);
                } else if (line.startsWith("SYSTEM|") && line.length() > "SYSTEM|".length()) {
                    String sys = line.substring("SYSTEM|".length());
                    SwingUtilities.invokeLater(() -> { if (currentRoom != null) addSystemBubble(sys); });
                } else if ("FILE_RECV".equals(line)) {
                    String from = in.readUTF();
                    String fn   = in.readUTF();
                    int len     = in.readInt();
                    if (len < 0 || len > Server.MAX_FILE_BYTES) break;
                    byte[] data = new byte[len];
                    in.readFully(data);
                    SwingUtilities.invokeLater(() -> addFileBubble(from, fn, data, false));
                }
            }
        } catch (EOFException ignored) {
        } catch (IOException ignored) {
        } finally {
            if (!closing) SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(Client.this, "Đã rời khỏi nhóm chat.",
                        "Mất kết nối", JOptionPane.WARNING_MESSAGE);
                disconnectToLogin();
            });
        }
    }

    private void switchToChat(String room) {
        if (waitTimer != null) { waitTimer.stop(); waitTimer = null; }
        lblRoomTitle.setText("Phòng: " + room);
        clearChatFeed();
        tabs.setEnabledAt(TAB_CHAT, true);
        tabs.setSelectedIndex(TAB_CHAT);
        if (udpReaderThread == null || !udpReaderThread.isAlive()) {
            udpReaderThread = new Thread(this::udpReadLoop, "chatmulti-client-udp");
            udpReaderThread.setDaemon(true);
            udpReaderThread.start();
        }
    }

    private void backToWaitingUi() {
        if (waitTimer != null) waitTimer.stop();
        tabs.setEnabledAt(TAB_CHAT, false);
        tabs.setSelectedIndex(TAB_WAIT);
        clearChatFeed();
        showWaitUi();
    }

    // ── UDP read loop ─────────────────────────────────────────────────────────
    private void udpReadLoop() {
        byte[] buf = new byte[65507];
        while (!closing) {
            DatagramSocket ds = udpSocket;
            if (ds == null || ds.isClosed()) break;
            DatagramPacket p = new DatagramPacket(buf, buf.length);
            try { ds.receive(p); } catch (IOException e) { break; }
            String msg = new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8).trim();
            if (!msg.startsWith("ROOM_MSG|")) continue;
            String rest  = msg.substring("ROOM_MSG|".length());
            String[] parts = rest.split("\\|", 3);
            if (parts.length < 3) continue;
            String room    = parts[0];
            String user    = parts[1];
            String payload = parts[2];
            String cr      = currentRoom;
            if (cr == null || !cr.equals(room)) continue;
            boolean isMe = user.equals(username);
            if (payload.startsWith("STICKER|")) {
                String em = payload.substring("STICKER|".length());
                SwingUtilities.invokeLater(() -> addStickerBubble(user, em, isMe));
            } else {
                SwingUtilities.invokeLater(() -> addTextBubble(user, payload, isMe));
            }
        }
    }

    // ── Send helpers ──────────────────────────────────────────────────────────
    private void sendTextUdp() {
        String text = textOrEmpty(tfMsg, PH_MSG);
        if (text.isEmpty()) return;
        sendRoomUdpPayload(text);
        addTextBubble(username, text, true);
        tfMsg.setText(""); tfMsg.setForeground(UiTheme.TEXT);
    }

    private void sendStickerUdp(String emoji) {
        sendRoomUdpPayload("STICKER|" + emoji);
        addStickerBubble(username, emoji, true);
    }

    private void sendRoomUdpPayload(String payload) {
        String room = currentRoom, u = username, host = serverHost;
        if (room == null || u == null || host == null) return;
        DatagramSocket ds = udpSocket;
        if (ds == null) return;
        int udpPort;
        try { udpPort = Integer.parseInt(tfUdpPort.getText().trim()); }
        catch (NumberFormatException e) { return; }
        String packet = "ROOM_MSG|" + room + "|" + u + "|" + payload;
        byte[] data = packet.getBytes(StandardCharsets.UTF_8);
        if (data.length > 65507) {
            JOptionPane.showMessageDialog(this, "Nội dung quá dài.", "Lỗi", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try { ds.send(new DatagramPacket(data, data.length, InetAddress.getByName(host), udpPort)); }
        catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Gửi UDP thất bại: " + ex.getMessage(),
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void pickAndSendFile() {
        if (currentRoom == null || tcpOut == null) return;
        JFileChooser jfc = new JFileChooser();
        if (jfc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        java.io.File f = jfc.getSelectedFile();
        new Thread(() -> {
            try {
                byte[] buf = Files.readAllBytes(f.toPath());
                if (buf.length > Server.MAX_FILE_BYTES) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                            "File tối đa 5 MB.", "Lỗi", JOptionPane.WARNING_MESSAGE));
                    return;
                }
                DataOutputStream out = tcpOut;
                synchronized (out) {
                    out.writeUTF("FILE");
                    out.writeUTF(f.getName());
                    out.writeLong(buf.length);
                    out.write(buf);
                    out.flush();
                }
                SwingUtilities.invokeLater(() -> addFileBubble(username, f.getName(), buf, true));
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                        "Gửi file lỗi: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE));
            }
        }).start();
    }

    // ── Bubble helpers ────────────────────────────────────────────────────────
    private void addTextBubble(String user, String text, boolean isMe) {
        JLabel l = bubbleLabel(escapeHtml(text), isMe);
        addBubbleRow(l, isMe);
        scrollChatBottom();
    }

    private void addStickerBubble(String user, String emoji, boolean isMe) {
        JLabel l = new JLabel(emoji, SwingConstants.CENTER);
        l.setFont(l.getFont().deriveFont(36f));
        l.setOpaque(true);
        l.setBackground(isMe ? UiTheme.BUBBLE_ME : UiTheme.BUBBLE_OTHER);
        l.setForeground(isMe ? UiTheme.BUBBLE_ME_TEXT : UiTheme.BUBBLE_OTHER_TEXT);
        l.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(isMe ? UiTheme.PRIMARY_DARK : UiTheme.BORDER_BLUE, 1, true),
                new EmptyBorder(12, 18, 12, 18)));
        addBubbleRow(l, isMe);
        scrollChatBottom();
    }

    private void addSystemBubble(String text) {
        JLabel l = new JLabel("<html><div style='text-align:center'>" + escapeHtml(text) + "</div></html>");
        l.setOpaque(true);
        l.setBackground(UiTheme.BUBBLE_SYSTEM_BG);
        l.setForeground(UiTheme.BUBBLE_SYSTEM_TEXT);
        l.setFont(UiTheme.uiFont(Font.PLAIN, 12));
        l.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(UiTheme.DIVIDER, 1, true),
                new EmptyBorder(6, 14, 6, 14)));
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 6));
        row.setOpaque(false);
        row.add(l);
        chatFeed.add(row);
        chatFeed.add(Box.createVerticalStrut(4));
        chatFeed.revalidate();
        scrollChatBottom();
    }

    private void addFileBubble(String user, String fileName, byte[] data, boolean isMe) {
        JPanel box = new JPanel(new BorderLayout(6, 6));
        box.setOpaque(true);
        box.setBackground(isMe ? UiTheme.BUBBLE_ME : UiTheme.BUBBLE_OTHER);
        box.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(isMe ? UiTheme.PRIMARY_DARK : UiTheme.BORDER_BLUE, 1, true),
                new EmptyBorder(10, 12, 10, 12)));
        JLabel cap = new JLabel(escapeHtml(user + " · " + fileName));
        cap.setFont(UiTheme.uiFont(Font.PLAIN, 12));
        cap.setForeground(isMe ? UiTheme.BUBBLE_ME_TEXT : UiTheme.BUBBLE_OTHER_TEXT);
        box.add(cap, BorderLayout.NORTH);
        try {
            java.awt.image.BufferedImage bi = ImageIO.read(new ByteArrayInputStream(data));
            if (bi != null) {
                int w = bi.getWidth(), h = bi.getHeight();
                double sc = Math.min(1.0, Math.min(
                        (double) UiTheme.IMAGE_PREVIEW_MAX_W / w,
                        (double) UiTheme.IMAGE_PREVIEW_MAX_H / h));
                Image scaled = bi.getScaledInstance(Math.max(1,(int)(w*sc)), Math.max(1,(int)(h*sc)), Image.SCALE_SMOOTH);
                box.add(new JLabel(new ImageIcon(scaled)), BorderLayout.CENTER);
            } else {
                JLabel info = new JLabel("📎 Tệp đính kèm");
                info.setForeground(isMe ? UiTheme.BUBBLE_ME_TEXT : UiTheme.BUBBLE_OTHER_TEXT);
                box.add(info, BorderLayout.CENTER);
            }
        } catch (IOException e) {
            JLabel info = new JLabel("📎 Tệp đính kèm");
            info.setForeground(isMe ? UiTheme.BUBBLE_ME_TEXT : UiTheme.BUBBLE_OTHER_TEXT);
            box.add(info, BorderLayout.CENTER);
        }
        addBubbleRow(box, isMe);
        scrollChatBottom();
    }

    private JLabel bubbleLabel(String htmlBody, boolean isMe) {
        JLabel l = new JLabel("<html><body style='width:240px;margin:0'>" + htmlBody + "</body></html>");
        l.setOpaque(true);
        l.setFont(UiTheme.uiFont(Font.PLAIN, 14));
        l.setBackground(isMe ? UiTheme.BUBBLE_ME : UiTheme.BUBBLE_OTHER);
        l.setForeground(isMe ? UiTheme.BUBBLE_ME_TEXT : UiTheme.BUBBLE_OTHER_TEXT);
        l.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(isMe ? UiTheme.PRIMARY_DARK : UiTheme.BORDER_BLUE, 1, true),
                new EmptyBorder(10, 14, 10, 14)));
        return l;
    }

    private void addBubbleRow(JComponent inner, boolean isMe) {
        JPanel row = new JPanel(new FlowLayout(isMe ? FlowLayout.RIGHT : FlowLayout.LEFT, 12, 6));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(inner);
        chatFeed.add(row);
        chatFeed.add(Box.createVerticalStrut(2));
        chatFeed.revalidate();
        chatFeed.repaint();
    }

    private static String escapeHtml(String s) {
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\n","<br/>");
    }

    private void clearChatFeed() {
        chatFeed.removeAll(); chatFeed.revalidate(); chatFeed.repaint();
    }

    private void scrollChatBottom() {
        SwingUtilities.invokeLater(() -> {
            JScrollBar bar = chatScroll.getVerticalScrollBar();
            bar.setValue(bar.getMaximum());
        });
    }

    // ── Emoji / sticker ───────────────────────────────────────────────────────
    private void insertInlineEmoji(String em) {
        if (em == null || em.isEmpty()) return;
        if (fieldIsPlaceholder(tfMsg, PH_MSG)) { tfMsg.setText(""); tfMsg.setForeground(UiTheme.TEXT); }
        int pos = Math.min(tfMsg.getCaretPosition(), tfMsg.getText().length());
        try { tfMsg.getDocument().insertString(pos, em, null); tfMsg.setCaretPosition(pos + em.length()); }
        catch (BadLocationException ex) { tfMsg.setText(tfMsg.getText() + em); }
        tfMsg.requestFocusInWindow();
    }

    private void showInlineEmojiPopup(JButton anchor) {
        JPopupMenu pop = new JPopupMenu();
        JPanel grid = new JPanel(new GridLayout(0, 6, 4, 4));
        grid.setBackground(UiTheme.WINDOW);
        grid.setBorder(new EmptyBorder(6, 8, 6, 8));
        for (String em : INLINE_EMOJIS) {
            JButton b = new JButton(em);
            b.setFont(b.getFont().deriveFont(18f));
            b.setFocusPainted(false); b.setOpaque(false);
            b.setContentAreaFilled(false); b.setBorderPainted(false);
            b.addActionListener(ev -> { insertInlineEmoji(em); pop.setVisible(false); });
            grid.add(b);
        }
        pop.add(grid);
        pop.show(anchor, 0, anchor.getHeight());
    }

    private void showStickerPicker() {
        if (currentRoom == null) return;
        int opt = JOptionPane.showOptionDialog(this, "Chọn sticker:", "Nhãn dán",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, STICKERS, STICKERS[0]);
        if (opt >= 0) sendStickerUdp(STICKERS[opt]);
    }

    // ── Networking lifecycle ──────────────────────────────────────────────────
    private void stopNetworking() {
        closing = true;
        if (waitTimer != null) { waitTimer.stop(); waitTimer = null; }

        Socket s = tcpSocket; tcpSocket = null; tcpIn = null; tcpOut = null;
        if (s != null) { try { s.close(); } catch (IOException ignored) {} }

        DatagramSocket ds = udpSocket; udpSocket = null;
        if (ds != null && !ds.isClosed()) ds.close();

        Socket as = adminTcpSocket; adminTcpSocket = null; adminTcpIn = null; adminTcpOut = null;
        if (as != null) { try { as.close(); } catch (IOException ignored) {} }

        currentRoom = null; username = null; serverHost = null; isAdmin = false;
    }

    private void disconnectToLogin() {
        stopNetworking();
        closing = false;
        setTitle("Chat Client");
        tabs.setEnabledAt(TAB_LOGIN, true);
        tabs.setEnabledAt(TAB_WAIT,  false);
        tabs.setEnabledAt(TAB_CHAT,  false);
        tabs.setEnabledAt(TAB_ADMIN, false);
        tabs.setSelectedIndex(TAB_LOGIN);
        tfAdminPass.setText("");
    }

    // ── Main ──────────────────────────────────────────────────────────────────
    public static void main(String[] args) {
        System.setProperty("java.net.preferIPv4Stack", "true");
        // ── Banner console (hiển thị trong cửa sổ CMD khi double-click JAR) ──
        System.out.println("╔══════════════════════════════╗");
        System.out.println("║     ChatApp Client v1.0      ║");
        System.out.println("║  Server: " + String.format("%-20s", Server.TCP_PORT + " (TCP) / " + Server.UDP_PORT + " (UDP)") + "║");
        System.out.println("╚══════════════════════════════╝");
        System.out.println("Starting ChatApp...");
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
        catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new Client().setVisible(true));
    }
}
