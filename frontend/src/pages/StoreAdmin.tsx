import { useEffect, useMemo, useState } from "react";
import { apiGet, apiPatch, apiPost, getStoreIdentity, setStoreIdentity, uploadFile } from "../api/http";
import { useNavigate } from "react-router-dom";

type Dish = {
  id: string;
  name: string;
  price: number;
  description?: string;
  ingredients?: string;
  allergens?: string;
  calories?: number;
  imageUrl?: string;
  detailImageUrl?: string;
  tags?: string[];
  optionGroups?: unknown[];
};

type Menu = {
  categories: { id: string; name: string; dishes: Dish[] }[];
};

export default function StoreAdmin() {
  const navigate = useNavigate();
  const identity = useMemo(() => getStoreIdentity(), []);
  const [storeId, setStoreId] = useState(identity.storeId || "");
  const [active, setActive] = useState("menu");
  const [menu, setMenu] = useState<Menu | null>(null);
  const [menuText, setMenuText] = useState("");
  const [newCategoryName, setNewCategoryName] = useState("");
  const [brandName, setBrandName] = useState("");
  const [logoFile, setLogoFile] = useState<File | null>(null);
  const [coverFile, setCoverFile] = useState<File | null>(null);
  const [users, setUsers] = useState<any[]>([]);
  const [wallet, setWallet] = useState<any>(null);
  const [tables, setTables] = useState<any[]>([]);
  const [baseUrl, setBaseUrl] = useState("http://localhost:8081/q?code=");
  const [newTableNo, setNewTableNo] = useState("");
  const [newTableCode, setNewTableCode] = useState("");
  const [bindTableNo, setBindTableNo] = useState("");
  const [bindCode, setBindCode] = useState("");
  const [newUser, setNewUser] = useState({ username: "", password: "", role: "CASHIER" });

  const loadAll = async () => {
    if (!storeId) return;
    const [menuRes, storeRes, usersRes, walletRes, tableRes] = await Promise.all([
      apiGet(`/api/menu/${storeId}`),
      apiGet(`/api/store/${storeId}`),
      apiGet(`/api/users?storeId=${storeId}`),
      apiGet(`/api/wallet/${storeId}`),
      apiGet(`/api/tables?storeId=${storeId}`),
    ]);
    setMenu(menuRes as Menu);
    setBrandName(storeRes.name || storeId);
    setUsers(usersRes || []);
    setWallet(walletRes);
    setTables(tableRes || []);
  };

  useEffect(() => {
    if (!storeId) return;
    setStoreIdentity(identity.role || "ADMIN", storeId);
    loadAll();
  }, [storeId]);

  const parseMenuText = async () => {
    await apiPost("/api/menu/parse", { storeId, ocrText: menuText });
    await loadAll();
  };

  const parseMenuImage = async (file: File) => {
    const reader = new FileReader();
    reader.onload = async () => {
      const result = String(reader.result || "");
      const base64 = result.split(",")[1];
      await apiPost("/api/menu/parse", { storeId, imageBase64: base64 });
      await loadAll();
    };
    reader.readAsDataURL(file);
  };

  const saveBrand = async () => {
    let logoUrl: string | undefined;
    let coverUrl: string | undefined;
    if (logoFile) {
      const res = await uploadFile("/api/media/upload", logoFile);
      logoUrl = res.thumbnailUrl || res.detailUrl;
    }
    if (coverFile) {
      const res = await uploadFile("/api/media/upload", coverFile);
      coverUrl = res.detailUrl || res.thumbnailUrl;
    }
    await apiPatch(`/api/store/${storeId}`, { name: brandName, logoUrl, coverUrl });
    await loadAll();
  };

  const createUser = async () => {
    if (!newUser.username || !newUser.password) return;
    await apiPost("/api/users", { storeId, ...newUser });
    setNewUser({ username: "", password: "", role: "CASHIER" });
    const usersRes = await apiGet(`/api/users?storeId=${storeId}`);
    setUsers(usersRes || []);
  };

  const updateDish = async (dish: Dish, patch: Partial<Dish>) => {
    await apiPatch(`/api/menu/${storeId}/dishes/${dish.id}`, patch);
    await loadAll();
  };

  const addCategory = async () => {
    if (!newCategoryName.trim()) return;
    await apiPost(`/api/menu/${storeId}/categories`, { name: newCategoryName.trim() });
    setNewCategoryName("");
    await loadAll();
  };

  const aiFillDish = async (dish: Dish) => {
    const response = await apiPost("/api/menu/ai-fill", { storeId, dishId: dish.id });
    const text = extractResponseText(response);
    let parsed: any = null;
    try {
      parsed = JSON.parse(text);
    } catch {
      parsed = null;
    }
    if (!parsed) return;
    await updateDish(dish, {
      ingredients: parsed.ingredients,
      allergens: parsed.allergens,
      calories: parsed.calories,
      tags: parsed.tags || [],
    });
  };

  const logout = () => {
    localStorage.removeItem("smartorder-access");
    localStorage.removeItem("smartorder-refresh");
    localStorage.removeItem("smartorder-role");
    localStorage.removeItem("smartorder-store");
    navigate("/login");
  };

  const createTable = async () => {
    if (!newTableNo.trim()) return;
    await apiPost("/api/tables", { storeId, tableNo: newTableNo.trim(), code: newTableCode.trim() });
    setNewTableNo("");
    setNewTableCode("");
    await loadAll();
  };

  const bindTable = async () => {
    if (!bindTableNo.trim() || !bindCode.trim()) return;
    await apiPost("/api/tables/bind", { storeId, tableNo: bindTableNo.trim(), code: bindCode.trim() });
    setBindTableNo("");
    setBindCode("");
    await loadAll();
  };

  const unbindTable = async (tableNo: string) => {
    await apiPost("/api/tables/unbind", { storeId, tableNo });
    await loadAll();
  };

  return (
    <div className="page">
      <div className="shell">
        <header className="topbar">
          <div>
            <h1>门店后台</h1>
            <p>菜单、品牌、员工、钱包与桌台统一管理。</p>
          </div>
          <div className="toolbar">
            <button className="ghost" onClick={() => navigate("/admin")}>管理</button>
            <button className="ghost" onClick={() => navigate("/cashier")}>收银</button>
            <button className="ghost" onClick={() => navigate("/kitchen")}>厨房</button>
            <button className="dark" onClick={logout}>退出登录</button>
          </div>
        </header>

        <div className="sidebar-layout">
          <aside className="sidebar">
            {[
              { id: "menu", label: "菜单构建" },
              { id: "brand", label: "品牌资产" },
              { id: "users", label: "员工账号" },
              { id: "wallet", label: "钱包" },
              { id: "tables", label: "桌台二维码" },
            ].map((item) => (
              <div
                key={item.id}
                className={`nav-link ${active === item.id ? "active" : ""}`}
                onClick={() => setActive(item.id)}
              >
                {item.label}
              </div>
            ))}
          </aside>

          <section className="stack">
            <div className="card">
              <label>门店 ID</label>
              <input value={storeId} onChange={(e) => setStoreId(e.target.value)} placeholder="store-id" />
            </div>

            {active === "menu" && (
              <section className="stack fade">
                <div className="card">
                  <h2>菜单构建</h2>
                  <div className="grid" style={{ marginTop: 12 }}>
                    <div>
                      <label>菜单文本</label>
                      <textarea rows={4} value={menuText} onChange={(e) => setMenuText(e.target.value)} />
                      <button className="secondary" style={{ marginTop: 8 }} onClick={parseMenuText}>
                        发送到 OpenAI
                      </button>
                    </div>
                    <div>
                      <label>菜单图片</label>
                      <input type="file" accept="image/*" onChange={(e) => e.target.files?.[0] && parseMenuImage(e.target.files[0])} />
                      <p className="small">图片会提交到识别接口。</p>
                    </div>
                  </div>
                </div>

                <div className="card">
                  <h3>手动维护菜单</h3>
                  <div className="toolbar" style={{ marginTop: 12 }}>
                    <input
                      value={newCategoryName}
                      onChange={(e) => setNewCategoryName(e.target.value)}
                      placeholder="新增分类名称"
                    />
                    <button className="dark" onClick={addCategory}>添加分类</button>
                  </div>
                </div>

                <div className="list">
                  {(menu?.categories || []).map((cat) => (
                    <div key={cat.id || cat.name} className="card">
                      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                        <h3>{cat.name}</h3>
                      </div>
                      <AddDishForm storeId={storeId} categoryId={cat.id} onCreated={loadAll} />
                      <div className="list">
                        {cat.dishes.map((dish) => (
                          <DishEditor
                            key={dish.id}
                            dish={dish}
                            onSave={updateDish}
                            onAiFill={aiFillDish}
                          />
                        ))}
                      </div>
                    </div>
                  ))}
                </div>
              </section>
            )}

            {active === "brand" && (
              <section className="stack fade">
                <div className="card">
                  <h2>品牌资产</h2>
                  <div className="grid" style={{ marginTop: 12 }}>
                    <div>
                      <label>门店名称</label>
                      <input value={brandName} onChange={(e) => setBrandName(e.target.value)} />
                    </div>
                    <div>
                      <label>Logo</label>
                      <input type="file" accept="image/*" onChange={(e) => setLogoFile(e.target.files?.[0] || null)} />
                    </div>
                    <div>
                      <label>封面图</label>
                      <input type="file" accept="image/*" onChange={(e) => setCoverFile(e.target.files?.[0] || null)} />
                    </div>
                  </div>
                  <div className="toolbar" style={{ marginTop: 12 }}>
                    <button className="dark" onClick={saveBrand}>保存</button>
                  </div>
                </div>
              </section>
            )}

            {active === "users" && (
              <section className="stack fade">
                <div className="card">
                  <h2>员工账号</h2>
                  <div className="grid" style={{ marginTop: 12 }}>
                    <div>
                      <label>用户名</label>
                      <input value={newUser.username} onChange={(e) => setNewUser({ ...newUser, username: e.target.value })} />
                    </div>
                    <div>
                      <label>密码</label>
                      <input value={newUser.password} onChange={(e) => setNewUser({ ...newUser, password: e.target.value })} />
                    </div>
                    <div>
                      <label>角色</label>
                      <select value={newUser.role} onChange={(e) => setNewUser({ ...newUser, role: e.target.value })}>
                        <option value="CASHIER">收银</option>
                        <option value="KITCHEN">厨房</option>
                        <option value="ADMIN">管理</option>
                      </select>
                    </div>
                  </div>
                  <div className="toolbar" style={{ marginTop: 12 }}>
                    <button className="dark" onClick={createUser}>创建账号</button>
                  </div>
                </div>

                <div className="list">
                  {users.length === 0 && <div className="card">暂无员工</div>}
                  {users.map((user) => (
                    <div key={user.id || user.username} className="card">
                      <strong>{user.username}</strong>
                      <span className="badge" style={{ marginLeft: 8 }}>{user.role}</span>
                    </div>
                  ))}
                </div>
              </section>
            )}

            {active === "wallet" && (
              <section className="stack fade">
                <div className="card">
                  <h2>钱包概览</h2>
                  {wallet && (
                    <div className="toolbar" style={{ marginTop: 12 }}>
                      <span className="badge">余额: ${wallet.balance?.toFixed?.(2) ?? wallet.balance}</span>
                      <span className="badge">AI 调用: {wallet.aiCalls}</span>
                      <span className="badge">订阅: {wallet.subscription?.status}</span>
                    </div>
                  )}
                </div>
                <div className="list">
                  {(wallet?.ledger || []).length === 0 && <div className="card">暂无流水</div>}
                  {(wallet?.ledger || []).map((entry: any, idx: number) => (
                    <div key={`${entry.type}-${idx}`} className="card">
                      <strong>{entry.type}</strong>
                      <p className="small">{entry.reason}</p>
                      <p className="small">金额: {entry.amount}</p>
                    </div>
                  ))}
                </div>
              </section>
            )}

            {active === "tables" && (
              <section className="stack fade">
                <div className="card">
                  <h2>桌台二维码链接</h2>
                  <div className="grid" style={{ marginTop: 12 }}>
                    <div>
                      <label>二维码基础地址</label>
                      <input value={baseUrl} onChange={(e) => setBaseUrl(e.target.value)} />
                    </div>
                    <div>
                      <label>示例链接</label>
                      <input value={`${baseUrl}YOUR_CODE`} readOnly />
                    </div>
                  </div>
                </div>

                <div className="card">
                  <h3>创建桌台并绑定二维码</h3>
                  <div className="grid" style={{ marginTop: 12 }}>
                    <div>
                      <label>桌号</label>
                      <input value={newTableNo} onChange={(e) => setNewTableNo(e.target.value)} placeholder="T1" />
                    </div>
                    <div>
                      <label>二维码 code（可选）</label>
                      <input value={newTableCode} onChange={(e) => setNewTableCode(e.target.value)} placeholder="code-001" />
                    </div>
                  </div>
                  <div className="toolbar" style={{ marginTop: 12 }}>
                    <button className="dark" onClick={createTable}>创建并绑定</button>
                  </div>
                </div>

                <div className="card">
                  <h3>绑定已有二维码</h3>
                  <div className="grid" style={{ marginTop: 12 }}>
                    <div>
                      <label>桌号</label>
                      <input value={bindTableNo} onChange={(e) => setBindTableNo(e.target.value)} placeholder="T2" />
                    </div>
                    <div>
                      <label>二维码 code</label>
                      <input value={bindCode} onChange={(e) => setBindCode(e.target.value)} placeholder="code-002" />
                    </div>
                  </div>
                  <div className="toolbar" style={{ marginTop: 12 }}>
                    <button className="dark" onClick={bindTable}>绑定</button>
                  </div>
                </div>

                <div className="list">
                  {tables.length === 0 && <div className="card">暂无桌台</div>}
                  {tables.map((item: any) => (
                    <div key={item.tableNo} className="card">
                      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                        <div>
                          <strong>{item.tableNo}</strong>
                          <span className="badge" style={{ marginLeft: 8 }}>{item.status}</span>
                        </div>
                        <div className="toolbar">
                          <button className="ghost" onClick={() => unbindTable(item.tableNo)}>解绑</button>
                        </div>
                      </div>
                      <p className="small">二维码 code: {item.code || "未绑定"}</p>
                      {item.code && <p className="small">链接: {`${baseUrl}${item.code}`}</p>}
                    </div>
                  ))}
                </div>
              </section>
            )}
          </section>
        </div>
      </div>
    </div>
  );
}

