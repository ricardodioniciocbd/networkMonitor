/*
 * NetworkMonitor v2.5
 * Captura tráfico de red en tiempo real.
 * Nuevas funciones:
 *   - Columna "Dispositivo Local": resuelve el hostname de IPs dentro de la red local (DNS invertido asíncrono).
 *   - Columna "Sitio/App Destino": extrae el dominio exacto visitado
 *       a) del campo SNI (Server Name Indication) en el "Client Hello" de TLS (puerto 443)
 *       b) de la pregunta en paquetes DNS (puerto 53 UDP)
 *   - Columna "Hostname Remoto": resuelve el hostname de IPs EXTERNAS/REMOTAS usando DNS inverso.
 *       Útil para identificar el servidor remoto al que se conectan los dispositivos de la red.
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
import java.io.FileWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.*;

public class NetworkMonitor extends JFrame {

    // Límite de paquetes por sesión para facilitar la generación de 3 CSV (~100,000 total).
    private static final int MAX_PAQUETES = 33_333;

    // -----------------------------------------------------------------------
    // Caché y pool de hilos para la resolución de nombres (DNS inverso)
    // -----------------------------------------------------------------------
    private final ConcurrentHashMap<String, String> hostCache = new ConcurrentHashMap<>();
    private final ExecutorService resolver = Executors.newFixedThreadPool(4);

    // Prefijos de red local (RFC 1918 + localhost)
    private static boolean esRedLocal(String ip) {
        return ip.startsWith("192.168.")
                || ip.startsWith("10.")
                || ip.startsWith("172.16.") || ip.startsWith("172.17.")
                || ip.startsWith("172.18.") || ip.startsWith("172.19.")
                || ip.startsWith("172.2")   || ip.startsWith("172.3")
                || ip.startsWith("127.")
                || ip.startsWith("169.254.");
    }

    /**
     * Devuelve el hostname de una IP local.
     * Si ya está en caché, respuesta inmediata.
     * Si es nueva, lanza una resolución asíncrona y mientras tanto devuelve la IP.
     */
    private String resolverDispositivoLocal(String ip) {
        if (!esRedLocal(ip)) return "";
        return hostCache.computeIfAbsent(ip, k -> {
            // Primero guardamos la IP como placeholder y en paralelo la resolvemos
            resolver.submit(() -> {
                try {
                    String nombre = InetAddress.getByName(k).getHostName();
                    // Si el nombre = IP misma, no hay hostname registrado
                    hostCache.put(k, nombre.equals(k) ? k : nombre);
                } catch (UnknownHostException e) {
                    hostCache.put(k, k);
                }
            });
            return ip; // mientras se resuelve, muestra la IP
        });
    }

    /**
     * Resuelve el HOSTNAME de una IP remota (externa) usando DNS inverso.
     * Usa el mismo mecanismo de caché que resolverDispositivoLocal pero sin restricción de red local.
     * Solo se intentará resolver si la IP NO es local.
     */
    private String resolverHostnameRemoto(String ip) {
        if (esRedLocal(ip)) return "";
        return hostCache.computeIfAbsent(ip + "_remote", k -> {
            resolver.submit(() -> {
                try {
                    String nombre = InetAddress.getByName(ip).getHostName();
                    // Si el nombre = IP misma, no hay hostname registrado en DNS inverso
                    hostCache.put(k, nombre.equals(ip) ? "" : nombre);
                } catch (UnknownHostException e) {
                    hostCache.put(k, "");
                }
            });
            return ""; // mientras se resuelve, muestra vacío
        });
    }

    // -----------------------------------------------------------------------
    // Extracción de SNI (Server Name Indication) desde TLS Client Hello
    // -----------------------------------------------------------------------
    /**
     * Intenta extraer el dominio del extensión SNI de un paquete TCP cuyo
     * payload comienza con el "Client Hello" de TLS (tipo=0x16, versión TLS 1.x,
     * handshake type=0x01).
     *
     * Retorna el dominio (ej. "youtube.com") o cadena vacía si no lo encuentra.
     */
    private static String extraerSNI(byte[] payload) {
        try {
            if (payload == null || payload.length < 43) return "";
            // TLS record type = 0x16 (Handshake), version 0x03xx
            if ((payload[0] & 0xFF) != 0x16) return "";
            if ((payload[1] & 0xFF) != 0x03)  return "";
            // Handshake type = 0x01 (Client Hello)
            if ((payload[5] & 0xFF) != 0x01)  return "";

            int pos = 43; // posición de Session ID Length

            // Session ID
            if (pos >= payload.length) return "";
            int sessionIdLen = payload[pos] & 0xFF;
            pos += 1 + sessionIdLen;

            // Cipher suites
            if (pos + 2 > payload.length) return "";
            int cipherSuitesLen = ((payload[pos] & 0xFF) << 8) | (payload[pos + 1] & 0xFF);
            pos += 2 + cipherSuitesLen;

            // Compression methods
            if (pos >= payload.length) return "";
            int compressionLen = payload[pos] & 0xFF;
            pos += 1 + compressionLen;

            // Extensions total length
            if (pos + 2 > payload.length) return "";
            int extTotalLen = ((payload[pos] & 0xFF) << 8) | (payload[pos + 1] & 0xFF);
            pos += 2;

            int extEnd = pos + extTotalLen;

            while (pos + 4 <= extEnd && pos + 4 <= payload.length) {
                int extType = ((payload[pos] & 0xFF) << 8) | (payload[pos + 1] & 0xFF);
                int extLen  = ((payload[pos + 2] & 0xFF) << 8) | (payload[pos + 3] & 0xFF);
                pos += 4;

                if (extType == 0x0000) { // SNI extension
                    if (pos + 5 > payload.length) break;
                    // Lista SNI
                    int sniListLen  = ((payload[pos] & 0xFF) << 8) | (payload[pos + 1] & 0xFF);
                    int nameType    = payload[pos + 2] & 0xFF; // 0 = host_name
                    int nameLen     = ((payload[pos + 3] & 0xFF) << 8) | (payload[pos + 4] & 0xFF);
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
    // Extracción de dominio desde paquete DNS (UDP puerto 53)
    // -----------------------------------------------------------------------
    /**
     * Lee el payload de un paquete DNS y extrae el nombre de dominio
     * de la primera pregunta (Question Section).
     * Retorna el dominio o cadena vacía.
     */
    private static String extraerDominiosDns(byte[] payload) {
        try {
            if (payload == null || payload.length < 12) return "";
            // flags: QR bit (bit 15 del word flags) = 0 → query
            int flags = ((payload[2] & 0xFF) << 8) | (payload[3] & 0xFF);
            boolean esQuery = (flags & 0x8000) == 0;
            if (!esQuery) return "";

            int qdCount = ((payload[4] & 0xFF) << 8) | (payload[5] & 0xFF);
            if (qdCount == 0) return "";

            // Leer primera pregunta
            int pos = 12;
            StringBuilder dominio = new StringBuilder();
            while (pos < payload.length) {
                int len = payload[pos] & 0xFF;
                if (len == 0) { pos++; break; }
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

    private boolean running = false;
    private PcapHandle handle;

    public NetworkMonitor() {
        setTitle("Network Monitor v2.5 — Java Swing (con DNS Inverso Remoto)");
        setSize(1500, 560);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Columnas actualizadas: Dispositivo Local + Sitio/App Destino + Hostname Remoto
        model = new DefaultTableModel(new Object[]{
            "IP Origen", "IP Destino", "MAC Origen", "MAC Destino",
            "Puerto Ori", "Puerto Des", "Protocolo", "Longitud",
            "Hora Captura", "Dispositivo Local", "Sitio/App Destino", "Hostname Remoto", "SSID"
        }, 0);

        table = new JTable(model);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);
        add(new JScrollPane(table), BorderLayout.CENTER);

        // Panel NORTE: SSID
        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        txtSSID = new JTextField(20);
        infoPanel.add(new JLabel("SSID Wi-Fi:"));
        infoPanel.add(txtSSID);

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

        // Panel SUR: botones + contador
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
        if (filtros.isEmpty()) {
            sorter.setRowFilter(null);
        } else {
            sorter.setRowFilter(RowFilter.andFilter(filtros));
        }
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
            btnStart.setEnabled(true);

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
            }).start();

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void stopCapture() {
        running = false;
        try {
            if (handle != null) handle.close();
        } catch (Exception ignored) {}
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
        String macSrc = "N/A", macDst = "N/A";
        if (pkt.contains(EthernetPacket.class)) {
            EthernetPacket eth = pkt.get(EthernetPacket.class);
            macSrc = eth.getHeader().getSrcAddr().toString();
            macDst = eth.getHeader().getDstAddr().toString();
        }

        String portSrc, portDst, protocol;
        byte[] payload = null;
        boolean esDnsUdp = false;

        if (pkt.contains(TcpPacket.class)) {
            protocol = "TCP";
            TcpPacket tcp = pkt.get(TcpPacket.class);
            portSrc = "" + tcp.getHeader().getSrcPort().valueAsInt();
            portDst = "" + tcp.getHeader().getDstPort().valueAsInt();
            if (tcp.getPayload() != null) {
                payload = tcp.getPayload().getRawData();
            }
        } else if (pkt.contains(UdpPacket.class)) {
            protocol = "UDP";
            UdpPacket udp = pkt.get(UdpPacket.class);
            portSrc = "" + udp.getHeader().getSrcPort().valueAsInt();
            portDst = "" + udp.getHeader().getDstPort().valueAsInt();
            if (udp.getPayload() != null) {
                payload = udp.getPayload().getRawData();
            }
            esDnsUdp = portDst.equals("53") || portSrc.equals("53");
        } else {
            return;
        }

        int    length      = pkt.length();
        String horaCaptura = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

        // --- Dispositivo Local (DNS inverso asíncrono) ---
        // Buscamos el nombre del equipo LOCAL: primero en Origen, luego en Destino
        String dispositivoLocal = resolverDispositivoLocal(ipSrc);
        if (dispositivoLocal.isEmpty()) {
            dispositivoLocal = resolverDispositivoLocal(ipDst);
        }

        // --- Hostname Remoto (DNS inverso de IPs externas) ---
        // Intentamos resolver el hostname de la IP DESTINO si es externa
        String hostnameRemoto = resolverHostnameRemoto(ipDst);

        // --- Sitio/App Destino (SNI o DNS) ---
        String sitioDestino = "";
        if (payload != null && payload.length > 0) {
            if (esDnsUdp) {
                sitioDestino = extraerDominiosDns(payload);
            } else {
                sitioDestino = extraerSNI(payload);
            }
        }

        String ssidTexto = txtSSID != null ? txtSSID.getText().trim() : "";
        if (ssidTexto.isEmpty()) ssidTexto = "N/A";

        final String fDisp = dispositivoLocal;
        final String fSitio = sitioDestino;
        final String fHostRemoto = hostnameRemoto;

        model.addRow(new Object[]{
            ipSrc, ipDst, macSrc, macDst,
            portSrc, portDst, protocol, length,
            horaCaptura, fDisp, fSitio, fHostRemoto, ssidTexto
        });

        // Actualizar contador y verificar límite
        int total = model.getRowCount();
        SwingUtilities.invokeLater(() ->
            lblContador.setText("Paquetes: " + total + " / " + MAX_PAQUETES)
        );

        if (total >= MAX_PAQUETES) {
            stopCapture();
            SwingUtilities.invokeLater(() -> {
                lblContador.setText("Paquetes: " + total + " / " + MAX_PAQUETES + "  [LÍMITE ALCANZADO]");
                btnStart.setEnabled(false);
                JOptionPane.showMessageDialog(
                    this,
                    "Se han capturado " + MAX_PAQUETES + " paquetes.\n\n"
                    + "La captura se ha detenido automáticamente.\n"
                    + "Por favor, pulsa 'Exportar CSV' para guardar el archivo de hoy,\n"
                    + "luego reinicia el programa mañana para generar el siguiente archivo.",
                    "Límite de captura alcanzado",
                    JOptionPane.INFORMATION_MESSAGE
                );
            });
        }
    }

    // -----------------------------------------------------------------------
    // Exportar CSV
    // -----------------------------------------------------------------------
    private void exportCSV() {
        String fecha = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        String nombreArchivo = "captura_red_" + fecha + ".csv";

        try (PrintWriter pw = new PrintWriter(new FileWriter(nombreArchivo))) {
            // Encabezados
            for (int j = 0; j < model.getColumnCount(); j++) {
                pw.print(model.getColumnName(j));
                if (j < model.getColumnCount() - 1) pw.print(",");
            }
            pw.println();

            // Datos de todas las filas
            for (int i = 0; i < model.getRowCount(); i++) {
                for (int j = 0; j < model.getColumnCount(); j++) {
                    Object val = model.getValueAt(i, j);
                    String cell = val == null ? "" : val.toString();
                    // Escapar comas dentro del valor
                    if (cell.contains(",")) cell = "\"" + cell + "\"";
                    pw.print(cell);
                    if (j < model.getColumnCount() - 1) pw.print(",");
                }
                pw.println();
            }

            JOptionPane.showMessageDialog(this, "CSV generado: " + nombreArchivo);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        new NetworkMonitor();
    }
}
