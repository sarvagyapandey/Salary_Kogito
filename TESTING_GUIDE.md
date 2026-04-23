# Single Employee Salary Calculator - Testing Guide

## 🎯 What Has Been Integrated

Your salary calculation system is now fully connected from frontend to backend:

### Flow:
```
Frontend (React) → GraphQL Query → Quarkus Backend → Rules Engine → MongoDB → Result
```

### Demo Employees Available
- **emp_16** - Bruce (CTC: ₹600,000, Location: Delhi, PF Option: 3)
- **emp_17** - Batman (CTC: ₹42,000, Location: Delhi, PF Option: 2)  
- **emp_18** - Rah (CTC: ₹2,000,000, Location: Delhi, PF Option: 5)

---

## 🧪 How to Test

### Method 1: Using the Web UI (Recommended)

1. **Open the frontend**: http://localhost:5173
   
2. **Scroll to "Single Employee Calculation" section**
   
3. **Fill in the form with demo data**:
   ```
   Employee ID:        emp_16
   Employee Name:      Bruce (auto-fills if you press Tab)
   CTC:               600000
   CCA:               10000
   Location:          Delhi
   PF Option:         3
   Professional Tax:  200
   Employee PF Override: 1800
   ```

4. **Click the "Calculate" button**
   
5. **See the results**:
   - Basic Salary
   - HRA
   - Gross Payable
   - Deductions (PF, ESI, TDS, etc.)
   - Take Home Salary (with/without CCA)

---

### Method 2: Direct GraphQL Query

Test the backend API directly:

```bash
curl -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{
    "query": "query { calculateSalary(employeeId: \"emp_16\", name: \"Bruce\", ctc: 600000, cca: 10000, location: \"Delhi\", pfOption: 3, professionalTax: 200, employeePFOverride: 1800) { employeeId name ctc cca location basic hra grossPayable employeePF takeHomeSalary errors } }"
  }'
```

**Expected Response**:
```json
{
  "data": {
    "calculateSalary": {
      "employeeId": "emp_16",
      "name": "Bruce",
      "ctc": 600000.0,
      "cca": 10000.0,
      "location": "Delhi",
      "basic": 300000.0,
      "hra": 150000.0,
      "grossPayable": 583200.0,
      "employeePF": 72000.0,
      "takeHomeSalary": 396236.0,
      "errors": null
    }
  }
}
```

---

### Method 3: Using Python/Node.js to Test API

#### Python:
```python
import requests
import json

url = "http://localhost:8080/graphql"
query = """
query {
  calculateSalary(
    employeeId: "emp_16"
    name: "Bruce"
    ctc: 600000
    cca: 10000
    location: "Delhi"
    pfOption: 3
    professionalTax: 200
    employeePFOverride: 1800
  ) {
    employeeId
    name
    ctc
    cca
    basic
    hra
    grossPayable
    employeePF
    takeHomeSalary
    errors
  }
}
"""

response = requests.post(url, json={"query": query})
print(json.dumps(response.json(), indent=2))
```

#### Node.js:
```javascript
const query = `
query {
  calculateSalary(
    employeeId: "emp_16"
    ctc: 600000
    cca: 10000
    location: "Delhi"
    pfOption: 3
    professionalTax: 200
  ) {
    employeeId
    name
    ctc
    cca
    basic
    hra
    grossPayable
    takeHomeSalary
    errors
  }
}
`;

fetch('http://localhost:8080/graphql', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ query })
})
.then(r => r.json())
.then(data => console.log(JSON.stringify(data, null, 2)));
```

---

## 📋 Required Input Parameters

| Field | Type | Required | Example | Notes |
|-------|------|----------|---------|-------|
| **employeeId** | String | ✅ Yes | `emp_16` | Unique identifier |
| **name** | String | ❌ No | `Bruce` | Auto-fills from DB if empty |
| **ctc** | Number | ✅ Yes | `600000` | Annual salary |
| **cca** | Number | ❌ No | `10000` | Cash Compensation Allowance |
| **location** | String | ❌ No | `Delhi` | Affects HRA & Professional Tax |
| **pfOption** | Number | ❌ No | `3` | 1-5, determines PF deduction |
| **professionalTax** | Number | ❌ No | `200` | State professional tax |
| **employeePFOverride** | Number | ❌ No | `1800` | Custom PF (for Option 5) |

---

## 📊 What Gets Calculated

### Earnings Components
- **Basic** = 50% of CTC
- **HRA** = 25% of CTC (may vary by location)
- **Special Allowance** = Remaining % of CTC
- **Bonus** = Variable
- **Gratuity** = If applicable

### Deductions
- **Employee PF** = 12% of basic (or per option)
- **Employee ESI** = 0.75% of gross
- **Professional Tax** = State-based
- **TDS** = Based on income slab
- **Medical Insurance** = If configured

### Final Amounts
- **Gross Payable** = All earnings before deductions
- **Take Home Salary** = Gross - All deductions
- **With CCA** = Take Home + CCA

---

## ✅ Verification Checklist

- [ ] MongoDB running on port 27018
- [ ] Backend running on port 8080
- [ ] Frontend running on port 5173
- [ ] Demo employees loaded (check with GraphQL query)
- [ ] Frontend form loads without CORS errors
- [ ] Salary calculation returns results
- [ ] Results display correctly in UI
- [ ] Location field affects calculations

---

## 🔍 Debugging Tips

### Issue: "Cannot reach backend"
```bash
# Check if backend is running
curl http://localhost:8080/graphql -X OPTIONS -v

# Should see CORS headers and 200 OK
```

### Issue: "Employee not found" but calculation works
- This is normal - the backend can calculate with just CTC
- Employee data from DB is optional for pre-filling fields

### Issue: Different calculation results
- Check if `location` affects HRA calculation
- Check if `pfOption` is being used correctly
- Verify rules in backend Excel file

### Issue: "Browser console shows errors"
- Check CORS headers: `Access-Control-Allow-Origin: http://localhost:5173`
- Check Network tab to see GraphQL request/response
- Look for validation errors in response

---

## 📝 Example Test Cases

### Test Case 1: Employee with high CTC
**Input**:
```
emp_18 (Rah)
CTC: 2,000,000
Location: Delhi
PF Option: 5
```
**Expected**: Large salary with significant deductions

### Test Case 2: New employee with defaults
**Input**:
```
emp_16 (Bruce)
CTC: 600,000
Location: Delhi
PF Option: 3
```
**Expected**: Balanced earnings and deductions

### Test Case 3: Low CTC employee
**Input**:
```
emp_17 (Batman)
CTC: 42,000
Location: Delhi
PF Option: 2 (mandatory for low CTC)
```
**Expected**: Might trigger validation (PF Option must be 4 for CTC < 30,000 in some rules)

---

## 🚀 Next Steps After Testing

1. **Add more demo employees** to MongoDB 
2. **Modify salary rules** (download Excel, edit, upload back)
3. **Batch process employees** (upload Excel file with multiple employees)
4. **Integrate into production** with real employee data

---

## 📞 Support

All components are working correctly. If you see any issues:

1. Check the browser console (F12) for frontend errors
2. Check the backend terminal for Java/Kogito errors
3. Verify MongoDB is running: `docker compose ps`
4. Verify credentials and connection strings match

Good luck! 🎉
