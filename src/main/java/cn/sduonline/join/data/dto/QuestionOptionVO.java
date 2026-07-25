package cn.sduonline.join.data.dto;

import cn.sduonline.join.data.po.DepartmentQuestionOption;

public record QuestionOptionVO(Long id, String content, Integer sortOrder) {
    public static QuestionOptionVO from(DepartmentQuestionOption option) {
        return new QuestionOptionVO(
                option.getId(), option.getContent(), option.getSortOrder()
        );
    }
}
