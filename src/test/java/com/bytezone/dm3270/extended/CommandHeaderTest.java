package com.bytezone.dm3270.extended;

import static org.assertj.core.api.Assertions.assertThat;

import com.bytezone.dm3270.Charset;
import com.bytezone.dm3270.commands.Command;
import com.bytezone.dm3270.commands.WriteCommand;
import com.bytezone.dm3270.display.Screen;
import com.bytezone.dm3270.display.ScreenDimensions;
import com.bytezone.dm3270.streams.TelnetState;
import org.junit.jupiter.api.Test;

class CommandHeaderTest {

    @Test
    void bidLocksKeyboard() {
        Screen screen = newScreen();
        byte[] bidHeader = {0x09, 0x00, 0x02, 0x00, 0x01};

        new CommandHeader(bidHeader, Charset.CP1140).process(screen);

        assertThat(screen.isKeyboardLocked()).isTrue();
    }

    @Test
    void keyboardRemainsLockedUntilBothRestoreAndSendDataArrive() {
        Screen screen = newScreen();
        acceptBid(screen);

        processWrite(screen, 0x02, 0x02);

        assertThat(screen.isKeyboardLocked()).isTrue();
    }

    @Test
    void keyboardUnlocksWhenRestoreAndSendDataArrive() {
        Screen screen = newScreen();
        acceptBid(screen);

        processWrite(screen, 0x03, 0x00);

        assertThat(screen.isKeyboardLocked()).isFalse();
    }

    @Test
    void sendDataDoesNotOverrideAWriteThatLeavesKeyboardLocked() {
        Screen screen = newScreen();
        acceptBid(screen);

        processWrite(screen, 0x01, 0x00);

        assertThat(screen.isKeyboardLocked()).isTrue();
    }

    private static Screen newScreen() {
        return new Screen(new ScreenDimensions(24, 80), null, new TelnetState(), Charset.CP1140);
    }

    private static void acceptBid(Screen screen) {
        byte[] bidHeader = {0x09, 0x00, 0x02, 0x00, 0x01};
        new CommandHeader(bidHeader, Charset.CP1140).process(screen);
    }

    private static void processWrite(Screen screen, int requestFlags, int writeControlCharacter) {
        byte[] header = {0x00, (byte) requestFlags, 0x00, 0x00, 0x02};
        byte[] writeData = {Command.WRITE_F1, (byte) writeControlCharacter};
        WriteCommand write = new WriteCommand(writeData, 0, writeData.length, Charset.CP1140);
        new TN3270ExtendedCommand(
                        new CommandHeader(header, Charset.CP1140),
                        write,
                        new TelnetState(),
                        Charset.CP1140)
                .process(screen);
    }
}
