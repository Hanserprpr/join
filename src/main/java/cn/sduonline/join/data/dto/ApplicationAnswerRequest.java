package cn.sduonline.join.data.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ApplicationAnswerRequest(
        @Positive Long questionId,
        @Size(max = 5000) String answerText,
        @Size(max = 50) List<@Positive Long> optionIds
) {
}
