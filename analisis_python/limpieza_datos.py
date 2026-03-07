"""
limpieza_datos.py
Carga, limpia y prepara el CSV de captura de red para análisis.

Lógica de detección automática de archivos de entrada:
  1. Si existe ../captura_red.csv          → se usa ese archivo.
  2. Si no existe pero hay archivos con el patrón ../captura_red_*.csv
     (p.ej. captura_red_2026-03-04.csv)  → se leen TODOS y se combinan.
  3. Si no hay ningún archivo CSV          → se lanza un error descriptivo.

Exporta la función cargar_datos() que devuelve un DataFrame listo para usar.
"""

import glob
import os
import pandas as pd
import numpy as np
from sklearn.preprocessing import LabelEncoder, StandardScaler

# Directorio raíz del proyecto (un nivel arriba de analisis_python/)
_RAIZ = os.path.join(os.path.dirname(__file__), "..")
CSV_PATH = os.path.join(_RAIZ, "captura_red.csv")

# Heurística por prefijo de IP para identificar servicios/aplicaciones conocidas.
# Nota: es una aproximación; la IP real puede variar según CDN o región.
PREFIJOS_APP = {
    # ========== Google / YouTube / Gmail / Drive ==========
    "142.250": "Google/YouTube", "142.251": "Google/YouTube",
    "172.217": "Google/YouTube", "216.58":  "Google/YouTube",
    "74.125":  "Google/YouTube", "64.233":  "Google/YouTube",
    "8.8":     "Google-DNS",     "8.34":    "Google",
    "35.190":  "Google-Cloud",   "35.201":  "Google-Cloud",
    "35.186":  "Google-Cloud",   "34.64":   "Google-Cloud",
    "34.102":  "Google-Cloud",   "34.117":  "Google-Cloud",
    
    # ========== Meta (Facebook / Instagram / WhatsApp / Messenger) ==========
    "157.240": "Meta (Facebook/Instagram)", "179.60": "Meta (Facebook/Instagram)",
    "31.13":   "Meta (Facebook/Instagram)", "163.70": "Meta (Facebook/Instagram)",
    "185.60":  "Meta (Facebook/Instagram)", "69.63":  "Meta (Facebook/Instagram)",
    "69.171":  "Meta (Facebook/Instagram)", "66.220": "Meta (Facebook/Instagram)",
    "204.15":  "Meta (Facebook/Instagram)", "173.252": "Meta (Facebook/Instagram)",
    
    # ========== Microsoft / Azure / Teams / Office365 / OneDrive ==========
    "52.":     "Microsoft/Azure",  "20.":    "Microsoft/Azure",
    "40.":     "Microsoft/Azure",  "13.":    "Microsoft/Azure",
    "104.208": "Microsoft/Azure",  "104.209": "Microsoft/Azure",
    "137.116": "Microsoft",        "137.117": "Microsoft",
    "207.46":  "Microsoft",        "65.52":  "Microsoft",
    "191.232": "Microsoft/Teams",  "191.233": "Microsoft/Teams",
    
    # ========== Cloudflare (CDN + ChatGPT + Muchos sitios) ==========
    "104.16":  "Cloudflare/ChatGPT-posible", "104.17": "Cloudflare/ChatGPT-posible",
    "104.18":  "Cloudflare/ChatGPT-posible", "104.19": "Cloudflare/ChatGPT-posible",
    "104.20":  "Cloudflare/ChatGPT-posible", "104.21": "Cloudflare/ChatGPT-posible",
    "104.22":  "Cloudflare/ChatGPT-posible", "104.23": "Cloudflare/ChatGPT-posible",
    "104.24":  "Cloudflare/ChatGPT-posible", "104.25": "Cloudflare/ChatGPT-posible",
    "104.26":  "Cloudflare/ChatGPT-posible", "104.27": "Cloudflare/ChatGPT-posible",
    "172.64":  "Cloudflare",      "172.65": "Cloudflare",
    "172.66":  "Cloudflare",      "172.67": "Cloudflare",
    "188.114": "Cloudflare",      "190.93": "Cloudflare",
    "1.1":     "Cloudflare-DNS",  "1.0":    "Cloudflare-DNS",
    
    # ========== AWS (Amazon Web Services) / OpenAI / ChatGPT ==========
    "3.":      "AWS/OpenAI-posible",  "34.":    "AWS/OpenAI-posible",
    "54.":     "AWS/OpenAI-posible",  "52.":    "AWS (overlap Microsoft)",
    "18.":     "AWS",                 "13.":    "AWS (overlap Microsoft)",
    "35.":     "AWS (overlap Google-Cloud)",
    "44.":     "AWS",                 "107.":   "AWS",
    
    # ========== Akamai (CDN) ==========
    "23.":     "Akamai-CDN",     "184.":   "Akamai-CDN",
    "2.16":    "Akamai-CDN",     "2.17":   "Akamai-CDN",
    "2.18":    "Akamai-CDN",     "2.19":   "Akamai-CDN",
    "2.20":    "Akamai-CDN",     "2.21":   "Akamai-CDN",
    "96.16":   "Akamai-CDN",     "104.64": "Akamai-CDN",
    
    # ========== Netflix ==========
    "185.2":   "Netflix",        "198.45":  "Netflix",
    "198.38":  "Netflix",        "45.57":   "Netflix",
    "23.246":  "Netflix-Akamai", "69.53":   "Netflix",
    
    # ========== Spotify ==========
    "35.186":  "Spotify/Google-Cloud", "104.154": "Spotify",
    "35.186":  "Spotify",       "104.199": "Spotify",
    
    # ========== TikTok / ByteDance ==========
    "162.":    "TikTok/ByteDance", "77.91":   "TikTok/ByteDance",
    "161.":    "TikTok/ByteDance", "118.24":  "TikTok/ByteDance",
    
    # ========== Twitter / X ==========
    "104.244": "Twitter/X",      "199.16":  "Twitter/X",
    "199.59":  "Twitter/X",      "192.133": "Twitter/X",
    
    # ========== Reddit ==========
    "151.101": "Reddit",         "199.232": "Reddit",
    
    # ========== Zoom ==========
    "3.80":    "Zoom/AWS",       "3.96":    "Zoom/AWS",
    "170.114": "Zoom",           "147.124": "Zoom",
    
    # ========== Discord ==========
    "162.159": "Discord/Cloudflare", "66.22": "Discord",
    
    # ========== Dropbox ==========
    "162.125": "Dropbox",        "108.160": "Dropbox",
    
    # ========== LinkedIn ==========
    "108.174": "LinkedIn",       "13.107":  "LinkedIn/Microsoft",
    
    # ========== Wikipedia / Wikimedia ==========
    "185.15":  "Wikipedia",      "198.35":  "Wikipedia",
    "208.80":  "Wikipedia",
    
    # ========== Apple (iCloud / App Store / Apple Music) ==========
    "17.":     "Apple",          "1.179":   "Apple",
    "17.253":  "Apple",          "17.248":  "Apple",
    
    # ========== Steam (Valve) ==========
    "155.133": "Steam/Valve",    "208.78":  "Steam/Valve",
    "104.96":  "Steam/Akamai",
    
    # ========== Twitch ==========
    "151.101": "Twitch/Fastly",  "199.9":   "Twitch",
    "185.42":  "Twitch",
    
    # ========== Epic Games ==========
    "23.32":   "Epic-Games",     "34.207":  "Epic-Games/AWS",
}



