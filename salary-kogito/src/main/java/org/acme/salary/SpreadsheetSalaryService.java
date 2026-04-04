package org.acme.salary;

import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.Message;
import org.kie.api.builder.Results;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.internal.builder.DecisionTableConfiguration;
import org.kie.internal.builder.DecisionTableInputType;
import org.kie.api.io.Resource;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;

/**
 * Builds a KieContainer from the XLSX decision table on disk so HR can upload new rules at runtime.
 */
@ApplicationScoped
public class SpreadsheetSalaryService {

    private static final Path EXTERNAL_RULES = Paths.get("data/rules/salary-rules.xlsx");

    private KieContainer kieContainer;

    @PostConstruct
    void init() {
        ensureDefaultRulesPresent();
        try {
            this.kieContainer = buildFromPath(EXTERNAL_RULES);
            System.out.println("✓ SpreadsheetSalaryService initialized successfully");
        } catch (RuntimeException e) {
            System.err.println("ERROR building spreadsheet rules: " + e.getMessage());
            e.printStackTrace();
            // If the stored workbook is corrupted (e.g., missing RuleTable), fall back to bundled default.
            restoreDefaultRules();
            this.kieContainer = buildFromPath(EXTERNAL_RULES);
        }
    }

    public SalaryFact evaluate(Map<String, Object> input) {
        KieSession session = kieContainer.newKieSession();
        try {
            SalaryFact fact = SalaryFact.from(input);
            FactHandle handle = session.insert(fact);

            // pass 1: base component rules (basic/hra/pf/esi/medical)
            int fired1 = session.fireAllRules();
            System.out.println("Pass 1 fired: " + fired1 + " rules");

            // derive amounts needed by tax tables (grossPayable, annualGross)
            fact.computePreTax();
            session.update(handle, fact);

            // pass 2: tax slab + multiplier rules
            int fired2 = session.fireAllRules();
            System.out.println("Pass 2 fired: " + fired2 + " rules");

            // final tax + take-home math
            fact.computePostTax();
            return fact;
        } catch (Exception e) {
            System.err.println("ERROR in evaluate: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Salary calculation failed", e);
        } finally {
            session.dispose();
        }
    }

    public synchronized void replaceRules(InputStream workbookStream) {
        Path tmp = null;
        try {
            Files.createDirectories(EXTERNAL_RULES.getParent());
            tmp = Files.createTempFile(EXTERNAL_RULES.getParent(), "rules-upload-", ".xlsx");
            Files.copy(workbookStream, tmp, StandardCopyOption.REPLACE_EXISTING);

            // Validate the uploaded workbook builds successfully before replacing the live one.
            KieContainer testContainer = buildFromPath(tmp);

            Files.move(tmp, EXTERNAL_RULES, StandardCopyOption.REPLACE_EXISTING);
            this.kieContainer = testContainer;
        } catch (Exception e) {
            if (tmp != null) {
                try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
            }
            throw new IllegalStateException("Failed to store new rules workbook: " + e.getMessage(), e);
        }
    }

    public Path currentRulesPath() {
        return Files.exists(EXTERNAL_RULES) ? EXTERNAL_RULES : null;
    }

    private void ensureDefaultRulesPresent() {
        try {
            if (Files.exists(EXTERNAL_RULES)) {
                return;
            }
            Files.createDirectories(EXTERNAL_RULES.getParent());
            restoreDefaultRules();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to prepare rules workbook", e);
        }
    }

    private void restoreDefaultRules() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("salary-rules.xlsx")) {
            if (in == null) {
                throw new IllegalStateException("Default salary-rules.xlsx not found on classpath");
            }
            Files.copy(in, EXTERNAL_RULES, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to restore default rules workbook", e);
        }
    }

    private KieContainer buildFromPath(Path workbook) {
        KieServices ks = KieServices.Factory.get();
        KieFileSystem kfs = ks.newKieFileSystem();

        // Treat XLSX as a decision table; explicitly set XLSX input type.
        DecisionTableConfiguration dtConf = org.kie.internal.builder.KnowledgeBuilderFactory.newDecisionTableConfiguration();
        dtConf.setInputType(DecisionTableInputType.XLSX);
        Resource resource = ks.getResources().newFileSystemResource(workbook.toFile());
        resource.setConfiguration(dtConf);
        resource.setResourceType(ResourceType.DTABLE);

        // Write directly; resource carries type/config.
        kfs.write(resource);

        KieBuilder builder = ks.newKieBuilder(kfs).buildAll();
        Results results = builder.getResults();
        if (results.hasMessages(Message.Level.ERROR)) {
            throw new IllegalStateException("Error building spreadsheet rules: " + results.getMessages());
        }
        return ks.newKieContainer(ks.getRepository().getDefaultReleaseId());
    }
}
