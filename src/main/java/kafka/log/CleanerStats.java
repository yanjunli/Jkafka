package kafka.log;/**
 * Created by zhoulf on 2017/4/11.
 */

import kafka.utils.Time;

/**
 * @author
 * @create 2017-04-11 55 15
 **/
public class CleanerStats {
    public Time time;

    /**
     * A simple struct for collecting stats about log cleaning
     */
    public CleanerStats(Time time) {
        this.time = time;
    }

    Long startTime, mapCompleteTime, endTime, bytesRead, bytesWritten, mapBytesRead, mapMessagesRead, messagesRead, messagesWritten = 0L;
    Double bufferUtilization = 0.0d;

    clear();

    public void readMessage(Integer size) {
        messagesRead += 1;
        bytesRead += size;
    }

    public void recopyMessage(Integer size) {
        messagesWritten += 1;
        bytesWritten += size;
    }

    public void indexMessage(Integer size) {
        mapMessagesRead += 1;
        mapBytesRead += size;
    }

    public void indexDone() {
        mapCompleteTime = time.milliseconds();
    }

    public void allDone() {
        endTime = time.milliseconds();
    }

    public Double elapsedSecs = (endTime - startTime) / 1000.0;

    public Double elapsedIndexSecs = (mapCompleteTime - startTime) / 1000.0;

    public void clear() {
        startTime = time.milliseconds();
        mapCompleteTime = -1L;
        endTime = -1L;
        bytesRead = 0L;
        bytesWritten = 0L;
        mapBytesRead = 0L;
        mapMessagesRead = 0L;
        messagesRead = 0L;
        messagesWritten = 0L;
        bufferUtilization = 0.0d;
    }
}

