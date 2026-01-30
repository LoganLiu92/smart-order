const params = new URLSearchParams(window.location.search);
const code = params.get("code");
const statusEl = document.getElementById("status");

async function resolve() {
  if (!code) {
    statusEl.textContent = "Missing code.";
    return;
  }
  try {
    const res = await window.api.apiGet(`/api/q?code=${encodeURIComponent(code)}`);
    if (res.status !== "OK") {
      statusEl.textContent = "Code not found.";
      return;
    }
    const url = `/customer/?storeId=${encodeURIComponent(res.storeId)}&tableNo=${encodeURIComponent(res.tableNo)}`;
    window.location.href = url;
  } catch (err) {
    statusEl.textContent = err.message;
  }
}

resolve();
