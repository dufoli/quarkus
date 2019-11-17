package io.quarkus.mongodb.deployment;

import static io.quarkus.deployment.annotations.ExecutionTime.RUNTIME_INIT;
import static io.quarkus.deployment.annotations.ExecutionTime.STATIC_INIT;

import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Default;
import javax.enterprise.inject.Produces;

import org.bson.codecs.configuration.CodecProvider;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Type;
import org.jboss.logging.Logger;

import com.mongodb.client.MongoClient;

import io.quarkus.arc.Unremovable;
import io.quarkus.arc.deployment.BeanContainerListenerBuildItem;
import io.quarkus.arc.deployment.BeanDefiningAnnotationBuildItem;
import io.quarkus.arc.deployment.GeneratedBeanBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.arc.processor.DotNames;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.ExtensionSslNativeSupportBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.SslNativeConfigBuildItem;
import io.quarkus.deployment.builditem.substrate.ReflectiveClassBuildItem;
import io.quarkus.deployment.recording.RecorderContext;
import io.quarkus.deployment.util.HashUtil;
import io.quarkus.gizmo.ClassCreator;
import io.quarkus.gizmo.ClassOutput;
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;
import io.quarkus.mongodb.ReactiveMongoClient;
import io.quarkus.mongodb.runtime.AbstractMongoClientProducer;
import io.quarkus.mongodb.runtime.MongoClientConfig;
import io.quarkus.mongodb.runtime.MongoClientRecorder;
import io.quarkus.mongodb.runtime.MongodbConfig;

public class MongoClientProcessor {
    private static DotName MONGOCLIENT_ANNOTATION = DotName
            .createSimple(io.quarkus.mongodb.runtime.MongoClient.class.getName());
    private static final Logger LOGGER = Logger.getLogger(MongoClientProcessor.class);
    private static final DotName UNREMOVABLE_BEAN = DotName.createSimple(AbstractMongoClientProducer.class.getName());
    /**
     * The mongodb configuration.
     */
    MongodbConfig mongodbConfig;

    @BuildStep
    BeanDefiningAnnotationBuildItem registerConnectionBean() {
        return new BeanDefiningAnnotationBuildItem(MONGOCLIENT_ANNOTATION);
    }

    @BuildStep
    UnremovableBeanBuildItem markBeansAsUnremovable() {
        return new UnremovableBeanBuildItem(beanInfo -> {
            //index.getAllKnownSubclasses
            for (Type t : beanInfo.getTypes()) {
                if (UNREMOVABLE_BEAN.equals(t.name())) {
                    return true;
                }
            }
            return false;
        });
    }

    @Record(RUNTIME_INIT)
    @BuildStep
    void configureRuntimeProperties(MongoClientRecorder recorder,
            CodecProviderBuildItem codecProvider,
            BuildProducer<MongoClientBuildItem> mongoClients,
            MongodbConfig config) {
        recorder.configureRuntimeProperties(codecProvider.getCodecProviderClassNames(), config);
        mongoClients.produce(new MongoClientBuildItem());
    }

    @BuildStep
    CodecProviderBuildItem collectCodecProviders(CombinedIndexBuildItem indexBuildItem) {
        Collection<ClassInfo> codecProviderClasses = indexBuildItem.getIndex()
                .getAllKnownImplementors(DotName.createSimple(CodecProvider.class.getName()));
        List<String> names = codecProviderClasses.stream().map(ci -> ci.name().toString()).collect(Collectors.toList());
        return new CodecProviderBuildItem(names);
    }

    @BuildStep
    List<ReflectiveClassBuildItem> addCodecsToNative(CodecProviderBuildItem providers) {
        return providers.getCodecProviderClassNames().stream()
                .map(s -> new ReflectiveClassBuildItem(true, true, false, s))
                .collect(Collectors.toList());
    }

