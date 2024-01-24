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
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.bson.Document;
import org.bson.conversions.Bson;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ArtistCommands {


    static String connectionString = System.getenv("CONNECTION_STRING");

    private static final String BASE_URL = "http://ws.audioscrobbler.com/2.0/";

    private static final String API_KEY = System.getenv().get("API_KEY");


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


        String userSessionKey = Service.getUserSessionKey(message);


        Bson filter = Filters.regex("artist",  artistName.replace("+", " "), "i");


        if (userSessionKey == null) {
            return message.getChannel().flatMap(channel -> channel.createMessage("please login to use this command"));
        }


        if (artistName.equals("")) {
            artistName = Service.getUserCurrentTrackArtistName(objectMapper, httpClient, message);
            filter = Filters.regex("artist",  artistName.replace("+", " "), "i");

        } else {

            String searchUrl = "https://ws.audioscrobbler.com/2.0/?method=artist.search&artist=" + artistName + "&api_key=" + API_KEY + "&format=json";


            JsonNode rootNodeForSearch =  Service.getJsonNodeFromUrl(objectMapper, searchUrl, httpClient);

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


        EmbedCreateSpec.Builder embedBuilder = Service.createEmbed("Who knows " + artistName.replace("+", " ")
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
                String lastFmUserURL = "https://www.last.fm/user/" + "spongeystar16";
                if (!iterator.hasNext()) {
                    wkString.append("\uD83D\uDDD1  ").append("**" + "[" + entry.getKey() + "](" + lastFmUserURL + ")").append(" - ").append(entry.getValue()).append("** plays \n");
                } else if (count == 1) {
                    wkString.append("\uD83D\uDC51  ").append("**" + "[" + entry.getKey() + "](" + lastFmUserURL + ")").append(" - ").append(entry.getValue()).append("** plays \n");
                } else {
                    wkString.append(count).append(".   ").append("**  " + "[" + entry.getKey() + "](" + lastFmUserURL + ")").append(" - ").append(entry.getValue()).append("** plays \n");
                }

                count++;
            }

            //owner is person w top plays
            Map.Entry<String, Integer> topOwner = list.get(0);
            int ownerPlays = topOwner.getValue();
            String owner = topOwner.getKey();

            embedBuilder.addField("",  wkString.toString(), false);


            embedBuilder.addField("", CrownsCommands.claimCrown(message, client, mongoClient, database, artistName.replace("+", " "), owner, ownerPlays), false);



        }



        return message.getChannel().flatMap(channel -> channel.createMessage(embedBuilder.build()));
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
        EmbedCreateSpec.Builder embedBuilder = Service.createEmbed(username  + "'s tracks for " + artist);
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
                    artist = Service.getUserCurrentTrackArtistName(objectMapper, httpClient, message);
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

            artist = Service.getUserCurrentTrackArtistName(objectMapper, httpClient, message);
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



    static Mono<?> artistInfoCommand(Message message, ObjectMapper objectMapper, CloseableHttpClient httpClient, GatewayDiscordClient client) {
        String artistName = Arrays.stream(message.getContent().split(" "))
                .collect(Collectors.toList())
                .stream()
                .skip(1)
                .collect(Collectors.joining("+"));

        if (artistName.equals("")) {
            artistName = Service.getUserCurrentTrackArtistName(objectMapper, httpClient, message);
        }

        String artistSummary = "";
        System.out.println("artist name: 0" + artistName);

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


        EmbedCreateSpec.Builder embedBuilder = Service.createEmbed((artistName.replace("+", " ")));

        embedBuilder.addField("Summary: ", artistSummary, false);
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


        String userSessionKey = Service.getUserSessionKey(message);

        if (userSessionKey == null) {
            return message.getChannel().flatMap(channel -> channel.createMessage("please login to use this command"));

        }

        String url = BASE_URL + "?method=user.gettopartists&api_key=" + API_KEY + "&sk=" + userSessionKey + "&limit=5" + "&format=json";
        return message.getChannel()
                .flatMap(userResponse -> {
                    try {
                        JsonNode rootNode =  Service.getJsonNodeFromUrl(objectMapper, url, httpClient);

                        EmbedCreateSpec.Builder embedBuilder = Service.createEmbed("Your top artists");


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


}
