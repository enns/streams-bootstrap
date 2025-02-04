/*
 * MIT License
 *
 * Copyright (c) 2022 bakdata
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

package com.bakdata.kafka.util;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyDescription.Node;
import org.apache.kafka.streams.TopologyDescription.Processor;
import org.apache.kafka.streams.TopologyDescription.Sink;
import org.apache.kafka.streams.TopologyDescription.Source;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.kstream.ValueJoiner;
import org.jooq.lambda.Seq;

/**
 * Representation of the nodes of a Kafka Streams topology
 */
public class TopologyInformation {
    private static final String CHANGELOG_SUFFIX = "-changelog";
    private static final String REPARTITION_SUFFIX = "-repartition";
    /**
     * See
     * {@link org.apache.kafka.streams.kstream.internals.KTableImpl#doJoinOnForeignKey(KTable, Function, ValueJoiner,
     * Named, Materialized, boolean)}
     */
    private static final Collection<String> PSEUDO_TOPIC_SUFFIXES = Set.of("-pk", "-fk", "-vh");
    private final String streamsId;
    private final Collection<Node> nodes;

    /**
     * Create a new TopologyInformation of a topology and the unique app id
     *
     * @param topology topology to extract nodes from
     * @param streamsId unique app id to represent auto-created topics
     */
    public TopologyInformation(final Topology topology, final String streamsId) {
        this.nodes = getNodes(topology);
        this.streamsId = streamsId;
    }

    private static List<Node> getNodes(final Topology topology) {
        return topology.describe().subtopologies()
                .stream()
                .flatMap(subtopology -> subtopology.nodes().stream())
                .collect(Collectors.toList());
    }

    private static Stream<TopicSubscription> getAllSources(final Collection<Node> nodes) {
        return nodes.stream()
                .filter(Source.class::isInstance)
                .map(Source.class::cast)
                .map(TopologyInformation::getAllSources);
    }

    private static TopicSubscription getAllSources(final Source source) {
        final Set<String> topicSet = source.topicSet();
        return topicSet == null ? new PatternTopicSubscription(source.topicPattern())
                : new DirectTopicSubscription(topicSet);
    }

    private static Stream<String> getAllSinks(final Collection<Node> nodes) {
        return nodes.stream()
                .filter(Sink.class::isInstance)
                .map(Sink.class::cast)
                .map(Sink::topic);
    }

    private static Stream<String> getAllStores(final Collection<Node> nodes) {
        return nodes.stream()
                .filter(Processor.class::isInstance)
                .map(Processor.class::cast)
                .flatMap(processor -> processor.stores().stream());
    }

    private static Stream<String> createPseudoTopics(final String topic) {
        if (topic.contains("FK-JOIN-SUBSCRIPTION-REGISTRATION")) {
            return PSEUDO_TOPIC_SUFFIXES.stream().map(suffix -> String.format("%s%s", topic, suffix));
        }
        return Stream.empty();
    }

    /**
     * Retrieve all topics associated with this topology that are auto-created by Kafka Streams
     *
     * @return list of internal topics
     */
    public List<String> getInternalTopics() {
        final Stream<String> internalSinks = this.getInternalSinks();
        final Stream<String> changelogTopics = this.getChangelogTopics();
        final Stream<String> repartitionTopics = this.getRepartitionTopics();

        return Stream.concat(Stream.concat(internalSinks, changelogTopics), repartitionTopics)
                .collect(Collectors.toList());
    }

    /**
     * Retrieve all sink topics associated with this topology that are not auto-created by Kafka Streams
     *
     * @return list of external sink topics
     */
    public List<String> getExternalSinkTopics() {
        return getAllSinks(this.nodes)
                .filter(this::isExternalTopic)
                .collect(Collectors.toList());
    }

    /**
     * Retrieve all source topics associated with this topology that are not auto-created by Kafka Streams
     *
     * @param allTopics list of all topics that exists in the Kafka cluster
     * @return list of external source topics
     */
    public List<String> getExternalSourceTopics(final Collection<String> allTopics) {
        final List<String> sinks = this.getExternalSinkTopics();
        return getAllSources(this.nodes)
                .map(subscription -> subscription.resolveTopics(allTopics))
                .flatMap(Collection::stream)
                .filter(this::isExternalTopic)
                .filter(t -> !sinks.contains(t))
                .collect(Collectors.toList());
    }

    /**
     * Retrieve all intermediate topics, i.e., topics that are both consumed and produced to/from, associated with this
     * topology that are not auto-created by Kafka Streams
     *
     * @param allTopics list of all topics that exists in the Kafka cluster
     * @return list of intermediate topics
     */
    public List<String> getIntermediateTopics(final Collection<String> allTopics) {
        final List<String> sinks = this.getExternalSinkTopics();
        return getAllSources(this.nodes)
                .map(subscription -> subscription.resolveTopics(allTopics))
                .flatMap(Collection::stream)
                .filter(this::isExternalTopic)
                .filter(sinks::contains)
                .collect(Collectors.toList());
    }

    private boolean isInternalTopic(final String topic) {
        if (topic.startsWith("KSTREAM-") || topic.startsWith("KTABLE-")) {
            return true;
        }
        if (topic.endsWith(CHANGELOG_SUFFIX)) {
            final List<String> changelogTopics = this.getChangelogTopics().collect(Collectors.toList());
            return changelogTopics.contains(topic);
        }
        if (topic.endsWith(REPARTITION_SUFFIX)) {
            final List<String> repartitionTopics = this.getRepartitionTopics().collect(Collectors.toList());
            return repartitionTopics.contains(topic);
        }
        return false;
    }

    private boolean isExternalTopic(final String topic) {
        return !this.isInternalTopic(topic);
    }

    private Stream<String> getInternalSinks() {
        return getAllSinks(this.nodes)
                .filter(this::isInternalTopic)
                .flatMap(topic -> Seq.of(topic).concat(createPseudoTopics(topic)))
                .map(topic -> String.format("%s-%s", this.streamsId, topic));
    }

    private Stream<String> getChangelogTopics() {
        return getAllStores(this.nodes)
                .map(store -> String.format("%s-%s%s", this.streamsId, store, CHANGELOG_SUFFIX));
    }

    private Stream<String> getRepartitionTopics() {
        return getAllStores(this.nodes)
                .map(store -> String.format("%s%s", store, REPARTITION_SUFFIX));
    }
}
