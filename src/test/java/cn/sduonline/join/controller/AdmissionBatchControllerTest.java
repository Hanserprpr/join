package cn.sduonline.join.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.sduonline.join.data.dto.AdmissionBatchVO;
import cn.sduonline.join.service.ApplicationExcelExportService;
import cn.sduonline.join.service.DepartmentApplicationService;
import cn.sduonline.join.service.ServiceResult;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdmissionBatchControllerTest {

    @Test
    void bindsApplicationIdsAndReturnsAdmittedCount() throws Exception {
        var service = mock(DepartmentApplicationService.class);
        when(service.admitBatch(12L, List.of(100L, 101L)))
                .thenReturn(ServiceResult.success(new AdmissionBatchVO(2)));
        var mvc = MockMvcBuilders.standaloneSetup(
                new DepartmentApplicationController(
                        service, mock(ApplicationExcelExportService.class))
        ).build();

        mvc.perform(post("/api/departments/12/applications/admissions/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"applicationIds\":[100,101]}"))
                .andExpect(status().isOk());

        verify(service).admitBatch(12L, List.of(100L, 101L));
    }

    @Test
    void rejectsEmptyApplicationIds() throws Exception {
        var service = mock(DepartmentApplicationService.class);
        var mvc = MockMvcBuilders.standaloneSetup(
                new DepartmentApplicationController(
                        service, mock(ApplicationExcelExportService.class))
        ).build();

        mvc.perform(post("/api/departments/12/applications/admissions/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"applicationIds\":[]}"))
                .andExpect(status().isBadRequest());

        verify(service, never()).admitBatch(any(), any());
    }
}
