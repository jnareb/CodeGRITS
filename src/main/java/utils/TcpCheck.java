package utils;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.SocketChannel;

public class TcpCheck {
    /**
     * Checks the availability of a server by attempting to establish a connection
     * to a specified host and port within a given timeout period.
     *
     * @param host          The hostname or IP address of the server to check.
     * @param port          The port number on which the server is expected to listen.
     * @param timeoutMillis The timeout in milliseconds for the connection attempt.
     * @return {@code true} if the server is available and responds within the specified timeout,
     *         {@code false} otherwise.
     */
    public static boolean isServerAvailable(String host, int port, int timeoutMillis) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMillis);
            socket.setSoTimeout(timeoutMillis); // read timeout

            // try to read one byte
            InputStream in = socket.getInputStream();
            int b = in.read();  // blocks until data or timeout

            return b != -1; // mark the server available if we got a response

        } catch (IOException e) {
            return false; // connection failed
        }
    }
}