def inferir_app(ip: str) -> str:
    """Intenta identificar la aplicación/servicio asociado a una IP por prefijo."""
    if not ip or ip == "N/A":
        return "Desconocido"
    for prefijo, app in PREFIJOS_APP.items():
        if ip.startswith(prefijo):
            return app
    return "Otro"


# Valores genéricos de la columna Sitio/App Destino que NO son sitios reales.
# Cuando app_inferida tiene uno de estos valores, se prefiere la inferencia por IP.
_GENERICO_SITIO = {
    "HTTPS", "HTTP", "DNS", "mDNS", "NetBIOS-NS", "NetBIOS-DGM", "NetBIOS-SSN",
    "LLMNR", "DHCP-Server", "DHCP-Client", "SMB/CIFS", "NTP", "SNMP", "SNMP-Trap",
    "UPnP/SSDP", "SSH", "FTP", "Telnet", "RDP", "SMTP", "SMTP-SSL", "SMTP-Envio",
    "POP3", "POP3-SSL", "IMAP", "IMAP-SSL", "HTTP-Alt", "HTTPS-Alt",
    "WinRM-HTTP", "WinRM-HTTPS", "Syslog", "PPTP-VPN", "OpenVPN", "IKE-VPN",
    "IPSec-VPN", "RTSP-Streaming", "RTMP-Streaming", "STUN/WebRTC", "STUN/TURN",
    "MySQL", "PostgreSQL", "SQL-Server", "MongoDB", "Redis",
    "Red-Local", "N/A", "Otro", "Desconocido", "",
}