function DishEditor({
  dish,
  onSave,
  onAiFill,
}: {
  dish: Dish;
  onSave: (dish: Dish, patch: Partial<Dish>) => void;
  onAiFill: (dish: Dish) => void;
}) {
  const [open, setOpen] = useState(false);
  const [draft, setDraft] = useState({
    description: dish.description || "",
    ingredients: dish.ingredients || "",
    allergens: dish.allergens || "",
    calories: dish.calories || 0,
    imageUrl: dish.imageUrl || "",
    detailImageUrl: dish.detailImageUrl || "",
    tags: dish.tags?.join(",") || "",
    optionGroups: dish.optionGroups ? JSON.stringify(dish.optionGroups) : "",
  });
  const [upload, setUpload] = useState<File | null>(null);

  const save = async () => {
    let imageUrl = draft.imageUrl;
    let detailImageUrl = draft.detailImageUrl;
    if (upload) {
      const res = await uploadFile("/api/media/upload", upload);
      imageUrl = res.thumbnailUrl || imageUrl;
      detailImageUrl = res.detailUrl || detailImageUrl;
    }
    let optionGroups: unknown[] = [];
    if (draft.optionGroups.trim()) {
      try {
        optionGroups = JSON.parse(draft.optionGroups);
      } catch {
        return;
      }
    }
    await onSave(dish, {
      description: draft.description,
      ingredients: draft.ingredients,
      allergens: draft.allergens,
      calories: Number(draft.calories || 0),
      imageUrl,
      detailImageUrl,
      tags: draft.tags.split(",").map((t) => t.trim()).filter(Boolean),
      optionGroups,
    });
  };

  return (
    <div className="card">
      <div style={{ display: "flex", justifyContent: "space-between", gap: 12 }}>
        <div>
          <strong>{dish.name}</strong>
          <p className="small">${dish.price}</p>
        </div>
        <div className="toolbar">
          <button className="secondary" onClick={() => onAiFill(dish)}>AI 填充</button>
          <button className="ghost" onClick={() => setOpen((prev) => !prev)}>
            {open ? "收起" : "编辑"}
          </button>
        </div>
      </div>
      {open && (
        <div className="stack" style={{ marginTop: 12 }}>
          <label>描述</label>
          <input value={draft.description} onChange={(e) => setDraft({ ...draft, description: e.target.value })} />
          <label>原料</label>
          <input value={draft.ingredients} onChange={(e) => setDraft({ ...draft, ingredients: e.target.value })} />
          <label>过敏原</label>
          <input value={draft.allergens} onChange={(e) => setDraft({ ...draft, allergens: e.target.value })} />
          <label>卡路里</label>
          <input value={draft.calories} onChange={(e) => setDraft({ ...draft, calories: Number(e.target.value) })} />
          <label>缩略图 URL</label>
          <input value={draft.imageUrl} onChange={(e) => setDraft({ ...draft, imageUrl: e.target.value })} />
          <label>详情图 URL</label>
          <input value={draft.detailImageUrl} onChange={(e) => setDraft({ ...draft, detailImageUrl: e.target.value })} />
          <label>上传图片</label>
          <input type="file" accept="image/*" onChange={(e) => setUpload(e.target.files?.[0] || null)} />
          <label>标签</label>
          <input value={draft.tags} onChange={(e) => setDraft({ ...draft, tags: e.target.value })} />
          <label>规格 JSON</label>
          <textarea rows={4} value={draft.optionGroups} onChange={(e) => setDraft({ ...draft, optionGroups: e.target.value })} />
          <button className="dark" onClick={save}>保存</button>
        </div>
      )}
    </div>
  );
}

