import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.*;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClient;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Message;


import discord4j.core.object.entity.User;
import discord4j.core.object.presence.ClientActivity;
import discord4j.core.object.presence.ClientPresence;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.rest.util.Color;
import discord4j.rest.util.Image;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.bson.Document;
import org.bson.conversions.Bson;
import reactor.core.publisher.Mono;


import java.io.*;
import java.net.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;



public class SpongeyBot {

    static String connectionString = System.getenv("CONNECTION_STRING");

    private static final String BOT_TOKEN = System.getenv("BOT_TOKEN");

    private static final String BASE_URL = "http://ws.audioscrobbler.com/2.0/";

    private static final String API_KEY = System.getenv().get("API_KEY");

    private static final String API_SECRET = System.getenv("API_SECRET");


    private static void handleException(String errorMessage, Message message) {
        message.getChannel().flatMap(channel -> channel.createMessage(errorMessage)).block();
    }

    private static EmbedCreateSpec.Builder createEmbed(String name) {

        return EmbedCreateSpec.builder()
                .color(Color.LIGHT_SEA_GREEN)
                .url("https://discord4j.com")
                .author(name, "https://discord4j.com", "https://i.imgur.com/F9BhEoz.png")
                .timestamp(Instant.now());
    }

    private static String getUserSessionKey(Message message) {

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

    private static String getUserCurrentTrackArtistName(ObjectMapper objectMapper, CloseableHttpClient httpClient, Message message) {

        String artistName = "";

        String userSessionKey = getUserSessionKey(message);

        String getRecentUserTracksUrl = BASE_URL + "?method=user.getrecenttracks&api_key=" + API_KEY + "&sk=" + userSessionKey + "&format=json";
        System.out.println("recent tracksu rl: " + getRecentUserTracksUrl);

        try {
            JsonNode rootNode =  getJsonNodeFromUrl(objectMapper, getRecentUserTracksUrl, httpClient, message);
            JsonNode trackNode = rootNode.path("recenttracks").path("track");
            JsonNode firstTrackNode = trackNode.get(0);
            JsonNode artistNode = firstTrackNode.path("artist").path("#text");
            artistName = artistNode.asText().replace(" ", "+");
        } catch (Exception e) {
            e.printStackTrace();
        }

        return artistName;
    }

        private static JsonNode getJsonNodeFromUrl(ObjectMapper objectMapper, String url, CloseableHttpClient httpClient, Message message) {

        HttpGet request = new HttpGet(url);
        HttpResponse response = null;
        try {
            response = httpClient.execute(request);
        } catch (IOException e) {
            handleException("error: couldn't execute track info url", message);
        }

        System.out.println("API Response: " + response.getStatusLine());
        JsonNode rootNode = null;
        try {
            rootNode = objectMapper.readTree(response.getEntity().getContent());
        } catch (IOException e) {
            handleException("error: couldn't get root node of track info url", message);
        }

        return rootNode;
    }


    static Mono<?> testUsername(GatewayDiscordClient client) throws JsonProcessingException, UnsupportedEncodingException {
        MongoClient mongoClient = MongoClients.create(connectionString);
        MongoDatabase database = mongoClient.getDatabase("spongeybot");

        MongoCollection<Document> users = database.getCollection("users");


        for (Document document : users.find()) {
            long userId = document.getLong("userid");


            String username = client.getUserById(Snowflake.of(userId))
                    .block()
                    .getUsername();


            System.out.println("this is user: " + userId + " username: " + username);

            MongoCollection<Document> userScrobblesCollection = database.getCollection(username);
            System.out.println("total scrobbles: " + userScrobblesCollection.countDocuments());


        }


        return Mono.empty();

    }

        static Mono<?> betterupdaterecentcommand(ObjectMapper objectMapper, CloseableHttpClient httpClient, Message message, GatewayDiscordClient client) throws JsonProcessingException, UnsupportedEncodingException {
        MongoClient mongoClient = MongoClients.create(connectionString);
        MongoDatabase database = mongoClient.getDatabase("spongeybot");
        MongoCollection<Document> tracks = database.getCollection("tracks");
            MongoCollection<Document> users = database.getCollection("users");


            String username2 = client.getUserById(Snowflake.of(message.getAuthor().get().getId().asLong()))
                    .block()
                    .getUsername();

            if (!username2.equals("_spongey")) {

                return message.getChannel().flatMap(channel -> channel.createMessage("you do not have permission to run this command"));

            }

            //wrap from here

            for (Document document : users.find()) {
                long userId = document.getLong("userid");
                String sessionKey = document.getString("sessionkey");

           /*     if (userId == 176505480011710464L) {
                    System.out.println("this is viperfan so we skip cuz we dont have his tracks yet");
                    continue;  // Skip this document and move to the next iteration
                }*/

                String username = client.getUserById(Snowflake.of(userId))
                        .block()
                        .getUsername();
                System.out.println("Now updating recent of user: " + userId + " username: " + username);

                 MongoCollection<Document> userScrobblesCollection = database.getCollection(username);





                Document documentWithMaxTimestamp = null;
                int maxTimestamp = 0;

                FindIterable<Document> documents = userScrobblesCollection.find();
                for (Document doc : documents) {
                    // Get the timestamp from the current document
                    int timestamp = doc.getInteger("timestamp");

                    // Check if the current timestamp is greater than the maximum
                    if (timestamp > maxTimestamp) {
                        maxTimestamp = timestamp;
                        documentWithMaxTimestamp = doc;
                    }
                }

                System.out.println("Max timestamp: " + maxTimestamp);
                System.out.println(documentWithMaxTimestamp);



                //GETTING TOTAL PAGES WE HAVE NOW
                JsonNode rootNode = null;
                int totalPages = 0;
                String getRecentUserTracksUrl = BASE_URL + "?method=user.getrecenttracks&limit=200&api_key=" + API_KEY + "&sk=" + sessionKey + "&format=json";

                try {
                    rootNode =  getJsonNodeFromUrl(objectMapper, getRecentUserTracksUrl, httpClient, message);
                    totalPages = Integer.parseInt(rootNode.path("recenttracks").path("@attr").path("totalPages").asText());
                    System.out.println(" total pages: " + totalPages);

                } catch (Exception e) {
                    e.printStackTrace();
                }






                outerLoop:
                for (int i = 1; i <= totalPages; i++) {

                    String urlPerPage = BASE_URL + "?method=user.getrecenttracks&limit=200&page=" + i + "&api_key=" + API_KEY + "&sk=" + sessionKey + "&format=json";
                    JsonNode pageNode = getJsonNodeFromUrl(objectMapper, urlPerPage, httpClient, message);
                    JsonNode listOfTracksForGivenPage = pageNode.get("recenttracks").get("track");

                    for (JsonNode track : listOfTracksForGivenPage) {

                        if (track.has("@attr") && track.get("@attr").has("nowplaying")
                                && track.get("@attr").get("nowplaying").asText().equals("true")) {
                            System.out.println("skipping over this song ebcaues its a nowplaying so doesnt have atime");
                            continue ;
                        }

                        if (Integer.parseInt(track.get("date").get("uts").asText()) > maxTimestamp) {
                            //this is if track isnt a nowplaying and date of track is bigger than latesttimestamp
                            System.out.println("This is a new track: " + track);
                            System.out.println("This is new tracks timestamp: " + Integer.parseInt(track.get("date").get("uts").asText()));

                            System.out.println(track);



                            String artist = track.get("artist").get("#text").asText();
                            String trackName = track.get("name").asText();


                            //first check if track alr saved in tracks table with duration
                            Document foundTrack = tracks.find(
                                    Filters.and(
                                            Filters.eq("track", trackName),
                                            Filters.eq("artist", artist)
                                    )
                            ).maxTime(2, TimeUnit.HOURS).first();

                            String album = track.get("album").get("#text").asText();
                            String trackUrl = track.get("url").asText();
                            String albumUrl = track.get("image").get(2).path("#text").asText();
                            int timestamp = Integer.parseInt(track.get("date").get("uts").asText());

                            int duration;

//this is for saving songs

                            if (foundTrack != null) {
                                duration = Integer.parseInt(foundTrack.get("duration").toString());
                                System.out.println("Found track");
                            } else {
                                System.out.println("Track not found so add into tracks db");


                                String encodedArtistName = URLEncoder.encode(artist, "UTF-8");
                                String encodedTrackName = URLEncoder.encode(trackName, "UTF-8");

                                String trackInfoUrl =  "http://ws.audioscrobbler.com/2.0/?method=track.getInfo&api_key=" + API_KEY +  "&artist=" + encodedArtistName + "&track=" + encodedTrackName + "&format=json";
                                System.out.println("Get duration url: " + trackInfoUrl);
                                JsonNode thisNode = getJsonNodeFromUrl(objectMapper, trackInfoUrl, httpClient, message);
                                System.out.println("track node: " + thisNode.get("track"));
                                int trackDurationSeconds = Integer.parseInt(thisNode.get("track").get("duration").asText())/1000 ;
                                if (trackDurationSeconds == 0) {
                                    trackDurationSeconds = 165;
                                }


                                Document newTrack = new Document("track", trackName)
                                        .append("artist", artist)
                                        .append("duration", trackDurationSeconds);

                                tracks.insertOne(newTrack);

                                duration = trackDurationSeconds;
                            }






                            Document scrobbleDoc = new Document("userId", userId)
                                    .append("track", trackName)
                                    .append("artist", artist)
                                    .append("album", album)
                                    .append("duration", duration)
                                    .append("timestamp", timestamp)
                                    .append("albumLink", albumUrl)
                                    .append("trackLink", trackUrl);

                            System.out.println("NEW DOC WE ARE ADDING: " + scrobbleDoc);

                            userScrobblesCollection.insertOne(scrobbleDoc);

                        } else if (Integer.parseInt(track.get("date").get("uts").asText())  < maxTimestamp) {
                            System.out.println("already got this i think so move on");
                            break outerLoop;
                        }

                    }


                }




                long totalDocuments = userScrobblesCollection.countDocuments();

                System.out.println("Total docs now for " + username  + totalDocuments);

            }



        return Mono.empty();

    }


    static Mono<?> betterUpdateCommand(ObjectMapper objectMapper, CloseableHttpClient httpClient, Message message, GatewayDiscordClient client) throws UnsupportedEncodingException {
        MongoClient mongoClient = MongoClients.create(connectionString);
        MongoDatabase database = mongoClient.getDatabase("spongeybot");
        MongoCollection<Document> tracks = database.getCollection("tracks");
        MongoCollection<Document> userScrobblesCollection = database.getCollection("viperfan"); //todo change


        String username2 = client.getUserById(Snowflake.of(message.getAuthor().get().getId().asLong()))
                .block()
                .getUsername();

        if (!username2.equals("_spongey")) {
            return message.getChannel().flatMap(channel -> channel.createMessage("you do not have permission to run this command"));

        }


        long userId = 176505480011710464L; //THISIS viperfan
        String sessionKey = "WFNgvAF0iiLZ5PET1i82Da8XLNXkKUi8"; //todo change



        //getting total num of pages

        String getRecentUserTracksUrl = BASE_URL + "?method=user.getrecenttracks&limit=200&api_key=" + API_KEY + "&sk=" + sessionKey + "&format=json";
        System.out.println("recent tracksu rl: " + getRecentUserTracksUrl);


        JsonNode rootNode = null;
        int totalPages = 0;
        try {
            rootNode =  getJsonNodeFromUrl(objectMapper, getRecentUserTracksUrl, httpClient, message);
            totalPages = Integer.parseInt(rootNode.path("recenttracks").path("@attr").path("totalPages").asText());
            System.out.println(" total pages: " + totalPages);

        } catch (Exception e) {
            e.printStackTrace();
        }

        //todo change i to whatever number it crashes
        for (int i = 240; i <= totalPages; i++) {
            String urlPerPage = BASE_URL + "?method=user.getrecenttracks&limit=200&page=" + i + "&api_key=" + API_KEY + "&sk=" + sessionKey + "&format=json";
            JsonNode pageNode = getJsonNodeFromUrl(objectMapper, urlPerPage, httpClient, message);

            JsonNode listOfTracksForGivenPage = pageNode.get("recenttracks").get("track");

            for (JsonNode track : listOfTracksForGivenPage) {
                if (!(track.has("@attr") && track.get("@attr").has("nowplaying")
                        && track.get("@attr").get("nowplaying").asText().equals("true"))) {




                    // create scrobble object and add it to collection


                    System.out.println("making a scrobble: ");

                    String artist = track.get("artist").get("#text").asText();
                    String trackName = track.get("name").asText();


                    //first check if track alr saved in tracks table with duration
                    Document foundTrack = tracks.find(
                            Filters.and(
                                    Filters.eq("track", trackName),
                                    Filters.eq("artist", artist)
                            )
                    ).maxTime(2, TimeUnit.HOURS).first();

                    String album = track.get("album").get("#text").asText();
                    String trackUrl = track.get("url").asText();
                    String albumUrl = track.get("image").get(2).path("#text").asText();
                    int timestamp = Integer.parseInt(track.get("date").get("uts").asText());

                    int duration;

//this is for saving songs

                    if (foundTrack != null) {
                        duration = Integer.parseInt(foundTrack.get("duration").toString());
                        System.out.println("Found track");
                    } else {
                        System.out.println("Track not found so add into tracks db");


                        String encodedArtistName = URLEncoder.encode(artist, "UTF-8");
                        String encodedTrackName = URLEncoder.encode(trackName, "UTF-8");

                        String trackInfoUrl = "http://ws.audioscrobbler.com/2.0/?method=track.getInfo&api_key=" + API_KEY + "&artist=" + encodedArtistName + "&track=" + encodedTrackName + "&format=json";
                        System.out.println("Get duration url: " + trackInfoUrl);
                        JsonNode thisNode = getJsonNodeFromUrl(objectMapper, trackInfoUrl, httpClient, message);
                        System.out.println("track node: " + thisNode.get("track"));
                        int trackDurationSeconds = Integer.parseInt(thisNode.get("track").get("duration").asText()) / 1000;
                        if (trackDurationSeconds == 0) {
                            trackDurationSeconds = 165;
                        }


                        Document newTrack = new Document("track", trackName)
                                .append("artist", artist)
                                .append("duration", trackDurationSeconds);

                        tracks.insertOne(newTrack);

                        duration = trackDurationSeconds;
                    }


                    Document scrobbleDoc = new Document("userId", userId)
                            .append("track", trackName)
                            .append("artist", artist)
                            .append("album", album)
                            .append("duration", duration)
                            .append("timestamp", timestamp)
                            .append("albumLink", albumUrl)
                            .append("trackLink", trackUrl);

                    userScrobblesCollection.insertOne(scrobbleDoc);
                    System.out.println("Finished processing track: " + track);

                } else {
                    System.out.println("Track currently playing so not proccessing this " + track);
                }

            }


            System.out.println("we have just finished page: " + i);
            System.out.println("This is url for the page we just finished " + i + ": " + urlPerPage);
        }

        return Mono.empty();
    }

    static Mono<?> testingbetterupdatecommand(ObjectMapper objectMapper, CloseableHttpClient httpClient, Message message, GatewayDiscordClient client) {

        MongoClient mongoClient = MongoClients.create(connectionString);
        MongoDatabase database = mongoClient.getDatabase("spongeybot");
        MongoCollection<Document> userScrobblesCollection = database.getCollection("viperfan");




        long totalDocuments = userScrobblesCollection.countDocuments();
        System.out.println("Total number of documents: " + totalDocuments);


        Document documentWithMaxTimestamp = null;
        int maxTimestamp = 0;

        List<Scrobble> scrobls = new ArrayList<>();

        FindIterable<Document> documents = userScrobblesCollection.find();
        for (Document document : documents) {
            // Get the timestamp from the current document
            int timestamp = document.getInteger("timestamp");

            // Check if the current timestamp is greater than the maximum
            if (timestamp > maxTimestamp) {
                maxTimestamp = timestamp;
                documentWithMaxTimestamp = document;
            }

            Scrobble scrobble = Scrobble.fromDocument(document);
            scrobls.add(scrobble);
        }

        System.out.println(" scrobble size: " + scrobls.size());

        Collections.sort(scrobls, Comparator.comparingLong(Scrobble::getTimestamp));


        System.out.println("Max timestamp: " + maxTimestamp);
        System.out.println(documentWithMaxTimestamp);


        int size = scrobls.size();

        int startIndex = Math.max(0, size - 10);  // Ensure startIndex is not negative

        List<Scrobble> lastTenScrobbles = scrobls.subList(startIndex, size);

// Print the last 5 scrobbles
        for (Scrobble scrobble : lastTenScrobbles) {
            System.out.println(scrobble);
        }


        return Mono.empty();

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


    private static List<ZonedDateTime> calculateTimePeriod(String timePeriod, ZonedDateTime currentTimeInTimezone, long userRegsiteredTimestamp, ZoneId userTimeZone) {
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


    static Mono<?> getTimeLeaderboard(Message message, GatewayDiscordClient client) throws JsonProcessingException, UnsupportedEncodingException {

        String[] command = message.getContent().split(" ");


        String timePeriod = "";
        if (command.length >= 2) {
            timePeriod = command[1];
            System.out.println("timePeriod: " + timePeriod);
        } else {
            return message.getChannel().flatMap(channel -> channel.createMessage("Valid commands: $timelb lifetime $timelb 2024, $timelb month, $timelb currentweek, $timelb lastweek, $timelb today etc"));

        }


        MongoClient mongoClient = MongoClients.create(connectionString);
        MongoDatabase database = mongoClient.getDatabase("spongeybot");
        MongoCollection<Document> userCollection = database.getCollection("users");


        EmbedCreateSpec.Builder embedBuilder = createEmbed("Time leaderboard for period: " + timePeriod);

        Map<String, Integer> userAndTime = new HashMap<>();

        for (Document user : userCollection.find()) {
            int listeningTime = 0;

            long userId = user.getLong("userid");

            String username = client.getUserById(Snowflake.of(userId))
                    .block()
                    .getUsername();


            MongoCollection<Document> scrobblesCollection = database.getCollection(username);


            ZoneId userTimeZone = ZoneId.of(user.getString("timezone"));

            long userRegsiteredTimestamp = Long.parseLong(user.getString("registered"));

            //if command is 'today'
            ZonedDateTime currentTimeInTimezone = ZonedDateTime.now(userTimeZone);

            ZonedDateTime firstMinuteOfPeriod;
            ZonedDateTime lastMinuteOfPeriod;

            try {
                List<ZonedDateTime> firstAndLastTimes =  calculateTimePeriod(timePeriod, currentTimeInTimezone, userRegsiteredTimestamp, userTimeZone);

                firstMinuteOfPeriod = firstAndLastTimes.get(0);
                lastMinuteOfPeriod = firstAndLastTimes.get(1);

            } catch (IllegalArgumentException e) {
                return message.getChannel().flatMap(channel -> channel.createMessage("Valid periods: today, currentweek, lastweek, month, 2024, lifetime"));
            }

            long utsStartOfPeriod = firstMinuteOfPeriod.toInstant().getEpochSecond();
            long utsEndOfPeriod = lastMinuteOfPeriod.toInstant().getEpochSecond();


            Bson filter = Filters.and(
                    Filters.gte("timestamp", utsStartOfPeriod),
                    Filters.lt("timestamp", utsEndOfPeriod)
            );

            listeningTime = calculateListeningTime(scrobblesCollection, filter);
            userAndTime.put(username, listeningTime);
        }


        List<Map.Entry<String, Integer>> sortedTimes = new LinkedList<>(userAndTime.entrySet());
        Collections.sort(sortedTimes, Map.Entry.<String, Integer>comparingByValue().reversed());



        int count = 1;
        StringBuilder sortedTimesString = new StringBuilder();
        for (Map.Entry<String, Integer> entry : sortedTimes) {


            //listening time is in seconds, the method returned seconds

            int newTime;
            String timeType = " minutes";


            newTime = entry.getValue()/60;
            if (entry.getValue() >= 3660) { //if time above 60 mins/3660 seconds
                //convert to hours
                newTime = entry.getValue()/3600;
                timeType = " hours";

                if (newTime >= 24) {
                    newTime = entry.getValue()/86400;
                    timeType = " days";

                }


            }

            sortedTimesString.append(count).append(". ").append(entry.getKey()).append(": ").append(newTime).append(timeType).append("\n");
            count++;

        }

        embedBuilder.addField("", sortedTimesString.toString(), false);

        return message.getChannel().flatMap(channel -> channel.createMessage(embedBuilder.build()));
    }


        static Mono<?> getDailyListeningTime(Message message, GatewayDiscordClient client) throws JsonProcessingException, UnsupportedEncodingException {


        String[] command = message.getContent().split(" ");


        String timePeriod = "";
        if (command.length >= 2) {
            timePeriod = command[1];
            System.out.println("timePeriod: " + timePeriod);
        } else {
            return message.getChannel().flatMap(channel -> channel.createMessage("Valid commands: $timelb lifetime $timelb 2024, $timelb 2023, $timelb 2022, $timelb today, $timelb currentweek, $timelb lastweek, $timelb month etc"));

        }


        MongoClient mongoClient = MongoClients.create(connectionString);
        MongoDatabase database = mongoClient.getDatabase("spongeybot");
        MongoCollection<Document> userCollection = database.getCollection("users");

        String username = client.getUserById(Snowflake.of(message.getAuthor().get().getId().asLong()))
                .block()
                .getUsername();

        long userid = message.getAuthor().get().getId().asLong();

        if (!message.getUserMentions().isEmpty()) {
            username = message.getUserMentions().get(0).getUsername();
            userid = message.getUserMentions().get(0).getId().asLong();
        }

        MongoCollection<Document> scrobblesCollection = database.getCollection(username);

        Document userDocument = userCollection.find(Filters.eq("userid", userid)).first();

        ZoneId userTimeZone = ZoneId.of(userDocument.getString("timezone"));

        long userRegsiteredTimestamp = Long.parseLong(userDocument.getString("registered"));


        int listeningTime;



        //if command is 'today'
        ZonedDateTime currentTimeInTimezone = ZonedDateTime.now(userTimeZone);



            ZonedDateTime firstMinuteOfPeriod;
            ZonedDateTime lastMinuteOfPeriod;

            try {
                List<ZonedDateTime> firstAndLastTimes =  calculateTimePeriod(timePeriod, currentTimeInTimezone, userRegsiteredTimestamp, userTimeZone);

                firstMinuteOfPeriod = firstAndLastTimes.get(0);
                lastMinuteOfPeriod = firstAndLastTimes.get(1);

            } catch (IllegalArgumentException e) {
                return message.getChannel().flatMap(channel -> channel.createMessage("Valid periods: today, currentweek, lastweek, month, 2024, lifetime"));
            }


        System.out.println("This is first minute: " + firstMinuteOfPeriod);
        System.out.println("This is last minute: " +  lastMinuteOfPeriod);

        long utsStartOfPeriod = firstMinuteOfPeriod.toInstant().getEpochSecond();

        long utsEndOfPeriod = lastMinuteOfPeriod.toInstant().getEpochSecond();


        Bson filter = Filters.and(
                Filters.gte("timestamp", utsStartOfPeriod),
                Filters.lt("timestamp", utsEndOfPeriod)
        );



        listeningTime = calculateListeningTime(scrobblesCollection, filter)/60;


        int finalListeningTime = listeningTime;
        String finalTimePeriod = timePeriod;
        ZonedDateTime finalFirstMinuteOfPeriod = firstMinuteOfPeriod;
        ZonedDateTime finalLastMinuteOfPeriod = lastMinuteOfPeriod;

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMMM yyyy");

        String formattedDateFirst = finalFirstMinuteOfPeriod.format(formatter);
        String formattedDateLast = finalLastMinuteOfPeriod.format(formatter);

        return message.getChannel().flatMap(channel -> channel.createMessage("Time spent listening to music for " + finalTimePeriod + ": " + finalListeningTime + " minutes\n this is between " + formattedDateFirst + " and " + formattedDateLast));
    }

    static Mono<?> blendWithUser(Message message, GatewayDiscordClient client) {


        String username = client.getUserById(Snowflake.of(message.getAuthor().get().getId().asLong()))
                .block()
                .getUsername();


        String username2;

        if (!message.getUserMentions().isEmpty()) {
            username2 = message.getUserMentions().get(0).getUsername();
        } else {
            return message.getChannel().flatMap(channel -> channel.createMessage("You must mention another user to blend with"));

        }

        System.out.println("BLENDING WITH USER" + username2);



        MongoClient mongoClient = MongoClients.create(connectionString);
        MongoDatabase database = mongoClient.getDatabase("spongeybot");
        MongoCollection<Document> collection1 = database.getCollection(username);
        MongoCollection<Document> collection2 = database.getCollection(username2);


        EmbedCreateSpec.Builder embedBuilder = createEmbed("Blend of " + username + " with " + username2 + " requested by " + username);

        Map<List<String>, Integer> topTracks1 = new HashMap<>();
        Map<List<String>, Integer> topTracks2 = new HashMap<>();

// Populate your maps with data

        String trackname = "";
        String artistname = "";
        int yourListens = 0;
        int otherPersonsListens = 0;

        for (Document scrobble : collection1.find()) {
            List<String> trackArtistPair = Arrays.asList(scrobble.getString("track"), scrobble.getString("artist"));
            topTracks1.put(trackArtistPair, topTracks1.getOrDefault(trackArtistPair, 0) + 1);
        }

        for (Document scrobble : collection2.find()) {
            List<String> trackArtistPair = Arrays.asList(scrobble.getString("track"), scrobble.getString("artist"));
            topTracks2.put(trackArtistPair, topTracks2.getOrDefault(trackArtistPair, 0) + 1);
        }


        List<Map.Entry<List<String>, Integer>> toptracks1List = new ArrayList<>(topTracks1.entrySet());
        Collections.sort(toptracks1List, Map.Entry.<List<String>, Integer>comparingByValue().reversed());

        List<Map.Entry<List<String>, Integer>> toptracks2List = new ArrayList<>(topTracks2.entrySet());
        Collections.sort(toptracks2List, Map.Entry.<List<String>, Integer>comparingByValue().reversed());

        System.out.println(toptracks1List.size());
        System.out.println(toptracks2List.size());

        int i = 0;
        int j = 0;

        while (i < toptracks1List.size() && j < toptracks2List.size()) {
            Map.Entry<List<String>, Integer> entry1 = toptracks1List.get(i);
            Map.Entry<List<String>, Integer> entry2 = toptracks2List.get(j);

            List<String> trackArtistPair1 = entry1.getKey();
            List<String> trackArtistPair2 = entry2.getKey();

            if (trackArtistPair1.equals(trackArtistPair2)) {
                // Found a matching track
                trackname = trackArtistPair1.get(0);
                artistname = trackArtistPair1.get(1);
                yourListens = entry1.getValue();
                otherPersonsListens = entry2.getValue();
                break;
            } else if (entry1.getValue() > entry2.getValue()) {
                // Move to the next track in toptracks1List
                i++;
                System.out.println("this is i: " + i);
            } else {
                // Move to the next track in toptracks2List
                j++;
                System.out.println("this is j: " + j);

            }
        }

     /*   for (Map.Entry<List<String>, Integer> entry1 : topTracks1.entrySet()) {
            List<String> trackArtistPair = entry1.getKey();
            if (topTracks2.containsKey(trackArtistPair)) {
                trackname = trackArtistPair.get(0);
                artistname = trackArtistPair.get(1);
                yourListens = entry1.getValue();
                otherPersonsListens = topTracks2.get(trackArtistPair);
                break;
            }
        }*/

        embedBuilder.addField("ur top matching track with " + username2, "artist: " + artistname + " track: " + trackname + username + "'s listens: " + yourListens + " " + username2 + "'s listens: " + otherPersonsListens, false);

        //this is all for artist stuff do it later plz
        //get artistcount for both users
     /*   Map<String, Integer> top100Artists1 = new HashMap<>();
        Map<String, Integer> top100Artists2 = new HashMap<>();

        for (Document scrobble : collection1.find()) {
            int currentCount = top100Artists1.getOrDefault(scrobble.getString("artist"), 0);
            top100Artists1.put(scrobble.getString("artist"), currentCount + 1);

        }


        for (Document scrobble : collection2.find()) {
            int currentCount = top100Artists2.getOrDefault(scrobble.getString("artist"), 0);
            top100Artists2.put(scrobble.getString("artist"), currentCount + 1);

        }



        List<Map.Entry<String, Integer>> top100List1 = new ArrayList<>(top100Artists1.entrySet());
        Collections.sort(top100List1, Map.Entry.<String, Integer>comparingByValue().reversed());
        List<Map.Entry<String, Integer>> first100Entries1 = top100List1.subList(0, Math.min(100, top100List1.size()));


        List<Map.Entry<String, Integer>> top100List2 = new ArrayList<>(top100Artists2.entrySet());
        Collections.sort(top100List2, Map.Entry.<String, Integer>comparingByValue().reversed());
        List<Map.Entry<String, Integer>> first100Entries2 = top100List2.subList(0, Math.min(100, top100List2.size()));

        Map<String, Integer> otherPersonsMap = new HashMap<>();


        List<Map.Entry<String, Integer>> sendersList = first100Entries1.stream()
                .filter(entry1 -> first100Entries2.stream()
                        .anyMatch(entry2 -> entry2.getKey().equals(entry1.getKey())))
                .collect(Collectors.toList());

        for (Map.Entry<String, Integer> entry : sendersList) {
            String key = entry.getKey();


            for (Map.Entry<String, Integer> entry1 : first100Entries2) {
                if (entry1.getKey().equals(key)) {
                    otherPersonsMap.put(key, entry1.getValue());
                    break;
                }
            }




        }


        List<Map.Entry<String, Integer>> otherPersonsList = new ArrayList<>(otherPersonsMap.entrySet());


        Integer valueFromList1 = null;
        Integer valueFromList2 = null;

        int count = 0;
        for (Map.Entry<String, Integer> senderEntry : sendersList) {
            String senderKey = senderEntry.getKey();

            // Find the corresponding entry in otherPersonsList
            for (Map.Entry<String, Integer> otherEntry : otherPersonsList) {
                if (otherEntry.getKey().equals(senderKey)) {
                    valueFromList1 = senderEntry.getValue();
                    valueFromList2 = otherEntry.getValue();
                    break; // No need to continue iterating once found
                }
            }

            count++;
            if (count == 10) {
                break; // Stop processing after the first 10 keys
            }



            String moreOrLess;



            int artistPercentage = (int) (((double)(valueFromList1 - valueFromList2) / valueFromList1) * 100);
            if (artistPercentage < 0) {
                moreOrLess = " less ";
            } else {
                moreOrLess = " more ";
            }
            embedBuilder.addField("", senderKey + ": " + username + " has " + artistPercentage + "%" + moreOrLess + " than " + username2 + "", false);
        }
*/




        return message.getChannel().flatMap(channel -> channel.createMessage(embedBuilder.build()));



    }

    static Mono<?> getTopScrobbledDays(Message message, GatewayDiscordClient client) throws JsonProcessingException {



        String username = client.getUserById(Snowflake.of(message.getAuthor().get().getId().asLong()))
                .block()
                .getUsername();

        long userid = message.getAuthor().get().getId().asLong();

        if (!message.getUserMentions().isEmpty()) {
            username = message.getUserMentions().get(0).getUsername();
            userid = message.getUserMentions().get(0).getId().asLong();
        }



        MongoClient mongoClient = MongoClients.create(connectionString);
        MongoDatabase database = mongoClient.getDatabase("spongeybot");
        MongoCollection<Document> collection = database.getCollection(username);
        MongoCollection<Document> users = database.getCollection("users");

        Document userDocument = users.find(Filters.eq("userid", userid)).first();

        Map<String, Integer> topScrobbledDays = new HashMap<>();


        ZoneId userTimeZone = ZoneId.of(userDocument.getString("timezone"));
        ZonedDateTime currentTimeInTimezone = ZonedDateTime.now(userTimeZone);


        System.out.println("this is details");
        System.out.println(username);
        System.out.println(userid);
        System.out.println(userTimeZone);
        System.out.println(currentTimeInTimezone);

        for (Document doc : collection.find()) {
            int duration = doc.getInteger("duration");

            int timestamp = doc.getInteger("timestamp");

            Instant instant = Instant.ofEpochSecond(timestamp);
            ZonedDateTime zonedDateTime = instant.atZone(userTimeZone);
            String firstDate = zonedDateTime.format(DateTimeFormatter.ofPattern("dd MMMM yyyy"));
            int currentCount = topScrobbledDays.getOrDefault(firstDate, 0);
            topScrobbledDays.put(firstDate, currentCount + 1);


        }


        List<Map.Entry<String, Integer>> topScrobbledDaysList = new ArrayList<>(topScrobbledDays.entrySet());
        Collections.sort(topScrobbledDaysList, Map.Entry.<String, Integer>comparingByValue().reversed());


        EmbedCreateSpec.Builder embedBuilder = createEmbed("Top Scrobbled Days");

        int count = 1;
        StringBuilder artistTracksAll = new StringBuilder();
        for (Map.Entry<String, Integer> entry : topScrobbledDaysList) {
            if (entry.getValue() > 350) {
                continue;
            }
            artistTracksAll.append(count).append(". ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            count++;
            if (count > 10) {
                break;
            }
        }

        embedBuilder.addField("top scrobbled days for " + username, artistTracksAll.toString(), false);
        return message.getChannel().flatMap(channel -> channel.createMessage(embedBuilder.build()));


    }


    static Mono<?> getSongListenedToMostOneDay(Message message, GatewayDiscordClient client) throws JsonProcessingException {
        String username = client.getUserById(Snowflake.of(message.getAuthor().get().getId().asLong()))
                .block()
                .getUsername();

        long userid = message.getAuthor().get().getId().asLong();

        if (!message.getUserMentions().isEmpty()) {
            username = message.getUserMentions().get(0).getUsername();
            userid = message.getUserMentions().get(0).getId().asLong();
        }

        Map<List<String>, Integer> mostListenedToInOneDay = new HashMap<>();

        MongoClient mongoClient = MongoClients.create(connectionString);
        MongoDatabase database = mongoClient.getDatabase("spongeybot");
        MongoCollection<Document> collection = database.getCollection(username);

        MongoCollection<Document> users = database.getCollection("users");

        Document userDocument = users.find(Filters.eq("userid", userid)).first();
        ZoneId userTimeZone = ZoneId.of(userDocument.getString("timezone"));


        for (Document doc : collection.find()) {
            int timestamp = doc.getInteger("timestamp");

            Instant instant = Instant.ofEpochSecond(timestamp);
            ZonedDateTime zonedDateTime = instant.atZone(userTimeZone);
            String firstDate = zonedDateTime.format(DateTimeFormatter.ofPattern("dd MMMM yyyy"));

            List<String> trackAndDate = new ArrayList<>();
            trackAndDate.add(doc.getString("track"));
            trackAndDate.add(firstDate);

            int currentCount = mostListenedToInOneDay.getOrDefault(trackAndDate, 0);
            mostListenedToInOneDay.put(trackAndDate, currentCount + 1);


        }

        List<Map.Entry<List<String>, Integer>> mostListenedList = new ArrayList<>(mostListenedToInOneDay.entrySet());
        Collections.sort(mostListenedList, Map.Entry.<List<String>, Integer>comparingByValue().reversed());


        String finalUsername = username;
        return message.getChannel().flatMap(channel -> channel.createMessage(finalUsername + " listened to " + mostListenedList.get(0).getKey().get(0) + " " + mostListenedList.get(0).getValue() + " times on " + mostListenedList.get(0).getKey().get(1)));


    }


    public static List<Double> getNewArtistsAndTracks(List<Document> relevantDocuments, String day, Bson filter, MongoCollection<Document> scrobbleCollection, ZoneId userTimeZone) {

        int newTrackCount = 0;

        int newArtistCount = 0;

        double newTracks;
        double newArtists;


        Map<String, Integer> artistMap = new HashMap<>();
        Map<List<String>, Integer> trackMap = new HashMap<>();

        for (Document doc : relevantDocuments) {

            //check for new artist
            Bson artistFilter = Filters.all("artist", doc.getString("artist"));

            long artistCount = scrobbleCollection.countDocuments(artistFilter);
            if (artistCount == 1) {
                newArtistCount += 1;
            }


            int currentCountArtist = artistMap.getOrDefault(doc.getString("artist"), 0);
            artistMap.put(doc.getString("artist"), currentCountArtist + 1);

            //check for new track
            Bson trackFilter = Filters.all("track", doc.getString("track"));
            long trackCount = scrobbleCollection.countDocuments(trackFilter);
            if (trackCount == 1) {
                newTrackCount += 1;
            }


            List<String> trackArtistPair = new ArrayList<>();
            trackArtistPair.add(doc.getString("track"));
            trackArtistPair.add(doc.getString("artist"));

            int currentCountTrack = trackMap.getOrDefault(trackArtistPair, 0);
            trackMap.put(trackArtistPair, currentCountTrack + 1);

        }


        newTracks = (double) newTrackCount / trackMap.size();
        double roundedTracks = Math.round(newTracks * 100.0) / 100.0;

        newArtists = (double) newArtistCount / artistMap.size();
        double roundedArtists = Math.round(newArtists * 100.0) / 100.0;



        List<Double> trackAndArtist = new ArrayList<>();
        trackAndArtist.add(roundedTracks);
        trackAndArtist.add(roundedArtists);
        return trackAndArtist;

    }

    static String claimCrown(Message message, GatewayDiscordClient client, MongoClient mongoClient, MongoDatabase mongoDatabase, String artist, String user, int ownerPlays) throws JsonProcessingException {
        MongoCollection<Document> crownsCollection = mongoDatabase.getCollection("crowns");
        Bson artistFilter = Filters.regex("artist", artist, "i");

        String crownClaimedString = "";

      /*  String username = client.getUserById(Snowflake.of(userId))
                .block()
                .getUsername();*/

        if (crownsCollection.countDocuments(artistFilter) == 0) {
            //create new crown object doc
            //dateClaimed is localdatetime
            Document crown = new Document("artist", artist)
                    .append("owner", user)
                    .append("dateClaimed", LocalDateTime.now())
                    .append("ownerPlays", ownerPlays);

            crownsCollection.insertOne(crown);

            crownClaimedString = user + " has claimed the crown with " + ownerPlays + " plays!";

        } else {
            //check existing crowns owner
         Document foundCrown = crownsCollection.find(artistFilter).first();
         if (Objects.equals(foundCrown.getString("owner"), user)) {
             //update owner plays
             Bson update = Updates.combine(
                     Updates.set("ownerPlays", ownerPlays)
             );

             crownsCollection.updateOne(artistFilter, update);

             crownClaimedString = "Current crown owner: " + user;

         } else {
             crownClaimedString = user + " has taken the crown from " + foundCrown.getString("owner") + " with " + ownerPlays + " plays!";


             Bson update = Updates.combine(
                     Updates.set("owner", user),
                     Updates.set("ownerPlays", ownerPlays),
                     Updates.set("dateClaimed", LocalDateTime.now())
             );

             crownsCollection.updateOne(artistFilter, update);
         }

        }





        return crownClaimedString;

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

    static Mono<?> durationTrackCommand(Message message, GatewayDiscordClient client) throws JsonProcessingException {

        long userid = message.getAuthor().get().getId().asLong();

        String username = client.getUserById(Snowflake.of(userid))
                .block()
                .getUsername();


        if (!message.getUserMentions().isEmpty()) {
            username = message.getUserMentions().get(0).getUsername();
            userid = message.getUserMentions().get(0).getId().asLong();
        }

        EmbedCreateSpec.Builder embedBuilder = createEmbed("Your top 5 longest tracks");

        MongoClient mongoClient = MongoClients.create(connectionString);
        MongoDatabase database = mongoClient.getDatabase("spongeybot");
        MongoCollection<Document> scrobbles = database.getCollection(username);
        Map<String, Integer> shortestSongs = new HashMap<>();
        Map<String, Integer> longestSongs = new HashMap<>();

        Set<String> uniqueSongsSet = new HashSet<>();
        List<Document> topSongsList = new ArrayList<>();

        for (Document scrobble : scrobbles.find()) {
            // Create a unique key for the song based on track and artist
            String songKey = scrobble.getString("track") + "_" + scrobble.getString("artist");

            if (!uniqueSongsSet.contains(songKey)) {
                uniqueSongsSet.add(songKey);

                // If the list is not full, add the song
                if (topSongsList.size() < 5) {
                    topSongsList.add(scrobble);
                } else {
                    // If the list is full, find the song with the smallest duration
                    Document minDurationSong = topSongsList.stream()
                            .min(Comparator.comparingInt(o -> o.getInteger("duration")))
                            .orElse(null);

                    // Compare the duration with the smallest duration in the list
                    if (scrobble.getInteger("duration") > minDurationSong.getInteger("duration")) {
                        // Replace the song with the smallest duration in the list
                        topSongsList.remove(minDurationSong);
                        topSongsList.add(scrobble);
                    }
                }
            }
        }

        for (Document doc : topSongsList) {
            embedBuilder.addField("", "duration: " + doc.getInteger("duration")/60 + "minutes. NAME: " + doc.getString("track")  + " by " + doc.getString("artist"), false );

        }

/*
        embedBuilder.addField("", "max song is: " + maxDuration/60 + "minutes. NAME: " + maxSong.getString("track")  + " by " + maxSong.getString("artist"), false );
*/


        return message.getChannel().flatMap(channel -> channel.createMessage(embedBuilder.build()));

    }

        static Mono<?> crownsCloseCommand(Message message, GatewayDiscordClient client) throws JsonProcessingException {
        long userid = message.getAuthor().get().getId().asLong();

        String username = client.getUserById(Snowflake.of(userid))
                .block()
                .getUsername();


        if (!message.getUserMentions().isEmpty()) {
            username = message.getUserMentions().get(0).getUsername();
            userid = message.getUserMentions().get(0).getId().asLong();
        }

        EmbedCreateSpec.Builder embedBuilder = createEmbed("Crowns " + username + " is close to getting");

        MongoClient mongoClient = MongoClients.create(connectionString);
        MongoDatabase database = mongoClient.getDatabase("spongeybot");
        MongoCollection<Document> crowns = database.getCollection("crowns");
        Map<String, Integer> crownsClose = new HashMap<>(); //artist, scrobbles away from crown

        for (Document crown : crowns.find()) {
            int authorsArtistPlays = getArtistPlays(userid, crown.getString("artist"), client);

            if (((crown.getInteger("ownerPlays") - authorsArtistPlays) <= 20)  && (crown.getInteger("ownerPlays") - authorsArtistPlays) != 0 && (!Objects.equals(crown.getString("owner"), username)) ) {
                crownsClose.put(crown.getString("artist"), crown.getInteger("ownerPlays") - authorsArtistPlays);
                System.out.println("This is owner plays: " + crown.getInteger("ownerPlays"));
                System.out.println("This is the person who entered the cmoomands' plays: " + authorsArtistPlays);

            }




        }

            List<Map.Entry<String, Integer>> crownsCloseList = new ArrayList<>(crownsClose.entrySet());
            Collections.sort(crownsCloseList, Map.Entry.<String, Integer>comparingByValue().reversed());

            StringBuilder crownsString = new StringBuilder();
            for (Map.Entry<String, Integer> entry : crownsCloseList) {
                crownsString.append(entry.getKey()).append(": ").append(entry.getValue()).append(" scrobbles away \n");
            }

            embedBuilder.addField("", crownsString.toString(), false);



            return message.getChannel().flatMap(channel -> channel.createMessage(embedBuilder.build()));

        }

        static Mono<?> getCrowns(Message message, GatewayDiscordClient client) throws JsonProcessingException {
        long userid = message.getAuthor().get().getId().asLong();

        String username = client.getUserById(Snowflake.of(userid))
                .block()
                .getUsername();


        if (!message.getUserMentions().isEmpty()) {
            username = message.getUserMentions().get(0).getUsername();
            userid = message.getUserMentions().get(0).getId().asLong();
        }


        MongoClient mongoClient = MongoClients.create(connectionString);
        MongoDatabase database = mongoClient.getDatabase("spongeybot");
        MongoCollection<Document> crowns = database.getCollection("crowns");
        Bson filter = Filters.all("owner", username);

        EmbedCreateSpec.Builder embedBuilder = createEmbed(username + "'s crowns");
        Map<String, Integer> crownMap = new HashMap<>(); //owner, total crowns

        int totalCrowns = 0;
        for (Document crown : crowns.find(filter)) {
            totalCrowns += 1;
            crownMap.put(crown.getString("artist"), crown.getInteger("ownerPlays"));
        }

        List<Map.Entry<String, Integer>> listOfCrowns = new ArrayList<>(crownMap.entrySet());
        Collections.sort(listOfCrowns, Map.Entry.<String, Integer>comparingByValue().reversed());

        int count = 1;
        StringBuilder crownsString = new StringBuilder();
        for (Map.Entry<String, Integer> entry : listOfCrowns) {
            crownsString.append(count).append(". ").append(entry.getKey()).append(": ").append(entry.getValue()).append(" scrobbles \n");
            count++;
        }

        embedBuilder.addField("", crownsString.toString(), false);

        embedBuilder.footer(totalCrowns + " total crowns", "https://i.imgur.com/F9BhEoz.png");

        embedBuilder.thumbnail(client.getUserById(Snowflake.of(userid))
                .block()
                .getAvatarUrl());

        return message.getChannel().flatMap(channel -> channel.createMessage(embedBuilder.build()));


    }


    static Mono<?> getCrownLb(Message message, GatewayDiscordClient client) throws JsonProcessingException {
        MongoClient mongoClient = MongoClients.create(connectionString);
        MongoDatabase database = mongoClient.getDatabase("spongeybot");
        MongoCollection<Document> crowns = database.getCollection("crowns");

        EmbedCreateSpec.Builder embedBuilder = createEmbed("Crowns Leaderboard");
        Map<String, Integer> crownMap = new HashMap<>(); //owner, total crowns

        for (Document crown : crowns.find()) {
            int currentCount = crownMap.getOrDefault(crown.getString("owner"), 0);
            crownMap.put(crown.getString("owner"), currentCount + 1);
        }



        List<Map.Entry<String, Integer>> sortedCrowns = new ArrayList<>(crownMap.entrySet());
        Collections.sort(sortedCrowns, Map.Entry.<String, Integer>comparingByValue().reversed());

        int count = 1;
        StringBuilder allCrowns = new StringBuilder();
        for (Map.Entry<String, Integer> entry : sortedCrowns) {
            String crownCrowns = "";
            if (entry.getValue() == 1) {
                crownCrowns = " crown ";
            } else {
                crownCrowns = " crowns ";
            }
            allCrowns.append(count).append(". ").append(entry.getKey()).append(": ").append(entry.getValue()).append(crownCrowns).append("\n");

            count++;
            if (count > 10) {
                break;
            }


        }

        embedBuilder.addField("", allCrowns.toString(), false);


        return message.getChannel().flatMap(channel -> channel.createMessage(embedBuilder.build()));

    }

        static Mono<?> getOverview(Message message, GatewayDiscordClient client) throws JsonProcessingException {

        String username = client.getUserById(Snowflake.of(message.getAuthor().get().getId().asLong()))
                .block()
                .getUsername();

        long userid = message.getAuthor().get().getId().asLong();
        if (!message.getUserMentions().isEmpty()) {
            username = message.getUserMentions().get(0).getUsername();
            userid = message.getUserMentions().get(0).getId().asLong();
        }



        MongoClient mongoClient = MongoClients.create(connectionString);
        MongoDatabase database = mongoClient.getDatabase("spongeybot");
        MongoCollection<Document> userScrobblesCollection = database.getCollection(username);
        MongoCollection<Document> userCollection = database.getCollection("users");

        Document userDocument = userCollection.find(Filters.eq("userid", userid)).first();

        ZoneId userTimeZone = ZoneId.of(userDocument.getString("timezone"));

        EmbedCreateSpec.Builder embedBuilder = createEmbed("Overview for " + username);


        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMMM yyyy");
        LocalDate currentDate = LocalDate.now(userTimeZone);

        for (int i = 0; i < 5; i++) {

            LocalDate date = currentDate.minusDays(i);
            String dateWeLoopThrough = date.format(formatter);

            ZonedDateTime startOfDay = date.atStartOfDay(userTimeZone);
            ZonedDateTime endOfDay = date.plusDays(1).atStartOfDay(userTimeZone);

            Bson filter = Filters.and(
                    Filters.gte("timestamp", startOfDay.toEpochSecond()),
                    Filters.lt("timestamp", endOfDay.toEpochSecond())
            );

            int scrobblesForDay = 0;
            int timeScrobbled = 0;
            Map<List<String>, Integer> topTrack = new HashMap<>(); //track,artist + count
            Map<String, Integer> topArtist = new HashMap<>();
            Map<List<String>, Integer> topAlbum = new HashMap<>(); //album,artist + count



            List<Document> relevantDocuments = new ArrayList<>();
            userScrobblesCollection.find(filter).into(relevantDocuments);


            for (Document doc : relevantDocuments) {

                scrobblesForDay += 1;
                timeScrobbled += doc.getInteger("duration");


                List<String> trackArtistPair = new ArrayList<>();
                trackArtistPair.add(doc.getString("track"));
                trackArtistPair.add(doc.getString("artist"));
                int currentCount = topTrack.getOrDefault(trackArtistPair, 0);
                topTrack.put(trackArtistPair, currentCount + 1);


                List<String> albumArtistPair = new ArrayList<>();
                albumArtistPair.add(doc.getString("album"));
                albumArtistPair.add(doc.getString("artist"));
                int currentCountAlbum = topAlbum.getOrDefault(albumArtistPair, 0);
                topAlbum.put(albumArtistPair, currentCountAlbum + 1);


                int currentCountArtist = topArtist.getOrDefault(doc.getString("artist"), 0);
                topArtist.put(doc.getString("artist"), currentCountArtist + 1);


            }


            List<Map.Entry<List<String>, Integer>> listOfTopTracks = new ArrayList<>(topTrack.entrySet());
            Collections.sort(listOfTopTracks, Map.Entry.<List<String>, Integer>comparingByValue().reversed());

            List<Map.Entry<List<String>, Integer>> listOfTopAlbums = new ArrayList<>(topAlbum.entrySet());
            Collections.sort(listOfTopAlbums, Map.Entry.<List<String>, Integer>comparingByValue().reversed());


            List<Map.Entry<String, Integer>> listOfTopArtist = new ArrayList<>(topArtist.entrySet());
            Collections.sort(listOfTopArtist, Map.Entry.<String, Integer>comparingByValue().reversed());


            String topArtistString;
            String topTrackString;
            String topAlbumString;

            if (listOfTopArtist.isEmpty()) {
                topArtistString = "";

            }
            else {
                topArtistString = listOfTopArtist.get(0).getKey() + " - " + listOfTopArtist.get(0).getValue() + " plays";
            }


            if (listOfTopTracks.isEmpty()) {
                topTrackString = "";

            } else {
                topTrackString = listOfTopTracks.get(0).getKey().get(0) + " - " + listOfTopTracks.get(0).getKey().get(1) + " - " + listOfTopTracks.get(0).getValue() + " plays";
            }


            if (listOfTopAlbums.isEmpty()) {
                topAlbumString = ""; } else {
                topAlbumString = listOfTopAlbums.get(0).getKey().get(0) + " - " + listOfTopAlbums.get(0).getKey().get(1) + " - " + listOfTopAlbums.get(0).getValue() + " plays";
            }



            List<Double> trackAndArtistPercents = getNewArtistsAndTracks(relevantDocuments, dateWeLoopThrough, filter, userScrobblesCollection, userTimeZone);


            embedBuilder.addField(dateWeLoopThrough, scrobblesForDay + " scrobbles | " + timeScrobbled/60 + " minutes\n " + trackAndArtistPercents.get(0)*100 + "% new tracks | " + trackAndArtistPercents.get(1)*100 + "% new artists" + "\n Top Artist: " + topArtistString +  "\nTop Album: " + topAlbumString + "\nTop Track: " + topTrackString, false);
        }

        return message.getChannel().flatMap(channel -> channel.createMessage(embedBuilder.build()));

    }

        static Mono<?> getTrackInfo(Message message, GatewayDiscordClient client) throws JsonProcessingException {
        String[] command = message.getContent().split(" ");

        String track = "";
        if (command.length >= 2) {
       /*     track = Arrays.stream(message.getContent().split(" "))
                    .collect(Collectors.toList())
                    .stream()
                    .skip(1)
                    .collect(Collectors.joining(" "));
*/
            if (!message.getUserMentions().isEmpty()) {
                track = Arrays.stream(command)
                        .collect(Collectors.toList())
                        .subList(0, command.length - 1)
                        .stream()
                        .skip(1)
                        .collect(Collectors.joining(" "));


            } else {
                track = Arrays.stream(message.getContent().split(" "))
                        .collect(Collectors.toList())
                        .stream()
                        .skip(1)
                        .collect(Collectors.joining(" "));

            }




        } else {
            return message.getChannel().flatMap(channel -> channel.createMessage("currently can only do $trackinfo track, will do trackinfo for current track later"));

        }


        String username = client.getUserById(Snowflake.of(message.getAuthor().get().getId().asLong()))
                .block()
                .getUsername();


            long userid = message.getAuthor().get().getId().asLong();

            if (!message.getUserMentions().isEmpty()) {
                username = message.getUserMentions().get(0).getUsername();
                userid = message.getUserMentions().get(0).getId().asLong();
            }


            MongoClient mongoClient = MongoClients.create(connectionString);
        MongoDatabase database = mongoClient.getDatabase("spongeybot");
        MongoCollection<Document> collection = database.getCollection(username);

        Bson filter = Filters.regex("track", track, "i");
        System.out.println("this is track: " + track);


            int totalDuration = 0;
        int maxTimestamp = 0;
        int totalScrobbles = 0;
        int minTimestamp = (int) (System.currentTimeMillis() / 1000);
        Map<String, Integer> daysListenedToTrack = new HashMap<>();

        String artist = collection.find(filter).first().getString("artist");


        Bson newFilter = Filters.and(
                    Filters.regex("track", track, "i"),
                    Filters.regex("artist", artist, "i")
            );

        for (Document doc : collection.find(newFilter)) {
            totalScrobbles += 1;
            int duration = doc.getInteger("duration");
            track = doc.getString("track");

            int timestamp = doc.getInteger("timestamp");
            totalDuration += duration;
            Instant instant = Instant.ofEpochSecond(timestamp);
            LocalDateTime dateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMMM yyyy");
            String firstDate = dateTime.format(formatter);

            int currentCount = daysListenedToTrack.getOrDefault(firstDate, 0);
            daysListenedToTrack.put(firstDate, currentCount + 1);


            // For max timestamp
            if (timestamp > maxTimestamp) {
                maxTimestamp = timestamp;
            }

            // For min timestamp
            if (timestamp < minTimestamp) {
                minTimestamp = timestamp;
            }

        }



        Instant instant = Instant.ofEpochSecond(minTimestamp);
        LocalDateTime dateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMMM yyyy");
        String firstDate = dateTime.format(formatter);

        Instant instant2 = Instant.ofEpochSecond(maxTimestamp);
        LocalDateTime dateTime2 = LocalDateTime.ofInstant(instant2, ZoneId.systemDefault());
        DateTimeFormatter formatter2 = DateTimeFormatter.ofPattern("dd MMMM yyyy");
        String maxDate = dateTime2.format(formatter2);


        List<Map.Entry<String, Integer>> entryListDaysListenedToTrack = new ArrayList<>(daysListenedToTrack.entrySet());
        Collections.sort(entryListDaysListenedToTrack, Map.Entry.<String, Integer>comparingByValue().reversed());

        String urinsane = "";
        if (entryListDaysListenedToTrack.get(0).getValue() >= 20) {
            urinsane = "(ur insane)";
        }

        String dayListenedToMost = entryListDaysListenedToTrack.get(0).getKey() + ": " + entryListDaysListenedToTrack.get(0).getValue() + " times! " + urinsane;

        EmbedCreateSpec.Builder embedBuilder = createEmbed("track info for " + track);

        embedBuilder.addField("total scrobbles: ", String.valueOf(totalScrobbles), false);

        embedBuilder.addField("first time listened to: ", firstDate, false);

        embedBuilder.addField("last time listened to: ", maxDate, false);

        embedBuilder.addField("day listened to most: ", dayListenedToMost, false);

        embedBuilder.addField("total time listened to: ", totalDuration/60 + " minutes", false);


        return message.getChannel().flatMap(channel -> channel.createMessage(embedBuilder.build()));


    }

        static Mono<?> getArtistTime(Message message, GatewayDiscordClient client, ObjectMapper objectMapper, CloseableHttpClient httpClient) throws JsonProcessingException {
        String[] command = message.getContent().split(" ");

        String artist = "";
        if (command.length >= 2) {
            System.out.println(command);

            if (!message.getUserMentions().isEmpty()) {
                artist = Arrays.stream(command)
                        .collect(Collectors.toList())
                        .subList(0, command.length - 1)
                        .stream()
                        .skip(1)
                        .collect(Collectors.joining(" "));

                if (artist.equals("")) {
                    artist = getUserCurrentTrackArtistName(objectMapper, httpClient, message);
                    artist = artist.replace("+", " ");
                }

            } else {
                artist = Arrays.stream(message.getContent().split(" "))
                        .collect(Collectors.toList())
                        .stream()
                        .skip(1)
                        .collect(Collectors.joining(" "));

            }


        } else {

                artist = getUserCurrentTrackArtistName(objectMapper, httpClient, message);
                artist = artist.replace("+", " ");


           // return message.getChannel().flatMap(channel -> channel.createMessage("currently can only do $artisttime artist, will do $artisttime for current artist later"));

        }

        String username = client.getUserById(Snowflake.of(message.getAuthor().get().getId().asLong()))
                .block()
                .getUsername();


            if (!message.getUserMentions().isEmpty()) {
                username = message.getUserMentions().get(0).getUsername();

            }

        MongoClient mongoClient = MongoClients.create(connectionString);
        MongoDatabase database = mongoClient.getDatabase("spongeybot");
        MongoCollection<Document> collection = database.getCollection(username);
        Bson filter = Filters.regex("artist", artist, "i");


        int totalArtistTime = 0;
        int scrobblecount = 0;

        for (Document doc : collection.find(filter)) {
            int duration = doc.getInteger("duration");
            scrobblecount += 1;
            totalArtistTime += duration;
        }

        String finalArtist = artist;
        int finalTotalArtistTime = totalArtistTime/60;
        int finalScrobblecount = scrobblecount;
            String finalUsername = username;
            return message.getChannel().flatMap(channel -> channel.createMessage( finalUsername + " has spent a total of " + finalTotalArtistTime + " minutes listening to " + finalArtist + "! scrobble count: " + finalScrobblecount));

    }




        static Mono<?> getArtistTracks(Message message, GatewayDiscordClient client) throws JsonProcessingException {
        String[] command = message.getContent().split(" ");

        String artist = "";
        if (command.length >= 2) {
            if (!message.getUserMentions().isEmpty()) {
                artist = Arrays.stream(command)
                        .collect(Collectors.toList())
                        .subList(0, command.length - 1)
                        .stream()
                        .skip(1)
                        .collect(Collectors.joining(" "));

            } else {
                artist = Arrays.stream(message.getContent().split(" "))
                        .collect(Collectors.toList())
                        .stream()
                        .skip(1)
                        .collect(Collectors.joining(" "));
            }



        } else {
            return message.getChannel().flatMap(channel -> channel.createMessage("currently can only do $at artist, will do $at for current artist later"));

        }



        System.out.println("artist: " + artist);



            String username = client.getUserById(Snowflake.of(message.getAuthor().get().getId().asLong()))
                    .block()
                    .getUsername();

            if (!message.getUserMentions().isEmpty()) {
                username = message.getUserMentions().get(0).getUsername();

            }

        MongoClient mongoClient = MongoClients.create(connectionString);
        MongoDatabase database = mongoClient.getDatabase("spongeybot");
        MongoCollection<Document> collection = database.getCollection(username);
        EmbedCreateSpec.Builder embedBuilder = createEmbed(username  + "'s tracks for " + artist);
        Bson filter = Filters.regex("artist", artist, "i");


        Map<String, Integer> artistTracks = new HashMap<>();


        for (Document doc : collection.find(filter)) {
            int currentCount = artistTracks.getOrDefault(doc.getString("track"), 0);
            artistTracks.put(doc.getString("track"), currentCount + 1);
        }



        List<Map.Entry<String, Integer>> entryListArtistTracks = new ArrayList<>(artistTracks.entrySet());
        Collections.sort(entryListArtistTracks, Map.Entry.<String, Integer>comparingByValue().reversed());

        int count = 1;
        StringBuilder artistTracksAll = new StringBuilder();
        for (Map.Entry<String, Integer> entry : entryListArtistTracks) {
            artistTracksAll.append(count).append(". ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");

            count++;
            if (count > 10) {
                break;
            }


        }

        embedBuilder.addField("ur artist tracks", artistTracksAll.toString(), false);

        return message.getChannel().flatMap(channel -> channel.createMessage(embedBuilder.build()));

    }


        static Mono<?> getYearlyInfo(Message message, GatewayDiscordClient client) throws JsonProcessingException {

        String[] command = message.getContent().split(" ");

        String year = "";
        if (command.length >= 2) {
            year = command[1];
            System.out.println("Year: " + year);
        } else {
            return message.getChannel().flatMap(channel -> channel.createMessage("Valid commands: $year 2023, $year 2022, $year 2021 etc"));

        }


        //GET SCROBBLES FOR GIVEN YEAR
        int scrobbleCounterForYear = 0;
        int currentTimeUTS = (int) (System.currentTimeMillis() / 1000);


            String username = client.getUserById(Snowflake.of(message.getAuthor().get().getId().asLong()))
                    .block()
                    .getUsername();

        MongoClient mongoClient = MongoClients.create(connectionString);
        MongoDatabase database = mongoClient.getDatabase("spongeybot");
        MongoCollection<Document> collection = database.getCollection(username);


        long startOfGivenYearUTS = 0;
        long endOfGivenYearUTS = 0;

        LocalDateTime givenStartDateTime = LocalDateTime.of(Integer.parseInt(year), Month.JANUARY, 1, 0, 0);
        LocalDateTime givenEndDateTime = LocalDateTime.of(Integer.parseInt(year), Month.DECEMBER, 31, 23, 59);

        if (year.equals("")) {
            startOfGivenYearUTS = 1672570800;
            endOfGivenYearUTS = currentTimeUTS;
        } else {
            startOfGivenYearUTS = convertToUnixTimestamp(givenStartDateTime);
            endOfGivenYearUTS = convertToUnixTimestamp(givenEndDateTime);
        }

        Map<String, Integer> artistsForYear = new HashMap<>();
        Map<List<String>, Integer> tracksForYearNew = new HashMap<>();
        Map<List<String>, Integer> albumsForYearNew = new HashMap<>();


        for (Document doc : collection.find()) {

            int timestamp = doc.getInteger("timestamp");

            if (timestamp  < endOfGivenYearUTS && timestamp >= startOfGivenYearUTS) {
                scrobbleCounterForYear += 1;
                String artistName = doc.getString("artist") ;
                int currentCount = artistsForYear.getOrDefault(artistName, 0);
                artistsForYear.put(artistName, currentCount + 1);

                String trackName = doc.getString("track");
                List<String> trackArtistPair = new ArrayList<>();
                trackArtistPair.add(trackName);
                trackArtistPair.add(artistName);
                int currentCountTrack = tracksForYearNew.getOrDefault(trackArtistPair, 0);
                tracksForYearNew.put(trackArtistPair, currentCountTrack + 1);

                String albumName = doc.getString("album");
                List<String> albumArtistPair = new ArrayList<>();
                albumArtistPair.add(albumName);
                albumArtistPair.add(artistName);
                int currentCountAlbum = albumsForYearNew.getOrDefault(albumArtistPair, 0);
                albumsForYearNew.put(albumArtistPair, currentCountAlbum + 1);

            }
        }


        List<Map.Entry<String, Integer>> entryListArtist = new ArrayList<>(artistsForYear.entrySet());
        Collections.sort(entryListArtist, Map.Entry.<String, Integer>comparingByValue().reversed());



        List<Map.Entry<List<String>, Integer>> entryListTracks = new ArrayList<>(tracksForYearNew.entrySet());
        Collections.sort(entryListTracks, Map.Entry.<List<String>, Integer>comparingByValue().reversed());


        List<Map.Entry<List<String>, Integer>> entryListAlbums = new ArrayList<>(albumsForYearNew.entrySet());
        Collections.sort(entryListAlbums, Map.Entry.<List<String>, Integer>comparingByValue().reversed());

        EmbedCreateSpec.Builder embedBuilder = createEmbed("Your stats for " + year);

        embedBuilder.addField("Total scrobbles: ", String.valueOf(scrobbleCounterForYear), false);

        int count = 1;
        StringBuilder artistField = new StringBuilder();
        for (Map.Entry<String, Integer> entry : entryListArtist) {
            artistField.append(count).append(". ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");

            count++;
            if (count > 10) {
                break;
            }
        }

        embedBuilder.addField("Top 10 Artists", artistField.toString(), false);


        embedBuilder.addField("Top 10 Tracks", " ", false);

        int trackCount = 1;
        StringBuilder trackField = new StringBuilder();

        for (Map.Entry<List<String>, Integer> entry : entryListTracks) {
            trackField.append(trackCount).append(". ").append(entry.getKey().get(0)).append(": ").append(entry.getValue()).append("\n");


            trackCount++;
            if (trackCount > 10) {
                break;
            }
        }

        embedBuilder.addField("Top 10 Tracks", trackField.toString(), false);



        embedBuilder.addField("Top 10 Albums", " ", false);

        int albumCount = 1;
        for (Map.Entry<List<String>, Integer> entry : entryListAlbums) {
            embedBuilder.addField(albumCount + ". " + entry.getKey().get(0) + ": " + entry.getValue(), "", false);
            albumCount++;
            if (albumCount > 10) {
                break;
            }
        }

        return message.getChannel().flatMap(channel -> channel.createMessage(embedBuilder.build()));



    }

    private static long convertToUnixTimestamp(LocalDateTime localDateTime) {
        return localDateTime.atZone(ZoneId.systemDefault()).toEpochSecond();
    }

    static Mono<?> getScrobblesInTimeframe(Message message, GatewayDiscordClient client) throws JsonProcessingException {
        //if no param given, go for all scrobbles
        //other params: years like 2023, 2022, 2021, 2020 then it would get the time for 01 jan 1200 am that year until 31 dec 11:59 pm that year


        String[] command = message.getContent().split(" ");

        String year = "";
        if (command.length >= 2) {
            year = command[1];
            System.out.println("Year: " + year);
        } else {
            return message.getChannel().flatMap(channel -> channel.createMessage("Valid commands: $scrobbles 2023, $scrobbles 2022, $scrobbles 2021 etc"));

        }

        int scrobbleCounterForYear = 0;
        int currentTimeUTS = (int) (System.currentTimeMillis() / 1000);

        String username = client.getUserById(Snowflake.of(message.getAuthor().get().getId().asLong()))
                .block()
                .getUsername();

        MongoClient mongoClient = MongoClients.create(connectionString);
        MongoDatabase database = mongoClient.getDatabase("spongeybot");
        MongoCollection<Document> collection = database.getCollection(username);

        //gets all docs in scrobbles collection for a user
        Bson filter = Filters.all("userId", message.getAuthor().get().getId().asLong());

        long startOfGivenYearUTS = 0;
        long endOfGivenYearUTS = 0;

        LocalDateTime givenStartDateTime = LocalDateTime.of(Integer.parseInt(year), Month.JANUARY, 1, 0, 0);
        LocalDateTime givenEndDateTime = LocalDateTime.of(Integer.parseInt(year), Month.DECEMBER, 31, 23, 59);

        if (year.equals("")) {

            startOfGivenYearUTS = 1672570800;
            endOfGivenYearUTS = currentTimeUTS;
        } else {
            System.out.println("This is start date: " + givenStartDateTime);
            System.out.println("This is end date: " + givenEndDateTime);

            startOfGivenYearUTS = convertToUnixTimestamp(givenStartDateTime);
            endOfGivenYearUTS = convertToUnixTimestamp(givenEndDateTime);

        }

        for (Document doc : collection.find(filter)) {
            int timestamp = doc.getInteger("timestamp");

            if (timestamp  < endOfGivenYearUTS && timestamp >= startOfGivenYearUTS) {
                scrobbleCounterForYear += 1;
            }
        }

        String finalYear = year;
        int finalScrobbleCounterForWeek = scrobbleCounterForYear;
        return message.getChannel().flatMap(channel -> channel.createMessage("Your total scrobbles for " + finalYear + ": " + finalScrobbleCounterForWeek));


    }


    static Mono<?> helpCommand(Message message) {

        EmbedCreateSpec.Builder embedBuilder = createEmbed("Help")
                .addField("", "$wkt", false)
                .addField("", "$toptracks", false)
                .addField("", "$login", false)
                .addField("", "$scrobbles 2021, $scrobbles 2023 etc", false)
                .addField("", "$year 2023, $year 2022 etc", false)
                .addField("", "$topscrobbleddays", false)

                .addField("random trakc info", "$trackinfo", false)

                .addField("Artists", "$artisttime\n $artisttracks\n $a\n $topartists\n $wk", false)

                .addField("Leaderboards", "$scrobblelb\n $timelb\n $artistlb\n $tracklb", false)

                .addField("crowns stuff", "$crownlb\n $crowns\n $crownsclose", false)


                .addField("", "ALSO SOME COMMANDS ALLOW U TO MENTION USERS AT THE END TO CHEcK THEIR STATS", false)

                .addField("", "more features coming soon", false);

        return message.getChannel().flatMap(channel -> channel.createMessage(embedBuilder.build()));


    }

    static Mono<?> tracklbCommand(Message message, GatewayDiscordClient client) {


        String[] command = message.getContent().split(" ");

        String timePeriod = ""; //overall if blank
        if (command.length >= 2) {
            timePeriod = command[1];
            System.out.println("timePeriod: " + timePeriod);
        }

        Map<String, Integer> unsortedscrobbleLb = new HashMap<>();


        EmbedCreateSpec.Builder embedBuilder = createEmbed("Track leaderboard for " + timePeriod);

        MongoClient mongoClient = MongoClients.create(connectionString);
        MongoDatabase database = mongoClient.getDatabase("spongeybot");
        MongoCollection<Document> collection = database.getCollection("users");


        for (Document document : collection.find()) {
            long userId = document.getLong("userid");

            String username = client.getUserById(Snowflake.of(userId))
                    .block()
                    .getUsername();

            MongoCollection<Document> scrobbleCollection = database.getCollection(username);

            ZoneId userTimeZone = ZoneId.of(document.getString("timezone"));

            long userRegsiteredTimestamp = Long.parseLong(document.getString("registered"));

            //if command is 'today'
            ZonedDateTime currentTimeInTimezone = ZonedDateTime.now(userTimeZone);

            ZonedDateTime firstMinuteOfPeriod;
            ZonedDateTime lastMinuteOfPeriod;

            try {
                List<ZonedDateTime> firstAndLastTimes =  calculateTimePeriod(timePeriod, currentTimeInTimezone, userRegsiteredTimestamp, userTimeZone);

                firstMinuteOfPeriod = firstAndLastTimes.get(0);
                lastMinuteOfPeriod = firstAndLastTimes.get(1);

            } catch (IllegalArgumentException e) {
                return message.getChannel().flatMap(channel -> channel.createMessage("Valid periods: today, currentweek, lastweek, month, 2024, lifetime"));
            }

            long utsStartOfPeriod = firstMinuteOfPeriod.toInstant().getEpochSecond();
            long utsEndOfPeriod = lastMinuteOfPeriod.toInstant().getEpochSecond();


            Bson filter = Filters.and(
                    Filters.gte("timestamp", utsStartOfPeriod),
                    Filters.lt("timestamp", utsEndOfPeriod)
            );

            Set<String> uniqueTracks = new HashSet<>();

            for (Document scrobble : scrobbleCollection.find(filter)) {
                String track = scrobble.getString("track");
                String artist = scrobble.getString("artist");

                if (track != null && artist != null) {
                    String uniqueKey = track + "|" + artist;
                    uniqueTracks.add(uniqueKey);
                }


            }


            unsortedscrobbleLb.put(username, uniqueTracks.size());

        }


        List<Map.Entry<String, Integer>> list = new LinkedList<>(unsortedscrobbleLb.entrySet());

        Collections.sort(list, Collections.reverseOrder(Comparator.comparing(Map.Entry::getValue)));

        int count = 1;
        StringBuilder scrobbleLbString = new StringBuilder();
        for (Map.Entry<String, Integer> entry : list) {
            scrobbleLbString.append(count).append(". ").append(entry.getKey()).append(": ").append(entry.getValue()).append(" tracks \n");
            count++;

        }

        embedBuilder.addField("", scrobbleLbString.toString(), false);

        return message.getChannel().flatMap(channel -> channel.createMessage(embedBuilder.build()));
    }

    static Mono<?> artistLbCommand(Message message, GatewayDiscordClient client) {


        String[] command = message.getContent().split(" ");

        String timePeriod = ""; //overall if blank
        if (command.length >= 2) {
            timePeriod = command[1];
            System.out.println("timePeriod: " + timePeriod);
        }

        Map<String, Integer> unsortedscrobbleLb = new HashMap<>();


        EmbedCreateSpec.Builder embedBuilder = createEmbed("Artist leaderboard for " + timePeriod);

        MongoClient mongoClient = MongoClients.create(connectionString);
        MongoDatabase database = mongoClient.getDatabase("spongeybot");
        MongoCollection<Document> collection = database.getCollection("users");


        for (Document document : collection.find()) {
            int userPlaycount;
            long userId = document.getLong("userid");

            String username = client.getUserById(Snowflake.of(userId))
                    .block()
                    .getUsername();

            MongoCollection<Document> scrobbleCollection = database.getCollection(username);

            ZoneId userTimeZone = ZoneId.of(document.getString("timezone"));

            long userRegsiteredTimestamp = Long.parseLong(document.getString("registered"));

            //if command is 'today'
            ZonedDateTime currentTimeInTimezone = ZonedDateTime.now(userTimeZone);

            ZonedDateTime firstMinuteOfPeriod;
            ZonedDateTime lastMinuteOfPeriod;

            try {
                List<ZonedDateTime> firstAndLastTimes =  calculateTimePeriod(timePeriod, currentTimeInTimezone, userRegsiteredTimestamp, userTimeZone);

                firstMinuteOfPeriod = firstAndLastTimes.get(0);
                lastMinuteOfPeriod = firstAndLastTimes.get(1);

            } catch (IllegalArgumentException e) {
                return message.getChannel().flatMap(channel -> channel.createMessage("Valid periods: today, currentweek, lastweek, month, 2024, lifetime"));
            }

            long utsStartOfPeriod = firstMinuteOfPeriod.toInstant().getEpochSecond();
            long utsEndOfPeriod = lastMinuteOfPeriod.toInstant().getEpochSecond();


            Bson filter = Filters.and(
                    Filters.gte("timestamp", utsStartOfPeriod),
                    Filters.lt("timestamp", utsEndOfPeriod)
            );

            Set<String> uniqueArtists = new HashSet<>();

            for (Document scrobble : scrobbleCollection.find(filter)) {
                String artist = scrobble.getString("artist");
                if (artist != null) {
                    uniqueArtists.add(artist);
                }


            }


            unsortedscrobbleLb.put(username, uniqueArtists.size());

        }


        List<Map.Entry<String, Integer>> list = new LinkedList<>(unsortedscrobbleLb.entrySet());

        Collections.sort(list, Collections.reverseOrder(Comparator.comparing(Map.Entry::getValue)));

        int count = 1;
        StringBuilder scrobbleLbString = new StringBuilder();
        for (Map.Entry<String, Integer> entry : list) {
            scrobbleLbString.append(count).append(". ").append(entry.getKey()).append(": ").append(entry.getValue()).append(" artist \n");
            count++;

        }

        embedBuilder.addField("", scrobbleLbString.toString(), false);

        return message.getChannel().flatMap(channel -> channel.createMessage(embedBuilder.build()));
    }

    static Mono<?> scrobbleLbCommand(Message message, ObjectMapper objectMapper, CloseableHttpClient httpClient, GatewayDiscordClient client) {


        String[] command = message.getContent().split(" ");

        String timePeriod = ""; //overall if blank
        if (command.length >= 2) {
            timePeriod = command[1];
            System.out.println("timePeriod: " + timePeriod);
        }

        Map<String, Integer> unsortedscrobbleLb = new HashMap<>();


        EmbedCreateSpec.Builder embedBuilder = createEmbed("Scrobble leaderboard for " + timePeriod);

        MongoClient mongoClient = MongoClients.create(connectionString);
        MongoDatabase database = mongoClient.getDatabase("spongeybot");
        MongoCollection<Document> collection = database.getCollection("users");


        for (Document document : collection.find()) {
            int userPlaycount;
            long userId = document.getLong("userid");

            String username = client.getUserById(Snowflake.of(userId))
                    .block()
                    .getUsername();

            MongoCollection<Document> scrobbleCollection = database.getCollection(username);

            ZoneId userTimeZone = ZoneId.of(document.getString("timezone"));

            long userRegsiteredTimestamp = Long.parseLong(document.getString("registered"));

            //if command is 'today'
            ZonedDateTime currentTimeInTimezone = ZonedDateTime.now(userTimeZone);

            ZonedDateTime firstMinuteOfPeriod;
            ZonedDateTime lastMinuteOfPeriod;

            try {
                List<ZonedDateTime> firstAndLastTimes =  calculateTimePeriod(timePeriod, currentTimeInTimezone, userRegsiteredTimestamp, userTimeZone);

                firstMinuteOfPeriod = firstAndLastTimes.get(0);
                lastMinuteOfPeriod = firstAndLastTimes.get(1);

            } catch (IllegalArgumentException e) {
                return message.getChannel().flatMap(channel -> channel.createMessage("Valid periods: today, currentweek, lastweek, month, 2024, lifetime"));
            }

            long utsStartOfPeriod = firstMinuteOfPeriod.toInstant().getEpochSecond();
            long utsEndOfPeriod = lastMinuteOfPeriod.toInstant().getEpochSecond();


            Bson filter = Filters.and(
                    Filters.gte("timestamp", utsStartOfPeriod),
                    Filters.lt("timestamp", utsEndOfPeriod)
            );

            userPlaycount = (int) scrobbleCollection.countDocuments(filter);
            unsortedscrobbleLb.put(username, userPlaycount);

        }


        List<Map.Entry<String, Integer>> list = new LinkedList<>(unsortedscrobbleLb.entrySet());

        Collections.sort(list, Collections.reverseOrder(Comparator.comparing(Map.Entry::getValue)));

        int count = 1;
        StringBuilder scrobbleLbString = new StringBuilder();
        for (Map.Entry<String, Integer> entry : list) {
            scrobbleLbString.append(count).append(". ").append(entry.getKey()).append(": ").append(entry.getValue()).append(" scrobbles \n");
            count++;

        }

        embedBuilder.addField("", scrobbleLbString.toString(), false);

        return message.getChannel().flatMap(channel -> channel.createMessage(embedBuilder.build()));
    }

    static Mono<?> wktCommand(Message message, ObjectMapper objectMapper, CloseableHttpClient httpClient, GatewayDiscordClient client) {
        String trackName = Arrays.stream(message.getContent().split(" "))
                .collect(Collectors.toList())
                .stream()
                .skip(1)
                .collect(Collectors.joining("+"));

        String artist = "";

        System.out.println("THIS IS TRACKNAME: " + trackName);

      MongoClient mongoClient = MongoClients.create(connectionString);
            MongoDatabase database = mongoClient.getDatabase("spongeybot");
            MongoCollection<Document> collection = database.getCollection("users");

        String userSessionKey = getUserSessionKey(message);




        if (userSessionKey == null) {
            return message.getChannel().flatMap(channel -> channel.createMessage("please login to use this command"));

        }

        Bson filter;

        filter = Filters.regex("track",  trackName.replace("+", " "), "i");

        if (trackName.equals("")) {
            //get users current track, name from that and set trackName to that
            String getRecentUserTracksUrl = BASE_URL + "?method=user.getrecenttracks&api_key=" + API_KEY + "&sk=" + userSessionKey + "&format=json";
            System.out.println("recent tracks url: " + getRecentUserTracksUrl);
            JsonNode rootNode =  getJsonNodeFromUrl(objectMapper, getRecentUserTracksUrl, httpClient, message);

            try {
                JsonNode trackNode = rootNode.path("recenttracks").path("track");
                JsonNode firstTrackNode = trackNode.get(0);
                JsonNode trackNodeMain = firstTrackNode.path("name");
                JsonNode artistNode = firstTrackNode.path("artist").path("#text");
                artist = artistNode.asText().replace(" ", "+");
                trackName = trackNodeMain.asText().replace(" ", "+");
               // filter = Filters.all("track", trackName.replace("+", " "));

             /*  filter = Filters.and(
                        Filters.eq("track", trackName.replace("+", " ")),
                        Filters.eq("artist", artist.replace("+", " "))
                );*/

                 filter = Filters.and(
                        Filters.regex("track", "^" + Pattern.quote(trackName.replace("+", " ")), "i"),
                        Filters.regex("artist", "^" + Pattern.quote(artist.replace("+", " ")), "i")
                );

            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {

            String searchUrl = "https://ws.audioscrobbler.com/2.0/?method=track.search&track=" + trackName + "&api_key=" + API_KEY + "&format=json";


            JsonNode rootNodeForSearch =  getJsonNodeFromUrl(objectMapper, searchUrl, httpClient, message);

            JsonNode trackListForSearch = rootNodeForSearch.path("results").path("trackmatches").path("track");

            if (trackListForSearch.size() > 0) {
                JsonNode firstTrackNode = trackListForSearch.get(0);
                trackName = firstTrackNode.path("name").asText();
                 artist = firstTrackNode.path("artist").asText();

              /*  filter = Filters.and(
                        Filters.eq("track", trackName.replace("+", " ")),
                        Filters.eq("artist", artist.replace("+", " "))
                );*/

                 filter = Filters.and(
                        Filters.regex("track", "^" + Pattern.quote(trackName.replace("+", " ")), "i"),
                        Filters.regex("artist", "^" + Pattern.quote(artist.replace("+", " ")), "i")
                );

            }



        }






        EmbedCreateSpec.Builder embedBuilder = createEmbed("Who knows track: " + trackName.replace("+", " ")
                + " by " + artist.replace("+", " ") + "?");


        Map<String, Integer> unsortedWk = new HashMap<>();

        for (Document document : collection.find()) {
            int userTrackCount;

            long userId = document.getLong("userid");

            String username = client.getUserById(Snowflake.of(userId))
                    .block()
                    .getUsername();

            MongoCollection<Document> userScrobblesCollection = database.getCollection(username);

            userTrackCount = (int) userScrobblesCollection.countDocuments(filter);


            if (userTrackCount != 0) {
                System.out.println(userScrobblesCollection.find(filter).first().getString("albumLink"));
                System.out.println(userScrobblesCollection.find(filter).first().getString("artist"));

                embedBuilder.thumbnail(userScrobblesCollection.find(filter).first().getString("albumLink"));
                unsortedWk.put(username, userTrackCount);
            }


        }




        List<Map.Entry<String, Integer>> sortedWkt = new LinkedList<>(unsortedWk.entrySet());
        Collections.sort(sortedWkt, Map.Entry.<String, Integer>comparingByValue().reversed());



        int count = 1;
        StringBuilder wktString = new StringBuilder();
        for (Map.Entry<String, Integer> entry : sortedWkt) {
            wktString.append(count).append(". ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            count++;

        }

        embedBuilder.addField("", wktString.toString(), false);



        return message.getChannel().flatMap(channel -> channel.createMessage(embedBuilder.build()));

    }

    static Mono<?> artistInfoCommand(Message message, ObjectMapper objectMapper, CloseableHttpClient httpClient, GatewayDiscordClient client) {
        String artistName = Arrays.stream(message.getContent().split(" "))
                .collect(Collectors.toList())
                .stream()
                .skip(1)
                .collect(Collectors.joining("+"));

        if (artistName.equals("")) {
            artistName = getUserCurrentTrackArtistName(objectMapper, httpClient, message);
        }

        String artistSummary = "";

        String artistInfoUrl = BASE_URL + "?method=artist.getinfo&artist=" + artistName + "&api_key=" + API_KEY + "&format=json";
        System.out.println("this is artist info url: " + artistInfoUrl);
        HttpGet requestArtistInfo = new HttpGet(artistInfoUrl);
        HttpResponse responseArtistInfo = null;
        try {
            responseArtistInfo = httpClient.execute(requestArtistInfo);
        } catch (IOException e) {
            return message.getChannel().flatMap(channel -> channel.createMessage("error: couldn't execute search track for artist name url"));
        }

        try {
            JsonNode rootNode1 =  objectMapper.readTree(responseArtistInfo.getEntity().getContent());
            JsonNode artistSummaryNode = rootNode1.path("artist").path("bio").path("summary");
            System.out.println("This is artistsumarynode " +  artistSummaryNode);
            artistSummary = artistSummaryNode.asText();
        } catch (Exception e) {
            return message.getChannel().flatMap(channel -> channel.createMessage("error: couldn't get nodes fromartist info url "));
        }


        EmbedCreateSpec.Builder embedBuilder = createEmbed((artistName.replace("+", " ")));

        embedBuilder.addField("Summary: ", artistSummary, false);
        return message.getChannel().flatMap(channel -> channel.createMessage(embedBuilder.build()));
    }

    static Mono<?> wkCommand(Message message, ObjectMapper objectMapper, CloseableHttpClient httpClient, GatewayDiscordClient client) throws JsonProcessingException {
        String artistName = Arrays.stream(message.getContent().split(" "))
                .collect(Collectors.toList())
                .stream()
                .skip(1)
                .collect(Collectors.joining("+"));



        Map<String, Integer> unsortedWk = new HashMap<>();


        MongoClient mongoClient = MongoClients.create(connectionString);
        MongoDatabase database = mongoClient.getDatabase("spongeybot");
        MongoCollection<Document> users = database.getCollection("users");


        String userSessionKey = getUserSessionKey(message);


        Bson filter = Filters.regex("artist",  artistName.replace("+", " "), "i");


        if (userSessionKey == null) {
            return message.getChannel().flatMap(channel -> channel.createMessage("please login to use this command"));
        }


        if (artistName.equals("")) {
           artistName = getUserCurrentTrackArtistName(objectMapper, httpClient, message);
            filter = Filters.regex("artist",  artistName.replace("+", " "), "i");

        } else {

            String searchUrl = "https://ws.audioscrobbler.com/2.0/?method=artist.search&artist=" + artistName + "&api_key=" + API_KEY + "&format=json";


            JsonNode rootNodeForSearch =  getJsonNodeFromUrl(objectMapper, searchUrl, httpClient, message);

            JsonNode artistListForSearch = rootNodeForSearch.path("results").path("artistmatches").path("artist");

            if (artistListForSearch.size() > 0) {
                JsonNode firstTrackNode = artistListForSearch.get(0);
                artistName = firstTrackNode.path("name").asText();

              /*  filter = Filters.and(
                        Filters.eq("track", trackName.replace("+", " ")),
                        Filters.eq("artist", artist.replace("+", " "))
                );*/

                filter = Filters.regex("artist", "^" + Pattern.quote(artistName.replace("+", " ")), "i");


            }

        }

        System.out.println("this is artist name:" + artistName);
        System.out.println("this is filter:" + filter);


        EmbedCreateSpec.Builder embedBuilder = createEmbed("Who knows " + artistName.replace("+", " ")
                + "?");




        for (Document document : users.find()) {
            int userArtistCount;

            long userId = document.getLong("userid");

           String username = client.getUserById(Snowflake.of(userId))
                    .block()
                    .getUsername();

            MongoCollection<Document> userScrobblesCollection = database.getCollection(username);

            userArtistCount = (int) userScrobblesCollection.countDocuments(filter);

            if (userArtistCount != 0) {
                unsortedWk.put(username, userArtistCount);

            }

        }

        if (!unsortedWk.isEmpty()) {

            List<Map.Entry<String, Integer>> list = new LinkedList<>(unsortedWk.entrySet());
            Collections.sort(list, Map.Entry.<String, Integer>comparingByValue().reversed());

            int count = 1;
            StringBuilder wkString = new StringBuilder();

            Iterator<Map.Entry<String, Integer>> iterator = list.iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, Integer> entry = iterator.next();
                if (!iterator.hasNext()) {
                    wkString.append("\uD83D\uDDD1 ").append(entry.getKey()).append(": ").append(entry.getValue()).append(" plays \n");
                } else if (count == 1) {
                    wkString.append("\uD83D\uDC51 ").append(entry.getKey()).append(": ").append(entry.getValue()).append(" plays \n");
                } else {
                    wkString.append(count).append(". ").append(entry.getKey()).append(": ").append(entry.getValue()).append(" plays \n");
                }

                count++;
            }

            //owner is person w top plays
            Map.Entry<String, Integer> topOwner = list.get(0);
            int ownerPlays = topOwner.getValue();
            String owner = topOwner.getKey();

            embedBuilder.addField("", wkString.toString(), false);


            embedBuilder.addField("crown stuff", claimCrown(message, client, mongoClient, database, artistName.replace("+", " "), owner, ownerPlays), false);



        }



        return message.getChannel().flatMap(channel -> channel.createMessage(embedBuilder.build()));
    }



    static Mono<?> topArtistsCommand(Message message, ObjectMapper objectMapper, CloseableHttpClient httpClient, GatewayDiscordClient client) {
        //week, month
        String[] command = message.getContent().split(" ");

        String timePeriod = ""; //overall if blank
        if (command.length >= 2) {
            timePeriod = command[1];
            System.out.println("timePeriod: " + timePeriod);
        }


        MongoClient mongoClient = MongoClients.create(connectionString);
        MongoDatabase database = mongoClient.getDatabase("spongeybot");
        MongoCollection<Document> collection = database.getCollection("users");


        String userSessionKey = getUserSessionKey(message);

        if (userSessionKey == null) {
            return message.getChannel().flatMap(channel -> channel.createMessage("please login to use this command"));

        }

        String url = BASE_URL + "?method=user.gettopartists&api_key=" + API_KEY + "&sk=" + userSessionKey + "&limit=5" + "&format=json";
        return message.getChannel()
                .flatMap(userResponse -> {
                    try {
                        JsonNode rootNode =  getJsonNodeFromUrl(objectMapper, url, httpClient, message);

                        EmbedCreateSpec.Builder embedBuilder = createEmbed("Your top artists");


                        JsonNode topArtistsNode = rootNode.path("topartists");

                        for (JsonNode artistNode : topArtistsNode.path("artist")) {
                            String name = artistNode.path("name").asText();
                            String playcount = artistNode.path("playcount").asText();
                            String imageUrl = artistNode.path("image")
                                    .elements()
                                    .next()
                                    .path("#text")
                                    .asText();
                            embedBuilder.thumbnail(imageUrl);

                            embedBuilder.addField(name, "Plays: " + playcount, false);

                        }

                        return message.getChannel().flatMap(channel -> channel.createMessage(embedBuilder.build()));

                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    return Mono.empty();
                });


    }

    static Mono<?> topTracksCommand(Message message, ObjectMapper objectMapper, CloseableHttpClient httpClient, GatewayDiscordClient client) {

        MongoClient mongoClient = MongoClients.create(connectionString);
        MongoDatabase database = mongoClient.getDatabase("spongeybot");


        String userSessionKey = getUserSessionKey(message);

        if (userSessionKey == null) {
            return message.getChannel().flatMap(channel -> channel.createMessage("please login to use this command"));

        }


        String url = BASE_URL + "?method=user.gettoptracks&api_key=" + API_KEY + "&sk=" + userSessionKey + "&limit=5" + "&format=json";

        System.out.println("top tracks url: " + url);

        return message.getChannel()

                .flatMap(userResponse -> {


                    try {

                        JsonNode rootNode =  getJsonNodeFromUrl(objectMapper, url, httpClient, message);

                        EmbedCreateSpec.Builder embedBuilder = createEmbed("Your top tracks");


                        JsonNode topTracksNode = rootNode.path("toptracks");
                        for (JsonNode trackNode : topTracksNode.path("track")) {
                            String name = trackNode.path("name").asText();
                            String playcount = trackNode.path("playcount").asText();
                            String imageUrl = trackNode.path("image")
                                    .elements()
                                    .next()
                                    .path("#text")
                                    .asText();
                            embedBuilder.thumbnail(imageUrl);

                            embedBuilder.addField(name, "Plays: " + playcount, false);

                        }

                        return message.getChannel().flatMap(channel -> channel.createMessage(embedBuilder.build()));

                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    return Mono.empty();
                });
    }

    static Mono<?> updateBotPic(Message message, ObjectMapper objectMapper, CloseableHttpClient httpClient, GatewayDiscordClient client, MessageCreateEvent event) throws IOException {

        //Getting current day
        String day = LocalDate.now().getDayOfWeek().name();

        System.out.println("THis is day: " + day);

        MongoClient mongoClient = MongoClients.create(connectionString);
        MongoDatabase database = mongoClient.getDatabase("spongeybot");
        MongoCollection<Document> collection = database.getCollection("users");


        //getting user who is featured today
        Bson filter = Filters.all("featuredDay", day);
        Document featuredUserDocument = collection.find(filter).first();



        //getting above users top weekly album

        String userSessionKey = (String) featuredUserDocument.get("sessionkey");

        long userId = featuredUserDocument.getLong("userid");

        String url = BASE_URL + "?method=user.gettopalbums&api_key=" + API_KEY + "&sk=" + userSessionKey  + "&limit=1" + "&period=7day" + "&format=json";
        System.out.println(url);
        JsonNode rootNode =  getJsonNodeFromUrl(objectMapper, url, httpClient, message);
        JsonNode albumNode = rootNode.path("topalbums").path("album");
        String firstAlbumNode = albumNode.get(0).path("image").get(2).path("#text").asText();
        System.out.println("album image: " + firstAlbumNode);

        //Get username
        String username = client.getUserById(Snowflake.of(userId))
                .block()
                .getUsername();

        //set it to the bot picture
        Image image = Image.ofUrl(firstAlbumNode).block();


        event.getClient().edit().withAvatar(image).block();

        client.updatePresence(ClientPresence.online(ClientActivity.playing(username + "'s top album of the week!"))).block();



        return message.getChannel().flatMap(channel -> channel.createMessage(username + " is today's featured user :D"));

    }


        static Mono<?> loginCommand(Message message, ObjectMapper objectMapper, CloseableHttpClient httpClient, GatewayDiscordClient client) {

        MongoClient mongoClient = MongoClients.create(connectionString);
        MongoDatabase database = mongoClient.getDatabase("spongeybot");
        MongoCollection<Document> collection = database.getCollection("users");


        Document query = new Document("userid", message.getAuthor().get().getId().asLong());
        Document result = collection.find(query).first();
        if (result != null) {
            return message.getChannel().flatMap(channel -> channel.createMessage("You are already logged in :D"));


        }


        return message.getChannel()
                .flatMap(userResponse -> {
                    String url = BASE_URL + "?method=auth.gettoken&api_key=" + API_KEY + "&format=json";
                    String token = "";
                    //Get user to auth first
                    try {
                        JsonNode rootNode =  getJsonNodeFromUrl(objectMapper, url, httpClient, message);

                        token = rootNode.get("token").asText();
                        String requestAuthUrl = "http://www.last.fm/api/auth/?api_key=" + API_KEY + "&token=" + token;


                        EmbedCreateSpec.Builder embedBuilder = createEmbed("Login to last.fm").addField("Login here: ", requestAuthUrl, false);


                        return Mono.justOrEmpty(message.getAuthor())
                                .flatMap(user -> user.getPrivateChannel()
                                        .flatMap(privateChannel -> privateChannel.createMessage(embedBuilder.build()))
                                        .map(sentMessage -> "Check your DMs for the authentication link"))
                                .delayElement(Duration.ofSeconds(10))
                                .then(Mono.just(token));
                    } catch (Exception e) {
                        e.printStackTrace();
                        return Mono.empty();
                    }
                })
                .flatMap(token -> {
                    String apisignature = SignatureGenerator.generateApiSignature(API_KEY, "auth.getSession", token, API_SECRET);

                    System.out.println(apisignature);

                    String getSessionUrl = BASE_URL + "?method=auth.getSession&api_key=" + API_KEY +
                            "&token=" + token + "&api_sig=" + apisignature  + "&format=json";

                    System.out.println("This is session url " + getSessionUrl);

                    JsonNode rootNode = getJsonNodeFromUrl(objectMapper, getSessionUrl, httpClient, message);

                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    System.out.println("this is root node: " + rootNode);

                    String sessionKey = rootNode.get("session").get("key").asText();
                    System.out.println("user id+ " + message.getAuthor().get().getId().asLong() );

                        Document newUser = new Document("userid", message.getAuthor().get().getId().asLong())
                                .append("sessionkey", sessionKey);

                        collection.insertOne(newUser);
                        System.out.println("newUser inserted successfully.");





                    return Mono.justOrEmpty(message.getAuthor())
                            .flatMap(user -> user.getPrivateChannel()
                                    .flatMap(privateChannel -> privateChannel.createMessage("your account has been authorized thx"))
                                    .map(sentMessage -> "Check your DMs for the authentication link"));
                });
    }


        public static void main(String[] args) {



            ObjectMapper objectMapper = new ObjectMapper();
        CloseableHttpClient httpClient = HttpClients.createDefault();

        DiscordClient.create(BOT_TOKEN)
                .withGateway(client ->
        client.on(MessageCreateEvent.class)
                                .flatMap(event -> {
                                    Message message = event.getMessage();
                                    if (message.getContent().startsWith("$scrobblelb")) {
                                        return scrobbleLbCommand(message,objectMapper, httpClient, client);
                                    }

                                    if (message.getContent().equalsIgnoreCase("$a")) {
                                        return artistInfoCommand(message,objectMapper, httpClient, client);
                                    }

                                    if (message.getContent().startsWith("$wkt")) {
                                        return wktCommand(message,objectMapper, httpClient, client);
                                    }

                                    if (message.getContent().startsWith("$wk")) {
                                        try {
                                            return wkCommand(message, objectMapper, httpClient, client);
                                        } catch (JsonProcessingException e) {
                                            throw new RuntimeException(e);
                                        }
                                    }


                                    if (message.getContent().equalsIgnoreCase("$topartists")) {
                                        return topArtistsCommand(message, objectMapper, httpClient, client);
                                    }

                                    if (message.getContent().equalsIgnoreCase("$toptracks")) {
                                        return topTracksCommand(message, objectMapper, httpClient, client);
                                    }

                                    if (message.getContent().equalsIgnoreCase("$login")) {
                                        return loginCommand(message, objectMapper, httpClient, client);
                                    }

                                    if (message.getContent().equalsIgnoreCase("$help")) {
                                        return helpCommand(message);
                                    }

                                    if (message.getContent().startsWith("$scrobbles")) {
                                        try {
                                            return getScrobblesInTimeframe(message, client);
                                        } catch (JsonProcessingException e) {
                                            throw new RuntimeException(e);
                                        }
                                    }

                                    if (message.getContent().startsWith("$year")) {
                                        try {
                                            return getYearlyInfo(message, client);
                                        } catch (JsonProcessingException e) {
                                            throw new RuntimeException(e);
                                        }
                                    }


                                    if (message.getContent().startsWith("$time")) {

                                        //split
                                        String[] commandArray = message.getContent().split(" ");

                                        String command = commandArray[0].toLowerCase();

                                        if (command.equals("$time")) {
                                            System.out.println("running time command");
                                            try {
                                                return getDailyListeningTime(message, client);
                                            } catch (JsonProcessingException e) {
                                                throw new RuntimeException(e);
                                            } catch (UnsupportedEncodingException e) {
                                                throw new RuntimeException(e);
                                            }

                                        } else if (command.equals("$timelb")) {
                                            System.out.println("running time leaderboard command");

                                            try {
                                                return getTimeLeaderboard(message, client);
                                            } catch (JsonProcessingException e) {
                                                throw new RuntimeException(e);
                                            } catch (UnsupportedEncodingException e) {
                                                throw new RuntimeException(e);
                                            }
                                        }




                                    }


                                  /*  if (message.getContent().startsWith("$timelb")) {
                                        try {
                                            return getTimeLeaderboard(message, client);
                                        } catch (JsonProcessingException e) {
                                            throw new RuntimeException(e);
                                        } catch (UnsupportedEncodingException e) {
                                            throw new RuntimeException(e);
                                        }
                                    }*/





                                    if (message.getContent().startsWith("$pic")) {
                                        try {
                                            return updateBotPic(message,objectMapper, httpClient, client, event);
                                        } catch (IOException e) {
                                            throw new RuntimeException(e);
                                        }
                                    }



                                    if (message.getContent().equalsIgnoreCase("$update")) {
                                        try {
                                            return betterUpdateCommand(objectMapper, httpClient, message, client);
                                        } catch (UnsupportedEncodingException e) {
                                            throw new RuntimeException(e);
                                        }
                                    }


                                    if (message.getContent().startsWith("$artisttime")) {
                                        try {
                                            return getArtistTime(message, client, objectMapper, httpClient);
                                        } catch (JsonProcessingException e) {
                                            throw new RuntimeException(e);
                                        }
                                    }

                                    if (message.getContent().startsWith("$at")) {
                                        try {
                                            return getArtistTracks(message, client);
                                        } catch (JsonProcessingException e) {
                                            throw new RuntimeException(e);
                                        }
                                    }

                                    if (message.getContent().startsWith("$trackinfo")) {
                                        try {
                                            return getTrackInfo(message, client);
                                        } catch (JsonProcessingException e) {
                                            throw new RuntimeException(e);
                                        }
                                    }

                                    if (message.getContent().startsWith("$overview") || message.getContent().equals("$o") ) {
                                        try {
                                            return getOverview(message, client);
                                        } catch (JsonProcessingException e) {
                                            throw new RuntimeException(e);
                                        }
                                    }


                                    if (message.getContent().startsWith("$duration")) {
                                        try {
                                            return durationTrackCommand(message, client);
                                        } catch (JsonProcessingException e) {
                                            throw new RuntimeException(e);
                                        }
                                    }

                                    if (message.getContent().startsWith("$artistlb")) {
                                        return artistLbCommand(message, client);
                                    }


                                    if (message.getContent().startsWith("$tracklb")) {
                                        return tracklbCommand(message, client);
                                    }


                                    if (message.getContent().startsWith("$crownlb")) {
                                        try {
                                            return getCrownLb(message, client);
                                        } catch (JsonProcessingException e) {
                                            throw new RuntimeException(e);
                                        }
                                    }

                                    if (message.getContent().startsWith("$blend")) {
                                        return blendWithUser(message, client);
                                    }

                                    if (message.getContent().startsWith("$crownsclose")) {
                                        try {
                                            return crownsCloseCommand(message, client);
                                        } catch (JsonProcessingException e) {
                                            throw new RuntimeException(e);
                                        }
                                    }

                                    if (message.getContent().startsWith("$crowns")) {
                                        try {
                                            return getCrowns(message, client);
                                        } catch (JsonProcessingException e) {
                                            throw new RuntimeException(e);
                                        }
                                    }

                                    if (message.getContent().startsWith("$most")) {
                                        try {
                                            return getSongListenedToMostOneDay(message, client);
                                        } catch (JsonProcessingException e) {
                                            throw new RuntimeException(e);
                                        }
                                    }

                                    if (message.getContent().startsWith("$topscrobbleddays")) {
                                        try {
                                            return getTopScrobbledDays(message, client);
                                        } catch (JsonProcessingException e) {
                                            throw new RuntimeException(e);
                                        }
                                    }


                                    if (message.getContent().equalsIgnoreCase("$testingbetter")) {
                                        return testingbetterupdatecommand(objectMapper, httpClient, message, client);
                                    }

                                    if (message.getContent().equalsIgnoreCase("$testusername")) {
                                        try {
                                            return testUsername(client);
                                        } catch (JsonProcessingException e) {
                                            throw new RuntimeException(e);
                                        } catch (UnsupportedEncodingException e) {
                                            throw new RuntimeException(e);
                                        }
                                    }


                                    if (message.getContent().equalsIgnoreCase("$betterupdaterecentcommand")) {
                                        try {
                                            return betterupdaterecentcommand(objectMapper, httpClient, message, client);
                                        } catch (JsonProcessingException e) {
                                            throw new RuntimeException(e);
                                        } catch (UnsupportedEncodingException e) {
                                            throw new RuntimeException(e);
                                        }
                                    }

                                    return Mono.empty();
                                }))
                .block();
    }
}
















