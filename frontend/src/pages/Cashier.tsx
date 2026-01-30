import { useEffect, useMemo, useState } from "react";
import { apiGet, apiPatch, apiPost, getStoreIdentity } from "../api/http";
import { connectWs } from "../api/ws";
import { useNavigate } from "react-router-dom";

export default function Cashier() {
  const navigate = useNavigate();
  const identity = useMemo(() => getStoreIdentity(), []);
  const [storeId, setStoreId] = useState(identity.storeId || "");
  const [operator, setOperator] = useState(identity.role === "CASHIER" ? identity.role : "cashier-1");
  const [tableFilter, setTableFilter] = useState("");
  const [orders, setOrders] = useState<any[]>([]);

  const loadOrders = async () => {
    if (!storeId) return;
    const query = new URLSearchParams({ storeId });
    if (tableFilter) query.set("tableNo", tableFilter);
    const data = await apiGet(`/api/orders?${query.toString()}`);
    const active = (data || []).filter((o: any) => o.status !== "CLOSED");
    setOrders(active);
  };

  useEffect(() => {
    loadOrders();
  }, [storeId, tableFilter]);

  useEffect(() => {
    const disconnect = connectWs(() => loadOrders());
    return () => disconnect();
  }, [storeId, tableFilter]);

  const markPaid = async (orderId: string) => {
    await apiPatch(`/api/orders/${orderId}/payment`, { paymentStatus: "PAID", paidBy: operator });
    await loadOrders();
  };

  const settleTable = async (tableNo: string) => {
    await apiPost(`/api/tables/${tableNo}/settle`, { paidBy: operator, storeId });
    await loadOrders();
  };

  const clearTable = async (tableNo: string) => {
    await apiPost(`/api/tables/${tableNo}/clear`, { clearedBy: operator, storeId });
    await loadOrders();
  };

  const logout = () => {
    localStorage.removeItem("smartorder-access");
    localStorage.removeItem("smartorder-refresh");
    localStorage.removeItem("smartorder-role");
    localStorage.removeItem("smartorder-store");
    navigate("/login");
  };

  const tables = orders.reduce((acc: Record<string, any>, order: any) => {
    acc[order.tableNo] = acc[order.tableNo] || { unpaid: [], paid: [], total: 0 };
    if (order.paymentStatus === "UNPAID") {
      acc[order.tableNo].unpaid.push(order);
      acc[order.tableNo].total += Number(order.totalAmount || 0);
    } else {
      acc[order.tableNo].paid.push(order);
    }
    return acc;
  }, {});

  return (
    <div className="page">
      <div className="shell">
        <header className="topbar">
          <div>
            <h1>收银台</h1>
            <p>对账、结账与清台一站完成。</p>
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
            <div>
              <label>操作员</label>
              <input value={operator} onChange={(e) => setOperator(e.target.value)} />
            </div>
            <div>
              <label>桌号过滤</label>
              <input value={tableFilter} onChange={(e) => setTableFilter(e.target.value)} placeholder="T1" />
            </div>
          </div>
          <div className="toolbar" style={{ marginTop: 16 }}>
            <button className="secondary" onClick={loadOrders}>刷新</button>
          </div>
        </section>

        <section className="grid" style={{ marginTop: 16 }}>
          <div className="stack">
            <h2>桌台汇总</h2>
            <div className="list">
              {Object.keys(tables).length === 0 && <div className="card">暂无桌台</div>}
              {Object.entries(tables).map(([tableNo, bucket]) => (
                <div key={tableNo} className="card">
                  <strong>桌号 {tableNo}</strong>
                  <p className="small">未结订单: {bucket.unpaid.length}</p>
                  <p className="small">未结金额: ${bucket.total.toFixed(2)}</p>
                  <div className="toolbar">
                    {bucket.unpaid.length > 0 && (
                      <button onClick={() => settleTable(tableNo)}>结清未结</button>
                    )}
                    <button className="secondary" onClick={() => clearTable(tableNo)}>清台</button>
                  </div>
                </div>
              ))}
            </div>
          </div>

          <div className="stack">
            <h2>订单明细</h2>
            <div className="list">
              {orders.length === 0 && <div className="card">暂无订单</div>}
              {orders.map((order) => (
                <div key={order.orderId} className="card">
                  <div style={{ display: "flex", justifyContent: "space-between" }}>
                    <strong>桌号 {order.tableNo}</strong>
                    <span className="badge">{order.paymentStatus}</span>
                  </div>
                  <p className="small">{(order.items || []).map((i: any) => `${i.dishName} x${i.qty}`).join(", ")}</p>
                  <p className="small">总计: ${order.totalAmount || 0}</p>
                  {order.paymentStatus === "UNPAID" && (
                    <button className="secondary" onClick={() => markPaid(order.orderId)}>
                      标记已支付
                    </button>
                  )}
                </div>
              ))}
            </div>
          </div>
        </section>
      </div>
    </div>
  );
}
