package util;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * Hệ thống thiết kế (Design System) cho ứng dụng Tra Cứu Thông Tin Địa Lý.
 * Chứa bảng màu, font chữ, và các phương thức factory tạo UI components
 * theo phong cách xanh lá tươi - hiện đại - mobile-inspired.
 */
public final class AppTheme {

    // ─── BẢNG MÀU ────────────────────────────────────────────────────────────
    public static final Color PRIMARY        = new Color(0x2ECC71);
    public static final Color PRIMARY_DARK   = new Color(0x27AE60);
    public static final Color PRIMARY_LIGHT  = new Color(0xD5F5E3);
    public static final Color ACCENT         = new Color(0x1ABC9C);
    public static final Color DANGER         = new Color(0xE74C3C);
    public static final Color DANGER_DARK    = new Color(0xC0392B);
    public static final Color DANGER_LIGHT   = new Color(0xFDEDEC);
    public static final Color WARNING        = new Color(0xF39C12);
    public static final Color WARNING_LIGHT  = new Color(0xFEF9E7);
    public static final Color BG_MAIN        = new Color(0xF0FBF4);
    public static final Color BG_CARD        = Color.WHITE;
    public static final Color BG_HEADER      = new Color(0x27AE60);
    public static final Color TEXT_PRIMARY   = new Color(0x1A2D23);
    public static final Color TEXT_SECONDARY = new Color(0x5D6D7E);
    public static final Color TEXT_MUTED     = new Color(0xABB2B9);
    public static final Color TEXT_WHITE     = Color.WHITE;
    public static final Color BORDER_COLOR   = new Color(0xD5E8D4);
    public static final Color BORDER_FOCUS   = PRIMARY;

    // ─── FONT ────────────────────────────────────────────────────────────────
    public static final Font FONT_TITLE   = new Font("Segoe UI", Font.BOLD,  22);
    public static final Font FONT_HEADER  = new Font("Segoe UI", Font.BOLD,  14);
    public static final Font FONT_SUBHEAD = new Font("Segoe UI", Font.BOLD,  12);
    public static final Font FONT_BODY    = new Font("Segoe UI", Font.PLAIN, 13);
    public static final Font FONT_SMALL   = new Font("Segoe UI", Font.PLAIN, 11);
    public static final Font FONT_BOLD    = new Font("Segoe UI", Font.BOLD,  13);
    public static final Font FONT_FIELD   = new Font("Segoe UI", Font.PLAIN, 13);
    public static final Font FONT_MONO    = new Font("Consolas",  Font.PLAIN, 12);

    // ─── ĐỘ CONG / KHOẢNG CÁCH ───────────────────────────────────────────────
    public static final int ARC_CARD   = 14;
    public static final int ARC_BTN    = 10;
    public static final int ARC_FIELD  = 8;
    public static final int PAD_CARD   = 16;

    private AppTheme() { }

    // ═══════════════════════════════════════════════════════════════════════
    // CẤU HÌNH TOÀN CỤC
    // ═══════════════════════════════════════════════════════════════════════

