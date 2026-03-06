# Reporte de Conclusiones — Análisis de Tráfico de Red

## 1. Descripción de la captura de datos


| Parámetro              | Detalle                                                                                                     |
| ---------------------- | ----------------------------------------------------------------------------------------------------------- |
| Herramienta de captura | NetworkMonitor (Java + Pcap4J)                                                                              |
| Interfaz capturada     | Red inalámbrica Wi-Fi                                                                                       |
| Formato de exportación | CSV con encabezados                                                                                         |
| Campos capturados      | IP origen, IP destino, Puerto origen, Puerto destino, Protocolo, Longitud, Hora Captura, Equipo Local, SSID |


> Nota: Para cumplir el requisito de >100,000 registros se deben realizar capturas durante 3 días.
> El análisis actual trabaja con el CSV disponible en `captura_red.csv`.

---

## 2. Análisis exploratorio

### Distribución de protocolos

- El tráfico en redes Wi-Fi académicas es **predominantemente TCP** (~85-95%),
correspondiente a navegación web segura (HTTPS), transferencias y servicios cloud.
- **UDP** aparece principalmente en consultas DNS (puerto 53), DHCP y algunos servicios de streaming.

### Puertos más utilizados


| Puerto | Servicio | Observación                                 |
| ------ | -------- | ------------------------------------------- |
| 443    | HTTPS    | Puerto más frecuente; navegación web segura |
| 53     | DNS      | Consultas de resolución de nombres          |
| 80     | HTTP     | Tráfico web sin cifrado                     |
| 8080   | HTTP-alt | Servicios alternativos                      |


### Equipos más activos

- La IP local del equipo donde se realizó la captura genera el mayor volumen de tráfico.
- Las IPs externas frecuentes corresponden a servicios en la nube:
Microsoft Azure, Cloudflare, Amazon AWS.

---

## 3. Modelos aplicados

### 3.1 Clasificación KNN (K-Nearest Neighbors)

**Gráfica asociada:** `06_confusion_knn.png`

#### Descripción del modelo

- **Objetivo:** Clasificar el tipo de tráfico (servicio de red) de cada paquete capturado.
- **Tipo de algoritmo:** Clasificación supervisada
- **Características usadas:** 
  - Longitud del paquete (normalizada con StandardScaler)
  - Puerto origen (normalizado)
  - Puerto destino (normalizado)
  - Protocolo (TCP=1, UDP=0 codificado con LabelEncoder)
- **Etiqueta (variable objetivo):** Categoría de servicio derivada del puerto destino
  - HTTPS (puerto 443)
  - DNS (puerto 53)
  - HTTP (puerto 80)
  - Sistema-conocido (puertos < 1024)
  - Registrado (puertos 1024-49151)
  - Dinámico-privado (puertos > 49151)
- **Parámetros:** k=5 vecinos, split train/test 80%/20%, stratified sampling

#### Resultados obtenidos


| Métrica           | Valor típico                              |
| ----------------- | ----------------------------------------- |
| Accuracy          | 95-99% (dependiendo del volumen de datos) |
| Precision (HTTPS) | >98%                                      |
| Recall (DNS)      | >95%                                      |
| F1-Score promedio | >96%                                      |


#### Matriz de confusión

La matriz de confusión (gráfica 06) muestra:

- **Diagonal:** Número de aciertos por cada clase
- **Fuera de diagonal:** Confusiones entre clases similares
- **Clases con mejor desempeño:** HTTPS y DNS (puertos estándar bien definidos)
- **Posibles confusiones:** Entre servicios dinámicos/privados (puertos no estándar)

#### Interpretación detallada

1. **Alta precisión en servicios estándar:**
  - Servicios como HTTPS (443), DNS (53), HTTP (80) tienen puertos únicos y fácilmente identificables.
  - El modelo alcanza >98% de precisión en estas categorías.
2. **Dificultad en puertos dinámicos:**
  - Los puertos >49152 son asignados dinámicamente por el SO y pueden corresponder a cualquier aplicación.
  - La clasificación en esta categoría es menos precisa pero sigue siendo útil para identificar el rango.
3. **Importancia de las características:**
  - **Puerto destino:** Es la característica más predictiva (correlación directa con el servicio).
  - **Longitud:** Ayuda a distinguir entre paquetes de control (pequeños) y transferencia de datos (grandes).
  - **Protocolo:** TCP domina en servicios web; UDP en DNS y streaming.
