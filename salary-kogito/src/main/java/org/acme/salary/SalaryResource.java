package org.acme.salary;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Query;

@GraphQLApi
public class SalaryResource {

    @Inject
    SalaryService salaryService;

    /**
     * GraphQL Query:
     * calculateSalary(employeeId: "emp_16", ctc: 600000, cca: 10000, ...)
     * 
     * Accepts all employee data directly - no database lookup needed.
     * Employees are provided from uploaded spreadsheet.
     */
    @Query
    public SalaryResponse calculateSalary(
            String employeeId,
            String name,
            Double ctc,
            Double cca,
            String category,
            String location,
            Double pfOption,
            Double professionalTax,
            Double employeePFOverride) {

        if (employeeId == null || employeeId.isEmpty()) {
            throw new RuntimeException("employeeId must be provided");
        }

        if (ctc == null || ctc <= 0) {
            throw new RuntimeException("ctc must be provided and positive");
        }

        // Build employee map from parameters
        Map<String, Object> employee = new HashMap<>();
        employee.put("employeeId", employeeId);
        employee.put("name", name);
        employee.put("ctc", ctc);
        employee.put("cca", cca == null ? 0 : cca);
        employee.put("category", category);
        employee.put("location", location);
        employee.put("pfOption", pfOption);
        employee.put("professionalTax", professionalTax == null ? 0 : professionalTax);
        employee.put("employeePFOverride", employeePFOverride);

        // Call DMN service with provided employee data
        return salaryService.calculate(employee);
    }
}
