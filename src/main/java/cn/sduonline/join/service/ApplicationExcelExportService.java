package cn.sduonline.join.service;

import cn.sduonline.join.data.dto.ApplicationAnswerVO;
import cn.sduonline.join.data.dto.DepartmentApplicationDetailVO;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

@Service
public class ApplicationExcelExportService {

    private static final int EXCEL_CELL_TEXT_LIMIT = 32767;
    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public byte[] export(List<DepartmentApplicationDetailVO> applications) {
        try (Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("报名信息");
            CellStyle headerStyle = createHeaderStyle(workbook);
            String[] headers = {
                "报名ID", "学号", "姓名", "学院", "专业", "年级",
                "手机号", "邮箱", "QQ", "报名状态", "提交时间", "问卷答案"
            };
            Row header = sheet.createRow(0);
            for (int index = 0; index < headers.length; index++) {
                Cell cell = header.createCell(index);
                cell.setCellValue(headers[index]);
                cell.setCellStyle(headerStyle);
            }
            for (int index = 0; index < applications.size(); index++) {
                writeRow(sheet.createRow(index + 1), applications.get(index));
            }
            int[] widths = {
                12, 16, 14, 24, 20, 10, 16, 28, 16, 14, 22, 60
            };
            for (int index = 0; index < widths.length; index++) {
                sheet.setColumnWidth(index, widths[index] * 256);
            }
            sheet.createFreezePane(0, 1);
            sheet.setAutoFilter(
                    new org.apache.poi.ss.util.CellRangeAddress(
                            0, Math.max(0, applications.size()), 0, headers.length - 1
                    )
            );
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("生成报名信息 Excel 失败", exception);
        }
    }

    private static void writeRow(
            Row row,
            DepartmentApplicationDetailVO application
    ) {
        setNumber(row, 0, application.id());
        setText(row, 1, application.casId());
        setText(row, 2, application.applicantName());
        setText(row, 3, application.college());
        setText(row, 4, application.major());
        if (application.grade() != null) {
            row.createCell(5).setCellValue(application.grade());
        }
        setText(row, 6, application.phone());
        setText(row, 7, application.email());
        setText(row, 8, application.qq());
        setText(row, 9, application.status().name());
        setText(
                row, 10,
                application.submittedAt() == null
                        ? null
                        : DATE_TIME_FORMAT.format(application.submittedAt())
        );
        setText(row, 11, formatAnswers(application.answers()));
    }

    private static String formatAnswers(List<ApplicationAnswerVO> answers) {
        return answers.stream()
                .map(answer -> {
                    String value = answer.answerText();
                    if (value == null || value.isBlank()) {
                        value = answer.selectedOptions().stream()
                                .map(option -> option.content())
                                .collect(java.util.stream.Collectors.joining("、"));
                    }
                    return answer.questionTitle() + "：" + value;
                })
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    private static CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private static void setText(Row row, int column, String value) {
        String safeValue = value == null ? "" : value;
        if (safeValue.length() > EXCEL_CELL_TEXT_LIMIT) {
            safeValue = safeValue.substring(0, EXCEL_CELL_TEXT_LIMIT);
        }
        row.createCell(column).setCellValue(safeValue);
    }

    private static void setNumber(Row row, int column, Long value) {
        if (value != null) {
            row.createCell(column).setCellValue(value);
        }
    }
}
