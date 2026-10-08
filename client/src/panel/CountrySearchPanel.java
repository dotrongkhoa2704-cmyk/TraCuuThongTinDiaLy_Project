package panel;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import model.Country;
import model.Message;
import model.User;
import network.ClientSocketManager;
import util.VietnameseText;
import util.InputValidation;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;

public class CountrySearchPanel extends JPanel {
    @FunctionalInterface
    interface RequestSender { Message send(Message request) throws Exception; }
    @FunctionalInterface
    interface FlagLoader { BufferedImage load(String url) throws Exception; }

    private final User currentUser;
    private final RequestSender requestSender;
    private final FlagLoader flagLoader;
    private final Gson gson = new Gson();
    final JTextField searchField = new JTextField(25);
    final JButton searchButton = new JButton("Tra cứu quốc gia");
    final JTextArea resultArea = new JTextArea();
    final JLabel flagLabel = new JLabel("Quốc kỳ sẽ xuất hiện sau khi tra cứu.", SwingConstants.CENTER);
    private final JPanel sourcePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
    SwingWorker<Message, Void> searchWorker;
    SwingWorker<BufferedImage, Void> flagWorker;
    private long searchVersion;
    private boolean removed;

    public CountrySearchPanel(User user, ClientSocketManager socketManager) {
        this(user, socketManager::sendRequest, CountryFlagLoader::load);
    }

    CountrySearchPanel(User user, RequestSender requestSender, FlagLoader flagLoader) {
        this.currentUser = user;
        this.requestSender = requestSender;
        this.flagLoader = flagLoader;
        buildLayout();
    }

    private void buildLayout() {
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        searchPanel.add(new JLabel("Tên quốc gia:"));
        searchPanel.add(searchField);
        searchPanel.add(searchButton);
        add(searchPanel, BorderLayout.NORTH);
        JPanel flagPanel = new JPanel(new BorderLayout());
        flagPanel.setBorder(BorderFactory.createTitledBorder("8. Quốc kỳ"));
        flagLabel.setPreferredSize(new Dimension(270, 180));
        flagPanel.add(flagLabel, BorderLayout.CENTER);
        resultArea.setEditable(false);
        resultArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        resultArea.setLineWrap(true);
        resultArea.setWrapStyleWord(true);
        resultArea.setMargin(new Insets(10, 10, 10, 10));
        JPanel resultPanel = new JPanel(new BorderLayout(8, 8));
        resultPanel.add(flagPanel, BorderLayout.WEST);
        resultPanel.add(new JScrollPane(resultArea), BorderLayout.CENTER);
        add(resultPanel, BorderLayout.CENTER);
        sourcePanel.setBorder(BorderFactory.createTitledBorder("Mở nguồn dữ liệu"));
        sourcePanel.setPreferredSize(new Dimension(0, 74));
        add(sourcePanel, BorderLayout.SOUTH);
        resultArea.setText("Nhập tên quốc gia để xem 12 mục thông tin và hình ảnh quốc kỳ.");
        searchButton.addActionListener(event -> search());
        searchField.addActionListener(event -> search());
    }

    void search() {
        if (removed || !searchButton.isEnabled()) return;
        final String keyword;
        try {
            keyword = InputValidation.keyword(searchField.getText(), "Tên quốc gia", 1, 200);
        } catch (InputValidation.ValidationException exception) {
            cancelWorkers();
            ++searchVersion;
            clearFlag("Chưa có thông tin quốc kỳ cho yêu cầu này.");
            clearSources();
            resultArea.setText(exception.getMessage());
            return;
        }
        cancelWorkers();
        final long version = ++searchVersion;
        clearFlag("Đang chờ thông tin quốc kỳ...");
        clearSources();
        resultArea.setText("Đang tra cứu thông tin quốc gia...");
        searchButton.setEnabled(false);
        searchWorker = new SwingWorker<Message, Void>() {
            @Override
            protected Message doInBackground() throws Exception {
                JsonObject data = new JsonObject();
                data.addProperty("countryName", keyword);
                return requestSender.send(new Message("SEARCH_COUNTRY", data));
            }

            @Override
            protected void done() {
                if (removed || version != searchVersion || isCancelled()) return;
                try {
                    Message response = get();
                    if (response == null) throw new IOException("Máy chủ không trả lời.");
                    if (!"SEARCH_COUNTRY_RESPONSE".equals(response.getAction())) {
                        throw new IOException("ERROR".equals(response.getAction())
                                ? String.valueOf(response.getData()) : "Phản hồi máy chủ không hợp lệ.");
                    }
                    if (response.getData() == null || gson.toJsonTree(response.getData()).isJsonNull()) {
                        resultArea.setText("Không tìm thấy thông tin cho quốc gia: " + keyword);
                        clearFlag("Không có dữ liệu quốc kỳ.");
                        return;
                    }
                    Country country = gson.fromJson(gson.toJsonTree(response.getData()), Country.class);
                    displayCountryInfo(country, version);
                    saveHistory(keyword);
                } catch (Exception error) {
                    resultArea.setText("Không lấy được thông tin quốc gia: " + VietnameseText.errorMessage(error));
                    clearFlag("Không có dữ liệu quốc kỳ.");
                    clearSources();
                } finally {
                    if (!removed && version == searchVersion) searchButton.setEnabled(true);
                }
            }
        };
        searchWorker.execute();
    }

