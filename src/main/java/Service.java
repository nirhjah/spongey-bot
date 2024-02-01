import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.*;
import com.mongodb.client.model.Filters;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.rest.util.Color;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.io.IOException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.regex.Pattern;

public class Service {

    static String connectionString = System.getenv("CONNECTION_STRING");
    private static final String BASE_URL = "http://ws.audioscrobbler.com/2.0/";
    private static final String API_KEY = System.getenv().get("API_KEY");

    static EmbedCreateSpec.Builder createEmbed(String name) {

        return EmbedCreateSpec.builder()
                .color(Color.LIGHT_SEA_GREEN)
                .author(name, "https://discord4j.com", null);
    }

    static int getArtistPlays(long userid, String artist, GatewayDiscordClient client) {
        int artistPlays;

        String username = client.getUserById(Snowflake.of(userid))
                .block()
                .getUsername();


        MongoClient mongoClient = MongoClients.create(connectionString);
        MongoDatabase database = mongoClient.getDatabase("spongeybot");
        MongoCollection<Document> userScrobbles = database.getCollection(username);
        Bson artistFilter = Filters.all("artist", artist);

        artistPlays = (int) userScrobbles.countDocuments(artistFilter);

        return artistPlays;


    }


    static JsonNode getJsonNodeFromUrl(ObjectMapper objectMapper, String url, CloseableHttpClient httpClient) {

        HttpGet request = new HttpGet(url);
        HttpResponse response = null;
        try {
            response = httpClient.execute(request);
        } catch (IOException e) {
            System.out.println("error: couldn't execute track info url");
        }

        System.out.println("API Response: " + response.getStatusLine());
        JsonNode rootNode = null;
        try {
            rootNode = objectMapper.readTree(response.getEntity().getContent());
        } catch (IOException e) {
            System.out.println("error: couldn't get root node of track info url");
        }

        return rootNode;
    }


    static long convertToUnixTimestamp(LocalDateTime localDateTime) {
        return localDateTime.atZone(ZoneId.systemDefault()).toEpochSecond();
    }


