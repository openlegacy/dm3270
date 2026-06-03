# dm3270

Pure Java TN3270 terminal client library.

## Loki Network Recording

Optional Loki TCP recording captures raw TN3270 network traffic for offline replay with the OpenLegacy Loki player.

### API

Configure recording **before** calling `connect()`:

```java
TerminalClient client = new TerminalClient(2, new ScreenDimensions(24, 80));
client.setRecordingFile("/tmp/session.json");  // null or empty = disabled (default)
client.setRecordingSsl(false);                 // true when using an SSL SocketFactory
client.connect("mainframe.example.com", 623);
// ... use the session ...
client.disconnect();                           // writes the Loki JSON file
```

When recording is enabled, a **WARN** log is emitted: `TN3270 network traffic is being recorded to: {file}`.

### Output format

The file is a Loki `NetworkTrace` JSON document with `backendSolution` set to `MF_SCREENS_OL`. It can be replayed with `SingleSequentialTpcPlayerVertical` or `TcpPlayersControllerVerticle` from `loki-tcp-player`.

### Notes

- Recording starts at `connect()` and is flushed on `disconnect()`.
- `setRecordingFile()` / `setRecordingSsl()` throw `IllegalStateException` if called after `connect()`.
- Sniffy instrumentation is JVM-global; avoid concurrent recording sessions in the same JVM.
- Match SSL settings between recording and replay (`setRecordingSsl` must reflect the actual socket factory).

## Build

```bash
./gradlew build
```

Requires OpenLegacy Artifactory credentials (`ARTIFACTORY_OL_OPS_USER`, `ARTIFACTORY_OL_OPS_PASSWORD`) for the `loki-tcp-recorder` dependency.
