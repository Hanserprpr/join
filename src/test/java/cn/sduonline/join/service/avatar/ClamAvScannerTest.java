package cn.sduonline.join.service.avatar;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cn.sduonline.join.config.AppProperties;
import cn.sduonline.join.data.enums.BizCode;
import java.io.DataInputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class ClamAvScannerTest {

    @Test
    void acceptsCleanFile() throws Exception {
        try (FakeClamAv server = new FakeClamAv("stream: OK")) {
            ClamAvScanner scanner = scanner(server.port());

            assertDoesNotThrow(() -> scanner.scan(file()));
        }
    }

    @Test
    void rejectsDetectedMalware() throws Exception {
        try (FakeClamAv server = new FakeClamAv("stream: Eicar-Signature FOUND")) {
            ClamAvScanner scanner = scanner(server.port());

            ClamAvScanException exception = assertThrows(
                    ClamAvScanException.class,
                    () -> scanner.scan(file())
            );

            assertEquals(BizCode.AVATAR_MALWARE_DETECTED, exception.getBizCode());
        }
    }

    private static ClamAvScanner scanner(int port) {
        AppProperties properties = new AppProperties();
        properties.getClamav().setEnabled(true);
        properties.getClamav().setHost("127.0.0.1");
        properties.getClamav().setPort(port);
        properties.getClamav().setConnectTimeoutMs(1000);
        properties.getClamav().setReadTimeoutMs(1000);
        return new ClamAvScanner(properties);
    }

    private static MockMultipartFile file() {
        return new MockMultipartFile("file", "avatar.png", "image/png", "payload".getBytes());
    }

    private static final class FakeClamAv implements AutoCloseable {

        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newSingleThreadExecutor();
        private final Future<?> future;

        private FakeClamAv(String response) throws Exception {
            serverSocket = new ServerSocket(0);
            future = executor.submit(() -> serve(response));
        }

        private int port() {
            return serverSocket.getLocalPort();
        }

        private void serve(String response) {
            try (Socket socket = serverSocket.accept()) {
                DataInputStream input = new DataInputStream(socket.getInputStream());
                while (input.readByte() != 0) {
                    // consume zINSTREAM command
                }
                int length;
                while ((length = input.readInt()) != 0) {
                    input.skipNBytes(length);
                }
                socket.getOutputStream().write(
                        (response + "\0").getBytes(StandardCharsets.UTF_8));
                socket.getOutputStream().flush();
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        }

        @Override
        public void close() throws Exception {
            serverSocket.close();
            future.get();
            executor.shutdownNow();
        }
    }
}
