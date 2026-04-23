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

    @Inject
    EmployeeRepository employeeRepository;

    /**
     * Fetch employee details from MongoDB by employee ID
     * Returns: { employeeId, name, ctc, cca, category, location, pfOption, professionalTax, employeePFOverride }
     */
    @Query
    public EmployeeData getEmployee(String employeeId) {
        if (employeeId == null || employeeId.isBlank()) {
            throw new RuntimeException("employeeId is required");
        }

        Map<String, Object> employee = employeeRepository.findById(employeeId);
        if (employee == null) {
            throw new RuntimeException("Employee not found: " + employeeId);
        }

        return new EmployeeData(employee);
    }

    /**
     * Calculate salary using employee data from MongoDB
     * Now only requires: employeeId (fetches other params from DB)
     * 
     * GraphQL Query example:
     * calculateSalaryFromDatabase(employeeId: "emp_16")
     */
    @Query
    public SalaryResponse calculateSalaryFromDatabase(String employeeId) {
        if (employeeId == null || employeeId.isBlank()) {
            throw new RuntimeException("employeeId is required");
        }

        Map<String, Object> employee = employeeRepository.findById(employeeId);
        if (employee == null) {
            throw new RuntimeException("Employee not found: " + employeeId);
        }

        return salaryService.calculate(employee);
    }

    /**
     * GraphQL Query example (legacy - single employee, no DB lookup):
     * calculateSalary(
     *   employeeId: "emp_16",
     *   name: "Bruce",
     *   ctc: 600000,
     *   cca: 10000,
     *   category: "Staff",
     *   location: "Delhi",
     *   pfOption: 3,
     *   professionalTax: 200,
     *   employeePFOverride: 1800
     * )
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

        // Two modes: (a) full payload provided, (b) only employeeId provided -> lookup MongoDB data
        Map<String, Object> employee = new HashMap<>();
        Map<String, Object> stored = employeeRepository.findById(employeeId);

        // If caller omitted CTC, pull everything from the stored MongoDB record
        if ((ctc == null || ctc <= 0) && stored != null) {
            employee.putAll(stored);
        } else {
            if (ctc == null || ctc <= 0) {
                throw new RuntimeException("ctc must be provided and positive");
            }
            employee.put("employeeId", employeeId);
            employee.put("name", name);
            employee.put("ctc", ctc);
            employee.put("cca", cca == null ? 0 : cca);
            employee.put("category", category);
            employee.put("location", location);
            employee.put("pfOption", pfOption);
            employee.put("professionalTax", professionalTax == null ? 0 : professionalTax);
            employee.put("employeePFOverride", employeePFOverride);
        }

        // If any optional fields are missing from manual payload, backfill from repository when available
        if (stored != null) {
            employee.putIfAbsent("name", stored.get("name"));
            employee.putIfAbsent("cca", stored.getOrDefault("cca", 0d));
            employee.putIfAbsent("category", stored.get("category"));
            employee.putIfAbsent("location", stored.get("location"));
            employee.putIfAbsent("pfOption", stored.get("pfOption"));
            employee.putIfAbsent("professionalTax", stored.getOrDefault("professionalTax", 0d));
            employee.putIfAbsent("employeePFOverride", stored.get("employeePFOverride"));
        }

        // Ensure defaults for nullable numbers
        employee.putIfAbsent("professionalTax", 0d);
        employee.putIfAbsent("cca", 0d);

        return salaryService.calculate(employee);
    }
}
