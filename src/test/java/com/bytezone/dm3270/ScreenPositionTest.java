package com.bytezone.dm3270;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bytezone.dm3270.attributes.Attribute;
import com.bytezone.dm3270.attributes.StartFieldAttribute;
import com.bytezone.dm3270.display.ScreenContext;
import com.bytezone.dm3270.display.ScreenPosition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScreenPositionTest {

    private ScreenPosition screenPosition;
    private static final byte VALUE = 0x01;

    @BeforeEach
    void setup() {
        screenPosition = new ScreenPosition(0, ScreenContext.DEFAULT_CONTEXT, Charset.CP1147);
    }

    @Test
    void shouldResetCharSizeWhenReset() {
        screenPosition.setChar(VALUE);
        screenPosition.reset();
        assertThat(screenPosition.getByte()).isEqualTo((byte) 0);
    }

    @Test
    void shouldThrowExceptionWhenNullContext() {
        assertThatThrownBy(() -> screenPosition.setScreenContext(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldClearAttributesWhenNullStartField() {
        StartFieldAttribute startFieldAttribute = new StartFieldAttribute(VALUE);
        screenPosition.setStartField(startFieldAttribute);
        screenPosition.addAttribute(
                Attribute.getAttribute(Attribute.XA_START_FIELD, VALUE).orElse(null));
        screenPosition.addAttribute(
                Attribute.getAttribute(Attribute.XA_BGCOLOR, VALUE).orElse(null));
        screenPosition.setStartField(null);
        assertThat(screenPosition.getAttributes()).isEmpty();
    }
}
