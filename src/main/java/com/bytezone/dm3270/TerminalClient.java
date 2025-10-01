package com.bytezone.dm3270;

import com.bytezone.dm3270.application.ConsolePane;
import com.bytezone.dm3270.application.KeyboardStatusListener;
import com.bytezone.dm3270.application.Site;
import com.bytezone.dm3270.display.Cursor;
import com.bytezone.dm3270.display.CursorMoveListener;
import com.bytezone.dm3270.display.Field;
import com.bytezone.dm3270.display.Screen;
import com.bytezone.dm3270.display.ScreenChangeListener;
import com.bytezone.dm3270.display.ScreenDimensions;
import com.bytezone.dm3270.display.ScreenPosition;
import com.bytezone.dm3270.streams.TelnetState;
import java.awt.Point;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import javax.net.SocketFactory;

/**
 * Client to connect to TN3270 terminal servers.
 * <p>
 * This class provides a facade to ease usage of dm3270 to java clients.
 */
public class TerminalClient {

  private final Screen screen;
  private boolean usesExtended3270;
  private ConsolePane consolePane;
  private SocketFactory socketFactory = SocketFactory.getDefault();
  private int connectionTimeoutMillis;
  private final ConnectionListenerBroadcast connectionListenerBroadcast;

  /**
   * Creates a new terminal client with given model and screen dimensions.
   *
   * @param model model of the terminal. Known values are 2,3,4 and 5
   * @param alternateScreenDimensions alternate screen dimensions in rows and columns
   */
  public TerminalClient(int model, ScreenDimensions alternateScreenDimensions) {
    this(model, alternateScreenDimensions, Charset.CP1047);
  }

  public TerminalClient(int model, ScreenDimensions alternateScreenDimensions, Charset charset) {
    charset.load();
    TelnetState telnetState = new TelnetState();
    telnetState.setDoDeviceType(model);
    screen = new Screen(new ScreenDimensions(24, 80), alternateScreenDimensions, telnetState,
        charset);
    connectionListenerBroadcast = new ConnectionListenerBroadcast();
  }

  /**
   * Sets whether the emulated terminal supports extended protocol or not.
   *
   * @param usesExtended3270 set true to support extended protocol, and false if not. By default is
   * false.
   */
  public void setUsesExtended3270(boolean usesExtended3270) {
    this.usesExtended3270 = usesExtended3270;
  }

  /**
   * Allows setting the {@link SocketFactory} to be used to create sockets which allows using SSL
   * sockets.
   *
   * @param socketFactory the {@link SocketFactory} to use. If non is specified {@link
   * SocketFactory#getDefault()} will be used.
   */
  public void setSocketFactory(SocketFactory socketFactory) {
    this.socketFactory = socketFactory;
  }

  /**
   * Sets the timeout for the socket connection.
   *
   * @param connectionTimeoutMillis Number of millis to wait for a connection to be established
   * before it fails. If not specified no timeout (same as 0 value) will be applied.
   */
  public void setConnectionTimeoutMillis(int connectionTimeoutMillis) {
    this.connectionTimeoutMillis = connectionTimeoutMillis;
  }

  /**
   * Adds a class to handle general exception handler.
   *
   * @param connectionListener a class to handle exceptions. If none is provided then exceptions
   * stack trace will be printed to error output.
   */
  public void addConnectionListener(ConnectionListener connectionListener) {
    this.connectionListenerBroadcast.add(connectionListener);
  }

  /**
   * Removes a class that handle general exception handler.
   *
   * @param connectionListener a class to handle exceptions. If none is provided then exceptions
   * stack trace will be printed to error output.
   */
  public void removeConnectionListener(ConnectionListener connectionListener) {
    this.connectionListenerBroadcast.remove(connectionListener);
  }

  /**
   * Connect to a terminal server.
   *
   * @param host host name of the terminal server.
   * @param port port where the terminal server is listening for connections.
   */
  public void connect(String host, int port) {
    screen.lockKeyboard("connect");
    consolePane = new ConsolePane(screen, new Site(host, port, usesExtended3270), socketFactory);
    consolePane.setConnectionTimeoutMillis(connectionTimeoutMillis);
    consolePane.setConnectionListener(connectionListenerBroadcast);
    consolePane.connect();
  }

