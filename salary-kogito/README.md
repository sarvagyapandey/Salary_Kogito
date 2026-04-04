# salary-kogito

This project uses Quarkus, the Supersonic Subatomic Java Framework.

If you want to learn more about Quarkus, please visit its website: https://quarkus.io/ .

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:
```shell script
./mvnw compile quarkus:dev
```

## Editing salary rules in Excel

- Rules live in a single-sheet workbook at `src/main/resources/salary-rules.xlsx` (sheet name `salaryRules`). Sections are stacked vertically for each component (Basic, HRA, Bonus, PF/ESI, Medical, TaxSlab, TaxMultiplier, etc.).
- Add/adjust bands by editing the rows on the relevant sheet. Keep the first three rows (`RuleSet`, `Import`, `Import`) intact on every sheet.
- To add a new component: append a new RuleTable block in the single sheet, set your conditions, and in ACTION call  
  `salary.addComponent("NewComponent", SalaryFact.decimal($expr), "EARNING|DEDUCTION|EMPLOYER_COST")`.
  - EARNING → added to gross + take-home  
  - DEDUCTION → subtracted from take-home only  
  - EMPLOYER_COST → subtracted from gross only
- The app copies the bundled workbook to `data/rules/salary-rules.xlsx` at startup; uploads replace that file and hot-reload the Kie rules.

GraphQL API (only)

- `calculateSalary(employeeId: String!): SalaryResponse` — single employee calc (uses rules workbook).
- `rulesWorkbook: String!` — download rules XLSX as base64.
- `uploadRulesWorkbook(workbookBase64: String!): Boolean!` — upload rules XLSX (base64) and hot-reload.
- `employeeTemplate: String!` — download employee input template as base64 XLSX.
- `processEmployeesWorkbook(workbookBase64: String!): String!` — upload employee input XLSX (base64) and get output XLSX (base64).

Example payloads
```graphql
# 1) Download rules workbook
query { rulesWorkbook }

# 2) Upload rules workbook
mutation($data:String!){ uploadRulesWorkbook(workbookBase64:$data) }

# 3) Get employee template
query { employeeTemplate }

# 4) Process filled employee sheet
mutation($data:String!){ processEmployeesWorkbook(workbookBase64:$data) }

# 5) Single employee salary by ID
query { calculateSalary(employeeId: "emp_16") { employeeId ctc basic hra takeHomeSalary } }
```

> **_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only at http://localhost:8080/q/dev/.

## Packaging and running the application

The application can be packaged using:
```shell script
./mvnw package
```
It produces the `quarkus-run.jar` file in the `target/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/quarkus-app/lib/` directory.

The application is now runnable using `java -jar target/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:
```shell script
./mvnw package -Dquarkus.package.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar target/*-runner.jar`.

## Creating a native executable

You can create a native executable using: 
```shell script
./mvnw package -Dnative
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using: 
```shell script
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./target/salary-kogito-1.0.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult https://quarkus.io/guides/maven-tooling.

## Provided Code

### REST

Easily start your REST Web Services

[Related guide section...](https://quarkus.io/guides/getting-started-reactive#reactive-jax-rs-resources)
