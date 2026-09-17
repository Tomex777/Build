export const BAILEY_MARK_SVG = `
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64" fill="none">
  <rect width="64" height="64" rx="17" fill="#EEF1EA"/>
  <path d="M20 15V49" stroke="#101214" stroke-width="7" stroke-linecap="round"/>
  <path d="M23.5 17.5H34.5C42 17.5 47 21.3 47 27.2C47 33.1 42 36.5 34.5 36.5H23.5" stroke="#101214" stroke-width="7" stroke-linecap="round" stroke-linejoin="round"/>
  <path d="M23.5 34.5H37C44.7 34.5 49.5 38 49.5 43.8C49.5 49.5 44.7 52.5 37 52.5H23.5" stroke="#101214" stroke-width="7" stroke-linecap="round" stroke-linejoin="round"/>
  <circle cx="20" cy="32" r="4.2" fill="#D8FF72" stroke="#101214" stroke-width="2.5"/>
</svg>`.trim();

export function baileyMarkDataUrl(): string {
  return `data:image/svg+xml;base64,${Buffer.from(BAILEY_MARK_SVG, "utf8").toString("base64")}`;
}
