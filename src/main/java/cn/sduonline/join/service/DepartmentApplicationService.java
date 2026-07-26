package cn.sduonline.join.service;

import cn.sduonline.join.data.dto.ApplicationAnswerRequest;
import cn.sduonline.join.data.dto.DepartmentApplicationRequest;
import cn.sduonline.join.data.dto.DepartmentApplicationDetailVO;
import cn.sduonline.join.data.dto.DepartmentApplicationVO;
import cn.sduonline.join.data.dto.DepartmentApplicationSummaryVO;
import cn.sduonline.join.data.dto.PageVO;
import cn.sduonline.join.data.dto.ApplicationAnswerVO;
import cn.sduonline.join.data.dto.ApplicationAnswerOptionVO;
import cn.sduonline.join.data.enums.ApplicationStatus;
import cn.sduonline.join.data.enums.BizCode;
import cn.sduonline.join.data.enums.QuestionType;
import cn.sduonline.join.data.po.DepartmentApplication;
import cn.sduonline.join.data.po.DepartmentApplicationAnswer;
import cn.sduonline.join.data.po.DepartmentApplicationAnswerOption;
import cn.sduonline.join.data.po.DepartmentQuestion;
import cn.sduonline.join.data.po.DepartmentQuestionOption;
import cn.sduonline.join.data.po.User;
import cn.sduonline.join.mapper.AdminOrganizationMapper;
import cn.sduonline.join.mapper.DepartmentApplicationMapper;
import cn.sduonline.join.mapper.DepartmentQuestionnaireMapper;
import cn.sduonline.join.mapper.UserMapper;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class DepartmentApplicationService {

    private final AdminOrganizationMapper organizationMapper;
    private final DepartmentQuestionnaireMapper questionnaireMapper;
    private final DepartmentApplicationMapper applicationMapper;
    private final UserMapper userMapper;

    public ServiceResult<PageVO<DepartmentApplicationSummaryVO>> findApplications(
            Long departmentId,
            String keyword,
            String college,
            Integer grade,
            Boolean interviewed,
            String sortBy,
            String sortOrder,
            int page,
            int size
    ) {
        if (organizationMapper.selectDepartmentById(departmentId) == null) {
            return ServiceResult.failure(BizCode.DEPARTMENT_NOT_FOUND);
        }
        String normalizedKeyword = trimToNull(keyword);
        String normalizedCollege = trimToNull(college);
        long total = applicationMapper.countApplications(
                departmentId, normalizedKeyword, normalizedCollege, grade,
                interviewed
        );
        List<DepartmentApplicationSummaryVO> applications = applicationMapper
                .selectApplications(
                        departmentId, normalizedKeyword, normalizedCollege, grade,
                        interviewed, sortBy, sortOrder,
                        (page - 1) * size, size
                )
                .stream()
                .map(DepartmentApplicationSummaryVO::from)
                .toList();
        return ServiceResult.success(PageVO.of(applications, total, page, size));
    }

    public ServiceResult<DepartmentApplicationDetailVO> findApplicationDetail(
            Long departmentId,
            Long applicationId
    ) {
        if (organizationMapper.selectDepartmentById(departmentId) == null) {
            return ServiceResult.failure(BizCode.DEPARTMENT_NOT_FOUND);
        }
        DepartmentApplication application =
                applicationMapper.selectApplicationDetail(
                        departmentId, applicationId
                );
        if (application == null) {
            return ServiceResult.failure(BizCode.APPLICATION_NOT_FOUND);
        }
        return ServiceResult.success(toDetailVO(application));
    }

    public ServiceResult<List<DepartmentApplicationDetailVO>> findForExport(
            Long departmentId,
            String keyword,
            String college,
            Integer grade,
            Boolean interviewed
    ) {
        if (organizationMapper.selectDepartmentById(departmentId) == null) {
            return ServiceResult.failure(BizCode.DEPARTMENT_NOT_FOUND);
        }
        List<DepartmentApplicationDetailVO> applications = applicationMapper
                .selectApplications(
                        departmentId, trimToNull(keyword), trimToNull(college),
                        grade, interviewed, null, null, null, null
                )
                .stream()
                .map(this::toDetailVO)
                .toList();
        return ServiceResult.success(applications);
    }

    @Transactional
    public ServiceResult<DepartmentApplicationVO> submit(
            Long departmentId,
            String casId,
            DepartmentApplicationRequest request
    ) {
        if (organizationMapper.selectDepartmentById(departmentId) == null) {
            return ServiceResult.failure(BizCode.DEPARTMENT_NOT_FOUND);
        }
        User user = userMapper.selectById(casId);
        if (user == null) {
            return ServiceResult.failure(BizCode.USER_NOT_FOUND);
        }
        if (!Boolean.TRUE.equals(user.getProfileCompleted())) {
            return ServiceResult.failure(BizCode.PROFILE_INCOMPLETE);
        }
        if (applicationMapper.countByDepartmentAndUser(departmentId, casId) > 0) {
            return ServiceResult.failure(BizCode.DUPLICATE_SUBMIT);
        }

        List<DepartmentQuestion> questions =
                questionnaireMapper.selectQuestions(departmentId);
        List<ApplicationAnswerRequest> answers =
                request.answers() == null ? List.of() : request.answers();
        if (!answersAreValid(questions, answers)) {
            return ServiceResult.failure(BizCode.PARAM_INVALID);
        }

        DepartmentApplication application = new DepartmentApplication();
        application.setDepartmentId(departmentId);
        application.setCasId(casId);
        application.setStatus(ApplicationStatus.SUBMITTED);
        application.setSubmittedAt(LocalDateTime.now());
        try {
            applicationMapper.insertApplication(application);
        } catch (DuplicateKeyException exception) {
            return ServiceResult.failure(BizCode.DUPLICATE_SUBMIT);
        }
        Map<Long, DepartmentQuestion> questionsById = questions.stream()
                .collect(java.util.stream.Collectors.toMap(
                        DepartmentQuestion::getId, question -> question
                ));
        for (ApplicationAnswerRequest source : answers) {
            saveAnswer(
                    application.getId(),
                    questionsById.get(source.questionId()),
                    source
            );
        }
        return ServiceResult.success(DepartmentApplicationVO.from(application));
    }

    private boolean answersAreValid(
            List<DepartmentQuestion> questions,
            List<ApplicationAnswerRequest> answers
    ) {
        if (questions.isEmpty()) {
            return answers.isEmpty();
        }
        Map<Long, ApplicationAnswerRequest> answersByQuestion = new HashMap<>();
        for (ApplicationAnswerRequest answer : answers) {
            if (answer.questionId() == null
                    || answersByQuestion.put(answer.questionId(), answer) != null) {
                return false;
            }
        }
        Set<Long> questionIds = new HashSet<>();
        for (DepartmentQuestion question : questions) {
            questionIds.add(question.getId());
            ApplicationAnswerRequest answer = answersByQuestion.get(question.getId());
            if (answer == null) {
                if (Boolean.TRUE.equals(question.getRequired())) {
                    return false;
                }
                continue;
            }
            if (!answerIsValid(question, answer)) {
                return false;
            }
        }
        return answersByQuestion.keySet().stream().allMatch(questionIds::contains);
    }

    private boolean answerIsValid(
            DepartmentQuestion question,
            ApplicationAnswerRequest answer
    ) {
        List<Long> optionIds =
                answer.optionIds() == null ? List.of() : answer.optionIds();
        boolean hasText = StringUtils.hasText(answer.answerText());
        if (question.getType() == QuestionType.SHORT_TEXT
                || question.getType() == QuestionType.LONG_TEXT) {
            return optionIds.isEmpty()
                    && (!Boolean.TRUE.equals(question.getRequired()) || hasText);
        }
        if (hasText || new HashSet<>(optionIds).size() != optionIds.size()) {
            return false;
        }
        if (question.getType() == QuestionType.SINGLE_CHOICE
                && optionIds.size() > 1) {
            return false;
        }
        if (Boolean.TRUE.equals(question.getRequired()) && optionIds.isEmpty()) {
            return false;
        }
        Set<Long> validOptionIds = questionnaireMapper
                .selectOptions(question.getId())
                .stream()
                .map(option -> option.getId())
                .collect(java.util.stream.Collectors.toSet());
        return validOptionIds.containsAll(optionIds);
    }

    private void saveAnswer(
            Long applicationId,
            DepartmentQuestion question,
            ApplicationAnswerRequest source
    ) {
        DepartmentApplicationAnswer answer = new DepartmentApplicationAnswer();
        answer.setApplicationId(applicationId);
        answer.setQuestionId(source.questionId());
        answer.setQuestionTitle(question.getTitle());
        answer.setQuestionType(question.getType());
        answer.setAnswerText(
                StringUtils.hasText(source.answerText())
                        ? source.answerText().trim()
                        : null
        );
        applicationMapper.insertAnswer(answer);
        if (source.optionIds() != null && !source.optionIds().isEmpty()) {
            Map<Long, DepartmentQuestionOption> optionsById =
                    questionnaireMapper.selectOptions(question.getId())
                            .stream()
                            .collect(java.util.stream.Collectors.toMap(
                                    DepartmentQuestionOption::getId,
                                    option -> option
                            ));
            for (Long optionId : source.optionIds()) {
                DepartmentApplicationAnswerOption selected =
                        new DepartmentApplicationAnswerOption();
                selected.setAnswerId(answer.getId());
                selected.setOptionId(optionId);
                selected.setOptionContent(optionsById.get(optionId).getContent());
                applicationMapper.insertAnswerOption(selected);
            }
        }
    }

    private DepartmentApplicationDetailVO toDetailVO(
            DepartmentApplication application
    ) {
        List<ApplicationAnswerVO> answers = applicationMapper
                .selectAnswers(application.getId())
                .stream()
                .map(answer -> new ApplicationAnswerVO(
                        answer.getQuestionId(),
                        answer.getQuestionTitle(),
                        answer.getQuestionType(),
                        answer.getAnswerText(),
                        applicationMapper.selectAnswerOptions(answer.getId())
                                .stream()
                                .map(option -> new ApplicationAnswerOptionVO(
                                        option.getOptionId(),
                                        option.getOptionContent()
                                ))
                                .toList()
                ))
                .toList();
        return new DepartmentApplicationDetailVO(
                application.getId(),
                application.getDepartmentId(),
                application.getCasId(),
                application.getApplicantName(),
                application.getCollege(),
                application.getMajor(),
                application.getGrade(),
                application.getPhone(),
                application.getEmail(),
                application.getQq(),
                application.getStatus(),
                application.getSubmittedAt(),
                answers
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
