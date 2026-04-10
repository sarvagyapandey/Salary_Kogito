import { useState } from 'react';
import { gql, downloadBase64DataUri } from './api';
import * as XLSX from 'xlsx';
import './App.css';

const statusLabels = {
  healthy: "System ready",
  loading: "Checking system",
  error: "Needs attention",
};

export default function App() {
  const [employeeId, setEmployeeId] = useState('emp_16');
  const [employeeName, setEmployeeName] = useState('');
  const [ctc, setCtc] = useState('600000');
  const [cca, setCca] = useState('10000');
  const [pfOption, setPfOption] = useState('');
  const [professionalTax, setProfessionalTax] = useState('200');
  const [employeePFOverride, setEmployeePFOverride] = useState('');
  const [calcResult, setCalcResult] = useState(null);
  const [status, setStatus] = useState("loading");
  const [message, setMessage] = useState("Checking the current setup...");
  const [busy, setBusy] = useState({ rules: false, employees: false });

  const handleCalc = async () => {
    setStatus('loading');
    setMessage('Calculating...');
    try {
      const data = await gql(`
        query(
          $id:String!
          $name:String
          $ctc:Float!
          $cca:Float
          $category:String
          $location:String
          $pfOption:Float
          $professionalTax:Float
          $employeePFOverride:Float
        ){
          calculateSalary(
            employeeId:$id
            name:$name
            ctc:$ctc
            cca:$cca
            category:$category
            location:$location
            pfOption:$pfOption
            professionalTax:$professionalTax
            employeePFOverride:$employeePFOverride
          ){
            employeeId name ctc cca location category professionalTax pfOption basic hra specialAllowance bonus grossPayable
            employeePF employerPF employeeESI employerESI
            gratuity medicalInsurance tds takeHomeSalary
            errors
          }
        }`, { 
          id: employeeId,
          name: employeeName || null,
          ctc: parseFloat(ctc) || 0,
          cca: parseFloat(cca) || 0,
          category: null,
          location: null,
          pfOption: pfOption === '' ? null : parseFloat(pfOption),
          professionalTax: professionalTax === '' ? 0 : parseFloat(professionalTax),
          employeePFOverride: employeePFOverride === '' ? null : parseFloat(employeePFOverride)
        });
      
      // Calculate both versions of takeHomeSalary and grossPayable
      const result = data.calculateSalary;
      const takeHomeSalaryWithCCA = (result.takeHomeSalary || 0) + (result.cca || 0);
      const takeHomeSalaryWithoutCCA = result.takeHomeSalary;
      const grossPayableWithCCA = (result.grossPayable || 0) + (result.cca || 0);
      const grossPayableWithoutCCA = result.grossPayable;

      setCalcResult({
        ...result,
        takeHomeSalaryWithCCA,
        takeHomeSalaryWithoutCCA,
        grossPayableWithCCA,
        grossPayableWithoutCCA
      });
      if (result.errors && result.errors.length) {
        setStatus('error');
        setMessage(result.errors.join('; '));
      } else {
        setStatus('healthy');
        setMessage('Calculation completed successfully');
      }
    } catch (e) { 
      setStatus('error');
      setMessage(e.message); 
    }
  };

  const handleDownloadRules = async () => {
    setMessage('Downloading rules...');
    try {
      const { rulesWorkbook } = await gql(`query{ rulesWorkbook }`);
      downloadBase64DataUri(rulesWorkbook, 'salary-rules.xlsx');
      setStatus('healthy');
      setMessage('Rules downloaded successfully');
    } catch (e) { 
      setStatus('error');
      setMessage(e.message); 
    }
  };

  const handleDownloadTemplate = async () => {
    setMessage('Downloading template...');
    try {
      const { employeeTemplate } = await gql(`query{ employeeTemplate }`);
      downloadBase64DataUri(employeeTemplate, 'employee-template.xlsx');
      setStatus('healthy');
      setMessage('Template downloaded successfully');
    } catch (e) { 
      setStatus('error');
      setMessage(e.message); 
    }
  };

  const uploadFile = async (file, mutationName) => {
    const b64 = await fileToBase64(file);
    return gql(
      `mutation($data:String!){ ${mutationName}(workbookBase64:$data) }`,
      { data: b64.replace(/^data:.*;base64,/, '') }
    );
  };

  const handleUploadRules = async (e) => {
    const file = e.target.files?.[0];
    if (!file) {
      setStatus('error');
      setMessage('Choose the updated salary rules workbook first.');
      return;
    }
    setBusy((current) => ({ ...current, rules: true }));
    setMessage('Uploading rules...');
    try {
      await uploadFile(file, 'uploadRulesWorkbook');
      setStatus('healthy');
      setMessage('Updated salary rules uploaded successfully.');
    } catch (e) { 
      setStatus('error');
      setMessage(e.message); 
    } finally {
      setBusy((current) => ({ ...current, rules: false }));
      e.target.value = '';
    }
  };

  const handleProcessEmployees = async (e) => {
    const file = e.target.files?.[0];
    if (!file) {
      setStatus('error');
      setMessage('Choose the employee spreadsheet first.');
      return;
    }
    setBusy((current) => ({ ...current, employees: true }));
    setMessage('Processing employees...');
    try {
      // Convert file to base64
      const b64 = await fileToBase64(file);
      const workbookBase64 = b64.replace(/^data:.*;base64,/, '');
      
      // Call GraphQL mutation with proper variable handling
      const resultBase64 = await gql(
        `mutation($workbookBase64:String!){ processEmployeesWorkbook(workbookBase64:$workbookBase64) }`,
        { workbookBase64 }
      );
      
      if (!resultBase64.processEmployeesWorkbook) {
        throw new Error('No result from server');
      }
      
      // Decode base64 result
      const binaryResult = atob(resultBase64.processEmployeesWorkbook);
      const bytes = new Uint8Array(binaryResult.length);
      for (let i = 0; i < binaryResult.length; i++) {
        bytes[i] = binaryResult.charCodeAt(i);
      }
      
      // Parse Excel and add calculated columns
      const workbook = XLSX.read(bytes, { type: 'array' });
      
      if (!workbook.SheetNames || workbook.SheetNames.length === 0) {
        throw new Error('Excel file has no sheets');
      }
      
      const sheetName = workbook.SheetNames[0];
      const worksheet = workbook.Sheets[sheetName];
      const data = XLSX.utils.sheet_to_json(worksheet);
      
      if (!data || data.length === 0) {
        throw new Error('Excel sheet has no data rows');
      }
      
      // Add calculated takeHomeSalary and grossPayable columns for each row
      // Reorganize columns for better readability: Earnings → Deductions → Take Home
      const enhancedData = data.map(row => {
        const newRow = {};
        const orderMap = {};
        let order = 0;
        
        // 1. Employee Info
        const infoKeys = ['employeeId', 'name', 'ctc', 'cca', 'category', 'location', 'pfOption', 'professionalTax', 'employeePFOverride'];
        for (const key of infoKeys) {
          if (key in row) {
            orderMap[key] = order++;
            newRow[key] = row[key];
          }
        }
        
        // 2. Earnings
        const earningKeys = ['basic', 'hra', 'specialAllowance', 'bonus', 'gratuity'];
        for (const key of earningKeys) {
          if (key in row) {
            orderMap[key] = order++;
            newRow[key] = row[key];
          }
        }
        
        // 3. Gross Payable (with variants - without and with CCA)
        orderMap['grossPayableWithoutCCA'] = order++;
        newRow['grossPayableWithoutCCA'] = row.grossPayable;
        orderMap['grossPayableWithCCA'] = order++;
        newRow['grossPayableWithCCA'] = row.grossPayable + (row.cca || 0);
        
        // 4. Deductions
        const deductionKeys = ['employeePF', 'employeeESI', 'professionalTax', 'tds', 'medicalInsurance'];
        for (const key of deductionKeys) {
          if (key in row) {
            orderMap[key] = order++;
            newRow[key] = row[key];
          }
        }
        
        // 5. Employer Contributions
        const contributionKeys = ['employerPF', 'employerESI'];
        for (const key of contributionKeys) {
          if (key in row) {
            orderMap[key] = order++;
            newRow[key] = row[key];
          }
        }
        
        // 6. Taxes
        const taxKeys = ['taxSlabBase', 'taxMultiplier', 'taxAfterRebate', 'taxWithCess'];
        for (const key of taxKeys) {
          if (key in row) {
            orderMap[key] = order++;
            newRow[key] = row[key];
          }
        }
        
        // 7. Take Home Salary (Final values)
        orderMap['takeHomeSalaryWithoutCCA'] = order++;
        newRow['takeHomeSalaryWithoutCCA'] = row.takeHomeSalary;
        orderMap['takeHomeSalaryWithCCA'] = order++;
        newRow['takeHomeSalaryWithCCA'] = row.takeHomeSalary + (row.cca || 0);
        
        // Add any remaining columns (but exclude the plain grossPayable since we have variants)
        const excludeColumns = ['grossPayable'];
        for (const key of Object.keys(row)) {
          if (!(key in newRow) && !excludeColumns.includes(key)) {
            newRow[key] = row[key];
          }
        }
        
        return newRow;
      });
      
      // Create new workbook with enhanced data
      const newWorksheet = XLSX.utils.json_to_sheet(enhancedData);
      const newWorkbook = XLSX.utils.book_new();
      XLSX.utils.book_append_sheet(newWorkbook, newWorksheet, sheetName);
      
      // Download the enhanced Excel file
      XLSX.writeFile(newWorkbook, 'salary_results.xlsx');
      
      setStatus('healthy');
      setMessage(`✓ Processed ${data.length} employees with calculated salary columns`);
    } catch (err) { 
      console.error('Batch processing error:', err);
      setStatus('error');
      setMessage(`Error: ${err.message}`);
    } finally {
      setBusy((current) => ({ ...current, employees: false }));
      e.target.value = '';
    }
  };

  return (
    <div className="shell">
      <div className="backdrop backdrop-a" />
      <div className="backdrop backdrop-b" />
      <main className="page">
        <section className="hero card">
          <div>
            <p className="eyebrow">Salary Management</p>
            <h1>Manage salary sheets without touching code.</h1>
            <p className="hero-copy">
              Download the current salary rules, update them in Excel, upload employee data, and get the finished payroll sheet back.
            </p>
          </div>
          <div className={`status-panel status-${status}`}>
            <span className="status-dot" />
            <div>
              <p className="status-label">{statusLabels[status]}</p>
              <p className="status-message">{message}</p>
            </div>
          </div>
        </section>

        <section className="grid">
          <article className="card action-card">
            <h2>1. Prepare your files</h2>
            <p>Start with the latest templates so the columns stay in the correct format.</p>
            <div className="button-row">
              <button className="primary-button" onClick={handleDownloadRules}>Download salary rules</button>
              <button className="secondary-button" onClick={handleDownloadTemplate}>Download employee template</button>
            </div>
            <div className="mini-note">
              <strong>Current rules file:</strong>
              <span>Salary rules workbook available</span>
            </div>
          </article>

          <article className="card action-card">
            <h2>2. Update salary rules</h2>
            <p>Upload the edited salary rules workbook after changing percentages, thresholds, caps, or formulas.</p>
            <form onSubmit={(e) => { e.preventDefault(); }} className="stack">
              <input name="rules" type="file" accept=".xlsx" onChange={handleUploadRules} />
              <button className="primary-button" type="button" disabled={busy.rules}>
                {busy.rules ? "Uploading..." : "Upload updated rules"}
              </button>
            </form>
          </article>

          <article className="card action-card">
            <h2>3. Process employee sheet</h2>
            <p>Upload the employee workbook and the finished salary sheet will download automatically.</p>
            <form onSubmit={(e) => { e.preventDefault(); }} className="stack">
              <input name="employees" type="file" accept=".xlsx" onChange={handleProcessEmployees} />
              <button className="primary-button warm" type="button" disabled={busy.employees}>
                {busy.employees ? "Processing..." : "Process employee workbook"}
              </button>
            </form>
          </article>
        </section>

        <section className="card summary-card">
          <div className="summary-head">
            <div>
              <p className="eyebrow">Employee Details</p>
              <h2>Single Employee Calculation</h2>
            </div>
          </div>
          
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr 1fr', gap: '12px', marginTop: '18px', alignItems: 'flex-end' }}>
            <div>
              <label style={{ color: 'var(--muted)', fontSize: '0.85rem', display: 'block', marginBottom: '6px' }}>Employee ID</label>
              <input 
                type="text" 
                placeholder="emp_16"
                value={employeeId}
                onChange={(e) => setEmployeeId(e.target.value)}
                style={{ width: '100%', padding: '12px', borderRadius: '12px', border: '1px dashed rgba(38, 70, 83, 0.22)', background: 'var(--paper-strong)', fontSize: 'inherit' }}
              />
            </div>
            <div>
              <label style={{ color: 'var(--muted)', fontSize: '0.85rem', display: 'block', marginBottom: '6px' }}>Employee Name</label>
              <input 
                type="text" 
                placeholder="e.g., John Doe"
                value={employeeName}
                onChange={(e) => setEmployeeName(e.target.value)}
                style={{ width: '100%', padding: '12px', borderRadius: '12px', border: '1px dashed rgba(38, 70, 83, 0.22)', background: 'var(--paper-strong)', fontSize: 'inherit' }}
              />
            </div>
            <div>
              <label style={{ color: 'var(--muted)', fontSize: '0.85rem', display: 'block', marginBottom: '6px' }}>CTC</label>
              <input 
                type="number" 
                placeholder="600000"
                value={ctc}
                onChange={(e) => setCtc(e.target.value)}
                style={{ width: '100%', padding: '12px', borderRadius: '12px', border: '1px dashed rgba(38, 70, 83, 0.22)', background: 'var(--paper-strong)', fontSize: 'inherit' }}
              />
            </div>
            <div>
              <label style={{ color: 'var(--muted)', fontSize: '0.85rem', display: 'block', marginBottom: '6px' }}>CCA</label>
              <input 
                type="number" 
                placeholder="10000"
                value={cca}
                onChange={(e) => setCca(e.target.value)}
                style={{ width: '100%', padding: '12px', borderRadius: '12px', border: '1px dashed rgba(38, 70, 83, 0.22)', background: 'var(--paper-strong)', fontSize: 'inherit' }}
              />
            </div>
            <div>
              <label style={{ color: 'var(--muted)', fontSize: '0.85rem', display: 'block', marginBottom: '6px' }}>PF Option (1-5)</label>
              <input 
                type="number" 
                placeholder="e.g., 4"
                value={pfOption}
                onChange={(e) => setPfOption(e.target.value)}
                style={{ width: '100%', padding: '12px', borderRadius: '12px', border: '1px dashed rgba(38, 70, 83, 0.22)', background: 'var(--paper-strong)', fontSize: 'inherit' }}
              />
            </div>
            <div>
              <label style={{ color: 'var(--muted)', fontSize: '0.85rem', display: 'block', marginBottom: '6px' }}>Professional Tax</label>
              <input 
                type="number" 
                placeholder="e.g., 200"
                value={professionalTax}
                onChange={(e) => setProfessionalTax(e.target.value)}
                style={{ width: '100%', padding: '12px', borderRadius: '12px', border: '1px dashed rgba(38, 70, 83, 0.22)', background: 'var(--paper-strong)', fontSize: 'inherit' }}
              />
            </div>
            <div>
              <label style={{ color: 'var(--muted)', fontSize: '0.85rem', display: 'block', marginBottom: '6px' }}>Employee PF Override</label>
              <input 
                type="number" 
                placeholder="optional(for Pf Option 5)"
                value={employeePFOverride}
                onChange={(e) => setEmployeePFOverride(e.target.value)}
                style={{ width: '100%', padding: '12px', borderRadius: '12px', border: '1px dashed rgba(38, 70, 83, 0.22)', background: 'var(--paper-strong)', fontSize: 'inherit' }}
              />
            </div>
          </div>

          <div style={{ display: 'flex', gap: '12px', marginTop: '12px' }}>
            <button className="primary-button" onClick={handleCalc} style={{ minWidth: '140px' }}>
              Calculate
            </button>
          </div>

          {calcResult && (
            <div style={{ marginTop: '18px', padding: '18px', borderRadius: '18px', background: '#fff', border: '1px solid rgba(38, 70, 83, 0.08)' }}>
              {calcResult.errors && calcResult.errors.length > 0 && (
                <div style={{ marginBottom: '12px', padding: '12px', borderRadius: '12px', background: '#fff4f0', border: '1px solid #f5c2a7', color: '#b54708' }}>
                  <strong>Validation issues:</strong>
                  <ul style={{ margin: '8px 0 0 18px', padding: 0 }}>
                    {calcResult.errors.map((err, idx) => <li key={idx}>{err}</li>)}
                  </ul>
                </div>
              )}
              <h3 style={{ marginTop: 0, marginBottom: '16px', color: 'var(--ink)', fontSize: '1rem', fontWeight: 600 }}>Salary Breakdown</h3>
              {/* Key Summary Cards */}
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px', marginBottom: '20px' }}>
                <div style={{ padding: '16px', borderRadius: '14px', background: 'linear-gradient(135deg, #2a9d8f 0%, #1f7a70 100%)', border: 'none', boxShadow: '0 4px 12px rgba(42, 157, 143, 0.2)' }}>
                  <div style={{ color: 'rgba(255,255,255, 0.85)', fontSize: '0.8rem', marginBottom: '6px', fontWeight: 500, textTransform: 'uppercase', letterSpacing: '0.5px' }}>Take Home (With CCA)</div>
                  <div style={{ fontSize: '1.6rem', fontWeight: '800', color: '#fff', fontFamily: 'Georgia, serif' }}>
                    ₹ {calcResult.takeHomeSalaryWithCCA?.toLocaleString('en-IN', { maximumFractionDigits: 0 })}
                  </div>
                </div>
                <div style={{ padding: '16px', borderRadius: '14px', background: 'linear-gradient(135deg, #e76f51 0%, #d45a3a 100%)', border: 'none', boxShadow: '0 4px 12px rgba(231, 111, 81, 0.2)' }}>
                  <div style={{ color: 'rgba(255,255,255, 0.85)', fontSize: '0.8rem', marginBottom: '6px', fontWeight: 500, textTransform: 'uppercase', letterSpacing: '0.5px' }}>Take Home (Without CCA)</div>
                  <div style={{ fontSize: '1.6rem', fontWeight: '800', color: '#fff', fontFamily: 'Georgia, serif' }}>
                    ₹ {calcResult.takeHomeSalaryWithoutCCA?.toLocaleString('en-IN', { maximumFractionDigits: 0 })}
                  </div>
                </div>
              </div>

              {/* Detailed Breakdown */}
              <div style={{ background: '#f9f7f4', borderRadius: '14px', padding: '16px', marginBottom: '16px' }}>
                <h4 style={{ margin: '0 0 14px 0', color: 'var(--ink)', fontSize: '0.95rem', fontWeight: 600 }}>Salary Components</h4>
                
                {/* Earnings Section */}
                <div style={{ marginBottom: '16px' }}>
                  <div style={{ fontSize: '0.85rem', fontWeight: 600, color: '#2a9d8f', marginBottom: '10px', textTransform: 'uppercase', letterSpacing: '0.5px' }}>Earnings</div>
                  <div style={{ display: 'grid', gap: '8px' }}>
                    {[
                      { label: 'Basic', key: 'basic' },
                      { label: 'HRA', key: 'hra' },
                      { label: 'Special Allowance', key: 'specialAllowance' },
                      { label: 'Bonus', key: 'bonus' }
                    ].map(item => (
                      <div key={item.key} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', paddingBottom: '8px', borderBottom: '1px solid rgba(38, 70, 83, 0.08)' }}>
                        <span style={{ color: 'var(--muted)', fontSize: '0.9rem' }}>{item.label}</span>
                        <span style={{ fontWeight: 600, color: 'var(--ink)', fontSize: '0.95rem' }}>₹ {calcResult[item.key]?.toLocaleString('en-IN', { maximumFractionDigits: 0 }) || '0'}</span>
                      </div>
                    ))}
                  </div>
                </div>

                {/* Gross Payable Section */}
                <div style={{ marginBottom: '16px' }}>
                  <div style={{ fontSize: '0.85rem', fontWeight: 600, color: '#264653', marginBottom: '10px', textTransform: 'uppercase', letterSpacing: '0.5px' }}>Gross Payable</div>
                  <div style={{ display: 'grid', gap: '8px' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '10px 0', background: '#e9c46a', borderRadius: '8px', paddingLeft: '10px', paddingRight: '10px' }}>
                      <span style={{ fontWeight: 600, color: '#264653' }}>Without CCA</span>
                      <span style={{ fontWeight: '800', color: '#264653', fontSize: '1.05rem' }}>₹ {calcResult.grossPayableWithoutCCA?.toLocaleString('en-IN', { maximumFractionDigits: 0 }) || '0'}</span>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '10px', background: '#2a9d8f', borderRadius: '8px', paddingLeft: '10px', paddingRight: '10px' }}>
                      <span style={{ fontWeight: 600, color: '#fff' }}>With CCA</span>
                      <span style={{ fontWeight: '800', color: '#fff', fontSize: '1.05rem' }}>₹ {calcResult.grossPayableWithCCA?.toLocaleString('en-IN', { maximumFractionDigits: 0 }) || '0'}</span>
                    </div>
                  </div>
                </div>

                {/* Deductions Section */}
                <div style={{ marginBottom: '16px' }}>
                  <div style={{ fontSize: '0.85rem', fontWeight: 600, color: '#e76f51', marginBottom: '10px', textTransform: 'uppercase', letterSpacing: '0.5px' }}>Deductions</div>
                  <div style={{ display: 'grid', gap: '8px' }}>
                    {[
                      { label: 'Employee PF', key: 'employeePF' },
                      { label: 'Employee ESI', key: 'employeeESI' },
                      { label: 'Professional Tax', key: 'professionalTax' },
                      { label: 'TDS', key: 'tds' },
                      { label: 'Medical Insurance', key: 'medicalInsurance' }
                    ].map(item => (
                      <div key={item.key} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', paddingBottom: '8px', borderBottom: '1px solid rgba(38, 70, 83, 0.08)' }}>
                        <span style={{ color: 'var(--muted)', fontSize: '0.9rem' }}>{item.label}</span>
                        <span style={{ fontWeight: 600, color: '#e76f51', fontSize: '0.95rem' }}>₹ {calcResult[item.key]?.toLocaleString('en-IN', { maximumFractionDigits: 0 }) || '0'}</span>
                      </div>
                    ))}
                  </div>
                </div>

                {/* Benefits Section */}
                <div style={{ marginBottom: '16px' }}>
                  <div style={{ fontSize: '0.85rem', fontWeight: 600, color: '#264653', marginBottom: '10px', textTransform: 'uppercase', letterSpacing: '0.5px' }}>Employer Contributions</div>
                  <div style={{ display: 'grid', gap: '8px' }}>
                    {[
                      { label: 'Employer PF', key: 'employerPF' },
                      { label: 'Employer ESI', key: 'employerESI' },
                      { label: 'Gratuity', key: 'gratuity' }
                    ].map(item => (
                      <div key={item.key} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', paddingBottom: '8px', borderBottom: '1px solid rgba(38, 70, 83, 0.08)' }}>
                        <span style={{ color: 'var(--muted)', fontSize: '0.9rem' }}>{item.label}</span>
                        <span style={{ fontWeight: 600, color: '#264653', fontSize: '0.95rem' }}>₹ {calcResult[item.key]?.toLocaleString('en-IN', { maximumFractionDigits: 0 }) || '0'}</span>
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            </div>
          )}
        </section>
      </main>
    </div>
  );
}

async function fileToBase64(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result.toString());
    reader.onerror = reject;
    reader.readAsDataURL(file);
  });
}
