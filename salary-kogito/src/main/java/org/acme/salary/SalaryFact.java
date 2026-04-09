package org.acme.salary;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Fact consumed by the spreadsheet decision table.
 *
 * The new XLSX only sets the basic salary based on CTC, so the rest of the
 * fields are computed defenely in code with sensible fallbacks to keep the
 * pipeline running even when the sheet is minimal.
 */
public class SalaryFact {

    private enum ComponentType { EARNING, DEDUCTION, EMPLOYER_COST }

    // ------- Input fields --------
    private String employeeId;
    private String name;
    private Double ctc;
    private Double cca;
    private String category;
    private String location;
    private Double pfOption;
    private Double professionalTax;
    private Double employeePFOverride;

    // ------- Rule-driven outputs (may stay null if sheet does not set them) --------
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
    private Double taxMultiplier;
    private Double taxAfterRebate;
    private Double taxWithCess;

    // ------- Derived values --------
    private Double grossPayable;
    private Double specialAllowance;
    private Double annualGross;
    private Double tds;
    private Double takeHomeSalary;

    // ------- Dynamic component tracking --------
    private final Map<String, Double> components = new LinkedHashMap<>();
    private final Map<String, String> componentTypes = new LinkedHashMap<>();

    public SalaryFact() {
        // default constructor required by Kogito/Drools
        this.taxMultiplier = 1.0d; // neutral default used as fallback
    }

    public SalaryFact(String employeeId, String name, Double ctc) {
        this();
        this.employeeId = employeeId;
        this.name = name;
        this.ctc = ctc;
    }

    /**
     * Build a fact from a generic input map (GraphQL / Excel upload).
     */
    public static SalaryFact from(Map<String, Object> input) {
        SalaryFact f = new SalaryFact();
        f.employeeId = asString(input.get("employeeId"));
        f.name = asString(input.get("name"));
        f.ctc = defaultDouble(input.get("ctc"), null);
        f.cca = defaultDouble(input.get("cca"), 0d);
        f.category = asString(input.get("category"));
        f.location = asString(input.get("location"));
        f.setPfOption(defaultDouble(input.get("pfOption"), null));
        f.professionalTax = defaultDouble(input.get("professionalTax"), 0d);
        f.employeePFOverride = defaultDouble(input.get("employeePFOverride"), null);
        return f;
    }

    /**
     * First pass derived math between rule engine phases.
     * With the spreadsheet now owning gratuity/medical/etc, this method only
     * does numeric aggregation and conservative fallbacks.
     */
    public void computePreTax() {
        // If BasicStat wasn't set by spreadsheet, derive it to keep downstream rules (Bonus, etc.) working
        if (this.basicStat == null || this.basicStat == 0d) {
            double computed = Math.max(n(basic), n(ctc) * 0.5 + n(cca) * 0.5);
            this.basicStat = decimal(computed);
        }

        // If Bonus wasn't set by the sheet (e.g., band mismatch), apply the same rule's intent defensively
        if (this.bonus == null) {
            this.bonus = (n(basicStat) <= 21000) ? decimal(n(basicStat) * 0.0833) : 0d;
        }

        if (this.gratuity == null || this.gratuity == 0d) {
            this.gratuity = decimal(n(basic) * 0.05); // fallback only; primary comes from sheet
        }

        calculateGrossAndSpecial();
    }

    /**
     * Second pass math after tax rules fire.
     * Dynamically applies components based on classification:
     * - EARNING: add to take-home
     * - DEDUCTION: subtract from take-home
     * - EMPLOYER_COST: not added to take-home
     */
    public void computePostTax() {
        // Start with gross payable
        double takeHome = n(grossPayable);
        
        // Subtract deduction components dynamically
        for (Map.Entry<String, Double> entry : components.entrySet()) {
            if (ComponentType.DEDUCTION.name().equals(componentTypes.get(entry.getKey()))) {
                takeHome -= n(entry.getValue());
            }
        }
        
        // Add CCA (special case: always added despite being component-like)
        takeHome += n(cca);
        
        // Fixed deductions that aren't in components (backward compatibility)
        takeHome -= n(employeePF);
        takeHome -= n(employeeESI);
        takeHome -= n(professionalTax);
        takeHome -= n(tds);
        takeHome -= n(medicalInsurance);
        
        this.takeHomeSalary = decimal(takeHome);
    }

