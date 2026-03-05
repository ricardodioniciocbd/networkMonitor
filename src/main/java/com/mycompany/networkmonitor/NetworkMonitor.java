/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */

package com.mycompany.networkmonitor;

/**
 *
 * @author RICARDO
 */
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class NetworkMonitor extends JFrame {

    private JTable table;
    private DefaultTableModel model;
    private TableRowSorter<DefaultTableModel> sorter;

    private JButton btnStart, btnStop, btnExport;
    private JTextField txtEquipo, txtSSID;
    private JTextField txtFiltroIP, txtFiltroPuerto;
    private JComboBox<String> comboProtocolo;

    private String defaultHostName;
    private boolean running = false;
    private PcapHandle handle;

    public NetworkMonitor() {
        setTitle("Network Monitor - Java Swing");
        setSize(1200, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Columnas: incluye MAC Origen y MAC Destino
        model = new DefaultTableModel(new Object[]{
            "IP Origen", "IP Destino", "MAC Origen", "MAC Destino",
            "Puerto Ori", "Puerto Des", "Protocolo", "Longitud",
            "Hora Captura", "Equipo Local", "SSID"
        }, 0);

        table = new JTable(model);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);
        add(new JScrollPane(table), BorderLayout.CENTER);

        // Panel NORTE: info del equipo y SSID
        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        txtEquipo = new JTextField(15);
        txtSSID   = new JTextField(15);

        try {
            defaultHostName = InetAddress.getLocalHost().getHostName();
        } catch (Exception ex) {
            defaultHostName = "Desconocido";
        }
        txtEquipo.setText(defaultHostName);

        infoPanel.add(new JLabel("Equipo local:"));
        infoPanel.add(txtEquipo);
        infoPanel.add(new JLabel("SSID Wi-Fi:"));
        infoPanel.add(txtSSID);

        // Panel CENTRO-NORTE: filtros
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

        // Panel agrupado norte
        JPanel panelNorte = new JPanel(new BorderLayout());
        panelNorte.add(infoPanel,   BorderLayout.NORTH);
        panelNorte.add(filtroPanel, BorderLayout.SOUTH);
        add(panelNorte, BorderLayout.NORTH);

        // Panel SUR: botones de acción
        JPanel panel = new JPanel();
        btnStart  = new JButton("Iniciar Captura");
        btnStop   = new JButton("Detener Captura");
        btnExport = new JButton("Exportar CSV");

        panel.add(btnStart);
        panel.add(btnStop);
        panel.add(btnExport);
        add(panel, BorderLayout.SOUTH);

        btnStart.addActionListener(e -> startCapture());
        btnStop.addActionListener(e -> stopCapture());
        btnExport.addActionListener(e -> exportCSV());

        setVisible(true);
    }

    // -----------------------------------------------------------------------
    // Filtros sobre la tabla
    // -----------------------------------------------------------------------
    private void aplicarFiltros() {
        List<RowFilter<DefaultTableModel, Object>> filtros = new ArrayList<>();

        String proto = (String) comboProtocolo.getSelectedItem();
        if (proto != null && !proto.equals("Todos")) {
            // Columna 6 = Protocolo
            filtros.add(RowFilter.regexFilter("(?i)^" + proto + "$", 6));
        }

        String ip = txtFiltroIP.getText().trim();
        if (!ip.isEmpty()) {
            // Columnas 0 (IP Origen) y 1 (IP Destino)
            List<RowFilter<DefaultTableModel, Object>> ipFiltros = new ArrayList<>();
            ipFiltros.add(RowFilter.regexFilter("(?i)" + ip, 0));
            ipFiltros.add(RowFilter.regexFilter("(?i)" + ip, 1));
            filtros.add(RowFilter.orFilter(ipFiltros));
        }

        String puerto = txtFiltroPuerto.getText().trim();
        if (!puerto.isEmpty()) {
            // Columna 5 = Puerto Des
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
                this,
                "Selecciona Interfaz de Red:",
                "Red",
                JOptionPane.PLAIN_MESSAGE,
                null,
                nets.toArray(), null
            );

            if (nif == null) return;

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

    private void processPacket(Packet pkt) {

        // Solo paquetes IPv4
        if (!pkt.contains(IpV4Packet.class)) return;

        IpV4Packet ip = pkt.get(IpV4Packet.class);
        String ipSrc = ip.getHeader().getSrcAddr().getHostAddress();
        String ipDst = ip.getHeader().getDstAddr().getHostAddress();

        // MAC Origen y Destino (desde cabecera Ethernet si está disponible)
        String macSrc = "N/A";
        String macDst = "N/A";
        if (pkt.contains(EthernetPacket.class)) {
            EthernetPacket eth = pkt.get(EthernetPacket.class);
            macSrc = eth.getHeader().getSrcAddr().toString();
            macDst = eth.getHeader().getDstAddr().toString();
        }

        String portSrc;
        String portDst;
        String protocol;

        // Solo TCP/UDP
        if (pkt.contains(TcpPacket.class)) {
            protocol = "TCP";
            TcpPacket tcp = pkt.get(TcpPacket.class);
            portSrc = "" + tcp.getHeader().getSrcPort().valueAsInt();
            portDst = "" + tcp.getHeader().getDstPort().valueAsInt();
        } else if (pkt.contains(UdpPacket.class)) {
            protocol = "UDP";
            UdpPacket udp = pkt.get(UdpPacket.class);
            portSrc = "" + udp.getHeader().getSrcPort().valueAsInt();
            portDst = "" + udp.getHeader().getDstPort().valueAsInt();
        } else {
            return;
        }

        int length = pkt.length();
        String horaCaptura = new SimpleDateFormat("HH:mm:ss").format(new Date());

        String equipoTexto = txtEquipo != null ? txtEquipo.getText().trim() : "";
        if (equipoTexto.isEmpty()) {
            equipoTexto = (defaultHostName != null && !defaultHostName.isEmpty())
                ? defaultHostName : "Desconocido";
        }

        String ssidTexto = txtSSID != null ? txtSSID.getText().trim() : "";
        if (ssidTexto.isEmpty()) ssidTexto = "N/A";

        model.addRow(new Object[]{
            ipSrc, ipDst, macSrc, macDst,
            portSrc, portDst, protocol, length,
            horaCaptura, equipoTexto, ssidTexto
        });
    }

    // -----------------------------------------------------------------------
    // Exportar CSV — nombre incluye la fecha del día (ej. captura_red_2026-03-04.csv)
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

            // Datos (todas las filas del modelo, independientemente del filtro activo)
            for (int i = 0; i < model.getRowCount(); i++) {
                for (int j = 0; j < model.getColumnCount(); j++) {
                    pw.print(model.getValueAt(i, j));
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
