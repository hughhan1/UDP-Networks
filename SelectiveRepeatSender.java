/** 
 * SelectiveRepeatSender.java
 * @author Hugh Han
 */

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ExecutionException;

public class SelectiveRepeatSender {
	
	/* Constants */
	private static final int PAYLOAD        = 1024;  // Maximum payload of 1024 bytes
    private static final int HEADER_SIZE    = 3;     // Header size of 3 bytes
	private static final int EOF_FLAG       = 255;   // Constant to represent end-of-file
	private static final int ACK_FLAG 	    = 1;     // Constant to represent an acknowledgement message
	private static final int PORT_OFFSET    = 1;     // Port offset used to receive acknowledgements
	private static final int TIMEOUT_OFFSET = 2000;  // Timeout offset
	
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
	private Timer timer;
	private HashMap<Integer, TimerTask> timers;
	
	/**
     * Constructor for SelectiveRepeatSender.
     * @param hostname    hostname of the address being sent to
     * @param port        port number being sent to
     * @param filename    name of the file to be sent
     * @param timeout 	  retry timeout value
     * @param windowSize  size of each transmission window
     */
	public SelectiveRepeatSender(String hostname, int port, String filename, int timeout, int windowSize) throws IOException {
		this.address = InetAddress.getByName(hostname);
		this.port = port;
		this.filename = filename;
		this.timeout = timeout;
		this.windowSize = windowSize;
		this.timer = new Timer();
		this.timers = new HashMap<Integer, TimerTask>();
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
		boolean fileSent = false;
		int seqNum = 0;
		int finalSeqNum = (int) Math.ceil((double) this.fbarr.length / (double) PAYLOAD) - 1;
		int finalPacketSize = this.fbarr.length - (finalSeqNum * PAYLOAD);
		
		/* Send until all packets are acknowledged. */
		while (true) {
			this.sendPacket(seqNum, finalSeqNum, finalPacketSize);
			if ((seqNum >= this.windowSize) || (seqNum >= finalSeqNum)) {
				this.receiveAck(seqNum, finalSeqNum, finalPacketSize);
				break;
			} else {
				++seqNum;
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
	public void sendPacket(int seqNum, int finalSeqNum, int finalPacketSize) throws IOException {
		
		int flag = (seqNum == finalSeqNum) ? EOF_FLAG : 0;
		int size = (flag != EOF_FLAG) ? PAYLOAD : finalPacketSize;
		
		byte[] message = new byte[HEADER_SIZE + size];
		message[0] = (byte) flag;
		message[1] = (byte) (seqNum >> 8);
		message[2] = (byte) (seqNum);
		System.arraycopy(fbarr, seqNum * PAYLOAD, message, HEADER_SIZE, size);
		DatagramPacket packet = new DatagramPacket(message, message.length, this.address, this.port);
		
		/* Schedule a packet to be sent. */
		PacketTimerTask packetTimer = new PacketTimerTask(this.senderSocket, packet);
		this.timer.scheduleAtFixedRate(packetTimer, 0, this.timeout);
		System.out.println("sent     : { number: " + seqNum + ", flag: " + flag + " }");

		/* Add the packet timer to a the dictionary of timers. */
		this.timers.put(seqNum, packetTimer);
	}
	
	/** Function to receieve acknowledgements. */
	public void receiveAck(int seqNum, int finalSeqNum, int finalPacketSize) throws IOException {
		
		byte[] header = new byte[3];
		DatagramPacket packet = new DatagramPacket(header, header.length);
		
		while (!this.timers.isEmpty() || seqNum < finalSeqNum) {
			if (seqNum == finalSeqNum && this.timers.size() == 1) {
				/* On the final packet, add a small timeout to prevent the acknowledgement from getting lost. */
				this.receiverSocket.setSoTimeout(TIMEOUT_OFFSET); 
				try {
					this.receiverSocket.receive(packet);
				} catch (SocketTimeoutException e) {
					for (Integer key : this.timers.keySet()) {
						this.timers.get(key).cancel();
						this.timers.remove(key);
					}
					this.timer.cancel();
					this.timer.purge();
					break;
				}
			} else {
				this.receiverSocket.receive(packet);
			}
			
	        int flag = (int) (header[0] & 0xFF);
	        int ackSeqNum = (int) ((header[1] & 0xFF) << 8 | (header[2] & 0xFF));

	        System.out.println("received : { number: " + ackSeqNum + ", flag: " + flag + " }");
	        
	        /* If a packet being sent has already been acknowledged ... */
	        if (flag == ACK_FLAG && (this.timers.get(ackSeqNum) != null)) {
	        	this.timers.get(ackSeqNum).cancel(); // Cancel the task of the packet being sent 
				this.timers.remove(ackSeqNum); 		 // Remove the task of the packet from the dictionary
				
				/* If we are within the window size and have not reached the last packet, schedule the next packet. */
				if (seqNum < finalSeqNum && this.timers.size() < this.windowSize) {
					++seqNum;
					this.sendPacket(seqNum, finalSeqNum, finalPacketSize);
				}
	        } 
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
            "\n}"
        );
	}

	/** Function to print the usage instructions to the user. */
	public static void printUserErrorMessage() {
		System.err.println("Invalid command line arguments.");
        System.out.println("Usage: java SelectiveRepeatSender localhost <port> <filename> <timeout> <window>");
        System.out.println(
            "\tport     - an integer specifying the port number of the receiver socket\n" + 
            "\tfilename - a string specifying the name of the file to be sent\n" +
            "\ttimeout  - an integer specifying the timeout value of the socket\n" +
            "\twindow   - an integer specifying the window size of the transmission\n"
        );
	}
	
	public static void main(String[] args) throws IOException, InterruptedException, ExecutionException {
		if (args.length != 5) {
            printUserErrorMessage();
        } else {
            try {
                SelectiveRepeatSender sender = new SelectiveRepeatSender(
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
                System.exit(1);
            } catch (FileNotFoundException e) {
            	System.out.println(args[2] + " does not exist.");
            } catch (IOException e) {
            	e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
		
		/* Need to tell System to exit due to multithreading. */
		System.exit(0);
	}
}
