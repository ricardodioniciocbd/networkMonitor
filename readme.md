Análisis y Captura de Datos con herramienta de monitoreo para
análisis con técnicas de Data science
En la presente asignación se tiene como objetivo principal que el estudiante adquiera experiencia
práctica en el monitoreo, captura y análisis del tráfico de una red inalámbrica (Wi-Fi- red
Estudiantes), específicamente en el área donde se encuentran alojadas las aulas de Ingeniería en
Sistemas Computacionales en el edificio N. Esta actividad permite comprender el
comportamiento real de una red, los protocolos utilizados, el tipo de tráfico generado y los patrones
de comunicación entre los dispositivos conectados. Durante 3 dias los estudiantes deberán realizar
capturas diarias del tráfico de red, generando archivos independientes por cada día de monitoreo.
Dichas capturas deberán ser procesadas y exportadas en formato .csv, ya que este tipo de archivo
es compatible con herramientas de datascience, usando algoritmos y operaciones de datascience
en python usando técnicas de análisis de datos, permitiendo posteriormente realizar análisis más
avanzados y generando resultados prometedores.
Uso de herramientas de monitoreo de red
Para la captura del tráfico de red se propone el uso del software generado en java para apache
netbeans, el cual debe permite monitorear el tráfico en tiempo real y capturar información relevante
de cada paquete que circula en la red Wi-Fi, tales como:
• Dirección IP de origen y destino de los equipos de cómputo conectados.
• Direcciones MAC, útiles para identificar dispositivos físicos.
• Puertos de comunicación, tanto de origen como de destino.
• Protocolos utilizados, principalmente TCP y UDP.
• Aplicaciones o servicios asociados, como navegación web (HTTP/HTTPS), servicios de
correo, DNS, streaming, entre otros.
• Tamaño de los paquetes, tiempo de transmisión y frecuencia de envío.
• Nombre del equipo
• Nombre de la red wifi
El proceso de captura consiste en seleccionar la interfaz de red inalámbrica correspondiente, iniciar
la captura durante un periodo prolongado de tiempo (por ejemplo, varias horas de uso académico)
y posteriormente detenerla para su análisis. Es importante aplicar filtros adecuados dentro del
sistema creado en java con la librería swing para organizar la información y facilitar su interpretación.
Generación del archivo CSV para minería de datos
Una vez obtenida la captura, el programa creado en java debe permite exportar los paquetes
capturados a un archivo .csv, seleccionando los campos más relevantes para el análisis. Para
cumplir con los requisitos de la asignación, se deberá generar un archivo con más de 100,000
registros, lo cual garantiza un volumen de datos suficiente para aplicar técnicas de datascience y
aprendizaje automático.
Entre los campos recomendados para el archivo CSV se encuentran:
• IP de origen
• IP de destino
• Puerto de origen
• Puerto de destino
• Protocolo (TCP/UDP)
• Longitud del paquete
• Tiempo de llegada
• Tipo de servicio o aplicación
• Nombre de equipo de cómputo
• Fecha/hora de captura
Este archivo representará una muestra real del comportamiento de la red durante un día específico,
y será utilizado como evidencia principal de la actividad, junto con la firma de visto bueno del
profesor.
Análisis de los datos capturados
Posteriormente, los datos obtenidos podrán ser analizados utilizando herramientas de pyhton. Con
esta herramienta se pueden aplicar distintos enfoques de análisis, entre los cuales se les pide usar:
• Clasificación con K-Nearest Neighbors (KNN), para identificar patrones de
comportamiento similares entre dispositivos o tipos de tráfico.
• Análisis predictivo, para estimar el tipo de tráfico o protocolo predominante en
determinados horarios.
• Clustering, para agrupar equipos según su comportamiento en la red (uso intensivo, uso
moderado, servicios específicos, etc.).
• Detección de patrones, como picos de tráfico o uso recurrente de ciertos puertos.
Estos análisis permiten comprender cómo se utiliza la red, qué servicios son los más demandados
y cómo se distribuye el tráfico entre los distintos equipos de cómputo.
Visualización y presentación de resultados
Finalmente, los resultados obtenidos podrán ser graficados para facilitar su interpretación. Algunas
gráficas recomendadas incluyen:
• Gráficas de barras del uso de protocolos TCP vs UDP.
• Gráficas de dispersión entre tamaño de paquetes y tiempo.
• Gráficas de frecuencia de uso de puertos.
• Gráficas de clasificación de dispositivos según su patrón de tráfico.
La visualización de los datos ayuda a presentar conclusiones claras y comprensibles, fortaleciendo
el análisis técnico del comportamiento de la red.
Conclusión
Esta práctica integra conocimientos de redes, seguridad informática, análisis de tráfico, análisis con
data science y visualización, permitiendo al estudiante desarrollar habilidades fundamentales para
el análisis de sistemas reales. El uso del software de monitores creado con java y el análisis no solo
fortalece la comprensión teórica, sino que también fomenta el pensamiento analítico y la toma de
decisiones basada en datos reales.
Programa en Java con Swing para capturar tráfico y generar CSV
Importante:
Java por sí solo no puede “escuchar” tráfico de red (capturar paquetes) sin acceso nativo al
adaptador de red.
Para que funcione necesitaremos una librería externa que puente a los controladores de red: Pcap4J
— una biblioteca Java para capturar paquetes (usa WinPcap/Npcap en Windows o libpcap en Linux).
¿Qué hace este programa?
Monitorea tráfico de red en la interfaz seleccionada Obtiene y muestra en tiempo real por cada
paquete:
• IP de origen
• IP de destino
• Puerto de origen
• Puerto de destino
• Protocolo (TCP/UDP)
• Longitud del paquete
• Tiempo de llegada
• Nombre de equipo local (si aplica)
• Hora de captura
• Guarda todo en un archivo CSV compatible con WEKA
Requisitos

