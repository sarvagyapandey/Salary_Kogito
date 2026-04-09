# Salary-Kogito Project: Complete Component Flow Analysis

## 1. SALARY-RULES.XLSX FILE STRUCTURE

**Location:** `/home/user/Salary_full_stack/salary-kogito/data/rules/salary-rules.xlsx`

**Format:** Apache Drools Decision Table (XLSX with RuleSeq syntax)

### Single Sheet: "Basic"
Contains 11 RuleTables with 38 total rules. The sheet implements Drools decision table syntax where:
- Each RuleTable defines a set of conditional rules
- Row 6+3=9 contains CONDITION/ACTION headers
- Row 10 contains column names  
- Row 11+ contains the actual rule data

### RuleTables and Their Structure:

| # | RuleTable | Row | Conditions | Actions | Rules | Purpose |
|---|-----------|-----|-----------|---------|-------|---------|
| 1 | **Basic** | 6 | `ctc >= $1`, `ctc <= $1` | `$salary.setBasic($1)` | 5 | Set basic salary by CTC range |
| 2 | **HRA** | 16 | `ctc >= $1`, `ctc <= $1` | `$salary.setHra($1)` | 4 | Set HRA allowance by CTC |
| 3 | **ESIEmployee** | 25 | `basic >= $1`, `basic <= $1` | `$salary.setEmployeeESI($1)` | 2 | Set employee ESI limit |
| 4 | **ESIEmployer** | 32 | `basic >= $1`, `basic <= $1` | `$salary.setEmployerESI($1)` | 2 | Set employer ESI contribution |
| 5 | **Bonus** | 39 | `basic >= $1`, `basic <= $1` | `$salary.setBonus($1)` | 2 | Set bonus by basic salary |
| 6 | **Gratuity** | 46 | `basic >= $1`, `basic <= $1` | `$salary.setGratuity($1)` | 2 | Set gratuity contribution |
| 7 | **MedicalInsurance** | 53 | `basic >= $1`, `basic <= $1` | `$salary.setMedicalInsurance($1)` | 2 | Set medical insurance amount |
| 8 | **EmployerPF** | 60 | `pfOption == $1` | `$salary.setEmployerPF($1)` | 6 | Set employer PF by option |
| 9 | **EmployeePF** | 71 | `pfOption == $1` | `$salary.setEmployeePF($1)` | 6 | Set employee PF by option |
| 10 | **TDS** | 82 | `grossPayable >= $1`, `grossPayable <= $1` | `$salary.setTds($1)` | 11 | Set TDS tax by gross payable |
| 11 | **Abcd(Deduction)** | 98 | `ctc >= $1`, `ctc <= $1` | `$salary.setHra($1)` | 4 | (Unused/Duplicate) |

### Example Rows in RuleTable Basic:
```
Row 11: Basic1    | CTCMin: 15000  | CTCMax: 24999 | Basic: $salary.getCtc()*0.5
Row 12: Basic2    | CTCMin: 25000  | CTCMax: 34999 | Basic: 22000
Row 13: Basic3    | CTCMin: 35000  | CTCMax: 44999 | Basic: 22500
Row 14: Basic4    | CTCMin: 45000  | CTCMax: 1B    | Basic: $salary.getCtc()*0.5
```

**Key Observation:** All current RuleTables use direct setter methods (e.g., `setBasic()`, `setHra()`) - they do NOT use `addComponent()` for dynamic components. `addComponent()` capability is available but not currently used by the decision tables.

---

## 2. SALARYFACT.JAVA FIELDS

### Input Fields
```java
String employeeId;        // Employee identifier
String name;              // Employee name
Double ctc;               // Cost to Company (annual)
Double cca;               // CCA allowance
String category;          // Job category
String location;          // Work location
Double pfOption;          // PF option (1-5)
Double professionalTax;   // Professional tax
Double employeePFOverride; // Override for employee PF
```

