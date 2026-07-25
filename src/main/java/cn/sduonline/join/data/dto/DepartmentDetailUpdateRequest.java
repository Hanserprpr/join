package cn.sduonline.join.data.dto;

import cn.sduonline.join.data.enums.Campus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 部门详情完整更新请求
 *
 * @param campus 所在校区
 * @param introduction 组织介绍
 * @param posters 部门海报
 * @param achievements 部门成果
 * @param recruitmentRequirements 纳新要求
 * @param contact 联系方式
 * @param recruitmentGroup 纳新群信息
 */
public record DepartmentDetailUpdateRequest(
        Campus campus,

        @Size(max = 10000)
        String introduction,

        @Valid
        @Size(max = 20)
        List<DepartmentPosterRequest> posters,

        @Size(max = 10000)
        String achievements,

        @Size(max = 10000)
        String recruitmentRequirements,

        @Size(max = 1000)
        String contact,

        @Size(max = 1000)
        String recruitmentGroup
) {
}
