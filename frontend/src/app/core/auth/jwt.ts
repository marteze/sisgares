/** Utilitários de JWT no cliente: apenas leitura de claims (a validação da assinatura é do backend). */

function base64UrlParaTexto(parte: string): string {
  const base64 = parte.replace(/-/g, '+').replace(/_/g, '/');
  const preenchido = base64 + '='.repeat((4 - (base64.length % 4)) % 4);
  const bytes = Uint8Array.from(atob(preenchido), (c) => c.charCodeAt(0));
  return new TextDecoder().decode(bytes);
}

function textoParaBase64Url(texto: string): string {
  const bytes = new TextEncoder().encode(texto);
  let binario = '';
  bytes.forEach((b) => (binario += String.fromCharCode(b)));
  return btoa(binario).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

/** Decodifica o payload de um JWT; retorna null se o formato for inválido. */
export function decodificarPayload(token: string): Record<string, unknown> | null {
  const partes = token.split('.');
  if (partes.length < 2) return null;
  try {
    const payload = JSON.parse(base64UrlParaTexto(partes[1]));
    return payload && typeof payload === 'object' ? (payload as Record<string, unknown>) : null;
  } catch {
    return null;
  }
}

/** Indica se o token está expirado (claim `exp`, em segundos). */
export function tokenExpirado(payload: Record<string, unknown>, agoraMs = Date.now()): boolean {
  const exp = payload['exp'];
  return typeof exp === 'number' && exp * 1000 <= agoraMs;
}

/** Gera um JWT fictício NÃO assinado (alg "none"), usado somente no modo mock local. */
export function gerarJwtFicticio(claims: Record<string, unknown>): string {
  const cabecalho = textoParaBase64Url(JSON.stringify({ alg: 'none', typ: 'JWT' }));
  const corpo = textoParaBase64Url(JSON.stringify(claims));
  return `${cabecalho}.${corpo}.`;
}

/** Codifica bytes em base64url (usado no PKCE). */
export function bytesParaBase64Url(bytes: Uint8Array): string {
  let binario = '';
  bytes.forEach((b) => (binario += String.fromCharCode(b)));
  return btoa(binario).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}
