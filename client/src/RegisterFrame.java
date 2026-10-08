import com.google.gson.JsonObject;

import model.Message;
import network.ClientSocketManager;
import util.InputValidation;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.Arrays;

public class RegisterFrame extends JFrame {

    private final JTextField usernameField;
    private final JPasswordField passwordField;
    private final JPasswordField confirmPasswordField;

    private final JTextField fullnameField;
    private final JTextField emailField;

    private final JButton registerButton;
    private final JButton cancelButton;

    private volatile ClientSocketManager socketManager;
    private SwingWorker<Message, Void> registerWorker;
    private volatile boolean disposed;

    public RegisterFrame() {
        super("Đăng ký tài khoản");

        usernameField = new JTextField(20);

        passwordField = new JPasswordField(20);
        confirmPasswordField = new JPasswordField(20);

        fullnameField = new JTextField(20);
        emailField = new JTextField(20);

        registerButton = new JButton("Đăng ký");
        cancelButton = new JButton("Hủy");

        setDefaultCloseOperation(
                JFrame.DISPOSE_ON_CLOSE
        );

        setResizable(false);

        buildLayout();

        registerButton.addActionListener(
                event -> register()
        );

        cancelButton.addActionListener(
                event -> dispose()
        );
        confirmPasswordField.addActionListener(event -> register());
        emailField.addActionListener(event -> register());

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

        GridBagConstraints gbc =
                new GridBagConstraints();

        gbc.insets =
                new Insets(6, 6, 6, 6);

        gbc.anchor =
                GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.gridy = 0;

        formPanel.add(
                new JLabel("Tên đăng nhập:"),
                gbc
        );

        gbc.gridx = 1;

        formPanel.add(
                usernameField,
                gbc
        );

        gbc.gridx = 0;
        gbc.gridy = 1;

        formPanel.add(
                new JLabel("Mật khẩu:"),
                gbc
        );

        gbc.gridx = 1;

        formPanel.add(
                passwordField,
                gbc
        );

        gbc.gridx = 0;
        gbc.gridy = 2;

        formPanel.add(
                new JLabel("Xác nhận mật khẩu:"),
                gbc
        );

        gbc.gridx = 1;

        formPanel.add(
                confirmPasswordField,
                gbc
        );

        gbc.gridx = 0;
        gbc.gridy = 3;

        formPanel.add(
                new JLabel("Họ tên:"),
                gbc
        );

        gbc.gridx = 1;

        formPanel.add(
                fullnameField,
                gbc
        );

        gbc.gridx = 0;
        gbc.gridy = 4;

        formPanel.add(
                new JLabel("Địa chỉ email:"),
                gbc
        );

        gbc.gridx = 1;

        formPanel.add(
                emailField,
                gbc
        );

        JPanel buttonPanel = new JPanel();

        buttonPanel.add(registerButton);
        buttonPanel.add(cancelButton);

        setLayout(new BorderLayout());

        add(formPanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void register() {
        if (disposed || !registerButton.isEnabled()) return;
        final String username, password, confirmPassword, fullname, email;
        char[] enteredPassword = passwordField.getPassword();
        char[] enteredConfirmation = confirmPasswordField.getPassword();
        try {
            username = InputValidation.username(usernameField.getText());
            password = InputValidation.password(new String(enteredPassword), "Mật khẩu");
            confirmPassword = new String(enteredConfirmation);
            fullname = InputValidation.fullname(fullnameField.getText());
            email = InputValidation.email(emailField.getText());
        } catch (InputValidation.ValidationException exception) {
            JOptionPane.showMessageDialog(
                    this,
                    exception.getMessage(),
                    "Lỗi",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        } finally {
            Arrays.fill(enteredPassword, '\0');
            Arrays.fill(enteredConfirmation, '\0');
        }

        if (!password.equals(confirmPassword)) {
            JOptionPane.showMessageDialog(
                    this,
                    "Mật khẩu xác nhận không khớp.",
                    "Lỗi",
                    JOptionPane.WARNING_MESSAGE
            );

            return;
        }

        setFormEnabled(false);

        registerWorker = new SwingWorker<Message, Void>() {

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
                    throw new IOException("Đã hủy đăng ký.");
                }

                JsonObject data =
                        new JsonObject();

                data.addProperty(
                        "username",
                        username
                );

                data.addProperty(
                        "password",
                        password
                );

                data.addProperty(
                        "fullname",
                        fullname
                );

                data.addProperty(
                        "email",
                        email
                );

                return socketManager.sendRequest(
                        new Message(
                                "REGISTER",
                                data
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
                        JOptionPane.showMessageDialog(RegisterFrame.this,
                                "Máy chủ báo lỗi: " + String.valueOf(response.getData()),
                                "Đăng ký thất bại", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    if (!"REGISTER_RESPONSE".equals(response.getAction())
                            || !(response.getData() instanceof Boolean)) {
                        throw new IOException("Phản hồi đăng ký của máy chủ không hợp lệ.");
                    }

                    if (
                            response != null
                                    && "REGISTER_RESPONSE".equals(
                                            response.getAction()
                                    )
                                    && (Boolean) response.getData()
                    ) {
                        JOptionPane.showMessageDialog(
                                RegisterFrame.this,
                                "Đăng ký thành công! Hãy đăng nhập.",
                                "Thông báo",
                                JOptionPane.INFORMATION_MESSAGE
                        );

                        dispose();

                    } else {
                        JOptionPane.showMessageDialog(
                                RegisterFrame.this,
                                "Đăng ký thất bại. "
                                        + "Tên đăng nhập hoặc địa chỉ email có thể đã tồn tại.",
                                "Lỗi",
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
                            RegisterFrame.this,
                            "Không đăng ký được: "
                                    + util.VietnameseText.errorMessage(exception),
                            "Lỗi kết nối",
                            JOptionPane.ERROR_MESSAGE
                    );

                } finally {
                    registerWorker = null;
                    if (!disposed) setFormEnabled(true);
                }
            }
        };
        registerWorker.execute();
    }

    private void setFormEnabled(boolean enabled) {

        usernameField.setEnabled(enabled);

        passwordField.setEnabled(enabled);
        confirmPasswordField.setEnabled(enabled);

        fullnameField.setEnabled(enabled);
        emailField.setEnabled(enabled);

        registerButton.setEnabled(enabled);
    }

    @Override
    public void dispose() {
        disposed = true;
        if (registerWorker != null) registerWorker.cancel(true);
        passwordField.setText("");
        confirmPasswordField.setText("");
        if (socketManager != null) {
            try {
                socketManager.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        super.dispose();
    }
}
