package cn.sduonline.join.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.sduonline.join.data.dto.ApplicationAnswerOptionVO;
import cn.sduonline.join.data.dto.ApplicationAnswerVO;
import cn.sduonline.join.data.dto.DepartmentApplicationDetailVO;
import cn.sduonline.join.data.enums.ApplicationStatus;
import cn.sduonline.join.data.enums.QuestionType;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ApplicationExcelExportServiceTest {

    @Test
    void exportsApplicantDataAndQuestionnaireAnswers() throws Exception {
        DepartmentApplicationDetailVO application =
                new DepartmentApplicationDetailVO(
                        100L, 12L, "20240001", "张三",
                        "软件学院", "软件工程", 2024,
                        "13900000000", "student@sdu.edu.cn", "123456",
                        ApplicationStatus.SUBMITTED,
                        LocalDateTime.of(2026, 7, 25, 15, 0),
                        List.of(new ApplicationAnswerVO(
                                1L, "意向方向", QuestionType.SINGLE_CHOICE,
                                null,
                                List.of(new ApplicationAnswerOptionVO(10L, "后端"))
                        )),
                        null
                );

        byte[] content = new ApplicationExcelExportService()
                .export(List.of(application));

        assertTrue(content.length > 0);
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            var sheet = workbook.getSheet("报名信息");
            assertEquals("学号", sheet.getRow(0).getCell(1).getStringCellValue());
            assertEquals(
                    "20240001",
                    sheet.getRow(1).getCell(1).getStringCellValue()
            );
            assertEquals(
                    "意向方向：后端",
                    sheet.getRow(1).getCell(11).getStringCellValue()
            );
        }
    }
}
