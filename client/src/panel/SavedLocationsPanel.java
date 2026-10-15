package panel;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import model.Location;
import model.Message;
import model.User;
import network.ClientSocketManager;
import util.AppTheme;
import util.VietnameseText;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

public class SavedLocationsPanel extends JPanel {
    private final User currentUser;
    private final ClientSocketManager socketManager;
    private final Gson gson;
    private JTable locationTable;
    private DefaultTableModel tableModel;
    private JButton refreshButton;
    private JButton deleteButton;
    private boolean busy;

    public SavedLocationsPanel(User user, ClientSocketManager socketManager) {
        this.currentUser = user;
        this.socketManager = socketManager;
        this.gson = new Gson();
        buildLayout();
        loadLocations();
    }

    private void buildLayout() {
        setLayout(new BorderLayout(0, 0));
        setBackground(AppTheme.BG_MAIN);

        // Header
        add(AppTheme.headerPanel("Dia diem da luu"), BorderLayout.NORTH);

        // Top bar
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        topPanel.setBackground(AppTheme.BG_CARD);
        topPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, AppTheme.BORDER_COLOR));
        refreshButton = new JButton("Lam moi");
        refreshButton.setFont(AppTheme.FONT_BOLD);
        refreshButton.setBackground(AppTheme.BG_CARD);
        refreshButton.setForeground(AppTheme.PRIMARY_DARK);
        deleteButton = new JButton("Xoa dia diem da chon");
        deleteButton.setEnabled(false);
        deleteButton.setFont(AppTheme.FONT_BOLD);
        deleteButton.setBackground(AppTheme.DANGER);
        deleteButton.setForeground(AppTheme.TEXT_WHITE);
        deleteButton.setFocusPainted(false);
        deleteButton.setBorderPainted(false);
        deleteButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        topPanel.add(refreshButton);
        topPanel.add(deleteButton);
        add(topPanel, BorderLayout.NORTH);

        // Center table
        String[] columns = {"Ma dia diem", "Ten dia diem", "Vi do", "Kinh do", "Ghi chu"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        locationTable = new JTable(tableModel);
        AppTheme.styleTable(locationTable);
        locationTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting())
                deleteButton.setEnabled(!busy && locationTable.getSelectedRow() != -1);
        });
        add(AppTheme.neatScrollPane(locationTable), BorderLayout.CENTER);

        refreshButton.addActionListener(e -> loadLocations());
        deleteButton.addActionListener(e -> deleteLocation());
    }

    private void loadLocations() {
        if (busy) return;
        setBusy(true);
        new SwingWorker<Message, Void>() {
            @Override
            protected Message doInBackground() throws Exception {
                JsonObject data = new JsonObject();
                return socketManager.sendRequest(new Message("GET_SAVED_LOCATIONS", data));
            }

            @Override
            protected void done() {
                if (!SavedLocationsPanel.this.isDisplayable()) return;
                try {
                    Message response = get();
                    requireResponse(response, "GET_SAVED_LOCATIONS_RESPONSE");
                    if (response != null && "GET_SAVED_LOCATIONS_RESPONSE".equals(response.getAction())) {
                        java.lang.reflect.Type listType = new TypeToken<List<Location>>(){}.getType();
                        List<Location> locations = gson.fromJson(gson.toJsonTree(response.getData()), listType);
                        tableModel.setRowCount(0);
                        if (locations != null) {
                            for (Location loc : locations) {
                                tableModel.addRow(new Object[]{
                                        loc.getId(), VietnameseText.location(loc.getCityName()),
                                        String.format(VietnameseText.LOCALE, "%.5f°", loc.getLatitude()),
                                        String.format(VietnameseText.LOCALE, "%.5f°", loc.getLongitude()), loc.getDescription()
                                });
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    JOptionPane.showMessageDialog(SavedLocationsPanel.this, "Không tải được địa điểm đã lưu: " + VietnameseText.errorMessage(e),
                            "Lỗi tải dữ liệu", JOptionPane.ERROR_MESSAGE);
                } finally {
                    setBusy(false);
                }
            }
        }.execute();
    }

    private void deleteLocation() {
        if (busy) return;
        int row = locationTable.getSelectedRow();
        if (row == -1) return;

        int id = (Integer) tableModel.getValueAt(row, 0);
        String name = (String) tableModel.getValueAt(row, 1);

        int confirm = JOptionPane.showConfirmDialog(this, "Bạn có chắc muốn xóa '" + name + "'?", "Xác nhận xóa", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        setBusy(true);
        new SwingWorker<Message, Void>() {
            @Override
            protected Message doInBackground() throws Exception {
                JsonObject data = new JsonObject();
                data.addProperty("id", id);
                return socketManager.sendRequest(new Message("DELETE_LOCATION", data));
            }

            @Override
            protected void done() {
                if (!SavedLocationsPanel.this.isDisplayable()) return;
                try {
                    Message response = get();
                    requireResponse(response, "DELETE_LOCATION_RESPONSE");
                    if (response != null && "DELETE_LOCATION_RESPONSE".equals(response.getAction()) && (Boolean)response.getData()) {
                        tableModel.removeRow(row);
                        JOptionPane.showMessageDialog(SavedLocationsPanel.this, "Đã xóa địa điểm.", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(SavedLocationsPanel.this, "Không xóa được địa điểm.", "Lỗi xóa địa điểm", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    JOptionPane.showMessageDialog(SavedLocationsPanel.this, "Không xóa được địa điểm: " + VietnameseText.errorMessage(e),
                            "Lỗi xóa địa điểm", JOptionPane.ERROR_MESSAGE);
                } finally {
                    setBusy(false);
                }
            }
        }.execute();
    }

    private void setBusy(boolean value) {
        busy = value;
        refreshButton.setEnabled(!value);
        locationTable.setEnabled(!value);
        deleteButton.setEnabled(!value && locationTable.getSelectedRow() != -1);
    }

    private static void requireResponse(Message response, String action) throws java.io.IOException {
        if (response == null) throw new java.io.IOException("Máy chủ không trả lời.");
        if ("ERROR".equals(response.getAction())) {
            throw new java.io.IOException("Máy chủ báo lỗi: " + String.valueOf(response.getData()));
        }
        if (!action.equals(response.getAction())) throw new java.io.IOException("Phản hồi của máy chủ không hợp lệ.");
    }
}
