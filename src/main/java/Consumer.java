
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.Document;

import java.util.Arrays;
import java.util.Properties;
import java.util.Scanner;

public class Consumer {
    private static Scanner in;
//    public static final Logger logger = LogManager.getLogger(Consumer.class);

    public static void main(String[] argv) throws Exception {
//        logger.error("Testing error");
//        logger.info("Hello info");
//        logger.warn("test");

        if (argv.length != 2) {
            System.err.printf("Usage: %s <topicName> <groupId>\n",
                    Consumer.class.getSimpleName());
            System.exit(-1);
        }
        in = new Scanner(System.in);
        String topicName = argv[0];
        String groupId = argv[1];

        ConsumerThread consumerRunnable = new ConsumerThread(topicName, groupId);
        consumerRunnable.start();
        String line = "";
        while (!line.equals("exit")) {
            line = in.next();
        }
        consumerRunnable.getKafkaConsumer().wakeup();
        System.out.println("Stopping consumer .....");
        consumerRunnable.join();
    }

    private static class ConsumerThread extends Thread {
        private String topicName;
        private String groupId;
        private KafkaConsumer<String, String> kafkaConsumer;

        public ConsumerThread(String topicName, String groupId) {
            this.topicName = topicName;
            this.groupId = groupId;
        }

        public void run() {
            Properties configProperties = new Properties();
            configProperties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
            configProperties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.ByteArrayDeserializer");
            configProperties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
            configProperties.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
            configProperties.put(ConsumerConfig.CLIENT_ID_CONFIG, "simple");

            //Figure out where to start processing messages from
            kafkaConsumer = new KafkaConsumer<String, String>(configProperties);
            kafkaConsumer.subscribe(Arrays.asList(topicName));
            //Start processing messages
            try {
                while (true) {
                    ConsumerRecords<String, String> records = kafkaConsumer.poll(100);
                    for (ConsumerRecord<String, String> record : records) {
                        String tmp = record.value();
                        String[] arrOfStr = tmp.split("\\|");
                        System.out.println(record.value()); //Prints the recorded log
//                        for (String a : arrOfStr)
                        String logDate = arrOfStr[0];
                        String logLevel = arrOfStr[1];
                        String logServer = arrOfStr[2];
                        logServer = logServer.replace("[", "");
                        logServer = logServer.replace("]", "");
                        String logMain = arrOfStr[3];
                        String logDetail = arrOfStr[4];
//                        System.out.println(logServer.length());
//                        System.out.println(logDate + logLevel + logServer + logMain + logMessage + "\n");

                        //Push logs into mongodb
                        MongoClientURI connectionString = new MongoClientURI("mongodb://localhost:27017");
                        MongoClient mongoClient = new MongoClient(connectionString);
                        MongoDatabase database = mongoClient.getDatabase("TEBLogs");
                        MongoCollection<Document> collection = database.getCollection("logs");
                        Document doc = new Document("name", "GeneratedLog").append("timestamp", logDate)
                                .append("level", logLevel).append("server", logServer).append("detail", logDetail)
                                .append("source", logMain);
                        collection.insertOne(doc);
                    }
                }
            } catch (WakeupException ex) {
                System.out.println("Exception caught " + ex.getMessage());
            } finally {
                kafkaConsumer.close();
                System.out.println("After closing KafkaConsumer");
            }
        }

        public KafkaConsumer<String, String> getKafkaConsumer() {
            return this.kafkaConsumer;
        }
    }
}

