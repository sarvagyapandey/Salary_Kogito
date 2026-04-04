package org.acme.salary;

import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Query;

import javax.inject.Inject;
import java.util.Map;

@GraphQLApi
public class SalaryResource {

    @Inject
    EmployeeRepository employeeRepository;

    @Inject
    SalaryService salaryService;

    /**
     * GraphQL Query:
     * calculateSalary(employeeId: "emp_16")
     */
    @Query
    public SalaryResponse calculateSalary(String employeeId) {

        if (employeeId == null || employeeId.isEmpty()) {
            throw new RuntimeException("employeeId must be provided");
        }

        // Fetch from mock DB
        Map<String, Object> employee = employeeRepository.findById(employeeId);

        if (employee == null) {
            throw new RuntimeException("Employee not found: " + employeeId);
        }

        // Call DMN service
        return salaryService.calculate(employee);
    }
}
