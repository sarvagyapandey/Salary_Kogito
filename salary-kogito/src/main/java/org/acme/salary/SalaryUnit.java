package org.acme.salary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.drools.ruleunits.api.DataProcessor;
import org.drools.ruleunits.api.DataSource;
import org.drools.ruleunits.api.DataStream;
import org.drools.ruleunits.api.RuleUnitData;
import org.kie.api.runtime.rule.FactHandle;

/**
 * Rule Unit data holder that exposes typed data sources for salary calculations.
 *
 * - salaries: incoming facts to be processed (DataStream)
 * - results: outgoing SalaryResponse stream (DataStream)
 * - capturedResults: in-memory list to let callers read what rules emitted
 */
public class SalaryUnit implements RuleUnitData {

    private final DataStream<SalaryFact> salaries = DataSource.createStream();
    private final DataStream<SalaryResponse> results = DataSource.createStream();

    private final List<SalaryResponse> capturedResults = new ArrayList<>();

    public SalaryUnit() {
        // Collect emitted responses so service code can easily return them.
        results.subscribe(new DataProcessor<>() {
            @Override
            public FactHandle insert(org.drools.ruleunits.api.DataHandle handle, SalaryResponse object) {
                capturedResults.add(object);
                return null; // not used outside the stream
            }

            @Override
            public void update(org.drools.ruleunits.api.DataHandle handle, SalaryResponse object) {
                // no-op for stream semantics
            }

            @Override
            public void delete(org.drools.ruleunits.api.DataHandle handle) {
                // no-op for stream semantics
            }
        });
    }

    public DataStream<SalaryFact> getSalaries() {
        return salaries;
    }

    public DataStream<SalaryResponse> getResults() {
        return results;
    }

    /**
     * Snapshot of emitted results, newest last.
     */
    public List<SalaryResponse> getCapturedResults() {
        return Collections.unmodifiableList(capturedResults);
    }
}
