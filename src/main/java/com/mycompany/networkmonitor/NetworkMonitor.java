/*
 * NetworkMonitor v3.1
 * Captura tráfico de red en tiempo real de TODOS los dispositivos conectados.
 *
 * Mejoras v3.1:
 *   - Caché DNS de respuestas: parsea registros A de respuestas DNS para mapear
 *     IP externa → dominio real (youtube.com, google.com, chatgpt.com, etc.).
 *   - "Sitio/App Destino": DNS-cache > SNI (TLS) > DNS query > puerto conocido.
 *   - NetBIOS mejorado: charset OEM del sistema, timeout 5s, compatible con
 *     Windows en español (ÚNICO / UNIQUE / sin acento).
 *   - Escaneo ARP al inicio con resolución NetBIOS asíncrona de todos los equipos.
 *   - SSID: auto-detectado con "netsh wlan show interfaces".
 *   - Ninguna celda queda vacía.
 */

package com.mycompany.networkmonitor;

import org.pcap4j.core.*;
import org.pcap4j.packet.EthernetPacket;
import org.pcap4j.packet.Packet;
import org.pcap4j.packet.IpV4Packet;
import org.pcap4j.packet.TcpPacket;
import org.pcap4j.packet.UdpPacket;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import javax.swing.RowFilter;
import java.awt.*;
import java.io.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;

public class NetworkMonitor extends JFrame {

    private static final int MAX_PAQUETES = 33_333;

    // -----------------------------------------------------------------------
    // Cachés y pool de hilos
    // -----------------------------------------------------------------------
    /** Caché de nombres de dispositivos locales: IP → nombre */
    private final ConcurrentHashMap<String, String> hostCache = new ConcurrentHashMap<>();
    /** Caché de dominios reales: IP externa → dominio (poblada desde respuestas DNS) */
    private final ConcurrentHashMap<String, String> dnsCache  = new ConcurrentHashMap<>();
    private final ExecutorService resolver = Executors.newFixedThreadPool(6);

    // Mapa estático de puertos -> nombre de servicio conocido
    private static final Map<Integer, String> SERVICIOS_CONOCIDOS = new LinkedHashMap<>();
    static {
        SERVICIOS_CONOCIDOS.put(80,   "HTTP");
        SERVICIOS_CONOCIDOS.put(443,  "HTTPS");
        SERVICIOS_CONOCIDOS.put(53,   "DNS");
        SERVICIOS_CONOCIDOS.put(5353, "mDNS");
        SERVICIOS_CONOCIDOS.put(5355, "LLMNR");
        SERVICIOS_CONOCIDOS.put(67,   "DHCP-Server");
        SERVICIOS_CONOCIDOS.put(68,   "DHCP-Client");
        SERVICIOS_CONOCIDOS.put(25,   "SMTP");
        SERVICIOS_CONOCIDOS.put(465,  "SMTP-SSL");
        SERVICIOS_CONOCIDOS.put(587,  "SMTP-Envio");
        SERVICIOS_CONOCIDOS.put(110,  "POP3");
        SERVICIOS_CONOCIDOS.put(995,  "POP3-SSL");
        SERVICIOS_CONOCIDOS.put(143,  "IMAP");
        SERVICIOS_CONOCIDOS.put(993,  "IMAP-SSL");
        SERVICIOS_CONOCIDOS.put(21,   "FTP");
        SERVICIOS_CONOCIDOS.put(22,   "SSH");
        SERVICIOS_CONOCIDOS.put(23,   "Telnet");
        SERVICIOS_CONOCIDOS.put(3389, "RDP");
        SERVICIOS_CONOCIDOS.put(3306, "MySQL");
        SERVICIOS_CONOCIDOS.put(5432, "PostgreSQL");
        SERVICIOS_CONOCIDOS.put(1433, "SQL-Server");
        SERVICIOS_CONOCIDOS.put(27017,"MongoDB");
        SERVICIOS_CONOCIDOS.put(6379, "Redis");
        SERVICIOS_CONOCIDOS.put(8080, "HTTP-Alt");
        SERVICIOS_CONOCIDOS.put(8443, "HTTPS-Alt");
        SERVICIOS_CONOCIDOS.put(1900, "UPnP/SSDP");
        SERVICIOS_CONOCIDOS.put(137,  "NetBIOS-NS");
        SERVICIOS_CONOCIDOS.put(138,  "NetBIOS-DGM");
        SERVICIOS_CONOCIDOS.put(139,  "NetBIOS-SSN");
        SERVICIOS_CONOCIDOS.put(445,  "SMB/CIFS");
        SERVICIOS_CONOCIDOS.put(5985, "WinRM-HTTP");
        SERVICIOS_CONOCIDOS.put(5986, "WinRM-HTTPS");
        SERVICIOS_CONOCIDOS.put(123,  "NTP");
        SERVICIOS_CONOCIDOS.put(161,  "SNMP");
        SERVICIOS_CONOCIDOS.put(162,  "SNMP-Trap");
        SERVICIOS_CONOCIDOS.put(514,  "Syslog");
        SERVICIOS_CONOCIDOS.put(1723, "PPTP-VPN");
        SERVICIOS_CONOCIDOS.put(1194, "OpenVPN");
        SERVICIOS_CONOCIDOS.put(500,  "IKE-VPN");
        SERVICIOS_CONOCIDOS.put(4500, "IPSec-VPN");
        SERVICIOS_CONOCIDOS.put(554,  "RTSP-Streaming");
        SERVICIOS_CONOCIDOS.put(1935, "RTMP-Streaming");
        SERVICIOS_CONOCIDOS.put(19302,"STUN/WebRTC");
        SERVICIOS_CONOCIDOS.put(3478, "STUN/TURN");
    }

