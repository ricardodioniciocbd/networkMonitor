# Evidencia — Análisis de Tráfico de Red con NetworkMonitor

## Flujo completo de la práctica

```
NetworkMonitor (Java + Pcap4J)
  ↓  captura en tiempo real (modo promiscuo, interfaz Wi-Fi)
Tabla tipo Wireshark en la interfaz Swing
  ↓  botón "Exportar CSV"
captura_red_YYYY-MM-DD.csv
  ↓  scripts Python (limpieza_datos.py, modelos.py, visualizacion.py)
Modelos de Data Science (KNN · K-Means · Regresión Logística)
  ↓
Gráficas PNG en analisis_python/resultados/
  ↓
CONCLUSIONES.md — Reporte final
```

---

## 1. Programa Java NetworkMonitor (tipo Wireshark)

El programa fue desarrollado en **Apache NetBeans** usando **Java Swing** y la librería **Pcap4J**,
que actúa como puente entre Java y los controladores de red del sistema operativo
(Npcap en Windows o libpcap en Linux/macOS).

### Funcionalidades de la interfaz

| Componente | Descripción |
|---|---|
| Campo "Equipo local" | Nombre del equipo, resuelto automáticamente con `InetAddress.getLocalHost().getHostName()` |
| Campo "SSID Wi-Fi" | Nombre de la red inalámbrica (editable manualmente) |
| Filtro "Protocolo" | ComboBox: Todos / TCP / UDP — filtra las filas visibles en la tabla |
| Filtro "IP" | Filtra filas donde la IP origen o destino contiene el texto ingresado |
| Filtro "Puerto destino" | Filtra filas por número de puerto destino exacto |
| Botón "Aplicar filtros" | Aplica los filtros seleccionados sobre la tabla (usa `TableRowSorter`) |
| Botón "Limpiar filtros" | Elimina todos los filtros y muestra todas las filas |
| Botón "Iniciar Captura" | Abre diálogo para seleccionar la interfaz de red y comienza la captura |
| Botón "Detener Captura" | Detiene la captura en curso |
| Botón "Exportar CSV" | Exporta todos los paquetes capturados a `captura_red_YYYY-MM-DD.csv` |

### Columnas capturadas en la tabla

| Columna | Descripción |
|---|---|
| IP Origen | Dirección IP del equipo que envía el paquete |
| IP Destino | Dirección IP del equipo que recibe el paquete |
| MAC Origen | Dirección MAC de la tarjeta de red origen (cabecera Ethernet) |
| MAC Destino | Dirección MAC de la tarjeta de red destino |
| Puerto Ori | Puerto de origen (TCP/UDP) |
| Puerto Des | Puerto de destino (TCP/UDP) |
| Protocolo | TCP o UDP |
| Longitud | Tamaño del paquete en bytes |
| Hora Captura | Hora exacta en formato HH:mm:ss |
| Equipo Local | Nombre del equipo donde se realiza la captura |
| SSID | Nombre de la red Wi-Fi |

> **Nota sobre direcciones MAC:** En redes Wi-Fi con puntos de acceso intermedios,
> la MAC capturada puede corresponder al punto de acceso (AP) y no directamente al
> dispositivo final. Este es un comportamiento esperado en modo promiscuo sobre Wi-Fi.

---

## 2. Ejemplo de datos capturados (CSV generado por el programa Java)

El siguiente fragmento muestra las primeras 15 filas **reales** del archivo `captura_red_2026-03-05.csv`
exportado desde el botón **"Exportar CSV"** del programa NetworkMonitor:

