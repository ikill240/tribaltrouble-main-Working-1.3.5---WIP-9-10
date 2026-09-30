package com.oddlabs.net;

import com.oddlabs.util.ByteBufferOutputStream;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.io.ObjectOutputStream;

public class DefaultARMIArgumentWriter implements ARMIArgumentWriter {
    @Override
    public void writeArgument(@NonNull Class<?> type, @NonNull Object arg,
            @NonNull ByteBufferOutputStream out) throws IOException {
        if (type.equals(char.class)) {
            char c = ((Character) arg);
            out.buffer().putChar(c);
        } else if (type.equals(byte.class)) {
            byte b = ((Byte) arg);
            out.buffer().put(b);
        } else if (type.equals(short.class)) {
            short s = ((Short) arg);
            out.buffer().putShort(s);
        } else if (type.equals(int.class)) {
            int integer = ((Integer) arg);
            out.buffer().putInt(integer);
        } else if (type.equals(long.class)) {
            long l = ((Long) arg);
            out.buffer().putLong(l);
        } else if (type.equals(float.class)) {
            float f = ((Number) arg).intValue();
            out.buffer().putFloat(f);
        } else if (type.equals(double.class)) {
            double d = ((Double) arg);
            out.buffer().putDouble(d);
        } else if (type.equals(boolean.class)) {
            boolean bool = ((Boolean) arg);
            byte val = bool ? (byte) 1 : (byte) 0;
            out.buffer().put(val);
        } else if (type.equals(HostSequenceID.class)) {
            HostSequenceID host_seq = (HostSequenceID) arg;
            out.ensureCapacity(8); //added by ikill240c - writes straight into buffer(), see the ARMIEvent case below
            out.buffer().putInt(host_seq.getHostID());
            out.buffer().putInt(host_seq.getSequenceID());
        } else if (type.equals(ARMIEvent.class)) {
            ARMIEvent event = (ARMIEvent) arg;
            // Writes straight into buffer(), bypassing write()'s growth check, and that buffer starts at 16382 bytes.
            // Forwarding a larger event (the matchmaking relay re-packs every tunnelled message this way) threw
            // BufferOverflowException, and the relay dropped the sender's connection - first hit by custom map
            // transfers. Grow first, like the Distributable arguments in GameArgumentWriter already do. //added by ikill240c
            out.ensureCapacity(2 + event.getEventSize()); //added by ikill240c
            out.buffer().putShort(event.getEventSize());
            event.write(out.buffer());
        } else {
            try (ObjectOutputStream obj_output_stream = new ObjectOutputStream(out)) {
                obj_output_stream.writeObject(arg);
            }
        }
    }
}
