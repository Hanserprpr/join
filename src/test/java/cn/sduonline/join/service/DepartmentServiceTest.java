package cn.sduonline.join.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.sduonline.join.data.dto.DepartmentAchievementRequest;
import cn.sduonline.join.data.dto.DepartmentDetailUpdateRequest;
import cn.sduonline.join.data.dto.DepartmentDetailPatchRequest;
import cn.sduonline.join.data.dto.DepartmentPosterOrderItemRequest;
import cn.sduonline.join.data.dto.DepartmentPosterOrderUpdateRequest;
import cn.sduonline.join.data.dto.DepartmentPosterRequest;
import cn.sduonline.join.data.enums.BizCode;
import cn.sduonline.join.data.enums.Campus;
import cn.sduonline.join.data.po.Department;
import cn.sduonline.join.data.po.DepartmentAchievement;
import cn.sduonline.join.data.po.DepartmentPoster;
import cn.sduonline.join.mapper.AdminOrganizationMapper;
import cn.sduonline.join.mapper.DepartmentQuestionnaireMapper;
import cn.sduonline.join.security.scope.OrgType;
import cn.sduonline.join.security.scope.PermissionCode;
import cn.sduonline.join.service.poster.PosterUrlPolicy;
import cn.sduonline.join.service.poster.PosterStorage;
import java.util.List;
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
    @Mock
    private PosterUrlPolicy posterUrlPolicy;
    @Mock
    private PosterStorage posterStorage;

    private DepartmentService service;

    @BeforeEach
    void setUp() {
        service = new DepartmentService(
                organizationMapper, questionnaireMapper, authorizationService,
                posterUrlPolicy, posterStorage
        );
        lenient().when(posterStorage.accessUrl(
                        org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));
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
    void findByIdReturnsPosterAccessUrl() {
        Department department = department();
        DepartmentPoster poster = poster(
                1L, "https://files.example.com/join/posters/example.png", 0
        );
        when(organizationMapper.selectDepartmentById(12L)).thenReturn(department);
        when(organizationMapper.selectDepartmentPosters(12L))
                .thenReturn(java.util.List.of(poster));
        when(posterStorage.accessUrl(poster.getUrl()))
                .thenReturn("https://s3.example/presigned-example.png?signature=x");

        var result = service.findById(12L, null);

        assertTrue(result.isSuccess());
        assertEquals(
                "https://s3.example/presigned-example.png?signature=x",
                result.data().posters().getFirst().url()
        );
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
    void findByIdReflectsRevokedPermissionOnTheVeryNextCall() {
        Department department = department();
        when(organizationMapper.selectDepartmentById(12L)).thenReturn(department);
        when(questionnaireMapper.countQuestions(12L)).thenReturn(0L);
        when(authorizationService.canAccessWithPermission(
                "20240001",
                PermissionCode.RECRUITMENT_MANAGE.code(),
                OrgType.DEPARTMENT,
                12L
        )).thenReturn(true, false);

        assertTrue(service.findById(12L, "20240001").data().canManage());

        // canManage 不做缓存，撤销授权后下一次请求就必须翻转。
        assertFalse(service.findById(12L, "20240001").data().canManage());
        verify(authorizationService, times(2)).canAccessWithPermission(
                "20240001",
                PermissionCode.RECRUITMENT_MANAGE.code(),
                OrgType.DEPARTMENT,
                12L
        );
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
                                null, " https://example.com/poster-1.jpg ", null
                        ),
                        new DepartmentPosterRequest(
                                null, "https://example.com/poster-2.jpg", 5
                        )
                ),
                java.util.List.of(
                        new DepartmentAchievementRequest(" 获奖 ", " 国一 "),
                        new DepartmentAchievementRequest("立项", null)
                ),
                " 要求 ", " QQ：123 ", " 456群 "
        );
        when(organizationMapper.selectDepartmentAchievements(12L))
                .thenReturn(java.util.List.of(
                        achievement("获奖", "国一", 0),
                        achievement("立项", null, 1)
                ));

        var result = service.updateDetail(12L, "20240001", request);

        assertTrue(result.isSuccess());
        assertEquals(Campus.CENTRAL, result.data().campus());
        assertEquals("介绍", result.data().introduction());
        assertEquals(2, result.data().posters().size());
        assertEquals(
                "https://example.com/poster-1.jpg",
                result.data().posters().getFirst().url()
        );
        assertEquals(2, result.data().achievements().size());
        assertEquals("获奖", result.data().achievements().getFirst().title());
        assertEquals("国一", result.data().achievements().getFirst().content());
        assertEquals("要求", result.data().recruitmentRequirements());
        assertEquals("QQ：123", result.data().contact());
        assertEquals("456群", result.data().recruitmentGroup());
        verify(organizationMapper).updateDepartmentDetail(department);
        // 请求全部是新增项，原有海报都不在目标列表里，按 id 删除。
        verify(organizationMapper)
                .deleteDepartmentPostersByIds(12L, List.of(1L, 2L));
        ArgumentCaptor<DepartmentPoster> posterCaptor =
                ArgumentCaptor.forClass(DepartmentPoster.class);
        verify(organizationMapper, org.mockito.Mockito.times(2))
                .insertDepartmentPoster(posterCaptor.capture());
        assertEquals(0, posterCaptor.getAllValues().getFirst().getSortOrder());
        assertEquals(5, posterCaptor.getAllValues().get(1).getSortOrder());
        verify(organizationMapper).deleteDepartmentAchievements(12L);
        ArgumentCaptor<DepartmentAchievement> achievementCaptor =
                ArgumentCaptor.forClass(DepartmentAchievement.class);
        verify(organizationMapper, org.mockito.Mockito.times(2))
                .insertDepartmentAchievement(achievementCaptor.capture());
        DepartmentAchievement first = achievementCaptor.getAllValues().getFirst();
        assertEquals("获奖", first.getTitle());
        assertEquals("国一", first.getContent());
        // 排序取请求数组下标，前端传什么顺序就按什么顺序展示。
        assertEquals(0, first.getSortOrder());
        assertEquals(1, achievementCaptor.getAllValues().get(1).getSortOrder());
        assertNull(achievementCaptor.getAllValues().get(1).getContent());
        verify(posterUrlPolicy).validateAll(request.posters());
    }

    @Test
    void patchDetailKeepsAchievementsThatAreNotPresent() {
        Department department = department();
        when(organizationMapper.selectDepartmentById(12L)).thenReturn(department);
        when(organizationMapper.selectDepartmentAchievements(12L))
                .thenReturn(java.util.List.of(achievement("原成果", "原内容", 0)));
        DepartmentDetailPatchRequest request = new DepartmentDetailPatchRequest();
        request.setContact("新联系方式");

        var result = service.patchDetail(12L, "20240001", request);

        assertTrue(result.isSuccess());
        assertEquals(1, result.data().achievements().size());
        assertEquals("原成果", result.data().achievements().getFirst().title());
        verify(organizationMapper, never()).deleteDepartmentAchievements(12L);
    }

    @Test
    void patchDetailReplacesAchievementsWhenPresent() {
        Department department = department();
        when(organizationMapper.selectDepartmentById(12L)).thenReturn(department);
        DepartmentDetailPatchRequest request = new DepartmentDetailPatchRequest();
        request.setAchievements(java.util.List.of(
                new DepartmentAchievementRequest("新成果", "新内容")
        ));

        var result = service.patchDetail(12L, "20240001", request);

        assertTrue(result.isSuccess());
        verify(organizationMapper).deleteDepartmentAchievements(12L);
        ArgumentCaptor<DepartmentAchievement> captor =
                ArgumentCaptor.forClass(DepartmentAchievement.class);
        verify(organizationMapper).insertDepartmentAchievement(captor.capture());
        assertEquals("新成果", captor.getValue().getTitle());
    }

    @Test
    void patchDetailClearsAchievementsOnExplicitNull() {
        Department department = department();
        when(organizationMapper.selectDepartmentById(12L)).thenReturn(department);
        DepartmentDetailPatchRequest request = new DepartmentDetailPatchRequest();
        request.setAchievements(null);

        var result = service.patchDetail(12L, "20240001", request);

        assertTrue(result.isSuccess());
        assertTrue(result.data().achievements().isEmpty());
        verify(organizationMapper).deleteDepartmentAchievements(12L);
        verify(organizationMapper, never()).insertDepartmentAchievement(
                org.mockito.ArgumentMatchers.any()
        );
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
        verify(posterUrlPolicy).validateAll(null);
        verify(organizationMapper).deleteDepartmentPosters(12L);
    }

    @Test
    void patchDetailDeletesAddsAndReordersPostersInOneSave() {
        Department department = department();
        when(organizationMapper.selectDepartmentById(12L)).thenReturn(department);
        DepartmentPoster keepA = poster(
                1L, "https://files.example.com/join/posters/a.jpg", 0
        );
        DepartmentPoster removeB = poster(
                2L, "https://files.example.com/join/posters/b.jpg", 1
        );
        DepartmentPoster keepC = poster(
                3L, "https://files.example.com/join/posters/c.jpg", 2
        );
        when(organizationMapper.selectDepartmentPostersForUpdate(12L))
                .thenReturn(List.of(keepA, removeB, keepC));
        when(organizationMapper.selectDepartmentPosters(12L))
                .thenReturn(List.of(keepA, removeB, keepC));

        // 一次提交内：删掉 B、保留 C 和 A 并对调顺序、新增一张 D。
        DepartmentDetailPatchRequest request = new DepartmentDetailPatchRequest();
        request.setPosters(List.of(
                new DepartmentPosterRequest(3L, null, null),
                new DepartmentPosterRequest(1L, null, null),
                new DepartmentPosterRequest(
                        null, "https://files.example.com/join/posters/d.jpg", null
                )
        ));

        var result = service.patchDetail(12L, "20240001", request);

        assertTrue(result.isSuccess());
        verify(organizationMapper)
                .deleteDepartmentPostersByIds(12L, List.of(2L));
        // 保留项只调整顺序，行 id 不变，不重新插入。
        verify(organizationMapper).updateDepartmentPosterSortOrder(12L, 3L, 0);
        verify(organizationMapper).updateDepartmentPosterSortOrder(12L, 1L, 1);
        ArgumentCaptor<DepartmentPoster> inserted =
                ArgumentCaptor.forClass(DepartmentPoster.class);
        verify(organizationMapper).insertDepartmentPoster(inserted.capture());
        assertEquals(
                "https://files.example.com/join/posters/d.jpg",
                inserted.getValue().getUrl()
        );
        assertEquals(2, inserted.getValue().getSortOrder());
        verify(organizationMapper, never()).deleteDepartmentPosters(12L);
    }

    @Test
    void patchDetailKeepsPostersReferencedByIdWithoutResendingSignedUrl() {
        // S3 存储下详情返回的是预签名 URL，前端只能按 id 回传保留项。
        Department department = department();
        when(organizationMapper.selectDepartmentById(12L)).thenReturn(department);
        DepartmentPoster existing = poster(
                1L, "https://files.example.com/join/posters/a.jpg", 0
        );
        when(organizationMapper.selectDepartmentPostersForUpdate(12L))
                .thenReturn(List.of(existing));
        when(organizationMapper.selectDepartmentPosters(12L))
                .thenReturn(List.of(existing));
        when(posterStorage.accessUrl(existing.getUrl()))
                .thenReturn(existing.getUrl() + "?X-Amz-Signature=abc");

        DepartmentDetailPatchRequest request = new DepartmentDetailPatchRequest();
        request.setPosters(List.of(new DepartmentPosterRequest(1L, null, null)));

        var result = service.patchDetail(12L, "20240001", request);

        assertTrue(result.isSuccess());
        assertEquals(
                "https://files.example.com/join/posters/a.jpg?X-Amz-Signature=abc",
                result.data().posters().getFirst().url()
        );
        verify(organizationMapper, never()).deleteDepartmentPostersByIds(
                anyLong(), org.mockito.ArgumentMatchers.anyList()
        );
        verify(organizationMapper, never()).insertDepartmentPoster(
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void patchDetailRejectsPosterIdThatDoesNotBelongToDepartment() {
        Department department = department();
        when(organizationMapper.selectDepartmentById(12L)).thenReturn(department);
        when(organizationMapper.selectDepartmentPostersForUpdate(12L))
                .thenReturn(List.of(poster(1L, "https://example.com/a.jpg", 0)));

        DepartmentDetailPatchRequest request = new DepartmentDetailPatchRequest();
        request.setIntroduction("新介绍");
        request.setPosters(List.of(new DepartmentPosterRequest(99L, null, null)));

        var result = service.patchDetail(12L, "20240001", request);

        assertFalse(result.isSuccess());
        assertEquals(BizCode.POSTER_ORDER_CONFLICT, result.error());
        // 冲突在任何写操作之前返回，详情不会被部分提交。
        verify(organizationMapper, never()).updateDepartmentDetail(
                org.mockito.ArgumentMatchers.any()
        );
        verify(organizationMapper, never()).insertDepartmentPoster(
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void patchDetailRejectsDuplicatePosterIdReferences() {
        Department department = department();
        when(organizationMapper.selectDepartmentById(12L)).thenReturn(department);
        when(organizationMapper.selectDepartmentPostersForUpdate(12L))
                .thenReturn(List.of(poster(1L, "https://example.com/a.jpg", 0)));

        DepartmentDetailPatchRequest request = new DepartmentDetailPatchRequest();
        request.setPosters(List.of(
                new DepartmentPosterRequest(1L, null, null),
                new DepartmentPosterRequest(1L, null, null)
        ));

        var result = service.patchDetail(12L, "20240001", request);

        assertFalse(result.isSuccess());
        assertEquals(BizCode.POSTER_ORDER_CONFLICT, result.error());
    }

    @Test
    void reorderPostersSwapsByIdAndReturnsSignedUrlsInTargetOrder() {
        Department first = department();
        DepartmentPoster posterOne = poster(
                1L, "https://files.example.com/join/posters/one.jpg", 0
        );
        DepartmentPoster posterTwo = poster(
                2L, "https://files.example.com/join/posters/two.jpg", 1
        );
        DepartmentPoster reorderedTwo = poster(
                2L, posterTwo.getUrl(), 0
        );
        DepartmentPoster reorderedOne = poster(
                1L, posterOne.getUrl(), 1
        );
        when(organizationMapper.selectDepartmentByIdForUpdate(12L))
                .thenReturn(first);
        when(organizationMapper.selectDepartmentPostersForUpdate(12L))
                .thenReturn(java.util.List.of(posterOne, posterTwo));
        when(organizationMapper.updateDepartmentPosterSortOrder(12L, 1L, 1))
                .thenReturn(1);
        when(organizationMapper.updateDepartmentPosterSortOrder(12L, 2L, 0))
                .thenReturn(1);
        when(organizationMapper.selectDepartmentPosters(12L))
                .thenReturn(java.util.List.of(reorderedTwo, reorderedOne));
        when(posterStorage.accessUrl(posterOne.getUrl()))
                .thenReturn("https://s3.example/one.jpg?signature=one");
        when(posterStorage.accessUrl(posterTwo.getUrl()))
                .thenReturn("https://s3.example/two.jpg?signature=two");

        var result = service.reorderPosters(
                12L,
                posterOrder(
                        new DepartmentPosterOrderItemRequest(2L, 0),
                        new DepartmentPosterOrderItemRequest(1L, 1)
                )
        );

        assertTrue(result.isSuccess());
        assertEquals(2L, result.data().getFirst().id());
        assertEquals(0, result.data().getFirst().sortOrder());
        assertEquals(
                "https://s3.example/two.jpg?signature=two",
                result.data().getFirst().url()
        );
        assertEquals(1L, result.data().get(1).id());
        assertEquals(1, result.data().get(1).sortOrder());
        assertEquals(
                "https://s3.example/one.jpg?signature=one",
                result.data().get(1).url()
        );
        var mapperOrder = inOrder(organizationMapper);
        mapperOrder.verify(organizationMapper).selectDepartmentByIdForUpdate(12L);
        mapperOrder.verify(organizationMapper).selectDepartmentPostersForUpdate(12L);
        mapperOrder.verify(organizationMapper)
                .updateDepartmentPosterSortOrder(12L, 1L, 1);
        mapperOrder.verify(organizationMapper)
                .updateDepartmentPosterSortOrder(12L, 2L, 0);
        mapperOrder.verify(organizationMapper).selectDepartmentPosters(12L);
        verify(posterUrlPolicy, never()).validateAll(
                org.mockito.ArgumentMatchers.any()
        );
        verify(organizationMapper, never()).deleteDepartmentPosters(anyLong());
        verify(organizationMapper, never()).insertDepartmentPoster(
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void reorderPostersIsIdempotentAndKeepsPosterIdentityAndStoredUrl() {
        DepartmentPoster first = poster(
                1L, "https://files.example.com/join/posters/one.jpg", 0
        );
        DepartmentPoster second = poster(
                2L, "https://files.example.com/join/posters/two.jpg", 1
        );
        when(organizationMapper.selectDepartmentByIdForUpdate(12L))
                .thenReturn(department());
        when(organizationMapper.selectDepartmentPostersForUpdate(12L))
                .thenReturn(java.util.List.of(first, second));
        when(organizationMapper.updateDepartmentPosterSortOrder(12L, 1L, 0))
                .thenReturn(1);
        when(organizationMapper.updateDepartmentPosterSortOrder(12L, 2L, 1))
                .thenReturn(1);
        when(organizationMapper.selectDepartmentPosters(12L))
                .thenReturn(java.util.List.of(first, second));
        DepartmentPosterOrderUpdateRequest request = posterOrder(
                new DepartmentPosterOrderItemRequest(1L, 0),
                new DepartmentPosterOrderItemRequest(2L, 1)
        );

        var firstResult = service.reorderPosters(12L, request);
        var secondResult = service.reorderPosters(12L, request);

        assertTrue(firstResult.isSuccess());
        assertTrue(secondResult.isSuccess());
        assertEquals(firstResult.data(), secondResult.data());
        assertEquals(1L, secondResult.data().getFirst().id());
        assertEquals(first.getUrl(), secondResult.data().getFirst().url());
        assertEquals(2L, secondResult.data().get(1).id());
        assertEquals(second.getUrl(), secondResult.data().get(1).url());
        verify(organizationMapper, never()).deleteDepartmentPosters(anyLong());
        verify(organizationMapper, never()).insertDepartmentPoster(
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void reorderPostersRejectsDuplicateIdsWithoutWriting() {
        stubPosterOrderLock(
                java.util.List.of(
                        poster(1L, "https://example.com/one.jpg", 0),
                        poster(2L, "https://example.com/two.jpg", 1)
                )
        );

        var result = service.reorderPosters(
                12L,
                posterOrder(
                        new DepartmentPosterOrderItemRequest(1L, 0),
                        new DepartmentPosterOrderItemRequest(1L, 1)
                )
        );

        assertEquals(BizCode.PARAM_INVALID, result.error());
        verifyNoPosterOrderUpdates();
    }

    @Test
    void reorderPostersRejectsNonContinuousSortOrdersWithoutWriting() {
        stubPosterOrderLock(
                java.util.List.of(
                        poster(1L, "https://example.com/one.jpg", 0),
                        poster(2L, "https://example.com/two.jpg", 1),
                        poster(3L, "https://example.com/three.jpg", 2)
                )
        );

        var result = service.reorderPosters(
                12L,
                posterOrder(
                        new DepartmentPosterOrderItemRequest(1L, 0),
                        new DepartmentPosterOrderItemRequest(2L, 2),
                        new DepartmentPosterOrderItemRequest(3L, 3)
                )
        );

        assertEquals(BizCode.PARAM_INVALID, result.error());
        verifyNoPosterOrderUpdates();
    }

    @Test
    void reorderPostersRejectsDuplicateSortOrdersWithoutWriting() {
        stubPosterOrderLock(
                java.util.List.of(
                        poster(1L, "https://example.com/one.jpg", 0),
                        poster(2L, "https://example.com/two.jpg", 1)
                )
        );

        var result = service.reorderPosters(
                12L,
                posterOrder(
                        new DepartmentPosterOrderItemRequest(1L, 0),
                        new DepartmentPosterOrderItemRequest(2L, 0)
                )
        );

        assertEquals(BizCode.PARAM_INVALID, result.error());
        verifyNoPosterOrderUpdates();
    }

    @Test
    void reorderPostersRejectsStaleOrForeignIdsAsConflictWithoutWriting() {
        stubPosterOrderLock(
                java.util.List.of(
                        poster(1L, "https://example.com/one.jpg", 0),
                        poster(2L, "https://example.com/two.jpg", 1)
                )
        );

        var result = service.reorderPosters(
                12L,
                posterOrder(
                        new DepartmentPosterOrderItemRequest(1L, 0),
                        new DepartmentPosterOrderItemRequest(99L, 1)
                )
        );

        assertEquals(BizCode.POSTER_ORDER_CONFLICT, result.error());
        verifyNoPosterOrderUpdates();
    }

    @Test
    void reorderPostersRejectsMissingIdsAsConflictWithoutWriting() {
        stubPosterOrderLock(
                java.util.List.of(
                        poster(1L, "https://example.com/one.jpg", 0),
                        poster(2L, "https://example.com/two.jpg", 1)
                )
        );

        var result = service.reorderPosters(
                12L,
                posterOrder(new DepartmentPosterOrderItemRequest(1L, 0))
        );

        assertEquals(BizCode.POSTER_ORDER_CONFLICT, result.error());
        verifyNoPosterOrderUpdates();
    }

    @Test
    void reorderPostersAllowsEmptyRequestWhenDepartmentHasNoPosters() {
        stubPosterOrderLock(java.util.List.of());
        when(organizationMapper.selectDepartmentPosters(12L))
                .thenReturn(java.util.List.of());

        var result = service.reorderPosters(
                12L, new DepartmentPosterOrderUpdateRequest(java.util.List.of())
        );

        assertTrue(result.isSuccess());
        assertTrue(result.data().isEmpty());
        verifyNoPosterOrderUpdates();
    }

    @Test
    void reorderPostersRejectsEmptyRequestWhenDepartmentHasPosters() {
        stubPosterOrderLock(java.util.List.of(
                poster(1L, "https://example.com/one.jpg", 0)
        ));

        var result = service.reorderPosters(
                12L, new DepartmentPosterOrderUpdateRequest(java.util.List.of())
        );

        assertEquals(BizCode.POSTER_ORDER_CONFLICT, result.error());
        verifyNoPosterOrderUpdates();
    }

    @Test
    void reorderPostersRejectsMissingDepartmentWithoutReadingOrWritingPosters() {
        when(organizationMapper.selectDepartmentByIdForUpdate(99L))
                .thenReturn(null);

        var result = service.reorderPosters(
                99L,
                posterOrder(new DepartmentPosterOrderItemRequest(1L, 0))
        );

        assertEquals(BizCode.DEPARTMENT_NOT_FOUND, result.error());
        verify(organizationMapper, never()).selectDepartmentPostersForUpdate(anyLong());
        verifyNoPosterOrderUpdates();
    }

    private void stubPosterOrderLock(List<DepartmentPoster> posters) {
        when(organizationMapper.selectDepartmentByIdForUpdate(12L))
                .thenReturn(department());
        when(organizationMapper.selectDepartmentPostersForUpdate(12L))
                .thenReturn(posters);
    }

    private void verifyNoPosterOrderUpdates() {
        verify(organizationMapper, never()).updateDepartmentPosterSortOrder(
                anyLong(), anyLong(), anyInt()
        );
    }

    private static DepartmentPosterOrderUpdateRequest posterOrder(
            DepartmentPosterOrderItemRequest... items
    ) {
        return new DepartmentPosterOrderUpdateRequest(java.util.List.of(items));
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

    private static DepartmentAchievement achievement(
            String title, String content, Integer sortOrder
    ) {
        DepartmentAchievement achievement = new DepartmentAchievement();
        achievement.setDepartmentId(12L);
        achievement.setTitle(title);
        achievement.setContent(content);
        achievement.setSortOrder(sortOrder);
        return achievement;
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
