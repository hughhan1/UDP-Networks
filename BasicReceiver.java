/** 
 * BasicReceiver.java 
 * @author Hugh Han
 */

import java.io.*;
import java.net.*;

public class BasicReceiver {

    /** Constants. */
    private static final int PAYLOAD     = 1024; // Maximum payload of 1024 bytes
    private static final int HEADER_SIZE = 3;    // Header size of 3 bytes

    /** Instance variables. */
    private InetAddress address;                 // the address of this socket
    private int port;                            // the port of this socket
    private String filename;                     // the file to be received
    private DatagramSocket receiverSocket;       // the receiver socket

    /**
     * Constructor for BasicReceiver.
     * @param hostname  name of the host
     * @param port      port number
     * @param filename  name of the file to be sent
     */
    public BasicReceiver(String hostname, int port, String filename) throws IOException {
        this.address = InetAddress.getByName(hostname);
        this.port = port;
        this.filename = filename;
    }

    /** Function to start the receiver socket. */
    public void start() throws SocketException {
        this.receiverSocket = new DatagramSocket(this.port, this.address);
        System.out.println(
            "Receiver socket running on " + 
            this.receiverSocket.getLocalAddress().toString() + ":" + 
            this.receiverSocket.getLocalPort() + "."
        );
    }

    /** Function to receive a file from a sender. */
    public void receiveData() throws IOException {

        /* Write out a file using an OutputStream and byte array. */
        File file = new File(this.filename);
        FileOutputStream fostream = new FileOutputStream(file);

        int sequenceNum = 0;     // Indicates the sequence number
        boolean eofFlag = false; // Indicates the end-of-file

        /* Iterate over each received message. */
        while (!eofFlag) {
            
            byte[] message = new byte[PAYLOAD + HEADER_SIZE]; // Initialize full message
            byte[] fbarr = new byte[PAYLOAD];                 // Initialize message without header

            /* Receive packet and obtain message. */
            DatagramPacket receivedPacket = new DatagramPacket(message, message.length);
            this.receiverSocket.receive(receivedPacket);
            message = receivedPacket.getData();

            /* Obtain the sequence number. */
            sequenceNum = ((message[0] & 0xFF) << 8) + (message[1] & 0xFF);  

            /* Obtain the flag specifying the end-of-file. */
            if ((message[2] & 0xFF) == 1) {
                eofFlag = true;
            } else {
                eofFlag = false;
            }

            /* Obtain the data from the message. */
            for (int i = 3; i < PAYLOAD + HEADER_SIZE ; ++i) {
                fbarr[i - 3] = message[i];
            }

            /* Write the message to the file and print received message. */
            fostream.write(fbarr);
            System.out.println("received: { number: " + sequenceNum + ", flag: " + (eofFlag ? 1 : 0) + " }");
        }
        
        /* Close the socket, write output, and finish. */
        fostream.close();
        this.receiverSocket.close();
        System.out.println(this.filename + " succesfully received.");
    }

    public static void main(String args[]) throws Exception {
        if (args.length != 2) {
            System.err.println("Invalid command line arguments.");
            System.out.println("Usage: java BasicReceiver <port> <filename>");
            System.exit(1);
        } else {
            try {
                BasicReceiver receiver = new BasicReceiver("localhost", Integer.parseInt(args[0]), args[1]);
                receiver.start();
                receiver.receiveData();
            } catch (NumberFormatException e) {
                System.err.println("Invalid command line arguments.");
                System.out.println("Usage: java BasicReceiver localhost <port> <filename>");
                System.out.println(
                    "\tport     - an integer specifying the port number this socket\n" + 
                    "\tfilename - a string specifying the name of the file to be written\n"
                );
                System.exit(1);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
