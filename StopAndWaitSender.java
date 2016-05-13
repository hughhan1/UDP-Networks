/**
 * StopAndWaitSender.java 
 * @author Hugh Han
 */

import java.io.*;
import java.net.*;

public class StopAndWaitSender {

    /* Constants */
    private static final int PAYLOAD     = 1024; // Maximum payload of 1024 bytes
    private static final int HEADER_SIZE = 3;    // Header size of 3 bytes

    /* Instance variables */
    private InetAddress address;                 // the address to be sent to
    private int port;                            // the port to be sent to
    private String filename;                     // the file that will be sent
    private DatagramSocket senderSocket;         // the sender socket
    private int timeout;                         // retry timeout

    /**
     * Constructor for StopAndWaitSender.
     * @param hostname  name of the host
     * @param port      port number
     * @param filename  name of the file to be sent
     */
    public StopAndWaitSender(String hostname, int port, String filename, int timeout) throws IOException {
        this.address = InetAddress.getByName(hostname);
        this.port = port;
        this.filename = filename;
        this.timeout = timeout;
    }

    /* Function to start the sender socket. */
    public void start() throws SocketException {
        this.senderSocket = new DatagramSocket();
        System.out.println(
            "Sender socket running on " + 
            this.senderSocket.getLocalAddress().toString() + ":" + 
            this.senderSocket.getLocalPort() + "."
        );
    }

    public void sendData() throws IOException {

        /* Read in a file using an InputStream and byte array. */
        File file = new File(this.filename); 
        FileInputStream fistream = new FileInputStream(file);
        byte[] fbarr = new byte[(int) file.length()];
        fistream.read(fbarr);

        /* Initialize tracking variables */
        int seqNum = 0;                                     // Indicates the sequence number received
        int ackSeqNum = 0;                                  // Indicates the sequence number to be sent
        int retransmissions = 0;                            // Indicates the number of retransmissions
        boolean eofFlag = false;                            // Indicates the end-of-file
        CustomTimer timer = new CustomTimer(this.timeout);  // Timer to calculate throughput

        /* Iterate over every message to send. */
        for (int i = 0; i < fbarr.length; i += PAYLOAD, ++seqNum) {

            byte[] message = new byte[PAYLOAD + HEADER_SIZE]; // Initialize message
            message[0] = (byte) (seqNum >> 8);                // Set to 1st 8 bits of sequence number
            message[1] = (byte) (seqNum);                     // Set to 2nd 8 bits of sequence number

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
            System.out.println("sent     : { number: " + seqNum + ", flag: " + (eofFlag ? 1 : 0) + " }");

            /* Initialize variables to verify acknowledgements. */
            boolean ackRecievedCorrect = false;
            boolean ackReceived = false;

            while (!ackRecievedCorrect) {
                byte[] ack = new byte[2];
                DatagramPacket ackPacket = new DatagramPacket(ack, ack.length);
                try {
                    this.senderSocket.setSoTimeout(this.timeout);
                    this.senderSocket.receive(ackPacket);
                    ackSeqNum = ((ack[0] & 0xFF) << 8) + (ack[1] & 0xFF);
                    ackReceived = true;
                } catch (SocketTimeoutException e) {
                    ackReceived = false;
                }

                /* Check whether the packet has been acknowledged. */
                if ((ackSeqNum == seqNum) && (ackReceived)) {    
                    ackRecievedCorrect = true;
                    System.out.println("received : { number: " + ackSeqNum + " }");
                } else {
                    System.out.println("resending: { number: " + seqNum + " }");
                    this.senderSocket.send(sendPacket);
                    ++retransmissions;
                }
            }
        }

        /* Close the socket. */
        fistream.close();
        this.senderSocket.close();
        System.out.println(this.filename + " successfully sent to " + this.address + ":" + this.port);

        /* Calculate file transfer details. */
        double fsizeKb = (fbarr.length) / 1024;
        double transferTime = timer.getTimeElapsed() / 1000;
        double throughput = fsizeKb / transferTime;

        /* Write output and finish. */
        System.out.println(
            "{" + 
            "\n\tFile Size: " + String.format("%.0f", fsizeKb) + "kb," + 
            "\n\tTransfer Time: " + String.format("%.3f", transferTime) + "s," +
            "\n\tThroughput: " + String.format("%.3f", throughput) + "kb/s," +
            "\n\tRetransmissions: " + retransmissions +
            "\n}"
        );
    }

    public static void main(String args[]) throws Exception {
        if (args.length != 4) {
            System.err.println("Invalid command line arguments.");
            System.out.println("Usage: java StopAndWaitSender localhost <port> <filename> <timeout>");
            System.exit(1);
        } else {
            try {
                StopAndWaitSender sender = new StopAndWaitSender(args[0], Integer.parseInt(args[1]), args[2], Integer.parseInt(args[3]));
                sender.start();
                sender.sendData();
            } catch (NumberFormatException e) {
                System.err.println("Invalid command line arguments.");
                System.out.println("Usage: java StopAndWaitSender localhost <port> <filename> <timeout>");
                System.out.println(
                    "\tport     - an integer specifying the port number of the receiver socket\n" + 
                    "\tfilename - a string specifying the name of the file to be sent\n" +
                    "\ttimeout  - an integer specifying the timeout value of the socket\n"
                );
                System.exit(1);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
