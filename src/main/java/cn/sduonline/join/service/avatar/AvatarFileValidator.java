package cn.sduonline.join.service.avatar;

import cn.sduonline.join.config.AppProperties;
import cn.sduonline.join.data.enums.BizCode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/** 不信任客户端文件名和 Content-Type，按文件签名识别头像格式。 */
@Component
@RequiredArgsConstructor
public class AvatarFileValidator {

    private static final int HEADER_SIZE = 12;
    private final AppProperties appProperties;

    public AvatarFileType validate(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new AvatarValidationException(BizCode.AVATAR_INVALID);
        }
        if (file.getSize() > appProperties.getAvatar().getMaxSizeBytes()) {
            throw new AvatarValidationException(BizCode.AVATAR_TOO_LARGE);
        }

        byte[] header = new byte[HEADER_SIZE];
        int length;
        try (InputStream input = file.getInputStream()) {
            length = input.readNBytes(header, 0, HEADER_SIZE);
        }
        AvatarFileType type = detect(header, length);
        if (type == null) {
            throw new AvatarValidationException(BizCode.AVATAR_INVALID);
        }
        return type;
    }

    private static AvatarFileType detect(byte[] bytes, int length) {
        if (length >= 3 && (bytes[0] & 0xff) == 0xff
                && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff) {
            return new AvatarFileType("jpg", "image/jpeg");
        }
        if (length >= 8 && (bytes[0] & 0xff) == 0x89
                && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G'
                && bytes[4] == 0x0d && bytes[5] == 0x0a
                && bytes[6] == 0x1a && bytes[7] == 0x0a) {
            return new AvatarFileType("png", "image/png");
        }
        if (length >= 6) {
            String signature = new String(bytes, 0, 6, StandardCharsets.US_ASCII)
                    .toUpperCase(Locale.ROOT);
            if (signature.equals("GIF87A") || signature.equals("GIF89A")) {
                return new AvatarFileType("gif", "image/gif");
            }
        }
        if (length >= 12 && bytes[0] == 'R' && bytes[1] == 'I'
                && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E'
                && bytes[10] == 'B' && bytes[11] == 'P') {
            return new AvatarFileType("webp", "image/webp");
        }
        return null;
    }
}
