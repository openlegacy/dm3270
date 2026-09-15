package com.bytezone.dm3270.display;

import static org.assertj.core.api.Assertions.assertThat;

import com.bytezone.dm3270.Charset;
import com.bytezone.dm3270.attributes.StartFieldAttribute;
import com.bytezone.dm3270.streams.TelnetState;
import org.junit.jupiter.api.Test;

class CicsCommandLineFieldsTest {

    private static final byte PROTECTED_ALPHANUMERIC = (byte) 0x60;

    @Test
    void commandPositionsUses23RowsOn24x80() {
        assertThat(CicsCommandLineFields.commandPositions(24, 80)).isEqualTo(23 * 80);
    }

    @Test
    void commandPositionsLeavesLastRowOnLargerScreens() {
        assertThat(CicsCommandLineFields.commandPositions(43, 80)).isEqualTo(23 * 80);
    }

    @Test
    void blankCicsCommandAreaNeedsSyntheticFieldWhenLaterFieldWouldWrap() {
        assertThat(CicsCommandLineFields.needsUnprotectedCommandField(false, true, false, true))
                .isTrue();
    }

    @Test
    void unformattedBlankScreenDoesNotNeedSyntheticField() {
        assertThat(CicsCommandLineFields.needsUnprotectedCommandField(false, true, false, false))
                .isFalse();
    }

    @Test
    void wrappingUnprotectedFieldThatStartsAtZeroIsLeftAlone() {
        assertThat(CicsCommandLineFields.needsUnprotectedCommandField(false, true, true, true))
                .isFalse();
    }

    @Test
    void wrappingUnprotectedFieldThatStartsLaterStillNeedsSyntheticField() {
        assertThat(CicsCommandLineFields.needsUnprotectedCommandField(false, true, false, true))
                .isTrue();
    }

    @Test
    void formattedCommandAreaWithStartFieldIsLeftAlone() {
        assertThat(CicsCommandLineFields.needsUnprotectedCommandField(true, true, false, true))
                .isFalse();
    }

    @Test
    void nonEmptyCommandAreaIsLeftAlone() {
        assertThat(CicsCommandLineFields.needsUnprotectedCommandField(false, false, false, true))
                .isFalse();
    }

    @Test
    void syntheticAttributeWrapsFromLastPosition() {
        assertThat(CicsCommandLineFields.syntheticAttributePosition(1920, false)).isEqualTo(1919);
    }

    @Test
    void syntheticAttributeFallsBackToZeroWhenLastIsStartField() {
        assertThat(CicsCommandLineFields.syntheticAttributePosition(1920, true)).isEqualTo(0);
    }

    @Test
    void unformattedBlankScreenStaysUnformatted() {
        ScreenDimensions dimensions = new ScreenDimensions(24, 80);
        Screen screen = new Screen(dimensions, null, new TelnetState(), Charset.CP1140);
        screen.buildFields();

        assertThat(screen.getFieldManager().getFields()).isEmpty();
    }

    @Test
    void wrappingProtectedFieldOnLastRowGetsSyntheticUnprotectedCommandField() {
        ScreenDimensions dimensions = new ScreenDimensions(24, 80);
        ScreenPosition[] positions = blankPositions(dimensions.size);
        positions[23 * 80].setStartField(new StartFieldAttribute(PROTECTED_ALPHANUMERIC));

        CicsCommandLineFields.apply(positions, dimensions);

        assertThat(positions[1919].isStartField()).isTrue();
        assertThat(positions[1919].getStartFieldAttribute().isProtected()).isFalse();
    }

    private static ScreenPosition[] blankPositions(int size) {
        ScreenPosition[] positions = new ScreenPosition[size];
        for (int i = 0; i < size; i++) {
            positions[i] = new ScreenPosition(i, ScreenContext.DEFAULT_CONTEXT, Charset.CP1140);
        }
        return positions;
    }
}
