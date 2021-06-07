package com.bytezone.dm3270;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class ConnectionListenerBroadcast implements ConnectionListener {

  private final Set<ConnectionListener> connectionListeners = ConcurrentHashMap.newKeySet();

  public void add(ConnectionListener connectionListener) {
    connectionListeners.add(connectionListener);
  }

  public void remove(ConnectionListener connectionListener) {
    connectionListeners.remove(connectionListener);
  }

  @Override
  public void onConnection() {
    notify(ConnectionListener::onConnection);
  }

  @Override
  public void onException(Exception ex) {
    notify(connectionListener -> connectionListener.onException(ex));
  }

  @Override
  public void onConnectionClosed() {
    notify(ConnectionListener::onConnectionClosed);
  }
  
  private void notify(Consumer<? super ConnectionListener> event) {
    connectionListeners.forEach(event);
  }
}