def es_sitio_real(s) -> bool:
    """Devuelve True solo si s es un dominio real (p.ej. youtube.com, api2.cursor.sh).
    Descarta nombres de servicio genéricos y puertos sueltos.
    """
    if not isinstance(s, str):
        return False
    s = s.strip()
    if not s or s in _GENERICO_SITIO:
        return False
    if s.startswith("Puerto ") or s.startswith("Equipo-"):
        return False
    # Debe tener al menos un punto (formato dominio)
    partes = s.split(".")
    if len(partes) < 2:
        return False
    # No puede ser una IP pura (todos los segmentos numéricos)
    if all(p.isdigit() for p in partes):
        return False
    return True


PUERTOS_SERVICIO = {
    20: "FTP-datos", 21: "FTP-control", 22: "SSH", 23: "Telnet",
    25: "SMTP", 53: "DNS", 67: "DHCP", 68: "DHCP",
    80: "HTTP", 110: "POP3", 143: "IMAP", 443: "HTTPS",
    993: "IMAP-SSL", 995: "POP3-SSL", 3389: "RDP", 8080: "HTTP-alt",
    8443: "HTTPS-alt", 5353: "mDNS", 1900: "SSDP",
}


def categorizar_servicio(puerto):
    """Devuelve la categoría de servicio según el número de puerto destino."""
    if pd.isna(puerto):
        return "Desconocido"
    p = int(puerto)
    if p in PUERTOS_SERVICIO:
        return PUERTOS_SERVICIO[p]
    if p < 1024:
        return "Sistema-conocido"
    if p < 49152:
        return "Registrado"
    return "Dinamico-privado"


def categorizar_rango_puerto(puerto):
    """Devuelve el rango del puerto: conocido, registrado o dinámico."""
    if pd.isna(puerto):
        return "Desconocido"
    p = int(puerto)
    if p < 1024:
        return "Conocido"
    if p < 49152:
        return "Registrado"
    return "Dinamico"