```csv
IP Origen,IP Destino,MAC Origen,MAC Destino,Puerto Ori,Puerto Des,Protocolo,Longitud,Hora Captura,Dispositivo Local,Sitio/App Destino,SSID
10.20.53.78,3.143.106.22,d4:d8:53:5d:25:cd,76:ac:1e:a3:16:a0,58487,443,TCP,119,2026-03-05 20:26:54,10.20.53.78,,N/A
10.20.53.78,52.112.53.52,d4:d8:53:5d:25:cd,76:ac:1e:a3:16:a0,51404,443,TCP,104,2026-03-05 20:26:54,DESKTOP-G6APLIR,,N/A
104.18.18.125,10.20.53.78,76:ac:1e:a3:16:a0,d4:d8:53:5d:25:cd,443,51001,TCP,54,2026-03-05 20:26:54,DESKTOP-G6APLIR,,N/A
10.20.53.33,224.0.0.251,76:ac:1e:a3:16:a0,01:00:5e:00:00:fb,5353,5353,UDP,119,2026-03-05 20:26:54,10.20.53.33,,N/A
104.18.18.125,10.20.53.78,76:ac:1e:a3:16:a0,d4:d8:53:5d:25:cd,443,51001,TCP,135,2026-03-05 20:26:54,DESKTOP-G6APLIR,,N/A
10.20.53.78,10.20.53.33,d4:d8:53:5d:25:cd,76:ac:1e:a3:16:a0,55898,53,UDP,84,2026-03-05 20:26:55,DESKTOP-G6APLIR,33.53.20.10.in-addr.arpa,N/A
10.20.53.78,224.0.0.251,d4:d8:53:5d:25:cd,01:00:5e:00:00:fb,5353,5353,UDP,84,2026-03-05 20:26:55,DESKTOP-G6APLIR,,N/A
10.20.53.78,104.18.18.125,d4:d8:53:5d:25:cd,76:ac:1e:a3:16:a0,51001,443,TCP,54,2026-03-05 20:26:55,DESKTOP-G6APLIR,,N/A
10.20.53.78,10.20.53.33,d4:d8:53:5d:25:cd,76:ac:1e:a3:16:a0,65224,53,UDP,94,2026-03-05 20:26:55,DESKTOP-G6APLIR,p2p-lax1.discovery.steamserver.net,N/A
10.20.53.78,10.20.53.33,d4:d8:53:5d:25:cd,76:ac:1e:a3:16:a0,55898,53,UDP,84,2026-03-05 20:26:55,DESKTOP-G6APLIR,33.53.20.10.in-addr.arpa,N/A
10.20.53.78,10.20.53.33,d4:d8:53:5d:25:cd,76:ac:1e:a3:16:a0,65224,53,UDP,94,2026-03-05 20:26:55,DESKTOP-G6APLIR,p2p-lax1.discovery.steamserver.net,N/A
10.20.53.78,52.112.53.52,d4:d8:53:5d:25:cd,76:ac:1e:a3:16:a0,51404,443,TCP,104,2026-03-05 20:26:55,DESKTOP-G6APLIR,,N/A
10.20.53.78,104.18.18.125,d4:d8:53:5d:25:cd,76:ac:1e:a3:16:a0,51001,443,TCP,810,2026-03-05 20:26:55,DESKTOP-G6APLIR,,N/A
10.20.53.78,3.143.106.22,d4:d8:53:5d:25:cd,76:ac:1e:a3:16:a0,57883,443,TCP,119,2026-03-05 20:26:55,DESKTOP-G6APLIR,,N/A
10.20.53.78,3.143.106.22,d4:d8:53:5d:25:cd,76:ac:1e:a3:16:a0,57428,443,TCP,119,2026-03-05 20:26:55,DESKTOP-G6APLIR,,N/A
```

### Análisis del fragmento CSV

**Observaciones:**
- **Columnas presentes:** Incluye IP origen/destino, MAC origen/destino, puertos, protocolo, longitud, fecha+hora completa, dispositivo local, sitio/app destino (cuando es DNS), SSID
- **Tráfico TCP dominante:** La mayoría de las filas muestran puerto 443 (HTTPS)
- **Tráfico UDP para DNS:** Filas con puerto 53 muestran consultas DNS (ej. `p2p-lax1.discovery.steamserver.net`, resolución inversa `in-addr.arpa`)
- **Multicast mDNS:** IP destino `224.0.0.251` es multicast DNS (puerto 5353)
- **IPs externas frecuentes:**
  - `3.143.106.22` → Rango de AWS (posiblemente servicios en la nube)
  - `52.112.53.52` → Microsoft Azure (posiblemente Teams/Office365)
  - `104.18.18.125` → Cloudflare CDN
- **MACs capturadas:** `d4:d8:53:5d:25:cd` (equipo local), `76:ac:1e:a3:16:a0` (probablemente punto de acceso Wi-Fi)
- **Hora de captura:** 2026-03-05 entre 20:26:54 y 20:26:55 (todos los paquetes en un intervalo de 1 segundo)

> El archivo CSV completo con los datos de los 3 días de captura se adjunta
> junto con este reporte. Los nombres de archivo siguen el formato:
> `captura_red_YYYY-MM-DD.csv` (uno por día de monitoreo).

---

## 2.1 Screenshots del programa Java NetworkMonitor

### Screenshot 1: Interfaz principal con captura en curso

**[INSTRUCCIÓN: Insertar aquí screenshot del programa Java mostrando:]**
- Ventana principal con título "Network Monitor - Java Swing"
- Tabla poblada con múltiples filas de paquetes capturados
- Columnas visibles: IP Origen, IP Destino, MAC Origen, MAC Destino, Puerto Ori, Puerto Des, Protocolo, Longitud, Hora Captura, Dispositivo Local, Sitio/App Destino, SSID
- Panel superior con campos "Equipo local" y "SSID Wi-Fi"
- Panel de filtros con combo "Protocolo", campos de texto para IP y Puerto
- Botones: "Iniciar Captura", "Detener Captura", "Exportar CSV"
- Contador visible: "Paquetes: 685 / 33333"

**Descripción de la interfaz:**
- La tabla muestra paquetes capturados en tiempo real con scroll para navegar por cientos/miles de registros.
- Los filtros permiten al usuario mostrar solo paquetes que cumplan ciertos criterios (ej. solo TCP, solo puerto 443, solo IPs que contengan "10.20").
- El contador indica que se han capturado 685 paquetes del límite de 33,333 configurado (para generar ~100k registros en 3 días).
- Las columnas MAC muestran las direcciones físicas de las tarjetas de red origen y destino.

### Screenshot 2: Filtros aplicados

