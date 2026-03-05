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
