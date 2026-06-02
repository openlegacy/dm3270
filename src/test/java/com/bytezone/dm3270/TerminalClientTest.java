package com.bytezone.dm3270;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bytezone.dm3270.attributes.StartFieldAttribute;
import com.bytezone.dm3270.commands.AIDCommand;
import com.bytezone.dm3270.display.Field;
import com.bytezone.dm3270.display.Screen;
import com.bytezone.dm3270.display.ScreenContext;
import com.bytezone.dm3270.display.ScreenDimensions;
import com.bytezone.dm3270.display.ScreenPosition;
import com.bytezone.dm3270.test.TcpMockServer;
import com.bytezone.dm3270.test.TcpMockServer.MockScenario;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.awt.Point;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
class TerminalClientTest {

    private static final Logger LOG = LoggerFactory.getLogger(TerminalClientTest.class);
    private static final int TERMINAL_MODEL_TYPE_TWO = 2;
    private static final int TERMINAL_MODEL_TYPE_THREE = 3;
    private static final ScreenDimensions SCREEN_DIMENSIONS = new ScreenDimensions(24, 80);
    private static final long TIMEOUT_MILLIS = 10000;
    private static final String SERVICE_HOST = "localhost";
    private static final String APP_NAME = "testapp";
    private static final String USERNAME = "testusr";
    private static final String PASSWORD = "testpsw";

    private TcpMockServer service;
    private TerminalClient client;
    private ExceptionWaiter exceptionWaiter;
    private final ScheduledExecutorService stableTimeoutExecutor =
            Executors.newSingleThreadScheduledExecutor();
    @Mock private Screen screenMock;
    @Mock private ConnectionListener connectionListenerMock;

    @BeforeEach
    void setup() throws Exception {
        service = new TcpMockServer();
        service.setSslEnabled(false);
        startServiceWithScenario(MockScenario.LOGIN);
        client = new TerminalClient(TERMINAL_MODEL_TYPE_TWO, SCREEN_DIMENSIONS);
        client.setConnectionTimeoutMillis(5000);
        exceptionWaiter = new ExceptionWaiter();
        client.addConnectionListener(exceptionWaiter);
        connectClient();
    }

    private static class ExceptionWaiter implements ConnectionListener {

        private final CountDownLatch exceptionLatch = new CountDownLatch(1);

        private final CountDownLatch closeLatch = new CountDownLatch(1);

        @Override
        public void onConnection() {}

        @Override
        public void onException(Exception ex) {
            exceptionLatch.countDown();
        }

        @Override
        public void onConnectionClosed() {
            closeLatch.countDown();
        }

        private void awaitException() throws InterruptedException {
            assertThat(exceptionLatch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isTrue();
        }

        private void awaitClose() throws InterruptedException {
            assertThat(closeLatch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isTrue();
        }
    }

    private void connectClient() {
        client.connect(SERVICE_HOST, service.getPort());
        client.addScreenChangeListener(
                screenWatcher ->
                        LOG.debug(
                                "Screen updated, cursor={}, alarm={}, screen:{}",
                                client.getCursorPosition().orElse(null),
                                client.isAlarmOn(),
                                getScreenText()));
    }

    private String getScreenText() {
        return client.getScreenText().replace('\u0000', ' ');
    }

    private void startServiceWithScenario(MockScenario scenario) throws Exception {
        service.setScenario(scenario);
        service.start();
    }

    private String getResourceFilePath(String resourcePath) {
        return getClass().getResource(resourcePath).getFile();
    }

    @AfterEach
    void teardown() throws Exception {
        client.disconnect();
        service.stop(TIMEOUT_MILLIS);
    }

    @Test
    void shouldGetUnlockedKeyboardWhenConnect() throws Exception {
        awaitKeyboardUnlock();
        assertThat(client.isKeyboardLocked()).isFalse();
    }

    private void awaitKeyboardUnlock() throws InterruptedException, TimeoutException {
        new UnlockWaiter(client, stableTimeoutExecutor).await(TIMEOUT_MILLIS);
    }

    @Test
    void shouldGetWelcomeScreenWhenConnect() throws Exception {
        awaitKeyboardUnlock();
        assertThat(getScreenText()).isEqualTo(getWelcomeScreen());
    }

    private String getWelcomeScreen() throws IOException {
        return getFileContent("login-welcome-screen.txt");
    }

    private String getFileContent(String resourceFile) throws IOException {
        return Resources.toString(Resources.getResource(resourceFile), Charsets.UTF_8);
    }

    @Test
    void shouldGetWelcomeScreenWithWrongCharset() throws Exception {
        setupExtendedFlow(
                TERMINAL_MODEL_TYPE_THREE,
                SCREEN_DIMENSIONS,
                MockScenario.LOGIN_SPECIAL_CHARACTERS);

        awaitKeyboardUnlock();
        assertThat(getScreenText())
                .isEqualTo(getFileContent("login-special-character-charset-CP1047.txt"));
    }

    @Disabled("Smth wrong with mocks")
    @Test
    void shouldGetWelcomeScreenWithRightCharset() throws Exception {
        cleanShutdown();
        startServiceWithScenario(MockScenario.LOGIN_SPECIAL_CHARACTERS);
        client = new TerminalClient(TERMINAL_MODEL_TYPE_TWO, SCREEN_DIMENSIONS, Charset.CP1147);
        client.setUsesExtended3270(true);
        connectClient();
        awaitKeyboardUnlock();
        assertThat(getScreenText())
                .isEqualTo(getFileContent("login-special-character-charset-CP1147.txt"));
    }

    private void cleanShutdown() throws Exception {
        awaitKeyboardUnlock();
        teardown();
    }

    @Test
    void shouldGetWelcomeScreenWhenConnectWithSsl() throws Exception {
        setupSslConnection();
        awaitKeyboardUnlock();
        assertThat(getScreenText()).isEqualTo(getWelcomeScreen());
    }

    private void setupSslConnection() throws Exception {
        cleanShutdown();

        service.setSslEnabled(true);
        service.setScenario(MockScenario.LOGIN);
        System.setProperty("javax.net.ssl.keyStore", getResourceFilePath("/keystore.jks"));
        System.setProperty("javax.net.ssl.keyStorePassword", "changeit");
        service.start();

        client = new TerminalClient(TERMINAL_MODEL_TYPE_TWO, SCREEN_DIMENSIONS);
        client.setSocketFactory(buildSslSocketFactory());
        exceptionWaiter = new ExceptionWaiter();
        client.addConnectionListener(exceptionWaiter);
        connectClient();
    }

    private SSLContext buildSslContext() throws GeneralSecurityException {
        SSLContext sslContext = SSLContext.getInstance("TLS", "SunJSSE");
        TrustManager trustManager =
                new X509TrustManager() {

                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }

                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}

                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                };
        sslContext.init(null, new TrustManager[] {trustManager}, new SecureRandom());
        return sslContext;
    }

