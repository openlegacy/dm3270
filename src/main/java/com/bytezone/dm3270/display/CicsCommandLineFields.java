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
        StartFieldAttribute ownerOfZero = null;

        for (int i = 0; i < screenPositions.length; i++) {
            ScreenPosition position = screenPositions[i];
            if (position.isStartField()) {
                ownerOfZero = position.getStartFieldAttribute();
                if (i < commandPositions) {
                    hasStartFieldInCommandArea = true;
                }
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
        boolean positionZeroAlreadyUnprotected = ownerOfZero != null && !ownerOfZero.isProtected();

        boolean needed =
                needsUnprotectedCommandField(
                        hasStartFieldInCommandArea, commandAreaAllNull, commandFieldStartsAtZero);
        int lastIsSfFlag = lastIsSf ? 1 : 0;
        int attributePosition =
                needed ? syntheticAttributePosition(screenPositions.length, lastIsSf) : -1;
        boolean injected = false;
        if (needed
                && attributePosition >= 0
                && !screenPositions[attributePosition].isStartField()) {
            screenPositions[attributePosition].setStartField(
                    new StartFieldAttribute(UNPROTECTED_ALPHANUMERIC));
            injected = true;
            LOG.debug(
                    "OC-2083: synthetic unprotected field at buffer position {}",
                    attributePosition);
        }
        // #region agent log
        try {
            java.nio.file.Files.writeString(
                    java.nio.file.Path.of("c:/Users/roimor/git/ol-terminal/debug-9970fd.log"),
                    "{\"sessionId\":\"9970fd\",\"runId\":\"post-fix\",\"hypothesisId\":\"A\",\"location\":\"CicsCommandLineFields.java:apply\",\"message\":\"cics command field apply\",\"data\":{\"needed\":"
                            + needed
                            + ",\"injected\":"
                            + injected
                            + ",\"hasSfInCmd\":"
                            + hasStartFieldInCommandArea
                            + ",\"allNull\":"
                            + commandAreaAllNull
                            + ",\"zeroUnprot\":"
                            + positionZeroAlreadyUnprotected
                            + ",\"startsAtZero\":"
                            + commandFieldStartsAtZero
                            + ",\"attrPos\":"
                            + attributePosition
                            + ",\"lastIsSf\":"
                            + lastIsSfFlag
                            + ",\"buf\":"
                            + screenPositions.length
                            + "},\"timestamp\":"
                            + System.currentTimeMillis()
                            + "}\n",
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
        // #endregion
    }
}
