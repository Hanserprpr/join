package cn.sduonline.join.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.sduonline.join.data.dto.DepartmentDetailUpdateRequest;
import cn.sduonline.join.data.dto.DepartmentDetailPatchRequest;
import cn.sduonline.join.data.dto.DepartmentPosterRequest;
import cn.sduonline.join.data.enums.BizCode;
import cn.sduonline.join.data.enums.Campus;
import cn.sduonline.join.data.po.Department;
import cn.sduonline.join.data.po.DepartmentPoster;
import cn.sduonline.join.mapper.AdminOrganizationMapper;
import cn.sduonline.join.mapper.DepartmentQuestionnaireMapper;
import cn.sduonline.join.security.scope.OrgType;
import cn.sduonline.join.security.scope.PermissionCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DepartmentServiceTest {

    @Mock
    private AdminOrganizationMapper organizationMapper;
    @Mock
    private DepartmentQuestionnaireMapper questionnaireMapper;
    @Mock
    private AuthorizationService authorizationService;

    private DepartmentService service;

    @BeforeEach
    void setUp() {
        service = new DepartmentService(
                organizationMapper, questionnaireMapper, authorizationService
        );
    }

    @Test
    void findByIdReturnsCompleteDepartmentDetail() {
        Department department = department();
        department.setCampus(Campus.CENTRAL);
        department.setIntroduction("组织介绍");
        when(organizationMapper.selectDepartmentById(12L)).thenReturn(department);
        when(questionnaireMapper.countQuestions(12L)).thenReturn(1L);

        var result = service.findById(12L, "20240001");

        assertTrue(result.isSuccess());
        assertEquals(Campus.CENTRAL, result.data().campus());
        assertEquals("组织介绍", result.data().introduction());
        assertTrue(result.data().hasQuestionnaire());
    }

    @Test
    void findByIdReturnsCanManageTrueWhenUserHasPermission() {
        Department department = department();
        when(organizationMapper.selectDepartmentById(12L)).thenReturn(department);
        when(questionnaireMapper.countQuestions(12L)).thenReturn(0L);
        when(authorizationService.canAccessWithPermission(
                "20240001",
                PermissionCode.RECRUITMENT_MANAGE.code(),
                OrgType.DEPARTMENT,
                12L
        )).thenReturn(true);

        var result = service.findById(12L, "20240001");

        assertTrue(result.isSuccess());
        assertTrue(result.data().canManage());
    }

    @Test
    void findByIdReturnsCanManageFalseWhenUserLacksPermission() {
        Department department = department();
        when(organizationMapper.selectDepartmentById(12L)).thenReturn(department);
        when(questionnaireMapper.countQuestions(12L)).thenReturn(0L);
        when(authorizationService.canAccessWithPermission(
                "20240001",
                PermissionCode.RECRUITMENT_MANAGE.code(),
                OrgType.DEPARTMENT,
                12L
        )).thenReturn(false);

        var result = service.findById(12L, "20240001");

        assertTrue(result.isSuccess());
        assertFalse(result.data().canManage());
    }

    @Test
    void findByIdReturnsCanManageFalseWhenNotLoggedIn() {
        Department department = department();
        when(organizationMapper.selectDepartmentById(12L)).thenReturn(department);
        when(questionnaireMapper.countQuestions(12L)).thenReturn(0L);

        var result = service.findById(12L, null);

        assertTrue(result.isSuccess());
        assertFalse(result.data().canManage());
        verify(authorizationService, never()).canAccessWithPermission(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyLong()
        );
    }

    @Test
    void updateDetailUpdatesAllDisplayFields() {
        Department department = department();
        when(organizationMapper.selectDepartmentById(12L)).thenReturn(department);
        DepartmentPoster firstPoster = poster(
                1L, "https://example.com/poster-1.jpg", 0
        );
        DepartmentPoster secondPoster = poster(
                2L, "https://example.com/poster-2.jpg", 5
        );
        when(organizationMapper.selectDepartmentPosters(12L))
                .thenReturn(java.util.List.of(firstPoster, secondPoster));
        DepartmentDetailUpdateRequest request = new DepartmentDetailUpdateRequest(
                Campus.CENTRAL, " 介绍 ",
                java.util.List.of(
                        new DepartmentPosterRequest(
                                " https://example.com/poster-1.jpg ", null
                        ),
                        new DepartmentPosterRequest(
                                "https://example.com/poster-2.jpg", 5
                        )
                ),
                " 成果 ", " 要求 ", " QQ：123 ", " 456群 "
        );

        var result = service.updateDetail(12L, "20240001", request);

        assertTrue(result.isSuccess());
        assertEquals(Campus.CENTRAL, result.data().campus());
        assertEquals("介绍", result.data().introduction());
        assertEquals(2, result.data().posters().size());
        assertEquals(
                "https://example.com/poster-1.jpg",
                result.data().posters().getFirst().url()
        );
        assertEquals("成果", result.data().achievements());
        assertEquals("要求", result.data().recruitmentRequirements());
        assertEquals("QQ：123", result.data().contact());
        assertEquals("456群", result.data().recruitmentGroup());
        verify(organizationMapper).updateDepartmentDetail(department);
        verify(organizationMapper).deleteDepartmentPosters(12L);
        ArgumentCaptor<DepartmentPoster> posterCaptor =
                ArgumentCaptor.forClass(DepartmentPoster.class);
        verify(organizationMapper, org.mockito.Mockito.times(2))
                .insertDepartmentPoster(posterCaptor.capture());
        assertEquals(0, posterCaptor.getAllValues().getFirst().getSortOrder());
        assertEquals(5, posterCaptor.getAllValues().get(1).getSortOrder());
    }

    @Test
    void updateDetailAllowsClearingFields() {
        Department department = department();
        department.setContact("旧联系方式");
        when(organizationMapper.selectDepartmentById(12L)).thenReturn(department);

        var result = service.updateDetail(
                12L,
                "20240001",
                new DepartmentDetailUpdateRequest(
                        null, null, null, null, null, " ", null
                )
        );

        assertTrue(result.isSuccess());
        assertNull(result.data().contact());
    }

    @Test
    void updateDetailRejectsMissingDepartment() {
        when(organizationMapper.selectDepartmentById(99L)).thenReturn(null);

        var result = service.updateDetail(
                99L,
                "20240001",
                new DepartmentDetailUpdateRequest(
                        null, null, null, null, null, null, null
                )
        );

        assertEquals(BizCode.DEPARTMENT_NOT_FOUND, result.error());
        verify(organizationMapper, never()).updateDepartmentDetail(
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void patchDetailKeepsFieldsAndPostersThatAreNotPresent() {
        Department department = department();
        department.setIntroduction("原介绍");
        department.setContact("原联系方式");
        DepartmentPoster poster = poster(1L, "https://example.com/old.jpg", 0);
        when(organizationMapper.selectDepartmentById(12L)).thenReturn(department);
        when(organizationMapper.selectDepartmentPosters(12L))
                .thenReturn(java.util.List.of(poster));
        DepartmentDetailPatchRequest request = new DepartmentDetailPatchRequest();
        request.setIntroduction(" 新介绍 ");

        var result = service.patchDetail(12L, "20240001", request);

        assertTrue(result.isSuccess());
        assertEquals("新介绍", result.data().introduction());
        assertEquals("原联系方式", result.data().contact());
        assertEquals(1, result.data().posters().size());
        verify(organizationMapper, never()).deleteDepartmentPosters(12L);
    }

    @Test
    void patchDetailClearsExplicitNullFieldsAndPosters() {
        Department department = department();
        department.setContact("原联系方式");
        when(organizationMapper.selectDepartmentById(12L)).thenReturn(department);
        DepartmentDetailPatchRequest request = new DepartmentDetailPatchRequest();
        request.setContact(null);
        request.setPosters(null);

        var result = service.patchDetail(12L, "20240001", request);

        assertTrue(result.isSuccess());
        assertNull(result.data().contact());
        verify(organizationMapper).deleteDepartmentPosters(12L);
    }

    private static Department department() {
        Department department = new Department();
        department.setId(12L);
        department.setWorkstationId(5L);
        department.setName("后端部门");
        department.setSortOrder(0);
        department.setEnabled(true);
        return department;
    }

    private static DepartmentPoster poster(Long id, String url, Integer sortOrder) {
        DepartmentPoster poster = new DepartmentPoster();
        poster.setId(id);
        poster.setDepartmentId(12L);
        poster.setUrl(url);
        poster.setSortOrder(sortOrder);
        return poster;
    }
}
