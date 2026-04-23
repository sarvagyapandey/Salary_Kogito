# Salary Full Stack Application

A complete full-stack salary calculation system using Kogito rules engine (Quarkus backend) and React frontend. Features dynamic salary computation based on CTC bands, tax calculations, and Excel-based decision rules.

## 🏗️ Architecture

```
Salary_full_stack/
├── frontend/              # React + Vite (Port 5173)
│   ├── src/
│   ├── package.json
│   └── vite.config.js
│
└── salary-kogito/         # Quarkus + Kogito backend (Port 8080)
    ├── src/main/java/     # Java source code
    ├── src/main/resources/
    │   ├── salary-rules.xlsx      # Decision tables (Excel-based)
    │   └── employees.json         # Sample employee data
    └── pom.xml            # Maven configuration
```

## 🚀 Quick Start

### Prerequisites
- **Java 17+** with Maven 3.8+
- **Node.js 16+** with npm

### Backend (Quarkus + Kogito)

```bash
cd salary-kogito
./mvnw compile quarkus:dev
```

- Runs on `http://localhost:8080`
- GraphQL API: `http://localhost:8080/graphql`
- Dev UI: `http://localhost:8080/q/dev/`

The backend now reads employee input data from MongoDB instead of `employees.json`.
By default it connects to:

```bash
mongodb://localhost:27018/payrollDB
```

You can override that with:

```bash
export MONGODB_URI=mongodb://localhost:27018/payrollDB
export MONGODB_DATABASE=payrollDB
```

For salary calculation lookups, keep the same `employeeId` in both:
- `employeeMaster.basicDetails.employeeId`
- `payroll.employeeId`

The backend merges these fields for calculation input:
- `name` from `employeeMaster.basicDetails.name`
- `ctc` from `payroll.inputSnapshot.ctc` or fallback `employeeMaster.statutoryBank.ctc`
- `cca`, `category`, `location`, `pfOption`, `professionalTax`, `employeePFOverride` from `payroll.inputSnapshot`

### Frontend (React + Vite)

```bash
cd frontend
npm install
npm run dev
```

- Runs on `http://localhost:5173`

### Both Together

Open two terminals and run both commands above. Then visit `http://localhost:5173`

### Optional MongoDB For `setup.mongo.js`

If you want to run the root-level `setup.mongo.js` script, start MongoDB first:

```bash
docker compose up -d
```

Then execute the setup script with `mongosh`:

```bash
mongosh "mongodb://localhost:27018/payrollDB" setup.mongo.js
```

Useful commands:

```bash
docker compose ps
docker compose down
```

This MongoDB container is only needed for the standalone schema/seed script. It is not required by the frontend or Quarkus quick start documented above.

---

## 📊 Features

### Salary Components Calculated
- **Basic Salary** - Based on CTC bands (15K-25K, 25K-35K, etc.)
- **HRA** - House Rent Allowance (depends on CTC range)
- **Bonus** - 8.33% of basicStat (if basicStat ≤ 21,000)
- **PF/ESI** - Five options (P1-P5) based on CTC and pfOption
- **Medical Insurance** - 250 if basicStat ≥ 21,001
- **Gratuity** - 5% of basic salary
- **Tax Calculation** - Slab-based with rebates and cess

### Rules Engine
- **13 RuleTables** in Excel-based decision tables
- **Dynamic salary calculation** with two-pass rule firing
- **Tax computation** with annual gross bands and multipliers
- **Component tracking** of earning, deductions, and employer costs

---

## 🔧 Technology Stack

### Backend
- **Framework**: Quarkus (Java)
- **Rules Engine**: Kogito + Drools Decision Tables
- **API**: GraphQL
- **Build**: Maven
- **Config**: application.properties

### Frontend
- **Framework**: React 19.2.4
- **Build Tool**: Vite 8
- **Styling**: CSS

---

## 📚 API Documentation

### GraphQL Endpoint
`http://localhost:8080/graphql`

### Example Queries