    private void createMongoClientProducerBean(BuildProducer<GeneratedBeanBuildItem> generatedBean,
            String mongoClientProducerClassName) {
        ClassOutput classOutput = new ClassOutput() {
            @Override
            public void write(String name, byte[] data) {
                generatedBean.produce(new GeneratedBeanBuildItem(name, data));
            }
        };

        ClassCreator classCreator = ClassCreator.builder().classOutput(classOutput)
                .className(mongoClientProducerClassName)
                .superClass(AbstractMongoClientProducer.class)
                .build();
        classCreator.addAnnotation(ApplicationScoped.class);
        classCreator.addAnnotation(Unremovable.class);

        if (mongodbConfig.defaultMongoClientConfig.connectionString.isPresent()
                || !mongodbConfig.defaultMongoClientConfig.hosts.isPresent()) {
            MethodCreator defaultMongoClientMethodCreator = classCreator.getMethodCreator("createDefaultMongoClient",
                    MongoClient.class);
            defaultMongoClientMethodCreator.addAnnotation(ApplicationScoped.class);
            defaultMongoClientMethodCreator.addAnnotation(Produces.class);
            defaultMongoClientMethodCreator.addAnnotation(Unremovable.class);
            defaultMongoClientMethodCreator.addAnnotation(Default.class);

            ResultHandle mongoClientConfig = defaultMongoClientMethodCreator.invokeVirtualMethod(
                    MethodDescriptor.ofMethod(AbstractMongoClientProducer.class, "getDefaultMongoClientConfig",
                            MongoClientConfig.class),
                    defaultMongoClientMethodCreator.getThis());

            defaultMongoClientMethodCreator.returnValue(
                    defaultMongoClientMethodCreator.invokeVirtualMethod(
                            MethodDescriptor.ofMethod(AbstractMongoClientProducer.class, "createMongoClient",
                                    MongoClient.class,
                                    MongoClientConfig.class),
                            defaultMongoClientMethodCreator.getThis(),
                            mongoClientConfig));
            MethodCreator defaultReactiveMongoClientMethodCreator = classCreator.getMethodCreator(
                    "createDefaultReactiveMongoClient",
                    ReactiveMongoClient.class);
            defaultReactiveMongoClientMethodCreator.addAnnotation(ApplicationScoped.class);
            defaultReactiveMongoClientMethodCreator.addAnnotation(Produces.class);
            defaultReactiveMongoClientMethodCreator.addAnnotation(Unremovable.class);
            defaultReactiveMongoClientMethodCreator.addAnnotation(Default.class);

            ResultHandle mongoReactiveClientConfig = defaultReactiveMongoClientMethodCreator.invokeVirtualMethod(
                    MethodDescriptor.ofMethod(AbstractMongoClientProducer.class, "getDefaultMongoClientConfig",
                            MongoClientConfig.class),
                    defaultReactiveMongoClientMethodCreator.getThis());

            defaultReactiveMongoClientMethodCreator.returnValue(
                    defaultReactiveMongoClientMethodCreator.invokeVirtualMethod(
                            MethodDescriptor.ofMethod(AbstractMongoClientProducer.class, "createReactiveMongoClient",
                                    ReactiveMongoClient.class,
                                    MongoClientConfig.class),
                            defaultReactiveMongoClientMethodCreator.getThis(),
                            mongoReactiveClientConfig));
        }

        for (Entry<String, MongoClientConfig> namedMongoClientEntry : mongodbConfig.mongoClientConfigs
                .entrySet()) {
            String namedMongoClientName = namedMongoClientEntry.getKey();

            if (!namedMongoClientEntry.getValue().connectionString.isPresent()
                    && namedMongoClientEntry.getValue().hosts.isPresent()) {
                LOGGER.warn("No connection url nor host defined for named MongoClient " + namedMongoClientName + ". Ignoring.");
                continue;
            }

            MethodCreator namedMongoClientMethodCreator = classCreator.getMethodCreator(
                    "createNamedMongoClient_" + HashUtil.sha1(namedMongoClientName),
                    MongoClient.class);
            namedMongoClientMethodCreator.addAnnotation(ApplicationScoped.class);
            namedMongoClientMethodCreator.addAnnotation(Produces.class);
            namedMongoClientMethodCreator.addAnnotation(Unremovable.class);
            namedMongoClientMethodCreator.addAnnotation(AnnotationInstance.create(DotNames.NAMED, null,
                    new AnnotationValue[] { AnnotationValue.createStringValue("value", namedMongoClientName) }));
            namedMongoClientMethodCreator
                    .addAnnotation(AnnotationInstance.create(MONGOCLIENT_ANNOTATION, null,
                            new AnnotationValue[] { AnnotationValue.createStringValue("value", namedMongoClientName) }));

            ResultHandle namedMongoClientNameRH = namedMongoClientMethodCreator.load(namedMongoClientName);

            ResultHandle namedMongoClientConfig = namedMongoClientMethodCreator.invokeVirtualMethod(
                    MethodDescriptor.ofMethod(AbstractMongoClientProducer.class, "getMongoClientConfig",
                            MongoClientConfig.class, String.class),
                    namedMongoClientMethodCreator.getThis(), namedMongoClientNameRH);

            namedMongoClientMethodCreator.returnValue(
                    namedMongoClientMethodCreator.invokeVirtualMethod(
                            MethodDescriptor.ofMethod(AbstractMongoClientProducer.class, "createMongoClient",
                                    MongoClient.class,
                                    MongoClientConfig.class),
                            namedMongoClientMethodCreator.getThis(),
                            namedMongoClientConfig));
            MethodCreator namedReactiveMongoClientMethodCreator = classCreator.getMethodCreator(
                    "createNamedReactiveMongoClient_" + HashUtil.sha1(namedMongoClientName),
                    ReactiveMongoClient.class);
            namedReactiveMongoClientMethodCreator.addAnnotation(ApplicationScoped.class);
            namedReactiveMongoClientMethodCreator.addAnnotation(Produces.class);
            namedReactiveMongoClientMethodCreator.addAnnotation(Unremovable.class);
            namedReactiveMongoClientMethodCreator.addAnnotation(AnnotationInstance.create(DotNames.NAMED, null,
                    new AnnotationValue[] { AnnotationValue.createStringValue("value", namedMongoClientName) }));
            namedReactiveMongoClientMethodCreator
                    .addAnnotation(AnnotationInstance.create(MONGOCLIENT_ANNOTATION, null,
                            new AnnotationValue[] { AnnotationValue.createStringValue("value", namedMongoClientName) }));

            ResultHandle namedReactiveMongoClientNameRH = namedReactiveMongoClientMethodCreator.load(namedMongoClientName);

            ResultHandle namedReactiveMongoClientConfig = namedReactiveMongoClientMethodCreator.invokeVirtualMethod(
                    MethodDescriptor.ofMethod(AbstractMongoClientProducer.class, "getMongoClientConfig",
                            MongoClientConfig.class, String.class),
                    namedReactiveMongoClientMethodCreator.getThis(), namedReactiveMongoClientNameRH);

            namedReactiveMongoClientMethodCreator.returnValue(
                    namedReactiveMongoClientMethodCreator.invokeVirtualMethod(
                            MethodDescriptor.ofMethod(AbstractMongoClientProducer.class, "createReactiveMongoClient",
                                    ReactiveMongoClient.class,
                                    MongoClientConfig.class),
                            namedReactiveMongoClientMethodCreator.getThis(),
                            namedReactiveMongoClientConfig));
        }
        classCreator.close();
    }

