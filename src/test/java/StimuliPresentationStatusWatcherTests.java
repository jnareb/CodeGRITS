import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import utils.StimuliPresentationStatusWatcher;
import utils.TcpCheck;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

class StimuliPresentationStatusWatcherTests {

    private ServerSocket serverSocket;
    private int port;
    private Future<?> serverTask;
    private long totalSleepMs = 0;

    @BeforeEach
    void setUp() throws IOException {
        serverSocket = new ServerSocket(0); // 0 = pick a free port
        port = serverSocket.getLocalPort();

        System.out.println("Server running on port : " + port);

        // Start a simple server in the background
        serverTask = Executors.newSingleThreadExecutor().submit(() -> {
            try (Socket client = serverSocket.accept()) {
                totalSleepMs = 0;
                client.getOutputStream().write("{\"SeqNo\":23331,\"DeviceName\":\"AttentionTool\",\"SampleName\":\"GazeCalibrationStart\",\"Timestamp\":-1,\"DateTime\":\"20250617112120816\"}\n".getBytes());
                client.getOutputStream().flush();
                Thread.sleep(100); // simulate streaming
                totalSleepMs += 100;
                client.getOutputStream().write("{\"SeqNo\":23333,\"DeviceName\":\"AttentionTool\",\"SampleName\":\"SlideshowStart\",\"Timestamp\":0,\"DateTime\":\"20250617092213090\"}\n".getBytes());
                client.getOutputStream().flush();
                Thread.sleep(100);
                totalSleepMs += 100;
                client.getOutputStream().write("{\"SeqNo\":23340,\"DeviceName\":\"EyeTracker\",\"SampleName\":\"EyeData\",\"Timestamp\":3.057,\"GazeTime\":0,\"GazeLeftX\":1048,\"GazeLeftY\":676,\"GazeRightX\":1101,\"GazeRightY\":682,\"PupilLeft\":2.9289222,\"PupilRight\":3.1213243,\"DistanceLeft\":640.85266,\"DistanceRight\":644.6187,\"CameraLeftX\":0.6137858,\"CameraLeftY\":0.5087645,\"CameraRightX\":0.417163,\"CameraRightY\":0.50805247}\n".getBytes());
                client.getOutputStream().flush();
                Thread.sleep(100);
                totalSleepMs += 100;
                client.getOutputStream().write("{\"SeqNo\":24577,\"DeviceName\":\"AttentionTool\",\"SampleName\":\"SlideshowEnd\",\"Timestamp\":10133.0606,\"DateTime\":\"20250617092223223\"}\n".getBytes());
                client.getOutputStream().flush();
                Thread.sleep(100);
                totalSleepMs += 100;
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
        List<String> events = new ArrayList<>();

        StimuliPresentationStatusWatcher client =
                new StimuliPresentationStatusWatcher("127.0.0.1", port, events::add);
        client.start();

        // Wait for the server to send and the client to process
        //TimeUnit.MILLISECONDS.sleep(totalSleepMs + 100);
        Thread.sleep(5*100);

        client.stop();

        // DEBUG
        System.out.println("Events received: " + events);
        System.out.println("Total sleep time: " + totalSleepMs);

        // Check that the client received the expected events
        assertEquals(6, events.size());
        assertEquals("data", events.get(0));
        assertEquals("start", events.get(1));
        assertEquals("data", events.get(2));
        assertEquals("stop", events.get(3));
        assertEquals("data", events.get(4));
        assertEquals("close", events.get(5));
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
