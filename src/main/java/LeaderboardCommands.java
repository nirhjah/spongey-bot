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
import java.util.*;

public class LeaderboardCommands {

    static String connectionString = System.getenv("CONNECTION_STRING");



    static Mono<?> scrobbleLbCommand(Message message, GatewayDiscordClient client) {


        String[] command = message.getContent().split(" ");

        String timePeriod = ""; //overall if blank
        if (command.length >= 2) {
            timePeriod = command[1];
            System.out.println("timePeriod: " + timePeriod);
        }

        Map<String, Integer> unsortedscrobbleLb = new HashMap<>();


        EmbedCreateSpec.Builder embedBuilder = Service.createEmbed("Scrobble leaderboard for " + timePeriod);

        MongoClient mongoClient = MongoClients.create(connectionString);
        MongoDatabase database = mongoClient.getDatabase("spongeybot");
        MongoCollection<Document> collection = database.getCollection("users");


        for (Document document : collection.find()) {
            int userPlaycount;
            long userId = document.getLong("userid");

            String username = client.getUserById(Snowflake.of(userId))
                    .block()
                    .getUsername();

            MongoCollection<Document> scrobbleCollection = database.getCollection(username);

            ZoneId userTimeZone = ZoneId.of(document.getString("timezone"));

            long userRegsiteredTimestamp = Long.parseLong(document.getString("registered"));

            //if command is 'today'
            ZonedDateTime currentTimeInTimezone = ZonedDateTime.now(userTimeZone);

            ZonedDateTime firstMinuteOfPeriod;
            ZonedDateTime lastMinuteOfPeriod;

            try {
                List<ZonedDateTime> firstAndLastTimes = Service.calculateTimePeriod(timePeriod, currentTimeInTimezone, userRegsiteredTimestamp, userTimeZone);

                firstMinuteOfPeriod = firstAndLastTimes.get(0);
                lastMinuteOfPeriod = firstAndLastTimes.get(1);

            } catch (IllegalArgumentException e) {
                return message.getChannel().flatMap(channel -> channel.createMessage("Valid periods: today, currentweek, lastweek, month, 2024, lifetime"));
            }

            long utsStartOfPeriod = firstMinuteOfPeriod.toInstant().getEpochSecond();
            long utsEndOfPeriod = lastMinuteOfPeriod.toInstant().getEpochSecond();


            Bson filter = Filters.and(
                    Filters.gte("timestamp", utsStartOfPeriod),
                    Filters.lt("timestamp", utsEndOfPeriod)
            );

            userPlaycount = (int) scrobbleCollection.countDocuments(filter);
            unsortedscrobbleLb.put(username, userPlaycount);

        }


        List<Map.Entry<String, Integer>> list = new LinkedList<>(unsortedscrobbleLb.entrySet());

        Collections.sort(list, Collections.reverseOrder(Comparator.comparing(Map.Entry::getValue)));

        int count = 1;
        StringBuilder scrobbleLbString = new StringBuilder();
        for (Map.Entry<String, Integer> entry : list) {
            scrobbleLbString.append(count).append(". ").append(entry.getKey()).append(": ").append(entry.getValue()).append(" scrobbles \n");
            count++;

        }

        embedBuilder.addField("", scrobbleLbString.toString(), false);

        return message.getChannel().flatMap(channel -> channel.createMessage(embedBuilder.build()));
    }



