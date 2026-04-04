# Salary Rules Cheat Sheet

This project now uses a multi-sheet Excel workbook at `src/main/resources/salary-rules.xlsx` (generated from the original CSV). Each sheet represents one salary component; adding a sheet adds a new component.

Each sheet has the form:

- `RuleTable,<Name>` — starts a table.
- `NAME,...` — header row with parameter names used in the conditions/actions.
- `CONDITION` — the constraint applied to `SalaryFact`; `$colName` references the column below.
- `ACTION` — fields set on `SalaryFact` when the row matches.

Key tables

- **Basic**: Sets `basic` based on CTC bands and updates `basicStat` (max of basic vs 50% of CTC+CCA).
- **HRA**: For CTC 25,001–45,000 HRA is `0`. For CTC ≥ 45,001 HRA is `50% of basic`.
- **Bonus**: 8.33% of `basicStat` when `basicStat` ≤ 21,000, else `0`.
- **PF/ESI**: `EmployeePF` and `EmployerPF/ESI` depend on the `pfOption` chosen (P1–P5).
- **Medical**: Adds 250 when `basicStat` ≥ 21,001, otherwise 0.
- **TaxSlab & TaxMultiplier**: Compute monthly TDS based on annual gross and a slab multiplier.

Flow

1. Rules fire once to set basic/hra/bonus/pf/esi/medical (all non-tax sheets).
2. `computePreTax()` derives gratuity, gross payable, special allowance, annual gross.
3. Rules fire again to set tax slab & multiplier (Tax* sheets).
4. `computePostTax()` computes TDS and take-home.

Troubleshooting tips

- If a band should be exclusive, ensure ranges do **not overlap**; Drools will fire every matching row.
- Use `null` for an open-ended min/max.
- Keep monetary formulas in rupees; rounding uses `SalaryFact.decimal()` (HALF_UP).

Editing the workbook

- Open `salary-rules.xlsx`; update the relevant sheet per component.
- To add a new component, duplicate an existing sheet, change the `RuleTable,<Name>` row to a new name, and adjust columns/rows.
- Keep the first three rows (`RuleSet`, `Import`, `Import`) as-is on every sheet so Drools can resolve classes.

Adding new salary components

- Component types:
  - `EARNING` (paid to employee): added to `grossPayable` and `takeHome`.
  - `DEDUCTION` (employee-paid): subtracted only from `takeHome`.
  - `EMPLOYER_COST` (company-paid): subtracted from `grossPayable` only.
- HR-friendly entry: put the type in the component name, e.g. `Fuel Allowance (EARNING)` or `Mediclaim [DEDUCTION]`.
- Use a fixed ACTION once per component section: `salary.addComponent($compName, SalaryFact.amount(base, $percent, $fixed, $cap, $minDefault))`. The engine auto-detects the type from the name suffix and defaults to `EARNING` if none is provided.
- Special allowance auto-balances after all earning components are added.

Column meanings (all sheets)
- `ctcMin` / `ctcMax` (or other range columns shown): bands; leave blank for open-ended.
- `percent`: e.g., 40 = 40% of the sheet’s base.
- `fixed`: absolute amount; overrides percent if filled.
- `cap`: optional upper cap on the base before applying percent.
- `minDefault`: optional floor applied after calculation (used for PF overrides, etc.).
