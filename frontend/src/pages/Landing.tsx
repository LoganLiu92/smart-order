export default function Landing() {
  return (
    <div className="page">
      <div className="shell">
        <header className="topbar">
          <div>
            <h1>Smart Order</h1>
            <p>一个更清爽的扫码点单与门店后台。</p>
          </div>
          <div className="toolbar">
            <a className="button" href="/customer">顾客扫码</a>
            <a className="button secondary" href="/login">门店登录</a>
            <a className="button ghost" href="/platform-login">平台登录</a>
          </div>
        </header>

        <section className="hero fade">
          <div className="grid">
            <div className="card">
              <h2>顾客体验</h2>
              <p className="small">更接近 me&u 风格，菜单、购物车、订单在一个流畅的节奏内完成。</p>
              <div className="tag-row">
                <span className="pill">可选 AI 推荐</span>
                <span className="pill">多人同桌</span>
                <span className="pill">实时更新</span>
              </div>
            </div>
            <div className="card soft">
              <h2>门店后台</h2>
              <p className="small">菜单、人员、钱包与桌台合在一个后台，登录后自动识别身份。</p>
              <div className="tag-row">
                <span className="pill">厨房/收银/管理</span>
                <span className="pill">鉴权闭环</span>
                <span className="pill">WebSocket 实时</span>
              </div>
            </div>
            <div className="card">
              <h2>平台控制台</h2>
              <p className="small">定价、订阅、店铺财务统一管理。</p>
              <div className="tag-row">
                <span className="pill">订阅管理</span>
                <span className="pill">充值与停续</span>
              </div>
            </div>
          </div>
        </section>
      </div>
    </div>
  );
}
