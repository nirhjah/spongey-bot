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
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

public class Service {

    static String connectionString = System.getenv("CONNECTION_STRING");
    private static final String BASE_URL = "http://ws.audioscrobbler.com/2.0/";
    private static final String API_KEY = System.getenv().get("API_KEY");

    static void handleException(String errorMessage, Message message) {
        message.getChannel().flatMap(channel -> channel.createMessage(errorMessage)).block();
    }

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


    static JsonNode getJsonNodeFromUrl(ObjectMapper objectMapper, String url, CloseableHttpClient httpClient, Message message) {

        HttpGet request = new HttpGet(url);
        HttpResponse response = null;
        try {
            response = httpClient.execute(request);
        } catch (IOException e) {
            Service.handleException("error: couldn't execute track info url", message);
        }

        System.out.println("API Response: " + response.getStatusLine());
        JsonNode rootNode = null;
        try {
            rootNode = objectMapper.readTree(response.getEntity().getContent());
        } catch (IOException e) {
            Service.handleException("error: couldn't get root node of track info url", message);
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


    static String getUserCurrentTrackArtistName(ObjectMapper objectMapper, CloseableHttpClient httpClient, Message message) {

        String artistName = "";

        String userSessionKey = Service.getUserSessionKey(message);

        String getRecentUserTracksUrl = BASE_URL + "?method=user.getrecenttracks&api_key=" + API_KEY + "&sk=" + userSessionKey + "&format=json";
        System.out.println("recent tracksu rl: " + getRecentUserTracksUrl);

        try {
            JsonNode rootNode =  Service.getJsonNodeFromUrl(objectMapper, getRecentUserTracksUrl, httpClient, message);
            JsonNode trackNode = rootNode.path("recenttracks").path("track");
            JsonNode firstTrackNode = trackNode.get(0);
            JsonNode artistNode = firstTrackNode.path("artist").path("#text");
            artistName = artistNode.asText().replace(" ", "+");
        } catch (Exception e) {
            e.printStackTrace();
        }

        return artistName;
    }



}