    @SuppressWarnings("unchecked")
    @Record(STATIC_INIT)
    @BuildStep
    BeanContainerListenerBuildItem build(
            RecorderContext recorderContext,
            MongoClientRecorder recorder,
            BuildProducer<FeatureBuildItem> feature,
            SslNativeConfigBuildItem sslNativeConfig, BuildProducer<ExtensionSslNativeSupportBuildItem> sslNativeSupport,
            BuildProducer<GeneratedBeanBuildItem> generatedBean) throws Exception {

        feature.produce(new FeatureBuildItem(FeatureBuildItem.MONGODB_CLIENT));
        sslNativeSupport.produce(new ExtensionSslNativeSupportBuildItem(FeatureBuildItem.MONGODB_CLIENT));

        String mongoClientProducerClassName = getMongoClientProducerClassName();
        createMongoClientProducerBean(generatedBean, mongoClientProducerClassName);

        return new BeanContainerListenerBuildItem(recorder.addMongoClient(
                (Class<? extends AbstractMongoClientProducer>) recorderContext.classProxy(mongoClientProducerClassName),
                sslNativeConfig.isExplicitlyDisabled()));
    }

    private String getMongoClientProducerClassName() {
        return AbstractMongoClientProducer.class.getPackage().getName() + "."
                + "MongoClientProducer";
    }
}
