package com.company;

import gnu.io.*;
import net.sf.json.JSONObject;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Enumeration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainFrame {
    private JPanel panel;
    private JTextArea tx_main;
    private JButton bt_start;
    private JButton bt_stop;
    private JTextField tx_num;

    //this is the object that contains the opened port
    private CommPortIdentifier selectedPortIdentifier = null;
    private SerialPort serialPort = null;

    //input and output streams for sending and receiving data
    private InputStream in = null;

    private int i;

    public MainFrame() {

        tx_num.setText("8");

        bt_start.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);

                if(isNumeric(tx_num.getText())){
                    write("Start");
                    if (connect()) {
                        bt_start.setEnabled(false);
                        bt_stop.setEnabled(true);
                    } else {
                        write("Retry");
                    }
                }else{
                    write("Please enter a number");
                }

            }
        });
        bt_stop.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                write("Stop");
                bt_start.setEnabled(true);
                bt_stop.setEnabled(false);
                disconnect();
            }
        });

    }

    public static void main(String[] args) {

        JFrame frame = new JFrame("MainFrame");
        frame.setContentPane(new MainFrame().panel);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setResizable(false);
        frame.setVisible(true);
    }


    private boolean connect() {

        i = 0;

        if (selectedPortIdentifier == null) {
            write("Searching for port");
            Enumeration<CommPortIdentifier> ports = CommPortIdentifier.getPortIdentifiers();

            if (ports.hasMoreElements()) {
                CommPortIdentifier curPort = ports.nextElement();

                //get only serial ports
                if (curPort.getPortType() == CommPortIdentifier.PORT_SERIAL) {
                    selectedPortIdentifier = curPort;
                }
            }
        }

        if (selectedPortIdentifier == null) {
            write("Error: No port is available");
        } else {
            try {
                if (selectedPortIdentifier.isCurrentlyOwned()) {
                    write("Error: Port is currently in use");
                } else {
                    write("Connect to " + selectedPortIdentifier.getName());
                    CommPort commPort = selectedPortIdentifier.open(this.getClass().getName(), 2000);

                    if (commPort instanceof SerialPort) {
                        serialPort = (SerialPort) commPort;
                        serialPort.setSerialPortParams(9600, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);

                        in = serialPort.getInputStream();
                        serialPort.addEventListener(new SerialReader(in));
                        serialPort.notifyOnDataAvailable(true);
                        return true;

                    } else {
                        write("Error: Only serial ports can be handled");
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }


    public class SerialReader implements SerialPortEventListener {
        private InputStream in;
        private byte[] buffer = new byte[1024];

        public SerialReader(InputStream in) {
            this.in = in;
        }

        public void serialEvent(SerialPortEvent serialPortEvent) {

            if (serialPortEvent.getEventType() == SerialPortEvent.DATA_AVAILABLE) {

                int data;

                try {
                    int len = 0;
                    while ((data = in.read()) > -1) {
                        if (data == '\n') {
                            break;
                        }
                        buffer[len++] = (byte) data;
                    }

                    send(new String(buffer, 0, len).replace(" ", ""));

                    if (i < Integer.parseInt(tx_num.getText())) {
                        i++;
                    } else {
                        reconnect();
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void reconnect() {

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    disconnect();
                    Thread.sleep(20000);
                    connect();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }


    private void disconnect() {
        //close the serial port
        try {

            if (serialPort != null) {
                serialPort.removeEventListener();
                serialPort.close();
            }
            in.close();
            in = null;
            write("Disconnected");
        } catch (Exception e) {
            write("Failed to close");
        }
    }

    private void write(String s) {
        tx_main.append(s + "\n");
        tx_main.setCaretPosition(tx_main.getText().length());
    }


    private void send(final String s) {

        write(s);

        new Thread(new Runnable() {
            @Override
            public void run() {

                try {

                    JSONObject jsonObject = new JSONObject();
                    jsonObject.element("serial", s.substring(1, 2));
                    jsonObject.element("value", s.substring(2));

                    HttpURLConnection connection = (HttpURLConnection) new URL("http://104.131.66.240/erp/scripts/send_electricity.php").openConnection();
                    connection.setDoOutput(true);
                    connection.setDoInput(true);
                    connection.setRequestMethod("POST");
                    connection.setUseCaches(false);
                    connection.setInstanceFollowRedirects(true);
                    connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                    connection.connect();

                    DataOutputStream out = new DataOutputStream(connection.getOutputStream());

                    out.writeBytes(jsonObject.toString());
                    out.flush();
                    out.close();

                    BufferedReader reader = new BufferedReader(new InputStreamReader(
                            connection.getInputStream()));
                    String lines;
                    StringBuilder sb = new StringBuilder("");
                    while ((lines = reader.readLine()) != null) {
                        lines = new String(lines.getBytes(), "utf-8");
                        sb.append(lines);
                    }
                    reader.close();
                    connection.disconnect();

                    jsonObject = JSONObject.fromObject(sb.toString());
                    if (jsonObject.getString("status").equals("succeed")) {
                        write(s.substring(2) + " is accepted");
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }).start();

    }


    public boolean isNumeric(String str){
        Pattern pattern = Pattern.compile("[0-9]*");
        Matcher isNum = pattern.matcher(str);
        return isNum.matches();
    }
}
