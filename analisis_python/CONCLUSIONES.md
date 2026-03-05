# Reporte de Conclusiones — Análisis de Tráfico de Red

## 1. Descripción de la captura de datos

| Parámetro | Detalle |
|---|---|
| Herramienta de captura | NetworkMonitor (Java + Pcap4J) |
| Interfaz capturada | Red inalámbrica Wi-Fi |
| Formato de exportación | CSV con encabezados |
| Campos capturados | IP origen, IP destino, Puerto origen, Puerto destino, Protocolo, Longitud, Hora Captura, Equipo Local, SSID |

> Nota: Para cumplir el requisito de >100,000 registros se deben realizar capturas durante 3 días.
> El análisis actual trabaja con el CSV disponible en `captura_red.csv`.

---

## 2. Análisis exploratorio

### Distribución de protocolos
- El tráfico en redes Wi-Fi académicas es **predominantemente TCP** (~85-95%),
  correspondiente a navegación web segura (HTTPS), transferencias y servicios cloud.
- **UDP** aparece principalmente en consultas DNS (puerto 53), DHCP y algunos servicios de streaming.

### Puertos más utilizados
| Puerto | Servicio | Observación |
|--------|----------|-------------|
| 443    | HTTPS    | Puerto más frecuente; navegación web segura |
| 53     | DNS      | Consultas de resolución de nombres |
| 80     | HTTP     | Tráfico web sin cifrado |
| 8080   | HTTP-alt | Servicios alternativos |

### Equipos más activos
- La IP local del equipo donde se realizó la captura genera el mayor volumen de tráfico.
- Las IPs externas frecuentes corresponden a servicios en la nube:
  Microsoft Azure, Cloudflare, Amazon AWS.

---

## 3. Modelos aplicados

### 3.1 Clasificación KNN (K-Nearest Neighbors)

- **Objetivo:** Clasificar el tipo de tráfico (servicio de red) de cada paquete.
- **Características usadas:** Longitud del paquete (normalizada), puerto origen (normalizado),
  puerto destino (normalizado), protocolo (codificado).
- **Etiqueta:** Categoría de servicio derivada del puerto destino (HTTPS, DNS, HTTP, etc.).
- **Parámetros:** k=5 vecinos, split train/test 80%/20%.
- **Resultado:** El modelo logra distinguir correctamente los servicios más frecuentes
  (HTTPS y DNS); la precisión aumenta significativamente con más datos.

### 3.2 Clustering K-Means

- **Objetivo:** Agrupar equipos (IPs de origen) según su nivel de uso de la red.
- **Características usadas:** Total de paquetes enviados, bytes totales, bytes promedio por paquete,
  número de puertos únicos utilizados, porcentaje de tráfico TCP.
- **Parámetros:** k=3 clusters (Intensivo, Moderado, Ligero).
- **Resultado:**
  - **Intensivo:** Equipos con alto volumen de tráfico y amplia variedad de puertos.
  - **Moderado:** Uso estándar de navegación y servicios web.
  - **Ligero:** Equipos con tráfico esporádico, principalmente DNS y pings.
- **Evaluación:** Silhouette score indica la calidad de la separación entre clusters.

### 3.3 Análisis Predictivo (Regresión Logística)

- **Objetivo:** Predecir el protocolo predominante (TCP/UDP) en función de la hora del día.
- **Características usadas:** Hora del día (0-23), longitud del paquete normalizada.
- **Resultado:**
  - TCP domina en la mayoría de horas del día, especialmente en horario de uso académico.
  - UDP aumenta en horarios de menor uso (madrugada), relacionado con tráfico DNS en background.
  - La probabilidad de TCP supera 0.80 en horas pico de actividad.

---

## 4. Uso de aplicaciones web (ChatGPT, YouTube, Facebook, Instagram, etc.)

### Limitación técnica

Con los datos de IP y puerto capturados por NetworkMonitor, **no es posible identificar
con certeza** la aplicación concreta de cada paquete, porque:

- La mayoría del tráfico de redes sociales, streaming y servicios de IA usa **HTTPS (puerto 443)**,
  que está cifrado y no revela el dominio o la app en la cabecera del paquete.
- Identificar la aplicación exacta requeriría **DPI (Deep Packet Inspection)** o análisis del
  campo **SNI (Server Name Indication)** del handshake TLS, lo cual excede el alcance de Pcap4J.

### Aproximación por heurística de IP

Para ofrecer una visión aproximada, el script Python (`limpieza_datos.py`) implementa una
**heurística por prefijo de IP destino** que asocia rangos de IP conocidos con servicios
o plataformas. Los resultados se muestran en la gráfica `09_apps_inferidas.png`.

