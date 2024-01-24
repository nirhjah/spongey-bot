import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.EmbedCreateSpec;
import org.apache.http.impl.client.CloseableHttpClient;
import org.bson.Document;
import org.bson.conversions.Bson;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class TrackCommands {

    static String connectionString = System.getenv("CONNECTION_STRING");


    private static final String BASE_URL = "http://ws.audioscrobbler.com/2.0/";

    private static final String API_KEY = System.getenv().get("API_KEY");



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

        String userSessionKey = Service.getUserSessionKey(message);




        if (userSessionKey == null) {
            return message.getChannel().flatMap(channel -> channel.createMessage("please login to use this command"));

        }

        Bson filter;

        filter = Filters.regex("track",  trackName.replace("+", " "), "i");

        if (trackName.equals("")) {
            //get users current track, name from that and set trackName to that
            String getRecentUserTracksUrl = BASE_URL + "?method=user.getrecenttracks&api_key=" + API_KEY + "&sk=" + userSessionKey + "&format=json";
            System.out.println("recent tracks url: " + getRecentUserTracksUrl);
            JsonNode rootNode =  Service.getJsonNodeFromUrl(objectMapper, getRecentUserTracksUrl, httpClient);

            try {
                JsonNode trackNode = rootNode.path("recenttracks").path("track");
                JsonNode firstTrackNode = trackNode.get(0);
                JsonNode trackNodeMain = firstTrackNode.path("name");
                JsonNode artistNode = firstTrackNode.path("artist").path("#text");
                artist = artistNode.asText().replace(" ", "+");
                trackName = trackNodeMain.asText().replace(" ", "+");


                filter = Filters.and(
                        Filters.regex("track", "^" + Pattern.quote(trackName.replace("+", " ")), "i"),
                        Filters.regex("artist", "^" + Pattern.quote(artist.replace("+", " ")), "i")
                );

            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {

            String searchUrl = "https://ws.audioscrobbler.com/2.0/?method=track.search&track=" + trackName + "&api_key=" + API_KEY + "&format=json";


            JsonNode rootNodeForSearch =  Service.getJsonNodeFromUrl(objectMapper, searchUrl, httpClient);

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






        EmbedCreateSpec.Builder embedBuilder = Service.createEmbed("Who knows " + trackName.replace("+", " ")
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
            String lastFmUserURL = "https://www.last.fm/user/" + "spongeystar16";

            wktString.append(count).append(". ").append("**" + "[" + entry.getKey() + "](" + lastFmUserURL + ")").append(" - ").append(entry.getValue()).append("** plays \n");
            count++;

        }

        embedBuilder.addField("", wktString.toString(), false);



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


    static Mono<?> topTracksCommand(Message message, ObjectMapper objectMapper, CloseableHttpClient httpClient, GatewayDiscordClient client) {

        MongoClient mongoClient = MongoClients.create(connectionString);

        String userSessionKey = Service.getUserSessionKey(message);

        if (userSessionKey == null) {
            return message.getChannel().flatMap(channel -> channel.createMessage("please login to use this command"));

        }


        String url = BASE_URL + "?method=user.gettoptracks&api_key=" + API_KEY + "&sk=" + userSessionKey + "&limit=5" + "&format=json";

        System.out.println("top tracks url: " + url);

        return message.getChannel()

                .flatMap(userResponse -> {


                    try {

                        JsonNode rootNode =  Service.getJsonNodeFromUrl(objectMapper, url, httpClient);

                        EmbedCreateSpec.Builder embedBuilder = Service.createEmbed("Your top tracks");


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

        EmbedCreateSpec.Builder embedBuilder = Service.createEmbed("track info for " + track);

        embedBuilder.addField("total scrobbles: ", String.valueOf(totalScrobbles), false);

        embedBuilder.addField("first time listened to: ", firstDate, false);

        embedBuilder.addField("last time listened to: ", maxDate, false);

        embedBuilder.addField("day listened to most: ", dayListenedToMost, false);

        embedBuilder.addField("total time listened to: ", totalDuration/60 + " minutes", false);


        return message.getChannel().flatMap(channel -> channel.createMessage(embedBuilder.build()));


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

        EmbedCreateSpec.Builder embedBuilder = Service.createEmbed("Your top 5 longest tracks");

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



}