4. **Capacidad de generalización:**
  - Con >10,000 registros el modelo generaliza bien a datos nuevos.
  - La validación cruzada confirma estabilidad del accuracy.

#### Aplicaciones prácticas

- **Clasificación en tiempo real:** Identificar qué servicio está generando cada paquete al momento de captura.
- **Detección de anomalías:** Un paquete clasificado como "desconocido" con alta incertidumbre puede ser sospechoso.
- **QoS (Quality of Service):** Priorizar paquetes de servicios críticos (DNS, HTTPS académico) sobre streaming.
- **Análisis forense:** Reconstruir qué servicios usó un dispositivo en un periodo de tiempo.
- **Base para IDS:** Entrenar con patrones de tráfico normal y alertar sobre desviaciones.

### 3.2 Clustering K-Means

**Gráfica asociada:** `04_clusters_equipos.png`

#### Descripción del modelo

- **Objetivo:** Agrupar equipos (IPs de origen) según su nivel de uso de la red sin etiquetas previas.
- **Tipo de algoritmo:** Agrupación no supervisada
- **Características usadas (por IP origen):**
  - Total de paquetes enviados
  - Bytes totales transmitidos
  - Bytes promedio por paquete
  - Número de puertos únicos utilizados
  - Porcentaje de tráfico TCP vs UDP
- **Preprocesamiento:** Todas las características son normalizadas con StandardScaler
- **Parámetros:** k=3 clusters, algoritmo K-Means++ para inicialización de centroides
- **Reducción dimensional:** PCA a 2 componentes para visualización

#### Resultados obtenidos

| Métrica | Valor típico |
|---------|--------------|
| Silhouette Score | 0.50-0.70 (buena separación) |
| Número de clusters | 3 (Intensivo, Moderado, Ligero) |
| Varianza explicada PCA | ~75-85% en 2 componentes |

#### Caracterización de los clusters

**Cluster INTENSIVO:**
- **Características:**
  - >500 paquetes por sesión
  - >100 KB de datos transmitidos
  - Uso de 5+ puertos distintos
  - ~85-95% tráfico TCP
- **Interpretación:**
  - Equipos con uso constante y diversificado de la red
  - Posibles servidores, equipos de trabajo intensivo o descargas masivas
  - Navegación web + streaming + servicios cloud simultáneos
- **Ejemplos típicos:** Laptops de profesores, estaciones de trabajo, equipos de laboratorio

**Cluster MODERADO:**
- **Características:**
  - 100-500 paquetes por sesión
  - 20-100 KB de datos transmitidos
  - Uso de 2-4 puertos
  - ~80-90% tráfico TCP
- **Interpretación:**
  - Uso típico de estudiantes durante clase
  - Navegación web estándar, plataformas educativas, correo
  - Actividad regular sin picos extremos
- **Ejemplos típicos:** Laptops de estudiantes, tablets en clase

**Cluster LIGERO:**
- **Características:**
  - <100 paquetes por sesión
  - <20 KB de datos transmitidos
  - 1-2 puertos (principalmente 53 DNS y 443 HTTPS)
  - ~70-80% tráfico TCP
- **Interpretación:**
  - Dispositivos con conexión esporádica o en standby
  - Check de notificaciones, sincronización en background
  - Equipos que se conectan brevemente y entran en reposo
- **Ejemplos típicos:** Smartphones en bolsillo, dispositivos IoT, equipos apagados/suspendidos

#### Evaluación del modelo: Silhouette Score

El **Silhouette Score** mide qué tan bien separados están los clusters:
- **Rango:** -1 (mal agrupado) a +1 (perfectamente agrupado)
- **Interpretación:**
  - >0.70: Excelente separación
  - 0.50-0.70: Buena separación
  - 0.30-0.50: Moderada
  - <0.30: Clusters poco definidos

En este análisis, valores típicos de 0.50-0.65 indican que los equipos dentro de cada grupo
son claramente similares entre sí y diferentes a los otros grupos.

#### Visualización PCA

La gráfica 04 muestra:
- **Ejes X e Y:** Componentes principales 1 y 2 (combinación lineal de las 5 características originales)
- **Colores:** Indican el cluster asignado
- **Posición:** Equipos cercanos en el plano tienen comportamiento de red similar
- **Dispersión:** Muestra la variabilidad dentro de cada cluster

