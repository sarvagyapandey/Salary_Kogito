package org.acme.salary;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class EmployeeRepository {

    @Inject
    MongoClient mongoClient;

    @ConfigProperty(name = "quarkus.mongodb.database")
    String databaseName;

    public Map<String, Object> findById(String employeeId) {
        // First try to find in simple flat 'employees' collection (demo data)
        MongoCollection<Document> employeesCol = getCollection("employees");
        Document simpleEmployee = employeesCol.find(Filters.eq("employeeId", employeeId)).first();
        if (simpleEmployee != null) {
            return documentToMap(simpleEmployee);
        }

        // Fall back to complex nested structure
        Document employeeMaster = employeeMasterCollection()
                .find(Filters.eq("basicDetails.employeeId", employeeId))
                .first();
        Document payroll = payrollCollection()
                .find(Filters.eq("employeeId", employeeId))
                .first();

        if (employeeMaster == null && payroll == null) {
            return null;
        }

        return mergeEmployeeData(employeeMaster, payroll);
    }

    private Map<String, Object> documentToMap(Document doc) {
        Map<String, Object> map = new LinkedHashMap<>();
        doc.forEach((key, value) -> {
            if (!"_id".equals(key)) {  // Skip MongoDB ID
                map.put(key, value);
            }
        });
        return map;
    }

    private MongoCollection<Document> getCollection(String collectionName) {
        return mongoClient.getDatabase(databaseName).getCollection(collectionName);
    }

    public List<Map<String, Object>> findAll() {
        List<Map<String, Object>> employees = new ArrayList<>();
        Set<String> seenEmployeeIds = new LinkedHashSet<>();

        for (Document employeeMaster : employeeMasterCollection().find()) {
            String employeeId = nestedString(employeeMaster, "basicDetails", "employeeId");
            if (employeeId == null || employeeId.isBlank()) {
                continue;
            }

            Document payroll = payrollCollection()
                    .find(Filters.eq("employeeId", employeeId))
                    .first();

            employees.add(mergeEmployeeData(employeeMaster, payroll));
            seenEmployeeIds.add(employeeId);
        }

        for (Document payroll : payrollCollection().find()) {
            String employeeId = payroll.getString("employeeId");
            if (employeeId == null || seenEmployeeIds.contains(employeeId)) {
                continue;
            }

            employees.add(mergeEmployeeData(null, payroll));
            seenEmployeeIds.add(employeeId);
        }

        return employees;
    }

    private Map<String, Object> mergeEmployeeData(Document employeeMaster, Document payroll) {
        Map<String, Object> employee = new LinkedHashMap<>();

        String employeeId = firstNonBlank(
                payroll == null ? null : payroll.getString("employeeId"),
                nestedString(employeeMaster, "basicDetails", "employeeId")
        );

        employee.put("employeeId", employeeId);
        employee.put("name", nestedString(employeeMaster, "basicDetails", "name"));
        employee.put("ctc", firstNonNullDouble(
                nestedNumber(payroll, "inputSnapshot", "ctc"),
                nestedNumber(employeeMaster, "statutoryBank", "ctc")
        ));
        employee.put("cca", firstNonNullDouble(
                nestedNumber(payroll, "inputSnapshot", "cca"),
                0d
        ));
        employee.put("category", nestedString(payroll, "inputSnapshot", "category"));
        employee.put("location", firstNonBlank(
                nestedString(payroll, "inputSnapshot", "location"),
                nestedString(employeeMaster, "job", "baseLocation")
        ));
        employee.put("pfOption", firstNonNullDouble(
                nestedNumber(payroll, "inputSnapshot", "pfOption"),
                nestedNumber(employeeMaster, "statutoryBank", "pfOption")
        ));
        employee.put("professionalTax", firstNonNullDouble(
                nestedNumber(payroll, "inputSnapshot", "professionalTax"),
                nestedNumber(employeeMaster, "statutoryBank", "professionalTax"),
                0d
        ));
        employee.put("employeePFOverride", firstNonNullDouble(
                nestedNumber(payroll, "inputSnapshot", "employeePfOverride"),
                nestedNumber(payroll, "inputSnapshot", "employeePFOverride")
        ));

        return employee;
    }

    private MongoCollection<Document> employeeMasterCollection() {
        return mongoClient.getDatabase(databaseName).getCollection("employeeMaster");
    }

    private MongoCollection<Document> payrollCollection() {
        return mongoClient.getDatabase(databaseName).getCollection("payroll");
    }

    private String nestedString(Document source, String... path) {
        Object value = nestedValue(source, path);
        return value == null ? null : value.toString();
    }

    private Double nestedNumber(Document source, String... path) {
        Object value = nestedValue(source, path);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Object nestedValue(Document source, String... path) {
        if (source == null || path == null || path.length == 0) {
            return null;
        }

        Object current = source;
        for (String key : path) {
            if (!(current instanceof Map)) {
                return null;
            }
            current = ((Map<String, Object>) current).get(key);
            if (current == null) {
                return null;
            }
        }

        return current;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private Double firstNonNullDouble(Double... values) {
        if (values == null) {
            return null;
        }
        for (Double value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
