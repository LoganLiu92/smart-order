import { useEffect, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { API_BASE } from "../api/http";

export default function QrRedirect() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [error, setError] = useState("");

  useEffect(() => {
    const code = params.get("code");
    if (!code) {
      setError("缺少二维码参数");
      return;
    }
    const run = async () => {
      try {
        const res = await fetch(`${API_BASE}/api/q?code=${encodeURIComponent(code)}`);
        if (!res.ok) {
          setError("二维码解析失败");
          return;
        }
        const data = await res.json();
        if (data.status !== "OK") {
          setError("二维码尚未绑定桌台");
          return;
        }
        const target = `/customer?storeId=${encodeURIComponent(data.storeId)}&tableNo=${encodeURIComponent(data.tableNo)}`;
        navigate(target, { replace: true });
      } catch (err) {
        setError(err instanceof Error ? err.message : "二维码解析失败");
      }
    };
    run();
  }, [navigate, params]);

  return (
    <div className="page">
      <div className="shell">
        <section className="card" style={{ textAlign: "center" }}>
          <h2>正在进入点餐</h2>
          {error ? <p className="small" style={{ color: "#d24c2f" }}>{error}</p> : <p className="small">请稍候...</p>}
        </section>
      </div>
    </div>
  );
}
