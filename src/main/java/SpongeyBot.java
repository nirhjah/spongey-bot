import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;


public class SpongeyBot {


    private static final String BOT_TOKEN = System.getenv("BOT_TOKEN");

    private static final String BASE_URL = "http://ws.audioscrobbler.com/2.0/";

    private static final String API_KEY = System.getenv().get("API_KEY");


    private static final String API_SECRET = System.getenv("API_SECRET");

    static Mono<?> scrobbleLbCommand(Message message, ObjectMapper objectMapper, CloseableHttpClient httpClient, GatewayDiscordClient client) {

        EmbedCreateSpec.Builder embedBuilder = EmbedCreateSpec.builder()
                .color(Color.BLUE)
                .url("https://discord4j.com")
                .author("Scrobble leaderboard", "https://discord4j.com", "https://i.imgur.com/F9BhEoz.png")
                .timestamp(Instant.now());


        for (Map.Entry<Long, String> entry : SessionManager.getUserSessions().entrySet()) {

            String getTrackInfoUrl = "http://ws.audioscrobbler.com/2.0/?method=user.getInfo&api_key=" + API_KEY + "&sk=" + entry.getValue() + "&format=json";
            System.out.println("This is user info for some user: " + getTrackInfoUrl);
            HttpGet request = new HttpGet(getTrackInfoUrl);
            HttpResponse response = null;
            try {
                response = httpClient.execute(request);
            } catch (IOException e) {
                return message.getChannel().flatMap(channel -> channel.createMessage("error: couldn't execute user info url"));
            }

            System.out.println("API Response: " + response.getStatusLine());
            JsonNode rootNode = null;
            try {
                rootNode = objectMapper.readTree(response.getEntity().getContent());
            } catch (IOException e) {
                return message.getChannel().flatMap(channel -> channel.createMessage("error: couldn't get root node of user info url"));

            }
            String userPlaycount = rootNode.get("user").get("playcount").asText();

            String username = client.getUserById(Snowflake.of(entry.getKey()))
                    .block()
                    .getUsername();

            embedBuilder.addField("", username +  ": " + userPlaycount + " plays", true);
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
        String userSessionKey = SessionManager.getSessionKey(message.getAuthor().get().getId().asLong());


        if (userSessionKey == null) {
            return message.getChannel().flatMap(channel -> channel.createMessage("please login to use this command"));

        }

        if (trackName.equals("")) {
            //get users current track, name from that and set trackName to that


            String getRecentUserTracksUrl = "http://ws.audioscrobbler.com/2.0/?method=user.getrecenttracks&api_key=" + API_KEY + "&sk=" + userSessionKey + "&format=json";
            System.out.println("recent tracksu rl: " + getRecentUserTracksUrl);
            HttpGet request = new HttpGet(getRecentUserTracksUrl);
            HttpResponse response = null;
            try {
                response = httpClient.execute(request);
            } catch (IOException e) {
                return message.getChannel().flatMap(channel -> channel.createMessage("error: couldn't go to the get recent tracks url"));
            }

            try {
                JsonNode rootNode =  objectMapper.readTree(response.getEntity().getContent());
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




        String searchTrackForArtistNameUrl = "http://ws.audioscrobbler.com/2.0/?method=track.search&track=" + trackName + "&api_key=" + API_KEY + "&format=json";
        System.out.println("This is search track for artistnameurl: " + searchTrackForArtistNameUrl);
        HttpGet request1 = new HttpGet(searchTrackForArtistNameUrl);
        HttpResponse response1 = null;
        try {
            response1 = httpClient.execute(request1);
        } catch (IOException e) {
            return message.getChannel().flatMap(channel -> channel.createMessage("error: couldn't execute search track for artist name url"));
        }

        try {
            JsonNode rootNode1 =  objectMapper.readTree(response1.getEntity().getContent());

            JsonNode trackNode1 = rootNode1.path("results").path("trackmatches").path("track");
            JsonNode firstTrackNode1 = trackNode1.get(0);
            JsonNode trackArtistName = firstTrackNode1.path("artist");
            artist = trackArtistName.asText().replace(" ", "+");
        } catch (Exception e) {
            return message.getChannel().flatMap(channel -> channel.createMessage("error: couldn't get nodes from search track for artist name url "));
        }





        EmbedCreateSpec.Builder embedBuilder = EmbedCreateSpec.builder()
                .color(Color.BLUE)
                .url("https://discord4j.com")
                .author("Who knows track: " + trackName.replace("+", " ")
                        + " by " + artist.replace("+", " ") + "?", "https://discord4j.com", "https://i.imgur.com/F9BhEoz.png")
                .timestamp(Instant.now());


        for (Map.Entry<Long, String> entry : SessionManager.getUserSessions().entrySet()) {

            String getTrackInfoUrl = "http://ws.audioscrobbler.com/2.0/?method=track.getInfo&api_key=" + API_KEY + "&artist=" + artist + "&track=" + trackName + "&sk=" + entry.getValue() + "&format=json";
            System.out.println("This is track info url for some user: " + getTrackInfoUrl);
            HttpGet request = new HttpGet(getTrackInfoUrl);
            HttpResponse response = null;
            try {
                response = httpClient.execute(request);
            } catch (IOException e) {
                return message.getChannel().flatMap(channel -> channel.createMessage("error: couldn't execute track info url"));
            }

            System.out.println("API Response: " + response.getStatusLine());
            JsonNode rootNode = null;
            try {
                rootNode = objectMapper.readTree(response.getEntity().getContent());
            } catch (IOException e) {
                return message.getChannel().flatMap(channel -> channel.createMessage("error: couldn't get root node of track info url"));

            }
            String userPlaycountForTrack = rootNode.get("track").get("userplaycount").asText();

            String username = client.getUserById(Snowflake.of(entry.getKey()))
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

            String userSessionKey = SessionManager.getSessionKey(message.getAuthor().get().getId().asLong());

            String getRecentUserTracksUrl = "http://ws.audioscrobbler.com/2.0/?method=user.getrecenttracks&api_key=" + API_KEY + "&sk=" + userSessionKey + "&format=json";
            System.out.println("recent tracksu rl: " + getRecentUserTracksUrl);
            HttpGet request = new HttpGet(getRecentUserTracksUrl);
            HttpResponse response = null;
            try {
                response = httpClient.execute(request);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            try {
                JsonNode rootNode =  objectMapper.readTree(response.getEntity().getContent());
                JsonNode trackNode = rootNode.path("recenttracks").path("track");
                JsonNode firstTrackNode = trackNode.get(0);
                JsonNode artistNode = firstTrackNode.path("artist").path("#text");
                artistName = artistNode.asText().replace(" ", "+");
            } catch (Exception e) {
                e.printStackTrace();
            }

        }

        String artistSummary = "";

        String artistInfoUrl = "http://ws.audioscrobbler.com/2.0/?method=artist.getinfo&artist=" + artistName + "&api_key=" + API_KEY + "&format=json";
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

        EmbedCreateSpec.Builder embedBuilder = EmbedCreateSpec.builder()
                .color(Color.BLUE)
                .url("https://discord4j.com")
                .author(artistName.replace("+", " "), "https://discord4j.com", "https://i.imgur.com/F9BhEoz.png")
                .timestamp(Instant.now());

        embedBuilder.addField("Summary: ", artistSummary, false);
        return message.getChannel().flatMap(channel -> channel.createMessage(embedBuilder.build()));
    }

    static Mono<?> wkCommand(Message message, ObjectMapper objectMapper, CloseableHttpClient httpClient, GatewayDiscordClient client) {
        String artistName = Arrays.stream(message.getContent().split(" "))
                .collect(Collectors.toList())
                .stream()
                .skip(1)
                .collect(Collectors.joining("+"));

        String userSessionKey = SessionManager.getSessionKey(message.getAuthor().get().getId().asLong());


        if (userSessionKey == null) {
            return message.getChannel().flatMap(channel -> channel.createMessage("please login to use this command"));

        }


        if (artistName.equals("")) {
            //get users current track, artist from that and set artistname to that

            String getRecentUserTracksUrl = "http://ws.audioscrobbler.com/2.0/?method=user.getrecenttracks&api_key=" + API_KEY + "&sk=" + userSessionKey + "&format=json";
            System.out.println("recent tracksu rl: " + getRecentUserTracksUrl);
            HttpGet request = new HttpGet(getRecentUserTracksUrl);
            HttpResponse response = null;
            try {
                response = httpClient.execute(request);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            try {
                JsonNode rootNode =  objectMapper.readTree(response.getEntity().getContent());
                JsonNode trackNode = rootNode.path("recenttracks").path("track");
                JsonNode firstTrackNode = trackNode.get(0);
                JsonNode artistNode = firstTrackNode.path("artist").path("#text");
                artistName = artistNode.asText().replace(" ", "+");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        EmbedCreateSpec.Builder embedBuilder = EmbedCreateSpec.builder()
                .color(Color.BLUE)
                .url("https://discord4j.com")
                .author("Who knows " + artistName.replace("+", " ")
                        + "?", "https://discord4j.com", "https://i.imgur.com/F9BhEoz.png")
                .timestamp(Instant.now());


        for (Map.Entry<Long, String> entry : SessionManager.getUserSessions().entrySet()) {
            String getArtistInfoUrl = "http://ws.audioscrobbler.com/2.0/?method=artist.getinfo&artist=" + artistName + "&api_key=" + API_KEY + "&sk=" + entry.getValue() + "&format=json";
            System.out.println("This is artist info url for some user: " + getArtistInfoUrl);
            HttpGet request = new HttpGet(getArtistInfoUrl);
            HttpResponse response = null;
            try {
                response = httpClient.execute(request);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            System.out.println("API Response: " + response.getStatusLine());
            JsonNode rootNode = null;
            try {
                rootNode = objectMapper.readTree(response.getEntity().getContent());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            String userPlaycountForArtist = rootNode.get("artist").get("stats").get("userplaycount").asText();

            String username = client.getUserById(Snowflake.of(entry.getKey()))
                    .block()
                    .getUsername();

            embedBuilder.addField(username, "Plays: " + userPlaycountForArtist, false);
        }

        return message.getChannel().flatMap(channel -> channel.createMessage(embedBuilder.build()));
    }



    static Mono<?> topArtistsCommand(Message message, ObjectMapper objectMapper, CloseableHttpClient httpClient, GatewayDiscordClient client) {
        String userSessionKey = SessionManager.getSessionKey(message.getAuthor().get().getId().asLong());

        if (userSessionKey == null) {
            return message.getChannel().flatMap(channel -> channel.createMessage("please login to use this command"));

        }


        String url = "http://ws.audioscrobbler.com/2.0/?method=user.gettopartists&api_key=" + API_KEY + "&sk=" + userSessionKey + "&limit=5" + "&format=json";
        return message.getChannel()
                .flatMap(userResponse -> {
                    try {
                        HttpGet request = new HttpGet(url);
                        HttpResponse response = httpClient.execute(request);

                        System.out.println("API Response: " + response.getStatusLine());
                        JsonNode rootNode = objectMapper.readTree(response.getEntity().getContent());

                        EmbedCreateSpec.Builder embedBuilder = EmbedCreateSpec.builder()
                                .color(Color.BLUE)
                                .url("https://discord4j.com")
                                .author( "Your top artists", "https://discord4j.com", "https://i.imgur.com/F9BhEoz.png")
                                .thumbnail("https://i.imgur.com/F9BhEoz.png")
                                .timestamp(Instant.now());


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
        String userSessionKey = SessionManager.getSessionKey(message.getAuthor().get().getId().asLong());

        if (userSessionKey == null) {
            return message.getChannel().flatMap(channel -> channel.createMessage("please login to use this command"));

        }


        String url = "http://ws.audioscrobbler.com/2.0/?method=user.gettoptracks&api_key=" + API_KEY + "&sk=" + userSessionKey + "&limit=5" + "&format=json";

        System.out.println("tihs is top tracks url: " + url);

        return message.getChannel()

                .flatMap(userResponse -> {


                    try {
                        HttpGet request = new HttpGet(url);
                        HttpResponse response = httpClient.execute(request);

                        System.out.println("API Response: " + response.getStatusLine());
                        JsonNode rootNode = objectMapper.readTree(response.getEntity().getContent());

                        EmbedCreateSpec.Builder embedBuilder = EmbedCreateSpec.builder()
                                .color(Color.BLUE)
                                .url("https://discord4j.com")
                                .author("your top tracks", "https://discord4j.com", "https://i.imgur.com/F9BhEoz.png")
                                .thumbnail("https://i.imgur.com/F9BhEoz.png")
                                .timestamp(Instant.now());


                        JsonNode topArtistsNode = rootNode.path("toptracks");
                        for (JsonNode artistNode : topArtistsNode.path("track")) {
                            String name = artistNode.path("name").asText();
                            String playcount = artistNode.path("playcount").asText();
                            String imageUrl = artistNode.path("image")
                                    .elements()
                                    .next()  // Assuming there's only one image, change accordingly if there are multiple
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

        return message.getChannel()
                .flatMap(userResponse -> {
                    String url = "http://ws.audioscrobbler.com/2.0/?method=auth.gettoken&api_key=" + API_KEY + "&format=json";
                    String token = "";
                    //Get user to auth first
                    try {
                        HttpGet request = new HttpGet(url);
                        HttpResponse response = httpClient.execute(request);

                        System.out.println("API Response: " + response.getStatusLine());
                        JsonNode rootNode = objectMapper.readTree(response.getEntity().getContent());
                        token = rootNode.get("token").asText();
                        String requestAuthUrl = "http://www.last.fm/api/auth/?api_key=" + API_KEY + "&token=" + token;


                        EmbedCreateSpec.Builder embedBuilder = EmbedCreateSpec.builder()
                                .color(Color.BLUE)
                                .url("https://discord4j.com")
                                .author("Login to lastfm ", "https://discord4j.com", "https://i.imgur.com/F9BhEoz.png")
                                .addField("Login here: ", requestAuthUrl, false)
                                .timestamp(Instant.now());

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

                    String getSessionUrl = "http://ws.audioscrobbler.com/2.0/?method=auth.getSession&api_key=d44fe925c065064a6c03ce9686f02c17" +
                            "&token=" + token + "&api_sig=" + apisignature  + "&format=json";

                    System.out.println("This is session url " + getSessionUrl);
                    HttpGet requestSessionUrl = new HttpGet(getSessionUrl);
                    HttpResponse sessionResponse = null;
                    try {
                        sessionResponse = httpClient.execute(requestSessionUrl);

                    } catch (IOException e) {
                        return Mono.error(new RuntimeException(e));
                    }

                    System.out.println("API Response: " + sessionResponse.getStatusLine());
                    JsonNode rootNode = null;
                    try {
                        rootNode = objectMapper.readTree(sessionResponse.getEntity().getContent());
                    } catch (IOException e) {
                        return Mono.error(new RuntimeException(e));
                    }

                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {
                        e.printStackTrace(); // Handle the exception as needed
                    }

                    System.out.println("this is root node: " + rootNode);

                    String sessionKey = rootNode.get("session").get("key").asText();
                    System.out.println("Nirhjah this is key: " + sessionKey);
                    System.out.println("user id+ " + message.getAuthor().get().getId().asLong() );
                    SessionManager.storeSessionKey(message.getAuthor().get().getId().asLong(), sessionKey);

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

                                    return Mono.empty();
                                }))
                .block();
    }
}