    // -----------------------------------------------------------------------
    // Utilidades: red local
    // -----------------------------------------------------------------------
    private static boolean esRedLocal(String ip) {
        return ip.startsWith("192.168.")
                || ip.startsWith("10.")
                || ip.startsWith("172.16.") || ip.startsWith("172.17.")
                || ip.startsWith("172.18.") || ip.startsWith("172.19.")
                || ip.startsWith("172.2")   || ip.startsWith("172.3")
                || ip.startsWith("127.")
                || ip.startsWith("169.254.");
    }

    // -----------------------------------------------------------------------
    // Mapeo de puerto a servicio conocido
    // -----------------------------------------------------------------------
    private static String mapearServicio(int puerto) {
        String servicio = SERVICIOS_CONOCIDOS.get(puerto);
        return (servicio != null) ? servicio : "Puerto " + puerto;
    }

    // -----------------------------------------------------------------------
    // Detección automática de SSID WiFi (Windows)
    // -----------------------------------------------------------------------
    private String detectarSSID() {
        try {
            Process p = new ProcessBuilder("netsh", "wlan", "show", "interfaces").start();
            BufferedReader br = new BufferedReader(
                new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
            String linea;
            while ((linea = br.readLine()) != null) {
                // Busca línea "    SSID            : NombreRed"
                // Evita "BSSID" que también contiene "SSID"
                String trimmed = linea.trim();
                if (trimmed.startsWith("SSID") && !trimmed.startsWith("BSSID")) {
                    int idx = trimmed.indexOf(':');
                    if (idx >= 0) {
                        String ssid = trimmed.substring(idx + 1).trim();
                        if (!ssid.isEmpty()) return ssid;
                    }
                }
            }
        } catch (Exception ignored) {}
        return "Sin WiFi";
    }

    // -----------------------------------------------------------------------
    // Resolución NetBIOS de un dispositivo por IP
    // Windows usa codificación OEM (IBM850/CP850) para la salida de nbtstat.
    // En español: "ÚNICO" o "UNIQUE" dependiendo de la versión de Windows.
    // -----------------------------------------------------------------------
    private String resolverNetBIOS(String ip) {
        try {
            Process p = new ProcessBuilder("nbtstat", "-A", ip).start();

            // Leer salida completa ANTES de esperar, para evitar bloqueo de buffer
            // Usamos charset OEM del sistema (CP850 en Windows español)
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(p.getInputStream()))) {
                String linea;
                while ((linea = br.readLine()) != null) {
                    sb.append(linea).append("\n");
                }
            }
            // Esperar hasta 5 segundos
            p.waitFor(5, TimeUnit.SECONDS);

            for (String linea : sb.toString().split("\n")) {
                String trim = linea.trim();
                // Formato: NOMBREEQUIPO     <00>  UNIQUE   Registered
                // Windows ES puede mostrar "ÚNICO" o "UNICO" en lugar de "UNIQUE"
                if (trim.contains("<00>") &&
                        (trim.toUpperCase().contains("UNIQUE") ||
                         trim.toUpperCase().contains("\u00danico") ||
                         trim.toUpperCase().contains("UNICO"))) {
                    // Nombre NetBIOS: primeros 15 caracteres (con relleno de espacios)
                    int end = trim.indexOf("<00>");
                    if (end > 0) {
                        String nombre = trim.substring(0, end).trim();
                        if (!nombre.isEmpty()) return nombre;
                    }
                }
            }
        } catch (Exception ignored) {}
        return "";
    }

    // -----------------------------------------------------------------------
    // Escaneo ARP: lee la tabla ARP del SO y lanza resolución NetBIOS para
    // cada IP local encontrada, pre-poblando la caché antes de capturar.
    // -----------------------------------------------------------------------
    private void escanearRedARP() {
        resolver.submit(() -> {
            try {
                Process p = new ProcessBuilder("arp", "-a").start();
                BufferedReader br = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
                String linea;
                while ((linea = br.readLine()) != null) {
                    // Formato típico: "  192.168.1.10         aa-bb-cc-dd-ee-ff     dynamic"
                    String trim = linea.trim();
                    if (trim.isEmpty() || trim.startsWith("Interface") || trim.startsWith("Internet")) continue;
                    String[] partes = trim.split("\\s+");
                    if (partes.length >= 2) {
                        String ip = partes[0];
                        // Validar que sea una IP local real (no multicast, no broadcast)
                        if (esRedLocal(ip) && !ip.endsWith(".255") && !ip.startsWith("224.")) {
                            // Resolución asíncrona para cada IP descubierta
                            resolver.submit(() -> resolverNombreCompleto(ip));
                        }
                    }
                }
            } catch (Exception ignored) {}
        });
    }

    // -----------------------------------------------------------------------
    // Resolución completa de nombre: NetBIOS > DNS inverso > fallback IP
    // Guarda el resultado en hostCache bajo la clave ip.
    // -----------------------------------------------------------------------
    private String resolverNombreCompleto(String ip) {
        // Si ya está en caché con un nombre real (no solo la IP), devolver directamente
        String cached = hostCache.get(ip);
        if (cached != null && !cached.equals(ip) && !cached.isEmpty()) {
            return cached;
        }

        // 1. NetBIOS
        String nombre = resolverNetBIOS(ip);
        if (!nombre.isEmpty()) {
            hostCache.put(ip, nombre);
            return nombre;
        }

        // 2. DNS inverso
        try {
            String dns = InetAddress.getByName(ip).getHostName();
            if (!dns.equals(ip) && !dns.isEmpty()) {
                hostCache.put(ip, dns);
                return dns;
            }
        } catch (UnknownHostException ignored) {}

        // 3. Fallback: "Equipo-<último octeto>"
        String[] partes = ip.split("\\.");
        String fallback = "Equipo-" + (partes.length == 4 ? partes[2] + "." + partes[3] : ip);
        hostCache.put(ip, fallback);
        return fallback;
    }

    // -----------------------------------------------------------------------
    // Nombre de dispositivo para columna (con caché y resolución asíncrona)
    // -----------------------------------------------------------------------
    private String obtenerNombreDispositivo(String ip) {
        if (!esRedLocal(ip)) return "";

        String cached = hostCache.get(ip);
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }

        // Aún no está en caché: poner placeholder y resolver en background
        String[] partes = ip.split("\\.");
        String placeholder = "Equipo-" + (partes.length == 4 ? partes[2] + "." + partes[3] : ip);
        hostCache.put(ip, placeholder);

        resolver.submit(() -> resolverNombreCompleto(ip));
        return placeholder;
    }

    // -----------------------------------------------------------------------
    // Hostname Remoto (IPs externas, DNS inverso asíncrono)
    // -----------------------------------------------------------------------
    private String resolverHostnameRemoto(String ip) {
        if (esRedLocal(ip)) return "";
        String key = ip + "_remote";
        String cached = hostCache.get(key);
        if (cached != null) return cached;

        hostCache.put(key, ""); // placeholder vacío mientras resuelve
        resolver.submit(() -> {
            try {
                String nombre = InetAddress.getByName(ip).getHostName();
                hostCache.put(key, nombre.equals(ip) ? "N/A" : nombre);
            } catch (UnknownHostException e) {
                hostCache.put(key, "N/A");
            }
        });
        return "";
    }

    // -----------------------------------------------------------------------
    // Extracción de SNI (Server Name Indication) desde TLS Client Hello
    // -----------------------------------------------------------------------
    private static String extraerSNI(byte[] payload) {
        try {
            if (payload == null || payload.length < 43) return "";
            if ((payload[0] & 0xFF) != 0x16) return "";
            if ((payload[1] & 0xFF) != 0x03)  return "";
            if ((payload[5] & 0xFF) != 0x01)  return "";

            int pos = 43;
            if (pos >= payload.length) return "";
            int sessionIdLen = payload[pos] & 0xFF;
            pos += 1 + sessionIdLen;

            if (pos + 2 > payload.length) return "";
            int cipherSuitesLen = ((payload[pos] & 0xFF) << 8) | (payload[pos + 1] & 0xFF);
            pos += 2 + cipherSuitesLen;

            if (pos >= payload.length) return "";
            int compressionLen = payload[pos] & 0xFF;
            pos += 1 + compressionLen;

            if (pos + 2 > payload.length) return "";
            int extTotalLen = ((payload[pos] & 0xFF) << 8) | (payload[pos + 1] & 0xFF);
            pos += 2;
            int extEnd = pos + extTotalLen;

            while (pos + 4 <= extEnd && pos + 4 <= payload.length) {
                int extType = ((payload[pos] & 0xFF) << 8) | (payload[pos + 1] & 0xFF);
                int extLen  = ((payload[pos + 2] & 0xFF) << 8) | (payload[pos + 3] & 0xFF);
                pos += 4;
                if (extType == 0x0000) {
                    if (pos + 5 > payload.length) break;
                    int nameType = payload[pos + 2] & 0xFF;
                    int nameLen  = ((payload[pos + 3] & 0xFF) << 8) | (payload[pos + 4] & 0xFF);
                    if (nameType == 0 && pos + 5 + nameLen <= payload.length) {
                        return new String(payload, pos + 5, nameLen, StandardCharsets.US_ASCII);
                    }
                }
                pos += extLen;
            }
        } catch (Exception ignored) {}
        return "";
    }

    // -----------------------------------------------------------------------
    // Extracción de nombre de dispositivo desde paquetes mDNS (puerto 5353)
    // Los dispositivos anuncian su nombre hostname.local → IP en registros A
    // de respuestas mDNS. Esto es más fiable que nbtstat.
    // -----------------------------------------------------------------------
    private void extraerNombreMdns(String ipSrc, byte[] payload) {
        try {
            if (payload == null || payload.length < 12) return;

            int qdCount = ((payload[4] & 0xFF) << 8) | (payload[5] & 0xFF);
            int anCount = ((payload[6] & 0xFF) << 8) | (payload[7] & 0xFF);
            int nsCount = ((payload[8] & 0xFF) << 8) | (payload[9] & 0xFF);
            int arCount = ((payload[10] & 0xFF) << 8) | (payload[11] & 0xFF);

            int pos = 12;

            // Saltar sección de preguntas
            for (int q = 0; q < qdCount && pos >= 0 && pos < payload.length; q++) {
                StringBuilder tmp = new StringBuilder();
                pos = leerNombreDns(payload, pos, tmp);
                if (pos < 0) return;
                pos += 4; // QTYPE + QCLASS
            }

            // Leer secciones de respuestas + autoridad + adicionales
            int total = anCount + nsCount + arCount;
            for (int i = 0; i < total && pos >= 0 && pos < payload.length; i++) {
                StringBuilder nombre = new StringBuilder();
                pos = leerNombreDns(payload, pos, nombre);
                if (pos < 0) return;

                if (pos + 10 > payload.length) break;
                int type     = ((payload[pos] & 0xFF) << 8) | (payload[pos + 1] & 0xFF);
                int rdLength = ((payload[pos + 8] & 0xFF) << 8) | (payload[pos + 9] & 0xFF);
                pos += 10;

                if (type == 1 && rdLength == 4 && pos + 4 <= payload.length) {
                    // Registro A: hostname.local → IP
                    String ip = (payload[pos] & 0xFF) + "." + (payload[pos + 1] & 0xFF) + "."
                              + (payload[pos + 2] & 0xFF) + "." + (payload[pos + 3] & 0xFF);
                    String hostname = nombre.toString();
                    // Limpiar sufijo .local
                    if (hostname.endsWith(".local")) {
                        hostname = hostname.substring(0, hostname.length() - 6);
                    }
                    if (!hostname.isEmpty() && esRedLocal(ip)) {
                        // Solo actualizar si no hay ya un nombre real (no placeholder)
                        String actual = hostCache.get(ip);
                        if (actual == null || actual.startsWith("Equipo-")) {
                            hostCache.put(ip, hostname);
                        }
                    }
                }
                if (rdLength > 0) pos += rdLength;
            }

            // También intentar asociar la IP origen con el nombre del anuncio
            // si el paquete viene directamente del dispositivo
            if (anCount > 0) {
                // Si encontramos algún registro A que coincide con ipSrc, ya fue guardado.
                // Además, buscar PTR records que nos den el nombre del servicio del origen.
                // (ya cubierto arriba — los A records mapean la IP correctamente)
            }
        } catch (Exception ignored) {}
    }

    // -----------------------------------------------------------------------
    // Extracción de dominio desde paquete DNS (UDP puerto 53)
    // Funciona tanto en queries como en respuestas: extrae el nombre de la
    // primera pregunta, que es siempre el dominio que se está consultando.
    // -----------------------------------------------------------------------
    private static String extraerDominiosDns(byte[] payload) {
        try {
            if (payload == null || payload.length < 12) return "";
            int qdCount = ((payload[4] & 0xFF) << 8) | (payload[5] & 0xFF);
            if (qdCount == 0) return "";

            // Leer el nombre de la primera pregunta usando el parser con soporte de compresión
            int pos = 12;
            StringBuilder dominio = new StringBuilder();
            while (pos < payload.length) {
                int len = payload[pos] & 0xFF;
                if (len == 0) break;
                // Puntero DNS (no debería haber en preguntas, pero por seguridad)
                if ((len & 0xC0) == 0xC0) break;
                if (dominio.length() > 0) dominio.append(".");
                if (pos + 1 + len > payload.length) break;
                dominio.append(new String(payload, pos + 1, len, StandardCharsets.US_ASCII));
                pos += 1 + len;
            }
            return dominio.toString();
        } catch (Exception ignored) {}
        return "";
    }

    // -----------------------------------------------------------------------
    // Parseo de respuestas DNS: extrae registros A (IP → dominio)
    // y los almacena en dnsCache para identificar sitios reales en tráfico TCP/UDP.
    // -----------------------------------------------------------------------
    /**
     * Lee una respuesta DNS y para cada registro A (IPv4) guarda la relación
     * IP → nombre_de_dominio en dnsCache. Solo se procesa si es una respuesta
     * (bit QR = 1) con al menos un registro de respuesta.
     *
     * Esto permite que cuando llegue tráfico TCP hacia, por ejemplo, 142.250.x.x,
     * podamos mostrar "youtube.com" en lugar de "HTTPS".
     */
    private void procesarRespuestaDns(byte[] payload) {
        try {
            if (payload == null || payload.length < 12) return;
            int flags   = ((payload[2] & 0xFF) << 8) | (payload[3] & 0xFF);
            boolean esRespuesta = (flags & 0x8000) != 0;
            if (!esRespuesta) return;

            int qdCount = ((payload[4] & 0xFF) << 8) | (payload[5] & 0xFF);
            int anCount = ((payload[6] & 0xFF) << 8) | (payload[7] & 0xFF);
            if (anCount == 0) return;

            int pos = 12;

            // 1. Extraer el nombre consultado (question section)
            StringBuilder nombreDominio = new StringBuilder();
            pos = leerNombreDns(payload, pos, nombreDominio);
            if (pos < 0) return;
            String dominio = nombreDominio.toString();
            // Saltar QTYPE y QCLASS de la pregunta (4 bytes por pregunta)
            pos += 4;
            // Si hay más preguntas, saltar también (poco frecuente)
            for (int q = 1; q < qdCount && pos < payload.length; q++) {
                StringBuilder tmp = new StringBuilder();
                pos = leerNombreDns(payload, pos, tmp);
                if (pos < 0) return;
                pos += 4;
            }

            // 2. Leer registros de respuesta
            for (int i = 0; i < anCount && pos < payload.length; i++) {
                // Nombre del recurso (puede ser puntero comprimido)
                StringBuilder tmp = new StringBuilder();
                pos = leerNombreDns(payload, pos, tmp);
                if (pos < 0) return;

                if (pos + 10 > payload.length) break;
                int type     = ((payload[pos] & 0xFF) << 8) | (payload[pos + 1] & 0xFF);
                // class (2) + TTL (4) ya incluidos en los 10 bytes de cabecera
                int rdLength = ((payload[pos + 8] & 0xFF) << 8) | (payload[pos + 9] & 0xFF);
                pos += 10;

                if (type == 1 && rdLength == 4 && pos + 4 <= payload.length) {
                    // Registro A: IPv4
                    String ip = (payload[pos] & 0xFF) + "." + (payload[pos + 1] & 0xFF) + "."
                              + (payload[pos + 2] & 0xFF) + "." + (payload[pos + 3] & 0xFF);
                    if (!dominio.isEmpty()) {
                        dnsCache.put(ip, dominio);
                    }
                } else if (type == 5 && !tmp.toString().isEmpty()) {
                    // CNAME: asociar alias con el dominio original
                    // El RDATA del CNAME es otro nombre, no lo procesamos aquí
                }
                pos += rdLength;
            }
        } catch (Exception ignored) {}
    }

    /**
     * Lee un nombre DNS (con soporte de punteros de compresión) desde payload[pos].
     * Agrega el nombre a sb. Devuelve la posición SIGUIENTE al nombre leído,
     * o -1 si hay error.
     */
    private static int leerNombreDns(byte[] payload, int pos, StringBuilder sb) {
        try {
            int saltos = 0;
            int posFinal = -1;
            while (pos < payload.length) {
                int len = payload[pos] & 0xFF;
                if (len == 0) {
                    pos++;
                    break;
                }
                if ((len & 0xC0) == 0xC0) {
                    // Puntero de compresión
                    if (pos + 1 >= payload.length) return -1;
                    int puntero = ((len & 0x3F) << 8) | (payload[pos + 1] & 0xFF);
                    if (posFinal < 0) posFinal = pos + 2;
                    pos = puntero;
                    if (++saltos > 10) return -1; // evitar bucles infinitos
                } else {
                    pos++;
                    if (pos + len > payload.length) return -1;
                    if (sb.length() > 0) sb.append(".");
                    sb.append(new String(payload, pos, len, StandardCharsets.US_ASCII));
                    pos += len;
                }
            }
            return posFinal >= 0 ? posFinal : pos;
        } catch (Exception e) {
            return -1;
        }
    }

    // -----------------------------------------------------------------------
    // UI Components
    // -----------------------------------------------------------------------
    private JTable table;
    private DefaultTableModel model;
    private TableRowSorter<DefaultTableModel> sorter;

    private JButton btnStart, btnStop, btnExport;
    private JTextField txtSSID;
    private JTextField txtFiltroIP, txtFiltroPuerto;
    private JComboBox<String> comboProtocolo;
    private JLabel lblContador;

    private volatile boolean running = false;
    private PcapHandle handle;

    public NetworkMonitor() {
        setTitle("Network Monitor v3.1 — Captura de Red Completa");
        setSize(1600, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        model = new DefaultTableModel(new Object[]{
            "IP Origen", "IP Destino", "MAC Origen", "MAC Destino",
            "Puerto Ori", "Puerto Des", "Protocolo", "Longitud",
            "Hora Captura", "Nombre de Dispositivos", "Sitio/App Destino",
            "Hostname Remoto", "SSID"
        }, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };

        table = new JTable(model);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setFillsViewportHeight(true);
        sorter = new TableRowSorter<>(model);


        table.setRowSorter(sorter);

        // Anchos de columna
        int[] anchos = {110, 110, 130, 130, 80, 80, 70, 70, 140, 160, 180, 180, 120};
        for (int i = 0; i < anchos.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(anchos[i]);
        }

        add(new JScrollPane(table), BorderLayout.CENTER);

        // Panel NORTE: SSID (auto-detectado, pero editable)
        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        txtSSID = new JTextField(25);
        txtSSID.setEditable(true);
        txtSSID.setToolTipText("SSID detectado automáticamente. Puedes editarlo si es necesario.");
        infoPanel.add(new JLabel("SSID Wi-Fi:"));
        infoPanel.add(txtSSID);
        JButton btnRefreshSSID = new JButton("Actualizar SSID");
        btnRefreshSSID.addActionListener(e -> txtSSID.setText(detectarSSID()));
        infoPanel.add(btnRefreshSSID);

        // Panel de filtros
        JPanel filtroPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        filtroPanel.setBorder(BorderFactory.createTitledBorder("Filtros"));
        comboProtocolo = new JComboBox<>(new String[]{"Todos", "TCP", "UDP"});
        txtFiltroIP     = new JTextField(12);
        txtFiltroPuerto = new JTextField(6);
        filtroPanel.add(new JLabel("Protocolo:"));
        filtroPanel.add(comboProtocolo);
        filtroPanel.add(new JLabel("  IP (origen/destino):"));
        filtroPanel.add(txtFiltroIP);
        filtroPanel.add(new JLabel("  Puerto destino:"));
        filtroPanel.add(txtFiltroPuerto);
        JButton btnFiltrar = new JButton("Aplicar filtros");
        JButton btnLimpiar = new JButton("Limpiar filtros");
        filtroPanel.add(btnFiltrar);
        filtroPanel.add(btnLimpiar);
        btnFiltrar.addActionListener(e -> aplicarFiltros());
        btnLimpiar.addActionListener(e -> limpiarFiltros());

        JPanel panelNorte = new JPanel(new BorderLayout());
        panelNorte.add(infoPanel,   BorderLayout.NORTH);
        panelNorte.add(filtroPanel, BorderLayout.SOUTH);
        add(panelNorte, BorderLayout.NORTH);

        // Panel SUR
        JPanel panel = new JPanel();
        btnStart  = new JButton("Iniciar Captura");
        btnStop   = new JButton("Detener Captura");
        btnExport = new JButton("Exportar CSV");
        lblContador = new JLabel("Paquetes: 0 / " + MAX_PAQUETES);
        lblContador.setFont(lblContador.getFont().deriveFont(Font.BOLD));
        panel.add(btnStart);
        panel.add(btnStop);
        panel.add(btnExport);
        panel.add(new JLabel("   "));
        panel.add(lblContador);
        add(panel, BorderLayout.SOUTH);

        btnStart.addActionListener(e -> startCapture());
        btnStop.addActionListener(e -> stopCapture());
        btnExport.addActionListener(e -> exportCSV());

        // Auto-detectar SSID al abrir la ventana
        SwingUtilities.invokeLater(() -> txtSSID.setText(detectarSSID()));

        setVisible(true);
    }

    // -----------------------------------------------------------------------
    // Filtros
    // -----------------------------------------------------------------------
    private void aplicarFiltros() {
        List<RowFilter<DefaultTableModel, Object>> filtros = new ArrayList<>();
        String proto = (String) comboProtocolo.getSelectedItem();
        if (proto != null && !proto.equals("Todos")) {
            filtros.add(RowFilter.regexFilter("(?i)^" + proto + "$", 6));
        }
        String ip = txtFiltroIP.getText().trim();
        if (!ip.isEmpty()) {
            List<RowFilter<DefaultTableModel, Object>> ipFiltros = new ArrayList<>();
            ipFiltros.add(RowFilter.regexFilter("(?i)" + ip, 0));
            ipFiltros.add(RowFilter.regexFilter("(?i)" + ip, 1));
            filtros.add(RowFilter.orFilter(ipFiltros));
        }
        String puerto = txtFiltroPuerto.getText().trim();
        if (!puerto.isEmpty()) {
            filtros.add(RowFilter.regexFilter("^" + puerto + "$", 5));
        }
        sorter.setRowFilter(filtros.isEmpty() ? null : RowFilter.andFilter(filtros));
    }

    private void limpiarFiltros() {
        comboProtocolo.setSelectedIndex(0);
        txtFiltroIP.setText("");
        txtFiltroPuerto.setText("");
        sorter.setRowFilter(null);
    }

    // -----------------------------------------------------------------------
    // Captura
    // -----------------------------------------------------------------------
    private void startCapture() {
        try {
            List<PcapNetworkInterface> nets = Pcaps.findAllDevs();
            PcapNetworkInterface nif = (PcapNetworkInterface) JOptionPane.showInputDialog(
                this, "Selecciona Interfaz de Red:", "Red",
                JOptionPane.PLAIN_MESSAGE, null, nets.toArray(), null
            );
            if (nif == null) return;

            model.setRowCount(0);
            lblContador.setText("Paquetes: 0 / " + MAX_PAQUETES);
            btnStart.setEnabled(false);
            btnStop.setEnabled(true);

            // Auto-detectar SSID y actualizar campo
            String ssid = detectarSSID();
            txtSSID.setText(ssid);

            // Escaneo ARP para pre-poblar caché de dispositivos
            escanearRedARP();

            handle = nif.openLive(65536, PcapNetworkInterface.PromiscuousMode.PROMISCUOUS, 10);
            running = true;

            new Thread(() -> {
                try {
                    while (running) {
                        Packet packet = handle.getNextPacket();
                        if (packet != null) processPacket(packet);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }, "packet-capture").start();

        } catch (Exception ex) {
            ex.printStackTrace();
            btnStart.setEnabled(true);
        }
    }

    private void stopCapture() {
        running = false;
        try {
            if (handle != null) handle.close();
        } catch (Exception ignored) {}
        btnStart.setEnabled(true);
        btnStop.setEnabled(false);
    }

    // -----------------------------------------------------------------------
    // Procesamiento de paquetes
    // -----------------------------------------------------------------------
    private void processPacket(Packet pkt) {
        if (!pkt.contains(IpV4Packet.class)) return;

        IpV4Packet ip = pkt.get(IpV4Packet.class);
        String ipSrc = ip.getHeader().getSrcAddr().getHostAddress();
        String ipDst = ip.getHeader().getDstAddr().getHostAddress();

        // MAC
        EthernetPacket eth = pkt.contains(EthernetPacket.class) ? pkt.get(EthernetPacket.class) : null;
        final String macSrc = eth != null ? eth.getHeader().getSrcAddr().toString() : "N/A";
        final String macDst = eth != null ? eth.getHeader().getDstAddr().toString() : "N/A";

        String portSrc, portDst, protocol;
        byte[] payload = null;
        boolean esDnsUdp = false;

        if (pkt.contains(TcpPacket.class)) {
            protocol = "TCP";
            TcpPacket tcp = pkt.get(TcpPacket.class);
            portSrc = String.valueOf(tcp.getHeader().getSrcPort().valueAsInt());
            portDst = String.valueOf(tcp.getHeader().getDstPort().valueAsInt());
            if (tcp.getPayload() != null) payload = tcp.getPayload().getRawData();
        } else if (pkt.contains(UdpPacket.class)) {
            protocol = "UDP";
            UdpPacket udp = pkt.get(UdpPacket.class);
            portSrc = String.valueOf(udp.getHeader().getSrcPort().valueAsInt());
            portDst = String.valueOf(udp.getHeader().getDstPort().valueAsInt());
            if (udp.getPayload() != null) payload = udp.getPayload().getRawData();
            esDnsUdp = portDst.equals("53") || portSrc.equals("53");
        } else {
            return;
        }

        int length = pkt.length();
        String horaCaptura = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

        // --- Nombre de Dispositivos ---
        String nombreDispositivo = obtenerNombreDispositivo(ipSrc);
        if (nombreDispositivo.isEmpty()) nombreDispositivo = obtenerNombreDispositivo(ipDst);
        if (nombreDispositivo.isEmpty()) nombreDispositivo = "N/A";

        // --- Alimentar cachés desde tráfico DNS/mDNS ---
        if (payload != null && payload.length > 0) {
            boolean esMdns = portDst.equals("5353") || portSrc.equals("5353");
            if (esDnsUdp) {
                procesarRespuestaDns(payload); // DNS puerto 53: IP externa → dominio real
            }
            if (esMdns) {
                extraerNombreMdns(ipSrc, payload); // mDNS: hostname.local → IP del dispositivo
                procesarRespuestaDns(payload);     // mDNS también puede tener A records de IPs externas
            }
        }

        // --- Sitio/App Destino: dnsCache > SNI (TLS) > DNS query > puerto ---
        String sitioDestino = "";

        // 1. Buscar en caché DNS: ¿ya sabemos a qué dominio pertenece esta IP destino?
        String dominioEnCache = dnsCache.get(ipDst);
        if (dominioEnCache != null && !dominioEnCache.isEmpty()) {
            sitioDestino = dominioEnCache;
        }

        // 2. Si no está en caché, intentar extraer SNI o consulta DNS del payload
        if (sitioDestino.isEmpty() && payload != null && payload.length > 0) {
            if (esDnsUdp) {
                sitioDestino = extraerDominiosDns(payload); // query DNS
            } else {
                sitioDestino = extraerSNI(payload); // TLS Client Hello
            }
        }

        // 3. Fallback: mapeo por puerto conocido
        if (sitioDestino.isEmpty()) {
            try {
                sitioDestino = mapearServicio(Integer.parseInt(portDst));
            } catch (NumberFormatException ignored) {
                sitioDestino = "Puerto " + portDst;
            }
        }

        // --- Hostname Remoto ---
        String hostnameRemoto = resolverHostnameRemoto(ipDst);
        // Si DNS inverso aún no resolvió o falló, usar Sitio/App como referencia
        if (hostnameRemoto.isEmpty()) {
            // Si el sitioDestino es un dominio real (tiene punto), usarlo
            if (sitioDestino.contains(".") && !sitioDestino.startsWith("Puerto ")) {
                hostnameRemoto = sitioDestino;
            } else if (!esRedLocal(ipDst)) {
                hostnameRemoto = "N/A";
            } else {
                hostnameRemoto = "Red-Local";
            }
        }

        // --- SSID ---
        String ssidTexto = txtSSID != null ? txtSSID.getText().trim() : "";
        if (ssidTexto.isEmpty()) ssidTexto = detectarSSID();

        final String fDisp         = nombreDispositivo;
        final String fSitio        = sitioDestino;
        final String fHostRemoto   = hostnameRemoto;
        final String fSsid         = ssidTexto;

        SwingUtilities.invokeLater(() -> {
            model.addRow(new Object[]{
                ipSrc, ipDst, macSrc, macDst,
                portSrc, portDst, protocol, length,
                horaCaptura, fDisp, fSitio, fHostRemoto, fSsid
            });

            int total = model.getRowCount();
            lblContador.setText("Paquetes: " + total + " / " + MAX_PAQUETES);

            if (total >= MAX_PAQUETES) {
                stopCapture();
                lblContador.setText("Paquetes: " + total + " / " + MAX_PAQUETES + "  [LÍMITE ALCANZADO]");
                JOptionPane.showMessageDialog(
                    NetworkMonitor.this,
                    "Se han capturado " + MAX_PAQUETES + " paquetes.\n\n"
                    + "La captura se ha detenido automáticamente.\n"
                    + "Pulsa 'Exportar CSV' para guardar los datos.",
                    "Límite de captura alcanzado",
                    JOptionPane.INFORMATION_MESSAGE
                );
            }
        });
    }

    // -----------------------------------------------------------------------
    // Exportar CSV
    // -----------------------------------------------------------------------
    private void exportCSV() {
        String fecha = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        String nombreArchivo = "captura_red_" + fecha + ".csv";

        try (PrintWriter pw = new PrintWriter(new FileWriter(nombreArchivo))) {
            for (int j = 0; j < model.getColumnCount(); j++) {
                pw.print(model.getColumnName(j));
                if (j < model.getColumnCount() - 1) pw.print(",");
            }
            pw.println();

            for (int i = 0; i < model.getRowCount(); i++) {
                for (int j = 0; j < model.getColumnCount(); j++) {
                    Object val = model.getValueAt(i, j);
                    String cell = val == null ? "" : val.toString();
                    if (cell.contains(",") || cell.contains("\"")) {
                        cell = "\"" + cell.replace("\"", "\"\"") + "\"";
                    }
                    pw.print(cell);
                    if (j < model.getColumnCount() - 1) pw.print(",");
                }
                pw.println();
            }

            JOptionPane.showMessageDialog(this, "CSV generado: " + nombreArchivo);

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error al exportar: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new NetworkMonitor());
    }
}
