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

    public SalaryResponse calculate(Map<String, Object> input) {
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
