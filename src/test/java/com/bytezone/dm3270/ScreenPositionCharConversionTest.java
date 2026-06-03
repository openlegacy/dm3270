package com.bytezone.dm3270;

import static org.assertj.core.api.Assertions.assertThat;

import com.bytezone.dm3270.display.ScreenContext;
import com.bytezone.dm3270.display.ScreenPosition;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ScreenPositionCharConversionTest {

    static Stream<Arguments> charConversionData() {
        return Stream.of(
                Arguments.of((byte) 0x85, '│', true),
                Arguments.of((byte) 0xA2, '─', true),
                Arguments.of((byte) 0xC4, '└', true),
                Arguments.of((byte) 0xC5, '┌', true),
                Arguments.of((byte) 0xC6, '├', true),
                Arguments.of((byte) 0xC7, '┴', true),
                Arguments.of((byte) 0xD3, '┼', true),
                Arguments.of((byte) 0xD4, '┘', true),
                Arguments.of((byte) 0xD5, '┐', true),
                Arguments.of((byte) 0xD6, '┤', true),
                Arguments.of((byte) 0xD7, '┬', true),
                Arguments.of((byte) 0x85, 'e', false),
                Arguments.of((byte) 0xA2, 's', false),
                Arguments.of((byte) 0xC5, 'E', false),
                Arguments.of((byte) 0xC7, 'G', false));
    }

    @ParameterizedTest
    @MethodSource("charConversionData")
    void shouldConvertToCharWhenGetChar(byte value, char expectedChar, boolean isAplCharset) {
        Charset.CP1047.load();
        ScreenPosition screenPosition =
                new ScreenPosition(0, ScreenContext.DEFAULT_CONTEXT, Charset.CP1047);
        if (isAplCharset) {
            screenPosition.setAplGraphicChar(value);
        } else {
            screenPosition.setChar(value);
        }
        assertThat(screenPosition.getChar()).isEqualTo(expectedChar);
    }
}
