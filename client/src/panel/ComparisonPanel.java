package panel;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import model.Location;
import model.Message;
import network.ClientSocketManager;
import util.AppTheme;
import javax.swing.*;
import java.awt.*;
import java.util.List;
import util.VietnameseText;
import util.InputValidation;

public final class ComparisonPanel extends JPanel {
    private final ClientSocketManager socketManager;
    private final Gson gson = new Gson();
    private final JComboBox<String> type = new JComboBox<>(new String[]{"Hai thành phố", "Hai quốc gia"});
    private final JTextField leftName = new JTextField(18), rightName = new JTextField(18);
    private final JComboBox<Location> leftCity = new JComboBox<>(), rightCity = new JComboBox<>();
    private final JButton leftSearch = new JButton("Tìm"), rightSearch = new JButton("Tìm");
    private final JButton compare = new JButton("So sánh");
    private final JLabel guidance = new JLabel();
    private final JTextArea status = new JTextArea(4, 50);
    private final ComparisonTableModel model = new ComparisonTableModel();
    private boolean busy;

    public ComparisonPanel(ClientSocketManager socketManager) {
        this.socketManager = socketManager;
        setLayout(new BorderLayout(8, 8));
        setBackground(AppTheme.BG_MAIN);
        setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        add(AppTheme.headerPanel("< So sánh địa điểm"), BorderLayout.NORTH);

        JPanel controls = new JPanel(new BorderLayout(8, 8));
        controls.setBackground(AppTheme.BG_CARD);
        controls.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, AppTheme.BORDER_COLOR),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        JPanel mode = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        mode.setOpaque(false);
        JLabel cmpLbl = new JLabel("So sánh:");
        cmpLbl.setFont(AppTheme.FONT_BOLD);
        cmpLbl.setForeground(AppTheme.TEXT_SECONDARY);
        type.setFont(AppTheme.FONT_FIELD);
        compare.setFont(AppTheme.FONT_BOLD);
        compare.setBackground(AppTheme.PRIMARY);
        compare.setForeground(AppTheme.TEXT_WHITE);
        compare.setFocusPainted(false);
        compare.setBorderPainted(false);
        compare.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        mode.add(cmpLbl);
        mode.add(type);
        mode.add(compare);
        controls.add(mode, BorderLayout.NORTH);
        JPanel choices = new JPanel(new GridLayout(1, 2, 12, 0));
        choices.setOpaque(false);
        choices.add(choice("Bên trái", leftName, leftSearch, leftCity));
        choices.add(choice("Bên phải", rightName, rightSearch, rightCity));
        controls.add(choices, BorderLayout.CENTER);
        guidance.setFont(AppTheme.FONT_SMALL);
        guidance.setForeground(AppTheme.TEXT_SECONDARY);
        controls.add(guidance, BorderLayout.SOUTH);
        add(controls, BorderLayout.NORTH);

        JTable table = new JTable(model) {
            @Override public String getToolTipText(java.awt.event.MouseEvent event) {
                int row = rowAtPoint(event.getPoint()), column = columnAtPoint(event.getPoint());
                return row < 0 || column < 0 ? null : String.valueOf(getValueAt(row, column));
            }
        };
        AppTheme.styleTable(table);
        add(AppTheme.neatScrollPane(table), BorderLayout.CENTER);

        status.setEditable(false);
        status.setFont(AppTheme.FONT_SMALL);
        status.setForeground(AppTheme.TEXT_SECONDARY);
        status.setBackground(AppTheme.BG_CARD);
        status.setLineWrap(true);
        status.setWrapStyleWord(true);
        JScrollPane statusScroll = AppTheme.neatScrollPane(status);
        statusScroll.setPreferredSize(new Dimension(0, 80));
        add(statusScroll, BorderLayout.SOUTH);

