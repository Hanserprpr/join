package cn.sduonline.join.data.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 批量拟录取请求，applicationIds 为部门内报名记录 ID。 */
public record AdmissionBatchRequest(
        @NotEmpty
        @Size(max = 500)
        List<@Positive Long> applicationIds
) {
}
