import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.*;
import com.mongodb.client.model.Filters;
import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClient;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Message;


import discord4j.core.object.presence.ClientActivity;
import discord4j.core.object.presence.ClientPresence;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.rest.util.Image;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.bson.Document;
import org.bson.conversions.Bson;
import reactor.core.publisher.Mono;


import java.io.*;
import java.net.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;



public class SpongeyBot {

    static String connectionString = System.getenv("CONNECTION_STRING");

    private static final String BOT_TOKEN = System.getenv("BOT_TOKEN");

    private static final String BASE_URL = "http://ws.audioscrobbler.com/2.0/";

    private static final String API_KEY = System.getenv().get("API_KEY");

    private static final String API_SECRET = System.getenv("API_SECRET");


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
                JsonNode rootNode;
                int totalPages = 0;
                String getRecentUserTracksUrl = BASE_URL + "?method=user.getrecenttracks&limit=200&api_key=" + API_KEY + "&sk=" + sessionKey + "&format=json";

                try {
                    rootNode =  Service.getJsonNodeFromUrl(objectMapper, getRecentUserTracksUrl, httpClient);
                    totalPages = Integer.parseInt(rootNode.path("recenttracks").path("@attr").path("totalPages").asText());
                    System.out.println(" total pages: " + totalPages);

                } catch (Exception e) {
                    e.printStackTrace();
                }