    private void saveHistory(String keyword) {
        if (currentUser == null || removed) return;
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                JsonObject data = new JsonObject();
                data.addProperty("keyword", keyword);
                data.addProperty("type", "quoc_gia");
                requestSender.send(new Message("SAVE_HISTORY", data));
                return null;
            }
        }.execute();
    }

    private void displayCountryInfo(Country country, long version) {
        resultArea.setText(CountryInfoFormatter.format(country));
        resultArea.setCaretPosition(0);
        addSource("Thông tin quốc gia", country.getMetadataSourceUrl());
        addSource("Thể chế", country.getPoliticalSystemSourceUrl());
        addSource("Kinh tế", country.getEconomySourceUrl());
        addSource("GDP", country.getGdpSourceUrl());
        addSource("Khí hậu", country.getClimateSourceUrl());
        String cultureSources = country.getCultureAndTourismSourceUrl();
        if (cultureSources != null && !cultureSources.isBlank()) {
            String[] urls = cultureSources.split("\\R");
            for (int index = 0; index < urls.length; index++) {
                addSource(urls.length == 1 ? "Văn hóa / Du lịch" : index == 0 ? "Văn hóa" : "Du lịch", urls[index]);
            }
        }
        addSource("Quốc kỳ", country.getUrlQuocKy());
        sourcePanel.revalidate();
        sourcePanel.repaint();
        loadFlag(country.getUrlQuocKy(), version);
    }

    private void loadFlag(String url, long version) {
        if (url == null || url.isBlank()) {
            clearFlag("Chưa có nguồn ảnh quốc kỳ.");
            return;
        }
        clearFlag("Đang tải ảnh quốc kỳ...");
        flagWorker = new SwingWorker<BufferedImage, Void>() {
            @Override
            protected BufferedImage doInBackground() throws Exception { return flagLoader.load(url); }

            @Override
            protected void done() {
                if (removed || version != searchVersion || isCancelled()) return;
                try {
                    BufferedImage image = get();
                    if (image == null) throw new IOException("Nguồn quốc kỳ không trả về ảnh.");
                    flagLabel.setIcon(new ImageIcon(image));
                    flagLabel.setText("");
                    flagLabel.setToolTipText("Quốc kỳ từ " + url);
                } catch (Exception error) {
                    clearFlag("Không tải được ảnh quốc kỳ.");
                    flagLabel.setToolTipText(VietnameseText.errorMessage(error));
                }
            }
        };
        flagWorker.execute();
    }

    private void clearFlag(String text) {
        flagLabel.setIcon(null);
        flagLabel.setText(text);
        flagLabel.setToolTipText(null);
    }

    private void clearSources() {
        sourcePanel.removeAll();
        sourcePanel.revalidate();
        sourcePanel.repaint();
    }

    private void addSource(String label, String url) {
        URI address = sourceAddress(url);
        if (address == null) return;
        JButton button = new JButton(label);
        button.setToolTipText(address.toString());
        button.addActionListener(event -> openSource(address));
        sourcePanel.add(button);
    }

    private static URI sourceAddress(String url) {
        if (url == null || url.isBlank()) return null;
        try {
            URI address = URI.create(url.trim());
            return "https".equalsIgnoreCase(address.getScheme()) && address.getHost() != null
                    && address.getRawUserInfo() == null ? address : null;
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    private void openSource(URI address) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(address);
                return;
            }
        } catch (Exception ignored) { }
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(address.toString()), null);
            JOptionPane.showMessageDialog(this, "Đã sao chép đường dẫn. Dán vào trình duyệt để xem nguồn dữ liệu.",
                    "Nguồn dữ liệu", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception error) {
            JOptionPane.showInputDialog(this, "Sao chép đường dẫn và mở trong trình duyệt:", address.toString());
        }
    }

    private void cancelWorkers() {
        if (searchWorker != null) searchWorker.cancel(true);
        if (flagWorker != null) flagWorker.cancel(true);
    }

    @Override
    public void addNotify() {
        removed = false;
        super.addNotify();
    }

    @Override
    public void removeNotify() {
        removed = true;
        ++searchVersion;
        cancelWorkers();
        clearFlag("Đã đóng tra cứu quốc gia.");
        searchButton.setEnabled(true);
        super.removeNotify();
    }
}
