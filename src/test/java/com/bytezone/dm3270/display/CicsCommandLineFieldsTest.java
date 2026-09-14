package com.bytezone.dm3270.display;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CicsCommandLineFieldsTest {

    @Test
    void commandPositionsUses23RowsOn24x80() {
        assertThat(CicsCommandLineFields.commandPositions(24, 80)).isEqualTo(23 * 80);
    }

    @Test
    void commandPositionsLeavesLastRowOnLargerScreens() {
        assertThat(CicsCommandLineFields.commandPositions(43, 80)).isEqualTo(23 * 80);
    }

    @Test
    void blankCicsCommandAreaNeedsSyntheticField() {
        assertThat(CicsCommandLineFields.needsUnprotectedCommandField(false, true, false)).isTrue();
    }

    @Test
    void wrappingUnprotectedFieldThatStartsAtZeroIsLeftAlone() {
        assertThat(CicsCommandLineFields.needsUnprotectedCommandField(false, true, true)).isFalse();
    }

    @Test
    void wrappingUnprotectedFieldThatStartsLaterStillNeedsSyntheticField() {
        assertThat(CicsCommandLineFields.needsUnprotectedCommandField(false, true, false)).isTrue();
    }

    @Test
    void formattedCommandAreaWithStartFieldIsLeftAlone() {
        assertThat(CicsCommandLineFields.needsUnprotectedCommandField(true, true, false)).isFalse();
    }

    @Test
    void nonEmptyCommandAreaIsLeftAlone() {
        assertThat(CicsCommandLineFields.needsUnprotectedCommandField(false, false, false))
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
}
