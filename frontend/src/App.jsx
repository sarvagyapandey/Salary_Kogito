import { useState } from 'react';
import { gql, downloadBase64DataUri } from './api';

export default function App() {
  const [employeeId, setEmployeeId] = useState('emp_16');
  const [calcResult, setCalcResult] = useState(null);
  const [status, setStatus] = useState('');

  const handleCalc = async () => {
    setStatus('Calculating...');
    try {
      const data = await gql(`
        query($id:String!){
          calculateSalary(employeeId:$id){
            employeeId name ctc basic hra bonus grossPayable
            employeePF employerPF employeeESI employerESI
            gratuity medicalInsurance tds takeHomeSalary
          }
        }`, { id: employeeId });
      setCalcResult(data.calculateSalary);
      setStatus('Done');
    } catch (e) { setStatus(e.message); }
  };

  const handleDownloadRules = async () => {
    setStatus('Downloading rules...');
    const { rulesWorkbook } = await gql(`query{ rulesWorkbook }`);
    downloadBase64DataUri(rulesWorkbook, 'salary-rules.xlsx');
    setStatus('Rules downloaded');
  };

  const handleDownloadTemplate = async () => {
    setStatus('Downloading template...');
    const { employeeTemplate } = await gql(`query{ employeeTemplate }`);
    downloadBase64DataUri(employeeTemplate, 'employee-template.xlsx');
    setStatus('Template downloaded');
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
    if (!file) return;
    setStatus('Uploading rules...');
    await uploadFile(file, 'uploadRulesWorkbook');
    setStatus('Rules uploaded/reloaded');
  };

  const handleProcessEmployees = async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setStatus('Processing employees...');
    const { processEmployeesWorkbook } =
      await uploadFile(file, 'processEmployeesWorkbook');
    downloadBase64DataUri(processEmployeesWorkbook, 'salary-output.xlsx');
    setStatus('Batch complete; output downloaded');
  };

  return (
    <div style={{ fontFamily: 'sans-serif', padding: 16 }}>
      <h2>Salary GraphQL UI</h2>
      <p>{status}</p>

      <section>
        <h3>Single Employee Calculation</h3>
        <input value={employeeId} onChange={e => setEmployeeId(e.target.value)} />
        <button onClick={handleCalc}>Calculate</button>
        {calcResult && (
          <pre>{JSON.stringify(calcResult, null, 2)}</pre>
        )}
      </section>

      <section>
        <h3>Rules Workbook</h3>
        <button onClick={handleDownloadRules}>Download Rules</button>
        <input type="file" accept=".xlsx" onChange={handleUploadRules} />
      </section>

      <section>
        <h3>Employee Batch</h3>
        <button onClick={handleDownloadTemplate}>Download Template</button>
        <div style={{ marginTop: 8 }}>
          <label>Upload filled template:</label>
          <input type="file" accept=".xlsx" onChange={handleProcessEmployees} />
        </div>
      </section>
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
