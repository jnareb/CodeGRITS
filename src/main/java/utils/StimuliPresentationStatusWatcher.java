package utils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Future;
import java.util.function.Consumer;

public class StimuliPresentationStatusWatcher {
    private final String host;
    private final int port;

    private final int pipeBufferSize = 8192;
    private final int byteBufferSize = 4096;

    private final PipedInputStream in;
    private final Consumer<String> onEvent;

    private AsynchronousSocketChannel channel;

    /// Buffer to accumulate partial lines
    private final StringBuilder lineBuffer = new StringBuilder();

    public StimuliPresentationStatusWatcher(String host, int port, Consumer<String> onEvent) {
        this.host = host;
        this.port = port;

        this.in  = new PipedInputStream(pipeBufferSize);
        this.onEvent = onEvent;
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
                    onEvent.accept("close");
                    return; // server closed connection
                }

                buf.flip();
                String chunk = StandardCharsets.UTF_8.decode(buf).toString();
                buf.clear();

                // Accumulate and process lines
                lineBuffer.append(chunk);
                int newlineIndex;
                while ((newlineIndex = lineBuffer.indexOf("\n")) != -1) {
                    String rawLine = lineBuffer.substring(0, newlineIndex).trim();
                    lineBuffer.delete(0, newlineIndex + 1);

                    String processed = processLine(rawLine);
                    onEvent.accept(processed);
                }

                // Schedule next read
                channel.read(buf, buf, this);
            }

            @Override
            public void failed(Throwable exc, ByteBuffer buf) {
                // just stop on failure
                onEvent.accept("failed");
            }
        });
    }

    /**
     * Example line processor (can be overridden/subclassed)
     * <p>
     * Currently: try to parse the line as a JSON object, and return
     * either "data", "start", or "stop" (or "invalid JSON", or "not a JSON object").
     * <p>
     * The value of "start" is returned on iMotions Lab stimulus presentation start,
     * and the value of "stop" is returned on the stimulus presentation stop.
     * <p>
     * @see <a href="https://github.com/ncusi/iMotions-bridge-for-iTrace">https://github.com/ncusi/iMotions-bridge-for-iTrace</a>
     *
     * @param line The line to process
     * @return "data", "start", or "stop"
     */
    protected String processLine(String line) {
        try {
            JsonElement jsonElement = JsonParser.parseString(line);
            JsonObject jsonObject = jsonElement.getAsJsonObject();

            if (jsonObject.has("DeviceName") &&
                jsonObject.get("DeviceName").getAsString().equals("AttentionTool") &&
                jsonObject.has("SampleName")) {

                String sampleName = jsonObject.get("SampleName").getAsString();
                if (sampleName.equals("SlideshowStart")) {
                    return "start";
                } else if (sampleName.equals("SlideshowEnd")) {
                    return "stop";
                }
            }
        } catch (JsonParseException ignored) {
            // invalid JSON, ignore
            return "invalid JSON";
        } catch (IllegalStateException ignored) {
            // not a JSON object, ignore
            return "not a JSON object";
        }

        return "data";
    }
}