  /**
   * Set the text of a field in the screen.
   *
   * @param row row number where to set the field text. First row is 1.
   * @param column column number where to set the field text. First column is 1.
   * @param text the text to set on the field.
   */
  public void setFieldTextByCoord(int row, int column, String text) {
    int linearPosition = (row - 1) * screen.getScreenDimensions().columns + column - 1;
    if (screen.getFieldManager().getFields().isEmpty()) {
      setPositionText(text, linearPosition);
    } else {
      Field field = screen.getFieldManager()
          .getFieldAt(linearPosition)
          .orElseThrow(
              () -> new IllegalArgumentException("Invalid field position " + row + "," + column));
      setFieldText(field, text);
    }
  }

  private void setPositionText(String text, int fieldPosition) {
    screen.setPositionText(fieldPosition, text);
    setCursorPosition(fieldPosition + findFieldNextPosition(text));
  }

  private int findFieldNextPosition(String text) {
    if (text.isEmpty()) {
      return 1;
    }
    int pos = text.length() - 1;
    while (text.charAt(pos) == '\u0000') {
      pos--;
    }
    return pos + 1;
  }

  private void setFieldText(Field field, String text) {
    field.setText(text);
    int nextPosition = findFieldNextPosition(text);
    int cursorPosition =
        field.getDisplayLength() > nextPosition ? field.getFirstLocation() + nextPosition
            : field.getNextUnprotectedField().getFirstLocation();

    setCursorPosition(cursorPosition);
  }

  public void setFieldTextByLabel(String lbl, String text) {
    if (screen.getFieldManager().getFields().isEmpty()) {
      String screenText = getScreenText();
      if (!screenText.contains(lbl)) {
        throw buildInvalidFieldLabelException(lbl);
      }
      // findLastNonBlankPosition() + 2 in order to get the first writable position,
      // avoiding the first space after labels (which has been considered as 'standard')
      int fieldPosition = findLastNonBlankPosition() + 2;
      setPositionText(text, fieldPosition);
    } else {
      Field field = findFieldByLabel(lbl);
      if (field == null) {
        throw buildInvalidFieldLabelException(lbl);
      }
      setFieldText(field, text);
    }

  }

  private IllegalArgumentException buildInvalidFieldLabelException(String lbl) {
    return new IllegalArgumentException("Invalid label: " + lbl);
  }

  private int findLastNonBlankPosition() {
    String screenText = getScreenText();
    int lastNonBlankPosition = screenText.length() - 1;
    while (lastNonBlankPosition >= 0 && (screenText.charAt(lastNonBlankPosition) == '\u0000'
        || screenText.charAt(lastNonBlankPosition) == '\n')
        || screenText.charAt(lastNonBlankPosition) == ' ') {
      lastNonBlankPosition--;
    }
    return lastNonBlankPosition;
  }

  private Field findFieldByLabel(String label) {
    Field labelField = findLabelField(label);
    return (labelField != null) ? labelField.getNextUnprotectedField() : null;
  }

  private Field findLabelField(String label) {
    String screenText = getScreenText().replace("\n", "");
    int pos = 0;
    Field fallbackLabelField = null;
    while (pos != -1) {
      pos = screenText.indexOf(label, pos);
      if (pos != -1) {
        Field field = screen.getFieldManager().getFieldAt(pos).orElse(null);
        if (field != null) {
          if (field.isProtected()) {
            return field;
          } else {
            if (fallbackLabelField == null) {
              fallbackLabelField = field;
            }
            pos++;
          }
        } else {
          pos++;
        }
      }
    }
    return fallbackLabelField;
  }