    static Mono<?> getTimeLeaderboard(Message message, GatewayDiscordClient client) throws JsonProcessingException, UnsupportedEncodingException {

        String[] command = message.getContent().split(" ");


        String timePeriod;
        if (command.length >= 2) {
            timePeriod = command[1];
            System.out.println("timePeriod: " + timePeriod);
        } else {
            return message.getChannel().flatMap(channel -> channel.createMessage("Valid commands: $timelb lifetime $timelb 2024, $timelb month, $timelb currentweek, $timelb lastweek, $timelb today etc"));

        }


        MongoClient mongoClient = MongoClients.create(connectionString);
        MongoDatabase database = mongoClient.getDatabase("spongeybot");
        MongoCollection<Document> userCollection = database.getCollection("users");


        EmbedCreateSpec.Builder embedBuilder = Service.createEmbed("Time leaderboard for period: " + timePeriod);

        Map<String, Integer> userAndTime = new HashMap<>();

        for (Document user : userCollection.find()) {
            int listeningTime = 0;

            long userId = user.getLong("userid");

            String username = client.getUserById(Snowflake.of(userId))
                    .block()
                    .getUsername();


            MongoCollection<Document> scrobblesCollection = database.getCollection(username);


            ZoneId userTimeZone = ZoneId.of(user.getString("timezone"));

            long userRegsiteredTimestamp = Long.parseLong(user.getString("registered"));

            //if command is 'today'
            ZonedDateTime currentTimeInTimezone = ZonedDateTime.now(userTimeZone);

            ZonedDateTime firstMinuteOfPeriod;
            ZonedDateTime lastMinuteOfPeriod;

            try {
                List<ZonedDateTime> firstAndLastTimes = Service.calculateTimePeriod(timePeriod, currentTimeInTimezone, userRegsiteredTimestamp, userTimeZone);

                firstMinuteOfPeriod = firstAndLastTimes.get(0);
                lastMinuteOfPeriod = firstAndLastTimes.get(1);

            } catch (IllegalArgumentException e) {
                return message.getChannel().flatMap(channel -> channel.createMessage("Valid periods: today, currentweek, lastweek, month, 2024, lifetime"));
            }

            long utsStartOfPeriod = firstMinuteOfPeriod.toInstant().getEpochSecond();
            long utsEndOfPeriod = lastMinuteOfPeriod.toInstant().getEpochSecond();


            Bson filter = Filters.and(
                    Filters.gte("timestamp", utsStartOfPeriod),
                    Filters.lt("timestamp", utsEndOfPeriod)
            );

            listeningTime = Service.calculateListeningTime(scrobblesCollection, filter);
            userAndTime.put(username, listeningTime);
        }


        List<Map.Entry<String, Integer>> sortedTimes = new LinkedList<>(userAndTime.entrySet());
        Collections.sort(sortedTimes, Map.Entry.<String, Integer>comparingByValue().reversed());



        int count = 1;
        StringBuilder sortedTimesString = new StringBuilder();
        for (Map.Entry<String, Integer> entry : sortedTimes) {


            //listening time is in seconds, the method returned seconds

            int newTime;
            String timeType = " minutes";


            newTime = entry.getValue()/60;
            if (entry.getValue() >= 3660) { //if time above 60 mins/3660 seconds
                //convert to hours
                newTime = entry.getValue()/3600;
                timeType = " hours";

                if (newTime >= 24) {
                    newTime = entry.getValue()/86400;
                    timeType = " days";

                }


            }

            sortedTimesString.append(count).append(". ").append(entry.getKey()).append(": ").append(newTime).append(timeType).append("\n");
            count++;

        }

        embedBuilder.addField("", sortedTimesString.toString(), false);

        return message.getChannel().flatMap(channel -> channel.createMessage(embedBuilder.build()));
    }


    static Mono<?> artistLbCommand(Message message, GatewayDiscordClient client) {


        String[] command = message.getContent().split(" ");

        String timePeriod = ""; //overall if blank
        if (command.length >= 2) {
            timePeriod = command[1];
            System.out.println("timePeriod: " + timePeriod);
        }

        Map<String, Integer> unsortedscrobbleLb = new HashMap<>();


        EmbedCreateSpec.Builder embedBuilder = Service.createEmbed("Artist leaderboard for " + timePeriod);

        MongoClient mongoClient = MongoClients.create(connectionString);
        MongoDatabase database = mongoClient.getDatabase("spongeybot");
        MongoCollection<Document> collection = database.getCollection("users");


        for (Document document : collection.find()) {
            int userPlaycount;
            long userId = document.getLong("userid");

            String username = client.getUserById(Snowflake.of(userId))
                    .block()
                    .getUsername();

            MongoCollection<Document> scrobbleCollection = database.getCollection(username);

            ZoneId userTimeZone = ZoneId.of(document.getString("timezone"));

            long userRegsiteredTimestamp = Long.parseLong(document.getString("registered"));

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

            long utsStartOfPeriod = firstMinuteOfPeriod.toInstant().getEpochSecond();
            long utsEndOfPeriod = lastMinuteOfPeriod.toInstant().getEpochSecond();


            Bson filter = Filters.and(
                    Filters.gte("timestamp", utsStartOfPeriod),
                    Filters.lt("timestamp", utsEndOfPeriod)
            );

            Set<String> uniqueArtists = new HashSet<>();

            for (Document scrobble : scrobbleCollection.find(filter)) {
                String artist = scrobble.getString("artist");
                if (artist != null) {
                    uniqueArtists.add(artist);
                }


            }


            unsortedscrobbleLb.put(username, uniqueArtists.size());

        }


        List<Map.Entry<String, Integer>> list = new LinkedList<>(unsortedscrobbleLb.entrySet());

        Collections.sort(list, Collections.reverseOrder(Comparator.comparing(Map.Entry::getValue)));

        int count = 1;
        StringBuilder scrobbleLbString = new StringBuilder();
        for (Map.Entry<String, Integer> entry : list) {
            scrobbleLbString.append(count).append(". ").append(entry.getKey()).append(": ").append(entry.getValue()).append(" artist \n");
            count++;

        }

        embedBuilder.addField("", scrobbleLbString.toString(), false);

        return message.getChannel().flatMap(channel -> channel.createMessage(embedBuilder.build()));
    }


