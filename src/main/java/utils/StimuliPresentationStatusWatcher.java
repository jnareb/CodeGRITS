package utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.Future;

public class StimuliPresentationStatusWatcher {
    private final String host;
    private final int port;

    private final int pipeBufferSize = 8192;
    private final int byteBufferSize = 4096;

    private final PipedInputStream in;
    private final PipedOutputStream out;

    private AsynchronousSocketChannel channel;

    public StimuliPresentationStatusWatcher(String host, int port) throws IOException {
        this.host = host;
        this.port = port;
        this.in  = new PipedInputStream(pipeBufferSize);
        this.out = new PipedOutputStream(in);
    }

    public void start() throws IOException {
        channel = AsynchronousSocketChannel.open();
        InetSocketAddress hostAddress = new InetSocketAddress(host, port);
        Future<Void> connectFuture = channel.connect(hostAddress);
        try {
            connectFuture.get(); // wait for the connection
        } catch (Exception e) {
            throw new IOException("Failed to connect to %s:%d".formatted(host, port), e);
        }

        // Begin an async read loop
        readLoop();
    }

    /** Close everything */
    public void stop() throws IOException {
        if (channel != null && channel.isOpen()) {
            try {
                channel.close();
            } catch (IOException ignored) {}
        }
        out.close();
        in.close();
    }

    /** Returns an InputStream for reading server data */
    public InputStream getInputStream() {
        return in;
    }

    private void readLoop() {
        ByteBuffer buffer = ByteBuffer.allocate(byteBufferSize);

        channel.read(buffer, buffer, new CompletionHandler<Integer, ByteBuffer>() {
            @Override
            public void completed(Integer bytesRead, ByteBuffer buf) {
                if (bytesRead == -1) {
                    try { out.close(); } catch (IOException ignored) {}
                    return; // server closed connection
                }

                buf.flip();
                try {
                    out.write(buf.array(), 0, bytesRead);
                    out.flush();
                } catch (IOException e) {
                    try { out.close(); } catch (IOException ignored) {}
                    return;
                }
                buf.clear();

                // Schedule next read
                channel.read(buf, buf, this);
            }

            @Override
            public void failed(Throwable exc, ByteBuffer buf) {
                try { out.close(); } catch (IOException ignored) {}
            }
        });
    }
}
