package cn.sduonline.join.service;

import cn.sduonline.join.data.dto.DepartmentQuestionRequest;
import cn.sduonline.join.data.dto.DepartmentQuestionVO;
import cn.sduonline.join.data.dto.DepartmentQuestionnaireUpdateRequest;
import cn.sduonline.join.data.dto.DepartmentQuestionnaireVO;
import cn.sduonline.join.data.dto.QuestionOptionRequest;
import cn.sduonline.join.data.dto.QuestionOptionVO;
import cn.sduonline.join.data.enums.BizCode;
import cn.sduonline.join.data.po.DepartmentQuestion;
import cn.sduonline.join.data.po.DepartmentQuestionOption;
import cn.sduonline.join.mapper.AdminOrganizationMapper;
import cn.sduonline.join.mapper.DepartmentQuestionnaireMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DepartmentQuestionnaireService {

    private final AdminOrganizationMapper organizationMapper;
    private final DepartmentQuestionnaireMapper questionnaireMapper;

    public ServiceResult<DepartmentQuestionnaireVO> findByDepartmentId(Long departmentId) {
        if (organizationMapper.selectDepartmentById(departmentId) == null) {
            return ServiceResult.failure(BizCode.DEPARTMENT_NOT_FOUND);
        }
        return ServiceResult.success(toVO(departmentId));
    }

    @Transactional
    public ServiceResult<DepartmentQuestionnaireVO> replace(
            Long departmentId,
            DepartmentQuestionnaireUpdateRequest request
    ) {
        if (organizationMapper.selectDepartmentById(departmentId) == null) {
            return ServiceResult.failure(BizCode.DEPARTMENT_NOT_FOUND);
        }
        questionnaireMapper.deleteQuestions(departmentId);
        for (int questionIndex = 0;
                questionIndex < request.questions().size();
                questionIndex++) {
            DepartmentQuestionRequest source = request.questions().get(questionIndex);
            DepartmentQuestion question = new DepartmentQuestion();
            question.setDepartmentId(departmentId);
            question.setTitle(source.title().trim());
            question.setDescription(trimToNull(source.description()));
            question.setType(source.type());
            question.setRequired(source.required());
            question.setSortOrder(questionIndex);
            questionnaireMapper.insertQuestion(question);
            insertOptions(question.getId(), source.options());
        }
        return ServiceResult.success(toVO(departmentId));
    }

    @Transactional
    public ServiceResult<DepartmentQuestionnaireVO> clear(Long departmentId) {
        if (organizationMapper.selectDepartmentById(departmentId) == null) {
            return ServiceResult.failure(BizCode.DEPARTMENT_NOT_FOUND);
        }
        questionnaireMapper.deleteQuestions(departmentId);
        return ServiceResult.success(new DepartmentQuestionnaireVO(
                departmentId, false, List.of()
        ));
    }

    private void insertOptions(Long questionId, List<QuestionOptionRequest> options) {
        if (options == null) {
            return;
        }
        for (int index = 0; index < options.size(); index++) {
            DepartmentQuestionOption option = new DepartmentQuestionOption();
            option.setQuestionId(questionId);
            option.setContent(options.get(index).content().trim());
            option.setSortOrder(index);
            questionnaireMapper.insertOption(option);
        }
    }

    private DepartmentQuestionnaireVO toVO(Long departmentId) {
        List<DepartmentQuestionVO> questions = questionnaireMapper
                .selectQuestions(departmentId)
                .stream()
                .map(question -> DepartmentQuestionVO.from(
                        question,
                        questionnaireMapper.selectOptions(question.getId())
                                .stream()
                                .map(QuestionOptionVO::from)
                                .toList()
                ))
                .toList();
        return new DepartmentQuestionnaireVO(
                departmentId, !questions.isEmpty(), questions
        );
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
