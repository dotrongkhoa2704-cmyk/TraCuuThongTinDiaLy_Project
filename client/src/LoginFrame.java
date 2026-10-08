import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import model.Message;
import model.User;

import network.ClientSocketManager;
import util.InputValidation;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingWorker;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import java.io.IOException;
import java.util.Arrays;

public class LoginFrame extends JFrame {

    private final JTextField usernameField;
    private final JPasswordField passwordField;

    private final JButton loginButton;
    private final JButton registerButton;

    private final Gson gson;

    private volatile ClientSocketManager socketManager;
    private SwingWorker<Message, Void> loginWorker;
    private volatile boolean disposed;

    public LoginFrame() {
        super("Đăng nhập");

        gson = new Gson();

        usernameField = new JTextField(20);
        passwordField = new JPasswordField(20);

        loginButton = new JButton("Đăng nhập");
        registerButton = new JButton("Đăng ký");

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);

        buildLayout();

        loginButton.addActionListener(
                event -> login()
        );

        registerButton.addActionListener(
                event -> openRegisterFrame()
        );
        usernameField.addActionListener(event -> login());
        passwordField.addActionListener(event -> login());

        pack();
        setLocationRelativeTo(null);
    }

    private void buildLayout() {

        JPanel formPanel =
                new JPanel(new GridBagLayout());

        formPanel.setBorder(
                BorderFactory.createEmptyBorder(
                        20,
                        20,
                        12,
                        20
                )
        );

        GridBagConstraints constraints =
                new GridBagConstraints();

        constraints.insets =
                new Insets(6, 6, 6, 6);

        constraints.anchor =
                GridBagConstraints.WEST;

        constraints.gridx = 0;
        constraints.gridy = 0;

        formPanel.add(
                new JLabel("Tên đăng nhập:"),
                constraints
        );

        constraints.gridx = 1;

        formPanel.add(
                usernameField,
                constraints
        );

        constraints.gridx = 0;
        constraints.gridy = 1;

        formPanel.add(
                new JLabel("Mật khẩu:"),
                constraints
        );

        constraints.gridx = 1;

        formPanel.add(
                passwordField,
                constraints
        );

        JPanel buttonPanel = new JPanel();

        buttonPanel.add(loginButton);
        buttonPanel.add(registerButton);

        setLayout(new BorderLayout());

        add(formPanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void openRegisterFrame() {

        RegisterFrame frame =
                new RegisterFrame();

        frame.setVisible(true);
    }

    private void login() {
        if (disposed || !loginButton.isEnabled()) return;
        final String username;
        final String password;
        char[] enteredPassword = passwordField.getPassword();
        try {
            username = InputValidation.username(usernameField.getText());
            password = InputValidation.password(new String(enteredPassword), "Mật khẩu");
        } catch (InputValidation.ValidationException exception) {
            JOptionPane.showMessageDialog(
                    this,
                    exception.getMessage(),
                    "Thông báo",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        } finally {
            Arrays.fill(enteredPassword, '\0');
        }

        setLoginEnabled(false);

        loginWorker = new SwingWorker<Message, Void>() {

            @Override
            protected Message doInBackground()
                    throws Exception {

                if (socketManager == null) {
                    socketManager =
                            new ClientSocketManager();
                }
                if (disposed || isCancelled()) {
                    ClientSocketManager cancelledConnection = socketManager;
                    socketManager = null;
                    if (cancelledConnection != null) cancelledConnection.close();
                    throw new IOException("Đã hủy đăng nhập.");
                }

                JsonObject loginData =
                        new JsonObject();

                loginData.addProperty(
                        "username",
                        username
                );

                loginData.addProperty(
                        "password",
                        password
                );

                return socketManager.sendRequest(
                        new Message(
                                "LOGIN",
                                loginData
                        )
                );
            }

            @Override
            protected void done() {
                if (disposed || isCancelled()) return;
                try {
                    Message response = get();
                    if (response == null) throw new IOException("Máy chủ không trả lời.");
                    if ("ERROR".equals(response.getAction())) {
                        JOptionPane.showMessageDialog(LoginFrame.this,
                                "Máy chủ báo lỗi: " + String.valueOf(response.getData()),
                                "Đăng nhập thất bại", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    if (!"LOGIN_RESPONSE".equals(response.getAction())) {
                        throw new IOException("Phản hồi đăng nhập của máy chủ không hợp lệ.");
                    }

                    if (
                            response != null
                                    && "LOGIN_RESPONSE".equals(
                                            response.getAction()
                                    )
                                    && hasData(response)
                    ) {
                        User user =
                                gson.fromJson(
                                        gson.toJsonTree(
                                                response.getData()
                                        ),
                                        User.class
                                );

                        JOptionPane.showMessageDialog(
                                LoginFrame.this,
                                "Đăng nhập thành công!",
                                "Thông báo",
                                JOptionPane.INFORMATION_MESSAGE
                        );

                        MainFrame mainFrame =
                                new MainFrame(
                                        user,
                                        socketManager
                                );

                        mainFrame.setVisible(true);

                        // Chuyển quyền sử dụng socket cho MainFrame.
                        socketManager = null;

                        dispose();

                    } else {
                        JOptionPane.showMessageDialog(
                                LoginFrame.this,
                                "Tên đăng nhập hoặc mật khẩu không đúng.",
                                "Đăng nhập thất bại",
                                JOptionPane.ERROR_MESSAGE
                        );
                    }

                } catch (Exception exception) {

                    if (socketManager != null) {
                        try {
                            socketManager.close();
                        } catch (IOException ignored) {
                        }

                        socketManager = null;
                    }

                    JOptionPane.showMessageDialog(
                            LoginFrame.this,
                            "Không đăng nhập được: "
                                    + util.VietnameseText.errorMessage(exception),
                            "Lỗi kết nối",
                            JOptionPane.ERROR_MESSAGE
                    );

                } finally {
                    loginWorker = null;
                    if (!disposed) setLoginEnabled(true);
                }
            }
        };
        loginWorker.execute();
    }

    private boolean hasData(Message response) {

        if (response.getData() == null) {
            return false;
        }

        JsonElement dataElement =
                gson.toJsonTree(
                        response.getData()
                );

        return !dataElement.isJsonNull();
    }

    private void setLoginEnabled(boolean enabled) {

        usernameField.setEnabled(enabled);
        passwordField.setEnabled(enabled);

        loginButton.setEnabled(enabled);
        registerButton.setEnabled(enabled);
    }

    @Override
    public void dispose() {
        disposed = true;
        if (loginWorker != null) loginWorker.cancel(true);
        passwordField.setText("");
        if (socketManager != null) {
            try {
                socketManager.close();
            } catch (IOException exception) {
                exception.printStackTrace();
            }
        }

        super.dispose();
    }
}
