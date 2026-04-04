package org.acme.salary;

import java.util.List;
import java.util.Map;

public class SalaryResponse {

    // Basic details
    public String employeeId;
    public String name;
    public Double ctc;
    public Double cca;
    public String category;
    public String location;
    public String pfOption;
    public Double professionalTax;

    // Rule-driven amounts
    public Double basic;
    public Double basicStat;
    public Double hra;
    public Double bonus;
    public Double gratuity;
    public Double employeePF;
    public Double employerPF;
    public Double employeeESI;
    public Double employerESI;
    public Double medicalInsurance;

    // Tax fields
    public Double taxSlabBase;
    public Double taxMultiplier;
    public Double taxAfterRebate;
    public Double taxWithCess;

    // Derived values
    public Double grossPayable;
    public Double specialAllowance;
    public Double annualGross;
    public Double tds;
    public Double takeHomeSalary;

    // Dynamic components added via rules
    public Map<String, Double> components;
    public Map<String, String> componentTypes;
    public List<ComponentDto> componentList;

    // GraphQL-friendly dynamic component DTO
    public static final class ComponentDto {
        public String name;
        public Double amount;
        public String type;

        public ComponentDto() {}

        public ComponentDto(String name, Double amount, String type) {
            this.name = name;
            this.amount = amount;
            this.type = type;
        }
    }
}