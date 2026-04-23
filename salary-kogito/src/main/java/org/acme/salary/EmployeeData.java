package org.acme.salary;

import java.util.Map;

/**
 * Represents employee data fetched from MongoDB
 */
public class EmployeeData {
    public String employeeId;
    public String name;
    public Double ctc;
    public Double cca;
    public String category;
    public String location;
    public Double pfOption;
    public Double professionalTax;
    public Double employeePFOverride;

    public EmployeeData() {}

    public EmployeeData(Map<String, Object> data) {
        if (data != null) {
            this.employeeId = (String) data.get("employeeId");
            this.name = (String) data.get("name");
            this.ctc = asDouble(data.get("ctc"));
            this.cca = asDouble(data.get("cca"));
            this.category = (String) data.get("category");
            this.location = (String) data.get("location");
            this.pfOption = asDouble(data.get("pfOption"));
            this.professionalTax = asDouble(data.get("professionalTax"));
            this.employeePFOverride = asDouble(data.get("employeePFOverride"));
        }
    }

    private static Double asDouble(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return Double.valueOf(value.toString());
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return "EmployeeData{" +
                "employeeId='" + employeeId + '\'' +
                ", name='" + name + '\'' +
                ", ctc=" + ctc +
                ", cca=" + cca +
                ", category='" + category + '\'' +
                ", location='" + location + '\'' +
                ", pfOption=" + pfOption +
                ", professionalTax=" + professionalTax +
                ", employeePFOverride=" + employeePFOverride +
                '}';
    }
}