### Rule-Driven Output Fields (set by Excel decision tables)
```java
Double basic;             // Basic salary (from RuleTable Basic)
Double basicStat;         // Basic stat (derived if not set)
Double hra;               // HRA allowance (from RuleTable HRA)
Double bonus;             // Bonus (from RuleTable Bonus)
Double gratuity;          // Gratuity (from RuleTable Gratuity)
Double employeePF;        // Employee PF % (from RuleTable EmployeePF)
Double employerPF;        // Employer PF % (from RuleTable EmployerPF)
Double employeeESI;       // Employee ESI (from RuleTable ESIEmployee)
Double employerESI;       // Employer ESI (from RuleTable ESIEmployer)
Double medicalInsurance;  // Medical insurance (from RuleTable MedicalInsurance)
Double taxSlabBase;       // Tax slab base (computed)
Double taxMultiplier;     // Tax multiplier (default 1.0)
Double taxAfterRebate;    // Tax after rebate
Double taxWithCess;       // Tax with cess
```

### Derived Computation Fields
```java
Double grossPayable;      // CTC - employer costs (computed in calculateGrossAndSpecial)
Double specialAllowance;  // Gross - (Basic + HRA + Bonus)
Double annualGross;       // Gross * 12
Double tds;               // From RuleTable TDS
Double takeHomeSalary;    // Gross - all deductions + CCA
```

### Dynamic Component Tracking
```java
Map<String, Double> components;        // Component name → amount
Map<String, String> componentTypes;    // Component name → type (EARNING|DEDUCTION|EMPLOYER_COST)
```

---

## 3. SALARYFACT.JAVA: COMPONENT TRACKING SYSTEM

### Component Type Enum
```java
enum ComponentType { 
    EARNING,        // Adds to employee take-home
    DEDUCTION,      // Subtracts from employee take-home
    EMPLOYER_COST   // Employer pays; doesn't affect take-home
}
```

### Adding Components
```java
public void addComponent(String name, Double amount)
    // Shorthand: infers type from component name

public void addComponent(String name, Double amount, String explicitType)
    // Full form: can override inferred type
```

### Type Resolution Logic
1. **Explicit type** (if provided): Parsed first
2. **Name-based inference**: Labels like `"Bonus (Earning)"` or `"[DEDUCTION]"` are parsed
   - Recognizes: `EARNING`, `EARNINGS`, `DEDUCTION`, `DEDUCTIONS`, `EMPLOYER_COST`, `EMPLOYER COST`, `EMPLOYER-COST`
3. **Default**: `EARNING` if no type detected

### Name Normalization
Components with type suffixes are cleaned:
- `"LTA (Deduction)"` → `"LTA"`
- `"Gratuity [EMPLOYER_COST]"` → `"Gratuity"`

### How Components Affect Calculations

#### grossPayable Calculation
```java
double gross = ctc;
// Subtract EMPLOYER_COST components (e.g., employer PF, ESI)
for (entry : components) {
    if (ComponentType.EMPLOYER_COST) {
        gross -= entry.value;
    }
}
// Subtract fixed employer costs (backward compatibility)
gross -= employerPF + employerESI + gratuity;
```

#### takeHomeSalary Calculation  
```java
double takeHome = grossPayable;
// Subtract DEDUCTION components 
for (entry : components) {
    if (ComponentType.DEDUCTION) {
        takeHome -= entry.value;
    }
}
// Add CCA (special case)
takeHome += cca;
// Subtract fixed deductions
takeHome -= employeePF + employeeESI + professionalTax + tds + medicalInsurance;
```

**Current Status:** No active use of `addComponent()` in the decision tables. Default setter methods (`setBasic()`, `setHra()`, etc.) are used exclusively.

---

## 4. SALARYFACT → SALARYFACT CALCULATION PHASES

### Phase 1: Base Components (Pass 1 of Rule Engine)
```
Input: SalaryFact with fields set from decision table
↓
1. RuleEngine fires all rules → sets: basic, hra, bonus, gratuity, employeePF, 
                                     employerPF, employeeESI, employerESI, medicalInsurance
2. computePreTax() applies fallbacks:
   - If basicStat is null/0: basicStat = max(basic, ctc*0.5 + cca*0.5)
   - If bonus is null: bonus = (basicStat ≤ 21000) ? basicStat*0.0833 : 0
   - If gratuity is null/0: gratuity = basic*0.05
3. calculateGrossAndSpecial() computes:
   - grossPayable = CTC - (employer costs + EMPLOYER_COST components)
   - specialAllowance = grossPayable - (basic + hra + bonus + EARNING components)
   - annualGross = grossPayable * 12
```