def _limpiar_un_csv(ruta: str, verbose: bool) -> pd.DataFrame:
    """
    Lee y limpia UN archivo CSV.  Devuelve un DataFrame con columnas renombradas
    y filtradas pero SIN codificación/escalado (eso se hace al final sobre el conjunto
    combinado para que el LabelEncoder y el scaler ven todas las categorías juntas).
    """
    df = pd.read_csv(ruta)
    original = len(df)
    if verbose:
        print(f"  ↳ {original:,} registros leídos desde '{os.path.basename(ruta)}'")

    # Renombrado de columnas (tolerante: solo renombra las que existan)
    df.columns = [c.strip() for c in df.columns]
    rename_map = {
        "IP Origen":              "ip_origen",
        "IP Destino":             "ip_destino",
        "Puerto Ori":             "puerto_origen",
        "Puerto Des":             "puerto_destino",
        "Protocolo":              "protocolo",
        "Longitud":               "longitud",
        "Hora Captura":           "hora_captura",
        "SSID":                   "ssid",
        # v2.0 / v3.0: columnas con nombres reales de dispositivos y sitios
        "Dispositivo Local":      "dispositivo_local",
        "Nombre de Dispositivos": "dispositivo_local",   # v3.0
        "Sitio/App Destino":      "sitio_app_destino",
        "Hostname Remoto":        "hostname_remoto",
        # Legacy: columnas de versiones anteriores de NetworkMonitor
        "Equipo Local":           "equipo_local",
        "MAC Origen":             "mac_origen",
        "MAC Destino":            "mac_destino",
    }
    df = df.rename(columns={k: v for k, v in rename_map.items() if k in df.columns})

    # Columna que identifica el archivo de origen
    df["origen_csv"] = os.path.basename(ruta)

    # Eliminar duplicados exactos
    df = df.drop_duplicates()

    # Conservar solo filas con IPs, puertos y protocolo completos
    antes = len(df)
    required = ["ip_origen", "ip_destino", "puerto_origen", "puerto_destino", "protocolo"]
    df = df.dropna(subset=[c for c in required if c in df.columns])
    df = df[df["ip_origen"].astype(str).str.strip() != ""]
    df = df[df["ip_destino"].astype(str).str.strip() != ""]
    df = df[df["protocolo"].isin(["TCP", "UDP"])]
    if verbose and antes - len(df) > 0:
        print(f"     Eliminadas {antes - len(df):,} filas incompletas/no TCP-UDP")

    # Conversión de tipos
    df["puerto_origen"]  = pd.to_numeric(df["puerto_origen"],  errors="coerce").astype("Int64")
    df["puerto_destino"] = pd.to_numeric(df["puerto_destino"], errors="coerce").astype("Int64")
    df["longitud"]       = pd.to_numeric(df["longitud"],       errors="coerce")
    antes = len(df)
    df = df.dropna(subset=["puerto_origen", "puerto_destino", "longitud"])
    if verbose and antes - len(df) > 0:
        print(f"     Eliminadas {antes - len(df):,} filas con valores numéricos inválidos")

    return df


def _detectar_archivos_csv() -> list:
    """
    Devuelve la lista de rutas de CSV a usar siguiendo la prioridad:
      1. captura_red.csv (archivo clásico sin fecha)
      2. captura_red_YYYY-MM-DD.csv (uno o varios)
    Si no hay ninguno, lanza FileNotFoundError con un mensaje descriptivo.
    """
    # Prioridad 1: archivo clásico
    if os.path.isfile(CSV_PATH):
        return [CSV_PATH]

    # Prioridad 2: archivos con fecha en el nombre
    patron = os.path.join(_RAIZ, "captura_red_*.csv")
    encontrados = sorted(glob.glob(patron))
    if encontrados:
        return encontrados

    raise FileNotFoundError(
        "\n[ERROR] No se encontró ningún archivo CSV de captura.\n"
        "  Opciones:\n"
        "    1. Genera y exporta 'captura_red.csv' desde el programa Java.\n"
        "    2. Genera y exporta 'captura_red_YYYY-MM-DD.csv' (con la fecha del día).\n"
        f"  El análisis busca archivos en: {os.path.abspath(_RAIZ)}\n"
    )


