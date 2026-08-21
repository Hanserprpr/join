package cn.sduonline.join.data.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;

public record DepartmentApplicationRequest(
        @Size(max = 100) List<@Valid ApplicationAnswerRequest> answers
) {
}