    static Mono<?> tracklbCommand(Message message, GatewayDiscordClient client) {


        String[] command = message.getContent().split(" ");

        String timePeriod = ""; //overall if blank
        if (command.length >= 2) {
            timePeriod = command[1];
            System.out.println("timePeriod: " + timePeriod);
        }

        Map<String, Integer> unsortedscrobbleLb = new HashMap<>();


        EmbedCreateSpec.Builder embedBuilder = Service.createEmbed("Track leaderboard for " + timePeriod);

        MongoClient mongoClient = MongoClients.create(connectionString);
        MongoDatabase database = mongoClient.getDatabase("spongeybot");
        MongoCollection<Document> collection = database.getCollection("users");


        for (Document document : collection.find()) {
            long userId = document.getLong("userid");

            String username = client.getUserById(Snowflake.of(userId))
                    .block()
                    .getUsername();

            MongoCollection<Document> scrobbleCollection = database.getCollection(username);

            ZoneId userTimeZone = ZoneId.of(document.getString("timezone"));

            long userRegsiteredTimestamp = Long.parseLong(document.getString("registered"));

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

            long utsStartOfPeriod = firstMinuteOfPeriod.toInstant().getEpochSecond();
            long utsEndOfPeriod = lastMinuteOfPeriod.toInstant().getEpochSecond();


            Bson filter = Filters.and(
                    Filters.gte("timestamp", utsStartOfPeriod),
                    Filters.lt("timestamp", utsEndOfPeriod)
            );

            Set<String> uniqueTracks = new HashSet<>();

            for (Document scrobble : scrobbleCollection.find(filter)) {
                String track = scrobble.getString("track");
                String artist = scrobble.getString("artist");

                if (track != null && artist != null) {
                    String uniqueKey = track + "|" + artist;
                    uniqueTracks.add(uniqueKey);
                }


            }


            unsortedscrobbleLb.put(username, uniqueTracks.size());

        }


        List<Map.Entry<String, Integer>> list = new LinkedList<>(unsortedscrobbleLb.entrySet());

        Collections.sort(list, Collections.reverseOrder(Comparator.comparing(Map.Entry::getValue)));

        int count = 1;
        StringBuilder scrobbleLbString = new StringBuilder();
        for (Map.Entry<String, Integer> entry : list) {
            scrobbleLbString.append(count).append(". ").append(entry.getKey()).append(": ").append(entry.getValue()).append(" tracks \n");
            count++;

        }

        embedBuilder.addField("", scrobbleLbString.toString(), false);

        return message.getChannel().flatMap(channel -> channel.createMessage(embedBuilder.build()));
    }


    static Mono<?> getCrownLb(Message message, GatewayDiscordClient client) throws JsonProcessingException {
        MongoClient mongoClient = MongoClients.create(connectionString);
        MongoDatabase database = mongoClient.getDatabase("spongeybot");
        MongoCollection<Document> crowns = database.getCollection("crowns");

        EmbedCreateSpec.Builder embedBuilder = Service.createEmbed("Crowns Leaderboard");
        Map<String, Integer> crownMap = new HashMap<>(); //owner, total crowns

        for (Document crown : crowns.find()) {
            int currentCount = crownMap.getOrDefault(crown.getString("owner"), 0);
            crownMap.put(crown.getString("owner"), currentCount + 1);
        }



        List<Map.Entry<String, Integer>> sortedCrowns = new ArrayList<>(crownMap.entrySet());
        Collections.sort(sortedCrowns, Map.Entry.<String, Integer>comparingByValue().reversed());

        int count = 1;
        StringBuilder allCrowns = new StringBuilder();
        for (Map.Entry<String, Integer> entry : sortedCrowns) {
            String crownCrowns = "";
            if (entry.getValue() == 1) {
                crownCrowns = " crown ";
            } else {
                crownCrowns = " crowns ";
            }
            allCrowns.append(count).append(". ").append(entry.getKey()).append(": ").append(entry.getValue()).append(crownCrowns).append("\n");

            count++;
            if (count > 10) {
                break;
            }


        }

        embedBuilder.addField("", allCrowns.toString(), false);


        return message.getChannel().flatMap(channel -> channel.createMessage(embedBuilder.build()));

    }


}
