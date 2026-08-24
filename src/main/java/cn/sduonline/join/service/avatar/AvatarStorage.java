package cn.sduonline.join.service.avatar;

import java.io.IOException;
import org.springframework.web.multipart.MultipartFile;

/**
 * 头像文件存储抽象。生产环境可用 OSS/S3 实现替换本地实现。
 */
public interface AvatarStorage {

    /** 保存头像并返回持久化到数据库的对象 key。 */
    String store(MultipartFile file, AvatarFileType fileType) throws IOException;

    /** 删除旧头像；对象不存在时不报错。 */
    void delete(String key) throws IOException;

    /** 将对象 key 转换为对外可访问的完整 URL。 */
    String publicUrl(String key);
}
