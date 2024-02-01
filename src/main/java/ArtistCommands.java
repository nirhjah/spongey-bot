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


    private static final String SPOTIFY_CLIENT_ID = System.getenv().get("SPOTIFY_CLIENT_ID");


    private static final String SPOTIFY_CLIENT_SECRET = System.getenv().get("SPOTIFY_CLIENT_SECRET");


    static Mono<?> tracksNotListenedTo(Message message, ObjectMapper objectMapper, CloseableHttpClient httpClient, GatewayDiscordClient client) throws Exception {
        String[] command = message.getContent().split(" ");


        String artistName = "";
        long userid = message.getAuthor().get().getId().asLong();

        String username = client.getUserById(Snowflake.of(userid))
                .block()
                .getUsername();

        if (!message.getUserMentions().isEmpty()) {
            username = message.getUserMentions().get(0).getUsername();
        }


        if (command.length >= 2) {
            if (!message.getUserMentions().isEmpty()) {
                artistName = Arrays.stream(command)
                        .collect(Collectors.toList())
                        .subList(0, command.length - 1)
                        .stream()
                        .skip(1)
                        .collect(Collectors.joining("+"));

            } else {
                artistName = Arrays.stream(message.getContent().split(" "))
                        .collect(Collectors.toList())
                        .stream()
                        .skip(1)
                        .collect(Collectors.joining("+"));
            }
        }

        if (artistName.equals("")) {
            artistName = Service.getUserCurrentTrackArtistName(objectMapper, httpClient, message);
        }


        MongoClient mongoClient = MongoClients.create(connectionString);
        MongoDatabase database = mongoClient.getDatabase("spongeybot");
        MongoCollection<Document> scrobbles = database.getCollection(username);

        String spotifyAccessToken = SpotifyService.getAccessToken(SPOTIFY_CLIENT_ID, SPOTIFY_CLIENT_SECRET);
        String artistSpotifyId = SpotifyService.getArtistSpotifyId(spotifyAccessToken, artistName);


        String oldname = artistName.replace("+", " ");
        EmbedCreateSpec.Builder embedBuilder = Service.createEmbed(oldname + "'s tracks " + username + " has not listened to yet ");
        embedBuilder.footer("Tracks not including songs artist has featured on/appears on", "https://i.imgur.com/F9BhEoz.png");

        Set<String> allArtistsTracks = SpotifyService.getArtistsTracksAll(spotifyAccessToken, artistSpotifyId);
       /*List<String> tracksNotListenedTo = new ArrayList<>();
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
        */
        List<String> tracksNotListenedTo = Service.getListOfTracksNotListenedTo(artistName, allArtistsTracks, scrobbles);

        System.out.println("THese are tracks u haevnt listned to: " + tracksNotListenedTo);


        embedBuilder.addField("Total artist tracks: ", String.valueOf(allArtistsTracks.size()), false);
        embedBuilder.addField("Tracks you haven't listened to: ", String.valueOf(tracksNotListenedTo.size()), false);
        embedBuilder.addField("Tracks you have listened to: ", String.valueOf(allArtistsTracks.size() - tracksNotListenedTo.size()), false);

        return message.getChannel().flatMap(channel -> channel.createMessage(embedBuilder.build()));

    }
    static Mono<?> wkCommand(Message message, ObjectMapper objectMapper, CloseableHttpClient httpClient, GatewayDiscordClient client) throws Exception {
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

        //if spotify image dont work use this

        /*String searchingArtistName = artistName.replace(" ", "+");
        String artistInfoUrl = BASE_URL + "?method=artist.getinfo&artist=" + searchingArtistName + "&api_key=" + API_KEY + "&format=json";
        JsonNode rootNode =  Service.getJsonNodeFromUrl(objectMapper, artistInfoUrl, httpClient);
        String artistMBID = rootNode.path("artist").path("mbid").asText();
        System.out.println("Artists mbid: " + artistMBID);*/


        String spotifyAccessToken = SpotifyService.getAccessToken(SPOTIFY_CLIENT_ID, SPOTIFY_CLIENT_SECRET);

        System.out.println("This is aceess token: " + spotifyAccessToken);
        String artistSpotifyId = SpotifyService.getArtistSpotifyId(spotifyAccessToken, artistName);
        System.out.println("THIS IS SPOTIFY ARTIST ID: " + artistSpotifyId);

        String artistImageUrl = SpotifyService.getArtistImageURL(artistSpotifyId, spotifyAccessToken);
        System.out.println("This is spotify artist image url: " + artistImageUrl);


        EmbedCreateSpec.Builder embedBuilder = Service.createEmbed("Who knows " + artistName.replace("+", " ")
                + "?");

        embedBuilder.thumbnail(artistImageUrl);



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
                    wkString.append("\uD83D\uDDD1  ").append("**" + "[").append(entry.getKey()).append("](").append(lastFmUserURL).append(")").append(" - ").append(entry.getValue()).append("** plays \n");
                } else if (count == 1) {
                    wkString.append("\uD83D\uDC51  ").append("**" + "[").append(entry.getKey()).append("](").append(lastFmUserURL).append(")").append(" - ").append(entry.getValue()).append("** plays \n");
                } else {
                    wkString.append(count).append(".   ").append("**  " + "[").append(entry.getKey()).append("](").append(lastFmUserURL).append(")").append(" - ").append(entry.getValue()).append("** plays \n");
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

        String artist;
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
        Bson filter = Filters.eq("artist", artist);
//todo put back case sensitiveif required czuz it counted sum youtube vid

        Map<String, Integer> artistTracks = new HashMap<>();


        for (Document doc : collection.find(filter)) {
            int currentCount = artistTracks.getOrDefault(doc.getString("track"), 0);
          //  System.out.println("THIS IS DOC: " + doc);
            artistTracks.put(doc.getString("track"), currentCount + 1);
        }



        List<Map.Entry<String, Integer>> entryListArtistTracks = new ArrayList<>(artistTracks.entrySet());
        entryListArtistTracks.sort(Map.Entry.<String, Integer>comparingByValue().reversed());

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
        System.out.println("These are artists tracks size: " + artistTracks );


        System.out.println("These are artists tracks size2: " + artistTracks.size() );


        return message.getChannel().flatMap(channel -> channel.createMessage(embedBuilder.build()));

    }


    static Mono<?> getArtistTime(Message message, GatewayDiscordClient client, ObjectMapper objectMapper, CloseableHttpClient httpClient) throws JsonProcessingException {
        String[] command = message.getContent().split(" ");

        String artist = "";
        if (command.length >= 2) {
            System.out.println(Arrays.toString(command));

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




    static Mono<?> artistInfoCommand(Message message, ObjectMapper objectMapper, CloseableHttpClient httpClient, GatewayDiscordClient client) throws Exception {
        String[] command = message.getContent().split(" ");


        String artistName = "";
        long userid = message.getAuthor().get().getId().asLong();

        String username = client.getUserById(Snowflake.of(userid))
                .block()
                .getUsername();

        if (!message.getUserMentions().isEmpty()) {
            username = message.getUserMentions().get(0).getUsername();
            userid = message.getUserMentions().get(0).getId().asLong();
        }


        if (command.length >= 2) {
            if (!message.getUserMentions().isEmpty()) {
                artistName = Arrays.stream(command)
                        .collect(Collectors.toList())
                        .subList(0, command.length - 1)
                        .stream()
                        .skip(1)
                        .collect(Collectors.joining("+"));

            } else {
                artistName = Arrays.stream(message.getContent().split(" "))
                        .collect(Collectors.toList())
                        .stream()
                        .skip(1)
                        .collect(Collectors.joining("+"));
            }

        }



        if (artistName.equals("")) {
            artistName = Service.getUserCurrentTrackArtistName(objectMapper, httpClient, message);
        }

        String artistSummary;
        System.out.println("artist name: " + artistName);

        String artistInfoUrl = BASE_URL + "?method=artist.getinfo&artist=" + artistName + "&api_key=" + API_KEY + "&format=json";
        System.out.println("this is artist info url: " + artistInfoUrl);
        HttpGet requestArtistInfo = new HttpGet(artistInfoUrl);
        HttpResponse responseArtistInfo;
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


        artistName = artistName.replace("+", " ");

        int authorsArtistPlays = Service.getArtistPlays(userid, artistName, client);

        String spotifyAccessToken = SpotifyService.getAccessToken(SPOTIFY_CLIENT_ID, SPOTIFY_CLIENT_SECRET);
        String artistSpotifyId = SpotifyService.getArtistSpotifyId(spotifyAccessToken, artistName);


        String artistImageUrl = SpotifyService.getArtistImageURL(artistSpotifyId, spotifyAccessToken);

        MongoClient mongoClient = MongoClients.create(connectionString);
        MongoDatabase database = mongoClient.getDatabase("spongeybot");
        MongoCollection<Document> scrobbles = database.getCollection(username);


        Set<String> allArtistsTracks = SpotifyService.getArtistsTracksAll(spotifyAccessToken, artistSpotifyId);
        List<String> tracksNotListenedTo = Service.getListOfTracksNotListenedTo(artistName, allArtistsTracks, scrobbles);

        int numArtistTracks = allArtistsTracks.size();

        EmbedCreateSpec.Builder embedBuilder = Service.createEmbed((artistName.replace("+", " ")));

        embedBuilder.thumbnail(artistImageUrl);

        embedBuilder.addField("Summary: ", artistSummary, true);
        embedBuilder.addField(username + "'s total plays: ", authorsArtistPlays + " plays", false);
        embedBuilder.addField("First time listened: ", Service.getFirstTimeListeningToArtist(artistName, username), false);
        embedBuilder.addField("Tracks played: ", "You have listened to " + (allArtistsTracks.size() - tracksNotListenedTo.size()) + " out of " + numArtistTracks + " of " + artistName + "'s tracks", false);
        embedBuilder.footer("Tracks not including songs artist has featured on/appears on", "https://i.imgur.com/F9BhEoz.png");



        return message.getChannel().flatMap(channel -> channel.createMessage(embedBuilder.build()));
    }


    static Mono<?> topArtistsCommand(Message message, ObjectMapper objectMapper, CloseableHttpClient httpClient) {
        //week, month
        String[] command = message.getContent().split(" ");

        String timePeriod; //overall if blank
        if (command.length >= 2) {
            timePeriod = command[1];
            System.out.println("timePeriod: " + timePeriod);
        }


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
