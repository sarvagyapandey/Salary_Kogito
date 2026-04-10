package org.acme.salary;

import java.util.Map;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.drools.ruleunits.api.RuleUnit;
import org.drools.ruleunits.api.RuleUnitInstance;

@ApplicationScoped
public class SalaryService {
    @Inject
    SpreadsheetSalaryService spreadsheetSalaryService;

    @Inject
    RuleUnit<SalaryUnit> salaryUnit;

    private static final double PF_STRICT_CTC_THRESHOLD = 30000d;
    private static final double PF_REQUIRED_OPTION = 4d;

    private void validatePfOption(Map<String, Object> input, java.util.List<String> errors) {
        Double ctc = asDouble(input.get("ctc"), asDouble(input.get("ctcMonthly"), null));
        if (ctc == null) {
            return; // no CTC supplied; let downstream handling manage missing numbers
        }

        Double pf = SalaryFact.parsePfOption(input.get("pfOption"));
        String rowInfo = input.containsKey("_rowNumber") ? " (sheet row " + input.get("_rowNumber") + ")" : "";

        if (ctc <= PF_STRICT_CTC_THRESHOLD) {
            if (pf == null || Double.compare(pf, PF_REQUIRED_OPTION) != 0) {
                errors.add("For monthly CTC <= 30000, pfOption must be 4" + rowInfo + "; received: " + input.get("pfOption"));
            }
        }
    }

    private Double asDouble(Object primary, Double fallback) {
        if (primary == null) return fallback;
        if (primary instanceof Number) return ((Number) primary).doubleValue();
        try { return Double.valueOf(primary.toString()); } catch (Exception e) { return fallback; }
    }

    public SalaryResponse calculate(Map<String, Object> input) {
        java.util.List<String> errors = new java.util.ArrayList<>();
        validatePfOption(input, errors);

        if (!errors.isEmpty()) {
            // Return a lightweight response with errors so frontend can display without backend exceptions.
            SalaryResponse res = new SalaryResponse();
            res.setErrors(errors);
            res.employeeId = input == null ? null : (String) input.get("employeeId");
            res.name = input == null ? null : (String) input.get("name");
            res.ctc = asDouble(input.get("ctc"), asDouble(input.get("ctcMonthly"), null));
            res.cca = input == null ? null : asDouble(input.get("cca"), 0d);
            res.pfOption = SalaryFact.parsePfOption(input.get("pfOption"));
            res.professionalTax = asDouble(input.get("professionalTax"), 0d);
            return res;
        }

        // Run the XLSX decision table first (existing behavior).
        SalaryFact fact = spreadsheetSalaryService.evaluate(input);

        // Pass the fact through the rule unit data sources so clients can subscribe/extend.
        SalaryUnit data = new SalaryUnit();
        data.getSalaries().append(fact);

        try (RuleUnitInstance<SalaryUnit> instance = salaryUnit.createInstance(data)) {
            instance.fire();
        }

        var emitted = data.getCapturedResults();
        return emitted.isEmpty() ? fact.toResponse() : emitted.get(emitted.size() - 1);
    }
}
