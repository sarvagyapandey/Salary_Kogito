const reset = false;        // ⚠️ set to false in non-destructive environments
const seedSample = true;   // insert demo data to verify validators

use("payrollDB");

// Helper: create or update collection with validator
function ensureCollection(name, options) {
  const exists = db.getCollectionInfos({ name }).length > 0;
  if (!exists) {
    db.createCollection(name, options);
  } else if (options && options.validator) {
    db.runCommand({
      collMod: name,
      validator: options.validator,
      validationLevel: options.validationLevel || "strict",
      validationAction: options.validationAction || "error",
    });
  }
}

// ======================================================
// Drop collections when reset is true (for safe rebuild)
// ======================================================
if (reset) {
  db.employeeMaster.drop();
  db.payroll.drop();
}

// ======================================================
// JSON Schemas
// ======================================================
const employeeMasterSchema = {
  bsonType: "object",
  required: [
    "basicDetails",
    "academic",
    "personal",
    "statutoryBank",
    "loanAdvance",
    "job",
    "attendance",
    "system",
  ],
  additionalProperties: false,
  properties: {
    _id: {},
    basicDetails: {
      bsonType: "object",
      additionalProperties: false,
      required: [
        "employeeId",
        "name",
        "dateOfJoining",
        "gender",
        "employmentType",
        "status",
        "payrollStatus",
      ],
      properties: {
        employeeId: { bsonType: "string" },
        name: { bsonType: "string" },
        dateOfJoining: { bsonType: "date" },
        dateOfResignation: { bsonType: ["date", "null"] },
        gender: { enum: ["Male", "Female", "Other"] },
        employmentType: { enum: ["FULL_TIME", "CONTRACT", "INTERN"] },
        status: { enum: ["ACTIVE", "INACTIVE"] },
        payrollStatus: { enum: ["ACTIVE", "HOLD", "EXITED"] },
      },
    },
    academic: {
      bsonType: "object",
      additionalProperties: false,
      required: [
        "highestQualification",
        "branch",
        "qualificationYear",
        "qualificationStatus",
        "professionalStartYear",
        "yearsOfExperience",
      ],
      properties: {
        highestQualification: { bsonType: "string" },
        branch: { bsonType: "string" },
        qualificationYear: { bsonType: "int" },
        qualificationStatus: { enum: ["COMPLETED", "PENDING"] },
        professionalStartYear: { bsonType: "int" },
        yearsOfExperience: { bsonType: "int" },
      },
    },
    certifications: {
      bsonType: "array",
      items: {
        bsonType: "object",
        additionalProperties: false,
        required: ["name", "date", "serialNo"],
        properties: {
          name: { bsonType: "string" },
          date: { bsonType: "date" },
          serialNo: { bsonType: "string" },
        },
      },
    },
    skills: {
      bsonType: "array",
      items: {
        bsonType: "object",
        additionalProperties: false,
        required: ["name", "proficiency", "years", "lastUsed", "primary"],
        properties: {
          name: { bsonType: "string" },
          proficiency: { enum: ["BEGINNER", "INTERMEDIATE", "ADVANCED"] },
          years: { bsonType: "int" },
          lastUsed: { bsonType: "date" },
          primary: { bsonType: "bool" },
          certificationRef: { bsonType: "string" },
        },
      },
    },
    personal: {
      bsonType: "object",
      additionalProperties: false,
      required: [
        "correspondenceAddress",
        "permanentAddress",
        "personalEmail",
        "officialEmail",
        "phone",
        "maritalStatus",
        "passportNo",
        "aadhaarNo",
        "panNo",
        "fatherName",
        "motherName",
      ],
      properties: {
        correspondenceAddress: { bsonType: "string" },
        permanentAddress: { bsonType: "string" },
        personalEmail: { bsonType: "string" },
        officialEmail: { bsonType: "string" },
        phone: { bsonType: "string" },
        maritalStatus: { enum: ["Single", "Married"] },
        passportNo: { bsonType: "string" },
        aadhaarNo: { bsonType: "string" },
        panNo: { bsonType: "string" },
        fatherName: { bsonType: "string" },
        motherName: { bsonType: "string" },
      },
    },
    statutoryBank: {
      bsonType: "object",
      additionalProperties: false,
      required: [
        "pfNo",
        "uanNo",
        "esicCardNo",
        "bankName",
        "accountNo",
        "ifscCode",
        "ctc",
        "pfOption",
        "professionalTax",
      ],
      properties: {
        pfNo: { bsonType: "string" },
        uanNo: { bsonType: "string" },
        esicCardNo: { bsonType: "string" },
        bankName: { bsonType: "string" },
        accountNo: { bsonType: "string" },
        ifscCode: { bsonType: "string" },
        ctc: { bsonType: "int" },
        pfOption: { bsonType: "int", minimum: 1, maximum: 5 },
        professionalTax: { bsonType: "int" },
      },
    },
    loanAdvance: {
      bsonType: "object",
      additionalProperties: false,
      required: ["loanName", "loanAmount", "numberOfEmi", "advanceSalary"],
      properties: {
        loanName: { bsonType: "string" },
        loanAmount: { bsonType: "int" },
        numberOfEmi: { bsonType: "int" },
        advanceSalary: { bsonType: "int" },
      },
    },
    job: {
      bsonType: "object",
      additionalProperties: false,
      required: [
        "teamName",
        "designation",
        "baseLocation",
        "department",
        "gradeLevel",
        "costCenter",
        "businessUnit",
      ],
      properties: {
        teamName: { bsonType: "string" },
        designation: { bsonType: "string" },
        baseLocation: { bsonType: "string" },
        department: { bsonType: "string" },
        gradeLevel: { enum: ["G1", "G2", "G3"] },
        costCenter: { bsonType: "string" },
        businessUnit: { bsonType: "string" },
      },
    },
    projects: {
      bsonType: "array",
      items: {
        bsonType: "object",
        additionalProperties: false,
        required: [
          "employeeId",
          "employeeName",
          "projectId",
          "projectName",
          "duration",
          "roleTitle",
          "allocationPct",
          "techDomain",
          "notes",
        ],
        properties: {
          employeeId: { bsonType: "string" },
          employeeName: { bsonType: "string" },
          projectId: { bsonType: "string" },
          projectName: { bsonType: "string" },
          duration: {
            bsonType: "object",
            additionalProperties: false,
            required: ["start", "end"],
            properties: {
              start: { bsonType: "date" },
              end: { bsonType: ["date", "null"] },
            },
          },
          roleTitle: { bsonType: "string" },
          allocationPct: { bsonType: "int", minimum: 0, maximum: 100 },
          techDomain: { bsonType: "string" },
          notes: { bsonType: "string" },
        },
      },
    },
    attendance: {
      bsonType: "object",
      additionalProperties: false,
      required: [
        "salaryToBeCalculated",
        "projectId",
        "officialDays",
        "workedDays",
        "presentDays",
        "lopDays",
      ],
      properties: {
        salaryToBeCalculated: { bsonType: "bool" },
        projectId: { bsonType: "string" },
        officialDays: { bsonType: "int" },
        workedDays: { bsonType: "int" },
        presentDays: { bsonType: "int" },
        lopDays: { bsonType: "int" },
      },
    },
    system: {
      bsonType: "object",
      additionalProperties: false,
      required: ["createdAt", "updatedAt"],
      properties: {
        createdAt: { bsonType: "date" },
        updatedAt: { bsonType: "date" },
      },
    },
  },
};