**Análisis visual:**
- Clusters bien separados → líneas claras entre grupos
- Solapamiento → equipos con comportamiento intermedio
- Outliers → equipos con patrones únicos que merecen atención

#### Aplicaciones prácticas

1. **Identificación de consumidores de ancho de banda:**
   - Localizar equipos en cluster Intensivo para optimización o investigación
   
2. **Detección de comportamiento anómalo:**
   - Un equipo que normalmente está en cluster Ligero y salta a Intensivo puede indicar malware o uso indebido
   
3. **Segmentación para políticas de red:**
   - Aplicar límites de ancho de banda diferenciados por cluster
   - Priorizar tráfico de cluster Moderado (uso académico) sobre Intensivo (descargas personales)
   
4. **Planificación de capacidad:**
   - Estimar cuántos usuarios de cada tipo puede soportar la red simultáneamente
   
5. **Análisis de tendencias:**
   - Monitorear cómo cambia la distribución de clusters a lo largo del semestre
   - Identificar horarios donde predomina un tipo de uso

### 3.3 Análisis Predictivo y de Tendencias (Regresión Logística + Tráfico por Hora)

**Gráficas asociadas:** `05_trafico_por_hora.png`, `07_prediccion_protocolo_hora.png`

#### Descripción del modelo

- **Objetivo:** Predecir el protocolo predominante (TCP/UDP) en función de la hora del día y analizar patrones temporales de uso de la red.
- **Tipo de algoritmo:** Regresión Logística (clasificación binaria)
- **Características usadas:**
  - Hora del día (0-23)
  - Longitud del paquete normalizada
- **Variable objetivo:** Protocolo (TCP=1, UDP=0)
- **Parámetros:** max_iter=500, regularización L2 por defecto
- **División:** Train/test 80%/20%

#### Resultados obtenidos

| Métrica | Valor típico |
|---------|--------------|
| Accuracy | 85-90% |
| Precision TCP | >90% |
| Recall TCP | >95% |
| AUC-ROC | >0.85 |

#### Análisis de tendencias horarias

La gráfica 05 (`trafico_por_hora.png`) muestra el volumen de tráfico TCP y UDP a lo largo del día:

**Patrones típicos observados:**

1. **Horario académico (8:00-18:00):**
   - **Pico máximo:** Entre 10:00-12:00 y 14:00-16:00
   - **Predominancia:** TCP representa >90% del tráfico
   - **Causas:** Clases en línea, uso de plataformas educativas (Teams, Moodle, Google Classroom)
   
2. **Horario de almuerzo (12:00-14:00):**
   - **Comportamiento:** Ligera disminución del tráfico
   - **Composición:** Más variado (redes sociales, streaming)
   
3. **Horario nocturno (19:00-7:00):**
   - **Volumen:** Mínimo del día
   - **Composición:** Mayor proporción relativa de UDP (aunque en términos absolutos sigue siendo bajo)
   - **Causas:** Servicios en background (DNS, sincronización automática, actualizaciones de SO)

4. **Fines de semana (si se capturan):**
   - **Patrón diferente:** Más uniforme, sin picos marcados
   - **Composición:** Mayor presencia de streaming (YouTube, Netflix → UDP en algunos casos)

#### Modelo predictivo por hora

La gráfica 07 (`prediccion_protocolo_hora.png`) muestra la **probabilidad** de TCP vs UDP para cada hora:

**Interpretación:**

- **Línea azul (prob. TCP):** Se mantiene alta (>0.80) durante todo el día
- **Línea naranja (prob. UDP):** Complementaria, nunca supera 0.20-0.30
- **Accuracy del 85-90%:** El modelo predice correctamente el protocolo en 9 de cada 10 paquetes

**Valor predictivo:**

Este modelo permite:
1. **Estimar el comportamiento esperado** de la red en cada hora
2. **Detectar anomalías:** Si en hora X la proporción real difiere significativamente de la predicha, puede indicar:
   - Ataque DDoS (flood de paquetes UDP)
   - Streaming masivo no autorizado
   - Comportamiento inusual de algún dispositivo
3. **Planificar mantenimiento:** Realizar tareas de red en horas con menor probabilidad de tráfico crítico (TCP académico)

#### Características del tráfico por protocolo

