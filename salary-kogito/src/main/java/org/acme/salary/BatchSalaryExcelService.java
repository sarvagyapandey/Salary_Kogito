package org.acme.salary;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Handles Excel uploads for salary calculations and produces a result workbook.
 */
@ApplicationScoped
public class BatchSalaryExcelService {

    private static final List<String> INPUT_COLUMNS = List.of(
            "employeeId", "name", "ctc", "cca", "category", "location",
            "pfOption", "professionalTax", "employeePFOverride"
    );

    private static final List<String> OUTPUT_COLUMNS = List.of(
            "basic", "hra", "specialAllowance", "bonus",
            "grossPayable", "employeePF", "employerPF",
            "employeeESI", "employerESI", "gratuity",
            "medicalInsurance", "tds", "takeHomeSalary"
    );

    @Inject
    SalaryService salaryService;

    public byte[] process(InputStream workbookStream) {
        try (Workbook wb = new XSSFWorkbook(workbookStream);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = wb.getSheetAt(0);
            if (sheet == null) {
                throw new IllegalArgumentException("Workbook has no sheets");
            }

            Map<String, Integer> colIndex = parseHeader(sheet.getRow(0));
            List<Map<String, Object>> inputs = readRows(sheet, colIndex);

            List<SalaryResponse> responses = new ArrayList<>();
            Set<String> dynamicNames = new LinkedHashSet<>();
            for (Map<String, Object> input : inputs) {
                SalaryResponse resp = salaryService.calculate(input);
                responses.add(resp);
                if (resp.components != null) {
                    dynamicNames.addAll(resp.components.keySet());
                }
            }

            Workbook resultWb = new XSSFWorkbook();
            Sheet resultSheet = resultWb.createSheet("SalaryOutput");
            writeHeader(resultSheet, dynamicNames);
            int rowNum = 1;
            for (int i = 0; i < inputs.size(); i++) {
                writeRow(resultSheet, rowNum++, inputs.get(i), responses.get(i), dynamicNames);
            }

            int totalCols = INPUT_COLUMNS.size() + OUTPUT_COLUMNS.size() + dynamicNames.size();
            for (int i = 0; i < totalCols; i++) {
                resultSheet.autoSizeColumn(i);
            }

            resultWb.write(out);
            resultWb.close();
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to process salary workbook", e);
        }
    }

    public byte[] template(List<Map<String, Object>> sampleEmployees) {
        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Employees");
            writeTemplateHeader(sheet);
            int row = 1;
            List<Map<String, Object>> rows = (sampleEmployees == null || sampleEmployees.isEmpty())
                    ? List.of(exampleEmployee())
                    : sampleEmployees;
            for (Map<String, Object> emp : rows) {
                Row r = sheet.createRow(row++);
                int c = 0;
                for (String col : INPUT_COLUMNS) {
                    Object val = emp.get(col);
                    writeCell(r, c++, val);
                }
            }
            for (int i = 0; i < INPUT_COLUMNS.size(); i++) {
                sheet.autoSizeColumn(i);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to build template", e);
        }
    }

    private Map<String, Integer> parseHeader(Row header) {
        if (header == null) {
            throw new IllegalArgumentException("First row must contain headers");
        }
        Map<String, Integer> map = new HashMap<>();
        for (Cell cell : header) {
            String name = cell.getStringCellValue();
            if (name != null && !name.isBlank()) {
                map.put(name.trim(), cell.getColumnIndex());
            }
        }
        for (String required : INPUT_COLUMNS) {
            if (!map.containsKey(required)) {
                throw new IllegalArgumentException("Missing required column: " + required);
            }
        }
        return map;
    }

    private List<Map<String, Object>> readRows(Sheet sheet, Map<String, Integer> colIndex) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            Map<String, Object> input = new HashMap<>();
            boolean hasData = false;
            for (String col : INPUT_COLUMNS) {
                Object value = readCell(row.getCell(colIndex.get(col)));
                if (value != null && !(value instanceof String && ((String) value).isBlank())) {
                    hasData = true;
                }
                input.put(col, value);
            }
            if (hasData) {
                rows.add(input);
            }
        }
        return rows;
    }

    private void writeHeader(Sheet sheet, Set<String> dynamicNames) {
        Row header = sheet.createRow(0);
        int c = 0;
        for (String col : INPUT_COLUMNS) {
            header.createCell(c++).setCellValue(col);
        }
        for (String col : OUTPUT_COLUMNS) {
            header.createCell(c++).setCellValue(col);
        }
        for (String name : dynamicNames) {
            header.createCell(c++).setCellValue(name);
        }
    }

    private void writeTemplateHeader(Sheet sheet) {
        Row header = sheet.createRow(0);
        int c = 0;
        for (String col : INPUT_COLUMNS) {
            header.createCell(c++).setCellValue(col);
        }
    }

    private void writeRow(Sheet sheet, int rowNum, Map<String, Object> input, SalaryResponse resp, Set<String> dynamicNames) {
        Row row = sheet.createRow(rowNum);
        int c = 0;
        for (String col : INPUT_COLUMNS) {
            writeCell(row, c++, input.get(col));
        }
        Map<String, Object> outMap = responseMap(resp);
        for (String col : OUTPUT_COLUMNS) {
            writeCell(row, c++, outMap.get(col));
        }
        if (resp.components != null) {
            for (String name : dynamicNames) {
                writeCell(row, c++, resp.components.get(name));
            }
        }
    }

    private Map<String, Object> responseMap(SalaryResponse resp) {
        Map<String, Object> map = new HashMap<>();
        map.put("basic", resp.basic);
        map.put("hra", resp.hra);
        map.put("specialAllowance", resp.specialAllowance);
        map.put("bonus", resp.bonus);
        map.put("grossPayable", resp.grossPayable);
        map.put("employeePF", resp.employeePF);
        map.put("employerPF", resp.employerPF);
        map.put("employeeESI", resp.employeeESI);
        map.put("employerESI", resp.employerESI);
        map.put("gratuity", resp.gratuity);
        map.put("medicalInsurance", resp.medicalInsurance);
        map.put("tds", resp.tds);
        map.put("takeHomeSalary", resp.takeHomeSalary);
        return map;
    }

    private Object readCell(Cell cell) {
        if (cell == null) return null;
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                return cell.getNumericCellValue();
            case BOOLEAN:
                return cell.getBooleanCellValue();
            default:
                return null;
        }
    }

    private void writeCell(Row row, int col, Object value) {
        Cell cell = row.createCell(col);
        if (value == null) return;
        if (value instanceof Number) {
            cell.setCellValue(((Number) value).doubleValue());
        } else if (value instanceof Boolean) {
            cell.setCellValue((Boolean) value);
        } else {
            cell.setCellValue(String.valueOf(value));
        }
    }

    private Map<String, Object> exampleEmployee() {
        Map<String, Object> m = new java.util.HashMap<>();
        m.put("employeeId", "emp_sample");
        m.put("name", "Sample User");
        m.put("ctc", 50000);
        m.put("cca", 2000);
        m.put("category", "Sample");
        m.put("location", "City");
        m.put("pfOption", "P2");
        m.put("professionalTax", 200);
        m.put("employeePFOverride", null);
        return m;
    }
}
