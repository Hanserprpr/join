package cn.sduonline.join.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.sduonline.join.data.dto.DepartmentQuestionRequest;
import cn.sduonline.join.data.dto.DepartmentQuestionnaireUpdateRequest;
import cn.sduonline.join.data.dto.QuestionOptionRequest;
import cn.sduonline.join.data.enums.BizCode;
import cn.sduonline.join.data.enums.QuestionType;
import cn.sduonline.join.data.po.Department;
import cn.sduonline.join.data.po.DepartmentQuestion;
import cn.sduonline.join.data.po.DepartmentQuestionOption;
import cn.sduonline.join.mapper.AdminOrganizationMapper;
import cn.sduonline.join.mapper.DepartmentQuestionnaireMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DepartmentQuestionnaireServiceTest {

    @Mock
    private AdminOrganizationMapper organizationMapper;
    @Mock
    private DepartmentQuestionnaireMapper questionnaireMapper;

    private DepartmentQuestionnaireService service;

    @BeforeEach
    void setUp() {
        service = new DepartmentQuestionnaireService(
                organizationMapper, questionnaireMapper
        );
    }

    @Test
    void findReturnsUnconfiguredQuestionnaireWhenThereAreNoQuestions() {
        when(organizationMapper.selectDepartmentById(12L))
                .thenReturn(new Department());
        when(questionnaireMapper.selectQuestions(12L)).thenReturn(List.of());

        var result = service.findByDepartmentId(12L);

        assertTrue(result.isSuccess());
        assertFalse(result.data().configured());
        assertTrue(result.data().questions().isEmpty());
    }

    @Test
    void replacePersistsQuestionAndOrderedOptions() {
        when(organizationMapper.selectDepartmentById(12L))
                .thenReturn(new Department());
        when(questionnaireMapper.selectQuestions(12L)).thenReturn(List.of());
        DepartmentQuestionnaireUpdateRequest request =
                new DepartmentQuestionnaireUpdateRequest(List.of(
                        new DepartmentQuestionRequest(
                                " 意向方向 ",
                                " 最多选择两个 ",
                                QuestionType.MULTIPLE_CHOICE,
                                true,
                                List.of(
                                        new QuestionOptionRequest(" 后端 "),
                                        new QuestionOptionRequest(" 前端 ")
                                )
                        )
                ));

        var result = service.replace(12L, request);

        assertTrue(result.isSuccess());
        verify(questionnaireMapper).deleteQuestions(12L);
        ArgumentCaptor<DepartmentQuestion> questionCaptor =
                ArgumentCaptor.forClass(DepartmentQuestion.class);
        verify(questionnaireMapper).insertQuestion(questionCaptor.capture());
        assertEquals("意向方向", questionCaptor.getValue().getTitle());
        assertEquals(0, questionCaptor.getValue().getSortOrder());
        ArgumentCaptor<DepartmentQuestionOption> optionCaptor =
                ArgumentCaptor.forClass(DepartmentQuestionOption.class);
        verify(questionnaireMapper, org.mockito.Mockito.times(2))
                .insertOption(optionCaptor.capture());
        assertEquals("后端", optionCaptor.getAllValues().getFirst().getContent());
        assertEquals(1, optionCaptor.getAllValues().get(1).getSortOrder());
    }

    @Test
    void replaceRejectsMissingDepartmentWithoutDeletingAnything() {
        when(organizationMapper.selectDepartmentById(99L)).thenReturn(null);

        var result = service.replace(
                99L, new DepartmentQuestionnaireUpdateRequest(List.of())
        );

        assertEquals(BizCode.DEPARTMENT_NOT_FOUND, result.error());
        verify(questionnaireMapper, never()).deleteQuestions(99L);
    }
}
