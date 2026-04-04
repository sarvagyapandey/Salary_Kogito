package org.acme.salary;

import java.util.Map;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class SalaryService {
    @Inject
    SpreadsheetSalaryService spreadsheetSalaryService;

    public SalaryResponse calculate(Map<String, Object> input) {
        SalaryFact fact = spreadsheetSalaryService.evaluate(input);
        return fact.toResponse();
    }
}
