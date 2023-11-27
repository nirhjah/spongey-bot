import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClient;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Message;

import discord4j.core.spec.EmbedCreateSpec;
import discord4j.rest.util.Color;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.bson.Document;
import org.bson.conversions.Bson;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.*;
import java.util.*;
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


   /* static Mono<?> updateRecentCommand(ObjectMapper objectMapper, CloseableHttpClient httpClient, Message message) {
        MongoClient mongoClient = MongoClients.create(connectionString);
        MongoDatabase database = mongoClient.getDatabase("spongeybot");
        MongoCollection<Document> collection = database.getCollection("scrobbles");

        Document query = new Document("userid", message.getAuthor().get().getId().asLong());
        Document result = collection.find(query).first();
        if (result != null) {
            //user found to have full update, now check for smaller update

            //get latest timestamp for track we got saved
            //get current uts


        }
    }*/

    static Mono<?> updateCommand(ObjectMapper objectMapper, CloseableHttpClient httpClient, Message message, GatewayDiscordClient client) {


        MongoClient mongoClient = MongoClients.create(connectionString);
        MongoDatabase database = mongoClient.getDatabase("spongeybot");




        long userId = 1;
        String sessionKey = "sessionkey";

        String username = client.getUserById(Snowflake.of(userId))
                .block()
                .getUsername();


            System.out.println("Now updating user: " + userId + " username: " + username);



            String getRecentUserTracksUrl = BASE_URL + "?method=user.getrecenttracks&limit=200&api_key=" + API_KEY + "&sk=" + sessionKey + "&format=json";
            System.out.println("recent tracksu rl: " + getRecentUserTracksUrl);

            ArrayNode mergedResult = objectMapper.createArrayNode();


            JsonNode rootNode = null;
            int totalPages = 0;
            try {
                rootNode =  getJsonNodeFromUrl(objectMapper, getRecentUserTracksUrl, httpClient, message);
                totalPages = Integer.parseInt(rootNode.path("recenttracks").path("@attr").path("totalPages").asText());
                System.out.println(" total pages: " + totalPages);

            } catch (Exception e) {
                e.printStackTrace();
            }


            for (int i = 1; i <= totalPages; i++) {
                String urlPerPage = BASE_URL + "?method=user.getrecenttracks&limit=200&page=" + i + "&api_key=" + API_KEY + "&sk=" + sessionKey + "&format=json";
                JsonNode pageNode = getJsonNodeFromUrl(objectMapper, urlPerPage, httpClient, message);
                ArrayNode tracksToProcess = new ObjectMapper().createArrayNode();

                JsonNode listOfTracksForGivenPage = pageNode.get("recenttracks").get("track");
                JsonNode firstTrackNode = listOfTracksForGivenPage.get(0);

                for (JsonNode track : listOfTracksForGivenPage) {
                    if (!(firstTrackNode.has("@attr") && firstTrackNode.get("@attr").has("nowplaying")
                            && firstTrackNode.get("@attr").get("nowplaying").asText().equals("true"))) {

                        tracksToProcess.add(track);

                    }
                }


                System.out.println("we on page: " + i);
                System.out.println("This is url for page " + i + ": " + urlPerPage);
                mergedResult.addAll((ArrayNode) tracksToProcess);
            }






            //after mergedresult done


            List<JsonNode> chunks = splitJsonArrayIntoChunks(mergedResult, 1000);
            Collections.reverse(chunks);
            for (int i = 0; i < chunks.size(); i++) {
                String chunkString = chunks.get(i).toString();

                Document dataDocument = new Document("userId", userId)
                        .append("chunkIndex", i)
                        .append("jsonData", chunkString);

                MongoCollection<Document> jsonDataCollection = database.getCollection("scrobbles");
                jsonDataCollection.insertOne(dataDocument);
            }

            System.out.println("Successfully updated user: " + userId);




        return message.getChannel().flatMap(channel -> channel.createMessage("All data  updated :D"));


    }

    private static long convertToUnixTimestamp(LocalDateTime localDateTime) {
        return localDateTime.atZone(ZoneId.systemDefault()).toEpochSecond();
    }

    static Mono<?> getScrobblesInTimeframe(ObjectMapper objectMapper, CloseableHttpClient httpClient, Message message) throws JsonProcessingException {
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

        int scrobbleCounterForWeek = 0;
        int currentTimeUTS = (int) (System.currentTimeMillis() / 1000);


        MongoClient mongoClient = MongoClients.create(connectionString);
        MongoDatabase database = mongoClient.getDatabase("spongeybot");
        MongoCollection<Document> collection = database.getCollection("scrobbles");

        //gets all docs in scrobbles collectionf for a user
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
            JsonNode jsonNode = objectMapper.readTree(doc.get("jsonData").toString());


            for (JsonNode track : jsonNode) {

                int dateNode = Integer.parseInt(track.get("date").get("uts").asText());

                if (dateNode  < endOfGivenYearUTS && dateNode >= startOfGivenYearUTS) {
                    scrobbleCounterForWeek += 1;
                }

            }
        }

        String finalYear = year;
        int finalScrobbleCounterForWeek = scrobbleCounterForWeek;
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
            String getArtistInfoUrl = BASE_URL + "?method=artist.getinfo&artist=" + artistName + "&api_key=" + API_KEY + "&sk=" + sessionKey + "&format=json";
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
                                            return getScrobblesInTimeframe(objectMapper, httpClient, message);
                                        } catch (JsonProcessingException e) {
                                            throw new RuntimeException(e);
                                        }
                                    }

                                 /*   if (message.getContent().equalsIgnoreCase("$update")) {
                                        return updateCommand(objectMapper, httpClient, message, client);
                                    }*/

                                    return Mono.empty();
                                }))
                .block();
    }
}