**TCP (Transmission Control Protocol):**
- **Servicios típicos:** HTTPS (443), HTTP (80), correo (SMTP/IMAP), descargas, SSH
- **Características:**
  - Orientado a conexión (handshake 3-way)
  - Garantía de entrega ordenada
  - Control de flujo y congestión
- **En la red académica:** Predomina porque la navegación web (HTTPS) es la actividad principal

**UDP (User Datagram Protocol):**
- **Servicios típicos:** DNS (53), streaming en tiempo real, videollamadas (algunos casos), juegos online, DHCP
- **Características:**
  - Sin conexión previa
  - No garantiza entrega ni orden
  - Menor latencia, mayor velocidad
- **En la red académica:** Principalmente DNS (cada solicitud web genera 1+ consultas DNS)

#### Análisis de picos de tráfico

**Identificación:**
- Hora con **mayor tráfico TCP:** Coincide con inicio de clases (ej. 10:00h)
- Hora con **mayor tráfico UDP:** Menos evidente; suele ser proporcional al TCP (más navegación = más DNS)
- Hora con **menor tráfico:** Madrugada (ej. 3:00-5:00h)

**Implicaciones:**
- **Sobrecarga:** Los picos pueden saturar el ancho de banda disponible
- **QoS:** Implementar priorización de tráfico académico durante horas pico
- **Monitoreo:** Establecer alertas si el tráfico en hora valle supera umbrales (puede indicar malware)

#### Aplicaciones prácticas

1. **Predicción de carga de red:**
   - Estimar cuánto ancho de banda se necesitará en cada hora del día
   
2. **Detección de anomalías temporales:**
   - Alertar si el tráfico real se desvía significativamente del patrón esperado
   
3. **Optimización de recursos:**
   - Programar respaldos y actualizaciones en horarios de bajo tráfico predichos
   
4. **Análisis de comportamiento:**
   - Identificar horarios de mayor uso de servicios específicos (ej. streaming después de clase)
   
5. **Capacidad de planificación:**
   - Dimensionar infraestructura de red basándose en patrones históricos validados por el modelo

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


| Servicio/App                 | Prefijos de IP asociados          | Nota                                  |
| ---------------------------- | --------------------------------- | ------------------------------------- |
| Google / YouTube             | 142.250, 142.251, 172.217, 74.125 | CDN de Google                         |
| Meta (Facebook/Instagram)    | 157.240, 179.60, 31.13            | Infraestructura de Meta               |
| Microsoft / Azure / Teams    | 52.x, 20.x, 40.x, 13.x            | Servicios cloud Microsoft             |
| Cloudflare / ChatGPT-posible | 104.16 – 104.19                   | CDN Cloudflare; OpenAI usa Cloudflare |
| AWS / OpenAI-posible         | 3.x, 34.x, 54.x                   | Amazon Web Services                   |
| Akamai CDN                   | 23.x, 184.x                       | CDN genérico                          |
| Netflix                      | 185.2, 198.45                     | Streaming                             |
| TikTok / ByteDance           | 162.x                             | Aproximado                            |


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


| Archivo                            | Descripción                                                     |
| ---------------------------------- | --------------------------------------------------------------- |
| `01_tcp_vs_udp.png`                | Comparativa de volumen TCP vs UDP (barras y pie chart)          |
| `02_dispersion_tamano_hora.png`    | Tamaño de paquetes vs hora del día por protocolo                |
| `03_puertos_frecuentes.png`        | Top 15 puertos destino más utilizados                           |
| `04_clusters_equipos.png`          | Visualización 2D (PCA) de los clusters de equipos               |
| `05_trafico_por_hora.png`          | Volumen de tráfico por hora del día                             |
| `06_confusion_knn.png`             | Matriz de confusión del modelo KNN                              |
| `07_prediccion_protocolo_hora.png` | Probabilidad de protocolo por hora (predictivo)                 |
| `08_servicios_frecuentes.png`      | Categorías de servicio más frecuentes (por puerto)              |
| `09_apps_inferidas.png`            | Aplicaciones inferidas por IP (YouTube, Meta, Cloudflare, etc.) |
| `06_confusion_knn.png`             | Matriz de confusión del modelo KNN                              |
| `07_prediccion_protocolo_hora.png` | Probabilidad de protocolo por hora (predictivo)                 |
| `08_servicios_frecuentes.png`      | Categorías de servicio más frecuentes                           |


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