    /**
     * Spreadsheet helper: calculate amount from percent/fixed/cap/minDefault columns.
     * NOTE: percent is expected to be in decimal form (0.05 for 5%, not 5)
     */
    public static Double amount(Double base, Double percent, Double fixed, Double cap, Double minDefault) {
        double effectiveBase = base == null ? 0d : base;
        if (cap != null && effectiveBase > cap) {
            effectiveBase = cap;
        }

        double result;
        if (fixed != null) {
            result = fixed;
        } else if (percent != null) {
            // percent is in decimal form (0.05 means 5%), so multiply directly without dividing by 100
            result = effectiveBase * percent;
        } else {
            result = 0d;
        }

        if (minDefault != null && result < minDefault) {
            result = minDefault;
        }
        return decimal(result);
    }

    /**
     * Central place for gross/special/annual derivation so it can be reused
     * both during rule passes and when getters are invoked directly.
     * 
     * Dynamically applies components based on classification:
     * - EARNING: counted as part of gross
     * - EMPLOYER_COST: subtracted from CTC to get gross
     */
    private void calculateGrossAndSpecial() {
        // Start with CTC (total cost to company)
        double gross = n(ctc);
        
        // Subtract employer-cost components (e.g., employer PF, ESI, gratuity)
        for (Map.Entry<String, Double> entry : components.entrySet()) {
            if (ComponentType.EMPLOYER_COST.name().equals(componentTypes.get(entry.getKey()))) {
                gross -= n(entry.getValue());
            }
        }
        
        // Subtract fixed employer costs (backward compatibility)
        gross -= n(employerPF);
        gross -= n(employerESI);
        gross -= n(gratuity);
        
        this.grossPayable = decimal(gross);
        
        // Special Allowance = Gross - (Base components: Basic + HRA + Bonus)
        // Any additional earning components flow through automatically
        double baseComponents = n(basic) + n(hra) + n(bonus);
        
        // Add other earning components to base
        for (Map.Entry<String, Double> entry : components.entrySet()) {
            if (ComponentType.EARNING.name().equals(componentTypes.get(entry.getKey()))) {
                baseComponents += n(entry.getValue());
            }
        }
        
        this.specialAllowance = decimal(n(grossPayable) - baseComponents);
        this.annualGross = decimal(n(grossPayable) * 12);
    }

    /**
     * Safeguard to ensure derived numbers are available even if computePreTax()
     * was skipped by the caller; lightweight since it only runs when needed.
     */
    private void ensureGrossAndSpecialComputed() {
        if (grossPayable == null || specialAllowance == null || annualGross == null) {
            calculateGrossAndSpecial();
        }
    }

    public void addComponent(String name, Double amount) {
        addComponent(name, amount, null);
    }

    public void addComponent(String name, Double amount, String explicitType) {
        if (name == null) return;
        ComponentType type = resolveType(name, explicitType);
        String cleanName = normalizeName(name);
        Double value = decimal(amount == null ? 0d : amount);
        System.out.println("DEBUG addComponent: name='" + name + "', cleanName='" + cleanName + "', amount=" + amount + ", value=" + value + ", type=" + type.name());
        components.put(cleanName, value);
        componentTypes.put(cleanName, type.name());
    }

    private ComponentType resolveType(String name, String explicitType) {
        ComponentType fromExplicit = parseType(explicitType);
        if (fromExplicit != null) return fromExplicit;

        ComponentType fromName = parseType(name);
        return fromName != null ? fromName : ComponentType.EARNING;
    }

    private ComponentType parseType(String label) {
        if (label == null) return null;
        String norm = label.replaceAll("[\\[\\]()]", "").trim().toUpperCase();
        switch (norm) {
            case "EARNING":
            case "EARNINGS":
                return ComponentType.EARNING;
            case "DEDUCTION":
            case "DEDUCTIONS":
                return ComponentType.DEDUCTION;
            case "EMPLOYER_COST":
            case "EMPLOYER COST":
            case "EMPLOYER-COST":
                return ComponentType.EMPLOYER_COST;
            default:
                return null;
        }
    }

    private String normalizeName(String name) {
        String cleaned = name;
        cleaned = cleaned.replaceAll("\\s*\\((?i:earning|earnings|deduction|deductions|employer[_\\- ]?cost)\\)\\s*$", "");
        cleaned = cleaned.replaceAll("\\s*\\[(?i:earning|earnings|deduction|deductions|employer[_\\- ]?cost)\\]\\s*$", "");
        return cleaned.trim();
    }