const payrollSchema = {
  bsonType: "object",
  required: [
    "employeeId",
    "payrollMonth",
    "runId",
    "inputSnapshot",
    "attendance",
    "salaryComponents",
    "employerContributions",
    "deductions",
    "taxDetails",
    "finalSalary",
    "system",
  ],
  additionalProperties: false,
  properties: {
    _id: {},
    employeeId: { bsonType: "string" },
    payrollMonth: { bsonType: "string" }, // format YYYY-MM
    runId: { bsonType: "string" },
    inputSnapshot: {
      bsonType: "object",
      additionalProperties: false,
      required: [
        "ctc",
        "cca",
        "category",
        "location",
        "pfOption",
        "professionalTax",
      ],
      properties: {
        ctc: { bsonType: "int" },
        cca: { bsonType: "int" },
        category: { bsonType: "string" },
        location: { bsonType: "string" },
        pfOption: { bsonType: "int", minimum: 1, maximum: 5 },
        professionalTax: { bsonType: "int" },
        employeePfOverride: { bsonType: ["int", "null"] },
      },
    },
    attendance: {
      bsonType: "object",
      additionalProperties: false,
      required: ["salaryToBeCalculated", "officialDays", "workedDays", "presentDays", "lopDays"],
      properties: {
        salaryToBeCalculated: { bsonType: "bool" },
        officialDays: { bsonType: "int" },
        workedDays: { bsonType: "int" },
        presentDays: { bsonType: "int" },
        lopDays: { bsonType: "int" },
      },
    },
    salaryComponents: {
      bsonType: "object",
      additionalProperties: false,
      required: ["basic", "basicStat", "hra", "bonus", "specialAllowance"],
      properties: {
        basic: { bsonType: "int" },
        basicStat: { bsonType: "int" },
        hra: { bsonType: "int" },
        bonus: { bsonType: "int" },
        specialAllowance: { bsonType: "int" },
      },
    },
    employerContributions: {
      bsonType: "object",
      additionalProperties: false,
      required: ["employerPf", "employerEsi", "gratuity"],
      properties: {
        employerPf: { bsonType: "int" },
        employerEsi: { bsonType: "int" },
        gratuity: { bsonType: "int" },
      },
    },
    deductions: {
      bsonType: "object",
      additionalProperties: false,
      required: ["employeePf", "employeeEsi", "medicalInsurance", "tds"],
      properties: {
        employeePf: { bsonType: "int" },
        employeeEsi: { bsonType: "int" },
        medicalInsurance: { bsonType: "int" },
        tds: { bsonType: "int" },
      },
    },
    components: {
      bsonType: "array",
      items: {
        bsonType: "object",
        additionalProperties: true, // allow metadata from Kogito
        required: ["code", "name", "category", "amount", "source"],
        properties: {
          code: { bsonType: "string" },           // unique component key from Kogito
          name: { bsonType: "string" },           // human readable label
          category: { enum: ["EARNING", "DEDUCTION", "EMPLOYER_COST"] },
          amount: { bsonType: ["int", "double"] },
          source: { enum: ["KOGITO", "USER", "OVERRIDE"] },
          computedAt: { bsonType: "date" },
          notes: { bsonType: "string" },
        },
      },
    },
    taxDetails: {
      bsonType: "object",
      additionalProperties: false,
      required: ["taxSlabBase", "taxMultiplier", "taxAfterRebate", "taxWithCess"],
      properties: {
        taxSlabBase: { bsonType: "int" },
        taxMultiplier: { bsonType: "double" },
        taxAfterRebate: { bsonType: "int" },
        taxWithCess: { bsonType: "int" },
      },
    },
    finalSalary: {
      bsonType: "object",
      additionalProperties: false,
      required: ["grossPayableWithoutCca", "annualGross", "takeHomeWithoutCca"],
      properties: {
        grossPayableWithoutCca: { bsonType: "int" },
        annualGross: { bsonType: "int" },
        takeHomeWithoutCca: { bsonType: "int" },
      },
    },
    system: {
      bsonType: "object",
      additionalProperties: false,
      required: ["payrollStatus", "createdAt", "updatedAt"],
      properties: {
        payrollStatus: { enum: ["PROCESSED", "PENDING", "FAILED"] },
        createdAt: { bsonType: "date" },
        updatedAt: { bsonType: "date" },
      },
    },
  },
};

