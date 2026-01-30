import { useEffect, useState } from "react";
import { apiGet, apiPost, clearTokens } from "../api/http";
import { useNavigate } from "react-router-dom";

export default function PlatformAdmin() {
  const navigate = useNavigate();
  const [active, setActive] = useState("pricing");
  const [pricing, setPricing] = useState({ platformMonthlyFee: 0, storeMonthlyFee: 0, aiCallPrice: 0 });
  const [stores, setStores] = useState<any[]>([]);

  const loadPricing = async () => {
    const data = await apiGet("/api/platform/pricing");
    setPricing({
      platformMonthlyFee: data.platformMonthlyFee || 0,
      storeMonthlyFee: data.storeMonthlyFee || 0,
      aiCallPrice: data.aiCallPrice || 0,
    });
  };

  const savePricing = async () => {
    await apiPost("/api/platform/pricing", pricing);
    await loadPricing();
  };

  const loadStores = async () => {
    const data = await apiGet("/api/platform/stores");
    setStores(data || []);
  };

  useEffect(() => {
    loadPricing();
    loadStores();
  }, []);

  const logout = () => {
    clearTokens("platform");
    navigate("/platform-login");
  };

  return (
    <div className="page">
      <div className="shell">
        <header className="topbar">
          <div>
            <h1>平台控制台</h1>
            <p>定价、订阅、账本与店铺运营。</p>
          </div>
          <div className="toolbar">
            <button className="dark" onClick={logout}>退出登录</button>
          </div>
        </header>

        <div className="sidebar-layout">
          <aside className="sidebar">
            <div className={`nav-link ${active === "pricing" ? "active" : ""}`} onClick={() => setActive("pricing")}>
              定价设置
            </div>
            <div className={`nav-link ${active === "stores" ? "active" : ""}`} onClick={() => setActive("stores")}>
              店铺列表
            </div>
          </aside>

          <section className="stack">
            {active === "pricing" && (
              <div className="card fade">
                <h2>平台定价</h2>
                <div className="grid" style={{ marginTop: 12 }}>
                  <div>
                    <label>平台月费</label>
                    <input
                      value={pricing.platformMonthlyFee}
                      onChange={(e) => setPricing({ ...pricing, platformMonthlyFee: Number(e.target.value || 0) })}
                    />
                  </div>
                  <div>
                    <label>门店月费</label>
                    <input
                      value={pricing.storeMonthlyFee}
                      onChange={(e) => setPricing({ ...pricing, storeMonthlyFee: Number(e.target.value || 0) })}
                    />
                  </div>
                  <div>
                    <label>AI 调用价格</label>
                    <input
                      value={pricing.aiCallPrice}
                      onChange={(e) => setPricing({ ...pricing, aiCallPrice: Number(e.target.value || 0) })}
                    />
                  </div>
                </div>
                <div className="toolbar" style={{ marginTop: 12 }}>
                  <button className="dark" onClick={savePricing}>保存</button>
                </div>
              </div>
            )}

            {active === "stores" && (
              <div className="list fade">
                {stores.length === 0 && <div className="card">暂无店铺</div>}
                {stores.map((store) => (
                  <div key={store.storeId} className="card">
                    <div style={{ display: "flex", justifyContent: "space-between", gap: 12 }}>
                      <div>
                        <strong>{store.storeId}</strong>
                        <p className="small">余额: ${store.balance}</p>
                        <p className="small">订阅: {store.subscriptionStatus}（到期 {store.subscriptionExpireAt}）</p>
                        <p className="small">AI 调用: {store.aiCalls} | Tokens: {store.aiTokens}</p>
                      </div>
                      <div className="toolbar">
                        <button onClick={async () => { await apiPost(`/api/platform/stores/${store.storeId}/topup`, { amount: 100, reason: "Manual topup" }); loadStores(); }}>
                          +$100
                        </button>
                        <button className="secondary" onClick={async () => { await apiPost(`/api/platform/stores/${store.storeId}/subscription/charge`, {}); loadStores(); }}>
                          扣月费
                        </button>
                        <button className="ghost" onClick={async () => { await apiPost(`/api/platform/stores/${store.storeId}/subscription/pause`, {}); loadStores(); }}>
                          暂停
                        </button>
                        <button className="ghost" onClick={async () => { await apiPost(`/api/platform/stores/${store.storeId}/subscription/renew`, { days: 30 }); loadStores(); }}>
                          续费 30 天
                        </button>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </section>
        </div>
      </div>
    </div>
  );
}
