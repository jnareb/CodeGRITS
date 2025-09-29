import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import utils.StimuliPresentationStatusWatcher;
import utils.TcpCheck;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

class StimuliPresentationStatusWatcherTests {

    private ServerSocket serverSocket;
    private int port;
    private Future<?> serverTask;

    @BeforeEach
    void setUp() throws IOException {
        serverSocket = new ServerSocket(0); // 0 = pick a free port
        port = serverSocket.getLocalPort();

        System.out.println("Server running on port : " + port);

        // Start a simple server in the background
        serverTask = Executors.newSingleThreadExecutor().submit(() -> {
            try (Socket client = serverSocket.accept()) {
                client.getOutputStream().write("{\"SeqNo\":23331,\"DeviceName\":\"AttentionTool\",\"SampleName\":\"GazeCalibrationStart\",\"Timestamp\":-1,\"DateTime\":\"20250617112120816\"}\n".getBytes());
                client.getOutputStream().flush();
                Thread.sleep(100); // simulate streaming
                client.getOutputStream().write("{\"SeqNo\":23333,\"DeviceName\":\"AttentionTool\",\"SampleName\":\"SlideshowStart\",\"Timestamp\":0,\"DateTime\":\"20250617092213090\"}\n".getBytes());
                client.getOutputStream().flush();
                Thread.sleep(100);
                client.getOutputStream().write("{\"SeqNo\":23340,\"DeviceName\":\"EyeTracker\",\"SampleName\":\"EyeData\",\"Timestamp\":3.057,\"GazeTime\":0,\"GazeLeftX\":1048,\"GazeLeftY\":676,\"GazeRightX\":1101,\"GazeRightY\":682,\"PupilLeft\":2.9289222,\"PupilRight\":3.1213243,\"DistanceLeft\":640.85266,\"DistanceRight\":644.6187,\"CameraLeftX\":0.6137858,\"CameraLeftY\":0.5087645,\"CameraRightX\":0.417163,\"CameraRightY\":0.50805247}\n".getBytes());
                client.getOutputStream().flush();
                Thread.sleep(100);
                client.getOutputStream().write("{\"SeqNo\":24577,\"DeviceName\":\"AttentionTool\",\"SampleName\":\"SlideshowEnd\",\"Timestamp\":10133.0606,\"DateTime\":\"20250617092223223\"}\n".getBytes());
                client.getOutputStream().flush();
                Thread.sleep(100);
                client.getOutputStream().write("{\"SeqNo\":24578,\"DeviceName\":\"EyeTracker\",\"SampleName\":\"EyeData\",\"Timestamp\":10130.3706,\"GazeTime\":10127.31300000001,\"GazeLeftX\":1028,\"GazeLeftY\":683,\"GazeRightX\":1066,\"GazeRightY\":690,\"PupilLeft\":2.5136456,\"PupilRight\":2.5781782,\"DistanceLeft\":644.88635,\"DistanceRight\":647.1505,\"CameraLeftX\":0.6022481,\"CameraLeftY\":0.48949304,\"CameraRightX\":0.40546718,\"CameraRightY\":0.4911291}\n".getBytes());
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
    void testWatcherProcessesMessages() throws Exception {
        StimuliPresentationStatusWatcher client = new StimuliPresentationStatusWatcher("127.0.0.1", port);
        client.start();

        try (
                InputStream in = client.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(in))
        ) {

            String line1 = reader.readLine();
            String line2 = reader.readLine();
            String line3 = reader.readLine();
            String line4 = reader.readLine();
            String line5 = reader.readLine();

            assertEquals("data", line1);
            assertEquals("start", line2);
            assertEquals("data", line3);
            assertEquals("stop", line4);
            assertEquals("data", line5);

            assertNull(reader.readLine());

        } finally {
            client.stop();
        }
    }

    @Test
    void testTcpCheckExists() {
        System.out.println("testTcpCheckExists");
        assertTrue(TcpCheck.isServerAvailable("127.0.0.1", port, 1000));
    }

    @Test
    void testTcpCheckNotExists() {
        System.out.println("testTcpCheckNotExists");
        assertFalse(TcpCheck.isServerAvailable("127.0.0.1", port+1, 1000));
    }
}
