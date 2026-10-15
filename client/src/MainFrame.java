import model.User;
import network.ClientSocketManager;
import panel.CitySearchPanel;
import panel.CountrySearchPanel;
import panel.HistoryPanel;
import panel.SavedLocationsPanel;
import panel.WeatherSubscriptionPanel;
import panel.ComparisonPanel;
import panel.AccountPanel;
import model.Message;
import util.AppTheme;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

public class MainFrame extends JFrame {
    private final User currentUser;
    private final ClientSocketManager socketManager;
    private JTabbedPane tabbedPane;
    private WeatherSubscriptionPanel weatherPanel;
    private JLabel userLabel;
    private boolean closing;

    public MainFrame(User user, ClientSocketManager socketManager) {
        super("Tra cuu thong tin dia ly");
        this.currentUser  = user;
        this.socketManager = socketManager;

        AppTheme.applyGlobalDefaults();

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1100, 740);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent event) { dispose(); }
        });

        buildLayout();
        startAlertListener();
    }

    private void buildLayout() {
        setLayout(new BorderLayout());
        setBackground(AppTheme.BG_MAIN);

        // Header toan cuc
        JPanel header = buildHeader();
        add(header, BorderLayout.NORTH);

        // Tab panel
        tabbedPane = new JTabbedPane(JTabbedPane.TOP);
        tabbedPane.setBackground(AppTheme.BG_MAIN);
        tabbedPane.setFont(AppTheme.FONT_BOLD);

        CitySearchPanel    cityPanel    = new CitySearchPanel(currentUser, socketManager);
        CountrySearchPanel countryPanel = new CountrySearchPanel(currentUser, socketManager);
        SavedLocationsPanel savedPanel  = new SavedLocationsPanel(currentUser, socketManager);
        HistoryPanel       historyPanel = new HistoryPanel(currentUser, socketManager);
        weatherPanel = new WeatherSubscriptionPanel(currentUser, socketManager);
        ComparisonPanel    compPanel    = new ComparisonPanel(socketManager);
        panel.TripPlannerPanel tripPanel = new panel.TripPlannerPanel(socketManager);
        AccountPanel       accountPanel = new AccountPanel(currentUser, socketManager,
                this::refreshUserLabel, this::logout);

        tabbedPane.addTab("  Tim TP  ",   cityPanel);
        tabbedPane.addTab("  Tim QG  ",   countryPanel);
        tabbedPane.addTab("  So sanh  ",  compPanel);
        tabbedPane.addTab("  Lich trinh  ", tripPanel);
        tabbedPane.addTab("  Da luu  ",   savedPanel);
        tabbedPane.addTab("  Lich su  ",  historyPanel);
        tabbedPane.addTab("  Thoi tiet  ", weatherPanel);
        tabbedPane.addTab("  Tai khoan  ", accountPanel);

        tabbedPane.addChangeListener(e -> {
            Component selected = tabbedPane.getSelectedComponent();
            if (selected == historyPanel) historyPanel.loadHistory();
        });

        add(tabbedPane, BorderLayout.CENTER);

        // Status bar duoi cung
        JPanel statusBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 6));
        statusBar.setBackground(AppTheme.BG_HEADER);
        statusBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, AppTheme.PRIMARY_DARK));
        userLabel = new JLabel();
        userLabel.setFont(AppTheme.FONT_SMALL);
        userLabel.setForeground(AppTheme.TEXT_WHITE);
        refreshUserLabel();
        statusBar.add(new JLabel(" "));
        statusBar.add(userLabel);
        add(statusBar, BorderLayout.SOUTH);
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(AppTheme.BG_HEADER);
        header.setBorder(BorderFactory.createEmptyBorder(10, 18, 10, 18));

        JLabel appTitle = new JLabel("BANG DIEU KHIEN - TRA CUU THONG TIN DIA LY");
        appTitle.setFont(AppTheme.FONT_TITLE);
        appTitle.setForeground(AppTheme.TEXT_WHITE);

        JLabel greeting = new JLabel("Chao, " + currentUser.getFullname() + "!");
        greeting.setFont(AppTheme.FONT_BODY);
        greeting.setForeground(new Color(0xD5F5E3));

        JPanel left = new JPanel(new GridLayout(2, 1, 0, 2));
        left.setOpaque(false);
        left.add(appTitle);
        left.add(greeting);
        header.add(left, BorderLayout.WEST);

        return header;
    }

    private void startAlertListener() {
        socketManager.setAlertListener(msg -> {
            if (!closing && isDisplayable() && weatherPanel != null)
                weatherPanel.addAlert(msg);
        });
    }

    private void refreshUserLabel() {
        if (userLabel != null && !closing)
            userLabel.setText("Nguoi dung: " + currentUser.getFullname() + " (" + currentUser.getUsername() + ")");
    }

    private void logout() {
        if (closing) return;
        closing = true;
        socketManager.setAlertListener(null);
        super.dispose();
        new LoginFrame().setVisible(true);
        Timer timeout = new Timer(1500, event -> closeConnection());
        timeout.setRepeats(false);
        timeout.start();
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() {
                try { socketManager.sendRequest(new Message("LOGOUT", null)); } catch (IOException ignored) { }
                finally { closeConnection(); }
                return null;
            }
            @Override protected void done() { timeout.stop(); }
        }.execute();
    }

    private void closeConnection() {
        try { socketManager.close(); } catch (IOException ignored) { }
    }

    @Override public void dispose() {
        closing = true;
        socketManager.setAlertListener(null);
        closeConnection();
        super.dispose();
    }
}