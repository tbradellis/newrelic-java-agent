package com.nr.instrumentation.kafka;

import com.newrelic.agent.introspec.InstrumentationTestConfig;
import com.newrelic.agent.introspec.InstrumentationTestRunner;
import com.newrelic.agent.introspec.Introspector;
import com.newrelic.agent.introspec.TracedMetricData;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.metrics.Gauge;
import org.apache.kafka.common.metrics.KafkaMetric;
import org.apache.kafka.common.metrics.MetricConfig;
import org.apache.kafka.common.utils.Time;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * These are not unit tests but a functionality test for the whole class.
 * Unit testing the methods would require a lot of reflection to check that it worked.
 * It uses the InstrumentationTestRunner even though it is not testing any weave class
 * so the introspector processes the calls to NewRelic. This prevents static mocking of
 * NewRelic, which is extra complicated because static mocking is thread based, and there
 * are other threads in the code being tested.
 */
@RunWith(InstrumentationTestRunner.class)
@InstrumentationTestConfig(includePrefixes = "org.apache.kafka")
public class NewRelicMetricsReporterTest {
    private Introspector introspector;
    private static final KafkaMetric METRIC1 = kafkaMetric("metric1", null, 42.0f);
    private static final KafkaMetric METRIC2 = kafkaMetric("metric2", null, 11.0f);

    @Before
    public void setup() {
        introspector = InstrumentationTestRunner.getIntrospector();
    }

    @Test
    public void initialLoad() throws InterruptedException {
        List<KafkaMetric> initialMetrics = Arrays.asList(METRIC1, METRIC2);

        NewRelicMetricsReporter reporter = initMetricsReporter(initialMetrics, Collections.emptyList());

        Map<String, TracedMetricData> unscopedMetrics = introspector.getUnscopedMetrics();
        TracedMetricData metric1 = unscopedMetrics.get("MessageBroker/Kafka/Internal/group/metric1");
        assertEquals(42.0f, metric1.getTotalTimeInSec(), 0.1f);
        TracedMetricData metric2 = unscopedMetrics.get("MessageBroker/Kafka/Internal/group/metric2");
        assertEquals(11.0f, metric2.getTotalTimeInSec(), 0.1f);

        reporter.close();
    }

    @Test
    public void laterLoad() throws Exception {
        List<KafkaMetric> otherMetrics = Arrays.asList(METRIC1, METRIC2);

        NewRelicMetricsReporter reporter = initMetricsReporter(Collections.emptyList(), otherMetrics);

        Map<String, TracedMetricData> unscopedMetrics = introspector.getUnscopedMetrics();
        assertEquals(1, unscopedMetrics.size());
        TracedMetricData nodeMetric = unscopedMetrics.get("MessageBroker/Kafka/Nodes/localhost:42");
        assertEquals(1.0f, nodeMetric.getTotalTimeInSec(), 0.1f);

        forceHarvest(reporter);

        unscopedMetrics = introspector.getUnscopedMetrics();
        TracedMetricData metric1 = unscopedMetrics.get("MessageBroker/Kafka/Internal/group/metric1");
        assertEquals(42.0f, metric1.getTotalTimeInSec(), 0.1f);
        TracedMetricData metric2 = unscopedMetrics.get("MessageBroker/Kafka/Internal/group/metric2");
        assertEquals(11.0f, metric2.getTotalTimeInSec(), 0.1f);

        reporter.close();
    }

    @Test
    public void removeMetric() throws Exception {
        List<KafkaMetric> initialMetrics = Arrays.asList(METRIC1, METRIC2);

        NewRelicMetricsReporter reporter = initMetricsReporter(initialMetrics, Collections.emptyList());

        Map<String, TracedMetricData> unscopedMetrics = introspector.getUnscopedMetrics();
        assertEquals(3, unscopedMetrics.size()); // metric1, metric2 and node metric

        introspector.clear();
        reporter.metricRemoval(METRIC2);
        forceHarvest(reporter);

        unscopedMetrics = introspector.getUnscopedMetrics();
        assertEquals(2, unscopedMetrics.size());
        TracedMetricData metric1 = unscopedMetrics.get("MessageBroker/Kafka/Internal/group/metric1");
        assertEquals(42.0f, metric1.getTotalTimeInSec(), 0.1f);
        TracedMetricData nodeMetric = unscopedMetrics.get("MessageBroker/Kafka/Nodes/localhost:42");
        assertEquals(1.0f, nodeMetric.getTotalTimeInSec(), 0.1f);

        reporter.close();
    }

    @Test
    public void nodeTopicMetrics() throws InterruptedException{
        KafkaMetric metricWithTopic = kafkaMetric("topicMetric", "hhgg", 20.0f);
        List<KafkaMetric> initialMetrics = Arrays.asList(metricWithTopic);

        NewRelicMetricsReporter reporter = initMetricsReporter(initialMetrics, Collections.emptyList());

        Map<String, TracedMetricData> unscopedMetrics = introspector.getUnscopedMetrics();
        assertEquals(3, unscopedMetrics.size());

        TracedMetricData metric1 = unscopedMetrics.get("MessageBroker/Kafka/Internal/group/topicMetric");
        assertEquals(20.0f, metric1.getTotalTimeInSec(), 0.1f);
        TracedMetricData nodeMetric = unscopedMetrics.get("MessageBroker/Kafka/Nodes/localhost:42");
        assertEquals(1.0f, nodeMetric.getTotalTimeInSec(), 0.1f);
        TracedMetricData nodeTopicMetric = unscopedMetrics.get("MessageBroker/Kafka/Nodes/localhost:42/Consume/hhgg");
        assertEquals(1.0f, nodeTopicMetric.getTotalTimeInSec(), 0.1f);

        reporter.close();
    }

    protected static NewRelicMetricsReporter initMetricsReporter(List<KafkaMetric> initMetrics, Collection<KafkaMetric> otherMetrics) throws InterruptedException {
        Node node = mock(Node.class);
        when(node.host())
                .thenReturn("localhost");
        when(node.port())
                .thenReturn(42);
        NewRelicMetricsReporter metricsReporter = new NewRelicMetricsReporter(ClientType.CONSUMER, Collections.singleton(node));
        metricsReporter.init(initMetrics);
        // init triggers the first harvest that happens in a different thread. Sleeping to let it finish.
        Thread.sleep(100L);

        for (KafkaMetric otherMetric : otherMetrics) {
            metricsReporter.metricChange(otherMetric);
        }
        return metricsReporter;
    }

    private static KafkaMetric kafkaMetric(String name, String topic, float value) {
        Gauge<Float> valueProvider = mock(Gauge.class);
        when(valueProvider.value(any(), anyLong()))
                .thenReturn(value);

        Map<String, String> tags = topic == null ?
                Collections.emptyMap() :
                Collections.singletonMap("topic", topic);

        MetricName metricName = new MetricName(name, "group", "descr", tags);
        MetricConfig config = new MetricConfig();
        return new KafkaMetric(new Object(), metricName, valueProvider, config, Time.SYSTEM);
    }


    private void forceHarvest(NewRelicMetricsReporter reporter) throws Exception {
        Method report = NewRelicMetricsReporter.class.getDeclaredMethod("report");
        if (!report.isAccessible()) {
            report.setAccessible(true);
        }
        report.invoke(reporter);
    }
}