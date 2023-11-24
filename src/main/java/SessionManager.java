import java.util.HashMap;
import java.util.Map;

public class SessionManager {
    private static final Map<Long, String> userSessions = new HashMap<>();

    public static void storeSessionKey(long userId, String sessionKey) {
        userSessions.put(userId, sessionKey);
    }

    public static Map<Long, String> getUserSessions() {
        return userSessions;
    }


    public static String getSessionKey(long userId) {
        return userSessions.get(userId);
    }

    public static boolean hasSessionKey(long userId) {
        return userSessions.containsKey(userId);
    }

    public void removeSessionKey(long userId) {
        userSessions.remove(userId);
    }
}