    static List<ZonedDateTime> calculateTimePeriod(String timePeriod, ZonedDateTime currentTimeInTimezone, long userRegsiteredTimestamp, ZoneId userTimeZone) {
        ZonedDateTime firstMinuteOfPeriod;
        ZonedDateTime lastMinuteOfPeriod;

        switch (timePeriod) {
            case "today":
                lastMinuteOfPeriod = currentTimeInTimezone.with(LocalDateTime.of(currentTimeInTimezone.toLocalDate(), LocalTime.MAX));
                firstMinuteOfPeriod = currentTimeInTimezone.with(LocalDateTime.of(currentTimeInTimezone.toLocalDate(), LocalTime.MIDNIGHT));
                break;

            case "currentweek":
                firstMinuteOfPeriod = currentTimeInTimezone.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).with(LocalTime.MIDNIGHT);
                lastMinuteOfPeriod = currentTimeInTimezone.with(TemporalAdjusters.next(DayOfWeek.SUNDAY)).with(LocalTime.MAX);
                break;

            case "lastweek":
                firstMinuteOfPeriod = currentTimeInTimezone.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY)).minusDays(6).with(LocalTime.MIDNIGHT);
                lastMinuteOfPeriod = currentTimeInTimezone.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY)).with(LocalTime.MAX);
                break;

            case "month":
                firstMinuteOfPeriod = currentTimeInTimezone.with(TemporalAdjusters.firstDayOfMonth()).with(LocalTime.MIDNIGHT);
                lastMinuteOfPeriod = currentTimeInTimezone.with(TemporalAdjusters.lastDayOfMonth()).with(LocalTime.MAX);
                break;

            case "2024":
                firstMinuteOfPeriod = currentTimeInTimezone.with(TemporalAdjusters.firstDayOfYear()).with(LocalTime.MIDNIGHT);
                lastMinuteOfPeriod = currentTimeInTimezone.with(TemporalAdjusters.lastDayOfYear()).with(LocalTime.MAX);
                break;

            case "2023":
                firstMinuteOfPeriod = currentTimeInTimezone.with(TemporalAdjusters.firstDayOfYear()).withYear(2023).with(LocalTime.MIDNIGHT);
                lastMinuteOfPeriod = currentTimeInTimezone.with(TemporalAdjusters.lastDayOfYear()).withYear(2023).with(LocalTime.MAX);
                break;


            case "2022":
                firstMinuteOfPeriod = currentTimeInTimezone.with(TemporalAdjusters.firstDayOfYear()).withYear(2022).with(LocalTime.MIDNIGHT);
                lastMinuteOfPeriod = currentTimeInTimezone.with(TemporalAdjusters.lastDayOfYear()).withYear(2022).with(LocalTime.MAX);
                break;


            case "2021":
                firstMinuteOfPeriod = currentTimeInTimezone.with(TemporalAdjusters.firstDayOfYear()).withYear(2021).with(LocalTime.MIDNIGHT);
                lastMinuteOfPeriod = currentTimeInTimezone.with(TemporalAdjusters.lastDayOfYear()).withYear(2021).with(LocalTime.MAX);
                break;

            case "2020":
                firstMinuteOfPeriod = currentTimeInTimezone.with(TemporalAdjusters.firstDayOfYear()).withYear(2020).with(LocalTime.MIDNIGHT);
                lastMinuteOfPeriod = currentTimeInTimezone.with(TemporalAdjusters.lastDayOfYear()).withYear(2020).with(LocalTime.MAX);
                break;

            case "2019":
                firstMinuteOfPeriod = currentTimeInTimezone.with(TemporalAdjusters.firstDayOfYear()).withYear(2019).with(LocalTime.MIDNIGHT);
                lastMinuteOfPeriod = currentTimeInTimezone.with(TemporalAdjusters.lastDayOfYear()).withYear(2019).with(LocalTime.MAX);
                break;

            case "lifetime":
                Instant instant = Instant.ofEpochSecond(userRegsiteredTimestamp);
                firstMinuteOfPeriod = ZonedDateTime.ofInstant(instant, userTimeZone).with(LocalDateTime.ofInstant(instant, userTimeZone).toLocalDate().atStartOfDay());
                lastMinuteOfPeriod = LocalDate.now().atTime(LocalTime.MAX).atZone(userTimeZone);
                break;

            default:
                throw new IllegalArgumentException("Invalid time period");
        }

        List<ZonedDateTime> result = new ArrayList<>();
        result.add(firstMinuteOfPeriod);
        result.add(lastMinuteOfPeriod);
        return result;
    }


    static int calculateListeningTime(MongoCollection<Document> scrobblesCollection, Bson filter) {
        int listeningTime = 0;

        FindIterable<Document> results = scrobblesCollection.find(filter);


        for (Document doc : results) {
            int trackDurationSeconds = doc.getInteger("duration");
            listeningTime += trackDurationSeconds;
        }


        return listeningTime;
    }


    static String getUserSessionKey(Message message) {

        String userSessionKey = "";


        try (MongoClient mongoClient = MongoClients.create(connectionString)) {
            MongoDatabase database = mongoClient.getDatabase("spongeybot");
            MongoCollection<Document> collection = database.getCollection("users");



            Document query = new Document("userid", message.getAuthor().get().getId().asLong());
            Document result = collection.find(query).first();
            if (result != null) {
                userSessionKey = result.getString("sessionkey");

            }

        }

        return userSessionKey;

    }

    static List<String> getListOfTracksNotListenedTo(String artistName, Set<String> allArtistsTracks, MongoCollection<Document> scrobbles) {
        List<String> tracksNotListenedTo = new ArrayList<>();
        String newArtistname = artistName.replace("+", " ");
        for (String track : allArtistsTracks) {

            Pattern regexPattern = Pattern.compile("^" + Pattern.quote(track) + "$", Pattern.CASE_INSENSITIVE);
            Bson filter = Filters.and(
                    Filters.regex("artist", newArtistname, "i"),
                    Filters.regex("track", regexPattern)
            );
            if (scrobbles.find(filter).first() == null) {
                tracksNotListenedTo.add(track);
            }
        }
        return tracksNotListenedTo;
    }

    static String getFirstTimeListeningToArtist(String artist, String username) {
        MongoClient mongoClient = MongoClients.create(connectionString);
        MongoDatabase database = mongoClient.getDatabase("spongeybot");
        MongoCollection<Document> collection = database.getCollection(username);
        int minTimestamp = (int) (System.currentTimeMillis() / 1000);
        //String artistReal = collection.find(filter).first().getString("artist");
        Bson newFilter = Filters.eq("artist", artist);

        for (Document doc : collection.find(newFilter)) {

            int timestamp = doc.getInteger("timestamp");
            // For min timestamp
            if (timestamp < minTimestamp) {
                minTimestamp = timestamp;
            }

        }

        Instant instant = Instant.ofEpochSecond(minTimestamp);
        LocalDateTime dateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMMM yyyy");
        return dateTime.format(formatter);
    }

    static String getUserCurrentTrackArtistName(ObjectMapper objectMapper, CloseableHttpClient httpClient, Message message) {

        String artistName = "";

        String userSessionKey = Service.getUserSessionKey(message);

        String getRecentUserTracksUrl = BASE_URL + "?method=user.getrecenttracks&api_key=" + API_KEY + "&sk=" + userSessionKey + "&format=json";
        System.out.println("recent tracksu rl: " + getRecentUserTracksUrl);

        try {
            JsonNode rootNode =  Service.getJsonNodeFromUrl(objectMapper, getRecentUserTracksUrl, httpClient);
            JsonNode trackNode = rootNode.path("recenttracks").path("track");
            JsonNode firstTrackNode = trackNode.get(0);
            JsonNode artistNode = firstTrackNode.path("artist").path("#text");
            artistName = artistNode.asText().replace(" ", "+");
        } catch (Exception e) {
            e.printStackTrace();
        }

        return artistName;
    }



    static int getTotalScrobblesForYear(int year, String username, long userid) {

        int scrobbleCounterForYear = 0;

        MongoClient mongoClient = MongoClients.create(connectionString);
        MongoDatabase database = mongoClient.getDatabase("spongeybot");
        MongoCollection<Document> collection = database.getCollection(username);

        Bson filter = Filters.all("userId", userid);

        long startOfGivenYearUTS;
        long endOfGivenYearUTS;

        LocalDateTime givenStartDateTime = LocalDateTime.of(year, Month.JANUARY, 1, 0, 0);
        LocalDateTime givenEndDateTime = LocalDateTime.of(year, Month.DECEMBER, 31, 23, 59);

        startOfGivenYearUTS = Service.convertToUnixTimestamp(givenStartDateTime);
        endOfGivenYearUTS = Service.convertToUnixTimestamp(givenEndDateTime);

        for (Document doc : collection.find(filter)) {
            int timestamp = doc.getInteger("timestamp");

            if (timestamp  < endOfGivenYearUTS && timestamp >= startOfGivenYearUTS) {
                scrobbleCounterForYear += 1;
            }
        }

      return scrobbleCounterForYear;

    }



    static int getTotalArtistsForYear(int year, String username, long userid) {

        MongoClient mongoClient = MongoClients.create(connectionString);
        MongoDatabase database = mongoClient.getDatabase("spongeybot");
        MongoCollection<Document> collection = database.getCollection(username);

        Bson filter = Filters.all("userId", userid);

        long startOfGivenYearUTS;
        long endOfGivenYearUTS;

        LocalDateTime givenStartDateTime = LocalDateTime.of(year, Month.JANUARY, 1, 0, 0);
        LocalDateTime givenEndDateTime = LocalDateTime.of(year, Month.DECEMBER, 31, 23, 59);

        startOfGivenYearUTS = Service.convertToUnixTimestamp(givenStartDateTime);
        endOfGivenYearUTS = Service.convertToUnixTimestamp(givenEndDateTime);
        Set<String> uniqueArtists = new HashSet<>();



        for (Document doc : collection.find(filter)) {
            int timestamp = doc.getInteger("timestamp");
                String artist = doc.getString("artist");

            if (timestamp  < endOfGivenYearUTS && timestamp >= startOfGivenYearUTS && (artist != null)) {
                uniqueArtists.add(artist);
            }

        }

        return uniqueArtists.size();

    }



    static int getTotalTracksForYear(int year, String username, long userid) {

        MongoClient mongoClient = MongoClients.create(connectionString);
        MongoDatabase database = mongoClient.getDatabase("spongeybot");
        MongoCollection<Document> collection = database.getCollection(username);

        Bson filter = Filters.all("userId", userid);

        long startOfGivenYearUTS;
        long endOfGivenYearUTS;

        LocalDateTime givenStartDateTime = LocalDateTime.of(year, Month.JANUARY, 1, 0, 0);
        LocalDateTime givenEndDateTime = LocalDateTime.of(year, Month.DECEMBER, 31, 23, 59);

        startOfGivenYearUTS = Service.convertToUnixTimestamp(givenStartDateTime);
        endOfGivenYearUTS = Service.convertToUnixTimestamp(givenEndDateTime);



        Set<String> uniqueTracks = new HashSet<>();

        for (Document scrobble : collection.find(filter)) {
            String track = scrobble.getString("track");
            String artist = scrobble.getString("artist");
            int timestamp = scrobble.getInteger("timestamp");

            if (timestamp  < endOfGivenYearUTS && timestamp >= startOfGivenYearUTS && (track != null && artist != null)) {
                String uniqueKey = track + "|" + artist;
                uniqueTracks.add(uniqueKey);
            }


        }



        return uniqueTracks.size();

    }

}
