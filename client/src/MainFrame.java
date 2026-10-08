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
        super("Tra cứu thông tin địa lý");
        this.currentUser = user;
        this.socketManager = socketManager;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 700);
        setLocationRelativeTo(null);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent event) { dispose(); }
        });

        buildLayout();
        
        startAlertListener();
    }

    private void buildLayout() {
        tabbedPane = new JTabbedPane();
        
        CitySearchPanel cityPanel = new CitySearchPanel(currentUser, socketManager);
        CountrySearchPanel countryPanel = new CountrySearchPanel(currentUser, socketManager);
        SavedLocationsPanel savedPanel = new SavedLocationsPanel(currentUser, socketManager);
        HistoryPanel historyPanel = new HistoryPanel(currentUser, socketManager);
        weatherPanel = new WeatherSubscriptionPanel(currentUser, socketManager);

        tabbedPane.addTab("Tra cứu thành phố", cityPanel);
        tabbedPane.addTab("Tra cứu quốc gia", countryPanel);
        tabbedPane.addTab("So sánh", new ComparisonPanel(socketManager));
        tabbedPane.addTab("Lịch trình du lịch", new panel.TripPlannerPanel(socketManager));
        tabbedPane.addTab("Địa điểm đã lưu", savedPanel);
        tabbedPane.addTab("Lịch sử tra cứu", historyPanel);
        tabbedPane.addTab("Theo dõi thời tiết", weatherPanel);
        tabbedPane.addTab("Tài khoản", new AccountPanel(currentUser, socketManager,
                this::refreshUserLabel, this::logout));

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
        userLabel = new JLabel();
        refreshUserLabel();
        statusBar.add(userLabel);
        add(statusBar, BorderLayout.SOUTH);
    }

    private void startAlertListener() {
        socketManager.setAlertListener(msg -> {
            if (!closing && isDisplayable() && weatherPanel != null) {
                weatherPanel.addAlert(msg);
            }
        });
    }

    private void refreshUserLabel() {
        if (userLabel != null && !closing) {
            userLabel.setText("Người dùng: " + currentUser.getFullname() + " (" + currentUser.getUsername() + ")");
        }
    }

    private void logout() {
        if (closing) return;
        closing = true;
        socketManager.setAlertListener(null);
        // Đóng giao diện ngay để dừng đồng hồ và các tác vụ của phiên cũ.
        super.dispose();
        new LoginFrame().setVisible(true);

        // Một yêu cầu API cũ có thể đang chờ; giới hạn thời gian giữ kết nối khi đăng xuất.
        Timer timeout = new Timer(1500, event -> closeConnection());
        timeout.setRepeats(false);
        timeout.start();
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() {
                try { socketManager.sendRequest(new Message("LOGOUT", null)); }
                catch (IOException ignored) { /* Đóng TCP cũng kết thúc phiên trên máy chủ. */ }
                finally { closeConnection(); }
                return null;
            }
            @Override protected void done() { timeout.stop(); }
        }.execute();
    }

    private void closeConnection() {
        try { socketManager.close(); }
        catch (IOException ignored) { }
    }

    @Override public void dispose() {
        closing = true;
        socketManager.setAlertListener(null);
        closeConnection();
        super.dispose();
    }
}
