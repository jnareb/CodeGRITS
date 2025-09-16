import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import utils.StimuliPresentationStatusWatcher;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

class StimuliPresentationStatusWatcherTests {

    private ServerSocket serverSocket;
    private int port;
    private Future<?> serverTask;

    @BeforeEach
    void setUp() throws IOException {
        serverSocket = new ServerSocket(0); // 0 = pick a free port
        port = serverSocket.getLocalPort();

        // Start a simple server in the background
        serverTask = Executors.newSingleThreadExecutor().submit(() -> {
            try (Socket client = serverSocket.accept()) {
                client.getOutputStream().write("line1\n".getBytes());
                client.getOutputStream().flush();
                Thread.sleep(100); // simulate streaming
                client.getOutputStream().write("line2\n".getBytes());
                client.getOutputStream().flush();
                Thread.sleep(100);
                client.getOutputStream().write("line3\n".getBytes());
                client.getOutputStream().flush();
            } catch (Exception ignored) {}
        });
    }

    @AfterEach
    void tearDown() throws Exception {
        if (serverSocket != null && !serverSocket.isClosed()) {
            serverSocket.close();
        }
        if (serverTask != null) {
            serverTask.cancel(true);
        }
    }

    @Test
    void testWatcherReceivesMessages() throws Exception {
        StimuliPresentationStatusWatcher client = new StimuliPresentationStatusWatcher("127.0.0.1", port);
        client.start();

        try (InputStream in = client.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {

            String line1 = reader.readLine();
            String line2 = reader.readLine();
            String line3 = reader.readLine();

            assertEquals("line1", line1);
            assertEquals("line2", line2);
            assertEquals("line3", line3);
        } finally {
            client.stop();
        }
    }

}