// ======================================================
// Create collections with validators
// ======================================================
ensureCollection("employeeMaster", {
  validator: { $jsonSchema: employeeMasterSchema },
  validationLevel: "strict",
  validationAction: "error",
});

ensureCollection("payroll", {
  validator: { $jsonSchema: payrollSchema },
  validationLevel: "strict",
  validationAction: "error",
});

// ======================================================
// Indexes
// ======================================================
db.employeeMaster.createIndex({ "basicDetails.employeeId": 1 }, { unique: true });
db.employeeMaster.createIndex({ "personal.officialEmail": 1 });
db.employeeMaster.createIndex({ "job.department": 1 });
db.employeeMaster.createIndex({ "skills.name": 1 });

db.payroll.createIndex({ employeeId: 1, payrollMonth: 1 }, { unique: true });
db.payroll.createIndex({ runId: 1 });

// ======================================================
// Seed sample data (optional)
// ======================================================
if (seedSample) {
  const now = new Date();

  const sampleEmployee = {
    basicDetails: {
      employeeId: "EMP001",
      name: "Jane Doe",
      dateOfJoining: new Date("2021-04-12"),
      dateOfResignation: null,
      gender: "Female",
      employmentType: "FULL_TIME",
      status: "ACTIVE",
      payrollStatus: "ACTIVE",
    },
    academic: {
      highestQualification: "B.Tech",
      branch: "Computer Science",
      qualificationYear: 2019,
      qualificationStatus: "COMPLETED",
      professionalStartYear: 2019,
      yearsOfExperience: 5,
    },
    certifications: [
      { name: "AWS SAA", date: new Date("2023-02-01"), serialNo: "AWS-12345" },
    ],
    skills: [
      {
        name: "Node.js",
        proficiency: "ADVANCED",
        years: 4,
        lastUsed: now,
        primary: true,
        certificationRef: "https://example.com/cert/aws-12345",
      },
      {
        name: "MongoDB",
        proficiency: "INTERMEDIATE",
        years: 3,
        lastUsed: now,
        primary: true,
      },
    ],
    personal: {
      correspondenceAddress: "123 Main Street, City",
      permanentAddress: "123 Main Street, City",
      personalEmail: "jane.personal@example.com",
      officialEmail: "jane.doe@company.com",
      phone: "+1-555-0100",
      maritalStatus: "Single",
      passportNo: "P1234567",
      aadhaarNo: "1234-5678-9012",
      panNo: "ABCDE1234F",
      fatherName: "John Doe",
      motherName: "Mary Doe",
    },
    statutoryBank: {
      pfNo: "PF123456",
      uanNo: "UAN123456",
      esicCardNo: "ESIC123456",
      bankName: "ABC Bank",
      accountNo: "1234567890",
      ifscCode: "ABC0001234",
      ctc: 1200000,
      pfOption: 3,
      professionalTax: 200,
    },
    loanAdvance: {
      loanName: "Personal Loan",
      loanAmount: 100000,
      numberOfEmi: 12,
      advanceSalary: 0,
    },
    job: {
      teamName: "Platform",
      designation: "Senior Engineer",
      baseLocation: "Bangalore",
      department: "Engineering",
      gradeLevel: "G2",
      costCenter: "ENG-01",
      businessUnit: "Product",
    },
    projects: [
      {
        employeeId: "EMP001",
        employeeName: "Jane Doe",
        projectId: "PRJ001",
        projectName: "Client Alpha",
        duration: { start: new Date("2023-01-01"), end: null },
        roleTitle: "Backend Lead",
        allocationPct: 80,
        techDomain: "Fintech",
        notes: "Owns API layer",
      },
    ],
    attendance: {
      salaryToBeCalculated: true,
      projectId: "PRJ001",
      officialDays: 30,
      workedDays: 28,
      presentDays: 28,
      lopDays: 2,
    },
    system: {
      createdAt: now,
      updatedAt: now,
    },
  };

  const samplePayroll = {
    employeeId: "EMP001",
    payrollMonth: "2024-12",
    runId: "RUN-2024-12-01",
    inputSnapshot: {
      ctc: 1200000,
      cca: 3000,
      category: "A",
      location: "Bangalore",
      pfOption: 3,
      professionalTax: 200,
      employeePfOverride: null,
    },
    attendance: {
      salaryToBeCalculated: true,
      officialDays: 30,
      workedDays: 28,
      presentDays: 28,
      lopDays: 2,
    },
    salaryComponents: {
      basic: 50000,
      basicStat: 50000,
      hra: 25000,
      bonus: 10000,
      specialAllowance: 15000,
    },
    employerContributions: {
      employerPf: 6000,
      employerEsi: 0,
      gratuity: 2000,
    },
    deductions: {
      employeePf: 6000,
      employeeEsi: 0,
      medicalInsurance: 1500,
      tds: 8000,
    },
    taxDetails: {
      taxSlabBase: 70000,
      taxMultiplier: 0.05,
      taxAfterRebate: 65000,
      taxWithCess: 68000,
    },
    finalSalary: {
      grossPayableWithoutCca: 100000,
      annualGross: 1200000,
      takeHomeWithoutCca: 85000,
    },
    system: {
      payrollStatus: "PROCESSED",
      createdAt: now,
      updatedAt: now,
    },
  };

  db.employeeMaster.insertOne(sampleEmployee);
  db.payroll.insertOne(samplePayroll);
}