1. Apache NetBeans
2. Java 8 o superior
3. Instalar Npcap (Windows) o libpcap (Linux/Mac)
4. Agregar la librería Pcap4J al proyecto
   Estructura del proyecto
   Se tendrá una interfaz Swing sencilla con:
   • Botón para seleccionar interfaz de red
   • Tabla para mostrar paquetes capturados
   • Botón para iniciar/parar captura
   • Botón para exportar a CSV

Notebook de Análisis de Tráfico de Red con Python-Data Science

1. Objetivo del análisis
   El objetivo de este notebook es analizar el tráfico de red capturado en una red Wi-Fi, con el fin de:
   • Identificar patrones de comportamiento entre dispositivos.
   <dependency>
   <groupId>org.pcap4j</groupId>
   <artifactId>pcap4j-core</artifactId>
   <version>1.8.0</version>
   </dependency>
   • Clasificar el tipo de tráfico mediante K-Nearest Neighbors (KNN).
   • Realizar predicción del protocolo predominante en distintos horarios.
   • Aplicar clustering para agrupar equipos según su uso de red.
   • Detectar picos de tráfico y puertos recurrentes.
   • Visualizar e interpretar los resultados mediante gráficas.
2. Importación de librerías
   import pandas as pd
   import numpy as np
   import matplotlib.pyplot as plt
   from sklearn.model_selection import train_test_split
   from sklearn.preprocessing import LabelEncoder, StandardScaler
   from sklearn.neighbors import KNeighborsClassifier
   from sklearn.cluster import KMeans
   from sklearn.metrics import classification_report, confusion_matrix
3. Carga del archivo CSV
   df = pd.read_csv("captura_red.csv")
   df.head()
   Columnas esperadas
   § IP Origen
   § IP Destino
   § Puerto Origen
   § Puerto Destino
   § Protocolo (TCP / UDP)
   § Longitud
   § Fecha-Hora Captura
   § Equipo Local
4. Limpieza y preparación de datos
   4.1 Conversión de variables categóricas
   encoder = LabelEncoder()
   df["Protocolo_cod"] = encoder.fit_transform(df["Protocolo"])
   df["Equipo_cod"] = encoder.fit_transform(df["Equipo Local"])
   4.2 Extracción de la hora (para análisis temporal)
   df["Hora"] = pd.to_datetime(df["Hora Captura"], format="%H:%M:%S").dt.hour
5. Clasificación con K-Nearest Neighbors (KNN)
   Objetivo:
   Clasificar el tipo de protocolo (TCP/UDP) con base en:
   § Puertos
   § Longitud
   § Fecha-Hora
   5.1 Selección de variables
   X = df[["Puerto Origen", "Puerto Destino", "Longitud", "Hora"]]
   y = df["Protocolo_cod"]
   5.2 Normalización
   scaler = StandardScaler()
   X_scaled = scaler.fit_transform(X)
   5.3 Entrenamiento del modelo KNN
   X_train, X_test, y_train, y_test = train_test_split(
   X_scaled, y, test_size=0.3, random_state=42
   )
   knn = KNeighborsClassifier(n_neighbors=5)
   knn.fit(X_train, y_train)
   5.4 Evaluación del modelo
   y_pred = knn.predict(X_test)
   print(classification_report(y_test, y_pred))
   Interpretación esperada
   § Alta precisión indica que los patrones de puertos y tamaños permiten distinguir tráfico TCP y
   UDP.
   § Un buen recall sugiere estabilidad del comportamiento de red.
6. Análisis predictivo por horarios
   Objetivo:
   Determinar qué protocolo predomina en cada hora del día.
   protocol_hour = df.groupby("Hora")["Protocolo_cod"].mean()
   plt.figure()
   protocol_hour.plot(kind="line")
   plt.xlabel("Hora del día")
   plt.ylabel("Promedio de Protocolo (0=TCP, 1=UDP)")
   plt.title("Predicción de Protocolo Predominante por Hora")
   plt.show()
   Interpretación
   Valores cercanos a 0 → predominio TCP (navegación web, correo).
   Valores cercanos a 1 → predominio UDP (streaming, DNS, multimedia).