  public void setTabulatedInput(String text, int offset) throws NoSuchFieldException {
    int row = getCursorPosition().get().y;
    int column = getCursorPosition().get().x;
    int linearPosition = (row - 1) * screen.getScreenDimensions().columns + column - 1;
    if (!getFields().isEmpty()) {
      Field finalField = screen.getFieldManager()
          .getFieldAt(linearPosition)
          .orElse(null);
      if (finalField == null && offset <= 0) {
        throw new NoSuchElementException("No field found at position (" + row + "," + column + ")");
      }
      if (finalField == null) {
        // this is considered as a tabulator therefore offset offset is reduced 
        finalField = getNextFieldFromPos(linearPosition);
        offset--;
      }
      for (int i = 0; i < offset; i++) {
        finalField = finalField.getNextUnprotectedField();
      }
      setFieldText(finalField, text);
    } else {
      if (offset == 0) {
        setPositionText(text, linearPosition);
      } else {
        throw new NoSuchElementException("No fields on screen to skip, " + offset + "tab/s");
      }
    }
  }

  private Field getNextFieldFromPos(int linealPosition) throws NoSuchFieldException {
    Field ret;
    int maxLinealPos = getScreenDimensions().size;
    for (int i = linealPosition; i <= maxLinealPos; i++) {
      int index = linealPosition + i <= maxLinealPos ? linealPosition + i
          : Math.abs((maxLinealPos - linealPosition) - i) - 1;
      ret = screen.getFieldManager().getFieldAt(index).orElse(null);
      if (ret != null) {
        return ret.isUnprotected() ? ret : ret.getNextUnprotectedField();
      }
    }
    throw new NoSuchFieldException("Screen is not constituted by fields");
  }

  /**
   * Send an Action ID.
   *
   * This method is usually used to send Enter after setting text fields, or to send some other keys
   * (like F1).
   *
   * @param aid Action ID to send. For example Enter.
   * @param name Name of the action sent.
   */
  public void sendAID(byte aid, String name) {
    consolePane.sendAID(aid, name);
  }

  /**
   * Gets the screen text.
   *
   * @return The screen text with newlines separating each row.
   */
  public String getScreenText() {
    StringBuilder text = new StringBuilder();
    int pos = 0;
    boolean visible = true;
    Iterator<ScreenPosition> positionsIterator = screen.getPen().iterator();
    ScreenDimensions screenDimensions = screen.getScreenDimensions();
    int positionsCount = screenDimensions.columns * screenDimensions.rows;
    while (pos < positionsCount && positionsIterator.hasNext()) {
      ScreenPosition sp = positionsIterator.next();
      if (sp.isStartField()) {
        visible = sp.getStartFieldAttribute().isVisible();
      }
      text.append(visible ? sp.getChar() : ' ');
      ++pos;
      if (pos % screenDimensions.columns == 0) {
        text.append("\n");
      }
    }
    return text.toString();
  }

  /**
   * Gets the list of all fields (protected and unprotected) that compose the screen.
   *
   * @return The list of fields that compose the screen. Fields are not only positions where input
   * is expected, but also parts of the screen which are not meant to be modified or even visible.
   */
  public List<Field> getFields() {
    return screen.getFieldManager().getFields();
  }

  /**
   * Adding a {@link ScreenChangeListener} to the terminal emulator.
   *
   * @param listener The listener to be notified when changes on the screen happen.
   */
  public void addScreenChangeListener(ScreenChangeListener listener) {
    screen.getFieldManager().addScreenChangeListener(listener);
  }

  /**
   * Remove a {@link ScreenChangeListener} from the terminal emulator.
   *
   * @param listener Listener to be removed from notifications.
   */
  public void removeScreenChangeListener(ScreenChangeListener listener) {
    screen.getFieldManager().removeScreenChangeListener(listener);
  }

  /**
   * Allows checking if keyboard has been locked (no input can be sent) by the terminal server.
   *
   * @return True if the keyboard is currently locked, false otherwise.
   */
  public boolean isKeyboardLocked() {
    return screen.isKeyboardLocked();
  }

  /**
   * Add a {@link KeyboardStatusListener} to the terminal emulator.
   *
   * @param listener the listener to be notified when the status (locked/unlocked) of the keyboard
   * has changed.
   */
  public void addKeyboardStatusListener(KeyboardStatusListener listener) {
    screen.addKeyboardStatusChangeListener(listener);
  }

