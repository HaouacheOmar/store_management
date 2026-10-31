// Cloudflare Pages Function : relaie /api/** vers la gateway (Render), comme nginx le fait en Docker.
// Le front reste sur la même origine : pas de CORS, aucune URL d'API codée en dur dans Angular.
// GATEWAY_URL (variable du projet Pages) : URL publique de la gateway, ex. https://store-gateway.onrender.com
export async function onRequest({ request, env }) {
  const url = new URL(request.url);
  const target = new URL(url.pathname + url.search, env.GATEWAY_URL);
  // même méthode, en-têtes (dont Authorization et Upgrade WebSocket) et corps
  return fetch(new Request(target, request));
}