### Phase 2: Tax Calculations (Pass 2 of Rule Engine)
```
1. Updated SalaryFact inserted back into rule engine
2. TaxSlab rules fire → sets: taxSlabBase, taxMultiplier, taxAfterRebate, taxWithCess, tds
3. computePostTax() finalizes:
   - takeHomeSalary = grossPayable - (DEDUCTION components) - fixed deductions + cca
```

---

## 5. SALARYFACT → SALARYRESPONSE CONVERSION

### toResponse() Method Flow
```java
SalaryResponse toResponse() {
    // 1. Ensure derived computations are fresh
    ensureGrossAndSpecialComputed();
    computePostTax();
    
    // 2. Copy all standard fields to response
    res.employeeId = employeeId;
    res.name = name;
    res.ctc = ctc;
    // ... (all 31 standard fields)
    
    // 3. Copy component maps and convert to DTO list
    if (!components.isEmpty()) {
        res.setComponents(new LinkedHashMap<>(components));
        res.setComponentTypes(new LinkedHashMap<>(componentTypes));
        
        List<ComponentDto> list = components.entrySet()
            .stream()
            .map(e -> new ComponentDto(
                e.getKey(),                          // name
                e.getValue(),                        // amount
                componentTypes.get(e.getKey())       // type
            ))
            .collect(Collectors.toList());
        res.setComponentList(list);
    }
    return res;
}
```

### All SalaryResponse Public Fields (31 total)

#### Basic Details (8)
- `employeeId` (String)
- `name` (String)
- `ctc` (Double)
- `cca` (Double)
- `category` (String)
- `location` (String)
- `pfOption` (Double)
- `professionalTax` (Double)

#### Rule-Driven Amounts (11)
- `basic` (Double)
- `basicStat` (Double)
- `hra` (Double)
- `bonus` (Double)
- `gratuity` (Double)
- `employeePF` (Double)
- `employerPF` (Double)
- `employeeESI` (Double)
- `employerESI` (Double)
- `medicalInsurance` (Double)

#### Tax Fields (4)
- `taxSlabBase` (Double)
- `taxMultiplier` (Double)
- `taxAfterRebate` (Double)
- `taxWithCess` (Double)

#### Derived Values (5)
- `grossPayable` (Double)
- `specialAllowance` (Double)
- `annualGross` (Double)
- `tds` (Double)
- `takeHomeSalary` (Double)

#### Dynamic Components (Private with Getters)
- `components` - Map<String, Double> (private, getter/setter)
- `componentTypes` - Map<String, String> (private, getter/setter)
- `componentList` - List<ComponentDto> (private, getter/setter)

#### ComponentDto Structure
```java
public static final class ComponentDto {
    public String name;      // Component name
    public Double amount;    // Component amount
    public String type;      // "EARNING", "DEDUCTION", or "EMPLOYER_COST"
}
```

---

## 6. COMPLETE FLOW: EXCEL → SALARYRESPONSE