    // Sniffy's SSL socket factory (wrapping the SunJSSE context) does not implement
    // createSocket() with no arguments — it throws UnsupportedOperationException after
    // accumulating state from prior test connections. DelayedSslSocket defers the actual
    // SSL socket creation to connect() time using createSocket(host, port), which Sniffy
    // does support, so the full-suite SSL test passes reliably.
    private SocketFactory buildSslSocketFactory() throws GeneralSecurityException {
        SSLContext ctx = buildSslContext();
        return new SocketFactory() {
            @Override
            public Socket createSocket() throws IOException {
                return new DelayedSslSocket(ctx);
            }

            @Override
            public Socket createSocket(String host, int port)
                    throws IOException, UnknownHostException {
                return ctx.getSocketFactory().createSocket(host, port);
            }

            @Override
            public Socket createSocket(String host, int port, InetAddress localAddr, int localPort)
                    throws IOException, UnknownHostException {
                return ctx.getSocketFactory().createSocket(host, port, localAddr, localPort);
            }

            @Override
            public Socket createSocket(InetAddress addr, int port) throws IOException {
                return ctx.getSocketFactory().createSocket(addr, port);
            }

            @Override
            public Socket createSocket(
                    InetAddress addr, int port, InetAddress localAddr, int localPort)
                    throws IOException {
                return ctx.getSocketFactory().createSocket(addr, port, localAddr, localPort);
            }
        };
    }

