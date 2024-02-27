import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

public class SpotifyService {

    private static String makeGetRequest(String apiUrl, String accessToken) throws Exception {
        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Authorization", "Bearer " + accessToken)
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            return response.body();
        } else {
            throw new RuntimeException("Error: " + response.statusCode() + "\n" + response.body());
        }
    }

    public static Set<String> getArtistsTracksAll(String accessToken, String artistId) {
        String apiUrl = "https://api.spotify.com/v1/artists/" + artistId + "/albums?limit=50";

        Set<String> allArtistsTracks = new HashSet<>();
        try {
            String response = makeGetRequest(apiUrl, accessToken);
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonResponse = objectMapper.readTree(response);
            JsonNode albums = jsonResponse.path("items");

            for (JsonNode album : albums) {
                //we don't want to include albums that the artist appears on
                if (!Objects.equals(album.get("album_group").asText(), "appears_on")) {

                    String albumTracksUrl = "https://api.spotify.com/v1/albums/" + album.get("id").asText() + "/tracks?limit=50&market=US";

                    try {
                        String response2 = makeGetRequest(albumTracksUrl, accessToken);
                        ObjectMapper objectMapper2 = new ObjectMapper();
                        JsonNode tracksResponse = objectMapper2.readTree(response2);
                        JsonNode albumsTracks = tracksResponse.get("items");

                        for (JsonNode track : albumsTracks) {
                            JsonNode artists = track.get("artists");
                            if ((artists.size() == 1) && Objects.equals(artists.get(0).get("id").asText(), artistId)){
                                //Song only by the artist
                                allArtistsTracks.add(track.get("name").asText());


                            } else {
                                if (Objects.equals(artists.get(0).get("id").asText(), artistId)) {
                                    //Song with multiple artists but the main artist is our artist - we aren't counting features
                                    allArtistsTracks.add(track.get("name").asText());

                                }
                            }
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return allArtistsTracks;
    }


    public static int getNumberOfTracks(String accessToken, String artistId) throws IOException {
        String apiUrl = "https://api.spotify.com/v1/artists/" + artistId + "/albums?market=US"; // You can adjust the market parameter if needed
        URL url = new URL(apiUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Authorization", "Bearer " + accessToken);

        try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String inputLine;
            StringBuilder response = new StringBuilder();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }

            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonResponse = objectMapper.readTree(response.toString());

            JsonNode albums = jsonResponse.path("items");
            int totalTracks = 0;

            for (JsonNode album : albums) {
                JsonNode albumTracks = album.path("total_tracks");

                if (albumTracks.isInt()) {
                    totalTracks += albumTracks.asInt();
                }
            }

            return totalTracks;
        }
    }

    public static String getArtistImageURL(String artistId, String accessToken) throws IOException {
        String apiUrl = "https://api.spotify.com/v1/artists/" + artistId;
        System.out.println("This is artist url: " + apiUrl);
        URL url = new URL(apiUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Authorization", "Bearer " + accessToken);

        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        String inputLine;
        StringBuilder response = new StringBuilder();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonResponse = objectMapper.readTree(response.toString());

        JsonNode images = jsonResponse.get("images");
        if (images.isArray() && images.size() > 0) {
            return images.get(0).path("url").asText();
        } else {
            return null;
        }
    }


    public static String getTrackArtist(String trackId, String accessToken) throws IOException {
        String apiUrl = "https://api.spotify.com/v1/tracks/" + trackId;
        //   System.out.println("This is track url: " + apiUrl);
        URL url = new URL(apiUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Authorization", "Bearer " + accessToken);

        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        String inputLine;
        StringBuilder response = new StringBuilder();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonResponse = objectMapper.readTree(response.toString());

        //  System.out.println("This is new artists: " + jsonResponse.get("artists").get(0).get("name"));
        int trackDuration = jsonResponse.get("duration_ms").asInt();

        return jsonResponse.get("artists").get(0).get("name").asText();
    }




    public static int getTrackDuration(String trackId, String accessToken) throws IOException {
        String apiUrl = "https://api.spotify.com/v1/tracks/" + trackId;
     //   System.out.println("This is track url: " + apiUrl);
        URL url = new URL(apiUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Authorization", "Bearer " + accessToken);

        int retryAfter = connection.getHeaderFieldInt("Retry-After", -1);

        System.out.println("THIS IS RETRY AFTER: " + retryAfter);
        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        String inputLine;
        StringBuilder response = new StringBuilder();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonResponse = objectMapper.readTree(response.toString());

      //  System.out.println("This is new artists: " + jsonResponse.get("artists").get(0).get("name"));
        int trackDuration = jsonResponse.get("duration_ms").asInt();

        return trackDuration;
    }

    public static String getTrackSpotifyId(String accessToken, String trackName) throws IOException {
        String encodedTrackName = URLEncoder.encode(trackName, "UTF-8");

        String apiUrl = "https://api.spotify.com/v1/search?q=" + encodedTrackName + "&type=track";
        URL url = new URL(apiUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        connection.setRequestProperty("Authorization", "Bearer " + accessToken);

        try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String inputLine;
            StringBuilder response = new StringBuilder();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }


            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonResponse = objectMapper.readTree(response.toString());

            JsonNode tracks = jsonResponse.path("tracks").path("items");
            if (tracks.isArray() && tracks.size() > 0) {
                return tracks.get(0).path("id").asText();
            } else {
                return null;
            }
        }
    }

    public static String getArtistSpotifyId(String accessToken, String artistName) throws IOException {
        String encodedArtistName = URLEncoder.encode(artistName, "UTF-8");

        String apiUrl = "https://api.spotify.com/v1/search?q=" + encodedArtistName + "&type=artist";
        URL url = new URL(apiUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        connection.setRequestProperty("Authorization", "Bearer " + accessToken);

        try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String inputLine;
            StringBuilder response = new StringBuilder();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }


            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonResponse = objectMapper.readTree(response.toString());

            JsonNode artists = jsonResponse.path("artists").path("items");
            if (artists.isArray() && artists.size() > 0) {
                return artists.get(0).path("id").asText();
            } else {
                return null;
            }
        }
    }

    public static String getAccessToken(String clientId, String clientSecret) throws Exception {
        String apiUrl = "https://accounts.spotify.com/api/token";

        String urlParameters = "grant_type=client_credentials";
        URL url = new URL(apiUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);

        String authHeader = clientId + ":" + clientSecret;
        String encodedAuthHeader = "Basic " + Base64.getEncoder().encodeToString(authHeader.getBytes());
        connection.setRequestProperty("Authorization", encodedAuthHeader);

        try (DataOutputStream wr = new DataOutputStream(connection.getOutputStream())) {
            wr.writeBytes(urlParameters);
            wr.flush();
        }

        try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String inputLine;
            StringBuilder response = new StringBuilder();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }

            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonResponse = objectMapper.readTree(response.toString());

            return jsonResponse.get("access_token").asText();
        }


}


}


