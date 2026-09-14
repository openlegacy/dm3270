package com.bytezone.dm3270.display;

import com.bytezone.dm3270.attributes.StartFieldAttribute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Port of h3270 OC-2083: CICS command entry uses the first 23 rows as a blank input area. When
 * those rows have no start-field, a later protected field wraps around and marks the command line
 * protected, so typing is rejected.
 *
 * <p>Place a synthetic unprotected attribute at the last buffer position so the command field wraps
 * to location 0 (first column of row 1). That matches h3270 setting the input field start to 0,0
 * after injecting SF(c0=c0).
 */
final class CicsCommandLineFields {

    private static final Logger LOG = LoggerFactory.getLogger(CicsCommandLineFields.class);

    // 3270 field attribute 0xC0: unprotected, alphanumeric, display, not modified.
    static final byte UNPROTECTED_ALPHANUMERIC = (byte) 0xC0;
    static final int CICS_COMMAND_ROWS = 23;

    private CicsCommandLineFields() {}

    static int commandPositions(int rows, int columns) {
        if (rows < 2 || columns <= 0) {
            return 0;
        }
        int commandRows = Math.min(CICS_COMMAND_ROWS, rows - 1);
        return commandRows * columns;
    }

    /**
     * @param commandFieldStartsAtZero true when the last buffer cell is already an unprotected
     *     start-field, so firstLocation is 0 (row 1 column 1). A wrapping unprotected field that
     *     starts later on the last row still needs a synthetic attribute: otherwise firstLocation
     *     stays on row 24 and the command line at 1,1 is not a field start.
     */
    static boolean needsUnprotectedCommandField(
            boolean hasStartFieldInCommandArea,
            boolean commandAreaAllNull,
            boolean commandFieldStartsAtZero) {
        if (hasStartFieldInCommandArea || !commandAreaAllNull) {
            return false;
        }
        return !commandFieldStartsAtZero;
    }

    static int syntheticAttributePosition(int bufferSize, boolean lastPositionIsStartField) {
        if (bufferSize <= 0) {
            return 0;
        }
        return lastPositionIsStartField ? 0 : bufferSize - 1;
    }

    static void apply(ScreenPosition[] screenPositions, ScreenDimensions dimensions) {
        if (screenPositions == null || dimensions == null) {
            return;
        }

        int commandPositions = commandPositions(dimensions.rows, dimensions.columns);
        if (commandPositions <= 0 || screenPositions.length < commandPositions) {
            return;
        }

        boolean hasStartFieldInCommandArea = false;
        boolean commandAreaAllNull = true;

        for (int i = 0; i < screenPositions.length; i++) {
            ScreenPosition position = screenPositions[i];
            if (position.isStartField() && i < commandPositions) {
                hasStartFieldInCommandArea = true;
            }
            if (i < commandPositions && !position.isNull()) {
                commandAreaAllNull = false;
            }
        }

        // Last start-field in the buffer wraps onto position 0, but that is not enough:
        // firstLocation must be 0 so the command line starts at 1,1.
        ScreenPosition lastPosition = screenPositions[screenPositions.length - 1];
        boolean lastIsSf = lastPosition.isStartField();
        boolean commandFieldStartsAtZero =
                lastIsSf && !lastPosition.getStartFieldAttribute().isProtected();

        if (!needsUnprotectedCommandField(
                hasStartFieldInCommandArea, commandAreaAllNull, commandFieldStartsAtZero)) {
            return;
        }

        int attributePosition = syntheticAttributePosition(screenPositions.length, lastIsSf);
        if (screenPositions[attributePosition].isStartField()) {
            return;
        }

        screenPositions[attributePosition].setStartField(
                new StartFieldAttribute(UNPROTECTED_ALPHANUMERIC));
        LOG.debug("OC-2083: synthetic unprotected field at buffer position {}", attributePosition);
    }
}
