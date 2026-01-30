import { Navigate, Route, Routes } from "react-router-dom";
import Landing from "./pages/Landing";
import StoreAuth from "./pages/StoreAuth";
import PlatformLogin from "./pages/PlatformLogin";
import StoreAdmin from "./pages/StoreAdmin";
import Kitchen from "./pages/Kitchen";
import Cashier from "./pages/Cashier";
import PlatformAdmin from "./pages/PlatformAdmin";
import Customer from "./pages/Customer";
import RequireAuth from "./components/RequireAuth";
import QrRedirect from "./pages/QrRedirect";

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Landing />} />
      <Route path="/login" element={<StoreAuth />} />
      <Route path="/platform-login" element={<PlatformLogin />} />

      <Route
        path="/admin"
        element={
          <RequireAuth mode="store">
            <StoreAdmin />
          </RequireAuth>
        }
      />
      <Route
        path="/kitchen"
        element={
          <RequireAuth mode="store">
            <Kitchen />
          </RequireAuth>
        }
      />
      <Route
        path="/cashier"
        element={
          <RequireAuth mode="store">
            <Cashier />
          </RequireAuth>
        }
      />
      <Route
        path="/platform"
        element={
          <RequireAuth mode="platform">
            <PlatformAdmin />
          </RequireAuth>
        }
      />

      <Route path="/customer" element={<Customer />} />
      <Route path="/q" element={<QrRedirect />} />

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
