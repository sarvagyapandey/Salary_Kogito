const GRAPHQL_URL = 'http://localhost:8080/graphql';

export async function gql(query, variables = {}) {
try {
const res = await fetch(GRAPHQL_URL, {
method: 'POST',
headers: { 'Content-Type': 'application/json' },
body: JSON.stringify({ query, variables }),
});

const json = await res.json();

// 🔍 Log full response for debugging
console.log("GraphQL full response:", json);

// ❌ Handle GraphQL errors
if (json.errors) {
  console.error("GraphQL Errors:", json.errors);
  throw new Error(json.errors.map(e => e.message).join(', '));
}

return json.data;

} catch (err) {
// ❌ Handle network / unexpected errors
console.error("Network / System Error:", err);
throw err;
}
}

export function downloadBase64DataUri(dataUri, filename) {
try {
const b64 = dataUri.replace(/^data:.*;base64,/, '');
const blob = new Blob([
Uint8Array.from(atob(b64), c => c.charCodeAt(0))
]);

const link = document.createElement('a');
link.href = URL.createObjectURL(blob);
link.download = filename;
link.click();

} catch (err) {
console.error("Download error:", err);
}
}