# ⚡ Quick Reference: Single Employee Salary Calculator

## 🎯 In 30 Seconds

**All services are running!** Start calculating salaries:

1. Open: http://localhost:5173
2. Scroll to "Single Employee Calculation" section
3. Click **Calculate** (form is pre-filled with demo data)
4. See salary breakdown

---

## 🔄 Data Flow

```
Employee Form (React)
         ↓
  GraphQL Query
         ↓
Backend Rules (Quarkus)
         ↓
MongoDB Lookup (optional)
         ↓
Calculated Salary → Display Results
```

---

## 📋 Form Fields

| Field | Default | Example | Notes |
|-------|---------|---------|-------|
| Employee ID | emp_16 | emp_16 | Try: emp_17, emp_18 |
| Name | blank | Bruce | Auto-fills from DB |
| CTC | 600000 | 600000 | Annual salary |
| CCA | 10000 | 10000 | Cash allowance |
| **Location** | **Delhi** | **Delhi** | NEW field! Affects HRA |
| PF Option | 3 | 3 | 1-5 (deduction method) |
| Tax | 200 | 200 | Professional tax |
| PF Override | blank | 1800 | For PF Option 5 |

---

## 💰 What You Get Back

```
Earnings:
- Basic: ₹300,000
- HRA: ₹150,000
- Allowance: ₹xxx

Gross Payable: ₹583,200

Deductions:
- Employee PF: ₹72,000
- ESI: ₹4,374
- TDS: ₹xxx
- Tax: ₹200

TAKE HOME: ₹396,236 (without CCA)
TAKE HOME: ₹406,236 (with CCA)
```

---

## 🔗 Available Demo Employees

### emp_16 (Bruce)
- CTC: ₹600,000
- PF Option: 3
- Location: Delhi
- **Use this for normal testing**

### emp_17 (Batman)  
- CTC: ₹42,000
- PF Option: 2
- Location: Delhi
- **Use for low-CTC testing**

### emp_18 (Rah)
- CTC: ₹2,000,000
- PF Option: 5
- Location: Delhi
- **Use for high-CTC testing**

---

## 🧪 Quick Tests

### Test 1: UI Calculation
```
1. Open http://localhost:5173
2. Fill form (already pre-filled)
3. Click Calculate
4. Check results display
```

### Test 2: Direct API
```bash
curl -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{"query": "query { calculateSalary(employeeId: \"emp_16\", ctc: 600000, location: \"Delhi\", pfOption: 3) { employeeId basic hra grossPayable takeHomeSalary } }"}'
```

### Test 3: Different Employee
Change form field "Employee ID" from emp_16 to:
- `emp_17` (different salary bracket)
- `emp_18` (large salary)

---

## ⚙️ Services Status

| Service | Port | Status | Command |
|---------|------|--------|---------|
| Frontend | 5173 | ✅ Running | `npm run dev` |
| Backend | 8080 | ✅ Running | `./mvnw quarkus:dev` |
| MongoDB | 27018 | ✅ Running | `docker compose up -d` |

---

## 🔧 Files Changed

1. ✅ `frontend/src/App.jsx`
   - Added `location` state
   - Added location input field  
   - Updated GraphQL with location param

2. ✅ `load_employees.py` 
   - Script to load demo employees

3. ✅ Demo employees loaded in MongoDB

---

## 🆘 Troubleshooting

| Issue | Solution |
|-------|----------|
| "Backend not responding" | Check: `curl http://localhost:8080` |
| "CORS error" | Make sure frontend is on `localhost:5173` |
| "No employees found" | Run: `python3 load_employees.py` |
| "Form not updating" | Clear cache: `Ctrl+Shift+Delete` then refresh |

---

## 📊 Example Calculations

### Scenario 1: emp_16 (Bruce)
```
Input: CTC=600000, Location=Delhi, PF=3
Output: Take Home = ₹396,236
```

### Scenario 2: emp_17 (Batman - Low CTC)
```
Input: CTC=42000, Location=Delhi, PF=2
Output: Take Home = ₹xxx (validates PF)
```

### Scenario 3: emp_18 (Rah - High CTC)
```
Input: CTC=2000000, Location=Delhi, PF=5
Output: Take Home = ₹xxx (with override)
```

---

## 🚀 Next Steps

1. ✅ Test single employee calculation
2. → Download salary rules (from UI)
3. → Modify rules in Excel
4. → Upload modified rules
5. → Process batch employee file

---

**The integration is complete! Everything is running. Start testing now! 🎉**

Last updated: Apr 17, 2026