function extractResponseText(response: any) {
  if (!response || !response.output) return "";
  const first = response.output.find((o: any) => o.type === "message");
  if (!first || !first.content) return "";
  const textItem = first.content.find((c: any) => c.type === "output_text");
  return textItem ? textItem.text : "";
}

function AddDishForm({
  storeId,
  categoryId,
  onCreated,
}: {
  storeId: string;
  categoryId: string;
  onCreated: () => void;
}) {
  const [name, setName] = useState("");
  const [price, setPrice] = useState("");
  const [description, setDescription] = useState("");

  const submit = async () => {
    if (!categoryId) return;
    if (!name.trim() || !price.trim()) return;
    const value = Number(price);
    if (Number.isNaN(value)) return;
    await apiPost(`/api/menu/${storeId}/categories/${categoryId}/dishes`, {
      name: name.trim(),
      price: value,
      description: description.trim(),
    });
    setName("");
    setPrice("");
    setDescription("");
    onCreated();
  };

  return (
    <div className="card" style={{ marginTop: 12 }}>
      <h4>新增菜品</h4>
      {!categoryId && <p className="small">该分类暂不支持新增（请重新创建分类）。</p>}
      <div className="grid" style={{ marginTop: 12 }}>
        <div>
          <label>名称</label>
          <input value={name} onChange={(e) => setName(e.target.value)} placeholder="Dish name" disabled={!categoryId} />
        </div>
        <div>
          <label>价格</label>
          <input value={price} onChange={(e) => setPrice(e.target.value)} placeholder="12.5" disabled={!categoryId} />
        </div>
      </div>
      <div style={{ marginTop: 12 }}>
        <label>描述</label>
        <input value={description} onChange={(e) => setDescription(e.target.value)} placeholder="可选" disabled={!categoryId} />
      </div>
      <div className="toolbar" style={{ marginTop: 12 }}>
        <button className="dark" onClick={submit} disabled={!categoryId}>添加菜品</button>
      </div>
    </div>
  );
}