**[INSTRUCCIÓN: Insertar aquí screenshot mostrando:]**
- Filtro "Protocolo" seleccionado en "TCP"
- Filtro "Puerto Des" con valor "443"
- Tabla mostrando SOLO filas que cumplan los filtros
- Resto de filas ocultas (no eliminadas del modelo, solo no visibles)

**Descripción del filtrado:**
- Al aplicar filtros, la tabla muestra solo los paquetes que cumplen las condiciones.
- Esto facilita el análisis de tráfico específico (ej. solo HTTPS, solo DNS, solo hacia una IP concreta).
- El botón "Limpiar filtros" restaura la vista completa de todos los paquetes capturados.

### Screenshot 3: Diálogo de exportación exitosa

**[INSTRUCCIÓN: Insertar aquí screenshot del JOptionPane mostrando:]**
- Mensaje: "CSV generado: captura_red_2026-03-05.csv"
- Botón "OK"

**Descripción de la exportación:**
- Al pulsar "Exportar CSV", el programa genera el archivo con nombre automático que incluye la fecha del día.
- El archivo se guarda en el directorio raíz del proyecto.
- TODAS las filas capturadas se exportan (no solo las visibles tras filtros).
- El CSV incluye encabezados en la primera fila para compatibilidad con herramientas de análisis.

---

## 3. Gráficas generadas desde el CSV

Las siguientes gráficas fueron generadas por los scripts Python a partir del CSV
exportado por NetworkMonitor. Se encuentran en la carpeta `analisis_python/resultados/`.

| Archivo | Descripción | Tipo |
|---------|-------------|------|
| `01_tcp_vs_udp.png` | Comparativa de volumen de tráfico TCP vs UDP | Barras + Pie chart |
| `02_dispersion_tamano_hora.png` | Tamaño de paquetes (bytes) vs hora del día, por protocolo | Dispersión |
| `03_puertos_frecuentes.png` | Top 15 puertos destino más utilizados en la red | Barras horizontales |
| `04_clusters_equipos.png` | Clustering K-Means de equipos/IPs (Intensivo / Moderado / Ligero) | Scatter PCA 2D |
| `05_trafico_por_hora.png` | Volumen de paquetes por hora del día (TCP y UDP) | Líneas |
| `06_confusion_knn.png` | Matriz de confusión del modelo KNN de clasificación de tráfico | Heatmap |
| `07_prediccion_protocolo_hora.png` | Probabilidad de protocolo predominante por hora (análisis predictivo) | Áreas |
| `08_servicios_frecuentes.png` | Categorías de servicio de red más frecuentes (por puerto destino) | Barras |
| `09_apps_inferidas.png` | Aplicaciones/servicios inferidos por IP destino (YouTube, Meta, Cloudflare, etc.) | Barras + Pie |

> Todas las gráficas se obtienen a partir del dataset capturado por NetworkMonitor (Java)
> y exportado en formato CSV. El análisis se realiza en Python con `pandas`, `scikit-learn`,
> `matplotlib` y `seaborn`.

---

## 4. Resultados del análisis (resumen)

| Métrica | Valor |
|---|---|
| Total de paquetes capturados (limpios) | 1,097 |
| Protocolo predominante | TCP (~88%) |
| Puerto más utilizado | 443 (HTTPS) |
| IP más activa (origen) | 10.20.53.78 (equipo local) |
| Accuracy KNN (clasificación de tráfico) | 99.55% |
| Silhouette score K-Means (clustering equipos) | 0.60 |
| Accuracy predicción protocolo por hora | 88.64% |

---

## 5. Códigos entregados

| Archivo | Descripción |
|---|---|
| `src/main/java/.../NetworkMonitor.java` | Programa Java principal (captura, tabla, filtros, exportación CSV) |
| `analisis_python/limpieza_datos.py` | Carga, limpieza, codificación e ingeniería de características |
| `analisis_python/modelos.py` | Modelos KNN, K-Means y regresión logística |
| `analisis_python/visualizacion.py` | Generación de las 9 gráficas |
| `analisis_python/main.py` | Script principal que ejecuta todo el flujo sin Jupyter |
| `analisis_python/analisis_trafico.ipynb` | Notebook Jupyter (opcional) con todo el análisis por secciones |
| `analisis_python/CONCLUSIONES.md` | Reporte de conclusiones con interpretación de resultados |
| `analisis_python/requirements.txt` | Dependencias Python necesarias |
| `captura_red_YYYY-MM-DD.csv` (×3) | Archivos CSV de los 3 días de monitoreo |

---

## 6. Instrucciones de reproducción

### Ejecutar el programa Java
1. Abrir el proyecto en Apache NetBeans.
2. Asegurarse de tener Npcap instalado en Windows.
3. Agregar la librería Pcap4J al proyecto.
4. Ejecutar `NetworkMonitor.java`.
5. Seleccionar la interfaz Wi-Fi en el diálogo.
6. Capturar durante varias horas y exportar CSV con el botón "Exportar CSV".

### Ejecutar el análisis Python
```powershell
cd analisis_python
py -m pip install -r requirements.txt
py main.py
```
Las gráficas se guardan en `analisis_python/resultados/`.
