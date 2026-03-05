"""
limpieza_datos.py
Carga, limpia y prepara el CSV de captura de red para análisis.
Exporta la función cargar_datos() que devuelve un DataFrame listo para usar.
"""

import os
import pandas as pd
import numpy as np
from sklearn.preprocessing import LabelEncoder, StandardScaler

CSV_PATH = os.path.join(os.path.dirname(__file__), "..", "captura_red.csv")

# Heurística por prefijo de IP para identificar servicios/aplicaciones conocidas.
# Nota: es una aproximación; la IP real puede variar según CDN o región.
PREFIJOS_APP = {
    # Google / YouTube
    "142.250": "Google/YouTube", "142.251": "Google/YouTube",
    "172.217": "Google/YouTube", "216.58":  "Google/YouTube",
    "74.125":  "Google/YouTube", "64.233":  "Google/YouTube",
    # Meta (Facebook / Instagram / WhatsApp)
    "157.240": "Meta (Facebook/Instagram)", "179.60": "Meta (Facebook/Instagram)",
    "31.13":   "Meta (Facebook/Instagram)", "163.70": "Meta (Facebook/Instagram)",
    # Microsoft / Azure / Teams
    "52.":     "Microsoft/Azure",  "20.":    "Microsoft/Azure",
    "40.":     "Microsoft/Azure",  "13.":    "Microsoft/Azure",
    # Cloudflare (ChatGPT usa Cloudflare, también CDN general)
    "104.16":  "Cloudflare/ChatGPT-posible", "104.17": "Cloudflare/ChatGPT-posible",
    "104.18":  "Cloudflare/ChatGPT-posible", "104.19": "Cloudflare/ChatGPT-posible",
    "1.1":     "Cloudflare-DNS",
    # OpenAI / ChatGPT
    "3.":      "AWS/OpenAI-posible", "34.": "AWS/OpenAI-posible",
    "54.":     "AWS/OpenAI-posible",
    # Akamai (CDN)
    "23.":     "Akamai-CDN", "184.": "Akamai-CDN",
    # Netflix
    "185.2":   "Netflix", "198.45": "Netflix",
    # Spotify
    "35.186":  "Spotify/Google-Cloud",
    # TikTok / ByteDance
    "162.":    "TikTok/ByteDance-posible",
}


def inferir_app(ip: str) -> str:
    """Intenta identificar la aplicación/servicio asociado a una IP por prefijo."""
    if not ip or ip == "N/A":
        return "Desconocido"
    for prefijo, app in PREFIJOS_APP.items():
        if ip.startswith(prefijo):
            return app
    return "Otro"


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


def cargar_datos(ruta_csv: str = CSV_PATH, verbose: bool = True) -> pd.DataFrame:
    """
    Carga y limpia el CSV de captura de red.

    Parámetros
    ----------
    ruta_csv : str
        Ruta al archivo CSV generado por NetworkMonitor.
    verbose : bool
        Si True, imprime un resumen del proceso de limpieza.

    Devuelve
    -------
    pd.DataFrame con columnas originales más columnas derivadas listas para análisis.
    """

    # --- Carga ---
    df = pd.read_csv(ruta_csv)
    original = len(df)
    if verbose:
        print(f"[carga] {original} registros leídos desde '{ruta_csv}'")
        print(f"[carga] Columnas: {list(df.columns)}")

    # --- Renombrado de columnas para facilitar el trabajo ---
    df.columns = [c.strip() for c in df.columns]
    df = df.rename(columns={
        "IP Origen":     "ip_origen",
        "IP Destino":    "ip_destino",
        "Puerto Ori":    "puerto_origen",
        "Puerto Des":    "puerto_destino",
        "Protocolo":     "protocolo",
        "Longitud":      "longitud",
        "Hora Captura":  "hora_captura",
        "Equipo Local":  "equipo_local",
        "SSID":          "ssid",
    })

    # --- Eliminación de duplicados exactos ---
    df = df.drop_duplicates()
    if verbose:
        print(f"[limpieza] Eliminados {original - len(df)} duplicados exactos")

    # --- Filtro: solo filas con IPs, puertos y protocolo completos ---
    antes = len(df)
    df = df.dropna(subset=["ip_origen", "ip_destino", "puerto_origen", "puerto_destino", "protocolo"])
    df = df[df["ip_origen"].str.strip() != ""]
    df = df[df["ip_destino"].str.strip() != ""]
    df = df[df["protocolo"].isin(["TCP", "UDP"])]
    if verbose:
        print(f"[limpieza] Eliminadas {antes - len(df)} filas incompletas/no TCP-UDP")

    # --- Conversión de tipos ---
    df["puerto_origen"]  = pd.to_numeric(df["puerto_origen"],  errors="coerce").astype("Int64")
    df["puerto_destino"] = pd.to_numeric(df["puerto_destino"], errors="coerce").astype("Int64")
    df["longitud"]       = pd.to_numeric(df["longitud"],       errors="coerce")

    # Eliminar filas donde la conversión dejó nulos en columnas numéricas críticas
    antes = len(df)
    df = df.dropna(subset=["puerto_origen", "puerto_destino", "longitud"])
    if verbose:
        print(f"[limpieza] Eliminadas {antes - len(df)} filas con valores numéricos inválidos")

    # --- Conversión de hora a datetime (se asume fecha de hoy para análisis temporal) ---
    fecha_base = "2026-03-04"
    df["datetime_captura"] = pd.to_datetime(
        fecha_base + " " + df["hora_captura"].astype(str), errors="coerce"
    )
    df["hora"] = df["datetime_captura"].dt.hour

    # --- Columnas derivadas ---
    df["servicio"]         = df["puerto_destino"].apply(categorizar_servicio)
    df["rango_puerto_dst"] = df["puerto_destino"].apply(categorizar_rango_puerto)
    df["rango_puerto_src"] = df["puerto_origen"].apply(categorizar_rango_puerto)

    # Heurística: inferir aplicación por IP destino (aproximación por prefijo)
    df["app_inferida"] = df["ip_destino"].apply(inferir_app)

    if verbose:
        print(f"[limpieza] Top 5 apps inferidas por IP destino:\n{df['app_inferida'].value_counts().head().to_string()}")

    # --- Codificación de variables categóricas ---
    le_protocolo = LabelEncoder()
    df["protocolo_enc"] = le_protocolo.fit_transform(df["protocolo"])   # TCP=1, UDP=0 (orden alfabético)

    le_servicio = LabelEncoder()
    df["servicio_enc"] = le_servicio.fit_transform(df["servicio"])

    # --- Normalización de características numéricas (útil para KNN y clustering) ---
    scaler = StandardScaler()
    cols_escalar = ["longitud", "puerto_origen", "puerto_destino"]
    df[["longitud_scaled", "po_scaled", "pd_scaled"]] = scaler.fit_transform(
        df[cols_escalar].astype(float)
    )

    if verbose:
        print(f"[limpieza] Dataset final: {len(df)} registros")
        print(f"[limpieza] Distribución de protocolos:\n{df['protocolo'].value_counts().to_string()}")
        print(f"[limpieza] Top 5 servicios:\n{df['servicio'].value_counts().head().to_string()}")

    return df


if __name__ == "__main__":
    df = cargar_datos(verbose=True)
    print("\nPrimeras filas del dataset limpio:")
    print(df.head(3).to_string())