def cargar_datos(ruta_csv: str = None, verbose: bool = True) -> pd.DataFrame:
    """
    Carga y limpia los CSV de captura de red.

    Parámetros
    ----------
    ruta_csv : str | None
        Ruta explícita a un archivo CSV.  Si es None (por defecto), se detectan
        automáticamente los archivos disponibles en la raíz del proyecto.
    verbose : bool
        Si True, imprime un resumen del proceso de limpieza.

    Devuelve
    -------
    pd.DataFrame combinado con columnas originales más columnas derivadas.
    """

    # --- Determinar qué archivos leer ---
    if ruta_csv is not None:
        rutas = [ruta_csv]
    else:
        rutas = _detectar_archivos_csv()

    if verbose:
        print(f"[carga] {len(rutas)} archivo(s) CSV detectado(s):")
        for r in rutas:
            print(f"        {os.path.basename(r)}")

    # --- Leer y limpiar cada archivo ---
    partes = [_limpiar_un_csv(r, verbose) for r in rutas]

    # --- Combinar ---
    df = pd.concat(partes, ignore_index=True)
    if verbose and len(rutas) > 1:
        print(f"[carga] Dataset combinado: {len(df):,} registros totales de {len(rutas)} archivos")

    # --- Conversión de hora a datetime ---
    # Se usa la fecha embebida en el nombre del archivo si está disponible,
    # o bien una fecha genérica para análisis horario.
    def _extraer_fecha(origen: str) -> str:
        partes_nombre = os.path.splitext(origen)[0].split("_")
        # captura_red_2026-03-04 → último trozo es la fecha
        if len(partes_nombre) >= 3:
            posible_fecha = partes_nombre[-1]
            try:
                pd.to_datetime(posible_fecha)
                return posible_fecha
            except Exception:
                pass
        return "2026-01-01"

    if "origen_csv" in df.columns:
        df["fecha_base"] = df["origen_csv"].apply(_extraer_fecha)
    else:
        df["fecha_base"] = "2026-01-01"

    # Compatibilidad:
    # - CSV antiguo: "Hora Captura" = HH:mm:ss
    # - CSV nuevo:   "Hora Captura" = yyyy-MM-dd HH:mm:ss
    # Primero intentamos parseo directo; si falla, usamos fecha_base + hora.
    hora_raw = df["hora_captura"].astype(str).str.strip()
    # utc=True evita el error "Mixed timezones" cuando se combinan CSVs de varios días
    # que pueden tener timestamps con y sin zona horaria.
    dt_directo = pd.to_datetime(hora_raw, errors="coerce", utc=True)
    dt_con_fecha = pd.to_datetime(df["fecha_base"] + " " + hora_raw, errors="coerce", utc=True)
    df["datetime_captura"] = dt_directo.fillna(dt_con_fecha)
    # tz_convert(None) quita la zona horaria UTC dejando la hora como valor naive
    # (equivalente a "ignorar" la zona y quedarse con la hora local de captura)
    df["datetime_captura"] = df["datetime_captura"].dt.tz_convert(None)
    df["hora"] = df["datetime_captura"].dt.hour

    # --- Columnas derivadas ---
    df["servicio"]         = df["puerto_destino"].apply(categorizar_servicio)
    df["rango_puerto_dst"] = df["puerto_destino"].apply(categorizar_rango_puerto)
    df["rango_puerto_src"] = df["puerto_origen"].apply(categorizar_rango_puerto)

    # app_inferida: solo conserva el valor de sitio_app_destino si es un dominio
    # real (ej. "youtube.com", "api2.cursor.sh"). Valores genéricos como "HTTPS",
    # "mDNS", "NetBIOS-NS", "Puerto 443" se descartan y se usa la heurística por IP.
    if "sitio_app_destino" in df.columns:
        # También renombrar columna nueva "Nombre de Dispositivos" si viene del CSV v3
        if "Nombre de Dispositivos" in df.columns and "dispositivo_local" not in df.columns:
            df = df.rename(columns={"Nombre de Dispositivos": "dispositivo_local"})
        df["app_inferida"] = df["sitio_app_destino"].apply(
            lambda s: s.strip() if es_sitio_real(s) else None
        ).fillna(df["ip_destino"].apply(inferir_app))
    else:
        # CSV antiguo sin columna SNI: usamos la heurística de prefijo de IP
        df["app_inferida"] = df["ip_destino"].apply(inferir_app)

    # --- Codificación de variables categóricas ---
    le_protocolo = LabelEncoder()
    df["protocolo_enc"] = le_protocolo.fit_transform(df["protocolo"])

    le_servicio = LabelEncoder()
    df["servicio_enc"] = le_servicio.fit_transform(df["servicio"])

    # --- Normalización ---
    scaler = StandardScaler()
    cols_escalar = ["longitud", "puerto_origen", "puerto_destino"]
    df[["longitud_scaled", "po_scaled", "pd_scaled"]] = scaler.fit_transform(
        df[cols_escalar].astype(float)
    )

    if verbose:
        print(f"[limpieza] Dataset final: {len(df):,} registros")
        print(f"[limpieza] Distribución de protocolos:\n{df['protocolo'].value_counts().to_string()}")
        print(f"[limpieza] Top 5 servicios:\n{df['servicio'].value_counts().head().to_string()}")
        print(f"[limpieza] Top 5 apps inferidas:\n{df['app_inferida'].value_counts().head().to_string()}")

    return df


if __name__ == "__main__":
    df = cargar_datos(verbose=True)
    print("\nPrimeras filas del dataset limpio:")
    print(df.head(3).to_string())
