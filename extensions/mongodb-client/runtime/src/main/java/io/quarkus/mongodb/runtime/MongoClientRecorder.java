package io.quarkus.mongodb.runtime;

import java.util.List;

import io.quarkus.arc.Arc;
import io.quarkus.arc.runtime.BeanContainer;
import io.quarkus.arc.runtime.BeanContainerListener;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class MongoClientRecorder {

    public BeanContainerListener addMongoClient(
            Class<? extends AbstractMongoClientProducer> mongoClientProducerClass,
            boolean disableSslSupport) {
        return new BeanContainerListener() {
            @Override
            public void created(BeanContainer beanContainer) {
                AbstractMongoClientProducer producer = beanContainer.instance(mongoClientProducerClass);
                if (disableSslSupport) {
                    producer.disableSslSupport();
                }
            }
        };
    }

    public void configureRuntimeProperties(List<String> codecs, MongodbConfig config) {
        // TODO @dmlloyd
        // Same here, the map is entirely empty (obviously, I didn't expect the values
        // that were not properly injected but at least the config objects present in
        // the map)
        // The elements from the default mongoClient are there
        AbstractMongoClientProducer producer = Arc.container().instance(AbstractMongoClientProducer.class).get();
        producer.setCodecs(codecs);
        producer.setConfig(config);
    }
}