  /**
   * Remove a {@link KeyboardStatusListener} from the terminal emulator.
   *
   * @param listener the listener to be removed from notifications.
   */
  public void removeKeyboardStatusListener(KeyboardStatusListener listener) {
    screen.removeKeyboardStatusChangeListener(listener);
  }

  /**
   * Gets the status of the alarm.
   *
   * Prefer using resetAlarm so it is properly reset when checking value. Use this operation only if
   * you are implementing some tracing or debugging and don't want to change the alarm flag status.
   */
  public boolean isAlarmOn() {
    return screen.isAlarmOn();
  }

  /**
   * Allows resetting and getting the status of the alarm triggered by the terminal server.
   *
   * @return True if the alarm has sounded, false otherwise.
   */
  public boolean resetAlarm() {
    return screen.resetAlarm();
  }

  /**
   * Get the screen dimensions of the terminal emulator screen.
   *
   * @return Allows getting the number of rows and columns used by the terminal emulator.
   */
  public ScreenDimensions getScreenDimensions() {
    return screen.getScreenDimensions();
  }

  /**
   * Get the position of the cursor in the screen.
   *
   * @return The position of the cursor in the screen (x contains the column and y the row). If the
   * cursor is not visible then empty value is returned.
   */
  public Optional<Point> getCursorPosition() {
    Cursor cursor = screen.getScreenCursor();
    int location = cursor.getLocation();
    int columns = screen.getScreenDimensions().columns;
    return cursor.isVisible()
        ? Optional.of(new Point(location % columns + 1, location / columns + 1))
        : Optional.empty();
  }

  public void setCursorPosition(int linearPosition) {
    screen.getScreenCursor().moveTo(linearPosition);
  }

  /**
   * Add a {@link CursorMoveListener} to the terminal emulator.
   *
   * @param listener listener to be notified when the cursor is moved by terminal server.
   */
  public void addCursorMoveListener(CursorMoveListener listener) {
    screen.getScreenCursor().addCursorMoveListener(listener);
  }

  /**
   * Remove a {@link CursorMoveListener} from the terminal emulator.
   *
   * @param listener listener to be remove from notificaitons.
   */
  public void removeCursorMoveListener(CursorMoveListener listener) {
    screen.getScreenCursor().removeCursorMoveListener(listener);
  }

  /**
   * Get the foreground color at a specific screen position.
   *
   * @param row row number (1-based)
   * @param column column number (1-based)
   * @return the foreground Color at the specified position
   */
  public java.awt.Color getColorAt(int row, int column) {
    int linearPosition = (row - 1) * screen.getScreenDimensions().columns + column - 1;
    ScreenPosition screenPosition = screen.getScreenPosition(linearPosition);
    return screenPosition.getScreenContext().foregroundColor;
  }

  /**
   * Get the background color at a specific screen position.
   *
   * @param row row number (1-based)
   * @param column column number (1-based)
   * @return the background Color at the specified position
   */
  public java.awt.Color getBackgroundColorAt(int row, int column) {
    int linearPosition = (row - 1) * screen.getScreenDimensions().columns + column - 1;
    ScreenPosition screenPosition = screen.getScreenPosition(linearPosition);
    return screenPosition.getScreenContext().backgroundColor;
  }

  /**
   * Check if a specific screen position has underline (underscore) highlighting.
   *
   * @param row row number (1-based)
   * @param column column number (1-based)
   * @return true if the position has underline highlighting, false otherwise
   */
  public boolean isUnderlineAt(int row, int column) {
    int linearPosition = (row - 1) * screen.getScreenDimensions().columns + column - 1;
    ScreenPosition screenPosition = screen.getScreenPosition(linearPosition);
    byte highlight = screenPosition.getScreenContext().highlight;
    return (highlight & 0x0F) == 0x04; // 0x04 is the underscore/underline highlight value
  }

  /**
   * Disconnect the terminal emulator from the server.
   *
   * @throws InterruptedException thrown when the disconnect is interrupted.
   */
  public void disconnect() throws InterruptedException {
    consolePane.disconnect();
  }

}

