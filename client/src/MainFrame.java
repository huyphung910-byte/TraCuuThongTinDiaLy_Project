import model.User;
import network.ClientSocketManager;
import panel.CitySearchPanel;
import panel.CountrySearchPanel;
import panel.HistoryPanel;
import panel.SavedLocationsPanel;
import panel.WeatherSubscriptionPanel;

import javax.swing.*;
import java.awt.*;
import com.google.gson.Gson;

public class MainFrame extends JFrame {
    private final User currentUser;
    private final ClientSocketManager socketManager;
    private JTabbedPane tabbedPane;
    private WeatherSubscriptionPanel weatherPanel;

    public MainFrame(User user, ClientSocketManager socketManager) {
        super("Tra cứu thông tin địa lý");
        this.currentUser = user;
        this.socketManager = socketManager;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 700);
        setLocationRelativeTo(null);

        buildLayout();
        
        // Start background thread to listen for alerts from server
        startAlertListener();
    }

    private void buildLayout() {
        tabbedPane = new JTabbedPane();
        
        CitySearchPanel cityPanel = new CitySearchPanel(currentUser, socketManager);
        CountrySearchPanel countryPanel = new CountrySearchPanel(currentUser, socketManager);
        SavedLocationsPanel savedPanel = new SavedLocationsPanel(currentUser, socketManager);
        HistoryPanel historyPanel = new HistoryPanel(currentUser, socketManager);
        weatherPanel = new WeatherSubscriptionPanel(currentUser, socketManager);

        tabbedPane.addTab("🏙️ Tra cứu thành phố", cityPanel);
        tabbedPane.addTab("🌍 Tra cứu quốc gia", countryPanel);
        tabbedPane.addTab("❤️ Địa điểm yêu thích", savedPanel);
        tabbedPane.addTab("📋 Lịch sử tra cứu", historyPanel);
        tabbedPane.addTab("🌤️ Theo dõi thời tiết", weatherPanel);

        // Thêm listener để refresh History và SavedLocations khi chuyển tab
        tabbedPane.addChangeListener(e -> {
            Component selected = tabbedPane.getSelectedComponent();
            if (selected == historyPanel) {
                historyPanel.loadHistory();
            }
        });

        setLayout(new BorderLayout());
        add(tabbedPane, BorderLayout.CENTER);

        // Status bar
        JPanel statusBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusBar.add(new JLabel("Người dùng: " + currentUser.getFullname() + " (" + currentUser.getUsername() + ")"));
        add(statusBar, BorderLayout.SOUTH);
    }

    private void startAlertListener() {
        Thread listenerThread = new Thread(() -> {
            try {
                // ClientSocketManager needs a way to expose its reader, but since we are using 
                // a synchronous sendRequest, we can't easily listen asynchronously on the same socket
                // without breaking the request-response flow.
                // In a real application, we'd have a separate connection or a more complex async handler.
                // For this project, we assume sendRequest handles normal traffic, and the server 
                // pushing alerts might interleave.
                // We will add an alert listening mechanism inside ClientSocketManager.
                socketManager.setAlertListener(msg -> {
                    if (weatherPanel != null) {
                        weatherPanel.addAlert(msg);
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        listenerThread.setDaemon(true);
        listenerThread.start();
    }
}
