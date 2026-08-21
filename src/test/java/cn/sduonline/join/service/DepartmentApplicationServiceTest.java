package cn.sduonline.join.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.sduonline.join.data.dto.ApplicationAnswerRequest;
import cn.sduonline.join.data.dto.DepartmentApplicationRequest;
import cn.sduonline.join.data.dto.AdmissionPublishRequest;
import cn.sduonline.join.data.enums.BizCode;
import cn.sduonline.join.data.enums.ApplicationStatus;
import cn.sduonline.join.data.enums.QuestionType;
import cn.sduonline.join.data.po.Department;
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
import java.util.List;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DepartmentApplicationServiceTest {

    @Mock AdminOrganizationMapper organizationMapper;
    @Mock DepartmentQuestionnaireMapper questionnaireMapper;
    @Mock DepartmentApplicationMapper applicationMapper;
    @Mock UserMapper userMapper;
    @Mock AdmissionEmailService admissionEmailService;
    private DepartmentApplicationService service;

    @BeforeEach
    void setUp() {
        service = new DepartmentApplicationService(
                organizationMapper, questionnaireMapper,
                applicationMapper, userMapper, admissionEmailService
        );
    }

    @Test
    void submitsDirectlyWhenDepartmentHasNoQuestionnaire() {
        prepareEligibleUser();
        when(questionnaireMapper.selectQuestions(12L)).thenReturn(List.of());
        assignApplicationId();

        var result = service.submit(
                12L, "20240001", new DepartmentApplicationRequest(null)
        );

        assertTrue(result.isSuccess());
        assertEquals(100L, result.data().id());
        verify(applicationMapper).insertApplication(any());
        verify(applicationMapper, never()).insertAnswer(any());
    }

    @Test
    void rejectsMissingRequiredAnswer() {
        prepareEligibleUser();
        when(questionnaireMapper.selectQuestions(12L))
                .thenReturn(List.of(question(1L, QuestionType.LONG_TEXT, true)));

        var result = service.submit(
                12L, "20240001",
                new DepartmentApplicationRequest(List.of())
        );

        assertEquals(BizCode.PARAM_INVALID, result.error());
        verify(applicationMapper, never()).insertApplication(any());
    }

    @Test
    void savesValidatedChoiceAnswer() {
        prepareEligibleUser();
        when(questionnaireMapper.selectQuestions(12L)).thenReturn(
                List.of(question(1L, QuestionType.SINGLE_CHOICE, true))
        );
        when(questionnaireMapper.selectOptions(1L))
                .thenReturn(List.of(option(10L, "后端"), option(11L, "前端")));
        assignApplicationId();
        doAnswer(invocation -> {
            DepartmentApplicationAnswer answer = invocation.getArgument(0);
            answer.setId(200L);
            return 1;
        }).when(applicationMapper).insertAnswer(any());

        var result = service.submit(
                12L, "20240001",
                new DepartmentApplicationRequest(List.of(
                        new ApplicationAnswerRequest(1L, null, List.of(10L))
                ))
        );

        assertTrue(result.isSuccess());
        verify(applicationMapper).insertAnswer(any());
        verify(applicationMapper).insertAnswerOption(any());
    }

    @Test
    void rejectsDuplicateApplication() {
        prepareEligibleUser();
        when(applicationMapper.countByDepartmentAndUser(12L, "20240001"))
                .thenReturn(1L);

        var result = service.submit(
                12L, "20240001", new DepartmentApplicationRequest(null)
        );

        assertEquals(BizCode.DUPLICATE_SUBMIT, result.error());
        verify(applicationMapper, never()).insertApplication(any());
    }

    @Test
    void paginatesAndFiltersApplicationsByCollegeAndGrade() {
        when(organizationMapper.selectDepartmentById(12L))
                .thenReturn(new Department());
        DepartmentApplication application = new DepartmentApplication();
        application.setId(100L);
        application.setDepartmentId(12L);
        application.setCasId("20240001");
        application.setApplicantName("张三");
        application.setCollege("软件学院");
        application.setGrade(2024);
        application.setStatus(
                cn.sduonline.join.data.enums.ApplicationStatus.SUBMITTED
        );
        application.setSubmittedAt(LocalDateTime.now());
        application.setInterviewId(300L);
        application.setInterviewed(true);
        application.setScore(5);
        when(applicationMapper.countApplications(
                12L, "张三", "软件学院", 2024, true
        )).thenReturn(1L);
        when(applicationMapper.selectApplications(
                12L, "张三", "软件学院", 2024,
                true, "score", "desc", 0, 20
        ))
                .thenReturn(List.of(application));

        var result = service.findApplications(
                12L, " 张三 ", " 软件学院 ", 2024,
                true, "score", "desc", 1, 20
        );

        assertTrue(result.isSuccess());
        assertEquals(1L, result.data().total());
        assertEquals("张三", result.data().items().getFirst().applicantName());
        assertEquals("软件学院", result.data().items().getFirst().college());
        assertTrue(result.data().items().getFirst().interviewed());
        assertEquals(5, result.data().items().getFirst().score());
    }

    @Test
    void returnsHistoricalAnswerSnapshotsInApplicationDetail() {
        when(organizationMapper.selectDepartmentById(12L))
                .thenReturn(new Department());
        DepartmentApplication application = new DepartmentApplication();
        application.setId(100L);
        application.setDepartmentId(12L);
        application.setCasId("20240001");
        application.setApplicantName("张三");
        application.setStatus(
                cn.sduonline.join.data.enums.ApplicationStatus.SUBMITTED
        );
        when(applicationMapper.selectApplicationDetail(12L, 100L))
                .thenReturn(application);
        DepartmentApplicationAnswer answer = new DepartmentApplicationAnswer();
        answer.setId(200L);
        answer.setQuestionId(null);
        answer.setQuestionTitle("修改前的题目");
        answer.setQuestionType(QuestionType.SINGLE_CHOICE);
        when(applicationMapper.selectAnswers(100L)).thenReturn(List.of(answer));
        DepartmentApplicationAnswerOption option =
                new DepartmentApplicationAnswerOption();
        option.setAnswerId(200L);
        option.setOptionId(null);
        option.setOptionContent("修改前的选项");
        when(applicationMapper.selectAnswerOptions(200L))
                .thenReturn(List.of(option));

        var result = service.findApplicationDetail(12L, 100L);

        assertTrue(result.isSuccess());
        assertEquals("张三", result.data().applicantName());
        assertEquals(
                "修改前的题目",
                result.data().answers().getFirst().questionTitle()
        );
        assertEquals(
                "修改前的选项",
                result.data().answers().getFirst()
                        .selectedOptions().getFirst().content()
        );
    }

    @Test
    void admitsApplicationInSpecifiedDepartment() {
        when(organizationMapper.selectDepartmentById(12L))
                .thenReturn(new Department());
        DepartmentApplication application = new DepartmentApplication();
        application.setId(100L);
        application.setDepartmentId(12L);
        application.setStatus(ApplicationStatus.SUBMITTED);
        when(applicationMapper.selectByDepartmentAndIdForUpdate(12L, 100L))
                .thenReturn(application);

        var result = service.admit(12L, 100L);

        assertTrue(result.isSuccess());
        assertEquals(ApplicationStatus.ADMISSION_DRAFT, result.data().status());
        verify(applicationMapper).updateStatus(
                12L, 100L, ApplicationStatus.ADMISSION_DRAFT
        );
    }

    @Test
    void rejectsAdmissionForApplicationOutsideDepartment() {
        when(organizationMapper.selectDepartmentById(12L))
                .thenReturn(new Department());
        when(applicationMapper.selectByDepartmentAndIdForUpdate(12L, 100L))
                .thenReturn(null);

        var result = service.admit(12L, 100L);

        assertEquals(BizCode.APPLICATION_NOT_FOUND, result.error());
        verify(applicationMapper, never()).updateStatus(any(), any(), any());
    }

    @Test
    void publishesDraftAdmissionsAndSendsPersonalizedEmails() {
        when(organizationMapper.selectDepartmentById(12L))
                .thenReturn(new Department());
        DepartmentApplication application = new DepartmentApplication();
        application.setId(100L);
        application.setApplicantName("张三");
        application.setEmail("zhangsan@example.com");
        when(applicationMapper.selectAdmissionDraftsForUpdate(12L))
                .thenReturn(List.of(application));
        when(applicationMapper.publishAdmissionDraftsByIds(
                12L, List.of(100L)
        )).thenReturn(1);
        AdmissionPublishRequest request =
                new AdmissionPublishRequest("录取通知", "恭喜你被录取。");

        var result = service.publishAdmissions(12L, request);

        assertTrue(result.isSuccess());
        assertEquals(1, result.data().publishedCount());
        verify(admissionEmailService).enqueue(
                List.of(application), "录取通知", "恭喜你被录取。"
        );
    }

    @Test
    void abortsPublishingIfLockedDraftSetCannotBeUpdatedExactly() {
        when(organizationMapper.selectDepartmentById(12L))
                .thenReturn(new Department());
        DepartmentApplication application = new DepartmentApplication();
        application.setId(100L);
        application.setApplicantName("张三");
        application.setEmail("zhangsan@example.com");
        when(applicationMapper.selectAdmissionDraftsForUpdate(12L))
                .thenReturn(List.of(application));
        when(applicationMapper.publishAdmissionDraftsByIds(
                12L, List.of(100L)
        )).thenReturn(0);

        assertThrows(
                IllegalStateException.class,
                () -> service.publishAdmissions(
                        12L,
                        new AdmissionPublishRequest("录取通知", "恭喜你被录取。")
                )
        );
        verify(admissionEmailService, never()).enqueue(any(), any(), any());
    }

    @Test
    void hidesDraftAdmissionFromApplicant() {
        when(organizationMapper.selectDepartmentById(12L))
                .thenReturn(new Department());
        DepartmentApplication application = new DepartmentApplication();
        application.setId(100L);
        application.setDepartmentId(12L);
        application.setStatus(ApplicationStatus.ADMISSION_DRAFT);
        when(applicationMapper.selectByDepartmentAndUser(12L, "20240001"))
                .thenReturn(application);

        var result = service.findMyApplication(12L, "20240001");

        assertTrue(result.isSuccess());
        assertEquals(ApplicationStatus.SUBMITTED, result.data().status());
    }

    @Test
    void listsAllApplicationsOfCurrentUser() {
        DepartmentApplication first = new DepartmentApplication();
        first.setId(100L);
        first.setDepartmentId(12L);
        first.setDepartmentName("技术部");
        first.setWorkstationId(3L);
        first.setWorkstationName("软件工作站");
        first.setBoardId(1L);
        first.setBoardName("技术板块");
        first.setStatus(ApplicationStatus.ADMITTED);
        DepartmentApplication second = new DepartmentApplication();
        second.setId(101L);
        second.setDepartmentId(13L);
        second.setStatus(ApplicationStatus.ADMISSION_DRAFT);
        when(applicationMapper.selectByUser("20240001"))
                .thenReturn(List.of(first, second));

        var result = service.findMyApplications("20240001");

        assertTrue(result.isSuccess());
        assertEquals(2, result.data().size());
        assertEquals("技术部", result.data().get(0).departmentName());
        assertEquals("软件工作站", result.data().get(0).workstationName());
        assertEquals("技术板块", result.data().get(0).boardName());
        assertEquals(ApplicationStatus.ADMITTED, result.data().get(0).status());
        assertEquals(ApplicationStatus.SUBMITTED, result.data().get(1).status());
    }

    @Test
    void returnsEmptyListWhenUserHasNoApplication() {
        when(applicationMapper.selectByUser("20240001")).thenReturn(List.of());

        var result = service.findMyApplications("20240001");

        assertTrue(result.isSuccess());
        assertTrue(result.data().isEmpty());
    }

    private void prepareEligibleUser() {
        when(organizationMapper.selectDepartmentById(12L))
                .thenReturn(new Department());
        User user = new User();
        user.setProfileCompleted(true);
        when(userMapper.selectById("20240001")).thenReturn(user);
    }

    private void assignApplicationId() {
        doAnswer(invocation -> {
            DepartmentApplication application = invocation.getArgument(0);
            application.setId(100L);
            return 1;
        }).when(applicationMapper).insertApplication(any());
    }

    private static DepartmentQuestion question(
            Long id, QuestionType type, boolean required
    ) {
        DepartmentQuestion question = new DepartmentQuestion();
        question.setId(id);
        question.setTitle("测试题目");
        question.setType(type);
        question.setRequired(required);
        return question;
    }

    private static DepartmentQuestionOption option(Long id, String content) {
        DepartmentQuestionOption option = new DepartmentQuestionOption();
        option.setId(id);
        option.setContent(content);
        return option;
    }
}
