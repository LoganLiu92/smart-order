import SockJS from "sockjs-client";
import Stomp from "stompjs";

const WS_URL =
  (window as typeof window & { WS_URL?: string }).WS_URL ||
  import.meta.env.VITE_WS_URL ||
  "http://localhost:8080/ws";

export type WsEvent = {
  type: string;
  payload?: unknown;
};

export function connectWs(onEvent?: (event: WsEvent) => void) {
  const socket = new SockJS(WS_URL);
  const client = Stomp.over(socket);
  client.connect({}, () => {
    client.subscribe("/topic/events", (message) => {
      try {
        const payload = JSON.parse(message.body) as WsEvent;
        onEvent?.(payload);
      } catch {
        // ignore
      }
    });
  });

  return () => {
    try {
      client.disconnect(() => undefined);
    } catch {
      // ignore
    }
  };
}
