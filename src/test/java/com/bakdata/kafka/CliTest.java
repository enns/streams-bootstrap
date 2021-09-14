/*
 * MIT License
 *
 * Copyright (c) 2021 bakdata
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.bakdata.kafka;

import com.bakdata.kafka.util.TopicClient;
import com.bakdata.kafka.util.TopicSettings;
import com.ginsberg.junit.exit.ExpectSystemExitWithStatus;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class CliTest {

    private static final int TIMEOUT_SECONDS = 10;
    @Container
    private final KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:5.4.3"));

    @Test
    @ExpectSystemExitWithStatus(0)
    void shouldExitWithSuccessCode() {
        KafkaStreamsApplication.startApplication(new KafkaStreamsApplication() {
            @Override
            public void buildTopology(final StreamsBuilder builder) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getUniqueAppId() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void run() {
                // do nothing
            }
        }, new String[]{
                "--brokers", "localhost:9092",
                "--schema-registry-url", "http://localhost:8081",
                "--input-topics", "input",
                "--output-topic", "output",
        });
    }

    @Test
    @ExpectSystemExitWithStatus(1)
    void shouldExitWithErrorCodeOnRunError() {
        KafkaStreamsApplication.startApplication(new KafkaStreamsApplication() {
            @Override
            public void buildTopology(final StreamsBuilder builder) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getUniqueAppId() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void run() {
                throw new RuntimeException();
            }
        }, new String[]{
                "--brokers", "localhost:9092",
                "--schema-registry-url", "http://localhost:8081",
                "--input-topics", "input",
                "--output-topic", "output",
        });
    }

    @Test
    @ExpectSystemExitWithStatus(1)
    void shouldExitWithErrorCodeOnCleanupError() {
        KafkaStreamsApplication.startApplication(new KafkaStreamsApplication() {
            @Override
            public void buildTopology(final StreamsBuilder builder) {
                builder.stream(this.getInputTopics());
            }

            @Override
            public String getUniqueAppId() {
                return "app";
            }

            @Override
            protected void runCleanUp() {
                throw new RuntimeException();
            }
        }, new String[]{
                "--brokers", "localhost:9092",
                "--schema-registry-url", "http://localhost:8081",
                "--input-topics", "input",
                "--output-topic", "output",
                "--clean-up",
        });
    }

    @Test
    @ExpectSystemExitWithStatus(2)
    void shouldExitWithErrorCodeOnMissingBrokerParameter() {
        KafkaStreamsApplication.startApplication(new KafkaStreamsApplication() {
            @Override
            public void buildTopology(final StreamsBuilder builder) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getUniqueAppId() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void run() {
                // do nothing
            }
        }, new String[]{
                "--schema-registry-url", "http://localhost:8081",
                "--input-topics", "input",
                "--output-topic", "output",
        });
    }

    private TopicClient createClient() {
        final String brokerList = this.kafka.getBootstrapServers();
        final Map<String, Object> config = Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, brokerList);
        return TopicClient.create(config, Duration.ofSeconds(20L));
    }

    @Test
    @ExpectSystemExitWithStatus(1)
    void shouldExitWithErrorInTopology() throws InterruptedException {
        Thread.sleep(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
        final String input = "input";
        final String output = "output";
        try (final TopicClient client = this.createClient()) {
            client.createTopic(input, TopicSettings.builder()
                    .partitions(1)
                    .replicationFactor((short) 1)
                    .build(), Collections.emptyMap());
        }
        Thread.sleep(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
        KafkaStreamsApplication.startApplication(new KafkaStreamsApplication() {
            @Override
            public void buildTopology(final StreamsBuilder builder) {
                builder.stream(this.getInputTopics(), Consumed.with(Serdes.ByteArray(), Serdes.ByteArray()))
                        .peek((k, v) -> {
                            throw new RuntimeException();
                        });
            }

            @Override
            public String getUniqueAppId() {
                return "app";
            }
        }, new String[]{
                "--brokers", this.kafka.getBootstrapServers(),
                "--schema-registry-url", "http://localhost:8081",
                "--input-topics", input,
                "--output-topic", output,
        });
        try (final Producer<String, String> kafkaProducer = new KafkaProducer<>(
                Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, this.kafka.getBootstrapServers()),
                new StringSerializer(),
                new StringSerializer()
        )) {
            kafkaProducer.send(new ProducerRecord<>(input, "foo", "bar"));
            kafkaProducer.flush();
        }
    }
}
