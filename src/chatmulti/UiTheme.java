package chatmulti;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;

/**
 * Giao diện dùng chung: {@link ChatClientUI}, {@link ChatServer}, {@link ConnectDialog}.
 */
public final class UiTheme {
    private UiTheme() {}

    public static final int IMAGE_PREVIEW_MAX_W = 280;
    public static final int IMAGE_PREVIEW_MAX_H = 320;

    public static final Color WINDOW_BG = new Color(238, 241, 246);
    public static final Color CARD = Color.WHITE;
    public static final Color SIDEBAR_BG = new Color(228, 232, 240);
    public static final Color CHAT_BG = new Color(248, 249, 252);
    public static final Color INPUT_BAR = new Color(255, 255, 255);
    public static final Color BORDER = new Color(185, 192, 205);
    public static final Color TEXT = new Color(22, 27, 34);
    public static final Color TEXT_MUTED = new Color(75, 83, 96);
    /** Nút chính: nền đậm để chữ trắng đọc rõ */
    public static final Color ACCENT = new Color(22, 93, 200);
    public static final Color ACCENT_DARK = new Color(16, 70, 160);
    public static final Color SUCCESS_BG = new Color(198, 232, 212);
    public static final Color SUCCESS_TEXT = new Color(10, 66, 32);
    public static final Color OFFLINE_BG = new Color(218, 222, 230);
    public static final Color OFFLINE_TEXT = new Color(55, 60, 70);
    /** Nền nút phụ: xám nhạt */
    public static final Color BUTTON_SECONDARY_BG = new Color(222, 226, 234);
    public static final Color BUBBLE_ME = new Color(22, 93, 200);
    public static final Color BUBBLE_ME_TEXT = Color.WHITE;
    public static final Color BUBBLE_OTHER = new Color(230, 235, 245);
    public static final Color BUBBLE_OTHER_TEXT = new Color(33, 37, 41);
    public static final Color LIST_SELECTION = new Color(200, 225, 255);
    public static final Color IMAGE_PANEL_ME = new Color(230, 242, 255);
    public static final Color IMAGE_PANEL_OTHER = new Color(248, 250, 252);

    public static Font uiFont(int style, int size) {
        return new Font(Font.SANS_SERIF, style, size);
    }

    public static void stylePrimaryButton(JButton b) {
        b.setUI(new BasicButtonUI());
        b.setFont(uiFont(Font.BOLD, 13));
        b.setBackground(ACCENT);
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setOpaque(true);
        b.setContentAreaFilled(true);
        b.setBorderPainted(true);
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ACCENT_DARK, 1),
                BorderFactory.createEmptyBorder(8, 16, 8, 16)));
    }

    public static void styleSecondaryButton(JButton b) {
        b.setUI(new BasicButtonUI());
        b.setFont(uiFont(Font.BOLD, 13));
        b.setBackground(BUTTON_SECONDARY_BG);
        b.setForeground(TEXT);
        b.setFocusPainted(false);
        b.setOpaque(true);
        b.setContentAreaFilled(true);
        b.setBorderPainted(true);
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(150, 158, 175), 1),
                BorderFactory.createEmptyBorder(8, 16, 8, 16)));
    }

    public static Border cardBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1),
                BorderFactory.createEmptyBorder(12, 12, 12, 12));
    }

    public static JPanel accentBar() {
        JPanel p = new JPanel();
        p.setPreferredSize(new Dimension(0, 5));
        p.setBackground(ACCENT);
        return p;
    }
}
