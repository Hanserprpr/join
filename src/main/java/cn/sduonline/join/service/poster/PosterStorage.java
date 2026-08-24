package cn.sduonline.join.service.poster;

import cn.sduonline.join.service.avatar.AvatarFileType;
import java.io.IOException;
import org.springframework.web.multipart.MultipartFile;

/** 部门海报文件存储。 */
public interface PosterStorage {

    String store(MultipartFile file, AvatarFileType fileType) throws IOException;

    /** 当前存储生成 URL 的可信路径前缀，以斜杠结尾。 */
    String publicUrlPrefix();
}
