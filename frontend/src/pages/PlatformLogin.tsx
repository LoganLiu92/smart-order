import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { apiPost, getRefreshToken, refreshAccess, setTokens } from "../api/http";

export default function PlatformLogin() {
  const navigate = useNavigate();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    const check = async () => {
      const hasRefresh = getRefreshToken("platform");
      if (!hasRefresh) return;
      const ok = await refreshAccess("platform");
      if (ok) navigate("/platform", { replace: true });
    };
    check();
  }, [navigate]);

  const submit = async () => {
    setLoading(true);
    setError("");
    try {
      const res = await apiPost("/api/platform/login", {
        username: username.trim(),
        password,
      });
      setTokens("platform", res.accessToken, res.refreshToken);
      navigate("/platform", { replace: true });
    } catch (err) {
      setError(err instanceof Error ? err.message : "登录失败");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="page">
      <div className="shell">
        <header className="topbar">
          <div>
            <h1>平台后台登录</h1>
            <p>管理定价、订阅与店铺财务。</p>
          </div>
          <div className="toolbar">
            <a className="button ghost" href="/">
              返回首页
            </a>
          </div>
        </header>

        <section className="hero fade">
          <div className="grid">
            <div>
              <label>用户名</label>
              <input
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                placeholder="platform-admin"
              />
            </div>
            <div>
              <label>密码</label>
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="******"
              />
            </div>
          </div>
          {error && <p className="small" style={{ color: "#d24c2f" }}>{error}</p>}
          <div className="toolbar" style={{ marginTop: 16 }}>
            <button className="dark" onClick={submit} disabled={loading}>
              {loading ? "处理中..." : "登录"}
            </button>
            <div className="small">平台账号由总部创建。</div>
          </div>
        </section>
      </div>
    </div>
  );
}
