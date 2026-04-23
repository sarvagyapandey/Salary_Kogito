# Salary Calculator - Frontend-Backend Integration Guide

## ✅ Current Setup Status

All components are now running and integrated:

### Running Services
- **MongoDB**: Running on `localhost:27018` (payrollDB database)
- **Backend (Quarkus/Kogito)**: Running on `http://localhost:8080`
  - GraphQL endpoint: `http://localhost:8080/graphql`
  - Status: ✅ Responding correctly, calculating salaries
  
- **Frontend (Vite React)**: Running on `http://localhost:5173`
  - Status: ✅ Connected and able to send requests

### Demo Employees Loaded in MongoDB
1. **emp_16** - Bruce (CTC: ₹600,000)
2. **emp_17** - Batman (CTC: ₹42,000)
3. **emp_18** - Rah (CTC: ₹2,000,000)

---

## 🎯 How to Calculate Single Employee Salary

### Via Frontend UI (Recommended)
1. Open `http://localhost:5173` in your browser
2. Scroll down to **"Employee Details"** section → **"Single Employee Calculation"**
3. Fill in the fields:
   - **Employee ID**: emp_16 (or any employee)
   - **Employee Name**: Bruce (optional, auto-fills from DB if ID exists)
   - **CTC**: 600000
   - **CCA**: 10000
   - **Location**: Delhi (optional)
   - **PF Option**: 3
   - **Professional Tax**: 200
   - **Employee PF Override**: 1800 (optional)

4. Click **"Calculate"** button
5. Results display below showing:
   - Basic Salary
   - HRA (House Rent Allowance)
   - Allowances
   - Deductions (PF, ESI, TDS, etc.)
   - Gross Payable
   - Take Home Salary

### Via GraphQL Query (Direct API Testing)

```bash
curl -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{
    "query": "query { calculateSalary(employeeId: \"emp_16\", name: \"Bruce\", ctc: 600000, cca: 10000, pfOption: 3) { employeeId name ctc cca basic hra specialAllowance grossPayable employeePF takeHomeSalary errors } }"
  }'
```

---

## 📊 What the Salary Calculation Includes

### Earnings Components
- **Basic**: Calculated based on CTC percentage rules
- **HRA** (House Rent Allowance): Based on location and rules
- **Special Allowance**: Based on CTC and category
- **Bonus**: As per rules (if applicable)
- **Gratuity**: If enabled

### Deductions
- **Employee PF** (Provident Fund): 12% or per pfOption
- **ESI** (Employee State Insurance): If eligible
- **Professional Tax**: As per state rules
- **TDS** (Tax Deducted at Source): Based on salary slab
- **Medical Insurance**: If applicable

### Final Values
- **Gross Payable**: Total earnings before deductions
- **Take Home Salary**: Final salary after all deductions
- **Employer Contributions**: PF and ESI contributions

---

## 🔄 Architecture Overview

### Data Flow
```
Frontend (Vite React)
        ↓
    API call with employee data
        ↓
Backend GraphQL Endpoint (Port 8080)
        ↓
  Quarkus/Kogito Rules Engine
        ↓
  Salary Calculation Rules (XLSX-based)
        ↓
  Lookup from MongoDB (if needed)
        ↓
Return calculated salary response
        ↓
Frontend displays results
```

---

## 🚀 Quick Commands Reference

### Start All Services
```bash
# Terminal 1: Start MongoDB
cd /home/user/Salary_full_stack
docker compose up -d

# Terminal 2: Start Backend
cd salary-kogito
./mvnw quarkus:dev

# Terminal 3: Start Frontend
cd ../frontend
npm run dev

# Terminal 4: Load demo data (one-time)
/home/user/Salary_full_stack/.venv/bin/python load_employees.py
```

### Stop All Services
```bash
# Stop backend: Press Ctrl+C in backend terminal
# Stop frontend: Press Ctrl+C in frontend terminal
# Stop MongoDB: docker compose down
```

---

## 🧪 Testing the Connection

### Test 1: Backend Health
```bash
curl http://localhost:8080/graphql -X OPTIONS -v
```
Should return CORS headers and 200 OK.

### Test 2: GraphQL Query
```bash
curl -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{"query": "query { calculateSalary(employeeId: \"emp_16\", ctc: 600000) { employeeId ctc basic takeHomeSalary } }"}'
```

### Test 3: Frontend Console
1. Open `http://localhost:5173`
2. Press F12 to open developer console
3. Fill in form and calculate
4. Check Console tab for GraphQL request/response logs

---

## 🛠️ Troubleshooting

### Issue: "Backend not responding"
- Check if Quarkus is running: `curl http://localhost:8080`
- Check logs in backend terminal for errors
- Ensure MongoDB is running: `docker compose ps`

### Issue: "Employee not found"
- Make sure demo data is loaded: `python3 load_employees.py`
- Check MongoDB: `docker exec payroll-mongo mongosh --port 27017 payrollDB --eval "db.employees.countDocuments()"`

### Issue: "CORS errors"
- Backend CORS is already configured for localhost:5173
- Clear browser cache and retry
- Check if frontend is on correct port (5173)

---

## 📝 Next Steps

1. **Test single employee calculation** - Use the UI or GraphQL endpoint above
2. **Modify salary rules** - Download rules, edit in Excel, upload back
3. **Batch processing** - Upload employee Excel sheet to process multiple employees
4. **Customize rules** - Edit salary calculation rules in the Quarkus backend

---

## 🔗 Key Files Changed/Created

- ✅ `load_employees.py` - Script to load demo employee data into MongoDB
- ✅ `frontend/src/api.js` - API client (already set up)
- ✅ `frontend/src/App.jsx` - Frontend with salary calculator UI
- ✅ `salary-kogito/src/main/java/org/acme/salary/SalaryResource.java` - Backend GraphQL endpoint

All integration points are working correctly! 🎉
