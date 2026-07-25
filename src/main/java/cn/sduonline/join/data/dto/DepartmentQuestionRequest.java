package cn.sduonline.join.data.dto;

import cn.sduonline.join.data.enums.QuestionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record DepartmentQuestionRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 1000) String description,
        @NotNull QuestionType type,
        @NotNull Boolean required,
        @Valid @Size(max = 50) List<QuestionOptionRequest> options
) {
    @AssertTrue(message = "选择题至少需要两个选项，文本题不能设置选项")
    public boolean isOptionsValid() {
        if (type == null) {
            return true;
        }
        boolean choice = type == QuestionType.SINGLE_CHOICE
                || type == QuestionType.MULTIPLE_CHOICE;
        return choice
                ? options != null && options.size() >= 2
                : options == null || options.isEmpty();
    }
}
