package kafka.utils;/**
 * Created by zhoulf on 2017/3/29.
 */

/**
 * @author
 * @create 2017-03-29 11:00
 **/
public class Time {
    public Integer NsPerUs = 1000;
    public Integer UsPerMs = 1000;
    public Integer MsPerSec = 1000;
    public Integer NsPerMs = NsPerUs * UsPerMs;
    public Integer NsPerSec = NsPerMs * MsPerSec;
    public Integer UsPerSec = UsPerMs * MsPerSec;
    public Integer SecsPerMin = 60;
    public Integer MinsPerHour = 60;
    public Integer HoursPerDay = 24;
    public Integer SecsPerHour = SecsPerMin * MinsPerHour;
    public Integer SecsPerDay = SecsPerHour * HoursPerDay;
    public Integer MinsPerDay = MinsPerHour * HoursPerDay;

    public static Long milliseconds() {
        return System.currentTimeMillis();
    }


    public static Long nanoseconds() {
        return System.nanoTime();
    }

    public static void sleep(Long ms) throws InterruptedException {
        Thread.sleep(ms);
    }
}