### End-to-End Flow Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│ 1. Excel Input (salary-rules.xlsx employee sheet)               │
│    ├─ Standard columns: employeeId, name, ctc, cca, category   │
│    └─ Optional: pfOption, professionalTax, employeePFOverride   │
└──────────────────┬──────────────────────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────────────────────┐
│ 2. SpreadsheetSalaryService.evaluate(input)                     │
│    ├─ Convert input map → SalaryFact                            │
│    └─ Create KieSession from decision table XLSX               │
└──────────────────┬──────────────────────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────────────────────┐
│ 3. Rule Engine PASS 1: Base Component Rules                    │
│    ├─ RuleTable Basic    → setBasic(amount)                    │
│    ├─ RuleTable HRA      → setHra(amount)                      │
│    ├─ RuleTable EmployeePF → setEmployeePF(amount)            │
│    ├─ RuleTable EmployerPF → setEmployerPF(amount)            │
│    ├─ RuleTable ESIEmployee → setEmployeeESI(amount)          │
│    ├─ RuleTable ESIEmployer → setEmployerESI(amount)          │
│    ├─ RuleTable Bonus   → setBonus(amount)                     │
│    ├─ RuleTable Gratuity → setGratuity(amount)                │
│    └─ RuleTable MedicalInsurance → setMedicalInsurance(amount)│
│                                                                 │
│    All values stored in fixed SalaryFact fields                │
└──────────────────┬──────────────────────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────────────────────┐
│ 4. computePreTax()                                              │
│    ├─ If basicStat null/0: basicStat = max(basic, ctc*0.5+...) │
│    ├─ If bonus null: bonus = basicStat*0.0833 or 0             │
│    ├─ If gratuity null/0: gratuity = basic*0.05                │
│    └─ calculateGrossAndSpecial():                              │
│        ├─ grossPayable = ctc - employer_costs                  │
│        ├─ specialAllowance = gross - (basic+hra+bonus)         │
│        └─ annualGross = grossPayable * 12                      │
└──────────────────┬──────────────────────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────────────────────┐
│ 5. Rule Engine PASS 2: Tax Rules                               │
│    └─ RuleTable TDS → setTds(amount) based on grossPayable    │
│       (Sets: taxSlabBase, taxMultiplier, taxAfterRebate, etc.) │
└──────────────────┬──────────────────────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────────────────────┐
│ 6. computePostTax()                                             │
│    └─ takeHomeSalary = grossPayable                            │
│       - (deduction components) - empPF - empESI - PT - TDS      │
│       - medicalIns + CCA                                        │
└──────────────────┬──────────────────────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────────────────────┐
│ 7. SalaryFact.toResponse()                                      │
│    ├─ Copy all 31 standard fields → SalaryResponse             │
│    ├─ Copy components Map → response.components               │
│    ├─ Copy componentTypes Map → response.componentTypes       │
│    └─ Convert to ComponentDto List → response.componentList   │
└──────────────────┬──────────────────────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────────────────────┐
│ 8. SalaryService.calculate() - RuleUnit wrapper                │
│    ├─ Pass SalaryFact through rule unit data sources           │
│    └─ Emit to results stream                                    │
└──────────────────┬──────────────────────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────────────────────┐
│ 9. Return SalaryResponse to Client                             │
│    (GraphQL, REST, or Excel batch export)                       │
└─────────────────────────────────────────────────────────────────┘
```

### Data Transformation Mapping

```
INPUT COLUMNS (from user Excel or GraphQL)
├─ employeeId        ──→  SalaryFact.employeeId  ──→  SalaryResponse.employeeId
├─ name              ──→  SalaryFact.name        ──→  SalaryResponse.name
├─ ctc               ──→  SalaryFact.ctc         ──→  SalaryResponse.ctc
├─ cca               ──→  SalaryFact.cca         ──→  SalaryResponse.cca
├─ category          ──→  SalaryFact.category    ──→  SalaryResponse.category
├─ location          ──→  SalaryFact.location    ──→  SalaryResponse.location
├─ pfOption          ──→  SalaryFact.pfOption    ──→  SalaryResponse.pfOption
├─ professionalTax   ──→  SalaryFact.professionalTax ──→ SalaryResponse.professionalTax
└─ employeePFOverride ─── SalaryFact.employeePFOverride (internal use)

RULE-DRIVEN FIELDS (from Excel decision tables)
├─ RuleTable Basic   ──→  setBasic()            ──→  SalaryResponse.basic
├─ RuleTable HRA     ──→  setHra()              ──→  SalaryResponse.hra
├─ RuleTable Bonus   ──→  setBonus()            ──→  SalaryResponse.bonus
├─ RuleTable EmployeePF ──→ setEmployeePF()    ──→  SalaryResponse.employeePF
├─ RuleTable EmployerPF ──→ setEmployerPF()    ──→  SalaryResponse.employerPF
├─ RuleTable ESIEmployee ──→ setEmployeeESI()  ──→  SalaryResponse.employeeESI
├─ RuleTable ESIEmployer ──→ setEmployerESI()  ──→  SalaryResponse.employerESI
├─ RuleTable Gratuity ──→ setGratuity()        ──→  SalaryResponse.gratuity
├─ RuleTable MedicalInsurance ──→ setMedicalInsurance() ──→ SalaryResponse.medicalInsurance
└─ RuleTable TDS ──→ setTds()                   ──→  SalaryResponse.tds

DERIVED FIELDS (computed by SalaryFact methods)
├─ calculateGrossAndSpecial() ──→ SalaryResponse.{grossPayable, specialAllowance, annualGross}
└─ computePostTax()          ──→ SalaryResponse.takeHomeSalary

