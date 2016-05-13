/**
 * GoBackNReceiver.java
 * @author Hugh Han
 */

import java.io.*;
import java.net.*;
import java.util.*;

public class GoBackNReceiver {

	/* Constants */
	private static final int PAYLOAD     = 1024;  // Maximum payload of 1024 bytes
    private static final int HEADER_SIZE = 3;     // Header size of 3 bytes
	private static final int EOF_FLAG    = 255;   // Constant to represent end-of-file
	private static final int ACK_FLAG 	 = 1;     // Constant to represent an acknowledgement message
	private static final int PORT_OFFSET = 1; 	  // Port offset used to receive acknowledgements
	
	/* Instance variables */
	private InetAddress address;  			// the address of this receiver socket
	private int port;			  			// the port number of this receiver socket
	private String filename;	  			// the name of the file to be saved as
	private int windowSize;		  			// the window size of each transmission
	private DatagramSocket receiverSocket;  // the receiver socket
	private DatagramSocket senderSocket;    // the sender socket
	
	/**
     * Constructor for GoBackNReceiver.
     * @param hostname    hostname of the receiver socket
     * @param port        port number of the receiver socket
     * @param filename    name of the file to be saved as
     * @param windowSize  size of each transmission window
     */
	public GoBackNReceiver(String hostname, int port, String filename, int windowSize) throws IOException {
		this.address 	= InetAddress.getByName(hostname);
		this.port 		= port;
		this.filename 	= filename;
		this.windowSize = windowSize;
	}

	/** Function to start the sender and receiver sockets. */
	public void start() throws SocketException {
		this.receiverSocket = new DatagramSocket(this.port, this.address);
        System.out.println(
            "Receiver socket running on " + 
            this.receiverSocket.getLocalAddress().toString() + ":" + 
            this.receiverSocket.getLocalPort() + "."
        );
        this.senderSocket = new DatagramSocket();
        System.out.println(
            "Sender socket running on " + 
            this.senderSocket.getLocalAddress().toString() + ":" + 
            this.senderSocket.getLocalPort() + "."
        );
	}
	
	/** Function to receive a file from a sender. */
	public void receiveFile() throws IOException {
		
		/* Initialize a file output stream to write the transmitted file. */
		FileOutputStream fostream = new FileOutputStream(filename);

		/* Initialize a byte-array to represent each message received. */
		byte[] message = new byte[HEADER_SIZE + PAYLOAD];

		/* Initialize tracking variables. */
		int nextSeqNum 	= 0;
		int seqNum 		= 0;
		int flag 		= 0;
        
        /* Iterate over every message received until an end-of-file header is received. */
        do {
        	/* Receive the message and check its header. */
	        this.receiverSocket.receive(new DatagramPacket(message, message.length));
	        flag = (int) (message[0] & 0xFF);
	        seqNum = (int) ((message[1] & 0xFF) << 8 | (message[2] & 0xFF));
	        System.out.println("received : { number: " + seqNum + ", flag: " + flag + " }");

	        /* If the correct sequence number is received, write it to the file. */
            if (seqNum == nextSeqNum) {
	            byte[] data = Arrays.copyOfRange(message, HEADER_SIZE, message.length);
            	fostream.write(data);
                System.out.println("written  : { number: " + seqNum + " }");
            	++nextSeqNum;
            }
    		this.sendAck(nextSeqNum - 1);
        } while ((flag != EOF_FLAG) || (nextSeqNum != seqNum + 1));

        /* Close the file output stream. */
        fostream.close();

        /* Close the sockets. */
		this.senderSocket.close();
        this.receiverSocket.close();

        System.out.println(this.filename + " successfully received.");
	}

	/**
     * Function to send an acknowledgement to a sender.
     * @param ackSeqNum    the most recently acknowledged sequence number
     */
	private void sendAck(int ackSeqNum) throws IOException {
        byte[] ack = new byte[3];
        ack[0] = (byte) ACK_FLAG;
        ack[1] = (byte) (ackSeqNum >> 8);
        ack[2] = (byte) (ackSeqNum);
		DatagramPacket packet = new DatagramPacket(ack, ack.length, this.address, this.port + PORT_OFFSET);
		this.senderSocket.send(packet);
		System.out.println("sent     : { number: " + ackSeqNum + ", flag: " + ACK_FLAG + " }");
	}

	/** Function to print the usage instructions to the user. */
	public static void printUserErrorMessage() {
		System.err.println("Invalid command line arguments.");
        System.out.println("Usage: java GoBackNReceiver <port> <filename> <window>");
        System.out.println(
            "\tport     - an integer specifying the port number this socket\n" + 
            "\tfilename - a string specifying the name of the file to be written\n" +
            "\twindow   - an integer specifying the window size of the transmission\n"
        );
	}
	
	public static void main(String[] args) throws IOException {
		GoBackNReceiver receiver = null;
		boolean inputOkay = false;

		if (args.length != 3) {
			printUserErrorMessage();
			System.exit(1);
		} else {
			try {
				String hostname = "localhost";
				int port 		= Integer.parseInt(args[0]);
				String filename = args[1];
				int windowSize 	= Integer.parseInt(args[2]);

				receiver = new GoBackNReceiver(
					hostname, 
					port, 
					filename, 
					windowSize
				);
				inputOkay = true;
			} catch (NumberFormatException e) {
				printUserErrorMessage();
				System.exit(1);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		if (inputOkay) {
			receiver.start();
			receiver.receiveFile();
		}
	}
}
