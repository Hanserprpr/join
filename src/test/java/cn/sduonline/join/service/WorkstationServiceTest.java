package cn.sduonline.join.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.sduonline.join.data.enums.BizCode;
import cn.sduonline.join.data.enums.Campus;
import cn.sduonline.join.data.po.Department;
import cn.sduonline.join.data.po.Workstation;
import cn.sduonline.join.mapper.AdminOrganizationMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkstationServiceTest {

    @Mock
    private AdminOrganizationMapper organizationMapper;

    private WorkstationService service;

    @BeforeEach
    void setUp() {
        service = new WorkstationService(organizationMapper);
    }

    @Test
    void findByIdReturnsDepartmentsWithCampus() {
        Workstation workstation = new Workstation();
        workstation.setId(5L);
        workstation.setBoardId(1L);
        workstation.setName("开发工作站");
        workstation.setEnabled(true);
        Department department = new Department();
        department.setId(12L);
        department.setName("后端部门");
        department.setCampus(Campus.SOFTWARE_PARK);
        department.setAssetId(88L);
        when(organizationMapper.selectEnabledWorkstationById(5L))
                .thenReturn(workstation);
        when(organizationMapper.selectEnabledDepartmentsByWorkstation(5L))
                .thenReturn(List.of(department));

        var result = service.findById(5L);

        assertTrue(result.isSuccess());
        assertEquals("开发工作站", result.data().name());
        assertEquals(12L, result.data().departments().getFirst().id());
        assertEquals(
                Campus.SOFTWARE_PARK,
                result.data().departments().getFirst().campus()
        );
        assertEquals(88L, result.data().departments().getFirst().assetId());
    }

    @Test
    void findByIdRejectsMissingOrDisabledWorkstation() {
        when(organizationMapper.selectEnabledWorkstationById(99L))
                .thenReturn(null);

        var result = service.findById(99L);

        assertEquals(BizCode.WORKSTATION_NOT_FOUND, result.error());
        verify(organizationMapper, never())
                .selectEnabledDepartmentsByWorkstation(99L);
    }
}
