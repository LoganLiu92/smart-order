import { useEffect, useMemo, useState } from "react";
import { apiDelete, apiGet, apiPatch, apiPost } from "../api/http";
import { connectWs } from "../api/ws";

type Dish = {
  id: string;
  name: string;
  price: number;
  description?: string;
  ingredients?: string;
  calories?: number;
  imageUrl?: string;
  optionGroups?: OptionGroup[];
};

type OptionGroup = {
  id: string;
  name: string;
  multiSelect?: boolean;
  items: { id: string; name: string; extraPrice?: number }[];
};

type Menu = {
  categories: { name: string; dishes: Dish[] }[];
};

type Cart = {
  items: any[];
};

export default function Customer() {
  const query = useMemo(() => new URLSearchParams(window.location.search), []);
  const [storeId, setStoreId] = useState(query.get("storeId") || "");
  const [tableNo, setTableNo] = useState(query.get("tableNo") || "");
  const [peopleCount, setPeopleCount] = useState(2);
  const [remark, setRemark] = useState("");
  const [step, setStep] = useState<"bind" | "start" | "ai" | "menu">("bind");
  const [menu, setMenu] = useState<Menu | null>(null);
  const [cart, setCart] = useState<Cart>({ items: [] });
  const [orders, setOrders] = useState<any[]>([]);
  const [aiInput, setAiInput] = useState("");
  const [aiResult, setAiResult] = useState<any[]>([]);
  const [aiRaw, setAiRaw] = useState("");
  const [adjustMode, setAdjustMode] = useState<"add" | "replace">("add");
  const [adjustInput, setAdjustInput] = useState("");
  const [pendingAdjust, setPendingAdjust] = useState<Dish[]>([]);
  const [optionDish, setOptionDish] = useState<Dish | null>(null);
  const [optionSelections, setOptionSelections] = useState<Record<string, string[]>>({});

  const clientId = useMemo(() => getClientId(), []);

  useEffect(() => {
    if (!storeId) return;
    loadMenu();
  }, [storeId]);

  useEffect(() => {
    if (!tableNo || !storeId) return;
    decideInitialStep();
    const disconnect = connectWs((event) => {
      if (event.type === "CART_UPDATED") {
        const payload: any = event.payload;
        if (payload?.tableNo === tableNo) {
          setCart(payload);
        }
      }
      if (event.type === "ORDER_CREATED" || event.type === "ORDER_UPDATED") {
        loadOrders();
      }
    });
    return () => disconnect();
  }, [storeId, tableNo]);

  useEffect(() => {
    if (step === "menu" && storeId && tableNo) {
      apiPost("/api/session/unlock", { storeId, tableNo });
    }
  }, [step, storeId, tableNo]);

  const loadMenu = async () => {
    const data = await apiGet(`/api/menu/${storeId}`);
    setMenu(data as Menu);
  };

  const loadCart = async () => {
    if (!tableNo) return { items: [] };
    const data = await apiGet(`/api/cart?storeId=${storeId}&tableNo=${tableNo}`);
    setCart(data);
    return data as Cart;
  };

  const loadOrders = async () => {
    if (!tableNo) return [];
    const data = await apiGet(`/api/orders?storeId=${storeId}&tableNo=${tableNo}`);
    const active = (data || []).filter((o: any) => o.status !== "CLOSED");
    setOrders(active);
    return active;
  };

  const decideInitialStep = async () => {
    const sessionExists = hasSession(storeId, tableNo);
    const sessionStatus = await apiGet(`/api/session?storeId=${storeId}&tableNo=${tableNo}`);
    const cartData = await loadCart();
    const ordersData = await loadOrders();
    const hasActivity = (cartData.items || []).length > 0 || ordersData.length > 0 || sessionStatus?.active;

    if (!sessionExists && hasActivity) {
      setStep("menu");
      return;
    }
    if (sessionExists) {
      setStep("menu");
    } else {
      setStep("start");
    }
  };

  const bindTable = async () => {
    if (!storeId || !tableNo) return;
    setStep("start");
  };

  const startSession = () => {
    if (!storeId || !tableNo) return;
    markSessionStarted(storeId, tableNo);
    setStep("ai");
  };

  const skipToMenu = () => {
    if (!storeId || !tableNo) return;
    markSessionStarted(storeId, tableNo);
    setStep("menu");
  };

  const addToCart = async (dish: Dish, qty = 1, selectedOptions: any[] = []) => {
    if (!tableNo) return;
    const extra = selectedOptions.reduce((sum, opt) => sum + (opt.extraPrice || 0), 0);
    const data = await apiPost("/api/cart/items", {
      storeId,
      tableNo,
      dishId: dish.id,
      dishName: dish.name,
      qty,
      unitPrice: Number(dish.price) + extra,
      selectedOptions,
    });
    setCart(data as Cart);
  };

  const updateQty = async (dishId: string, qty: number, optionSignature?: string) => {
    if (!tableNo) return;
    if (qty <= 0) {
      const sig = optionSignature ? `&optionSignature=${encodeURIComponent(optionSignature)}` : "";
      const data = await apiDelete(`/api/cart/items?storeId=${storeId}&tableNo=${tableNo}&dishId=${dishId}${sig}`);
      setCart(data as Cart);
    } else {
      const data = await apiPatch("/api/cart/items", { storeId, tableNo, dishId, optionSignature, qty });
      setCart(data as Cart);
    }
  };

  const submitOrder = async () => {
    if (!tableNo) return;
    const items = cart.items || [];
    if (items.length === 0) return;
    const orderItems = items.map((item: any) => ({ ...item, lineTotal: item.qty * item.unitPrice }));
    const order = await apiPost("/api/orders", {
      storeId,
      tableNo,
      peopleCount,
      remark,
      clientId,
      items: orderItems,
    });
    await apiPost("/api/cart/clear", { storeId, tableNo });
    setCart({ items: [] });
    await loadOrders();
    setStep("menu");
    alert(`订单提交成功: ${order.orderId}`);
  };

  const buildAiMessage = (extraPreference: string) => {
    const parts = [
      `People count: ${peopleCount}`,
      remark ? `Special requests: ${remark}` : "",
      extraPreference ? `User preference: ${extraPreference}` : "",
      cart.items?.length ? `Current cart: ${cart.items.map((i: any) => `${i.dishName} x${i.qty}`).join(", ")}` : "Current cart: empty",
    ].filter(Boolean);
    return parts.join(". ");
  };

  const askAi = async () => {
    if (!aiInput.trim()) return;
    setAiRaw("thinking");
    const response = await apiPost("/api/ai/recommend", {
      storeId,
      tableNo,
      message: buildAiMessage(aiInput.trim()),
    });
    const { recommendations, rawText } = extractRecommendations(response, menu);
    setAiResult(recommendations);
    setAiRaw(rawText || "");
  };

  const acceptAi = async () => {
    for (const dish of aiResult) {
      await addToCart(dish, 1);
    }
    setStep("menu");
  };

  const askAdjust = async () => {
    if (!adjustInput.trim()) return;
    const response = await apiPost("/api/ai/recommend", {
      storeId,
      tableNo,
      message: buildAiMessage(adjustInput.trim()),
    });
    const { recommendations } = extractRecommendations(response, menu);
    setPendingAdjust(recommendations);
  };

  const applyAdjust = async () => {
    if (adjustMode === "replace") {
      await apiPost("/api/cart/clear", { storeId, tableNo });
      setCart({ items: [] });
    }
    for (const dish of pendingAdjust) {
      await addToCart(dish, 1);
    }
    setPendingAdjust([]);
  };

  const totalCount = (cart.items || []).reduce((sum: number, item: any) => sum + (item.qty || 0), 0);
  const totalAmount = (cart.items || []).reduce((sum: number, item: any) => sum + (item.qty || 0) * (item.unitPrice || 0), 0);

  const openOptions = (dish: Dish) => {
    setOptionDish(dish);
    const initial: Record<string, string[]> = {};
    dish.optionGroups?.forEach((group) => {
      if (!group.multiSelect && group.items?.[0]) {
        initial[group.id] = [group.items[0].id];
      }
    });
    setOptionSelections(initial);
  };

  const confirmOptions = async () => {
    if (!optionDish) return;
    const selected: any[] = [];
    optionDish.optionGroups?.forEach((group) => {
      const picked = optionSelections[group.id] || [];
      group.items.forEach((item) => {
        if (picked.includes(item.id)) {
          selected.push({
            groupId: group.id,
            groupName: group.name,
            optionId: item.id,
            optionName: item.name,
            extraPrice: item.extraPrice || 0,
          });
        }
      });
    });
    await addToCart(optionDish, 1, selected);
    setOptionDish(null);
  };

  return (
    <div className="page">
      <div className="shell">
        <header className="topbar">
          <div>
            <h1>点单</h1>
            <p>一桌同点，实时同步。</p>
          </div>
          <div className="toolbar">
            {storeId && <span className="badge">{storeId}</span>}
            {tableNo && <span className="badge">桌号 {tableNo}</span>}
          </div>
        </header>

        {step === "bind" && (
          <section className="hero fade">
            <h2>欢迎入座</h2>
            <p className="small">可以扫码，也可以手动输入门店与桌号。</p>
            <div className="grid" style={{ marginTop: 12 }}>
              <div>
                <label>门店 ID</label>
                <input value={storeId} onChange={(e) => setStoreId(e.target.value)} placeholder="store-id" />
              </div>
              <div>
                <label>桌号</label>
                <input value={tableNo} onChange={(e) => setTableNo(e.target.value)} placeholder="T1" />
              </div>
            </div>
            <div className="toolbar" style={{ marginTop: 16 }}>
              <button className="dark" onClick={bindTable}>进入点单</button>
            </div>
          </section>
        )}

        {step === "start" && (
          <section className="hero fade">
            <h2>开始点单</h2>
            <p className="small">告诉我们人数与偏好，系统可以给出建议。</p>
            <div className="grid" style={{ marginTop: 12 }}>
              <div>
                <label>人数</label>
                <input type="number" value={peopleCount} onChange={(e) => setPeopleCount(Number(e.target.value))} />
              </div>
              <div>
                <label>备注</label>
                <input value={remark} onChange={(e) => setRemark(e.target.value)} placeholder="少辣、少盐等" />
              </div>
            </div>
            <div className="toolbar" style={{ marginTop: 16 }}>
              <button className="dark" onClick={startSession}>让 AI 先推荐</button>
              <button className="ghost" onClick={skipToMenu}>直接看菜单</button>
            </div>
          </section>
        )}

        {step === "ai" && (
          <section className="hero fade">
            <h2>AI 推荐</h2>
            <p className="small">描述一下口味偏好或预算范围。</p>
            <textarea rows={3} value={aiInput} onChange={(e) => setAiInput(e.target.value)} placeholder="清淡、低脂、适合分享..." />
            <div className="toolbar" style={{ marginTop: 12 }}>
              <button className="dark" onClick={askAi}>生成推荐</button>
              <button className="ghost" onClick={() => setStep("menu")}>先看菜单</button>
            </div>
            <div className="list" style={{ marginTop: 12 }}>
              {aiRaw === "thinking" && <div className="card">AI 思考中...</div>}
              {aiResult.length === 0 && aiRaw && aiRaw !== "thinking" && <div className="card">{aiRaw}</div>}
              {aiResult.map((dish) => (
                <div key={dish.id} className="card">
                  <strong>{dish.name}</strong>
                  <p className="small">${dish.price}</p>
                </div>
              ))}
            </div>
            <div className="toolbar" style={{ marginTop: 12 }}>
              <button onClick={acceptAi}>加入购物车</button>
              <button className="ghost" onClick={() => setStep("menu")}>跳过</button>
            </div>
          </section>
        )}

        {step === "menu" && (
          <section className="stack" style={{ marginTop: 24 }}>
            <div className="card">
              <div className="toolbar" style={{ justifyContent: "space-between" }}>
                <div>
                  <h2>菜单</h2>
                  <p className="small">轻点即可加入购物车。</p>
                </div>
                <div className="toolbar">
                  <button className="secondary" onClick={() => setStep("ai")}>再问 AI</button>
                </div>
              </div>
            </div>

            <div className="grid">
              <div className="stack">
                {(menu?.categories || []).map((cat) => (
                  <div key={cat.name} className="card">
                    <h3>{cat.name}</h3>
                    <div className="list">
                      {cat.dishes.map((dish) => (
                        <div key={dish.id} className="card soft">
                          <div style={{ display: "flex", gap: 12, justifyContent: "space-between" }}>
                            <div style={{ flex: 1 }}>
                              <strong>{dish.name}</strong>
                              <p className="small">${dish.price}</p>
                              {dish.description && <p className="small">{dish.description}</p>}
                              {dish.ingredients && <p className="small">原料: {dish.ingredients}</p>}
                              {dish.calories && <p className="small">{dish.calories} kcal</p>}
                            </div>
                            <div className="toolbar" style={{ alignSelf: "center" }}>
                              <button onClick={() => (dish.optionGroups?.length ? openOptions(dish) : addToCart(dish))}>
                                加入
                              </button>
                            </div>
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                ))}
              </div>

              <div className="stack">
                <div className="card">
                  <h2>购物车</h2>
                  <div className="list" style={{ marginTop: 12 }}>
                    {(cart.items || []).length === 0 && <div className="small">暂无商品</div>}
                    {(cart.items || []).map((item: any) => (
                      <div key={`${item.dishId}-${item.optionSignature || ""}`} className="card">
                        <strong>{item.dishName}</strong>
                        {item.selectedOptions?.length > 0 && (
                          <p className="small">{item.selectedOptions.map((o: any) => o.optionName).join(", ")}</p>
                        )}
                        <div className="toolbar">
                          <button className="secondary" onClick={() => updateQty(item.dishId, item.qty - 1, item.optionSignature)}>-</button>
                          <span>x {item.qty}</span>
                          <button onClick={() => updateQty(item.dishId, item.qty + 1, item.optionSignature)}>+</button>
                        </div>
                      </div>
                    ))}
                  </div>
                  <div className="toolbar" style={{ marginTop: 12 }}>
                    <button className="dark" onClick={submitOrder}>提交订单</button>
                  </div>
                </div>

                <div className="card">
                  <h2>订单进度</h2>
                  <div className="list" style={{ marginTop: 12 }}>
                    {orders.length === 0 && <div className="small">暂无订单</div>}
                    {orders.map((order) => (
                      <div key={order.orderId} className="card">
                        <strong>{order.status}</strong>
                        <span className="badge" style={{ marginLeft: 8 }}>{order.paymentStatus}</span>
                        <p className="small">{(order.items || []).map((i: any) => `${i.dishName} x${i.qty}`).join(", ")}</p>
                      </div>
                    ))}
                  </div>
                </div>

                <div className="card">
                  <h2>AI 调整</h2>
                  <p className="small">用一句话让 AI 优化当前购物车。</p>
                  <div className="grid" style={{ marginTop: 12 }}>
                    <div>
                      <label>模式</label>
                      <select value={adjustMode} onChange={(e) => setAdjustMode(e.target.value as "add" | "replace")}> 
                        <option value="add">在现有基础上添加</option>
                        <option value="replace">替换当前购物车</option>
                      </select>
                    </div>
                    <div>
                      <label>偏好描述</label>
                      <input value={adjustInput} onChange={(e) => setAdjustInput(e.target.value)} placeholder="更清淡 / 更适合分享" />
                    </div>
                  </div>
                  <div className="toolbar" style={{ marginTop: 12 }}>
                    <button className="secondary" onClick={askAdjust}>生成建议</button>
                    <button onClick={applyAdjust}>应用建议</button>
                  </div>
                  {pendingAdjust.length > 0 && (
                    <div className="list" style={{ marginTop: 12 }}>
                      {pendingAdjust.map((dish) => (
                        <div key={dish.id} className="card soft">
                          <strong>{dish.name}</strong>
                          <p className="small">${dish.price}</p>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            </div>
          </section>
        )}

        {optionDish && (
          <div className="page centered" style={{ position: "fixed", inset: 0, background: "rgba(0,0,0,0.4)", zIndex: 20 }}>
            <div className="card" style={{ maxWidth: 460, width: "90%" }}>
              <h2>{optionDish.name}</h2>
              <div className="stack" style={{ marginTop: 12 }}>
                {optionDish.optionGroups?.map((group) => (
                  <div key={group.id} className="card soft">
                    <strong>{group.name}</strong>
                    <div className="stack" style={{ marginTop: 8 }}>
                      {group.items.map((item) => {
                        const selected = optionSelections[group.id] || [];
                        const checked = selected.includes(item.id);
                        return (
                          <label key={item.id} style={{ display: "flex", gap: 8, alignItems: "center" }}>
                            <input
                              type={group.multiSelect ? "checkbox" : "radio"}
                              name={group.id}
                              checked={checked}
                              onChange={() => {
                                setOptionSelections((prev) => {
                                  const next = { ...prev };
                                  if (group.multiSelect) {
                                    const list = new Set(next[group.id] || []);
                                    if (list.has(item.id)) list.delete(item.id);
                                    else list.add(item.id);
                                    next[group.id] = Array.from(list);
                                  } else {
                                    next[group.id] = [item.id];
                                  }
                                  return next;
                                });
                              }}
                            />
                            {item.name}{item.extraPrice ? ` +$${item.extraPrice}` : ""}
                          </label>
                        );
                      })}
                    </div>
                  </div>
                ))}
              </div>
              <div className="toolbar" style={{ marginTop: 12 }}>
                <button onClick={confirmOptions}>确认</button>
                <button className="ghost" onClick={() => setOptionDish(null)}>取消</button>
              </div>
            </div>
          </div>
        )}

        {totalCount > 0 && (
          <div className="cart-bar">
            <div>
              <strong>{totalCount} 件</strong>
              <div className="small">$ {totalAmount.toFixed(2)}</div>
            </div>
            <button onClick={() => setStep("menu")}>查看购物车</button>
          </div>
        )}
      </div>
    </div>
  );
}

function extractRecommendations(response: any, menu: Menu | null) {
  const text = extractResponseText(response);
  let parsed: any = null;
  try {
    parsed = JSON.parse(text);
  } catch {
    parsed = null;
  }
  const recommendations: Dish[] = [];
  if (parsed?.recommendations && menu) {
    parsed.recommendations.forEach((rec: any) => {
      const dish = findDish(menu, rec.dishId, rec.dishName);
      if (dish) recommendations.push(dish);
    });
  }
  return { recommendations, rawText: text };
}

function extractResponseText(response: any) {
  if (!response || !response.output) return "";
  const first = response.output.find((o: any) => o.type === "message");
  if (!first || !first.content) return "";
  const textItem = first.content.find((c: any) => c.type === "output_text");
  return textItem ? textItem.text : "";
}

function findDish(menu: Menu, dishId?: string, dishName?: string) {
  for (const cat of menu.categories || []) {
    for (const dish of cat.dishes || []) {
      if (dishId && dish.id === dishId) return dish;
      if (dishName && dish.name === dishName) return dish;
    }
  }
  return null;
}

function getClientId() {
  const key = "smartorder-client-id";
  let value = localStorage.getItem(key);
  if (!value) {
    value = crypto.randomUUID();
    localStorage.setItem(key, value);
  }
  return value;
}

function sessionKey(storeId: string, tableNo: string) {
  return `smartorder-session-${storeId}-${tableNo}`;
}

function markSessionStarted(storeId: string, tableNo: string) {
  localStorage.setItem(sessionKey(storeId, tableNo), "1");
  apiPost("/api/session/lock", { storeId, tableNo });
}

function hasSession(storeId: string, tableNo: string) {
  return localStorage.getItem(sessionKey(storeId, tableNo)) === "1";
}
