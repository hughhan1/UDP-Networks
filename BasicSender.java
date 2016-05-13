/** 
 * BasicSender.java
 * @author Hugh Han
 */

import java.io.*;
import java.net.*;

public class BasicSender {

    /** Constants. */
    private static final int PAYLOAD     = 1024; // Maximum payload of 1024 bytes
    private static final int HEADER_SIZE = 3;    // Header size of 3 bytes

    /** Instance variables. */
    private InetAddress address;                 // the address to be sent to
    private int port;                            // the port to be sent to
    private String filename;                     // the file that will be sent
    private DatagramSocket senderSocket;         // the sender socket

    /**
     * Constructor for BasicSender.
     * @param hostname  name of the host
     * @param port      port number
     * @param filename  name of the file to be sent
     */
    public BasicSender(String hostname, int port, String filename) throws IOException {
        this.address = InetAddress.getByName(hostname);
        this.port = port;
        this.filename = filename;
    }

    /** Function to start the sender socket. */
    public void start() throws SocketException {
        this.senderSocket = new DatagramSocket();
        System.out.println(
            "Sender socket running on " + 
            this.senderSocket.getLocalAddress().toString() + ":" + 
            this.senderSocket.getLocalPort() + "."
        );
    }

    /** Function to send the specified file to the specified address and port. */
    public void sendData() throws IOException {

        /* Read in a file using an InputStream and byte array. */
        File file = new File(this.filename);
        FileInputStream fistream = new FileInputStream(file);
        byte[] fbarr = new byte[(int) file.length()];
        fistream.read(fbarr);

        /* Initialize tracking variables. */
        int sequenceNum = 0;     // Indicates the sequence number
        boolean eofFlag = false; // Indicates the end-of-file

        /* Iterate over every message to send. */
        for (int i = 0; i < fbarr.length; i += PAYLOAD, ++sequenceNum) {

            byte[] message = new byte[PAYLOAD + HEADER_SIZE]; // Initialize message
            message[0] = (byte) (sequenceNum >> 8);           // Set to 1st 8 bits of sequence number
            message[1] = (byte) (sequenceNum);                // Set to 2nd 8 bits of sequence number

            /* Set EOF flag depending on whether it is the end of the file. */
            if ((i + PAYLOAD) >= fbarr.length) {
                eofFlag = true;
                message[2] = (byte) 1;
            } else {
                eofFlag = false;
                message[2] = (byte) 0;
            }

            /* Populate the packet. */
            if (eofFlag) {
                for (int j = 0;  j < fbarr.length - i; ++j)
                    message[j + 3] = fbarr[i + j];
            } else {
                for (int j = 0; j < PAYLOAD; ++j)
                    message[j + 3] = fbarr[i + j];
            }

            /* Send the packet. */
            DatagramPacket sendPacket = new DatagramPacket(message, message.length, this.address, this.port);
            this.senderSocket.send(sendPacket);
            System.out.println("sent: { number: " + sequenceNum + ", flag: " + (eofFlag ? 1 : 0) + " }");

            /* 
             * Simulate a 5msec propagation delay between each packet.
             * Uncomment the code block below if the propagation delay is being configured using pipes.
             */
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        /* Close the socket, write output, and finish. */
        fistream.close();
        this.senderSocket.close();
        System.out.println(this.filename + " successfully sent to " + this.address + ":" + this.port);
    }

    public static void main(String args[]) throws Exception {
        if (args.length != 3) {
            System.err.println("Invalid command line arguments.");
            System.out.println("Usage: java BasicSender localhost <port> <filename>");
            System.exit(1);
        } else {
            try {
                BasicSender sender = new BasicSender(args[0], Integer.parseInt(args[1]), args[2]);
                sender.start();
                sender.sendData();
            } catch (NumberFormatException e) {
                System.err.println("Invalid command line arguments.");
                System.out.println("Usage: java BasicSender localhost <port> <filename>");
                System.out.println(
                    "\tport     - an integer specifying the port number of the receiver socket\n" + 
                    "\tfilename - a string specifying the name of the file to be sent\n"
                );
                System.exit(1);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
