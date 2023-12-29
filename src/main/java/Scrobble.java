import org.bson.Document;

public class Scrobble {

    long userId;
    String trackName;

    String artistName;

    String albumName;

    int trackDuration;

    long timestamp;

    String albumURL;

    String trackURL;

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public String getTrackName() {
        return trackName;
    }

    public void setTrackName(String trackName) {
        this.trackName = trackName;
    }

    public String getArtistName() {
        return artistName;
    }

    public void setArtistName(String artistName) {
        this.artistName = artistName;
    }

    public String getAlbumName() {
        return albumName;
    }

    public void setAlbumName(String albumName) {
        this.albumName = albumName;
    }

    public int getTrackDuration() {
        return trackDuration;
    }

    public void setTrackDuration(int trackDuration) {
        this.trackDuration = trackDuration;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getAlbumURL() {
        return albumURL;
    }

    public void setAlbumURL(String albumURL) {
        this.albumURL = albumURL;
    }

    public String getTrackURL() {
        return trackURL;
    }

    public void setTrackURL(String trackURL) {
        this.trackURL = trackURL;
    }


    public Scrobble(long usersId, String track, String artist, String album, int duration, long time, String albumLink, String trackLink) {
        userId = usersId;
        trackName = track;
        artistName = artist;
        albumName = album;
        trackDuration = duration;
        timestamp = time;
        albumURL = albumLink;
        trackURL = trackLink;
        //do we need id?
    }


    public String toString()
    {
        return "Artist: " + artistName + " Track: " + trackName + " Album: " + albumName + " Duration: " + trackDuration + " Timestamp: " + timestamp + " AlbumURL: " + albumURL + " Track url: " + trackURL;
    }


    public static Scrobble fromDocument(Document document) {


        long userId = document.getLong("userId");
        String trackName = document.getString("track");
        String artist = document.getString("artist");
        String album = document.getString("album");
        int duration = (int) document.get("duration");
        long timestamp = document.getInteger("timestamp");
        String albumURL = document.getString("albumLink");
        String trackURL = document.getString("trackLink");


        return new Scrobble(userId, trackName, artist, album, duration, timestamp, albumURL, trackURL);
    }
}
