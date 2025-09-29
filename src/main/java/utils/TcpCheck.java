package utils;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.SocketChannel;

public class TcpCheck {
    public static boolean isServerAvailable(String host, int port) {
        try (SocketChannel channel = SocketChannel.open()) {
            channel.configureBlocking(true);
            boolean connected = channel.connect(new InetSocketAddress(host, port));
//            System.out.println("Connected to: " + channel.getRemoteAddress());

            return connected; // connected successfully

        } catch (IOException e) {
            return false; // connection failed
        }
    }

    public static boolean isServerAvailable(String host, int port, int timeoutMillis) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMillis);
            socket.setSoTimeout(timeoutMillis); // read timeout
//            System.out.println(
//                    "Connected to: " + socket.getRemoteSocketAddress() + "; " +
//                    "(timeout: " + timeoutMillis + "ms)" + "; " +
//                    socket.getInetAddress().isReachable(timeoutMillis)
//            );

            // try to read one byte
            InputStream in = socket.getInputStream();
            int b = in.read();  // blocks until data or timeout

            return b != -1; // mark the server available if we got a response

        } catch (IOException e) {
            return false; // connection failed
        }
    }
}