    private double total(ComponentType type) {
        return components.entrySet().stream()
                .filter(e -> resolveType(e.getKey(), componentTypes.get(e.getKey())) == type)
                .mapToDouble(e -> n(e.getValue()))
                .sum();
    }
    
    /**
     * Calculate how a new component should affect gross payable based on its type.
     * - EARNING: Add to gross (+1)
     * - EMPLOYER_COST: Subtract from gross (-1)
     * - DEDUCTION: No impact on gross (0)
     */
    public double getComponentGrossImpact(Double amount, ComponentType type) {
        if (amount == null || type == null) return 0d;
        switch (type) {
            case EARNING:
                return n(amount);  // Add earnings to gross
            case EMPLOYER_COST:
                return -n(amount); // Subtract employer costs from gross
            case DEDUCTION:
                return 0d;         // Deductions don't affect gross
            default:
                return 0d;
        }
    }
    
    /**
     * Calculate how a new component should affect take-home salary based on its type.
     * - EARNING: Add to take-home (+1)
     * - DEDUCTION: Subtract from take-home (-1)
     * - EMPLOYER_COST: No impact on take-home (0)
     */
    public double getComponentTakeHomeImpact(Double amount, ComponentType type) {
        if (amount == null || type == null) return 0d;
        switch (type) {
            case EARNING:
                return n(amount);  // Add earnings to take-home
            case DEDUCTION:
                return -n(amount); // Subtract deductions from take-home
            case EMPLOYER_COST:
                return 0d;         // Employer costs don't affect take-home
            default:
                return 0d;
        }
    }

    /**
     * Convert to API response object.
     */
    public SalaryResponse toResponse() {
        // Align derived values with the business formulas before exposing the response.
        // Special Allowance = Gross - (Basic + HRA + Bonus)
        // Take Home      = Gross - Employee PF - Employee ESI - TDS - PT + CCA - Medical Insurance
        ensureGrossAndSpecialComputed();
        computePostTax();
        SalaryResponse res = new SalaryResponse();
        res.employeeId = employeeId;
        res.name = name;
        res.ctc = ctc;
        res.cca = cca;
        res.category = category;
        res.location = location;
        res.pfOption = pfOption;
        res.professionalTax = professionalTax;

        res.basic = basic;
        res.basicStat = basicStat;
        res.hra = hra;
        res.bonus = bonus;
        res.gratuity = gratuity;
        res.employeePF = employeePF;
        res.employerPF = employerPF;
        res.employeeESI = employeeESI;
        res.employerESI = employerESI;
        res.medicalInsurance = medicalInsurance;

        res.taxSlabBase = taxSlabBase;
        res.taxMultiplier = taxMultiplier;
        res.taxAfterRebate = taxAfterRebate;
        res.taxWithCess = taxWithCess;

        res.grossPayable = grossPayable;
        res.specialAllowance = specialAllowance;
        res.annualGross = annualGross;
        res.tds = tds;
        res.takeHomeSalary = takeHomeSalary;

        // Set dynamic components using setters for proper serialization
        if (!components.isEmpty()) {
            res.setComponents(new LinkedHashMap<>(components));
            res.setComponentTypes(new LinkedHashMap<>(componentTypes));
            List<SalaryResponse.ComponentDto> list = components.entrySet()
                    .stream()
                    .map(e -> new SalaryResponse.ComponentDto(e.getKey(), e.getValue(), componentTypes.get(e.getKey())))
                    .collect(Collectors.toList());
            res.setComponentList(list);
        }
        return res;
    }

    public static Double decimal(Double value) {
        if (value == null) return null;
        // Round to the nearest whole number but keep Double semantics (e.g., 82.6 -> 83.0)
        return (double) Math.round(value);
    }

    private static String asString(Object o) {
        return o == null ? null : Objects.toString(o, null);
    }

