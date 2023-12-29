import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.mongodb.client.*;
import com.mongodb.client.model.Filters;
import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClient;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Message;


import discord4j.core.object.presence.ClientActivity;
import discord4j.core.object.presence.ClientPresence;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.rest.util.Color;
import discord4j.rest.util.Image;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.bson.Document;
import org.bson.conversions.Bson;
import reactor.core.publisher.Mono;


import java.io.*;
import java.net.*;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.concurrent.TimeUnit;
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
                .color(Color.BLUE)
                .url("https://discord4j.com")
                .author(name, "https://discord4j.com", "https://i.imgur.com/F9BhEoz.png")
                .thumbnail("https://i.imgur.com/F9BhEoz.png")
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



    private static List<JsonNode> splitJsonArrayIntoChunks(ArrayNode jsonArray, int chunkSize) {
        List<JsonNode> chunks = new ArrayList<>();
        int totalItems = jsonArray.size();

        for (int i = 0; i < totalItems; i += chunkSize) {
            int endIndex = Math.min(i + chunkSize, totalItems);

            // Create a new ArrayNode for each chunk
            ArrayNode chunk = jsonArray.arrayNode();
            for (int j = i; j < endIndex; j++) {
                chunk.add(jsonArray.get(j));
            }

            chunks.add(chunk);
        }

        return chunks;
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


        //wrap from here

            for (Document document : users.find()) {
                long userId = document.getLong("userid");
                String sessionKey = document.getString("sessionkey");

                if (userId == 176505480011710464L) {
                    System.out.println("this is viperfan so we skip cuz we dont have his tracks yet");
                    continue;  // Skip this document and move to the next iteration
                }

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





        //to here



        return Mono.empty();

    }


    static Mono<?> betterUpdateCommand(ObjectMapper objectMapper, CloseableHttpClient httpClient, Message message, GatewayDiscordClient client) throws UnsupportedEncodingException {
        MongoClient mongoClient = MongoClients.create(connectionString);
        MongoDatabase database = mongoClient.getDatabase("spongeybot");
        MongoCollection<Document> tracks = database.getCollection("tracks");
        MongoCollection<Document> userScrobblesCollection = database.getCollection("emperormeznik"); //todo change




        long userId = 559929980705046529L; //THISIS MEZ
        String sessionKey = "c07moFTuUJON8XXZxhtZYsu5TGfxqo9Q"; //todo chnage, currenlty spongey



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
        for (int i = 1; i <= totalPages; i++) {
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
        MongoCollection<Document> userScrobblesCollection = database.getCollection("_spongey");




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



    static Mono<?> getDailyListeningTime(Message message, GatewayDiscordClient client) throws JsonProcessingException, UnsupportedEncodingException {


        String[] command = message.getContent().split(" ");


        String timePeriod = "";
        if (command.length >= 2) {
            timePeriod = command[1];
            System.out.println("timePeriod: " + timePeriod);
        } else {
            return message.getChannel().flatMap(channel -> channel.createMessage("Valid commands: $time 2023, $time 2022, $time today, $time currentweek, $time lastweek, $time month etc"));

        }


        MongoClient mongoClient = MongoClients.create(connectionString);
        MongoDatabase database = mongoClient.getDatabase("spongeybot");
        MongoCollection<Document> userCollection = database.getCollection("users");

        String username = client.getUserById(Snowflake.of(message.getAuthor().get().getId().asLong()))
                .block()
                .getUsername();


        MongoCollection<Document> scrobblesCollection = database.getCollection(username);

        Document userDocument = userCollection.find(Filters.eq("userid", message.getAuthor().get().getId().asLong())).first();

        ZoneId userTimeZone = ZoneId.of(userDocument.getString("timezone"));

        int listeningTime = 0;

        int totalTrackslistenedto = 0;


        //if command is 'today'
        ZonedDateTime currentTimeInTimezone = ZonedDateTime.now(userTimeZone);


        ZonedDateTime lastMinuteOfPeriod = null;
        ZonedDateTime firstMinuteOfPeriod = null;


        if (timePeriod.equals("today")) {

            lastMinuteOfPeriod = currentTimeInTimezone
                    .with(LocalDateTime.of(currentTimeInTimezone.toLocalDate(), LocalTime.MAX));

            firstMinuteOfPeriod = currentTimeInTimezone
                    .with(LocalDateTime.of(currentTimeInTimezone.toLocalDate(), LocalTime.MIDNIGHT));




        } else if (timePeriod.equals("currentweek")) {

            firstMinuteOfPeriod = currentTimeInTimezone
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    .with(LocalTime.MIDNIGHT);

            lastMinuteOfPeriod = currentTimeInTimezone
                    .with(TemporalAdjusters.next(DayOfWeek.SUNDAY))
                    .with(LocalTime.MAX);


        }  else if (timePeriod.equals("lastweek")) {

            firstMinuteOfPeriod = currentTimeInTimezone
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
                    .minusDays(6)
                    .with(LocalTime.MIDNIGHT);

            lastMinuteOfPeriod = currentTimeInTimezone
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
                    .with(LocalTime.MAX);

        } else if (timePeriod.equals("2023")) {

            firstMinuteOfPeriod = currentTimeInTimezone
                    .with(TemporalAdjusters.firstDayOfYear())
                    .with(LocalTime.MIDNIGHT);

            lastMinuteOfPeriod = currentTimeInTimezone
                    .with(TemporalAdjusters.lastDayOfYear())
                    .with(LocalTime.MAX);

        }

        else {
            return message.getChannel().flatMap(channel -> channel.createMessage("Valid commands: $time 2023, $time 2022, $time today, $time week, $time month etc"));

        }



        System.out.println("This is first minute: " + firstMinuteOfPeriod);
        System.out.println("This is last minute: " +  lastMinuteOfPeriod);

        long utsStartOfPeriod = firstMinuteOfPeriod.toInstant().getEpochSecond();

        long utsEndOfPeriod = lastMinuteOfPeriod.toInstant().getEpochSecond();



        System.out.println(utsStartOfPeriod);
        System.out.println(utsEndOfPeriod);


        //looping through all scroblbles for user
        for (Document doc : scrobblesCollection.find()) {

            int timestamp = doc.getInteger("timestamp");


            if (timestamp > utsStartOfPeriod && timestamp < utsEndOfPeriod) {
                int trackDurationSeconds = doc.getInteger("duration");
                totalTrackslistenedto += 1;

                listeningTime += trackDurationSeconds;

            }
        }


        System.out.println(userTimeZone);
        System.out.println("this is time for period " + timePeriod + ": " + listeningTime);


        int finalListeningTime = listeningTime;
        int finalTotalTrackslistenedto = totalTrackslistenedto;
        String finalTimePeriod = timePeriod;
        ZonedDateTime finalFirstMinuteOfPeriod = firstMinuteOfPeriod;
        ZonedDateTime finalLastMinuteOfPeriod = lastMinuteOfPeriod;
        return message.getChannel().flatMap(channel -> channel.createMessage("Time spent listening to music for " + finalTimePeriod + ": " + finalListeningTime /60 + " minutes, and total tracks listened to: " + finalTotalTrackslistenedto + "\n this is between " + finalFirstMinuteOfPeriod + " and " + finalLastMinuteOfPeriod));
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
                .addField("", "$wk", false)
                .addField("", "$toptracks", false)
                .addField("", "$topartists", false)
                .addField("", "$a", false)
                .addField("", "$scrobblelb", false)
                .addField("", "$login", false)
                .addField("", "$scrobbles 2021, $scrobbles 2023 etc", false)
                .addField("", "$year 2023, $year 2022 etc", false)
                .addField("", "more features coming soon", false);

        return message.getChannel().flatMap(channel -> channel.createMessage(embedBuilder.build()));


    }

    static Mono<?> scrobbleLbCommand(Message message, ObjectMapper objectMapper, CloseableHttpClient httpClient, GatewayDiscordClient client) {

        Map<String, Integer> unsortedscrobbleLb = new HashMap<>();


        EmbedCreateSpec.Builder embedBuilder = createEmbed("Scrobble leaderboard");

        MongoClient mongoClient = MongoClients.create(connectionString);
        MongoDatabase database = mongoClient.getDatabase("spongeybot");
        MongoCollection<Document> collection = database.getCollection("users");


        for (Document document : collection.find()) {

            String sessionKey = document.getString("sessionkey");
            long userId = document.getLong("userid");

            String getTrackInfoUrl = BASE_URL + "?method=user.getInfo&api_key=" + API_KEY + "&sk=" + sessionKey + "&format=json";
            System.out.println("This is user info for some user: " + getTrackInfoUrl);


            JsonNode rootNode =  getJsonNodeFromUrl(objectMapper, getTrackInfoUrl, httpClient, message);


            String userPlaycount = rootNode.get("user").get("playcount").asText();

            String username = client.getUserById(Snowflake.of(userId))
                    .block()
                    .getUsername();

            unsortedscrobbleLb.put(username, Integer.valueOf(userPlaycount));

        }


        List<Map.Entry<String, Integer>> list = new LinkedList<>(unsortedscrobbleLb.entrySet());

        Collections.sort(list, Collections.reverseOrder(Comparator.comparing(Map.Entry::getValue)));

        Map<String, Integer> sortedMap = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : list) {
            sortedMap.put(entry.getKey(), entry.getValue());
        }

        for (Map.Entry<String, Integer> entry : sortedMap.entrySet()) {
            embedBuilder.addField(entry.getKey() , "Plays: " + entry.getValue() , false);
        }



        return message.getChannel().flatMap(channel -> channel.createMessage(embedBuilder.build()));
    }

    static Mono<?> wktCommand(Message message, ObjectMapper objectMapper, CloseableHttpClient httpClient, GatewayDiscordClient client) {
        String trackName = Arrays.stream(message.getContent().split(" "))
                .collect(Collectors.toList())
                .stream()
                .skip(1)
                .collect(Collectors.joining("+"));

        String artist = "";


      MongoClient mongoClient = MongoClients.create(connectionString);
            MongoDatabase database = mongoClient.getDatabase("spongeybot");
            MongoCollection<Document> collection = database.getCollection("users");

        String userSessionKey = getUserSessionKey(message);




        if (userSessionKey == null) {
            return message.getChannel().flatMap(channel -> channel.createMessage("please login to use this command"));

        }

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
            } catch (Exception e) {
                e.printStackTrace();
            }
        }


        EmbedCreateSpec.Builder embedBuilder = createEmbed("Who knows track: " + trackName.replace("+", " ")
                + " by " + artist.replace("+", " ") + "?");




        for (Document document : collection.find()) {

            String sessionKey = document.getString("sessionkey");
            long userId = document.getLong("userid");

            String getTrackInfoUrl = BASE_URL + "?method=track.getInfo&api_key=" + API_KEY + "&artist=" + artist + "&track=" + trackName + "&sk=" + sessionKey + "&format=json";

            System.out.println("This is track info url: " + getTrackInfoUrl);

            JsonNode rootNode = getJsonNodeFromUrl(objectMapper, getTrackInfoUrl, httpClient, message);
            String userPlaycountForTrack = rootNode.get("track").get("userplaycount").asText();
            String username = client.getUserById(Snowflake.of(userId))
                    .block()
                    .getUsername();

            embedBuilder.addField(username, "Plays: " + userPlaycountForTrack, false);
        }


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

    static Mono<?> wkCommand(Message message, ObjectMapper objectMapper, CloseableHttpClient httpClient, GatewayDiscordClient client) {
        String artistName = Arrays.stream(message.getContent().split(" "))
                .collect(Collectors.toList())
                .stream()
                .skip(1)
                .collect(Collectors.joining("+"));



        Map<String, Integer> unsortedWk = new HashMap<>();


        MongoClient mongoClient = MongoClients.create(connectionString);
        MongoDatabase database = mongoClient.getDatabase("spongeybot");
        MongoCollection<Document> collection = database.getCollection("users");


        String userSessionKey = getUserSessionKey(message);


        if (userSessionKey == null) {
            return message.getChannel().flatMap(channel -> channel.createMessage("please login to use this command"));
        }

        if (artistName.equals("")) {
           artistName = getUserCurrentTrackArtistName(objectMapper, httpClient, message);
        }

        EmbedCreateSpec.Builder embedBuilder = createEmbed("Who knows " + artistName.replace("+", " ")
                + "?");

        for (Document document : collection.find()) {

            String sessionKey = document.getString("sessionkey");
            long userId = document.getLong("userid");
            String getArtistInfoUrl = BASE_URL + "?method=artist.getinfo&artist=" + artistName + "&api_key=" + API_KEY + "&sk=" + sessionKey + "&autocorrect=1" + "&format=json";
            System.out.println("This is artist info url for some user: " + getArtistInfoUrl);

            JsonNode rootNode = getJsonNodeFromUrl(objectMapper, getArtistInfoUrl, httpClient, message);
            String userPlaycountForArtist = rootNode.get("artist").get("stats").get("userplaycount").asText();

            String username = client.getUserById(Snowflake.of(userId))
                    .block()
                    .getUsername();

            unsortedWk.put(username, Integer.valueOf(userPlaycountForArtist));
        }

        List<Map.Entry<String, Integer>> list = new LinkedList<>(unsortedWk.entrySet());

        // Sort the list based on the values
        Collections.sort(list, Collections.reverseOrder(Comparator.comparing(Map.Entry::getValue)));

        Map<String, Integer> sortedMap = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : list) {
            sortedMap.put(entry.getKey(), entry.getValue());
        }

        for (Map.Entry<String, Integer> entry : sortedMap.entrySet()) {
            embedBuilder.addField(entry.getKey() , "Plays: " + entry.getValue() , false);
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
        MongoCollection<Document> collection = database.getCollection("users");


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

                                    if (message.getContent().startsWith("$a")) {
                                        return artistInfoCommand(message,objectMapper, httpClient, client);
                                    }

                                    if (message.getContent().startsWith("$wkt")) {
                                        return wktCommand(message,objectMapper, httpClient, client);
                                    }

                                    if (message.getContent().startsWith("$wk")) {
                                       return wkCommand(message, objectMapper, httpClient, client);
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
                                        try {
                                            return getDailyListeningTime(message, client);
                                        } catch (JsonProcessingException e) {
                                            throw new RuntimeException(e);
                                        } catch (UnsupportedEncodingException e) {
                                            throw new RuntimeException(e);
                                        }
                                    }





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
















