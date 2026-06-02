package com.bytezone.dm3270;

import io.openlegacy.core.definitions.BackendSolution;
import io.openlegacy.loki.record.LokiTcpRecorder;
import io.sniffy.Spy;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Optional Loki TCP recorder for TN3270 sessions. Must be started before any socket is opened. */
public class LokiNetworkRecorder {

    private static final Logger LOG = LoggerFactory.getLogger(LokiNetworkRecorder.class);

    private String recordingFile;
    private boolean isSsl;
    private LokiTcpRecorder recorder;
    private Spy<?> spy;
    private final AtomicBoolean saved = new AtomicBoolean(false);

    public void setRecordingFile(String recordingFile) {
        this.recordingFile = recordingFile;
    }

    public void setRecordingSsl(boolean isSsl) {
        this.isSsl = isSsl;
    }

    public void startIfNeeded() {
        if (!isRecordingEnabled() || spy != null) {
            return;
        }
        LOG.warn("TN3270 network traffic is being recorded to: {}", recordingFile);
        recorder = new LokiTcpRecorder(null);
        spy = recorder.start(isSsl);
    }

    public void finishAndSave() {
        if (!isRecordingEnabled() || !saved.compareAndSet(false, true) || spy == null) {
            return;
        }
        recorder.saveNetworkTraffic(BackendSolution.MF_SCREENS_OL, spy, recordingFile, isSsl);
        LOG.info("TN3270 network traffic has been recorded to: {}", recordingFile);
        spy = null;
        recorder = null;
    }

    private boolean isRecordingEnabled() {
        return recordingFile != null && !recordingFile.trim().isEmpty();
    }
}
