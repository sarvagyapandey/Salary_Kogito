# Kogito Spreadsheet Format Validation Report

## Summary
Your Excel file `salary-rules.xlsx` is mostly correct but has several **formatting inconsistencies** that need to be fixed for proper Kogito decision table parsing.

---

## ✅ CORRECT SECTIONS

### 1. File Header (Rows 1-2)
- ✅ RuleSet: `salaryRules` 
- ✅ Import: `org.acme.salary.SalaryFact`

### 2. Rule Tables Present
- ✅ Basic (salary setting based on CTC bands)
- ✅ BasicStat (derived from basic)
- ✅ HRA (depends on CTC and basic)
- ✅ Bonus (based on basicStat)
- ✅ EmployeePF & EmployerPF (based on CTC and pfOption)
- ✅ ESI (based on basicStat)
- ✅ TaxSlab (tax calculation based on grossPayable)
- ✅ TaxAfterRebate (tax rebate logic)
- ✅ TaxWithCess (cess calculation)
- ✅ FinalCalc (pre/post tax computations)

---

## ❌ FORMATTING ISSUES FOUND

### Issue 1: Inconsistent Column References

**Location**: HRA, Bonus, and other tables

**Problem**: Variable names used in CONDITIONS don't match standardized naming

```
Current (WRONG):
  HRA table uses:  $min, $max
  Basic table uses: $ctcMin, $ctcMax
  
Expected (Kogito standard):
  All should use consistent variable naming like $min, $max or $ctcMin, $ctcMax
```

**Impact**: Drools may not properly map values from header row to conditions

---

### Issue 2: Missing Medical Table

**Location**: Documentation mentions Medical insurance rule, **but it's not in your Excel**

**Missing Rule**: Medical Allowance should be a separate RuleTable
- Sets medical insurance to 250 when basicStat ≥ 21,001
- Currently hardcoded in `computePostTax()` method (WRONG)

---

### Issue 3: Row Structure Issues

**Location**: Rows 10, 18, 23, 30, etc. (CONDITION definition rows)

**Problem**: Some condition definition rows are incomplete or inconsistent:
- Row 10 (Basic): `['', '$sf : SalaryFact()', '$sf.getCtc() >= $ctcMin', '$sf.getCtc() <= $ctcMax', '', '', '']`
  - Has blank NAME column (Column A)
  - ✅ This is CORRECT for Drools format

- Row 18 (BasicStat): Has blank columns that should remain empty
  - ✅ This is CORRECT

---

### Issue 4: Bonus Formula Truncation

**Location**: Row 31, Bonus table

**Problem**: Formula appears truncated in display: `$sf.setBonus(SalaryFact.decimal($sf.getBasic...`

**Need to verify**: The full formula should be:
```
$sf.setBonus(SalaryFact.decimal($sf.getBasicStat() * 0.0833))
```

---

### Issue 5: Code-Spreadsheet Mismatch

**Location**: SalaryFact.java computePreTax() and computePostTax()

**Hardcoded Logic Found** (SHOULD BE IN SPREADSHEET):
```java
computePreTax():
  - this.gratuity = decimal(basic * 0.05);  // 5% gratuity
  
computePostTax():
  - Default rebate: Math.max(taxSlabBase - 5000, 0)    // 5000 rebate
  - Default cess: taxAfterRebate * 1.04                // 4% cess
  - Medical insurance removed instead of calculated
```

**Expected**: These should all be driven by the spreadsheet in FinalCalc and other tables

---

## 🔧 REQUIRED FIXES

### Fix 1: Standardize Column Variable Names

**Action**: Make all column references consistent across all tables
- Use `$min` and `$max` for range columns consistently
- Or use more descriptive names like `$ctcMin`, `$ctcMax`, `$basicSalaryMin`

### Fix 2: Add Missing Medical Table

**Action**: Create new RuleTable for Medical:
```
RuleTable Medical
NAME, CONDITION, ACTION
BasicStat Range, $sf.getBasicStat() >= $limit, $sf.setMedicalInsurance(250)
```

### Fix 3: Move Hardcoded Logic to Spreadsheet

**Action**: Create new RuleTables:
- **Gratuity**: Set gratuity = 5% of basic (move from computePreTax)
- **DefaultTaxRebate**: Set tax rebate = max(taxSlabBase - 5000, 0) if not set
- **DefaultCess**: Multiply tax by 1.04 if not already set

### Fix 4: Update Code to Load All Rules from Spreadsheet

**Action**: Modify SalaryFact.java:
- Remove hardcoded computePostTax() logic for rebate/cess defaults
- Let spreadsheet rules fire instead
- Only keep the mathematical utility methods

---

## Summary of Changes Needed

| Component | Current Status | Action Required |
|-----------|---|---|
| Basic | ✅ Correct | None |
| BasicStat | ✅ Correct | None |
| HRA | ⚠️ Column naming | Standardize $min/$max |
| Bonus | ⚠️ Formula truncated? | Verify full formula |
| EmployeePF | ✅ Correct | None |
| EmployerPF | ✅ Correct | None |
| ESI | ✅ Correct | None |
| TaxSlab | ✅ Correct | None |
| TaxAfterRebate | ⚠️ Incomplete | Add default logic to spreadsheet |
| TaxWithCess | ⚠️ Incomplete | Fix formula, add to spreadsheet |
| Medical | ❌ MISSING | Create new table |
| Gratuity | ❌ END CODE | Move to spreadsheet |

---

## Next Steps

1. **Fix and corrects the XLSX file** to standardize variable names and add missing tables
2. **Update SalaryFact.java** to remove hardcoded computation logic
3. **Test** end-to-end to ensure all rules fire from spreadsheet only
