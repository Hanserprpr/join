package cn.sduonline.join.data.dto;

import java.util.List;

public record DepartmentQuestionnaireVO(
        Long departmentId,
        boolean configured,
        List<DepartmentQuestionVO> questions
) {
}
