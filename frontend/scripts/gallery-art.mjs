// Собственные векторные иллюстрации для демонстрационных данных.
export const themes = {
  cs: {
    name: "CS2",
    label: "SKINS / LOADOUT",
    bg: "#eebd49",
    ink: "#242625",
    mark: "weapon",
  },
  dota: {
    name: "DOTA 2",
    label: "ITEMS / COLLECTION",
    bg: "#b53b39",
    ink: "#f9e8d5",
    mark: "shield",
  },
  craft: {
    name: "MINECRAFT",
    label: "WORLDS / BUILDS",
    bg: "#b9c8a4",
    ink: "#284933",
    mark: "blocks",
  },
  gpt: {
    name: "ChatGPT",
    label: "WORK / IDEAS",
    bg: "#dce8db",
    ink: "#235d4b",
    mark: "chat",
  },
  spotify: {
    name: "Spotify",
    label: "MUSIC / PLAYLISTS",
    bg: "#b6eb85",
    ink: "#163c2d",
    mark: "music",
  },
  discord: {
    name: "Discord",
    label: "SERVERS / COMMUNITY",
    bg: "#c2c7f0",
    ink: "#3e4086",
    mark: "chat",
  },
};

const marks = {
  weapon:
    '<path d="M120 180h260l40-35h105v45h-90l-30 30H240l-38 100h-55l26-95h-53z"/><rect x="350" y="200" width="36" height="70" rx="7"/>',
  shield:
    '<path d="M210 80h200v130c0 80-100 142-100 142s-100-62-100-142z"/><path d="M245 130l130 140M370 118l-30 54M250 242l-12 40" fill="none" stroke="var(--bg)" stroke-width="24"/>',
  blocks:
    '<path d="M200 170l100-58 100 58v118l-100 57-100-57z"/><path d="M200 170l100 58 100-58M300 228v117" fill="none" stroke="var(--bg)" stroke-width="10"/><path d="M330 85l66-38 66 38v76l-66 38-66-38z" opacity=".35"/>',
  chat: '<rect x="170" y="85" width="270" height="200" rx="55"/><path d="M215 260v65l90-65z"/><path d="M230 155h150M230 200h100" fill="none" stroke="var(--bg)" stroke-width="20" stroke-linecap="round"/>',
  music:
    '<path d="M205 220v-48M250 258v-122M295 292V102M340 266V128M385 228v-46" fill="none" stroke="currentColor" stroke-width="25" stroke-linecap="round"/>',
};

export function cover(key, label) {
  const t = themes[key] || themes.gpt;
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1000 660" style="--bg:${t.bg};color:${t.ink}">
  <rect width="1000" height="660" fill="${t.bg}"/>
  <circle cx="835" cy="105" r="260" fill="${t.ink}" opacity=".045"/>
  <circle cx="95" cy="610" r="250" fill="${t.ink}" opacity=".035"/>
  <path d="M45 45h910v570H45z" fill="none" stroke="${t.ink}" stroke-opacity=".14"/>
  <text x="70" y="95" font-family="Arial,sans-serif" font-size="18" letter-spacing="5" fill="${t.ink}">${t.label}</text>
  <g transform="translate(380,80)" fill="currentColor">${marks[t.mark]}</g>
  <text x="65" y="485" font-family="Arial,sans-serif" font-size="${key === "craft" ? 74 : 100}" font-weight="800" letter-spacing="-5" fill="${t.ink}">${t.name}</text>
  <text x="70" y="555" font-family="Arial,sans-serif" font-size="27" fill="${t.ink}" opacity=".8">${label || "ITEMBRO / MARKET"}</text>
  </svg>`;
}

export function avatar(index) {
  const bg = ["#ead6ca", "#d3e4dc", "#dbd7ed", "#f0dbac", "#cbdfe8"][index % 5];
  const coat = ["#b64141", "#42675b", "#54538b", "#b27638", "#33586e"][
    index % 5
  ];
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 240 240"><rect width="240" height="240" rx="120" fill="${bg}"/>
  <path d="M35 245v-35c0-58 170-58 170 0v35" fill="${coat}"/>
  <rect x="100" y="136" width="40" height="48" rx="15" fill="#d59d7e"/>
  <ellipse cx="120" cy="104" rx="51" ry="62" fill="#e6b292"/>
  <path d="M69 99c-15-90 113-105 105 9-12-4-19-27-24-41-22 22-60 6-81 32" fill="#3b302d"/>
  <circle cx="100" cy="107" r="4" fill="#362e2c"/><circle cx="142" cy="107" r="4" fill="#362e2c"/>
  <path d="M107 134q14 12 28 0" fill="none" stroke="#925b48" stroke-width="4" stroke-linecap="round"/></svg>`;
}
