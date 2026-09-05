package cn.sduonline.join.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.*;

import cn.sduonline.join.data.dto.ContactUpdateRequest;
import cn.sduonline.join.data.dto.PageVO;
import cn.sduonline.join.data.enums.Campus;
import cn.sduonline.join.service.ApplicationExcelExportService;
import cn.sduonline.join.service.DepartmentApplicationService;
import cn.sduonline.join.service.ServiceResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ApplicationCampusCompatibilityTest {
    @Test
    void oldAndNewListRequestsBindWithUnchangedDefaults() throws Exception {
        var service = mock(DepartmentApplicationService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new DepartmentApplicationController(
                service, mock(ApplicationExcelExportService.class))).build();
        for (Campus campus : new Campus[] {null, Campus.CENTRAL}) {
            when(service.findApplications(12L, null, null, null, null, null,
                    "submittedAt", "desc", 1, 20, campus))
                    .thenReturn(ServiceResult.success(PageVO.of(List.of(), 0L, 1, 20)));
            var request = get("/api/departments/12/applications");
            if (campus != null) request.param("campus", campus.name());
            mvc.perform(request).andExpect(status().isOk());
            verify(service).findApplications(12L, null, null, null, null, null,
                    "submittedAt", "desc", 1, 20, campus);
        }
        mvc.perform(get("/api/departments/12/applications").param("campus", "INVALID"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exportAcceptsOptionalCampus() throws Exception {
        var service = mock(DepartmentApplicationService.class);
        var excel = mock(ApplicationExcelExportService.class);
        when(excel.export(List.of())).thenReturn(new byte[0]);
        var mvc = MockMvcBuilders.standaloneSetup(new DepartmentApplicationController(service, excel)).build();
        for (Campus campus : new Campus[] {null, Campus.CENTRAL}) {
            when(service.findForExport(12L, null, null, null, null, campus))
                    .thenReturn(ServiceResult.success(List.of()));
            var request = get("/api/departments/12/applications/export");
            if (campus != null) request.param("campus", campus.name());
            mvc.perform(request).andExpect(status().isOk());
            verify(service).findForExport(12L, null, null, null, null, campus);
        }
    }

    @Test
    void oldProfileJsonAndOptionalCampusRemainCompatible() throws Exception {
        ObjectMapper json = new ObjectMapper();
        assertNull(json.readValue("{\"qq\":\"123456\"}", ContactUpdateRequest.class).campus());
        assertNull(json.readValue("{\"campus\":null}", ContactUpdateRequest.class).campus());
        assertEquals(Campus.CENTRAL, json.readValue("{\"campus\":\"CENTRAL\"}", ContactUpdateRequest.class).campus());
        assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class,
                () -> json.readValue("{\"campus\":\"INVALID\"}", ContactUpdateRequest.class));
    }
}