    private static Double defaultDouble(Object o, Double fallback) {
        try {
            if (o == null) return fallback;
            if (o instanceof Number) {
                return ((Number) o).doubleValue();
            }
            return Double.valueOf(o.toString());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private Double n(Double v) {
        return v == null ? 0d : decimal(v);
    }

    // --------------- Getters and Setters ----------------
    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Double getCtc() { return ctc; }
    public void setCtc(Double ctc) { this.ctc = ctc; }

    public Double getCca() { return cca; }
    public void setCca(Double cca) { this.cca = cca; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public Double getPfOption() { return pfOption; }

    // Strict double-only setter 1-5
    public void setPfOption(Double value) {
        if (value != null && value >= 1 && value <= 5) {
            this.pfOption = value;
        } else {
            this.pfOption = null;
        }
    }

    public Double getPfOptionCode() { return pfOption; }
    public void setPfOptionCode(Double code) { setPfOption(code); }

    public Double getProfessionalTax() { return professionalTax; }
    public void setProfessionalTax(Double professionalTax) { this.professionalTax = professionalTax; }

    public Double getEmployeePFOverride() { return employeePFOverride; }
    public void setEmployeePFOverride(Double employeePFOverride) { this.employeePFOverride = employeePFOverride; }

    public Double getBasic() { return basic; }
    public void setBasic(Double basic) { this.basic = decimal(basic); }
    public void setBasic(Number basic) {
        if (basic != null) this.basic = decimal(basic.doubleValue());
    }

    public Double getBasicStat() { return basicStat; }
    public void setBasicStat(Double basicStat) { this.basicStat = decimal(basicStat); }
    
    public Double getHra() { return hra; }
    public void setHra(Double hra) { this.hra = decimal(hra); }

    public Double getBonus() { return bonus; }
    public void setBonus(Double bonus) { this.bonus = decimal(bonus); }

    public Double getGratuity() { return gratuity; }
    public void setGratuity(Double gratuity) { this.gratuity = decimal(gratuity); }

    public Double getEmployeePF() { return employeePF; }
    public void setEmployeePF(Double employeePF) { this.employeePF = decimal(employeePF); }

    public Double getEmployerPF() { return employerPF; }
    public void setEmployerPF(Double employerPF) { this.employerPF = decimal(employerPF); }

    public Double getEmployeeESI() { return employeeESI; }
    public void setEmployeeESI(Double employeeESI) { this.employeeESI = decimal(employeeESI); }

    public Double getEmployerESI() { return employerESI; }
    public void setEmployerESI(Double employerESI) { this.employerESI = decimal(employerESI); }

    public Double getMedicalInsurance() { return medicalInsurance; }
    public void setMedicalInsurance(Double medicalInsurance) { this.medicalInsurance = decimal(medicalInsurance); }

    public Double getTaxSlabBase() { return taxSlabBase; }
    public void setTaxSlabBase(Double taxSlabBase) { this.taxSlabBase = taxSlabBase; }

    public Double getTaxMultiplier() { return taxMultiplier; }
    public void setTaxMultiplier(Double taxMultiplier) { this.taxMultiplier = taxMultiplier; }

    public Double getTaxAfterRebate() { return taxAfterRebate; }
    public void setTaxAfterRebate(Double taxAfterRebate) { this.taxAfterRebate = taxAfterRebate; }

    public Double getTaxWithCess() { return taxWithCess; }
    public void setTaxWithCess(Double taxWithCess) { this.taxWithCess = taxWithCess; }

    public Double getGrossPayable() { ensureGrossAndSpecialComputed(); return grossPayable; }
    public void setGrossPayable(Double grossPayable) { this.grossPayable = decimal(grossPayable); }

    public Double getSpecialAllowance() { ensureGrossAndSpecialComputed(); return specialAllowance; }
    public void setSpecialAllowance(Double specialAllowance) { this.specialAllowance = decimal(specialAllowance); }

    public Double getAnnualGross() { ensureGrossAndSpecialComputed(); return annualGross; }
    public void setAnnualGross(Double annualGross) { this.annualGross = decimal(annualGross); }

    public Double getTds() { return tds; }
    public void setTds(Double tds) { this.tds = decimal(tds); }

    public Double getTakeHomeSalary() { return takeHomeSalary; }
    public void setTakeHomeSalary(Double takeHomeSalary) { this.takeHomeSalary = decimal(takeHomeSalary); }

    public Map<String, Double> getComponents() { return components; }
    public Map<String, String> getComponentTypes() { return componentTypes; }
    
    /**
     * Get the ComponentType for a given component name.
     * Returns EARNING as default if not found.
     */
    public ComponentType getComponentType(String componentName) {
        if (componentName == null) return ComponentType.EARNING;
        String type = componentTypes.get(componentName);
        if (type == null) return ComponentType.EARNING;
        return ComponentType.valueOf(type);
    }

    // Factory method for convenience
    public static SalaryFact from(String employeeId, String name, Double ctc) {
        return new SalaryFact(employeeId, name, ctc);
    }
}
