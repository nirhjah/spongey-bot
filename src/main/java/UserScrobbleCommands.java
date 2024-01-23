import com.fasterxml.jackson.core.JsonProcessingException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.EmbedCreateSpec;
import org.bson.Document;
import org.bson.conversions.Bson;
import reactor.core.publisher.Mono;

import java.io.UnsupportedEncodingException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class UserScrobbleCommands {


    static String connectionString = System.getenv("CONNECTION_STRING");


    static Mono<?> getYearlyInfo(Message message, GatewayDiscordClient client) throws JsonProcessingException {

        String[] command = message.getContent().split(" ");

        String year;
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


        long startOfGivenYearUTS;
        long endOfGivenYearUTS;

        LocalDateTime givenStartDateTime = LocalDateTime.of(Integer.parseInt(year), Month.JANUARY, 1, 0, 0);
        LocalDateTime givenEndDateTime = LocalDateTime.of(Integer.parseInt(year), Month.DECEMBER, 31, 23, 59);

        if (year.equals("")) {
            startOfGivenYearUTS = 1672570800;
            endOfGivenYearUTS = currentTimeUTS;
        } else {
            startOfGivenYearUTS = Service.convertToUnixTimestamp(givenStartDateTime);
            endOfGivenYearUTS = Service.convertToUnixTimestamp(givenEndDateTime);
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
        entryListArtist.sort(Map.Entry.<String, Integer>comparingByValue().reversed());



        List<Map.Entry<List<String>, Integer>> entryListTracks = new ArrayList<>(tracksForYearNew.entrySet());
        entryListTracks.sort(Map.Entry.<List<String>, Integer>comparingByValue().reversed());


        List<Map.Entry<List<String>, Integer>> entryListAlbums = new ArrayList<>(albumsForYearNew.entrySet());
        entryListAlbums.sort(Map.Entry.<List<String>, Integer>comparingByValue().reversed());

        EmbedCreateSpec.Builder embedBuilder = Service.createEmbed("Your stats for " + year);

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

    static Mono<?> getScrobblesInTimeframe(Message message, GatewayDiscordClient client) throws JsonProcessingException {

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

        long startOfGivenYearUTS;
        long endOfGivenYearUTS;

        LocalDateTime givenStartDateTime = LocalDateTime.of(Integer.parseInt(year), Month.JANUARY, 1, 0, 0);
        LocalDateTime givenEndDateTime = LocalDateTime.of(Integer.parseInt(year), Month.DECEMBER, 31, 23, 59);

        if (year.equals("")) {

            startOfGivenYearUTS = 1672570800;
            endOfGivenYearUTS = currentTimeUTS;
        } else {
            System.out.println("This is start date: " + givenStartDateTime);
            System.out.println("This is end date: " + givenEndDateTime);

            startOfGivenYearUTS = Service.convertToUnixTimestamp(givenStartDateTime);
            endOfGivenYearUTS = Service.convertToUnixTimestamp(givenEndDateTime);

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



    static Mono<?> getTopScrobbledDays(Message message, GatewayDiscordClient client) throws JsonProcessingException {



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
        MongoCollection<Document> users = database.getCollection("users");

        Document userDocument = users.find(Filters.eq("userid", userid)).first();

        Map<String, Integer> topScrobbledDays = new HashMap<>();


        ZoneId userTimeZone = ZoneId.of(userDocument.getString("timezone"));


        for (Document doc : collection.find()) {

            int timestamp = doc.getInteger("timestamp");

            Instant instant = Instant.ofEpochSecond(timestamp);
            ZonedDateTime zonedDateTime = instant.atZone(userTimeZone);
            String firstDate = zonedDateTime.format(DateTimeFormatter.ofPattern("dd MMMM yyyy"));
            int currentCount = topScrobbledDays.getOrDefault(firstDate, 0);
            topScrobbledDays.put(firstDate, currentCount + 1);


        }


        List<Map.Entry<String, Integer>> topScrobbledDaysList = new ArrayList<>(topScrobbledDays.entrySet());
        Collections.sort(topScrobbledDaysList, Map.Entry.<String, Integer>comparingByValue().reversed());


        EmbedCreateSpec.Builder embedBuilder = Service.createEmbed("Top Scrobbled Days");

        int count = 1;
        StringBuilder artistTracksAll = new StringBuilder();
        for (Map.Entry<String, Integer> entry : topScrobbledDaysList) {
            if (entry.getValue() > 350) {
                continue;
            }
            artistTracksAll.append(count).append(". ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            count++;
            if (count > 10) {
                break;
            }
        }

        embedBuilder.addField("top scrobbled days for " + username, artistTracksAll.toString(), false);
        embedBuilder.footer("Days with more than 350 scrobbles have been ignored", "https://i.imgur.com/F9BhEoz.png");
        return message.getChannel().flatMap(channel -> channel.createMessage(embedBuilder.build()));


    }


    static Mono<?> getDailyListeningTime(Message message, GatewayDiscordClient client) throws JsonProcessingException, UnsupportedEncodingException {


        String[] command = message.getContent().split(" ");


        String timePeriod = "";
        if (command.length >= 2) {
            timePeriod = command[1];
            System.out.println("timePeriod: " + timePeriod);
        } else {
            return message.getChannel().flatMap(channel -> channel.createMessage("Valid commands: $time lifetime $time 2024, $time 2023, $time 2022, $time today, $time currentweek, $time lastweek, $time month etc"));

        }


        MongoClient mongoClient = MongoClients.create(connectionString);
        MongoDatabase database = mongoClient.getDatabase("spongeybot");
        MongoCollection<Document> userCollection = database.getCollection("users");

        String username = client.getUserById(Snowflake.of(message.getAuthor().get().getId().asLong()))
                .block()
                .getUsername();

        long userid = message.getAuthor().get().getId().asLong();

        if (!message.getUserMentions().isEmpty()) {
            username = message.getUserMentions().get(0).getUsername();
            userid = message.getUserMentions().get(0).getId().asLong();
        }

        MongoCollection<Document> scrobblesCollection = database.getCollection(username);

        Document userDocument = userCollection.find(Filters.eq("userid", userid)).first();

        ZoneId userTimeZone = ZoneId.of(userDocument.getString("timezone"));

        long userRegsiteredTimestamp = Long.parseLong(userDocument.getString("registered"));


        int listeningTime;

        //if command is 'today'
        ZonedDateTime currentTimeInTimezone = ZonedDateTime.now(userTimeZone);

        ZonedDateTime firstMinuteOfPeriod;
        ZonedDateTime lastMinuteOfPeriod;

        try {
            List<ZonedDateTime> firstAndLastTimes =  Service.calculateTimePeriod(timePeriod, currentTimeInTimezone, userRegsiteredTimestamp, userTimeZone);

            firstMinuteOfPeriod = firstAndLastTimes.get(0);
            lastMinuteOfPeriod = firstAndLastTimes.get(1);

        } catch (IllegalArgumentException e) {
            return message.getChannel().flatMap(channel -> channel.createMessage("Valid periods: today, currentweek, lastweek, month, 2024, lifetime"));
        }


        System.out.println("This is first minute: " + firstMinuteOfPeriod);
        System.out.println("This is last minute: " +  lastMinuteOfPeriod);

        long utsStartOfPeriod = firstMinuteOfPeriod.toInstant().getEpochSecond();

        long utsEndOfPeriod = lastMinuteOfPeriod.toInstant().getEpochSecond();


        Bson filter = Filters.and(
                Filters.gte("timestamp", utsStartOfPeriod),
                Filters.lt("timestamp", utsEndOfPeriod)
        );

        listeningTime = Service.calculateListeningTime(scrobblesCollection, filter)/60;

        int finalListeningTime = listeningTime;
        String finalTimePeriod = timePeriod;

        return message.getChannel().flatMap(channel -> channel.createMessage("You listened to " + finalListeningTime + " minutes! (" + finalTimePeriod + ")"));
    }


}
