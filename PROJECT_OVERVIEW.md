# Salary Full-Stack Application - Complete Documentation

**Project Date:** April 13, 2026  
**Tech Stack:** React + Vite | Quarkus + Kogito | Excel Decision Tables

---

## TABLE OF CONTENTS

1. [Executive Summary](#executive-summary)
2. [Architecture Overview](#architecture-overview)
3. [Technology Stack](#technology-stack)
4. [Core Components](#core-components)
5. [Data Model: SalaryFact](#data-model-salaryfact)
6. [Salary Rules Engine](#salary-rules-engine)
7. [Execution Flow](#execution-flow)
8. [Component Type System](#component-type-system)
9. [Key Features](#key-features)
10. [Output Structure](#output-structure)
11. [API Documentation](#api-documentation)
12. [Getting Started](#getting-started)

---

## EXECUTIVE SUMMARY

**Salary Full-Stack** is a comprehensive, full-stack salary calculation system designed for HR departments to compute complete employee salary packages based on CTC (Cost to Company), tax regulations, and flexible employment rules.

### Key Innovation
**Salary rules live in Excel**, not hardcoded in Java. This allows HR teams to adjust compensation bands, tax slabs, and deductions without requiring engineering involvement.

### Core Capability
End-to-end salary computation with dynamic components, supporting:
- Multiple PF schemes (P1-P5)
- Tax band calculations with rebates and cess
- Flexible custom components (earnings, deductions, employer costs)
- Batch processing via Excel upload/download
- Real-time salary calculations via GraphQL API

---

## ARCHITECTURE OVERVIEW

```
┌─────────────────────────────────────────────────┐
│         Frontend Application                    │
│      React + Vite | Port 5173                  │
│  - Manual salary calculator                    │
│  - Rules download/upload interface             │
│  - Batch employee processing                   │
└────────────────────┬────────────────────────────┘
                     │ GraphQL
                     ↓
┌─────────────────────────────────────────────────┐
│       Backend Rules Engine                      │
│    Quarkus + Kogito | Port 8080                │
│  - SalaryFact data model                       │
│  - Two-pass rule execution (pre-tax/post-tax)  │
│  - GraphQL API endpoints                       │
│  - Employee repository                         │
│  - Hot-reload rule management                  │
└────────────────────┬────────────────────────────┘
                     │
                     ↓
┌─────────────────────────────────────────────────┐
│      Excel Rules Engine                         │
│   salary-rules.xlsx (13 RuleTables)            │
│  - Base salary calculations (CTC bands)        │
│  - Earnings components (HRA, Bonus, etc.)      │
│  - Deductions (PF, ESI, Tax)                   │
│  - Tax calculations (slabs, rebates, cess)     │
└─────────────────────────────────────────────────┘
```

### Data Flow

```
User Input
    ↓
GraphQL Request (calculateSalary)
    ↓
SalaryFact Creation (from input map)
    ↓
PASS 1: Rule Execution (Base Components)
    ├─ Basic Salary (from CTC bands)
    ├─ HRA, Bonus, Gratuity
    ├─ PF/ESI (by pfOption)
    ├─ Medical Insurance
    └─ computePreTax() → Gross Payable
    ↓
PASS 2: Rule Execution (Tax Calculations)
    ├─ Tax Slab (by annual gross)
    ├─ Tax Rebates
    └─ Tax Cess Multiplier
    ↓
computePostTax() → Take-Home Salary
    ↓
SalaryResponse (with all fields + components)
    ↓
JSON Response to Frontend
```

---

## TECHNOLOGY STACK

### Frontend
| Component | Technology | Version |
|-----------|-----------|---------|
| Framework | React | 19.2.4+ |
| Build Tool | Vite | 8.x |
| HTTP Client | Axios | - |
| Spreadsheet | XLSX Library | - |
| Styling | CSS | - |
| Port | localhost:5173 | - |

### Backend
| Component | Technology | Version |
|-----------|-----------|---------|
| Framework | Quarkus | 2.16.12.Final |
| Language | Java | 17+ |
| Rules Engine | Kogito | 1.44.0.Final |
| Rules Language | Drools Decision Tables | - |
| API | GraphQL (MicroProfile) | - |
| Build Tool | Maven | 3.8+ |
| Port | localhost:8080 | - |

### Storage & Database
| Component | Format | Location |
|-----------|--------|----------|
| Salary Rules | Excel XLSX | src/main/resources/salary-rules.xlsx |
| Rules Copy | Excel XLSX | data/rules/salary-rules.xlsx (runtime) |
| Employee Data | JSON | src/main/resources/employees.json |

---

## CORE COMPONENTS

### 1. SalaryFact.java - Domain Model

**Single Source of Truth** for all salary data across both rule execution passes.

#### Input Fields (Employee Information)
```java
String employeeId;           // Employee identifier
String name;                 // Employee name
Double ctc;                  // Cost to Company (annual, in rupees)
Double cca;                  // CCA (additional compensation component)
String category;             // Employee category (Staff, Management, etc.)
String location;             // Work location (Delhi, Mumbai, etc.)
Double pfOption;             // PF scheme selection (1-5, representing P1-P5)
Double professionalTax;      // Professional tax amount
Double employeePFOverride;   // Custom PF override value
```

#### Rule-Driven Output Fields
Set by Excel decision tables during rule execution:
```java
// Base Components (PreTax Pass)
Double basic;               // Base salary (from CTC band rules)
Double basicStat;          // Engineering: max(basic, 50% of CTC+CCA)
Double hra;                // House Rent Allowance
Double bonus;              // Performance/annual bonus
Double gratuity;           // Gratuity contribution (from Medical table)
Double employeePF;         // Employee's PF deduction %
Double employerPF;         // Employer's PF contribution %
Double employeeESI;        // Employee's ESI deduction amount
Double employerESI;        // Employer's ESI contribution amount
Double medicalInsurance;   // Medical insurance deduction

// Tax Fields (PostTax Pass)
Double taxSlabBase;        // Base amount for tax slab calculation
Double taxMultiplier;      // Tax rate multiplier (from TaxWithCess table)
Double taxAfterRebate;     // Tax amount after rebate (₹5,000 relief)
Double taxWithCess;        // Final tax with cess applied
```

#### Derived Calculation Fields
Calculated automatically during computation phases:
```java
Double grossPayable;       // Monthly gross = CTC - employer costs
Double specialAllowance;   // Auto-balanced remainder (Gross - Basic - HRA - Bonus)
Double annualGross;        // Annual gross = Monthly Gross × 12
Double tds;                // Tax Deducted at Source (annual tax ÷ 12)
Double takeHomeSalary;     // Final net salary after all deductions + CCA
```

#### Dynamic Component Tracking
Supports custom salary components added via Excel rules:
```java
Map<String, Double> components;        // Component name → calculated amount
Map<String, String> componentTypes;    // Component name → type classification

// Three component types:
enum ComponentType {
    EARNING,          // Adds to both gross and take-home
    DEDUCTION,        // Subtracts from take-home only
    EMPLOYER_COST     // Subtracts from gross only
}
```

#### Key Methods

**`from(Map<String, Object> input)`** - Factory method
- Creates SalaryFact from GraphQL/Excel input
- Normalizes PF option (P1-P5 → 1-5)
- Applies sensible defaults

**`computePreTax()`** - First pass calculations
- Called after base component rules fire
- Calculates: grossPayable, specialAllowance, annualGross
- Dynamically applies EARNING and EMPLOYER_COST components

**`computePostTax()`** - Second pass calculations
- Called after tax rules fire
- Calculates final takeHomeSalary
- Applies deductions: PF, ESI, TDS, Medical, Professional Tax
- Adds CCA back to net salary

**`addComponent(String name, Double amount, String type)`** - Component insertion
- Used by spreadsheet rules to add custom components
- Auto-detects type from name suffix: "(EARNING)", "[DEDUCTION]", etc.
- Normalizes component names (removes type suffix)

**`toResponse()`** - API conversion
- Converts SalaryFact to SalaryResponse JSON
- Ensures all derived fields computed before serialization
- Includes component list with type metadata

---

### 2. salary-rules.xlsx - Excel Decision Tables

**Location:** `src/main/resources/salary-rules.xlsx` (single sheet: "salaryRules")

**Purpose:** Define all salary rules in a non-technical, HR-editable format.

#### 13 RuleTables with 38+ Rules

| # | Table | Rows | Purpose | Conditions |
|---|-------|------|---------|-----------|
| **1** | **Basic** | 6-15 | Determine base salary from CTC band | CTC ranges: 15K-25K, 25K-35K, 35K-45K, 45K+ |
| **2** | **HRA** | 16-20 | House Rent Allowance by CTC | CTC-dependent (0 for low, 50% of basic if high) |
| **3** | **Bonus** | 21-25 | Annual bonus calculation | If basicStat ≤ 21,000 → 8.33%, else 0 |
| **4** | **EmployeePF** | 26-32 | Employee PF deduction | By pfOption (P1-P5): 5-12% of basic |
| **5** | **EmployerPF** | 33-40 | Employer PF contribution | By pfOption: 3.67-13% of basic |
| **6** | **ESIEmployee** | 41-45 | Employee ESI deduction | If basicStat < 21,000 → 0.75%, else 0 |
| **7** | **ESIEmployer** | 46-50 | Employer ESI contribution | If basicStat < 21,000 → 3.25%, else 0 |
| **8** | **Medical** | 51-56 | Medical insurance | If basicStat ≥ 21,001 → ₹250, else 0 |
| **9** | **Gratuity** | 57-62 | Gratuity contribution | 5% of basic (moved from code) |
| **10** | **TaxSlab** | 63-78 | Income tax percentage by slab | Annual gross bands (0-2.5L, 2.5L-5L, 5L-10L, 10L+) |
| **11** | **TaxAfterRebate** | 79-85 | Apply tax rebates | ₹5,000 rebate if eligible |
| **12** | **TaxWithCess** | 86-92 | Apply cess multiplier | Multiplier varies by income level |
| **13** | **FinalCalc** | 93-98 | Orchestration logic | Coordination between passes |

#### Spreadsheet Format

**Header Structure (First 3 Rows - Keep Intact):**
```
Row 1: RuleSet, [RuleSet Name]
Row 2: Import, org.acme.salary.SalaryFact
Row 3: Import, org.acme.salary.SalaryFact.*
```

**Table Header (Row 6 + offset):**
```
RuleTable, [TABLE_NAME]
NAME, [Column Headers]
CONDITION, [Conditions using $1, $2, etc.]
ACTION, [Actions on salary object]
```

**Parameter Naming Convention:**

| Parameter | Usage | Format |
|-----------|-------|--------|
| `$ctcMin` | CTC range lower bound | Numeric (e.g., 15000) |
| `$ctcMax` | CTC range upper bound | Numeric (e.g., 24999) |
| `$limit` | Generic limit | Numeric |
| `$percent` | Percentage value | Decimal (0.05 = 5%) |
| `$fixed` | Fixed amount | Numeric |
| `$cap` | Upper cap on base | Numeric |
| `$minDefault` | Minimum floor after calc | Numeric |

#### Example Table: Basic Salary by CTC
```
RuleTable, Basic
NAME, $ctcMin, $ctcMax, $basicAmount
CONDITION, ctc >= $1, ctc <= $2, 
ACTION, salary.setBasic($3)

15000, 24999, 15000
25000, 34999, 22000
35000, 44999, 22500
45000, null, salary.getCtc() * 0.5
```

**Execution Rules:**
- All rows with matching conditions execute (rules fire together)
- No explicit if/else—rows represent additive conditions
- Ranges should NOT overlap (Drools fires every match)
- `null` = open-ended (no min/max)
- Monetary values in rupees; automatic rounding via `decimal()`

---

### 3. SalaryService.java - Rule Orchestration

**Purpose:** Manage two-pass rule execution and coordinate computations.

#### Execution Phases

**Phase 1: Base Components**
```
1. Load SalaryFact from input
2. Fire ALL non-tax rules:
   - Basic (by CTC band)
   - HRA (by CTC/basic)
   - Bonus (if basicStat ≤ 21K)
   - PF options (P1-P5)
   - ESI (if applicable)
   - Medical (if basicStat ≥ 21K)
   - Gratuity (5% of basic)
3. Call computePreTax():
   - Calculate grossPayable = CTC - (companyPF + companyESI + gratuity)
   - Calculate specialAllowance = Gross - (basic + hra + bonus)
   - Calculate annualGross = Gross × 12
```

**Phase 2: Tax Calculations**
```
1. Fire ALL tax rules based on annualGross:
   - TaxSlab: Determine % by income bracket
   - TaxAfterRebate: Apply ₹5,000 relief
   - TaxWithCess: Apply multiplier for high incomes
2. Set tds = annualTax ÷ 12
3. Call computePostTax():
   - Calculate takeHomeSalary = Gross - deductions + CCA
   - Deductions: PF + ESI + Professional Tax + TDS + Medical
```

---

### 4. SalaryResource.java - GraphQL API

**Endpoint:** `http://localhost:8080/graphql`

#### Query: calculateSalary
Calculate salary for a single employee.

**Parameters:**
```graphql
employeeId: String!          # Required: Employee ID
name: String                 # Employee name
ctc: Float!                  # Required: Cost to Company
cca: Float                   # CCA allowance
category: String             # Employee category
location: String             # Work location
pfOption: Float              # PF option (1-5)
professionalTax: Float       # Professional tax amount
employeePFOverride: Float    # PF override value
```

**Response:**
```graphql
type SalaryResponse {
  # Input fields
  employeeId: String
  name: String
  ctc: Float
  cca: Float
  category: String
  location: String
  pfOption: Float
  professionalTax: Float
  
  # Earnings
  basic: Float
  hra: Float
  bonus: Float
  specialAllowance: Float
  employerPF: Float
  employerESI: Float
  gratuity: Float
  
  # Deductions
  employeePF: Float
  employeeESI: Float
  medicalInsurance: Float
  tds: Float
  
  # Derived
  grossPayable: Float
  annualGross: Float
  takeHomeSalary: Float
  
  # Tax info
  taxSlabBase: Float
  taxMultiplier: Float
  taxAfterRebate: Float
  taxWithCess: Float
  
  # Dynamic components
  components: Map<String, Float>
  componentTypes: Map<String, String>
  componentList: [ComponentDto]
  
  errors: [String]
}
```

#### Example Query
```graphql
query {
  calculateSalary(
    employeeId: "emp_16"
    name: "Bruce"
    ctc: 600000
    cca: 10000
    category: "Staff"
    location: "Delhi"
    pfOption: 3
    professionalTax: 200
    employeePFOverride: null
  ) {
    employeeId
    name
    ctc
    basic
    hra
    bonus
    grossPayable
    employeePF
    employeeESI
    tds
    takeHomeSalary
  }
}
```

#### Other API Endpoints

**Query: downloadRulesWorkbook**
```graphql
query {
  rulesWorkbook  # Returns base64-encoded XLSX
}
```

**Mutation: uploadRulesWorkbook**
```graphql
mutation($data: String!) {
  uploadRulesWorkbook(workbookBase64: $data)  # Returns success boolean
}
```

**Query: employeeTemplate**
```graphql
query {
  employeeTemplate  # Returns base64-encoded template XLSX
}
```

**Mutation: processEmployeesWorkbook**
```graphql
mutation($data: String!) {
  processEmployeesWorkbook(workbookBase64: $data)  # Returns base64-encoded output XLSX
}
```

---

### 5. React Frontend - User Interface

**Location:** `frontend/src/App.jsx`  
**Port:** `http://localhost:5173`

#### Features

**Manual Salary Calculator**
- Input fields: Employee ID, name, CTC, CCA, PF option, professional tax
- Displays calculated components:
  - Basic, HRA, Bonus, Gratuity
  - Employee/Employer PF, ESI
  - Tax calculation details
  - Take-home salary (with/without CCA)
- Status indicator (healthy/loading/error)

**Rules Management**
- **Download Rules:** Export current Excel workbook
- **Upload Rules:** Import updated Excel workbook (hot-reload)

**Batch Processing**
- **Download Template:** Get blank employee Excel template
- **Upload Employees:** Process multiple employees at once
- **Generate Output:** Download Excel with all salary calculations

#### Component Integration
```
GraphQL Queries ─→ Backend API (Port 8080)
        ↓
    Handle Responses
        ↓
    Format Results
        ↓
    Excel Export (XLSX Library)
```

---

## EXECUTION FLOW

### Single Employee Calculation Flow

```
1. USER INPUT
   ├─ employeeId: "emp_16"  
   ├─ ctc: 600000
   ├─ pfOption: 3
   └─ ... other fields

2. GRAPHQL REQUEST
   └─ calculateSalary(...)
      ↓
   
3. BACKEND: SalaryResource.calculateSalary()
   ├─ Look up employee in repository (if only ID provided)
   ├─ Merge input with stored data
   └─ Create SalaryFact from merged map
      ↓

4. PASS 1: RULE EXECUTION (Base Components)
   ├─ Basic Rule: ctc ≥ 45000? → basic = ctc × 0.5
   ├─ HRA Rule: ctc > 45000? → hra = basic × 0.5
   ├─ Bonus Rule: basicStat ≤ 21000? → bonus = basicStat × 0.0833
   ├─ PF Rules: pfOption == 3?
   │  ├─ employeePF = basic × 0.12
   │  └─ employerPF = basic × 0.1367
   ├─ ESI Rules: basicStat < 21000?
   │  ├─ employeeESI = basic × 0.0075
   │  └─ employerESI = basic × 0.0325
   ├─ Medical Rule: basicStat ≥ 21001? → medicalInsurance = 250
   └─ Gratuity Rule: → gratuity = basic × 0.05
      ↓

5. computePreTax() CALCULATIONS
   ├─ grossPayable = CTC - employerPF - employerESI - gratuity
   ├─ specialAllowance = grossPayable - (basic + hra + bonus)
   ├─ annualGross = grossPayable × 12
   └─ Store all fields in SalaryFact
      ↓

6. PASS 2: RULE EXECUTION (Tax Calculations)
   ├─ TaxSlab Rule: annualGross ≥ 500000?
   │  └─ taxSlabBase = grossPayable × 0.10
   ├─ TaxAfterRebate Rule: eligibleForRebate() && tax > 5000?
   │  └─ taxAfterRebate = taxSlabBase - 5000
   └─ TaxWithCess Rule: annualGross ≥ 1000000?
      └─ taxWithCess = taxAfterRebate × 1.04
      ↓

7. computePostTax() CALCULATIONS
   ├─ tds = taxWithCess ÷ 12
   ├─ takeHome = grossPayable
   ├─ takeHome -= employeePF
   ├─ takeHome -= employeeESI
   ├─ takeHome -= professionalTax
   ├─ takeHome -= tds
   ├─ takeHome -= medicalInsurance
   ├─ takeHome += cca
   └─ takeHomeSalary = takeHome (rounded)
      ↓

8. CONVERT TO RESPONSE
   └─ SalaryFact.toResponse()
      ├─ Package all 27+ fields
      ├─ Add component list
      └─ Return SalaryResponse JSON
      ↓

9. FRONTEND DISPLAY
   ├─ Show all calculated fields
   ├─ Display status: ✅ Success
   └─ Offer download options
```

### Batch Processing Flow

```
User uploads Excel (employees.xlsx)
        ↓
processEmployeesWorkbook() API
        ↓
For each row in Excel:
  ├─ Extract: employeeId, ctc, pfOption, etc.
  ├─ Create SalaryFact
  ├─ Execute two-pass rules
  ├─ Generate SalaryResponse
  └─ Build output row
        ↓
Generate output XLSX with:
  ├─ All 27+ standard columns
  ├─ Dynamic custom components
  └─ Return as base64
        ↓
Frontend: Download output.xlsx
```

---

## COMPONENT TYPE SYSTEM

Three-tier classification for salary components:

### EARNING Components
**Definition:** Amounts paid to the employee  
**Gross Impact:** +1 (Adds to grossPayable)  
**Take-Home Impact:** +1 (Adds to takeHomeSalary)  
**Examples:** Basic, HRA, Bonus, Performance allowance, Special allowance  

**Formula Impact:**
```
grossPayable  = CTC - employers + EARNING
takeHomeSalary = grossPayable - deductions + EARNING + CCA
```

### DEDUCTION Components
**Definition:** Amounts deducted from employee pay (non-tax)  
**Gross Impact:** 0 (No effect on grossPayable)  
**Take-Home Impact:** -1 (Subtracts from takeHomeSalary)  
**Examples:** Loan deduction, Mediclaim, Voluntary deduction  

**Formula Impact:**
```
grossPayable  = CTC - employers  (unaffected)
takeHomeSalary = grossPayable - DEDUCTION - taxes + CCA
```

### EMPLOYER_COST Components
**Definition:** Amounts paid by company (not employee salary)  
**Gross Impact:** -1 (Subtracts from grossPayable)  
**Take-Home Impact:** 0 (No effect on takeHomeSalary)  
**Examples:** Employer PF, Employer ESI, Gratuity  

**Formula Impact:**
```
grossPayable  = CTC - EMPLOYER_COST
takeHomeSalary = grossPayable - deductions + CCA  (unaffected)
```

### Component Declaration in Excel

Rules call `salary.addComponent()` with type classification:

```java
// Explicit type parameter:
salary.addComponent("Performance Bonus", 2500, "EARNING");
salary.addComponent("Loan Repayment", 5000, "DEDUCTION");
salary.addComponent("Company Match", 3000, "EMPLOYER_COST");

// Type auto-detected from name:
salary.addComponent("Bonus (EARNING)", 2500);           // → EARNING
salary.addComponent("Loan [DEDUCTION]", 5000);          // → DEDUCTION
salary.addComponent("Match (Employer Cost)", 3000);     // → EMPLOYER_COST

// Default to EARNING if no type specified:
salary.addComponent("Allowance", 1000);                 // → EARNING (default)
```

---

## KEY FEATURES

### ✅ Excel-Based Rule Management
- HR teams edit rules without coding knowledge
- 13 configurable RuleTables for all components
- Hot-reload: Upload new rules, immediate effect
- Version control friendly (Excel is editable)

### ✅ Two-Pass Rule Execution
- **Pass 1:** Base salary components before tax
- **Pass 2:** Tax calculations after gross known
- Allows rules to depend on derived values

### ✅ Dynamic Custom Components
- Add new salary components at runtime
- Type-based impact calculation (EARNING/DEDUCTION/EMPLOYER_COST)
- Auto-normalize names and type detection
- Included in response JSON

### ✅ Multiple PF Schemes
- Five PF options (P1-P5)
- Different contribution rates per option
- Supports PF overrides per employee

### ✅ Tax Band Support
- Income slab-based tax calculation
- Tax rebates (₹5,000 if eligible)
- Cess multiplier for high income
- Annual-to-monthly conversion

### ✅ Batch Processing
- Upload Excel with multiple employees
- Process all at once
- Download Excel with complete salary data
- Template available for bulk uploads

### ✅ GraphQL API
- Modern, flexible query interface
- Type-safe parameters
- Easy integration with frontend/mobile apps
- Introspection support for documentation

### ✅ Fallback Logic
- Sensible defaults when rules missing
- Continues processing even with incomplete sheets
- Graceful error reporting

---

## OUTPUT STRUCTURE

### 27+ Output Fields

#### INPUT SECTION (9 fields)
```
employeeId          String   - Employee identifier
name                String   - Employee name
ctc                 Float    - Cost to Company (annual)
cca                 Float    - CCA allowance
category            String   - Employee category
location            String   - Work location
pfOption            Float    - PF selection (1-5)
professionalTax     Float    - Professional tax
employeePFOverride  Float    - Custom PF override
```

#### EARNINGS SECTION (6 fields)
```
basic               Float    - Base salary from CTC bands
basicStat           Float    - max(basic, 50% of CTC+CCA)
hra                 Float    - House Rent Allowance
bonus               Float    - Performance/annual bonus
specialAllowance    Float    - Auto-balanced remainder
gratuity            Float    - Gratuity (5% of basic)
```

#### DEDUCTIONS SECTION (5 fields)
```
employeePF          Float    - Employee PF contribution %
employeeESI         Float    - Employee ESI deduction
professionalTax     Float    - State professional tax
medicalInsurance    Float    - Medical insurance deduction
tds                 Float    - Tax Deducted at Source
```

#### DERIVED SECTION (3 fields)
```
grossPayable        Float    - Monthly gross after employer costs
annualGross         Float    - Annual gross (gross × 12)
takeHomeSalary      Float    - Final net salary
```

#### EMPLOYER SECTION (2 fields)
```
employerPF          Float    - Employer PF contribution
employerESI         Float    - Employer ESI contribution
```

#### TAX DETAILS SECTION (4 fields - for reference)
```
taxSlabBase         Float    - Base for tax slab
taxMultiplier       Float    - Tax rate multiplier
taxAfterRebate      Float    - Tax after rebate
taxWithCess         Float    - Tax with cess applied
```

#### DYNAMIC COMPONENTS SECTION (Variable)
```
[Component Name]    Float    - Calculated amount
[Component Name]    Float    - Calculated amount
...
```

### Sample Output

```
employeeId | name  | ctc     | basic | hra   | bonus | grossPayable | takeHomeSalary
-----------|-------|---------|-------|-------|-------|--------------|---------------
emp_101    | John  | 600000  | 50000 | 25000 | 4167  | 525000       | 450000
emp_102    | Jane  | 800000  | 60000 | 30000 | 5000  | 700000       | 580000
emp_103    | Bob   | 1200000 | 80000 | 40000 | 6667  | 900000       | 750000
```

---

## API DOCUMENTATION

### GraphQL Endpoint

**URL:** `http://localhost:8080/graphql`

**Development UI:** `http://localhost:8080/q/dev/`

### Queries

#### 1. calculateSalary (Primary Query)

**Purpose:** Calculate salary for a single employee

**GraphQL:**
```graphql
query calculateSalary(
  $employeeId: String!
  $name: String
  $ctc: Float!
  $cca: Float
  $category: String
  $location: String
  $pfOption: Float
  $professionalTax: Float
  $employeePFOverride: Float
) {
  calculateSalary(
    employeeId: $employeeId
    name: $name
    ctc: $ctc
    cca: $cca
    category: $category
    location: $location
    pfOption: $pfOption
    professionalTax: $professionalTax
    employeePFOverride: $employeePFOverride
  ) {
    employeeId
    name
    ctc
    cca
    basic
    hra
    bonus
    gratuity
    specialAllowance
    grossPayable
    employeePF
    employerPF
    employeeESI
    employerESI
    medicalInsurance
    tds
    taxSlabBase
    taxMultiplier
    taxAfterRebate
    taxWithCess
    takeHomeSalary
    errors
  }
}
```

**Variables:**
```json
{
  "employeeId": "emp_16",
  "name": "Bruce",
  "ctc": 600000,
  "cca": 10000,
  "category": "Staff",
  "location": "Delhi",
  "pfOption": 3,
  "professionalTax": 200,
  "employeePFOverride": null
}
```

#### 2. rulesWorkbook (Download Rules)

**Purpose:** Export current salary rules as Excel

**GraphQL:**
```graphql
query {
  rulesWorkbook
}
```

**Response:** Base64-encoded XLSX file

#### 3. employeeTemplate (Get Batch Template)

**Purpose:** Download template for batch employee upload

**GraphQL:**
```graphql
query {
  employeeTemplate
}
```

**Response:** Base64-encoded XLSX template

### Mutations

#### 1. uploadRulesWorkbook

**Purpose:** Update salary rules and hot-reload

**GraphQL:**
```graphql
mutation uploadRulesWorkbook($data: String!) {
  uploadRulesWorkbook(workbookBase64: $data)
}
```

**Variables:**
```json
{
  "data": "[base64-encoded XLSX file]"
}
```

**Response:** `true` if successful, `false` if error

#### 2. processEmployeesWorkbook

**Purpose:** Batch process multiple employees

**GraphQL:**
```graphql
mutation processEmployeesWorkbook($data: String!) {
  processEmployeesWorkbook(workbookBase64: $data)
}
```

**Variables:**
```json
{
  "data": "[base64-encoded employee XLSX]"
}
```

**Response:** Base64-encoded output XLSX with calculated salaries

---

## GETTING STARTED

### Prerequisites

- **Java 17+** with Maven 3.8+
- **Node.js 16+** with npm 8+
- **Excel** or compatible spreadsheet editor (LibreOffice, Google Sheets)

### Backend Setup

```bash
# Navigate to backend directory
cd salary-kogito

# Compile and run in development mode
./mvnw compile quarkus:dev

# Backend runs on http://localhost:8080
# GraphQL API at http://localhost:8080/graphql
# Dev UI at http://localhost:8080/q/dev/
```

### Frontend Setup

```bash
# Navigate to frontend directory
cd frontend

# Install dependencies
npm install

# Start development server
npm run dev

# Frontend runs on http://localhost:5173
```

### Running Both Together

**Terminal 1 (Backend):**
```bash
cd salary-kogito
./mvnw compile quarkus:dev
```

**Terminal 2 (Frontend):**
```bash
cd frontend
npm install
npm run dev
```

Then open your browser to `http://localhost:5173`

### First Calculation

1. Open `http://localhost:5173`
2. Keep default Employee ID (`emp_16`) or enter your own
3. Enter CTC: `600000`
4. Enter CCA: `10000`
5. Select PF Option: `3` (P3)
6. Click **Calculate**
7. View result: Basic, HRA, Bonus, Tax, Take-Home

### Upload Custom Rules

1. Download current rules from frontend
2. Edit `salary-rules.xlsx` (adjust CTC bands, percentages, etc.)
3. Click **Upload Rules**
4. Rules are immediately effective (no rebuild needed)

### Batch Process Employees

1. Click **Download Template** to get blank workbook
2. Fill in employee data (ID, CTC, location, PF option, etc.)
3. Click **Upload Employees**
4. Download output XLSX with all calculations

---

## PROJECT STRUCTURE

```
Salary_full_stack/
├── README.md                           # Main project guide
├── IMPLEMENTATION_SUMMARY.md           # Implementation details
├── SALARY_OUTPUT_STRUCTURE.md          # Output field documentation
├── COMPONENT_FLOW_ANALYSIS.md          # Component flow details
├── KOGITO_VALIDATION_REPORT.md         # Rules validation report
│
├── frontend/                           # React + Vite UI
│   ├── src/
│   │   ├── App.jsx                     # Main component
│   │   ├── api.js                      # GraphQL client
│   │   ├── App.css                     # Styling
│   │   └── main.jsx                    # Entry point
│   ├── package.json
│   ├── vite.config.js
│   └── index.html
│
└── salary-kogito/                      # Quarkus + Kogito backend
    ├── pom.xml                         # Maven configuration
    ├── RULES.md                        # Rules documentation
    ├── mvnw / mvnw.cmd                 # Maven wrapper
    │
    ├── src/main/
    │   ├── java/org/acme/salary/
    │   │   ├── SalaryFact.java          # Domain model
    │   │   ├── SalaryService.java       # Rule orchestration
    │   │   ├── SalaryResource.java      # GraphQL API
    │   │   ├── SalaryResponse.java      # API response dto
    │   │   ├── EmployeeRepository.java  # Employee data access
    │   │   ├── CorsFilter.java          # CORS configuration
    │   │   ├── RulesGraphQL.java        # Rules API
    │   │   └── ... other classes
    │   │
    │   └── resources/
    │       ├── salary-rules.xlsx        # Decision tables (bundled)
    │       ├── employees.json           # Sample data
    │       ├── application.properties   # Configuration
    │       └── META-INF/resources/index.html
    │
    ├── data/
    │   └── rules/salary-rules.xlsx      # Rules copy (runtime editable)
    │
    └── target/                          # Build artifacts
        └── classes/
            └── ... compiled files
```

---

## TROUBLESHOOTING

### Backend Won't Start

**Error:** `Failed to compile`
- Check Java version: `java -version` (need 17+)
- Clear cache: `./mvnw clean`
- Rebuild: `./mvnw compile quarkus:dev`

**Error:** `Port 8080 already in use`
- Find process: `lsof -i :8080`
- Kill process: `kill -9 [PID]`
- Or change port in `application.properties`

### GraphQL Not Responding

- Verify backend is running on `http://localhost:8080`
- Check GraphQL endpoint: `http://localhost:8080/graphql`
- Try Dev UI: `http://localhost:8080/q/dev/`
- Check console for errors

### Rules Not Updating After Upload

- Ensure XLSX is valid (no merged cells, proper headers)
- Check backend console for parse errors
- Verify RuleTable names match expected format
- Try downloading rules first, then edit and re-upload

### Calculation Results Seem Wrong

- Check PF option (1-5) matches expected scheme
- Verify CTC is annual (not monthly)
- Review tax slab rules in Excel (may differ by region)
- Check if any rules are missing in spreadsheet
- Validate basic salary calculation first (simplest component)

---

## SUPPORT & MAINTENANCE

### Updating Salary Rules

**Without Code Changes:**
1. Download `salary-rules.xlsx` from frontend
2. Edit appropriate RuleTable:
   - Adjust CTC bands
   - Change percentages
   - Add new components
3. Upload updated file
4. Test with sample employees
5. No rebuilding needed!

### Adding New Components

**In salary-rules.xlsx:**
```
1. Find appropriate section (or add new section)
2. Add RuleTable header:
   RuleTable, [NewComponentName]
3. Define CONDITIONS and ACTIONS
4. In ACTION, call:
   salary.addComponent("Name", amount, "TYPE")
   TYPE = EARNING | DEDUCTION | EMPLOYER_COST
5. Upload and test
```

### Common Modifications

| Need | Location | Steps |
|------|----------|-------|
| Change CTC band thresholds | salary-rules.xlsx, Basic table | Edit $ctcMin, $ctcMax columns |
| Add HRA for new locations | salary-rules.xlsx, HRA table | Add rule row with new conditions |
| Adjust tax rates | salary-rules.xlsx, TaxSlab table | Modify percentage columns |
| Add custom allowance | salary-rules.xlsx | New RuleTable + addComponent() call |
| Change PF percentages | salary-rules.xlsx, EmployeePF/EmployerPF | Edit rate columns |

---

## CONCLUSION

The Salary Full-Stack application provides a **production-ready, rules-engine-based salary calculation system** that balances:

✅ **Technical Robustness** - Two-pass rule execution, proper data normalization  
✅ **Business Flexibility** - HR-editable Excel rules, custom components  
✅ **User Experience** - Simple UI, batch processing, instant feedback  
✅ **Scalability** - GraphQL API, hot-reload, containerizable (Docker ready)  
✅ **Maintainability** - Clear separation of concerns, well-documented rules  

Whether calculating single employee salaries or processing payroll for hundreds, the system provides accuracy, traceability, and ease of modification.

---

**Document Version:** 1.0  
**Last Updated:** April 13, 2026  
**Project Status:** Production Ready ✅