        type.addActionListener(e -> resetMode());
        leftSearch.addActionListener(e -> search(leftName, leftCity));
        rightSearch.addActionListener(e -> search(rightName, rightCity));
        leftName.addActionListener(e -> { if (cities()) search(leftName, leftCity); else compare(); });
        rightName.addActionListener(e -> { if (cities()) search(rightName, rightCity); else compare(); });
        compare.addActionListener(e -> compare());
        invalidateSelectionOnEdit(leftName, leftCity);
        invalidateSelectionOnEdit(rightName, rightCity);
        resetMode();
    }

    private JPanel choice(String title, JTextField name, JButton search, JComboBox<Location> candidates) {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder(title));
        JPanel input = new JPanel(new BorderLayout(5, 0));
        input.add(name, BorderLayout.CENTER);
        input.add(search, BorderLayout.EAST);
        panel.add(input, BorderLayout.NORTH);
        candidates.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                     boolean selected, boolean focused) {
                super.getListCellRendererComponent(list, value, index, selected, focused);
                if (value instanceof Location) {
                    Location location = (Location)value;
                    setText(VietnameseText.city(location.getCityName(), location.getCountryCode()) + ", "
                            + VietnameseText.country(location.getCountry(), location.getCountryCode())
                            + (location.getDescription() == null || location.getDescription().isBlank()
                            ? "" : " — " + VietnameseText.city(location.getDescription(), location.getCountryCode())));
                }
                return this;
            }
        });
        candidates.setPrototypeDisplayValue(new Location(0, "Tên thành phố", "Quốc gia", 0, 0, ""));
        panel.add(candidates, BorderLayout.SOUTH);
        return panel;
    }

    private boolean cities() { return type.getSelectedIndex() == 0; }

    private void invalidateSelectionOnEdit(JTextField input, JComboBox<Location> choices) {
        input.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void changed() { if (cities()) choices.removeAllItems(); }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { changed(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { changed(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { changed(); }
        });
    }

    private void resetMode() {
        leftCity.removeAllItems();
        rightCity.removeAllItems();
        leftName.setText("");
        rightName.setText("");
        leftSearch.setVisible(cities());
        rightSearch.setVisible(cities());
        leftCity.setVisible(cities());
        rightCity.setVisible(cities());
        model.setRowCount(0);
        guidance.setText(cities() ? "Nhập tên, bấm Tìm và chọn đúng thành phố ở mỗi bên trước khi so sánh."
                : "Nhập tên quốc gia hoặc mã ISO (ví dụ Việt Nam/VN, Nhật Bản/JP), rồi bấm So sánh.");
        status.setText("Dân số theo nguồn API. Thời tiết mới nhất do OpenWeather cung cấp; cần khóa API trên máy chủ.");
        revalidate();
    }

    private void setBusy(boolean value) {
        busy = value;
        for (Component component : new Component[]{type, leftName, rightName, leftSearch, rightSearch, leftCity, rightCity, compare}) {
            component.setEnabled(!value);
        }
    }

    private void search(JTextField input, JComboBox<Location> choices) {
        if (busy || !cities()) return;
        final String keyword;
        try {
            keyword = InputValidation.keyword(input.getText(), "Tên thành phố", 2, 200);
        } catch (InputValidation.ValidationException exception) {
            status.setText(exception.getMessage());
            return;
        }
        choices.removeAllItems();
        setBusy(true);
        status.setText("Đang tìm thành phố: " + keyword);
        new SwingWorker<Message, Void>() {
            @Override protected Message doInBackground() throws Exception {
                JsonObject data = new JsonObject();
                data.addProperty("keyword", keyword);
                return socketManager.sendRequest(new Message("SEARCH_COMPARISON_CITIES", data));
            }
            @Override protected void done() {
                if (!ComparisonPanel.this.isDisplayable()) return;
                try {
                    Message response = get();
                    require(response, "SEARCH_COMPARISON_CITIES_RESPONSE");
                    List<Location> found = gson.fromJson(gson.toJsonTree(response.getData()),
                            new TypeToken<List<Location>>(){}.getType());
                    if (found != null) for (Location city : found) choices.addItem(city);
                    status.setText(choices.getItemCount() == 0 ? "Không tìm thấy thành phố: " + keyword
                            : "Chọn đúng thành phố và quốc gia trong danh sách. Bấm So sánh khi đã chọn đủ hai bên.");
                } catch (Exception e) { status.setText("Tìm thành phố thất bại: " + message(e)); }
                finally { setBusy(false); }
            }
        }.execute();
    }

    private void compare() {
        if (busy) return;
        boolean comparingCities = cities();
        JsonObject data = new JsonObject();
        if (comparingCities) {
            Location left = (Location)leftCity.getSelectedItem(), right = (Location)rightCity.getSelectedItem();
            if (left == null || right == null) { status.setText("Hãy tìm và chọn thành phố ở cả hai bên."); return; }
            try {
                InputValidation.positiveId(left.getId(), "Mã thành phố bên trái");
                InputValidation.positiveId(right.getId(), "Mã thành phố bên phải");
                InputValidation.coordinates(left.getLatitude(), left.getLongitude());
                InputValidation.coordinates(right.getLatitude(), right.getLongitude());
            } catch (InputValidation.ValidationException exception) {
                status.setText(exception.getMessage());
                return;
            }
            if (left.getId() == right.getId()) { status.setText("Hãy chọn hai thành phố khác nhau."); return; }
            data.addProperty("leftId", left.getId());
            data.addProperty("rightId", right.getId());
        } else {
            final String left, right;
            try {
                left = InputValidation.keyword(leftName.getText(), "Tên quốc gia bên trái", 1, 200);
                right = InputValidation.keyword(rightName.getText(), "Tên quốc gia bên phải", 1, 200);
            } catch (InputValidation.ValidationException exception) {
                status.setText(exception.getMessage());
                return;
            }
            if (left.equalsIgnoreCase(right)) { status.setText("Hãy nhập hai quốc gia khác nhau."); return; }
            data.addProperty("leftName", left);
            data.addProperty("rightName", right);
        }
        setBusy(true);
        model.setRowCount(0);
        status.setText("Đang lấy dữ liệu để so sánh...");
        new SwingWorker<Message, Void>() {
            @Override protected Message doInBackground() throws Exception {
                return socketManager.sendRequest(new Message(comparingCities ? "COMPARE_CITIES" : "COMPARE_COUNTRIES", data));
            }
            @Override protected void done() {
                if (!ComparisonPanel.this.isDisplayable()) return;
                try {
                    Message response = get();
                    require(response, comparingCities ? "COMPARE_CITIES_RESPONSE" : "COMPARE_COUNTRIES_RESPONSE");
                    JsonObject result = gson.toJsonTree(response.getData()).getAsJsonObject();
                    model.display(result, comparingCities);
                    StringBuilder notes = new StringBuilder(comparingCities
                            ? "Thời tiết theo tọa độ từng thành phố. "
                            : "Thời tiết được lấy tại thủ đô ghi trong bảng; số liệu này đại diện cho địa điểm đó. ");
                    notes.append("Chênh lệch = bên trái trừ bên phải. Dữ liệu thiếu không được tính là 0.\n");
                    notes.append("Nguồn địa lý: GeoNames/Open-Meteo (thành phố), countries.dev (quốc gia).\n");
                    appendErrors(notes, "Bên trái", ComparisonTableModel.object(result, "left"));
                    appendErrors(notes, "Bên phải", ComparisonTableModel.object(result, "right"));
                    status.setText(notes.toString());
                } catch (Exception e) { status.setText("So sánh thất bại: " + message(e)); }
                finally { setBusy(false); }
            }
        }.execute();
    }

    private static void appendErrors(StringBuilder text, String label, JsonObject side) {
        for (String field : new String[]{"error", "countryError", "weatherError"}) {
            if (side.has(field)) text.append(label).append(": ").append(ComparisonTableModel.text(side, field)).append('\n');
        }
    }

    private static void require(Message response, String action) throws java.io.IOException {
        if (response == null || !action.equals(response.getAction())) {
            throw new java.io.IOException(response == null ? "Máy chủ không trả lời." : String.valueOf(response.getData()));
        }
    }

    private static String message(Exception e) {
        return VietnameseText.errorMessage(e);
    }
}
