/**
 * GoBackNSender.java
 * @author Hugh Han 
 */

import java.io.*;
import java.net.*;
import java.text.*;
import java.util.*;

public class GoBackNSender {

	/* Constants */
	private static final int PAYLOAD     = 1024;  // Maximum payload of 1024 bytes
    private static final int HEADER_SIZE = 3;     // Header size of 3 bytes
	private static final int EOF_FLAG    = 255;	  // Constant to represent end-of-file
	private static final int ACK_FLAG 	 = 1;     // Constant to represent an acknowledgement message
	private static final int PORT_OFFSET = 1; 	  // Port offset used to receive acknowledgements
	
	/* Instance variables */
	private InetAddress address;			// the address to be sent to
	private int port;						// the port to be sent to
	private String filename;				// the name of the file to be sent
	private byte[] fbarr;					// the byte array of the to be sent
	private int timeout;					// the retry timeout
	private int windowSize;					// the window size of each transmission
	private DatagramSocket senderSocket;    // the sender socket
	private DatagramSocket receiverSocket;	// the receiver socket
	private long startTime;
	private long endTime;
	private int retransmissions;
	
	/**
     * Constructor for GoBackNSender.
     * @param hostname    hostname of the address being sent to
     * @param port        port number being sent to
     * @param filename    name of the file to be sent
     * @param timeout 	  retry timeout value
     * @param windowSize  size of each transmission window
     */
	public GoBackNSender(String hostname, int port, String filename, int timeout, int windowSize) throws IOException {
		this.address = InetAddress.getByName(hostname);
		this.port = port;
		this.filename = filename;
		this.timeout = timeout;
		this.windowSize = windowSize;
		this.retransmissions = 0;
	}

	/** Function to start the sender and receiver sockets. */
	public void start() throws SocketException {
		this.senderSocket = new DatagramSocket();
        System.out.println(
            "Sender socket running on " + 
            this.senderSocket.getLocalAddress().toString() + ":" + 
            this.senderSocket.getLocalPort() + "."
        );
        this.receiverSocket = new DatagramSocket(this.port + PORT_OFFSET);
        System.out.println(
            "Receiver socket running on " + 
            this.receiverSocket.getLocalAddress().toString() + ":" + 
            this.receiverSocket.getLocalPort() + "."
        );
	}
	
	/** Function to send the file. */
	public void sendFile() throws IOException {

		/* Start a timer to time the file transmission. */
		this.startTimer();

		/* Initialize a byte-array to represent the file to be sent. */
		File file = new File(this.filename);
	    FileInputStream fistream = new FileInputStream(file);
		this.fbarr = new byte[(int) file.length()];
		fistream.read(this.fbarr);
		fistream.close();
		
		/* Initialize tracking variables. */
		int base = -1;
		int seqNum = 0;
		int finalSeqNum = (int) Math.ceil((double) this.fbarr.length / (double) PAYLOAD) - 1;
		int finalPacketSize = this.fbarr.length - (finalSeqNum * PAYLOAD);
		boolean fileSent = false;
		
		/* Iterate over every message to send. */
		while (fileSent == false) {
			
			while (seqNum - base <= windowSize && seqNum <= finalSeqNum) {
				this.sendPacket(seqNum, finalSeqNum, finalPacketSize);
				++seqNum;
			} 
			
			/* Check for acknowledgements. */
			try {
				base = this.receiveAck(base);
			}  catch (SocketTimeoutException e) {
				seqNum = base + 1;
				System.out.println(
					"Receiving socket at " + this.receiverSocket.getLocalAddress().toString() + 
					":" + this.receiverSocket.getLocalPort() + " timed out."
				);
				System.out.println("resending: { number: " + seqNum + " to " + (seqNum + this.windowSize - 1) + " }");
				++this.retransmissions;
			}

			/* Exit when we reach the final sequence number. */
			if (base == finalSeqNum) {
				fileSent = true;
			}
		}

		/* Close the sockets. */
		this.senderSocket.close();
		this.receiverSocket.close();

		/* End the timer that times the file transmission. */
		this.endTimer();

		System.out.println(this.filename + " successfully sent to " + this.address + ":" + this.port);
	}

