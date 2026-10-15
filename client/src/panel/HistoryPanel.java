package panel;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import model.Message;
import model.User;
import model.HistoryEntry;
import network.ClientSocketManager;
import util.AppTheme;
import util.VietnameseText;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

public class HistoryPanel extends JPanel {
    private final User currentUser;
    private final ClientSocketManager socketManager;
    private final Gson gson;
    private JTable historyTable;
    private DefaultTableModel tableModel;
    private JButton refreshButton;

    public HistoryPanel(User user, ClientSocketManager socketManager) {
        this.currentUser = user;
        this.socketManager = socketManager;
        this.gson = new Gson();
        buildLayout();
        loadHistory();
    }

    private void buildLayout() {
        setLayout(new BorderLayout(0, 0));
        setBackground(AppTheme.BG_MAIN);

        // Header
        add(AppTheme.headerPanel("Lich su tra cuu"), BorderLayout.NORTH);

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        topPanel.setBackground(AppTheme.BG_CARD);
        topPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, AppTheme.BORDER_COLOR));
        refreshButton = new JButton("Lam moi lich su");
        refreshButton.setFont(AppTheme.FONT_BOLD);
        refreshButton.setBackground(AppTheme.BG_CARD);
        refreshButton.setForeground(AppTheme.PRIMARY_DARK);
        topPanel.add(refreshButton);
        add(topPanel, BorderLayout.NORTH);

        String[] columns = {"So thu tu", "Tu khoa tim kiem", "Loai tra cuu"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        historyTable = new JTable(tableModel);
        AppTheme.styleTable(historyTable);
        add(AppTheme.neatScrollPane(historyTable), BorderLayout.CENTER);

        refreshButton.addActionListener(e -> loadHistory());
    }

    public void loadHistory() {
        if (!refreshButton.isEnabled()) return;
        refreshButton.setEnabled(false);
        new SwingWorker<Message, Void>() {
            @Override
            protected Message doInBackground() throws Exception {
                JsonObject data = new JsonObject();
                return socketManager.sendRequest(new Message("GET_HISTORY", data));
            }

            @Override
            protected void done() {
                if (!HistoryPanel.this.isDisplayable()) return;
                try {
                    Message response = get();
                    requireResponse(response, "GET_HISTORY_RESPONSE");
                    if (response != null && "GET_HISTORY_RESPONSE".equals(response.getAction())) {
                        java.lang.reflect.Type listType = new TypeToken<List<HistoryEntry>>(){}.getType();
                        List<HistoryEntry> history = gson.fromJson(gson.toJsonTree(response.getData()), listType);
                        tableModel.setRowCount(0);
                        if (history != null) {
                            int stt = 1;
                            for (HistoryEntry entry : history) {
                                tableModel.addRow(new Object[]{stt++, entry.getKeyword(),
                                        "quoc_gia".equals(entry.getType()) ? "Quốc gia" : "Thành phố"});
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    JOptionPane.showMessageDialog(HistoryPanel.this, "Không tải được lịch sử tra cứu: " + VietnameseText.errorMessage(e),
                            "Lỗi tải dữ liệu", JOptionPane.ERROR_MESSAGE);
                } finally {
                    refreshButton.setEnabled(true);
                }
            }
        }.execute();
    }

    private static void requireResponse(Message response, String action) throws java.io.IOException {
        if (response == null) throw new java.io.IOException("Máy chủ không trả lời.");
        if ("ERROR".equals(response.getAction())) {
            throw new java.io.IOException("Máy chủ báo lỗi: " + String.valueOf(response.getData()));
        }
        if (!action.equals(response.getAction())) throw new java.io.IOException("Phản hồi của máy chủ không hợp lệ.");
    }
}