**Calculate Salary for Employee**
```graphql
query {
  calculateSalary(employeeId: "emp_16") {
    employeeId
    name
    ctc
    basic
    basicStat
    hra
    bonus
    employeePF
    employerPF
    employeeESI
    employerESI
    medicalInsurance
    gratuity
    grossPayable
    specialAllowance
    annualGross
    taxSlabBase
    taxAfterRebate
    taxMultiplier
    taxWithCess
    tds
    takeHomeSalary
    components {
      name
      amount
      type
    }
  }
}
```

**Get Current Rules Workbook**
```graphql
query {
  rulesWorkbook
}
```

**Upload New Rules**
```graphql
mutation {
  uploadRulesWorkbook(workbookBase64: "base64EncodedExcelFile")
}
```

---

## 📋 Salary Rules (Excel Format)

The salary calculation is completely driven by Excel decision tables. Each component has its own RuleTable:

| # | Table | Purpose |
|---|-------|---------|
| 1 | Basic | Salary by CTC band |
| 2 | BasicStat | max(basic, 50% of CTC+CCA) |
| 3 | HRA | HRA calculation by CTC range |
| 4 | Bonus | 8.33% bonus threshold |
| 5 | EmployeePF | Employee PF by pfOption |
| 6 | EmployerPF | Employer PF by pfOption |
| 7 | ESI | ESI based on basicStat |
| 8 | Medical | Medical insurance threshold |
| 9 | Gratuity | Gratuity 5% of basic |
| 10 | TaxSlab | Tax slab percentage |
| 11 | TaxAfterRebate | Tax rebate logic |
| 12 | TaxWithCess | Tax multiplier |
| 13 | FinalCalc | Final calculations |

---

## 🛠️ Build & Deployment

### Production Build (Backend)
```bash
cd salary-kogito
./mvnw clean package
java -jar target/quarkus-app/quarkus-run.jar
```

### Production Build (Frontend)
```bash
cd frontend
npm run build
npm run preview
```

---

## 📖 Documentation

- **FORMAT_ANALYSIS.md** - Spreadsheet validation
- **KOGITO_VALIDATION_REPORT.md** - Complete validation report
- **KOGITO_FORMAT_REFERENCE.md** - Kogito format specification
- **QUICK_REFERENCE.md** - Quick comparison guide
- **IMPLEMENTATION_SUMMARY.md** - Implementation details
- **CHANGES_CHECKLIST.md** - Complete changes list

---

## 🧪 Testing

### Unit Testing
- Backend tests in `salary-kogito/src/test/`

### Manual Testing via GraphQL
1. Start backend: `./mvnw compile quarkus:dev`
2. Open `http://localhost:8080/graphql`
3. Run sample queries (see API Documentation)

### Frontend Testing
1. Start frontend: `npm run dev`
2. Navigate to `http://localhost:5173`
3. Test salary calculations through UI

---

## 🔐 CORS Configuration

Frontend and backend communicate via GraphQL. CORS is configured for:
- `http://localhost:5173` (Vite frontend)
- `http://localhost:3000` (alternative)

See `salary-kogito/src/main/resources/application.properties` for CORS settings.

---

## 📝 Employee Data

Sample employees in `salary-kogito/src/main/resources/employees.json`:
- emp_16, emp_17, emp_18, etc.
- Used for testing salary calculations

---

## 🤝 Contributing

1. Create a feature branch
2. Make your changes
3. Test locally
4. Push and create a pull request

---

## 📄 License

[Add your license here]

---

## 📞 Support

For issues or questions about:
- **Backend/Rules**: Check KOGITO_VALIDATION_REPORT.md
- **Frontend**: See frontend/README.md
- **Build Issues**: Ensure Java 17+ and Node 16+ are installed

---

## 🎯 Key Highlights

✅ **100% Spreadsheet-Driven** - All salary rules in Excel  
✅ **HR-Friendly** - Modify rules by editing Excel  
✅ **Two-Pass Rule Engine** - Base components then tax calculations  
✅ **GraphQL API** - Modern API for salary calculations  
✅ **Hot-Reload Rules** - Update rules without restarting  
✅ **Full-Stack** - Complete UI and backend included  