	/** 
	 * Function to send a single packet. 
	 * @param seqNum 		   the sequence number of the message to be sent
	 * @param finalSeqNum      the final sequence number of the file
	 * @param finalPacketSize  the final packet size of the file
	 */
	private void sendPacket(int seqNum, int finalSeqNum, int finalPacketSize) throws IOException {
		int flag = (seqNum == finalSeqNum) ? EOF_FLAG : 0;
		int size = (flag != EOF_FLAG) ? PAYLOAD : finalPacketSize;
		
		byte[] message = new byte[HEADER_SIZE + size];
		message[0] = (byte) flag;
		message[1] = (byte) (seqNum >> 8);
		message[2] = (byte) (seqNum);

		System.arraycopy(this.fbarr, seqNum * PAYLOAD, message, HEADER_SIZE, size);

		DatagramPacket packet = new DatagramPacket(message, message.length, this.address, this.port);
		this.senderSocket.send(packet);
		System.out.println("sent     : { number: " + seqNum + ", flag: " + flag + " }");
	}
	
	/** Function to receieve acknowledgements. */
	private int receiveAck(int base) throws IOException, SocketTimeoutException {

		/* Receive an acknowledgement and check the sequence number. */
		int ackSeqNum = base;
        byte[] header = new byte[3];
		this.receiverSocket.setSoTimeout(this.timeout);
		this.receiverSocket.receive(new DatagramPacket(header, header.length));

		/* 
		 * If the acknowledged sequence number is greater than the base, set the base to that value. 
		 * Otherwise, the acknowledgement is incorrect, and we will attempt to re-receive an acknowledgement.
		 */
        int flag = (int) (header[0] & 0xFF);
        if (flag == ACK_FLAG) {
        	ackSeqNum = (int) ((header[1] & 0xFF) << 8 | (header[2] & 0xFF));
        	System.out.println("received : { number: " + ackSeqNum + ", flag: " + flag + " }");
        }
		if (base < ackSeqNum) {
			return ackSeqNum;
		} else {
			return this.receiveAck(base);
		}
	}

	/**
	 * Sets the start time in milliseconds of the file transmission.
	 * @return the start time in milliseconds of the file transmission
	 */
	private long startTimer() {
		this.startTime = System.currentTimeMillis();
		return this.startTime;
	}

	/**
	 * Sets the end time in milliseconds of the file transmission.
	 * @return the end time in milliseconds of the file transmission
	 */
	private long endTimer() {
		this.endTime = System.currentTimeMillis();
		return this.endTime;
	}

	/**
	 * Returns the elapsed time in milliseconds of the file transmission.
	 * @return the elapsed time in milliseconds of the file transmission
	 */
	public long getElapsedTime() {
		return this.endTime - this.startTime;
	}

	/** Function to print the transmission details to stdout. */
	public void printTransmissionDetails() {
		/* Calculate file transfer details. */
        double fsizeKb = (this.fbarr.length) / 1024;
        double transferTime = this.getElapsedTime() / 1000;
        double throughput = fsizeKb / transferTime;

		System.out.println(
            "{" + 
            "\n\tFile Size: " + String.format("%.0f", fsizeKb) + "kb," + 
            "\n\tTransfer Time: " + String.format("%.3f", transferTime) + "s," +
            "\n\tThroughput: " + String.format("%.3f", throughput) + "kb/s," +
            "\n\tRetransmissions: " + this.retransmissions +
            "\n}"
        );
	}

	/** Function to print the usage instructions to the user. */
	public static void printUserErrorMessage() {
		System.err.println("Invalid command line arguments.");
        System.out.println("Usage: java GoBackNSender localhost <port> <filename> <timeout> <window>");
        System.out.println(
            "\tport     - an integer specifying the port number of the receiver socket\n" + 
            "\tfilename - a string specifying the name of the file to be sent\n" +
            "\ttimeout  - an integer specifying the timeout value of the socket\n" +
            "\twindow   - an integer specifying the window size of the transmission\n"
        );
	}
	
	public static void main(String[] args) throws Exception {
		if (args.length != 5) {
            printUserErrorMessage();
        } else {
            try {
                GoBackNSender sender = new GoBackNSender(
                    args[0], 					// String - hostname (localhost)
                    Integer.parseInt(args[1]), 	// int 	  - port number
                    args[2], 					// String - filename
                    Integer.parseInt(args[3]), 	// int 	  - retry timeout
                    Integer.parseInt(args[4])	// int 	  - window size
                );
                sender.start();
                sender.sendFile();
                sender.printTransmissionDetails();
            } catch (NumberFormatException e) {
                printUserErrorMessage();
            } catch (FileNotFoundException e) {
            	System.out.println(args[2] + " does not exist.");
            } catch (IOException e) {
            	e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
	}
}
