import { useEffect, useMemo, useState } from "react";
import { apiGet, apiPatch, getStoreIdentity } from "../api/http";
import { connectWs } from "../api/ws";
import { useNavigate } from "react-router-dom";

export default function Kitchen() {
  const navigate = useNavigate();
  const identity = useMemo(() => getStoreIdentity(), []);
  const [storeId, setStoreId] = useState(identity.storeId || "");
  const [status, setStatus] = useState("");
  const [orders, setOrders] = useState<any[]>([]);

  const loadOrders = async () => {
    if (!storeId) return;
    const data = await apiGet(`/api/orders?storeId=${storeId}&status=${status}`);
    setOrders(data || []);
  };

  useEffect(() => {
    loadOrders();
  }, [storeId, status]);

  useEffect(() => {
    const disconnect = connectWs(() => loadOrders());
    return () => disconnect();
  }, [storeId, status]);

  const updateStatus = async (orderId: string, next: string) => {
    await apiPatch(`/api/orders/${orderId}/status`, { status: next });
    await loadOrders();
  };

  const logout = () => {
    localStorage.removeItem("smartorder-access");
    localStorage.removeItem("smartorder-refresh");
    localStorage.removeItem("smartorder-role");
    localStorage.removeItem("smartorder-store");
    navigate("/login");
  };

  return (
    <div className="page">
      <div className="shell">
        <header className="topbar">
          <div>
            <h1>厨房看板</h1>
            <p>新订单、制作中与出餐状态实时同步。</p>
          </div>
          <div className="toolbar">
            {identity.role === "ADMIN" && (
              <button className="ghost" onClick={() => navigate("/admin")}>返回管理</button>
            )}
            <button className="dark" onClick={logout}>退出登录</button>
          </div>
        </header>

        <section className="hero">
          <div className="grid">
            <div>
              <label>门店 ID</label>
              <input value={storeId} onChange={(e) => setStoreId(e.target.value)} />
            </div>
          </div>
          <div className="tabs" style={{ marginTop: 16 }}>
            {[
              { id: "", label: "全部" },
              { id: "NEW", label: "新订单" },
              { id: "ACCEPTED", label: "制作中" },
              { id: "READY", label: "已出餐" },
            ].map((tab) => (
              <button
                key={tab.id || "all"}
                className={`tab-button ${status === tab.id ? "active" : ""}`}
                onClick={() => setStatus(tab.id)}
              >
                {tab.label}
              </button>
            ))}
          </div>
        </section>

        <section className="list" style={{ marginTop: 16 }}>
          {orders.length === 0 && <div className="card">暂无订单</div>}
          {orders.map((order) => (
            <div key={order.orderId} className="card">
              <div style={{ display: "flex", justifyContent: "space-between" }}>
                <div>
                  <strong>桌号 {order.tableNo}</strong>
                  <p className="small">{order.status}</p>
                </div>
                <span className="badge">{order.paymentStatus}</span>
              </div>
              <p className="small">
                {(order.items || []).map((i: any) => `${i.dishName} x${i.qty}`).join(", ")}
              </p>
              <div className="toolbar">
                {order.status === "NEW" && (
                  <button onClick={() => updateStatus(order.orderId, "ACCEPTED")}>接单</button>
                )}
                {order.status === "ACCEPTED" && (
                  <button onClick={() => updateStatus(order.orderId, "READY")}>出餐</button>
                )}
              </div>
            </div>
          ))}
        </section>
      </div>
    </div>
  );
}
