import React from "react";
import ReactDOM from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import App from "./App";
import "./styles/theme.css";

const Root = import.meta.env.DEV ? (
  // ✅ 开发环境：不开 StrictMode，避免 effect 双触发
  <BrowserRouter>
    <App />
  </BrowserRouter>
) : (
  // ✅ 生产环境：保留 StrictMode
  <React.StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </React.StrictMode>
);

ReactDOM.createRoot(document.getElementById("root")!).render(Root);
