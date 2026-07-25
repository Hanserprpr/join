package cn.sduonline.join.data.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record DepartmentQuestionnaireUpdateRequest(
        @NotNull @Valid @Size(max = 100) List<DepartmentQuestionRequest> questions
) {
}