                outerLoop:
                for (int i = 1; i <= totalPages; i++) {

                    String urlPerPage = BASE_URL + "?method=user.getrecenttracks&limit=200&page=" + i + "&api_key=" + API_KEY + "&sk=" + sessionKey + "&format=json";
                    JsonNode pageNode = Service.getJsonNodeFromUrl(objectMapper, urlPerPage, httpClient);
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
                                JsonNode thisNode = Service.getJsonNodeFromUrl(objectMapper, trackInfoUrl, httpClient);
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


        JsonNode rootNode;
        int totalPages = 0;
        try {
            rootNode =  Service.getJsonNodeFromUrl(objectMapper, getRecentUserTracksUrl, httpClient);
            totalPages = Integer.parseInt(rootNode.path("recenttracks").path("@attr").path("totalPages").asText());
            System.out.println(" total pages: " + totalPages);

        } catch (Exception e) {
            e.printStackTrace();
        }

        //todo change i to whatever number it crashes
        for (int i = 240; i <= totalPages; i++) {
            String urlPerPage = BASE_URL + "?method=user.getrecenttracks&limit=200&page=" + i + "&api_key=" + API_KEY + "&sk=" + sessionKey + "&format=json";
            JsonNode pageNode = Service.getJsonNodeFromUrl(objectMapper, urlPerPage, httpClient);

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
                        JsonNode thisNode = Service.getJsonNodeFromUrl(objectMapper, trackInfoUrl, httpClient);
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


        EmbedCreateSpec.Builder embedBuilder = Service.createEmbed("Blend of " + username + " with " + username2 + " requested by " + username);

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

    public static List<Double> getNewArtistsAndTracks(List<Document> relevantDocuments, MongoCollection<Document> scrobbleCollection) {

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

        EmbedCreateSpec.Builder embedBuilder = Service.createEmbed("Overview for " + username);


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



            List<Double> trackAndArtistPercents = getNewArtistsAndTracks(relevantDocuments, userScrobblesCollection);


            embedBuilder.addField(dateWeLoopThrough, scrobblesForDay + " scrobbles | " + timeScrobbled/60 + " minutes\n " + trackAndArtistPercents.get(0)*100 + "% new tracks | " + trackAndArtistPercents.get(1)*100 + "% new artists" + "\n Top Artist: " + topArtistString +  "\nTop Album: " + topAlbumString + "\nTop Track: " + topTrackString, false);
        }

        return message.getChannel().flatMap(channel -> channel.createMessage(embedBuilder.build()));

    }

    static Mono<?> helpCommand(Message message) {

        EmbedCreateSpec.Builder embedBuilder = Service.createEmbed("Help")
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
        JsonNode rootNode =  Service.getJsonNodeFromUrl(objectMapper, url, httpClient);
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


        static Mono<?> loginCommand(Message message, ObjectMapper objectMapper, CloseableHttpClient httpClient) {

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
                    String token;
                    //Get user to auth first
                    try {
                        JsonNode rootNode =  Service.getJsonNodeFromUrl(objectMapper, url, httpClient);

                        token = rootNode.get("token").asText();
                        String requestAuthUrl = "http://www.last.fm/api/auth/?api_key=" + API_KEY + "&token=" + token;


                        EmbedCreateSpec.Builder embedBuilder = Service.createEmbed("Login to last.fm").addField("Login here: ", requestAuthUrl, false);


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

                    JsonNode rootNode = Service.getJsonNodeFromUrl(objectMapper, getSessionUrl, httpClient);

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


    private static Mono<?> handleMessage(MessageCreateEvent event, ObjectMapper objectMapper, CloseableHttpClient httpClient, GatewayDiscordClient client) {
        Message message = event.getMessage();
        String content = message.getContent();

        if (message.getContent().startsWith("$scrobblelb")) {
            return LeaderboardCommands.scrobbleLbCommand(message,objectMapper, httpClient, client);
        }

        if (message.getContent().equalsIgnoreCase("$a")) {
            return ArtistCommands.artistInfoCommand(message,objectMapper, httpClient, client);
        }

        if (message.getContent().startsWith("$wkt")) {
            return TrackCommands.wktCommand(message,objectMapper, httpClient, client);
        }

        if (message.getContent().startsWith("$wk")) {
            try {
                return ArtistCommands.wkCommand(message, objectMapper, httpClient, client);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }


        if (message.getContent().equalsIgnoreCase("$topartists")) {
            return ArtistCommands.topArtistsCommand(message, objectMapper, httpClient, client);
        }

        if (message.getContent().equalsIgnoreCase("$toptracks")) {
            return TrackCommands.topTracksCommand(message, objectMapper, httpClient, client);
        }

        if (message.getContent().equalsIgnoreCase("$login")) {
            return loginCommand(message, objectMapper, httpClient);
        }

        if (message.getContent().equalsIgnoreCase("$help")) {
            return helpCommand(message);
        }

        if (message.getContent().startsWith("$scrobbles")) {
            try {
                return UserScrobbleCommands.getScrobblesInTimeframe(message, client);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }

        if (message.getContent().startsWith("$year")) {
            try {
                return UserScrobbleCommands.getYearlyInfo(message, client);
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
                    return UserScrobbleCommands.getDailyListeningTime(message, client);
                } catch (JsonProcessingException | UnsupportedEncodingException e) {
                    throw new RuntimeException(e);
                }

            } else if (command.equals("$timelb")) {
                System.out.println("running time leaderboard command");

                try {
                    return LeaderboardCommands.getTimeLeaderboard(message, client);
                } catch (JsonProcessingException | UnsupportedEncodingException e) {
                    throw new RuntimeException(e);
                }
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


        if (message.getContent().startsWith("$artisttime")) {
            try {
                return ArtistCommands.getArtistTime(message, client, objectMapper, httpClient);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }

        if (message.getContent().startsWith("$at")) {
            try {
                return ArtistCommands.getArtistTracks(message, client);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }

        if (message.getContent().startsWith("$trackinfo")) {
            try {
                return TrackCommands.getTrackInfo(message, client);
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
                return TrackCommands.durationTrackCommand(message, client);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }

        if (message.getContent().startsWith("$artistlb")) {
            return LeaderboardCommands.artistLbCommand(message, client);
        }


        if (message.getContent().startsWith("$tracklb")) {
            return LeaderboardCommands.tracklbCommand(message, client);
        }


        if (message.getContent().startsWith("$crownlb")) {
            try {
                return LeaderboardCommands.getCrownLb(message, client);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }

        if (message.getContent().startsWith("$blend")) {
            return blendWithUser(message, client);
        }

        if (message.getContent().startsWith("$crownsclose")) {
            try {
                return CrownsCommands.crownsCloseCommand(message, client);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }

        if (message.getContent().startsWith("$crowns")) {
            try {
                return CrownsCommands.getCrowns(message, client);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }

        if (message.getContent().startsWith("$most")) {
            try {
                return TrackCommands.getSongListenedToMostOneDay(message, client);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }

        if (message.getContent().startsWith("$topscrobbleddays")) {
            try {
                return UserScrobbleCommands.getTopScrobbledDays(message, client);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }


        if (message.getContent().equalsIgnoreCase("$testingbetter")) {
            return AdminCommands.testingbetterupdatecommand();
        }

        if (message.getContent().equalsIgnoreCase("$testusername")) {
            return AdminCommands.testUsername(client);
        }


        if (message.getContent().equalsIgnoreCase("$betterupdaterecentcommand")) {
            try {
                return betterupdaterecentcommand(objectMapper, httpClient, message, client);
            } catch (JsonProcessingException | UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        }

        return Mono.empty();

    }

    public static void main(String[] args) {



            ObjectMapper objectMapper = new ObjectMapper();
        CloseableHttpClient httpClient = HttpClients.createDefault();


        DiscordClient.create(BOT_TOKEN)
                .withGateway(client -> {
                    client.on(MessageCreateEvent.class)
                            .flatMap(event -> handleMessage(event, objectMapper, httpClient, client))
                            .subscribe();

                    client.on(ButtonInteractionEvent.class)
                            .flatMap(event -> {
                                try {
                                    return UserScrobbleCommands.handleButtonInteraction(event);
                                } catch (JsonProcessingException e) {
                                    throw new RuntimeException(e);
                                }
                            })
                            .subscribe();

                    return client.onDisconnect();
                })
                .block();


    }
}
















