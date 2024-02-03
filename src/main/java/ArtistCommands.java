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
import discord4j.core.object.Embed;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.EmbedCreateFields;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.MessageCreateSpec;

import org.apache.http.impl.client.CloseableHttpClient;
import org.bson.Document;
import org.bson.conversions.Bson;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ArtistCommands {

    private static Map<List<String>, List<String>> tracksNotListenedToCache = new HashMap<>();

    private static int currentPage = 1;

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
        EmbedCreateSpec.Builder embedBuilder = Service.createEmbed(oldname + " tracks " + username + " has not listened to yet ");
        embedBuilder.footer("Tracks not including songs artist has featured on/appears on", "https://i.imgur.com/F9BhEoz.png");

        Set<String> allArtistsTracks = SpotifyService.getArtistsTracksAll(spotifyAccessToken, artistSpotifyId);


        List<String> tracksNotListenedTo;

        List<String> expectedKey = new ArrayList<>();
        expectedKey.add(username);
        expectedKey.add(oldname);

        if (tracksNotListenedToCache.containsKey(expectedKey)) {
            tracksNotListenedTo = tracksNotListenedToCache.get(expectedKey);
        } else {
            tracksNotListenedTo = Service.getListOfTracksNotListenedTo(artistName, allArtistsTracks, scrobbles);
            tracksNotListenedToCache.put(expectedKey, tracksNotListenedTo);
            System.out.println("Saved in cache with name: " + expectedKey);
        }

        if (currentPage == 1) {
            int endIndex = Math.min(1 + 10, tracksNotListenedTo.size());
            List<String> currentTracks = tracksNotListenedTo.subList(1, endIndex);


            StringBuilder fieldToSave = new StringBuilder();


            int counter = 1;
            for (String track : currentTracks) {
                fieldToSave.append(counter).append(". ").append(track).append("\n");
                counter += 1;
            }

            embedBuilder.addField("", fieldToSave.toString(), false);

        }




       System.out.println("THese are tracks u haevnt listned to: " + tracksNotListenedTo);


        Button nextButton = Button.secondary("nextTracks", "Next Page");
        Button prevButton = Button.secondary("prevTracks", "Prev Page").disabled();



        ActionRow actionRow = ActionRow.of(prevButton, nextButton);


        MessageCreateSpec messageCreateSpec = MessageCreateSpec.builder()
                .addEmbed(embedBuilder.build())
                .addComponent(actionRow)
                .build();



        return message.getChannel().flatMap(channel -> channel.createMessage(messageCreateSpec));



    }



     static void handleTracksNotListenedButtonClick(Message message, Embed oldEmbed, boolean next) throws Exception {




         if (next) {
            currentPage++;
        } else {
            currentPage--;
        }


         EmbedCreateSpec.Builder embedBuilder = EmbedCreateSpec.builder();
         Embed.Author existingAuthor = oldEmbed.getAuthor().orElse(null);
         String authorName = existingAuthor != null ? existingAuthor.getName().orElse(null) : null;
         String authorUrl = existingAuthor != null ? existingAuthor.getUrl().orElse(null) : null;
         String authorIconUrl = existingAuthor != null ? existingAuthor.getIconUrl().orElse(null) : null;

         System.out.println("THIS IS COMPONENT: " + message.getComponents().get(0));
         ActionRow editableActionRow = (ActionRow) message.getComponents().get(0);
         Button editablePrev = (Button) editableActionRow.getChildren().get(0);

         Button editableNext = (Button) editableActionRow.getChildren().get(1);




         String[] words = authorName.split("\\s+");

         //getting artist name from string
         StringBuilder resultBuilder = new StringBuilder();
         for (String word : words) {
             if ("tracks".equalsIgnoreCase(word)) {
                 break;
             }
             resultBuilder.append(word).append(" ");
         }

         String artistName = resultBuilder.toString();
         String username = words[words.length - 6];

         System.out.println("THiS IS Artist name: " + artistName);
         System.out.println("USERNAME: " + username);
         List<String> tracksNotListenedTo;

         System.out.println("This is cache after clickin button: " + tracksNotListenedToCache);
        System.out.println();


         List<String> expectedKey = new ArrayList<>();
         expectedKey.add(username);
         expectedKey.add(artistName.trim());

         if (tracksNotListenedToCache.containsKey(expectedKey)) {
             System.out.println("We have it in the cache");
             tracksNotListenedTo = tracksNotListenedToCache.get(expectedKey);
         } else {
             System.out.println("Not in the cache for artist: " + artistName.trim());
             MongoClient mongoClient = MongoClients.create(connectionString);
             MongoDatabase database = mongoClient.getDatabase("spongeybot");

             MongoCollection<Document> scrobbles = database.getCollection(username);
             String spotifyAccessToken = SpotifyService.getAccessToken(SPOTIFY_CLIENT_ID, SPOTIFY_CLIENT_SECRET);
             String artistSpotifyId = SpotifyService.getArtistSpotifyId(spotifyAccessToken, artistName.trim());
             Set<String> allArtistsTracks = SpotifyService.getArtistsTracksAll(spotifyAccessToken, artistSpotifyId);

             tracksNotListenedTo = Service.getListOfTracksNotListenedTo(artistName.trim(), allArtistsTracks, scrobbles);
             tracksNotListenedToCache.put(expectedKey, tracksNotListenedTo);
         }



         embedBuilder.color(oldEmbed.getColor().get())
                 .author(EmbedCreateFields.Author.of(authorName, authorUrl, authorIconUrl));


         int startIndex = currentPage * 10;
         int endIndex = Math.min(startIndex + 10, tracksNotListenedTo.size());



         System.out.println("This is start index: " + startIndex);
         System.out.println("This is end index: " + endIndex);

         if (startIndex > endIndex) {
             System.out.println("index too big");
             message.edit().withEmbeds(embedBuilder.build()).subscribe();

         }
         List<String> currentTracks = tracksNotListenedTo.subList(startIndex, endIndex);
         //need to fix indexes billie eilish was 20 17 here ?? how idk
         //17 is list  size, 21 is the startindex


         StringBuilder fieldToSave = new StringBuilder();


         int counter = startIndex;
         for (String track : currentTracks) {
             fieldToSave.append(counter).append(". ").append(track).append("\n");
             counter += 1;
         }

         System.out.println("This is counter size: " + counter + " and this is list size: " + tracksNotListenedTo.size());
         if (counter == tracksNotListenedTo.size()) {
             System.out.println("WE are displaying the last item on the list currently.");
                editableNext = editableNext.disabled(true);
                editablePrev = editablePrev.disabled(false);
         }  if (startIndex == 0) {
             System.out.println("start index is 0");
             editablePrev = editablePrev.disabled();
             editableNext = editableNext.disabled(false);
         } if (counter < tracksNotListenedTo.size()) {
             System.out.println("counter smaller than tracksnotlistened size");
             editableNext = editableNext.disabled(false);
         }

         else {
             System.out.println("in the else");
             editablePrev = editablePrev.disabled(false);
         }


         ActionRow newActionRow = ActionRow.of(editablePrev, editableNext);

         embedBuilder.addField("", fieldToSave.toString(), false);

         message.edit().withEmbeds(embedBuilder.build()).withComponents(newActionRow).subscribe();



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
        MongoCollection<Document> artists = database.getCollection("artists");

        String userSessionKey = Service.getUserSessionKey(message);

        Bson filter = Filters.regex("artist",  artistName.replace("+", " "), "i");


        if (userSessionKey == null) {
            return message.getChannel().flatMap(channel -> channel.createMessage("please login to use this command"));
        }


        if (artistName.equals("")) {
            artistName = Service.getUserCurrentTrackArtistName(objectMapper, httpClient, message);
            filter = Filters.regex("artist",  artistName.replace("+", " "), "i");

        } else {
            //get most recently listened to artist
            String searchUrl = "https://ws.audioscrobbler.com/2.0/?method=artist.search&artist=" + artistName + "&api_key=" + API_KEY + "&format=json";
            JsonNode rootNodeForSearch =  Service.getJsonNodeFromUrl(objectMapper, searchUrl, httpClient);
            JsonNode artistListForSearch = rootNodeForSearch.path("results").path("artistmatches").path("artist");

            if (artistListForSearch.size() > 0) {
                JsonNode firstTrackNode = artistListForSearch.get(0);
                artistName = firstTrackNode.path("name").asText();
                filter = Filters.regex("artist", "^" + Pattern.quote(artistName.replace("+", " ")), "i");
            }
        }




        String artistImageUrl;

        Bson artistFilter = Filters.regex("name", artistName.replace("+", " "), "i");
        if (artists.find(artistFilter).first() != null) {
            System.out.println("Artist saved in db already");
           artistImageUrl = artists.find(artistFilter).first().getString("image");
        } else {
            System.out.println("Artist not saved in db");
            String artistSummary;
            String artistInfoUrl = BASE_URL + "?method=artist.getinfo&artist=" + artistName.replace(" ", "+") + "&api_key=" + API_KEY + "&format=json";
            System.out.println("Artist Info URL: " + artistInfoUrl);
            JsonNode rootNode1 = Service.getJsonNodeFromUrl(objectMapper, artistInfoUrl, httpClient);
            JsonNode artistSummaryNode = rootNode1.path("artist").path("bio").path("summary");
            artistSummary = artistSummaryNode.asText();


            String spotifyAccessToken = SpotifyService.getAccessToken(SPOTIFY_CLIENT_ID, SPOTIFY_CLIENT_SECRET);
            String artistSpotifyId = SpotifyService.getArtistSpotifyId(spotifyAccessToken, artistName);
            artistImageUrl = SpotifyService.getArtistImageURL(artistSpotifyId, spotifyAccessToken);

            Document newArtist = new Document("name", artistName.replace("+", " "))
                    .append("image", artistImageUrl)
                    .append("bio", artistSummary);

            artists.insertOne(newArtist);

        }




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

        String artist;
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
        MongoCollection<Document> artists = database.getCollection("artists");

        Bson artistFilter = Filters.regex("name", artist, "i");
        Bson filter = Filters.regex("artist", artist, "i");


        int totalArtistTime = 0;

        for (Document doc : collection.find(filter)) {
            int duration = doc.getInteger("duration");
            totalArtistTime += duration;
        }

        EmbedCreateSpec.Builder embedBuilder = Service.createEmbed(username + "'s time spent listening to " + artist);
        embedBuilder.addField("", "You have spent a total of " + totalArtistTime/60 + " minutes listening to " + artist + "!", false);

        String artistImage;
        if (artists.find(artistFilter).first() != null) {
            artistImage = artists.find(artistFilter).first().getString("image");
            embedBuilder.thumbnail(artistImage);
        }


        return message.getChannel().flatMap(channel -> channel.createMessage(embedBuilder.build()));

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


        MongoClient mongoClient = MongoClients.create(connectionString);
        MongoDatabase database = mongoClient.getDatabase("spongeybot");
        MongoCollection<Document> artists = database.getCollection("artists");

        String artistSummary;
        System.out.println("artist name: " + artistName);

        Bson artistFilter = Filters.regex("name", artistName.replace("+", " "), "i");
        if (artists.find(artistFilter).first() != null) {
            System.out.println("Artist in db");
            artistSummary = artists.find(artistFilter).first().getString("bio");
        } else {
            String artistInfoUrl = BASE_URL + "?method=artist.getinfo&artist=" + artistName + "&api_key=" + API_KEY + "&format=json";
            System.out.println("Artist Info URL: " + artistInfoUrl);

            JsonNode rootNode1 = Service.getJsonNodeFromUrl(objectMapper, artistInfoUrl, httpClient);
            JsonNode artistSummaryNode = rootNode1.path("artist").path("bio").path("summary");
            System.out.println("This is artistsumarynode " +  artistSummaryNode);
            artistSummary = artistSummaryNode.asText();

        }


        artistName = artistName.replace("+", " ");

        int authorsArtistPlays = Service.getArtistPlays(userid, artistName, client);

        String spotifyAccessToken = SpotifyService.getAccessToken(SPOTIFY_CLIENT_ID, SPOTIFY_CLIENT_SECRET);
        String artistSpotifyId = SpotifyService.getArtistSpotifyId(spotifyAccessToken, artistName);


        String artistImageUrl = SpotifyService.getArtistImageURL(artistSpotifyId, spotifyAccessToken);


        MongoCollection<Document> scrobbles = database.getCollection(username);


        Set<String> allArtistsTracks = SpotifyService.getArtistsTracksAll(spotifyAccessToken, artistSpotifyId);
       // List<String> tracksNotListenedTo = Service.getListOfTracksNotListenedTo(artistName, allArtistsTracks, scrobbles);
        List<String> tracksNotListenedTo;
        List<String> expectedKey = new ArrayList<>();
        expectedKey.add(username);
        expectedKey.add(artistName.replace("+", " "));

        if (tracksNotListenedToCache.containsKey(expectedKey)) {
            System.out.println("already in cache");
            tracksNotListenedTo = tracksNotListenedToCache.get(expectedKey);
        } else {
            tracksNotListenedTo = Service.getListOfTracksNotListenedTo(artistName, allArtistsTracks, scrobbles);
            tracksNotListenedToCache.put(expectedKey, tracksNotListenedTo);
            System.out.println("Saved in cache with name: " + expectedKey);
        }

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
        String[] command = message.getContent().split(" ");

        String timePeriod = "";
        if (command.length >= 2) {
            timePeriod = command[1];
        }

        String periodForAPI = "";

        if (Objects.equals(timePeriod, "lifetime")) {
            periodForAPI = "overall";
        } else if (Objects.equals(timePeriod, "week")) {
            periodForAPI = "7day";
        } else if (Objects.equals(timePeriod, "month")) {
            periodForAPI = "1month";
        } else if (Objects.equals(timePeriod, "quarter")) {
            periodForAPI = "3month";
        } else if (Objects.equals(timePeriod, "halfyear")) {
            periodForAPI = "6month";
        } else if (Objects.equals(timePeriod, "year")) {
            periodForAPI = "12month";
        } else {
            return message.getChannel().flatMap(channel -> channel.createMessage("Invalid time period provided: week, month, quarter, halfyear, year, lifetime"));

        }
        System.out.println("Period: " + periodForAPI);


        String userSessionKey = Service.getUserSessionKey(message);

        if (userSessionKey == null) {
            return message.getChannel().flatMap(channel -> channel.createMessage("please login to use this command"));

        }

        String url = BASE_URL + "?method=user.gettopartists&api_key=" + API_KEY + "&sk=" + userSessionKey + "&limit=10&period=" + periodForAPI + "&format=json";
        JsonNode rootNode =  Service.getJsonNodeFromUrl(objectMapper, url, httpClient);
        JsonNode topArtistsNode = rootNode.path("topartists");

        EmbedCreateSpec.Builder embedBuilder = Service.createEmbed("Your top artists for " + timePeriod);

        int count = 1;
        for (JsonNode artistNode : topArtistsNode.path("artist")) {
            String name = artistNode.path("name").asText();
            String playcount = artistNode.path("playcount").asText();
            String artistUrl = artistNode.path("url").asText();
            String hyperlinkText = "**[" + name + "](" + artistUrl + ")**";
            embedBuilder.addField("", "**" + count + "**. " + hyperlinkText + ": " + playcount + " plays", false);
            count += 1;
        }

        return message.getChannel().flatMap(channel -> channel.createMessage(embedBuilder.build()));
    }


}
