package com.oddlabs.net;

import java.io.Serial;

/**
 * Thrown when an ARMI event was well-formed and dispatched to the right method, but that method itself threw while
 * running. This is a bug in the receiving code, not bad data from the sender.
 * <p>
 * Before this existed, {@link ARMIInterfaceMethods#invoke} wrapped these in a plain {@link IllegalARMIEventException},
 * the same type used for malformed or malicious network data. Game code treats that as "this peer sent something
 * illegal" and disconnects them, so any game-logic exception while executing a perfectly legitimate player command
 * (moving units into a tower, for example) kicked that player, and the real exception was never shown - the logs only
 * ever said "java.lang.reflect.InvocationTargetException".
 * <p>
 * It extends IllegalARMIEventException so every existing catch site keeps working exactly as before; only the callers
 * that execute game commands (Peer.executeEvents, PeerHub.receiveEvent) catch this subtype separately. The cause is
 * the real exception thrown by the handler, already unwrapped from InvocationTargetException.
 * //added by ikill240c
 */
public final class ARMIHandlerException extends IllegalARMIEventException { //added by ikill240c
    @Serial
    private static final long serialVersionUID = 4127559183262045731L; //added by ikill240c

    public ARMIHandlerException(Throwable cause) { //added by ikill240c
        super(cause); //added by ikill240c
    } //added by ikill240c
} //added by ikill240c
