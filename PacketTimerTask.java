/** 
 * PacketTimerTask.java 
 * @author Hugh Han
 */

import java.io.*;
import java.net.*;
import java.util.*;

public class PacketTimerTask extends TimerTask {
	
	private DatagramSocket socket;
	private DatagramPacket packet;
    
	PacketTimerTask(DatagramSocket socket, DatagramPacket packet) {
        this.socket = socket;
        this.packet = packet;
    }

    public void run() {
        try {
            this.socket.send(this.packet);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}