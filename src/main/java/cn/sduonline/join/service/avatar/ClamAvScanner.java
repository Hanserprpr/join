package cn.sduonline.join.service.avatar;

import cn.sduonline.join.config.AppProperties;
import cn.sduonline.join.data.enums.BizCode;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/** 使用 clamd INSTREAM 协议，在文件公开前进行同步病毒扫描。 */
@Component
@RequiredArgsConstructor
public class ClamAvScanner {

    private static final int CHUNK_SIZE = 8192;
    private static final int MAX_RESPONSE_SIZE = 8192;
    private final AppProperties appProperties;

    public void scan(MultipartFile file) {
        AppProperties.Clamav properties = appProperties.getClamav();
        if (!properties.isEnabled()) {
            return;
        }

        try (Socket socket = new Socket()) {
            socket.connect(
                    new InetSocketAddress(properties.getHost(), properties.getPort()),
                    properties.getConnectTimeoutMs());
            socket.setSoTimeout(properties.getReadTimeoutMs());
            sendFile(socket, file);
            handleResponse(readResponse(socket));
        } catch (ClamAvScanException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new ClamAvScanException(
                    BizCode.THIRD_PARTY_UNAVAILABLE,
                    "ClamAV 扫描服务不可用",
                    exception
            );
        }
    }

    private static void sendFile(Socket socket, MultipartFile file) throws IOException {
        DataOutputStream output = new DataOutputStream(socket.getOutputStream());
        output.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
        byte[] buffer = new byte[CHUNK_SIZE];
        try (InputStream input = file.getInputStream()) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.writeInt(read);
                output.write(buffer, 0, read);
            }
        }
        output.writeInt(0);
        output.flush();
    }

    private static String readResponse(Socket socket) throws IOException {
        ByteArrayOutputStream response = new ByteArrayOutputStream();
        InputStream input = socket.getInputStream();
        while (response.size() < MAX_RESPONSE_SIZE) {
            int value = input.read();
            if (value == -1 || value == 0) {
                break;
            }
            response.write(value);
        }
        if (response.size() == MAX_RESPONSE_SIZE) {
            throw new IOException("ClamAV 响应过长");
        }
        return response.toString(StandardCharsets.UTF_8).trim();
    }

    private static void handleResponse(String response) {
        if (response.endsWith(" OK")) {
            return;
        }
        if (response.endsWith(" FOUND")) {
            throw new ClamAvScanException(
                    BizCode.AVATAR_MALWARE_DETECTED,
                    "ClamAV 检出恶意头像文件"
            );
        }
        throw new ClamAvScanException(
                BizCode.THIRD_PARTY_UNAVAILABLE,
                "ClamAV 返回异常扫描结果"
        );
    }
}
