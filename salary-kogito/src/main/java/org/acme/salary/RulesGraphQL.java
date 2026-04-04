package org.acme.salary;

import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Mutation;
import org.eclipse.microprofile.graphql.Query;

import javax.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@GraphQLApi
public class RulesGraphQL {

    @Inject
    BatchSalaryExcelService batchSalaryExcelService;

    @Inject
    EmployeeRepository employeeRepository;

    @Inject
    SpreadsheetSalaryService spreadsheetSalaryService;

    /**
     * Download the current rules workbook as base64-encoded XLSX.
     */
    @Query("rulesWorkbook")
    public String rulesWorkbook() {
        var path = spreadsheetSalaryService.currentRulesPath();
        if (path == null || !Files.exists(path)) {
            throw new RuntimeException("Rules workbook not found");
        }
        try {
            byte[] bytes = Files.readAllBytes(path);
            return Base64.getEncoder().encodeToString(bytes);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read rules workbook", e);
        }
    }

    /**
     * Upload a new rules workbook (base64-encoded XLSX). Reloads rules.
     */
    @Mutation("uploadRulesWorkbook")
    public boolean uploadRulesWorkbook(String workbookBase64) {
        if (workbookBase64 == null || workbookBase64.isBlank()) {
            throw new RuntimeException("workbookBase64 is required");
        }
        byte[] bytes = Base64.getDecoder().decode(workbookBase64);
        spreadsheetSalaryService.replaceRules(new ByteArrayInputStream(bytes));
        return true;
    }

    /**
     * Download an HR-ready employee input template as base64-encoded XLSX.
     */
    @Query("employeeTemplate")
    public String employeeTemplate() {
        List<Map<String, Object>> sample = employeeRepository.findAll();
        byte[] workbook = batchSalaryExcelService.template(sample);
        String b64 = Base64.getEncoder().encodeToString(workbook);
        // Return as data URI to avoid empty-looking value in some GraphQL UIs
        return "data:application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;base64," + b64;
    }

    /**
     * Upload filled employee workbook (base64 XLSX); returns output workbook (base64 XLSX).
     */
    @Mutation("processEmployeesWorkbook")
    public String processEmployeesWorkbook(String workbookBase64) {
        if (workbookBase64 == null || workbookBase64.isBlank()) {
            throw new RuntimeException("workbookBase64 is required");
        }
        byte[] bytes = Base64.getDecoder().decode(workbookBase64);
        byte[] result = batchSalaryExcelService.process(new ByteArrayInputStream(bytes));
        return Base64.getEncoder().encodeToString(result);
    }
}