7. Clustering de equipos (K-Means)
   Objetivo:
   Agrupar equipos según su comportamiento de red:
   § Uso intensivo
   § Uso moderado
   § Servicios específicos
   7.1 Variables para clustering
   X_cluster = df.groupby("Equipo_cod").agg({
   "Longitud": "mean",
   "Puerto Destino": "nunique",
   "Hora": "mean"
   })
   7.2 Aplicación de K-Means
   kmeans = KMeans(n_clusters=3, random_state=0)
   X_cluster["Cluster"] = kmeans.fit_predict(X_cluster)
   7.3 Visualización
   plt.figure()
   plt.scatter(X_cluster["Longitud"], X_cluster["Puerto Destino"],
   c=X_cluster["Cluster"])
   plt.xlabel("Longitud promedio de paquetes")
   plt.ylabel("Cantidad de puertos usados")
   plt.title("Clustering de Equipos en la Red")
   plt.show()
   Interpretación
   § Cluster 0 → equipos con uso ligero (navegación básica).
   § Cluster 1 → uso moderado (clases, plataformas educativas).
   § Cluster 2 → uso intensivo (descargas, streaming).
8. Detección de patrones y picos de tráfico
   8.1 Paquetes por hora
   tra|ic_hour = df.groupby("Hora").size()
   plt.figure()
   tra|ic_hour.plot(kind="bar")
   plt.xlabel("Hora del día")
   plt.ylabel("Número de paquetes")
   plt.title("Picos de Tráfico por Hora")
   plt.show()
   8.2 Puertos más utilizados
   top_ports = df["Puerto Destino"].value_counts().head(10)
   plt.figure()
   top_ports.plot(kind="bar")
   plt.xlabel("Puerto")
   plt.ylabel("Frecuencia")
   plt.title("Puertos más utilizados en la red")
   plt.show()
   Interpretación
   § Puerto 80/443 → HTTP/HTTPS
   § Puerto 53 → DNS
   § Puertos altos → streaming o aplicaciones específicas

nstrucciones
En la presente asignación se tiene como objetivo principal que el estudiante adquiera experiencia práctica en el monitoreo, captura y análisis del tráfico de una red inalámbrica (Wi-Fi- red Estudiantes), específicamente en el área donde se encuentran alojadas las aulas de Ingeniería en Sistemas Computacionales en el edificio N. Esta actividad permite comprender el comportamiento real de una red, los protocolos utilizados, el tipo de tráfico generado y los patrones de comunicación entre los dispositivos conectados. Durante 3 dias los estudiantes deberán realizar capturas diarias del tráfico de red, generando archivos independientes por cada día de monitoreo. Dichas capturas deberán ser procesadas y exportadas en formato .csv, ya que este tipo de archivo es compatible con herramientas de datascience, usando algoritmos y operaciones de datascience en python usando técnicas de análisis de datos, permitiendo posteriormente realizar análisis más avanzados y generando resultados prometedores DONDE SE VEAN GRÁFICAS QUE EXPLIQUEN LOS RESULTADOS OBTENIDOS EN EL DATASET DEL SOFTWARE TIPO O AL ESTILO WIRESHARK Y DE DONDE SE OBTIENE EL ARCHIVO csv.

Uso de herramientas de monitoreo de red

Para la captura del tráfico de red se propone el uso del software generado en java para apache netbeans, el cual debe permite monitorear el tráfico en tiempo real y capturar información relevante de cada paquete que circula en la red Wi-Fi, tales como:

Dirección IP de origen y destino de los equipos de cómputo conectados.
Direcciones MAC, útiles para identificar dispositivos físicos.
Puertos de comunicación, tanto de origen como de destino.
Protocolos utilizados, principalmente TCP y UDP.
Aplicaciones o servicios asociados, como navegación web (HTTP/HTTPS), servicios de correo, DNS, streaming, VERIFICAR EL USO DE APLICACIONES WEB COMO CHATGPT, FACEBOOK, INSTAGRAM, YOUTUBE, ETC QUE MÁS VEN LOS ESTUDIANTES.
Tamaño de los paquetes, tiempo de transmisión y frecuencia de envío.
Nombre del equipo
Nombre de la red wifi
El proceso de captura consiste en seleccionar la interfaz de red inalámbrica correspondiente, iniciar la captura durante un periodo prolongado de tiempo (por ejemplo, varias horas de uso académico) y posteriormente detenerla para su análisis. Es importante aplicar filtros adecuados dentro del sistema creado en java con la librería swing para organizar la información y facilitar su interpretación.

AL FINAL FAVOR DE SUBIR SU EVIDENCIA ESCRITA, LOS CÓDIGOS, VERIFICAR QUE LOS RESULTADOS DE LA CAPTURA REALZIADO POR EL PROGRAMA EN JAVA SE MUESTRE EN LA DOCUMENTACIÓN, TAMBIÉN SUBAN EL ARCHIVO CSV GENERADO EN ESTOS 3 DIAS.
