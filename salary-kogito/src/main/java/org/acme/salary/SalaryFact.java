package org.acme.salary;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public class SalaryFact {

    // Inputs
    private String employeeId;
    private String name;
    private Double ctc;
    private Double cca;
    private String category;
    private String location;
    private String pfOption;
    private Double professionalTax;
    private Double employeePFOverride;

    // Rule-driven values
    private Double basic;
    private Double basicStat;
    private Double hra;
    private Double bonus;
    private Double gratuity;
    private Double employeePF;
    private Double employerPF;
    private Double employeeESI;
    private Double employerESI;
    private Double medicalInsurance;
    private Double taxSlabBase;
    private Double taxAfterRebate;
    private Double taxMultiplier;
    private Double taxWithCess;
    private Double tds;

    // Derived values
    private Double grossPayable;
    private Double specialAllowance;
    private Double annualGross;
    private Double takeHomeSalary;

    // Components
    private final Map<String, Component> components = new HashMap<>();
    private final EnumMap<ComponentType, Double> componentTotals = new EnumMap<>(ComponentType.class);

    public SalaryFact() {
        this.basic = 0d;
        this.basicStat = 0d;
        this.hra = 0d;
        this.bonus = 0d;
        this.gratuity = 0d;
        this.employeePF = 0d;
        this.employerPF = 0d;
        this.employeeESI = 0d;
        this.employerESI = 0d;
        this.medicalInsurance = 0d;
        this.taxSlabBase = 0d;
        this.taxAfterRebate = 0d;
        this.taxMultiplier = 1.0d;
        this.taxWithCess = 0d;
        this.tds = 0d;
        for (ComponentType type : ComponentType.values()) {
            componentTotals.put(type, 0d);
        }
    }

    // Factory
    public static SalaryFact from(Map<String, Object> input) {
        SalaryFact fact = new SalaryFact();
        fact.employeeId = (String) input.get("employeeId");
        fact.name = (String) input.get("name");
        fact.ctc = getNumber(input.get("ctc"));
        fact.cca = getNumber(input.get("cca"));
        fact.category = (String) input.get("category");
        fact.location = (String) input.get("location");
        fact.pfOption = (String) input.get("pfOption");
        fact.professionalTax = getNumber(input.get("professionalTax"));
        fact.employeePFOverride = getNumber(input.get("employeePFOverride"));
        return fact;
    }

    private static Double getNumber(Object val) {
        if (val == null) return 0d;
        if (val instanceof Number) return ((Number) val).doubleValue();
        return Double.valueOf(val.toString());
    }

    // ---------------- Helpers ----------------
    public static Double decimal(Double value) {
        if (value == null) return 0d;
        return BigDecimal.valueOf(value).setScale(0, RoundingMode.HALF_UP).doubleValue();
    }

    public static Double decimal(Double value, int scale) {
        if (value == null) return 0d;
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }

    public static boolean inRange(Double value, Double min, Double max) {
        if (value == null) return false;
        if (min != null && value < min) return false;
        if (max != null && value > max) return false;
        return true;
    }

    // ---------------- Basic ----------------
    public void setBasic(Double basic) { this.basic = basic; }
    public Double getBasic() { return basic; }

    public void updateBasicStat() {
        this.basicStat = decimal(Math.max(basic, (ctc + cca) * 0.5));
    }

    public Double getBasicStat() { return basicStat; }

    // ---------------- HRA ----------------
    public void setHra(Double hra) { this.hra = hra; }
    public Double getHra() { return hra; }

    // ---------------- Bonus ----------------
    public void setBonus(Double bonus) { this.bonus = bonus; }
    public Double getBonus() { return bonus; }

    // ---------------- PF ----------------
    public void setEmployeePF(Double pf) { this.employeePF = pf; }
    public Double getEmployeePF() { return employeePF; }

    public void setEmployerPF(Double pf) { this.employerPF = pf; }
    public Double getEmployerPF() { return employerPF; }

    public Double employeePFOverrideOrDefault(Double defaultValue) {
        if (employeePFOverride == null) return defaultValue;
        return Math.max(employeePFOverride, defaultValue);
    }

    // ---------------- ESI ----------------
    public void setEmployeeESI(Double esi) { this.employeeESI = esi; }
    public Double getEmployeeESI() { return employeeESI; }

    public void setEmployerESI(Double esi) { this.employerESI = esi; }
    public Double getEmployerESI() { return employerESI; }

    // ---------------- Gratuity ----------------
    public void setGratuity(Double gratuity) { this.gratuity = gratuity; }
    public Double getGratuity() { return gratuity; }

    // ---------------- Tax ----------------
    public void setTaxSlabBase(Double base) { this.taxSlabBase = base; }
    public Double getTaxSlabBase() { return taxSlabBase; }

    public void setTaxAfterRebate(Double rebate) { this.taxAfterRebate = rebate; }
    public Double getTaxAfterRebate() { return taxAfterRebate; }

    public void setTaxMultiplier(Double multiplier) { this.taxMultiplier = multiplier; }
    public Double getTaxMultiplier() { return taxMultiplier; }

    public void setTaxWithCess(Double tax) { this.taxWithCess = tax; }
    public Double getTaxWithCess() { return taxWithCess; }

    public void setTds(Double tds) { this.tds = tds; }
    public Double getTds() { return tds; }

    // ---------------- Derived ----------------
    public void computePreTax() {
        // Note: Gratuity is now calculated by spreadsheet rule (5% of basic)
        // If gratuity is still 0 (not set by rules), calculate it here as fallback
        if (this.gratuity == null || this.gratuity == 0d) {
            this.gratuity = decimal(basic * 0.05);
        }
        double earnings = total(ComponentType.EARNING);
        double employerCosts = total(ComponentType.EMPLOYER_COST);
        this.grossPayable = decimal(ctc - employerPF - employerESI - gratuity - employerCosts + earnings);
        this.specialAllowance = decimal(grossPayable - basic - hra - bonus - earnings);
        this.annualGross = decimal(grossPayable * 12);
    }

    public void computePostTax() {
        // All tax calculations are now driven by spreadsheet rules:
        // - TaxAfterRebate rule applies rebate if annualGross <= 1200000
        // - TaxWithCess rule applies the correct multiplier based on gross range
        // If they're not set by rules (shouldn't happen), use fallback logic
        if (taxAfterRebate == null || taxAfterRebate == 0d) {
            this.taxAfterRebate = decimal(Math.max(taxSlabBase - 5000, 0));
        }
        if (taxWithCess == null || taxWithCess == 0d) {
            Double multiplier = (taxMultiplier == null || taxMultiplier == 0d) ? 1.0d : taxMultiplier;
            this.taxWithCess = decimal(taxAfterRebate * multiplier);
        }
        this.tds = decimal(taxWithCess / 12);
        double deductions = total(ComponentType.DEDUCTION);
        this.takeHomeSalary = decimal(grossPayable - employeePF - employeeESI - professionalTax - tds - deductions + cca - medicalInsurance);
    }

    public SalaryResponse toResponse() {
    SalaryResponse res = new SalaryResponse();
    res.employeeId = this.employeeId;
    res.name = this.name;
    res.ctc = this.ctc;
    res.basic = this.basic;
    res.basicStat = this.basicStat;
    res.hra = this.hra;
    res.bonus = this.bonus;
    res.gratuity = this.gratuity;
    res.employeePF = this.employeePF;
    res.employerPF = this.employerPF;
    res.employeeESI = this.employeeESI;
    res.employerESI = this.employerESI;
    res.medicalInsurance = this.medicalInsurance;
    res.grossPayable = this.grossPayable;
    res.specialAllowance = this.specialAllowance;
    res.annualGross = this.annualGross;
    res.taxSlabBase = this.taxSlabBase;
    res.taxAfterRebate = this.taxAfterRebate;
    res.taxMultiplier = this.taxMultiplier;
    res.taxWithCess = this.taxWithCess;
    res.tds = this.tds;
    res.takeHomeSalary = this.takeHomeSalary;
    res.cca = this.cca;
    res.category = this.category;
    res.pfOption = this.pfOption;
    res.location = this.location;

    // Components mapping
    Map<String, Double> compValues = new HashMap<>();
    Map<String, String> compTypes = new HashMap<>();
    java.util.List<SalaryResponse.ComponentDto> compList = new java.util.ArrayList<>();
    for (Component c : components.values()) {
        compValues.put(c.name, c.amount);
        compTypes.put(c.name, c.type.name());
        compList.add(new SalaryResponse.ComponentDto(c.name, c.amount, c.type.name()));
    }
    res.components = compValues;
    res.componentTypes = compTypes;
    res.componentList = compList;

    return res;
}

    public Double getGrossPayable() { return grossPayable; }
    public Double getSpecialAllowance() { return specialAllowance; }
    public Double getAnnualGross() { return annualGross; }
    public Double getTakeHomeSalary() { return takeHomeSalary; }

    // ---------------- Components ----------------
    public void addComponent(String name, Double amount, String type) {
        ComponentType ct = ComponentType.from(type);
        Component component = new Component(name, amount == null ? 0d : amount, ct);
        components.put(name, component);
        componentTotals.put(ct, decimal(componentTotals.get(ct) + amount));
    }

    public Map<String, Component> getComponents() { return components; }

    private double total(ComponentType type) { return componentTotals.getOrDefault(type, 0d); }

    public enum ComponentType { EARNING, DEDUCTION, EMPLOYER_COST;

        public static ComponentType from(String raw) {
            if (raw == null) return EARNING;
            try { return ComponentType.valueOf(raw.trim().toUpperCase()); }
            catch(Exception e) { return EARNING; }
        }
    }

    public static final class Component {
        public final String name;
        public final double amount;
        public final ComponentType type;
        public Component(String name, double amount, ComponentType type) {
            this.name = name;
            this.amount = amount;
            this.type = type;
        }
    }

    // ---------------- Inputs Getters ----------------
    public String getEmployeeId() { return employeeId; }
    public String getName() { return name; }
    public Double getCtc() { return ctc; }
    public Double getCca() { return cca; }
    public String getCategory() { return category; }
    public String getLocation() { return location; }
    public String getPfOption() { return pfOption; }
    public Double getProfessionalTax() { return professionalTax; }
    public Double getEmployeePFOverride() { return employeePFOverride; }
    public Double getMedicalInsurance() { return medicalInsurance; }
    public void setMedicalInsurance(Double med) { this.medicalInsurance = med; }
}