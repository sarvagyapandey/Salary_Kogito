# Complete Salary Output Structure

## Overview
After processing employees through the salary calculation engine, the Excel output includes **standard salary fields + dynamic component fields**.

---

## Standard Fields (20 Columns)

### INPUT FIELDS (9)
User-provided employee data passed to the calculation engine.
1. **employeeId** - Employee identifier
2. **name** - Employee name
3. **ctc** - Cost to Company (annual)
4. **cca** - Additional compensation component
5. **category** - Employee category
6. **location** - Work location
7. **pfOption** - PF selection (P1-P5)
8. **professionalTax** - Professional tax amount
9. **employeePFOverride** - Custom PF override

### EARNINGS (6)
Components that add to both gross and take-home salary.
10. **basic** - Base salary (calculated from CTC by rules)
11. **hra** - House Rent Allowance (50% of basic for CTC > 45K, else 0)
12. **specialAllowance** - Auto-balancing component (Gross - Basic - HRA - Bonus - other earnings)
13. **bonus** - 8.33% of basicStat when basicStat ≤ 21K
14. **employerPF** - Employer's PF contribution (set by pfOption)
15. **employerESI** - Employer's ESI contribution (set by employment category)

### DEDUCTIONS (5)
Components that reduce take-home salary (but NOT gross).
16. **employeePF** - Employee's PF deduction
17. **employeeESI** - Employee's ESI deduction
18. **medicalInsurance** - Medical coverage deduction (250 when basicStat ≥ 21K)
19. **tds** - Tax Deducted at Source (calculated from TDS rules)
20. **takeHomeSalary** - Final net salary = Gross - Deductions + CCA

### DERIVED FIELDS (3)
Calculated values derived from rules and basic fields.
21. **grossPayable** - Gross = CTC - (employerPF + employerESI + gratuity)
22. **annualGross** - Annual gross = Gross × 12
23. **gratuity** - Gratuity amount (5% of basic)

### TAX FIELDS (4)
Used in TDS calculation (informational).
24. **taxSlabBase** - Base for tax slab calculation
25. **taxMultiplier** - Tax rate multiplier
26. **taxAfterRebate** - Tax after rebates
27. **taxWithCess** - Final tax amount

---

## Dynamic Components (Variable Columns)

Custom salary components added via Excel rules. Each uses the format:
```
$salary.addComponent("Component Name (TYPE)", SalaryFact.amount(...))
```

### Component Types & Impact

| Type | Component Name Format | Gross Impact | Take-Home Impact |
|------|----------------------|--------------|-----------------|
| **EARNING** | "Name (Earning)" | +1 (Adds to gross) | +1 (Adds to take-home) |
| **DEDUCTION** | "Name (Deduction)" | 0 (No gross impact) | -1 (Reduces take-home) |
| **EMPLOYER_COST** | "Name (Employer Cost)" | -1 (Reduces gross) | 0 (No take-home impact) |

### Currently Implemented Custom Components

#### 1. **Performance Bonus (Earning)**
- **Type**: EARNING
- **Calculation**: 5% of basic salary
- **CTC Range**: ₹3,00,000 - ₹1,00,00,000
- **Effect**: 
  - Adds to Gross Payable
  - Adds to Take-Home Salary
  - Updates Special Allowance calculation

---

## Excel Output Example

```
employeeId | name    | ctc     | cca    | ... | basic | hra   | bonus | grossPayable | ... | Performance Bonus (Earning) | takeHomeSalary
-----------|---------|---------|--------|-----|-------|-------|-------|--------------|-----|------------------------------|---------------
emp_101    | John    | 600000  | 10000  | ... | 50000 | 25000 | 4167  | 525000       | ... | 2500                         | 450000
emp_102    | Jane    | 800000  | 10000  | ... | 60000 | 30000 | 5000  | 700000       | ... | 3000                         | 580000
emp_103    | Bob     | 1200000 | 15000  | ... | 80000 | 40000 | 6667  | 900000       | ... | 4000                         | 750000
```

---

## How to Add More Custom Components

### Method 1: Modify salary-rules.xlsx
1. Open `data/rules/salary-rules.xlsx`
2. Find the "PerformanceBonus(Earning)" table (rows 98-103)
3. Add more RuleTables after it with format:

```
RuleTable LTAReimbursement(Earning)

NAME | CONDITION           | CONDITION        | ACTION
-----|---------------------|------------------|--------
     | $salary:SalaryFact  |                  |
     | ctc >= $ctcMin      | ctc <= $ctcMax   | $salary.addComponent("LTA Reimbursement (Earning)", SalaryFact.amount(null, null, $fixed, null, null)); update($salary);
Name | ctcMin              | ctcMax           | LTA Amount
lta1 | 300000              | 10000000         | Fixed LTA: 50000
```

4. Component naming: Use `(Earning)`, `(Deduction)`, or `(Employer Cost)` suffix
5. Save and upload via frontend

### Method 2: Upload new Excel file
1. Download rules from frontend using "Download Rules" button
2. Edit the Excel file
3. Upload back using "Upload Rules" button
4. Backend automatically reloads rules without restart ✅

---

## Calculation Flow

```
INPUT (emp data)
    ↓
PASS 1: Rule Engine fires
    → basic = 8.33% of CTC (by CTC band)
    → hra = 0 or 50% of basic (by CTC band)
    → bonus, pf, esi, medical, gratuity
    → addComponent() for custom components
    ↓
computePreTax()
    → grossPayable = CTC - (employerPF + employerESI + gratuity)
    → specialAllowance = grossPayable - (basic + HRA + bonus + custom earnings)
    ↓
PASS 2: Tax Rule Engine fires
    → tds = tax calculation based on grossPayable
    ↓
computePostTax()
    → takeHomeSalary = grossPayable - (empPF + empESI + TDS + PT + medical + custom deductions) + CCA
    ↓
OUTPUT: SalaryResponse with all 27+ fields + dynamic components
```

---

## Verification Checklist

- [x] Standard fields present in Excel output (basic, hra, bonus, etc.)
- [x] Custom Performance Bonus (Earning) component implemented
- [x] Component appears as new Excel column when processing employees
- [x] Proper impact on grossPayable and takeHomeSalary
- [ ] Test with sample employee data to verify numbers
- [ ] Add more custom components as needed

---

## Debug Logging

When processing Excel files, check backend console for:
```
DEBUG: Total dynamic component names collected: [Performance Bonus (Earning)]
DEBUG: Writing 1 dynamic components for row 2
  DEBUG: Component 'Performance Bonus (Earning)' = 2500.0
```

If you don't see this output, components are not being created by rules.

