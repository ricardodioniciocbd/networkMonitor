
# NetworkMonitor - Captura y Análisis de Tráfico de Red

## Descripción
Este proyecto consiste en el desarrollo de una herramienta en **Java (Swing)** para **monitorear y capturar tráfico de red Wi-Fi**.  
Los datos capturados se exportan a **archivos CSV** para posteriormente ser analizados utilizando **técnicas de Data Science en Python**.

El objetivo es comprender el **comportamiento real de una red**, los protocolos utilizados y los patrones de comunicación entre dispositivos conectados.

---

# Objetivo del Proyecto
Monitorear el tráfico de la red **Wi-Fi Estudiantes** en el edificio **N** (Ingeniería en Sistemas Computacionales) durante **3 días**, capturando información de los paquetes de red para su posterior análisis.

Cada día se realiza una captura independiente del tráfico de red.

---

# Información Capturada
La herramienta registra información relevante de cada paquete:

- IP de origen
- IP de destino
- Dirección MAC
- Puerto de origen
- Puerto de destino
- Protocolo (TCP / UDP)
- Longitud del paquete
- Tiempo de llegada
- Nombre del equipo
- Nombre de la red Wi-Fi

---

# Generación de Dataset
Los datos capturados se exportan a **archivos `.csv`**, los cuales contienen más de **100,000 registros** para permitir un análisis adecuado.

Campos incluidos en el dataset:

- IP de origen
- IP de destino
- Puerto de origen
- Puerto de destino
- Protocolo
- Tamaño del paquete
- Tiempo de llegada
- Tipo de servicio o aplicación
- Nombre del equipo
- Fecha y hora de captura

Estos archivos sirven como base para **análisis de datos y aprendizaje automático**.

---

# Análisis de Datos
Los datos exportados pueden analizarse utilizando **Python** y técnicas de **Data Science**, incluyendo:

- **K-Nearest Neighbors (KNN)** para clasificación de tráfico
- **Clustering** para agrupar dispositivos según su comportamiento
- **Análisis predictivo** para identificar patrones de uso
- **Detección de patrones de tráfico** como picos de red o uso frecuente de puertos

---

# Visualización de Resultados
Los resultados pueden representarse mediante gráficas como:

- Uso de protocolos **TCP vs UDP**
- Relación entre **tamaño de paquetes y tiempo**
- Frecuencia de **uso de puertos**
- Clasificación de dispositivos según su tráfico

Estas visualizaciones ayudan a interpretar el comportamiento de la red.

---

# Tecnologías Utilizadas

- **Java**
- **Java Swing**
- **Pcap4J**
- **Python**
- **Data Science / Machine Learning**
- **CSV Dataset**

---

# Funcionalidades del Programa

La aplicación desarrollada en Java permite:

- Seleccionar la **interfaz de red**
- Capturar tráfico de red en **tiempo real**
- Mostrar paquetes capturados en una **tabla**
- Iniciar y detener la captura
- Exportar los datos capturados a **CSV**

---

# Estructura del Programa

La interfaz gráfica incluye:

- Selector de **interfaz de red**
- Tabla de **paquetes capturados**
- Botón para **iniciar / detener captura**
- Botón para **exportar datos a CSV**
