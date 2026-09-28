package panel;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import model.Message;
import model.User;
import network.ClientSocketManager;

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
        setLayout(new BorderLayout());

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        refreshButton = new JButton("Làm mới lịch sử");
        topPanel.add(refreshButton);
        add(topPanel, BorderLayout.NORTH);

        String[] columns = {"STT", "Từ khóa tìm kiếm"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        historyTable = new JTable(tableModel);
        add(new JScrollPane(historyTable), BorderLayout.CENTER);

        refreshButton.addActionListener(e -> loadHistory());
    }

    public void loadHistory() {
        refreshButton.setEnabled(false);
        new SwingWorker<Message, Void>() {
            @Override
            protected Message doInBackground() throws Exception {
                JsonObject data = new JsonObject();
                data.addProperty("userId", currentUser.getId());
                return socketManager.sendRequest(new Message("GET_HISTORY", data));
            }

            @Override
            protected void done() {
                try {
                    Message response = get();
                    if (response != null && "GET_HISTORY_RESPONSE".equals(response.getAction())) {
                        java.lang.reflect.Type listType = new TypeToken<List<String>>(){}.getType();
                        List<String> history = gson.fromJson(gson.toJsonTree(response.getData()), listType);
                        tableModel.setRowCount(0);
                        if (history != null) {
                            int stt = 1;
                            for (String kw : history) {
                                tableModel.addRow(new Object[]{stt++, kw});
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    refreshButton.setEnabled(true);
                }
            }
        }.execute();
    }
}
