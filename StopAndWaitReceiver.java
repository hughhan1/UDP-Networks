/** 
 * StopAndWaitReceiver.java 
 * @author Hugh Han
 */

import java.io.*;
import java.net.*;

public class StopAndWaitReceiver {

    /** Constants. */
    private static final int PAYLOAD     = 1024; // Maximum payload of 1024 bytes
    private static final int HEADER_SIZE = 3;    // Header size of 3 bytes
    private static final int TIMEOUT     = 0;    // Receiver does not have a timeout.

    /** Instance variables. */
    private InetAddress address;                 // the address of this socket
    private int port;                            // the port of this socket
    private String filename;                     // the file to be received
    private DatagramSocket receiverSocket;       // the receiver socket

    /**
     * Constructor for StopAndWaitReceiver.
     * @param hostname  name of the host
     * @param port      port number
     * @param filename  name of the file to be sent
     */
    public StopAndWaitReceiver(String hostname, int port, String filename) throws IOException {
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

        InetAddress sendAddress; // address to send acknowledgements to.
        int sendPort;            // port to send acknowledgements to.

        // Store sequence number
        int currSeqNum = 0;      // Indicates the current sequence number
        int prevSeqNum = 0;      // Indicates the previous sequence number
        boolean eofFlag = false; // Indicates the end-of-file

        /* Iterate over each received message. */
        while (!eofFlag) {

            byte[] message = new byte[PAYLOAD + HEADER_SIZE]; // Initialize full message
            byte[] fbarr = new byte[PAYLOAD];                 // Initialize message without header

            /* Receive packet and obtain message. */
            DatagramPacket receivedPacket = new DatagramPacket(message, message.length);
            this.receiverSocket.setSoTimeout(TIMEOUT);
            this.receiverSocket.receive(receivedPacket);
            message = receivedPacket.getData();

            sendAddress = receivedPacket.getAddress(); // Obtain the address to send an acknowledgement to.
            sendPort = receivedPacket.getPort();       // Obtain the port to send an acknowledgement to.

            currSeqNum = ((message[0] & 0xFF) << 8) + (message[1] & 0xFF); // Obtain current sequence number

            /* Set the end-of-file flag. */
            if ((message[2] & 0xFF) == 1) {
                eofFlag = true;
            } else {
                eofFlag = false;
            }

            /* Attempt to write the obtained data to the specified file. */
            if (currSeqNum == (prevSeqNum + 1)) {
                prevSeqNum = currSeqNum;
                for (int i = 3; i < PAYLOAD + HEADER_SIZE; ++i) {
                    fbarr[i - 3] = message[i];
                }
                fostream.write(fbarr);
                System.out.println("received : { number: " + currSeqNum + ", flag: " + (eofFlag ? 1 : 0) + " }");
                this.sendAck(prevSeqNum, sendAddress, sendPort);
            } else {
                System.out.println("error    : expected " + (prevSeqNum + 1) + " but received " + currSeqNum + ". Retrying.");
                this.sendAck(prevSeqNum, sendAddress, sendPort);
            }
        }
        
        /* Close the socket, write output, and finish. */
        fostream.close();
        this.receiverSocket.close();
        System.out.println(this.filename + " succesfully received.");
    }

    /**
     * Function to send an acknowledgement to a sender.
     * @param prevSeqNum   the previous sequence number
     * @param sendAddress  the address to which the acknowledgement will be sent
     * @param sendPort     the port to which the acknowledgement will be sent
     */
    public void sendAck(int prevSeqNum, InetAddress sendAddress, int sendPort) throws IOException {
        byte[] ackPacket = new byte[2];
        ackPacket[0] = (byte) (prevSeqNum >> 8);
        ackPacket[1] = (byte) (prevSeqNum);
        DatagramPacket ack = new DatagramPacket(ackPacket, ackPacket.length, sendAddress, sendPort);
        this.receiverSocket.send(ack);
        System.out.println("sent     : { number: " + prevSeqNum + " }");
    }

    public static void main(String args[]) {
        if (args.length != 2) {
            System.err.println("Invalid command line arguments.");
            System.out.println("Usage: java StopAndWaitReceiver <port> <filename>");
            System.exit(1);
        } else {
            try {
                StopAndWaitReceiver receiver = new StopAndWaitReceiver("localhost", Integer.parseInt(args[0]), args[1]);
                receiver.start();
                receiver.receiveData();
            } catch (NumberFormatException e) {
                System.err.println("Invalid command line arguments.");
                System.out.println("Usage: java StopAndWaitReceiver localhost <port> <filename>");
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
