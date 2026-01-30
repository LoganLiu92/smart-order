import { ReactNode, useEffect, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { AuthMode, clearTokens, getAccessToken, getRefreshToken, refreshAccess } from "../api/http";

export default function RequireAuth({
  mode,
  children,
}: {
  mode: AuthMode;
  children: ReactNode;
}) {
  const navigate = useNavigate();
  const location = useLocation();
  const [ready, setReady] = useState(false);

  useEffect(() => {
    let cancelled = false;
    const check = async () => {
      const access = getAccessToken(mode);
      const refresh = getRefreshToken(mode);
      if (!access && !refresh) {
        if (!cancelled) {
          setReady(false);
          navigate(mode === "platform" ? "/platform-login" : "/login", {
            replace: true,
            state: { from: location.pathname },
          });
        }
        return;
      }
      const ok = await refreshAccess(mode);
      if (!ok) {
        clearTokens(mode);
        if (!cancelled) {
          setReady(false);
          navigate(mode === "platform" ? "/platform-login" : "/login", {
            replace: true,
            state: { from: location.pathname },
          });
        }
        return;
      }
      if (!cancelled) setReady(true);
    };
    check();
    return () => {
      cancelled = true;
    };
  }, [mode, navigate, location.pathname]);

  if (!ready) {
    return (
      <div className="page centered">
        <div className="card fade">正在校验身份…</div>
      </div>
    );
  }

  return <>{children}</>;
}