DYNAMIC COMPONENTS (Optional, currently unused)
├─ components Map         ──→  SalaryResponse.components
├─ componentTypes Map     ──→  SalaryResponse.componentTypes
└─ (converted to DTO)     ──→  SalaryResponse.componentList
```

---

## 7. CURRENTLY NOT USING ADDCOMPONENT()

### Why Components Framework Exists But Unused

The `addComponent()` system was designed for flexibility to support:
- **Custom earnings**: LTA, Conveyance, Other Allowances
- **Custom deductions**: Loan EMI, Mess, Uniform Charges
- **Employer costs**: Gratuity accrual, Additional PF contributions

### If addComponent() Were Used

Example rule action in Excel could be:
```
$salary.addComponent("LTA", 50000, "EARNING"); update($salary);
$salary.addComponent("Loan EMI", 5000, "DEDUCTION"); update($salary);
$salary.addComponent("Gratuity Accrual", 25000, "EMPLOYER_COST"); update($salary);
```

This would:
1. Populate `SalaryFact.components` map
2. Automatically affect `grossPayable` and `takeHomeSalary` calculations
3. Serialize to `SalaryResponse.componentList` for display/export
4. Show in Excel batch output with proper column headers

### Current Design Constraint

All 11 RuleTables use direct setters (`setBasic()`, `setHra()`, etc.):
- ✅ Type-safe (fields are well-defined)
- ✅ GraphQL schema clarity 
- ❌ Limited to pre-defined fields
- ❌ No runtime extensibility for new components

---

## 8. KEY CALCULATIONS

### Numeric Precision
All calculations use `Double` with `decimal()` rounding to nearest integer:
```java
static Double decimal(Double value) {
    if (value == null) return null;
    return (double) Math.round(value);
}
```

### grossPayable Formula
```
grossPayable = CTC 
             - employerPF 
             - employerESI 
             - gratuity 
             - [employer_cost_components]
```

### takeHomeSalary Formula
```
takeHomeSalary = grossPayable 
               - employeePF 
               - employeeESI 
               - professionalTax 
               - TDS 
               - medicalInsurance 
               - [deduction_components]
               + CCA
```

### specialAllowance Formula
```
specialAllowance = grossPayable - (basic + HRA + bonus + [earning_components])
```

### If basicStat Fallback (computePreTax)
```
basicStat = max(basic, ctc * 0.5 + cca * 0.5)
```

### If Bonus Fallback (computePreTax)
```
bonus = (basicStat ≤ 21000) ? basicStat * 0.0833 : 0
```

### If Gratuity Fallback (computePreTax)
```
gratuity = basic * 0.05
```

---

## 9. PROCESSING FLOW IN BATCHSALARYEXCELSERVICE

```
Input XLSX (employee sheet)
    ↓
1. parse header row → extract column indices
2. readRows() → List<Map<String, Object>> inputs
3. For each employee input:
   ├─ salaryService.calculate(input) 
   │  └─ Returns SalaryResponse with all computed fields
   └─ Collect dynamic component names
4. Collect all unique dynamic component names across all employees
5. Build output Excel:
   ├─ Header: INPUT_COLUMNS + OUTPUT_COLUMNS + dynamicComponentNames
   ├─ For each response: writeRow() with standard + dynamic columns
   └─ Auto-size all columns
6. Return output XLSX as byte[]
```

**Dynamic Component Collection:**
```java
Set<String> dynamicNames = new LinkedHashSet<>();
for (SalaryResponse resp : responses) {
    if (resp.getComponents() != null && !resp.getComponents().isEmpty()) {
        dynamicNames.addAll(resp.getComponents().keySet());
    }
}
```

This enables the output Excel to include discovered component columns even if they weren't in the input.

---

## SUMMARY

| Aspect | Status | Details |
|--------|--------|---------|
| **Excel Rules** | ✅ Active | 11 RuleTables, 38 rules in salary-rules.xlsx |
| **Standard Fields** | ✅ Active | 31 public fields in SalaryResponse |
| **Fixed Setters** | ✅ Active | setBasic(), setHra(), setBonus(), etc. |
| **Component Framework** | ✅ Designed | `addComponent()`, type inference, impact calculations |
| **Component Usage** | ❌ Unused | No RuleTable actions call addComponent() |
| **Two-Pass Engine** | ✅ Active | Pass 1: base components; Pass 2: tax |
| **Fallbacks** | ✅ Active | For basicStat, bonus, gratuity if rules don't set |
| **Batch Excel** | ✅ Active | Process multiple employees, discover dynamic columns |
| **GraphQL Export** | ✅ Active | Rules workbook upload/download, template generation |