    public static void applyGlobalDefaults() {
        UIManager.put("Panel.background",             BG_MAIN);
        UIManager.put("Label.foreground",             TEXT_PRIMARY);
        UIManager.put("Label.font",                   FONT_BODY);
        UIManager.put("TextField.font",               FONT_FIELD);
        UIManager.put("PasswordField.font",           FONT_FIELD);
        UIManager.put("TextArea.font",                FONT_FIELD);
        UIManager.put("ComboBox.font",                FONT_FIELD);
        UIManager.put("CheckBox.font",                FONT_BODY);
        UIManager.put("Table.font",                   FONT_BODY);
        UIManager.put("TableHeader.font",             FONT_BOLD);
        UIManager.put("TableHeader.background",       PRIMARY_LIGHT);
        UIManager.put("TableHeader.foreground",       PRIMARY_DARK);
        UIManager.put("Table.selectionBackground",    PRIMARY);
        UIManager.put("Table.selectionForeground",    TEXT_WHITE);
        UIManager.put("Table.gridColor",              BORDER_COLOR);
        UIManager.put("Table.background",             BG_CARD);
        UIManager.put("ScrollPane.border",            BorderFactory.createEmptyBorder());
        UIManager.put("TabbedPane.font",              FONT_BOLD);
        UIManager.put("TabbedPane.selected",          BG_CARD);
        UIManager.put("TabbedPane.background",        BG_MAIN);
        UIManager.put("TabbedPane.contentAreaColor",  BG_CARD);
        UIManager.put("TitledBorder.font",            FONT_BOLD);
        UIManager.put("TitledBorder.titleColor",      PRIMARY_DARK);
        UIManager.put("SplitPane.background",         BG_MAIN);
        UIManager.put("SplitPaneDivider.background",  BORDER_COLOR);
        UIManager.put("OptionPane.background",        BG_CARD);
        UIManager.put("OptionPane.messageForeground", TEXT_PRIMARY);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // FACTORY: BUTTONS
    // ═══════════════════════════════════════════════════════════════════════

    /** Nút xanh lá chủ đạo */
    public static JButton primaryButton(String text) {
        return makeButton(text, PRIMARY, PRIMARY_DARK, TEXT_WHITE);
    }

    /** Nút đỏ - nguy hiểm / hủy */
    public static JButton dangerButton(String text) {
        return makeButton(text, DANGER, DANGER_DARK, TEXT_WHITE);
    }

    /** Nút phụ - viền xanh, nền trắng */
    public static JButton outlineButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(FONT_BOLD);
        btn.setForeground(PRIMARY_DARK);
        btn.setBackground(BG_CARD);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(PRIMARY, 1, true),
                BorderFactory.createEmptyBorder(7, 18, 7, 18)));
        return btn;
    }

    private static JButton makeButton(String text, Color bg, Color hover, Color fg) {
        JButton btn = new JButton(text);
        btn.setFont(FONT_BOLD);
        btn.setForeground(fg);
        btn.setBackground(bg);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(BorderFactory.createEmptyBorder(8, 22, 8, 22));
        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseEntered(java.awt.event.MouseEvent e) {
                if (btn.isEnabled()) btn.setBackground(hover);
            }
            @Override public void mouseExited(java.awt.event.MouseEvent e) {
                btn.setBackground(bg);
            }
        });
        return btn;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // FACTORY: FIELDS
    // ═══════════════════════════════════════════════════════════════════════

    public static JTextField styledField(int columns) {
        JTextField f = new JTextField(columns);
        applyFieldStyle(f);
        return f;
    }

    public static JPasswordField styledPasswordField(int columns) {
        JPasswordField f = new JPasswordField(columns);
        applyFieldStyle(f);
        return f;
    }

    public static JTextArea styledTextArea(int rows, int cols) {
        JTextArea ta = new JTextArea(rows, cols);
        ta.setFont(FONT_FIELD);
        ta.setForeground(TEXT_PRIMARY);
        ta.setBackground(BG_CARD);
        ta.setCaretColor(PRIMARY_DARK);
        ta.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        ta.setLineWrap(true);
        ta.setWrapStyleWord(true);
        return ta;
    }

    private static void applyFieldStyle(JTextField f) {
        f.setFont(FONT_FIELD);
        f.setBackground(BG_CARD);
        f.setForeground(TEXT_PRIMARY);
        f.setCaretColor(PRIMARY_DARK);
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1, true),
                BorderFactory.createEmptyBorder(7, 10, 7, 10)));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // FACTORY: PANELS / LABELS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Panel nền trắng với góc bo tròn - dùng làm "card".
     */
    public static JPanel cardPanel(LayoutManager layout) {
        JPanel p = new JPanel(layout);
        p.setBackground(BG_CARD);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1, true),
                BorderFactory.createEmptyBorder(PAD_CARD, PAD_CARD, PAD_CARD, PAD_CARD)));
        return p;
    }

    /**
     * Panel header xanh lá (dùng cho đầu mỗi màn hình).
     */
    public static JPanel headerPanel(String title) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(BG_HEADER);
        p.setBorder(BorderFactory.createEmptyBorder(12, 18, 12, 18));
        JLabel lbl = new JLabel(title);
        lbl.setFont(FONT_HEADER);
        lbl.setForeground(TEXT_WHITE);
        p.add(lbl, BorderLayout.WEST);
        return p;
    }

    /**
     * Nhãn tiêu đề section nhỏ.
     */
    public static JLabel sectionLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(FONT_SUBHEAD);
        lbl.setForeground(TEXT_SECONDARY);
        return lbl;
    }

    /** Viền tiêu đề section có đường kẻ trái xanh */
    public static Border sectionBorder(String title) {
        return BorderFactory.createTitledBorder(
                BorderFactory.createMatteBorder(0, 3, 0, 0, PRIMARY),
                "  " + title,
                javax.swing.border.TitledBorder.LEFT,
                javax.swing.border.TitledBorder.DEFAULT_POSITION,
                FONT_BOLD,
                PRIMARY_DARK);
    }

    /** Badge nhỏ màu xanh nhạt */
    public static JLabel badge(String text) {
        JLabel lbl = new JLabel("  " + text + "  ");
        lbl.setFont(FONT_SMALL);
        lbl.setForeground(PRIMARY_DARK);
        lbl.setBackground(PRIMARY_LIGHT);
        lbl.setOpaque(true);
        lbl.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        return lbl;
    }

    /** Dòng thông tin key-value */
    public static JPanel infoRow(String key, String value) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.setOpaque(false);
        JLabel k = new JLabel(key);
        k.setFont(FONT_BOLD);
        k.setForeground(TEXT_SECONDARY);
        JLabel v = new JLabel(value != null ? value : "—");
        v.setFont(FONT_BODY);
        v.setForeground(TEXT_PRIMARY);
        row.add(k);
        row.add(v);
        return row;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // FACTORY: TABLE + SCROLL
    // ═══════════════════════════════════════════════════════════════════════

    public static void styleTable(JTable table) {
        table.setFont(FONT_BODY);
        table.setRowHeight(32);
        table.setGridColor(BORDER_COLOR);
        table.setBackground(BG_CARD);
        table.setSelectionBackground(PRIMARY);
        table.setSelectionForeground(TEXT_WHITE);
        table.setShowVerticalLines(false);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setFillsViewportHeight(true);
        table.getTableHeader().setFont(FONT_BOLD);
        table.getTableHeader().setBackground(PRIMARY_LIGHT);
        table.getTableHeader().setForeground(PRIMARY_DARK);
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setBorder(
                BorderFactory.createMatteBorder(0, 0, 2, 0, PRIMARY));
    }

    public static JScrollPane neatScrollPane(Component view) {
        JScrollPane sp = new JScrollPane(view);
        sp.setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 1));
        sp.setBackground(BG_MAIN);
        sp.getViewport().setBackground(BG_CARD);
        styleScrollBar(sp.getVerticalScrollBar());
        styleScrollBar(sp.getHorizontalScrollBar());
        return sp;
    }

    private static void styleScrollBar(JScrollBar bar) {
        bar.setUI(new BasicScrollBarUI() {
            @Override protected void configureScrollBarColors() {
                thumbColor = new Color(0x27AE60);
                trackColor = BG_MAIN;
            }
            @Override protected JButton createDecreaseButton(int o) { return tiny(); }
            @Override protected JButton createIncreaseButton(int o) { return tiny(); }
            private JButton tiny() {
                JButton b = new JButton(); b.setPreferredSize(new Dimension(0, 0)); return b;
            }
        });
        bar.setPreferredSize(new Dimension(7, 7));
    }
}
