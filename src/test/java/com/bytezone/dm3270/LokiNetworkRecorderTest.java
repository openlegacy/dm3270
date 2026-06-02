package com.bytezone.dm3270;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bytezone.dm3270.display.ScreenDimensions;
import com.bytezone.dm3270.test.TcpMockServer;
import com.bytezone.dm3270.test.TcpMockServer.MockScenario;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LokiNetworkRecorderTest {

    private static final int TERMINAL_MODEL_TYPE_TWO = 2;
    private static final ScreenDimensions SCREEN_DIMENSIONS = new ScreenDimensions(24, 80);
    private static final long TIMEOUT_MILLIS = 10000;
    private static final String SERVICE_HOST = "localhost";

    private TcpMockServer service;
    private final ScheduledExecutorService stableTimeoutExecutor =
            Executors.newSingleThreadScheduledExecutor();
    private TerminalClient client;
    private File recordingFile;

    @BeforeEach
    void setup() throws Exception {
        service = new TcpMockServer();
        service.setSslEnabled(false);
        service.setScenario(MockScenario.LOGIN);
        service.start();
        recordingFile = File.createTempFile("dm3270-loki-", ".json");
        recordingFile.deleteOnExit();
    }

    @AfterEach
    void teardown() throws Exception {
        if (client != null) {
            client.disconnect();
        }
        service.stop(TIMEOUT_MILLIS);
        stableTimeoutExecutor.shutdownNow();
    }

    @Test
    void shouldNotCreateRecordingFileWhenRecordingDisabled() throws Exception {
        client = new TerminalClient(TERMINAL_MODEL_TYPE_TWO, SCREEN_DIMENSIONS);
        client.setConnectionTimeoutMillis(5000);
        client.connect(SERVICE_HOST, service.getPort());
        awaitKeyboardUnlock();
        client.disconnect();
        client = null;

        assertThat(recordingFile.length()).isZero();
    }

    @Test
    void shouldRecordNetworkTrafficWhenRecordingEnabled() throws Exception {
        client = new TerminalClient(TERMINAL_MODEL_TYPE_TWO, SCREEN_DIMENSIONS);
        client.setConnectionTimeoutMillis(5000);
        client.setRecordingFile(recordingFile.getAbsolutePath());
        client.setRecordingSsl(false);
        client.connect(SERVICE_HOST, service.getPort());
        awaitKeyboardUnlock();
        client.disconnect();
        client = null;

        assertThat(recordingFile).exists();
        String content =
                new String(Files.readAllBytes(recordingFile.toPath()), StandardCharsets.UTF_8);
        assertThat(content).contains("conversions");
        assertThat(content).contains("MF_SCREENS_OL");
    }

    @Test
    void shouldRejectRecordingFileAfterConnect() {
        client = new TerminalClient(TERMINAL_MODEL_TYPE_TWO, SCREEN_DIMENSIONS);
        client.connect(SERVICE_HOST, service.getPort());

        assertThatThrownBy(() -> client.setRecordingFile("/tmp/test.json"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("setRecordingFile");
    }

    private void awaitKeyboardUnlock() throws InterruptedException, TimeoutException {
        new UnlockWaiter(client, stableTimeoutExecutor).await(TIMEOUT_MILLIS);
    }
}