    private SSLContext buildTls12SslContext() throws GeneralSecurityException {
        SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
        TrustManager trustManager =
                new X509TrustManager() {

                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }

                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        // Accept all client certificates (untrusted)
                    }

                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        // Accept all server certificates (untrusted)
                    }
                };
        sslContext.init(null, new TrustManager[] {trustManager}, new SecureRandom());
        return sslContext;
    }

    @Test
    void shouldGetWelcomeScreenWhenConnectWithScreenWithExtendFieldWithoutFieldAttribute()
            throws Exception {
        cleanShutdown();
        startServiceWithScenario(MockScenario.LOGIN_EXTENDED_FIELD_WITHOUT_FIELD_ATTRIBUTE);
        client = new TerminalClient(TERMINAL_MODEL_TYPE_TWO, SCREEN_DIMENSIONS);
        connectClient();
        awaitKeyboardUnlock();
        assertThat(getScreenText()).isEqualTo(getWelcomeScreen());
    }

    @Test
    void shouldGetUserMenuScreenWhenSendUserFieldByCoord() throws Exception {
        awaitKeyboardUnlock();
        sendUserFieldByCoord();
        awaitKeyboardUnlock();
        assertThat(getScreenText()).isEqualTo(getUserMenuScreen());
    }

    private void sendUserFieldByCoord() {
        sendFieldByCoord(1, 27, USERNAME);
    }

    private void sendFieldByCoord(int row, int column, String text) {
        client.setFieldTextByCoord(row, column, text);
        sendEnter();
    }

    private String getUserMenuScreen() throws IOException {
        return getFileContent("user-menu-screen.txt");
    }

    private void sendEnter() {
        client.sendAID(AIDCommand.AID_ENTER, "ENTER");
    }

    @Test
    void shouldGetLoginSuccessScreenWhenSendPasswordFieldByProtectedLabel() throws Exception {
        awaitKeyboardUnlock();
        sendUserFieldByCoord();
        awaitKeyboardUnlock();
        sendFieldByLabel("Password", PASSWORD);
        awaitSuccessScreen();
    }

    private void sendFieldByLabel(String label, String text) {
        client.setFieldTextByLabel(label, text);
        sendEnter();
    }

    private void awaitSuccessScreen() throws InterruptedException, TimeoutException {
        new ScreenTextWaiter("READY", client, stableTimeoutExecutor).await(TIMEOUT_MILLIS);
    }

    @Test
    void shouldGetUserMenuScreenWhenSendUserFieldByUnprotectedLabel() throws Exception {
        awaitKeyboardUnlock();
        sendFieldByLabel("ENTER USERID", USERNAME);
        awaitKeyboardUnlock();
        assertThat(getScreenText()).isEqualTo(getUserMenuScreen());
    }

    @Test
    void shouldGetWelcomeMessageWhenSendUserInScreenWithoutFields() throws Exception {
        setupExtendedFlow(
                TERMINAL_MODEL_TYPE_TWO, SCREEN_DIMENSIONS, MockScenario.LOGIN_WITHOUT_FIELDS);
        awaitKeyboardUnlock();
        sendFieldByCoord(20, 48, USERNAME);
        awaitKeyboardUnlock();
        sendFieldByCoord(1, 1, USERNAME);
        awaitKeyboardUnlock();
    }

    private void setupExtendedFlow(
            int terminalType, ScreenDimensions screenDimensions, MockScenario scenario)
            throws Exception {
        cleanShutdown();
        startServiceWithScenario(scenario);
        client = new TerminalClient(terminalType, screenDimensions);
        client.setUsesExtended3270(true);
        connectClient();
    }

    @Test
    void shouldGetNotSoundedAlarmWhenWhenConnect() throws Exception {
        awaitKeyboardUnlock();
        assertThat(client.resetAlarm()).isFalse();
    }

    @Test
    void shouldGetSoundedAlarmWhenWhenSendUserField() throws Exception {
        awaitKeyboardUnlock();
        sendUserFieldByCoord();
        awaitKeyboardUnlock();
        assertThat(client.resetAlarm()).isTrue();
    }

    @Test
    void shouldGetNotSoundedAlarmWhenWhenSendUserFieldAndResetAlarm() throws Exception {
        awaitKeyboardUnlock();
        sendUserFieldByCoord();
        awaitKeyboardUnlock();
        client.resetAlarm();
        assertThat(client.resetAlarm()).isFalse();
    }

    @Test
    void shouldGetFieldPositionWhenGetCursorPositionAfterConnect() throws Exception {
        Point fieldPosition = new Point(1, 2);
        awaitCursorPosition(fieldPosition);
        assertThat(client.getCursorPosition()).isEqualTo(Optional.of(fieldPosition));
    }

    private void awaitCursorPosition(Point position) throws InterruptedException, TimeoutException {
        CountDownLatch latch = new CountDownLatch(1);
        client.addCursorMoveListener(
                (newPos, oldPos, field) -> {
                    if (position.equals(client.getCursorPosition().orElse(null))) {
                        latch.countDown();
                    }
                });
        if (!client.isKeyboardLocked()) {
            latch.countDown();
        }
        if (!latch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
            throw new TimeoutException();
        }
    }

    @Test
    void shouldSendExceptionToExceptionHandlerWhenConnectWithInvalidPort() throws Exception {
        client.connect(SERVICE_HOST, 1);
        exceptionWaiter.awaitException();
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenSendIncorrectFieldPosition() throws Exception {
        awaitKeyboardUnlock();
        assertThatThrownBy(() -> client.setFieldTextByCoord(0, 1, "test"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldSendCloseToExceptionHandlerWhenServerDown() throws Exception {
        awaitKeyboardUnlock();
        service.stop(TIMEOUT_MILLIS);
        exceptionWaiter.awaitClose();
    }

    @Test
    void shouldSendExceptionToExceptionHandlerWhenSendAndServerDown() throws Exception {
        awaitKeyboardUnlock();
        service.closeActiveClients();
        sendUserFieldByCoord();
        exceptionWaiter.awaitException();
    }

    @Test
    void shouldGetLoginSuccessScreenWhenLoginWithSscpLuData() throws Exception {
        setupSscpLuLoginFlow();
        awaitKeyboardUnlock();
        sendFieldByCoord(11, 25, APP_NAME);
        awaitKeyboardUnlock();
        client.setFieldTextByCoord(12, 21, USERNAME);
        client.setFieldTextByCoord(13, 21, PASSWORD);
        sendEnterAndWaitKeyboardUnlock();
        assertThat(getScreenText()).isEqualTo(getSccpLuLoginSuccessScreen());
    }

    private void setupSscpLuLoginFlow() throws Exception {
        setupExtendedFlow(TERMINAL_MODEL_TYPE_TWO, SCREEN_DIMENSIONS, MockScenario.SSCPLU_LOGIN);
    }

    private String getSccpLuLoginSuccessScreen() throws IOException {
        return getFileContent("sscplu-login-success-screen.txt");
    }

    @Test
    void shouldGetCorrectFieldsWhenGetFields() throws Exception {
        when(screenMock.validate(anyInt()))
                .thenAnswer(
                        (Answer<Integer>)
                                invocationOnMock -> (Integer) invocationOnMock.getArguments()[0]);
        awaitKeyboardUnlock();
        sendUserFieldByCoord();
        awaitKeyboardUnlock();
        assertEquals(expectedFields(), client.getFields());
    }

    private List<Field> expectedFields() {
        ScreenBuilder screenBuilder =
                new ScreenBuilder()
                        .withField(
                                new FieldBuilder(
                                                "------------------------------- TSO/E LOGON ----------------"
                                                        + "-------------------")
                                        .withHighIntensity()
                                        .withSelectorPenDetectable())
                        .withField(
                                new FieldBuilder(
                                                "                                                             "
                                                        + "                  ")
                                        .withHighIntensity()
                                        .withSelectorPenDetectable())
                        .withField(
                                new FieldBuilder(
                                                "                                                             "
                                                        + "                   \u0000\u0000")
                                        .withHighIntensity()
                                        .withSelectorPenDetectable())
                        .withField(
                                new FieldBuilder(
                                                "Enter LOGON parameters below:"
                                                        + buildNullString(18))
                                        .withHighIntensity()
                                        .withSelectorPenDetectable())
                        .withField(
                                new FieldBuilder("RACF LOGON parameters:" + buildNullString(88))
                                        .withHighIntensity()
                                        .withSelectorPenDetectable())
                        .withField(new FieldBuilder(" Userid    ===>"))
                        .withField(new FieldBuilder("TESTUSR ").withHighIntensity())
                        .withField(new FieldBuilder(buildNullString(22)).withNumeric())
                        .withField(
                                new FieldBuilder(" Seclabel     ===>").withNumeric().withHidden())
                        .withField(new FieldBuilder("        ").withNumeric().withHidden())
                        .withField(new FieldBuilder(buildNullString(83)).withNumeric())
                        .withField(new FieldBuilder(" Password  ===>"))
                        .withField(new FieldBuilder("        ").withNotProtected().withHidden())
                        .withField(new FieldBuilder(buildNullString(22)).withNumeric())
                        .withField(new FieldBuilder(" New Password ===>"))
                        .withField(new FieldBuilder("        ").withNotProtected().withHidden())
                        .withField(new FieldBuilder(buildNullString(83)).withNumeric())
                        .withField(new FieldBuilder(" Procedure ===>"))
                        .withField(
                                new FieldBuilder("PROC000 ")
                                        .withNotProtected()
                                        .withHighIntensity()
                                        .withSelectorPenDetectable())
                        .withField(new FieldBuilder(buildNullString(22)).withNumeric())
                        .withField(new FieldBuilder(" Group Ident  ===>"))
                        .withField(
                                new FieldBuilder("        ")
                                        .withNotProtected()
                                        .withHighIntensity()
                                        .withSelectorPenDetectable())
                        .withField(new FieldBuilder(buildNullString(83)).withNumeric())
                        .withField(new FieldBuilder(" Acct Nmbr ===>"))
                        .withField(
                                new FieldBuilder("1000000                                 ")
                                        .withNotProtected()
                                        .withHighIntensity()
                                        .withSelectorPenDetectable())
                        .withField(new FieldBuilder(buildNullString(102)).withNumeric())
                        .withField(new FieldBuilder(" Size      ===>"))
                        .withField(
                                new FieldBuilder("4096   ")
                                        .withNotProtected()
                                        .withHighIntensity()
                                        .withSelectorPenDetectable())
                        .withField(new FieldBuilder(buildNullString(135)).withNumeric())
                        .withField(new FieldBuilder(" Perform   ===>"))
                        .withField(
                                new FieldBuilder("   ")
                                        .withNotProtected()
                                        .withHighIntensity()
                                        .withSelectorPenDetectable())
                        .withField(new FieldBuilder(buildNullString(139)).withNumeric())
                        .withField(new FieldBuilder(" Command   ===>"))
                        .withField(
                                new FieldBuilder(
                                                "                                                            "
                                                        + "                    ")
                                        .withNotProtected()
                                        .withHighIntensity()
                                        .withSelectorPenDetectable())
                        .withField(new FieldBuilder(buildNullString(63)).withNumeric())
                        .withField(
                                new FieldBuilder("Enter an 'S' before each option desired below:")
                                        .withHighIntensity()
                                        .withSelectorPenDetectable())
                        .withField(new FieldBuilder(buildNullString(36)))
                        .withField(
                                new FieldBuilder(" ")
                                        .withHighIntensity()
                                        .withSelectorPenDetectable())
                        .withField(
                                new FieldBuilder(" ")
                                        .withNotProtected()
                                        .withHighIntensity()
                                        .withSelectorPenDetectable())
                        .withField(new FieldBuilder("-Nomail").withNumeric())
                        .withField(new FieldBuilder("\u0000\u0000\u0000"))
                        .withField(
                                new FieldBuilder(" ")
                                        .withHighIntensity()
                                        .withSelectorPenDetectable())
                        .withField(
                                new FieldBuilder(" ")
                                        .withNotProtected()
                                        .withHighIntensity()
                                        .withSelectorPenDetectable())
                        .withField(new FieldBuilder("-Nonotice").withNumeric())
                        .withField(new FieldBuilder("\u0000\u0000"))
                        .withField(
                                new FieldBuilder(" ")
                                        .withHighIntensity()
                                        .withSelectorPenDetectable())
                        .withField(
                                new FieldBuilder(" ")
                                        .withNotProtected()
                                        .withHighIntensity()
                                        .withSelectorPenDetectable())
                        .withField(new FieldBuilder("-Reconnect").withNumeric())
                        .withField(new FieldBuilder("\u0000\u0000"))
                        .withField(
                                new FieldBuilder(" ")
                                        .withHighIntensity()
                                        .withSelectorPenDetectable())
                        .withField(
                                new FieldBuilder(" ")
                                        .withNotProtected()
                                        .withHighIntensity()
                                        .withSelectorPenDetectable())
                        .withField(new FieldBuilder("-OIDcard ").withNumeric())
                        .withField(new FieldBuilder(buildNullString(87)))
                        .withField(
                                new FieldBuilder(
                                                "PF1/PF13 ==> Help    PF3/PF15 ==> Logoff    PA1 ==> Attention"
                                                        + "    PA2 ==> Reshow")
                                        .withHighIntensity()
                                        .withSelectorPenDetectable())
                        .withField(
                                new FieldBuilder(
                                                "You may request specific help information by entering a '?' in"
                                                        + " any entry field\u0000")
                                        .withHighIntensity()
                                        .withSelectorPenDetectable());
        return screenBuilder.build();
    }

    private String buildNullString(int count) {
        return new String(new char[count]);
    }

    private static final class ScreenBuilder {

        private final List<Field> fields = new ArrayList<>();

        private Field lastField = null;

        private ScreenBuilder withField(FieldBuilder builder) {
            Field currField =
                    lastField != null
                            ? builder.withStartPosition(
                                            lastField.getFirstLocation()
                                                    + lastField.getText().length())
                                    .build()
                            : builder.build();
            fields.add(currField);
            lastField = currField;
            return this;
        }

        private List<Field> build() {
            return fields;
        }
    }

    private final class FieldBuilder {

        private int startPosition;

        private final String text;
        private boolean isProtected = true;
        private boolean isNumeric = false;
        private boolean isHidden = false;
        private boolean isHighIntensity = false;
        private boolean isModified = false;
        private boolean selectorPenDetectable = false;

        private FieldBuilder(String text) {
            this.text = text;
        }

        private FieldBuilder withStartPosition(int pos) {
            this.startPosition = pos;
            return this;
        }

        private FieldBuilder withNotProtected() {
            isProtected = false;
            return this;
        }

        private FieldBuilder withNumeric() {
            isNumeric = true;
            return this;
        }

        private FieldBuilder withHidden() {
            isHidden = true;
            return this;
        }

        private FieldBuilder withModified() {
            isModified = true;
            return this;
        }

        private FieldBuilder withHighIntensity() {
            isHighIntensity = true;
            return this;
        }

        private FieldBuilder withSelectorPenDetectable() {
            selectorPenDetectable = true;
            return this;
        }

        private Field build() {
            try {
                List<ScreenPosition> positions = new ArrayList<>();
                for (int i = 0; i <= text.length(); i++) {
                    positions.add(
                            new ScreenPosition(
                                    startPosition + i,
                                    ScreenContext.DEFAULT_CONTEXT,
                                    Charset.CP1047));
                }
                positions.get(0).setStartField(buildStartFieldAttribute());
                Field f = new Field(TerminalClientTest.this.screenMock, positions);
                f.setText(text.getBytes(Charset.CP1047.name()));
                return f;
            } catch (UnsupportedEncodingException e) {
                // As this is not expected to happen, we just throw RuntimeException.
                throw new RuntimeException(e);
            }
        }

        private StartFieldAttribute buildStartFieldAttribute() {
            byte b = 0;
            if (isProtected) {
                b |= 0x20;
            }
            if (isNumeric) {
                b |= 0x10;
            }
            if (isModified) {
                b |= 0x01;
            }
            if (isHighIntensity) {
                b |= 0x08;
            } else if (isHidden) {
                b |= 0x0C;
            } else if (selectorPenDetectable) {
                b |= 0x04;
            }
            return new StartFieldAttribute(b);
        }
    }

    @Test
    void shouldShowMenuScreenWithDifferentTerminalType() throws Exception {
        int terminalType = 5;
        setupExtendedFlow(
                terminalType, new ScreenDimensions(27, 132), MockScenario.LOGIN_3270_MODEL_5);
        awaitKeyboardUnlock();
        sendUserFieldByCoord();
        awaitKeyboardUnlock();
        assertThat(getScreenText()).isEqualTo(getFileContent("user-menu-for-screen-type-5.txt"));
    }

    @Test
    void shouldSetTextWhenNoScreenFieldsWhileInputByLabel() throws Exception {
        setupSscpLuLoginFlow();
        awaitKeyboardUnlock();
        sendFieldByLabel("APPLICATION NAME", APP_NAME);
        awaitKeyboardUnlock();
        assertThat(getScreenText()).isEqualTo(getFileContent("sscplu-login-middle-screen"));
    }

    @Test
    void shouldGetLoginSuccessScreenWhenEmptyInputByCord() throws Exception {
        setupFlowWithEmptyField();
        awaitKeyboardUnlock();
        sendFieldByCoord(1, 27, "");
        awaitKeyboardUnlock();
        assertThat(getScreenText()).isEqualTo(getUserMenuScreen());
    }

    private void setupFlowWithEmptyField() throws Exception {
        cleanShutdown();
        startServiceWithScenario(MockScenario.LOGIN_3270_EMPTY_FIELD);
        client = new TerminalClient(TERMINAL_MODEL_TYPE_TWO, SCREEN_DIMENSIONS, Charset.CP1147);
        connectClient();
    }

    @Test
    void shouldSendTabulatorInput() throws Exception {
        setupSscpLuLoginFlow();
        awaitKeyboardUnlock();
        sendFieldByTab(APP_NAME, 0);
        sendEnterAndWaitKeyboardUnlock();
        sendFieldByTab(USERNAME, 0);
        sendFieldByTab(PASSWORD, 1);
        sendEnterAndWaitKeyboardUnlock();
        assertThat(getScreenText()).isEqualTo(getSccpLuLoginSuccessScreen());
    }

    void sendFieldByTab(String text, int offset) throws NoSuchFieldException {
        client.setTabulatedInput(text, offset);
    }

    @Test
    void shouldSetTabulatorInputWhenCursorPosLacksFieldAndOffsetBiggerThanZero() throws Exception {
        awaitKeyboardUnlock();
        sendFieldByCoord(1, 27, "testusr");
        sendEnterAndWaitKeyboardUnlock();
        client.setCursorPosition(50);
        sendFieldByTab("testpsw", 1);
        sendEnterAndWaitKeyboardUnlock();
    }

    @Test
    void shouldGetSuccessScreenWhenUsingMultipleInputByLabel() throws Exception {
        setupExtendedFlow(TERMINAL_MODEL_TYPE_TWO, SCREEN_DIMENSIONS, MockScenario.LOGIN_3278_M2_E);
        awaitKeyboardUnlock();
        client.setFieldTextByLabel("Userid:", "testusr ");
        client.setFieldTextByLabel("Passcode:", "testpsw");
        sendEnterAndWaitKeyboardUnlock();
        assertThat(getFileContent("login-3278-M2-E-final-screen.txt")).isEqualTo(getScreenText());
    }

    @Test
    void shouldSuccessfullyLoginWhenAplScreen() throws Exception {
        setupExtendedFlow(
                TERMINAL_MODEL_TYPE_TWO, SCREEN_DIMENSIONS, MockScenario.LOGIN_APL_CHARSET_SCREEN);
        awaitKeyboardUnlock();
        sendFieldByTab("TESTUSR", 0);
        sendFieldByTab("TESTPSW", 1);
        sendEnterAndWaitKeyboardUnlock();
        assertThat(getScreenText()).isEqualTo(getFileContent("success-apl-screen.txt"));
    }

    @Test
    void shouldSetFieldsWhenFieldsNotHaveStartAttribute() throws Exception {
        setupExtendedFlow(
                TERMINAL_MODEL_TYPE_TWO,
                SCREEN_DIMENSIONS,
                MockScenario.FIELD_WITHOUT_START_ATTRIBUTE);
        awaitKeyboardUnlock();
        sendFieldByTab("TESTUSR", 0);
        sendFieldByTab("TESTPSW", 2);
        sendEnterAndWaitKeyboardUnlock();
        assertThat(getScreenText())
                .isEqualTo(getFileContent("field_without_start_attribute_expected_screen.txt"));
    }

    @Test
    void shouldConnectCorrectlyWhenQueryListEquivalentPlusQCODE() throws Exception {
        setupExtendedFlow(
                TERMINAL_MODEL_TYPE_TWO, SCREEN_DIMENSIONS, MockScenario.TEST_CAPABILITIES);
        awaitKeyboardUnlock();
        assertThat(getScreenText())
                .isEqualTo(getFileContent("field_without_start_attribute_expected_screen.txt"));
    }

    @Test
    void shouldNotFailWhenFieldAttributeIsNotRecognised() throws Exception {
        setupExtendedFlow(
                TERMINAL_MODEL_TYPE_TWO, SCREEN_DIMENSIONS, MockScenario.ATTRIBUTE_NOT_PRESENT);
        awaitKeyboardUnlock();
        sendFieldByTab("1", 0);
        sendEnterAndWaitKeyboardUnlock();
        assertThat(getScreenText())
                .isEqualTo(getFileContent("attribute_not_present_expected_screen.txt"));
    }

    private void sendEnterAndWaitKeyboardUnlock() throws TimeoutException, InterruptedException {
        sendEnter();
        awaitKeyboardUnlock();
    }

    private static final class DelayedSslSocket extends Socket {
        private final SSLContext sslContext;
        private SSLSocket sslSocket;

        DelayedSslSocket(SSLContext ctx) {
            this.sslContext = ctx;
        }

        @Override
        public void connect(SocketAddress endpoint, int timeout) throws IOException {
            InetSocketAddress addr = (InetSocketAddress) endpoint;
            sslSocket =
                    (SSLSocket)
                            sslContext
                                    .getSocketFactory()
                                    .createSocket(addr.getHostName(), addr.getPort());
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return sslSocket.getInputStream();
        }

        @Override
        public OutputStream getOutputStream() throws IOException {
            return sslSocket.getOutputStream();
        }

        @Override
        public void close() throws IOException {
            if (sslSocket != null) {
                sslSocket.close();
            } else {
                super.close();
            }
        }

        @Override
        public boolean isConnected() {
            return sslSocket != null && sslSocket.isConnected();
        }

        @Override
        public boolean isClosed() {
            return sslSocket == null ? super.isClosed() : sslSocket.isClosed();
        }

        @Override
        public boolean isInputShutdown() {
            return sslSocket == null ? super.isInputShutdown() : sslSocket.isInputShutdown();
        }
    }

    @Test
    void shouldNotNotifyServerDisconnectionWhenClientDisconnect() throws Exception {
        awaitKeyboardUnlock();
        client.addConnectionListener(connectionListenerMock);
        client.disconnect();
        verify(connectionListenerMock, never()).onConnectionClosed();
    }

    @Disabled("Requires connectivity to mainframe.openlegacy.com")
    @Test
    void shouldConnectToOpenLegacyMainframeAndSendCICS61() throws Exception {
        // Clean up the mock service connection first
        cleanShutdown();
        service.stop(TIMEOUT_MILLIS);

        // Create a new client for the real mainframe connection
        client = new TerminalClient(TERMINAL_MODEL_TYPE_TWO, SCREEN_DIMENSIONS);
        client.setConnectionTimeoutMillis(10000);
        exceptionWaiter = new ExceptionWaiter();
        client.addConnectionListener(exceptionWaiter);

        // Connect to the real mainframe at mainframe.openlegacy.com
        client.connect("mainframe.openlegacy.com", 623);

        // Add screen change listener for debugging
        client.addScreenChangeListener(
                screenWatcher ->
                        LOG.debug(
                                "Screen updated from OpenLegacy, cursor={}, alarm={}, screen:{}",
                                client.getCursorPosition().orElse(null),
                                client.isAlarmOn(),
                                getScreenText()));

        // Wait for the initial screen to load and keyboard to unlock
        awaitKeyboardUnlock();

        // First screen: Send CICS61 at position 24,2 (row 24, column 2)
        client.setFieldTextByCoord(24, 2, "CICS61");

        // Send ENTER key
        sendEnter();

        // Wait for the response
        awaitKeyboardUnlock();

        // Log the first screen response
        String firstScreenText = getScreenText();
        LOG.info("Screen after sending CICS61: {}", firstScreenText);

        // Verify that we got a response (the screen should have changed)
        assertThat(firstScreenText).isNotEmpty();

        // Second screen: Send username and password
        String username = System.getProperty("ol.mf.user", "XXXX");
        String password = System.getProperty("ol.mf.password", "YYYYYYY");

        // Send username at position 10,26
        client.setFieldTextByCoord(10, 26, username);

        // Send password at position 11,26
        client.setFieldTextByCoord(11, 26, password);

        // Send ENTER key
        sendEnter();

        // Wait for the response
        awaitKeyboardUnlock();

        // Log the second screen response
        String secondScreenText = getScreenText();
        LOG.info("Screen after sending credentials: {}", secondScreenText);

        // Verify that we got a response after login
        assertThat(secondScreenText).isNotEmpty();
        assertThat(secondScreenText).isNotEqualTo(firstScreenText);

        // Third screen: Send CC00 command
        // Send "CC00" at position 1,5
        client.setFieldTextByCoord(1, 5, "CC00");

        // Send ENTER key
        sendEnter();

        // Wait for the response
        awaitKeyboardUnlock();

        // Log the third screen response
        String thirdScreenText = getScreenText();
        LOG.info("Screen after sending CC00 command: {}", thirdScreenText);

        // Verify that we got a response after the CC00 command
        assertThat(thirdScreenText).isNotEmpty();
        assertThat(thirdScreenText).isNotEqualTo(secondScreenText);

        // Clean up - disconnect from the real mainframe
        client.disconnect();
    }

    @Disabled("Requires connectivity to mainframe.openlegacy.com")
    @Test
    void shouldConnectToOpenLegacyMainframeViaTls12AndSendCICS61() throws Exception {
        // Clean up the mock service connection first
        cleanShutdown();
        service.stop(TIMEOUT_MILLIS);

        // Create a new client for the real mainframe connection with TLS 1.2
        client = new TerminalClient(TERMINAL_MODEL_TYPE_TWO, SCREEN_DIMENSIONS);
        client.setConnectionTimeoutMillis(10000);
        client.setSocketFactory(buildTls12SslContext().getSocketFactory());
        exceptionWaiter = new ExceptionWaiter();
        client.addConnectionListener(exceptionWaiter);

        // Connect to the real mainframe at mainframe.openlegacy.com via TLS port 629
        client.connect("mainframe.openlegacy.com", 629);

        // Add screen change listener for debugging
        client.addScreenChangeListener(
                screenWatcher ->
                        LOG.debug(
                                "Screen updated from OpenLegacy TLS, cursor={}, alarm={}, screen:{}",
                                client.getCursorPosition().orElse(null),
                                client.isAlarmOn(),
                                getScreenText()));

        // Wait for the initial screen to load and keyboard to unlock
        awaitKeyboardUnlock();

        // First screen: Send CICS61 at position 24,2 (row 24, column 2)
        client.setFieldTextByCoord(24, 2, "CICS61");

        // Send ENTER key
        sendEnter();

        // Wait for the response
        awaitKeyboardUnlock();

        // Log the first screen response
        String firstScreenText = getScreenText();
        LOG.info("Screen after sending CICS61 via TLS: {}", firstScreenText);

        // Verify that we got a response (the screen should have changed)
        assertThat(firstScreenText).isNotEmpty();

        // Second screen: Send username and password
        String username = System.getProperty("ol.mf.user", "XXXX");
        String password = System.getProperty("ol.mf.password", "YYYYYYY");

        // Send username at position 10,26
        client.setFieldTextByCoord(10, 26, username);

        // Send password at position 11,26
        client.setFieldTextByCoord(11, 26, password);

        // Send ENTER key
        sendEnter();

        // Wait for the response
        awaitKeyboardUnlock();

        // Log the second screen response
        String secondScreenText = getScreenText();
        LOG.info("Screen after sending credentials via TLS: {}", secondScreenText);

        // Verify that we got a response after login
        assertThat(secondScreenText).isNotEmpty();
        assertThat(secondScreenText).isNotEqualTo(firstScreenText);

        // Third screen: Send CC00 command
        // Send "CC00" at position 1,5
        client.setFieldTextByCoord(1, 5, "CC00");

        // Send ENTER key
        sendEnter();

        // Wait for the response
        awaitKeyboardUnlock();

        // Log the third screen response
        String thirdScreenText = getScreenText();
        LOG.info("Screen after sending CC00 command via TLS: {}", thirdScreenText);

        // Verify that we got a response after the CC00 command
        assertThat(thirdScreenText).isNotEmpty();
        assertThat(thirdScreenText).isNotEqualTo(secondScreenText);

        // Clean up - disconnect from the real mainframe
        client.disconnect();
    }

    @Disabled("There`s nothing on port 23...")
    @Test
    void shouldGetCorrectFieldColorsWhenConnectingToLocalhost23() throws Exception {
        // Clean up the mock service connection first
        cleanShutdown();
        service.stop(TIMEOUT_MILLIS);

        // Create a new client for localhost:23 connection
        client = new TerminalClient(TERMINAL_MODEL_TYPE_TWO, SCREEN_DIMENSIONS);
        client.setConnectionTimeoutMillis(1000);
        exceptionWaiter = new ExceptionWaiter();
        client.addConnectionListener(exceptionWaiter);

        // Connect to localhost:23
        client.connect("localhost", 23);

        // Add screen change listener for debugging
        client.addScreenChangeListener(
                screenWatcher ->
                        LOG.debug(
                                "Screen updated from localhost:23, cursor={}, alarm={}, screen:{}",
                                client.getCursorPosition().orElse(null),
                                client.isAlarmOn(),
                                getScreenText()));

        // Wait for the initial screen to load and keyboard to unlock

        // Log the screen content for debugging
        String screenText = getScreenText();
        LOG.info("Screen content from localhost:23: {}", screenText);

        // Check field colors at specified positions
        java.awt.Color colorAt11_24 = client.getColorAt(11, 24);
        java.awt.Color colorAt12_24 = client.getColorAt(12, 24);
        java.awt.Color colorAt14_24 = client.getColorAt(14, 24);

        LOG.info("Color at position 11,24: {}", colorAt11_24);
        LOG.info("Color at position 12,24: {}", colorAt12_24);
        LOG.info("Color at position 14,24: {}", colorAt14_24);

        // Verify the expected colors
        // Red color
        assertThat(colorAt11_24).isEqualTo(java.awt.Color.RED);

        // Pink color
        assertThat(colorAt12_24).isEqualTo(java.awt.Color.PINK);

        // Yellow color
        assertThat(colorAt14_24).isEqualTo(java.awt.Color.YELLOW);

        // Clean up - disconnect from localhost
        client.disconnect();
    }
}