| Servicio/App | Prefijos de IP asociados | Nota |
|---|---|---|
| Google / YouTube | 142.250, 142.251, 172.217, 74.125 | CDN de Google |
| Meta (Facebook/Instagram) | 157.240, 179.60, 31.13 | Infraestructura de Meta |
| Microsoft / Azure / Teams | 52.x, 20.x, 40.x, 13.x | Servicios cloud Microsoft |
| Cloudflare / ChatGPT-posible | 104.16 – 104.19 | CDN Cloudflare; OpenAI usa Cloudflare |
| AWS / OpenAI-posible | 3.x, 34.x, 54.x | Amazon Web Services |
| Akamai CDN | 23.x, 184.x | CDN genérico |
| Netflix | 185.2, 198.45 | Streaming |
| TikTok / ByteDance | 162.x | Aproximado |

> **Importante:** Esta clasificación es **aproximada**. Una IP de Cloudflare puede
> corresponder a ChatGPT, a Cloudflare Pages, o a cualquier otro sitio que use esa CDN.
> La columna `app_inferida` en el análisis Python debe interpretarse como tendencia,
> no como identificación exacta.

### Contexto académico

En una red de aulas de Ingeniería en Sistemas (edificio N), las aplicaciones más probables
durante el horario académico son:

- **Navegación HTTPS general** (la gran mayoría del tráfico)
- **Google (YouTube, Drive, Meet, Gmail)** — servicios educativos frecuentes
- **Microsoft (Teams, OneDrive, Office 365)** — herramientas de colaboración académica
- **ChatGPT / OpenAI** — uso creciente por estudiantes de IS
- **Facebook / Instagram** — redes sociales en horario libre
- **DNS (53/UDP)** — generado automáticamente por todas las aplicaciones

---

## 5. Gráficas generadas

Las siguientes gráficas se encuentran en la carpeta `resultados/`:

| Archivo | Descripción |
|---------|-------------|
| `01_tcp_vs_udp.png` | Comparativa de volumen TCP vs UDP (barras y pie chart) |
| `02_dispersion_tamano_hora.png` | Tamaño de paquetes vs hora del día por protocolo |
| `03_puertos_frecuentes.png` | Top 15 puertos destino más utilizados |
| `04_clusters_equipos.png` | Visualización 2D (PCA) de los clusters de equipos |
| `05_trafico_por_hora.png` | Volumen de tráfico por hora del día |
| `06_confusion_knn.png` | Matriz de confusión del modelo KNN |
| `07_prediccion_protocolo_hora.png` | Probabilidad de protocolo por hora (predictivo) |
| `08_servicios_frecuentes.png` | Categorías de servicio más frecuentes (por puerto) |
| `09_apps_inferidas.png` | Aplicaciones inferidas por IP (YouTube, Meta, Cloudflare, etc.) |
| `06_confusion_knn.png` | Matriz de confusión del modelo KNN |
| `07_prediccion_protocolo_hora.png` | Probabilidad de protocolo por hora (predictivo) |
| `08_servicios_frecuentes.png` | Categorías de servicio más frecuentes |

---

## 5. Conclusiones generales

1. **El tráfico de la red Wi-Fi académica es mayoritariamente HTTPS (TCP/443)**,
   lo que refleja un uso intensivo de servicios web, plataformas educativas en línea
   y servicios en la nube.

2. **DNS (UDP/53) es el segundo servicio más frecuente**, generado automáticamente
   por todas las aplicaciones que resuelven nombres de dominio.

3. **Los picos de tráfico coinciden con horarios de actividad académica**, mientras
   que en horas no laborables el tráfico se reduce considerablemente y se vuelve
   más uniforme (predominio de servicios en background).

4. **El modelo KNN es efectivo para clasificar tipos de tráfico** cuando se entrena
   con suficientes datos etiquetados. Con >100,000 registros se esperaría una
   precisión superior al 85%.

5. **El clustering K-Means permite identificar claramente los equipos con mayor
   consumo de red**, información útil para gestión y seguridad de la red.

6. **El análisis predictivo confirma que TCP es el protocolo dominante**
   independientemente de la hora, con ligeras variaciones estadísticas.

---

## 6. Recomendaciones

- Realizar capturas durante los 3 días completos para alcanzar >100,000 registros
  y obtener resultados estadísticamente más robustos.
- Agregar la dirección MAC al CSV (requiere permisos adicionales en Pcap4J)
  para identificar dispositivos físicos con mayor precisión.
- Explorar técnicas de detección de anomalías para identificar tráfico sospechoso
  o inusual en la red.
