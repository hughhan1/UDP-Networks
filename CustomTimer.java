/** 
 * CustomTimer.java
 * @author Hugh Han
*/

import java.util.Calendar;
import java.util.GregorianCalendar;

public class CustomTimer {

    /** Instance variables. */
    private double startTime;  // Start time in milliseconds.
    private double timeout;    // Timeout in milliseconds.

    /**
     * Public constructor for a CustomTimer.
     * @param timeout  the number of milliseconds before a timeout
     */
    public CustomTimer(double timeout) {
        this.timeout = timeout;
        this.startTime = this.getCurrentTime();
    }

    /**
     * Returns the current time as a double representation.
     * @return the current time as a double
     */
    public double getCurrentTime() {
        Calendar cal = new GregorianCalendar();
        double sec = cal.get(Calendar.SECOND);
        double min = cal.get(Calendar.MINUTE);
        double hour = cal.get(Calendar.HOUR_OF_DAY);
        double msec = cal.get(Calendar.MILLISECOND);
        return msec + (1000 * sec) + (60000 * min) + (3600000 * hour);
    }

    /**
     * Returns the elapsed time as a double representation.
     * @return the elapsed time as a double
     */
    public double getTimeElapsed() {
        return this.getCurrentTime() - this.startTime;
    }

    /**
     * Returns whether the CustomTimer has timed out.
     * @return whether the CustomTimer has timed out
     */
    public boolean timeout() {
        return this.getTimeElapsed() >= timeout;
    }
}
