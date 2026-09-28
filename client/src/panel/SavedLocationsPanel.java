package panel;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import model.Location;
import model.Message;
import model.User;
import network.ClientSocketManager;

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

    public SavedLocationsPanel(User user, ClientSocketManager socketManager) {
        this.currentUser = user;
        this.socketManager = socketManager;
        this.gson = new Gson();
        buildLayout();
        loadLocations();
    }

    private void buildLayout() {
        setLayout(new BorderLayout());

        // Top bar
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        refreshButton = new JButton("Làm mới");
        deleteButton = new JButton("Xóa địa điểm chọn");
        deleteButton.setEnabled(false);
        topPanel.add(refreshButton);
        topPanel.add(deleteButton);
        add(topPanel, BorderLayout.NORTH);

        // Center table
        String[] columns = {"ID", "Tên địa điểm", "Vĩ độ", "Kinh độ", "Ghi chú"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        locationTable = new JTable(tableModel);
        locationTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                deleteButton.setEnabled(locationTable.getSelectedRow() != -1);
            }
        });
        add(new JScrollPane(locationTable), BorderLayout.CENTER);

        refreshButton.addActionListener(e -> loadLocations());
        deleteButton.addActionListener(e -> deleteLocation());
    }

    private void loadLocations() {
        refreshButton.setEnabled(false);
        new SwingWorker<Message, Void>() {
            @Override
            protected Message doInBackground() throws Exception {
                JsonObject data = new JsonObject();
                data.addProperty("userId", currentUser.getId());
                return socketManager.sendRequest(new Message("GET_SAVED_LOCATIONS", data));
            }

            @Override
            protected void done() {
                try {
                    Message response = get();
                    if (response != null && "GET_SAVED_LOCATIONS_RESPONSE".equals(response.getAction())) {
                        java.lang.reflect.Type listType = new TypeToken<List<Location>>(){}.getType();
                        List<Location> locations = gson.fromJson(gson.toJsonTree(response.getData()), listType);
                        tableModel.setRowCount(0);
                        if (locations != null) {
                            for (Location loc : locations) {
                                tableModel.addRow(new Object[]{
                                        loc.getId(), loc.getCityName(), loc.getLatitude(), loc.getLongitude(), loc.getDescription()
                                });
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    JOptionPane.showMessageDialog(SavedLocationsPanel.this, "Lỗi khi tải danh sách địa điểm.");
                } finally {
                    refreshButton.setEnabled(true);
                }
            }
        }.execute();
    }

    private void deleteLocation() {
        int row = locationTable.getSelectedRow();
        if (row == -1) return;

        int id = (Integer) tableModel.getValueAt(row, 0);
        String name = (String) tableModel.getValueAt(row, 1);

        int confirm = JOptionPane.showConfirmDialog(this, "Bạn có chắc muốn xóa '" + name + "'?", "Xác nhận xóa", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        new SwingWorker<Message, Void>() {
            @Override
            protected Message doInBackground() throws Exception {
                JsonObject data = new JsonObject();
                data.addProperty("id", id);
                data.addProperty("userId", currentUser.getId());
                return socketManager.sendRequest(new Message("DELETE_LOCATION", data));
            }

            @Override
            protected void done() {
                try {
                    Message response = get();
                    if (response != null && "DELETE_LOCATION_RESPONSE".equals(response.getAction()) && (Boolean)response.getData()) {
                        tableModel.removeRow(row);
                        JOptionPane.showMessageDialog(SavedLocationsPanel.this, "Đã xóa thành công.");
                    } else {
                        JOptionPane.showMessageDialog(SavedLocationsPanel.this, "Xóa thất bại.");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }.execute();
    }
}
