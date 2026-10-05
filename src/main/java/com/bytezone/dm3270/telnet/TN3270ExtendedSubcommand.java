package com.bytezone.dm3270.telnet;

import com.bytezone.dm3270.display.Screen;
import com.bytezone.dm3270.streams.TelnetState;
import java.nio.charset.StandardCharsets;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TN3270ExtendedSubcommand extends TelnetSubcommand {

    private static final Logger LOG = LoggerFactory.getLogger(TN3270ExtendedSubcommand.class);

    private static final byte EXT_DEVICE_TYPE = 2;
    private static final byte EXT_FUNCTIONS = 3;

    private static final byte EXT_IS = 4;
    private static final byte EXT_REJECT = 6;
    private static final byte EXT_REQUEST = 7;
    private static final byte EXT_SEND = 8;

    private SubType subType;
    private int rejectReason = -1;
    private String luName = "";
    private List<Function> functions;
    private String functionsList = "";

    private enum SubType {
        IS,
        REQUEST,
        REJECT,
        DEVICE_TYPE
    }

    public enum Function {
        BIND_IMAGE,
        RESPONSES,
        SYSREQ,
        REASON
    }

    public TN3270ExtendedSubcommand(
            byte[] buffer, int offset, int length, TelnetState telnetState) {
        super(buffer, offset, length, telnetState);

        switch (buffer[3]) {
            case EXT_SEND:
                type = SubcommandType.SEND;
                if (buffer[4] == EXT_DEVICE_TYPE) {
                    subType = SubType.DEVICE_TYPE;
                }
                break;

            case EXT_DEVICE_TYPE:
                type = SubcommandType.DEVICE_TYPE;
                if (buffer[4] == EXT_REQUEST) {
                    subType = SubType.REQUEST;
                    value = new String(buffer, 5, length - 7);
                } else if (buffer[4] == EXT_IS) {
                    subType = SubType.IS;
                    for (int ptr = 6; ptr < length; ptr++) {
                        if (buffer[ptr] == 1) {
                            value = new String(buffer, 5, ptr - 5); // value before the ptr
                            luName = new String(buffer, ptr + 1, length - ptr - 3); // after
                            break;
                        }
                    }
                    // don't know if this can happen
                    if (value == null) {
                        value = new String(buffer, 5, length - 5);
                    }
                } else if (buffer[4] == EXT_REJECT) {
                    subType = SubType.REJECT;
                    if (length > 5) {
                        rejectReason = buffer[5] & 0xFF;
                    }
                    value = rejectReasonText(rejectReason);
                }
                break;

            case EXT_FUNCTIONS:
                type = SubcommandType.FUNCTIONS;
                if (buffer[4] == EXT_REQUEST) {
                    subType = SubType.REQUEST;
                    setFunctions(buffer, length);
                } else if (buffer[4] == EXT_IS) {
                    subType = SubType.IS;
                    setFunctions(buffer, length);
                }
                break;

            default:
                throw new InvalidParameterException(
                        String.format("Unknown Extended: %02X", buffer[3]));
        }
    }

    private void setFunctions(byte[] buffer, int length) {
        StringBuilder funcList = new StringBuilder();
        functions = new ArrayList<>();

        for (int ptr = 5, max = length - 2; ptr < max; ptr++) {
            if (buffer[ptr] == 0) {
                functions.add(Function.BIND_IMAGE);
                funcList.append("BIND, ");
            } else if (buffer[ptr] == 2) {
                functions.add(Function.RESPONSES);
                funcList.append("RESPONSES, ");
            } else if (buffer[ptr] == 4) {
                functions.add(Function.SYSREQ);
                funcList.append("SYSREQ, ");
            } else if (buffer[ptr] == 5) {
                functions.add(Function.REASON);
                funcList.append("CONTENTION-RESOLUTION, ");
            } else {
                throw new InvalidParameterException(
                        String.format("Unknown function: %02X%n", buffer[ptr]));
            }
        }

        if (funcList.length() > 0) {
            funcList.deleteCharAt(funcList.length() - 1);
            funcList.deleteCharAt(funcList.length() - 1);
        }
        functionsList = funcList.toString();
    }

    @Override
    public void process(Screen screen) {
        if (subType == null) {
            LOG.warn("TN3270E subcommand {} has no subtype", type);
            return;
        }

        if (type == SubcommandType.SEND && subType == SubType.DEVICE_TYPE) {
            requestDeviceType();
        }

        // after the server assigns our device type, request these functions
        if (type == SubcommandType.DEVICE_TYPE && subType == SubType.IS) {
            byte[] reply = {
                TelnetCommand.IAC,
                TelnetCommand.SB,
                TN3270E,
                EXT_FUNCTIONS,
                EXT_REQUEST,
                0x00, // BIND-IMAGE
                0x02, // RESPONSES
                0x04, // SYSREQ
                0x05, // CONTENTION-RESOLUTION
                TelnetCommand.IAC,
                TelnetCommand.SE
            };
            setReply(new TN3270ExtendedSubcommand(reply, 0, reply.length, telnetState));
        }

        switch (subType) {
                // the server disagrees with our request and is making a counter-request
            case REQUEST:
                if (type == SubcommandType.FUNCTIONS) {
                    // copy the server's proposal and accept it
                    byte[] reply = new byte[data.length];
                    System.arraycopy(data, 0, reply, 0, data.length);
                    reply[4] = EXT_IS; // replace REQUEST with IS
                    setReply(new TN3270ExtendedSubcommand(reply, 0, reply.length, telnetState));
                }
                break;

                // if the server agrees to our request
            case IS:
                if (type == SubcommandType.FUNCTIONS) {
                    telnetState.setFunctions(functions);
                } else if (type == SubcommandType.DEVICE_TYPE) {
                    telnetState.setDeviceType(getValue());
                }
                break;

            case DEVICE_TYPE:
                break;

            case REJECT:
                LOG.warn("TN3270E device-type rejected: {}", value);
                if (telnetState.requestExtendedDeviceType()) {
                    telnetState.declineExtendedDeviceType();
                    requestDeviceType();
                }
                break;

            default:
                LOG.warn("Unknown subtype: {}", subType);
                break;
        }
    }

    private void requestDeviceType() {
        byte[] header = {
            TelnetCommand.IAC, TelnetCommand.SB, TN3270E, EXT_DEVICE_TYPE, EXT_REQUEST
        };
        String terminalType = telnetState.deviceTypeRequestName();
        byte[] terminal = terminalType.getBytes(StandardCharsets.US_ASCII);
        byte[] reply = new byte[header.length + terminal.length + 2];

        System.arraycopy(header, 0, reply, 0, header.length);
        System.arraycopy(terminal, 0, reply, header.length, terminal.length);
        reply[reply.length - 2] = TelnetCommand.IAC;
        reply[reply.length - 1] = TelnetCommand.SE;

        setReply(new TN3270ExtendedSubcommand(reply, 0, reply.length, telnetState));
    }

    private static String rejectReasonText(int reason) {
        switch (reason) {
            case 0:
                return "CONN-PARTNER";
            case 1:
                return "DEVICE-IN-USE";
            case 2:
                return "INV-ASSOCIATE";
            case 3:
                return "INV-NAME";
            case 4:
                return "INV-DEVICE-TYPE";
            case 5:
                return "TYPE-NAME-ERROR";
            case 6:
                return "UNKNOWN-ERROR";
            case 7:
                return "UNSUPPORTED-REQ";
            default:
                return String.format("%02X", reason);
        }
    }

    @Override
    public String toString() {
        switch (type) {
            case SEND:
                return type + " " + subType;
            case FUNCTIONS:
                return type + " " + subType + " : " + functionsList;
            case DEVICE_TYPE:
                String connectText = luName.isEmpty() ? "" : " (" + luName + ")";
                return type + " " + subType + " " + value + connectText;
            default:
                return "SUB: " + "Unknown";
        }
    }
}
