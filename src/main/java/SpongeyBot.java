import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.*;
import com.mongodb.client.model.*;
import com.mongodb.client.result.DeleteResult;
import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClient;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
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
import java.text.DecimalFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.mongodb.client.model.Filters.*;


public class SpongeyBot {


    static String connectionString = System.getenv("CONNECTION_STRING");

    private static final MongoClient mongoClient = MongoClients.create(connectionString);


    private static final String BOT_TOKEN = System.getenv("BOT_TOKEN");

    private static final String BASE_URL = "http://ws.audioscrobbler.com/2.0/";

    private static final String API_KEY = System.getenv().get("API_KEY");

    private static final String API_SECRET = System.getenv("API_SECRET");


    private static final String SPOTIFY_CLIENT_ID = System.getenv().get("SPOTIFY_CLIENT_ID");


    private static final String SPOTIFY_CLIENT_SECRET = System.getenv().get("SPOTIFY_CLIENT_SECRET");




    static void betterupdaterecentcommand(ObjectMapper objectMapper, CloseableHttpClient httpClient) throws Exception {
            MongoDatabase database = mongoClient.getDatabase("spongeybot");
            MongoCollection<Document> tracks = database.getCollection("tracks");
            MongoCollection<Document> users = database.getCollection("users");


            for (Document document : users.find()) {
                long userId = document.getLong("userid");
                String username = document.getString("username");
                String sessionKey = document.getString("sessionkey");

             /*   if (userId == 259492875153178634L) {
                    continue;
                }*/

                System.out.println("Now updating recent of user: " + userId + " username: " + username);
                MongoCollection<Document> userScrobblesCollection = database.getCollection(username);

                //Getting latest timestamp
                int maxTimestamp;
                Document lastDocument = userScrobblesCollection.find().sort(Sorts.descending("timestamp")).first();
                System.out.println("LAST DOC: " + lastDocument);
                maxTimestamp = lastDocument.getInteger("timestamp");
                System.out.println("Max timestamp: " + maxTimestamp);


                //GETTING TOTAL PAGES WE HAVE NOW
                JsonNode rootNode;
                int totalPages = 0;
                String getRecentUserTracksUrl = BASE_URL + "?method=user.getrecenttracks&limit=200&api_key=" + API_KEY + "&sk=" + sessionKey + "&format=json";

                rootNode =  Service.getJsonNodeFromUrl(objectMapper, getRecentUserTracksUrl, httpClient);
                totalPages = Integer.parseInt(rootNode.path("recenttracks").path("@attr").path("totalPages").asText());



                outerLoop:
                for (int i = 1; i <= totalPages; i++) {

                    String urlPerPage = BASE_URL + "?method=user.getrecenttracks&limit=200&page=" + i + "&api_key=" + API_KEY + "&sk=" + sessionKey + "&format=json";
                    JsonNode pageNode = Service.getJsonNodeFromUrl(objectMapper, urlPerPage, httpClient);
                    JsonNode listOfTracksForGivenPage = pageNode.get("recenttracks").get("track");

                    for (JsonNode track : listOfTracksForGivenPage) {

                        if (track.has("@attr") && track.get("@attr").has("nowplaying")
                                && track.get("@attr").get("nowplaying").asText().equals("true")) {
                            System.out.println("Skipping over this track as it's currently being played so doens't have duration");
                            continue ;
                        }

                        else if (Integer.parseInt(track.get("date").get("uts").asText())  < maxTimestamp) {
                            System.out.println("Already have this track so move on");
                            break outerLoop;
                        }

                        if (Integer.parseInt(track.get("date").get("uts").asText()) > maxTimestamp) {
                            //this is if track isnt a nowplaying and date of track is bigger than latesttimestamp
                            System.out.println("This is a new track: " + track);

                            String artist = track.get("artist").get("#text").asText();
                            String trackName = track.get("name").asText();

                            //first check if track alr saved in tracks table with duration
                            Document foundTrack = tracks.find(
                                    and(
                                            eq("track", trackName),
                                            eq("artist", artist)
                                    )
                            ).maxTime(2, TimeUnit.HOURS).first();

                            String album = track.get("album").get("#text").asText();
                            String trackUrl = track.get("url").asText();
                            String albumUrl = track.get("image").get(2).path("#text").asText();
                            int timestamp = Integer.parseInt(track.get("date").get("uts").asText());

                            int duration;

                            //this is for saving songs in the tracks collection

                            if (foundTrack != null) {
                                duration = Integer.parseInt(foundTrack.get("duration").toString());
                                System.out.println("Found track");
                            } else {
                                System.out.println("Track not found so add into tracks db");

                                int finalDurationSeconds = 0;

                                String encodedArtistName = URLEncoder.encode(artist, "UTF-8");
                                String encodedTrackName = URLEncoder.encode(trackName, "UTF-8");
                                System.out.println("encode artist: " + encodedArtistName);
                                System.out.println("encode track: " + encodedTrackName);

                                String trackInfoUrl =  "http://ws.audioscrobbler.com/2.0/?method=track.getInfo&api_key=" + API_KEY +  "&artist=" + encodedArtistName + "&track=" + encodedTrackName + "&format=json";
                                 JsonNode thisNode = Service.getJsonNodeFromUrl(objectMapper, trackInfoUrl, httpClient);
                                //get spotify duration here, if artist dont match use 165 dont use last fm

                                String artistAndTrack = trackName + " " + artist;
                                System.out.println("This is artistandtrack: " + artistAndTrack);

                                String spotifyAccessToken = SpotifyService.getAccessToken(SPOTIFY_CLIENT_ID, SPOTIFY_CLIENT_SECRET);

                                //FIRST CHECK SPOTIFY ARTIST SAME, IF SAME USE SPOTIFY DURATION
                                String trackSpotifyId = SpotifyService.getTrackSpotifyId(spotifyAccessToken, artistAndTrack);


                                System.out.println("Checking spotify duration");

                                if (trackSpotifyId != null) {
                                    String spotifyArtist = SpotifyService.getTrackArtist(trackSpotifyId, spotifyAccessToken);
                                    System.out.println("SPOTIFY ARTIST: " + spotifyArtist + " LASTFM ARTIST: " + artist);

                                    if (spotifyArtist.trim().equalsIgnoreCase(artist)) {
                                        //use spotify
                                        finalDurationSeconds = SpotifyService.getTrackDuration(trackSpotifyId, spotifyAccessToken) / 1000;
                                        System.out.println("Found atist track on spotify so using duration. spotify artist: " + spotifyArtist + " , " + artistAndTrack + " " + finalDurationSeconds );

                                    }    else {

                                        if (thisNode == null) {
                                            System.out.println("Node was someone null when trying to get last fm duration so using 165");
                                            finalDurationSeconds = 165;
                                        } else {
                                            finalDurationSeconds = Integer.parseInt(thisNode.get("track").get("duration").asText())/1000;
                                        }


                                        System.out.println("artist didnt match: " + spotifyArtist + " and " + artist + " so using last fm duration: " + finalDurationSeconds);

                                        if (finalDurationSeconds == 0) {
                                            finalDurationSeconds = 165;
                                        }

                                    }
                                } else {
                                    finalDurationSeconds = Integer.parseInt(thisNode.get("track").get("duration").asText())/1000;
                                    System.out.println("spotify artist was null:   so using last fm duration: " + finalDurationSeconds);

                                    if (finalDurationSeconds == 0) {
                                        finalDurationSeconds = 165;
                                    }

                                }





                                Document newTrack = new Document("track", trackName)
                                        .append("artist", artist)
                                        .append("duration", finalDurationSeconds);

                                tracks.insertOne(newTrack);

                                duration = finalDurationSeconds;
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

                        }

                    }

                }
            }


    }


    static Mono<?> betterUpdateCommand(ObjectMapper objectMapper, CloseableHttpClient httpClient, Message message, GatewayDiscordClient client) throws UnsupportedEncodingException {
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
                            and(
                                    eq("track", trackName),
                                    eq("artist", artist)
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
        double roundedTracks = Math.round(newTracks * 10.0) / 100.0;

        newArtists = (double) newArtistCount / artistMap.size();
        double roundedArtists = Math.round(newArtists * 10.0) / 100.0;



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



        MongoDatabase database = mongoClient.getDatabase("spongeybot");
        MongoCollection<Document> userScrobblesCollection = database.getCollection(username);
        MongoCollection<Document> userCollection = database.getCollection("users");
        MongoCollection<Document> overviewCollection = database.getCollection("overview");


        Document userDocument = userCollection.find(eq("userid", userid)).first();


        ZoneId userTimeZone = ZoneId.of(userDocument.getString("timezone"));

        EmbedCreateSpec.Builder embedBuilder = Service.createEmbed("Overview for " + username);


        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMMM yyyy");
        LocalDate currentDate = LocalDate.now(userTimeZone);



            //for the last 5 days including today
        for (int i = 0; i < 5; i++) {

            LocalDate date = currentDate.minusDays(i);
            String dateWeLoopThrough = date.format(formatter);

            //overview for date we loopin through
            Document overview = overviewCollection.find(and(eq("date", date), eq("username", username))).first();
            //if not null AND not currentdate (we don't want to save current date as that always changes throughout the day)
            // then we want to grab that info
            //else (if non existent) we wanna create a new one

            if (overview != null && date != currentDate) {
                System.out.println("There is an overview available for date: " + date + " so we are getting it");


                int scrobbles = overview.getInteger("scrobbles");
                List<Double> trackAndArtistPercents = (List<Double>) overview.get("trackAndArtistPercents");
                int time = overview.getInteger("time");
                String topArtist = overview.getString("topArtist");
                String topTrack = overview.getString("topTrack");
                String topAlbum = overview.getString("topAlbum");

                DecimalFormat df = new DecimalFormat("0.0%");
                embedBuilder.addField(dateWeLoopThrough, scrobbles + " scrobbles | " + time/60 + " minutes\n " + df.format(trackAndArtistPercents.get(0)) + " new tracks | " + df.format(trackAndArtistPercents.get(1)) + " new artists" + "\n Top Artist: " + topArtist +  "\nTop Album: " + topAlbum + "\nTop Track: " + topTrack, false);


            } if (overview == null && date != currentDate) {
                System.out.println("No overview for " + date + " so creating new");
                //create new document for user for the date

                ZonedDateTime startOfDay = date.atStartOfDay(userTimeZone);
                ZonedDateTime endOfDay = date.plusDays(1).atStartOfDay(userTimeZone);

                Bson filter = and(
                        Filters.gte("timestamp", startOfDay.toEpochSecond()),
                        lt("timestamp", endOfDay.toEpochSecond())
                );

                int scrobblesForDay = 0;
                int timeScrobbled = 0;
                Map<List<String>, Integer> topTrack = new HashMap<>(); //track,artist + count
                Map<String, Integer> topArtist = new HashMap<>();
                Map<List<String>, Integer> topAlbum = new HashMap<>(); //album,artist + count


                List<Document> relevantDocuments = new ArrayList<>();
                userScrobblesCollection.find(filter).into(relevantDocuments);

                for (Document doc : relevantDocuments) {

                    //FIELD: SCROBBLES
                    //FIELD: TIMESCROBBLED
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
                    //FIELD: TOP ARTIST STRING
                    topArtistString = listOfTopArtist.get(0).getKey() + " - " + listOfTopArtist.get(0).getValue() + " plays";
                }


                if (listOfTopTracks.isEmpty()) {
                    topTrackString = "";

                } else {
                    //FIELD: TOP TRACK STRING
                    topTrackString = listOfTopTracks.get(0).getKey().get(0) + " - " + listOfTopTracks.get(0).getKey().get(1) + " - " + listOfTopTracks.get(0).getValue() + " plays";
                }


                if (listOfTopAlbums.isEmpty()) {
                    topAlbumString = ""; } else {
                    //FIELD: TOP ALBUM STRING
                    topAlbumString = listOfTopAlbums.get(0).getKey().get(0) + " - " + listOfTopAlbums.get(0).getKey().get(1) + " - " + listOfTopAlbums.get(0).getValue() + " plays";
                }


                //FIELD: TRACK AND ARTIST PERCENTS
                List<Double> trackAndArtistPercents = getNewArtistsAndTracks(relevantDocuments, userScrobblesCollection);



                Document newUsersOverviewForDate = new Document("username", username)
                        .append("date", date)
                        .append("scrobbles", scrobblesForDay)
                        .append("time", timeScrobbled)
                        .append("topArtist", topArtistString)
                        .append("topTrack", topTrackString)
                        .append("topAlbum", topAlbumString)
                        .append("trackAndArtistPercents", trackAndArtistPercents);

                overviewCollection.insertOne(newUsersOverviewForDate);
                DecimalFormat df = new DecimalFormat("0.0%");

                embedBuilder.addField(dateWeLoopThrough, scrobblesForDay + " scrobbles | " + timeScrobbled/60 + " minutes\n " + df.format(trackAndArtistPercents.get(0)) + " new tracks | " + df.format(trackAndArtistPercents.get(1)) + " new artists" + "\n Top Artist: " + topArtistString +  "\nTop Album: " + topAlbumString + "\nTop Track: " + topTrackString, false);


            } if (date == currentDate) {

                System.out.println("Overview for today's date: " + currentDate + " so just showing not creating");

                ZonedDateTime startOfDay = date.atStartOfDay(userTimeZone);
                ZonedDateTime endOfDay = date.plusDays(1).atStartOfDay(userTimeZone);

                Bson filter = and(
                        Filters.gte("timestamp", startOfDay.toEpochSecond()),
                        lt("timestamp", endOfDay.toEpochSecond())
                );

                int scrobblesForDay;
                int timeScrobbled = 0;
                Map<List<String>, Integer> topTrack = new HashMap<>(); //track,artist + count
                Map<String, Integer> topArtist = new HashMap<>();
                Map<List<String>, Integer> topAlbum = new HashMap<>(); //album,artist + count


                List<Document> relevantDocuments = new ArrayList<>();
                userScrobblesCollection.find(filter).into(relevantDocuments);

                scrobblesForDay = relevantDocuments.size();
                for (Document doc : relevantDocuments) {

                    //FIELD: SCROBBLES
                    //FIELD: TIMESCROBBLED
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
                    //FIELD: TOP ARTIST STRING
                    topArtistString = listOfTopArtist.get(0).getKey() + " - " + listOfTopArtist.get(0).getValue() + " plays";
                }


                if (listOfTopTracks.isEmpty()) {
                    topTrackString = "";

                } else {
                    //FIELD: TOP TRACK STRING
                    topTrackString = listOfTopTracks.get(0).getKey().get(0) + " - " + listOfTopTracks.get(0).getKey().get(1) + " - " + listOfTopTracks.get(0).getValue() + " plays";
                }


                if (listOfTopAlbums.isEmpty()) {
                    topAlbumString = ""; } else {
                    //FIELD: TOP ALBUM STRING
                    topAlbumString = listOfTopAlbums.get(0).getKey().get(0) + " - " + listOfTopAlbums.get(0).getKey().get(1) + " - " + listOfTopAlbums.get(0).getValue() + " plays";
                }


                //FIELD: TRACK AND ARTIST PERCENTS
                List<Double> trackAndArtistPercents = getNewArtistsAndTracks(relevantDocuments, userScrobblesCollection);
                DecimalFormat df = new DecimalFormat("0.0%");
                embedBuilder.addField(dateWeLoopThrough, scrobblesForDay + " scrobbles | " + timeScrobbled/60 + " minutes\n " + df.format(trackAndArtistPercents.get(0)) + " new tracks | " + df.format(trackAndArtistPercents.get(1)) + " new artists" + "\n Top Artist: " + topArtistString +  "\nTop Album: " + topAlbumString + "\nTop Track: " + topTrackString, false);

            }

            //end
        }

        // Cleaning old data
        LocalDate currentDate2 = LocalDate.now();
        LocalDate fiveDaysAgo = currentDate2.minusDays(5);

        System.out.println("five days ago: " + fiveDaysAgo);
        System.out.println("OLD DOCS: ");

        DeleteResult deleteResult = overviewCollection.deleteMany(and(
                eq("username", username),
                lt("date", fiveDaysAgo)));

        System.out.println("Deleted docs: " + deleteResult.getDeletedCount());


        return message.getChannel().flatMap(channel -> channel.createMessage(embedBuilder.build()));

    }

    static Mono<?> helpCommand(Message message) {

        EmbedCreateSpec.Builder embedBuilder = Service.createEmbed("Help")
                .addField("", "**$login**", false)
                .addField("Scrobbles", "**$topscrobbleddays** - the top ten days you scrobbled the most \n **$year** - your listening stats for given year \n **$scrobbles** - how many scrobbles you have for a given year", false)

                .addField("Tracks", "**$trackinfo** - provides stats about the given/current track \n **$toptracks** - your top tracks of all time \n **$wkt** - who has listened to current/given track \n **$tracksnotlistened** - tracks of given/current artist you have not listened to yet \n **$fakefan** shows who listened to a current/given track first", false)

                .addField("Artists", "**$artisttime** - total time spent listening to artist \n **$artisttracks** - all tracks of given/current artist you have listened to \n **$artistinfo** - info about given/current artist \n **$topartists** - your top artists of all time \n **$wk** - who has listened to given/current artist", false)

                .addField("Leaderboards", "$scrobblelb\n $timelb\n $artistlb\n $tracklb", false)

                .addField("Crowns", "**$crownlb** \n **$crowns** - crowns you have claimed \n **$crownsclose** - crowns you are close to stealing", false)

                .addField("Featured", "**$featured** - shows who is currently featured \n **$featuredlog** - shows when you have been featured \n", false)


                .addField("", "Some commands allow you to mention other users, to see their stats", false)

                .addField("", "more features coming soon", false);

        return message.getChannel().flatMap(channel -> channel.createMessage(embedBuilder.build()));


    }

    static Mono<?> updateBotPic(ObjectMapper objectMapper, CloseableHttpClient httpClient, GatewayDiscordClient client, ReadyEvent event) throws IOException {


        MongoDatabase database = mongoClient.getDatabase("spongeybot");
        MongoCollection<Document> collection = database.getCollection("users");
        MongoCollection<Document> featuredCollection = database.getCollection("featured");
        MongoCollection<Document> days = database.getCollection("featuredDays");


        DayOfWeek currentDay = LocalDate.now().getDayOfWeek();

        if (Objects.equals(currentDay.toString(), "MONDAY")) { //todo add check
            System.out.println("Today is Monday so we are assigning days to users");


            List<String> users = new ArrayList<>();
            Map<String, Integer> userDayAssignments = new HashMap<>();

            for (Document user : collection.find()) {
                String username = user.getString("username");
                users.add(username);
                userDayAssignments.put(username, 0);
            }

            List<String> daysOfWeek = new ArrayList<>(Arrays.asList("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"));

            Collections.shuffle(users);

            Random random = new Random();


            for (String user : users) {
                int randomDayVal = random.nextInt(daysOfWeek.size());
                String day = daysOfWeek.get(randomDayVal);
                if (userDayAssignments.get(user) == 0) {
                    userDayAssignments.put(user, userDayAssignments.get(user) + 1);
                    System.out.println("Day: " + day + " for user: " + user);

                    Document query = new Document("day", day);

                    Document update = new Document("$set", new Document("username", user));

                    days.updateOne(query, update);

                    daysOfWeek.remove(day);

                }

            }

            System.out.println("days now: " + daysOfWeek);


            for (String day: daysOfWeek) {
                System.out.println(day);
                int randomUserVal = random.nextInt(users.size());
                String user = users.get(randomUserVal);

                Document query = new Document("day", day);

                Document update = new Document("$set", new Document("username", user));

                days.updateOne(query, update);

                daysOfWeek.remove(day);

            }
        }



        //Finished assignment of days

        //Getting current day
        String day = LocalDate.now().getDayOfWeek().name();


        //first check if this command has been run today already so by check if date filter
        Bson dateFilter = Filters.all("date", LocalDate.now());

        Document featuredDocAlready = featuredCollection.find(dateFilter).first();


        //getting user who is featured today



        Bson filter = Filters.regex("day", day, "i");

        System.out.println("This is filter: " + filter);
       String username = days.find(filter).first().getString("username");

       Bson filterUser = Filters.all("username", username);
       String userSessionKey = (String) collection.find(filterUser).first().get("sessionkey");
        long userId = collection.find(filterUser).first().getLong("userid");




        if (featuredDocAlready != null) {
            System.out.println("Already ran pic command so updating status only:");

            //already ran this command and have featured so just set status of bot
            client.updatePresence(ClientPresence.online(ClientActivity.playing(username + "'s top album of the week!"))).block();

        } else {
            //none yet so do it everything
            System.out.println("First time running command update pic today:");
            String url = BASE_URL + "?method=user.gettopalbums&api_key=" + API_KEY + "&sk=" + userSessionKey  + "&limit=1" + "&period=7day" + "&format=json";
            System.out.println(url);
            JsonNode rootNode =  Service.getJsonNodeFromUrl(objectMapper, url, httpClient);
            JsonNode albumNode = rootNode.path("topalbums").path("album");
            String firstAlbumNode = albumNode.get(0).path("image").get(2).path("#text").asText();
            String artistName = albumNode.get(0).path("artist").path("name").asText();
            String albumName = albumNode.get(0).path("name").asText();
            System.out.println("album image: " + firstAlbumNode);

            //set it to the bot picture
            Image image = Image.ofUrl(firstAlbumNode).block();
            event.getClient().edit().withAvatar(image).block();
            client.updatePresence(ClientPresence.online(ClientActivity.playing(username + "'s top album of the week!"))).block();

            LocalDate currentDate = LocalDate.now();
            Document newFeatured = new Document("username", username)
                    .append("artist", artistName)
                    .append("album", albumName)
                    .append("albumImage", firstAlbumNode)
                    .append("date", currentDate);
            featuredCollection.insertOne(newFeatured);
            //return message.getChannel().flatMap(channel -> channel.createMessage(username + " is today's featured user :D"));
            return Mono.empty();

        }

        return Mono.empty();

    }

    static Mono<?> featuredCommand(Message message) {

        MongoDatabase database = mongoClient.getDatabase("spongeybot");
        MongoCollection<Document> featuredCollection = database.getCollection("featured");

        Bson dateFilter = Filters.all("date", LocalDate.now());


        Document featuredUserDocument = featuredCollection.find(dateFilter).first();

        EmbedCreateSpec.Builder embedBuilder = Service.createEmbed("Today's featured user: " + featuredUserDocument.getString("username"));

        embedBuilder.addField("", "Artist: " + featuredUserDocument.getString("artist"), false);
        embedBuilder.addField("", "Album: " + featuredUserDocument.getString("album"), false);

        embedBuilder.thumbnail(featuredUserDocument.getString("albumImage"));
        return message.getChannel().flatMap(channel -> channel.createMessage(embedBuilder.build()));

    }



    static Mono<?> featuredLogCommand(Message message, GatewayDiscordClient client) {

        long userid = message.getAuthor().get().getId().asLong();

        String username = client.getUserById(Snowflake.of(userid))
                .block()
                .getUsername();


        if (!message.getUserMentions().isEmpty()) {
            username = message.getUserMentions().get(0).getUsername();
        }

        MongoDatabase database = mongoClient.getDatabase("spongeybot");
        MongoCollection<Document> featuredCollection = database.getCollection("featured");

        Bson userFilter = Filters.all("username", username);


        EmbedCreateSpec.Builder embedBuilder = Service.createEmbed(username + "'s featured log");

        FindIterable<Document> documents = featuredCollection.find(userFilter);

        MongoCursor<Document> cursor = documents.iterator();

        if (!cursor.hasNext()) {
            // No documents found
            embedBuilder.addField("You haven't been featured yet :(", "", false);
        } else {
            // Documents found
            for (Document document : documents) {
                embedBuilder.addField(document.get("date").toString(), "Artist: " + document.getString("artist") + "\nAlbum: " + document.getString("album"), false);

            }

        }






        return message.getChannel().flatMap(channel -> channel.createMessage(embedBuilder.build()));

    }


    static Mono<?> loginCommand(Message message, ObjectMapper objectMapper, CloseableHttpClient httpClient) {

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


    private static Mono<?> handleMessage(MessageCreateEvent event, ObjectMapper objectMapper, CloseableHttpClient httpClient, GatewayDiscordClient client) throws Exception {
        Message message = event.getMessage();

        if (message.getContent().startsWith("$scrobblelb")) {
            return LeaderboardCommands.scrobbleLbCommand(message, client);
        }

        if (message.getContent().startsWith("$artistinfo")) {
            return ArtistCommands.artistInfoCommand(message,objectMapper, httpClient, client);
        }

        if (message.getContent().startsWith("$tracksnotlistened")) {
            return ArtistCommands.tracksNotListenedTo(message,objectMapper, httpClient, client);
        }

        if (message.getContent().startsWith("$wkt")) {
            return TrackCommands.wktCommand(message,objectMapper, httpClient, client);
        }

        if (message.getContent().startsWith("$wk")) {
            try {
                return ArtistCommands.wkCommand(message, objectMapper, httpClient, client);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }


        if (message.getContent().startsWith("$topartists")) {
            return ArtistCommands.topArtistsCommand(message, objectMapper, httpClient);
        }

        if (message.getContent().startsWith("$toptracks")) {
            return TrackCommands.topTracksCommand(message, objectMapper, httpClient);
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
                    return UserScrobbleCommands.getListeningTime(message, client);
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


        if (message.getContent().equals("$featured")) {
          return featuredCommand(message);
        }


        if (message.getContent().startsWith("$featuredlog")) {
            return featuredLogCommand(message, client);
        }



     /*   if (message.getContent().equalsIgnoreCase("$update")) {
            try {
                return betterUpdateCommand(objectMapper, httpClient, message, client);
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        }*/


        if (message.getContent().startsWith("$artisttime")) {
            try {
                return ArtistCommands.getArtistTime(message, client, objectMapper, httpClient);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }

        if (message.getContent().startsWith("$artisttracks")) {
            try {
                return ArtistCommands.getArtistTracks(message, client, objectMapper, httpClient);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }

        if (message.getContent().startsWith("$trackinfo")) {
            try {
                return TrackCommands.getTrackInfo(message, client, objectMapper, httpClient);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }

        if (message.getContent().startsWith("$fakefan")) {
            try {
                return TrackCommands.getFakeFan(message, client, objectMapper, httpClient);
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


        if (message.getContent().equalsIgnoreCase("$testusername")) {
            return AdminCommands.testUsername(client);
        }


       if (message.getContent().equalsIgnoreCase("$betterupdaterecentcommand")) {
            try {
                betterupdaterecentcommand(objectMapper, httpClient);
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        }

        return Mono.empty();

    }

    public static void main(String[] args) throws Exception {


            ObjectMapper objectMapper = new ObjectMapper();
        CloseableHttpClient httpClient = HttpClients.createDefault();


        //Update everyone's scrobbles every 5 minutes
       ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> {
            try {
                System.out.println("UPDATING EVERYONES SCROBBLES NOW");
                betterupdaterecentcommand(objectMapper, httpClient);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 1, 5, TimeUnit.MINUTES);




        DiscordClient.create(BOT_TOKEN)
                .withGateway(client -> {


                    client.on(ReadyEvent.class)
                            .flatMap(event -> {
                               System.out.println("BOT STARTED UP");
                                try {
                                    return updateBotPic(objectMapper, httpClient, client, event);
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            })
                            .subscribe();

                    client.on(MessageCreateEvent.class)
                            .flatMap(event -> {
                                try {
                                    return handleMessage(event, objectMapper, httpClient, client);
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            })
                            .subscribe();

                    client.on(ButtonInteractionEvent.class)
                            .flatMap(event -> {
                                try {
                                    return UserScrobbleCommands.handleButtonInteraction(event);
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            })
                            .subscribe();

                    return client.onDisconnect();
                })
                .block();


    }
}
















