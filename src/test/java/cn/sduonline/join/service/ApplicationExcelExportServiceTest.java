package cn.sduonline.join.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.sduonline.join.data.dto.ApplicationAnswerOptionVO;
import cn.sduonline.join.data.dto.ApplicationAnswerVO;
import cn.sduonline.join.data.dto.DepartmentApplicationExportVO;
import cn.sduonline.join.data.dto.InterviewEvaluationVO;
import cn.sduonline.join.data.enums.ApplicationStatus;
import cn.sduonline.join.data.enums.QuestionType;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ApplicationExcelExportServiceTest {

    @Test
    void exportsApplicantDataAndQuestionnaireAnswers() throws Exception {
        DepartmentApplicationExportVO application =
                new DepartmentApplicationExportVO(
                        100L, "20240001", "张三",
                        "软件学院", "软件工程", 2024,
                        "13900000000", "student@sdu.edu.cn", "123456",
                        ApplicationStatus.SUBMITTED,
                        LocalDateTime.of(2026, 7, 25, 15, 0),
                        List.of(new ApplicationAnswerVO(
                                1L, "意向方向", QuestionType.SINGLE_CHOICE,
                                null,
                                List.of(new ApplicationAnswerOptionVO(10L, "后端"))
                        )),
                        true,
                        List.of(
                                new InterviewEvaluationVO(
                                        9L, "admin1", "李四",
                                        new BigDecimal("4.5"), "表现不错",
                                        LocalDateTime.of(2026, 7, 26, 10, 0)
                                ),
                                new InterviewEvaluationVO(
                                        9L, "admin2", "王五",
                                        new BigDecimal("5.0"), null,
                                        LocalDateTime.of(2026, 7, 26, 10, 5)
                                )
                        ),
                        new BigDecimal("4.8")
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
            assertEquals("是否已面试", sheet.getRow(0).getCell(12).getStringCellValue());
            assertEquals("是", sheet.getRow(1).getCell(12).getStringCellValue());
            assertEquals("面试官评价", sheet.getRow(0).getCell(13).getStringCellValue());
            assertEquals(
                    "李四（4.5分）：表现不错\n王五（5.0分）",
                    sheet.getRow(1).getCell(13).getStringCellValue()
            );
            assertEquals("平均分", sheet.getRow(0).getCell(14).getStringCellValue());
            assertEquals(4.8, sheet.getRow(1).getCell(14).getNumericCellValue());
        }
    }
}
