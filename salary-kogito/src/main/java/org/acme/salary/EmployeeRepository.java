package org.acme.salary;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class EmployeeRepository {

    private List<Map<String, Object>> employees;

    @PostConstruct
    void loadData() {
        try {
            ObjectMapper mapper = new ObjectMapper();

            InputStream is = getClass()
                    .getClassLoader()
                    .getResourceAsStream("employees.json");

            if (is == null) {
                throw new RuntimeException("employees.json not found in resources");
            }

            employees = mapper.readValue(is, new TypeReference<List<Map<String, Object>>>() {});

        } catch (Exception e) {
            throw new RuntimeException("Failed to load employees.json", e);
        }
    }

    public Map<String, Object> findById(String employeeId) {
        return employees.stream()
                .filter(emp -> employeeId.equals(emp.get("employeeId")))
                .findFirst()
                .orElse(null);
    }

    public List<Map<String, Object>> findAll() {
        return employees;
    }
}
