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
    public Double pfOption;
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

    // Validation / user-facing errors (non-fatal)
    private java.util.List<String> errors;

    // Dynamic components added via rules (private with getters/setters for proper serialization)
    private Map<String, Double> components;
    private Map<String, String> componentTypes;
    private List<ComponentDto> componentList;

    // Explicit getters to ensure GraphQL schema exposes these commonly requested fields
    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
    public String getName() { return name; }
    public Double getCtc() { return ctc; }
    public Double getCca() { return cca; }
    public String getCategory() { return category; }
    public String getLocation() { return location; }
    public Double getPfOption() { return pfOption; }
    public Double getProfessionalTax() { return professionalTax; }

    public Double getBasic() { return basic; }
    public Double getBasicStat() { return basicStat; }
    public Double getHra() { return hra; }
    public Double getBonus() { return bonus; }
    public Double getGratuity() { return gratuity; }
    public Double getEmployeePF() { return employeePF; }
    public Double getEmployerPF() { return employerPF; }
    public Double getEmployeeESI() { return employeeESI; }
    public Double getEmployerESI() { return employerESI; }
    public Double getMedicalInsurance() { return medicalInsurance; }

    public Double getTaxSlabBase() { return taxSlabBase; }
    public Double getTaxMultiplier() { return taxMultiplier; }
    public Double getTaxAfterRebate() { return taxAfterRebate; }
    public Double getTaxWithCess() { return taxWithCess; }

    public Double getGrossPayable() { return grossPayable; }
    public Double getSpecialAllowance() { return specialAllowance; }
    public Double getAnnualGross() { return annualGross; }
    public Double getTds() { return tds; }
    public Double getTakeHomeSalary() { return takeHomeSalary; }

    public java.util.List<String> getErrors() { return errors; }
    public void setErrors(java.util.List<String> errors) { this.errors = errors; }

    public Map<String, Double> getComponents() { return components; }
    public void setComponents(Map<String, Double> components) { this.components = components; }
    
    public Map<String, String> getComponentTypes() { return componentTypes; }
    public void setComponentTypes(Map<String, String> componentTypes) { this.componentTypes = componentTypes; }
    
    public List<ComponentDto> getComponentList() { return componentList; }
    public void setComponentList(List<ComponentDto> componentList) { this.componentList = componentList; }

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
