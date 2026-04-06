package chatmulti;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class ConnectDialog {

    public static final class Result {
        public final String host;
        public final int port;

        public Result(String host, int port) {
            this.host = host;
            this.port = port;
        }
    }

    private ConnectDialog() {}

    public static Result show(Window owner) {
        JDialog dlg = new JDialog(owner, "Kết nối server", Dialog.ModalityType.APPLICATION_MODAL);
        dlg.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dlg.getContentPane().setBackground(UiTheme.WINDOW_BG);

        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(true);
        p.setBackground(UiTheme.CARD);
        p.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(UiTheme.BORDER, 1),
                new EmptyBorder(14, 16, 14, 16)));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 8, 6, 8);
        gbc.anchor = GridBagConstraints.WEST;
        Font lf = UiTheme.uiFont(Font.PLAIN, 13);

        gbc.gridx = 0;
        gbc.gridy = 0;
        JLabel lbIp = new JLabel("IP máy chủ:");
        lbIp.setFont(lf);
        lbIp.setForeground(UiTheme.TEXT);
        p.add(lbIp, gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        JTextField txtIP = new JTextField(18);
        txtIP.setToolTipText("Máy khác: IP LAN server. Cùng máy với server: có thể gõ 127.0.0.1 (app tự xử lý nếu nhập đúng IP của máy).");
        txtIP.setFont(lf);
        txtIP.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(UiTheme.BORDER, 1), new EmptyBorder(6, 8, 6, 8)));
        p.add(txtIP, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        JLabel lbPort = new JLabel("Cổng TCP:");
        lbPort.setFont(lf);
        lbPort.setForeground(UiTheme.TEXT);
        p.add(lbPort, gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        JTextField txtPort = new JTextField(String.valueOf(ChatServer.TCP_PORT), 8);
        txtPort.setFont(lf);
        txtPort.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(UiTheme.BORDER, 1), new EmptyBorder(6, 8, 6, 8)));
        p.add(txtPort, gbc);

        JLabel lblStatus = new JLabel(" ");
        lblStatus.setFont(UiTheme.uiFont(Font.PLAIN, 12));
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        lblStatus.setForeground(UiTheme.TEXT_MUTED);
        p.add(lblStatus, gbc);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        btns.setOpaque(false);
        JButton btnAuto = new JButton("Tự động tìm (WiFi)");
        JButton btnOk = new JButton("Kết nối");
        JButton btnCancel = new JButton("Hủy");
        UiTheme.styleSecondaryButton(btnAuto);
        UiTheme.stylePrimaryButton(btnOk);
        UiTheme.styleSecondaryButton(btnCancel);
        btns.add(btnAuto);
        btns.add(btnOk);
        btns.add(btnCancel);

        final Result[] holder = { null };

        Runnable applyResult = () -> {
            String host = txtIP.getText().trim();
            if (host.isEmpty()) {
                JOptionPane.showMessageDialog(dlg, "Nhập IP máy chạy server, hoặc dùng \"Tự động tìm\".", "Thiếu IP", JOptionPane.WARNING_MESSAGE);
                return;
            }
            int port;
            try {
                port = Integer.parseInt(txtPort.getText().trim());
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(dlg, "Port không hợp lệ.", "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }
            holder[0] = new Result(host, port);
            dlg.dispose();
        };

        btnOk.addActionListener(e -> applyResult.run());
        btnCancel.addActionListener(e -> dlg.dispose());

        btnAuto.addActionListener(e -> {
            btnAuto.setEnabled(false);
            btnOk.setEnabled(false);
            lblStatus.setForeground(UiTheme.TEXT_MUTED);
            lblStatus.setText("Đang tìm server trên mạng LAN...");
            new SwingWorker<Result, Void>() {
                @Override
                protected Result doInBackground() {
                    return discoverServer(3500);
                }

                @Override
                protected void done() {
                    btnAuto.setEnabled(true);
                    btnOk.setEnabled(true);
                    try {
                        Result r = get();
                        if (r != null) {
                            txtIP.setText(r.host);
                            txtPort.setText(String.valueOf(r.port));
                            lblStatus.setForeground(UiTheme.SUCCESS_TEXT);
                            lblStatus.setText("Đã tìm thấy: " + r.host + ":" + r.port);
                            holder[0] = r;
                            dlg.dispose();
                        } else {
                            lblStatus.setForeground(new Color(180, 90, 30));
                            lblStatus.setText("Không tìm thấy. Kiểm tra server đã Start và cùng WiFi; hoặc nhập IP.");
                        }
                    } catch (Exception ex) {
                        lblStatus.setForeground(new Color(180, 50, 50));
                        lblStatus.setText("Lỗi khi tìm server.");
                    }
                }
            }.execute();
        });

        dlg.setLayout(new BorderLayout(10, 12));
        dlg.getRootPane().setBorder(new EmptyBorder(12, 14, 14, 14));
        dlg.add(p, BorderLayout.CENTER);
        dlg.add(btns, BorderLayout.SOUTH);
        dlg.pack();
        dlg.setLocationRelativeTo(owner);
        dlg.setVisible(true);
        return holder[0];
    }

    static Result discoverServer(int timeoutMs) {
        final String req = "CHATMULTI_DISCOVER";
        final String prefix = "CHATMULTI_HERE|";
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            socket.setSoTimeout(timeoutMs);
            byte[] reqBytes = req.getBytes(StandardCharsets.UTF_8);
            Set<InetAddress> targets = new HashSet<>();
            targets.add(InetAddress.getByName("255.255.255.255"));
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    InetAddress b = ia.getBroadcast();
                    if (b != null) targets.add(b);
                }
            }
            for (InetAddress bc : targets) {
                try {
                    socket.send(new DatagramPacket(reqBytes, reqBytes.length, bc, ChatServer.DISCOVER_PORT));
                } catch (Exception ignored) { }
            }
            byte[] buf = new byte[512];
            DatagramPacket p = new DatagramPacket(buf, buf.length);
            socket.receive(p);
            String line = new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8).trim();
            if (line.startsWith(prefix)) {
                int port = Integer.parseInt(line.substring(prefix.length()).trim());
                return new Result(p.getAddress().getHostAddress(), port);
            }
        } catch (SocketTimeoutException e) {
            return null;
        } catch (Exception e) {
            return null;
        }
        return null;
    }
}
