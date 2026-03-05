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

El siguiente fragmento muestra las primeras filas del archivo `captura_red.csv`
exportado desde el botón **"Exportar CSV"** del programa NetworkMonitor:

```
IP Origen,IP Destino,Puerto Ori,Puerto Des,Protocolo,Longitud,Hora Captura,Equipo Local,SSID
10.20.53.78,3.227.113.226,61930,443,TCP,55,21:31:02,DESKTOP-G6APLIR,N/A
10.20.53.78,98.95.185.24,57223,443,TCP,93,21:31:02,DESKTOP-G6APLIR,N/A
3.227.113.226,10.20.53.78,443,61930,TCP,66,21:31:02,DESKTOP-G6APLIR,N/A
98.95.185.24,10.20.53.78,443,57223,TCP,93,21:31:02,DESKTOP-G6APLIR,N/A
10.20.53.78,98.95.185.24,57223,443,TCP,54,21:31:02,DESKTOP-G6APLIR,N/A
10.20.53.78,3.227.113.226,61930,443,TCP,55,21:31:03,DESKTOP-G6APLIR,N/A
10.20.53.78,10.20.53.33,52400,53,UDP,105,21:31:03,DESKTOP-G6APLIR,N/A
```

> El archivo CSV con los datos completos de los 3 días de captura se adjunta
> junto con este reporte. Los nombres de archivo siguen el formato:
> `captura_red_YYYY-MM-DD.csv` (uno por día de monitoreo).

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
