package com.oddlabs.tt.net;

import com.oddlabs.net.DefaultARMIArgumentWriter;
import com.oddlabs.util.ByteBufferOutputStream;
import org.jspecify.annotations.NonNull;

import java.io.IOException;

final class GameArgumentWriter extends DefaultARMIArgumentWriter {
    private final DistributableTable distributable_table;

    GameArgumentWriter(DistributableTable table) {
        this.distributable_table = table;
    }

    @Override
    public void writeArgument(@NonNull Class<?> type, @NonNull Object arg,
            @NonNull ByteBufferOutputStream out) throws IOException {
        if (Distributable.class.isAssignableFrom(type)) {
            int name = distributable_table.getName((Distributable) arg);
            // Was `out.buffer().putInt(name)` with no capacity check at all - putInt()/putShort() on
            // the raw NIO buffer bypass ensureCapacity() entirely (that's only wired into the base
            // write() methods), so a large enough argument could write past the fixed-size buffer and
            // throw BufferOverflowException. ensureCapacity() can replace the buffer with a bigger one,
            // so buffer() must be re-fetched AFTER calling it, not cached beforehand. //added by ikill240c
            out.ensureCapacity(Integer.BYTES); //added by ikill240c
            out.buffer().putInt(name);
        } else if (Distributable[].class.isAssignableFrom(type)) {
            Distributable[] distributables = (Distributable[]) arg;
            out.ensureCapacity(Short.BYTES); //added by ikill240c
            out.buffer().putShort((short) distributables.length);
            for (Distributable distributable : distributables) {
                int name = distributable_table.getName(distributable);
                out.ensureCapacity(Integer.BYTES); //added by ikill240c
                out.buffer().putInt(name);
            }
        } else {
            super.writeArgument(type, arg, out);
        }
    }
}
