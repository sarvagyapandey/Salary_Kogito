package org.acme.salary;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

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
            "medicalInsurance", "tds", "takeHomeSalary",
            "errors"
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
                if (resp.getComponents() != null && !resp.getComponents().isEmpty()) {
                    System.out.println("DEBUG: Employee " + input.get("employeeId") + " has components: " + resp.getComponents().keySet());
                    dynamicNames.addAll(resp.getComponents().keySet());
                } else {
                    System.out.println("DEBUG: Employee " + input.get("employeeId") + " has NO components");
                }
            }
            System.out.println("DEBUG: Total dynamic component names collected: " + dynamicNames);

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

        // Accept either "ctc" or the clearer label "ctcMonthly".
        if (!map.containsKey("ctc") && map.containsKey("ctcMonthly")) {
            map.put("ctc", map.get("ctcMonthly"));
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
            input.put("_rowNumber", i + 1); // 1-based row number for validation messages
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
        System.out.println("DEBUG: writeHeader - dynamicNames size = " + dynamicNames.size() + ", names = " + dynamicNames);
        for (String name : dynamicNames) {
            System.out.println("DEBUG: Adding header column for component: " + name);
            header.createCell(c++).setCellValue(name);
        }
    }

    private void writeTemplateHeader(Sheet sheet) {
        Row header = sheet.createRow(0);
        int c = 0;
        for (String col : INPUT_COLUMNS) {
            // Use ctcMonthly for clarity while still accepting "ctc" in uploads.
            String label = "ctc".equals(col) ? "ctcMonthly" : col;
            header.createCell(c++).setCellValue(label);
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
        if (resp.getComponents() != null && !resp.getComponents().isEmpty()) {
            System.out.println("DEBUG: Writing " + dynamicNames.size() + " dynamic components for row " + rowNum);
            for (String name : dynamicNames) {
                Double value = resp.getComponents().get(name);
                System.out.println("  DEBUG: Component '" + name + "' = " + value);
                writeCell(row, c++, value);
            }
        } else {
            System.out.println("DEBUG: Row " + rowNum + " has no components to write");
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
        if (resp.getErrors() != null && !resp.getErrors().isEmpty()) {
            map.put("errors", String.join("; ", resp.getErrors()));
        }
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
            // POI expects a double for numeric cells; use doubleValue to handle all Number types.
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
