import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  apiPost,
  getStoreIdentity,
  refreshAccess,
  setStoreIdentity,
  setTokens,
  getRefreshToken,
} from "../api/http";

export default function StoreAuth() {
  const navigate = useNavigate();
  const identity = getStoreIdentity();
  const [mode, setMode] = useState<"login" | "register">("login");
  const [storeId, setStoreId] = useState(identity.storeId || "");
  const [storeName, setStoreName] = useState("");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    const check = async () => {
      const hasRefresh = getRefreshToken("store");
      if (!hasRefresh) return;
      const ok = await refreshAccess("store");
      if (ok) {
        const role = getStoreIdentity().role || "ADMIN";
        navigate(roleRedirect(role), { replace: true });
      }
    };
    check();
  }, [navigate]);

  const roleRedirect = (role?: string | null) => {
    if (role === "CASHIER") return "/cashier";
    if (role === "KITCHEN") return "/kitchen";
    return "/admin";
  };

  const submit = async () => {
    setLoading(true);
    setError("");
    try {
      if (mode === "login") {
        const res = await apiPost("/api/auth/login", {
          storeId: storeId.trim(),
          username: username.trim(),
          password,
        });
        setTokens("store", res.accessToken, res.refreshToken);
        setStoreIdentity(res.role || "ADMIN", res.storeId || storeId.trim());
        navigate(roleRedirect(res.role), { replace: true });
      } else {
        const res = await apiPost("/api/auth/register", {
          storeId: storeId.trim(),
          storeName: storeName.trim(),
          username: username.trim(),
          password,
        });
        setTokens("store", res.accessToken, res.refreshToken);
        setStoreIdentity(res.role || "ADMIN", res.storeId || storeId.trim());
        navigate("/admin", { replace: true });
      }
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
            <h1>门店后台登录</h1>
            <p>登录后自动识别身份，进入对应后台。</p>
          </div>
          <div className="toolbar">
            <button
              className={mode === "login" ? "dark" : "ghost"}
              onClick={() => setMode("login")}
            >
              登录
            </button>
            <button
              className={mode === "register" ? "dark" : "ghost"}
              onClick={() => setMode("register")}
            >
              注册门店
            </button>
          </div>
        </header>

        <section className="hero fade">
          <div className="grid">
            <div>
              <label>门店 ID</label>
              <input
                value={storeId}
                onChange={(e) => setStoreId(e.target.value)}
                placeholder="如: taproom-01"
              />
            </div>
            {mode === "register" && (
              <div>
                <label>门店名称</label>
                <input
                  value={storeName}
                  onChange={(e) => setStoreName(e.target.value)}
                  placeholder="如: Warm Bistro"
                />
              </div>
            )}
            <div>
              <label>用户名</label>
              <input
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                placeholder="admin"
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
              {loading ? "处理中..." : mode === "login" ? "登录" : "注册并进入"}
            </button>
            <a className="button ghost" href="/">
              返回首页
            </a>
          </div>
        </section>
      </div>
    </div>
  );
}
