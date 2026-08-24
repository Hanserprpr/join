package cn.sduonline.join.service;

import cn.sduonline.join.config.AppProperties;
import cn.sduonline.join.data.dto.PosterUploadVO;
import cn.sduonline.join.data.enums.BizCode;
import cn.sduonline.join.mapper.AdminOrganizationMapper;
import cn.sduonline.join.service.avatar.AvatarFileType;
import cn.sduonline.join.service.avatar.AvatarFileValidator;
import cn.sduonline.join.service.avatar.ClamAvScanner;
import cn.sduonline.join.service.poster.PosterStorage;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** 部门海报图片上传。 */
@Service
@RequiredArgsConstructor
public class DepartmentPosterUploadService {

    private final AdminOrganizationMapper organizationMapper;
    private final AppProperties appProperties;
    private final AvatarFileValidator imageValidator;
    private final ClamAvScanner clamAvScanner;
    private final PosterStorage posterStorage;

    public ServiceResult<PosterUploadVO> upload(
            Long departmentId,
            MultipartFile file
    ) throws IOException {
        if (organizationMapper.selectDepartmentById(departmentId) == null) {
            return ServiceResult.failure(BizCode.DEPARTMENT_NOT_FOUND);
        }
        AvatarFileType fileType = imageValidator.validate(
                file, appProperties.getPoster().getMaxSizeBytes()
        );
        clamAvScanner.scan(file);
        return ServiceResult.success(new PosterUploadVO(
                posterStorage.store(file, fileType)
        ));
    }
}
